package com.onyx.android.config

import android.content.Context
import android.content.SharedPreferences

class QuickColorPaletteStore private constructor(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getPalette(): List<String> {
        val stored = prefs.getString(PALETTE_KEY, null)?.trim().orEmpty()
        if (stored.isBlank()) {
            return DEFAULT_PALETTE
        }
        val parsed =
            stored
                .split(PALETTE_SEPARATOR)
                .map { token -> token.trim().uppercase() }
                .filter { token -> HEX_COLOR_REGEX.matches(token) }
                .distinct()
        return if (parsed.size >= DEFAULT_PALETTE.size) {
            parsed.take(DEFAULT_PALETTE.size)
        } else {
            parsed + DEFAULT_PALETTE.drop(parsed.size)
        }
    }

    fun savePalette(colors: List<String>) {
        val normalized =
            colors
                .map { color -> color.trim().uppercase() }
                .map { color -> if (color.startsWith("#")) color else "#$color" }
                .filter { color -> HEX_COLOR_REGEX.matches(color) }
                .take(DEFAULT_PALETTE.size)
        if (normalized.isEmpty()) {
            prefs.edit().remove(PALETTE_KEY).apply()
            return
        }
        prefs.edit().putString(PALETTE_KEY, normalized.joinToString(PALETTE_SEPARATOR)).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "onyx_editor_quick_colors"
        private const val PALETTE_KEY = "quick_palette"
        private const val PALETTE_SEPARATOR = ","
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-F]{6}$")
        val DEFAULT_PALETTE =
            listOf(
                "#111111",
                "#1E88E5",
                "#E53935",
                "#43A047",
                "#8E24AA",
            )

        @Volatile
        private var instance: QuickColorPaletteStore? = null

        fun getInstance(context: Context): QuickColorPaletteStore =
            instance
                ?: synchronized(this) {
                    instance ?: QuickColorPaletteStore(context).also { instance = it }
                }
    }
}
