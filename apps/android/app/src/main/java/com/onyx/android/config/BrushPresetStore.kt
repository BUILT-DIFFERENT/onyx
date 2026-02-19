package com.onyx.android.config

import android.content.Context
import android.content.SharedPreferences
import com.onyx.android.ink.model.BrushPreset
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BrushPresetStore private constructor(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun getPresets(): List<BrushPreset> {
        val encoded = prefs.getString(PRESETS_KEY, null) ?: return BrushPreset.DEFAULT_PRESETS
        return runCatching { json.decodeFromString<List<BrushPreset>>(encoded) }
            .getOrDefault(BrushPreset.DEFAULT_PRESETS)
    }

    fun savePreset(preset: BrushPreset) {
        val updated =
            getPresets()
                .toMutableList()
                .apply {
                    val existingIndex = indexOfFirst { it.id == preset.id }
                    if (existingIndex >= 0) {
                        set(existingIndex, preset)
                    } else {
                        add(preset)
                    }
                }
        prefs.edit().putString(PRESETS_KEY, json.encodeToString(updated)).apply()
    }

    fun resetToDefaults() {
        prefs.edit().putString(PRESETS_KEY, json.encodeToString(BrushPreset.DEFAULT_PRESETS)).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "onyx_brush_presets"
        private const val PRESETS_KEY = "brush_presets"

        @Volatile
        private var instance: BrushPresetStore? = null

        fun getInstance(context: Context): BrushPresetStore =
            instance
                ?: synchronized(this) {
                    instance ?: BrushPresetStore(context).also { instance = it }
                }
    }
}
