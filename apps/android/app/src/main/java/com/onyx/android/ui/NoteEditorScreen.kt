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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.repository.EditorSettings
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.TransformGesture
import com.onyx.android.pdf.OutlineItem
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfIncorrectPasswordException
import com.onyx.android.pdf.PdfPasswordRequiredException
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.pdf.openPdfDocumentRenderer
import com.onyx.android.pdf.renderThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.hypot

private const val MIN_PAN_FLING_SPEED_PX_PER_SECOND = 8.0
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val PAN_FLING_DECAY_RATE = -5f
private const val NOTE_EDITOR_LOG_TAG = "NoteEditorScreen"
private const val PDF_OPEN_FAILED_MESSAGE = "Unable to open this PDF."
private const val PDF_PASSWORD_INCORRECT_MESSAGE = "Incorrect PDF password. Please try again."
private const val ENABLE_STACKED_PAGES = true
private const val STACKED_DOCUMENT_MIN_ZOOM = 1f
private const val STACKED_DOCUMENT_MAX_ZOOM = 4f

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
        is PdfPasswordRequiredException -> {
            PdfOpenFailureUiAction.PromptForPassword(false)
        }

        is PdfIncorrectPasswordException -> {
            PdfOpenFailureUiAction.PromptForPassword(true)
        }

        else -> {
            val message = error.message?.takeIf { it.isNotBlank() } ?: PDF_OPEN_FAILED_MESSAGE
            PdfOpenFailureUiAction.ShowOpenError(message)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
) {
    NoteEditorStatusBarEffect()
    val viewModel: NoteEditorViewModel = hiltViewModel()
    NoteEditorScreenContent(
        noteId = noteId,
        viewModel = viewModel,
        pdfAssetStorage = viewModel.pdfAssetStorage,
        pdfPasswordStore = viewModel.pdfPasswordStore,
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
    val editorSettings by viewModel.editorSettings.collectAsState()
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
                editorSettings = editorSettings,
                onEditorSettingsChange = viewModel::updateEditorSettings,
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
                editorSettings = editorSettings,
                onEditorSettingsChange = viewModel::updateEditorSettings,
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
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
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
    onLoadOutline: (PdfDocumentRenderer) -> Unit,
    editorSettings: EditorSettings,
    onEditorSettingsChange: (EditorSettings) -> Unit,
): NoteEditorUiState {
    val appContext = LocalContext.current.applicationContext
    val brushState =
        rememberBrushState(
            editorSettings = editorSettings,
            onSettingsChange = onEditorSettingsChange,
        )
    val pageState = rememberPageState(viewModel)
    val undoController = remember(viewModel) { UndoController(viewModel, MAX_UNDO_ACTIONS) }
    var isReadOnly by rememberSaveable { mutableStateOf(false) }
    var isTextSelectionMode by rememberSaveable { mutableStateOf(false) }
    var viewTransform by remember { mutableStateOf(ViewTransform.DEFAULT) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isStylusButtonEraserActive by remember { mutableStateOf(false) }
    var templateState by rememberSaveable { mutableStateOf(PageTemplateState.BLANK) }
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
        isTextSelectionMode = false
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
                    onLoadOutline(renderer)
                }
                onOpenOutline()
            },
        ).copy(
            isReadOnly = isReadOnly,
            onToggleReadOnly = { isReadOnly = !isReadOnly },
            isTextSelectionMode = isTextSelectionMode,
            onToggleTextSelectionMode = {
                if (pdfState.isPdfPage) {
                    isTextSelectionMode = !isTextSelectionMode
                }
            },
            onJumpToPage = { targetPageIndex ->
                val offset = targetPageIndex - pageState.currentPageIndex
                if (offset != 0) {
                    viewModel.navigateBy(offset)
                }
            },
        )
    val toolbarState =
        buildToolbarState(
            brushState.activeBrush,
            brushState.lastNonEraserTool,
            isStylusButtonEraserActive,
            templateState,
            brushState.onBrushChange,
            { templateState = it },
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
    val thumbnailCache = remember(pdfState.pdfRenderer) { mutableStateMapOf<Int, android.graphics.Bitmap?>() }

    // Build thumbnail metadata for PDF documents. Bitmaps load lazily.
    val thumbnails =
        remember(pdfState.pdfRenderer, pdfState.isPdfPage) {
            val renderer = pdfState.pdfRenderer
            if (renderer != null && pdfState.isPdfPage) {
                val pageCount = renderer.getPageCount()
                (0 until pageCount).map { pageIndex ->
                    val bounds = renderer.getPageBounds(pageIndex)
                    val aspectRatio = bounds.width() / bounds.height().coerceAtLeast(1f)
                    ThumbnailItem(
                        pageIndex = pageIndex,
                        aspectRatio = aspectRatio,
                    )
                }
            } else {
                emptyList()
            }
        }

    val loadThumbnail: suspend (Int) -> android.graphics.Bitmap? =
        remember(pdfState.pdfRenderer) {
            { pageIndex ->
                thumbnailCache[pageIndex]
                    ?: withContext(Dispatchers.Default) {
                        pdfState.pdfRenderer?.renderThumbnail(pageIndex)
                    }?.also { bitmap ->
                        thumbnailCache[pageIndex] = bitmap
                    }
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
            brush = brushState.activeBrush,
            isStylusButtonEraserActive = isStylusButtonEraserActive,
            interactionMode = if (isTextSelectionMode) InteractionMode.TEXT_SELECTION else InteractionMode.DRAW,
            allowCanvasFingerGestures = true,
            thumbnails = thumbnails,
            currentPageIndex = pageState.currentPageIndex,
            templateState = templateState,
            isTextSelectionEnabled = isTextSelectionMode,
            loadThumbnail = loadThumbnail,
            onStrokeFinished = strokeCallbacks.onStrokeFinished,
            onStrokeErased = strokeCallbacks.onStrokeErased,
            onStylusButtonEraserActiveChanged = { isStylusButtonEraserActive = it },
            onTransformGesture = onTransformGesture,
            onPanGestureEnd = onPanGestureEnd,
            onViewportSizeChanged = { viewportSize = it },
            onPageSelected = { targetIndex -> viewModel.navigateBy(targetIndex - pageState.currentPageIndex) },
            onTemplateChange = { templateState = it },
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
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
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
    onLoadOutline: (PdfDocumentRenderer) -> Unit,
    editorSettings: EditorSettings,
    onEditorSettingsChange: (EditorSettings) -> Unit,
): MultiPageUiState {
    val appContext = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val brushState =
        rememberBrushState(
            editorSettings = editorSettings,
            onSettingsChange = onEditorSettingsChange,
        )
    val pageState = rememberPageState(viewModel)
    val undoController = remember(viewModel) { UndoController(viewModel, MAX_UNDO_ACTIONS, useMultiPage = true) }
    var isReadOnly by rememberSaveable { mutableStateOf(false) }
    var isTextSelectionMode by rememberSaveable { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isStylusButtonEraserActive by remember { mutableStateOf(false) }
    var templateState by rememberSaveable { mutableStateOf(PageTemplateState.BLANK) }
    var documentZoom by rememberSaveable { mutableStateOf(1f) }
    var documentPanX by rememberSaveable { mutableStateOf(0f) }
    var immediateVisibleRange by
        remember(pageState.currentPageIndex) {
            mutableStateOf(pageState.currentPageIndex..pageState.currentPageIndex)
        }
    val pageStrokesCache by viewModel.pageStrokesCache.collectAsState()
    val pageById = remember(pageState.pages) { pageState.pages.associateBy { it.pageId } }
    val viewportWidth = viewportSize.width.toFloat()

    val pdfAssetId =
        pageState.pages.firstOrNull { it.kind == "pdf" || it.kind == "mixed" }?.pdfAssetId
    val rendererResult =
        remember(pdfAssetId, pdfOpenRetryNonce) {
            pdfAssetId?.let { assetId ->
                runCatching {
                    openPdfDocumentRenderer(
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
            is PdfOpenFailureUiAction.PromptForPassword -> {
                onPdfPasswordRequired(assetId, action.isIncorrectPassword)
            }

            is PdfOpenFailureUiAction.ShowOpenError -> {
                onPdfOpenError(action.message)
            }
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
        isTextSelectionMode = false
    }

    val pages =
        pageState.pages.mapIndexed { index, page ->
            val pageWidth = page.width
            val pageHeight = page.height
            val scale =
                if (viewportWidth > 0f && pageWidth > 0f) {
                    (viewportWidth / pageWidth) * documentZoom
                } else {
                    documentZoom
                }
            val scaledPageWidth = pageWidth * scale
            val minPanX = (viewportWidth - scaledPageWidth).coerceAtMost(0f)
            val pagePanX = documentPanX.coerceIn(minPanX, 0f)
            val renderTransform =
                ViewTransform(
                    zoom = scale,
                    panX = pagePanX,
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
                isVisible = index in immediateVisibleRange,
                renderTransform = renderTransform,
                templateState = templateState,
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
            isTextSelectionMode = isTextSelectionMode,
            onToggleTextSelectionMode = {
                if (pdfRenderer != null) {
                    isTextSelectionMode = !isTextSelectionMode
                }
            },
            onJumpToPage = { targetPageIndex ->
                val offset = targetPageIndex - pageState.currentPageIndex
                if (offset != 0) {
                    viewModel.navigateBy(offset)
                }
            },
        )
    val toolbarState =
        buildToolbarState(
            brushState.activeBrush,
            brushState.lastNonEraserTool,
            isStylusButtonEraserActive,
            templateState,
            brushState.onBrushChange,
            { templateState = it },
        )

    val thumbnailCache = remember(pdfRenderer) { mutableStateMapOf<Int, android.graphics.Bitmap?>() }

    val thumbnails =
        remember(pdfRenderer) {
            val renderer = pdfRenderer
            if (renderer != null) {
                val pageCount = renderer.getPageCount()
                (0 until pageCount).map { pageIndex ->
                    val bounds = renderer.getPageBounds(pageIndex)
                    val aspectRatio = bounds.width() / bounds.height().coerceAtLeast(1f)
                    ThumbnailItem(
                        pageIndex = pageIndex,
                        aspectRatio = aspectRatio,
                    )
                }
            } else {
                emptyList()
            }
        }

    val loadThumbnail: suspend (Int) -> android.graphics.Bitmap? =
        remember(pdfRenderer) {
            { pageIndex ->
                thumbnailCache[pageIndex]
                    ?: withContext(Dispatchers.Default) {
                        pdfRenderer?.renderThumbnail(pageIndex)
                    }?.also { bitmap ->
                        thumbnailCache[pageIndex] = bitmap
                    }
            }
        }

    val multiPageContentState =
        MultiPageContentState(
            pages = pages,
            isReadOnly = isReadOnly,
            brush = brushState.activeBrush,
            isStylusButtonEraserActive = isStylusButtonEraserActive,
            interactionMode = if (isTextSelectionMode) InteractionMode.TEXT_SELECTION else InteractionMode.SCROLL,
            pdfRenderer = pdfRenderer,
            firstVisiblePageIndex = pageState.currentPageIndex,
            documentZoom = documentZoom,
            documentPanX = documentPanX,
            minDocumentZoom = STACKED_DOCUMENT_MIN_ZOOM,
            maxDocumentZoom = STACKED_DOCUMENT_MAX_ZOOM,
            thumbnails = thumbnails,
            templateState = templateState,
            isTextSelectionEnabled = isTextSelectionMode,
            loadThumbnail = loadThumbnail,
            onStrokeFinished = { stroke, pageId ->
                val page = pageById[pageId]
                viewModel.setActiveRecognitionPage(pageId)
                undoController.onStrokeFinished(stroke, page)
            },
            onStrokeErased = { stroke, pageId ->
                undoController.onStrokeErased(stroke, pageId)
            },
            onStylusButtonEraserActiveChanged = { isStylusButtonEraserActive = it },
            onTransformGesture = { _, _, _, _, _ -> },
            onPanGestureEnd = { _, _ -> },
            onDocumentZoomChange = { updatedZoom ->
                val clampedZoom = updatedZoom.coerceIn(STACKED_DOCUMENT_MIN_ZOOM, STACKED_DOCUMENT_MAX_ZOOM)
                documentZoom = clampedZoom
                val minPanX =
                    if (viewportWidth > 0f) {
                        (viewportWidth * (1f - clampedZoom)).coerceAtMost(0f)
                    } else {
                        0f
                    }
                documentPanX = documentPanX.coerceIn(minPanX, 0f)
            },
            onDocumentPanXChange = { updatedPanX ->
                val minPanX =
                    if (viewportWidth > 0f) {
                        (viewportWidth * (1f - documentZoom)).coerceAtMost(0f)
                    } else {
                        0f
                    }
                documentPanX = updatedPanX.coerceIn(minPanX, 0f)
            },
            onViewportSizeChanged = { viewportSize = it },
            onVisiblePageChanged = viewModel::setVisiblePageIndex,
            onVisiblePagesImmediateChanged = { range ->
                immediateVisibleRange = range
            },
            onVisiblePagesPrefetchChanged = { range ->
                viewModel.onVisiblePagesChanged(range)
            },
            onPageSelected = { targetIndex ->
                viewModel.navigateBy(targetIndex - pageState.currentPageIndex)
            },
            onTemplateChange = { templateState = it },
        )

    return MultiPageUiState(
        topBarState = topBarState,
        toolbarState = toolbarState,
        multiPageContentState = multiPageContentState,
    )
}

@Composable
@Suppress("LongMethod")
private fun rememberBrushState(
    editorSettings: EditorSettings,
    onSettingsChange: (EditorSettings) -> Unit,
): BrushState {
    var penBrush by remember { mutableStateOf(editorSettings.penBrush.copy(tool = Tool.PEN)) }
    var highlighterBrush by remember {
        mutableStateOf(editorSettings.highlighterBrush.copy(tool = Tool.HIGHLIGHTER))
    }
    var selectedTool by remember { mutableStateOf(editorSettings.selectedTool) }
    var lastNonEraserTool by remember { mutableStateOf(editorSettings.lastNonEraserTool) }

    LaunchedEffect(editorSettings) {
        penBrush = editorSettings.penBrush.copy(tool = Tool.PEN)
        highlighterBrush = editorSettings.highlighterBrush.copy(tool = Tool.HIGHLIGHTER)
        selectedTool = editorSettings.selectedTool
        lastNonEraserTool = editorSettings.lastNonEraserTool
    }

    fun persistSettings() {
        onSettingsChange(
            EditorSettings(
                selectedTool = selectedTool,
                penBrush = penBrush.copy(tool = Tool.PEN),
                highlighterBrush = highlighterBrush.copy(tool = Tool.HIGHLIGHTER),
                lastNonEraserTool = lastNonEraserTool,
            ),
        )
    }

    val activeBrush =
        when (selectedTool) {
            Tool.PEN -> {
                penBrush
            }

            Tool.HIGHLIGHTER -> {
                highlighterBrush
            }

            Tool.ERASER -> {
                when (lastNonEraserTool) {
                    Tool.HIGHLIGHTER -> highlighterBrush.copy(tool = Tool.ERASER)
                    else -> penBrush.copy(tool = Tool.ERASER)
                }
            }

            Tool.LASSO -> {
                penBrush
            }
        }
    return BrushState(
        activeBrush = activeBrush,
        penBrush = penBrush,
        highlighterBrush = highlighterBrush,
        lastNonEraserTool = lastNonEraserTool,
        onBrushChange = { updatedBrush ->
            when (updatedBrush.tool) {
                Tool.PEN -> {
                    selectedTool = Tool.PEN
                    lastNonEraserTool = Tool.PEN
                    penBrush = updatedBrush
                    persistSettings()
                }

                Tool.HIGHLIGHTER -> {
                    selectedTool = Tool.HIGHLIGHTER
                    lastNonEraserTool = Tool.HIGHLIGHTER
                    highlighterBrush = updatedBrush
                    persistSettings()
                }

                Tool.ERASER -> {
                    selectedTool = Tool.ERASER
                    if (lastNonEraserTool == Tool.HIGHLIGHTER) {
                        highlighterBrush = highlighterBrush.copy(tool = Tool.HIGHLIGHTER)
                    } else {
                        penBrush = penBrush.copy(tool = Tool.PEN)
                    }
                    persistSettings()
                }

                Tool.LASSO -> {
                    selectedTool = Tool.LASSO
                    lastNonEraserTool = Tool.LASSO
                    persistSettings()
                }
            }
        },
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
                    openPdfDocumentRenderer(
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
            is PdfOpenFailureUiAction.PromptForPassword -> {
                onPdfPasswordRequired(assetId, action.isIncorrectPassword)
            }

            is PdfOpenFailureUiAction.ShowOpenError -> {
                onPdfOpenError(action.message)
            }
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

@Suppress("LongParameterList")
private fun buildToolbarState(
    brush: Brush,
    lastNonEraserTool: Tool,
    isStylusButtonEraserActive: Boolean,
    templateState: PageTemplateState,
    onBrushChange: (Brush) -> Unit,
    onTemplateChange: (PageTemplateState) -> Unit,
): NoteEditorToolbarState =
    NoteEditorToolbarState(
        brush = brush,
        lastNonEraserTool = lastNonEraserTool,
        isStylusButtonEraserActive = isStylusButtonEraserActive,
        templateState = templateState,
        onBrushChange = onBrushChange,
        onTemplateChange = onTemplateChange,
    )
