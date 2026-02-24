@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "FunctionName",
)

package com.onyx.android.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Loupe
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.android.config.QuickColorPaletteStore
import com.onyx.android.ink.model.Tool
import com.onyx.android.input.DoubleFingerMode
import com.onyx.android.input.DoubleTapZoomAction
import com.onyx.android.input.DoubleTapZoomPointerMode
import com.onyx.android.input.InputSettings
import com.onyx.android.input.MultiFingerTapAction
import com.onyx.android.input.SingleFingerMode
import com.onyx.android.input.StylusButtonAction
import com.onyx.android.objects.model.InsertAction
import com.onyx.android.ui.NoteEditorToolbarState
import com.onyx.android.ui.NoteEditorTopBarState
import com.onyx.android.ui.PageTemplateState
import com.onyx.android.ui.spacingRangeForTemplate

internal const val HEX_COLOR_LENGTH_RGB = 7
internal const val MIN_COLOR_CHANNEL = 0
internal const val MAX_COLOR_CHANNEL = 255
internal const val COLOR_ALPHA_MAX_FLOAT = 255f
internal const val DEFAULT_HIGHLIGHTER_OPACITY = 0.35f
internal const val DEFAULT_HIGHLIGHTER_BASE_WIDTH = 6.5f
internal val HEX_COLOR_REGEX = Regex("^#([0-9A-F]{6}|[0-9A-F]{8})$")
private val NOTEWISE_CHROME = Color(0xFF20263A)
private val NOTEWISE_PILL = Color(0xFF2B3144)
private val NOTEWISE_ICON = Color(0xFFF2F5FF)
private val NOTEWISE_ICON_MUTED = Color(0xFFA9B0C5)
private val NOTEWISE_SELECTED = Color(0xFF136CC5)
private val NOTEWISE_STROKE = Color(0xFF3B435B)
private const val NOTEWISE_LEFT_GROUP_WIDTH_DP = 360
private const val NOTEWISE_RIGHT_GROUP_WIDTH_DP = 128
private const val TITLE_INPUT_TEST_TAG = "note-title-input"
internal const val TOOLBAR_ROW_HEIGHT_DP = 48
internal const val TOOLBAR_VERTICAL_PADDING_DP = 8
internal const val TOOLBAR_HORIZONTAL_PADDING_DP = 12
internal const val TOOLBAR_ITEM_SPACING_DP = 4
internal const val TOOLBAR_GROUP_CORNER_RADIUS_DP = 8
internal const val TOOLBAR_GROUP_HORIZONTAL_PADDING_DP = 8
internal const val TOOLBAR_GROUP_VERTICAL_PADDING_DP = 6
internal const val TOOLBAR_DIVIDER_HORIZONTAL_PADDING_DP = 4
internal const val TOOLBAR_DIVIDER_HEIGHT_DP = 20
internal const val TOOLBAR_TOUCH_TARGET_SIZE_DP = 48
internal const val ERASER_BUTTON_SIZE_DP = 48
internal const val PALETTE_SWATCH_SIZE_DP = 24
internal const val SELECTED_BORDER_WIDTH_DP = 2
internal const val UNSELECTED_BORDER_WIDTH_DP = 1
internal const val UNSELECTED_BORDER_ALPHA = 0.5f
internal const val TOOL_SETTINGS_PANEL_MIN_WIDTH_DP = 280
internal const val TOOL_SETTINGS_PANEL_OFFSET_DP = 8
internal const val TOOL_SETTINGS_PANEL_HORIZONTAL_PADDING_DP = 16
internal const val TOOL_SETTINGS_PANEL_VERTICAL_PADDING_DP = 12
internal const val TOOL_SETTINGS_PANEL_ITEM_SPACING_DP = 8
internal const val TOOL_SETTINGS_DIALOG_SLIDER_STEPS = 4
internal const val BRUSH_SIZE_MIN = 0.5f
internal const val BRUSH_SIZE_MAX = 20f
internal const val BRUSH_SIZE_STEPS = 10
internal const val BRUSH_SIZE_INDICATOR_SCALE = 2f
internal const val BRUSH_SIZE_INDICATOR_MIN_DP = 4f
internal const val BRUSH_SIZE_INDICATOR_MAX_DP = 24f
internal const val BRUSH_SIZE_SLIDER_WIDTH_DP = 80
internal const val HIGHLIGHTER_OPACITY_MIN = 0.1f
internal const val HIGHLIGHTER_OPACITY_MAX = 0.6f
internal const val COLOR_PICKER_TEXT_FIELD_WIDTH_DP = 180
internal const val EDGE_GLOW_ALPHA_MAX = 0.8f

internal enum class ToolPanelType {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
}

private data class ToolButtonVisuals(
    val label: String,
    val icon: ImageVector,
)

@Composable
internal fun EditorToolbar(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
) {
    val context = LocalContext.current
    val quickColorStore = remember(context) { QuickColorPaletteStore.getInstance(context) }
    var activeToolPanel by rememberSaveable { mutableStateOf<ToolPanelType?>(null) }
    var isColorPickerVisible by rememberSaveable { mutableStateOf(false) }
    var colorPickerInput by rememberSaveable { mutableStateOf(toolbarState.brush.color) }
    var colorPickerTargetSlotIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var favoritePalette by rememberSaveable { mutableStateOf(quickColorStore.getPalette()) }
    var isTitleEditing by rememberSaveable { mutableStateOf(false) }
    var titleDraft by rememberSaveable { mutableStateOf(topBarState.noteTitle) }
    var isOverflowMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isInsertMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isPageJumpDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pageJumpInput by rememberSaveable { mutableStateOf("") }
    var isInputSettingsDialogVisible by rememberSaveable { mutableStateOf(false) }
    var inputSettingsDraft by remember { mutableStateOf(toolbarState.inputSettings) }
    val focusManager = LocalFocusManager.current
    val brush = toolbarState.brush
    val selectedTool =
        if (toolbarState.isStylusButtonEraserActive) {
            Tool.ERASER
        } else {
            brush.tool
        }
    val isEditingEnabled = !topBarState.isReadOnly
    val currentPageNumber =
        if (topBarState.totalPages > 0) {
            (topBarState.currentPageIndex + 1).coerceIn(1, topBarState.totalPages)
        } else {
            0
        }
    val pageCounterDescription = "Page $currentPageNumber of ${topBarState.totalPages}"
    val commitTitleEdit: () -> Unit = {
        topBarState.onUpdateTitle(titleDraft)
        isTitleEditing = false
        focusManager.clearFocus()
    }
    LaunchedEffect(topBarState.noteTitle, isTitleEditing) {
        if (!isTitleEditing) {
            titleDraft = topBarState.noteTitle
        }
    }
    LaunchedEffect(isEditingEnabled) {
        if (!isEditingEnabled) {
            isTitleEditing = false
        }
    }
    LaunchedEffect(toolbarState.inputSettings) {
        inputSettingsDraft = toolbarState.inputSettings
    }
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
    val saveFavoritePalette: (List<String>) -> Unit = { updatedPalette ->
        favoritePalette = updatedPalette
        quickColorStore.savePalette(updatedPalette)
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
                NoteTitleEditor(
                    noteTitle = topBarState.noteTitle,
                    titleDraft = titleDraft,
                    isEditing = isTitleEditing,
                    isEditingEnabled = isEditingEnabled,
                    onTitleDraftChange = { titleDraft = it },
                    onStartEditing = { isTitleEditing = true },
                    onCommit = commitTitleEdit,
                )
                Box {
                    IconButton(
                        onClick = { isOverflowMenuExpanded = true },
                        modifier = Modifier.semantics { contentDescription = "More actions" },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = null,
                            tint = NOTEWISE_ICON,
                        )
                    }
                    DropdownMenu(
                        expanded = isOverflowMenuExpanded,
                        onDismissRequest = { isOverflowMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename note") },
                            enabled = isEditingEnabled,
                            onClick = {
                                isOverflowMenuExpanded = false
                                if (isEditingEnabled) {
                                    isTitleEditing = true
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("New page") },
                            enabled = isEditingEnabled,
                            onClick = {
                                isOverflowMenuExpanded = false
                                if (isEditingEnabled) {
                                    topBarState.onCreatePage()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Page manager") },
                            enabled = isEditingEnabled && topBarState.totalPages > 0,
                            modifier = Modifier.testTag("open-page-manager"),
                            onClick = {
                                isOverflowMenuExpanded = false
                                if (isEditingEnabled && topBarState.totalPages > 0) {
                                    topBarState.onOpenPageManager()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Input settings") },
                            enabled = isEditingEnabled,
                            modifier = Modifier.testTag("open-input-settings"),
                            onClick = {
                                isOverflowMenuExpanded = false
                                inputSettingsDraft = toolbarState.inputSettings
                                isInputSettingsDialogVisible = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (topBarState.isReadOnly) {
                                        "Switch to edit mode"
                                    } else {
                                        "Switch to view mode"
                                    },
                                )
                            },
                            onClick = {
                                isOverflowMenuExpanded = false
                                topBarState.onToggleReadOnly()
                            },
                        )
                    }
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
                        isSelected = selectedTool == Tool.PEN,
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
                        ToolSettingsPanel(
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
                        isSelected = selectedTool == Tool.HIGHLIGHTER,
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
                        ToolSettingsPanel(
                            panelType = ToolPanelType.HIGHLIGHTER,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }
                Box {
                    EraserToggleButton(
                        isSelected = selectedTool == Tool.ERASER,
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
                        ToolSettingsPanel(
                            panelType = ToolPanelType.ERASER,
                            brush = brush,
                            isSegmentEraserEnabled = toolbarState.isSegmentEraserEnabled,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                            onSegmentEraserEnabledChange = toolbarState.onSegmentEraserEnabledChange,
                        )
                    }
                }
                Box {
                    LassoToggleButton(
                        isSelected = selectedTool == Tool.LASSO,
                        enabled = isEditingEnabled,
                        onToggle = {
                            activeToolPanel = null
                            toolbarState.onBrushChange(brush.copy(tool = Tool.LASSO))
                        },
                        onLongPress = {
                            if (isEditingEnabled) {
                                isColorPickerVisible = false
                                activeToolPanel = ToolPanelType.LASSO
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = activeToolPanel == ToolPanelType.LASSO,
                        onDismissRequest = { activeToolPanel = null },
                    ) {
                        ToolSettingsPanel(
                            panelType = ToolPanelType.LASSO,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }
                Box {
                    TemplateButton(
                        templateState = toolbarState.templateState,
                        enabled = isEditingEnabled,
                        onTemplateChange = toolbarState.onTemplateChange,
                    )
                }

                ToolbarDivider()

                Box {
                    PaletteRow(
                        favoritePalette = favoritePalette,
                        selectedColor = brush.color,
                        enabled = isEditingEnabled,
                        onColorSelected = { color ->
                            activeToolPanel = null
                            onColorSelected(color)
                        },
                        onColorLongPress = { slotIndex, color ->
                            if (isEditingEnabled) {
                                activeToolPanel = null
                                colorPickerInput = color
                                colorPickerTargetSlotIndex = slotIndex
                                isColorPickerVisible = true
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = isColorPickerVisible,
                        onDismissRequest = {
                            isColorPickerVisible = false
                            colorPickerTargetSlotIndex = null
                        },
                    ) {
                        ColorPickerDialog(
                            initialValue = colorPickerInput,
                            onDismiss = {
                                isColorPickerVisible = false
                                colorPickerTargetSlotIndex = null
                            },
                            onApply = { appliedColor ->
                                isColorPickerVisible = false
                                colorPickerTargetSlotIndex?.let { slotIndex ->
                                    if (slotIndex in favoritePalette.indices) {
                                        val updatedPalette = favoritePalette.toMutableList()
                                        updatedPalette[slotIndex] = stripAlpha(normalizeHexColor(appliedColor))
                                        saveFavoritePalette(updatedPalette)
                                    }
                                }
                                colorPickerTargetSlotIndex = null
                                onColorSelected(stripAlpha(normalizeHexColor(appliedColor)))
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
                Text(
                    text = "$currentPageNumber/${topBarState.totalPages}",
                    color = NOTEWISE_ICON,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { contentDescription = pageCounterDescription },
                )
                if (topBarState.totalPages > 1) {
                    TextButton(
                        onClick = {
                            isPageJumpDialogVisible = true
                            pageJumpInput = currentPageNumber.toString()
                        },
                        modifier = Modifier.semantics { contentDescription = "Jump to page" },
                    ) {
                        Text("Jump", color = NOTEWISE_ICON)
                    }
                }
                // Outline button for PDF documents
                if (topBarState.isPdfDocument) {
                    IconButton(
                        onClick = topBarState.onToggleTextSelectionMode,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (topBarState.isTextSelectionMode) {
                                        "Text selection mode on"
                                    } else {
                                        "Text selection mode off"
                                    }
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Loupe,
                            contentDescription = null,
                            tint = if (topBarState.isTextSelectionMode) NOTEWISE_SELECTED else NOTEWISE_ICON,
                        )
                    }
                    IconButton(
                        onClick = topBarState.onOpenOutline,
                        modifier = Modifier.semantics { contentDescription = "Table of contents" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = NOTEWISE_ICON,
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { isInsertMenuExpanded = true },
                        enabled = isEditingEnabled,
                        modifier = Modifier.semantics { contentDescription = "Insert menu" },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = if (isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                        )
                    }
                    DropdownMenu(
                        expanded = isInsertMenuExpanded,
                        onDismissRequest = { isInsertMenuExpanded = false },
                    ) {
                        InsertMenuItem(
                            text = "Line",
                            icon = Icons.Filled.HorizontalRule,
                            enabled = isEditingEnabled,
                            active = toolbarState.activeInsertAction == InsertAction.LINE,
                            testTag = "insert-line",
                            onClick = {
                                toolbarState.onInsertActionSelected(InsertAction.LINE)
                                isInsertMenuExpanded = false
                            },
                        )
                        InsertMenuItem(
                            text = "Rectangle",
                            icon = Icons.Filled.CropSquare,
                            enabled = isEditingEnabled,
                            active = toolbarState.activeInsertAction == InsertAction.RECTANGLE,
                            testTag = "insert-rectangle",
                            onClick = {
                                toolbarState.onInsertActionSelected(InsertAction.RECTANGLE)
                                isInsertMenuExpanded = false
                            },
                        )
                        InsertMenuItem(
                            text = "Ellipse",
                            icon = Icons.Filled.Circle,
                            enabled = isEditingEnabled,
                            active = toolbarState.activeInsertAction == InsertAction.ELLIPSE,
                            testTag = "insert-ellipse",
                            onClick = {
                                toolbarState.onInsertActionSelected(InsertAction.ELLIPSE)
                                isInsertMenuExpanded = false
                            },
                        )
                        HorizontalDivider()
                        InsertMenuItem(
                            text = "Image",
                            icon = Icons.Filled.BorderColor,
                            enabled = isEditingEnabled,
                            active = toolbarState.activeInsertAction == InsertAction.IMAGE,
                            testTag = "insert-image",
                            onClick = {
                                toolbarState.onInsertActionSelected(InsertAction.IMAGE)
                                isInsertMenuExpanded = false
                            },
                        )
                        InsertMenuItem(
                            text = "Text",
                            icon = Icons.Filled.Create,
                            enabled = isEditingEnabled,
                            active = toolbarState.activeInsertAction == InsertAction.TEXT,
                            testTag = "insert-text",
                            onClick = {
                                toolbarState.onInsertActionSelected(InsertAction.TEXT)
                                isInsertMenuExpanded = false
                            },
                        )
                        HorizontalDivider()
                        InsertMenuItem(
                            text = "Camera (coming next wave)",
                            icon = Icons.Filled.Add,
                            enabled = false,
                            active = false,
                            testTag = "insert-camera-disabled",
                            onClick = {},
                        )
                        InsertMenuItem(
                            text = "Scan (coming next wave)",
                            icon = Icons.Filled.Add,
                            enabled = false,
                            active = false,
                            testTag = "insert-scan-disabled",
                            onClick = {},
                        )
                        InsertMenuItem(
                            text = "Voice recording (coming next wave)",
                            icon = Icons.Filled.Add,
                            enabled = false,
                            active = false,
                            testTag = "insert-voice-disabled",
                            onClick = {},
                        )
                        InsertMenuItem(
                            text = "Audio file (coming next wave)",
                            icon = Icons.Filled.Add,
                            enabled = false,
                            active = false,
                            testTag = "insert-audio-disabled",
                            onClick = {},
                        )
                        InsertMenuItem(
                            text = "Sticky note (coming next wave)",
                            icon = Icons.Filled.Add,
                            enabled = false,
                            active = false,
                            testTag = "insert-sticky-disabled",
                            onClick = {},
                        )
                    }
                }
            }

            ToolbarGroup(
                contentDescription = "Mode controls",
                modifier = Modifier.width(NOTEWISE_RIGHT_GROUP_WIDTH_DP.dp),
            ) {
                IconButton(
                    onClick = topBarState.onToggleRecognitionOverlay,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (topBarState.isRecognitionOverlayEnabled) {
                                    "Recognition overlay on"
                                } else {
                                    "Recognition overlay off"
                                }
                        },
                ) {
                    Icon(
                        imageVector = if (topBarState.isRecognitionOverlayEnabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = if (topBarState.isRecognitionOverlayEnabled) NOTEWISE_SELECTED else NOTEWISE_ICON,
                    )
                }
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

    if (isPageJumpDialogVisible) {
        AlertDialog(
            onDismissRequest = { isPageJumpDialogVisible = false },
            title = { Text("Jump to page") },
            text = {
                OutlinedTextField(
                    value = pageJumpInput,
                    onValueChange = { value ->
                        pageJumpInput = value.filter { it.isDigit() }.take(6)
                    },
                    label = { Text("Page number (1-${topBarState.totalPages})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pageNumber = pageJumpInput.toIntOrNull()
                        if (pageNumber != null && pageNumber in 1..topBarState.totalPages) {
                            topBarState.onJumpToPage(pageNumber - 1)
                            isPageJumpDialogVisible = false
                        }
                    },
                ) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPageJumpDialogVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (isInputSettingsDialogVisible) {
        InputSettingsDialog(
            settings = inputSettingsDraft,
            onSettingsChange = { updated -> inputSettingsDraft = updated },
            onDismiss = { isInputSettingsDialogVisible = false },
            onApply = {
                toolbarState.onInputSettingsChange(inputSettingsDraft)
                isInputSettingsDialogVisible = false
            },
        )
    }
}

@Composable
private fun RowScope.NoteTitleEditor(
    noteTitle: String,
    titleDraft: String,
    isEditing: Boolean,
    isEditingEnabled: Boolean,
    onTitleDraftChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCommit: () -> Unit,
) {
    if (isEditing && isEditingEnabled) {
        OutlinedTextField(
            value = titleDraft,
            onValueChange = onTitleDraftChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(TITLE_INPUT_TEST_TAG)
                    .semantics { contentDescription = "Note title input" },
            textStyle = MaterialTheme.typography.titleSmall,
        )
    } else {
        val displayTitle = noteTitle.ifBlank { "Untitled note" }
        TextButton(
            onClick = onStartEditing,
            enabled = isEditingEnabled,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Note title" },
        ) {
            Text(
                text = displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
            )
        }
    }
}

@Composable
private fun InputSettingsDialog(
    settings: InputSettings,
    onSettingsChange: (InputSettings) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input settings") },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InputSettingsSelector(
                    label = "Single finger",
                    selected = settings.singleFingerMode.name,
                    options = SingleFingerMode.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                singleFingerMode = SingleFingerMode.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Double finger",
                    selected = settings.doubleFingerMode.name,
                    options = DoubleFingerMode.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                doubleFingerMode = DoubleFingerMode.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Stylus primary",
                    selected = settings.stylusPrimaryAction.name,
                    options = StylusButtonAction.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                stylusPrimaryAction = StylusButtonAction.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Stylus secondary",
                    selected = settings.stylusSecondaryAction.name,
                    options = StylusButtonAction.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                stylusSecondaryAction = StylusButtonAction.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Stylus long hold",
                    selected = settings.stylusLongHoldAction.name,
                    options = StylusButtonAction.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                stylusLongHoldAction = StylusButtonAction.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Double tap zoom",
                    selected = settings.doubleTapZoomAction.name,
                    options = DoubleTapZoomAction.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                doubleTapZoomAction = DoubleTapZoomAction.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Double tap source",
                    selected = settings.doubleTapZoomPointerMode.name,
                    options = DoubleTapZoomPointerMode.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                doubleTapZoomPointerMode = DoubleTapZoomPointerMode.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Two-finger tap",
                    selected = settings.twoFingerTapAction.name,
                    options = MultiFingerTapAction.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                twoFingerTapAction = MultiFingerTapAction.valueOf(selected),
                            ),
                        )
                    },
                )
                InputSettingsSelector(
                    label = "Three-finger tap",
                    selected = settings.threeFingerTapAction.name,
                    options = MultiFingerTapAction.entries.map { it.name },
                    onSelect = { selected ->
                        onSettingsChange(
                            settings.copy(
                                threeFingerTapAction = MultiFingerTapAction.valueOf(selected),
                            ),
                        )
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = onApply) {
                Text("Apply")
            }
        },
    )
}

@Composable
private fun InputSettingsSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $selected")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
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
                ).combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ).semantics { contentDescription = visuals.label },
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
                ).combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ).semantics { contentDescription = "Eraser" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoFixOff,
            contentDescription = null,
            tint = NOTEWISE_ICON,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LassoToggleButton(
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = if (isSelected) NOTEWISE_SELECTED else Color.Transparent

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
                ).combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ).semantics { contentDescription = "Lasso selection tool" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Loupe,
            contentDescription = null,
            tint = NOTEWISE_ICON,
        )
    }
}

@Composable
private fun TemplateButton(
    templateState: PageTemplateState,
    enabled: Boolean,
    onTemplateChange: (PageTemplateState) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val currentKind = templateState.backgroundKind

    Box {
        IconButton(
            onClick = { isExpanded = true },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Page template" },
        ) {
            Icon(
                imageVector = Icons.Filled.GridOn,
                contentDescription = null,
                tint = if (currentKind != "blank") NOTEWISE_SELECTED else NOTEWISE_ICON,
            )
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            TemplateSettingsPanel(
                templateState = templateState,
                onDismiss = { isExpanded = false },
                onTemplateChange = onTemplateChange,
            )
        }
    }
}

@Composable
private fun TemplateSettingsPanel(
    templateState: PageTemplateState,
    onDismiss: () -> Unit,
    onTemplateChange: (PageTemplateState) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .widthIn(min = TOOL_SETTINGS_PANEL_MIN_WIDTH_DP.dp)
                .padding(TOOL_SETTINGS_PANEL_OFFSET_DP.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier =
                Modifier
                    .padding(
                        horizontal = TOOL_SETTINGS_PANEL_HORIZONTAL_PADDING_DP.dp,
                        vertical = TOOL_SETTINGS_PANEL_VERTICAL_PADDING_DP.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(TOOL_SETTINGS_PANEL_ITEM_SPACING_DP.dp),
        ) {
            Text(
                text = "Page template",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            val kinds = listOf("blank" to "Blank", "grid" to "Grid", "lined" to "Lined", "dotted" to "Dotted")
            kinds.forEach { (kind, label) ->
                val isSelected = templateState.backgroundKind == kind
                TextButton(
                    onClick = {
                        val newSpacing =
                            when (kind) {
                                "blank" -> {
                                    0f
                                }

                                else -> {
                                    val range = spacingRangeForTemplate(kind)
                                    templateState.spacing.coerceIn(range.start, range.endInclusive)
                                }
                            }
                        onTemplateChange(
                            templateState.copy(
                                backgroundKind = kind,
                                spacing = newSpacing,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) NOTEWISE_SELECTED else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = NOTEWISE_SELECTED,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (templateState.backgroundKind != "blank") {
                val spacingRange = spacingRangeForTemplate(templateState.backgroundKind)
                HorizontalDivider()
                Text(text = "Density", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = templateState.spacing.coerceIn(spacingRange.start, spacingRange.endInclusive),
                    onValueChange = { spacing ->
                        val bounded = spacing.coerceIn(spacingRange.start, spacingRange.endInclusive)
                        onTemplateChange(templateState.copy(spacing = bounded))
                    },
                    valueRange = spacingRange,
                )
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
private fun InsertMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        enabled = enabled,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) NOTEWISE_SELECTED else NOTEWISE_ICON,
            )
        },
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun PaletteRow(
    favoritePalette: List<String>,
    selectedColor: String,
    enabled: Boolean,
    onColorSelected: (String) -> Unit,
    onColorLongPress: (Int, String) -> Unit,
) {
    val normalizedSelected = normalizeHexColor(selectedColor).takeLast(HEX_COLOR_LENGTH_RGB - 1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        favoritePalette.forEachIndexed { index, color ->
            val normalizedPaletteColor = normalizeHexColor(color).takeLast(HEX_COLOR_LENGTH_RGB - 1)
            PaletteSwatch(
                hexColor = color,
                isSelected = normalizedSelected == normalizedPaletteColor,
                enabled = enabled,
                onClick = { onColorSelected(color) },
                onLongPress = { onColorLongPress(index, color) },
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
                ).semantics {
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
