@file:Suppress("FunctionName")

package com.onyx.android.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGesturesPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import kotlin.math.roundToInt

@Composable
internal fun NoteEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
) {
    Scaffold(
        topBar = { NoteEditorTopBar(topBarState) },
        bottomBar = { NoteEditorToolbar(toolbarState) },
    ) { paddingValues ->
        NoteEditorContent(
            contentState = contentState,
            transformState = transformState,
            paddingValues = paddingValues,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NoteEditorTopBar(state: NoteEditorTopBarState) {
    TopAppBar(
        title = {
            val pageNumber = if (state.totalPages == 0) 0 else state.currentPageIndex + 1
            Text(text = "Page $pageNumber of ${state.totalPages}")
        },
        navigationIcon = {
            IconButton(onClick = state.onNavigateBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(
                onClick = state.onNavigatePrevious,
                enabled = state.canNavigatePrevious,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous page",
                )
            }
            IconButton(
                onClick = state.onNavigateNext,
                enabled = state.canNavigateNext,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next page",
                )
            }
            IconButton(onClick = state.onCreatePage) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New page",
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                )
            }
            IconButton(onClick = state.onUndo, enabled = state.canUndo) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = state.onRedo, enabled = state.canRedo) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
        },
    )
}

@Composable
private fun NoteEditorToolbar(state: NoteEditorToolbarState) {
    val brush = state.brush
    val isEraserSelected = brush.tool == Tool.ERASER
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = TOOLBAR_HORIZONTAL_PADDING_DP.dp,
                        vertical = TOOLBAR_VERTICAL_PADDING_DP.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaletteRow(
                selectedColor = brush.color,
                onColorSelected = { selectedColor ->
                    state.onBrushChange(brush.copy(color = selectedColor))
                },
            )
            EraserToggleButton(
                isSelected = isEraserSelected,
                onToggle = {
                    state.onBrushChange(brush.toggleEraser(state.lastNonEraserTool))
                },
            )
            ToolbarDivider()
            BrushSizeControl(
                brush = brush,
                onBrushChange = state.onBrushChange,
            )
        }
    }
}

@Composable
private fun RowScope.PaletteRow(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DEFAULT_PALETTE.forEach { hexColor ->
            PaletteSwatch(
                hexColor = hexColor,
                isSelected = selectedColor == hexColor,
                onClick = { onColorSelected(hexColor) },
            )
        }
    }
}

@Composable
private fun PaletteSwatch(
    hexColor: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val swatchColor = Color(android.graphics.Color.parseColor(hexColor))
    IconButton(onClick = onClick) {
        Surface(
            modifier = Modifier.size(PALETTE_SWATCH_SIZE_DP.dp),
            color = swatchColor,
            shape = CircleShape,
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
        }
    }
}

@Composable
private fun EraserToggleButton(
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
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
                    contentDescription = "Eraser",
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
private fun RowScope.BrushSizeControl(
    brush: Brush,
    onBrushChange: (Brush) -> Unit,
) {
    Row(
        modifier = Modifier.weight(1f),
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
                value = brush.baseWidth,
                onValueChange = { newSize ->
                    onBrushChange(brush.copy(baseWidth = newSize))
                },
                valueRange = BRUSH_SIZE_MIN..BRUSH_SIZE_MAX,
                steps = BRUSH_SIZE_STEPS,
            )
        }
        Text(
            text = brush.baseWidth.roundToInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
                        .transformable(state = transformState),
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
                            onStrokeFinished = contentState.onStrokeFinished,
                            onStrokeErased = contentState.onStrokeErased,
                        )
                    InkCanvas(
                        state = inkCanvasState,
                        callbacks = inkCanvasCallbacks,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun Brush.toggleEraser(lastNonEraserTool: Tool): Brush =
    if (tool == Tool.ERASER) {
        copy(tool = lastNonEraserTool)
    } else {
        copy(tool = Tool.ERASER)
    }
