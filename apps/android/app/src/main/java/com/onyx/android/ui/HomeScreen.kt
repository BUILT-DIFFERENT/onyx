@file:Suppress("FunctionName")

package com.onyx.android.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artifex.mupdf.fitz.Document
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.repository.SearchResultItem
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.requireAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val HOME_LOG_TAG = "HomeScreen"
private const val PDF_MIME_TYPE = "application/pdf"
private const val MIN_SEARCH_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 300L
private const val MAX_PDF_FILE_SIZE_BYTES = 50L * 1024 * 1024
private const val MAX_PDF_PAGE_COUNT = 100
private const val BYTES_PER_MB: Long = 1024L * 1024L
private const val MIN_WARNING_MB = 1L

private data class HomeScreenState(
    val searchQuery: String,
    val searchResults: List<SearchResultItem>,
    val notes: List<NoteEntity>,
    val warningMessage: String?,
    val errorMessage: String?,
)

private data class HomeScreenActions(
    val onDismissWarning: () -> Unit,
    val onDismissError: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onImportPdf: () -> Unit,
    val onCreateNote: () -> Unit,
    val onNavigateToEditor: (String, String?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToEditor: (String, String?) -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val appContainer = appContext.requireAppContainer()
    val repository = appContainer.noteRepository
    val pdfAssetStorage = remember { PdfAssetStorage(appContext) }
    val viewModel: HomeScreenViewModel =
        viewModel(
            key = "HomeScreenViewModel",
            factory = HomeScreenViewModelFactory(repository, pdfAssetStorage),
        )
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val notes by viewModel.notes.collectAsState()
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val openPdfLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.importPdf(
                    uri = uri,
                    onWarning = { message -> warningMessage = message },
                    onError = { message -> errorMessage = message },
                    onNavigateToEditor = onNavigateToEditor,
                )
            }
        }

    val uiState =
        HomeScreenState(
            searchQuery = searchQuery,
            searchResults = searchResults,
            notes = notes,
            warningMessage = warningMessage,
            errorMessage = errorMessage,
        )
    val actions =
        HomeScreenActions(
            onDismissWarning = { warningMessage = null },
            onDismissError = { errorMessage = null },
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onImportPdf = { openPdfLauncher.launch(arrayOf(PDF_MIME_TYPE)) },
            onCreateNote = {
                viewModel.createNote(
                    onNavigateToEditor = onNavigateToEditor,
                    onError = { message -> errorMessage = message },
                )
            },
            onNavigateToEditor = onNavigateToEditor,
        )
    HomeScreenContent(
        state = uiState,
        actions = actions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    state: HomeScreenState,
    actions: HomeScreenActions,
) {
    HomeDialogs(state = state, actions = actions)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Notes") },
                actions = {
                    IconButton(onClick = actions.onImportPdf) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Import PDF",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onCreateNote) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New note")
            }
        },
    ) { paddingValues ->
        HomeContentBody(
            state = state,
            actions = actions,
            paddingValues = paddingValues,
        )
    }
}

@Composable
private fun HomeDialogs(
    state: HomeScreenState,
    actions: HomeScreenActions,
) {
    if (state.warningMessage != null) {
        AlertDialog(
            onDismissRequest = actions.onDismissWarning,
            title = { Text(text = "Large PDF") },
            text = { Text(text = state.warningMessage) },
            confirmButton = {
                Button(onClick = actions.onDismissWarning) {
                    Text(text = "OK")
                }
            },
        )
    }
    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = actions.onDismissError,
            title = { Text(text = "Import failed") },
            text = { Text(text = state.errorMessage) },
            confirmButton = {
                Button(onClick = actions.onDismissError) {
                    Text(text = "OK")
                }
            },
        )
    }
}

@Composable
private fun HomeContentBody(
    state: HomeScreenState,
    actions: HomeScreenActions,
    paddingValues: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = actions.onSearchQueryChange,
            placeholder = { Text(text = "Search notes...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        HomeListContent(
            state = state,
            onNavigateToEditor = actions.onNavigateToEditor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeListContent(
    state: HomeScreenState,
    onNavigateToEditor: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.searchQuery.length >= MIN_SEARCH_QUERY_LENGTH -> {
            SearchResultsContent(
                searchResults = state.searchResults,
                onNavigateToEditor = onNavigateToEditor,
                modifier = modifier,
            )
        }

        state.notes.isNotEmpty() -> {
            NotesListContent(
                notes = state.notes,
                onNavigateToEditor = onNavigateToEditor,
                modifier = modifier,
            )
        }

        else -> EmptyNotesMessage(modifier)
    }
}

@Composable
private fun SearchResultsContent(
    searchResults: List<SearchResultItem>,
    onNavigateToEditor: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searchResults.isEmpty()) {
        Text(
            text = "No results found",
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier =
            modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 80.dp,
            ),
    ) {
        items(searchResults) { result ->
            SearchResultRow(
                result = result,
                onClick = { onNavigateToEditor(result.noteId, result.pageId) },
            )
        }
    }
}

@Composable
private fun NotesListContent(
    notes: List<NoteEntity>,
    onNavigateToEditor: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 80.dp,
            ),
    ) {
        items(notes, key = { it.noteId }) { note ->
            NoteRow(
                note = note,
                onClick = { onNavigateToEditor(note.noteId, null) },
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        val updatedLabel = remember(note.updatedAt) { formatTimestamp(note.updatedAt) }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title.ifEmpty { "Untitled Note" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Updated $updatedLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResultItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = result.noteTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Page ${result.pageNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = result.snippetText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(timestamp))
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
private class HomeScreenViewModel(
    private val repository: NoteRepository,
    private val pdfAssetStorage: PdfAssetStorage,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<SearchResultItem>> =
        _searchQuery
            .debounce(SEARCH_DEBOUNCE_MS)
            .flatMapLatest { query ->
                if (query.length < MIN_SEARCH_QUERY_LENGTH) {
                    Log.d(HOME_LOG_TAG, "Search query='$query' results=0")
                    flowOf(emptyList())
                } else {
                    repository.searchNotes(query).onEach { results ->
                        Log.d(HOME_LOG_TAG, "Search query='$query' results=${results.size}")
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notes: StateFlow<List<NoteEntity>> =
        repository
            .getAllNotes()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun createNote(
        onNavigateToEditor: (String, String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.createNote() }
                .onSuccess { noteWithPage ->
                    onNavigateToEditor(noteWithPage.note.noteId, null)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    Log.e(HOME_LOG_TAG, "Create note failed", throwable)
                    onError("Unable to create note. Please try again.")
                }
        }
    }

    fun importPdf(
        uri: Uri,
        onWarning: (String) -> Unit,
        onError: (String) -> Unit,
        onNavigateToEditor: (String, String?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                importPdfInternal(uri)
            }.onSuccess { (noteId, warningMessage) ->
                if (warningMessage != null) {
                    onWarning(warningMessage)
                }
                onNavigateToEditor(noteId, null)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "PDF import failed", throwable)
                onError("PDF import failed. Please try again.")
            }
        }
    }

    private suspend fun importPdfInternal(uri: Uri): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            val pdfAssetId = pdfAssetStorage.importPdf(uri)
            val pdfFile = pdfAssetStorage.getFileForAsset(pdfAssetId)
            var document: Document? = null
            try {
                document = Document.openDocument(pdfFile.absolutePath)
                val pageCount = document.countPages()
                val warning = buildPdfWarning(pageCount, pdfFile.length())
                val noteWithPage = repository.createNote()
                repository.deletePage(noteWithPage.firstPageId)
                createPagesFromPdf(document, noteWithPage.note.noteId, pdfAssetId, pageCount)
                noteWithPage.note.noteId to warning
            } finally {
                document?.destroy()
            }
        }

    private fun buildPdfWarning(
        pageCount: Int,
        fileSizeBytes: Long,
    ): String? {
        val shouldWarn = fileSizeBytes > MAX_PDF_FILE_SIZE_BYTES || pageCount > MAX_PDF_PAGE_COUNT
        if (!shouldWarn) {
            return null
        }
        val sizeMb = (fileSizeBytes / BYTES_PER_MB).coerceAtLeast(MIN_WARNING_MB)
        return "This PDF has $pageCount pages and is ${sizeMb}MB. Performance may be affected."
    }

    private suspend fun createPagesFromPdf(
        document: Document,
        noteId: String,
        pdfAssetId: String,
        pageCount: Int,
    ) {
        for (pageIndex in 0 until pageCount) {
            val page = document.loadPage(pageIndex)
            try {
                val bounds = page.bounds
                val width = bounds.x1 - bounds.x0
                val height = bounds.y1 - bounds.y0
                repository.createPageFromPdf(
                    noteId = noteId,
                    indexInNote = pageIndex,
                    pdfAssetId = pdfAssetId,
                    pdfPageNo = pageIndex,
                    pdfWidth = width,
                    pdfHeight = height,
                )
            } finally {
                page.destroy()
            }
        }
    }
}

private class HomeScreenViewModelFactory(
    private val repository: NoteRepository,
    private val pdfAssetStorage: PdfAssetStorage,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeScreenViewModel::class.java))
        return HomeScreenViewModel(repository, pdfAssetStorage) as T
    }
}
