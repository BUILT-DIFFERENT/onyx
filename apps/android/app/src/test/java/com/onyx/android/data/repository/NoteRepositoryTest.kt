package com.onyx.android.data.repository

import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.RecognitionIndexEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun `createPage inserts recognition index and updates note timestamp`() =
        runTest {
            val page =
                PageEntity(
                    pageId = "new-page",
                    noteId = "note-id",
                    kind = "ink",
                    geometryKind = "fixed",
                    indexInNote = 1,
                    width = 612f,
                    height = 792f,
                    unit = "pt",
                    pdfAssetId = null,
                    pdfPageNo = null,
                    updatedAt = 0L,
                    contentLamportMax = 0,
                )
            val pageSlot = slot<PageEntity>()
            val recognitionSlot = slot<RecognitionIndexEntity>()

            val storedPage = repository.createPage(page)

            coVerify(exactly = 1) { pageDao.insert(capture(pageSlot)) }
            coVerify(exactly = 1) { recognitionDao.insert(capture(recognitionSlot)) }
            coVerify(exactly = 1) { noteDao.updateTimestamp("note-id", any()) }
            assertEquals("new-page", storedPage.pageId)
            assertEquals("new-page", recognitionSlot.captured.pageId)
            assertEquals("note-id", recognitionSlot.captured.noteId)
        }

    @Test
    fun `deleteStroke removes stroke and updates parent timestamps`() =
        runTest {
            val strokeId = "stroke-id"
            val pageId = "page-id"
            val strokeEntity =
                StrokeEntity(
                    strokeId = strokeId,
                    pageId = pageId,
                    strokeData = byteArrayOf(1, 2, 3),
                    style = "{}",
                    bounds = "{}",
                    createdAt = 1234L,
                    createdLamport = 0L,
                )

            coEvery { strokeDao.getById(strokeId) } returns strokeEntity
            coEvery {
                pageDao.getById(pageId)
            } returns
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

            repository.deleteStroke(strokeId)

            coVerify(exactly = 1) { strokeDao.delete(strokeId) }
            coVerify(exactly = 1) { pageDao.updateTimestamp(pageId, any()) }
            coVerify(exactly = 1) { noteDao.updateTimestamp("note-id", any()) }
        }

    @Test
    fun `deleteStroke returns early when stroke does not exist`() =
        runTest {
            coEvery { strokeDao.getById("missing") } returns null

            repository.deleteStroke("missing")

            coVerify(exactly = 0) { strokeDao.delete(any()) }
            coVerify(exactly = 0) { pageDao.updateTimestamp(any(), any()) }
            coVerify(exactly = 0) { noteDao.updateTimestamp(any(), any()) }
        }

    @Test
    fun `updateRecognition stores text and updates page and note timestamps`() =
        runTest {
            val pageId = "page-id"
            coEvery {
                pageDao.getById(pageId)
            } returns
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

            repository.updateRecognition(pageId, "hello", "myscript-4.3")

            coVerify(exactly = 1) {
                recognitionDao.updateRecognition(
                    pageId = pageId,
                    text = "hello",
                    version = "myscript-4.3",
                    updatedAt = any(),
                )
            }
            coVerify(exactly = 1) { pageDao.updateTimestamp(pageId, any()) }
            coVerify(exactly = 1) { noteDao.updateTimestamp("note-id", any()) }
        }

    @Test
    fun `upgradePageToMixed does nothing when page is not pdf`() =
        runTest {
            val pageId = "ink-page-id"
            coEvery {
                pageDao.getById(pageId)
            } returns
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

            repository.upgradePageToMixed(pageId)

            coVerify(exactly = 0) { pageDao.updateKind(any(), any(), any()) }
            coVerify(exactly = 0) { recognitionDao.insert(any()) }
            coVerify(exactly = 0) { noteDao.updateTimestamp(any(), any()) }
        }

    @Suppress("LongMethod")
    @Test
    fun `searchNotes deduplicates by note and truncates snippets`() =
        runTest {
            val firstText = "a".repeat(130)
            val recognitions =
                listOf(
                    RecognitionIndexEntity(
                        pageId = "page-1",
                        noteId = "note-1",
                        recognizedText = firstText,
                        recognizedAtLamport = null,
                        recognizerVersion = null,
                        updatedAt = 1L,
                    ),
                    RecognitionIndexEntity(
                        pageId = "page-2",
                        noteId = "note-1",
                        recognizedText = "duplicate note hit",
                        recognizedAtLamport = null,
                        recognizerVersion = null,
                        updatedAt = 2L,
                    ),
                    RecognitionIndexEntity(
                        pageId = "page-3",
                        noteId = "note-2",
                        recognizedText = "hello world",
                        recognizedAtLamport = null,
                        recognizerVersion = null,
                        updatedAt = 3L,
                    ),
                )
            every { recognitionDao.search("hello") } returns flowOf(recognitions)
            coEvery {
                pageDao.getById("page-1")
            } returns
                PageEntity(
                    pageId = "page-1",
                    noteId = "note-1",
                    kind = "ink",
                    geometryKind = "fixed",
                    indexInNote = 0,
                    width = 612f,
                    height = 792f,
                    unit = "pt",
                    pdfAssetId = null,
                    pdfPageNo = null,
                    updatedAt = 1L,
                    contentLamportMax = 0,
                )
            coEvery {
                pageDao.getById("page-2")
            } returns
                PageEntity(
                    pageId = "page-2",
                    noteId = "note-1",
                    kind = "ink",
                    geometryKind = "fixed",
                    indexInNote = 1,
                    width = 612f,
                    height = 792f,
                    unit = "pt",
                    pdfAssetId = null,
                    pdfPageNo = null,
                    updatedAt = 1L,
                    contentLamportMax = 0,
                )
            coEvery {
                pageDao.getById("page-3")
            } returns
                PageEntity(
                    pageId = "page-3",
                    noteId = "note-2",
                    kind = "ink",
                    geometryKind = "fixed",
                    indexInNote = 2,
                    width = 612f,
                    height = 792f,
                    unit = "pt",
                    pdfAssetId = null,
                    pdfPageNo = null,
                    updatedAt = 1L,
                    contentLamportMax = 0,
                )
            coEvery { noteDao.getById("note-1") } returns
                com.onyx.android.data.entity.NoteEntity(
                    noteId = "note-1",
                    ownerUserId = "owner",
                    title = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null,
                )
            coEvery { noteDao.getById("note-2") } returns
                com.onyx.android.data.entity.NoteEntity(
                    noteId = "note-2",
                    ownerUserId = "owner",
                    title = "Project",
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null,
                )

            val results = repository.searchNotes("hello").first()

            assertEquals(2, results.size)
            val first = results.first { it.noteId == "note-1" }
            val second = results.first { it.noteId == "note-2" }
            assertEquals("Untitled Note", first.noteTitle)
            assertEquals(1, first.pageNumber)
            assertEquals(100, first.snippetText.length)
            assertEquals("Project", second.noteTitle)
            assertEquals(3, second.pageNumber)
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
