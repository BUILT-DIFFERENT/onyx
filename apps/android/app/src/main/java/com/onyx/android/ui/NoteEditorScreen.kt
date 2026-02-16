@file:Suppress("FunctionName", "TooManyFunctions")

package com.onyx.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.pdf.OutlineItem
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfIncorrectPasswordException
import com.onyx.android.pdf.PdfPasswordRequiredException
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.pdf.PdfiumRenderer
import com.onyx.android.recognition.MyScriptPageManager
import com.onyx.android.requireAppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.hypot

private const val MIN_PAN_FLING_SPEED_PX_PER_SECOND = 8.0
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val PAN_FLING_DECAY_RATE = -5f
private const val NOTE_EDITOR_LOG_TAG = "NoteEditorScreen"
private const val PDF_OPEN_FAILED_MESSAGE = "Unable to open this PDF."
private const val PDF_PASSWORD_INCORRECT_MESSAGE = "Incorrect PDF password. Please try again."
private const val ENABLE_STACKED_PAGES = true

internal sealed interface PdfOpenFailureUiAction {
    data class PromptForPassword(
        val isIncorrectPassword: Boolean,
    ) : PdfOpenFailureUiAction

    data class ShowOpenError(
        val message: String,
    ) : PdfOpenFailureUiAction
}

private data class EditorPdfPasswordPromptState(
    val assetId: String,
    val showIncorrectMessage: Boolean = false,
)

internal fun mapPdfOpenFailureToUiAction(error: Throwable): PdfOpenFailureUiAction =
    when (error) {
        is PdfPasswordRequiredException -> PdfOpenFailureUiAction.PromptForPassword(false)
        is PdfIncorrectPasswordException -> PdfOpenFailureUiAction.PromptForPassword(true)
        else -> {
            val message = error.message?.takeIf { it.isNotBlank() } ?: PDF_OPEN_FAILED_MESSAGE
            PdfOpenFailureUiAction.ShowOpenError(message)
        }
    }

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
    val pdfPasswordStore = appContainer.pdfPasswordStore
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
        pdfPasswordStore = pdfPasswordStore,
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
@Suppress("LongMethod")
private fun NoteEditorScreenContent(
    noteId: String,
    viewModel: NoteEditorViewModel,
    pdfAssetStorage: PdfAssetStorage,
    pdfPasswordStore: PdfPasswordStore,
    onNavigateBack: () -> Unit,
) {
    val errorMessage by viewModel.errorMessage.collectAsState()
    var pdfPasswordPrompt by remember { mutableStateOf<EditorPdfPasswordPromptState?>(null) }
    var pdfPasswordInput by rememberSaveable { mutableStateOf("") }
    var pdfOpenErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfOpenRetryNonce by rememberSaveable { mutableStateOf(0) }
    var showOutlineSheet by rememberSaveable { mutableStateOf(false) }
    var outlineItems by remember { mutableStateOf<List<OutlineItem>>(emptyList()) }
    if (ENABLE_STACKED_PAGES) {
        val uiState =
            rememberMultiPageUiState(
                noteId = noteId,
                viewModel = viewModel,
                pdfAssetStorage = pdfAssetStorage,
                pdfPasswordStore = pdfPasswordStore,
                pdfOpenRetryNonce = pdfOpenRetryNonce,
                onPdfPasswordRequired = { assetId, isIncorrectPassword ->
                    pdfPasswordPrompt =
                        EditorPdfPasswordPromptState(
                            assetId = assetId,
                            showIncorrectMessage = isIncorrectPassword,
                        )
                    pdfPasswordInput = ""
                },
                onPdfOpenError = { message ->
                    pdfOpenErrorMessage = message
                },
                onNavigateBack = onNavigateBack,
                onOpenOutline = { showOutlineSheet = true },
                onLoadOutline = { renderer -> outlineItems = renderer.getTableOfContents() },
            )
        MultiPageEditorScaffold(
            topBarState = uiState.topBarState,
            toolbarState = uiState.toolbarState,
            multiPageContentState = uiState.multiPageContentState,
        )
        if (showOutlineSheet) {
            PdfOutlineSheet(
                outlineItems = outlineItems,
                onOutlineItemClick = { item ->
                    showOutlineSheet = false
                    if (item.pageIndex >= 0) {
                        viewModel.navigateBy(item.pageIndex - uiState.topBarState.currentPageIndex)
                    }
                },
                onDismiss = { showOutlineSheet = false },
            )
        }
    } else {
        val uiState =
            rememberNoteEditorUiState(
                noteId = noteId,
                viewModel = viewModel,
                pdfAssetStorage = pdfAssetStorage,
                pdfPasswordStore = pdfPasswordStore,
                pdfOpenRetryNonce = pdfOpenRetryNonce,
                onPdfPasswordRequired = { assetId, isIncorrectPassword ->
                    pdfPasswordPrompt =
                        EditorPdfPasswordPromptState(
                            assetId = assetId,
                            showIncorrectMessage = isIncorrectPassword,
                        )
                    pdfPasswordInput = ""
                },
                onPdfOpenError = { message ->
                    pdfOpenErrorMessage = message
                },
                onNavigateBack = onNavigateBack,
                onOpenOutline = { showOutlineSheet = true },
                onLoadOutline = { renderer -> outlineItems = renderer.getTableOfContents() },
            )
        NoteEditorScaffold(
            topBarState = uiState.topBarState,
            toolbarState = uiState.toolbarState,
            contentState = uiState.contentState,
            transformState = uiState.transformState,
        )
        if (showOutlineSheet) {
            PdfOutlineSheet(
                outlineItems = outlineItems,
                onOutlineItemClick = { item ->
                    showOutlineSheet = false
                    if (item.pageIndex >= 0) {
                        viewModel.navigateBy(item.pageIndex - uiState.topBarState.currentPageIndex)
                    }
                },
                onDismiss = { showOutlineSheet = false },
            )
        }
    }
    if (pdfPasswordPrompt != null) {
        NoteEditorPdfPasswordDialog(
            password = pdfPasswordInput,
            showIncorrectMessage = pdfPasswordPrompt?.showIncorrectMessage == true,
            onPasswordChange = { value -> pdfPasswordInput = value },
            onConfirm = {
                val prompt = pdfPasswordPrompt ?: return@NoteEditorPdfPasswordDialog
                pdfPasswordStore.rememberPassword(prompt.assetId, pdfPasswordInput)
                pdfPasswordPrompt = null
                pdfOpenRetryNonce += 1
            },
            onDismiss = {
                pdfPasswordPrompt = null
            },
        )
    }
    if (pdfOpenErrorMessage != null) {
        NoteEditorErrorDialog(
            message = pdfOpenErrorMessage.orEmpty(),
            onDismiss = { pdfOpenErrorMessage = null },
        )
    }
    if (errorMessage != null) {
        NoteEditorErrorDialog(
            message = errorMessage.orEmpty(),
            onDismiss = viewModel::clearError,
        )
    }
}

@Composable
private fun NoteEditorPdfPasswordDialog(
    password: String,
    showIncorrectMessage: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF password required") },
        text = {
            androidx.compose.foundation.layout.Column {
                if (showIncorrectMessage) {
                    Text(PDF_PASSWORD_INCORRECT_MESSAGE)
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Open")
            }
        },
    )
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
@Suppress("LongMethod", "LongParameterList")
private fun rememberNoteEditorUiState(
    noteId: String,
    viewModel: NoteEditorViewModel,
    pdfAssetStorage: PdfAssetStorage,
    pdfPasswordStore: PdfPasswordStore,
    pdfOpenRetryNonce: Int,
    onPdfPasswordRequired: (assetId: String, isIncorrectPassword: Boolean) -> Unit,
    onPdfOpenError: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOutline: () -> Unit,
    onLoadOutline: (PdfiumRenderer) -> Unit,
): NoteEditorUiState {
    val appContext = LocalContext.current.applicationContext
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
            appContext = appContext,
            currentPage = pageState.currentPage,
            pdfAssetStorage = pdfAssetStorage,
            pdfPasswordStore = pdfPasswordStore,
            pdfOpenRetryNonce = pdfOpenRetryNonce,
            onPdfPasswordRequired = onPdfPasswordRequired,
            onPdfOpenError = onPdfOpenError,
            viewTransform = viewTransform,
            viewportSize = viewportSize,
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
    // Track visible pages for virtualized stroke loading
    LaunchedEffect(pageState.currentPageIndex) {
        viewModel.onVisiblePagesChanged(pageState.currentPageIndex..pageState.currentPageIndex)
    }
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
            isPdfDocument = pdfState.isPdfPage,
            onOpenOutline = {
                pdfState.pdfRenderer?.let { renderer ->
                    onLoadOutline(renderer as PdfiumRenderer)
                }
                onOpenOutline()
            },
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
    // Generate thumbnails for PDF documents
    val thumbnails =
        remember(pdfState.pdfRenderer, pdfState.isPdfPage) {
            val renderer = pdfState.pdfRenderer
            if (renderer != null && pdfState.isPdfPage) {
                val pageCount = renderer.getPageCount()
                (0 until pageCount).map { pageIndex ->
                    val bounds = renderer.getPageBounds(pageIndex)
                    val aspectRatio = bounds.first / bounds.second.coerceAtLeast(1f)
                    ThumbnailItem(
                        pageIndex = pageIndex,
                        bitmap = renderer.renderThumbnail(pageIndex),
                        aspectRatio = aspectRatio,
                    )
                }
            } else {
                emptyList()
            }
        }

    val contentState =
        NoteEditorContentState(
            isPdfPage = pdfState.isPdfPage,
            isReadOnly = isReadOnly,
            pdfTiles = pdfState.pdfTiles,
            pdfRenderScaleBucket = pdfState.pdfRenderScaleBucket,
            pdfPreviousScaleBucket = pdfState.pdfPreviousScaleBucket,
            pdfTileSizePx = pdfState.pdfTileSizePx,
            pdfCrossfadeProgress = pdfState.pdfCrossfadeProgress,
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
            interactionMode = InteractionMode.DRAW,
            thumbnails = thumbnails,
            currentPageIndex = pageState.currentPageIndex,
            onStrokeFinished = strokeCallbacks.onStrokeFinished,
            onStrokeErased = strokeCallbacks.onStrokeErased,
            onStylusButtonEraserActiveChanged = { isStylusButtonEraserActive = it },
            onTransformGesture = onTransformGesture,
            onPanGestureEnd = onPanGestureEnd,
            onViewportSizeChanged = { viewportSize = it },
            onPageSelected = { targetIndex -> viewModel.navigateBy(targetIndex - pageState.currentPageIndex) },
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
@Suppress("LongMethod", "LongParameterList")
private fun rememberMultiPageUiState(
    noteId: String,
    viewModel: NoteEditorViewModel,
    pdfAssetStorage: PdfAssetStorage,
    pdfPasswordStore: PdfPasswordStore,
    pdfOpenRetryNonce: Int,
    onPdfPasswordRequired: (assetId: String, isIncorrectPassword: Boolean) -> Unit,
    onPdfOpenError: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOutline: () -> Unit,
    onLoadOutline: (PdfiumRenderer) -> Unit,
): MultiPageUiState {
    val appContext = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val brushState = rememberBrushState()
    val pageState = rememberPageState(viewModel)
    val undoController = remember(viewModel) { UndoController(viewModel, MAX_UNDO_ACTIONS, useMultiPage = true) }
    var isReadOnly by rememberSaveable { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isStylusButtonEraserActive by remember { mutableStateOf(false) }
    var visibleRange by remember { mutableStateOf(0..0) }
    val pageStrokesCache by viewModel.pageStrokesCache.collectAsState()
    val pageById = remember(pageState.pages) { pageState.pages.associateBy { it.pageId } }
    val viewportWidth = viewportSize.width.toFloat()

    val pdfAssetId =
        pageState.pages.firstOrNull { it.kind == "pdf" || it.kind == "mixed" }?.pdfAssetId
    val rendererResult =
        remember(pdfAssetId, pdfOpenRetryNonce) {
            pdfAssetId?.let { assetId ->
                runCatching {
                    PdfiumRenderer(
                        context = appContext,
                        pdfFile = pdfAssetStorage.getFileForAsset(assetId),
                        password = pdfPasswordStore.getPassword(assetId),
                    )
                }
            }
        }
    val pdfRenderer = rendererResult?.getOrNull()
    LaunchedEffect(pdfAssetId, rendererResult?.exceptionOrNull()) {
        val assetId = pdfAssetId ?: return@LaunchedEffect
        val error = rendererResult?.exceptionOrNull() ?: return@LaunchedEffect
        Log.e(NOTE_EDITOR_LOG_TAG, "Failed to open PDF asset $assetId", error)
        when (val action = mapPdfOpenFailureToUiAction(error)) {
            is PdfOpenFailureUiAction.PromptForPassword ->
                onPdfPasswordRequired(assetId, action.isIncorrectPassword)
            is PdfOpenFailureUiAction.ShowOpenError -> onPdfOpenError(action.message)
        }
    }
    DisposePdfRenderer(pdfRenderer)
    ObserveNoteEditorLifecycle(
        noteId = noteId,
        currentPageId = pageState.currentPage?.pageId,
        viewModel = viewModel,
        undoController = undoController,
    )
    LaunchedEffect(pageState.currentPage?.pageId) {
        isStylusButtonEraserActive = false
    }

    val pages =
        pageState.pages.mapIndexed { index, page ->
            val pageWidth = page.width
            val pageHeight = page.height
            val scale =
                if (viewportWidth > 0f && pageWidth > 0f) {
                    viewportWidth / pageWidth
                } else {
                    1f
                }
            val renderTransform =
                ViewTransform(
                    zoom = scale,
                    panX = 0f,
                    panY = 0f,
                )
            val pageWidthDp = with(density) { (pageWidth * scale).toDp() }
            val pageHeightDp = with(density) { (pageHeight * scale).toDp() }
            PageItemState(
                page = page,
                pageWidthDp = pageWidthDp,
                pageHeightDp = pageHeightDp,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                strokes = pageStrokesCache[page.pageId].orEmpty(),
                isPdfPage = page.kind == "pdf" || page.kind == "mixed",
                isVisible = index in visibleRange,
                renderTransform = renderTransform,
            )
        }

    val topBarState =
        buildTopBarState(
            pageState,
            undoController,
            onNavigateBack,
            viewModel,
            isPdfDocument = pdfRenderer != null,
            onOpenOutline = {
                pdfRenderer?.let(onLoadOutline)
                onOpenOutline()
            },
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

    val thumbnails =
        remember(pdfRenderer) {
            val renderer = pdfRenderer
            if (renderer != null) {
                val pageCount = renderer.getPageCount()
                (0 until pageCount).map { pageIndex ->
                    val bounds = renderer.getPageBounds(pageIndex)
                    val aspectRatio = bounds.first / bounds.second.coerceAtLeast(1f)
                    ThumbnailItem(
                        pageIndex = pageIndex,
                        bitmap = renderer.renderThumbnail(pageIndex),
                        aspectRatio = aspectRatio,
                    )
                }
            } else {
                emptyList()
            }
        }

    val multiPageContentState =
        MultiPageContentState(
            pages = pages,
            isReadOnly = isReadOnly,
            brush = brushState.brush,
            isStylusButtonEraserActive = isStylusButtonEraserActive,
            interactionMode = InteractionMode.SCROLL,
            pdfRenderer = pdfRenderer,
            firstVisiblePageIndex = pageState.currentPageIndex,
            thumbnails = thumbnails,
            onStrokeFinished = { stroke, pageId ->
                val page = pageById[pageId]
                undoController.onStrokeFinished(stroke, page)
            },
            onStrokeErased = { stroke, pageId ->
                undoController.onStrokeErased(stroke, pageId)
            },
            onStylusButtonEraserActiveChanged = { isStylusButtonEraserActive = it },
            onTransformGesture = { _, _, _, _, _ -> },
            onPanGestureEnd = { _, _ -> },
            onViewportSizeChanged = { viewportSize = it },
            onVisiblePageChanged = viewModel::setVisiblePageIndex,
            onVisiblePagesChanged = { range ->
                visibleRange = range
                viewModel.onVisiblePagesChanged(range)
            },
            onPageSelected = { targetIndex ->
                viewModel.navigateBy(targetIndex - pageState.currentPageIndex)
            },
        )

    return MultiPageUiState(
        topBarState = topBarState,
        toolbarState = toolbarState,
        multiPageContentState = multiPageContentState,
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
            val pageId = currentPage?.pageId ?: return@StrokeCallbacks
            undoController.onStrokeErased(erasedStroke, pageId)
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
@Suppress("LongMethod", "LongParameterList")
private fun rememberPdfState(
    appContext: Context,
    currentPage: PageEntity?,
    pdfAssetStorage: PdfAssetStorage,
    pdfPasswordStore: PdfPasswordStore,
    pdfOpenRetryNonce: Int,
    onPdfPasswordRequired: (assetId: String, isIncorrectPassword: Boolean) -> Unit,
    onPdfOpenError: (String) -> Unit,
    viewTransform: ViewTransform,
    viewportSize: IntSize,
): NoteEditorPdfState {
    val isPdfPage = currentPage?.kind == "pdf" || currentPage?.kind == "mixed"
    val pageWidth = currentPage?.width ?: 0f
    val pageHeight = currentPage?.height ?: 0f
    val pdfAssetId = currentPage?.pdfAssetId
    val rendererResult =
        remember(pdfAssetId, pdfOpenRetryNonce) {
            pdfAssetId?.let { assetId ->
                runCatching {
                    PdfiumRenderer(
                        context = appContext,
                        pdfFile = pdfAssetStorage.getFileForAsset(assetId),
                        password = pdfPasswordStore.getPassword(assetId),
                    )
                }
            }
        }
    val pdfRenderer = rendererResult?.getOrNull()
    LaunchedEffect(pdfAssetId, rendererResult?.exceptionOrNull()) {
        val assetId = pdfAssetId ?: return@LaunchedEffect
        val error = rendererResult?.exceptionOrNull() ?: return@LaunchedEffect
        Log.e(NOTE_EDITOR_LOG_TAG, "Failed to open PDF asset $assetId", error)
        when (val action = mapPdfOpenFailureToUiAction(error)) {
            is PdfOpenFailureUiAction.PromptForPassword ->
                onPdfPasswordRequired(assetId, action.isIncorrectPassword)
            is PdfOpenFailureUiAction.ShowOpenError -> onPdfOpenError(action.message)
        }
    }
    val pdfBitmap =
        rememberPdfBitmap(
            isPdfPage = isPdfPage,
            currentPage = currentPage,
            pdfRenderer = pdfRenderer,
            viewZoom = viewTransform.zoom,
        )
    val pdfTileState =
        rememberPdfTiles(
            isPdfPage = isPdfPage,
            currentPage = currentPage,
            pdfRenderer = pdfRenderer,
            viewTransform = viewTransform,
            viewportSize = viewportSize,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )
    val pageWidthDp = with(LocalDensity.current) { pageWidth.toDp() }
    val pageHeightDp = with(LocalDensity.current) { pageHeight.toDp() }
    return NoteEditorPdfState(
        isPdfPage = isPdfPage,
        pdfRenderer = pdfRenderer,
        pdfTiles = pdfTileState.tiles,
        pdfRenderScaleBucket = pdfTileState.scaleBucket,
        pdfPreviousScaleBucket = pdfTileState.previousScaleBucket,
        pdfTileSizePx = pdfTileState.tileSizePx,
        pdfCrossfadeProgress = pdfTileState.crossfadeProgress,
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

@Suppress("LongParameterList")
private fun buildTopBarState(
    pageState: NoteEditorPageState,
    undoController: UndoController,
    onNavigateBack: () -> Unit,
    viewModel: NoteEditorViewModel,
    isPdfDocument: Boolean,
    onOpenOutline: () -> Unit,
): NoteEditorTopBarState =
    NoteEditorTopBarState(
        noteTitle = pageState.noteTitle,
        totalPages = pageState.pages.size,
        currentPageIndex = pageState.currentPageIndex,
        isReadOnly = false,
        isPdfDocument = isPdfDocument,
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
        onOpenOutline = onOpenOutline,
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
