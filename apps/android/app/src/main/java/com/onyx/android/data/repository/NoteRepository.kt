package com.onyx.android.data.repository

import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.RecognitionIndexEntity
import com.onyx.android.data.entity.StrokeEntity
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.ink.model.Stroke
import java.util.UUID

@Suppress("TooManyFunctions")
class NoteRepository(
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val strokeDao: StrokeDao,
    private val recognitionDao: RecognitionDao,
    private val deviceIdentity: DeviceIdentity,
    private val strokeSerializer: StrokeSerializer,
) {
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

    suspend fun createPageForNote(
        noteId: String,
        indexInNote: Int,
    ): PageEntity {
        val now = System.currentTimeMillis()
        val page =
            PageEntity(
                pageId = UUID.randomUUID().toString(),
                noteId = noteId,
                kind = "ink",
                geometryKind = "fixed",
                indexInNote = indexInNote,
                width = 612f,
                height = 792f,
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
        noteDao.updateTimestamp(noteId, now)
        return page
    }

    suspend fun saveStroke(
        pageId: String,
        stroke: Stroke,
    ) {
        val now = System.currentTimeMillis()
        val entity =
            StrokeEntity(
                strokeId = stroke.id,
                pageId = pageId,
                strokeData = strokeSerializer.serializePoints(stroke.points),
                style = strokeSerializer.serializeStyle(stroke.style),
                bounds = strokeSerializer.serializeBounds(stroke.bounds),
                createdAt = stroke.createdAt,
                createdLamport = stroke.createdLamport,
            )
        strokeDao.insert(entity)

        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun deleteStroke(strokeId: String) {
        val stroke = requireNotNull(strokeDao.getById(strokeId)) { "Stroke not found: $strokeId" }
        strokeDao.delete(strokeId)

        val now = System.currentTimeMillis()
        pageDao.updateTimestamp(stroke.pageId, now)
        val page = requireNotNull(pageDao.getById(stroke.pageId)) { "Page not found: ${stroke.pageId}" }
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

    suspend fun upgradePageToMixed(pageId: String) {
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        if (page.kind == "pdf") {
            pageDao.updateKind(pageId, "mixed")
            updatePageTimestamp(pageId)
        }
    }

    suspend fun deletePage(pageId: String) {
        strokeDao.deleteAllForPage(pageId)
        recognitionDao.deleteByPageId(pageId)
        pageDao.delete(pageId)
    }

    suspend fun deleteNote(noteId: String) {
        val now = System.currentTimeMillis()
        noteDao.softDelete(noteId, now)
    }

    private suspend fun updatePageTimestamp(pageId: String) {
        val now = System.currentTimeMillis()
        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }
}
