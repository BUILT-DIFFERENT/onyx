package com.onyx.android.recognition

import android.content.Context
import android.util.Log
import com.myscript.iink.ContentPackage
import com.myscript.iink.ContentPart
import com.myscript.iink.EditorError
import com.myscript.iink.Engine
import com.myscript.iink.IOffscreenEditorListener
import com.myscript.iink.ItemIdHelper
import com.myscript.iink.MimeType
import com.myscript.iink.OffscreenEditor
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerType
import com.onyx.android.ink.model.Stroke
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
class MyScriptPageManager(
    private val engine: Engine,
    private val context: Context,
    // NOTE: displayMetrics not needed - we use fixed pt→mm conversion
) {
    private var currentPageId: String? = null
    private var currentPackage: ContentPackage? = null
    private var currentPart: ContentPart? = null
    private var offscreenEditor: OffscreenEditor? = null
    private var itemIdHelper: ItemIdHelper? = null

    // Map MyScript stroke IDs to our stroke UUIDs
    private val strokeIdsMapping = mutableMapOf<String, String>()

    // Callback for recognition updates (set by ViewModel/Repository)
    var onRecognitionUpdated: ((pageId: String, text: String) -> Unit)? = null

    /**
     * Coordinate conversion: Page pt → MyScript mm (fixed ratio, DPI independent).
     * 1 pt = 1/72 inch, 1 inch = 25.4 mm
     * Therefore: 1 pt = 25.4 / 72 mm ≈ 0.3528 mm
     */
    private fun ptToMm(pt: Float): Float = pt * MM_PER_POINT

    companion object {
        private const val MM_PER_INCH = 25.4f
        private const val POINTS_PER_INCH = 72f
        private const val MM_PER_POINT = MM_PER_INCH / POINTS_PER_INCH
        private const val DEFAULT_PRESSURE = 0.5f
    }

    fun onPageEnter(pageId: String) {
        if (currentPageId != pageId) {
            closeCurrentPage()
        }

        currentPageId = pageId
        val packagePath = File(context.filesDir, "myscript/page_$pageId.iink")
        packagePath.parentFile?.mkdirs()

        currentPackage =
            if (packagePath.exists()) {
                engine.openPackage(packagePath)
            } else {
                engine.createPackage(packagePath)
            }

        currentPart =
            if (currentPackage!!.partCount > 0) {
                currentPackage!!.getPart(0)
            } else {
                currentPackage!!.createPart("Raw Content")
            }

        offscreenEditor = engine.createOffscreenEditor(1f, 1f)
        offscreenEditor?.part = currentPart

        offscreenEditor?.addListener(recognitionListener)

        itemIdHelper = engine.createItemIdHelper(offscreenEditor!!)
        strokeIdsMapping.clear()

        Log.d("MyScript", "Page entered: $pageId")
    }

    /**
     * Called when navigating AWAY from a page.
     * Saves and closes ContentPackage.
     */
    fun closeCurrentPage() {
        val pageId = currentPageId ?: return

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

        Log.d("MyScript", "Page closed: $pageId")
    }

    /**
     * Add stroke to recognizer using OffscreenEditor.addStrokes().
     * Strokes are in page coordinates (pt), converted to mm for MyScript.
     * Returns MyScript's stroke ID for mapping.
     */
    fun addStroke(stroke: Stroke): String? {
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
            Log.d("MyScript", "Stroke added: ${stroke.id} → MyScript ID: $msStrokeId")
        }
    }

    fun onStrokeErased(
        erasedStrokeId: String,
        remainingStrokes: List<Stroke>,
    ) {
        val editor = offscreenEditor ?: return

        editor.clear()
        strokeIdsMapping.clear()
        remainingStrokes.forEach { stroke: Stroke ->
            addStroke(stroke)
        }
        Log.d("MyScript", "Stroke erased: $erasedStrokeId, re-fed ${remainingStrokes.size} strokes")
    }

    fun onUndo(currentStrokes: List<Stroke>) {
        val editor = offscreenEditor ?: return

        val history = editor.historyManager
        if (history != null && history.possibleUndoCount > 0) {
            history.undo()
            Log.d("MyScript", "Native undo performed")
            return
        }

        editor.clear()
        strokeIdsMapping.clear()
        currentStrokes.forEach { stroke: Stroke ->
            addStroke(stroke)
        }
        Log.d("MyScript", "Undo via clear+re-feed: ${currentStrokes.size} strokes")
    }

    fun onRedo(currentStrokes: List<Stroke>) {
        val editor = offscreenEditor ?: return

        val history = editor.historyManager
        if (history != null && history.possibleRedoCount > 0) {
            history.redo()
            Log.d("MyScript", "Native redo performed")
            return
        }

        editor.clear()
        strokeIdsMapping.clear()
        currentStrokes.forEach { stroke: Stroke ->
            addStroke(stroke)
        }
        Log.d("MyScript", "Redo via clear+re-feed: ${currentStrokes.size} strokes")
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
                // Recognition result available
                val text = exportText()
                Log.d("MyScript", "Recognition updated: $text")

                // Emit to observers for database storage
                val pageId = currentPageId
                if (pageId != null && text != null) {
                    onRecognitionUpdated?.invoke(pageId, text)
                }
            }

            override fun onError(
                editor: OffscreenEditor,
                blockId: String,
                err: EditorError,
                message: String,
            ) {
                Log.e("MyScript", "Recognition error [$err]: $message")
            }
        }
}
