package com.onyx.android.ui

import androidx.lifecycle.ViewModelStore
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {
    private lateinit var repository: NoteRepository
    private lateinit var noteDao: NoteDao
    private lateinit var pageDao: PageDao
    private val stores = mutableListOf<ViewModelStore>()

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)

        coEvery { noteDao.getById(NOTE_ID) } returns sampleNote()
        coEvery { pageDao.getPagesForNote(NOTE_ID) } returns flowOf(listOf(samplePage()))
        coEvery { repository.getStrokesForPage(PAGE_ID) } returns emptyList()
        coEvery { pageDao.getMaxIndexForNote(NOTE_ID) } returns 0
        coEvery { repository.createPageForNote(NOTE_ID, any()) } returns samplePage(index = 1)
    }

    @Test
    fun `loadNote emits error state when note query fails`() =
        runViewModelTest {
            coEvery { noteDao.getById(NOTE_ID) } throws IllegalStateException("db unavailable")

            val viewModel = createViewModel()
            drainMainQueue()

            assertEquals("Failed to load note.", viewModel.errorMessage.value)
        }

    @Test
    fun `createNewPage emits error state when page indexing fails`() =
        runViewModelTest {
            val viewModel = createViewModel()
            drainMainQueue()
            coEvery { pageDao.getMaxIndexForNote(NOTE_ID) } throws IllegalStateException("write failed")

            viewModel.createNewPage()
            drainMainQueue()

            assertEquals("Failed to create a new page.", viewModel.errorMessage.value)
        }

    @Test
    fun `addStroke persist failure reports queue error after retry`() =
        runViewModelTest {
            coEvery { repository.saveStroke(PAGE_ID, any()) } throws IllegalStateException("disk full")
            val viewModel = createViewModel()
            drainMainQueue()

            viewModel.addStroke(sampleStroke(), persist = true, updateRecognition = false)
            drainMainQueue()

            coVerify(timeout = 1_000, exactly = 2) { repository.saveStroke(PAGE_ID, any()) }
            assertEquals("Failed to persist stroke changes.", awaitErrorMessage(viewModel))
        }

    @Test
    fun `addStroke succeeds after retry without surfacing queue error`() =
        runViewModelTest {
            coEvery {
                repository.saveStroke(PAGE_ID, any())
            } throws IllegalStateException("transient io") andThen Unit
            val viewModel = createViewModel()
            drainMainQueue()

            viewModel.addStroke(sampleStroke(), persist = true, updateRecognition = false)
            drainMainQueue()

            coVerify(timeout = 1_000, exactly = 2) { repository.saveStroke(PAGE_ID, any()) }
            assertNull(viewModel.errorMessage.value)
        }

    private fun runViewModelTest(block: suspend () -> Unit) =
        runBlocking {
            Dispatchers.setMain(Dispatchers.Unconfined)
            try {
                block()
            } finally {
                stores.forEach { store ->
                    store.clear()
                }
                stores.clear()
                Dispatchers.resetMain()
            }
        }

    private suspend fun drainMainQueue() {
        repeat(3) {
            yield()
        }
    }

    private suspend fun awaitErrorMessage(viewModel: NoteEditorViewModel): String? {
        repeat(40) {
            viewModel.errorMessage.value?.let { message ->
                return message
            }
            delay(25)
            yield()
        }
        return viewModel.errorMessage.value
    }

    private fun createViewModel(): NoteEditorViewModel {
        val viewModel =
            NoteEditorViewModel(
                noteId = NOTE_ID,
                repository = repository,
                noteDao = noteDao,
                pageDao = pageDao,
                myScriptPageManager = null,
                initialPageId = null,
            )
        val store = ViewModelStore()
        store.put("noteEditor", viewModel)
        stores += store
        return viewModel
    }

    private fun sampleNote(): NoteEntity =
        NoteEntity(
            noteId = NOTE_ID,
            ownerUserId = "owner",
            title = "Weekly Plan",
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
        )

    private fun samplePage(index: Int = 0): PageEntity =
        PageEntity(
            pageId = PAGE_ID,
            noteId = NOTE_ID,
            kind = "ink",
            geometryKind = "fixed",
            indexInNote = index,
            width = 612f,
            height = 792f,
            unit = "pt",
            pdfAssetId = null,
            pdfPageNo = null,
            updatedAt = 1L,
            contentLamportMax = 0,
        )

    private fun sampleStroke(): Stroke =
        Stroke(
            id = "stroke-1",
            points =
                listOf(
                    StrokePoint(x = 1f, y = 2f, t = 100L),
                    StrokePoint(x = 4f, y = 8f, t = 120L),
                ),
            style = StrokeStyle(tool = Tool.PEN, color = "#000000", baseWidth = 2f),
            bounds = StrokeBounds(x = 1f, y = 2f, w = 3f, h = 6f),
            createdAt = 100L,
            createdLamport = 0L,
        )

    private companion object {
        private const val NOTE_ID = "note-1"
        private const val PAGE_ID = "page-1"
    }
}
