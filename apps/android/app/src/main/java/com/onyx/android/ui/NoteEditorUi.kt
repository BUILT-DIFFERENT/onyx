@file:Suppress(
    "FunctionName",
    "LongMethod",
    "TooManyFunctions",
    "MagicNumber",
    "MaxLineLength",
    "CyclomaticComplexMethod",
)

package com.onyx.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import java.util.Locale
import kotlin.math.roundToInt

private const val MIN_WIDTH_FACTOR_FLOOR = 0.1f
private const val HALF_FACTOR = 2f
private const val HEX_COLOR_LENGTH_RGB = 7
private const val MIN_COLOR_CHANNEL = 0
private const val MAX_COLOR_CHANNEL = 255
private const val COLOR_ALPHA_MAX_FLOAT = 255f
private const val DEFAULT_HIGHLIGHTER_OPACITY = 0.35f
private const val DEFAULT_HIGHLIGHTER_BASE_WIDTH = 6.5f
private val HEX_COLOR_REGEX = Regex("^#([0-9A-F]{6}|[0-9A-F]{8})$")
private val NOTEWISE_CHROME = Color(0xFF20263A)
private val NOTEWISE_PILL = Color(0xFF2B3144)
private val NOTEWISE_ICON = Color(0xFFF2F5FF)
private val NOTEWISE_ICON_MUTED = Color(0xFFA9B0C5)
private val NOTEWISE_SELECTED = Color(0xFF136CC5)
private val NOTEWISE_STROKE = Color(0xFF3B435B)
private val NOTE_PAPER = Color(0xFFFDFDFD)
private val NOTE_PAPER_STROKE = Color(0xFFCBCED6)
private const val NOTEWISE_LEFT_GROUP_WIDTH_DP = 320
private const val NOTEWISE_RIGHT_GROUP_WIDTH_DP = 82
private const val EDITOR_VIEWPORT_TEST_TAG = "note-editor-viewport"

enum class ToolPanelType {
    PEN,
    HIGHLIGHTER,
    ERASER,
}

enum class EraserMode {
    SOFT,
    OBJECT,
}

data class ToolButtonVisuals(
    val label: String,
    val icon: ImageVector,
)

@Composable
internal fun NoteEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
    var activeToolPanel by rememberSaveable { mutableStateOf<ToolPanelType?>(null) }
    var isColorPickerVisible by rememberSaveable { mutableStateOf(false) }
    var colorPickerInput by rememberSaveable { mutableStateOf(toolbarState.brush.color) }
    val brush = toolbarState.brush
    val isEditingEnabled = !topBarState.isReadOnly
    val onColorSelected: (String) -> Unit = { selectedColor ->
        val targetTool =
            if (brush.tool == Tool.ERASER) {
                toolbarState.lastNonEraserTool
            } else {
                brush.tool
            }
        val normalized = normalizeHexColor(selectedColor)
        val adjustedColor =
            if (targetTool == Tool.HIGHLIGHTER) {
                applyOpacity(normalized, resolveOpacity(brush))
            } else {
                normalized
            }
        toolbarState.onBrushChange(
            brush.copy(
                tool = targetTool,
                color = adjustedColor,
            ),
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NOTEWISE_CHROME,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height((TOOLBAR_ROW_HEIGHT_DP + TOOLBAR_VERTICAL_PADDING_DP * 2).dp)
                    .padding(horizontal = TOOLBAR_HORIZONTAL_PADDING_DP.dp)
                    .padding(top = TOOLBAR_VERTICAL_PADDING_DP.dp, bottom = TOOLBAR_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        ) {
            ToolbarGroup(
                contentDescription = "Navigation controls",
                modifier = Modifier.width(NOTEWISE_LEFT_GROUP_WIDTH_DP.dp),
            ) {
                IconButton(
                    onClick = topBarState.onNavigateBack,
                    modifier = Modifier.semantics { contentDescription = "Back" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = NOTEWISE_ICON,
                    )
                }
                ToolbarDivider()
                IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "Grid" }) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = null,
                        tint = NOTEWISE_ICON,
                    )
                }
                IconButton(
                    onClick = {
                        if (isEditingEnabled) {
                            topBarState.onCreatePage()
                        }
                    },
                    enabled = isEditingEnabled,
                    modifier = Modifier.semantics { contentDescription = "Quick new page" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = if (isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "Search" }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = NOTEWISE_ICON,
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "Inbox" }) {
                    Icon(
                        imageVector = Icons.Filled.Inbox,
                        contentDescription = null,
                        tint = NOTEWISE_ICON,
                    )
                }
            }

            ToolbarGroup(
                contentDescription = "Main tools",
                modifier = Modifier.weight(1f),
            ) {
                IconButton(
                    onClick = topBarState.onUndo,
                    enabled = topBarState.canUndo && isEditingEnabled,
                    modifier = Modifier.semantics { contentDescription = "Undo" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = if (topBarState.canUndo && isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                IconButton(
                    onClick = topBarState.onRedo,
                    enabled = topBarState.canRedo && isEditingEnabled,
                    modifier = Modifier.semantics { contentDescription = "Redo" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = null,
                        tint = if (topBarState.canRedo && isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                ToolbarDivider()

                val penVisuals =
                    remember {
                        ToolButtonVisuals(
                            label = "Pen",
                            icon = Icons.Filled.Create,
                        )
                    }
                val highlighterVisuals =
                    remember {
                        ToolButtonVisuals(
                            label = "Highlighter",
                            icon = Icons.Filled.BorderColor,
                        )
                    }
                Box {
                    ToolToggleButton(
                        visuals = penVisuals,
                        isSelected = brush.tool == Tool.PEN,
                        enabled = isEditingEnabled,
                        onToggle = {
                            activeToolPanel = null
                            toolbarState.onBrushChange(
                                brush.copy(
                                    tool = Tool.PEN,
                                    color = stripAlpha(normalizeHexColor(brush.color)),
                                ),
                            )
                        },
                        onLongPress = {
                            if (isEditingEnabled) {
                                isColorPickerVisible = false
                                activeToolPanel = ToolPanelType.PEN
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = activeToolPanel == ToolPanelType.PEN,
                        onDismissRequest = { activeToolPanel = null },
                    ) {
                        ToolSettingsDialog(
                            panelType = ToolPanelType.PEN,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }
                Box {
                    ToolToggleButton(
                        visuals = highlighterVisuals,
                        isSelected = brush.tool == Tool.HIGHLIGHTER,
                        enabled = isEditingEnabled,
                        onToggle = {
                            activeToolPanel = null
                            val normalized = normalizeHexColor(brush.color)
                            val targetOpacity =
                                if (brush.tool == Tool.HIGHLIGHTER) {
                                    resolveOpacity(brush)
                                } else {
                                    DEFAULT_HIGHLIGHTER_OPACITY
                                }
                            toolbarState.onBrushChange(
                                brush.copy(
                                    tool = Tool.HIGHLIGHTER,
                                    color = applyOpacity(normalized, targetOpacity),
                                    baseWidth = maxOf(brush.baseWidth, DEFAULT_HIGHLIGHTER_BASE_WIDTH),
                                ),
                            )
                        },
                        onLongPress = {
                            if (isEditingEnabled) {
                                isColorPickerVisible = false
                                activeToolPanel = ToolPanelType.HIGHLIGHTER
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = activeToolPanel == ToolPanelType.HIGHLIGHTER,
                        onDismissRequest = { activeToolPanel = null },
                    ) {
                        ToolSettingsDialog(
                            panelType = ToolPanelType.HIGHLIGHTER,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }
                Box {
                    EraserToggleButton(
                        isSelected = brush.tool == Tool.ERASER,
                        enabled = isEditingEnabled,
                        onToggle = {
                            activeToolPanel = null
                            toolbarState.onBrushChange(
                                toggleEraser(brush, toolbarState.lastNonEraserTool),
                            )
                        },
                        onLongPress = {
                            if (isEditingEnabled) {
                                isColorPickerVisible = false
                                activeToolPanel = ToolPanelType.ERASER
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = activeToolPanel == ToolPanelType.ERASER,
                        onDismissRequest = { activeToolPanel = null },
                    ) {
                        ToolSettingsDialog(
                            panelType = ToolPanelType.ERASER,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }

                ToolbarDivider()

                Box {
                    PaletteRow(
                        selectedColor = brush.color,
                        enabled = isEditingEnabled,
                        onColorSelected = { color ->
                            activeToolPanel = null
                            onColorSelected(color)
                        },
                        onColorLongPress = { color ->
                            if (isEditingEnabled) {
                                activeToolPanel = null
                                colorPickerInput = color
                                isColorPickerVisible = true
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = isColorPickerVisible,
                        onDismissRequest = { isColorPickerVisible = false },
                    ) {
                        ColorPickerDialog(
                            initialValue = colorPickerInput,
                            currentBrush = brush,
                            onDismiss = { isColorPickerVisible = false },
                            onApply = { appliedColor ->
                                isColorPickerVisible = false
                                onColorSelected(appliedColor)
                            },
                        )
                    }
                }

                ToolbarDivider()

                IconButton(
                    onClick = topBarState.onNavigatePrevious,
                    enabled = topBarState.canNavigatePrevious,
                    modifier = Modifier.semantics { contentDescription = "Previous page" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = null,
                        tint = if (topBarState.canNavigatePrevious) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                IconButton(
                    onClick = topBarState.onNavigateNext,
                    enabled = topBarState.canNavigateNext,
                    modifier = Modifier.semantics { contentDescription = "Next page" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = if (topBarState.canNavigateNext) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                IconButton(
                    onClick = topBarState.onCreatePage,
                    enabled = isEditingEnabled,
                    modifier = Modifier.semantics { contentDescription = "New page" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = if (isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
            }

            ToolbarGroup(
                contentDescription = "Mode controls",
                modifier = Modifier.width(NOTEWISE_RIGHT_GROUP_WIDTH_DP.dp),
            ) {
                IconButton(
                    onClick = topBarState.onToggleReadOnly,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (topBarState.isReadOnly) {
                                    "Edit mode"
                                } else {
                                    "View mode"
                                }
                        },
                ) {
                    Icon(
                        imageVector =
                            if (topBarState.isReadOnly) {
                                Icons.Filled.Edit
                            } else {
                                Icons.Filled.Visibility
                            },
                        contentDescription = null,
                        tint = NOTEWISE_ICON,
                    )
                }
            }
        }
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
    val containerColor =
        if (isSelected) {
            NOTEWISE_SELECTED
        } else {
            Color.Transparent
        }
    val contentColor = NOTEWISE_ICON
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .size(TOOLBAR_TOUCH_TARGET_SIZE_DP.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = if (isSelected) 0.dp else UNSELECTED_BORDER_WIDTH_DP.dp,
                    color = NOTEWISE_STROKE,
                    shape = CircleShape,
                )
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                )
                .semantics { contentDescription = visuals.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = visuals.icon,
            contentDescription = null,
            tint = contentColor,
        )
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
    val interactionSource = remember { MutableInteractionSource() }
    val selectedColor = NOTEWISE_SELECTED
    val defaultColor = Color.Transparent
    val containerColor = if (isSelected) selectedColor else defaultColor

    Box(
        modifier =
            Modifier
                .size(ERASER_BUTTON_SIZE_DP.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = if (isSelected) 0.dp else UNSELECTED_BORDER_WIDTH_DP.dp,
                    color = NOTEWISE_STROKE,
                    shape = CircleShape,
                )
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                )
                .semantics { contentDescription = "Eraser" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoFixOff,
            contentDescription = null,
            tint = NOTEWISE_ICON,
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
    val normalizedSelected = normalizeHexColor(selectedColor).takeLast(HEX_COLOR_LENGTH_RGB - 1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
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
private fun PaletteSwatch(
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
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .semantics {
                    contentDescription = "Brush color ${paletteColorName(hexColor)}"
                },
    )
}

@Composable
private fun ToolbarGroup(
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(TOOLBAR_GROUP_CORNER_RADIUS_DP.dp),
        color = NOTEWISE_PILL,
        modifier =
            modifier
                .height(TOOLBAR_ROW_HEIGHT_DP.dp)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                },
        border = BorderStroke(1.dp, NOTEWISE_STROKE.copy(alpha = 0.7f)),
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
private fun ToolbarDivider() {
    HorizontalDivider(
        modifier =
            Modifier
                .padding(horizontal = TOOLBAR_DIVIDER_HORIZONTAL_PADDING_DP.dp)
                .width(1.dp)
                .height(TOOLBAR_DIVIDER_HEIGHT_DP.dp),
        thickness = 1.dp,
        color = NOTEWISE_STROKE.copy(alpha = 0.65f),
    )
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

            BrushSizeControl(
                brush = brush,
                enabled = true,
                onBrushChange = onBrushChange,
            )

            when (panelType) {
                ToolPanelType.PEN -> {
                    val stabilization = resolveStabilization(brush)
                    Text(text = "Stabilization", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = stabilization,
                        onValueChange = { level ->
                            onBrushChange(applyStabilization(brush, level))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                }
                ToolPanelType.HIGHLIGHTER -> {
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
                }
                ToolPanelType.ERASER -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { eraserMode = EraserMode.SOFT },
                        ) {
                            Text(if (eraserMode == EraserMode.SOFT) "Soft eraser" else "Soft")
                        }
                        TextButton(
                            onClick = { eraserMode = EraserMode.OBJECT },
                        ) {
                            Text(if (eraserMode == EraserMode.OBJECT) "Object eraser" else "Object")
                        }
                    }
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
private fun BrushSizeControl(
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

@Composable
private fun ColorPickerDialog(
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

@Composable
@Suppress("UNUSED_PARAMETER")
private fun NoteEditorContent(
    contentState: NoteEditorContentState,
    transformState: TransformableState,
    paddingValues: PaddingValues,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .onSizeChanged { size ->
                    contentState.onViewportSizeChanged(size)
                }
                .testTag(EDITOR_VIEWPORT_TEST_TAG)
                .semantics { contentDescription = "Editor viewport" },
    ) {
        if (!contentState.isPdfPage) {
            FixedPageBackground(contentState = contentState)
        }
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
                    onPanGestureEnd = contentState.onPanGestureEnd,
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

@Composable
private fun FixedPageBackground(contentState: NoteEditorContentState) {
    val pageWidth = contentState.pageWidth
    val pageHeight = contentState.pageHeight
    val transform = contentState.viewTransform

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (pageWidth <= 0f || pageHeight <= 0f) {
            return@Canvas
        }
        val (left, top) = transform.pageToScreen(0f, 0f)
        val pageWidthPx = transform.pageWidthToScreen(pageWidth)
        val pageHeightPx = transform.pageWidthToScreen(pageHeight)
        drawRect(
            color = NOTE_PAPER,
            topLeft = Offset(left, top),
            size = Size(pageWidthPx, pageHeightPx),
        )
        drawRect(
            color = NOTE_PAPER_STROKE,
            topLeft = Offset(left, top),
            size = Size(pageWidthPx, pageHeightPx),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun ReadOnlyInputBlocker() {
    androidx.compose.ui.viewinterop.AndroidView(
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

private fun resolveStabilization(brush: Brush): Float {
    val delta =
        ((brush.maxWidthFactor - brush.minWidthFactor) / HALF_FACTOR)
            .coerceAtLeast(MIN_WIDTH_FACTOR_FLOOR)
    val normalized =
        (
            (delta - PEN_STABILIZATION_DELTA_MIN) /
                (PEN_STABILIZATION_DELTA_MAX - PEN_STABILIZATION_DELTA_MIN)
        )
            .coerceIn(0f, 1f)
    return normalized
}

private fun applyStabilization(
    brush: Brush,
    level: Float,
): Brush {
    val clampedLevel = level.coerceIn(0f, 1f)
    val delta =
        (
            PEN_STABILIZATION_DELTA_MIN +
                (PEN_STABILIZATION_DELTA_MAX - PEN_STABILIZATION_DELTA_MIN) * clampedLevel
        )
            .coerceAtLeast(MIN_WIDTH_FACTOR_FLOOR)
    return brush.copy(
        minWidthFactor = (1f - delta).coerceAtLeast(MIN_WIDTH_FACTOR_FLOOR),
        maxWidthFactor = 1f + delta,
    )
}

private fun resolveOpacity(brush: Brush): Float {
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

private fun applyOpacity(
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

private fun normalizeHexColor(rawColor: String): String {
    val trimmed = rawColor.trim().uppercase(Locale.US)
    if (trimmed.isBlank()) {
        return "#000000"
    }
    val prefixed = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return when {
        HEX_COLOR_REGEX.matches(prefixed) -> prefixed
        prefixed.matches(Regex("^#[0-9A-F]{3}$")) -> {
            "#${prefixed[1]}${prefixed[1]}${prefixed[2]}${prefixed[2]}${prefixed[3]}${prefixed[3]}"
        }
        prefixed.matches(Regex("^#[0-9A-F]{4}$")) -> {
            "#${prefixed[1]}${prefixed[1]}${prefixed[2]}${prefixed[2]}${prefixed[3]}${prefixed[3]}${prefixed[4]}${prefixed[4]}"
        }
        else -> "#000000"
    }
}

private fun stripAlpha(colorHex: String): String {
    val normalized = normalizeHexColor(colorHex)
    return if (normalized.length == 9) {
        "#${normalized.takeLast(6)}"
    } else {
        normalized
    }
}

private fun toggleEraser(
    brush: Brush,
    lastNonEraserTool: Tool,
): Brush =
    if (brush.tool == Tool.ERASER) {
        brush.copy(tool = lastNonEraserTool)
    } else {
        brush.copy(tool = Tool.ERASER)
    }

private fun paletteColorName(hexColor: String): String {
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
