package com.onyx.android.data.repository

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.onyx.android.data.dao.FolderDao
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.PageObjectDao
import com.onyx.android.data.dao.PageTemplateDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.dao.TagDao
import com.onyx.android.data.entity.FolderEntity
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.NoteTagCrossRef
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.PageObjectEntity
import com.onyx.android.data.entity.RecognitionIndexEntity
import com.onyx.android.data.entity.StrokeEntity
import com.onyx.android.data.entity.TagEntity
import com.onyx.android.data.entity.ThumbnailEntity
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.data.thumbnail.ThumbnailGenerator
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.ink.model.Stroke
import com.onyx.android.objects.model.ImagePayload
import com.onyx.android.objects.model.PageObject
import com.onyx.android.objects.model.PageObjectKind
import com.onyx.android.objects.model.ShapePayload
import com.onyx.android.objects.model.TextPayload
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.pdf.PdfTextChar
import com.onyx.android.pdf.PdfiumRenderer
import com.onyx.android.recognition.ConvertedTextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class SearchSourceType {
    INK,
    PDF,
    NOTE_METADATA,
    PAGE_METADATA,
}

data class SearchResult(
    val resultId: String,
    val noteId: String,
    val noteTitle: String,
    val pageId: String?,
    val sourceType: SearchSourceType,
    val matchedText: String,
    val contextSnippet: String,
    val bounds: Rect?,
    val pageIndex: Int?,
    val highlightColor: Color,
)

/**
 * Sort options for notes list.
 */
enum class SortOption {
    NAME,
    CREATED,
    MODIFIED,
}

/**
 * Sort direction for notes list.
 */
enum class SortDirection {
    ASC,
    DESC,
}

/**
 * Date range filter options.
 */
enum class DateRange {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    OLDER,
}

/**
 * Combined filter state for notes list.
 */
data class FilterState(
    val folderId: String? = null,
    val tagId: String? = null,
    val dateRange: DateRange? = null,
)

/**
 * Exception thrown when attempting to create a tag with a name that already exists.
 */
class DuplicateTagNameException(
    tagName: String,
) : Exception("Tag with name '$tagName' already exists")

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class NoteRepository(
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val pageObjectDao: PageObjectDao,
    private val strokeDao: StrokeDao,
    private val recognitionDao: RecognitionDao,
    private val folderDao: FolderDao,
    private val pageTemplateDao: PageTemplateDao,
    private val tagDao: TagDao,
    private val deviceIdentity: DeviceIdentity,
    private val strokeSerializer: StrokeSerializer,
    private val appContext: Context,
    private val pdfAssetStorage: PdfAssetStorage,
    private val pdfPasswordStore: PdfPasswordStore,
    private val thumbnailGenerator: ThumbnailGenerator?,
) {
    companion object {
        private const val SNIPPET_LENGTH = 100
        private const val CONTEXT_RADIUS = 40
        private const val MAX_SEARCH_RESULTS = 50
        private const val SCORE_WEIGHT_INK = 8.0
        private const val SCORE_WEIGHT_PDF = 7.0
        private const val SCORE_WEIGHT_PAGE_METADATA = 5.0
        private const val SCORE_WEIGHT_NOTE_METADATA = 4.0
        private const val SCORE_METADATA_PENALTY = -4.0
        private const val SCORE_EXACT_BOOST = 25.0
        private const val SCORE_PREFIX_BOOST = 8.0
        private const val PAGE_TEMPLATE_ID_SUFFIX = "template"
        private const val TEMPLATE_MIN_SPACING = 8f
        private const val TEMPLATE_MAX_SPACING = 80f
        private const val RGB_MASK = 0xFFFFFF
        private const val DEFAULT_PAGE_WIDTH_PT = 612f
        private const val DEFAULT_PAGE_HEIGHT_PT = 792f
        private const val PAPER_TEMPLATE_PREFIX = "paper:"
        private const val PAPER_A4 = "a4"
        private const val PAPER_LETTER = "letter"
        private const val PAPER_PHONE = "phone"
        private const val PAPER_A4_WIDTH_PT = 595f
        private const val PAPER_A4_HEIGHT_PT = 842f
        private const val PAPER_PHONE_WIDTH_PT = 360f
        private const val PAPER_PHONE_HEIGHT_PT = 640f
        private val INK_RESULT_COLOR = Color(0xFF0A84FF)
        private val PDF_RESULT_COLOR = Color(0xFFF2994A)
        private val NOTE_METADATA_RESULT_COLOR = Color(0xFF34A853)
        private val PAGE_METADATA_RESULT_COLOR = Color(0xFF7E57C2)
    }

    private data class RankedResult(
        val result: SearchResult,
        val score: Double,
        val updatedAt: Long,
    )

    private val overlayJson = Json { ignoreUnknownKeys = true }
    private val objectJson = Json { ignoreUnknownKeys = true }

    suspend fun createNote(): NoteWithFirstPage {
        val now = System.currentTimeMillis()
        val note =
            NoteEntity(
                noteId = UUID.randomUUID().toString(),
                ownerUserId = deviceIdentity.getDeviceId(),
                title = "",
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        noteDao.insert(note)

        val firstPage = createPageForNote(note.noteId, indexInNote = 0)
        return NoteWithFirstPage(note, firstPage.pageId)
    }

    data class NoteWithFirstPage(
        val note: NoteEntity,
        val firstPageId: String,
    )

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun getTrashNotes(): Flow<List<NoteEntity>> = noteDao.getDeletedNotes()

    fun getSharedNotes(): Flow<List<NoteEntity>> = flowOf(emptyList())

    fun getPagesForNote(noteId: String): Flow<List<PageEntity>> = pageDao.getPagesForNote(noteId)

    suspend fun createPage(page: PageEntity): PageEntity {
        val now = System.currentTimeMillis()
        val storedPage = page.copy(updatedAt = now)
        pageDao.insert(storedPage)

        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = storedPage.pageId,
                noteId = storedPage.noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )

        noteDao.updateTimestamp(storedPage.noteId, now)
        return storedPage
    }

    suspend fun createPageForNote(
        noteId: String,
        indexInNote: Int,
    ): PageEntity {
        val now = System.currentTimeMillis()
        val paperTemplateId = pageDao.getPagesForNoteSync(noteId).lastOrNull()?.templateId
        val (pageWidth, pageHeight) = resolvePaperSize(paperTemplateId)
        val page =
            PageEntity(
                pageId = UUID.randomUUID().toString(),
                noteId = noteId,
                kind = "ink",
                geometryKind = "fixed",
                indexInNote = indexInNote,
                width = pageWidth,
                height = pageHeight,
                unit = "pt",
                pdfAssetId = null,
                pdfPageNo = null,
                updatedAt = now,
                contentLamportMax = 0,
            )
        pageDao.insert(page)

        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = page.pageId,
                noteId = noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )

        noteDao.updateTimestamp(noteId, now)
        return page
    }

    private fun resolvePaperSize(templateId: String?): Pair<Float, Float> =
        when (templateId?.removePrefix(PAPER_TEMPLATE_PREFIX)) {
            PAPER_A4 -> PAPER_A4_WIDTH_PT to PAPER_A4_HEIGHT_PT
            PAPER_PHONE -> PAPER_PHONE_WIDTH_PT to PAPER_PHONE_HEIGHT_PT
            PAPER_LETTER -> DEFAULT_PAGE_WIDTH_PT to DEFAULT_PAGE_HEIGHT_PT
            else -> DEFAULT_PAGE_WIDTH_PT to DEFAULT_PAGE_HEIGHT_PT
        }

    suspend fun movePage(
        noteId: String,
        pageId: String,
        targetIndex: Int,
    ) {
        val pages = pageDao.getPagesForNoteSync(noteId)
        if (pages.isNotEmpty()) {
            val currentIndex = pages.indexOfFirst { page -> page.pageId == pageId }
            val boundedTarget = targetIndex.coerceIn(0, pages.lastIndex)
            if (currentIndex >= 0 && boundedTarget != currentIndex) {
                val reordered = pages.toMutableList()
                val movedPage = reordered.removeAt(currentIndex)
                reordered.add(boundedTarget, movedPage)
                resequencePages(noteId = noteId, pages = reordered)
            }
        }
    }

    suspend fun duplicatePage(pageId: String): PageEntity {
        val source = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        val noteId = source.noteId
        val pages = pageDao.getPagesForNoteSync(noteId)
        val insertIndex = (source.indexInNote + 1).coerceAtMost(pages.size)
        val now = System.currentTimeMillis()
        val duplicatedPageId = UUID.randomUUID().toString()
        val duplicatedPage =
            source.copy(
                pageId = duplicatedPageId,
                indexInNote = insertIndex,
                updatedAt = now,
            )

        pages
            .filter { page -> page.indexInNote >= insertIndex }
            .sortedBy { page -> page.indexInNote }
            .forEach { page ->
                pageDao.updateIndex(page.pageId, page.indexInNote + 1, now)
            }

        pageDao.insert(duplicatedPage)

        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = duplicatedPageId,
                noteId = noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )

        val duplicatedStrokes =
            strokeDao.getByPageId(pageId).map { stroke ->
                stroke.copy(
                    strokeId = UUID.randomUUID().toString(),
                    pageId = duplicatedPageId,
                    createdAt = now,
                )
            }
        if (duplicatedStrokes.isNotEmpty()) {
            strokeDao.insertAll(duplicatedStrokes)
        }

        val duplicatedObjects =
            pageObjectDao.getByPageId(pageId).map { pageObject ->
                pageObject.copy(
                    objectId = UUID.randomUUID().toString(),
                    pageId = duplicatedPageId,
                    noteId = noteId,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            }
        if (duplicatedObjects.isNotEmpty()) {
            pageObjectDao.insertAll(duplicatedObjects)
        }

        noteDao.updateTimestamp(noteId, now)
        return duplicatedPage
    }

    suspend fun getTemplateConfig(templateId: String?): PageTemplateConfig {
        if (templateId.isNullOrBlank()) {
            return PageTemplateConfig.BLANK
        }
        val template = pageTemplateDao.getById(templateId)
        return template?.toConfig() ?: PageTemplateConfig.BLANK
    }

    suspend fun getTemplateConfigMapByPageId(pages: List<PageEntity>): Map<String, PageTemplateConfig> {
        if (pages.isEmpty()) {
            return emptyMap()
        }
        val templateIds = pages.mapNotNull { page -> page.templateId }.toSet()
        val templateById =
            if (templateIds.isEmpty()) {
                emptyMap()
            } else {
                pageTemplateDao
                    .getByIds(templateIds)
                    .associateBy { template -> template.templateId }
            }
        return pages.associate { page ->
            val config =
                page.templateId
                    ?.let { templateId -> templateById[templateId]?.toConfig() }
                    ?: PageTemplateConfig.BLANK
            page.pageId to config
        }
    }

    suspend fun saveTemplateForPage(
        pageId: String,
        backgroundKind: String,
        spacing: Float,
        colorHex: String,
    ) {
        val now = System.currentTimeMillis()
        val page = pageDao.getById(pageId) ?: return
        if (backgroundKind == PageTemplateConfig.KIND_BLANK) {
            pageDao.updateTemplate(pageId = pageId, templateId = null, updatedAt = now)
            noteDao.updateTimestamp(page.noteId, now)
            return
        }

        val normalizedKind = normalizeBackgroundKind(backgroundKind)
        val normalizedSpacing = spacing.coerceIn(TEMPLATE_MIN_SPACING, TEMPLATE_MAX_SPACING)
        val normalizedColor = normalizeColorHex(colorHex)
        val templateId = "$pageId-$PAGE_TEMPLATE_ID_SUFFIX"
        pageTemplateDao.insert(
            com.onyx.android.data.entity.PageTemplateEntity(
                templateId = templateId,
                name = templateName(normalizedKind, normalizedSpacing),
                backgroundKind = normalizedKind,
                spacing = normalizedSpacing,
                color = normalizedColor,
                isBuiltIn = false,
                createdAt = now,
            ),
        )
        pageDao.updateTemplate(pageId = pageId, templateId = templateId, updatedAt = now)
        noteDao.updateTimestamp(page.noteId, now)
    }

    @Suppress("LongParameterList")
    suspend fun createPageFromPdf(
        noteId: String,
        indexInNote: Int,
        pdfAssetId: String,
        pdfPageNo: Int,
        pdfWidth: Float,
        pdfHeight: Float,
    ): PageEntity {
        val now = System.currentTimeMillis()
        val page =
            PageEntity(
                pageId = UUID.randomUUID().toString(),
                noteId = noteId,
                kind = "pdf",
                geometryKind = "fixed",
                indexInNote = indexInNote,
                width = pdfWidth,
                height = pdfHeight,
                unit = "pt",
                pdfAssetId = pdfAssetId,
                pdfPageNo = pdfPageNo,
                updatedAt = now,
                contentLamportMax = 0,
            )
        pageDao.insert(page)
        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = page.pageId,
                noteId = noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )
        noteDao.updateTimestamp(noteId, now)
        return page
    }

    suspend fun saveStroke(
        pageId: String,
        stroke: Stroke,
    ) {
        val now = System.currentTimeMillis()
        val entity = stroke.toEntity(pageId)
        strokeDao.insert(entity)

        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun deleteStroke(strokeId: String) {
        val stroke = strokeDao.getById(strokeId) ?: return
        strokeDao.delete(strokeId)

        val now = System.currentTimeMillis()
        pageDao.updateTimestamp(stroke.pageId, now)
        val page = requireNotNull(pageDao.getById(stroke.pageId)) { "Page not found: ${stroke.pageId}" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun replaceStrokeWithSegments(
        pageId: String,
        originalStrokeId: String,
        segments: List<Stroke>,
    ) {
        val now = System.currentTimeMillis()
        strokeDao.delete(originalStrokeId)
        if (segments.isNotEmpty()) {
            strokeDao.insertAll(segments.map { segment -> segment.toEntity(pageId) })
        }
        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    private fun com.onyx.android.data.entity.PageTemplateEntity.toConfig(): PageTemplateConfig =
        PageTemplateConfig(
            templateId = templateId,
            backgroundKind = normalizeBackgroundKind(backgroundKind),
            spacing =
                spacing?.coerceIn(TEMPLATE_MIN_SPACING, TEMPLATE_MAX_SPACING)
                    ?: PageTemplateConfig.DEFAULT_SPACING,
            colorHex = normalizeColorHex(color),
        )

    private fun normalizeBackgroundKind(backgroundKind: String): String =
        when (backgroundKind) {
            PageTemplateConfig.KIND_GRID,
            PageTemplateConfig.KIND_LINED,
            PageTemplateConfig.KIND_DOTTED,
            -> backgroundKind

            else -> PageTemplateConfig.KIND_GRID
        }

    private fun templateName(
        kind: String,
        spacing: Float,
    ): String = "${kind.replaceFirstChar { value -> value.uppercase() }} ${spacing.toInt()}pt"

    private fun normalizeColorHex(colorHex: String?): String {
        if (colorHex.isNullOrBlank()) {
            return PageTemplateConfig.DEFAULT_COLOR_HEX
        }
        return runCatching {
            val colorInt = android.graphics.Color.parseColor(colorHex)
            String.format(Locale.US, "#%06X", (RGB_MASK and colorInt))
        }.getOrDefault(PageTemplateConfig.DEFAULT_COLOR_HEX)
    }

    suspend fun restoreSplitStroke(
        pageId: String,
        original: Stroke,
        segmentIds: List<String>,
    ) {
        val now = System.currentTimeMillis()
        if (segmentIds.isNotEmpty()) {
            strokeDao.deleteByIds(segmentIds)
        }
        strokeDao.insert(original.toEntity(pageId))
        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun replaceStrokes(
        pageId: String,
        strokes: List<Stroke>,
    ) {
        if (strokes.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        strokeDao.insertAll(strokes.map { stroke -> stroke.toEntity(pageId) })
        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun getStrokesForPage(pageId: String): List<Stroke> =
        strokeDao.getByPageId(pageId).map { entity ->
            Stroke(
                id = entity.strokeId,
                points = strokeSerializer.deserializePoints(entity.strokeData),
                style = strokeSerializer.deserializeStyle(entity.style),
                bounds = strokeSerializer.deserializeBounds(entity.bounds),
                createdAt = entity.createdAt,
                createdLamport = entity.createdLamport,
            )
        }

    private fun Stroke.toEntity(pageId: String): StrokeEntity =
        StrokeEntity(
            strokeId = id,
            pageId = pageId,
            strokeData = strokeSerializer.serializePoints(points),
            style = strokeSerializer.serializeStyle(style),
            bounds = strokeSerializer.serializeBounds(bounds),
            createdAt = createdAt,
            createdLamport = createdLamport,
        )

    private fun PageObjectEntity.toDomain(): PageObject {
        val kind = PageObjectKind.fromStorageValue(kind)
        val shapePayload =
            if (kind == PageObjectKind.SHAPE) {
                runCatching {
                    objectJson.decodeFromString(ShapePayload.serializer(), payloadJson)
                }.getOrNull()
            } else {
                null
            }
        val imagePayload =
            if (kind == PageObjectKind.IMAGE) {
                runCatching {
                    objectJson.decodeFromString(ImagePayload.serializer(), payloadJson)
                }.getOrNull()
            } else {
                null
            }
        val textPayload =
            if (kind == PageObjectKind.TEXT) {
                runCatching {
                    objectJson.decodeFromString(TextPayload.serializer(), payloadJson)
                }.getOrNull()
            } else {
                null
            }

        return PageObject(
            objectId = objectId,
            pageId = pageId,
            noteId = noteId,
            kind = kind,
            zIndex = zIndex,
            x = x,
            y = y,
            width = width,
            height = height,
            rotationDeg = rotationDeg,
            payloadJson = payloadJson,
            shapePayload = shapePayload,
            imagePayload = imagePayload,
            textPayload = textPayload,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
    }

    private fun PageObject.toEntity(): PageObjectEntity {
        val resolvedPayloadJson =
            when (kind) {
                PageObjectKind.SHAPE ->
                    shapePayload?.let {
                        objectJson.encodeToString(ShapePayload.serializer(), it)
                    } ?: payloadJson

                PageObjectKind.IMAGE ->
                    imagePayload?.let {
                        objectJson.encodeToString(ImagePayload.serializer(), it)
                    } ?: payloadJson

                PageObjectKind.TEXT ->
                    textPayload?.let {
                        objectJson.encodeToString(TextPayload.serializer(), it)
                    } ?: payloadJson
            }
        return PageObjectEntity(
            objectId = objectId,
            pageId = pageId,
            noteId = noteId,
            kind = kind.storageValue,
            zIndex = zIndex,
            x = x,
            y = y,
            width = width,
            height = height,
            rotationDeg = rotationDeg,
            payloadJson = resolvedPayloadJson,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
    }

    /**
     * Load strokes for multiple pages in a single transaction.
     * More efficient than calling getStrokesForPage multiple times.
     *
     * @param pageIds List of page IDs to load strokes for
     * @return Map of pageId to list of strokes for that page
     */
    suspend fun getStrokesForPages(pageIds: List<String>): Map<String, List<Stroke>> {
        if (pageIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, List<Stroke>>()
        for (pageId in pageIds) {
            result[pageId] = getStrokesForPage(pageId)
        }
        return result
    }

    fun getPageObjectsFlow(pageId: String): Flow<List<PageObject>> =
        pageObjectDao
            .getByPageIdFlow(pageId)
            .map { entities -> entities.map { entity -> entity.toDomain() } }

    suspend fun getPageObjectsForPage(pageId: String): List<PageObject> =
        pageObjectDao.getByPageId(pageId).map { entity -> entity.toDomain() }

    suspend fun getPageObjectsForPages(pageIds: List<String>): Map<String, List<PageObject>> {
        if (pageIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, List<PageObject>>()
        for (pageId in pageIds) {
            result[pageId] = getPageObjectsForPage(pageId)
        }
        return result
    }

    suspend fun getNextPageObjectZIndex(pageId: String): Int = pageObjectDao.getMaxZIndex(pageId) + 1

    suspend fun upsertPageObject(pageObject: PageObject) {
        pageObjectDao.insert(pageObject.toEntity())
        updatePageTimestamp(pageObject.pageId)
    }

    suspend fun upsertPageObjects(pageObjects: List<PageObject>) {
        if (pageObjects.isEmpty()) {
            return
        }
        pageObjectDao.insertAll(pageObjects.map { pageObject -> pageObject.toEntity() })
        val pageIds = pageObjects.mapTo(mutableSetOf()) { pageObject -> pageObject.pageId }
        pageIds.forEach { pageId -> updatePageTimestamp(pageId) }
    }

    suspend fun updatePageObjectGeometry(pageObject: PageObject) {
        val now = System.currentTimeMillis()
        pageObjectDao.updateGeometry(
            objectId = pageObject.objectId,
            x = pageObject.x,
            y = pageObject.y,
            width = pageObject.width,
            height = pageObject.height,
            rotationDeg = pageObject.rotationDeg,
            updatedAt = now,
        )
        updatePageTimestamp(pageObject.pageId)
    }

    suspend fun deletePageObject(
        objectId: String,
        pageId: String,
    ) {
        val now = System.currentTimeMillis()
        pageObjectDao.markDeleted(
            objectId = objectId,
            deletedAt = now,
            updatedAt = now,
        )
        updatePageTimestamp(pageId)
    }

    suspend fun updateRecognition(
        pageId: String,
        text: String?,
        recognizerVersion: String?,
    ) {
        val now = System.currentTimeMillis()
        recognitionDao.updateRecognition(
            pageId = pageId,
            text = text,
            version = recognizerVersion,
            updatedAt = now,
        )

        updatePageTimestamp(pageId)
    }

    suspend fun getRecognitionText(pageId: String): String? = recognitionDao.getByPageId(pageId)?.recognizedText

    suspend fun getConvertedTextBlocks(pageId: String): List<ConvertedTextBlock> =
        withContext(Dispatchers.IO) {
            val file = conversionOverlayFile(pageId)
            if (!file.exists()) {
                return@withContext emptyList()
            }
            runCatching {
                overlayJson.decodeFromString(ListSerializer(ConvertedTextBlock.serializer()), file.readText())
            }.getOrElse { emptyList() }
        }

    suspend fun saveConvertedTextBlocks(
        pageId: String,
        blocks: List<ConvertedTextBlock>,
    ) {
        withContext(Dispatchers.IO) {
            val file = conversionOverlayFile(pageId)
            file.parentFile?.mkdirs()
            file.writeText(overlayJson.encodeToString(ListSerializer(ConvertedTextBlock.serializer()), blocks))
        }
    }

    suspend fun upgradePageToMixed(pageId: String) {
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        if (page.kind == "pdf") {
            pageDao.updateKind(pageId, "mixed")
            if (recognitionDao.getByPageId(pageId) == null) {
                val now = System.currentTimeMillis()
                recognitionDao.insert(
                    RecognitionIndexEntity(
                        pageId = pageId,
                        noteId = page.noteId,
                        recognizedText = null,
                        recognizedAtLamport = null,
                        recognizerVersion = null,
                        updatedAt = now,
                    ),
                )
            }
            updatePageTimestamp(pageId)
        }
    }

    suspend fun deletePage(pageId: String) {
        val page = pageDao.getById(pageId) ?: return
        val now = System.currentTimeMillis()
        strokeDao.deleteAllForPage(pageId)
        pageObjectDao.deleteAllForPage(pageId)
        recognitionDao.deleteByPageId(pageId)
        pageDao.delete(pageId)
        val remainingPages = pageDao.getPagesForNoteSync(page.noteId)
        resequencePages(noteId = page.noteId, pages = remainingPages, timestamp = now)
    }

    private suspend fun resequencePages(
        noteId: String,
        pages: List<PageEntity>,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        pages.forEachIndexed { index, page ->
            if (page.indexInNote != index) {
                pageDao.updateIndex(page.pageId, index, timestamp)
            }
        }
        noteDao.updateTimestamp(noteId, timestamp)
    }

    suspend fun deleteNote(noteId: String) {
        val now = System.currentTimeMillis()
        noteDao.softDelete(noteId, now)
    }

    suspend fun restoreNote(noteId: String) {
        val now = System.currentTimeMillis()
        noteDao.restore(noteId = noteId, updatedAt = now)
    }

    suspend fun permanentlyDeleteNote(noteId: String) {
        val pages = pageDao.getPagesForNoteSync(noteId)
        pages.forEach { page ->
            strokeDao.deleteAllForPage(page.pageId)
            pageObjectDao.deleteAllForPage(page.pageId)
            recognitionDao.deleteByPageId(page.pageId)
            pageDao.delete(page.pageId)
        }
        noteDao.hardDelete(noteId)
    }

    suspend fun clearPageContent(pageId: String) {
        val page = pageDao.getById(pageId) ?: return
        val now = System.currentTimeMillis()
        strokeDao.deleteAllForPage(pageId)
        pageObjectDao.deleteAllForPage(pageId)
        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = pageId,
                noteId = page.noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )
        pageDao.updateTimestamp(pageId, now)
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun updateNoteTitle(
        noteId: String,
        title: String,
    ) {
        val now = System.currentTimeMillis()
        noteDao.updateTitle(noteId = noteId, title = title, updatedAt = now)
    }

    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    fun searchNotes(query: String): Flow<List<SearchResult>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return flowOf(emptyList())
        }
        return noteDao.getAllNotes().map { notes ->
            val folderNameCache = mutableMapOf<String, String?>()
            val templateNameCache = mutableMapOf<String, String?>()
            val pdfRendererCache = mutableMapOf<String, PdfiumRenderer?>()
            val ranked = mutableListOf<RankedResult>()

            try {
                notes.forEach { note ->
                    val noteTitle = note.title.ifEmpty { "Untitled Note" }
                    val folderName =
                        note.folderId?.let { folderId ->
                            folderNameCache.getOrPut(folderId) {
                                folderDao.getById(folderId)?.name
                            }
                        }

                    if (noteTitle.contains(normalizedQuery, ignoreCase = true)) {
                        ranked +=
                            buildRankedResult(
                                noteId = note.noteId,
                                noteTitle = noteTitle,
                                pageId = null,
                                sourceType = SearchSourceType.NOTE_METADATA,
                                matchedText = noteTitle,
                                contextSnippet = buildSnippet(noteTitle, normalizedQuery),
                                bounds = null,
                                pageIndex = null,
                                updatedAt = note.updatedAt,
                                query = normalizedQuery,
                            )
                    }

                    if (!folderName.isNullOrBlank() && folderName.contains(normalizedQuery, ignoreCase = true)) {
                        ranked +=
                            buildRankedResult(
                                noteId = note.noteId,
                                noteTitle = noteTitle,
                                pageId = null,
                                sourceType = SearchSourceType.NOTE_METADATA,
                                matchedText = folderName,
                                contextSnippet = "Folder: $folderName",
                                bounds = null,
                                pageIndex = null,
                                updatedAt = note.updatedAt,
                                query = normalizedQuery,
                            )
                    }

                    val pages = pageDao.getPagesForNoteSync(note.noteId)
                    pages.forEach { page ->
                        val templateName =
                            page.templateId?.let { templateId ->
                                templateNameCache.getOrPut(templateId) {
                                    pageTemplateDao.getById(templateId)?.name
                                }
                            }

                        if (
                            page.kind.contains(normalizedQuery, ignoreCase = true) ||
                            (!templateName.isNullOrBlank() && templateName.contains(normalizedQuery, ignoreCase = true))
                        ) {
                            val matchedText =
                                when {
                                    page.kind.contains(normalizedQuery, ignoreCase = true) -> page.kind
                                    else -> templateName.orEmpty()
                                }
                            val snippet =
                                if (templateName.isNullOrBlank()) {
                                    "Page type: ${page.kind}"
                                } else {
                                    "Page type: ${page.kind}, template: $templateName"
                                }
                            ranked +=
                                buildRankedResult(
                                    noteId = note.noteId,
                                    noteTitle = noteTitle,
                                    pageId = page.pageId,
                                    sourceType = SearchSourceType.PAGE_METADATA,
                                    matchedText = matchedText,
                                    contextSnippet = snippet,
                                    bounds = null,
                                    pageIndex = page.indexInNote,
                                    updatedAt = note.updatedAt,
                                    query = normalizedQuery,
                                )
                        }

                        val recognitionText = recognitionDao.getByPageId(page.pageId)?.recognizedText
                        if (
                            !recognitionText.isNullOrBlank() &&
                            recognitionText.contains(normalizedQuery, ignoreCase = true)
                        ) {
                            ranked +=
                                buildRankedResult(
                                    noteId = note.noteId,
                                    noteTitle = noteTitle,
                                    pageId = page.pageId,
                                    sourceType = SearchSourceType.INK,
                                    matchedText = extractMatchedText(recognitionText, normalizedQuery),
                                    contextSnippet = buildSnippet(recognitionText, normalizedQuery),
                                    bounds = computeInkBounds(page.pageId),
                                    pageIndex = page.indexInNote,
                                    updatedAt = note.updatedAt,
                                    query = normalizedQuery,
                                )
                        }

                        val pdfAssetId = page.pdfAssetId
                        if (pdfAssetId != null) {
                            val renderer =
                                pdfRendererCache.getOrPut(pdfAssetId) {
                                    runCatching {
                                        PdfiumRenderer(
                                            context = appContext,
                                            pdfFile = pdfAssetStorage.getFileForAsset(pdfAssetId),
                                            password = pdfPasswordStore.getPassword(pdfAssetId),
                                        )
                                    }.getOrNull()
                                }
                            if (renderer != null) {
                                val pageIndex = page.pdfPageNo?.minus(1)?.coerceAtLeast(0) ?: page.indexInNote
                                val pdfMatch =
                                    runCatching {
                                        findPdfMatch(renderer.getCharacters(pageIndex), normalizedQuery)
                                    }.getOrNull()
                                if (pdfMatch != null) {
                                    ranked +=
                                        buildRankedResult(
                                            noteId = note.noteId,
                                            noteTitle = noteTitle,
                                            pageId = page.pageId,
                                            sourceType = SearchSourceType.PDF,
                                            matchedText = pdfMatch.first,
                                            contextSnippet = pdfMatch.second,
                                            bounds = pdfMatch.third,
                                            pageIndex = page.indexInNote,
                                            updatedAt = note.updatedAt,
                                            query = normalizedQuery,
                                        )
                                }
                            }
                        }
                    }
                }
            } finally {
                pdfRendererCache.values.forEach { renderer ->
                    if (renderer != null) {
                        runCatching { renderer.close() }
                    }
                }
            }

            ranked
                .distinctBy { rankedResult ->
                    val result = rankedResult.result
                    val boundsKey =
                        result.bounds?.let { bounds ->
                            "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}"
                        } ?: "none"
                    "${result.noteId}|${result.pageId}|${result.sourceType}|${result.matchedText}|$boundsKey"
                }.sortedWith(
                    compareByDescending<RankedResult> { rankedResult -> rankedResult.score }
                        .thenByDescending { rankedResult -> rankedResult.updatedAt },
                ).take(MAX_SEARCH_RESULTS)
                .map { rankedResult -> rankedResult.result }
        }
    }

    private fun buildRankedResult(
        noteId: String,
        noteTitle: String,
        pageId: String?,
        sourceType: SearchSourceType,
        matchedText: String,
        contextSnippet: String,
        bounds: Rect?,
        pageIndex: Int?,
        updatedAt: Long,
        query: String,
    ): RankedResult {
        val isExact = matchedText.equals(query, ignoreCase = true)
        val normalizedMatched = matchedText.trim().lowercase()
        val normalizedQuery = query.trim().lowercase()
        val sourceWeight =
            when (sourceType) {
                SearchSourceType.INK -> SCORE_WEIGHT_INK
                SearchSourceType.PDF -> SCORE_WEIGHT_PDF
                SearchSourceType.PAGE_METADATA -> SCORE_WEIGHT_PAGE_METADATA
                SearchSourceType.NOTE_METADATA -> SCORE_WEIGHT_NOTE_METADATA
            }
        val metadataPenalty =
            if (sourceType == SearchSourceType.NOTE_METADATA || sourceType == SearchSourceType.PAGE_METADATA) {
                SCORE_METADATA_PENALTY
            } else {
                0.0
            }
        val exactBoost = if (isExact) SCORE_EXACT_BOOST else 0.0
        val prefixBoost = if (normalizedMatched.startsWith(normalizedQuery)) SCORE_PREFIX_BOOST else 0.0
        val score = sourceWeight + exactBoost + prefixBoost + metadataPenalty
        val boundedSnippet = contextSnippet.take(SNIPPET_LENGTH)

        return RankedResult(
            result =
                SearchResult(
                    resultId = UUID.randomUUID().toString(),
                    noteId = noteId,
                    noteTitle = noteTitle,
                    pageId = pageId,
                    sourceType = sourceType,
                    matchedText = matchedText,
                    contextSnippet = boundedSnippet,
                    bounds = bounds,
                    pageIndex = pageIndex,
                    highlightColor = highlightColorFor(sourceType),
                ),
            score = score,
            updatedAt = updatedAt,
        )
    }

    private fun highlightColorFor(sourceType: SearchSourceType): Color =
        when (sourceType) {
            SearchSourceType.INK -> INK_RESULT_COLOR
            SearchSourceType.PDF -> PDF_RESULT_COLOR
            SearchSourceType.NOTE_METADATA -> NOTE_METADATA_RESULT_COLOR
            SearchSourceType.PAGE_METADATA -> PAGE_METADATA_RESULT_COLOR
        }

    private fun buildSnippet(
        text: String,
        query: String,
    ): String {
        val index = text.indexOf(query, ignoreCase = true)
        if (index < 0) {
            return text.take(SNIPPET_LENGTH)
        }
        val start = max(0, index - CONTEXT_RADIUS)
        val end = min(text.length, index + query.length + CONTEXT_RADIUS)
        return text.substring(start, end).trim()
    }

    private fun extractMatchedText(
        text: String,
        query: String,
    ): String {
        val index = text.indexOf(query, ignoreCase = true)
        if (index < 0) {
            return query
        }
        val end = (index + query.length).coerceAtMost(text.length)
        return text.substring(index, end)
    }

    private suspend fun computeInkBounds(pageId: String): Rect? {
        val strokeBounds =
            strokeDao
                .getByPageId(pageId)
                .mapNotNull { stroke ->
                    runCatching { strokeSerializer.deserializeBounds(stroke.bounds) }.getOrNull()
                }
        if (strokeBounds.isEmpty()) {
            return null
        }
        val minX = strokeBounds.minOf { bounds -> bounds.x }
        val minY = strokeBounds.minOf { bounds -> bounds.y }
        val maxX = strokeBounds.maxOf { bounds -> bounds.x + bounds.w }
        val maxY = strokeBounds.maxOf { bounds -> bounds.y + bounds.h }
        return Rect(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY,
        )
    }

    @Suppress("ReturnCount", "ComplexCondition")
    private fun findPdfMatch(
        characters: List<PdfTextChar>,
        query: String,
    ): Triple<String, String, Rect>? {
        if (characters.isEmpty()) {
            return null
        }
        val documentText = characters.joinToString(separator = "") { character -> character.char }
        val startIndex = documentText.indexOf(query, ignoreCase = true)
        if (startIndex < 0) {
            return null
        }
        val endExclusive = (startIndex + query.length).coerceAtMost(characters.size)
        val matchedChars = characters.subList(startIndex, endExclusive)
        if (matchedChars.isEmpty()) {
            return null
        }

        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        matchedChars.forEach { matchedChar ->
            val quad = matchedChar.quad
            left = min(left, min(min(quad.p1.x, quad.p2.x), min(quad.p3.x, quad.p4.x)))
            top = min(top, min(min(quad.p1.y, quad.p2.y), min(quad.p3.y, quad.p4.y)))
            right = max(right, max(max(quad.p1.x, quad.p2.x), max(quad.p3.x, quad.p4.x)))
            bottom = max(bottom, max(max(quad.p1.y, quad.p2.y), max(quad.p3.y, quad.p4.y)))
        }

        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
            return null
        }

        return Triple(
            documentText.substring(startIndex, endExclusive),
            buildSnippet(documentText, query),
            Rect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            ),
        )
    }

    private suspend fun updatePageTimestamp(pageId: String) {
        val now = System.currentTimeMillis()
        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    private fun conversionOverlayFile(pageId: String): java.io.File =
        java.io.File(appContext.filesDir, "recognition/overlays/page_$pageId.json")

    // Thumbnail operations

    /**
     * Get the thumbnail for a note, regenerating if missing.
     *
     * @param noteId The ID of the note
     * @return The ThumbnailEntity, or null if not available
     */
    suspend fun getThumbnail(noteId: String): ThumbnailEntity? = thumbnailGenerator?.getThumbnail(noteId)

    /**
     * Generate a thumbnail for a note.
     *
     * @param noteId The ID of the note
     * @return The generated ThumbnailEntity, or null if generation failed
     */
    suspend fun generateThumbnail(noteId: String): ThumbnailEntity? = thumbnailGenerator?.generateThumbnail(noteId)

    /**
     * Regenerate a thumbnail if it's missing from storage.
     *
     * @param noteId The ID of the note
     * @return The regenerated ThumbnailEntity,
     * or null if regeneration failed
     */
    @Suppress("MaxLineLength")
    suspend fun regenerateThumbnailIfMissing(noteId: String): ThumbnailEntity? = thumbnailGenerator?.regenerateIfMissing(noteId)

    /**
     * Delete a thumbnail for a note.
     *
     * @param noteId The ID of the note
     */
    suspend fun deleteThumbnail(noteId: String) {
        thumbnailGenerator?.deleteThumbnail(noteId)
    }

    // Folder operations

    /**
     * Create a new folder with the given name.
     *
     * @param name The name of the folder
     * @return The created FolderEntity
     */
    suspend fun createFolder(name: String): FolderEntity {
        val now = System.currentTimeMillis()
        val folder =
            FolderEntity(
                folderId = UUID.randomUUID().toString(),
                name = name,
                parentId = null,
                createdAt = now,
                updatedAt = now,
            )
        folderDao.insert(folder)
        return folder
    }

    /**
     * Delete a folder. All notes in the folder are moved to root (folderId set to null).
     *
     * @param folderId The ID of the folder to delete
     */
    suspend fun deleteFolder(folderId: String) {
        val now = System.currentTimeMillis()
        // Move all notes in this folder to root
        noteDao.moveNotesToRoot(folderId, now)
        // Delete the folder
        folderDao.delete(folderId)
    }

    /**
     * Get all folders.
     *
     * @return Flow of list of all folders
     */
    fun getFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    /**
     * Move a note to a folder. Pass null for folderId to move to root.
     *
     * @param noteId The ID of the note to move
     * @param folderId The ID of the destination folder, or null for root
     */
    suspend fun moveNoteToFolder(
        noteId: String,
        folderId: String?,
    ) {
        val now = System.currentTimeMillis()
        noteDao.updateFolder(noteId, folderId, now)
    }

    /**
     * Get notes in a specific folder. Pass null for folderId to get root notes.
     *
     * @param folderId The ID of the folder, or null for root
     * @return Flow of list of notes in the folder
     */
    fun getNotesInFolder(folderId: String?): Flow<List<NoteEntity>> =
        when (folderId) {
            null -> noteDao.getRootNotes()
            else -> noteDao.getNotesByFolder(folderId)
        }

    /**
     * Get notes in a specific folder with sorting.
     *
     * @param folderId The ID of the folder, or null for root
     * @param sortOption The sort option (NAME, CREATED, MODIFIED)
     * @param sortDirection The sort direction (ASC, DESC)
     * @return Flow of list of notes in the folder, sorted
     */
    @Suppress("CyclomaticComplexMethod")
    fun getNotesInFolderSorted(
        folderId: String?,
        sortOption: SortOption,
        sortDirection: SortDirection,
    ): Flow<List<NoteEntity>> =
        when (folderId) {
            null -> {
                // Root notes
                when (sortOption) {
                    SortOption.NAME -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getRootNotesSortedByNameAsc()
                        } else {
                            noteDao.getRootNotesSortedByNameDesc()
                        }
                    }

                    SortOption.CREATED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getRootNotesSortedByCreatedAsc()
                        } else {
                            noteDao.getRootNotesSortedByCreatedDesc()
                        }
                    }

                    SortOption.MODIFIED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getRootNotesSortedByModifiedAsc()
                        } else {
                            noteDao.getRootNotesSortedByModifiedDesc()
                        }
                    }
                }
            }

            else -> {
                // Folder notes
                when (sortOption) {
                    SortOption.NAME -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getNotesByFolderSortedByNameAsc(folderId)
                        } else {
                            noteDao.getNotesByFolderSortedByNameDesc(folderId)
                        }
                    }

                    SortOption.CREATED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getNotesByFolderSortedByCreatedAsc(folderId)
                        } else {
                            noteDao.getNotesByFolderSortedByCreatedDesc(folderId)
                        }
                    }

                    SortOption.MODIFIED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getNotesByFolderSortedByModifiedAsc(folderId)
                        } else {
                            noteDao.getNotesByFolderSortedByModifiedDesc(folderId)
                        }
                    }
                }
            }
        }

    /**
     * Get notes filtered by date range.
     *
     * @param folderId The ID of the folder, or null for root
     * @param dateRange The date range filter
     * @return Flow of list of notes matching the date range
     */
    fun getNotesByDateRange(
        folderId: String?,
        dateRange: DateRange,
    ): Flow<List<NoteEntity>> {
        val (startTime, endTime) = calculateDateRangeBounds(dateRange)
        return when (folderId) {
            null -> {
                if (dateRange == DateRange.OLDER) {
                    noteDao.getRootNotesOlderThan(endTime)
                } else {
                    noteDao.getRootNotesByDateRange(startTime, endTime)
                }
            }

            else -> {
                if (dateRange == DateRange.OLDER) {
                    noteDao.getNotesByFolderOlderThan(folderId, endTime)
                } else {
                    noteDao.getNotesByFolderAndDateRange(folderId, startTime, endTime)
                }
            }
        }
    }

    /**
     * Calculate start and end time bounds for a date range.
     * Returns Pair(startTime, endTime) in milliseconds.
     */
    private fun calculateDateRangeBounds(dateRange: DateRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now

        return when (dateRange) {
            DateRange.TODAY -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }

            DateRange.THIS_WEEK -> {
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek())
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }

            DateRange.THIS_MONTH -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.MONTH, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }

            DateRange.THIS_YEAR -> {
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.YEAR, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }

            DateRange.OLDER -> {
                // For OLDER, we use endTime as the cutoff (1 year ago)
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val endTime = calendar.timeInMillis
                Pair(0L, endTime)
            }
        }
    }

    // Tag operations

    /**
     * Create a new tag with the given name and color.
     *
     * @param name The name of the tag (must be unique)
     * @param color The color of the tag as a hex string (e.g., "#FF5722")
     * @return The created TagEntity
     * @throws DuplicateTagNameException if a tag with the same name already exists
     */
    suspend fun createTag(
        name: String,
        color: String,
    ): TagEntity {
        // Check for duplicate name
        val existingTag = tagDao.getByName(name)
        if (existingTag != null) {
            throw DuplicateTagNameException(name)
        }

        val now = System.currentTimeMillis()
        val tag =
            TagEntity(
                tagId = UUID.randomUUID().toString(),
                name = name,
                color = color,
                createdAt = now,
            )
        tagDao.insert(tag)
        return tag
    }

    /**
     * Delete a tag by its ID. This also removes all note-tag associations.
     *
     * @param tagId The ID of the tag to delete
     */
    suspend fun deleteTag(tagId: String) {
        tagDao.delete(tagId)
    }

    /**
     * Get all tags.
     *
     * @return Flow of list of all tags ordered by name
     */
    fun getTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    /**
     * Add a tag to a note.
     *
     * @param noteId The ID of the note
     * @param tagId The ID of the tag
     */
    suspend fun addTagToNote(
        noteId: String,
        tagId: String,
    ) {
        tagDao.addTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tagId))
    }

    /**
     * Remove a tag from a note.
     *
     * @param noteId The ID of the note
     * @param tagId The ID of the tag
     */
    suspend fun removeTagFromNote(
        noteId: String,
        tagId: String,
    ) {
        tagDao.removeTagFromNote(noteId = noteId, tagId = tagId)
    }

    /**
     * Get all tags assigned to a note.
     *
     * @param noteId The ID of the note
     * @return Flow of list of tags assigned to the note
     */
    fun getTagsForNote(noteId: String): Flow<List<TagEntity>> = tagDao.getTagsForNote(noteId)

    /**
     * Get all notes that have a specific tag.
     *
     * @param tagId The ID of the tag
     * @return Flow of list of notes with the tag
     */
    fun getNotesByTag(tagId: String): Flow<List<NoteEntity>> = tagDao.getNotesWithTag(tagId)

    // Batch operations for multi-select

    /**
     * Delete multiple notes by their IDs.
     *
     * @param noteIds The set of note IDs to delete
     */
    suspend fun deleteNotes(noteIds: Set<String>) {
        val now = System.currentTimeMillis()
        noteIds.forEach { noteId ->
            noteDao.softDelete(noteId, now)
        }
    }

    /**
     * Move multiple notes to a folder. Pass null for folderId to move to root.
     *
     * @param noteIds The set of note IDs to move
     * @param folderId The ID of the destination folder, or null for root
     */
    suspend fun moveNotesToFolder(
        noteIds: Set<String>,
        folderId: String?,
    ) {
        val now = System.currentTimeMillis()
        noteIds.forEach { noteId ->
            noteDao.updateFolder(noteId, folderId, now)
        }
    }

    /**
     * Add a tag to multiple notes.
     *
     * @param noteIds The set of note IDs to add the tag to
     * @param tagId The ID of the tag to add
     */
    suspend fun addTagToNotes(
        noteIds: Set<String>,
        tagId: String,
    ) {
        noteIds.forEach { noteId ->
            tagDao.addTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tagId))
        }
    }
}
