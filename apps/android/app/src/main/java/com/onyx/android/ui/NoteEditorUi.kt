@file:Suppress("FunctionName", "LongMethod", "TooManyFunctions")

package com.onyx.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGesturesPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun NoteEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
) {
    Scaffold(
        topBar = {
            NoteEditorTopBar(
                topBarState = topBarState,
                toolbarState = toolbarState,
            )
        },
    ) { paddingValues ->
        NoteEditorContent(
            contentState = contentState,
            transformState = transformState,
            paddingValues = paddingValues,
        )
    }
}

@Composable
private fun NoteEditorTopBar(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
) {
    val pageNumber = if (topBarState.totalPages == 0) 0 else topBarState.currentPageIndex + 1
    val viewModeActionLabel = if (topBarState.isReadOnly) "Edit mode" else "View mode"
    val resolvedTitle = topBarState.noteTitle.ifBlank { "Untitled Note" }
    val brush = toolbarState.brush
    val isEraserSelected = brush.tool == Tool.ERASER
    var activeToolPanel by rememberSaveable { mutableStateOf<ToolPanelType?>(null) }
    var isColorPickerVisible by rememberSaveable { mutableStateOf(false) }
    var colorPickerInput by rememberSaveable { mutableStateOf(brush.color) }
    Surface(
        tonalElevation = 2.dp,
        modifier =
            Modifier.semantics {
                contentDescription = "Editor toolbar"
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        horizontal = TOOLBAR_HORIZONTAL_PADDING_DP.dp,
                        vertical = TOOLBAR_VERTICAL_PADDING_DP.dp,
                    ),
            horizontalArrangement = Arrangement.spacedBy(TOOLBAR_TOP_ROW_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarGroup(contentDescription = "Back and page title") {
                IconButton(
                    onClick = topBarState.onNavigateBack,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Back"
                        },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
                ToolbarDivider()
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(min = 108.dp, max = 220.dp),
                )
            }

            ToolbarGroup(contentDescription = "Tools and colors") {
                ToolToggleButton(
                    visuals = ToolButtonVisuals("Pen", Icons.Default.Edit),
                    isSelected = brush.tool == Tool.PEN,
                    enabled = !topBarState.isReadOnly,
                    onToggle = {
                        toolbarState.onBrushChange(brush.copy(tool = Tool.PEN))
                    },
                    onLongPress = {
                        activeToolPanel = ToolPanelType.PEN
                    },
                )
                ToolToggleButton(
                    visuals = ToolButtonVisuals("Highlighter", Icons.Default.Draw),
                    isSelected = brush.tool == Tool.HIGHLIGHTER,
                    enabled = !topBarState.isReadOnly,
                    onToggle = {
                        toolbarState.onBrushChange(brush.copy(tool = Tool.HIGHLIGHTER))
                    },
                    onLongPress = {
                        activeToolPanel = ToolPanelType.HIGHLIGHTER
                    },
                )
                ToolbarDivider()
                PaletteRow(
                    selectedColor = brush.color,
                    enabled = !topBarState.isReadOnly,
                    onColorSelected = { selectedColor ->
                        toolbarState.onBrushChange(
                            brush.copy(
                                color =
                                    if (brush.tool == Tool.HIGHLIGHTER) {
                                        applyOpacity(
                                            selectedColor,
                                            brush.resolveOpacity(),
                                        )
                                    } else {
                                        normalizeHexColor(selectedColor) ?: selectedColor
                                    },
                            ),
                        )
                    },
                    onColorLongPress = { selectedColor ->
                        colorPickerInput = selectedColor
                        isColorPickerVisible = true
                    },
                )
                ToolbarDivider()
                EraserToggleButton(
                    isSelected = isEraserSelected,
                    enabled = !topBarState.isReadOnly,
                    onToggle = {
                        toolbarState.onBrushChange(
                            brush.toggleEraser(toolbarState.lastNonEraserTool),
                        )
                    },
                    onLongPress = {
                        activeToolPanel = ToolPanelType.ERASER
                    },
                )
                ToolbarDivider()
                BrushSizeControl(
                    brush = brush,
                    enabled = !topBarState.isReadOnly,
                    onBrushChange = toolbarState.onBrushChange,
                )
            }

            ToolbarGroup(contentDescription = "Editor actions") {
                IconButton(
                    onClick = topBarState.onNavigatePrevious,
                    enabled = topBarState.canNavigatePrevious,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous page",
                    )
                }
                IconButton(
                    onClick = topBarState.onNavigateNext,
                    enabled = topBarState.canNavigateNext,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next page",
                    )
                }
                IconButton(onClick = topBarState.onCreatePage) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New page",
                    )
                }
                Text(
                    text = "$pageNumber/${topBarState.totalPages}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = topBarState.onUndo, enabled = topBarState.canUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                    )
                }
                IconButton(onClick = topBarState.onRedo, enabled = topBarState.canRedo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                    )
                }
                TextButton(
                    onClick = topBarState.onToggleReadOnly,
                    colors =
                        ButtonDefaults.textButtonColors(
                            containerColor =
                                if (topBarState.isReadOnly) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                        ),
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (topBarState.isReadOnly) {
                                    "Edit mode"
                                } else {
                                    "View mode"
                                }
                            selected = topBarState.isReadOnly
                            role = Role.Button
                        },
                ) {
                    Text(text = viewModeActionLabel)
                }
            }
        }
    }
    activeToolPanel?.let { panelType ->
        ToolSettingsDialog(
            panelType = panelType,
            brush = brush,
            onDismiss = { activeToolPanel = null },
            onBrushChange = toolbarState.onBrushChange,
        )
    }
    if (isColorPickerVisible) {
        ColorPickerDialog(
            initialValue = colorPickerInput,
            currentBrush = brush,
            onDismiss = { isColorPickerVisible = false },
            onApply = { selectedColor ->
                toolbarState.onBrushChange(brush.copy(color = selectedColor))
                isColorPickerVisible = false
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolToggleButton(
    visuals: ToolButtonVisuals,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier =
            Modifier.semantics {
                contentDescription = visuals.label
                selected = isSelected
                role = Role.Button
            }
                .size(TOOLBAR_TOUCH_TARGET_SIZE_DP.dp)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(ERASER_BUTTON_SIZE_DP.dp),
            shape = CircleShape,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = ERASER_SELECTED_ALPHA)
                } else {
                    Color.Transparent
                },
            border =
                if (isSelected) {
                    BorderStroke(SELECTED_BORDER_WIDTH_DP.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(
                        UNSELECTED_BORDER_WIDTH_DP.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = UNSELECTED_BORDER_ALPHA,
                        ),
                    )
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun ToolbarGroup(
    contentDescription: String,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(TOOLBAR_GROUP_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier =
            Modifier.semantics {
                this.contentDescription = contentDescription
            },
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = TOOLBAR_GROUP_HORIZONTAL_PADDING_DP.dp,
                    vertical = TOOLBAR_GROUP_VERTICAL_PADDING_DP.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun PaletteRow(
    selectedColor: String,
    enabled: Boolean,
    onColorSelected: (String) -> Unit,
    onColorLongPress: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DEFAULT_PALETTE.forEach { hexColor ->
            PaletteSwatch(
                hexColor = hexColor,
                isSelected = selectedColor == hexColor,
                enabled = enabled,
                onClick = { onColorSelected(hexColor) },
                onLongPress = { onColorLongPress(hexColor) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaletteSwatch(
    hexColor: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val swatchColor = Color(android.graphics.Color.parseColor(hexColor))
    val colorName = paletteColorName(hexColor)
    Box(
        modifier =
            Modifier.semantics {
                contentDescription = "Brush color $colorName"
                selected = isSelected
                role = Role.Button
            }
                .size(TOOLBAR_TOUCH_TARGET_SIZE_DP.dp)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(PALETTE_SWATCH_SIZE_DP.dp),
            color = swatchColor,
            shape = CircleShape,
            border =
                if (isSelected) {
                    BorderStroke(
                        SELECTED_BORDER_WIDTH_DP.dp,
                        Color.White,
                    )
                } else {
                    BorderStroke(
                        UNSELECTED_BORDER_WIDTH_DP.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = UNSELECTED_BORDER_ALPHA,
                        ),
                    )
                },
        ) {
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EraserToggleButton(
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier =
            Modifier.semantics {
                contentDescription = "Eraser"
                selected = isSelected
                role = Role.Button
            }
                .size(TOOLBAR_TOUCH_TARGET_SIZE_DP.dp)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(ERASER_BUTTON_SIZE_DP.dp),
            shape = CircleShape,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = ERASER_SELECTED_ALPHA)
                } else {
                    Color.Transparent
                },
            border =
                if (isSelected) {
                    BorderStroke(SELECTED_BORDER_WIDTH_DP.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(
                        UNSELECTED_BORDER_WIDTH_DP.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = UNSELECTED_BORDER_ALPHA,
                        ),
                    )
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(
        modifier =
            Modifier
                .height(TOOLBAR_DIVIDER_HEIGHT_DP.dp)
                .padding(horizontal = TOOLBAR_DIVIDER_HORIZONTAL_PADDING_DP.dp),
    )
}

@Composable
private fun BrushSizeControl(
    brush: Brush,
    enabled: Boolean,
    onBrushChange: (Brush) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
    ) {
        val indicatorSize =
            (brush.baseWidth * BRUSH_SIZE_INDICATOR_SCALE)
                .coerceIn(BRUSH_SIZE_INDICATOR_MIN_DP, BRUSH_SIZE_INDICATOR_MAX_DP)
        Surface(
            modifier = Modifier.size(indicatorSize.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface,
        ) {
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Size",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                modifier =
                    Modifier
                        .width(BRUSH_SIZE_SLIDER_WIDTH_DP.dp)
                        .semantics {
                            contentDescription = "Brush size"
                        },
                value = brush.baseWidth,
                onValueChange = { newSize ->
                    onBrushChange(brush.copy(baseWidth = newSize))
                },
                valueRange = BRUSH_SIZE_MIN..BRUSH_SIZE_MAX,
                steps = BRUSH_SIZE_STEPS,
                enabled = enabled,
            )
        }
        Text(
            modifier = Modifier.widthIn(min = BRUSH_SIZE_VALUE_MIN_WIDTH_DP.dp),
            text = brush.baseWidth.roundToInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolSettingsDialog(
    panelType: ToolPanelType,
    brush: Brush,
    onDismiss: () -> Unit,
    onBrushChange: (Brush) -> Unit,
) {
    var eraserMode by rememberSaveable { mutableStateOf(EraserMode.SOFT) }
    val title =
        when (panelType) {
            ToolPanelType.PEN -> "Pen settings"
            ToolPanelType.HIGHLIGHTER -> "Highlighter settings"
            ToolPanelType.ERASER -> "Eraser options"
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.widthIn(min = TOOL_SETTINGS_DIALOG_MIN_WIDTH_DP.dp),
                verticalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
            ) {
                when (panelType) {
                    ToolPanelType.PEN -> {
                        Text(
                            text = "Thickness ${brush.baseWidth.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = brush.baseWidth,
                            onValueChange = { newSize ->
                                onBrushChange(brush.copy(baseWidth = newSize, tool = Tool.PEN))
                            },
                            valueRange = BRUSH_SIZE_MIN..BRUSH_SIZE_MAX,
                            steps = BRUSH_SIZE_STEPS,
                        )
                        val stabilization = brush.resolveStabilization()
                        Text(
                            text = "Stabilization ${(stabilization * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = stabilization,
                            onValueChange = { newStabilization ->
                                onBrushChange(
                                    brush
                                        .copy(tool = Tool.PEN)
                                        .applyStabilization(newStabilization),
                                )
                            },
                            valueRange = 0f..1f,
                            steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                        )
                    }

                    ToolPanelType.HIGHLIGHTER -> {
                        Text(
                            text = "Thickness ${brush.baseWidth.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = brush.baseWidth,
                            onValueChange = { newSize ->
                                onBrushChange(
                                    brush.copy(
                                        baseWidth = newSize,
                                        tool = Tool.HIGHLIGHTER,
                                    ),
                                )
                            },
                            valueRange = BRUSH_SIZE_MIN..BRUSH_SIZE_MAX,
                            steps = BRUSH_SIZE_STEPS,
                        )
                        val opacity = brush.resolveOpacity()
                        Text(
                            text = "Opacity ${(opacity * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = opacity,
                            onValueChange = { newOpacity ->
                                onBrushChange(
                                    brush.copy(
                                        tool = Tool.HIGHLIGHTER,
                                        color = applyOpacity(brush.color, newOpacity),
                                    ),
                                )
                            },
                            valueRange = HIGHLIGHTER_OPACITY_MIN..HIGHLIGHTER_OPACITY_MAX,
                            steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                        )
                    }

                    ToolPanelType.ERASER -> {
                        Text(
                            text = "Mode",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
                        ) {
                            TextButton(
                                onClick = { eraserMode = EraserMode.SOFT },
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        containerColor =
                                            if (eraserMode == EraserMode.SOFT) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                    ),
                            ) {
                                Text(text = "Stroke")
                            }
                            TextButton(
                                onClick = { eraserMode = EraserMode.OBJECT },
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        containerColor =
                                            if (eraserMode == EraserMode.OBJECT) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                    ),
                            ) {
                                Text(text = "Object")
                            }
                        }
                        Text(
                            text = "Placeholder options only. Eraser behavior is unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun ColorPickerDialog(
    initialValue: String,
    currentBrush: Brush,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var hexInput by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val normalizedColor = normalizeHexColor(hexInput)
    val previewColor = normalizedColor?.let { Color(android.graphics.Color.parseColor(it)) }
    val supportsApply = normalizedColor != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Custom brush color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp)) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { updatedValue ->
                        hexInput = updatedValue.trim()
                    },
                    modifier = Modifier.width(COLOR_PICKER_TEXT_FIELD_WIDTH_DP.dp),
                    singleLine = true,
                    label = { Text("Hex color") },
                    supportingText = { Text("Use #RRGGBB or #AARRGGBB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = !supportsApply && hexInput.isNotBlank(),
                )
                if (previewColor != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(PALETTE_SWATCH_SIZE_DP.dp),
                            shape = CircleShape,
                            color = previewColor,
                            border =
                                BorderStroke(
                                    UNSELECTED_BORDER_WIDTH_DP.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = UNSELECTED_BORDER_ALPHA,
                                    ),
                                ),
                        ) {
                        }
                        Text(
                            text = normalizedColor,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                enabled = supportsApply,
                onClick = {
                    val colorValue =
                        normalizedColor?.let { validColor ->
                            if (currentBrush.tool == Tool.HIGHLIGHTER && validColor.length == HEX_COLOR_LENGTH_RGB) {
                                applyOpacity(validColor, currentBrush.resolveOpacity())
                            } else {
                                validColor
                            }
                        } ?: return@TextButton
                    onApply(colorValue)
                },
            ) {
                Text("Apply")
            }
        },
    )
}

@Composable
private fun NoteEditorContent(
    contentState: NoteEditorContentState,
    transformState: TransformableState,
    paddingValues: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .systemGesturesPadding(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .transformable(
                            state = transformState,
                            enabled = false,
                        ),
            ) {
                if (contentState.isPdfPage) {
                    PdfPageContent(contentState)
                } else {
                    val inkCanvasState =
                        InkCanvasState(
                            strokes = contentState.strokes,
                            viewTransform = contentState.viewTransform,
                            brush = contentState.brush,
                        )
                    val inkCanvasCallbacks =
                        InkCanvasCallbacks(
                            onStrokeFinished =
                                if (contentState.isReadOnly) {
                                    {}
                                } else {
                                    contentState.onStrokeFinished
                                },
                            onStrokeErased =
                                if (contentState.isReadOnly) {
                                    {}
                                } else {
                                    contentState.onStrokeErased
                                },
                            onTransformGesture = contentState.onTransformGesture,
                        )
                    InkCanvas(
                        state = inkCanvasState,
                        callbacks = inkCanvasCallbacks,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (contentState.isReadOnly) {
                    ReadOnlyInputBlocker()
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyInputBlocker() {
    AndroidView(
        factory = { context ->
            android.view.View(context).apply {
                setOnTouchListener { _, _ -> true }
            }
        },
        modifier =
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "View mode active. Editing disabled."
                },
    )
}

private enum class ToolPanelType {
    PEN,
    HIGHLIGHTER,
    ERASER,
}

private enum class EraserMode {
    SOFT,
    OBJECT,
}

private data class ToolButtonVisuals(
    val label: String,
    val icon: ImageVector,
)

private fun Brush.resolveStabilization(): Float {
    val delta =
        ((maxWidthFactor - minWidthFactor) / HALF_FACTOR)
            .coerceIn(PEN_STABILIZATION_DELTA_MIN, PEN_STABILIZATION_DELTA_MAX)
    val normalized =
        (PEN_STABILIZATION_DELTA_MAX - delta) /
            (PEN_STABILIZATION_DELTA_MAX - PEN_STABILIZATION_DELTA_MIN)
    return normalized.coerceIn(0f, 1f)
}

private fun Brush.applyStabilization(level: Float): Brush {
    val clampedLevel = level.coerceIn(0f, 1f)
    val delta =
        PEN_STABILIZATION_DELTA_MAX -
            ((PEN_STABILIZATION_DELTA_MAX - PEN_STABILIZATION_DELTA_MIN) * clampedLevel)
    return copy(
        minWidthFactor = (1f - delta).coerceAtLeast(MIN_WIDTH_FACTOR_FLOOR),
        maxWidthFactor = 1f + delta,
    )
}

private fun Brush.resolveOpacity(): Float =
    runCatching {
        val parsedColor = android.graphics.Color.parseColor(color)
        android.graphics.Color.alpha(parsedColor) / COLOR_ALPHA_MAX_FLOAT
    }.getOrDefault(1f)

private fun applyOpacity(
    colorHex: String,
    opacity: Float,
): String {
    val normalizedColor = normalizeHexColor(colorHex)
    val parsedColor =
        normalizedColor?.let { validHex ->
            runCatching {
                android.graphics.Color.parseColor(validHex)
            }.getOrNull()
        }
    return if (parsedColor == null) {
        colorHex
    } else {
        val alpha =
            (opacity.coerceIn(0f, 1f) * COLOR_ALPHA_MAX_FLOAT)
                .roundToInt()
                .coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)
        String.format(
            Locale.US,
            "#%02X%02X%02X%02X",
            alpha,
            android.graphics.Color.red(parsedColor),
            android.graphics.Color.green(parsedColor),
            android.graphics.Color.blue(parsedColor),
        )
    }
}

private fun normalizeHexColor(rawColor: String): String? {
    val trimmed = rawColor.trim().uppercase(Locale.US)
    val prefixed =
        if (trimmed.startsWith("#")) {
            trimmed
        } else {
            "#$trimmed"
        }
    return if (prefixed.matches(HEX_COLOR_REGEX)) prefixed else null
}

private fun Brush.toggleEraser(lastNonEraserTool: Tool): Brush =
    if (tool == Tool.ERASER) {
        copy(tool = lastNonEraserTool)
    } else {
        copy(tool = Tool.ERASER)
    }

private fun paletteColorName(hexColor: String): String =
    when (hexColor.uppercase()) {
        "#111111" -> "black"
        "#1E88E5" -> "blue"
        "#E53935" -> "red"
        "#43A047" -> "green"
        "#8E24AA" -> "purple"
        else -> "custom"
    }

private val HEX_COLOR_REGEX = Regex("^#([0-9A-F]{6}|[0-9A-F]{8})$")
private const val HEX_COLOR_LENGTH_RGB = 7
private const val HALF_FACTOR = 2f
private const val MIN_WIDTH_FACTOR_FLOOR = 0.1f
private const val COLOR_ALPHA_MAX_FLOAT = 255f
private const val MIN_COLOR_CHANNEL = 0
private const val MAX_COLOR_CHANNEL = 255
