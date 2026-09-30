package com.vishnu.assistant.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

class PhoneCallHandler(
    private val context: Context
) {

    sealed class CallResult {

        data class CallStarted(
            val displayName: String,
            val phoneNumber: String
        ) : CallResult()

        data class PermissionRequired(
            val permission: Permission,
            val displayName: String? = null,
            val phoneNumber: String? = null
        ) : CallResult()

        data class ContactNotFound(
            val contactName: String
        ) : CallResult()

        data class MultipleNumbers(
            val contactName: String,
            val numbers: List<String>
        ) : CallResult()

        data class InvalidNumber(
            val originalText: String
        ) : CallResult()

        data class Failed(
            val displayName: String,
            val phoneNumber: String
        ) : CallResult()

        data object NotHandled : CallResult()
    }

    enum class Permission {
        CONTACTS,
        CALL_LOG,
        CALL_PHONE
    }

    private data class ContactNumber(
        val name: String,
        val number: String,
        val type: Int
    )

    fun handle(
        message: String
    ): CallResult {

        if (!isCallCommand(message)) {
            return CallResult.NotHandled
        }

        /*
         * First check whether the user provided
         * an actual phone number.
         */
        val phoneNumber =
            extractPhoneNumber(message)

        if (phoneNumber != null) {

            if (!isValidPhoneNumber(phoneNumber)) {
                return CallResult.InvalidNumber(
                    originalText = message
                )
            }

            if (!hasCallPermission()) {
                return CallResult.PermissionRequired(
                    permission = Permission.CALL_PHONE,
                    phoneNumber = phoneNumber
                )
            }

            return makeCall(
                displayName = phoneNumber,
                phoneNumber = phoneNumber
            )
        }

        /*
         * Otherwise treat it as a contact name.
         */
        val contactName =
            extractContactName(message)
                ?: return CallResult.InvalidNumber(
                    originalText = message
                )

        if (!hasContactsPermission()) {

            return CallResult.PermissionRequired(
                permission = Permission.CONTACTS,
                displayName = contactName
            )
        }

        val numbers =
            findContactNumbers(
                contactName
            )

        if (numbers.isEmpty()) {

            return CallResult.ContactNotFound(
                contactName = contactName
            )
        }

        /*
         * Only one number.
         */
        if (numbers.size == 1) {

            return callContact(
                contact = numbers.first()
            )
        }

        /*
         * Multiple numbers exist.
         *
         * First try to find the number that was
         * most recently used in the call history.
         */
        if (!hasCallLogPermission()) {

            return CallResult.PermissionRequired(
                permission = Permission.CALL_LOG,
                displayName =
                    numbers.first().name
            )
        }

        val recentNumber =
            findRecentlyCalledNumber(
                numbers = numbers
            )

        if (recentNumber != null) {

            return callContact(
                contact = recentNumber
            )
        }

        /*
         * If no previous call exists, prefer a single
         * mobile number.
         */
        val mobileNumbers =
            numbers.filter {
                it.type ==
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            }

        if (mobileNumbers.size == 1) {

            return callContact(
                contact = mobileNumbers.first()
            )
        }

        /*
         * Still ambiguous.
         */
        return CallResult.MultipleNumbers(
            contactName =
                numbers.first().name,

            numbers =
                numbers.map {
                    it.number
                }
        )
    }

    fun retryCall(
        displayName: String,
        phoneNumber: String
    ): CallResult {

        if (!hasCallPermission()) {

            return CallResult.PermissionRequired(
                permission =
                    Permission.CALL_PHONE,

                displayName =
                    displayName,

                phoneNumber =
                    phoneNumber
            )
        }

        return makeCall(
            displayName =
                displayName,

            phoneNumber =
                phoneNumber
        )
    }

    private fun callContact(
        contact: ContactNumber
    ): CallResult {

        if (!hasCallPermission()) {

            return CallResult.PermissionRequired(
                permission =
                    Permission.CALL_PHONE,

                displayName =
                    contact.name,

                phoneNumber =
                    contact.number
            )
        }

        return makeCall(
            displayName =
                contact.name,

            phoneNumber =
                contact.number
        )
    }

    private fun findRecentlyCalledNumber(
        numbers: List<ContactNumber>
    ): ContactNumber? {

        if (!hasCallLogPermission()) {
            return null
        }

        val normalizedNumbers =
            numbers.associateBy {
                normalizePhoneNumber(
                    it.number
                )
            }

        val projection =
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            )

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->

            val numberIndex =
                cursor.getColumnIndex(
                    CallLog.Calls.NUMBER
                )

            while (cursor.moveToNext()) {

                if (numberIndex < 0) {
                    continue
                }

                val callNumber =
                    cursor.getString(
                        numberIndex
                    )

                val normalizedCallNumber =
                    normalizePhoneNumber(
                        callNumber
                    )

                val matchedContact =
                    normalizedNumbers[
                        normalizedCallNumber
                    ]

                if (matchedContact != null) {
                    return matchedContact
                }
            }
        }

        return null
    }

    private fun makeCall(
        displayName: String,
        phoneNumber: String
    ): CallResult {

        return try {

            val intent =
                Intent(
                    Intent.ACTION_CALL,
                    Uri.parse(
                        "tel:${Uri.encode(phoneNumber)}"
                    )
                ).apply {

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            context.startActivity(
                intent
            )

            CallResult.CallStarted(
                displayName =
                    displayName,

                phoneNumber =
                    phoneNumber
            )

        } catch (
            exception: Exception
        ) {

            CallResult.Failed(
                displayName =
                    displayName,

                phoneNumber =
                    phoneNumber
            )
        }
    }

    private fun findContactNumbers(
        contactName: String
    ): List<ContactNumber> {

        val results =
            mutableListOf<ContactNumber>()

        val uri =
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        val projection =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

        val selection =
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"

        val selectionArgs =
            arrayOf(
                "%$contactName%"
            )

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->

            val nameIndex =
                cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

            val numberIndex =
                cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

            val typeIndex =
                cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.TYPE
                )

            while (cursor.moveToNext()) {

                if (
                    nameIndex < 0 ||
                    numberIndex < 0
                ) {
                    continue
                }

                val name =
                    cursor.getString(
                        nameIndex
                    )

                val number =
                    cursor.getString(
                        numberIndex
                    )

                val type =
                    if (typeIndex >= 0) {

                        cursor.getInt(
                            typeIndex
                        )

                    } else {

                        ContactsContract
                            .CommonDataKinds
                            .Phone
                            .TYPE_OTHER
                    }

                if (
                    name.isNotBlank() &&
                    number.isNotBlank()
                ) {

                    results.add(
                        ContactNumber(
                            name =
                                name,

                            number =
                                number,

                            type =
                                type
                        )
                    )
                }
            }
        }

        return results.distinctBy {
            normalizePhoneNumber(
                it.number
            )
        }
    }

    private fun hasContactsPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasCallLogPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasCallPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun extractPhoneNumber(
        message: String
    ): String? {

        val normalized =
            message
                .replace(
                    " ",
                    ""
                )
                .replace(
                    "-",
                    ""
                )
                .replace(
                    "(",
                    ""
                )
                .replace(
                    ")",
                    ""
                )

        val phonePattern =
            Regex(
                """(?<!\d)(?:\+91)?[6-9]\d{9}(?!\d)"""
            )

        return phonePattern
            .find(normalized)
            ?.value
    }

    private fun extractContactName(
        message: String
    ): String? {

        var text =
            message.trim()

        val patterns =
            listOf(

                Regex(
                    """(?i)^\s*(?:please\s+)?call\s+(.+?)\s*$"""
                ),

                Regex(
                    """(?i)^\s*(?:please\s+)?dial\s+(.+?)\s*$"""
                ),

                Regex(
                    """(?i)^\s*(?:please\s+)?phone\s+(.+?)\s*$"""
                ),

                Regex(
                    """(?i)^(.+?)\s+call\s+pannu(?:nga)?\s*$"""
                ),

                Regex(
                    """(?i)^(.+?)\s*[- ]?ku\s+call\s+pannu(?:nga)?\s*$"""
                ),

                Regex(
                    """(?i)^call\s+(.+?)\s*$"""
                )
            )

        for (pattern in patterns) {

            val match =
                pattern.find(text)

            if (match != null) {

                text =
                    match
                        .groupValues
                        .getOrNull(1)
                        ?.trim()
                        .orEmpty()

                break
            }
        }

        /*
         * Remove common spoken Tamil/Tanglish suffixes.
         */
        text =
            text
                .replace(
                    Regex(
                        """(?i)\s*(?:ku|kku)$"""
                    ),
                    ""
                )
                .replace(
                    Regex(
                        """(?i)\s*(?:a|ah)$"""
                    ),
                    ""
                )
                .trim()

        /*
         * Remove common Tamil call words.
         */
        text =
            text
                .replace(
                    Regex(
                        """\s*(?:க்கு|கிட்ட|அண்ணா|அக்கா)$"""
                    ),
                    ""
                )
                .trim()

        val invalidNames =
            setOf(
                "call",
                "dial",
                "phone",
                "pannu",
                "pannunga",
                "கால்",
                "அழை"
            )

        if (
            text.isBlank() ||
            text.lowercase() in invalidNames
        ) {
            return null
        }

        return text
    }

    private fun isCallCommand(
        message: String
    ): Boolean {

        val text =
            message.lowercase()

        val keywords =
            listOf(
                "call",
                "dial",
                "phone",
                "call pannu",
                "call pannunga",
                "கால்",
                "கால் பண்ணு",
                "கால் பண்ணுங்க",
                "அழை",
                "அழைக்க",
                "அழைப்பு"
            )

        return keywords.any {
            text.contains(it)
        }
    }

    private fun isValidPhoneNumber(
        phoneNumber: String
    ): Boolean {

        val digits =
            phoneNumber
                .removePrefix("+91")

        return digits.length == 10 &&
            digits.firstOrNull() in
            '6'..'9'
    }

    private fun normalizePhoneNumber(
        phoneNumber: String
    ): String {

        return phoneNumber
            .filter {
                it.isDigit()
            }
            .removePrefix("91")
    }
}