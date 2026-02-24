package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.LassoSelection
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.input.InputSettings
import com.onyx.android.objects.model.InsertAction
import com.onyx.android.objects.model.PageObject
import com.onyx.android.objects.model.ShapeType
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfTextChar
import com.onyx.android.pdf.PdfTextSelection
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.ValidatingTile
import com.onyx.android.recognition.ConvertedTextBlock

internal enum class InteractionMode {
    DRAW,
    TEXT_SELECTION,
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
    val pdfSearchQuery: String = "",
    val pdfSearchStatusText: String = "",
    val onPdfSearchQuerySubmit: (String) -> Unit = {},
    val onPdfSearchPrevious: () -> Unit = {},
    val onPdfSearchNext: () -> Unit = {},
    val isTextSelectionMode: Boolean = false,
    val onToggleTextSelectionMode: () -> Unit = {},
    val onJumpToPage: (Int) -> Unit = {},
    val onOpenPageManager: () -> Unit = {},
    val isRecognitionOverlayEnabled: Boolean = false,
    val onToggleRecognitionOverlay: () -> Unit = {},
    val keepScreenOn: Boolean = false,
    val hideSystemBars: Boolean = true,
    val onKeepScreenOnChanged: (Boolean) -> Unit = {},
    val onHideSystemBarsChanged: (Boolean) -> Unit = {},
)

internal data class NoteEditorToolbarState(
    val brush: Brush,
    val lastNonEraserTool: Tool,
    val isStylusButtonEraserActive: Boolean,
    val isSegmentEraserEnabled: Boolean = false,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val eraserFilter: EraserFilter = EraserFilter.ALL_STROKES,
    val activeInsertAction: InsertAction = InsertAction.NONE,
    val inputSettings: InputSettings = InputSettings(),
    val templateState: PageTemplateState,
    val onBrushChange: (Brush) -> Unit,
    val onInputSettingsChange: (InputSettings) -> Unit = {},
    val onSegmentEraserEnabledChange: (Boolean) -> Unit = {},
    val onEraserModeChange: (EraserMode) -> Unit = {},
    val onEraserFilterChange: (EraserFilter) -> Unit = {},
    val onClearPageRequested: () -> Unit = {},
    val onInsertActionSelected: (InsertAction) -> Unit = {},
    val onTemplateChange: (PageTemplateState) -> Unit,
)

internal enum class EraserFilter {
    ALL_STROKES,
    PEN_ONLY,
    HIGHLIGHTER_ONLY,
}

internal enum class EraserMode {
    STROKE,
    SEGMENT,
    AREA,
}

internal data class NoteEditorContentState(
    val isPdfPage: Boolean,
    val isReadOnly: Boolean,
    val pdfTiles: Map<PdfTileKey, ValidatingTile>,
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
    val pageObjects: List<PageObject> = emptyList(),
    val selectedObjectId: String? = null,
    val brush: Brush,
    val isStylusButtonEraserActive: Boolean,
    val isSegmentEraserEnabled: Boolean = false,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val eraserFilter: EraserFilter = EraserFilter.ALL_STROKES,
    val activeInsertAction: InsertAction = InsertAction.NONE,
    val interactionMode: InteractionMode,
    val allowCanvasFingerGestures: Boolean = true,
    val inputSettings: InputSettings = InputSettings(),
    val thumbnails: List<ThumbnailItem>,
    val currentPageIndex: Int,
    val totalPages: Int,
    val templateState: PageTemplateState,
    val isZoomLocked: Boolean = false,
    val lassoSelection: LassoSelection = LassoSelection(),
    val isTextSelectionEnabled: Boolean = false,
    val isRecognitionOverlayEnabled: Boolean = false,
    val recognitionText: String? = null,
    val convertedTextBlocks: List<ConvertedTextBlock> = emptyList(),
    val onConvertedTextBlockSelected: (ConvertedTextBlock) -> Unit = {},
    val loadThumbnail: suspend (Int) -> android.graphics.Bitmap? = { null },
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
    val onStrokeSplit: (Stroke, List<Stroke>) -> Unit = { _, _ -> },
    val onLassoMove: (Float, Float) -> Unit = { _, _ -> },
    val onLassoResize: (Float, Float, Float) -> Unit = { _, _, _ -> },
    val onInsertActionChanged: (InsertAction) -> Unit = {},
    val onShapeObjectCreate: (ShapeType, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    val onTextObjectCreate: (Float, Float) -> Unit = { _, _ -> },
    val onImageObjectCreate: (Float, Float) -> Unit = { _, _ -> },
    val onTextObjectEdit: (PageObject, String) -> Unit = { _, _ -> },
    val onObjectSelected: (String?) -> Unit = {},
    val onObjectTransformed: (PageObject, PageObject) -> Unit = { _, _ -> },
    val onDuplicateObject: (PageObject) -> Unit = {},
    val onDeleteObject: (PageObject) -> Unit = {},
    val onSegmentEraserEnabledChange: (Boolean) -> Unit = {},
    val onEraserModeChange: (EraserMode) -> Unit = {},
    val onEraserFilterChange: (EraserFilter) -> Unit = {},
    val onClearPageRequested: () -> Unit = {},
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
    val onUndoShortcut: () -> Unit = {},
    val onRedoShortcut: () -> Unit = {},
    val onDoubleTapZoomRequested: () -> Unit = {},
    val onViewportSizeChanged: (IntSize) -> Unit,
    val onPageSelected: (Int) -> Unit,
    val onZoomPresetSelected: (Int) -> Unit = {},
    val onFitZoomRequested: () -> Unit = {},
    val onZoomLockChanged: (Boolean) -> Unit = {},
    val onTemplateChange: (PageTemplateState) -> Unit,
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
    val pageObjects: List<PageObject> = emptyList(),
    val isPdfPage: Boolean,
    val isVisible: Boolean,
    val renderTransform: ViewTransform,
    val templateState: PageTemplateState,
    val lassoSelection: LassoSelection = LassoSelection(),
    val selectedObjectId: String? = null,
    val searchHighlightBounds: Rect? = null,
    val recognitionText: String? = null,
    val convertedTextBlocks: List<ConvertedTextBlock> = emptyList(),
)

/**
 * State for the multi-page LazyColumn layout.
 */
internal data class MultiPageContentState(
    val pages: List<PageItemState>,
    val isReadOnly: Boolean,
    val brush: Brush,
    val isStylusButtonEraserActive: Boolean,
    val isSegmentEraserEnabled: Boolean = false,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val eraserFilter: EraserFilter = EraserFilter.ALL_STROKES,
    val activeInsertAction: InsertAction = InsertAction.NONE,
    val selectedObjectId: String? = null,
    val interactionMode: InteractionMode,
    val inputSettings: InputSettings = InputSettings(),
    val pdfRenderer: PdfDocumentRenderer?,
    val firstVisiblePageIndex: Int,
    val documentZoom: Float,
    val documentPanX: Float,
    val totalPages: Int,
    val minDocumentZoom: Float,
    val maxDocumentZoom: Float,
    val isZoomLocked: Boolean = false,
    val thumbnails: List<ThumbnailItem>,
    val templateState: PageTemplateState,
    val lassoSelectionsByPageId: Map<String, LassoSelection> = emptyMap(),
    val isTextSelectionEnabled: Boolean = false,
    val isRecognitionOverlayEnabled: Boolean = false,
    val onConvertedTextBlockSelected: (pageId: String, block: ConvertedTextBlock) -> Unit = { _, _ -> },
    val loadThumbnail: suspend (Int) -> android.graphics.Bitmap? = { null },
    val onStrokeFinished: (Stroke, String) -> Unit,
    val onStrokeErased: (Stroke, String) -> Unit,
    val onStrokeSplit: (Stroke, List<Stroke>, String) -> Unit = { _, _, _ -> },
    val onLassoMove: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onLassoResize: (String, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    val onInsertActionChanged: (InsertAction) -> Unit = {},
    val onShapeObjectCreate: (String, ShapeType, Float, Float, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
    val onTextObjectCreate: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onImageObjectCreate: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onTextObjectEdit: (String, PageObject, String) -> Unit = { _, _, _ -> },
    val onObjectSelected: (String?) -> Unit = {},
    val onObjectTransformed: (String, PageObject, PageObject) -> Unit = { _, _, _ -> },
    val onDuplicateObject: (String, PageObject) -> Unit = { _, _ -> },
    val onDeleteObject: (String, PageObject) -> Unit = { _, _ -> },
    val onSegmentEraserEnabledChange: (Boolean) -> Unit = {},
    val onEraserModeChange: (EraserMode) -> Unit = {},
    val onEraserFilterChange: (EraserFilter) -> Unit = {},
    val onClearPageRequested: () -> Unit = {},
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
    val onUndoShortcut: () -> Unit = {},
    val onRedoShortcut: () -> Unit = {},
    val onDoubleTapZoomRequested: () -> Unit = {},
    val onDocumentZoomChange: (Float) -> Unit,
    val onDocumentPanXChange: (Float) -> Unit,
    val onViewportSizeChanged: (IntSize) -> Unit,
    val onVisiblePageChanged: (Int) -> Unit,
    val onVisiblePagesImmediateChanged: (IntRange) -> Unit,
    val onVisiblePagesPrefetchChanged: (IntRange) -> Unit,
    val onPageSelected: (Int) -> Unit,
    val onZoomPresetSelected: (Int) -> Unit = {},
    val onFitZoomRequested: () -> Unit = {},
    val onZoomLockChanged: (Boolean) -> Unit = {},
    val onTemplateChange: (PageTemplateState) -> Unit,
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
    val pdfTiles: Map<PdfTileKey, ValidatingTile>,
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
    val snackbarHostState: SnackbarHostState,
)

/**
 * UI state for multi-page LazyColumn layout.
 */
internal data class MultiPageUiState(
    val topBarState: NoteEditorTopBarState,
    val toolbarState: NoteEditorToolbarState,
    val multiPageContentState: MultiPageContentState,
    val snackbarHostState: SnackbarHostState,
)

internal data class BrushState(
    val activeBrush: Brush,
    val penBrush: Brush,
    val highlighterBrush: Brush,
    val lastNonEraserTool: Tool,
    val inputSettings: InputSettings,
    val onBrushChange: (Brush) -> Unit,
    val onInputSettingsChange: (InputSettings) -> Unit,
)

internal data class StrokeCallbacks(
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
    val onStrokeSplit: (Stroke, List<Stroke>, String) -> Unit,
)
