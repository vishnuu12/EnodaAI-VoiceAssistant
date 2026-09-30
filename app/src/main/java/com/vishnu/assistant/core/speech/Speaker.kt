package com.vishnu.assistant.core.speech

/**
 * Abstraction over text-to-speech so a different engine
 * (cloud TTS, recorded prompts) can replace the Android one later.
 */
interface Speaker {

    /** True once the engine finished initializing. */
    val isAvailable: Boolean

    /**
     * Preferred voice gender (Tamil Nadu style male/female).
     * Applied to subsequent speak() calls.
     */
    fun setVoiceGender(gender: VoiceGender)

    /**
     * Speak the given text using the requested language/locale.
     *
     * Examples:
     * English -> "en-IN"
     * Tamil   -> "ta-IN"
     *
     * If the requested locale is unavailable, the implementation
     * should gracefully fall back to a supported voice.
     *
     * [onDone] fires exactly once, when the utterance finishes
     * or fails. May be called from a background thread.
     */
    fun speak(
        text: String,
        speechLocale: String,
        onDone: () -> Unit = {}
    )

    /** Stop any current speech immediately. Safe if not speaking. */
    fun stop()

    /** Release the engine. */
    fun destroy()
}
