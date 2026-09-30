package com.vishnu.assistant.ui

import com.vishnu.assistant.core.speech.VoiceGender
import com.vishnu.assistant.data.ChatMessage

enum class AssistantLanguage(
    val displayName: String,
    val speechLocale: String,
    val apiHint: String
) {
    ENGLISH(
        displayName = "English",
        speechLocale = "en-IN",
        apiHint = "en"
    ),
    TAMIL(
        displayName = "Tamil",
        speechLocale = "ta-IN",
        apiHint = "ta"
    )
}

/**
 * Online: LLM (Groq or the EnodaAI backend) + web search.
 * Offline: on-device local commands and LocalBrain answers.
 */
enum class AssistantMode(
    val displayName: String
) {
    ONLINE("Online"),
    OFFLINE("Offline")
}

enum class AssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    RECOGNIZED,
    ERROR
}

data class AssistantUiState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val statusMessage: String = "Tap the mic or type below",
    val recognizedText: String = "",
    val responseText: String = "",
    val language: AssistantLanguage = AssistantLanguage.ENGLISH,

    val mode: AssistantMode = AssistantMode.ONLINE,
    val voiceGender: VoiceGender = VoiceGender.FEMALE,

    val groqApiKey: String = "",
    val webSearchEnabled: Boolean = true,

    val contactsPermissionRequired: Boolean = false,
    val callLogPermissionRequired: Boolean = false,
    val callPermissionRequired: Boolean = false,

    val messages: List<ChatMessage> = emptyList()
)
