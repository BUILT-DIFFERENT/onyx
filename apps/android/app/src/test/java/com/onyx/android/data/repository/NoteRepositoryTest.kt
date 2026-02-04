package com.onyx.android.data.repository

import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.StrokeEntity
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NoteRepositoryTest {
    private lateinit var noteDao: NoteDao
    private lateinit var pageDao: PageDao
    private lateinit var strokeDao: StrokeDao
    private lateinit var recognitionDao: RecognitionDao
    private lateinit var deviceIdentity: DeviceIdentity
    private lateinit var repository: NoteRepository

    @BeforeEach
    fun setup() {
        noteDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        strokeDao = mockk(relaxed = true)
        recognitionDao = mockk(relaxed = true)
        deviceIdentity = mockk()

        every { deviceIdentity.getDeviceId() } returns "test-device-uuid"
        coEvery { pageDao.getById(any()) } answers {
            val pageId = firstArg<String>()
            PageEntity(
                pageId = pageId,
                noteId = "note-id",
                kind = "ink",
                geometryKind = "fixed",
                indexInNote = 0,
                width = 612f,
                height = 792f,
                unit = "pt",
                pdfAssetId = null,
                pdfPageNo = null,
                updatedAt = 0L,
                contentLamportMax = 0,
            )
        }

        repository =
            NoteRepository(
                noteDao = noteDao,
                pageDao = pageDao,
                strokeDao = strokeDao,
                recognitionDao = recognitionDao,
                deviceIdentity = deviceIdentity,
                strokeSerializer = StrokeSerializer,
            )
    }

    @Test
    fun `createNote returns valid NoteEntity with UUID`() =
        runTest {
            val result = repository.createNote()
            val note = result.note

            assertTrue(UUID_REGEX.matches(note.noteId))
            assertEquals("test-device-uuid", note.ownerUserId)
            assertEquals("", note.title)
            assertTrue(note.createdAt > 0)
            assertEquals(note.createdAt, note.updatedAt)
        }

    @Test
    fun `createNote also creates first page with indexInNote 0`() =
        runTest {
            val pageSlot = slot<PageEntity>()

            val result = repository.createNote()

            coVerify(exactly = 1) { pageDao.insert(capture(pageSlot)) }
            assertEquals(0, pageSlot.captured.indexInNote)
            assertEquals(pageSlot.captured.pageId, result.firstPageId)
        }

    @Test
    fun `saveStroke serializes and persists correctly`() =
        runTest {
            val pageId = "page-id"
            val stroke = createStroke("stroke-id", createdAt = 1234L)
            val strokeSlot = slot<StrokeEntity>()

            repository.saveStroke(pageId, stroke)

            coVerify(exactly = 1) { strokeDao.insert(capture(strokeSlot)) }
            val entity = strokeSlot.captured
            assertEquals(stroke.id, entity.strokeId)
            assertEquals(pageId, entity.pageId)
            assertEquals(stroke.createdAt, entity.createdAt)
            assertEquals(stroke.createdLamport, entity.createdLamport)
            assertEquals(StrokeSerializer.serializeStyle(stroke.style), entity.style)
            assertEquals(StrokeSerializer.serializeBounds(stroke.bounds), entity.bounds)
            assertTrue(StrokeSerializer.serializePoints(stroke.points).contentEquals(entity.strokeData))
        }

    @Test
    fun `saveStroke updates page and note updatedAt`() =
        runTest {
            val pageId = "page-id"
            val stroke = createStroke("stroke-id", createdAt = 1234L)

            repository.saveStroke(pageId, stroke)

            coVerify(exactly = 1) { pageDao.updateTimestamp(pageId, any()) }
            coVerify(exactly = 1) { noteDao.updateTimestamp("note-id", any()) }
        }

    @Test
    fun `getStrokesForPage deserializes all strokes`() =
        runTest {
            val pageId = "page-id"
            val stroke1 = createStroke("stroke-1", createdAt = 1000L)
            val stroke2 = createStroke("stroke-2", createdAt = 2000L)
            val entity1 =
                StrokeEntity(
                    strokeId = stroke1.id,
                    pageId = pageId,
                    strokeData = StrokeSerializer.serializePoints(stroke1.points),
                    style = StrokeSerializer.serializeStyle(stroke1.style),
                    bounds = StrokeSerializer.serializeBounds(stroke1.bounds),
                    createdAt = stroke1.createdAt,
                    createdLamport = stroke1.createdLamport,
                )
            val entity2 =
                StrokeEntity(
                    strokeId = stroke2.id,
                    pageId = pageId,
                    strokeData = StrokeSerializer.serializePoints(stroke2.points),
                    style = StrokeSerializer.serializeStyle(stroke2.style),
                    bounds = StrokeSerializer.serializeBounds(stroke2.bounds),
                    createdAt = stroke2.createdAt,
                    createdLamport = stroke2.createdLamport,
                )

            coEvery { strokeDao.getByPageId(pageId) } returns listOf(entity1, entity2)

            val result = repository.getStrokesForPage(pageId)

            assertEquals(listOf(stroke1, stroke2), result)
        }

    @Test
    fun `deleteNote sets deletedAt timestamp`() =
        runTest {
            repository.deleteNote("note-id")

            coVerify(exactly = 1) { noteDao.softDelete("note-id", any()) }
        }

    @Test
    fun `createPageFromPdf sets kind pdf and correct dimensions`() =
        runTest {
            val pageSlot = slot<PageEntity>()

            repository.createPageFromPdf(
                noteId = "note-id",
                indexInNote = 2,
                pdfAssetId = "asset-id",
                pdfPageNo = 4,
                pdfWidth = 500f,
                pdfHeight = 700f,
            )

            coVerify(exactly = 1) { pageDao.insert(capture(pageSlot)) }
            val page = pageSlot.captured
            assertEquals("pdf", page.kind)
            assertEquals("fixed", page.geometryKind)
            assertEquals(500f, page.width)
            assertEquals(700f, page.height)
            assertEquals("asset-id", page.pdfAssetId)
            assertEquals(4, page.pdfPageNo)
            assertEquals(2, page.indexInNote)
        }

    @Test
    fun `upgradePageToMixed changes kind from pdf to mixed`() =
        runTest {
            val pageId = "page-id"
            val page =
                PageEntity(
                    pageId = pageId,
                    noteId = "note-id",
                    kind = "pdf",
                    geometryKind = "fixed",
                    indexInNote = 0,
                    width = 612f,
                    height = 792f,
                    unit = "pt",
                    pdfAssetId = "asset-id",
                    pdfPageNo = 1,
                    updatedAt = 0L,
                    contentLamportMax = 0,
                )
            coEvery { pageDao.getById(pageId) } returns page

            repository.upgradePageToMixed(pageId)

            coVerify(exactly = 1) { pageDao.updateKind(pageId, "mixed", any()) }
            coVerify(exactly = 1) { pageDao.updateTimestamp(pageId, any()) }
            coVerify(exactly = 1) { noteDao.updateTimestamp("note-id", any()) }
        }

    private fun createStroke(
        id: String,
        createdAt: Long,
    ): Stroke =
        Stroke(
            id = id,
            points = listOf(StrokePoint(x = 1f, y = 2f, t = createdAt)),
            style =
                StrokeStyle(
                    tool = Tool.PEN,
                    color = "#000000",
                    baseWidth = 2f,
                    minWidthFactor = 0.5f,
                    maxWidthFactor = 1.5f,
                    nibRotation = false,
                ),
            bounds = StrokeBounds(x = 0f, y = 0f, w = 10f, h = 10f),
            createdAt = createdAt,
            createdLamport = 0,
        )

    companion object {
        private val UUID_REGEX =
            Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            )
    }
}
