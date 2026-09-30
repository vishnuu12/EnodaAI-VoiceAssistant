package com.vishnu.assistant.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps Android's on-device TextToSpeech engine.
 *
 * - No permissions required; works offline once voice data is
 *   present on the device.
 * - Initialization is asynchronous: anything spoken before it
 *   completes is buffered and spoken when ready.
 * - Supports English (en-IN) and Tamil (ta-IN).
 * - Supports the Tamil Nadu style MALE / FEMALE voice choice:
 *   a real gendered voice is used when the engine exposes one,
 *   otherwise pitch/rate are tuned so the two genders sound
 *   clearly different.
 * - If the requested language is unavailable, falls back to
 *   Indian English and then US English.
 * - Completion callbacks arrive on a binder thread; the ViewModel's
 *   StateFlow handles updates from any thread safely.
 */
class AndroidSpeaker(context: Context) : Speaker {

    private var tts: TextToSpeech? = null

    private val ready = AtomicBoolean(false)

    private val utteranceCounter = AtomicInteger(0)

    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    @Volatile
    private var voiceGender: VoiceGender = VoiceGender.FEMALE

    // Text that arrived before the engine finished initializing.
    private var pendingText: String? = null
    private var pendingSpeechLocale: String? = null
    private var pendingOnDone: (() -> Unit)? = null

    override val isAvailable: Boolean
        get() = ready.get()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->

            if (status == TextToSpeech.SUCCESS) {

                val engine = tts

                if (engine != null) {

                    configureEngine(engine)

                    ready.set(true)

                    // Flush anything spoken while the engine
                    // was still initializing.
                    val text = pendingText
                    val speechLocale = pendingSpeechLocale
                    val onDone = pendingOnDone

                    pendingText = null
                    pendingSpeechLocale = null
                    pendingOnDone = null

                    if (text != null) {
                        speak(
                            text = text,
                            speechLocale = speechLocale ?: "en-IN",
                            onDone = onDone ?: {}
                        )
                    }
                }
            }
        }
    }

    override fun setVoiceGender(gender: VoiceGender) {
        voiceGender = gender
    }

    /**
     * Configure the TTS engine and register the progress listener.
     *
     * The actual language is selected inside speak(), because the
     * language can change between conversations.
     */
    private fun configureEngine(engine: TextToSpeech) {

        // Set a safe default language.
        // Individual utterances can change this later.
        setLanguageWithFallback(engine, "en-IN")

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    utteranceId
                        ?.let { callbacks.remove(it) }
                        ?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    // Treat failure like completion so the caller's
                    // state machine always settles.
                    utteranceId
                        ?.let { callbacks.remove(it) }
                        ?.invoke()
                }
            }
        )
    }

    /**
     * Speak using the requested locale and the selected gender.
     *
     * English:
     *     en-IN -> en-US
     *
     * Tamil:
     *     ta-IN -> en-IN -> en-US
     *
     * Any unsupported/unknown locale:
     *     en-IN -> en-US
     */
    override fun speak(
        text: String,
        speechLocale: String,
        onDone: () -> Unit
    ) {

        if (!ready.get()) {

            pendingText = text
            pendingSpeechLocale = speechLocale
            pendingOnDone = onDone

            return
        }

        val engine = tts

        if (engine == null) {
            onDone()
            return
        }

        // Select the requested language.
        setLanguageWithFallback(
            engine = engine,
            requestedLocale = speechLocale
        )

        // Then select the best voice for the chosen gender.
        applyGenderedVoice(
            engine = engine,
            speechLocale = speechLocale
        )

        val id = "enodaai-${utteranceCounter.incrementAndGet()}"

        callbacks[id] = onDone

        // QUEUE_FLUSH:
        // A new utterance immediately replaces any speech currently
        // being spoken. This preserves EnodaAI's barge-in behavior.
        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            id
        )
    }

    /**
     * Picks a male/female voice for the current language.
     *
     * Strategy:
     * 1. If the engine exposes voices whose names carry gender
     *    hints ("female" / "male"), use the matching one.
     * 2. Some engines expose multiple voices per locale without
     *    gender hints - then the higher-pitched voice is treated
     *    as female and the deeper one as male.
     * 3. Single-voice engines: simulate the gender with pitch and
     *    rate, which keeps an audible male/female difference for
     *    the Tamil Nadu style voices on every device.
     */
    private fun applyGenderedVoice(
        engine: TextToSpeech,
        speechLocale: String
    ) {

        val locale = localeFromString(speechLocale)

        val voices: List<Voice> = try {
            engine.voices.orEmpty().filter {
                sameLanguage(it.locale, locale)
            }
        } catch (exception: Exception) {
            emptyList()
        }

        val gender = voiceGender

        // ---------------------------------------------------------
        // 1. Explicit gender hints in the voice name.
        //    ("female" is checked first: it also contains "male")
        // ---------------------------------------------------------

        val hinted = when (gender) {
            VoiceGender.FEMALE -> voices.firstOrNull {
                it.name.contains("female", ignoreCase = true) ||
                    it.name.contains("woman", ignoreCase = true)
            }

            VoiceGender.MALE -> voices.firstOrNull {
                it.name.contains("male", ignoreCase = true) &&
                    !it.name.contains("female", ignoreCase = true) ||
                    it.name.contains("man", ignoreCase = true) &&
                    !it.name.contains("woman", ignoreCase = true)
            }
        }

        if (hinted != null) {
            try {
                engine.voice = hinted
            } catch (_: Exception) {
                // Keep the default voice; pitch still applies.
            }

            engine.setSpeechRate(1.0f)

            // Light polish so hinted voices still sound distinct.
            engine.setPitch(
                if (gender == VoiceGender.MALE) 0.9f else 1.0f
            )

            return
        }

        // ---------------------------------------------------------
        // 2. Multiple voices, no gender hints.
        // ---------------------------------------------------------

        if (voices.size > 1) {

            val chosen = when (gender) {
                VoiceGender.FEMALE -> voices.first()
                VoiceGender.MALE -> voices.last()
            }

            try {
                engine.voice = chosen
            } catch (_: Exception) {
                // Ignore; fall through to pitch simulation.
            }

            engine.setSpeechRate(1.0f)
            engine.setPitch(
                if (gender == VoiceGender.MALE) 0.85f else 1.05f
            )

            return
        }

        // ---------------------------------------------------------
        // 3. Single voice: simulate gender with pitch.
        // ---------------------------------------------------------

        // Reset any previously selected voice so pitch works
        // against the language default.
        try {
            engine.voice = voices.firstOrNull()
        } catch (_: Exception) {
            // Ignore.
        }

        when (gender) {

            VoiceGender.FEMALE -> {
                engine.setPitch(1.18f)
                engine.setSpeechRate(1.0f)
            }

            VoiceGender.MALE -> {
                engine.setPitch(0.72f)
                engine.setSpeechRate(0.95f)
            }
        }
    }

    private fun sameLanguage(
        voiceLocale: Locale,
        requested: Locale
    ): Boolean {

        return voiceLocale.language.equals(
            requested.language,
            ignoreCase = true
        )
    }

    /**
     * Selects the language with a graceful fallback chain.
     */
    private fun setLanguageWithFallback(
        engine: TextToSpeech,
        requestedLocale: String
    ) {

        val requested = localeFromString(requestedLocale)

        if (isLanguageSupported(engine, requested)) {
            engine.language = requested
            return
        }

        // Fallback 1: Indian English
        val indianEnglish = Locale("en", "IN")

        if (isLanguageSupported(engine, indianEnglish)) {
            engine.language = indianEnglish
            return
        }

        // Fallback 2: US English
        val usEnglish = Locale.US

        if (isLanguageSupported(engine, usEnglish)) {
            engine.language = usEnglish
        }
    }

    /**
     * Checks whether the TTS engine can actually speak the locale.
     */
    private fun isLanguageSupported(
        engine: TextToSpeech,
        locale: Locale
    ): Boolean {

        val result = engine.isLanguageAvailable(locale)

        return result == TextToSpeech.LANG_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    /**
     * Converts Android locale strings such as:
     *
     * "en-IN" -> Locale("en", "IN")
     * "ta-IN" -> Locale("ta", "IN")
     */
    private fun localeFromString(
        speechLocale: String
    ): Locale {

        val parts = speechLocale.split("-")

        return when {
            parts.size >= 2 -> {
                Locale(
                    parts[0],
                    parts[1]
                )
            }

            parts.size == 1 -> {
                Locale(parts[0])
            }

            else -> {
                Locale.ENGLISH
            }
        }
    }

    override fun stop() {

        pendingText = null
        pendingSpeechLocale = null
        pendingOnDone = null

        tts?.stop()
    }

    override fun destroy() {

        stop()

        callbacks.clear()

        tts?.shutdown()
        tts = null

        ready.set(false)
    }
}
