package com.vishnu.assistant.core.speech

/**
 * The user's preferred voice gender.
 *
 * Tamil Nadu style voices: the app tries to find a real
 * male/female voice on the device's TTS engine; when the
 * engine exposes only one voice for a language, pitch and
 * speech rate are adjusted so the difference is still
 * clearly audible.
 */
enum class VoiceGender(val displayName: String) {
    FEMALE("Female"),
    MALE("Male")
}
