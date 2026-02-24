@file:Suppress(
    "FunctionName",
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "MaxLineLength",
)

package com.onyx.android.ui.editor

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.objects.model.InsertAction
import com.onyx.android.objects.model.PageObject
import com.onyx.android.objects.model.PageObjectKind
import com.onyx.android.objects.model.ShapeType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_SHAPE_SIZE_PT = 12f
private const val MIN_OBJECT_SIZE_PT = 12f
private const val HANDLE_SIZE_DP = 18
private val OBJECT_HIGHLIGHT_COLOR = Color(0xFF136CC5)
private val OBJECT_ACTION_BG = Color(0xEE1F2433)
private val IMAGE_PLACEHOLDER_BG = Color(0x33000000)
private val IMAGE_PLACEHOLDER_STROKE = Color(0xAA000000)
private const val TEXT_DEFAULT = "Text"

@Composable
internal fun PageObjectLayer(
    pageObjects: List<PageObject>,
    selectedObjectId: String?,
    activeInsertAction: InsertAction,
    viewTransform: ViewTransform,
    isReadOnly: Boolean,
    isInteractionBlocked: Boolean,
    onInsertActionChanged: (InsertAction) -> Unit,
    onShapeObjectCreate: (ShapeType, Float, Float, Float, Float) -> Unit,
    onTextObjectCreate: (Float, Float) -> Unit,
    onImageObjectCreate: (Float, Float) -> Unit,
    onTextObjectEdit: (PageObject, String) -> Unit,
    onObjectSelected: (String?) -> Unit,
    onObjectTransformed: (PageObject, PageObject) -> Unit,
    onDuplicateObject: (PageObject) -> Unit,
    onDeleteObject: (PageObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactive = !isReadOnly && !isInteractionBlocked
    val selectedObject = pageObjects.firstOrNull { pageObject -> pageObject.objectId == selectedObjectId }
    var draftInsertStart by remember { mutableStateOf<Offset?>(null) }
    var draftInsertCurrent by remember { mutableStateOf<Offset?>(null) }
    var draftObject by remember { mutableStateOf<PageObject?>(null) }
    var textEditValue by remember { mutableStateOf(TEXT_DEFAULT) }

    LaunchedEffect(selectedObjectId) {
        draftObject = null
        textEditValue = selectedObject?.textPayload?.text ?: TEXT_DEFAULT
    }

    val resolvedSelectedObject = draftObject ?: selectedObject
    val shapeInsertType = activeInsertAction.toShapeTypeOrNull()
    val handleSizePx = with(LocalDensity.current) { HANDLE_SIZE_DP.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            pageObjects
                .sortedBy { pageObject -> pageObject.zIndex }
                .forEach { pageObject ->
                    drawPageObject(
                        pageObject = pageObject,
                        viewTransform = viewTransform,
                        isSelected = pageObject.objectId == selectedObjectId,
                    )
                }

            if (shapeInsertType != null && draftInsertStart != null && draftInsertCurrent != null) {
                val bounds = normalizedBounds(draftInsertStart!!, draftInsertCurrent!!)
                drawShapePreview(bounds = bounds, shapeType = shapeInsertType, viewTransform = viewTransform)
            }
        }

        if (interactive && shapeInsertType != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(activeInsertAction, viewTransform.zoom) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val pagePoint =
                                        Offset(
                                            viewTransform.screenToPageX(start.x),
                                            viewTransform.screenToPageY(start.y),
                                        )
                                    draftInsertStart = pagePoint
                                    draftInsertCurrent = pagePoint
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    draftInsertCurrent =
                                        Offset(
                                            viewTransform.screenToPageX(change.position.x),
                                            viewTransform.screenToPageY(change.position.y),
                                        )
                                },
                                onDragEnd = {
                                    val start = draftInsertStart
                                    val current = draftInsertCurrent
                                    if (start != null && current != null) {
                                        val bounds = normalizedBounds(start, current)
                                        if (
                                            bounds.width >= MIN_SHAPE_SIZE_PT &&
                                            bounds.height >= MIN_SHAPE_SIZE_PT
                                        ) {
                                            onShapeObjectCreate(
                                                shapeInsertType,
                                                bounds.left,
                                                bounds.top,
                                                bounds.width,
                                                bounds.height,
                                            )
                                        }
                                    }
                                    draftInsertStart = null
                                    draftInsertCurrent = null
                                    onInsertActionChanged(InsertAction.NONE)
                                },
                                onDragCancel = {
                                    draftInsertStart = null
                                    draftInsertCurrent = null
                                },
                            )
                        },
            )
        } else if (interactive && activeInsertAction == InsertAction.TEXT) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(activeInsertAction, viewTransform.zoom) {
                            detectTapGestures { position ->
                                onTextObjectCreate(
                                    viewTransform.screenToPageX(position.x),
                                    viewTransform.screenToPageY(position.y),
                                )
                                onInsertActionChanged(InsertAction.NONE)
                            }
                        },
            )
        } else if (interactive && activeInsertAction == InsertAction.IMAGE) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(activeInsertAction, viewTransform.zoom) {
                            detectTapGestures { position ->
                                onImageObjectCreate(
                                    viewTransform.screenToPageX(position.x),
                                    viewTransform.screenToPageY(position.y),
                                )
                                onInsertActionChanged(InsertAction.NONE)
                            }
                        },
            )
        } else if (interactive && pageObjects.isNotEmpty()) {
            pageObjects.forEach { pageObject ->
                val objectRect = pageObject.toScreenRect(viewTransform)
                Box(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    objectRect.left.roundToInt(),
                                    objectRect.top.roundToInt(),
                                )
                            }.size(
                                width = max(objectRect.width, 1f).dp,
                                height = max(objectRect.height, 1f).dp,
                            ).pointerInput(pageObject.objectId) {
                                detectTapGestures {
                                    onObjectSelected(pageObject.objectId)
                                }
                            }.pointerInput(pageObject.objectId, selectedObjectId, viewTransform.zoom) {
                                detectDragGestures(
                                    onDragStart = {
                                        onObjectSelected(pageObject.objectId)
                                        draftObject = pageObject
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (selectedObjectId != pageObject.objectId) {
                                            return@detectDragGestures
                                        }
                                        change.consume()
                                        val pageDeltaX = dragAmount.x / viewTransform.zoom
                                        val pageDeltaY = dragAmount.y / viewTransform.zoom
                                        val current = draftObject ?: pageObject
                                        draftObject =
                                            current.copy(
                                                x = current.x + pageDeltaX,
                                                y = current.y + pageDeltaY,
                                            )
                                    },
                                    onDragEnd = {
                                        val before = pageObject
                                        val after = draftObject
                                        if (after != null && after != before) {
                                            onObjectTransformed(before, after.copy(updatedAt = System.currentTimeMillis()))
                                        }
                                        draftObject = null
                                    },
                                    onDragCancel = {
                                        draftObject = null
                                    },
                                )
                            },
                )
            }
        }

        if (interactive && resolvedSelectedObject != null && activeInsertAction == InsertAction.NONE) {
            val selectedRect = resolvedSelectedObject.toScreenRect(viewTransform)
            val handleOffsetX = (selectedRect.right - handleSizePx / 2f).roundToInt()
            val handleOffsetY = (selectedRect.bottom - handleSizePx / 2f).roundToInt()

            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(handleOffsetX, handleOffsetY) }
                        .size(HANDLE_SIZE_DP.dp)
                        .background(OBJECT_HIGHLIGHT_COLOR, RoundedCornerShape(4.dp))
                        .pointerInput(resolvedSelectedObject.objectId) {
                            detectDragGestures(
                                onDragStart = {
                                    draftObject = resolvedSelectedObject
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val pageDeltaX = dragAmount.x / viewTransform.zoom
                                    val pageDeltaY = dragAmount.y / viewTransform.zoom
                                    val current = draftObject ?: resolvedSelectedObject
                                    draftObject =
                                        current.copy(
                                            width = max(MIN_OBJECT_SIZE_PT, current.width + pageDeltaX),
                                            height = max(MIN_OBJECT_SIZE_PT, current.height + pageDeltaY),
                                        )
                                },
                                onDragEnd = {
                                    val after = draftObject
                                    if (after != null && after != resolvedSelectedObject) {
                                        onObjectTransformed(
                                            resolvedSelectedObject,
                                            after.copy(updatedAt = System.currentTimeMillis()),
                                        )
                                    }
                                    draftObject = null
                                },
                                onDragCancel = {
                                    draftObject = null
                                },
                            )
                        },
            )

            if (resolvedSelectedObject.kind == PageObjectKind.TEXT) {
                val density = LocalDensity.current
                val fieldWidthDp = with(density) { max(96f, selectedRect.width).toDp() }
                val fieldHeightDp = with(density) { max(56f, selectedRect.height).toDp() }
                OutlinedTextField(
                    value = textEditValue,
                    onValueChange = { updated ->
                        textEditValue = updated
                    },
                    singleLine = false,
                    label = { Text("Text object") },
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    selectedRect.left.roundToInt(),
                                    selectedRect.top.roundToInt(),
                                )
                            }.size(width = fieldWidthDp, height = fieldHeightDp)
                            .testTag("text-object-editor"),
                )
            }

            Row(
                modifier =
                    Modifier
                        .offset {
                            IntOffset(
                                selectedRect.left.roundToInt(),
                                max(0f, selectedRect.top - 40f).roundToInt(),
                            )
                        }.background(OBJECT_ACTION_BG, RoundedCornerShape(8.dp))
                        .testTag("shape-object-actions"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (resolvedSelectedObject.kind == PageObjectKind.TEXT) {
                    TextButton(
                        onClick = { onTextObjectEdit(resolvedSelectedObject, textEditValue.ifBlank { TEXT_DEFAULT }) },
                        modifier = Modifier.testTag("text-save-action"),
                    ) {
                        Text("Save text", color = Color.White)
                    }
                }
                TextButton(
                    onClick = { onDuplicateObject(resolvedSelectedObject) },
                    modifier = Modifier.testTag("shape-duplicate-action"),
                ) {
                    Text("Duplicate", color = Color.White)
                }
                TextButton(
                    onClick = { onDeleteObject(resolvedSelectedObject) },
                    modifier = Modifier.testTag("shape-delete-action"),
                ) {
                    Text("Delete", color = Color.White)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShapePreview(
    bounds: Rect,
    shapeType: ShapeType,
    viewTransform: ViewTransform,
) {
    val strokeWidth = max(1f, viewTransform.zoom)
    val left = viewTransform.pageToScreenX(bounds.left)
    val top = viewTransform.pageToScreenY(bounds.top)
    val width = viewTransform.pageWidthToScreen(bounds.width)
    val height = viewTransform.pageWidthToScreen(bounds.height)
    when (shapeType) {
        ShapeType.LINE -> {
            drawLine(
                color = OBJECT_HIGHLIGHT_COLOR,
                start = Offset(left, top),
                end = Offset(left + width, top + height),
                strokeWidth = strokeWidth,
            )
        }

        ShapeType.RECTANGLE -> {
            drawRect(
                color = OBJECT_HIGHLIGHT_COLOR,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = strokeWidth),
            )
        }

        ShapeType.ELLIPSE -> {
            drawOval(
                color = OBJECT_HIGHLIGHT_COLOR,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPageObject(
    pageObject: PageObject,
    viewTransform: ViewTransform,
    isSelected: Boolean,
) {
    val left = viewTransform.pageToScreenX(pageObject.x)
    val top = viewTransform.pageToScreenY(pageObject.y)
    val width = viewTransform.pageWidthToScreen(pageObject.width)
    val height = viewTransform.pageWidthToScreen(pageObject.height)
    val selectionStroke = max(1f, viewTransform.zoom)

    when (pageObject.kind) {
        PageObjectKind.SHAPE -> {
            val shapePayload = pageObject.shapePayload ?: return
            val color = shapePayload.strokeColor.toComposeColor()
            val strokeWidth = max(1f, shapePayload.strokeWidth * viewTransform.zoom)
            when (shapePayload.shapeType) {
                ShapeType.LINE -> {
                    drawLine(
                        color = color,
                        start = Offset(left, top),
                        end = Offset(left + width, top + height),
                        strokeWidth = strokeWidth,
                    )
                }

                ShapeType.RECTANGLE -> {
                    drawRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = strokeWidth),
                    )
                }

                ShapeType.ELLIPSE -> {
                    drawOval(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }

        PageObjectKind.IMAGE -> {
            drawRect(
                color = IMAGE_PLACEHOLDER_BG,
                topLeft = Offset(left, top),
                size = Size(width, height),
            )
            drawRect(
                color = IMAGE_PLACEHOLDER_STROKE,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = max(1f, viewTransform.zoom)),
            )
            drawLine(
                color = IMAGE_PLACEHOLDER_STROKE,
                start = Offset(left, top),
                end = Offset(left + width, top + height),
                strokeWidth = max(1f, viewTransform.zoom),
            )
            drawLine(
                color = IMAGE_PLACEHOLDER_STROKE,
                start = Offset(left + width, top),
                end = Offset(left, top + height),
                strokeWidth = max(1f, viewTransform.zoom),
            )
            val imageLabelPaint =
                Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#1F2433")
                    textSize = max(10f, 12f * viewTransform.zoom)
                }
            val label = pageObject.imagePayload?.displayName ?: "Image"
            drawContext.canvas.nativeCanvas.drawText(label, left + 8f * viewTransform.zoom, top + 20f * viewTransform.zoom, imageLabelPaint)
        }

        PageObjectKind.TEXT -> {
            drawRect(
                color = Color(0x12000000),
                topLeft = Offset(left, top),
                size = Size(width, height),
            )
            drawRect(
                color = Color(0x55000000),
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = max(1f, viewTransform.zoom)),
            )
            val textPaint =
                Paint().apply {
                    isAntiAlias = true
                    color = pageObject.textPayload?.color?.toAndroidColor() ?: android.graphics.Color.BLACK
                    textSize = max(10f, (pageObject.textPayload?.fontSizeSp ?: 16f) * viewTransform.zoom)
                }
            val textValue = pageObject.textPayload?.text?.ifBlank { TEXT_DEFAULT } ?: TEXT_DEFAULT
            drawContext.canvas.nativeCanvas.drawText(textValue, left + 8f * viewTransform.zoom, top + 24f * viewTransform.zoom, textPaint)
        }
    }

    if (isSelected) {
        drawRect(
            color = OBJECT_HIGHLIGHT_COLOR,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(width = selectionStroke),
        )
    }
}

private fun PageObject.toScreenRect(viewTransform: ViewTransform): Rect {
    val left = viewTransform.pageToScreenX(x)
    val top = viewTransform.pageToScreenY(y)
    val widthPx = viewTransform.pageWidthToScreen(width)
    val heightPx = viewTransform.pageWidthToScreen(height)
    return Rect(
        left = left,
        top = top,
        right = left + widthPx,
        bottom = top + heightPx,
    )
}

private fun normalizedBounds(
    start: Offset,
    end: Offset,
): Rect =
    Rect(
        left = min(start.x, end.x),
        top = min(start.y, end.y),
        right = max(start.x, end.x),
        bottom = max(start.y, end.y),
    )

private fun InsertAction.toShapeTypeOrNull(): ShapeType? =
    when (this) {
        InsertAction.LINE -> ShapeType.LINE
        InsertAction.RECTANGLE -> ShapeType.RECTANGLE
        InsertAction.ELLIPSE -> ShapeType.ELLIPSE
        else -> null
    }

private fun String.toComposeColor(): Color =
    runCatching {
        Color(android.graphics.Color.parseColor(this))
    }.getOrElse { Color.Black }

private fun String.toAndroidColor(): Int =
    runCatching {
        android.graphics.Color.parseColor(this)
    }.getOrElse { android.graphics.Color.BLACK }
