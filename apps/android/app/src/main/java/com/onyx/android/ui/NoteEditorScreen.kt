@file:Suppress("FunctionName", "TooManyFunctions")

package com.onyx.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredText.TextChar
import com.onyx.android.OnyxApplication
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.InkAction
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfRenderer
import com.onyx.android.recognition.MyScriptPageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.hypot

private class NoteEditorViewModel(
    private val noteId: String,
    private val repository: NoteRepository,
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val myScriptPageManager: MyScriptPageManager?,
    private val initialPageId: String?,
) : ViewModel() {
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()
    private val _pages = MutableStateFlow<List<PageEntity>>(emptyList())
    val pages: StateFlow<List<PageEntity>> = _pages.asStateFlow()
    private val _noteTitle = MutableStateFlow("")
    val noteTitle: StateFlow<String> = _noteTitle.asStateFlow()
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()
    private val _currentPage = MutableStateFlow<PageEntity?>(null)
    val currentPage: StateFlow<PageEntity?> = _currentPage.asStateFlow()
    private var currentPageId: String? = null
    private var pendingInitialPageId: String? = initialPageId
    private val strokeWriteQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        myScriptPageManager?.onRecognitionUpdated = { pageId, text ->
            viewModelScope.launch {
                repository.updateRecognition(pageId, text, "myscript-4.3")
            }
        }
        viewModelScope.launch {
            for (task in strokeWriteQueue) {
                task()
            }
        }
        loadNote()
    }

    fun loadNote() {
        viewModelScope.launch {
            _noteTitle.value = noteDao.getById(noteId)?.title.orEmpty()
            refreshPages(selectPageId = pendingInitialPageId)
            pendingInitialPageId = null
        }
    }

    fun navigateBy(offset: Int) {
        if (_pages.value.isEmpty()) {
            return
        }
        val targetIndex = (_currentPageIndex.value + offset).coerceIn(0, _pages.value.lastIndex)
        if (targetIndex == _currentPageIndex.value) {
            return
        }
        _currentPageIndex.value = targetIndex
        viewModelScope.launch {
            setCurrentPage(_pages.value.getOrNull(targetIndex))
        }
    }

    fun createNewPage() {
        viewModelScope.launch {
            val maxIndex = pageDao.getMaxIndexForNote(noteId) ?: -1
            val newIndex = maxIndex + 1
            repository.createPageForNote(noteId, indexInNote = newIndex)
            refreshPages(selectIndex = newIndex)
        }
    }

    fun addStroke(
        stroke: Stroke,
        persist: Boolean,
        updateRecognition: Boolean = true,
    ) {
        _strokes.value = _strokes.value + stroke
        if (persist) {
            val pageId = currentPageId ?: return
            enqueueStrokeWrite {
                repository.saveStroke(pageId, stroke)
            }
        }
        if (updateRecognition) {
            myScriptPageManager?.addStroke(stroke)
        }
    }

    fun removeStroke(
        stroke: Stroke,
        persist: Boolean,
        updateRecognition: Boolean = true,
    ) {
        _strokes.value = _strokes.value - stroke
        if (persist) {
            enqueueStrokeWrite {
                repository.deleteStroke(stroke.id)
            }
        }
        if (updateRecognition) {
            myScriptPageManager?.onStrokeErased(stroke.id, _strokes.value)
        }
    }

    fun upgradePageToMixed(pageId: String) {
        viewModelScope.launch {
            val existingPage = _pages.value.firstOrNull { it.pageId == pageId } ?: return@launch
            if (existingPage.kind != "pdf") {
                return@launch
            }
            val locallyMixedPage =
                existingPage.copy(
                    kind = "mixed",
                    updatedAt = System.currentTimeMillis(),
                )
            replacePageInState(locallyMixedPage)
            if (currentPageId == pageId) {
                _currentPage.value = locallyMixedPage
                myScriptPageManager?.onPageEnter(pageId)
            }
            repository.upgradePageToMixed(pageId)
            val updatedPage = pageDao.getById(pageId)
            if (updatedPage != null) {
                replacePageInState(updatedPage)
                if (currentPageId == pageId) {
                    _currentPage.value = updatedPage
                }
            }
            android.util.Log.d("PageKind", "Updated page kind to mixed for pageId=$pageId")
        }
    }

    private fun replacePageInState(updatedPage: PageEntity) {
        val currentPages = _pages.value.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.pageId == updatedPage.pageId }
        if (pageIndex >= 0) {
            currentPages[pageIndex] = updatedPage
            _pages.value = currentPages
        }
    }

    private suspend fun refreshPages(
        selectIndex: Int? = null,
        selectPageId: String? = null,
    ) {
        val pages = pageDao.getPagesForNote(noteId).first()
        _pages.value = pages
        if (pages.isEmpty()) {
            _currentPageIndex.value = 0
            setCurrentPage(null)
            return
        }
        val pageIdIndex =
            selectPageId?.let { pageId ->
                pages.indexOfFirst { it.pageId == pageId }.takeIf { it >= 0 }
            }
        val resolvedIndex =
            pageIdIndex
                ?: selectIndex?.coerceIn(0, pages.lastIndex)
                ?: _currentPageIndex.value.coerceIn(0, pages.lastIndex)
        _currentPageIndex.value = resolvedIndex
        setCurrentPage(pages.getOrNull(resolvedIndex))
    }

    private suspend fun setCurrentPage(page: PageEntity?) {
        if (currentPageId != page?.pageId && currentPageId != null) {
            myScriptPageManager?.closeCurrentPage()
        }
        currentPageId = page?.pageId
        _currentPage.value = page
        if (page?.kind == "pdf") {
            _strokes.value = emptyList()
            return
        }
        currentPageId?.let { pageId ->
            myScriptPageManager?.onPageEnter(pageId)
            val loadedStrokes = repository.getStrokesForPage(pageId)
            _strokes.value = loadedStrokes
            loadedStrokes.forEach { stroke ->
                myScriptPageManager?.addStroke(stroke)
            }
        } ?: run {
            _strokes.value = emptyList()
        }
    }

    override fun onCleared() {
        myScriptPageManager?.closeCurrentPage()
        strokeWriteQueue.close()
        super.onCleared()
    }

    private fun enqueueStrokeWrite(task: suspend () -> Unit) {
        strokeWriteQueue.trySend {
            withContext(Dispatchers.IO) {
                task()
            }
        }
    }
}

private class NoteEditorViewModelFactory(
    private val noteId: String,
    private val repository: NoteRepository,
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val myScriptPageManager: MyScriptPageManager?,
    private val initialPageId: String?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NoteEditorViewModel::class.java))
        return NoteEditorViewModel(
            noteId,
            repository,
            noteDao,
            pageDao,
            myScriptPageManager,
            initialPageId,
        ) as T
    }
}

private class UndoController(
    private val viewModel: NoteEditorViewModel,
    private val maxUndoActions: Int,
) {
    val undoStack = mutableStateListOf<InkAction>()
    val redoStack = mutableStateListOf<InkAction>()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        when (action) {
            is InkAction.AddStroke -> viewModel.removeStroke(action.stroke, persist = true)
            is InkAction.RemoveStroke -> viewModel.addStroke(action.stroke, persist = true)
        }
        redoStack.add(action)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is InkAction.AddStroke -> viewModel.addStroke(action.stroke, persist = true)
            is InkAction.RemoveStroke -> viewModel.removeStroke(action.stroke, persist = true)
        }
        undoStack.add(action)
        trimUndoStack()
    }

    fun onStrokeFinished(
        stroke: Stroke,
        currentPage: PageEntity?,
    ) {
        viewModel.addStroke(stroke, persist = true)
        undoStack.add(InkAction.AddStroke(stroke))
        trimUndoStack()
        redoStack.clear()
        val pageId = currentPage?.pageId
        if (pageId != null && currentPage.kind == "pdf") {
            viewModel.upgradePageToMixed(pageId)
        }
        logStrokePoints(stroke)
    }

    fun onStrokeErased(stroke: Stroke) {
        viewModel.removeStroke(stroke, persist = true)
        undoStack.add(InkAction.RemoveStroke(stroke))
        trimUndoStack()
        redoStack.clear()
    }

    private fun trimUndoStack() {
        if (undoStack.size > maxUndoActions) {
            undoStack.removeAt(0)
        }
    }

    private fun logStrokePoints(stroke: Stroke) {
        val firstPoint = stroke.points.firstOrNull()
        val lastPoint = stroke.points.lastOrNull()
        if (firstPoint != null && lastPoint != null) {
            val message =
                "Saved stroke points in pt: " +
                    "start=(${firstPoint.x}, ${firstPoint.y}) " +
                    "end=(${lastPoint.x}, ${lastPoint.y})"
            android.util.Log.d("InkStroke", message)
        }
    }
}

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
    val onBrushChange: (Brush) -> Unit,
)

internal data class NoteEditorContentState(
    val isPdfPage: Boolean,
    val isReadOnly: Boolean,
    val pdfBitmap: android.graphics.Bitmap?,
    val pdfRenderer: PdfRenderer?,
    val currentPage: PageEntity?,
    val viewTransform: ViewTransform,
    val pageWidthDp: androidx.compose.ui.unit.Dp,
    val pageHeightDp: androidx.compose.ui.unit.Dp,
    val pageWidth: Float,
    val pageHeight: Float,
    val strokes: List<Stroke>,
    val brush: Brush,
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
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

private data class NoteEditorPageState(
    val noteTitle: String,
    val strokes: List<Stroke>,
    val pages: List<PageEntity>,
    val currentPageIndex: Int,
    val currentPage: PageEntity?,
)

private data class NoteEditorPdfState(
    val isPdfPage: Boolean,
    val pdfRenderer: PdfRenderer?,
    val pdfBitmap: android.graphics.Bitmap?,
    val pageWidthDp: androidx.compose.ui.unit.Dp,
    val pageHeightDp: androidx.compose.ui.unit.Dp,
    val pageWidth: Float,
    val pageHeight: Float,
)

private data class NoteEditorUiState(
    val topBarState: NoteEditorTopBarState,
    val toolbarState: NoteEditorToolbarState,
    val contentState: NoteEditorContentState,
    val transformState: androidx.compose.foundation.gestures.TransformableState,
)

private data class BrushState(
    val brush: Brush,
    val lastNonEraserTool: Tool,
    val onBrushChange: (Brush) -> Unit,
)

private data class StrokeCallbacks(
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String,
    initialPageId: String? = null,
    onNavigateBack: () -> Unit,
) {
    NoteEditorStatusBarEffect()
    val app = LocalContext.current.applicationContext as OnyxApplication
    val repository = app.noteRepository
    val noteDao = app.database.noteDao()
    val pageDao = app.database.pageDao()
    val pdfAssetStorage = remember { PdfAssetStorage(app) }

    val myScriptPageManager =
        remember {
            if (app.myScriptEngine.isInitialized()) {
                MyScriptPageManager(
                    engine = app.myScriptEngine.getEngine(),
                    context = app,
                )
            } else {
                null
            }
        }

    val viewModel: NoteEditorViewModel =
        viewModel(
            key = "NoteEditorViewModel_$noteId",
            factory =
                NoteEditorViewModelFactory(
                    noteId,
                    repository,
                    noteDao,
                    pageDao,
                    myScriptPageManager,
                    initialPageId,
                ),
        )
    NoteEditorScreenContent(
        noteId = noteId,
        viewModel = viewModel,
        pdfAssetStorage = pdfAssetStorage,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun NoteEditorStatusBarEffect() {
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(context, view) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window == null) {
            return@DisposableEffect onDispose {}
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val previousSystemBarsBehavior = insetsController.systemBarsBehavior
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = previousSystemBarsBehavior
        }
    }
}

@Composable
private fun NoteEditorScreenContent(
    noteId: String,
    viewModel: NoteEditorViewModel,
    pdfAssetStorage: PdfAssetStorage,
    onNavigateBack: () -> Unit,
) {
    val uiState =
        rememberNoteEditorUiState(
            noteId = noteId,
            viewModel = viewModel,
            pdfAssetStorage = pdfAssetStorage,
            onNavigateBack = onNavigateBack,
        )
    NoteEditorScaffold(
        topBarState = uiState.topBarState,
        toolbarState = uiState.toolbarState,
        contentState = uiState.contentState,
        transformState = uiState.transformState,
    )
}

@Composable
@Suppress("LongMethod")
private fun rememberNoteEditorUiState(
    noteId: String,
    viewModel: NoteEditorViewModel,
    pdfAssetStorage: PdfAssetStorage,
    onNavigateBack: () -> Unit,
): NoteEditorUiState {
    val brushState = rememberBrushState()
    val pageState = rememberPageState(viewModel)
    val undoController = remember(viewModel) { UndoController(viewModel, MAX_UNDO_ACTIONS) }
    var isReadOnly by rememberSaveable { mutableStateOf(false) }
    var viewTransform by remember { mutableStateOf(ViewTransform.DEFAULT) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var panFlingJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val pdfState =
        rememberPdfState(
            currentPage = pageState.currentPage,
            pdfAssetStorage = pdfAssetStorage,
            viewZoom = viewTransform.zoom,
        )
    DisposePdfRenderer(pdfState.pdfRenderer)
    ObserveNoteEditorLifecycle(
        noteId = noteId,
        currentPageId = pageState.currentPage?.pageId,
        viewModel = viewModel,
        undoController = undoController,
    )
    val strokeCallbacks = buildStrokeCallbacks(undoController, pageState.currentPage)
    LaunchedEffect(pageState.currentPage?.pageId, viewportSize) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            panFlingJob?.cancel()
            viewTransform =
                fitTransformToViewport(
                    pageWidth = pdfState.pageWidth,
                    pageHeight = pdfState.pageHeight,
                    viewportWidth = viewportSize.width.toFloat(),
                    viewportHeight = viewportSize.height.toFloat(),
                )
        }
    }
    val topBarState =
        buildTopBarState(
            pageState,
            undoController,
            onNavigateBack,
            viewModel,
        ).copy(
            isReadOnly = isReadOnly,
            onToggleReadOnly = { isReadOnly = !isReadOnly },
        )
    val toolbarState =
        buildToolbarState(
            brushState.brush,
            brushState.lastNonEraserTool,
            brushState.onBrushChange,
        )
    val onTransformGesture: (Float, Float, Float, Float, Float) -> Unit =
        { zoomChange, panChangeX, panChangeY, centroidX, centroidY ->
            panFlingJob?.cancel()
            viewTransform =
                applyTransformGesture(
                    current = viewTransform,
                    gesture =
                        TransformGesture(
                            zoomChange,
                            panChangeX,
                            panChangeY,
                            centroidX,
                            centroidY,
                        ),
                )
        }
    val onPanGestureEnd: (Float, Float) -> Unit =
        { velocityX, velocityY ->
            panFlingJob?.cancel()
            panFlingJob =
                coroutineScope.launch {
                    var currentVelocityX = velocityX
                    var currentVelocityY = velocityY
                    var previousFrameNanos = 0L
                    while (hypot(currentVelocityX.toDouble(), currentVelocityY.toDouble()) > 8.0) {
                        val frameNanos = withFrameNanos { it }
                        if (previousFrameNanos == 0L) {
                            previousFrameNanos = frameNanos
                            continue
                        }
                        val deltaSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                        previousFrameNanos = frameNanos
                        if (deltaSeconds <= 0f) {
                            continue
                        }
                        viewTransform =
                            applyTransformGesture(
                                current = viewTransform,
                                gesture =
                                    TransformGesture(
                                        zoomChange = 1f,
                                        panChangeX = currentVelocityX * deltaSeconds,
                                        panChangeY = currentVelocityY * deltaSeconds,
                                        centroidX = 0f,
                                        centroidY = 0f,
                                    ),
                            )
                        val decayFactor = exp((-5f * deltaSeconds).toDouble()).toFloat()
                        currentVelocityX *= decayFactor
                        currentVelocityY *= decayFactor
                    }
                }
        }
    val contentState =
        NoteEditorContentState(
            isPdfPage = pdfState.isPdfPage,
            isReadOnly = isReadOnly,
            pdfBitmap = pdfState.pdfBitmap,
            pdfRenderer = pdfState.pdfRenderer,
            currentPage = pageState.currentPage,
            viewTransform = viewTransform,
            pageWidthDp = pdfState.pageWidthDp,
            pageHeightDp = pdfState.pageHeightDp,
            pageWidth = pdfState.pageWidth,
            pageHeight = pdfState.pageHeight,
            strokes = pageState.strokes,
            brush = brushState.brush,
            onStrokeFinished = strokeCallbacks.onStrokeFinished,
            onStrokeErased = strokeCallbacks.onStrokeErased,
            onTransformGesture = onTransformGesture,
            onPanGestureEnd = onPanGestureEnd,
            onViewportSizeChanged = { viewportSize = it },
        )

    return NoteEditorUiState(
        topBarState = topBarState,
        toolbarState = toolbarState,
        contentState = contentState,
        transformState =
            rememberTransformState(viewTransform) { updatedTransform ->
                panFlingJob?.cancel()
                viewTransform = updatedTransform
            },
    )
}

@Composable
private fun rememberBrushState(): BrushState {
    var brush by remember { mutableStateOf(Brush()) }
    var lastNonEraserTool by remember { mutableStateOf(brush.tool) }
    LaunchedEffect(brush.tool) {
        if (brush.tool != Tool.ERASER) {
            lastNonEraserTool = brush.tool
        }
    }
    return BrushState(
        brush = brush,
        lastNonEraserTool = lastNonEraserTool,
        onBrushChange = { updatedBrush -> brush = updatedBrush },
    )
}

@Composable
private fun ObserveNoteEditorLifecycle(
    noteId: String,
    currentPageId: String?,
    viewModel: NoteEditorViewModel,
    undoController: UndoController,
) {
    LaunchedEffect(noteId) {
        viewModel.loadNote()
    }
    LaunchedEffect(currentPageId) {
        undoController.clear()
    }
}

private fun buildStrokeCallbacks(
    undoController: UndoController,
    currentPage: PageEntity?,
): StrokeCallbacks =
    StrokeCallbacks(
        onStrokeFinished = { newStroke ->
            undoController.onStrokeFinished(newStroke, currentPage)
        },
        onStrokeErased = { erasedStroke ->
            undoController.onStrokeErased(erasedStroke)
        },
    )

@Composable
private fun rememberPageState(viewModel: NoteEditorViewModel): NoteEditorPageState {
    val noteTitle by viewModel.noteTitle.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    return NoteEditorPageState(
        noteTitle = noteTitle,
        strokes = strokes,
        pages = pages,
        currentPageIndex = currentPageIndex,
        currentPage = currentPage,
    )
}

@Composable
private fun rememberPdfState(
    currentPage: PageEntity?,
    pdfAssetStorage: PdfAssetStorage,
    viewZoom: Float,
): NoteEditorPdfState {
    val isPdfPage = currentPage?.kind == "pdf" || currentPage?.kind == "mixed"
    val pageWidth = currentPage?.width ?: 0f
    val pageHeight = currentPage?.height ?: 0f
    val pdfRenderer =
        remember(currentPage?.pdfAssetId) {
            currentPage?.pdfAssetId?.let { assetId ->
                PdfRenderer(pdfAssetStorage.getFileForAsset(assetId))
            }
        }
    val pdfBitmap =
        rememberPdfBitmap(
            isPdfPage = isPdfPage,
            currentPage = currentPage,
            pdfRenderer = pdfRenderer,
            viewZoom = viewZoom,
        )
    val pageWidthDp = with(LocalDensity.current) { pageWidth.toDp() }
    val pageHeightDp = with(LocalDensity.current) { pageHeight.toDp() }
    return NoteEditorPdfState(
        isPdfPage = isPdfPage,
        pdfRenderer = pdfRenderer,
        pdfBitmap = pdfBitmap,
        pageWidthDp = pageWidthDp,
        pageHeightDp = pageHeightDp,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun buildTopBarState(
    pageState: NoteEditorPageState,
    undoController: UndoController,
    onNavigateBack: () -> Unit,
    viewModel: NoteEditorViewModel,
): NoteEditorTopBarState =
    NoteEditorTopBarState(
        noteTitle = pageState.noteTitle,
        totalPages = pageState.pages.size,
        currentPageIndex = pageState.currentPageIndex,
        isReadOnly = false,
        canNavigatePrevious = pageState.currentPageIndex > 0,
        canNavigateNext = pageState.currentPageIndex < pageState.pages.size - 1,
        canUndo = undoController.undoStack.isNotEmpty(),
        canRedo = undoController.redoStack.isNotEmpty(),
        onNavigateBack = onNavigateBack,
        onNavigatePrevious = { viewModel.navigateBy(-1) },
        onNavigateNext = { viewModel.navigateBy(1) },
        onCreatePage = { viewModel.createNewPage() },
        onUndo = undoController::undo,
        onRedo = undoController::redo,
        onToggleReadOnly = {},
    )

private fun buildToolbarState(
    brush: Brush,
    lastNonEraserTool: Tool,
    onBrushChange: (Brush) -> Unit,
): NoteEditorToolbarState =
    NoteEditorToolbarState(
        brush = brush,
        lastNonEraserTool = lastNonEraserTool,
        onBrushChange = onBrushChange,
    )
