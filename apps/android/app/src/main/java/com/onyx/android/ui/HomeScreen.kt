@file:Suppress("FunctionName", "TooManyFunctions")

package com.onyx.android.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.repository.SearchResultItem
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfDocumentInfoReader
import com.onyx.android.pdf.PdfIncorrectPasswordException
import com.onyx.android.pdf.PdfPageInfo
import com.onyx.android.pdf.PdfPasswordRequiredException
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.pdf.PdfiumDocumentInfoReader
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

private data class PdfPasswordPromptState(
    val uri: Uri,
    val password: String = "",
)

private data class HomeScreenState(
    val searchQuery: String,
    val searchResults: List<SearchResultItem>,
    val notes: List<NoteEntity>,
    val notePendingDelete: NoteEntity?,
    val passwordPrompt: PdfPasswordPromptState?,
    val warningMessage: String?,
    val errorMessage: String?,
)

private data class PdfImportCallbacks(
    val onPasswordRequired: (Uri) -> Unit,
    val onWarning: (String) -> Unit,
    val onError: (String) -> Unit,
    val onNavigateToEditor: (String, String?) -> Unit,
)

private data class HomeScreenActions(
    val onDismissWarning: () -> Unit,
    val onDismissError: () -> Unit,
    val onDismissPasswordPrompt: () -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onConfirmPasswordPrompt: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onImportPdf: () -> Unit,
    val onCreateNote: () -> Unit,
    val onRequestDeleteNote: (NoteEntity) -> Unit,
    val onConfirmDeleteNote: (String) -> Unit,
    val onDismissDeleteNote: () -> Unit,
    val onNavigateToEditor: (String, String?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun HomeScreen(onNavigateToEditor: (String, String?) -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val appContainer = appContext.requireAppContainer()
    val repository = appContainer.noteRepository
    val pdfPasswordStore = appContainer.pdfPasswordStore
    val pdfAssetStorage = remember { PdfAssetStorage(appContext) }
    val pdfDocumentInfoReader = remember { PdfiumDocumentInfoReader(appContext) }
    val viewModel: HomeScreenViewModel =
        viewModel(
            key = "HomeScreenViewModel",
            factory =
                HomeScreenViewModelFactory(
                    repository = repository,
                    pdfAssetStorage = pdfAssetStorage,
                    pdfDocumentInfoReader = pdfDocumentInfoReader,
                    pdfPasswordStore = pdfPasswordStore,
                ),
        )
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val notes by viewModel.notes.collectAsState()
    var notePendingDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var passwordPrompt by remember { mutableStateOf<PdfPasswordPromptState?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val beginPdfImport: (Uri, String?) -> Unit = { uri, password ->
        viewModel.importPdf(
            uri = uri,
            password = password,
            callbacks =
                PdfImportCallbacks(
                    onPasswordRequired = { requiredUri ->
                        passwordPrompt = PdfPasswordPromptState(uri = requiredUri)
                    },
                    onWarning = { message -> warningMessage = message },
                    onError = { message -> errorMessage = message },
                    onNavigateToEditor = onNavigateToEditor,
                ),
        )
    }
    val openPdfLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                beginPdfImport(uri, null)
            }
        }

    val uiState =
        HomeScreenState(
            searchQuery = searchQuery,
            searchResults = searchResults,
            notes = notes,
            notePendingDelete = notePendingDelete,
            passwordPrompt = passwordPrompt,
            warningMessage = warningMessage,
            errorMessage = errorMessage,
        )
    val actions =
        HomeScreenActions(
            onDismissWarning = { warningMessage = null },
            onDismissError = { errorMessage = null },
            onDismissPasswordPrompt = { passwordPrompt = null },
            onPasswordChange = { updatedPassword ->
                passwordPrompt =
                    passwordPrompt?.copy(
                        password = updatedPassword,
                    )
            },
            onConfirmPasswordPrompt = {
                passwordPrompt?.let { currentPrompt ->
                    passwordPrompt = null
                    beginPdfImport(currentPrompt.uri, currentPrompt.password)
                }
            },
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onImportPdf = { openPdfLauncher.launch(arrayOf(PDF_MIME_TYPE)) },
            onCreateNote = {
                viewModel.createNote(
                    onNavigateToEditor = onNavigateToEditor,
                    onError = { message -> errorMessage = message },
                )
            },
            onRequestDeleteNote = { note -> notePendingDelete = note },
            onConfirmDeleteNote = { noteId ->
                notePendingDelete = null
                viewModel.deleteNote(
                    noteId = noteId,
                    onError = { message -> errorMessage = message },
                )
            },
            onDismissDeleteNote = { notePendingDelete = null },
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
    HomeWarningDialog(
        warningMessage = state.warningMessage,
        onDismiss = actions.onDismissWarning,
    )
    HomeImportErrorDialog(
        errorMessage = state.errorMessage,
        onDismiss = actions.onDismissError,
    )
    HomePasswordPromptDialog(
        prompt = state.passwordPrompt,
        onDismiss = actions.onDismissPasswordPrompt,
        onPasswordChange = actions.onPasswordChange,
        onConfirm = actions.onConfirmPasswordPrompt,
    )
    HomeDeleteDialog(
        notePendingDelete = state.notePendingDelete,
        onDismiss = actions.onDismissDeleteNote,
        onConfirmDeleteNote = actions.onConfirmDeleteNote,
    )
}

@Composable
private fun HomeWarningDialog(
    warningMessage: String?,
    onDismiss: () -> Unit,
) {
    if (warningMessage == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Large PDF") },
        text = { Text(text = warningMessage) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = "OK")
            }
        },
    )
}

@Composable
private fun HomeImportErrorDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    if (errorMessage == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Import failed") },
        text = { Text(text = errorMessage) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = "OK")
            }
        },
    )
}

@Composable
private fun HomePasswordPromptDialog(
    prompt: PdfPasswordPromptState?,
    onDismiss: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    if (prompt == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "PDF password required") },
        text = {
            OutlinedTextField(
                value = prompt.password,
                onValueChange = onPasswordChange,
                label = { Text(text = "Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Open")
            }
        },
    )
}

@Composable
private fun HomeDeleteDialog(
    notePendingDelete: NoteEntity?,
    onDismiss: () -> Unit,
    onConfirmDeleteNote: (String) -> Unit,
) {
    if (notePendingDelete == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete note") },
        text = {
            val title = notePendingDelete.title.ifBlank { "Untitled Note" }
            Text(text = "Delete \"$title\"? This removes it from your note list.")
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmDeleteNote(notePendingDelete.noteId) }) {
                Text(text = "Delete")
            }
        },
    )
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
            onRequestDeleteNote = actions.onRequestDeleteNote,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeListContent(
    state: HomeScreenState,
    onNavigateToEditor: (String, String?) -> Unit,
    onRequestDeleteNote: (NoteEntity) -> Unit,
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
                onRequestDeleteNote = onRequestDeleteNote,
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
internal fun NotesListContent(
    notes: List<NoteEntity>,
    onNavigateToEditor: (String, String?) -> Unit,
    onRequestDeleteNote: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeContextMenuNoteId by remember { mutableStateOf<String?>(null) }
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
                onLongPress = { activeContextMenuNoteId = note.noteId },
                isContextMenuExpanded = activeContextMenuNoteId == note.noteId,
                onDismissContextMenu = { activeContextMenuNoteId = null },
                onDelete = {
                    activeContextMenuNoteId = null
                    onRequestDeleteNote(note)
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
private fun NoteRow(
    note: NoteEntity,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    isContextMenuExpanded: Boolean,
    onDismissContextMenu: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongPress,
                    ),
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
        DropdownMenu(
            expanded = isContextMenuExpanded,
            onDismissRequest = onDismissContextMenu,
        ) {
            DropdownMenuItem(
                text = { Text("Delete note") },
                onClick = onDelete,
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
    private val pdfDocumentInfoReader: PdfDocumentInfoReader,
    private val pdfPasswordStore: PdfPasswordStore,
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

    fun deleteNote(
        noteId: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.deleteNote(noteId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Delete note failed", throwable)
                onError("Unable to delete note. Please try again.")
            }
        }
    }

    fun importPdf(
        uri: Uri,
        password: String?,
        callbacks: PdfImportCallbacks,
    ) {
        viewModelScope.launch {
            runCatching {
                importPdfInternal(
                    uri = uri,
                    password = password,
                )
            }.onSuccess { (noteId, warningMessage) ->
                if (warningMessage != null) {
                    callbacks.onWarning(warningMessage)
                }
                callbacks.onNavigateToEditor(noteId, null)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (throwable is PdfPasswordRequiredException) {
                    callbacks.onPasswordRequired(uri)
                    return@onFailure
                }
                if (throwable is PdfIncorrectPasswordException) {
                    callbacks.onError("Incorrect PDF password. Please try again.")
                    return@onFailure
                }
                Log.e(HOME_LOG_TAG, "PDF import failed", throwable)
                callbacks.onError("PDF import failed. Please try again.")
            }
        }
    }

    private suspend fun importPdfInternal(
        uri: Uri,
        password: String?,
    ): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            val pdfAssetId = pdfAssetStorage.importPdf(uri)
            val pdfFile = pdfAssetStorage.getFileForAsset(pdfAssetId)
            val documentInfo =
                runCatching {
                    pdfDocumentInfoReader.read(pdfFile, password)
                }.getOrElse { error ->
                    pdfAssetStorage.deleteAsset(pdfAssetId)
                    throw error
                }
            val warning = buildPdfWarning(documentInfo.pageCount, pdfFile.length())
            val noteWithPage = repository.createNote()
            runCatching {
                repository.deletePage(noteWithPage.firstPageId)
                createPagesFromPdf(
                    noteId = noteWithPage.note.noteId,
                    pdfAssetId = pdfAssetId,
                    pages = documentInfo.pages,
                )
            }.getOrElse { error ->
                pdfAssetStorage.deleteAsset(pdfAssetId)
                repository.deleteNote(noteWithPage.note.noteId)
                throw error
            }
            pdfPasswordStore.rememberPassword(pdfAssetId, password)
            noteWithPage.note.noteId to warning
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
        noteId: String,
        pdfAssetId: String,
        pages: List<PdfPageInfo>,
    ) {
        pages.forEachIndexed { pageIndex, pageInfo ->
            repository.createPageFromPdf(
                noteId = noteId,
                indexInNote = pageIndex,
                pdfAssetId = pdfAssetId,
                pdfPageNo = pageIndex,
                pdfWidth = pageInfo.widthPoints,
                pdfHeight = pageInfo.heightPoints,
            )
        }
    }
}

private class HomeScreenViewModelFactory(
    private val repository: NoteRepository,
    private val pdfAssetStorage: PdfAssetStorage,
    private val pdfDocumentInfoReader: PdfDocumentInfoReader,
    private val pdfPasswordStore: PdfPasswordStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeScreenViewModel::class.java))
        return HomeScreenViewModel(
            repository = repository,
            pdfAssetStorage = pdfAssetStorage,
            pdfDocumentInfoReader = pdfDocumentInfoReader,
            pdfPasswordStore = pdfPasswordStore,
        ) as T
    }
}
