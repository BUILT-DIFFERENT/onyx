@file:Suppress("FunctionName", "TooManyFunctions", "MaxLineLength")

package com.onyx.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.repository.EditorSettings
import com.onyx.android.data.repository.PageTemplateConfig
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.LassoSelection
import com.onyx.android.ink.model.SpatialIndex
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.TransformGesture
import com.onyx.android.ink.ui.calculateSelectionBounds
import com.onyx.android.ink.ui.findStrokesInLasso
import com.onyx.android.ink.ui.moveStrokes
import com.onyx.android.ink.ui.resizeStrokes
import com.onyx.android.input.DoubleFingerMode
import com.onyx.android.input.DoubleTapZoomAction
import com.onyx.android.input.InputSettings
import com.onyx.android.input.MultiFingerTapAction
import com.onyx.android.input.SingleFingerMode
import com.onyx.android.objects.model.InsertAction
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
import kotlinx.coroutines.delay
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
private const val SEARCH_HIGHLIGHT_CLEAR_DELAY_MS = 4000L
private const val MIN_LASSO_POLYGON_POINTS = 3
private const val MIN_LASSO_SCALE = 0.2f
private const val MAX_LASSO_SCALE = 5f
private const val ZOOM_PERCENT_DIVISOR = 100f
private const val FIT_ZOOM_BASELINE = 1f
private const val MIN_ZOOM_CHANGE_EPSILON = 0.001f

@Suppress("MagicNumber")
private val DOUBLE_TAP_ZOOM_PRESETS = listOf(50, 100, 200, 300, 400)

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
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun NoteEditorScreenContent(
    noteId: String,
    viewModel: NoteEditorViewModel,
    pdfAssetStorage: PdfAssetStorage,
    pdfPasswordStore: PdfPasswordStore,
    onNavigateBack: () -> Unit,
) {
    val errorMessage by viewModel.errorMessage.collectAsState()
    val editorSettings by viewModel.editorSettings.collectAsState()
    val conversionDraft by viewModel.conversionDraft.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    var pdfPasswordPrompt by remember { mutableStateOf<EditorPdfPasswordPromptState?>(null) }
    var pdfPasswordInput by rememberSaveable { mutableStateOf("") }
    var pdfOpenErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfOpenRetryNonce by rememberSaveable { mutableStateOf(0) }
    var showOutlineSheet by rememberSaveable { mutableStateOf(false) }
    var showPageManagerDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeletePageId by rememberSaveable { mutableStateOf<String?>(null) }
    var outlineItems by remember { mutableStateOf<List<OutlineItem>>(emptyList()) }
    var conversionText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(conversionDraft?.pageId, conversionDraft?.editingBlockId) {
        conversionText = conversionDraft?.initialText.orEmpty()
    }
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
            topBarState =
                uiState.topBarState.copy(
                    onOpenPageManager = { showPageManagerDialog = true },
                ),
            toolbarState = uiState.toolbarState,
            multiPageContentState = uiState.multiPageContentState,
            snackbarHostState = uiState.snackbarHostState,
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
            topBarState =
                uiState.topBarState.copy(
                    onOpenPageManager = { showPageManagerDialog = true },
                ),
            toolbarState = uiState.toolbarState,
            contentState = uiState.contentState,
            transformState = uiState.transformState,
            snackbarHostState = uiState.snackbarHostState,
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
    if (conversionDraft != null) {
        NoteEditorTextConversionDialog(
            text = conversionText,
            onTextChange = { updated -> conversionText = updated },
            onDismiss = viewModel::dismissConversionDraft,
            onSave = { viewModel.commitConversionDraft(conversionText) },
        )
    }
    if (showPageManagerDialog) {
        NoteEditorPageManagerDialog(
            pages = pages,
            currentPageIndex = currentPageIndex,
            onDismiss = {
                showPageManagerDialog = false
                pendingDeletePageId = null
            },
            onJumpToPage = { targetPageIndex ->
                val offset = targetPageIndex - currentPageIndex
                if (offset != 0) {
                    viewModel.navigateBy(offset)
                }
            },
            onMovePageUp = { pageId -> viewModel.movePage(pageId, -1) },
            onMovePageDown = { pageId -> viewModel.movePage(pageId, 1) },
            onDuplicatePage = viewModel::duplicatePage,
            onDeletePageRequest = { pageId -> pendingDeletePageId = pageId },
        )
    }
    val deleteTarget =
        pendingDeletePageId?.let { pageId ->
            pages.firstOrNull { page -> page.pageId == pageId }
        }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDeletePageId = null },
            title = { Text("Delete page") },
            text = {
                Text("Delete page ${deleteTarget.indexInNote + 1}? This cannot be undone from page manager.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePage(deleteTarget.pageId)
                        pendingDeletePageId = null
                    },
                    enabled = pages.size > 1,
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { pendingDeletePageId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NoteEditorTextConversionDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert to text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Editable text") },
                minLines = 3,
            )
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
    )
}

@Composable
@Suppress("LongMethod", "LongParameterList")
private fun NoteEditorPageManagerDialog(
    pages: List<PageEntity>,
    currentPageIndex: Int,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onMovePageUp: (String) -> Unit,
    onMovePageDown: (String) -> Unit,
    onDuplicatePage: (String) -> Unit,
    onDeletePageRequest: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page manager") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pages.forEachIndexed { index, page ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Button(
                            onClick = { onJumpToPage(index) },
                            modifier = Modifier.weight(1f),
                        ) {
                            val marker = if (index == currentPageIndex) "Current" else "Open"
                            Text("$marker - Page ${index + 1}")
                        }
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Button(
                                onClick = { onMovePageUp(page.pageId) },
                                enabled = index > 0,
                                modifier = Modifier.width(64.dp),
                            ) {
                                Text("Up")
                            }
                            Button(
                                onClick = { onMovePageDown(page.pageId) },
                                enabled = index < pages.lastIndex,
                                modifier = Modifier.width(72.dp),
                            ) {
                                Text("Down")
                            }
                            Button(
                                onClick = { onDuplicatePage(page.pageId) },
                                modifier = Modifier.width(70.dp),
                            ) {
                                Text("Copy")
                            }
                            Button(
                                onClick = { onDeletePageRequest(page.pageId) },
                                enabled = pages.size > 1,
                                modifier = Modifier.width(64.dp),
                            ) {
                                Text("Del")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
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
    val pageObjects by viewModel.pageObjects.collectAsState()
    val selectedObjectId by viewModel.selectedObjectId.collectAsState()
    val recognitionOverlayEnabled by viewModel.recognitionOverlayEnabled.collectAsState()
    val recognizedTextByPage by viewModel.recognizedTextByPage.collectAsState()
    val convertedTextBlocksByPage by viewModel.convertedTextBlocksByPage.collectAsState()
    val undoController = remember(viewModel) { UndoController(viewModel, MAX_UNDO_ACTIONS) }
    var isReadOnly by rememberSaveable { mutableStateOf(false) }
    var isTextSelectionMode by rememberSaveable { mutableStateOf(false) }
    var isZoomLocked by rememberSaveable { mutableStateOf(false) }
    var viewTransform by remember { mutableStateOf(ViewTransform.DEFAULT) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isStylusButtonEraserActive by remember { mutableStateOf(false) }
    var isSegmentEraserEnabled by rememberSaveable { mutableStateOf(false) }
    var activeInsertAction by rememberSaveable { mutableStateOf(InsertAction.NONE) }
    var pendingImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lassoSelection by remember { mutableStateOf(LassoSelection()) }
    val templateConfig by viewModel.currentTemplateConfig.collectAsState()
    val templateState = remember(templateConfig) { templateConfig.toUiState() }
    var panFlingJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pickImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pendingImageUri = uri?.toString()
            activeInsertAction = if (uri != null) InsertAction.IMAGE else InsertAction.NONE
            if (uri != null) {
                viewModel.selectObject(null)
            }
        }
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
        lassoSelection = LassoSelection()
        activeInsertAction = InsertAction.NONE
        pendingImageUri = null
        viewModel.selectObject(null)
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
            onToggleReadOnly = {
                isReadOnly = !isReadOnly
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        if (isReadOnly) "View mode enabled" else "Edit mode enabled",
                    )
                }
            },
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
            isRecognitionOverlayEnabled = recognitionOverlayEnabled,
            onToggleRecognitionOverlay = viewModel::toggleRecognitionOverlay,
        )
    val toolbarState =
        buildToolbarState(
            brushState.activeBrush,
            brushState.lastNonEraserTool,
            isStylusButtonEraserActive,
            isSegmentEraserEnabled,
            activeInsertAction,
            brushState.inputSettings,
            templateState,
            brushState.onBrushChange,
            brushState.onInputSettingsChange,
            { isSegmentEraserEnabled = it },
            { action ->
                if (action == InsertAction.IMAGE) {
                    pickImageLauncher.launch("image/*")
                } else {
                    activeInsertAction = action
                    if (action != InsertAction.NONE) {
                        viewModel.selectObject(null)
                    }
                }
            },
            {
                viewModel.updateCurrentPageTemplate(it.toConfig())
            },
        )
    val onTransformGesture: (Float, Float, Float, Float, Float) -> Unit =
        { zoomChange, panChangeX, panChangeY, centroidX, centroidY ->
            panFlingJob?.cancel()
            val effectiveZoomChange = if (isZoomLocked) 1f else zoomChange
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
                            effectiveZoomChange,
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
            pageObjects = pageObjects,
            selectedObjectId = selectedObjectId,
            brush = brushState.activeBrush,
            isStylusButtonEraserActive = isStylusButtonEraserActive,
            isSegmentEraserEnabled = isSegmentEraserEnabled,
            activeInsertAction = activeInsertAction,
            interactionMode = if (isTextSelectionMode) InteractionMode.TEXT_SELECTION else InteractionMode.DRAW,
            allowCanvasFingerGestures = brushState.inputSettings.allowsAnyFingerGesture(),
            inputSettings = brushState.inputSettings,
            thumbnails = thumbnails,
            currentPageIndex = pageState.currentPageIndex,
            totalPages = pageState.pages.size,
            templateState = templateState,
            isZoomLocked = isZoomLocked,
            lassoSelection = lassoSelection,
            isTextSelectionEnabled = isTextSelectionMode,
            isRecognitionOverlayEnabled = recognitionOverlayEnabled,
            recognitionText = pageState.currentPage?.pageId?.let { pageId -> recognizedTextByPage[pageId] },
            convertedTextBlocks =
                pageState.currentPage
                    ?.pageId
                    ?.let { pageId ->
                        convertedTextBlocksByPage[pageId].orEmpty()
                    }.orEmpty(),
            onConvertedTextBlockSelected = { block ->
                pageState.currentPage?.pageId?.let { pageId ->
                    viewModel.startConversionDraftFromBlock(pageId, block)
                }
            },
            loadThumbnail = loadThumbnail,
            onStrokeFinished = { stroke ->
                pageState.currentPage?.pageId?.let { pageId ->
                    if (stroke.style.tool == Tool.LASSO) {
                        lassoSelection = resolveLassoSelection(stroke, pageState.strokes)
                    } else {
                        strokeCallbacks.onStrokeFinished(stroke)
                    }
                }
            },
            onStrokeErased = strokeCallbacks.onStrokeErased,
            onStrokeSplit = { original, segments ->
                pageState.currentPage?.pageId?.let { pageId ->
                    strokeCallbacks.onStrokeSplit(original, segments, pageId)
                }
            },
            onSegmentEraserEnabledChange = { isSegmentEraserEnabled = it },
            onLassoMove = { deltaX, deltaY ->
                val pageId = pageState.currentPage?.pageId
                if (pageId != null) {
                    lassoSelection =
                        applyLassoMove(
                            undoController = undoController,
                            pageId = pageId,
                            pageStrokes = pageState.strokes,
                            selection = lassoSelection,
                            deltaX = deltaX,
                            deltaY = deltaY,
                        )
                }
            },
            onLassoResize = { scale, _, _ ->
                val pageId = pageState.currentPage?.pageId
                if (pageId != null) {
                    lassoSelection =
                        applyLassoResize(
                            undoController = undoController,
                            pageId = pageId,
                            pageStrokes = pageState.strokes,
                            selection = lassoSelection,
                            scale = scale,
                        )
                }
            },
            onInsertActionChanged = { action ->
                activeInsertAction = action
            },
            onShapeObjectCreate = { shapeType, x, y, width, height ->
                val pageId = pageState.currentPage?.pageId
                if (pageId != null) {
                    val shapeObject =
                        viewModel.createShapeObject(
                            pageId = pageId,
                            shapeType = shapeType,
                            x = x,
                            y = y,
                            width = width,
                            height = height,
                        )
                    undoController.onObjectAdded(pageId = pageId, pageObject = shapeObject)
                    viewModel.selectObject(shapeObject.objectId)
                    activeInsertAction = InsertAction.NONE
                }
            },
            onTextObjectCreate = { x, y ->
                val pageId = pageState.currentPage?.pageId
                if (pageId != null) {
                    val textObject =
                        viewModel.createTextObject(
                            pageId = pageId,
                            x = x,
                            y = y,
                        )
                    undoController.onObjectAdded(pageId = pageId, pageObject = textObject)
                    viewModel.selectObject(textObject.objectId)
                    activeInsertAction = InsertAction.NONE
                }
            },
            onImageObjectCreate = { x, y ->
                val pageId = pageState.currentPage?.pageId
                if (pageId != null) {
                    val imageObject =
                        viewModel.createImageObject(
                            pageId = pageId,
                            x = x,
                            y = y,
                            sourceUri = pendingImageUri,
                        )
                    undoController.onObjectAdded(pageId = pageId, pageObject = imageObject)
                    viewModel.selectObject(imageObject.objectId)
                    activeInsertAction = InsertAction.NONE
                    pendingImageUri = null
                }
            },
            onTextObjectEdit = { pageObject, text ->
                val updatedObject = viewModel.updateTextObjectPayload(pageObject, text)
                undoController.onObjectTransformed(
                    pageId = pageObject.pageId,
                    before = pageObject,
                    after = updatedObject,
                )
            },
            onObjectSelected = viewModel::selectObject,
            onObjectTransformed = { before, after ->
                undoController.onObjectTransformed(
                    pageId = before.pageId,
                    before = before,
                    after = after,
                )
            },
            onDuplicateObject = { pageObject ->
                val duplicated = viewModel.duplicatePageObject(pageObject.pageId, pageObject)
                undoController.onObjectAdded(pageId = duplicated.pageId, pageObject = duplicated)
                viewModel.selectObject(duplicated.objectId)
            },
            onDeleteObject = { pageObject ->
                undoController.onObjectRemoved(pageObject.pageId, pageObject)
            },
            onStylusButtonEraserActiveChanged = { isStylusButtonEraserActive = it },
            onTransformGesture = onTransformGesture,
            onPanGestureEnd = onPanGestureEnd,
            onUndoShortcut = undoController::undo,
            onRedoShortcut = undoController::redo,
            onDoubleTapZoomRequested = {
                if (isZoomLocked) {
                    return@NoteEditorContentState
                }
                viewTransform =
                    applyDoubleTapZoomToTransform(
                        action = brushState.inputSettings.doubleTapZoomAction,
                        currentTransform = viewTransform,
                        zoomLimits = zoomLimits.minZoom..zoomLimits.maxZoom,
                        pageWidth = pdfState.pageWidth,
                        pageHeight = pdfState.pageHeight,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                    )
            },
            onViewportSizeChanged = { viewportSize = it },
            onPageSelected = { targetIndex -> viewModel.navigateBy(targetIndex - pageState.currentPageIndex) },
            onZoomPresetSelected = { zoomPercent ->
                val targetZoom =
                    (zoomPercent / 100f).coerceIn(
                        zoomLimits.minZoom,
                        zoomLimits.maxZoom,
                    )
                viewTransform =
                    constrainTransformToViewport(
                        transform = viewTransform.copy(zoom = targetZoom),
                        pageWidth = pdfState.pageWidth,
                        pageHeight = pdfState.pageHeight,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                    )
            },
            onFitZoomRequested = {
                viewTransform =
                    fitTransformToViewport(
                        pageWidth = pdfState.pageWidth,
                        pageHeight = pdfState.pageHeight,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        zoomLimits = zoomLimits,
                    )
            },
            onZoomLockChanged = { locked -> isZoomLocked = locked },
            onTemplateChange = { state -> viewModel.updateCurrentPageTemplate(state.toConfig()) },
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
                viewTransform =
                    if (isZoomLocked) {
                        constrainTransformToViewport(
                            transform = updatedTransform.copy(zoom = viewTransform.zoom),
                            pageWidth = pdfState.pageWidth,
                            pageHeight = pdfState.pageHeight,
                            viewportWidth = viewportWidth,
                            viewportHeight = viewportHeight,
                        )
                    } else {
                        updatedTransform
                    }
            },
        snackbarHostState = snackbarHostState,
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isStylusButtonEraserActive by remember { mutableStateOf(false) }
    var isSegmentEraserEnabled by rememberSaveable { mutableStateOf(false) }
    var activeInsertAction by rememberSaveable { mutableStateOf(InsertAction.NONE) }
    var isZoomLocked by rememberSaveable { mutableStateOf(false) }
    var pendingImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val lassoSelectionsByPageId = remember { mutableStateMapOf<String, LassoSelection>() }
    val templateConfigByPageId by viewModel.templateConfigByPageId.collectAsState()
    val currentTemplateConfig by viewModel.currentTemplateConfig.collectAsState()
    val templateState = remember(currentTemplateConfig) { currentTemplateConfig.toUiState() }
    var documentZoom by rememberSaveable { mutableStateOf(1f) }
    var documentPanX by rememberSaveable { mutableStateOf(0f) }
    var immediateVisibleRange by
        remember(pageState.currentPageIndex) {
            mutableStateOf(pageState.currentPageIndex..pageState.currentPageIndex)
        }
    val pageStrokesCache by viewModel.pageStrokesCache.collectAsState()
    val pageObjectsCache by viewModel.pageObjectsCache.collectAsState()
    val selectedObjectId by viewModel.selectedObjectId.collectAsState()
    val recognitionOverlayEnabled by viewModel.recognitionOverlayEnabled.collectAsState()
    val recognizedTextByPage by viewModel.recognizedTextByPage.collectAsState()
    val convertedTextBlocksByPage by viewModel.convertedTextBlocksByPage.collectAsState()
    val searchHighlightBounds by viewModel.searchHighlightBounds.collectAsState()
    val pageById = remember(pageState.pages) { pageState.pages.associateBy { it.pageId } }
    val viewportWidth = viewportSize.width.toFloat()
    val pickImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pendingImageUri = uri?.toString()
            activeInsertAction = if (uri != null) InsertAction.IMAGE else InsertAction.NONE
            if (uri != null) {
                viewModel.selectObject(null)
            }
        }

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
        activeInsertAction = InsertAction.NONE
        pendingImageUri = null
        viewModel.selectObject(null)
    }
    LaunchedEffect(searchHighlightBounds) {
        if (searchHighlightBounds != null) {
            delay(SEARCH_HIGHLIGHT_CLEAR_DELAY_MS)
            viewModel.clearSearchHighlight()
        }
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
                pageObjects = pageObjectsCache[page.pageId].orEmpty(),
                isPdfPage = page.kind == "pdf" || page.kind == "mixed",
                isVisible = index in immediateVisibleRange,
                renderTransform = renderTransform,
                templateState =
                    templateConfigByPageId[page.pageId]
                        ?.toUiState()
                        ?: PageTemplateState.BLANK,
                lassoSelection = lassoSelectionsByPageId[page.pageId] ?: LassoSelection(),
                selectedObjectId =
                    if (selectedObjectId in pageObjectsCache[page.pageId].orEmpty().map { it.objectId }) {
                        selectedObjectId
                    } else {
                        null
                    },
                searchHighlightBounds =
                    if (
                        searchHighlightBounds != null &&
                        (
                            page.pageId == viewModel.searchHighlightPageId ||
                                page.indexInNote == viewModel.searchHighlightPageIndex
                        )
                    ) {
                        searchHighlightBounds
                    } else {
                        null
                    },
                recognitionText = recognizedTextByPage[page.pageId],
                convertedTextBlocks = convertedTextBlocksByPage[page.pageId].orEmpty(),
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
            onToggleReadOnly = {
                isReadOnly = !isReadOnly
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        if (isReadOnly) "View mode enabled" else "Edit mode enabled",
                    )
                }
            },
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
            isRecognitionOverlayEnabled = recognitionOverlayEnabled,
            onToggleRecognitionOverlay = viewModel::toggleRecognitionOverlay,
        )
    val toolbarState =
        buildToolbarState(
            brushState.activeBrush,
            brushState.lastNonEraserTool,
            isStylusButtonEraserActive,
            isSegmentEraserEnabled,
            activeInsertAction,
            brushState.inputSettings,
            templateState,
            brushState.onBrushChange,
            brushState.onInputSettingsChange,
            { isSegmentEraserEnabled = it },
            { action ->
                if (action == InsertAction.IMAGE) {
                    pickImageLauncher.launch("image/*")
                } else {
                    activeInsertAction = action
                    if (action != InsertAction.NONE) {
                        viewModel.selectObject(null)
                    }
                }
            },
            { state ->
                viewModel.updateCurrentPageTemplate(state.toConfig())
            },
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
            isSegmentEraserEnabled = isSegmentEraserEnabled,
            activeInsertAction = activeInsertAction,
            selectedObjectId = selectedObjectId,
            interactionMode = if (isTextSelectionMode) InteractionMode.TEXT_SELECTION else InteractionMode.SCROLL,
            inputSettings = brushState.inputSettings,
            pdfRenderer = pdfRenderer,
            firstVisiblePageIndex = pageState.currentPageIndex,
            documentZoom = documentZoom,
            documentPanX = documentPanX,
            totalPages = pageState.pages.size,
            minDocumentZoom = STACKED_DOCUMENT_MIN_ZOOM,
            maxDocumentZoom = STACKED_DOCUMENT_MAX_ZOOM,
            isZoomLocked = isZoomLocked,
            thumbnails = thumbnails,
            templateState = templateState,
            lassoSelectionsByPageId = lassoSelectionsByPageId,
            isTextSelectionEnabled = isTextSelectionMode,
            isRecognitionOverlayEnabled = recognitionOverlayEnabled,
            onConvertedTextBlockSelected = { pageId, block ->
                viewModel.startConversionDraftFromBlock(pageId, block)
            },
            loadThumbnail = loadThumbnail,
            onStrokeFinished = { stroke, pageId ->
                val page = pageById[pageId]
                viewModel.setActiveRecognitionPage(pageId)
                if (stroke.style.tool == Tool.LASSO) {
                    lassoSelectionsByPageId[pageId] = resolveLassoSelection(stroke, pageStrokesCache[pageId].orEmpty())
                } else {
                    undoController.onStrokeFinished(stroke, page)
                }
            },
            onStrokeErased = { stroke, pageId ->
                undoController.onStrokeErased(stroke, pageId)
            },
            onStrokeSplit = { original, segments, pageId ->
                undoController.onStrokeSplit(original, segments, pageId)
            },
            onLassoMove = { pageId, deltaX, deltaY ->
                val currentSelection = lassoSelectionsByPageId[pageId]
                if (currentSelection != null) {
                    val pageStrokes = pageStrokesCache[pageId].orEmpty()
                    lassoSelectionsByPageId[pageId] =
                        applyLassoMove(
                            undoController = undoController,
                            pageId = pageId,
                            pageStrokes = pageStrokes,
                            selection = currentSelection,
                            deltaX = deltaX,
                            deltaY = deltaY,
                        )
                }
            },
            onLassoResize = { pageId, scale, _, _ ->
                val currentSelection = lassoSelectionsByPageId[pageId]
                if (currentSelection != null) {
                    val pageStrokes = pageStrokesCache[pageId].orEmpty()
                    lassoSelectionsByPageId[pageId] =
                        applyLassoResize(
                            undoController = undoController,
                            pageId = pageId,
                            pageStrokes = pageStrokes,
                            selection = currentSelection,
                            scale = scale,
                        )
                }
            },
            onInsertActionChanged = { action ->
                activeInsertAction = action
            },
            onShapeObjectCreate = { pageId, shapeType, x, y, width, height ->
                val shapeObject =
                    viewModel.createShapeObject(
                        pageId = pageId,
                        shapeType = shapeType,
                        x = x,
                        y = y,
                        width = width,
                        height = height,
                    )
                undoController.onObjectAdded(pageId = pageId, pageObject = shapeObject)
                viewModel.selectObject(shapeObject.objectId)
                activeInsertAction = InsertAction.NONE
            },
            onTextObjectCreate = { pageId, x, y ->
                val textObject =
                    viewModel.createTextObject(
                        pageId = pageId,
                        x = x,
                        y = y,
                    )
                undoController.onObjectAdded(pageId = pageId, pageObject = textObject)
                viewModel.selectObject(textObject.objectId)
                activeInsertAction = InsertAction.NONE
            },
            onImageObjectCreate = { pageId, x, y ->
                val imageObject =
                    viewModel.createImageObject(
                        pageId = pageId,
                        x = x,
                        y = y,
                        sourceUri = pendingImageUri,
                    )
                undoController.onObjectAdded(pageId = pageId, pageObject = imageObject)
                viewModel.selectObject(imageObject.objectId)
                activeInsertAction = InsertAction.NONE
                pendingImageUri = null
            },
            onTextObjectEdit = { pageId, pageObject, text ->
                val updatedObject = viewModel.updateTextObjectPayload(pageObject, text)
                undoController.onObjectTransformed(
                    pageId = pageId,
                    before = pageObject,
                    after = updatedObject,
                )
            },
            onObjectSelected = viewModel::selectObject,
            onObjectTransformed = { pageId, before, after ->
                undoController.onObjectTransformed(
                    pageId = pageId,
                    before = before,
                    after = after,
                )
            },
            onDuplicateObject = { pageId, pageObject ->
                val duplicated = viewModel.duplicatePageObject(pageId, pageObject)
                undoController.onObjectAdded(pageId = duplicated.pageId, pageObject = duplicated)
                viewModel.selectObject(duplicated.objectId)
            },
            onDeleteObject = { pageId, pageObject ->
                undoController.onObjectRemoved(pageId, pageObject)
            },
            onSegmentEraserEnabledChange = { isSegmentEraserEnabled = it },
            onStylusButtonEraserActiveChanged = { isStylusButtonEraserActive = it },
            onTransformGesture = { _, _, _, _, _ -> },
            onPanGestureEnd = { _, _ -> },
            onUndoShortcut = undoController::undo,
            onRedoShortcut = undoController::redo,
            onDoubleTapZoomRequested = {
                if (isZoomLocked) {
                    return@MultiPageContentState
                }
                when (brushState.inputSettings.doubleTapZoomAction) {
                    DoubleTapZoomAction.NONE -> Unit
                    DoubleTapZoomAction.CYCLE_PRESET -> {
                        val nextZoomPercent =
                            nextZoomPresetPercent((documentZoom * ZOOM_PERCENT_DIVISOR).toInt())
                        val targetZoom =
                            (nextZoomPercent / ZOOM_PERCENT_DIVISOR).coerceIn(
                                STACKED_DOCUMENT_MIN_ZOOM,
                                STACKED_DOCUMENT_MAX_ZOOM,
                            )
                        documentZoom = targetZoom
                        val minPanX =
                            if (viewportWidth > 0f) {
                                (viewportWidth * (1f - documentZoom)).coerceAtMost(0f)
                            } else {
                                0f
                            }
                        documentPanX = documentPanX.coerceIn(minPanX, 0f)
                    }
                    DoubleTapZoomAction.FIT_TO_PAGE -> {
                        documentZoom = FIT_ZOOM_BASELINE
                        val minPanX =
                            if (viewportWidth > 0f) {
                                (viewportWidth * (1f - documentZoom)).coerceAtMost(0f)
                            } else {
                                0f
                            }
                        documentPanX = documentPanX.coerceIn(minPanX, 0f)
                    }
                }
            },
            onDocumentZoomChange = { updatedZoom ->
                if (!isZoomLocked) {
                    val clampedZoom = updatedZoom.coerceIn(STACKED_DOCUMENT_MIN_ZOOM, STACKED_DOCUMENT_MAX_ZOOM)
                    documentZoom = clampedZoom
                    val minPanX =
                        if (viewportWidth > 0f) {
                            (viewportWidth * (1f - clampedZoom)).coerceAtMost(0f)
                        } else {
                            0f
                        }
                    documentPanX = documentPanX.coerceIn(minPanX, 0f)
                }
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
            onZoomPresetSelected = { zoomPercent ->
                val targetZoom = (zoomPercent / 100f).coerceIn(STACKED_DOCUMENT_MIN_ZOOM, STACKED_DOCUMENT_MAX_ZOOM)
                documentZoom = targetZoom
                val minPanX =
                    if (viewportWidth > 0f) {
                        (viewportWidth * (1f - documentZoom)).coerceAtMost(0f)
                    } else {
                        0f
                    }
                documentPanX = documentPanX.coerceIn(minPanX, 0f)
            },
            onFitZoomRequested = {
                documentZoom = 1f
                val minPanX =
                    if (viewportWidth > 0f) {
                        (viewportWidth * (1f - documentZoom)).coerceAtMost(0f)
                    } else {
                        0f
                    }
                documentPanX = documentPanX.coerceIn(minPanX, 0f)
            },
            onZoomLockChanged = { locked -> isZoomLocked = locked },
            onTemplateChange = { state -> viewModel.updateCurrentPageTemplate(state.toConfig()) },
        )

    return MultiPageUiState(
        topBarState = topBarState,
        toolbarState = toolbarState,
        multiPageContentState = multiPageContentState,
        snackbarHostState = snackbarHostState,
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
    var inputSettings by remember { mutableStateOf(editorSettings.inputSettings) }

    LaunchedEffect(editorSettings) {
        penBrush = editorSettings.penBrush.copy(tool = Tool.PEN)
        highlighterBrush = editorSettings.highlighterBrush.copy(tool = Tool.HIGHLIGHTER)
        selectedTool = editorSettings.selectedTool
        lastNonEraserTool = editorSettings.lastNonEraserTool
        inputSettings = editorSettings.inputSettings
    }

    fun persistSettings() {
        onSettingsChange(
            EditorSettings(
                selectedTool = selectedTool,
                penBrush = penBrush.copy(tool = Tool.PEN),
                highlighterBrush = highlighterBrush.copy(tool = Tool.HIGHLIGHTER),
                lastNonEraserTool = lastNonEraserTool,
                inputSettings = inputSettings,
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
                penBrush.copy(tool = Tool.LASSO)
            }
        }
    return BrushState(
        activeBrush = activeBrush,
        penBrush = penBrush,
        highlighterBrush = highlighterBrush,
        lastNonEraserTool = lastNonEraserTool,
        inputSettings = inputSettings,
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
        onInputSettingsChange = { updatedInputSettings ->
            inputSettings = updatedInputSettings
            persistSettings()
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
        onStrokeSplit = { original, segments, pageId ->
            undoController.onStrokeSplit(original, segments, pageId)
        },
    )

private fun resolveLassoSelection(
    lassoStroke: Stroke,
    pageStrokes: List<Stroke>,
): LassoSelection {
    val polygon = lassoStroke.points.map { point -> point.x to point.y }
    if (polygon.size < MIN_LASSO_POLYGON_POINTS) {
        return LassoSelection()
    }
    val selectableStrokes =
        pageStrokes.filter { stroke ->
            stroke.style.tool != Tool.LASSO
        }
    val spatialIndex = SpatialIndex().also { index -> selectableStrokes.forEach(index::insert) }
    val selectedIds = findStrokesInLasso(polygon, selectableStrokes, spatialIndex)
    val selectedStrokes = selectableStrokes.filter { stroke -> stroke.id in selectedIds }
    val bounds = calculateSelectionBounds(selectedStrokes)
    return LassoSelection().withSelection(selectedIds, bounds)
}

@Suppress("LongParameterList")
private fun applyLassoMove(
    undoController: UndoController,
    pageId: String,
    pageStrokes: List<Stroke>,
    selection: LassoSelection,
    deltaX: Float,
    deltaY: Float,
): LassoSelection {
    val selected = pageStrokes.filter { stroke -> stroke.id in selection.selectedStrokeIds }
    if (selected.isEmpty()) {
        return selection.clear()
    }
    val transformed = moveStrokes(selected, deltaX = deltaX, deltaY = deltaY)
    undoController.onStrokesTransformed(pageId = pageId, before = selected, after = transformed)
    return selection.withSelection(
        strokeIds = transformed.mapTo(mutableSetOf()) { stroke -> stroke.id },
        bounds = calculateSelectionBounds(transformed),
    )
}

private fun applyLassoResize(
    undoController: UndoController,
    pageId: String,
    pageStrokes: List<Stroke>,
    selection: LassoSelection,
    scale: Float,
): LassoSelection {
    val selected = pageStrokes.filter { stroke -> stroke.id in selection.selectedStrokeIds }
    if (selected.isEmpty()) {
        return selection.clear()
    }
    val clampedScale = scale.coerceIn(MIN_LASSO_SCALE, MAX_LASSO_SCALE)
    val pivot = selection.transformCenter
    val transformed = resizeStrokes(selected, clampedScale, pivot.first, pivot.second)
    undoController.onStrokesTransformed(pageId = pageId, before = selected, after = transformed)
    return selection.withSelection(
        strokeIds = transformed.mapTo(mutableSetOf()) { stroke -> stroke.id },
        bounds = calculateSelectionBounds(transformed),
    )
}

private fun PageTemplateConfig.toUiState(): PageTemplateState {
    val normalizedKind =
        when (backgroundKind) {
            PageTemplateConfig.KIND_GRID,
            PageTemplateConfig.KIND_LINED,
            PageTemplateConfig.KIND_DOTTED,
            -> backgroundKind

            else -> PageTemplateConfig.KIND_BLANK
        }
    return if (normalizedKind == PageTemplateConfig.KIND_BLANK) {
        PageTemplateState.BLANK
    } else {
        PageTemplateState(
            templateId = templateId,
            backgroundKind = normalizedKind,
            spacing = spacing,
            color = parseTemplateColor(colorHex),
        )
    }
}

private fun PageTemplateState.toConfig(): PageTemplateConfig =
    PageTemplateConfig(
        templateId = templateId,
        backgroundKind = backgroundKind,
        spacing = spacing,
        colorHex = templateColorToHex(color),
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
    isSegmentEraserEnabled: Boolean,
    activeInsertAction: InsertAction,
    inputSettings: InputSettings,
    templateState: PageTemplateState,
    onBrushChange: (Brush) -> Unit,
    onInputSettingsChange: (InputSettings) -> Unit,
    onSegmentEraserEnabledChange: (Boolean) -> Unit,
    onInsertActionSelected: (InsertAction) -> Unit,
    onTemplateChange: (PageTemplateState) -> Unit,
): NoteEditorToolbarState =
    NoteEditorToolbarState(
        brush = brush,
        lastNonEraserTool = lastNonEraserTool,
        isStylusButtonEraserActive = isStylusButtonEraserActive,
        isSegmentEraserEnabled = isSegmentEraserEnabled,
        activeInsertAction = activeInsertAction,
        inputSettings = inputSettings,
        templateState = templateState,
        onBrushChange = onBrushChange,
        onInputSettingsChange = onInputSettingsChange,
        onSegmentEraserEnabledChange = onSegmentEraserEnabledChange,
        onInsertActionSelected = onInsertActionSelected,
        onTemplateChange = onTemplateChange,
    )

private fun InputSettings.allowsAnyFingerGesture(): Boolean =
    singleFingerMode != SingleFingerMode.IGNORE ||
        doubleFingerMode != DoubleFingerMode.IGNORE ||
        doubleTapZoomAction != DoubleTapZoomAction.NONE ||
        twoFingerTapAction != MultiFingerTapAction.NONE ||
        threeFingerTapAction != MultiFingerTapAction.NONE

@Suppress("LongParameterList")
private fun applyDoubleTapZoomToTransform(
    action: DoubleTapZoomAction,
    currentTransform: ViewTransform,
    zoomLimits: ClosedFloatingPointRange<Float>,
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ViewTransform =
    when (action) {
        DoubleTapZoomAction.NONE -> currentTransform
        DoubleTapZoomAction.CYCLE_PRESET -> {
            val nextZoomPercent =
                nextZoomPresetPercent((currentTransform.zoom * ZOOM_PERCENT_DIVISOR).toInt())
            val targetZoom =
                (nextZoomPercent / ZOOM_PERCENT_DIVISOR).coerceIn(zoomLimits.start, zoomLimits.endInclusive)
            val zoomChange = (targetZoom / currentTransform.zoom).coerceAtLeast(MIN_ZOOM_CHANGE_EPSILON)
            applyTransformGesture(
                current = currentTransform,
                zoomLimits =
                    ZoomLimits(
                        minZoom = zoomLimits.start,
                        maxZoom = zoomLimits.endInclusive,
                        fitZoom = FIT_ZOOM_BASELINE,
                    ),
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                gesture =
                    TransformGesture(
                        zoomChange = zoomChange,
                        panChangeX = 0f,
                        panChangeY = 0f,
                        centroidX = viewportWidth / 2f,
                        centroidY = viewportHeight / 2f,
                    ),
            )
        }

        DoubleTapZoomAction.FIT_TO_PAGE -> ViewTransform.DEFAULT
    }

private fun nextZoomPresetPercent(currentZoomPercent: Int): Int {
    val current = currentZoomPercent.coerceAtLeast(DOUBLE_TAP_ZOOM_PRESETS.first())
    return DOUBLE_TAP_ZOOM_PRESETS.firstOrNull { preset -> preset > current } ?: DOUBLE_TAP_ZOOM_PRESETS.first()
}
