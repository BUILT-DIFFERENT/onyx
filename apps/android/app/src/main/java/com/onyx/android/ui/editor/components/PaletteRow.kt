@file:Suppress("FunctionName", "MagicNumber")

package com.onyx.android.ui.editor.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PaletteRow(
    selectedColor: String,
    enabled: Boolean,
    onColorSelected: (String) -> Unit,
    onColorLongPress: (String) -> Unit,
) {
    val normalizedSelected = normalizeHexColor(selectedColor).takeLast(HEX_COLOR_LENGTH_RGB - 1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        DEFAULT_PALETTE.forEach { color ->
            val normalizedPaletteColor = normalizeHexColor(color).takeLast(HEX_COLOR_LENGTH_RGB - 1)
            PaletteSwatch(
                hexColor = color,
                isSelected = normalizedSelected == normalizedPaletteColor,
                enabled = enabled,
                onClick = { onColorSelected(color) },
                onLongPress = { onColorLongPress(color) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PaletteSwatch(
    hexColor: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colorInt =
        runCatching { android.graphics.Color.parseColor(normalizeHexColor(hexColor)) }
            .getOrDefault(android.graphics.Color.BLACK)
    val color = Color(colorInt)
    val borderWidth = if (isSelected) SELECTED_BORDER_WIDTH_DP.dp else UNSELECTED_BORDER_WIDTH_DP.dp
    val borderColor =
        if (isSelected) {
            NOTEWISE_SELECTED
        } else {
            NOTEWISE_STROKE.copy(alpha = UNSELECTED_BORDER_ALPHA)
        }
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier =
            Modifier
                .size(PALETTE_SWATCH_SIZE_DP.dp)
                .clip(CircleShape)
                .background(color)
                .border(width = borderWidth, color = borderColor, shape = CircleShape)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                    onLongClick = onLongPress,
                ).semantics {
                    contentDescription = "Brush color ${paletteColorName(hexColor)}"
                },
    )
}
