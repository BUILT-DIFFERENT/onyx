package com.onyx.android.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.onyx.android.OnyxApplication
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.repository.SearchResultItem
import com.onyx.android.pdf.PdfAssetStorage
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToEditor: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as OnyxApplication
    val repository = app.noteRepository
    val pdfAssetStorage = remember { PdfAssetStorage(app) }
    val viewModel: HomeScreenViewModel =
        viewModel(
            key = "HomeScreenViewModel",
            factory = HomeScreenViewModelFactory(repository, pdfAssetStorage),
        )
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Notes") },
                actions = {
                    IconButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Import PDF",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.createNote(
                        onNavigateToEditor = onNavigateToEditor,
                        onError = { message -> errorMessage = message },
                    )
                },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New note")
            }
        },
    ) { paddingValues ->
        if (warningMessage != null) {
            AlertDialog(
                onDismissRequest = { warningMessage = null },
                title = { Text(text = "Large PDF") },
                text = { Text(text = warningMessage.orEmpty()) },
                confirmButton = {
                    Button(onClick = { warningMessage = null }) {
                        Text(text = "OK")
                    }
                },
            )
        }
        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text(text = "Import failed") },
                text = { Text(text = errorMessage.orEmpty()) },
                confirmButton = {
                    Button(onClick = { errorMessage = null }) {
                        Text(text = "OK")
                    }
                },
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text(text = "Search notes...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                ) {
                    items(searchResults) { result ->
                        SearchResultRow(result = result)
                    }
                }
            } else {
                val emptyStateText =
                    if (searchQuery.length >= 2) {
                        "No results found"
                    } else {
                        "Type to search notes..."
                    }
                Text(
                    text = emptyStateText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResultItem) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = result.noteTitle,
                style = MaterialTheme.typography.titleMedium,
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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
private class HomeScreenViewModel(
    private val repository: NoteRepository,
    private val pdfAssetStorage: PdfAssetStorage,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<SearchResultItem>> =
        _searchQuery
            .debounce(300)
            .flatMapLatest { query ->
                if (query.length < 2) {
                    Log.d("HomeScreen", "Search query='$query' results=0")
                    flowOf(emptyList())
                } else {
                    repository.searchNotes(query).onEach { results ->
                        Log.d("HomeScreen", "Search query='$query' results=${results.size}")
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun createNote(
        onNavigateToEditor: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val noteWithPage = repository.createNote()
                onNavigateToEditor(noteWithPage.note.noteId)
            } catch (e: Exception) {
                Log.e("HomeScreen", "Create note failed", e)
                onError("Unable to create note. Please try again.")
            }
        }
    }

    fun importPdf(
        uri: Uri,
        onWarning: (String) -> Unit,
        onError: (String) -> Unit,
        onNavigateToEditor: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val (noteId, warningMessage) =
                    withContext(Dispatchers.IO) {
                        val pdfAssetId = pdfAssetStorage.importPdf(uri)
                        val pdfFile = pdfAssetStorage.getFileForAsset(pdfAssetId)
                        var document: Document? = null
                        try {
                            document = Document.openDocument(pdfFile.absolutePath)
                            val pageCount = document.countPages()
                            val fileSizeBytes = pdfFile.length()
                            val shouldWarn = fileSizeBytes > 50L * 1024 * 1024 || pageCount > 100
                            val warning =
                                if (shouldWarn) {
                                    val sizeMb = (fileSizeBytes / (1024 * 1024)).coerceAtLeast(1)
                                    "This PDF has $pageCount pages and is ${sizeMb}MB. Performance may be affected."
                                } else {
                                    null
                                }
                            val noteWithPage = repository.createNote()
                            repository.deletePage(noteWithPage.firstPageId)
                            for (pageIndex in 0 until pageCount) {
                                val page = document.loadPage(pageIndex)
                                try {
                                    val bounds = page.bounds
                                    val width = bounds.x1 - bounds.x0
                                    val height = bounds.y1 - bounds.y0
                                    repository.createPageFromPdf(
                                        noteId = noteWithPage.note.noteId,
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
                            noteWithPage.note.noteId to warning
                        } finally {
                            document?.destroy()
                        }
                    }
                if (warningMessage != null) {
                    onWarning(warningMessage)
                }
                onNavigateToEditor(noteId)
            } catch (e: Exception) {
                Log.e("HomeScreen", "PDF import failed", e)
                onError("PDF import failed. Please try again.")
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
