package com.onyx.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.InkAction
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.InkCanvas
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
) {
    var brush by remember { mutableStateOf(Brush()) }
    var lastNonEraserTool by remember { mutableStateOf(brush.tool) }
    var strokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    val undoStack = remember { mutableStateListOf<InkAction>() }
    val redoStack = remember { mutableStateListOf<InkAction>() }
    val maxUndoActions = 50
    val viewTransform = remember { ViewTransform.DEFAULT }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        when (action) {
            is InkAction.AddStroke -> {
                strokes = strokes - action.stroke
            }

            is InkAction.RemoveStroke -> {
                strokes = strokes + action.stroke
            }
        }
        redoStack.add(action)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is InkAction.AddStroke -> {
                strokes = strokes + action.stroke
            }

            is InkAction.RemoveStroke -> {
                strokes = strokes - action.stroke
            }
        }
        undoStack.add(action)
        if (undoStack.size > maxUndoActions) {
            undoStack.removeFirst()
        }
    }
    LaunchedEffect(brush.tool) {
        if (brush.tool != Tool.ERASER) {
            lastNonEraserTool = brush.tool
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Note") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                        )
                    }
                    IconButton(
                        onClick = { undo() },
                        enabled = undoStack.isNotEmpty(),
                    ) {
                        Icon(imageVector = Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { redo() },
                        enabled = redoStack.isNotEmpty(),
                    ) {
                        Icon(imageVector = Icons.Default.Redo, contentDescription = "Redo")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val palette =
                        listOf(
                            "#111111",
                            "#1E88E5",
                            "#E53935",
                            "#43A047",
                            "#FDD835",
                            "#FB8C00",
                            "#8E24AA",
                        )
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        palette.forEach { hexColor ->
                            val swatchColor = Color(android.graphics.Color.parseColor(hexColor))
                            val isSelected = brush.color == hexColor
                            IconButton(onClick = { brush = brush.copy(color = hexColor) }) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    color = swatchColor,
                                    shape = CircleShape,
                                    border =
                                        if (isSelected) {
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.35f,
                                                ),
                                            )
                                        },
                                ) {
                                }
                            }
                        }
                    }
                    val isEraserSelected = brush.tool == Tool.ERASER
                    IconButton(
                        onClick = {
                            brush =
                                if (isEraserSelected) {
                                    brush.copy(tool = lastNonEraserTool)
                                } else {
                                    brush.copy(tool = Tool.ERASER)
                                }
                        },
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color =
                                if (isEraserSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                            border =
                                if (isEraserSelected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.35f,
                                        ),
                                    )
                                },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eraser",
                                    tint =
                                        if (isEraserSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }
                    VerticalDivider(
                        modifier =
                            Modifier
                                .height(32.dp)
                                .padding(horizontal = 12.dp),
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier =
                                Modifier
                                    .size((brush.baseWidth * 2.6f).coerceIn(12f, 24f).dp),
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
                                    brush = brush.copy(baseWidth = newSize)
                                },
                                valueRange = 1f..8f,
                                steps = 6,
                            )
                        }
                        Text(
                            text = brush.baseWidth.roundToInt().toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
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
                    modifier = Modifier.fillMaxSize(),
                ) {
                    InkCanvas(
                        strokes = strokes,
                        viewTransform = viewTransform,
                        brush = brush,
                        onStrokeFinished = { newStroke ->
                            strokes = strokes + newStroke
                            undoStack.add(InkAction.AddStroke(newStroke))
                            if (undoStack.size > maxUndoActions) {
                                undoStack.removeFirst()
                            }
                            redoStack.clear()
                        },
                        onStrokeErased = { erasedStroke ->
                            strokes = strokes - erasedStroke
                            undoStack.add(InkAction.RemoveStroke(erasedStroke))
                            if (undoStack.size > maxUndoActions) {
                                undoStack.removeFirst()
                            }
                            redoStack.clear()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
