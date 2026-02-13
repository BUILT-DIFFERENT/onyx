@file:Suppress("FunctionName", "TooManyFunctions")

package com.onyx.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfRenderer
import com.onyx.android.recognition.MyScriptPageManager
import com.onyx.android.requireAppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.hypot

private const val MIN_PAN_FLING_SPEED_PX_PER_SECOND = 8.0
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val PAN_FLING_DECAY_RATE = -5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String,
    initialPageId: String? = null,
    onNavigateBack: () -> Unit,
) {
    NoteEditorStatusBarEffect()
    val appContext = LocalContext.current.applicationContext
    val appContainer = appContext.requireAppContainer()
    val repository = appContainer.noteRepository
    val noteDao = appContainer.database.noteDao()
    val pageDao = appContainer.database.pageDao()
    val pdfAssetStorage = remember { PdfAssetStorage(appContext) }

    val myScriptPageManager =
        remember {
            if (appContainer.myScriptEngine.isInitialized()) {
                MyScriptPageManager(
                    engine = appContainer.myScriptEngine.getEngine(),
                    context = appContext,
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
    val errorMessage by viewModel.errorMessage.collectAsState()
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
    if (errorMessage != null) {
        NoteEditorErrorDialog(
            message = errorMessage.orEmpty(),
            onDismiss = viewModel::clearError,
        )
    }
}

@Composable
private fun NoteEditorErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editor error") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
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
    var isStylusButtonEraserActive by remember { mutableStateOf(false) }
    var panFlingJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val viewportWidth = viewportSize.width.toFloat()
    val viewportHeight = viewportSize.height.toFloat()
    val pdfState =
        rememberPdfState(
            currentPage = pageState.currentPage,
            pdfAssetStorage = pdfAssetStorage,
            viewZoom = viewTransform.zoom,
        )
    val zoomLimits =
        remember(pdfState.pageWidth, pdfState.pageHeight, viewportSize) {
            computeZoomLimits(
                pageWidth = pdfState.pageWidth,
                pageHeight = pdfState.pageHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
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
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    zoomLimits = zoomLimits,
                )
        }
    }
    LaunchedEffect(pageState.currentPage?.pageId) {
        isStylusButtonEraserActive = false
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
            isStylusButtonEraserActive,
            brushState.onBrushChange,
        )
    val onTransformGesture: (Float, Float, Float, Float, Float) -> Unit =
        { zoomChange, panChangeX, panChangeY, centroidX, centroidY ->
            panFlingJob?.cancel()
            viewTransform =
                applyTransformGesture(
                    current = viewTransform,
                    zoomLimits = zoomLimits,
                    pageWidth = pdfState.pageWidth,
                    pageHeight = pdfState.pageHeight,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
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
                    var velocityMagnitude = hypot(currentVelocityX.toDouble(), currentVelocityY.toDouble())
                    while (velocityMagnitude > MIN_PAN_FLING_SPEED_PX_PER_SECOND) {
                        val frameNanos = withFrameNanos { it }
                        if (previousFrameNanos == 0L) {
                            previousFrameNanos = frameNanos
                            continue
                        }
                        val deltaSeconds = (frameNanos - previousFrameNanos) / NANOS_PER_SECOND
                        previousFrameNanos = frameNanos
                        if (deltaSeconds > 0f) {
                            viewTransform =
                                applyTransformGesture(
                                    current = viewTransform,
                                    zoomLimits = zoomLimits,
                                    pageWidth = pdfState.pageWidth,
                                    pageHeight = pdfState.pageHeight,
                                    viewportWidth = viewportWidth,
                                    viewportHeight = viewportHeight,
                                    gesture =
                                        TransformGesture(
                                            zoomChange = 1f,
                                            panChangeX = currentVelocityX * deltaSeconds,
                                            panChangeY = currentVelocityY * deltaSeconds,
                                            centroidX = 0f,
                                            centroidY = 0f,
                                        ),
                                )
                            val decayFactor = exp((PAN_FLING_DECAY_RATE * deltaSeconds).toDouble()).toFloat()
                            currentVelocityX *= decayFactor
                            currentVelocityY *= decayFactor
                        }
                        velocityMagnitude = hypot(currentVelocityX.toDouble(), currentVelocityY.toDouble())
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
            isStylusButtonEraserActive = isStylusButtonEraserActive,
            onStrokeFinished = strokeCallbacks.onStrokeFinished,
            onStrokeErased = strokeCallbacks.onStrokeErased,
            onStylusButtonEraserActiveChanged = { isStylusButtonEraserActive = it },
            onTransformGesture = onTransformGesture,
            onPanGestureEnd = onPanGestureEnd,
            onViewportSizeChanged = { viewportSize = it },
        )

    return NoteEditorUiState(
        topBarState = topBarState,
        toolbarState = toolbarState,
        contentState = contentState,
        transformState =
            rememberTransformState(
                viewTransform = viewTransform,
                zoomLimits = zoomLimits,
                pageWidth = pdfState.pageWidth,
                pageHeight = pdfState.pageHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            ) { updatedTransform ->
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
        onUpdateTitle = viewModel::updateNoteTitle,
        onUndo = undoController::undo,
        onRedo = undoController::redo,
        onToggleReadOnly = {},
    )

private fun buildToolbarState(
    brush: Brush,
    lastNonEraserTool: Tool,
    isStylusButtonEraserActive: Boolean,
    onBrushChange: (Brush) -> Unit,
): NoteEditorToolbarState =
    NoteEditorToolbarState(
        brush = brush,
        lastNonEraserTool = lastNonEraserTool,
        isStylusButtonEraserActive = isStylusButtonEraserActive,
        onBrushChange = onBrushChange,
    )
