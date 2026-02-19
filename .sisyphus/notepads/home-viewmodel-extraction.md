# Home Screen ViewModel Extraction Analysis

## Task: 3.3 Home screen architecture cleanup and ViewModel extraction

### Current State

**HomeScreen.kt** (2543 lines) contains:

1. `HomeScreen` composable function
2. `HomeScreenViewModel` private class (inline)
3. `HomeScreenViewModelFactory` private class
4. Helper composables for dialogs, lists, etc.

### Architecture Assessment

**ViewModel Pattern**: ✅ Already implemented

- `HomeScreenViewModel` handles business logic
- Uses `StateFlow` for reactive state
- Provides repository operations

**State Management**: ⚠️ Mixed

- ViewModel flows: `searchQuery`, `searchResults`, `notes`, `folders`, `currentFolder`, `tags`, `selectedTagFilter`, `noteTags`, `sortOption`, `sortDirection`, `dateRangeFilter`
- Composable `remember { mutableStateOf }`: Dialog visibility, selection mode, filter dropdown, pending deletes

**Loading State**: ❌ Not explicit

- Data flows reactively via StateFlow
- No explicit loading indicator

**Error State**: ✅ Handled

- `errorMessage` StateFlow/String for error display
- `HomeImportErrorDialog` shows errors
- Retry via user action

**Empty State**: ✅ Handled

- `EmptyNotesMessage` composable
- Empty folder list message
- No tags message

### Blockers

1. **Pre-existing compile errors** in other files:
   - `AsyncPdfPipeline.kt`: Unresolved reference: PerfInstrumentation
   - `NoteEditorScreen.kt`: Unresolved reference: TransformGesture
   - `NoteEditorUi.kt`: Type mismatch, unresolved reference

2. **File restoration**: HomeScreen.kt appears to be restored to original state after edits

### Recommended Approach

1. Fix pre-existing compile errors first (separate task)
2. Extract `HomeScreenViewModel` to `HomeViewModel.kt`
3. Use Hilt injection (`@HiltViewModel`, `@Inject`)
4. Move all `remember { mutableStateOf }` state into ViewModel
5. Create unified `HomeUiState` data class
6. Add explicit loading state for async operations

### Key Files

- `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt` - Main screen
- `apps/android/app/src/main/java/com/onyx/android/ui/HomeEmptyState.kt` - Empty state component
- `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt` - Data layer

### Dependencies

- `NoteRepository` - Note, folder, tag operations
- `PdfAssetStorage` - PDF file management
- `PdfiumDocumentInfoReader` - PDF reading
- `PdfPasswordStore` - Password caching

## Learnings

1. **Inline ViewModels are valid** - Kotlin allows private classes in the same file, but extracting improves testability
2. **StateFlow vs mutableStateOf** - StateFlow is better for ViewModel state; mutableStateOf is for composable-only state
3. **Hilt integration** - Requires `@HiltViewModel` annotation and `@Inject` constructor
4. **Compile errors cascade** - Other file errors block lint/test commands
