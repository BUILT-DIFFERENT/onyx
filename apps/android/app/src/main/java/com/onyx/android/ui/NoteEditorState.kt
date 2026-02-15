package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfTextChar
import com.onyx.android.pdf.PdfTextSelection
import com.onyx.android.pdf.PdfTileKey

/**
 * Interaction mode for finger input.
 * Stylus always triggers DRAW mode regardless of this setting.
 */
internal enum class InteractionMode {
    DRAW,
    PAN,
    SCROLL,
}

internal data class TextSelection(
    val pageIndex: Int,
    val pageCharacters: List<PdfTextChar>,
    val startCharIndex: Int,
    val endCharIndex: Int,
    val selection: PdfTextSelection,
) {
    val quads: List<com.onyx.android.pdf.PdfTextQuad>
        get() = selection.chars.map { char -> char.quad }

    val text: String
        get() = selection.text
}

internal data class NoteEditorTopBarState(
    val noteTitle: String,
    val totalPages: Int,
    val currentPageIndex: Int,
    val isReadOnly: Boolean,
    val isPdfDocument: Boolean,
    val canNavigatePrevious: Boolean,
    val canNavigateNext: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val onNavigateBack: () -> Unit,
    val onNavigatePrevious: () -> Unit,
    val onNavigateNext: () -> Unit,
    val onCreatePage: () -> Unit,
    val onUpdateTitle: (String) -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onToggleReadOnly: () -> Unit,
    val onOpenOutline: () -> Unit,
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
    val pdfTiles: Map<PdfTileKey, android.graphics.Bitmap>,
    val pdfRenderScaleBucket: Float?,
    val pdfPreviousScaleBucket: Float?,
    val pdfTileSizePx: Int,
    val pdfCrossfadeProgress: Float,
    val pdfBitmap: android.graphics.Bitmap?,
    val pdfRenderer: PdfDocumentRenderer?,
    val currentPage: PageEntity?,
    val viewTransform: ViewTransform,
    val pageWidthDp: Dp,
    val pageHeightDp: Dp,
    val pageWidth: Float,
    val pageHeight: Float,
    val strokes: List<Stroke>,
    val brush: Brush,
    val isStylusButtonEraserActive: Boolean,
    val interactionMode: InteractionMode,
    val thumbnails: List<ThumbnailItem>,
    val currentPageIndex: Int,
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
    val onPageSelected: (Int) -> Unit,
)

/**
 * State for a single page item in the multi-page LazyColumn.
 */
internal data class PageItemState(
    val page: PageEntity,
    val pageWidthDp: Dp,
    val pageHeightDp: Dp,
    val pageWidth: Float,
    val pageHeight: Float,
    val strokes: List<Stroke>,
    val isPdfPage: Boolean,
    val pdfBitmap: android.graphics.Bitmap?,
    val pdfTiles: Map<PdfTileKey, android.graphics.Bitmap>,
    val pdfRenderScaleBucket: Float?,
    val pdfPreviousScaleBucket: Float?,
    val pdfTileSizePx: Int,
    val pdfCrossfadeProgress: Float,
)

/**
 * State for the multi-page LazyColumn layout.
 */
internal data class MultiPageContentState(
    val pages: List<PageItemState>,
    val isReadOnly: Boolean,
    val brush: Brush,
    val isStylusButtonEraserActive: Boolean,
    val interactionMode: InteractionMode,
    val viewTransform: ViewTransform,
    val pdfRenderer: PdfDocumentRenderer?,
    val firstVisiblePageIndex: Int,
    val thumbnails: List<ThumbnailItem>,
    val onStrokeFinished: (Stroke, String) -> Unit,
    val onStrokeErased: (Stroke, String) -> Unit,
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
    val onVisiblePageChanged: (Int) -> Unit,
    val onVisiblePagesChanged: (IntRange) -> Unit,
    val onPageSelected: (Int) -> Unit,
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
    val pdfRenderer: PdfDocumentRenderer?,
    val pdfTiles: Map<PdfTileKey, android.graphics.Bitmap>,
    val pdfRenderScaleBucket: Float?,
    val pdfPreviousScaleBucket: Float?,
    val pdfTileSizePx: Int,
    val pdfCrossfadeProgress: Float,
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

/**
 * UI state for multi-page LazyColumn layout.
 */
internal data class MultiPageUiState(
    val topBarState: NoteEditorTopBarState,
    val toolbarState: NoteEditorToolbarState,
    val multiPageContentState: MultiPageContentState,
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
