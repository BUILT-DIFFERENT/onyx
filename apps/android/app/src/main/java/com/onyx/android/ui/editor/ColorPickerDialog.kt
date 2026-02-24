@file:Suppress("LongMethod", "FunctionName")

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onyx.android.config.QuickColorPaletteStore
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_RGB_INPUT_LENGTH = 3
private const val RGB_TEXT_FIELD_WIDTH_DP = 70

@Composable
internal fun ColorPickerDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val initial = normalizeHexColor(initialValue)
    val initialRgb = parseRgb(initial)
    var hexInput by remember(initial) { mutableStateOf(stripAlpha(initial)) }
    var red by remember(initial) { mutableIntStateOf(initialRgb.first) }
    var green by remember(initial) { mutableIntStateOf(initialRgb.second) }
    var blue by remember(initial) { mutableIntStateOf(initialRgb.third) }
    var redInput by remember(initial) { mutableStateOf(red.toString()) }
    var greenInput by remember(initial) { mutableStateOf(green.toString()) }
    var blueInput by remember(initial) { mutableStateOf(blue.toString()) }

    val normalizedColor = normalizeHexColor(hexInput)
    val supportsApply = HEX_COLOR_REGEX.matches(normalizedColor)
    val previewColor =
        runCatching { Color(android.graphics.Color.parseColor(normalizedColor)) }
            .getOrDefault(Color.Black)

    fun updateFromRgb(
        updatedRed: Int = red,
        updatedGreen: Int = green,
        updatedBlue: Int = blue,
    ) {
        red = updatedRed.coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)
        green = updatedGreen.coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)
        blue = updatedBlue.coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)
        redInput = red.toString()
        greenInput = green.toString()
        blueInput = blue.toString()
        hexInput =
            String.format(
                Locale.US,
                "#%02X%02X%02X",
                red,
                green,
                blue,
            )
    }

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
                text = "Advanced brush color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
            OutlinedTextField(
                value = hexInput,
                onValueChange = { candidate ->
                    val trimmed = candidate.trim()
                    hexInput = trimmed
                    val parsed = parseRgb(trimmed)
                    red = parsed.first
                    green = parsed.second
                    blue = parsed.third
                    redInput = red.toString()
                    greenInput = green.toString()
                    blueInput = blue.toString()
                },
                label = { Text("HEX") },
                singleLine = true,
                modifier = Modifier.width(COLOR_PICKER_TEXT_FIELD_WIDTH_DP.dp),
            )
            Text(text = "Spectrum (RGB)", style = MaterialTheme.typography.bodyMedium)
            ColorSlider(
                label = "R",
                value = red,
                textValue = redInput,
                onTextValueChange = { value ->
                    redInput = value.filter { it.isDigit() }.take(MAX_RGB_INPUT_LENGTH)
                    redInput.toIntOrNull()?.let { parsed ->
                        updateFromRgb(updatedRed = parsed)
                    }
                },
                onSliderChange = { value -> updateFromRgb(updatedRed = value.roundToInt()) },
            )
            ColorSlider(
                label = "G",
                value = green,
                textValue = greenInput,
                onTextValueChange = { value ->
                    greenInput = value.filter { it.isDigit() }.take(MAX_RGB_INPUT_LENGTH)
                    greenInput.toIntOrNull()?.let { parsed ->
                        updateFromRgb(updatedGreen = parsed)
                    }
                },
                onSliderChange = { value -> updateFromRgb(updatedGreen = value.roundToInt()) },
            )
            ColorSlider(
                label = "B",
                value = blue,
                textValue = blueInput,
                onTextValueChange = { value ->
                    blueInput = value.filter { it.isDigit() }.take(MAX_RGB_INPUT_LENGTH)
                    blueInput.toIntOrNull()?.let { parsed ->
                        updateFromRgb(updatedBlue = parsed)
                    }
                },
                onSliderChange = { value -> updateFromRgb(updatedBlue = value.roundToInt()) },
            )
            Text(text = "Swatches", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickColorPaletteStore.DEFAULT_PALETTE.forEach { swatch ->
                    PaletteSwatch(
                        hexColor = swatch,
                        isSelected = stripAlpha(normalizedColor).equals(swatch, ignoreCase = true),
                        enabled = true,
                        onClick = {
                            val parsed = parseRgb(swatch)
                            updateFromRgb(parsed.first, parsed.second, parsed.third)
                        },
                        onLongPress = {},
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onApply(stripAlpha(normalizeHexColor(hexInput))) },
                    enabled = supportsApply,
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Int,
    textValue: String,
    onTextValueChange: (String) -> Unit,
    onSliderChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.width(18.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = onSliderChange,
            valueRange = MIN_COLOR_CHANNEL.toFloat()..MAX_COLOR_CHANNEL.toFloat(),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = onTextValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(RGB_TEXT_FIELD_WIDTH_DP.dp),
        )
    }
}

private fun parseRgb(color: String): Triple<Int, Int, Int> {
    val parsed =
        runCatching { android.graphics.Color.parseColor(normalizeHexColor(color)) }
            .getOrNull()
            ?: return Triple(0, 0, 0)
    return Triple(
        android.graphics.Color.red(parsed),
        android.graphics.Color.green(parsed),
        android.graphics.Color.blue(parsed),
    )
}
