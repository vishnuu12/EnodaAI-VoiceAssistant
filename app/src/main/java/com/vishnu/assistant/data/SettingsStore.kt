package com.vishnu.assistant.data

import android.content.Context
import android.content.SharedPreferences
import com.vishnu.assistant.core.speech.VoiceGender

/**
 * Persists the user's assistant preferences so choices survive
 * app restarts:
 *
 * - Online / Offline mode
 * - Language (English / Tamil)
 * - Voice gender (Tamil Nadu style male / female)
 * - Groq API key (LLM access in Online mode)
 * - Web search enabled
 */
class SettingsStore(
    context: Context
) {

    private val prefs: SharedPreferences =
        context.applicationContext
            .getSharedPreferences(
                "enodaai_settings",
                Context.MODE_PRIVATE
            )

    fun isOnlineMode(): Boolean =
        prefs.getBoolean(KEY_MODE_ONLINE, true)

    fun setOnlineMode(online: Boolean) {
        prefs.edit()
            .putBoolean(KEY_MODE_ONLINE, online)
            .apply()
    }

    fun isTamil(): Boolean =
        prefs.getBoolean(KEY_LANGUAGE_TAMIL, false)

    fun setTamil(tamil: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LANGUAGE_TAMIL, tamil)
            .apply()
    }

    fun getVoiceGender(): VoiceGender {

        val stored = prefs.getString(
            KEY_VOICE_GENDER,
            VoiceGender.FEMALE.name
        )

        return try {
            VoiceGender.valueOf(stored ?: VoiceGender.FEMALE.name)
        } catch (exception: Exception) {
            VoiceGender.FEMALE
        }
    }

    fun setVoiceGender(gender: VoiceGender) {
        prefs.edit()
            .putString(KEY_VOICE_GENDER, gender.name)
            .apply()
    }

    fun getGroqApiKey(): String =
        prefs.getString(KEY_GROQ_API_KEY, "")?.trim() ?: ""

    fun setGroqApiKey(key: String) {
        prefs.edit()
            .putString(KEY_GROQ_API_KEY, key.trim())
            .apply()
    }

    fun isWebSearchEnabled(): Boolean =
        prefs.getBoolean(KEY_WEB_SEARCH, true)

    fun setWebSearchEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_WEB_SEARCH, enabled)
            .apply()
    }

    companion object {

        private const val KEY_MODE_ONLINE = "mode_online"

        private const val KEY_LANGUAGE_TAMIL = "language_tamil"

        private const val KEY_VOICE_GENDER = "voice_gender"

        private const val KEY_GROQ_API_KEY = "groq_api_key"

        private const val KEY_WEB_SEARCH = "web_search_enabled"
    }
}
