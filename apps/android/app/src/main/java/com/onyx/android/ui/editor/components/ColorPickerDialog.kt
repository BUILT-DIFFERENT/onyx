@file:Suppress("FunctionName", "LongMethod", "MagicNumber")

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool

@Composable
internal fun ColorPickerDialog(
    initialValue: String,
    currentBrush: Brush,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var hexInput by remember(initialValue) { mutableStateOf(normalizeHexColor(initialValue)) }
    val normalizedColor = normalizeHexColor(hexInput)
    val supportsApply = HEX_COLOR_REGEX.matches(normalizedColor)
    val previewColor =
        runCatching {
            Color(android.graphics.Color.parseColor(normalizedColor))
        }.getOrDefault(Color.Black)

    Card(
        modifier =
            Modifier
                .widthIn(min = TOOL_SETTINGS_PANEL_MIN_WIDTH_DP.dp)
                .padding(TOOL_SETTINGS_PANEL_OFFSET_DP.dp),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = TOOL_SETTINGS_PANEL_HORIZONTAL_PADDING_DP.dp,
                    vertical = TOOL_SETTINGS_PANEL_VERTICAL_PADDING_DP.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(TOOL_SETTINGS_PANEL_ITEM_SPACING_DP.dp),
        ) {
            Text(
                text = "Custom brush color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = { candidate ->
                    hexInput = candidate.trim()
                },
                label = { Text("Hex color") },
                singleLine = true,
                modifier = Modifier.width(COLOR_PICKER_TEXT_FIELD_WIDTH_DP.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Preview", style = MaterialTheme.typography.bodyMedium)
                Box(
                    modifier =
                        Modifier
                            .size(PALETTE_SWATCH_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(previewColor),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val normalized = normalizeHexColor(hexInput)
                        val finalColor =
                            if (currentBrush.tool == Tool.HIGHLIGHTER) {
                                applyOpacity(normalized, resolveOpacity(currentBrush))
                            } else {
                                normalized
                            }
                        onApply(finalColor)
                    },
                    enabled = supportsApply,
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
