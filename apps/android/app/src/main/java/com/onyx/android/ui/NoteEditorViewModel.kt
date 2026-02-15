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
    companion object {
        private const val LOG_TAG = "NoteEditorViewModel"
        private const val PAGE_BUFFER_SIZE = 1
        private const val PAGE_LOG_PREVIEW_COUNT = 5
    }

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

    // Active recognition page - updated ONLY on stylus interaction, NOT on scroll
    // This ensures recognition stays on the correct page during fast scrolling
    private val _activeRecognitionPageId = MutableStateFlow<String?>(null)
    val activeRecognitionPageId: StateFlow<String?> = _activeRecognitionPageId.asStateFlow()

    // Multi-page strokes cache: pageId -> strokes
    private val _pageStrokesCache = MutableStateFlow<Map<String, List<Stroke>>>(emptyMap())
    val pageStrokesCache: StateFlow<Map<String, List<Stroke>>> = _pageStrokesCache.asStateFlow()

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
                    runCatching {
                        Log.w(
                            LOG_TAG,
                            "Stroke persistence failed on first attempt. Retrying once.",
                            firstAttempt.exceptionOrNull(),
                        )
                    }
                    val retryAttempt =
                        runCatching {
                            task()
                        }
                    if (retryAttempt.isFailure) {
                        reportError(
                            "Failed to persist stroke changes.",
                            retryAttempt.exceptionOrNull() ?: firstAttempt.exceptionOrNull(),
                        )
                    } else {
                        runCatching {
                            Log.i(LOG_TAG, "Stroke persistence retry succeeded.")
                        }
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

    fun updateNoteTitle(title: String) {
        val normalizedTitle = title.trim()
        if (_noteTitle.value == normalizedTitle) {
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.updateNoteTitle(noteId, normalizedTitle)
                _noteTitle.value = normalizedTitle
            }.onFailure { throwable ->
                reportError("Failed to update note title.", throwable)
            }
        }
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
        // Only update recognition if this is the active recognition page
        if (updateRecognition && currentPageId == _activeRecognitionPageId.value) {
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
        // Only update recognition if this is the active recognition page
        if (updateRecognition && currentPageId == _activeRecognitionPageId.value) {
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

    /**
     * Add stroke for a specific page (multi-page mode).
     */
    fun addStrokeForPage(
        stroke: Stroke,
        pageId: String,
        persist: Boolean,
        updateRecognition: Boolean = true,
    ) {
        val currentCache = _pageStrokesCache.value.toMutableMap()
        val pageStrokes = currentCache[pageId].orEmpty() + stroke
        currentCache[pageId] = pageStrokes
        _pageStrokesCache.value = currentCache

        if (persist) {
            enqueueStrokeWrite {
                repository.saveStroke(pageId, stroke)
            }
        }
        if (updateRecognition && pageId == currentPageId) {
            myScriptPageManager?.addStroke(stroke)
        }
    }

    /**
     * Remove stroke for a specific page (multi-page mode).
     */
    fun removeStrokeForPage(
        stroke: Stroke,
        pageId: String,
        persist: Boolean,
        updateRecognition: Boolean = true,
    ) {
        val currentCache = _pageStrokesCache.value.toMutableMap()
        val pageStrokes = currentCache[pageId].orEmpty() - stroke
        currentCache[pageId] = pageStrokes
        _pageStrokesCache.value = currentCache

        if (persist) {
            enqueueStrokeWrite {
                repository.deleteStroke(stroke.id)
            }
        }
        if (updateRecognition && pageId == currentPageId) {
            myScriptPageManager?.onStrokeErased(stroke.id, pageStrokes)
        }
    }

    /**
     * Load strokes for a range of pages (for pre-loading in multi-page mode).
     */
    fun loadStrokesForPages(pageIds: List<String>) {
        viewModelScope.launch {
            runCatching {
                val currentCache = _pageStrokesCache.value.toMutableMap()
                for (pageId in pageIds) {
                    if (!currentCache.containsKey(pageId)) {
                        val strokes = repository.getStrokesForPage(pageId)
                        currentCache[pageId] = strokes
                    }
                }
                _pageStrokesCache.value = currentCache
            }.onFailure { throwable ->
                reportError("Failed to load strokes for pages.", throwable)
            }
        }
    }

    /**
     * Update the current visible page index (for tracking in multi-page mode).
     */
    fun setVisiblePageIndex(index: Int) {
        if (index != _currentPageIndex.value && index in 0..(_pages.value.lastIndex)) {
            _currentPageIndex.value = index
            val page = _pages.value.getOrNull(index)
            if (page != null && page.pageId != currentPageId) {
                currentPageId = page.pageId
                _currentPage.value = page
            }
        }
    }

    /**
     * Get strokes for a specific page from cache or load if needed.
     */
    fun getStrokesForPage(pageId: String): List<Stroke> {
        return _pageStrokesCache.value[pageId].orEmpty()
    }

    /**
     * Set the active recognition page based on stylus interaction.
     * This should be called when stylus input is detected on a page.
     * This is intentionally decoupled from scroll-driven currentPage changes.
     *
     * @param pageId The page ID that received stylus input
     */
    fun setActiveRecognitionPage(pageId: String) {
        if (_activeRecognitionPageId.value != pageId) {
            // Close the previous recognition page if different
            if (_activeRecognitionPageId.value != null && _activeRecognitionPageId.value != pageId) {
                myScriptPageManager?.closeCurrentPage()
            }
            _activeRecognitionPageId.value = pageId
            // Initialize recognition for the new active page
            myScriptPageManager?.onPageEnter(pageId)
        }
    }

    /**
     * Get the active recognition page ID.
     * Returns the current page ID if no active recognition page is set.
     */
    fun getActiveRecognitionPageId(): String? {
        return _activeRecognitionPageId.value ?: currentPageId
    }

    /**
     * Handle visible pages changed event for virtualized stroke loading.
     * Loads strokes for visible pages ± buffer, unloads strokes for pages outside range.
     * Commits any in-progress strokes before unloading a page.
     *
     * @param visibleRange The range of currently visible page indices (0-based, inclusive)
     */
    fun onVisiblePagesChanged(visibleRange: IntRange) {
        val pages = _pages.value
        if (pages.isEmpty()) return

        // Calculate pages to load with ±1 buffer
        val rangeStart = (visibleRange.first - PAGE_BUFFER_SIZE).coerceAtLeast(0)
        val rangeEnd = (visibleRange.last + PAGE_BUFFER_SIZE).coerceAtMost(pages.lastIndex)
        val pagesToLoadIndices =
            rangeStart..rangeEnd

        // Get page IDs for the range to load
        val pageIdsToLoad = pages.slice(pagesToLoadIndices).map { it.pageId }.toSet()

        // Find pages to unload (outside the buffer range)
        val pageIdsToUnload = _pageStrokesCache.value.keys - pageIdsToLoad

        // Commit strokes for pages that will be unloaded
        if (pageIdsToUnload.isNotEmpty()) {
            commitStrokesForPages(pageIdsToUnload)
        }

        // Unload pages outside the range
        if (pageIdsToUnload.isNotEmpty()) {
            unloadPagesFromCache(pageIdsToUnload)
        }

        // Load strokes for pages in range that aren't already cached
        val pagesNotCached = pageIdsToLoad.filter { !_pageStrokesCache.value.containsKey(it) }
        if (pagesNotCached.isNotEmpty()) {
            loadStrokesForPagesAsync(pagesNotCached)
        }
    }

    /**
     * Commit any pending strokes for the specified pages before unloading.
     * This ensures no strokes are lost when pages are removed from cache.
     */
    private fun commitStrokesForPages(pageIds: Set<String>) {
        // Strokes are already persisted via strokeWriteQueue when added/removed.
        // This method exists for future extensibility if we add batched/uncommitted strokes.
        // Currently, all strokes are persisted immediately via addStrokeForPage/removeStrokeForPage.
        Log.d(
            LOG_TAG,
            "Committing strokes for pages before unload: ${
                pageIds.take(PAGE_LOG_PREVIEW_COUNT).joinToString()
            }",
        )
    }

    /**
     * Remove pages from the stroke cache to free memory.
     */
    private fun unloadPagesFromCache(pageIds: Set<String>) {
        val currentCache = _pageStrokesCache.value.toMutableMap()
        var modified = false
        for (pageId in pageIds) {
            if (currentCache.remove(pageId) != null) {
                modified = true
                Log.d(LOG_TAG, "Unloaded strokes from cache for page: $pageId")
            }
        }
        if (modified) {
            _pageStrokesCache.value = currentCache
        }
    }

    /**
     * Load strokes for pages asynchronously (non-blocking).
     */
    private fun loadStrokesForPagesAsync(pageIds: List<String>) {
        viewModelScope.launch {
            runCatching {
                val currentCache = _pageStrokesCache.value.toMutableMap()
                for (pageId in pageIds) {
                    if (!currentCache.containsKey(pageId)) {
                        val strokes = repository.getStrokesForPage(pageId)
                        currentCache[pageId] = strokes
                        Log.d(LOG_TAG, "Loaded ${strokes.size} strokes for page: $pageId")
                    }
                }
                _pageStrokesCache.value = currentCache
            }.onFailure { throwable ->
                reportError("Failed to load strokes for pages.", throwable)
            }
        }
    }

    /**
     * Force load strokes for a specific page (synchronous).
     * Returns immediately if already cached.
     */
    suspend fun ensurePageLoaded(pageId: String) {
        if (_pageStrokesCache.value.containsKey(pageId)) return

        runCatching {
            val strokes = repository.getStrokesForPage(pageId)
            val currentCache = _pageStrokesCache.value.toMutableMap()
            currentCache[pageId] = strokes
            _pageStrokesCache.value = currentCache
        }.onFailure { throwable ->
            reportError("Failed to load strokes for page: $pageId", throwable)
        }
    }

    /**
     * Get the set of currently loaded page IDs in the cache.
     */
    fun getLoadedPageIds(): Set<String> = _pageStrokesCache.value.keys

    /**
     * Clear all strokes from cache (for memory pressure situations).
     * Commits any pending data first.
     */
    fun clearStrokeCache() {
        val loadedPages = _pageStrokesCache.value.keys
        commitStrokesForPages(loadedPages)
        _pageStrokesCache.value = emptyMap()
        Log.d(LOG_TAG, "Cleared stroke cache for ${loadedPages.size} pages")
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
        // Note: Do NOT change activeRecognitionPageId here - it's only updated on stylus interaction
        if (currentPageId != page?.pageId && currentPageId != null) {
            // Only close MyScript page if it's not the active recognition page
            if (currentPageId != _activeRecognitionPageId.value) {
                myScriptPageManager?.closeCurrentPage()
            }
        }
        currentPageId = page?.pageId
        _currentPage.value = page
        if (page?.kind == "pdf") {
            _strokes.value = emptyList()
            return
        }
        currentPageId?.let { pageId ->
            // Only initialize recognition if this is also the active recognition page
            if (pageId == _activeRecognitionPageId.value) {
                myScriptPageManager?.onPageEnter(pageId)
            }
            val loadedStrokes = repository.getStrokesForPage(pageId)
            _strokes.value = loadedStrokes
            // Only add strokes to recognition if this is the active recognition page
            if (pageId == _activeRecognitionPageId.value) {
                loadedStrokes.forEach { stroke ->
                    myScriptPageManager?.addStroke(stroke)
                }
            }
        } ?: run {
            _strokes.value = emptyList()
        }
    }

    override fun onCleared() {
        myScriptPageManager?.closeCurrentPage()
        _activeRecognitionPageId.value = null
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
        runCatching {
            Log.e(LOG_TAG, message, throwable)
        }
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
