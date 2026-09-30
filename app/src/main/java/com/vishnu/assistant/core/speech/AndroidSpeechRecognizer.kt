package com.vishnu.assistant.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wraps Android's built-in SpeechRecognizer (backed by Google's
 * speech service). Requires RECORD_AUDIO permission and internet.
 *
 * Must be created and called from the main thread.
 * All callbacks arrive on the main thread.
 */
class AndroidSpeechRecognizer(
    private val context: Context
) : VoiceRecognizer {

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null

    override fun startListening(
        speechLocale: String,
        onEvent: (VoiceRecognitionEvent) -> Unit
    ) {
        release()

        if (!isAvailable) {
            onEvent(
                VoiceRecognitionEvent.Error(
                    code = -1,
                    message = "No speech recognition service found on this device. " +
                            "Install or enable the Google app and try again."
                )
            )
            return
        }

        recognizer = SpeechRecognizer
            .createSpeechRecognizer(context)
            .apply {

                setRecognitionListener(
                    object : RecognitionListener {

                        override fun onReadyForSpeech(
                            params: Bundle?
                        ) {
                            onEvent(
                                VoiceRecognitionEvent.ListeningStarted
                            )
                        }

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(
                            rmsdB: Float
                        ) = Unit

                        override fun onBufferReceived(
                            buffer: ByteArray?
                        ) = Unit

                        override fun onEndOfSpeech() {
                            onEvent(
                                VoiceRecognitionEvent.SpeechEnded
                            )
                        }

                        override fun onError(
                            error: Int
                        ) {
                            onEvent(
                                VoiceRecognitionEvent.Error(
                                    error,
                                    errorMessage(error)
                                )
                            )
                        }

                        override fun onResults(
                            results: Bundle?
                        ) {
                            val text = results
                                ?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                .orEmpty()
                                .firstOrNull()
                                .orEmpty()

                            onEvent(
                                VoiceRecognitionEvent.Final(text)
                            )
                        }

                        override fun onPartialResults(
                            partialResults: Bundle?
                        ) {
                            val text = partialResults
                                ?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                .orEmpty()
                                .firstOrNull()
                                .orEmpty()

                            if (text.isNotBlank()) {
                                onEvent(
                                    VoiceRecognitionEvent.Partial(text)
                                )
                            }
                        }

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?
                        ) = Unit
                    }
                )
            }

        val listenIntent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )

            // Selected language:
            // English -> en-IN
            // Tamil   -> ta-IN
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                speechLocale
            )

            // Prefer the requested locale for the recognition session.
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                speechLocale
            )
        }

        recognizer?.startListening(listenIntent)
    }

    override fun stopListening() {
        // Stopping asks the recognizer to finalize now; it still
        // delivers onResults or onError(NO_MATCH) afterwards.
        recognizer?.stopListening()
    }

    override fun destroy() {
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO ->
            "Audio recording error. Please try again."

        SpeechRecognizer.ERROR_CLIENT ->
            "Speech client error. Please try again."

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is missing."

        SpeechRecognizer.ERROR_NETWORK ->
            "Network error. Speech recognition needs internet."

        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network timeout. Check your connection and retry."

        SpeechRecognizer.ERROR_NO_MATCH ->
            "I didn't catch any speech. Please try again."

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "The recognizer is busy. Wait a moment and retry."

        SpeechRecognizer.ERROR_SERVER ->
            "Speech server error. Please try again later."

        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No speech detected. Please try again."

        else ->
            "Speech recognition failed (code $error)."
    }
}