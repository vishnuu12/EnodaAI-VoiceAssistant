package com.vishnu.assistant.core.speech

/**
 * Events emitted while a recognition session runs.
 * Partial fires repeatedly while the user speaks (live transcription).
 */
sealed interface VoiceRecognitionEvent {
    data object ListeningStarted : VoiceRecognitionEvent
    data object SpeechEnded : VoiceRecognitionEvent
    data class Partial(val text: String) : VoiceRecognitionEvent
    data class Final(val text: String) : VoiceRecognitionEvent
    data class Error(val code: Int, val message: String) : VoiceRecognitionEvent
}

/**
 * Abstraction over speech-to-text so a different engine
 * (offline model, cloud STT) can replace the Android one later.
 */
interface VoiceRecognizer {
    val isAvailable: Boolean

    /**
     * Starts speech recognition using the requested locale.
     *
     * Examples:
     * English -> "en-IN"
     * Tamil   -> "ta-IN"
     */
    fun startListening(
        speechLocale: String,
        onEvent: (VoiceRecognitionEvent) -> Unit
    )

    fun stopListening()

    fun destroy()
}