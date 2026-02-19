@file:Suppress("FunctionName", "MagicNumber", "LongMethod")

package com.onyx.android.ui.editor.components

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.Brush

@Composable
internal fun ToolSettingsDialog(
    panelType: ToolPanelType,
    brush: Brush,
    onDismiss: () -> Unit,
    onBrushChange: (Brush) -> Unit,
) {
    val title =
        when (panelType) {
            ToolPanelType.PEN -> "Pen settings"
            ToolPanelType.HIGHLIGHTER -> "Highlighter settings"
            ToolPanelType.ERASER -> "Eraser options"
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
                    BrushSizeControl(
                        brush = brush,
                        enabled = true,
                        onBrushChange = onBrushChange,
                    )
                    val smoothing = resolveSmoothingLevel(brush)
                    Text(text = "Smoothing", style = MaterialTheme.typography.bodyMedium)
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
                }

                ToolPanelType.HIGHLIGHTER -> {
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
                    val smoothing = resolveSmoothingLevel(brush)
                    Text(text = "Smoothing", style = MaterialTheme.typography.bodyMedium)
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
                }

                ToolPanelType.ERASER -> {
                    Text(text = "Stroke eraser", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Erases entire strokes until segment eraser support is added.",
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
internal fun BrushSizeControl(
    brush: Brush,
    enabled: Boolean,
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
            text = "Brush size",
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
