package com.vishnu.assistant.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishnu.assistant.core.speech.Speaker
import com.vishnu.assistant.core.speech.VoiceGender
import com.vishnu.assistant.core.speech.VoiceRecognitionEvent
import com.vishnu.assistant.core.speech.VoiceRecognizer
import com.vishnu.assistant.data.ChatMessage
import com.vishnu.assistant.data.ChatRepository
import com.vishnu.assistant.data.ChatRole
import com.vishnu.assistant.data.SettingsStore
import com.vishnu.assistant.utils.LocalBrain
import com.vishnu.assistant.utils.LocalCommandHandler
import com.vishnu.assistant.utils.PhoneCallHandler
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val voiceRecognizer: VoiceRecognizer,
    private val chatRepository: ChatRepository,
    private val speaker: Speaker,
    private val settingsStore: SettingsStore,
    context: Context
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            AssistantUiState()
        )

    val uiState: StateFlow<AssistantUiState> =
        _uiState.asStateFlow()

    val isRecognizerAvailable: Boolean
        get() = voiceRecognizer.isAvailable

    private val appContext =
        context.applicationContext

    private val localCommandHandler =
        LocalCommandHandler(
            appContext
        )

    private val phoneCallHandler =
        PhoneCallHandler(
            appContext
        )

    private val localBrain =
        LocalBrain(
            appContext
        )

    init {

        // Restore persisted preferences on startup.

        val savedLanguage =
            if (settingsStore.isTamil()) {
                AssistantLanguage.TAMIL
            } else {
                AssistantLanguage.ENGLISH
            }

        val savedMode =
            if (settingsStore.isOnlineMode()) {
                AssistantMode.ONLINE
            } else {
                AssistantMode.OFFLINE
            }

        val savedGender =
            settingsStore.getVoiceGender()

        speaker.setVoiceGender(savedGender)

        _uiState.update {
            it.copy(
                language = savedLanguage,
                mode = savedMode,
                voiceGender = savedGender,
                groqApiKey = settingsStore.getGroqApiKey(),
                webSearchEnabled = settingsStore.isWebSearchEnabled()
            )
        }
    }

    private var silenceWatchdog: Job? = null

    private var speechWatchdog: Job? = null

    private var conversationId =
        UUID.randomUUID().toString()

    /*
     * Pending contact call.
     *
     * Used when Android asks for:
     * READ_CONTACTS or READ_CALL_LOG permission.
     */
    private var pendingContactCallName: String? = null

    /*
     * Pending actual call.
     *
     * Used when Android asks for CALL_PHONE permission.
     */
    private var pendingCallName: String? = null

    private var pendingCallNumber: String? = null

    fun startListening() {

        speaker.stop()

        speechWatchdog?.cancel()

        val language =
            _uiState.value.language

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.LISTENING,

                statusMessage =
                    "Listening...",

                recognizedText =
                    "",

                responseText =
                    ""
            )
        }

        voiceRecognizer.startListening(
            speechLocale =
                language.speechLocale,

            onEvent =
                ::handleEvent
        )

        restartSilenceWatchdog()
    }

    fun setLanguage(
        language: AssistantLanguage
    ) {

        settingsStore.setTamil(
            language == AssistantLanguage.TAMIL
        )

        if (
            _uiState.value.status == AssistantStatus.LISTENING
        ) {
            return
        }

        _uiState.update {
            it.copy(
                language = language,

                recognizedText = "",

                responseText = "",

                status = AssistantStatus.IDLE,

                statusMessage = "Tap the mic or type below"
            )
        }
    }

    /**
     * Text input from the chat box (Indus-style) follows the
     * exact same pipeline as voice: local commands first,
     * then search + LLM, and the reply is always spoken.
     */
    fun sendTextMessage(
        text: String
    ) {

        val trimmed = text.trim()

        if (trimmed.isEmpty()) {
            return
        }

        speaker.stop()

        _uiState.update {
            it.copy(
                status = AssistantStatus.THINKING,

                statusMessage = "Thinking...",

                recognizedText = trimmed
            )
        }

        handleUserMessage(trimmed)
    }

    /*
     * Online / Offline mode (persisted).
     */
    fun setMode(
        mode: AssistantMode
    ) {

        settingsStore.setOnlineMode(
            mode == AssistantMode.ONLINE
        )

        _uiState.update {
            it.copy(
                mode = mode,

                status = AssistantStatus.IDLE,

                statusMessage =
                    if (mode == AssistantMode.ONLINE) {
                        "Online mode - AI with web search"
                    } else {
                        "Offline mode - device commands only"
                    }
            )
        }
    }

    /*
     * Tamil Nadu style male / female voice (persisted).
     */
    fun setVoiceGender(
        gender: VoiceGender
    ) {

        settingsStore.setVoiceGender(gender)

        speaker.setVoiceGender(gender)

        _uiState.update {
            it.copy(
                voiceGender = gender
            )
        }
    }

    /*
     * Groq API key for direct LLM access (persisted).
     */
    fun setGroqApiKey(
        key: String
    ) {

        settingsStore.setGroqApiKey(key)

        _uiState.update {
            it.copy(
                groqApiKey = key.trim()
            )
        }
    }

    /*
     * Web search toggle (persisted).
     */
    fun setWebSearchEnabled(
        enabled: Boolean
    ) {

        settingsStore.setWebSearchEnabled(enabled)

        _uiState.update {
            it.copy(
                webSearchEnabled = enabled
            )
        }
    }

    /*
     * Tap-again on the mic: stop listening and submit what
     * was captured so far.
     */
    fun stopListeningAndSubmit() {

        if (
            _uiState.value.status != AssistantStatus.LISTENING
        ) {
            return
        }

        silenceWatchdog?.cancel()

        voiceRecognizer.stopListening()
    }

    /*
     * Stop any speech immediately (barge-in).
     */
    fun stopSpeaking() {

        speechWatchdog?.cancel()

        speaker.stop()

        _uiState.update { state ->

            if (
                state.status == AssistantStatus.SPEAKING
            ) {
                state.copy(
                    status = AssistantStatus.RECOGNIZED,
                    statusMessage = "Reply received."
                )
            } else {
                state
            }
        }
    }

    /*
     * READ_CONTACTS permission result.
     */
    fun onContactsPermissionResult(
        granted: Boolean
    ) {

        _uiState.update {
            it.copy(
                contactsPermissionRequired =
                    false
            )
        }

        if (granted) {

            val contactName =
                pendingContactCallName

            pendingContactCallName =
                null

            if (
                !contactName.isNullOrBlank()
            ) {

                continueContactCall(
                    contactName
                )
            }

        } else {

            pendingContactCallName =
                null

            showCallError(
                english =
                    "Contacts permission is required to find the contact.",

                tamil =
                    "Contact-ஐ தேட Contacts permission தேவை."
            )
        }
    }

    /*
     * READ_CALL_LOG permission result.
     *
     * This is needed when the contact has
     * multiple phone numbers.
     */
    fun onCallLogPermissionResult(
        granted: Boolean
    ) {

        _uiState.update {
            it.copy(
                callLogPermissionRequired =
                    false
            )
        }

        val contactName =
            pendingContactCallName

        pendingContactCallName =
            null

        if (
            granted &&
            !contactName.isNullOrBlank()
        ) {

            continueContactCall(
                contactName
            )

        } else if (!granted) {

            showCallError(
                english =
                    "Call history permission is required to choose the recently contacted number.",

                tamil =
                    "சமீபத்தில் தொடர்பு கொண்ட number-ஐ தேர்வு செய்ய Call history permission தேவை."
            )
        }
    }

    /*
     * CALL_PHONE permission result.
     */
    fun onCallPermissionResult(
        granted: Boolean
    ) {

        _uiState.update {
            it.copy(
                callPermissionRequired =
                    false
            )
        }

        val name =
            pendingCallName

        val number =
            pendingCallNumber

        pendingCallName =
            null

        pendingCallNumber =
            null

        if (
            granted &&
            !name.isNullOrBlank() &&
            !number.isNullOrBlank()
        ) {

            val result =
                phoneCallHandler.retryCall(
                    displayName =
                        name,

                    phoneNumber =
                        number
                )

            handleCallResult(
                result
            )

        } else if (!granted) {

            showCallError(
                english =
                    "Phone permission is required to make the call.",

                tamil =
                    "Call செய்ய Phone permission தேவை."
            )
        }
    }

    fun reset() {

        silenceWatchdog?.cancel()

        speechWatchdog?.cancel()

        speaker.stop()

        pendingContactCallName =
            null

        pendingCallName =
            null

        pendingCallNumber =
            null

        conversationId =
            UUID.randomUUID().toString()

        chatRepository.resetForNewConversation()

        _uiState.value = AssistantUiState(
            language = _uiState.value.language,
            mode = _uiState.value.mode,
            voiceGender = _uiState.value.voiceGender,
            groqApiKey = _uiState.value.groqApiKey,
            webSearchEnabled = _uiState.value.webSearchEnabled
        )
    }

    override fun onCleared() {

        silenceWatchdog?.cancel()

        speechWatchdog?.cancel()

        voiceRecognizer.destroy()

        speaker.destroy()

        super.onCleared()
    }

    private fun restartSilenceWatchdog() {

        silenceWatchdog?.cancel()

        silenceWatchdog =
            viewModelScope.launch {

                delay(
                    SILENCE_TIMEOUT_MS
                )

                endSessionManually()
            }
    }

    private fun handleEvent(
        event: VoiceRecognitionEvent
    ) {

        when (event) {

            VoiceRecognitionEvent.ListeningStarted,
            VoiceRecognitionEvent.SpeechEnded -> {

                restartSilenceWatchdog()
            }

            is VoiceRecognitionEvent.Partial -> {

                _uiState.update {
                    it.copy(
                        recognizedText =
                            event.text
                    )
                }

                restartSilenceWatchdog()
            }

            is VoiceRecognitionEvent.Final -> {

                silenceWatchdog?.cancel()

                if (
                    event.text.isBlank()
                ) {

                    _uiState.update {
                        it.copy(
                            status =
                                AssistantStatus.ERROR,

                            statusMessage =
                                "I didn't catch any speech. Please try again."
                        )
                    }

                } else {

                    _uiState.update {
                        it.copy(
                            status =
                                AssistantStatus.RECOGNIZED,

                            statusMessage =
                                "Recognized.",

                            recognizedText =
                                event.text
                        )
                    }

                    handleUserMessage(
                        event.text
                    )
                }
            }

            is VoiceRecognitionEvent.Error -> {

                silenceWatchdog?.cancel()

                _uiState.update {
                    it.copy(
                        status =
                            AssistantStatus.ERROR,

                        statusMessage =
                            event.message
                    )
                }
            }
        }
    }

    private fun handleUserMessage(
        text: String
    ) {

        addConversationMessage(
            role =
                ChatRole.USER,

            text =
                text
        )

        /*
         * Phone calls have priority over
         * other local commands.
         */
        when (
            val result =
                phoneCallHandler.handle(text)
        ) {

            PhoneCallHandler.CallResult.NotHandled -> {

                handleOtherLocalCommands(
                    text
                )
            }

            is PhoneCallHandler.CallResult.PermissionRequired -> {

                handlePermissionRequired(
                    result
                )
            }

            is PhoneCallHandler.CallResult.CallStarted -> {

                handleCallStarted(
                    result
                )
            }

            is PhoneCallHandler.CallResult.ContactNotFound -> {

                handleContactNotFound(
                    result.contactName
                )
            }

            is PhoneCallHandler.CallResult.MultipleNumbers -> {

                handleMultipleNumbers(
                    result
                )
            }

            is PhoneCallHandler.CallResult.InvalidNumber -> {

                handleInvalidPhoneNumber()
            }

            is PhoneCallHandler.CallResult.Failed -> {

                handleCallFailed(
                    result
                )
            }
        }
    }

    private fun handleOtherLocalCommands(
        text: String
    ) {

        when (
            val result =
                localCommandHandler.handle(text)
        ) {

            is LocalCommandHandler.CommandResult.AppOpened -> {

                handleLocalAppOpened(
                    result.appName
                )
            }

            is LocalCommandHandler.CommandResult.AppNotFound -> {

                handleLocalAppNotFound(
                    result.requestedName
                )
            }

            LocalCommandHandler.CommandResult.NotHandled -> {

                askBackend(
                    text
                )
            }
        }
    }

    private fun handlePermissionRequired(
        result:
            PhoneCallHandler.CallResult.PermissionRequired
    ) {

        when (
            result.permission
        ) {

            PhoneCallHandler.Permission.CONTACTS -> {

                pendingContactCallName =
                    result.displayName

                _uiState.update {
                    it.copy(
                        contactsPermissionRequired =
                            true,

                        status =
                            AssistantStatus.THINKING,

                        statusMessage =
                            "Contacts permission required."
                    )
                }
            }

            PhoneCallHandler.Permission.CALL_LOG -> {

                pendingContactCallName =
                    result.displayName

                _uiState.update {
                    it.copy(
                        callLogPermissionRequired =
                            true,

                        status =
                            AssistantStatus.THINKING,

                        statusMessage =
                            "Call history permission required."
                    )
                }
            }

            PhoneCallHandler.Permission.CALL_PHONE -> {

                pendingCallName =
                    result.displayName

                pendingCallNumber =
                    result.phoneNumber

                _uiState.update {
                    it.copy(
                        callPermissionRequired =
                            true,

                        status =
                            AssistantStatus.THINKING,

                        statusMessage =
                            "Phone permission required."
                    )
                }
            }
        }
    }

    private fun continueContactCall(
        contactName: String
    ) {

        when (
            val result =
                phoneCallHandler.handle(
                    "call $contactName"
                )
        ) {

            PhoneCallHandler.CallResult.NotHandled -> {

                handleContactNotFound(
                    contactName
                )
            }

            else -> {

                handleCallResult(
                    result
                )
            }
        }
    }

    private fun handleCallResult(
        result:
            PhoneCallHandler.CallResult
    ) {

        when (result) {

            PhoneCallHandler.CallResult.NotHandled -> {

                handleContactNotFound(
                    "contact"
                )
            }

            is PhoneCallHandler.CallResult.PermissionRequired -> {

                handlePermissionRequired(
                    result
                )
            }

            is PhoneCallHandler.CallResult.CallStarted -> {

                handleCallStarted(
                    result
                )
            }

            is PhoneCallHandler.CallResult.ContactNotFound -> {

                handleContactNotFound(
                    result.contactName
                )
            }

            is PhoneCallHandler.CallResult.MultipleNumbers -> {

                handleMultipleNumbers(
                    result
                )
            }

            is PhoneCallHandler.CallResult.InvalidNumber -> {

                handleInvalidPhoneNumber()
            }

            is PhoneCallHandler.CallResult.Failed -> {

                handleCallFailed(
                    result
                )
            }
        }
    }

    private fun handleCallStarted(
        result:
            PhoneCallHandler.CallResult.CallStarted
    ) {

        val language =
            _uiState.value.language

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {

                "${result.displayName}க்கு அழைக்கிறேன்."

            } else {

                "Calling ${result.displayName}."
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.SPEAKING,

                statusMessage =
                    "Speaking...",

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun handleContactNotFound(
        contactName: String
    ) {

        val language =
            _uiState.value.language

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {

                "$contactName என்ற contact கிடைக்கவில்லை."

            } else {

                "I couldn't find $contactName in your contacts."
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.SPEAKING,

                statusMessage =
                    "Speaking...",

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun handleMultipleNumbers(
        result:
            PhoneCallHandler.CallResult.MultipleNumbers
    ) {

        val language =
            _uiState.value.language

        val numbers =
            result.numbers
                .take(3)
                .joinToString(
                    separator = ", "
                )

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {

                "${result.contactName}க்கு ஒன்றுக்கும் மேற்பட்ட phone numbers உள்ளன: $numbers"

            } else {

                "${result.contactName} has multiple phone numbers: $numbers"
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.SPEAKING,

                statusMessage =
                    "Multiple phone numbers found.",

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun handleInvalidPhoneNumber() {

        val language =
            _uiState.value.language

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {

                "சரியான phone number அல்லது contact name சொல்லுங்கள்."

            } else {

                "Please provide a valid phone number or contact name."
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.SPEAKING,

                statusMessage =
                    "Invalid call request.",

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun handleCallFailed(
        result:
            PhoneCallHandler.CallResult.Failed
    ) {

        val language =
            _uiState.value.language

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {

                "${result.displayName}க்கு call செய்ய முடியவில்லை."

            } else {

                "I couldn't make the call to ${result.displayName}."
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.ERROR,

                statusMessage =
                    "Call failed.",

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun handleLocalAppOpened(
        appName: String
    ) {

        val language =
            _uiState.value.language

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {

                "$appName திறக்கப்பட்டது."

            } else {

                "$appName opened."
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.SPEAKING,

                statusMessage =
                    "Speaking...",

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun handleLocalAppNotFound(
        requestedName: String
    ) {

        val language =
            _uiState.value.language

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {

                "$requestedName என்ற app கிடைக்கவில்லை."

            } else {

                "I couldn't find $requestedName on your phone."
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.SPEAKING,

                statusMessage =
                    "Speaking...",

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun addConversationMessage(
        role: ChatRole,
        text: String
    ) {

        _uiState.update { state ->

            state.copy(
                messages =
                    state.messages +
                        ChatMessage(
                            role =
                                role,

                            text =
                                text
                        )
            )
        }
    }

    private fun askBackend(
        text: String
    ) {

        val language =
            _uiState.value.language

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.THINKING,

                statusMessage =
                    "Thinking..."
            )
        }

        viewModelScope.launch {

            val settings =
                ChatRepository.ChatSettings(
                    online = _uiState.value.mode ==
                        AssistantMode.ONLINE,

                    groqApiKey = _uiState.value.groqApiKey,

                    webSearchEnabled =
                        _uiState.value.webSearchEnabled
                )

            when (
                val result =
                    chatRepository.sendMessage(
                        text = text,

                        language = language.apiHint,

                        conversationId = conversationId,

                        settings = settings,

                        localBrain = localBrain
                    )
            ) {

                is ChatRepository.ChatResult.Success -> {

                    addConversationMessage(
                        role =
                            ChatRole.ASSISTANT,

                        text =
                            result.reply
                    )

                    _uiState.update {
                        it.copy(
                            status =
                                AssistantStatus.SPEAKING,

                            statusMessage =
                                "Speaking...",

                            responseText =
                                result.reply
                        )
                    }

                    speakReply(
                        reply =
                            result.reply,

                        speechLocale =
                            language.speechLocale
                    )
                }

                is ChatRepository.ChatResult.Failure -> {

                    addConversationMessage(
                        role = ChatRole.ASSISTANT,
                        text = result.message
                    )

                    _uiState.update {
                        it.copy(
                            status = AssistantStatus.ERROR,
                            statusMessage = result.message,
                            responseText = result.message
                        )
                    }
                }
            }
        }
    }

    private fun speakReply(
        reply: String,
        speechLocale: String
    ) {

        speechWatchdog?.cancel()

        speechWatchdog =
            viewModelScope.launch {

                delay(
                    SPEAKING_TIMEOUT_MS
                )

                if (
                    _uiState.value.status ==
                    AssistantStatus.SPEAKING
                ) {

                    _uiState.update {
                        it.copy(
                            status =
                                AssistantStatus.RECOGNIZED,

                            statusMessage =
                                "Reply received."
                        )
                    }
                }
            }

        speaker.speak(
            text =
                reply,

            speechLocale =
                speechLocale
        ) {

            speechWatchdog?.cancel()

            _uiState.update { state ->

                if (
                    state.status ==
                    AssistantStatus.SPEAKING
                ) {

                    state.copy(
                        status =
                            AssistantStatus.RECOGNIZED,

                        statusMessage =
                            "Reply received."
                    )

                } else {

                    state
                }
            }
        }
    }

    private fun showCallError(
        english: String,
        tamil: String
    ) {

        val language =
            _uiState.value.language

        val reply =
            if (
                language ==
                AssistantLanguage.TAMIL
            ) {
                tamil
            } else {
                english
            }

        addConversationMessage(
            role =
                ChatRole.ASSISTANT,

            text =
                reply
        )

        _uiState.update {
            it.copy(
                status =
                    AssistantStatus.ERROR,

                statusMessage =
                    reply,

                responseText =
                    reply
            )
        }

        speakReply(
            reply =
                reply,

            speechLocale =
                language.speechLocale
        )
    }

    private fun endSessionManually() {

        if (
            _uiState.value.status !=
            AssistantStatus.LISTENING
        ) {
            return
        }

        voiceRecognizer.destroy()

        val partial =
            _uiState.value.recognizedText

        if (
            partial.isNotBlank()
        ) {

            _uiState.update {
                it.copy(
                    status =
                        AssistantStatus.RECOGNIZED,

                    statusMessage =
                        "Recognized."
                )
            }

            handleUserMessage(
                partial
            )

        } else {

            _uiState.update {
                it.copy(
                    status =
                        AssistantStatus.ERROR,

                    statusMessage =
                        "I didn't catch any speech. Please try again."
                )
            }
        }
    }

    companion object {

        private const val
            SILENCE_TIMEOUT_MS =
            7_000L

        private const val
            SPEAKING_TIMEOUT_MS =
            30_000L
    }
}