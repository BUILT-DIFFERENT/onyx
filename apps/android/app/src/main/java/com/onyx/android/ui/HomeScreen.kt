@file:Suppress(
    "FunctionName",
    "TooManyFunctions",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "MaxLineLength",
)

package com.onyx.android.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.android.data.entity.FolderEntity
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.TagEntity
import com.onyx.android.data.repository.DateRange
import com.onyx.android.data.repository.DuplicateTagNameException
import com.onyx.android.data.repository.FilterState
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.repository.SearchResultItem
import com.onyx.android.data.repository.SortDirection
import com.onyx.android.data.repository.SortOption
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
import kotlinx.coroutines.flow.combine
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

/**
 * Predefined color palette for tags.
 */
object TagColors {
    val PALETTE =
        listOf(
            "#EF5350",
            "#FF7043",
            "#FFA726",
            "#FFCA28",
            "#66BB6A",
            "#26A69A",
            "#42A5F5",
            "#7E57C2",
            "#EC407A",
            "#78909C",
        )

    val DEFAULT_COLOR: String get() = PALETTE.first()

    fun parseColor(hex: String): Color {
        return try {
            val hexValue = hex.removePrefix("#").toLong(16)
            Color(hexValue)
        } catch (e: Exception) {
            Color.Gray
        }
    }
}

private data class PdfPasswordPromptState(
    val uri: Uri,
    val password: String = "",
)

private data class HomeScreenState(
    val searchQuery: String,
    val searchResults: List<SearchResultItem>,
    val notes: List<NoteEntity>,
    val folders: List<FolderEntity>,
    val currentFolder: FolderEntity?,
    val tags: List<TagEntity>,
    val selectedTagFilter: TagEntity?,
    val noteTags: Map<String, List<TagEntity>>,
    val notePendingDelete: NoteEntity?,
    val folderPendingDelete: FolderEntity?,
    val tagPendingDelete: TagEntity?,
    val passwordPrompt: PdfPasswordPromptState?,
    val warningMessage: String?,
    val errorMessage: String?,
    val showCreateFolderDialog: Boolean,
    val showCreateTagDialog: Boolean,
    val showManageTagsDialog: NoteEntity?,
    val showMoveNoteDialog: NoteEntity?,
    val selectionMode: Boolean = false,
    val selectedNoteIds: Set<String> = emptySet(),
    val showBatchDeleteDialog: Boolean = false,
    val showBatchMoveDialog: Boolean = false,
    val showBatchAddTagDialog: Boolean = false,
    // Sort/Filter state
    val sortOption: SortOption = SortOption.MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESC,
    val filterState: FilterState = FilterState(),
    val showSortFilterDropdown: Boolean = false,
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
    val onSelectFolder: (FolderEntity?) -> Unit,
    val onShowCreateFolderDialog: () -> Unit,
    val onDismissCreateFolderDialog: () -> Unit,
    val onCreateFolder: (String) -> Unit,
    val onRequestDeleteFolder: (FolderEntity) -> Unit,
    val onConfirmDeleteFolder: (String) -> Unit,
    val onDismissDeleteFolder: () -> Unit,
    val onShowMoveNoteDialog: (NoteEntity) -> Unit,
    val onDismissMoveNoteDialog: () -> Unit,
    val onMoveNoteToFolder: (String, String?) -> Unit,
    val onShowCreateTagDialog: () -> Unit,
    val onDismissCreateTagDialog: () -> Unit,
    val onCreateTag: (String, String) -> Unit,
    val onRequestDeleteTag: (TagEntity) -> Unit,
    val onConfirmDeleteTag: (String) -> Unit,
    val onDismissDeleteTag: () -> Unit,
    val onSelectTagFilter: (TagEntity?) -> Unit,
    val onShowManageTagsDialog: (NoteEntity) -> Unit,
    val onDismissManageTagsDialog: () -> Unit,
    val onAddTagToNote: (String, String) -> Unit,
    val onRemoveTagFromNote: (String, String) -> Unit,
    val onEnterSelectionMode: (String) -> Unit,
    val onExitSelectionMode: () -> Unit,
    val onToggleNoteSelection: (String) -> Unit,
    val onSelectAllNotes: () -> Unit,
    val onDeselectAllNotes: () -> Unit,
    val onShowBatchDeleteDialog: () -> Unit,
    val onDismissBatchDeleteDialog: () -> Unit,
    val onConfirmBatchDelete: () -> Unit,
    val onShowBatchMoveDialog: () -> Unit,
    val onDismissBatchMoveDialog: () -> Unit,
    val onConfirmBatchMove: (String?) -> Unit,
    val onShowBatchAddTagDialog: () -> Unit,
    val onDismissBatchAddTagDialog: () -> Unit,
    val onConfirmBatchAddTag: (String) -> Unit,
    // Sort/Filter actions
    val onToggleSortFilterDropdown: () -> Unit,
    val onDismissSortFilterDropdown: () -> Unit,
    val onSortOptionChange: (SortOption) -> Unit,
    val onSortDirectionChange: (SortDirection) -> Unit,
    val onDateRangeFilterChange: (DateRange?) -> Unit,
    val onClearFilters: () -> Unit,
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
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val selectedTagFilter by viewModel.selectedTagFilter.collectAsState()
    val noteTags by viewModel.noteTags.collectAsState()
    var notePendingDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var folderPendingDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var tagPendingDelete by remember { mutableStateOf<TagEntity?>(null) }
    var passwordPrompt by remember { mutableStateOf<PdfPasswordPromptState?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf<NoteEntity?>(null) }
    var showMoveNoteDialog by remember { mutableStateOf<NoteEntity?>(null) }
    // Multi-select state
    var selectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showBatchAddTagDialog by remember { mutableStateOf(false) }
    // Sort/Filter state
    var sortOption by remember { mutableStateOf(SortOption.MODIFIED) }
    var sortDirection by remember { mutableStateOf(SortDirection.DESC) }
    var filterState by remember { mutableStateOf(FilterState()) }
    var showSortFilterDropdown by remember { mutableStateOf(false) }
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
            folders = folders,
            currentFolder = currentFolder,
            tags = tags,
            selectedTagFilter = selectedTagFilter,
            noteTags = noteTags,
            notePendingDelete = notePendingDelete,
            folderPendingDelete = folderPendingDelete,
            tagPendingDelete = tagPendingDelete,
            passwordPrompt = passwordPrompt,
            warningMessage = warningMessage,
            errorMessage = errorMessage,
            showCreateFolderDialog = showCreateFolderDialog,
            showCreateTagDialog = showCreateTagDialog,
            showManageTagsDialog = showManageTagsDialog,
            showMoveNoteDialog = showMoveNoteDialog,
            selectionMode = selectionMode,
            selectedNoteIds = selectedNoteIds,
            showBatchDeleteDialog = showBatchDeleteDialog,
            showBatchMoveDialog = showBatchMoveDialog,
            showBatchAddTagDialog = showBatchAddTagDialog,
            sortOption = sortOption,
            sortDirection = sortDirection,
            filterState = filterState,
            showSortFilterDropdown = showSortFilterDropdown,
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
                    currentFolderId = currentFolder?.folderId,
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
            onSelectFolder = { folder -> viewModel.selectFolder(folder) },
            onShowCreateFolderDialog = { showCreateFolderDialog = true },
            onDismissCreateFolderDialog = { showCreateFolderDialog = false },
            onCreateFolder = { name ->
                showCreateFolderDialog = false
                viewModel.createFolder(
                    name = name,
                    onError = { message -> errorMessage = message },
                )
            },
            onRequestDeleteFolder = { folder -> folderPendingDelete = folder },
            onConfirmDeleteFolder = { folderId ->
                folderPendingDelete = null
                viewModel.deleteFolder(
                    folderId = folderId,
                    onError = { message -> errorMessage = message },
                )
            },
            onDismissDeleteFolder = { folderPendingDelete = null },
            onShowMoveNoteDialog = { note -> showMoveNoteDialog = note },
            onDismissMoveNoteDialog = { showMoveNoteDialog = null },
            onMoveNoteToFolder = { noteId, folderId ->
                showMoveNoteDialog = null
                viewModel.moveNoteToFolder(
                    noteId = noteId,
                    folderId = folderId,
                    onError = { message -> errorMessage = message },
                )
            },
            onShowCreateTagDialog = { showCreateTagDialog = true },
            onDismissCreateTagDialog = { showCreateTagDialog = false },
            onCreateTag = { name, color ->
                showCreateTagDialog = false
                viewModel.createTag(
                    name = name,
                    color = color,
                    onError = { message -> errorMessage = message },
                )
            },
            onRequestDeleteTag = { tag -> tagPendingDelete = tag },
            onConfirmDeleteTag = { tagId ->
                tagPendingDelete = null
                viewModel.deleteTag(
                    tagId = tagId,
                    onError = { message -> errorMessage = message },
                )
            },
            onDismissDeleteTag = { tagPendingDelete = null },
            onSelectTagFilter = { tag -> viewModel.selectTagFilter(tag) },
            onShowManageTagsDialog = { note -> showManageTagsDialog = note },
            onDismissManageTagsDialog = { showManageTagsDialog = null },
            onAddTagToNote = { noteId, tagId ->
                viewModel.addTagToNote(
                    noteId = noteId,
                    tagId = tagId,
                    onError = { message -> errorMessage = message },
                )
            },
            onRemoveTagFromNote = { noteId, tagId ->
                viewModel.removeTagFromNote(
                    noteId = noteId,
                    tagId = tagId,
                    onError = { message -> errorMessage = message },
                )
            },
            onEnterSelectionMode = { noteId ->
                selectionMode = true
                selectedNoteIds = setOf(noteId)
            },
            onExitSelectionMode = {
                selectionMode = false
                selectedNoteIds = emptySet()
            },
            onToggleNoteSelection = { noteId ->
                selectedNoteIds =
                    if (noteId in selectedNoteIds) {
                        selectedNoteIds - noteId
                    } else {
                        selectedNoteIds + noteId
                    }
                if (selectedNoteIds.isEmpty()) {
                    selectionMode = false
                }
            },
            onSelectAllNotes = {
                selectedNoteIds = notes.map { it.noteId }.toSet()
            },
            onDeselectAllNotes = {
                selectedNoteIds = emptySet()
                selectionMode = false
            },
            onShowBatchDeleteDialog = { showBatchDeleteDialog = true },
            onDismissBatchDeleteDialog = { showBatchDeleteDialog = false },
            onConfirmBatchDelete = {
                showBatchDeleteDialog = false
                viewModel.deleteNotes(
                    noteIds = selectedNoteIds,
                    onError = { message -> errorMessage = message },
                )
                selectionMode = false
                selectedNoteIds = emptySet()
            },
            onShowBatchMoveDialog = { showBatchMoveDialog = true },
            onDismissBatchMoveDialog = { showBatchMoveDialog = false },
            onConfirmBatchMove = { folderId ->
                showBatchMoveDialog = false
                viewModel.moveNotesToFolder(
                    noteIds = selectedNoteIds,
                    folderId = folderId,
                    onError = { message -> errorMessage = message },
                )
                selectionMode = false
                selectedNoteIds = emptySet()
            },
            onShowBatchAddTagDialog = { showBatchAddTagDialog = true },
            onDismissBatchAddTagDialog = { showBatchAddTagDialog = false },
            onConfirmBatchAddTag = { tagId ->
                showBatchAddTagDialog = false
                viewModel.addTagToNotes(
                    noteIds = selectedNoteIds,
                    tagId = tagId,
                    onError = { message -> errorMessage = message },
                )
                selectionMode = false
                selectedNoteIds = emptySet()
            },
            // Sort/Filter actions
            onToggleSortFilterDropdown = { showSortFilterDropdown = !showSortFilterDropdown },
            onDismissSortFilterDropdown = { showSortFilterDropdown = false },
            onSortOptionChange = { newSortOption ->
                sortOption = newSortOption
                viewModel.setSortOption(newSortOption)
            },
            onSortDirectionChange = { newSortDirection ->
                sortDirection = newSortDirection
                viewModel.setSortDirection(newSortDirection)
            },
            onDateRangeFilterChange = { dateRange ->
                filterState = filterState.copy(dateRange = dateRange)
                viewModel.setDateRangeFilter(dateRange)
            },
            onClearFilters = {
                filterState = FilterState()
                viewModel.clearFilters()
            },
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
            if (state.selectionMode) {
                SelectionModeTopBar(
                    selectedCount = state.selectedNoteIds.size,
                    totalCount = state.notes.size,
                    onExitSelectionMode = actions.onExitSelectionMode,
                    onSelectAll = actions.onSelectAllNotes,
                    onDeselectAll = actions.onDeselectAllNotes,
                    onDelete = actions.onShowBatchDeleteDialog,
                    onMove = actions.onShowBatchMoveDialog,
                    onAddTag = actions.onShowBatchAddTagDialog,
                )
            } else {
                TopAppBar(
                    title = { Text(text = "Notes") },
                    actions = {
                        // Sort/Filter dropdown
                        Box {
                            IconButton(onClick = actions.onToggleSortFilterDropdown) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort and filter",
                                )
                            }
                            SortFilterDropdown(
                                expanded = state.showSortFilterDropdown,
                                onDismiss = actions.onDismissSortFilterDropdown,
                                sortOption = state.sortOption,
                                sortDirection = state.sortDirection,
                                dateRange = state.filterState.dateRange,
                                onSortOptionChange = actions.onSortOptionChange,
                                onSortDirectionChange = actions.onSortDirectionChange,
                                onDateRangeFilterChange = actions.onDateRangeFilterChange,
                                onClearFilters = actions.onClearFilters,
                                hasActiveFilters = state.filterState.dateRange != null,
                            )
                        }
                        IconButton(onClick = actions.onImportPdf) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Import PDF",
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!state.selectionMode) {
                FloatingActionButton(onClick = actions.onCreateNote) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New note")
                }
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
    HomeDeleteFolderDialog(
        folderPendingDelete = state.folderPendingDelete,
        onDismiss = actions.onDismissDeleteFolder,
        onConfirmDeleteFolder = actions.onConfirmDeleteFolder,
    )
    HomeCreateFolderDialog(
        show = state.showCreateFolderDialog,
        onDismiss = actions.onDismissCreateFolderDialog,
        onCreateFolder = actions.onCreateFolder,
    )
    HomeMoveNoteDialog(
        note = state.showMoveNoteDialog,
        folders = state.folders,
        onDismiss = actions.onDismissMoveNoteDialog,
        onMoveToFolder = actions.onMoveNoteToFolder,
    )
    HomeCreateTagDialog(
        show = state.showCreateTagDialog,
        onDismiss = actions.onDismissCreateTagDialog,
        onCreateTag = actions.onCreateTag,
    )
    HomeDeleteTagDialog(
        tagPendingDelete = state.tagPendingDelete,
        onDismiss = actions.onDismissDeleteTag,
        onConfirmDeleteTag = actions.onConfirmDeleteTag,
    )
    HomeManageTagsDialog(
        note = state.showManageTagsDialog,
        tags = state.tags,
        noteTags = state.showManageTagsDialog?.let { state.noteTags[it.noteId] } ?: emptyList(),
        onDismiss = actions.onDismissManageTagsDialog,
        onAddTagToNote = actions.onAddTagToNote,
        onRemoveTagFromNote = actions.onRemoveTagFromNote,
    )
    // Batch operation dialogs
    HomeBatchDeleteDialog(
        show = state.showBatchDeleteDialog,
        selectedCount = state.selectedNoteIds.size,
        onDismiss = actions.onDismissBatchDeleteDialog,
        onConfirm = actions.onConfirmBatchDelete,
    )
    HomeBatchMoveDialog(
        show = state.showBatchMoveDialog,
        folders = state.folders,
        selectedCount = state.selectedNoteIds.size,
        onDismiss = actions.onDismissBatchMoveDialog,
        onMoveToFolder = actions.onConfirmBatchMove,
    )
    HomeBatchAddTagDialog(
        show = state.showBatchAddTagDialog,
        tags = state.tags,
        selectedCount = state.selectedNoteIds.size,
        onDismiss = actions.onDismissBatchAddTagDialog,
        onAddTag = actions.onConfirmBatchAddTag,
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
private fun HomeDeleteFolderDialog(
    folderPendingDelete: FolderEntity?,
    onDismiss: () -> Unit,
    onConfirmDeleteFolder: (String) -> Unit,
) {
    if (folderPendingDelete == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete folder") },
        text = {
            Text(text = "Delete folder \"${folderPendingDelete.name}\"? Notes in this folder will be moved to root.")
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmDeleteFolder(folderPendingDelete.folderId) }) {
                Text(text = "Delete")
            }
        },
    )
}

@Composable
private fun HomeCreateFolderDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    if (!show) {
        return
    }
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Create folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(text = "Folder name") },
                singleLine = true,
            )
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onCreateFolder(folderName)
                    }
                },
            ) {
                Text(text = "Create")
            }
        },
    )
}

@Composable
private fun HomeMoveNoteDialog(
    note: NoteEntity?,
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onMoveToFolder: (String, String?) -> Unit,
) {
    if (note == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Move note") },
        text = {
            Column {
                Text(
                    text = "Move \"${note.title.ifEmpty { "Untitled Note" }}\" to:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (folders.isEmpty()) {
                    Text(
                        text = "No folders available. Create a folder first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        dismissButton = {
            // Move to root option
            Button(
                onClick = {
                    onMoveToFolder(note.noteId, null)
                },
            ) {
                Text(text = "Root")
            }
        },
    )
    // Show folder options as additional dialog content
    if (folders.isNotEmpty()) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
        ) {
            folders.forEach { folder ->
                Button(
                    onClick = {
                        onMoveToFolder(note.noteId, folder.folderId)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text(text = folder.name)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeCreateTagDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreateTag: (String, String) -> Unit,
) {
    if (!show) {
        return
    }
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(TagColors.DEFAULT_COLOR) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Create tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text(text = "Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagColors.PALETTE.forEach { color ->
                        val isSelected = color == selectedColor
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(TagColors.parseColor(color))
                                    .clickable { selectedColor = color }
                                    .then(
                                        if (isSelected) {
                                            Modifier.padding(3.dp)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface),
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tagName.isNotBlank()) {
                        onCreateTag(tagName, selectedColor)
                    }
                },
            ) {
                Text(text = "Create")
            }
        },
    )
}

@Composable
private fun HomeDeleteTagDialog(
    tagPendingDelete: TagEntity?,
    onDismiss: () -> Unit,
    onConfirmDeleteTag: (String) -> Unit,
) {
    if (tagPendingDelete == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete tag") },
        text = {
            Text(text = "Delete tag \"${tagPendingDelete.name}\"? This will remove it from all notes.")
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmDeleteTag(tagPendingDelete.tagId) }) {
                Text(text = "Delete")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeManageTagsDialog(
    note: NoteEntity?,
    tags: List<TagEntity>,
    noteTags: List<TagEntity>,
    onDismiss: () -> Unit,
    onAddTagToNote: (String, String) -> Unit,
    onRemoveTagFromNote: (String, String) -> Unit,
) {
    if (note == null) {
        return
    }
    val noteId = note.noteId
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Manage tags") },
        text = {
            Column {
                Text(
                    text = "Tags for \"${note.title.ifEmpty { "Untitled Note" }}\"",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (tags.isEmpty()) {
                    Text(
                        text = "No tags available. Create a tag first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            val isAssigned = noteTags.any { it.tagId == tag.tagId }
                            TagChip(
                                tag = tag,
                                isSelected = isAssigned,
                                onClick = {
                                    if (isAssigned) {
                                        onRemoveTagFromNote(noteId, tag.tagId)
                                    } else {
                                        onAddTagToNote(noteId, tag.tagId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = "Done")
            }
        },
    )
}

@Composable
private fun TagChip(
    tag: TagEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isSelected) {
            TagColors.parseColor(tag.color)
        } else {
            TagColors.parseColor(tag.color).copy(alpha = 0.2f)
        }
    val contentColor =
        if (isSelected) {
            Color.White
        } else {
            TagColors.parseColor(tag.color)
        }
    Surface(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(contentColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tag.name,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

// Batch operation dialogs

@Composable
private fun HomeBatchDeleteDialog(
    show: Boolean,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete notes") },
        text = {
            Text(text = "Delete $selectedCount note${if (selectedCount != 1) "s" else ""}? This will remove them from your note list.")
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Delete")
            }
        },
    )
}

@Composable
private fun HomeBatchMoveDialog(
    show: Boolean,
    folders: List<FolderEntity>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onMoveToFolder: (String?) -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Move notes") },
        text = {
            Column {
                Text(
                    text = "Move $selectedCount note${if (selectedCount != 1) "s" else ""} to:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (folders.isEmpty()) {
                    Text(
                        text = "No folders available. Create a folder first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        dismissButton = {
            Button(onClick = { onMoveToFolder(null) }) {
                Text(text = "Root")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeBatchAddTagDialog(
    show: Boolean,
    tags: List<TagEntity>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onAddTag: (String) -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add tag to notes") },
        text = {
            Column {
                Text(
                    text = "Add a tag to $selectedCount note${if (selectedCount != 1) "s" else ""}:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (tags.isEmpty()) {
                    Text(
                        text = "No tags available. Create a tag first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            TagChip(
                                tag = tag,
                                isSelected = false,
                                onClick = { onAddTag(tag.tagId) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

// Selection mode top bar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionModeTopBar(
    selectedCount: Int,
    totalCount: Int,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onAddTag: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = "$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onExitSelectionMode) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection mode",
                )
            }
        },
        actions = {
            if (selectedCount == totalCount) {
                IconButton(onClick = onDeselectAll) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Deselect all",
                    )
                }
            } else {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Select all",
                    )
                }
            }
            IconButton(onClick = onAddTag) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Label,
                    contentDescription = "Add tag",
                )
            }
            IconButton(onClick = onMove) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Move to folder",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                )
            }
        },
    )
}

// Sort/Filter dropdown

@Composable
private fun SortFilterDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sortOption: SortOption,
    sortDirection: SortDirection,
    dateRange: DateRange?,
    onSortOptionChange: (SortOption) -> Unit,
    onSortDirectionChange: (SortDirection) -> Unit,
    onDateRangeFilterChange: (DateRange?) -> Unit,
    onClearFilters: () -> Unit,
    hasActiveFilters: Boolean,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        // Sort section header
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Sort options
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text =
                                when (option) {
                                    SortOption.NAME -> "Name"
                                    SortOption.CREATED -> "Created"
                                    SortOption.MODIFIED -> "Modified"
                                },
                        )
                        if (sortOption == option) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector =
                                    if (sortDirection == SortDirection.ASC) {
                                        Icons.Default.ArrowUpward
                                    } else {
                                        Icons.Default.ArrowDownward
                                    },
                                contentDescription =
                                    if (sortDirection == SortDirection.ASC) {
                                        "Ascending"
                                    } else {
                                        "Descending"
                                    },
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                onClick = {
                    if (sortOption == option) {
                        // Toggle direction if same option
                        onSortDirectionChange(
                            if (sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC,
                        )
                    } else {
                        onSortOptionChange(option)
                    }
                },
            )
        }

        // Divider
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Filter section header
        Text(
            text = "Filter by date",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Date range options
        DateRange.entries.forEach { range ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text =
                                when (range) {
                                    DateRange.TODAY -> "Today"
                                    DateRange.THIS_WEEK -> "This week"
                                    DateRange.THIS_MONTH -> "This month"
                                    DateRange.THIS_YEAR -> "This year"
                                    DateRange.OLDER -> "Older than a year"
                                },
                        )
                        if (dateRange == range) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Selected",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                onClick = {
                    if (dateRange == range) {
                        onDateRangeFilterChange(null)
                    } else {
                        onDateRangeFilterChange(range)
                    }
                },
            )
        }

        // Clear filters option
        if (hasActiveFilters) {
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Clear filters",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    onClearFilters()
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

        // Show folder chips when at root level
        if (state.currentFolder == null && state.searchQuery.length < MIN_SEARCH_QUERY_LENGTH) {
            FolderSection(
                folders = state.folders,
                onSelectFolder = actions.onSelectFolder,
                onCreateFolder = actions.onShowCreateFolderDialog,
                onDeleteFolder = actions.onRequestDeleteFolder,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Show current folder breadcrumb when inside a folder
        if (state.currentFolder != null) {
            FolderBreadcrumb(
                folder = state.currentFolder,
                onNavigateToRoot = { actions.onSelectFolder(null) },
                onDeleteFolder = { actions.onRequestDeleteFolder(state.currentFolder) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Tag filter section
        if (state.tags.isNotEmpty() && state.searchQuery.length < MIN_SEARCH_QUERY_LENGTH) {
            TagFilterSection(
                tags = state.tags,
                selectedTag = state.selectedTagFilter,
                onSelectTag = actions.onSelectTagFilter,
                onCreateTag = actions.onShowCreateTagDialog,
                onDeleteTag = actions.onRequestDeleteTag,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.tags.isEmpty() && state.searchQuery.length < MIN_SEARCH_QUERY_LENGTH) {
            // Show "Create tag" button when no tags exist
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Button(onClick = actions.onShowCreateTagDialog) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(text = "New Tag")
                }
            }
        }

        HomeListContent(
            state = state,
            onNavigateToEditor = actions.onNavigateToEditor,
            onRequestDeleteNote = actions.onRequestDeleteNote,
            onMoveNote = actions.onShowMoveNoteDialog,
            onManageTags = actions.onShowManageTagsDialog,
            onEnterSelectionMode = actions.onEnterSelectionMode,
            onToggleNoteSelection = actions.onToggleNoteSelection,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeListContent(
    state: HomeScreenState,
    onNavigateToEditor: (String, String?) -> Unit,
    onRequestDeleteNote: (NoteEntity) -> Unit,
    onMoveNote: (NoteEntity) -> Unit,
    onManageTags: (NoteEntity) -> Unit,
    onEnterSelectionMode: (String) -> Unit,
    onToggleNoteSelection: (String) -> Unit,
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
                noteTags = state.noteTags,
                selectionMode = state.selectionMode,
                selectedNoteIds = state.selectedNoteIds,
                onNavigateToEditor = onNavigateToEditor,
                onRequestDeleteNote = onRequestDeleteNote,
                onMoveNote = onMoveNote,
                onManageTags = onManageTags,
                onEnterSelectionMode = onEnterSelectionMode,
                onToggleNoteSelection = onToggleNoteSelection,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotesListContent(
    notes: List<NoteEntity>,
    noteTags: Map<String, List<TagEntity>>,
    selectionMode: Boolean,
    selectedNoteIds: Set<String>,
    onNavigateToEditor: (String, String?) -> Unit,
    onRequestDeleteNote: (NoteEntity) -> Unit,
    onMoveNote: (NoteEntity) -> Unit,
    onManageTags: (NoteEntity) -> Unit,
    onEnterSelectionMode: (String) -> Unit,
    onToggleNoteSelection: (String) -> Unit,
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
                tags = noteTags[note.noteId] ?: emptyList(),
                selectionMode = selectionMode,
                isSelected = note.noteId in selectedNoteIds,
                onClick = {
                    if (selectionMode) {
                        onToggleNoteSelection(note.noteId)
                    } else {
                        onNavigateToEditor(note.noteId, null)
                    }
                },
                onLongPress = {
                    if (!selectionMode) {
                        onEnterSelectionMode(note.noteId)
                    }
                },
                isContextMenuExpanded = activeContextMenuNoteId == note.noteId,
                onDismissContextMenu = { activeContextMenuNoteId = null },
                onDelete = {
                    activeContextMenuNoteId = null
                    onRequestDeleteNote(note)
                },
                onMove = {
                    activeContextMenuNoteId = null
                    onMoveNote(note)
                },
                onManageTags = {
                    activeContextMenuNoteId = null
                    onManageTags(note)
                },
                onToggleSelection = { onToggleNoteSelection(note.noteId) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList")
private fun NoteRow(
    note: NoteEntity,
    tags: List<TagEntity>,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    isContextMenuExpanded: Boolean,
    onDismissContextMenu: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onManageTags: () -> Unit,
    onToggleSelection: () -> Unit,
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
                        onLongClick = if (selectionMode) null else onLongPress,
                    ),
            tonalElevation = if (isSelected) 2.dp else 1.dp,
            shape = MaterialTheme.shapes.medium,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Checkbox in selection mode
                if (selectionMode) {
                    androidx.compose.material3.Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                val updatedLabel = remember(note.updatedAt) { formatTimestamp(note.updatedAt) }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(16.dp),
                ) {
                    Text(
                        text = note.title.ifEmpty { "Untitled Note" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Updated $updatedLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Display tags on note card
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tags.forEach { tag ->
                                TagChip(
                                    tag = tag,
                                    isSelected = true,
                                    onClick = { /* no-op for display */ },
                                )
                            }
                        }
                    }
                }
            }
        }
        // Context menu only shown when not in selection mode
        if (!selectionMode) {
            DropdownMenu(
                expanded = isContextMenuExpanded,
                onDismissRequest = onDismissContextMenu,
            ) {
                DropdownMenuItem(
                    text = { Text("Manage tags") },
                    onClick = onManageTags,
                )
                DropdownMenuItem(
                    text = { Text("Move to folder") },
                    onClick = onMove,
                )
                DropdownMenuItem(
                    text = { Text("Delete note") },
                    onClick = onDelete,
                )
            }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderSection(
    folders: List<FolderEntity>,
    onSelectFolder: (FolderEntity) -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Folders",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (folders.isEmpty()) {
            Text(
                text = "No folders. Tap + to create one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(folders, key = { it.folderId }) { folder ->
                    var showContextMenu by remember { mutableStateOf(false) }
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { onSelectFolder(folder) },
                                    onLongClick = { showContextMenu = true },
                                ),
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete folder") },
                            onClick = {
                                showContextMenu = false
                                onDeleteFolder(folder)
                            },
                        )
                    }
                }
            }
        }
        Button(
            onClick = onCreateFolder,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(text = "New Folder")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TagFilterSection(
    tags: List<TagEntity>,
    selectedTag: TagEntity?,
    onSelectTag: (TagEntity?) -> Unit,
    onCreateTag: () -> Unit,
    onDeleteTag: (TagEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                var showContextMenu by remember { mutableStateOf(false) }
                Box(
                    modifier =
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onClick = {
                                if (selectedTag?.tagId == tag.tagId) {
                                    onSelectTag(null)
                                } else {
                                    onSelectTag(tag)
                                }
                            },
                            onLongClick = { showContextMenu = true },
                        ),
                ) {
                    TagChip(
                        tag = tag,
                        isSelected = selectedTag?.tagId == tag.tagId,
                        onClick = {
                            if (selectedTag?.tagId == tag.tagId) {
                                onSelectTag(null)
                            } else {
                                onSelectTag(tag)
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete tag") },
                            onClick = {
                                showContextMenu = false
                                onDeleteTag(tag)
                            },
                        )
                    }
                }
            }
            // Add tag button
            Surface(
                modifier = Modifier.clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier =
                        Modifier
                            .clickable(onClick = onCreateTag)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add tag",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderBreadcrumb(
    folder: FolderEntity,
    onNavigateToRoot: () -> Unit,
    onDeleteFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    Surface(
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .combinedClickable(
                    role = Role.Button,
                    onClick = { /* no-op */ },
                    onLongClick = { showContextMenu = true },
                ),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "All Notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onNavigateToRoot),
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    DropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text("Delete folder") },
            onClick = {
                showContextMenu = false
                onDeleteFolder()
            },
        )
    }
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

    private val _currentFolder = MutableStateFlow<FolderEntity?>(null)
    val currentFolder: StateFlow<FolderEntity?> = _currentFolder.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow<TagEntity?>(null)
    val selectedTagFilter: StateFlow<TagEntity?> = _selectedTagFilter.asStateFlow()

    // Sort/Filter state
    private val _sortOption = MutableStateFlow(SortOption.MODIFIED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.DESC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _dateRangeFilter = MutableStateFlow<DateRange?>(null)
    val dateRangeFilter: StateFlow<DateRange?> = _dateRangeFilter.asStateFlow()

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
        combine(
            _currentFolder,
            _selectedTagFilter,
            _sortOption,
            _sortDirection,
            _dateRangeFilter,
        ) { folder, tag, sort, direction, dateRange ->
            Triple(folder, tag, Pair(sort, direction) to dateRange)
        }.flatMapLatest { (folder, tag, sortAndFilter) ->
            val (sortPair, dateRange) = sortAndFilter
            val (sort, direction) = sortPair

            when {
                // Tag filter takes precedence
                tag != null -> repository.getNotesByTag(tag.tagId)
                // Date range filter
                dateRange != null -> repository.getNotesByDateRange(folder?.folderId, dateRange)
                // Sorted notes
                else -> repository.getNotesInFolderSorted(folder?.folderId, sort, direction)
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        repository
            .getFolders()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tags: StateFlow<List<TagEntity>> =
        repository
            .getTags()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val noteTags: StateFlow<Map<String, List<TagEntity>>> =
        notes.flatMapLatest { noteList ->
            if (noteList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                kotlinx.coroutines.flow.combine(
                    noteList.map { note ->
                        repository.getTagsForNote(note.noteId)
                    },
                ) { tagLists ->
                    noteList.mapIndexed { index, note ->
                        note.noteId to tagLists[index]
                    }.toMap()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folder: FolderEntity?) {
        _currentFolder.value = folder
    }

    fun selectTagFilter(tag: TagEntity?) {
        _selectedTagFilter.value = tag
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setSortDirection(direction: SortDirection) {
        _sortDirection.value = direction
    }

    fun setDateRangeFilter(dateRange: DateRange?) {
        _dateRangeFilter.value = dateRange
    }

    fun clearFilters() {
        _dateRangeFilter.value = null
        _selectedTagFilter.value = null
    }

    fun createNote(
        currentFolderId: String?,
        onNavigateToEditor: (String, String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val noteWithPage = repository.createNote()
                // Move note to current folder if we're in one
                if (currentFolderId != null) {
                    repository.moveNoteToFolder(noteWithPage.note.noteId, currentFolderId)
                }
                noteWithPage
            }.onSuccess { noteWithPage ->
                onNavigateToEditor(noteWithPage.note.noteId, null)
            }.onFailure { throwable ->
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

    fun createFolder(
        name: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.createFolder(name)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Create folder failed", throwable)
                onError("Unable to create folder. Please try again.")
            }
        }
    }

    fun deleteFolder(
        folderId: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.deleteFolder(folderId)
                // If we were in the deleted folder, go back to root
                if (_currentFolder.value?.folderId == folderId) {
                    _currentFolder.value = null
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Delete folder failed", throwable)
                onError("Unable to delete folder. Please try again.")
            }
        }
    }

    fun moveNoteToFolder(
        noteId: String,
        folderId: String?,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.moveNoteToFolder(noteId, folderId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Move note failed", throwable)
                onError("Unable to move note. Please try again.")
            }
        }
    }

    fun createTag(
        name: String,
        color: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.createTag(name, color)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (throwable is DuplicateTagNameException) {
                    onError("A tag with name '$name' already exists.")
                } else {
                    Log.e(HOME_LOG_TAG, "Create tag failed", throwable)
                    onError("Unable to create tag. Please try again.")
                }
            }
        }
    }

    fun deleteTag(
        tagId: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.deleteTag(tagId)
                // Clear filter if we deleted the selected tag
                if (_selectedTagFilter.value?.tagId == tagId) {
                    _selectedTagFilter.value = null
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Delete tag failed", throwable)
                onError("Unable to delete tag. Please try again.")
            }
        }
    }

    fun addTagToNote(
        noteId: String,
        tagId: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addTagToNote(noteId, tagId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Add tag to note failed", throwable)
                onError("Unable to add tag to note. Please try again.")
            }
        }
    }

    fun removeTagFromNote(
        noteId: String,
        tagId: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.removeTagFromNote(noteId, tagId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Remove tag from note failed", throwable)
                onError("Unable to remove tag from note. Please try again.")
            }
        }
    }

    // Batch operations for multi-select
    fun deleteNotes(
        noteIds: Set<String>,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.deleteNotes(noteIds)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Batch delete notes failed", throwable)
                onError("Unable to delete notes. Please try again.")
            }
        }
    }

    fun moveNotesToFolder(
        noteIds: Set<String>,
        folderId: String?,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.moveNotesToFolder(noteIds, folderId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Batch move notes failed", throwable)
                onError("Unable to move notes. Please try again.")
            }
        }
    }

    fun addTagToNotes(
        noteIds: Set<String>,
        tagId: String,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addTagToNotes(noteIds, tagId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(HOME_LOG_TAG, "Batch add tag to notes failed", throwable)
                onError("Unable to add tag to notes. Please try again.")
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
