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
        return runCatching { json.decodeFromString<List<BrushPreset>>(encoded).map(::normalizePreset) }
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

private fun normalizePreset(preset: BrushPreset): BrushPreset {
    val normalizedRanges =
        when (preset.tool) {
            com.onyx.android.ink.model.Tool.HIGHLIGHTER ->
                preset.copy(
                    minWidthFactor = preset.minWidthFactor.coerceAtLeast(HIGHLIGHTER_MIN_WIDTH_FACTOR_FLOOR),
                    maxWidthFactor = preset.maxWidthFactor.coerceAtLeast(HIGHLIGHTER_MAX_WIDTH_FACTOR_FLOOR),
                    smoothingLevel = preset.smoothingLevel.coerceAtLeast(DEFAULT_SMOOTHING_LEVEL),
                )

            else ->
                preset.copy(
                    minWidthFactor = preset.minWidthFactor.coerceAtMost(PEN_MIN_WIDTH_FACTOR_CEILING),
                    maxWidthFactor = preset.maxWidthFactor.coerceAtLeast(PEN_MAX_WIDTH_FACTOR_FLOOR),
                    smoothingLevel = preset.smoothingLevel.coerceAtLeast(DEFAULT_SMOOTHING_LEVEL),
                    endTaperStrength = preset.endTaperStrength.coerceAtLeast(DEFAULT_END_TAPER_STRENGTH),
                )
        }
    return if (normalizedRanges.id == "fountain") {
        normalizedRanges.copy(nibRotation = true)
    } else {
        normalizedRanges
    }
}

private const val HIGHLIGHTER_MIN_WIDTH_FACTOR_FLOOR = 0.7f
private const val HIGHLIGHTER_MAX_WIDTH_FACTOR_FLOOR = 1.3f
private const val PEN_MIN_WIDTH_FACTOR_CEILING = 0.3f
private const val PEN_MAX_WIDTH_FACTOR_FLOOR = 2.5f
private const val DEFAULT_SMOOTHING_LEVEL = 0.5f
private const val DEFAULT_END_TAPER_STRENGTH = 0.6f
