package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredText.TextChar
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.pdf.PdfRenderer

internal data class TextSelection(
    val structuredText: StructuredText,
    val startChar: TextChar,
    val endChar: TextChar,
    val quads: List<Quad>,
)

internal data class NoteEditorTopBarState(
    val noteTitle: String,
    val totalPages: Int,
    val currentPageIndex: Int,
    val isReadOnly: Boolean,
    val canNavigatePrevious: Boolean,
    val canNavigateNext: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val onNavigateBack: () -> Unit,
    val onNavigatePrevious: () -> Unit,
    val onNavigateNext: () -> Unit,
    val onCreatePage: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onToggleReadOnly: () -> Unit,
)

internal data class NoteEditorToolbarState(
    val brush: Brush,
    val lastNonEraserTool: Tool,
    val isStylusButtonEraserActive: Boolean,
    val onBrushChange: (Brush) -> Unit,
)

internal data class NoteEditorContentState(
    val isPdfPage: Boolean,
    val isReadOnly: Boolean,
    val pdfBitmap: android.graphics.Bitmap?,
    val pdfRenderer: PdfRenderer?,
    val currentPage: PageEntity?,
    val viewTransform: ViewTransform,
    val pageWidthDp: Dp,
    val pageHeightDp: Dp,
    val pageWidth: Float,
    val pageHeight: Float,
    val strokes: List<Stroke>,
    val brush: Brush,
    val isStylusButtonEraserActive: Boolean,
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
    val onStylusButtonEraserActiveChanged: (Boolean) -> Unit,
    val onTransformGesture: (
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        centroidX: Float,
        centroidY: Float,
    ) -> Unit,
    val onPanGestureEnd: (
        velocityX: Float,
        velocityY: Float,
    ) -> Unit,
    val onViewportSizeChanged: (IntSize) -> Unit,
)

internal data class NoteEditorPageState(
    val noteTitle: String,
    val strokes: List<Stroke>,
    val pages: List<PageEntity>,
    val currentPageIndex: Int,
    val currentPage: PageEntity?,
)

internal data class NoteEditorPdfState(
    val isPdfPage: Boolean,
    val pdfRenderer: PdfRenderer?,
    val pdfBitmap: android.graphics.Bitmap?,
    val pageWidthDp: Dp,
    val pageHeightDp: Dp,
    val pageWidth: Float,
    val pageHeight: Float,
)

internal data class NoteEditorUiState(
    val topBarState: NoteEditorTopBarState,
    val toolbarState: NoteEditorToolbarState,
    val contentState: NoteEditorContentState,
    val transformState: TransformableState,
)

internal data class BrushState(
    val brush: Brush,
    val lastNonEraserTool: Tool,
    val onBrushChange: (Brush) -> Unit,
)

internal data class StrokeCallbacks(
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
)
