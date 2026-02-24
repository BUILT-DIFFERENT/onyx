package com.onyx.android.recognition

import android.content.Context
import android.content.SharedPreferences

enum class RecognitionMode(
    val storageValue: String,
    val label: String,
) {
    OFF(storageValue = "off", label = "Off"),
    SEARCH_ONLY(storageValue = "search_only", label = "Search-only"),
    LIVE_CONVERT(storageValue = "live_convert", label = "Live convert"),
    ;

    companion object {
        fun fromStorageValue(value: String?): RecognitionMode {
            val matched = entries.firstOrNull { mode -> mode.storageValue == value }
            return matched ?: LIVE_CONVERT
        }
    }
}

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

    fun getRecognitionMode(): RecognitionMode =
        RecognitionMode.fromStorageValue(prefs.getString(KEY_RECOGNITION_MODE, DEFAULT_RECOGNITION_MODE))

    fun setRecognitionMode(mode: RecognitionMode) {
        prefs.edit().putString(KEY_RECOGNITION_MODE, mode.storageValue).apply()
    }

    fun isShapeBeautificationEnabled(): Boolean = prefs.getBoolean(KEY_SHAPE_BEAUTIFICATION_ENABLED, false)

    fun setShapeBeautificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAPE_BEAUTIFICATION_ENABLED, enabled).apply()
    }

    fun getMathRecognitionMode(): MathRecognitionMode =
        MathRecognitionMode.fromStorageValue(prefs.getString(KEY_MATH_RECOGNITION_MODE, DEFAULT_MATH_RECOGNITION_MODE))

    fun setMathRecognitionMode(mode: MathRecognitionMode) {
        prefs.edit().putString(KEY_MATH_RECOGNITION_MODE, mode.storageValue).apply()
    }

    companion object {
        private const val PREFS_NAME = "onyx_recognition_settings"
        private const val KEY_LANGUAGE = "recognition_language"
        private const val KEY_DEBOUNCE_MS = "recognition_debounce_ms"
        private const val KEY_RECOGNITION_MODE = "recognition_mode"
        private const val KEY_SHAPE_BEAUTIFICATION_ENABLED = "shape_beautification_enabled"
        private const val KEY_MATH_RECOGNITION_MODE = "math_recognition_mode"

        const val DEFAULT_LANGUAGE = "en_CA"
        const val DEFAULT_DEBOUNCE_MS = 300L
        private const val DEFAULT_RECOGNITION_MODE = "live_convert"
        private const val DEFAULT_MATH_RECOGNITION_MODE = "off"

        val SUPPORTED_LANGUAGES =
            listOf(
                "en_CA" to "English (Canada)",
                "en_US" to "English (US)",
                "en_GB" to "English (UK)",
            )
    }
}
