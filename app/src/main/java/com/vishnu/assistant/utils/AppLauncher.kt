package com.vishnu.assistant.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

class AppLauncher(
    private val context: Context
) {

    private val packageManager: PackageManager =
        context.packageManager

    data class InstalledApp(
        val name: String,
        val packageName: String
    )

    /**
     * Try to detect an app name from a natural-language command.
     *
     * Examples:
     *
     * "Open YouTube" -> YouTube
     * "YouTube open pannunga" -> YouTube
     * "WhatsApp-ஐ open பண்ணு" -> WhatsApp
     */
    fun extractAppName(message: String): String? {

        var text = message.trim()

        if (text.isEmpty()) {
            return null
        }

        // Remove common English/Tanglish command words.
        val commandWords = listOf(
            "open",
            "launch",
            "start",
            "run",
            "please",
            "the",
            "app",
            "application",
            "pannu",
            "pannunga",
            "pannum",
            "pannidu",
            "pannidunga",
            "thira",
            "thirandhu",
            "thirakkavum"
        )

        for (word in commandWords) {
            text = text.replace(
                Regex(
                    "\\b${Regex.escape(word)}\\b",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
        }

        // Remove common Tamil particles/suffixes.
        text = text
            .replace("ஐ", " ")
            .replace("யை", " ")
            .replace("க்கு", " ")
            .replace("ல", " ")
            .replace("ல்", " ")

        // Remove punctuation.
        text = text
            .replace("-", " ")
            .replace("_", " ")
            .replace(",", " ")
            .replace(".", " ")
            .replace("?", " ")
            .replace("!", " ")

        text = text
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

        if (text.isEmpty()) {
            return null
        }

        return text
    }

    /**
     * Find a launcher app using the user's spoken app name.
     */
    fun findApp(
        requestedName: String
    ): InstalledApp? {

        val apps = getInstalledLauncherApps()

        val normalizedRequest =
            normalize(requestedName)

        if (normalizedRequest.isEmpty()) {
            return null
        }

        // ---------------------------------------------------------
        // Exact match
        // ---------------------------------------------------------

        apps.firstOrNull {
            normalize(it.name) == normalizedRequest
        }?.let {
            return it
        }

        // ---------------------------------------------------------
        // App name contains requested name
        // ---------------------------------------------------------

        apps.firstOrNull {
            normalize(it.name).contains(
                normalizedRequest
            )
        }?.let {
            return it
        }

        // ---------------------------------------------------------
        // Requested name contains app name
        // ---------------------------------------------------------

        apps.firstOrNull {
            normalizedRequest.contains(
                normalize(it.name)
            )
        }?.let {
            return it
        }

        // ---------------------------------------------------------
        // Partial word matching
        // ---------------------------------------------------------

        val requestWords =
            normalizedRequest
                .split(" ")
                .filter { it.length >= 2 }

        if (requestWords.isNotEmpty()) {

            apps.firstOrNull { app ->

                val appName =
                    normalize(app.name)

                requestWords.any { word ->
                    appName.contains(word)
                }
            }?.let {
                return it
            }
        }

        return null
    }

    /**
     * Open the application.
     */
    fun launchApp(
        app: InstalledApp
    ): Boolean {

        return try {

            val launchIntent =
                packageManager.getLaunchIntentForPackage(
                    app.packageName
                )

            if (launchIntent == null) {
                false
            } else {

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(
                    launchIntent
                )

                true
            }

        } catch (
            exception: Exception
        ) {

            false
        }
    }

    /**
     * Get all applications that have a launcher activity.
     */
    private fun getInstalledLauncherApps():
            List<InstalledApp> {

        val intent = Intent(
            Intent.ACTION_MAIN
        ).apply {
            addCategory(
                Intent.CATEGORY_LAUNCHER
            )
        }

        val resolveInfos =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )

            } else {

                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(
                    intent,
                    0
                )
            }

        return resolveInfos
            .mapNotNull { resolveInfo ->

                val packageName =
                    resolveInfo.activityInfo.packageName

                val appName =
                    resolveInfo.loadLabel(
                        packageManager
                    )?.toString()
                        ?.trim()

                if (
                    appName.isNullOrEmpty()
                ) {
                    null
                } else {
                    InstalledApp(
                        name = appName,
                        packageName = packageName
                    )
                }
            }
            .distinctBy {
                it.packageName
            }
            .sortedBy {
                it.name.lowercase(
                    Locale.getDefault()
                )
            }
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase(Locale.getDefault())
            .replace(
                Regex("[^a-z0-9 ]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}