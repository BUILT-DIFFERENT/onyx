@file:Suppress("LongMethod", "TooManyFunctions", "MaxLineLength", "MagicNumber", "FunctionName")

package com.onyx.android.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.BrushPreset
import com.onyx.android.ink.model.StrokeLineStyle
import com.onyx.android.ink.model.Tool
import java.util.Locale
import kotlin.math.roundToInt

@Composable
@Suppress("LongParameterList")
internal fun ToolSettingsPanel(
    panelType: ToolPanelType,
    brush: Brush,
    isSegmentEraserEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onBrushChange: (Brush) -> Unit,
    onSegmentEraserEnabledChange: (Boolean) -> Unit = {},
) {
    val title =
        when (panelType) {
            ToolPanelType.PEN -> "Pen settings"
            ToolPanelType.HIGHLIGHTER -> "Highlighter settings"
            ToolPanelType.ERASER -> "Eraser options"
            ToolPanelType.LASSO -> "Lasso tool"
        }

    Card(
        modifier =
            Modifier
                .widthIn(min = TOOL_SETTINGS_PANEL_MIN_WIDTH_DP.dp)
                .padding(TOOL_SETTINGS_PANEL_OFFSET_DP.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        horizontal = TOOL_SETTINGS_PANEL_HORIZONTAL_PADDING_DP.dp,
                        vertical = TOOL_SETTINGS_PANEL_VERTICAL_PADDING_DP.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(TOOL_SETTINGS_PANEL_ITEM_SPACING_DP.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            when (panelType) {
                ToolPanelType.PEN -> {
                    BrushPresetRow(
                        presets = BrushPreset.DEFAULT_PRESETS.filter { preset -> preset.tool == Tool.PEN },
                        activeBrush = brush,
                        onPresetSelected = { preset ->
                            onBrushChange(
                                brush.copy(
                                    tool = Tool.PEN,
                                    color = preset.color,
                                    baseWidth = preset.baseWidth,
                                    smoothingLevel = preset.smoothingLevel,
                                    endTaperStrength = preset.endTaperStrength,
                                    minWidthFactor = preset.minWidthFactor,
                                    maxWidthFactor = preset.maxWidthFactor,
                                ),
                            )
                        },
                    )
                    BrushSizeControl(
                        brush = brush,
                        enabled = true,
                        onBrushChange = onBrushChange,
                    )
                    val pressureSensitivity = resolvePressureSensitivity(brush)
                    Text(text = "Pressure sensitivity", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = pressureSensitivity,
                        onValueChange = { sensitivity ->
                            onBrushChange(applyPressureSensitivity(brush, sensitivity))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    val smoothing = resolveSmoothingLevel(brush)
                    Text(text = "Stabilization", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = smoothing,
                        onValueChange = { level ->
                            onBrushChange(applySmoothingLevel(brush, level))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    val taperStrength = resolveEndTaperStrength(brush)
                    Text(text = "End taper", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = taperStrength,
                        onValueChange = { strength ->
                            onBrushChange(applyEndTaperStrength(brush, strength))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    LineStyleSelector(
                        selectedStyle = brush.lineStyle,
                        onStyleSelected = { lineStyle ->
                            onBrushChange(brush.copy(lineStyle = lineStyle))
                        },
                    )
                }

                ToolPanelType.HIGHLIGHTER -> {
                    BrushPresetRow(
                        presets = BrushPreset.DEFAULT_PRESETS.filter { preset -> preset.tool == Tool.HIGHLIGHTER },
                        activeBrush = brush,
                        onPresetSelected = { preset ->
                            onBrushChange(
                                brush.copy(
                                    tool = Tool.HIGHLIGHTER,
                                    color = applyOpacity(preset.color, resolveOpacity(brush)),
                                    baseWidth = preset.baseWidth,
                                    smoothingLevel = preset.smoothingLevel,
                                    endTaperStrength = preset.endTaperStrength,
                                    minWidthFactor = preset.minWidthFactor,
                                    maxWidthFactor = preset.maxWidthFactor,
                                ),
                            )
                        },
                    )
                    BrushSizeControl(
                        brush = brush,
                        enabled = true,
                        onBrushChange = onBrushChange,
                    )
                    val opacity = resolveOpacity(brush)
                    Text(text = "Opacity", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = opacity,
                        onValueChange = { value ->
                            onBrushChange(
                                brush.copy(
                                    color = applyOpacity(brush.color, value),
                                ),
                            )
                        },
                        valueRange = HIGHLIGHTER_OPACITY_MIN..HIGHLIGHTER_OPACITY_MAX,
                    )
                    val pressureSensitivity = resolvePressureSensitivity(brush)
                    Text(text = "Pressure sensitivity", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = pressureSensitivity,
                        onValueChange = { sensitivity ->
                            onBrushChange(applyPressureSensitivity(brush, sensitivity))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    val smoothing = resolveSmoothingLevel(brush)
                    Text(text = "Stabilization", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = smoothing,
                        onValueChange = { level ->
                            onBrushChange(applySmoothingLevel(brush, level))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    val taperStrength = resolveEndTaperStrength(brush)
                    Text(text = "End taper", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = taperStrength,
                        onValueChange = { strength ->
                            onBrushChange(applyEndTaperStrength(brush, strength))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    LineStyleSelector(
                        selectedStyle = brush.lineStyle,
                        onStyleSelected = { lineStyle ->
                            onBrushChange(brush.copy(lineStyle = lineStyle))
                        },
                    )
                }

                ToolPanelType.ERASER -> {
                    Text(text = "Stroke eraser", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Erases whole strokes on touch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BrushSizeControl(
                        brush = brush,
                        enabled = true,
                        label = "Eraser size",
                        onBrushChange = onBrushChange,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "Segment eraser", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Split strokes where the eraser path intersects.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isSegmentEraserEnabled,
                            onCheckedChange = onSegmentEraserEnabledChange,
                        )
                    }
                }

                ToolPanelType.LASSO -> {
                    Text(text = "Lasso selection", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Draw around strokes to select them. Drag to move selected strokes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun LineStyleSelector(
    selectedStyle: StrokeLineStyle,
    onStyleSelected: (StrokeLineStyle) -> Unit,
) {
    Text(text = "Line style", style = MaterialTheme.typography.bodyMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
    ) {
        StrokeLineStyle.entries.forEach { lineStyle ->
            TextButton(onClick = { onStyleSelected(lineStyle) }) {
                Text(
                    text = lineStyle.name.lowercase().replaceFirstChar { it.uppercase() },
                    color =
                        if (lineStyle == selectedStyle) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

@Composable
private fun BrushSizeControl(
    brush: Brush,
    enabled: Boolean,
    label: String = "Brush size",
    onBrushChange: (Brush) -> Unit,
) {
    val normalizedValue =
        ((brush.baseWidth - BRUSH_SIZE_MIN) / (BRUSH_SIZE_MAX - BRUSH_SIZE_MIN))
            .coerceIn(0f, 1f)
    val indicatorSize =
        (brush.baseWidth * BRUSH_SIZE_INDICATOR_SCALE)
            .coerceIn(BRUSH_SIZE_INDICATOR_MIN_DP, BRUSH_SIZE_INDICATOR_MAX_DP)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(BRUSH_SIZE_SLIDER_WIDTH_DP.dp),
        )
        Box(
            modifier =
                Modifier
                    .size(indicatorSize.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
        )
    }
    Slider(
        value = normalizedValue,
        onValueChange = { ratio ->
            val baseWidth =
                BRUSH_SIZE_MIN + (BRUSH_SIZE_MAX - BRUSH_SIZE_MIN) * ratio.coerceIn(0f, 1f)
            onBrushChange(brush.copy(baseWidth = baseWidth))
        },
        enabled = enabled,
        valueRange = 0f..1f,
        steps = BRUSH_SIZE_STEPS,
    )
}

@Composable
private fun BrushPresetRow(
    presets: List<BrushPreset>,
    activeBrush: Brush,
    onPresetSelected: (BrushPreset) -> Unit,
) {
    if (presets.isEmpty()) {
        return
    }
    Text(text = "Presets", style = MaterialTheme.typography.bodyMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
    ) {
        presets.forEach { preset ->
            val active = stripAlpha(activeBrush.color).equals(stripAlpha(preset.color), ignoreCase = true)
            TextButton(
                onClick = { onPresetSelected(preset) },
            ) {
                Text(
                    text = preset.name,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

internal fun resolveSmoothingLevel(brush: Brush): Float = brush.smoothingLevel.coerceIn(0f, 1f)

internal fun applySmoothingLevel(
    brush: Brush,
    level: Float,
): Brush = brush.copy(smoothingLevel = level.coerceIn(0f, 1f))

internal fun resolvePressureSensitivity(brush: Brush): Float {
    val spread = (brush.maxWidthFactor - brush.minWidthFactor).coerceIn(PRESSURE_SPREAD_MIN, PRESSURE_SPREAD_MAX)
    return ((spread - PRESSURE_SPREAD_MIN) / (PRESSURE_SPREAD_MAX - PRESSURE_SPREAD_MIN)).coerceIn(0f, 1f)
}

internal fun applyPressureSensitivity(
    brush: Brush,
    sensitivity: Float,
): Brush {
    val normalized = sensitivity.coerceIn(0f, 1f)
    val spread = PRESSURE_SPREAD_MIN + (PRESSURE_SPREAD_MAX - PRESSURE_SPREAD_MIN) * normalized
    val halfSpread = spread / 2f
    val minFactor = (PRESSURE_BASELINE - halfSpread).coerceAtLeast(PRESSURE_MIN_WIDTH_FACTOR_FLOOR)
    val maxFactor = (PRESSURE_BASELINE + halfSpread).coerceAtMost(PRESSURE_MAX_WIDTH_FACTOR_CEILING)
    return brush.copy(
        minWidthFactor = minFactor,
        maxWidthFactor = maxFactor,
    )
}

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
            "#${prefixed[1]}${prefixed[1]}${prefixed[2]}${prefixed[2]}${prefixed[3]}${prefixed[3]}${prefixed[4]}${prefixed[4]}"
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

private const val PRESSURE_BASELINE = 1f
private const val PRESSURE_SPREAD_MIN = 0.2f
private const val PRESSURE_SPREAD_MAX = 1.2f
private const val PRESSURE_MIN_WIDTH_FACTOR_FLOOR = 0.1f
private const val PRESSURE_MAX_WIDTH_FACTOR_CEILING = 2.2f

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
