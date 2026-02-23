@file:Suppress("TooManyFunctions", "LongParameterList", "ComplexCondition", "LargeClass", "MagicNumber", "MaxLineLength")

package com.onyx.android.ui

import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.repository.EditorSettings
import com.onyx.android.data.repository.EditorSettingsRepository
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.repository.PageTemplateConfig
import com.onyx.android.ink.algorithm.applyStrokeSplit
import com.onyx.android.ink.algorithm.restoreStrokeSplit
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool
import com.onyx.android.objects.model.PageObject
import com.onyx.android.objects.model.PageObjectKind
import com.onyx.android.objects.model.ShapePayload
import com.onyx.android.objects.model.ShapeType
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.recognition.ConvertedTextBlock
import com.onyx.android.recognition.MyScriptPageManager
import com.onyx.android.recognition.OverlayBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
internal class NoteEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: NoteRepository,
        private val editorSettingsRepository: EditorSettingsRepository,
        private val noteDao: NoteDao,
        private val pageDao: PageDao,
        private val myScriptPageManager: MyScriptPageManager?,
        val pdfPasswordStore: PdfPasswordStore,
        val pdfAssetStorage: PdfAssetStorage,
    ) : ViewModel() {
        data class ConversionDraft(
            val pageId: String,
            val selectedStrokeIds: Set<String>,
            val bounds: OverlayBounds,
            val initialText: String,
            val editingBlockId: String? = null,
        )

        private val noteId: String = checkNotNull(savedStateHandle.get<String>("noteId"))
        private var pendingInitialPageId: String? = savedStateHandle.get<String>("pageId")
        private val initialSearchHighlightPageId: String? = pendingInitialPageId
        private var pendingInitialPageIndex: Int? =
            savedStateHandle
                .get<Int>("pageIndex")
                ?.takeIf { pageIndex -> pageIndex >= 0 }
        private val initialSearchHighlightPageIndex: Int? = pendingInitialPageIndex
        private val initialHighlightBounds: Rect? =
            run {
                val left = savedStateHandle.get<Float>("highlightLeft") ?: Float.NaN
                val top = savedStateHandle.get<Float>("highlightTop") ?: Float.NaN
                val right = savedStateHandle.get<Float>("highlightRight") ?: Float.NaN
                val bottom = savedStateHandle.get<Float>("highlightBottom") ?: Float.NaN
                if (left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
                    Rect(left = left, top = top, right = right, bottom = bottom)
                } else {
                    null
                }
            }

        companion object {
            private const val LOG_TAG = "NoteEditorViewModel"
            private const val PAGE_BUFFER_SIZE = 1
            private const val PAGE_LOG_PREVIEW_COUNT = 5
            private const val SPLIT_RECOGNITION_REFRESH_STROKE_ID = "__segment_eraser_refresh__"
        }

        private val objectJson = Json { ignoreUnknownKeys = true }

        private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
        val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()
        private val _pageObjects = MutableStateFlow<List<PageObject>>(emptyList())
        val pageObjects: StateFlow<List<PageObject>> = _pageObjects.asStateFlow()
        private val _selectedObjectId = MutableStateFlow<String?>(null)
        val selectedObjectId: StateFlow<String?> = _selectedObjectId.asStateFlow()
        private val _pages = MutableStateFlow<List<PageEntity>>(emptyList())
        val pages: StateFlow<List<PageEntity>> = _pages.asStateFlow()
        private val _noteTitle = MutableStateFlow("")
        val noteTitle: StateFlow<String> = _noteTitle.asStateFlow()
        private val _currentPageIndex = MutableStateFlow(0)
        val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()
        private val _currentPage = MutableStateFlow<PageEntity?>(null)
        val currentPage: StateFlow<PageEntity?> = _currentPage.asStateFlow()
        private val _currentTemplateConfig = MutableStateFlow(PageTemplateConfig.BLANK)
        val currentTemplateConfig: StateFlow<PageTemplateConfig> = _currentTemplateConfig.asStateFlow()
        private val _templateConfigByPageId = MutableStateFlow<Map<String, PageTemplateConfig>>(emptyMap())
        val templateConfigByPageId: StateFlow<Map<String, PageTemplateConfig>> = _templateConfigByPageId.asStateFlow()
        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
        private val _searchHighlightBounds = MutableStateFlow<Rect?>(initialHighlightBounds)
        val searchHighlightBounds: StateFlow<Rect?> = _searchHighlightBounds.asStateFlow()
        val searchHighlightPageId: String? = initialSearchHighlightPageId
        val searchHighlightPageIndex: Int? = initialSearchHighlightPageIndex
        private val defaultEditorSettings = EditorSettingsRepository.DEFAULT_SETTINGS
        val editorSettings: StateFlow<EditorSettings> =
            editorSettingsRepository
                .getSettings()
                .map { settings -> settings ?: defaultEditorSettings }
                .stateIn(viewModelScope, SharingStarted.Eagerly, defaultEditorSettings)

        // Active recognition page - updated ONLY on stylus interaction, NOT on scroll
        // This ensures recognition stays on the correct page during fast scrolling
        private val _activeRecognitionPageId = MutableStateFlow<String?>(null)
        val activeRecognitionPageId: StateFlow<String?> = _activeRecognitionPageId.asStateFlow()

        // Multi-page strokes cache: pageId -> strokes
        private val _pageStrokesCache = MutableStateFlow<Map<String, List<Stroke>>>(emptyMap())
        val pageStrokesCache: StateFlow<Map<String, List<Stroke>>> = _pageStrokesCache.asStateFlow()
        private val _pageObjectsCache = MutableStateFlow<Map<String, List<PageObject>>>(emptyMap())
        val pageObjectsCache: StateFlow<Map<String, List<PageObject>>> = _pageObjectsCache.asStateFlow()

        private val _recognitionOverlayEnabled = MutableStateFlow(false)
        val recognitionOverlayEnabled: StateFlow<Boolean> = _recognitionOverlayEnabled.asStateFlow()

        private val _recognizedTextByPage = MutableStateFlow<Map<String, String>>(emptyMap())
        val recognizedTextByPage: StateFlow<Map<String, String>> = _recognizedTextByPage.asStateFlow()

        private val _convertedTextBlocksByPage = MutableStateFlow<Map<String, List<ConvertedTextBlock>>>(emptyMap())
        val convertedTextBlocksByPage: StateFlow<Map<String, List<ConvertedTextBlock>>> =
            _convertedTextBlocksByPage.asStateFlow()

        private val _conversionDraft = MutableStateFlow<ConversionDraft?>(null)
        val conversionDraft: StateFlow<ConversionDraft?> = _conversionDraft.asStateFlow()

        private var currentPageId: String? = null
        private val strokeWriteQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

        init {
            myScriptPageManager?.onRecognitionUpdated = { pageId, text ->
                viewModelScope.launch {
                    runCatching {
                        repository.updateRecognition(pageId, text, "myscript-4.3")
                        val updated = _recognizedTextByPage.value.toMutableMap()
                        updated[pageId] = text
                        _recognizedTextByPage.value = updated
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

        fun updateEditorSettings(settings: EditorSettings) {
            if (editorSettings.value == settings) {
                return
            }
            viewModelScope.launch {
                runCatching {
                    editorSettingsRepository.saveSettings(settings)
                }.onFailure { throwable ->
                    reportError("Failed to persist editor settings.", throwable)
                }
            }
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
                    refreshPages(selectIndex = pendingInitialPageIndex, selectPageId = pendingInitialPageId)
                    pendingInitialPageId = null
                    pendingInitialPageIndex = null
                }.onFailure { throwable ->
                    reportError("Failed to load note.", throwable)
                }
            }
        }

        fun clearSearchHighlight() {
            _searchHighlightBounds.value = null
        }

        fun clearError() {
            _errorMessage.value = null
        }

        fun toggleRecognitionOverlay() {
            _recognitionOverlayEnabled.value = !_recognitionOverlayEnabled.value
        }

        fun dismissConversionDraft() {
            _conversionDraft.value = null
        }

        fun startConversionDraftFromBlock(
            pageId: String,
            block: ConvertedTextBlock,
        ) {
            _conversionDraft.value =
                ConversionDraft(
                    pageId = pageId,
                    selectedStrokeIds = emptySet(),
                    bounds = block.bounds,
                    initialText = block.text,
                    editingBlockId = block.id,
                )
        }

        fun startConversionDraftFromLasso(
            pageId: String,
            lassoStroke: Stroke,
            pageStrokes: List<Stroke>,
        ) {
            val polygon = lassoStroke.points.map { point -> point.x to point.y }
            if (polygon.size < 3) {
                return
            }
            val selectedStrokes =
                pageStrokes.filter { stroke ->
                    stroke.id != lassoStroke.id &&
                        stroke.style.tool != Tool.LASSO &&
                        isStrokeCenterInsidePolygon(stroke, polygon)
                }
            if (selectedStrokes.isEmpty()) {
                return
            }
            val selectionBounds = mergeBounds(selectedStrokes)
            val preferredText =
                _recognizedTextByPage.value[pageId]
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Converted text" }
            _conversionDraft.value =
                ConversionDraft(
                    pageId = pageId,
                    selectedStrokeIds = selectedStrokes.mapTo(mutableSetOf()) { stroke -> stroke.id },
                    bounds = selectionBounds,
                    initialText = preferredText,
                    editingBlockId = null,
                )
        }

        fun commitConversionDraft(text: String) {
            val draft = _conversionDraft.value ?: return
            val normalized = text.trim()
            if (normalized.isBlank()) {
                _conversionDraft.value = null
                return
            }
            viewModelScope.launch {
                runCatching {
                    val existing = _convertedTextBlocksByPage.value[draft.pageId].orEmpty()
                    val now = System.currentTimeMillis()
                    val updatedBlocks =
                        if (draft.editingBlockId != null) {
                            existing.map { block ->
                                if (block.id == draft.editingBlockId) {
                                    block.copy(text = normalized, updatedAt = now)
                                } else {
                                    block
                                }
                            }
                        } else {
                            existing +
                                ConvertedTextBlock(
                                    id = UUID.randomUUID().toString(),
                                    text = normalized,
                                    bounds = draft.bounds,
                                    updatedAt = now,
                                )
                        }
                    repository.saveConvertedTextBlocks(draft.pageId, updatedBlocks)
                    val updated = _convertedTextBlocksByPage.value.toMutableMap()
                    updated[draft.pageId] = updatedBlocks
                    _convertedTextBlocksByPage.value = updated
                    _conversionDraft.value = null
                }.onFailure { throwable ->
                    reportError("Failed to persist converted text.", throwable)
                }
            }
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

        fun updateCurrentPageTemplate(config: PageTemplateConfig) {
            val page = _currentPage.value ?: return
            val normalized = normalizeTemplateConfig(config)
            val updatedTemplateMap = _templateConfigByPageId.value.toMutableMap()
            updatedTemplateMap[page.pageId] = normalized
            _templateConfigByPageId.value = updatedTemplateMap
            _currentTemplateConfig.value = normalized
            viewModelScope.launch {
                runCatching {
                    repository.saveTemplateForPage(
                        pageId = page.pageId,
                        backgroundKind = normalized.backgroundKind,
                        spacing = normalized.spacing,
                        colorHex = normalized.colorHex,
                    )
                    pageDao.getById(page.pageId)?.let { updatedPage ->
                        replacePageInState(updatedPage)
                        if (currentPageId == updatedPage.pageId) {
                            _currentPage.value = updatedPage
                        }
                    }
                }.onFailure { throwable ->
                    reportError("Failed to persist page template.", throwable)
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

        fun splitStroke(
            original: Stroke,
            segments: List<Stroke>,
            pageId: String,
            persist: Boolean,
            updateRecognition: Boolean = true,
        ): Int? {
            val mutation = applyStrokeSplit(_strokes.value, original, segments) ?: return null
            _strokes.value = mutation.strokes
            if (persist) {
                enqueueStrokeWrite {
                    repository.replaceStrokeWithSegments(pageId, original.id, segments)
                }
            }
            if (updateRecognition && currentPageId == _activeRecognitionPageId.value) {
                myScriptPageManager?.onStrokeErased(SPLIT_RECOGNITION_REFRESH_STROKE_ID, mutation.strokes)
            }
            return mutation.insertionIndex
        }

        fun restoreSplitStroke(
            original: Stroke,
            segments: List<Stroke>,
            pageId: String,
            insertionIndex: Int,
            persist: Boolean,
            updateRecognition: Boolean = true,
        ) {
            val restored = restoreStrokeSplit(_strokes.value, original, segments, insertionIndex)
            _strokes.value = restored
            if (persist) {
                enqueueStrokeWrite {
                    repository.restoreSplitStroke(
                        pageId = pageId,
                        original = original,
                        segmentIds = segments.map { segment -> segment.id },
                    )
                }
            }
            if (updateRecognition && currentPageId == _activeRecognitionPageId.value) {
                myScriptPageManager?.onStrokeErased(SPLIT_RECOGNITION_REFRESH_STROKE_ID, restored)
            }
        }

        fun replaceStrokes(
            pageId: String,
            replacement: List<Stroke>,
            persist: Boolean,
            updateRecognition: Boolean = true,
        ) {
            if (replacement.isEmpty()) {
                return
            }
            val replacementById = replacement.associateBy { stroke -> stroke.id }
            _strokes.value =
                _strokes.value.map { stroke ->
                    replacementById[stroke.id] ?: stroke
                }
            if (persist) {
                enqueueStrokeWrite {
                    repository.replaceStrokes(pageId, replacement)
                }
            }
            if (updateRecognition && currentPageId == _activeRecognitionPageId.value) {
                myScriptPageManager?.onStrokeErased(SPLIT_RECOGNITION_REFRESH_STROKE_ID, _strokes.value)
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

        fun splitStrokeForPage(
            original: Stroke,
            segments: List<Stroke>,
            pageId: String,
            persist: Boolean,
            updateRecognition: Boolean = true,
        ): Int? {
            val currentCache = _pageStrokesCache.value.toMutableMap()
            val currentPageStrokes = currentCache[pageId].orEmpty()
            val mutation = applyStrokeSplit(currentPageStrokes, original, segments) ?: return null
            currentCache[pageId] = mutation.strokes
            _pageStrokesCache.value = currentCache
            if (persist) {
                enqueueStrokeWrite {
                    repository.replaceStrokeWithSegments(pageId, original.id, segments)
                }
            }
            if (updateRecognition && pageId == currentPageId) {
                myScriptPageManager?.onStrokeErased(SPLIT_RECOGNITION_REFRESH_STROKE_ID, mutation.strokes)
            }
            return mutation.insertionIndex
        }

        fun restoreSplitStrokeForPage(
            original: Stroke,
            segments: List<Stroke>,
            pageId: String,
            insertionIndex: Int,
            persist: Boolean,
            updateRecognition: Boolean = true,
        ) {
            val currentCache = _pageStrokesCache.value.toMutableMap()
            val currentPageStrokes = currentCache[pageId].orEmpty()
            val restored = restoreStrokeSplit(currentPageStrokes, original, segments, insertionIndex)
            currentCache[pageId] = restored
            _pageStrokesCache.value = currentCache
            if (persist) {
                enqueueStrokeWrite {
                    repository.restoreSplitStroke(
                        pageId = pageId,
                        original = original,
                        segmentIds = segments.map { segment -> segment.id },
                    )
                }
            }
            if (updateRecognition && pageId == currentPageId) {
                myScriptPageManager?.onStrokeErased(SPLIT_RECOGNITION_REFRESH_STROKE_ID, restored)
            }
        }

        fun replaceStrokesForPage(
            pageId: String,
            replacement: List<Stroke>,
            persist: Boolean,
            updateRecognition: Boolean = true,
        ) {
            if (replacement.isEmpty()) {
                return
            }
            val currentCache = _pageStrokesCache.value.toMutableMap()
            val replacementById = replacement.associateBy { stroke -> stroke.id }
            val updatedPageStrokes =
                currentCache[pageId]
                    .orEmpty()
                    .map { stroke ->
                        replacementById[stroke.id] ?: stroke
                    }
            currentCache[pageId] = updatedPageStrokes
            _pageStrokesCache.value = currentCache
            if (persist) {
                enqueueStrokeWrite {
                    repository.replaceStrokes(pageId, replacement)
                }
            }
            if (updateRecognition && pageId == currentPageId) {
                myScriptPageManager?.onStrokeErased(SPLIT_RECOGNITION_REFRESH_STROKE_ID, updatedPageStrokes)
            }
        }

        fun selectObject(objectId: String?) {
            _selectedObjectId.value = objectId
        }

        fun addPageObject(
            pageObject: PageObject,
            persist: Boolean,
        ) {
            _pageObjects.value = (_pageObjects.value + pageObject).sortedBy { objectItem -> objectItem.zIndex }
            updatePageObjectCacheForPage(pageObject.pageId, _pageObjects.value)
            if (persist) {
                viewModelScope.launch {
                    runCatching {
                        repository.upsertPageObject(pageObject)
                    }.onFailure { throwable ->
                        reportError("Failed to save page object.", throwable)
                    }
                }
            }
        }

        fun removePageObject(
            pageObject: PageObject,
            persist: Boolean,
        ) {
            _pageObjects.value = _pageObjects.value.filterNot { objectItem -> objectItem.objectId == pageObject.objectId }
            updatePageObjectCacheForPage(pageObject.pageId, _pageObjects.value)
            if (_selectedObjectId.value == pageObject.objectId) {
                _selectedObjectId.value = null
            }
            if (persist) {
                viewModelScope.launch {
                    runCatching {
                        repository.deletePageObject(pageObject.objectId, pageObject.pageId)
                    }.onFailure { throwable ->
                        reportError("Failed to delete page object.", throwable)
                    }
                }
            }
        }

        fun transformPageObject(
            before: PageObject,
            after: PageObject,
            persist: Boolean,
        ) {
            val updated =
                _pageObjects.value.map { objectItem ->
                    if (objectItem.objectId == before.objectId) {
                        after
                    } else {
                        objectItem
                    }
                }
            _pageObjects.value = updated
            updatePageObjectCacheForPage(after.pageId, updated)
            if (persist) {
                viewModelScope.launch {
                    runCatching {
                        repository.updatePageObjectGeometry(after)
                    }.onFailure { throwable ->
                        reportError("Failed to update page object.", throwable)
                    }
                }
            }
        }

        fun addPageObjectForPage(
            pageId: String,
            pageObject: PageObject,
            persist: Boolean,
        ) {
            val currentCache = _pageObjectsCache.value.toMutableMap()
            val pageObjects =
                (currentCache[pageId].orEmpty() + pageObject)
                    .sortedBy { objectItem -> objectItem.zIndex }
            currentCache[pageId] = pageObjects
            _pageObjectsCache.value = currentCache
            if (pageId == currentPageId) {
                _pageObjects.value = pageObjects
            }
            if (persist) {
                viewModelScope.launch {
                    runCatching {
                        repository.upsertPageObject(pageObject)
                    }.onFailure { throwable ->
                        reportError("Failed to save page object.", throwable)
                    }
                }
            }
        }

        fun removePageObjectForPage(
            pageId: String,
            pageObject: PageObject,
            persist: Boolean,
        ) {
            val currentCache = _pageObjectsCache.value.toMutableMap()
            val pageObjects = currentCache[pageId].orEmpty().filterNot { objectItem -> objectItem.objectId == pageObject.objectId }
            currentCache[pageId] = pageObjects
            _pageObjectsCache.value = currentCache
            if (pageId == currentPageId) {
                _pageObjects.value = pageObjects
            }
            if (_selectedObjectId.value == pageObject.objectId) {
                _selectedObjectId.value = null
            }
            if (persist) {
                viewModelScope.launch {
                    runCatching {
                        repository.deletePageObject(pageObject.objectId, pageObject.pageId)
                    }.onFailure { throwable ->
                        reportError("Failed to delete page object.", throwable)
                    }
                }
            }
        }

        fun transformPageObjectForPage(
            pageId: String,
            before: PageObject,
            after: PageObject,
            persist: Boolean,
        ) {
            val currentCache = _pageObjectsCache.value.toMutableMap()
            val pageObjects =
                currentCache[pageId]
                    .orEmpty()
                    .map { objectItem ->
                        if (objectItem.objectId == before.objectId) {
                            after
                        } else {
                            objectItem
                        }
                    }.sortedBy { objectItem -> objectItem.zIndex }
            currentCache[pageId] = pageObjects
            _pageObjectsCache.value = currentCache
            if (pageId == currentPageId) {
                _pageObjects.value = pageObjects
            }
            if (persist) {
                viewModelScope.launch {
                    runCatching {
                        repository.updatePageObjectGeometry(after)
                    }.onFailure { throwable ->
                        reportError("Failed to update page object.", throwable)
                    }
                }
            }
        }

        fun createShapeObject(
            pageId: String,
            shapeType: ShapeType,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
        ): PageObject {
            val now = System.currentTimeMillis()
            val noteIdForPage =
                _pages.value.firstOrNull { page -> page.pageId == pageId }?.noteId ?: noteId
            val zIndex = _pageObjectsCache.value[pageId].orEmpty().maxOfOrNull { objectItem -> objectItem.zIndex }?.plus(1) ?: 0
            val shapePayload = ShapePayload(shapeType = shapeType)
            return PageObject(
                objectId = UUID.randomUUID().toString(),
                pageId = pageId,
                noteId = noteIdForPage,
                kind = PageObjectKind.SHAPE,
                zIndex = zIndex,
                x = x,
                y = y,
                width = width,
                height = height,
                rotationDeg = 0f,
                payloadJson = objectJson.encodeToString(ShapePayload.serializer(), shapePayload),
                shapePayload = shapePayload,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        }

        fun duplicatePageObject(
            pageId: String,
            pageObject: PageObject,
        ): PageObject {
            val now = System.currentTimeMillis()
            return pageObject.copy(
                objectId = UUID.randomUUID().toString(),
                x = pageObject.x + 12f,
                y = pageObject.y + 12f,
                zIndex = (_pageObjectsCache.value[pageId].orEmpty().maxOfOrNull { objectItem -> objectItem.zIndex } ?: 0) + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        }

        /**
         * Load strokes for a range of pages (for pre-loading in multi-page mode).
         */
        fun loadStrokesForPages(pageIds: List<String>) {
            viewModelScope.launch {
                runCatching {
                    val currentStrokeCache = _pageStrokesCache.value.toMutableMap()
                    val currentObjectCache = _pageObjectsCache.value.toMutableMap()
                    for (pageId in pageIds) {
                        if (!currentStrokeCache.containsKey(pageId)) {
                            val strokes = repository.getStrokesForPage(pageId)
                            currentStrokeCache[pageId] = strokes
                        }
                        if (!currentObjectCache.containsKey(pageId)) {
                            val pageObjects = repository.getPageObjectsForPage(pageId)
                            currentObjectCache[pageId] = pageObjects
                        }
                    }
                    _pageStrokesCache.value = currentStrokeCache
                    _pageObjectsCache.value = currentObjectCache
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
                    _currentTemplateConfig.value =
                        _templateConfigByPageId.value[page.pageId]
                            ?: PageTemplateConfig.BLANK
                }
            }
        }

        /**
         * Get strokes for a specific page from cache or load if needed.
         */
        fun getStrokesForPage(pageId: String): List<Stroke> = _pageStrokesCache.value[pageId].orEmpty()

        fun getPageObjectsForPage(pageId: String): List<PageObject> = _pageObjectsCache.value[pageId].orEmpty()

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
        fun getActiveRecognitionPageId(): String? = _activeRecognitionPageId.value ?: currentPageId

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
            if (pageIdsToUnload.isNotEmpty()) {
                unloadPageObjectsFromCache(pageIdsToUnload)
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

        private fun unloadPageObjectsFromCache(pageIds: Set<String>) {
            val currentCache = _pageObjectsCache.value.toMutableMap()
            var modified = false
            for (pageId in pageIds) {
                if (currentCache.remove(pageId) != null) {
                    modified = true
                }
            }
            if (modified) {
                _pageObjectsCache.value = currentCache
            }
        }

        private fun updatePageObjectCacheForPage(
            pageId: String,
            pageObjects: List<PageObject>,
        ) {
            val currentCache = _pageObjectsCache.value.toMutableMap()
            currentCache[pageId] = pageObjects
            _pageObjectsCache.value = currentCache
        }

        /**
         * Load strokes for pages asynchronously (non-blocking).
         */
        private fun loadStrokesForPagesAsync(pageIds: List<String>) {
            viewModelScope.launch {
                runCatching {
                    val currentStrokeCache = _pageStrokesCache.value.toMutableMap()
                    val currentObjectCache = _pageObjectsCache.value.toMutableMap()
                    for (pageId in pageIds) {
                        if (!currentStrokeCache.containsKey(pageId)) {
                            val strokes = repository.getStrokesForPage(pageId)
                            currentStrokeCache[pageId] = strokes
                            Log.d(LOG_TAG, "Loaded ${strokes.size} strokes for page: $pageId")
                            loadRecognitionArtifacts(pageId)
                        }
                        if (!currentObjectCache.containsKey(pageId)) {
                            currentObjectCache[pageId] = repository.getPageObjectsForPage(pageId)
                        }
                    }
                    _pageStrokesCache.value = currentStrokeCache
                    _pageObjectsCache.value = currentObjectCache
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
                val pageObjects = repository.getPageObjectsForPage(pageId)
                val currentStrokeCache = _pageStrokesCache.value.toMutableMap()
                val currentObjectCache = _pageObjectsCache.value.toMutableMap()
                currentStrokeCache[pageId] = strokes
                currentObjectCache[pageId] = pageObjects
                _pageStrokesCache.value = currentStrokeCache
                _pageObjectsCache.value = currentObjectCache
                loadRecognitionArtifacts(pageId)
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
            _pageObjectsCache.value = emptyMap()
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
            _templateConfigByPageId.value = repository.getTemplateConfigMapByPageId(pages)
            if (pages.isEmpty()) {
                _currentPageIndex.value = 0
                _currentTemplateConfig.value = PageTemplateConfig.BLANK
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
            _currentTemplateConfig.value =
                page
                    ?.pageId
                    ?.let { pageId ->
                        _templateConfigByPageId.value[pageId]
                    } ?: PageTemplateConfig.BLANK
            currentPageId?.let { pageId ->
                // Only initialize recognition if this is also the active recognition page
                if (pageId == _activeRecognitionPageId.value) {
                    myScriptPageManager?.onPageEnter(pageId)
                }
                val loadedStrokes =
                    if (page?.kind == "pdf") {
                        emptyList()
                    } else {
                        repository.getStrokesForPage(pageId)
                    }
                _strokes.value = loadedStrokes
                val loadedPageObjects = repository.getPageObjectsForPage(pageId)
                _pageObjects.value = loadedPageObjects
                updatePageObjectCacheForPage(pageId, loadedPageObjects)
                _selectedObjectId.value = null
                if (page?.kind != "pdf" && pageId == _activeRecognitionPageId.value) {
                    loadedStrokes.forEach { stroke ->
                        myScriptPageManager?.addStroke(stroke)
                    }
                }
                loadRecognitionArtifacts(pageId)
            } ?: run {
                _strokes.value = emptyList()
                _pageObjects.value = emptyList()
                _selectedObjectId.value = null
            }
        }

        private fun normalizeTemplateConfig(config: PageTemplateConfig): PageTemplateConfig {
            val normalizedKind =
                when (config.backgroundKind) {
                    PageTemplateConfig.KIND_GRID,
                    PageTemplateConfig.KIND_LINED,
                    PageTemplateConfig.KIND_DOTTED,
                    PageTemplateConfig.KIND_BLANK,
                    -> config.backgroundKind

                    else -> PageTemplateConfig.KIND_GRID
                }
            val normalizedSpacing =
                if (normalizedKind == PageTemplateConfig.KIND_BLANK) {
                    0f
                } else {
                    config.spacing.coerceIn(8f, 80f)
                }
            return config.copy(backgroundKind = normalizedKind, spacing = normalizedSpacing)
        }

        private suspend fun loadRecognitionArtifacts(pageId: String) {
            val recognitionText = repository.getRecognitionText(pageId).orEmpty()
            val convertedBlocks = repository.getConvertedTextBlocks(pageId)
            val textMap = _recognizedTextByPage.value.toMutableMap()
            textMap[pageId] = recognitionText
            _recognizedTextByPage.value = textMap
            val blocksMap = _convertedTextBlocksByPage.value.toMutableMap()
            blocksMap[pageId] = convertedBlocks
            _convertedTextBlocksByPage.value = blocksMap
        }

        private fun mergeBounds(strokes: List<Stroke>): OverlayBounds {
            val left = strokes.minOf { stroke -> stroke.bounds.x }
            val top = strokes.minOf { stroke -> stroke.bounds.y }
            val right = strokes.maxOf { stroke -> stroke.bounds.x + stroke.bounds.w }
            val bottom = strokes.maxOf { stroke -> stroke.bounds.y + stroke.bounds.h }
            return OverlayBounds(
                x = left,
                y = top,
                w = (right - left).coerceAtLeast(1f),
                h = (bottom - top).coerceAtLeast(1f),
            )
        }

        private fun isStrokeCenterInsidePolygon(
            stroke: Stroke,
            polygon: List<Pair<Float, Float>>,
        ): Boolean {
            val centerX = stroke.bounds.x + (stroke.bounds.w / 2f)
            val centerY = stroke.bounds.y + (stroke.bounds.h / 2f)
            return pointInPolygon(centerX, centerY, polygon)
        }

        private fun pointInPolygon(
            x: Float,
            y: Float,
            polygon: List<Pair<Float, Float>>,
        ): Boolean {
            var inside = false
            var j = polygon.lastIndex
            for (i in polygon.indices) {
                val xi = polygon[i].first
                val yi = polygon[i].second
                val xj = polygon[j].first
                val yj = polygon[j].second
                val intersects =
                    (yi > y) != (yj > y) &&
                        x < (((xj - xi) * (y - yi)) / ((yj - yi).takeIf { delta -> delta != 0f } ?: 1f)) + xi
                if (intersects) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }

        override fun onCleared() {
            myScriptPageManager?.shutdown()
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
