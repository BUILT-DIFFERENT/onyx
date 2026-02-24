package com.onyx.android.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeLineStyle
import com.onyx.android.ink.model.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@Serializable
data class PdfExportArtifactMetadata(
    val exportId: String,
    val noteId: String,
    val ownerUserId: String,
    val format: String = "pdf",
    val mode: String,
    val status: String = "succeeded",
    val assetId: String,
    val requestedAt: Long,
    val completedAt: Long,
    val errorMessage: String? = null,
)

data class PdfExportArtifact(
    val exportId: String,
    val pdfFile: File,
    val metadataFile: File,
    val flatten: Boolean,
)

class PdfExportRepository(
    private val context: Context,
    private val pdfAssetStorage: PdfAssetStorage,
    private val noteRepository: NoteRepository,
) {
    companion object {
        private const val MAX_EXPORT_NAME_LENGTH = 64
        private const val MIN_STROKE_WIDTH = 0.1f
        private const val HIGHLIGHTER_ALPHA = 96
        private const val DASH_LENGTH_FACTOR = 4f
        private const val DASH_GAP_FACTOR = 2f
        private const val DOT_GAP_FACTOR = 1.5f
    }

    private val json = Json { prettyPrint = true }

    suspend fun exportNotePdf(
        note: NoteEntity,
        pdfAssetId: String,
        flatten: Boolean,
    ): PdfExportArtifact =
        withContext(Dispatchers.IO) {
            val exportsDir = File(context.filesDir, "exports").also { it.mkdirs() }
            val exportId = UUID.randomUUID().toString()
            val requestedAt = System.currentTimeMillis()
            val sourceFile = pdfAssetStorage.getFileForAsset(pdfAssetId)
            check(sourceFile.exists()) { "PDF asset not found for export." }

            val mode = if (flatten) "flattened" else "layered"
            val safeTitle = sanitizeFileName(note.title.ifBlank { "note" })
            val outputName = "${safeTitle}_$mode.pdf"
            val outputFile = File(exportsDir, outputName)
            if (flatten) {
                exportFlattenedPdf(
                    note = note,
                    sourcePdf = sourceFile,
                    outputFile = outputFile,
                )
            } else {
                sourceFile.inputStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val metadata =
                PdfExportArtifactMetadata(
                    exportId = exportId,
                    noteId = note.noteId,
                    ownerUserId = note.ownerUserId,
                    mode = mode,
                    assetId = outputName,
                    requestedAt = requestedAt,
                    completedAt = System.currentTimeMillis(),
                )
            val metadataFile = File(exportsDir, "${outputName.removeSuffix(".pdf")}.metadata.json")
            metadataFile.writeText(json.encodeToString(metadata))

            PdfExportArtifact(
                exportId = exportId,
                pdfFile = outputFile,
                metadataFile = metadataFile,
                flatten = flatten,
            )
        }

    private suspend fun exportFlattenedPdf(
        note: NoteEntity,
        sourcePdf: File,
        outputFile: File,
    ) {
        val pages = noteRepository.getPagesForNoteSync(note.noteId).sortedBy { page -> page.indexInNote }
        if (pages.isEmpty()) {
            sourcePdf.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        val document = PdfDocument()
        val renderer = PdfiumRenderer(context = context, pdfFile = sourcePdf)
        try {
            pages.forEachIndexed { outputPageIndex, page ->
                val pageWidth = page.width.roundToInt().coerceAtLeast(1)
                val pageHeight = page.height.roundToInt().coerceAtLeast(1)
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, outputPageIndex + 1).create()
                val documentPage = document.startPage(pageInfo)
                val canvas = documentPage.canvas
                canvas.drawColor(Color.WHITE)

                val pdfPageNo = page.pdfPageNo
                if (pdfPageNo != null && pdfPageNo in 0 until renderer.getPageCount()) {
                    drawSourcePdfPage(
                        renderer = renderer,
                        pageIndex = pdfPageNo,
                        canvasWidth = pageWidth.toFloat(),
                        canvasHeight = pageHeight.toFloat(),
                        pageCanvas = canvas,
                    )
                }

                val strokes = noteRepository.getStrokesForPage(page.pageId)
                drawStrokesOnCanvas(
                    strokes = strokes,
                    pageCanvas = canvas,
                )
                document.finishPage(documentPage)
            }

            outputFile.outputStream().use { output ->
                document.writeTo(output)
            }
        } finally {
            document.close()
            renderer.close()
        }
    }

    private fun drawSourcePdfPage(
        renderer: PdfiumRenderer,
        pageIndex: Int,
        canvasWidth: Float,
        canvasHeight: Float,
        pageCanvas: android.graphics.Canvas,
    ) {
        val sourceBounds = renderer.getPageBounds(pageIndex)
        val sourceWidth = sourceBounds.width().coerceAtLeast(1f)
        val zoomScale = (canvasWidth / sourceWidth).coerceAtLeast(1f)
        val renderedPage = renderer.renderPage(pageIndex = pageIndex, zoom = zoomScale)
        pageCanvas.drawBitmap(
            renderedPage,
            null,
            RectF(0f, 0f, canvasWidth, canvasHeight),
            null,
        )
    }

    private fun drawStrokesOnCanvas(
        strokes: List<Stroke>,
        pageCanvas: android.graphics.Canvas,
    ) {
        strokes.forEach { stroke ->
            if (
                stroke.style.tool == Tool.ERASER ||
                stroke.style.tool == Tool.LASSO ||
                stroke.points.isEmpty()
            ) {
                return@forEach
            }
            val paint =
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                    color = runCatching { Color.parseColor(stroke.style.color) }.getOrDefault(Color.BLACK)
                    strokeWidth = stroke.style.baseWidth.coerceAtLeast(MIN_STROKE_WIDTH)
                    if (stroke.style.tool == Tool.HIGHLIGHTER) {
                        alpha = HIGHLIGHTER_ALPHA
                    }
                    pathEffect =
                        when (stroke.style.lineStyle) {
                            StrokeLineStyle.DASHED ->
                                DashPathEffect(
                                    floatArrayOf(strokeWidth * DASH_LENGTH_FACTOR, strokeWidth * DASH_GAP_FACTOR),
                                    0f,
                                )

                            StrokeLineStyle.DOTTED ->
                                DashPathEffect(
                                    floatArrayOf(strokeWidth, strokeWidth * DOT_GAP_FACTOR),
                                    0f,
                                )

                            StrokeLineStyle.SOLID -> null
                        }
                }

            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                pageCanvas.drawPoint(point.x, point.y, paint)
                return@forEach
            }

            val path = Path()
            val firstPoint = stroke.points.first()
            path.moveTo(firstPoint.x, firstPoint.y)
            for (index in 1 until stroke.points.size) {
                val previous = stroke.points[index - 1]
                val current = stroke.points[index]
                val controlX = (previous.x + current.x) / 2f
                val controlY = (previous.y + current.y) / 2f
                path.quadTo(previous.x, previous.y, controlX, controlY)
            }
            val lastPoint = stroke.points.last()
            path.lineTo(lastPoint.x, lastPoint.y)
            pageCanvas.drawPath(path, paint)
        }
    }

    private fun sanitizeFileName(input: String): String =
        input
            .trim()
            .ifBlank { "note" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(MAX_EXPORT_NAME_LENGTH)
}
