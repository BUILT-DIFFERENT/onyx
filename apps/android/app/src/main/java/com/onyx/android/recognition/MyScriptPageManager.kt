package com.onyx.android.recognition

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.myscript.iink.ContentPackage
import com.myscript.iink.ContentPart
import com.myscript.iink.EditorError
import com.myscript.iink.IOffscreenEditorListener
import com.myscript.iink.ItemIdHelper
import com.myscript.iink.MimeType
import com.myscript.iink.OffscreenEditor
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerType
import com.onyx.android.ink.model.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File

/**
 * MyScript content lifecycle manager - tied to PAGE, not note.
 * Each page has its own recognition context using OffscreenEditor.
 *
 * OWNERSHIP MODEL (v4.x OffscreenEditor Pattern):
 * - Engine: Singleton (Application-scoped) - owned by MyScriptEngine
 * - ContentPackage: One per page, stored in filesDir
 * - ContentPart: Active part for current page
 * - OffscreenEditor: One per page, manages ink input (NO rendering/view)
 * - ItemIdHelper: Maps between MyScript stroke IDs and our stroke IDs
 *
 * This class:
 * - Creates OffscreenEditor per page (NOT per stroke)
 * - Manages ContentPackage/ContentPart lifecycle
 * - Feeds strokes to MyScript via addStrokes()
 * - Listens for recognition results via IOffscreenEditorListener
 * - Handles undo/redo/erase operations
 *
 * Reference: myscript-examples/samples/offscreen-interactivity/InkViewModel.kt
 */
@Suppress("TooManyFunctions")
class MyScriptPageManager(
    private val myScriptEngine: MyScriptEngine,
    private val context: Context,
    // NOTE: displayMetrics not needed - we use fixed pt→mm conversion
) {
    private data class PageContext(
        val contentPackage: ContentPackage,
        val contentPart: ContentPart,
    )

    private var currentPageId: String? = null
    private var currentPackage: ContentPackage? = null
    private var currentPart: ContentPart? = null
    private var offscreenEditor: OffscreenEditor? = null
    private var itemIdHelper: ItemIdHelper? = null
    private val activeStrokes = mutableListOf<Stroke>()

    private val recognitionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recognitionTriggerVersion = mutableIntStateOf(0)
    private val recognitionQueue = ArrayDeque<RecognitionRequest>()
    private val recognitionQueueLock = Any()

    // Map MyScript stroke IDs to our stroke UUIDs
    private val strokeIdsMapping = mutableMapOf<String, String>()

    // Reverse map: our stroke UUIDs to MyScript stroke IDs for O(1) lookup on erase
    private val reverseStrokeMapping = mutableMapOf<String, String>()

    // Callback for recognition updates (set by ViewModel/Repository)
    var onRecognitionUpdated: ((pageId: String, text: String) -> Unit)? = null

    private data class RecognitionRequest(
        val pageId: String,
        val strokeCount: Int,
        val reason: String,
    )

    data class RecognitionPipelineConfig(
        val frameDebounceMs: Long = RECOGNITION_FRAME_DEBOUNCE_MS,
        val maxQueueSize: Int = MAX_RECOGNITION_QUEUE_SIZE,
        val maxRetries: Int = MAX_RECOGNITION_RETRIES,
        val initialBackoffMs: Long = INITIAL_RETRY_BACKOFF_MS,
        val maxBackoffMs: Long = MAX_RETRY_BACKOFF_MS,
    )

    private val recognitionPipelineConfig = RecognitionPipelineConfig()

    /**
     * Coordinate conversion: Page pt → MyScript mm (fixed ratio, DPI independent).
     * 1 pt = 1/72 inch, 1 inch = 25.4 mm
     * Therefore: 1 pt = 25.4 / 72 mm ≈ 0.3528 mm
     */
    private fun ptToMm(pt: Float): Float = pt * MM_PER_POINT

    companion object {
        private const val MM_PER_INCH = 25.4f
        private const val POINTS_PER_INCH = 72f
        internal const val MM_PER_POINT = MM_PER_INCH / POINTS_PER_INCH
        private const val DEFAULT_PRESSURE = 0.5f
        private const val RECOGNITION_FRAME_DEBOUNCE_MS = 16L
        private const val MAX_RECOGNITION_QUEUE_SIZE = 24
        private const val MAX_RECOGNITION_RETRIES = 3
        private const val INITIAL_RETRY_BACKOFF_MS = 100L
        private const val MAX_RETRY_BACKOFF_MS = 2_000L
    }

    init {
        recognitionScope.launch {
            snapshotFlow { recognitionTriggerVersion.intValue }
                .debounce(recognitionPipelineConfig.frameDebounceMs)
                .collect {
                    drainRecognitionQueue()
                }
        }
    }

    fun onPageEnter(pageId: String) {
        if (currentPageId == pageId && currentPackage != null && offscreenEditor != null) {
            Log.d("MyScript", "Page already active: $pageId")
            return
        }
        if (currentPageId != pageId) {
            closeCurrentPage()
            activeStrokes.clear()
        }

        val pageContext = openOrCreatePageContext(pageId) ?: return

        currentPackage = pageContext.contentPackage
        currentPart = pageContext.contentPart
        currentPageId = pageId

        createEditor(pageId)
        strokeIdsMapping.clear()
        reverseStrokeMapping.clear()

        Log.d("MyScript", "Page entered: $pageId (recognitionReady=${offscreenEditor != null})")
    }

    fun shutdown() {
        closeCurrentPage()
        recognitionScope.cancel()
    }

    private fun createEditor(pageId: String) {
        val engineResult = myScriptEngine.ensureInitialized()
        val activeEngine =
            engineResult.getOrElse { error ->
                Log.e("MyScript", "Recognition unavailable for page $pageId: ${error.message}", error)
                offscreenEditor?.close()
                offscreenEditor = null
                itemIdHelper?.close()
                itemIdHelper = null
                return
            }

        try {
            val editor = activeEngine.createOffscreenEditor(1f, 1f)
            editor.part = currentPart
            editor.addListener(recognitionListener)
            offscreenEditor = editor
            itemIdHelper = activeEngine.createItemIdHelper(editor)
        } catch (e: IllegalStateException) {
            Log.e("MyScript", "Recognition unavailable for page $pageId: ${e.message}", e)
            offscreenEditor?.close()
            offscreenEditor = null
            itemIdHelper?.close()
            itemIdHelper = null
        }
    }

    private fun openOrCreatePageContext(pageId: String): PageContext? {
        val activeEngine =
            myScriptEngine.ensureInitialized().getOrElse { error ->
                Log.e("MyScript", "Failed to initialize engine for page $pageId: ${error.message}", error)
                return null
            }

        val packagePath = File(context.filesDir, "myscript/page_$pageId.iink")
        packagePath.parentFile?.mkdirs()

        var openedPackage: ContentPackage? = null
        var openedPart: ContentPart? = null

        runCatching {
            if (packagePath.exists()) {
                activeEngine.openPackage(packagePath)
            } else {
                activeEngine.createPackage(packagePath)
            }
        }.onSuccess { contentPackage ->
            openedPackage = contentPackage
            openedPart =
                runCatching {
                    if (contentPackage.partCount > 0) {
                        contentPackage.getPart(0)
                    } else {
                        contentPackage.createPart("Raw Content")
                    }
                }.onFailure { error ->
                    Log.e(
                        "MyScript",
                        "Failed to open content part for page $pageId: ${error.message}",
                        error,
                    )
                }.getOrNull()
        }.onFailure { error ->
            Log.e("MyScript", "Failed to open package for page $pageId: ${error.message}", error)
        }

        if (openedPackage != null && openedPart == null) {
            openedPackage?.close()
        }

        return if (openedPackage != null && openedPart != null) {
            PageContext(
                contentPackage = openedPackage!!,
                contentPart = openedPart!!,
            )
        } else {
            null
        }
    }

    /**
     * Called when navigating AWAY from a page.
     * Saves and closes ContentPackage.
     */
    fun closeCurrentPage() {
        val pageId = currentPageId ?: return

        synchronized(recognitionQueueLock) {
            recognitionQueue.clear()
        }

        offscreenEditor?.close()
        itemIdHelper?.close()
        currentPart?.close()
        currentPackage?.save()
        currentPackage?.close()

        offscreenEditor = null
        itemIdHelper = null
        currentPart = null
        currentPackage = null
        currentPageId = null
        strokeIdsMapping.clear()
        reverseStrokeMapping.clear()
        activeStrokes.clear()

        Log.d("MyScript", "Page closed: $pageId")
    }

    /**
     * Add stroke to recognizer using OffscreenEditor.addStrokes().
     * Strokes are in page coordinates (pt), converted to mm for MyScript.
     * Returns MyScript's stroke ID for mapping.
     */
    fun addStroke(stroke: Stroke): String? {
        val pageId = currentPageId
        val editor =
            offscreenEditor ?: run {
                Log.w("MyScript", "addStroke called but no editor active")
                return null
            }

        val pointerEvents =
            stroke.points
                .mapIndexed { index, point ->
                    val eventType =
                        when (index) {
                            0 -> PointerEventType.DOWN
                            stroke.points.lastIndex -> PointerEventType.UP
                            else -> PointerEventType.MOVE
                        }
                    PointerEvent(
                        eventType,
                        ptToMm(point.x),
                        ptToMm(point.y),
                        point.t,
                        point.p ?: DEFAULT_PRESSURE,
                        PointerType.PEN,
                        0,
                    )
                }.toTypedArray()

        val myScriptStrokeIds = editor.addStrokes(pointerEvents, true)
        return myScriptStrokeIds?.firstOrNull()?.also { msStrokeId ->
            strokeIdsMapping[msStrokeId] = stroke.id
            reverseStrokeMapping[stroke.id] = msStrokeId
            activeStrokes.removeAll { it.id == stroke.id }
            activeStrokes.add(stroke)
            Log.d("MyScript", "Stroke added: ${stroke.id} → MyScript ID: $msStrokeId")
            if (pageId != null) {
                enqueueRecognitionRequest(pageId = pageId, reason = "stroke_added")
            }
        }
    }

    fun onStrokeErased(
        erasedStrokeId: String,
        remainingStrokes: List<Stroke>,
    ) {
        val pageId = currentPageId
        val editor = offscreenEditor ?: return
        activeStrokes.clear()
        activeStrokes.addAll(remainingStrokes)

        // Try to find the MyScript stroke ID for direct erasure (O(1) via reverse map)
        val msStrokeId = reverseStrokeMapping[erasedStrokeId]
        if (msStrokeId != null) {
            runCatching {
                editor.erase(arrayOf(msStrokeId))
                strokeIdsMapping.remove(msStrokeId)
                reverseStrokeMapping.remove(erasedStrokeId)
                Log.d("MyScript", "Stroke erased directly: $erasedStrokeId (ms=$msStrokeId)")
            }.onFailure {
                // Fallback: clear and re-feed all remaining strokes
                Log.d("MyScript", "Direct erase failed, falling back to clear+re-feed: ${it.message}")
                clearAndRefeed(remainingStrokes)
            }
        } else {
            clearAndRefeed(remainingStrokes)
            Log.d("MyScript", "Stroke $erasedStrokeId not in mapping, clear+re-feed ${remainingStrokes.size}")
        }

        if (pageId != null) {
            enqueueRecognitionRequest(pageId = pageId, reason = "stroke_erased")
        }
    }

    fun onUndo(currentStrokes: List<Stroke>) {
        val pageId = currentPageId
        val editor = offscreenEditor ?: return
        activeStrokes.clear()
        activeStrokes.addAll(currentStrokes)

        val history = editor.historyManager
        if (history != null && history.possibleUndoCount > 0) {
            history.undo()
            Log.d("MyScript", "Native undo performed")
            if (pageId != null) {
                enqueueRecognitionRequest(pageId = pageId, reason = "undo_native")
            }
            return
        }

        clearAndRefeed(currentStrokes)
        Log.d("MyScript", "Undo via clear+re-feed: ${currentStrokes.size} strokes")
        if (pageId != null) {
            enqueueRecognitionRequest(pageId = pageId, reason = "undo_refeed")
        }
    }

    fun onRedo(currentStrokes: List<Stroke>) {
        val pageId = currentPageId
        val editor = offscreenEditor ?: return
        activeStrokes.clear()
        activeStrokes.addAll(currentStrokes)

        val history = editor.historyManager
        if (history != null && history.possibleRedoCount > 0) {
            history.redo()
            Log.d("MyScript", "Native redo performed")
            if (pageId != null) {
                enqueueRecognitionRequest(pageId = pageId, reason = "redo_native")
            }
            return
        }

        clearAndRefeed(currentStrokes)
        Log.d("MyScript", "Redo via clear+re-feed: ${currentStrokes.size} strokes")
        if (pageId != null) {
            enqueueRecognitionRequest(pageId = pageId, reason = "redo_refeed")
        }
    }

    private fun clearAndRefeed(strokes: List<Stroke>) {
        val editor = offscreenEditor ?: return
        editor.clear()
        strokeIdsMapping.clear()
        reverseStrokeMapping.clear()
        strokes.forEach { stroke: Stroke ->
            addStroke(stroke)
        }
    }

    /**
     * Get recognized text as plain text.
     */
    fun exportText(): String? = offscreenEditor?.export_(emptyArray(), MimeType.TEXT)

    /**
     * Get recognition result as JIIX JSON.
     */
    fun exportJIIX(): String? = offscreenEditor?.export_(emptyArray(), MimeType.JIIX)

    private val recognitionListener =
        object : IOffscreenEditorListener {
            override fun partChanged(editor: OffscreenEditor) {
                // Part changed (new part loaded)
                Log.d("MyScript", "Part changed")
            }

            override fun contentChanged(
                editor: OffscreenEditor,
                blockIds: Array<out String>,
            ) {
                val pageId = currentPageId
                if (pageId != null) {
                    enqueueRecognitionRequest(pageId = pageId, reason = "content_changed")
                }
            }

            override fun onError(
                editor: OffscreenEditor,
                blockId: String,
                err: EditorError,
                message: String,
            ) {
                Log.e("MyScript", "Recognition error [$err]: $message")
                val pageId = currentPageId
                if (pageId != null) {
                    enqueueRecognitionRequest(pageId = pageId, reason = "listener_error")
                }
            }
        }

    private fun enqueueRecognitionRequest(
        pageId: String,
        reason: String,
    ) {
        val strokeCount = activeStrokes.size
        synchronized(recognitionQueueLock) {
            if (recognitionQueue.size >= recognitionPipelineConfig.maxQueueSize) {
                val dropped = recognitionQueue.removeFirst()
                Log.w(
                    "MyScriptRecognition",
                    "recognition_queue_drop_oldest: " +
                        "page=${dropped.pageId}, reason=${dropped.reason}, strokes=${dropped.strokeCount}",
                )
            }
            recognitionQueue.addLast(
                RecognitionRequest(
                    pageId = pageId,
                    strokeCount = strokeCount,
                    reason = reason,
                ),
            )
            Log.d(
                "MyScriptRecognition",
                "recognition_request_enqueued: " +
                    "page=$pageId, reason=$reason, " +
                    "strokes=$strokeCount, queue=${recognitionQueue.size}",
            )
        }
        Snapshot.withMutableSnapshot {
            recognitionTriggerVersion.intValue += 1
        }
    }

    private suspend fun drainRecognitionQueue() {
        val nextRequest =
            synchronized(recognitionQueueLock) {
                if (recognitionQueue.isEmpty()) {
                    null
                } else {
                    val droppedForBatching = (recognitionQueue.size - 1).coerceAtLeast(0)
                    val latest = recognitionQueue.removeLast()
                    recognitionQueue.clear()
                    if (droppedForBatching > 0) {
                        Log.d(
                            "MyScriptRecognition",
                            "recognition_request_debounced: " +
                                "droppedIntermediate=$droppedForBatching, page=${latest.pageId}",
                        )
                    }
                    latest
                }
            } ?: return

        runRecognitionWithRecovery(nextRequest)
    }

    @Suppress("ReturnCount")
    private suspend fun runRecognitionWithRecovery(request: RecognitionRequest) {
        if (currentPageId != request.pageId) {
            return
        }

        var backoffMs = recognitionPipelineConfig.initialBackoffMs
        repeat(recognitionPipelineConfig.maxRetries) { attempt ->
            val attemptResult =
                runCatching {
                    requireNotNull(exportText()) { "exportText returned null" }
                }
            if (attemptResult.isSuccess) {
                val text = checkNotNull(attemptResult.getOrNull())
                if (currentPageId == request.pageId) {
                    Log.d(
                        "MyScriptRecognition",
                        "recognition_request_processed: " +
                            "page=${request.pageId}, reason=${request.reason}, " +
                            "strokes=${request.strokeCount}",
                    )
                    onRecognitionUpdated?.invoke(request.pageId, text)
                }
                return
            }

            val error = attemptResult.exceptionOrNull()
            val attemptNumber = attempt + 1
            if (attemptNumber >= recognitionPipelineConfig.maxRetries) {
                Log.e(
                    "MyScriptRecognition",
                    "recognition_engine_failed: " +
                        "page=${request.pageId}, strokes=${request.strokeCount}, " +
                        "reason=${request.reason}, error=${error?.message}",
                    error,
                )
                return
            }

            Log.w(
                "MyScriptRecognition",
                "recognition_failed: " +
                    "page=${request.pageId}, strokes=${request.strokeCount}, " +
                    "reason=${request.reason}, attempt=$attemptNumber, " +
                    "retryInMs=$backoffMs, error=${error?.message}",
                error,
            )

            val recovered = restartRecognitionSession(request.pageId)
            if (!recovered) {
                Log.e(
                    "MyScriptRecognition",
                    "recognition_engine_failed: restart_unavailable " +
                        "page=${request.pageId}, strokes=${request.strokeCount}, " +
                        "reason=${request.reason}",
                )
                return
            }

            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(recognitionPipelineConfig.maxBackoffMs)
        }
    }

    @Suppress("ReturnCount")
    private fun restartRecognitionSession(pageId: String): Boolean {
        val restartResult = myScriptEngine.restart()
        if (restartResult.isFailure) {
            Log.e(
                "MyScriptRecognition",
                "recognition_restart_failed: page=$pageId, error=${restartResult.exceptionOrNull()?.message}",
                restartResult.exceptionOrNull(),
            )
            return false
        }

        val strokesForRefeed = activeStrokes.toList()
        offscreenEditor?.close()
        offscreenEditor = null
        itemIdHelper?.close()
        itemIdHelper = null
        currentPart?.close()
        currentPart = null
        currentPackage?.close()
        currentPackage = null
        currentPageId = null
        strokeIdsMapping.clear()
        reverseStrokeMapping.clear()

        onPageEnter(pageId)
        if (offscreenEditor == null) {
            return false
        }

        if (strokesForRefeed.isNotEmpty()) {
            clearAndRefeed(strokesForRefeed)
        }
        Log.w(
            "MyScriptRecognition",
            "recognition_restart_success: page=$pageId, strokes=${strokesForRefeed.size}",
        )
        return true
    }
}
