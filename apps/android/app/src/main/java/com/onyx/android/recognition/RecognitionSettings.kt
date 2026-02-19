package com.onyx.android.recognition

import android.content.Context
import android.content.SharedPreferences

class RecognitionSettings(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun getRecognitionLanguage(): String = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun setRecognitionLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getRecognitionDebounceMs(): Long = prefs.getLong(KEY_DEBOUNCE_MS, DEFAULT_DEBOUNCE_MS)

    fun setRecognitionDebounceMs(debounceMs: Long) {
        prefs.edit().putLong(KEY_DEBOUNCE_MS, debounceMs).apply()
    }

    companion object {
        private const val PREFS_NAME = "onyx_recognition_settings"
        private const val KEY_LANGUAGE = "recognition_language"
        private const val KEY_DEBOUNCE_MS = "recognition_debounce_ms"

        const val DEFAULT_LANGUAGE = "en_CA"
        const val DEFAULT_DEBOUNCE_MS = 300L

        val SUPPORTED_LANGUAGES =
            listOf(
                "en_CA" to "English (Canada)",
                "en_US" to "English (US)",
                "en_GB" to "English (UK)",
            )
    }
}
