@file:Suppress("TooManyFunctions")

package com.onyx.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.ink.model.Stroke
import com.onyx.android.recognition.MyScriptPageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class NoteEditorViewModel(
    private val noteId: String,
    private val repository: NoteRepository,
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val myScriptPageManager: MyScriptPageManager?,
    private val initialPageId: String?,
) : ViewModel() {
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()
    private val _pages = MutableStateFlow<List<PageEntity>>(emptyList())
    val pages: StateFlow<List<PageEntity>> = _pages.asStateFlow()
    private val _noteTitle = MutableStateFlow("")
    val noteTitle: StateFlow<String> = _noteTitle.asStateFlow()
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()
    private val _currentPage = MutableStateFlow<PageEntity?>(null)
    val currentPage: StateFlow<PageEntity?> = _currentPage.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private var currentPageId: String? = null
    private var pendingInitialPageId: String? = initialPageId
    private val strokeWriteQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        myScriptPageManager?.onRecognitionUpdated = { pageId, text ->
            viewModelScope.launch {
                runCatching {
                    repository.updateRecognition(pageId, text, "myscript-4.3")
                }.onFailure { throwable ->
                    reportError("Failed to update recognition state.", throwable)
                }
            }
        }
        viewModelScope.launch {
            for (task in strokeWriteQueue) {
                val firstAttempt =
                    runCatching {
                        task()
                    }
                if (firstAttempt.isFailure) {
                    val retryAttempt =
                        runCatching {
                            task()
                        }
                    if (retryAttempt.isFailure) {
                        reportError(
                            "Failed to persist stroke changes.",
                            retryAttempt.exceptionOrNull() ?: firstAttempt.exceptionOrNull(),
                        )
                    }
                }
            }
        }
        loadNote()
    }

    fun loadNote() {
        viewModelScope.launch {
            runCatching {
                val note = noteDao.getById(noteId)
                if (note == null) {
                    _noteTitle.value = ""
                    reportError("Note not found while loading editor state.")
                } else {
                    _noteTitle.value = note.title
                }
                refreshPages(selectPageId = pendingInitialPageId)
                pendingInitialPageId = null
            }.onFailure { throwable ->
                reportError("Failed to load note.", throwable)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun navigateBy(offset: Int) {
        if (_pages.value.isEmpty()) {
            return
        }
        val targetIndex = (_currentPageIndex.value + offset).coerceIn(0, _pages.value.lastIndex)
        if (targetIndex == _currentPageIndex.value) {
            return
        }
        _currentPageIndex.value = targetIndex
        viewModelScope.launch {
            runCatching {
                setCurrentPage(_pages.value.getOrNull(targetIndex))
            }.onFailure { throwable ->
                reportError("Failed to navigate pages.", throwable)
            }
        }
    }

    fun createNewPage() {
        viewModelScope.launch {
            runCatching {
                val maxIndex = pageDao.getMaxIndexForNote(noteId) ?: -1
                val newIndex = maxIndex + 1
                repository.createPageForNote(noteId, indexInNote = newIndex)
                refreshPages(selectIndex = newIndex)
            }.onFailure { throwable ->
                reportError("Failed to create a new page.", throwable)
            }
        }
    }

    fun addStroke(
        stroke: Stroke,
        persist: Boolean,
        updateRecognition: Boolean = true,
    ) {
        _strokes.value = _strokes.value + stroke
        if (persist) {
            val pageId = currentPageId ?: return
            enqueueStrokeWrite {
                repository.saveStroke(pageId, stroke)
            }
        }
        if (updateRecognition) {
            myScriptPageManager?.addStroke(stroke)
        }
    }

    fun removeStroke(
        stroke: Stroke,
        persist: Boolean,
        updateRecognition: Boolean = true,
    ) {
        _strokes.value = _strokes.value - stroke
        if (persist) {
            enqueueStrokeWrite {
                repository.deleteStroke(stroke.id)
            }
        }
        if (updateRecognition) {
            myScriptPageManager?.onStrokeErased(stroke.id, _strokes.value)
        }
    }

    fun upgradePageToMixed(pageId: String) {
        viewModelScope.launch {
            runCatching {
                val existingPage = _pages.value.firstOrNull { it.pageId == pageId } ?: return@runCatching
                if (existingPage.kind != "pdf") {
                    return@runCatching
                }
                val locallyMixedPage =
                    existingPage.copy(
                        kind = "mixed",
                        updatedAt = System.currentTimeMillis(),
                    )
                replacePageInState(locallyMixedPage)
                if (currentPageId == pageId) {
                    _currentPage.value = locallyMixedPage
                    myScriptPageManager?.onPageEnter(pageId)
                }
                repository.upgradePageToMixed(pageId)
                val updatedPage = pageDao.getById(pageId)
                if (updatedPage != null) {
                    replacePageInState(updatedPage)
                    if (currentPageId == pageId) {
                        _currentPage.value = updatedPage
                    }
                }
                Log.d("PageKind", "Updated page kind to mixed for pageId=$pageId")
            }.onFailure { throwable ->
                reportError("Failed to upgrade page to mixed mode.", throwable)
            }
        }
    }

    private fun replacePageInState(updatedPage: PageEntity) {
        val currentPages = _pages.value.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.pageId == updatedPage.pageId }
        if (pageIndex >= 0) {
            currentPages[pageIndex] = updatedPage
            _pages.value = currentPages
        }
    }

    private suspend fun refreshPages(
        selectIndex: Int? = null,
        selectPageId: String? = null,
    ) {
        val pages = pageDao.getPagesForNote(noteId).first()
        _pages.value = pages
        if (pages.isEmpty()) {
            _currentPageIndex.value = 0
            setCurrentPage(null)
            return
        }
        val pageIdIndex =
            selectPageId?.let { pageId ->
                pages.indexOfFirst { it.pageId == pageId }.takeIf { it >= 0 }
            }
        val resolvedIndex =
            pageIdIndex
                ?: selectIndex?.coerceIn(0, pages.lastIndex)
                ?: _currentPageIndex.value.coerceIn(0, pages.lastIndex)
        _currentPageIndex.value = resolvedIndex
        setCurrentPage(pages.getOrNull(resolvedIndex))
    }

    private suspend fun setCurrentPage(page: PageEntity?) {
        if (currentPageId != page?.pageId && currentPageId != null) {
            myScriptPageManager?.closeCurrentPage()
        }
        currentPageId = page?.pageId
        _currentPage.value = page
        if (page?.kind == "pdf") {
            _strokes.value = emptyList()
            return
        }
        currentPageId?.let { pageId ->
            myScriptPageManager?.onPageEnter(pageId)
            val loadedStrokes = repository.getStrokesForPage(pageId)
            _strokes.value = loadedStrokes
            loadedStrokes.forEach { stroke ->
                myScriptPageManager?.addStroke(stroke)
            }
        } ?: run {
            _strokes.value = emptyList()
        }
    }

    override fun onCleared() {
        myScriptPageManager?.closeCurrentPage()
        strokeWriteQueue.close()
        super.onCleared()
    }

    private fun enqueueStrokeWrite(task: suspend () -> Unit) {
        val result =
            strokeWriteQueue.trySend {
                withContext(Dispatchers.IO) {
                    task()
                }
            }
        if (result.isFailure) {
            reportError("Failed to queue stroke save operation.", result.exceptionOrNull())
        }
    }

    private fun reportError(
        message: String,
        throwable: Throwable? = null,
    ) {
        Log.e("NoteEditorViewModel", message, throwable)
        _errorMessage.value = message
    }
}

internal class NoteEditorViewModelFactory(
    private val noteId: String,
    private val repository: NoteRepository,
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val myScriptPageManager: MyScriptPageManager?,
    private val initialPageId: String?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NoteEditorViewModel::class.java))
        return NoteEditorViewModel(
            noteId,
            repository,
            noteDao,
            pageDao,
            myScriptPageManager,
            initialPageId,
        ) as T
    }
}
