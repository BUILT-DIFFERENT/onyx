package com.onyx.android.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.PageTemplateDao
import com.onyx.android.data.dao.ThumbnailDao
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.ThumbnailEntity
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfiumRenderer
import com.onyx.android.ui.computeTemplatePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

private const val TAG = "ThumbnailGenerator"
private const val THUMBNAIL_SIZE = 256
private const val THUMBNAIL_QUALITY = 90
private const val MAX_CONCURRENT_GENERATIONS = 3
private const val STARTUP_RATE_LIMIT_PER_MINUTE = 10
private const val STARTUP_RATE_LIMIT_WINDOW_MS = 60_000L
private const val INK_THUMBNAIL_GRID_DIVISIONS = 8f
private const val CONTENT_HASH_BUFFER_SIZE_BYTES = 8192
private const val TEMPLATE_MIN_SPACING = 8f
private const val TEMPLATE_MAX_SPACING = 80f
private const val TEMPLATE_DOT_RADIUS_RATIO = 0.08f
private const val TEMPLATE_DOT_MIN_RADIUS = 0.8f
private const val TEMPLATE_DEFAULT_COLOR = "#E0E0E0"

/**
 * Generates and manages thumbnails for notes.
 *
 * Thumbnails are 256x256 PNG images used for note previews in the HomeScreen.
 * Features:
 * - Rate limiting: Max 3 concurrent generations, 10/min during startup
 * - Content hash: SHA-256 for cache validation
 * - Atomic write: temp file + rename for crash safety
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class ThumbnailGenerator(
    private val context: Context,
    private val thumbnailDao: ThumbnailDao,
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val pageTemplateDao: PageTemplateDao,
    private val pdfAssetStorage: PdfAssetStorage,
) {
    private data class ThumbnailTemplateRenderContext(
        val left: Float,
        val top: Float,
        val scale: Float,
        val color: Int,
        val spacing: Float,
    )

    private val thumbnailDir: File
        get() = File(context.filesDir, "thumbnails").also { it.mkdirs() }

    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_GENERATIONS)

    // Rate limiting for startup period
    private val startupEndTime = System.currentTimeMillis() + STARTUP_RATE_LIMIT_WINDOW_MS
    private val startupGenerationCount = AtomicLong(0)

    /**
     * Generate a thumbnail for a note.
     *
     * @param noteId The ID of the note to generate a thumbnail for
     * @param pages The pages of the note (if already loaded), or null to load from DB
     * @return The generated ThumbnailEntity, or null if generation failed
     */
    suspend fun generateThumbnail(
        noteId: String,
        pages: List<PageEntity>? = null,
    ): ThumbnailEntity? =
        withContext(Dispatchers.IO) {
            // Check rate limiting during startup period
            if (System.currentTimeMillis() < startupEndTime) {
                val count = startupGenerationCount.incrementAndGet()
                if (count > STARTUP_RATE_LIMIT_PER_MINUTE) {
                    Log.d(TAG, "Rate limiting thumbnail generation for note $noteId during startup")
                    return@withContext null
                }
            }

            concurrencySemaphore.acquire()
            try {
                generateThumbnailInternal(noteId, pages)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate thumbnail for note $noteId", e)
                null
            } finally {
                concurrencySemaphore.release()
            }
        }

    /**
     * Regenerate a thumbnail if it's missing from storage.
     * Called when cache is wiped or thumbnail file is corrupted.
     *
     * @param noteId The ID of the note to check
     * @return The regenerated ThumbnailEntity, or null if regeneration failed
     */
    suspend fun regenerateIfMissing(noteId: String): ThumbnailEntity? =
        withContext(Dispatchers.IO) {
            val existing = thumbnailDao.getByNoteId(noteId)

            if (existing != null) {
                val thumbnailFile = File(existing.filePath)
                if (thumbnailFile.exists()) {
                    // Thumbnail exists and file is present
                    return@withContext existing
                }
                // File is missing, need to regenerate
                Log.d(TAG, "Thumbnail file missing for note $noteId, regenerating...")
            }

            // Generate new thumbnail
            generateThumbnail(noteId)
        }

    /**
     * Delete a thumbnail for a note.
     *
     * @param noteId The ID of the note to delete the thumbnail for
     */
    suspend fun deleteThumbnail(noteId: String) =
        withContext(Dispatchers.IO) {
            val existing = thumbnailDao.getByNoteId(noteId)
            if (existing != null) {
                val file = File(existing.filePath)
                if (file.exists()) {
                    file.delete()
                }
                thumbnailDao.deleteByNoteId(noteId)
            }
        }

    /**
     * Get the thumbnail for a note, regenerating if missing.
     *
     * @param noteId The ID of the note
     * @return The ThumbnailEntity, or null if not available
     */
    suspend fun getThumbnail(noteId: String): ThumbnailEntity? =
        withContext(Dispatchers.IO) {
            val existing = thumbnailDao.getByNoteId(noteId)
            if (existing != null) {
                val file = File(existing.filePath)
                if (file.exists()) {
                    return@withContext existing
                }
                // File missing, regenerate
                Log.d(TAG, "Thumbnail file missing for note $noteId, regenerating...")
            }
            generateThumbnail(noteId)
        }

    /**
     * Generate thumbnails for multiple notes concurrently.
     *
     * @param noteIds List of note IDs to generate thumbnails for
     * @return Map of noteId to ThumbnailEntity (only successful generations)
     */
    suspend fun generateThumbnails(noteIds: List<String>): Map<String, ThumbnailEntity> =
        coroutineScope {
            noteIds
                .map { noteId ->
                    async { noteId to generateThumbnail(noteId) }
                }.awaitAll()
                .filter { it.second != null }
                .associate { it.first to it.second!! }
        }

    @Suppress("ReturnCount")
    private suspend fun generateThumbnailInternal(
        noteId: String,
        pages: List<PageEntity>?,
    ): ThumbnailEntity? {
        val notePages = pages ?: pageDao.getPagesForNoteSync(noteId)
        if (notePages.isEmpty()) {
            Log.d(TAG, "No pages found for note $noteId")
            return null
        }

        // Get the first page for thumbnail
        val firstPage = notePages.minByOrNull { it.indexInNote } ?: return null

        val bitmap =
            when (firstPage.kind) {
                "pdf", "mixed" -> {
                    renderPdfThumbnail(firstPage)
                }

                "ink" -> {
                    renderInkThumbnail(firstPage)
                }

                else -> {
                    Log.d(TAG, "Unknown page kind: ${firstPage.kind}")
                    null
                }
            }

        if (bitmap == null) {
            return null
        }

        return saveThumbnail(noteId, bitmap).also {
            bitmap.recycle()
        }
    }

    @Suppress("ReturnCount")
    private suspend fun renderPdfThumbnail(page: PageEntity): Bitmap? {
        val pdfAssetId = page.pdfAssetId ?: return null
        val pdfPageNo = page.pdfPageNo ?: 0

        if (!pdfAssetStorage.assetExists(pdfAssetId)) {
            Log.d(TAG, "PDF asset not found: $pdfAssetId")
            return null
        }

        val pdfFile = pdfAssetStorage.getFileForAsset(pdfAssetId)

        return try {
            val renderer = PdfiumRenderer(context, pdfFile)
            try {
                val pageBounds = renderer.getPageBounds(pdfPageNo)
                val pageWidth = pageBounds.width()
                val pageHeight = pageBounds.height()

                // Calculate scale to fit in THUMBNAIL_SIZE x THUMBNAIL_SIZE
                val scale =
                    min(
                        THUMBNAIL_SIZE.toFloat() / pageWidth,
                        THUMBNAIL_SIZE.toFloat() / pageHeight,
                    )

                // Render at calculated scale
                val renderedBitmap = renderer.renderPage(pdfPageNo, scale)

                // Create final thumbnail bitmap (may need padding if aspect ratio differs)
                val thumbnailBitmap =
                    Bitmap.createBitmap(
                        THUMBNAIL_SIZE,
                        THUMBNAIL_SIZE,
                        Bitmap.Config.ARGB_8888,
                    )
                val canvas = Canvas(thumbnailBitmap)
                canvas.drawColor(Color.WHITE)

                // Center the rendered bitmap
                val left = (THUMBNAIL_SIZE - renderedBitmap.width) / 2f
                val top = (THUMBNAIL_SIZE - renderedBitmap.height) / 2f
                canvas.drawBitmap(renderedBitmap, left, top, null)
                renderedBitmap.recycle()

                thumbnailBitmap
            } finally {
                renderer.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF thumbnail for asset $pdfAssetId", e)
            null
        }
    }

    private suspend fun renderInkThumbnail(page: PageEntity): Bitmap? {
        val bitmap =
            Bitmap.createBitmap(
                THUMBNAIL_SIZE,
                THUMBNAIL_SIZE,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val pageWidth = page.width.coerceAtLeast(1f)
        val pageHeight = page.height.coerceAtLeast(1f)
        val scale = min(THUMBNAIL_SIZE.toFloat() / pageWidth, THUMBNAIL_SIZE.toFloat() / pageHeight)
        val pageRenderWidth = pageWidth * scale
        val pageRenderHeight = pageHeight * scale
        val left = (THUMBNAIL_SIZE - pageRenderWidth) / 2f
        val top = (THUMBNAIL_SIZE - pageRenderHeight) / 2f

        val template = page.templateId?.let { templateId -> pageTemplateDao.getById(templateId) }
        val templateKind = template?.backgroundKind ?: "blank"
        val templateSpacing =
            template?.spacing?.coerceIn(TEMPLATE_MIN_SPACING, TEMPLATE_MAX_SPACING)
                ?: (pageWidth / INK_THUMBNAIL_GRID_DIVISIONS)
        val templateColor = parseTemplatePreviewColor(template?.color)

        val pattern =
            computeTemplatePattern(
                backgroundKind = templateKind,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                spacing = templateSpacing,
            )

        drawTemplatePatternOnThumbnail(
            canvas = canvas,
            pattern = pattern,
            context =
                ThumbnailTemplateRenderContext(
                    left = left,
                    top = top,
                    scale = scale,
                    color = templateColor,
                    spacing = templateSpacing,
                ),
        )

        return bitmap
    }

    private fun parseTemplatePreviewColor(rawColor: String?): Int =
        runCatching { Color.parseColor(rawColor ?: TEMPLATE_DEFAULT_COLOR) }
            .getOrDefault(Color.LTGRAY)

    private fun drawTemplatePatternOnThumbnail(
        canvas: Canvas,
        pattern: com.onyx.android.ui.TemplatePattern,
        context: ThumbnailTemplateRenderContext,
    ) {
        val linePaint =
            Paint().apply {
                this.color = context.color
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
        pattern.lines.forEach { line ->
            canvas.drawLine(
                context.left + (line.start.x * context.scale),
                context.top + (line.start.y * context.scale),
                context.left + (line.end.x * context.scale),
                context.top + (line.end.y * context.scale),
                linePaint,
            )
        }
        if (pattern.dots.isEmpty()) {
            return
        }
        val dotPaint =
            Paint().apply {
                this.color = context.color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        val dotRadius =
            maxOf(
                (context.spacing * TEMPLATE_DOT_RADIUS_RATIO * context.scale),
                TEMPLATE_DOT_MIN_RADIUS,
            )
        pattern.dots.forEach { dot ->
            canvas.drawCircle(
                context.left + (dot.center.x * context.scale),
                context.top + (dot.center.y * context.scale),
                dotRadius,
                dotPaint,
            )
        }
    }

    private suspend fun saveThumbnail(
        noteId: String,
        bitmap: Bitmap,
    ): ThumbnailEntity? {
        val thumbnailFile = File(thumbnailDir, "$noteId.png")
        val tempFile = File(thumbnailDir, "$noteId.png.tmp")

        return try {
            // Write to temp file first (atomic write pattern)
            FileOutputStream(tempFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, THUMBNAIL_QUALITY, output)
            }

            // Rename temp file to final file (atomic on most filesystems)
            if (!tempFile.renameTo(thumbnailFile)) {
                // Fallback: delete and rename
                thumbnailFile.delete()
                check(tempFile.renameTo(thumbnailFile)) { "Failed to rename temp thumbnail file" }
            }

            // Calculate content hash
            val contentHash = calculateContentHash(thumbnailFile)

            val entity =
                ThumbnailEntity(
                    noteId = noteId,
                    filePath = thumbnailFile.absolutePath,
                    contentHash = contentHash,
                    generatedAt = System.currentTimeMillis(),
                )

            thumbnailDao.insert(entity)
            Log.d(TAG, "Saved thumbnail for note $noteId")
            entity
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save thumbnail for note $noteId", e)
            tempFile.delete()
            null
        }
    }

    private fun calculateContentHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(CONTENT_HASH_BUFFER_SIZE_BYTES)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Clean up old thumbnails that are no longer associated with any note.
     */
    suspend fun cleanupOrphanedThumbnails() =
        withContext(Dispatchers.IO) {
            val thumbnailFiles = thumbnailDir.listFiles()?.toList() ?: emptyList()
            val noteIds =
                noteDao
                    .getAllNotes()
                    .first()
                    .map { it.noteId }
                    .toSet()

            thumbnailFiles.forEach { file ->
                val noteId = file.nameWithoutExtension
                if (noteId !in noteIds && file.name.endsWith(".png")) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted orphaned thumbnail: ${file.name}")
                        thumbnailDao.deleteByNoteId(noteId)
                    }
                }
            }
        }
}
