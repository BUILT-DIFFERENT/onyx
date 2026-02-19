@file:Suppress("MagicNumber")

package com.onyx.android.ui.editor.components

import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import java.util.Locale
import kotlin.math.roundToInt

internal fun normalizeHexColor(rawColor: String): String {
    val trimmed = rawColor.trim().uppercase(Locale.US)
    if (trimmed.isBlank()) {
        return "#000000"
    }
    val prefixed = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return when {
        HEX_COLOR_REGEX.matches(prefixed) -> {
            prefixed
        }

        prefixed.matches(Regex("^#[0-9A-F]{3}$")) -> {
            "#${prefixed[1]}${prefixed[1]}${prefixed[2]}${prefixed[2]}${prefixed[3]}${prefixed[3]}"
        }

        prefixed.matches(Regex("^#[0-9A-F]{4}$")) -> {
            "#${prefixed[1]}${prefixed[1]}" +
                "${prefixed[2]}${prefixed[2]}" +
                "${prefixed[3]}${prefixed[3]}" +
                "${prefixed[4]}${prefixed[4]}"
        }

        else -> {
            "#000000"
        }
    }
}

internal fun stripAlpha(colorHex: String): String {
    val normalized = normalizeHexColor(colorHex)
    return if (normalized.length == 9) {
        "#${normalized.takeLast(6)}"
    } else {
        normalized
    }
}

internal fun toggleEraser(
    brush: Brush,
    lastNonEraserTool: Tool,
): Brush =
    if (brush.tool == Tool.ERASER) {
        brush.copy(tool = lastNonEraserTool)
    } else {
        brush.copy(tool = Tool.ERASER)
    }

internal fun paletteColorName(hexColor: String): String {
    val normalized = normalizeHexColor(hexColor).takeLast(HEX_COLOR_LENGTH_RGB - 1)
    return when (normalized) {
        "111111" -> "black"
        "1E88E5" -> "blue"
        "E53935" -> "red"
        "43A047" -> "green"
        "8E24AA" -> "purple"
        else -> "custom"
    }
}

internal fun resolveSmoothingLevel(brush: Brush): Float = brush.smoothingLevel.coerceIn(0f, 1f)

internal fun applySmoothingLevel(
    brush: Brush,
    level: Float,
): Brush = brush.copy(smoothingLevel = level.coerceIn(0f, 1f))

internal fun resolveEndTaperStrength(brush: Brush): Float = brush.endTaperStrength.coerceIn(0f, 1f)

internal fun applyEndTaperStrength(
    brush: Brush,
    strength: Float,
): Brush = brush.copy(endTaperStrength = strength.coerceIn(0f, 1f))

internal fun resolveOpacity(brush: Brush): Float {
    val parsedColor =
        runCatching {
            android.graphics.Color.parseColor(normalizeHexColor(brush.color))
        }.getOrNull()
    val alpha =
        if (parsedColor != null) {
            android.graphics.Color.alpha(parsedColor) / COLOR_ALPHA_MAX_FLOAT
        } else {
            HIGHLIGHTER_OPACITY_MAX
        }
    return alpha.coerceIn(HIGHLIGHTER_OPACITY_MIN, HIGHLIGHTER_OPACITY_MAX)
}

internal fun applyOpacity(
    colorHex: String,
    opacity: Float,
): String {
    val normalizedColor = normalizeHexColor(colorHex)
    val parsedColor =
        runCatching {
            android.graphics.Color.parseColor(normalizedColor)
        }.getOrNull() ?: return normalizedColor
    val alpha =
        (opacity.coerceIn(0f, 1f) * COLOR_ALPHA_MAX_FLOAT)
            .roundToInt()
            .coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)
    return String.format(
        Locale.US,
        "#%02X%02X%02X%02X",
        alpha,
        android.graphics.Color.red(parsedColor),
        android.graphics.Color.green(parsedColor),
        android.graphics.Color.blue(parsedColor),
    )
}
