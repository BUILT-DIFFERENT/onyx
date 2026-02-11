# Milestone: UI Overhaul - Samsung Notes + Notewise Hybrid Design

## Context

### Objective
Redesign the Onyx Android app UI combining the best of Samsung Notes and Notewise UX patterns while maintaining existing architecture and adding organizational features like folders.

### Plan Update (Prototype Review - Feb 1, 2026)
- **Baseline layout** (single always-visible top toolbar with back/settings, no collapse, single bar) is now part of Milestone A.
- This plan focuses on visual polish, tool panels, templates, folders, and interaction refinements.
- **Android source of truth**: Web prototype informs layout decisions; this plan is the Android implementation target.
- **Ignore onyx-ui-prototype**: Do not modify or depend on `apps/onyx-ui-prototype` (experimental only).

### Key Inspirations

**From Samsung Notes:**
1. **Integrated Titlebar + Tools (Updated)** - Single, always-visible top bar with title + tools; no collapse on draw
2. **Contextual Tool Panels** - Floating panels for tool settings instead of persistent sidebars
3. **Color Picker** - Superior spectrum + swatches with hex input
4. **Folder Organization** - Clear hierarchical folder structure with breadcrumbs
5. **Minimal Chrome** - Maximum canvas space with floating/fade UI elements

**From Notewise:**
1. **Dual Toolbar Layout** - Separate primary (tools) and secondary (colors/options) toolbars
2. **Template System** - Rich template picker with categories, background colors, density/line width sliders
3. **Stabilization Slider** - 0-10 smoothness control for handwriting
4. **S Pen Button Configuration** - Customizable primary/secondary button actions
5. **Card-Based Settings** - Grouped settings in cards vs long list
6. **Movable Scrollbar** - Left/right position preference
7. **Eraser Filters** - Select which tool types to erase

---

## Design System

### Color Palette (Dark Theme Primary) - NEW TOKENS TO CREATE

> **Naming Convention**: 
> - **EXISTING tokens** use PascalCase: `OnyxPrimary`, `DarkSurfaceVariant` (Material3 style)
> - **NEW tokens** use camelCase: `bgPrimary`, `surface`, `textPrimary` (design system semantic naming)
> - Both conventions coexist; new UI components should use semantic camelCase tokens

> **Note**: These tokens must be added to `apps/android/app/src/main/java/com/onyx/android/ui/theme/Color.kt`.
> The existing file defines `OnyxPrimary`, `DarkSurfaceVariant`, etc. The tokens below are NEW and 
> should be added alongside the existing definitions.

| Token | Value | Usage | Naming Convention |
|-------|-------|-------|-------------------|
| `bgPrimary` | `#000000` | Main canvas background | NEW - camelCase |
| `bgSecondary` | `#1C1C1E` | Cards, panels, dialogs | NEW - camelCase |
| `bgTertiary` | `#2C2C2E` | Elevated surfaces, hover states | NEW - camelCase |
| `surface` | `#3A3A3C` | Toolbars, controls | NEW - camelCase |
| `surfaceLight` | `#48484A` | Active states, sliders | NEW - camelCase |
| `textPrimary` | `#FFFFFF` | Primary text | NEW - camelCase |
| `textSecondary` | `#98989D` | Secondary text, placeholders | NEW - camelCase |
| `accentBlue` | `#0A84FF` | Selection, active tools | NEW - camelCase |
| `divider` | `#38383A` | Separators | NEW - camelCase |
| `OnyxPrimary` | (existing) | Brand color | EXISTING - PascalCase |
| `DarkSurfaceVariant` | (existing) | Surface variant | EXISTING - PascalCase |

### Typography

| Style | Font | Size | Weight | Usage |
|-------|------|------|--------|-------|
| `titleLarge` | System/SF Pro | 28sp | Bold | Note titles |
| `titleMedium` | System/SF Pro | 20sp | Semibold | Folder names |
| `titleSmall` | System/SF Pro | 17sp | Medium | Section headers |
| `body` | System/SF Pro | 15sp | Regular | Body text |
| `caption` | System/SF Pro | 13sp | Regular | Metadata, counts |
| `toolbar` | System/SF Pro | 12sp | Medium | Toolbar labels |

### Spacing & Layout

- **Toolbar Group Height**: 48dp (slim, always visible)
- **Pill Group Theme**: Toolbar content is split into floating pill groups (left nav/title, center tools+colors, right actions/menu)
- **No Secondary Toolbar**: All controls live in the top pill groups
- **Toolbar Icon Size**: 28dp touch target, 24dp icon
- **Color Dot Size**: 24dp with 2dp selected ring
- **Floating Panel Padding**: 16dp
- **Corner Radius (Panels)**: 16dp
- **Corner Radius (Buttons)**: 8dp
- **Sidebar Width**: 280dp (tablet)

---

## Architecture Changes

### New Database Entities

```kotlin
// FolderEntity.kt
@Entity(tableName = "folders")
data class FolderEntity(
  @PrimaryKey val folderId: String = UUID.randomUUID().toString(),
  val parentFolderId: String? = null, // null = root level
  val name: String,
  val color: String? = null, // Hex color for folder icon
  val icon: String = "folder", // folder, school, work, etc.
  val sortOrder: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)

// NoteFolderEntity.kt (junction table for many-to-many)
@Entity(
  tableName = "note_folders",
  primaryKeys = ["noteId", "folderId"]
)
data class NoteFolderEntity(
  val noteId: String,
  val folderId: String,
  val addedAt: Long = System.currentTimeMillis()
)

// UserPreferenceEntity.kt (for tool settings persistence)
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
  @PrimaryKey val key: String,
  val value: String,
  val updatedAt: Long = System.currentTimeMillis()
)

// TemplateEntity.kt (for page templates)
@Entity(tableName = "templates")
data class TemplateEntity(
  @PrimaryKey val templateId: String = UUID.randomUUID().toString(),
  val category: String, // "basic", "education", "music"
  val name: String,
  val displayName: String,
  val previewResId: String?, // Resource name or asset path
  val bgColors: String, // JSON array of hex colors
  val defaultDensity: Int = 5, // 1-10
  val defaultLineWidth: Int = 5, // 1-10
  val sortOrder: Int = 0
)

// StylusPreferenceEntity.kt (for S Pen / stylus settings)
@Entity(tableName = "stylus_preferences")
data class StylusPreferenceEntity(
  @PrimaryKey val deviceId: String = "default",
  val primaryButtonAction: String = "switch_to_eraser", // "switch_to_eraser" | "switch_to_last_used" | "hold_to_erase"
  val secondaryButtonAction: String? = null, // For Bluetooth styluses
  val latencyOptimization: Boolean = false, // "fast" experimental mode
  val singleFingerAction: String = "scroll", // "draw" | "scroll" | "ignored"
  val doubleFingerAction: String = "zoom_and_pan" // "zoom_and_pan" | "scroll" | "ignored"
)
```

### DAO Additions

```kotlin
@Dao
interface FolderDao {
  @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY sortOrder, name")
  fun getRootFolders(): Flow<List<FolderEntity>>
  
  @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY sortOrder, name")
  fun getSubfolders(parentId: String): Flow<List<FolderEntity>>
  
  @Query("SELECT * FROM folders ORDER BY sortOrder, name")
  fun getAllFolders(): Flow<List<FolderEntity>>
  
  @Insert
  suspend fun insert(folder: FolderEntity)
  
  @Update
  suspend fun update(folder: FolderEntity)
  
  @Delete
  suspend fun delete(folder: FolderEntity)
  
  @Query("DELETE FROM folders WHERE folderId = :folderId")
  suspend fun deleteById(folderId: String)  // Required for cascade delete
  
  @Query("SELECT COUNT(*) FROM note_folders WHERE folderId = :folderId")
  suspend fun getNoteCount(folderId: String): Int
  
  @Query("SELECT folderId FROM folders WHERE parentFolderId IN (:parentIds)")
  suspend fun getChildFolderIds(parentIds: List<String>): List<String>  // Required for recursive delete
  
  @Query("SELECT COUNT(*) FROM folders WHERE parentFolderId = :folderId")
  suspend fun getChildCount(folderId: String): Int  // For chevron display
}

@Dao
interface UserPreferenceDao {
  @Query("SELECT * FROM user_preferences WHERE key = :key")
  suspend fun get(key: String): UserPreferenceEntity?
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun set(preference: UserPreferenceEntity)
  
  @Query("DELETE FROM user_preferences WHERE key = :key")
  suspend fun delete(key: String)
  
  // Convenience methods for common prefs
  @Query("SELECT value FROM user_preferences WHERE key = 'scrollbar_position'")
  suspend fun getScrollbarPosition(): String? // "left" | "right"
  
  @Query("SELECT value FROM user_preferences WHERE key = 'stabilization_level'")
  suspend fun getStabilizationLevel(): String? // "0" - "10"
}

@Dao
interface TemplateDao {
  @Query("SELECT * FROM templates ORDER BY category, sortOrder")
  fun getAllTemplates(): Flow<List<TemplateEntity>>
  
  @Query("SELECT * FROM templates WHERE category = :category ORDER BY sortOrder")
  fun getTemplatesByCategory(category: String): Flow<List<TemplateEntity>>
  
  @Insert
  suspend fun insert(template: TemplateEntity)
  
  @Insert
  suspend fun insertAll(templates: List<TemplateEntity>)
}

@Dao
interface StylusPreferenceDao {
  @Query("SELECT * FROM stylus_preferences WHERE deviceId = :deviceId")
  suspend fun getForDevice(deviceId: String): StylusPreferenceEntity?
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun set(prefs: StylusPreferenceEntity)
}
```

### HomeScreen Query Surface (Repository Methods Required)

**Current State:**
- `HomeScreen` currently calls `repository.getNotesForHomeScreen()` via `HomeViewModel` (inside `HomeScreen.kt:360`)
- This returns all notes without folder filtering

**Required Repository Methods for Folder Navigation:**

```kotlin
// In NoteRepository.kt (add these methods)

/**
 * Get notes for display in HomeScreen
 * - folderId = null: Return all notes (root/"All Notes" view)
 * - folderId != null: Return notes in specific folder via note_folders join
 */
fun getNotesForFolder(folderId: String?): Flow<List<Note>>

/**
 * Get note count for a folder (for sidebar display)
 * Returns count of notes directly in folder (NOT recursive into subfolders)
 */
suspend fun getNoteCountInFolder(folderId: String): Int

/**
 * Get note count for "All Notes" (root level)
 * Returns count of notes with NO folder membership
 */
suspend fun getRootNoteCount(): Int
```

**Implementation Pattern:**
```kotlin
// NoteRepository.kt
fun getNotesForFolder(folderId: String?): Flow<List<Note>> {
  return if (folderId == null) {
    // "All Notes" - notes with NO folder assignments
    noteDao.getNotesWithoutFolders()
  } else {
    // Specific folder - join with note_folders
    noteDao.getNotesInFolder(folderId)
  }
}
```

**DAO Methods Required:**
```kotlin
// In NoteDao.kt (add these)
@Query("""
  SELECT n.* FROM notes n 
  LEFT JOIN note_folders nf ON n.noteId = nf.noteId 
  WHERE nf.noteId IS NULL
  ORDER BY n.updatedAt DESC
""")
fun getNotesWithoutFolders(): Flow<List<NoteEntity>>

@Query("""
  SELECT n.* FROM notes n 
  INNER JOIN note_folders nf ON n.noteId = nf.noteId 
  WHERE nf.folderId = :folderId
  ORDER BY n.updatedAt DESC
""")
fun getNotesInFolder(folderId: String): Flow<List<NoteEntity>>
```

**Folder Tree Display Requirements:**

| UI Element | Data Source | Repository Method | Count Type |
|------------|-------------|-------------------|------------|
| "All Notes" | NoteRepository | getRootNoteCount() | Notes with no folder |
| Sidebar folder | NoteRepository | getNoteCountInFolder(folderId) | Direct notes only |
| Breadcrumb | NoteRepository | getFolderPath(folderId) | Navigation path |

**Important:** 
- Note counts in sidebar are **NOT recursive** (don't count subfolder notes)
- "All Notes" count includes ONLY notes at root level (no folder assignments)
- For child folder counts (chevron display), use FolderDao.getChildCount()

---

### Folder Membership Semantics (CRITICAL CLARIFICATION)

> **Chosen Model**: Many-to-many via `note_folders` junction table.

**What "Root" Means**:
- A note is at "root level" (visible in "All Notes") when it has NO rows in `note_folders`
- When a note is moved TO a folder: INSERT row into `note_folders(noteId, folderId)`
- When a note is moved OUT of a folder: DELETE row from `note_folders` WHERE noteId=X AND folderId=Y
- A note CAN exist in multiple folders (link, not move) - this is intentional for organization flexibility

**Folder Deletion Behavior**:
- When a folder is deleted: DELETE rows from `note_folders` WHERE folderId=X
- This "orphans" notes back to root (no cascade delete of notes themselves)
- Child subfolders: SET parentFolderId=NULL (orphan to root) OR cascade delete subfolder hierarchy
- **Chosen approach**: Cascade delete subfolders (user expects folder + subfolders deleted together)
- Notes in subfolders: Also orphaned to root via `note_folders` row deletion

**Foreign Key Constraints**:
```kotlin
@Entity(
  tableName = "note_folders",
  primaryKeys = ["noteId", "folderId"],
  foreignKeys = [
    ForeignKey(
      entity = NoteEntity::class,
      parentColumns = ["noteId"],
      childColumns = ["noteId"],
      onDelete = ForeignKey.CASCADE // If note deleted, remove folder membership
    ),
    ForeignKey(
      entity = FolderEntity::class,
      parentColumns = ["folderId"],
      childColumns = ["folderId"],
      onDelete = ForeignKey.CASCADE // If folder deleted, orphan notes to root
    )
  ]
)
```

### Repository Ownership (CRITICAL CLARIFICATION)

> **Chosen Model**: Single NoteRepository with folder operations (NOT separate FolderRepository)

**Rationale**: 
- Folder operations are tightly coupled with note operations (move, organize, query)
- Keeping them in NoteRepository maintains transactional consistency
- Simpler dependency graph (one repository injected vs multiple)

**All folder operations go into `NoteRepository.kt`** (no separate FolderRepository created).

**Template Repository**: TemplateRepository.kt IS created as separate class because:
- Templates are independent of notes (no FK relationships)
- Template operations (CRUD, category filtering) are self-contained
- Separation allows future template sharing/sync without note coupling

### DAO and Repository Wiring (CRITICAL)

**Step-by-step wiring for new DAOs:**

1. **Add DAO accessors to OnyxDatabase** (Task 1.6-1.8):
```kotlin
// In OnyxDatabase.kt, add abstract methods:
abstract fun folderDao(): FolderDao
abstract fun templateDao(): TemplateDao
abstract fun stylusPreferenceDao(): StylusPreferenceDao
abstract fun userPreferenceDao(): UserPreferenceDao
```

2. **Update NoteRepository constructor** (Task 1.9):
```kotlin
// Current constructor (OnyxApplication.kt:41-49):
NoteRepository(
    noteDao = database.noteDao(),
    pageDao = database.pageDao(),
    strokeDao = database.strokeDao(),
    recognitionDao = database.recognitionDao(),
    deviceIdentity = deviceIdentity,
    strokeSerializer = StrokeSerializer,
)

// New constructor signature:
NoteRepository(
    noteDao = database.noteDao(),
    pageDao = database.pageDao(),
    strokeDao = database.strokeDao(),
    recognitionDao = database.recognitionDao(),
    folderDao = database.folderDao(),          // NEW
    userPreferenceDao = database.userPreferenceDao(),  // NEW
    deviceIdentity = deviceIdentity,
    strokeSerializer = StrokeSerializer,
)
```

3. **Create TemplateRepository** (Task 4.1-4.8):
```kotlin
class TemplateRepository(
    private val templateDao: TemplateDao,
    private val userPreferenceDao: UserPreferenceDao
)
```

4. **Wire repositories in OnyxApplication** (update lines 41-62):
```kotlin
// Current OnyxApplication fields (line 19-22):
lateinit var database: OnyxDatabase
lateinit var noteRepository: NoteRepository
lateinit var deviceIdentity: DeviceIdentity
lateinit var myScriptEngine: MyScriptEngine

// Add new field:
lateinit var templateRepository: TemplateRepository

// Initialize after noteRepository (around line 41-49):
noteRepository = NoteRepository(
    noteDao = database.noteDao(),
    pageDao = database.pageDao(),
    strokeDao = database.strokeDao(),
    recognitionDao = database.recognitionDao(),
    folderDao = database.folderDao(),          // NEW
    userPreferenceDao = database.userPreferenceDao(),  // NEW
    deviceIdentity = deviceIdentity,
    strokeSerializer = StrokeSerializer,
)

// Add TemplateRepository initialization:
templateRepository = TemplateRepository(
    templateDao = database.templateDao(),
    userPreferenceDao = database.userPreferenceDao()
)
```

5. **Update ViewModel factories** (NOTE: Factories are local classes within Screen files, NOT separate files):
   - `HomeViewModelFactory` is a private class inside `HomeScreen.kt:337` - update it there
   - `NoteEditorViewModelFactory` does NOT exist - create new ViewModel management for editor (see Task 3.11)
```kotlin
// Current pattern (HomeScreen.kt:71):
val viewModel: HomeViewModel = viewModel(
    factory = HomeViewModelFactory(repository)
)

// New pattern - pass both repositories:
val viewModel: HomeViewModel = viewModel(
    factory = HomeViewModelFactory(
        noteRepository = (context.applicationContext as OnyxApplication).noteRepository,
        templateRepository = (context.applicationContext as OnyxApplication).templateRepository
    )
)
```

**Dependency Injection Pattern**:
- Repositories are constructed in OnyxApplication.onCreate() and stored as fields
- ViewModel factories receive repositories via constructor
- ViewModels receive repositories, not DAOs directly
- Access via: `(context.applicationContext as OnyxApplication).repositoryName`

### SharedPreferences Migration Plan (CRITICAL)

> **Current State**: `NoteEditorScreen.kt` uses SharedPreferences for overlay settings (lines 168-189)
> **Target State**: All preferences in Room via `UserPreferenceEntity`

**Migration Strategy - Timing & Race Condition Prevention:**

```
┌─────────────────────────────────────────────────────────────────┐
│ OnyxApplication.onCreate()                                       │
├─────────────────────────────────────────────────────────────────┤
│ 1. Build database (OnyxDatabase.build())                        │
│    ↓                                                             │
│ 2. Check migration flag: "prefs_migrated_to_room_v1"            │
│    ↓ (if not set)                                                │
│ 3. Run PreferenceMigrator.migrateIfNeeded()                     │
│    - Read ALL SharedPreferences values                          │
│    - Insert into user_preferences table                         │
│    - Set migration flag in SharedPreferences                    │
│    ↓                                                             │
│ 4. Initialize repositories                                      │
│    ↓                                                             │
│ 5. Normal app startup                                           │
└─────────────────────────────────────────────────────────────────┘
```

**Migration Timing Details:**
1. **When**: BEFORE any screen can access preferences, during `OnyxApplication.onCreate()`
2. **Order Guarantee**: Migration runs AFTER database creation but BEFORE repository initialization
3. **Trigger**: Check SharedPreferences flag `prefs_migrated_to_room_v1` 
   - If flag exists → migration already done, skip
   - If flag missing → run migration once
4. **Migration Code Location**: `PreferenceMigrator.kt` utility class
5. **What to Migrate**:
   - `show_overlay` → `PreferenceKeys.SHOW_OVERLAY`
   - `opacity` → `PreferenceKeys.OVERLAY_OPACITY`
   - `font_size` → `PreferenceKeys.OVERLAY_FONT_SIZE`
6. **Race Condition Prevention**: 
   - Set migration flag IMMEDIATELY after migration completes (before returning)
   - Even if migration fails, set flag to prevent retry loops
   - Screens read from Room only - no SharedPreferences fallback during normal operation
7. **SharedPreferences After Migration**: 
   - Original values left intact (backward compatibility)
   - Migration flag added: `prefs_migrated_to_room_v1 = true`
   - NOT used as fallback (always read from Room)
8. **New Preferences**: All new preferences (stabilization, scrollbar, colors) go directly to Room

**Critical Implementation Order in OnyxApplication:**
```kotlin
override fun onCreate() {
    super.onCreate()
    
    // 1. Build database first
    database = OnyxDatabase.build(applicationContext)
    
    // 2. Run migration BEFORE any repository can access prefs
    PreferenceMigrator.migrateIfNeeded(
        context = applicationContext,
        userPreferenceDao = database.userPreferenceDao()
    )
    
    // 3. Now safe to initialize repositories
    noteRepository = NoteRepository(...)
    templateRepository = TemplateRepository(...)
}
```

**NoteEditorScreen Migration Handling:**
- **Before Task 3.10/3.11**: Screen reads SharedPreferences directly (legacy)
- **After Task 3.10/3.11**: Screen uses ViewModel → Repository → Room (new)
- Migration ensures Room has values before first read
- No race condition because migration completes before NoteEditorScreen can be opened

**Default Values** (when no preference exists):
```kotlin
object PreferenceDefaults {
  const val SCROLLBAR_POSITION = "right"
  const val STABILIZATION_LEVEL = "0"
  const val QUICK_COLOR_1 = "#FF000000" // Black
  const val QUICK_COLOR_2 = "#FF0D47A1" // Blue
  const val QUICK_COLOR_3 = "#FFB71C1C" // Red
  const val QUICK_COLOR_4 = "#FF1B5E20" // Green
  const val QUICK_COLOR_5 = "#FF8E24AA" // Purple
  const val SHOW_OVERLAY = "false"
  const val OVERLAY_OPACITY = "0.3"
  const val OVERLAY_FONT_SIZE = "16"
}
```

### Preference Keys (CANONICAL LIST)

> **IMPORTANT**: This is the authoritative list of all preference keys stored in `UserPreferenceEntity`.
> All preferences are stored as `String` values (key-value pairs) in Room.

| Key Constant | Storage Key | Type (as String) | Default | Description | Read By | Written By |
|--------------|-------------|------------------|---------|-------------|---------|------------|
| `SCROLLBAR_POSITION` | `scrollbar_position` | `"left"` \| `"right"` | `"right"` | Page scrollbar edge position | `MovableScrollbar`, `SettingsScreen` | `SettingsScreen` |
| `STABILIZATION_LEVEL` | `stabilization_level` | `"0"` - `"10"` (integer as string) | `"0"` | Stroke smoothing level | `NoteEditorViewModel`, `DualToolbar` | `PenSettingsPanel` |
| `QUICK_COLOR_1` | `quick_color_1` | Hex color `"#AARRGGBB"` | `"#FF000000"` | First quick-access color slot | `DualToolbar` | `ColorPicker` |
| `QUICK_COLOR_2` | `quick_color_2` | Hex color `"#AARRGGBB"` | `"#FF0D47A1"` | Second quick-access color slot | `DualToolbar` | `ColorPicker` |
| `QUICK_COLOR_3` | `quick_color_3` | Hex color `"#AARRGGBB"` | `"#FFB71C1C"` | Third quick-access color slot | `DualToolbar` | `ColorPicker` |
| `QUICK_COLOR_4` | `quick_color_4` | Hex color `"#AARRGGBB"` | `"#FF1B5E20"` | Fourth quick-access color slot | `DualToolbar` | `ColorPicker` |
| `QUICK_COLOR_5` | `quick_color_5` | Hex color `"#AARRGGBB"` | `"#FF8E24AA"` | Fifth quick-access color slot | `DualToolbar` | `ColorPicker` |
| `SHOW_OVERLAY` | `show_overlay` | `"true"` \| `"false"` | `"false"` | Recognition overlay visibility | `NoteEditorScreen` | `OverlaySettingsDialog` |
| `OVERLAY_OPACITY` | `overlay_opacity` | `"0.0"` - `"1.0"` (float as string) | `"0.3"` | Recognition overlay transparency | `NoteEditorScreen` | `OverlaySettingsDialog` |
| `OVERLAY_FONT_SIZE` | `overlay_font_size` | `"8"` - `"32"` (integer as string) | `"16"` | Recognition overlay text size | `NoteEditorScreen` | `OverlaySettingsDialog` |
| `LAST_TEMPLATE_ID` | `last_template_id` | Template ID string | `"basic_blank"` | Last used page template | `NoteRepository.createPage()` | `TemplatePicker` |
| `DEFAULT_PAGE_SIZE` | `default_page_size` | `"A3"` \| `"A4"` \| `"A5"` \| `"A6"` \| `"A7"` \| JSON `{"w":int,"h":int}` | `"A4"` | Default new page dimensions | `NoteRepository.createPage()` | `TemplatePicker` |
| `PEN_THICKNESS` | `pen_thickness` | `"1"` - `"30"` (integer as string) | `"5"` | Pen stroke thickness | `NoteEditorViewModel` | `PenSettingsPanel` |
| `PEN_STYLE` | `pen_style` | `"FOUNTAIN"` \| `"BALL"` \| `"CALLIGRAPHY"` | `"BALL"` | Pen rendering style | `NoteEditorViewModel` | `PenSettingsPanel` |
| `HIGHLIGHTER_THICKNESS` | `highlighter_thickness` | `"1"` - `"50"` (integer as string) | `"20"` | Highlighter stroke thickness | `NoteEditorViewModel` | `HighlighterSettingsPanel` |
| `HIGHLIGHTER_OPACITY` | `highlighter_opacity` | `"0.0"` - `"1.0"` (float as string) | `"0.2"` | Highlighter transparency | `NoteEditorViewModel` | `HighlighterSettingsPanel` |
| `ERASER_SIZE` | `eraser_size` | `"1"` - `"100"` (integer as string) | `"10"` | Eraser diameter | `NoteEditorViewModel` | `EraserSettingsPanel` |
| `ERASER_MODE` | `eraser_mode` | `"STROKE"` \| `"AREA"` | `"STROKE"` | Eraser behavior mode | `NoteEditorViewModel` | `EraserSettingsPanel` |
| `ERASER_FILTERS` | `eraser_filters` | JSON `{"pen":bool,"highlighter":bool,...}` | `{"pen":true,"highlighter":true,"shapes":true,"images":false,"text":true,"stickers":false}` | Which content types eraser affects | `NoteEditorViewModel` | `EraserSettingsPanel` |
| `PRESSURE_SENSITIVITY` | `pressure_sensitivity` | `"0"` - `"10"` (integer as string) | `"5"` | Stylus pressure response curve | `InkCanvas` | `PenSettingsPanel` |

**Implementation Pattern:**
```kotlin
// PreferenceKeys.kt
object PreferenceKeys {
  const val SCROLLBAR_POSITION = "scrollbar_position"
  const val STABILIZATION_LEVEL = "stabilization_level"
  const val QUICK_COLOR_1 = "quick_color_1"
  // ... etc
}

// Reading a preference with default:
suspend fun getPreference(key: String, default: String): String {
  return userPreferenceDao.get(key)?.value ?: default
}

// Writing a preference:
suspend fun setPreference(key: String, value: String) {
  userPreferenceDao.set(UserPreferenceEntity(key = key, value = value))
}
```

### Folder Membership Behavior (Move vs Link)

> **Chosen Model**: "Move" semantics by default, with explicit "Link" for power users.

**Default Behavior ("Move to Folder"):**
- When user selects notes and taps "Move to folder X":
  1. DELETE all existing rows from `note_folders` WHERE `noteId` IN (selected)
  2. INSERT new rows into `note_folders` for each (noteId, X)
- Result: Notes appear ONLY in folder X after move

**Alternative Behavior ("Add to Folder" / Link):**
- Available via long-press on "Move to folder" or separate menu option "Add to folder"
- INSERT row into `note_folders` WITHOUT deleting existing memberships
- Result: Note appears in multiple folders (linked, not moved)

**UI Implications:**
- Multi-select action bar shows "Move to folder" as primary action
- Context menu for single note shows both "Move to folder" and "Add to folder"
- Notes in multiple folders show a "linked" badge or icon

### Subfolder Cascade Delete Mechanism (CRITICAL)

> **Implementation Strategy**: Self-referential FK with manual recursive delete.

**Why NOT use FK CASCADE for subfolders:**
- Room does not natively support recursive FK cascade on self-referential tables
- SQLite FK cascade only handles direct references, not recursive hierarchies
- Need explicit deletion order to satisfy FK constraints

**Chosen Implementation:**
```kotlin
// In NoteRepository.kt
@Transaction
suspend fun deleteFolder(folderId: String) {
  // 1. Recursively collect all descendant folder IDs
  val allFolderIds = mutableListOf(folderId)
  var toProcess = listOf(folderId)
  while (toProcess.isNotEmpty()) {
    val children = folderDao.getChildFolderIds(toProcess)
    allFolderIds.addAll(children)
    toProcess = children
  }
  
  // 2. Delete note_folders rows for all affected folders (orphans notes to root)
  // FK CASCADE on note_folders.folderId handles this automatically when folders deleted
  
  // 3. Delete folders in reverse order (deepest first) to satisfy FK
  allFolderIds.reversed().forEach { id ->
    folderDao.deleteById(id)
  }
}

// Add to FolderDao:
@Query("SELECT folderId FROM folders WHERE parentFolderId IN (:parentIds)")
suspend fun getChildFolderIds(parentIds: List<String>): List<String>
```

**FK Constraint on folders table:**
```kotlin
@Entity(
  tableName = "folders",
  foreignKeys = [
    ForeignKey(
      entity = FolderEntity::class,
      parentColumns = ["folderId"],
      childColumns = ["parentFolderId"],
      onDelete = ForeignKey.NO_ACTION // Manual cascade, not automatic
    )
  ]
)
```

### Folder Tree Lazy Loading Strategy (CRITICAL)

> **Problem**: Room cannot do recursive CTEs efficiently; loading 1000+ folders at once is slow.
> **Solution**: Two-level loading with on-demand expansion.

**Initial Load (on HomeScreen mount):**
1. Load ONLY root folders: `SELECT * FROM folders WHERE parentFolderId IS NULL`
2. For each root folder, load immediate children count (for chevron display)
3. All folders start collapsed

**On Folder Expand:**
1. When user taps chevron, load immediate children: `SELECT * FROM folders WHERE parentFolderId = :expandedId`
2. Cache loaded folders in ViewModel state
3. On collapse, keep cached (don't reload on re-expand)

**State Structure:**
```kotlin
data class FolderTreeState(
  val rootFolders: List<Folder>,
  val expandedFolderIds: Set<String>,
  val loadedChildren: Map<String, List<Folder>>, // folderId -> children
  val loadingFolderIds: Set<String> // Currently loading
)
```

**DAO Methods:**
```kotlin
@Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY sortOrder, name")
fun getRootFolders(): Flow<List<FolderEntity>>

@Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY sortOrder, name")
fun getChildFolders(parentId: String): Flow<List<FolderEntity>>

@Query("SELECT COUNT(*) FROM folders WHERE parentFolderId = :folderId")
suspend fun getChildCount(folderId: String): Int
```

**Compose Integration:**
```kotlin
@Composable
fun FolderTree(viewModel: HomeViewModel) {
  val state by viewModel.folderTreeState.collectAsState()
  
  LazyColumn {
    items(state.rootFolders) { folder ->
      FolderItem(
        folder = folder,
        isExpanded = folder.folderId in state.expandedFolderIds,
        isLoading = folder.folderId in state.loadingFolderIds,
        onExpand = { viewModel.toggleFolderExpansion(folder.folderId) }
      )
      if (folder.folderId in state.expandedFolderIds) {
        val children = state.loadedChildren[folder.folderId] ?: emptyList()
        items(children) { child ->
          // Recursive or depth-limited rendering
        }
      }
    }
  }
}
```

### S Pen Button Handling API (CRITICAL)

> **Chosen Approach**: Standard Android `MotionEvent` API (not Samsung SDK).

**Rationale:**
- Samsung S Pen SDK requires separate SDK dependency and Samsung device detection
- Standard `MotionEvent` provides stylus button state via `getButtonState()`
- Works across all Android styluses, not just S Pen

**API Entry Points:**
- `MotionEvent.getButtonState()` - Returns bitmask of pressed buttons
- `MotionEvent.BUTTON_STYLUS_PRIMARY` (0x20) - Primary stylus button
- `MotionEvent.BUTTON_STYLUS_SECONDARY` (0x40) - Secondary stylus button
- `MotionEvent.getToolType()` - Returns `TOOL_TYPE_STYLUS` (2) for stylus input

**Implementation Location & Integration:**
InkCanvas.kt:191 uses `setOnTouchListener`. Integration with existing tool state from `NoteEditorScreen.kt`:

```kotlin
// InkCanvas.kt:191 - Inside view.setOnTouchListener { touchedView, event ->
when (event.actionMasked) {
  MotionEvent.ACTION_DOWN -> {
    // Check for stylus button state
    if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
      val buttonState = event.buttonState
      val primaryPressed = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
      
      // Handle button action - delegate to NoteEditorScreen via callback
      if (primaryPressed) {
        // Call back to screen/ViewModel to handle tool switching
        onStylusButtonPressed(primaryButtonAction)
      }
    }
    // ... existing ACTION_DOWN handling
  }
  MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
    // Notify that button was released (for hold-to-erase)
    onStylusButtonReleased()
    // ... existing ACTION_UP handling
  }
}
```

**Integration with Existing Tool State (NoteEditorScreen.kt:191):**

**BEFORE Task 3.11 (current state):**
- Tool state lives in `NoteEditorScreen.kt` as `currentBrush` and `eraserMode` (lines 191-200)
- InkCanvas receives these as parameters
- **S Pen handling**: Add callbacks to InkCanvas composable:
  ```kotlin
  InkCanvas(
    currentBrush = currentBrush,
    eraserMode = eraserMode,
    // NEW: S Pen callbacks
    onStylusButtonPressed = { action ->
      // Handle in screen-level state
      when (action) {
        SWITCH_TO_ERASER -> {
          savedToolBeforeErase = currentBrush
          currentBrush = Brush.ERASER
        }
        HOLD_TO_ERASE -> { /* similar */ }
      }
    },
    onStylusButtonReleased = {
      // Restore tool if hold-to-erase was active
      savedToolBeforeErase?.let { currentBrush = it }
    }
  )
  ```

**AFTER Task 3.11 (with ViewModel):**
- Tool state moves to `NoteEditorViewModel`
- InkCanvas callbacks delegate to ViewModel methods
- ViewModel handles tool switching and restoration

**Tool State Preservation:**
- Hold-to-erase: Screen/ViewModel tracks `savedToolBeforeErase: Tool?`
- On button down: Save current tool, switch to eraser
- On button up: Restore saved tool
- State survives configuration changes (in ViewModel after 3.11)

**Reference Documentation:**
- Android MotionEvent: https://developer.android.com/reference/android/view/MotionEvent#getButtonState()
- Stylus input handling: https://developer.android.com/develop/ui/views/touch-and-input/stylus-input

### Database Construction Strategy (CRITICAL)

> **Current State**: OnyxApplication.kt:34-39 builds database directly using `Room.databaseBuilder(...).build()`
> **Target State**: OnyxApplication.kt should use `OnyxDatabase.build(applicationContext)` helper

**Current Code** (OnyxApplication.kt:34-39):
```kotlin
database = Room.databaseBuilder(
    applicationContext,
    OnyxDatabase::class.java,
    OnyxDatabase.DATABASE_NAME,
).build()
```

**OnyxDatabase.build()** (OnyxDatabase.kt:51) currently only adds MIGRATION_1_2.

**CRITICAL**: The plan's migration timing, callbacks, and prepopulation strategy depends on `OnyxDatabase.build()` being authoritative. **Task 0.2** must update OnyxApplication to use OnyxDatabase.build() BEFORE any other database-related tasks.

**Required Updates to OnyxDatabase.build()**:
- All migrations (MIGRATION_1_2, MIGRATION_2_3, etc.)
- `.addCallback()` for template prepopulation (Task 1.3)
- Future migrations as they're added

**Single Source of Truth**: All DB configuration should live in `OnyxDatabase.build()`. OnyxApplication should call this method.

### Data Models

```kotlin
// Folder.kt (domain model)
data class Folder(
  val folderId: String,
  val parentFolderId: String?,
  val name: String,
  val color: Color?,
  val icon: FolderIcon,
  val noteCount: Int = 0,
  val hasSubfolders: Boolean = false,
  val createdAt: Long,
  val updatedAt: Long
)

enum class FolderIcon {
  FOLDER, SCHOOL, WORK, PERSONAL, PROJECTS, ARCHIVE
}

// ToolSettings.kt
data class ToolSettings(
  val toolType: ToolType,
  val strokeWidth: Float, // 1-100 scale
  val opacity: Float, // 0-100
  val color: Color,
  // Tool-specific settings
  val penStyle: PenStyle? = null, // Fountain, Ball, Calligraphy
  val highlighterStyle: HighlighterStyle? = null, // Square, Round
  val eraserMode: EraserMode? = null,
  val eraserSize: Float? = null,
  val eraserFilters: EraserFilters? = null, // Which tools to erase
  val stabilizationLevel: Float = 0f, // 0.0-1.0 (0-10 in UI)
  val pressureSensitivity: Int = 5, // 0-10
  val lineType: LineType = LineType.STROKE // For shape detection
)

enum class PenStyle {
  FOUNTAIN, BALL, CALLIGRAPHY
}

enum class HighlighterStyle {
  SQUARE, ROUND
}

enum class LineType {
  STROKE, SHAPE_DETECTION
}

// Eraser filter - which tool types to erase
data class EraserFilters(
  val pen: Boolean = true,
  val highlighter: Boolean = true,
  val shapes: Boolean = true,
  val images: Boolean = false,
  val text: Boolean = true,
  val stickers: Boolean = false
)

// Template model
data class Template(
  val templateId: String,
  val category: TemplateCategory,
  val name: String,
  val displayName: String,
  val previewResId: String?,
  val bgColors: List<Color>,
  val defaultDensity: Int,
  val defaultLineWidth: Int
)

enum class TemplateCategory {
  BASIC, EDUCATION, MUSIC
}

// Stylus configuration
data class StylusConfig(
  val deviceId: String,
  val primaryButtonAction: StylusButtonAction,
  val secondaryButtonAction: StylusButtonAction?,
  val latencyOptimization: Boolean,
  val singleFingerAction: FingerAction,
  val doubleFingerAction: FingerAction
)

enum class StylusButtonAction {
  SWITCH_TO_ERASER,
  SWITCH_TO_LAST_USED,
  HOLD_TO_ERASE
}

enum class FingerAction {
  DRAW, SCROLL, ZOOM_AND_PAN, IGNORED
}
```

---

## Screen Specifications

### 1. Home Screen (Folder Browser)

#### Layout: Two-Pane (Tablet) / Single-Pane (Phone)

**Left Pane: Navigation Sidebar (280dp fixed width)**
- Collapsible on phones (drawer behavior)
- Always visible on tablets

**Sidebar Structure (TOP TO BOTTOM):**

1. **Quick Filters Section** (no header, just items):
   - "All notes" with note count (right-aligned)
   - "Starred" notes
   - "Shared notes" with BETA badge
   - "Trash" with trash count

2. **Folder Section:**
   - Header row: "Folders" (left) + [+] add button (right)
   - Folder tree below

```
┌─────────────────────────────┐
│ ≡  All Notes              151 │  <- Header with hamburger
├─────────────────────────────┤
│ ⭐ Starred                   │
│ 👥 Shared notes     [BETA]  │
│ 🗑️ Trash              15    │
├─────────────────────────────┤
│ 📁 Folders              [+] │  <- Section header with add button
│                             │
│ ▶ 🟣 Physics 2          3   │  <- Collapsed folder
│    ▼ 📄 Lecture         4   │  <- Expanded subfolder
│    📄 Labs              2   │
│    📄 Hw                1   │
│ ▶ 🟢 Chemistry          3   │  <- Collapsed folder
│ ▶ 🔵 Design 2           7   │
└─────────────────────────────┘
```

**Folder Item Layout (HORIZONTAL):**
```
[Chevron 24dp][FolderIcon 24dp][FolderName][Count]
                 ↑
          24dp indent per depth level
```

**Folder Item Component:**
- Chevron: **LEFT of folder icon** (not right), right-facing when collapsed, down-facing when expanded
- Icon: Colored folder icon (default gray, customizable)
- Name: Folder name, truncated with ellipsis if too long
- Count: Note count, right-aligned, secondary text color
- Indent: **24dp per depth level**
- Height: 48dp
- **Selected state: Filled pill shape with `accentBlue` at 15% opacity + `accentBlue` text color** (not just bgTertiary overlay)
- Selected corner radius: 8dp pill shape
- Chevron size: 24dp touch target

**Right Pane: Note Grid**

**Header:**
```
┌─────────────────────────────────────────────────────────────┐
│ Physics 2  >  Labs       [📁+]    🔍  ≡  Date modified ▼ │
└─────────────────────────────────────────────────────────────┘
```
- **Breadcrumb navigation**: "Folder > Subfolder" format with **folder icons**, not just text
  - Separator: " > " with spacing
  - Each segment is tappable for navigation
- **Create folder button**: Folder icon with + (opens create dialog)
- Search icon (opens search overlay)
- Sort menu: Shows "Date modified" with **arrow indicator** (up/down for sort direction)
- View toggle (Grid/List) - optional
- Grid uses **2 columns on tablet** (not 3 as previously spec'd)

**Note Grid:**
- 3 columns on large tablets
- 2 columns on small tablets/phones
- Card size: aspect ratio 3:4
- Gap: 16dp
- Padding: 16dp

**Note Card Component:**
```
┌─────────────────────────────┐
│                             │
│     [Thumbnail/Preview]     │
│                             │
│                             │
├─────────────────────────────┤
│ Note Title                  │
│ Jan 27                      │
└─────────────────────────────┘
```
- Thumbnail: First page preview, letterboxed if needed
- Title: Single line, bold
- Date: Secondary text, "Jan 27" format
- Background: `bgSecondary` with 1dp border `divider`
- Corner radius: 12dp
- Long-press: Multi-select mode with checkboxes

---

### 2. Note Editor Screen

**Unified Top Toolbar (Title + Tools)**

**Toolbar Approach:**
- **Single top toolbar system using floating pill groups** (Samsung Notes style chrome)
- **Back button + Settings/overflow are inside the same top toolbar system** (no separate title bar)
- **Tools + 5 color dots inline** in the same row (Samsung Notes style)
- **NO secondary toolbar** and **NO bottom toolbar**
- If width is constrained: tool cluster can scroll horizontally; secondary actions collapse into overflow

**State Machine:**
```
┌──────────────┐    View mode     ┌──────────────┐
│  EDIT_MODE   │ ───────────────> │  VIEW_MODE   │
│ Toolbar on   │ <──────────────  │ Read-only    │
└──────────────┘    Edit mode
```

#### EDIT_MODE (Default)

**Unified Toolbar Groups (48dp height, always visible):**
```
┌─────────────────┐  ┌───────────────────────────────────────┐  ┌──────────────────────┐
│ ←  Note title   │  │ [🖐️][✏️][🖍️][🧼][🔍] [⚫][🔵][🔴][🟢][🟣] │  │ [↩️][↪️][🔍%][🔒] ⋮ │
└─────────────────┘  └───────────────────────────────────────┘  └──────────────────────┘
```
- Back arrow: Return to home
- Title: Editable, inline within toolbar (tap to rename)
- Share/Add: Either inline (right cluster) **or** folded into overflow on narrow screens
- More (⋮): Search, Change template, Stylus & finger, View mode, Settings (and Share/Add if collapsed)

**Toolbar Layout (single row):**
- **Left pill**: Back + editable title
- **Center pill**: Hand, Pen, Highlighter, Eraser, Lasso
- **Inline colors**: 5 color dots integrated into the same row
- **Right pill**: Undo, Redo, Zoom (show %), Lock, Overflow (⋮)
- Pills use rounded/segmented chrome with subtle elevation and divider separators between major sections

**Behavior (Updated):**
- **Toolbar does NOT hide on canvas tap**; titlebar stays visible while drawing
- No FULL_UI/FOCUSED_UI collapse; no height change
- Optional: fade secondary actions to ~90% opacity during active drawing

**Color Dot Specifications (Inline in Toolbar):**
- **Size**: 24dp visual, 32dp touch target
- **Spacing**: 8dp between dots
- **Selected indicator**: 2dp white ring + 1dp elevation shadow
- **Selected dot scale**: 1.1x (26.4dp visual)
- **Unselected**: No ring, slight transparency (90% opacity)
- **NO label above color in toolbar**

**Color Dot Behavior:**
- Tap: Select color
- **Long-press**: Opens Samsung-style color picker to customize that slot
  - Duration: 500ms
  - Haptic feedback on press
  - Opens ColorPicker dialog (NOT inline editor)
- Default colors: Black, Blue, Red, Green, Purple
- User can customize each slot

**Quick Settings (Long-press Tools):**
- Long-press Pen: Opens Pen settings panel (thickness, stabilization)
- Long-press Highlighter: Opens Highlighter settings panel
- Long-press Eraser: Opens Eraser settings panel
- Stabilization and size adjusted via panels, not inline indicators

**Stabilization Quick Toggle:**
- Tap stabilization value: Opens slider popup (0-10)
- Visual: Number with small wave icon

#### VIEW_MODE (Read-only)

**Behavior:**
- Toolbar remains visible but disables drawing tools
- Show “Edit mode” button in toolbar (right side) for quick exit
- Back + title remain visible at all times

#### Tool Settings Panels (Samsung-style Floating)

**Panel Specifications:**
- **Type**: Notewise-style floating card (not Samsung dropdown)
- **Width**: **280dp** (NOT 320dp - too wide for phones)
- **Max height**: 400dp
- Background: `bgSecondary` with 16dp corners
- Shadow: 8dp elevation
- Padding: 16dp internal
- Backdrop: Semi-transparent scrim: Color.Black at 30% opacity
  - Tap scrim to dismiss
  - No scrim tap-through
- Dismiss: Tap outside, swipe down, or tap X

**Positioning Logic:**
1. Calculate anchor point = center of tool button that triggered it
2. **Default position**: Below anchor, left-aligned with button left edge
3. **If insufficient vertical space (<400dp below)**:
   - Flip to ABOVE toolbar
   - Maintain horizontal alignment
4. **Horizontal bounds checking**:
   - If would extend beyond screen right edge:
   - Shift left to maintain 16dp margin

**Comparison:**
- **Samsung Notes** (00:50, 01:00): Panel opens BELOW the toolbar as a dropdown
- **Notewise** (01:15): Panel opens as a floating card near the tool button
- **Chosen approach**: Notewise-style floating card

**Pen Settings Panel:**
```
┌────────────────────────────────┐
│  Pen Settings            [×]   │
├────────────────────────────────┤
│  ┌──────────────────────────┐  │
│  │     Stroke Preview       │  │  <- Curved line preview at top
│  │      ~~~~~~~~~~~~~       │  │     (animated sine-wave)
│  └──────────────────────────┘  │     Background: surfaceLight
├────────────────────────────────┤
│  [🖊️] [✏️] [🖋️]                 │  <- Pen style icons (not text)
│ Fountain Ball Calligraphy      │     Selected: filled bg
├────────────────────────────────┤
│  Toggle Section:               │
│  Line type: [────] [⭕]        │  <- Toggle: Stroke vs Shape detection
│  Hold to draw shape      [●/○] │  <- Switch (NOT in original plan)
│  Scribble to erase       [●/○] │  <- Switch (NOT in original plan)
├────────────────────────────────┤
│  Thickness              7      │  <- Label on left, value on RIGHT
│  ○━━━━━━━━━━━━━━━○             │     Slider 1-30
├────────────────────────────────┤
│  Stabilization          3      │  <- 0-10 with numeric display
│  ○━━━━━━━━━━━━━━━○             │
├────────────────────────────────┤
│  Pressure sensitivity   0      │  <- 0-10, default 0
│  ○━━━━━━━━━━━━━━━○             │
├────────────────────────────────┤
│  Colors:              [+] [↕]  │  <- Label with add/reorder buttons
│  ⚫🔴🟢🔵🟡🟣🟠⚪ [🌈]          │
│                                │
│  ┌──────────────────────────┐  │
│  │    Color Spectrum        │  │  <- Samsung-style picker
│  └──────────────────────────┘  │
├────────────────────────────────┤
│        [       Done       ]    │  <- Full width, accentBlue bg
└────────────────────────────────┘
```

**Panel Layout (VERTICAL stack):**

1. **Stroke Preview Area (80dp height)**
   - Shows a **curved sine-wave stroke** (not straight line)
   - Animates when settings change
   - Background: `surfaceLight` color
   
2. **Pen Style Selector (56dp height)**
   - Three **icon buttons** (not text): Fountain pen, Ball pen, Calligraphy pen
   - Selected state: Filled background with accentBlue
   - Unselected: Outline only
   
3. **Toggle Section (48dp per toggle)**
   - "Line type": Toggle between solid and dashed indicator
   - **"Hold to draw shape"**: Switch with info tooltip (NEW)
   - **"Scribble to erase"**: Switch with info tooltip (NEW)
   
4. **Sliders Section**
   - **Label format**: "LabelName            CurrentValue"
     - Value displayed on RIGHT side of label row
   - Thickness: 1-30, default 7
   - Pressure sensitivity: 0-10, default 0 (slider + numeric)
   - Stabilization: 0-10, default 1 (slider + numeric)
   
5. **Color Section**
   - Label: "Color" with [+] add and [reorder] buttons
   - Row of 8-10 color circles
   - Currently selected color has white ring indicator
   
6. **Done Button**
   - Full width, accentBlue background

**Highlighter Settings Panel:**
```
┌────────────────────────────────┐
│  Highlighter             [×]   │
├────────────────────────────────┤
│  ┌──────────────────────────┐  │
│  │     Preview              │  │
│  └──────────────────────────┘  │
├────────────────────────────────┤
│  Draw straight line            │
│  [Always] [Never] [Hold]       │  <- 3-state segmented button
│                                 │     (CRITICAL: NOT 2-state toggle)
├────────────────────────────────┤
│  Line tip appearance           │
│  Square ▼                      │  <- Dropdown (not toggle)
├────────────────────────────────┤
│  Thickness                     │
│  ○━━━━━━━━━━━━━━━○    20       │
├────────────────────────────────┤
│  Opacity                       │
│  ○━━━━━━━━━━━━━━━○    20%      │  <- Shows percentage
├────────────────────────────────┤
│  Colors (default yellows):     │
│  🟡🟠🟢🔵                       │  <- Different palette from pen
└────────────────────────────────┘
```

**Notes:**
- **"Draw straight line"**: 3-state segmented button (Always/Never/Hold), NOT 2-state toggle
- **"Line tip appearance"**: Dropdown with options, not toggle buttons
- **Opacity slider**: Shows percentage value AND visual opacity preview
- **Colors**: Highlighter has separate palette focused on yellows/oranges

**Eraser Settings Panel:**
```
┌────────────────────────────────┐
│  Eraser Settings         [×]   │
├────────────────────────────────┤
│  ┌──────────────────────────┐  │
│  │     Preview              │  │  <- Shows eraser cursor
│  └──────────────────────────┘  │
├────────────────────────────────┤
│  Toggles Section:              │
│  Erase entire object  [●━/━○]  │  <- ON by default
│  Erase locked objects [●━/━○]  │  <- OFF by default
│  Auto deselect        [●━/━○]  │  <- OFF by default
├────────────────────────────────┤
│  Size                          │
│  ○━━━━━━━━━━━━━━━○    10       │
│        ⭕                      │  <- Visual: Circle preview
├────────────────────────────────┤
│  Erase the following:          │  <- Toggle buttons (not just icons)
│  ┌────┐┌────┐┌────┐┌────┐     │
│  │ ✏️ ││ 🖍️ ││ ⭕ ││ 🖼️ │     │  <- Selected: accentBlue bg
│  │Pen ││High││Shap││Img │     │     Unselected: gray outline
│  └────┘└────┘└────┘└────┘     │
│  ┌────┐┌────┐┌────┐┌────┐     │
│  │ 🧼 ││ 🔤 ││ 🎭 ││ ➕ │     │  <- Eraser tool included
│  │Eras││Text││Stic││More│     │
│  └────┘└────┘└────┘└────┘     │
├────────────────────────────────┤
│  🔴 [Clear Current Page]       │  <- Red text, confirmation required
└────────────────────────────────┘
```

**Filter Section Details:**
- Label: "Erase the following"
- **Icon grid** (2 rows x 4 columns):
  - Row 1: Pen, Highlighter, Eraser tool, Lasso selection
  - Row 2: Shapes, Images, Text boxes, Stickers
- **Each icon**:
  - **Selected**: accentBlue background, white icon (toggle ON)
  - **Unselected**: Transparent background, gray icon (toggle OFF)
  - Toggle behavior on tap

**Size Section:**
- Slider: 1-100
- **Visual**: Circle preview showing eraser size relative to page

**Destructive Action:**
- "Clear current page" button
- Red text color
- **Confirmation dialog required**

---

### 3. Template Picker (Notewise-style)

**Access:** Overflow menu (⋮) → "Change template"

**Layout:**
```
┌─────────────────────────────────────────────┐
│  Template                           [×]     │
├─────────────────────────────────────────────┤
│  ┌──────────┐ ┌────────┐  Size: [...]▼      │  <- Tab pills + dropdown
│  │ Built-in │ │ Custom │                      │     (pills have rounded corners)
│  └──────────┘ └────────┘                      │     (selected pill is filled)
├─────────────────────────────────────────────┤
│  Background:                                │
│  ⚪  🟡  🟠  ⚪  🔘  ⚫                         │  <- 6 color circles
│  (selected has 2dp blue ring)               │
├─────────────────────────────────────────────┤
│  Density    ●━━━○━━━━○    3                 │  <- Blue fill left of thumb
│  Line width ○━━━●━━━━○    5                 │     Dots indicate 10 positions
│         ↑                                   │     Numeric value on right
│    dot markers (10 positions)               │
├─────────────────────────────────────────────┤
│  ▼ Basic                              4     │  <- Expandable sections
│  ┌────┐┌────┐┌────┐┌────┐                   │     (count badge on right)
│  │    ││    ││    ││    │                   │
│  │Blank│Rule│Grid│ Dot│                   │
│  └────┘└────┘└────┘└────┘                   │
│                                             │
│  ▶ Education                          5     │  <- Collapsed = right arrow
│                                             │
│  ▶ Music                              3     │
├─────────────────────────────────────────────┤
│        ┌────────────────────┐▼              │  <- Floating pill button
│        │ Apply to current pg│               │     with dropdown arrow
│        └────────────────────┘               │
└─────────────────────────────────────────────┘
```

**Tab Style (CRITICAL - differs from earlier spec):**
- **Pill-shaped segmented control** (not text tabs)
- Rounded corners (16dp radius)
- Selected pill: Filled with accentBlue background
- Unselected: Outline only

**Size Dropdown:**
- Shows full format: "A4 (2480 × 3508)" with dimensions
- Not just "A4"
- Dropdown arrow indicates expandable

**Slider Specifications:**
- **Track height**: 4dp
- **Active track** (left of thumb): accentBlue color (blue fill)
- **Inactive track** (right of thumb): surfaceLight color
- **Thumb size**: 16dp circle
- **Dot markers**: 1.5dp circles at 10 positions, surfaceLight color
- **Value range**: 1-10 (10 positions)
- **Numeric display**: Right-aligned, 24dp from slider end

**Apply Button:**
- Floating pill-shaped button at bottom (not full-width bar)
- Has dropdown arrow (▼) for "Apply to current" vs "Apply to all pages"
- Centered in bottom area

**Category Headers:**
- Arrow indicator (▼ expanded, ▶ collapsed)
- Template count badge on right
- "Basic", "Education", "Music" sections
- Default: Basic expanded, others collapsed
┌─────────────────────────────────────────┐
│  Template                      [×]      │
├─────────────────────────────────────────┤
│  [Built-in] [Custom]  Size: A4 ▼        │  <- Tabs + size dropdown
├─────────────────────────────────────────┤
│  Background:                            │
│  ⚪ 🟡 🟠 🔘 ⚫                         │  <- Color circles
├─────────────────────────────────────────┤
│  Density      ○━━━●━━━━○  5             │  <- Slider 1-10
│  Line width   ○━━━●━━━━○  5             │  <- Slider 1-10
├─────────────────────────────────────────┤
│  ▼ Basic                     (expanded) │
│  ┌────┐┌────┐┌────┐┌────┐              │
│  │Blank│Rule│Grid│ Dot│                │
│  └────┘└────┘└────┘└────┘              │
│                                         │
│  ▶ Education                            │
│  ┌────┐┌────┐┌────┐┌────┐┌────┐       │
│  │Hex ││Corn││Corn││Legl││Eng │       │
│  │Grid││ A  ││ B  ││    ││    │       │
│  └────┘└────┘└────┘└────┘└────┘       │
│                                         │
│  ▶ Music                                │
│  ┌────┐┌────┐┌────┐                    │
│  │Score│Tab │Score│                    │
│  └────┘└────┘└────┘                    │
├─────────────────────────────────────────┤
│  [     Apply to current page     ]      │  <- Primary button
└─────────────────────────────────────────┘
```

**Template Categories:**
- **Basic**: Blank, Rule (lined), Grid, Dot
- **Education**: Hexagonal Grid, Cornell A, Cornell B, Legal, Engineering
- **Music**: Music Score, Guitar Tablature, Guitar Score

**Size Options (dropdown):**
- A3 (3508 x 4961)
- A4 (2480 x 3508) - default
- A5 (1748 x 2480)
- A6 (1240 x 1748)
- A7 (874 x 1240) - "Suitable for phones"
- Custom (W/H input fields)

**Default Templates to Bundle:**
```kotlin
val defaultTemplates = listOf(
  Template("basic_blank", BASIC, "blank", "Blank", ...),
  Template("basic_rule", BASIC, "rule", "Rule", ...),
  Template("basic_grid", BASIC, "grid", "Grid", ...),
  Template("basic_dot", BASIC, "dot", "Dot", ...),
  Template("edu_hex", EDUCATION, "hexagonal", "Hexagonal Grid", ...),
  Template("edu_cornell_a", EDUCATION, "cornell_a", "Cornell A", ...),
  Template("edu_cornell_b", EDUCATION, "cornell_b", "Cornell B", ...),
  Template("edu_legal", EDUCATION, "legal", "Legal", ...),
  Template("music_score", MUSIC, "music_score", "Music Score", ...),
  Template("music_tab", MUSIC, "guitar_tab", "Guitar Tablature", ...)
)
```

---

### 4. Stylus & Finger Settings (Notewise-style)

**Access:** Overflow menu (⋮) → "Stylus & finger"

**Layout:**
```
┌─────────────────────────────────────────┐
│  Stylus & finger               [×]      │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │ ✒️ Stylus                       │    │  <- Card icon at top-left
│  │                                 │    │
│  │ Latency optimization  [Fast ▼] │    │  <- DROPDOWN (not toggle!)
│  │ Fast mode is experimental.      │    │     Shows: "Standard" or "Fast"
│  │ [Learn more]                    │    │  <- Blue link text
│  │                                 │    │
│  │ Primary action   [Switch to ▼] │    │  <- Dropdown with chevron
│  │                                 │    │     Shows selected value
│  │ Secondary action [Switch to ▼] │    │
│  │                                 │    │
│  │ Long hold on pr. [Hold to ▼]   │    │
│  │                                 │    │
│  │ Remap eraser     [Eraser ▼]    │    │
│  │ Supported actions vary...       │    │
│  │ [Learn more]                    │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ 👆 Single finger                │    │  <- Card icon
│  │                                 │    │
│  │ ○ Ignored                       │    │  <- Radio buttons
│  │ ○ Draw                          │    │     (not switches!)
│  │ ● Scroll   ← selected           │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ ✌️ Double finger                │    │  <- Card icon
│  │                                 │    │
│  │ ○ Ignored                       │    │  <- Radio buttons
│  │ ● Zoom and pan  ← selected      │    │
│  │ ○ Scroll                        │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ ✨ More gestures                │    │  <- Card icon
│  │                                 │    │
│  │ Double tap zoom       [●━/━○]  │    │  <- Toggle switch
│  │                                 │    │
│  │ Undo               Double tap   │    │
│  │                    with 2 fingers│   │
│  │ Redo               Double tap   │    │
│  │                    with 3 fingers│   │
│  │                                 │    │
│  │ Multi-tap gestures are not      │    │  <- Gray helper text
│  │ available when drawing...       │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ ⌨️ Keyboard                     │    │  <- Card icon
│  │ Keyboard shortcuts      [>]    │    │  <- Navigation link
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**CRITICAL CORRECTIONS:**

1. **Latency optimization is a DROPDOWN**, not a toggle:
   - Shows: "Standard" or "Fast (Experimental)"
   - Dropdown indicator (▼) on right
   - Info icon (ⓘ) with tooltip available

2. **Finger gestures use RADIO BUTTONS**, not switches:
   - Single finger: 3 radio options
   - Double finger: 3 radio options
   - Selected option has filled circle

3. **Each card has a distinct icon** at top-left:
   - Stylus: ✒️ (pen icon)
   - Single finger: 👆 (finger tap)
   - Double finger: ✌️ (two fingers)
   - More gestures: ✨ (hand with sparkles)
   - Keyboard: ⌨️ (keyboard)

4. **"Learn more" links** are blue text (accentBlue color)

5. **Card Specifications:**
   - Background: bgSecondary
   - Corner radius: 16dp
   - Padding: 16dp
   - Margin between cards: 12dp
   - Title: titleSmall typography, textPrimary color
   - Icon: 24dp, textSecondary color
┌─────────────────────────────────────────┐
│  Stylus & finger               [×]      │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │ Stylus                          │    │
│  │                                 │    │
│  │ Latency optimization            │    │
│  │ [Standard] [Fast (Exp.)]        │    │  <- Toggle group
│  │                                 │    │
│  │ Primary action                  │    │
│  │ Switch to eraser ▼              │    │  <- Dropdown
│  │                                 │    │
│  │ Secondary action                │    │
│  │ Switch to last used ▼           │    │
│  │                                 │    │
│  │ Long hold on primary button     │    │
│  │ Hold to erase ▼                 │    │
│  │                                 │    │
│  │ Remap eraser on stylus          │    │
│  │ Eraser ▼                        │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Single finger                   │    │
│  │ ○ Ignored                       │    │
│  │ ○ Draw                          │    │
│  │ ● Scroll                        │    │  <- Radio group
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Double finger                   │    │
│  │ ○ Ignored                       │    │
│  │ ● Zoom and pan                  │    │
│  │ ○ Scroll                        │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ More gestures                   │    │
│  │ Double tap to change zoom       │    │
│  │ [Toggle]                        │    │
│  │                                 │    │
│  │ Undo                  Double tap│    │
│  │                       with two fingers│
│  │ Redo                  Double tap│    │
│  │                       with three│    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**Stylus Button Actions (dropdown options):**
- Switch to eraser
- Switch to last used tool
- Hold to erase
- (Secondary button only): None

---

### 5. Zoom Controls (Notewise-style)

**Access:** Tap zoom indicator button in toolbar

**Zoom Popup Menu:**
```
50%
100%
200%
300%
400%
─────────────
[⤢] Zoom to fit content
[🔒] Lock zoom
```

**Current Zoom Display:**
- Shows in bottom-right corner: "147%"
- Real-time update as zoom changes

**Zoom Popup Specifications:**
- Trigger: Tap on zoom button in toolbar
- Preset percentages: 50%, 100%, 200%, 300%, 400%
- "Zoom to fit content" option with icon
- "Lock zoom" option with lock icon
- Divider between presets and special options
- Tap outside to dismiss

**Zoom Behavior:**
- Current zoom displayed in toolbar: shows percentage (e.g., "147%")
- Pinch gesture on canvas adjusts zoom
- Double-tap to zoom in/out (if enabled in settings)
- Zoom persists per note

---

### 6. View/Edit Mode Toggle

**Access:** Overflow menu (⋮) → "View mode"

**View Mode Behavior:**
- **Enters read-only mode** for reviewing notes
- Unified toolbar remains visible; drawing tools are disabled
- Show "Edit mode" button in the toolbar for quick exit
- Disables all drawing/editing interactions
- Shows toast notification: "You're now in view mode."

**To Exit View Mode:**
- Tap canvas area
- Tap "Edit mode" button
- Toast notification: "Edit mode enabled."

**Use Cases:**
- Presenting notes without accidental edits
- Reading notes without UI clutter
- Reviewing before sharing

---

### 7. Page Navigation

**Page Indicator:**
- Location: Bottom-right corner (or left if scrollbar preference set)
- Format: "1 / 2" (current page / total pages)
- Tappable to open "Go to page" dialog

**"Go to page" Dialog:**
```
┌─────────────────────────────────┐
│ Go to Page             [×]     │
├─────────────────────────────────┤
│                                 │
│     [        5        ] / 12   │
│                                 │
│         ○━━━━━━━●━━━━○        │
│                                 │
├─────────────────────────────────┤
│     [Cancel]    [Go]           │
└─────────────────────────────────┘
```

**Dialog Specifications:**
- Number input field with current page
- Shows "/ totalPages" suffix
- Slider for quick navigation
- Soft keyboard opens automatically
- Invalid numbers show error state
- "Go" navigates to page, "Cancel" dismisses

---

### 8. Settings Screen (Notewise Card-based)

**Layout:**
```
┌─────────────────────────────────────────┐
│  ←  Settings                            │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │ General                         │    │
│  │                                 │    │
│  │ New note name                   │    │
│  │ Untitled 2026-08-19     >       │    │
│  │                                 │    │
│  │ Continue from last opened page  │    │
│  │ [Toggle: ON]                    │    │
│  │                                 │    │
│  │ Keep screen on                  │    │
│  │ [Toggle: ON]                    │    │
│  │                                 │    │
│  │ Hide all system bars            │    │
│  │ [Toggle: ON]                    │    │
│  │                                 │    │
│  │ Position for scrollbar          │    │
│  │ Left ▼                    >     │    │  <- Left/Right option
│  │                                 │    │
│  │ Toolbox                         │    │
│  │                         >       │    │
│  │                                 │    │
│  │ Link                            │    │
│  │                         >       │    │
│  │                                 │    │
│  │ Snap to align                   │    │
│  │                         >       │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Security                        │    │
│  │                                 │    │
│  │ Password                        │    │
│  │ Lock important notes...   >     │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Manage storage                  │    │
│  │                                 │    │
│  │ Notes           11.53 MB        │    │
│  │ Cache           340 kB          │    │
│  │                                 │    │
│  │                 [Clear cache]   │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ About                           │    │
│  │ Terms of service                │    │
│  │ Privacy policy                  │    │
│  │ Help                            │    │
│  │ Share Diagnostic Data           │    │
│  │ Version 0.1.0                   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**Key Setting: Scrollbar Position**
- Options: Left, Right
- Default: Right
- Affects: The floating page/scroll indicator position

---

### 6. Color Picker Dialog (Samsung-style - Superior)

**Full-Screen Color Picker:**
```
┌──────────────────────────────────────────────────────────────────┐
│  Color Picker                                       [×]          │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [   Swatches   ] [   Spectrum   ]                              │
│  ────────────────────────                                        │  <- Underline under selected tab
│                                                                  │
│     ┌──────────────────────────────────────────────────┐        │
│     │                                                  │        │
│     │            COLOR SPECTRUM GRADIENT               │        │  <- In Spectrum tab
│     │              HSV PICKER                          │        │     (large gradient area)
│     │                                                  │        │
│     └──────────────────────────────────────────────────┘        │
│                                                                  │
│     ┌──────────────────────────────────────────────────┐        │
│     │  PRESET COLOR GRID (16 colors, 2 rows x 8)       │        │  <- In Swatches tab
│     └──────────────────────────────────────────────────┘        │     (no spectrum)
│                                                                  │
│  Hex:  [#FF3636    ] [📋]                                       │  <- Copy button
│         R: 255  G: 54  B: 54                                     │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│  Recent:  🔴 🟢 🔵 🟡 🟣 ⚫ ⚪ 🟠                                  │  <- Last 8 used colors
│                                                                  │
│  Presets:                                                       │
│  [⚫][⚪][🔴][🟢][🔵][🟡][🟣][🟠]                                  │  <- Row 1: basic colors
│  [⬛][🩶][🩷][🩵][🟤][🟨][🟩][🟦]                                  │  <- Row 2: pastels
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│              [    Cancel    ] [     Done     ]                  │
└──────────────────────────────────────────────────────────────────┘
```

**Tab Behavior:**
- Two tabs at TOP: "Swatches" and "Spectrum"
- **Selected tab** has underline indicator (accentBlue)
- **Swatches tab**: Shows only preset grid (no spectrum area)
- **Spectrum tab**: Shows full spectrum picker + presets at bottom
- Recent colors shown in BOTH tabs

**Hex Input Specifications:**
- Field shows: `#FF3636` format with # prefix
- **Auto-format**: Add # prefix if user types without it
- **Copy button** next to field (📋 icon)
- Valid characters: 0-9, A-F, a-f
- Max length: 7 characters (# + 6 hex digits)
- **Invalid input**: Show error state (red border)
- Updates picker in real-time

**Recent Colors:**
- Horizontal row of 8 color circles
- Persists last 8 used colors across sessions
- Stored in UserPreferenceEntity with RECENT_COLORS key
- Tap to select

**Preset Grid:**
- 2 rows x 8 columns = 16 preset colors
- Row 1: Basic colors (black, white, red, green, blue, yellow, purple, orange)
- Row 2: Pastels (gray, pink, light blue, brown, etc.)
- Tap to select

**Features:**
- Two tabs: "Swatches" (preset grid) and "Spectrum" (HSV picker)
- Recent colors row (last 8 used)
- Hex input field with copy/paste
- RGB values displayed (Red: 255, Green: 54, Blue: 54)
- Preview dot shows selected color
- **Real-time validation** of hex input

---

## Component Specifications

### 1. UnifiedEditorToolbar Component (Title + Tools)

```kotlin
@Composable
fun UnifiedEditorToolbar(
  noteTitle: String,
  onTitleChange: (String) -> Unit,
  onBack: () -> Unit,
  onOverflowClick: () -> Unit, // Settings + more actions
  isViewMode: Boolean,
  onExitViewMode: () -> Unit,
  activeTool: ToolType,
  quickColors: List<Color>,
  selectedColor: Color,
  onToolClick: (ToolType) -> Unit,
  onToolLongClick: (ToolType) -> Unit, // Opens settings panel
  onColorClick: (Color) -> Unit,
  onColorLongClick: (Int) -> Unit, // Index for customization (opens ColorPicker)
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  canUndo: Boolean,
  canRedo: Boolean,
  onZoomClick: () -> Unit,
  onLockToggle: () -> Unit,
  modifier: Modifier = Modifier
)
```

**Layout:**
- **Single horizontal toolbar system** composed of floating pill groups (title, tools/colors, actions)
- Back + editable title in left pill, tools/colors in center pill, actions + overflow in right pill
- No separate top bar or bottom toolbar
- Height: **48dp (fixed, always visible)**

**Animation:**
- Optional: subtle opacity fade on secondary actions during active drawing (no height change)
- Scale bounce on tool selection
- **NO hiding** of toolbar or titlebar

### 2. FloatingToolPanel Component (Samsung-style)

```kotlin
@Composable
fun FloatingToolPanel(
  toolType: ToolType,
  settings: ToolSettings,
  onSettingsChange: (ToolSettings) -> Unit,
  onDismiss: () -> Unit,
  anchorPosition: Offset // For popup positioning
)
```

**Positioning:**
- Anchored to toolbar button that triggered it
- Smart positioning: flip up if not enough space below
- Backdrop dismiss

### 3. TemplatePicker Component

```kotlin
@Composable
fun TemplatePicker(
  templates: List<Template>,
  selectedTemplate: Template?,
  selectedSize: PageSize,
  selectedBgColor: Color,
  density: Int,
  lineWidth: Int,
  onTemplateSelect: (Template) -> Unit,
  onSizeChange: (PageSize) -> Unit,
  onBgColorChange: (Color) -> Unit,
  onDensityChange: (Int) -> Unit,
  onLineWidthChange: (Int) -> Unit,
  onApply: () -> Unit,
  onDismiss: () -> Unit
)
```

### 4. StylusSettingsPanel Component

```kotlin
@Composable
fun StylusSettingsPanel(
  config: StylusConfig,
  onConfigChange: (StylusConfig) -> Unit,
  onDismiss: () -> Unit
)
```

### 5. MovableScrollbar Component

```kotlin
@Composable
fun MovableScrollbar(
  position: ScrollbarPosition, // LEFT or RIGHT
  currentPage: Int,
  totalPages: Int,
  onPageChange: (Int) -> Unit,
  modifier: Modifier = Modifier
)
```

**Behavior:**
- Floats on left or right edge based on preference
- Auto-hides after 2s of inactivity
- Tap to show "Go to page" dialog
- Drag to scroll quickly

---

## Animation Specifications

### Transition: Home -> Editor
- Duration: 300ms
- Shared element: Note thumbnail scales to full screen
- Unified toolbar (title + tools) fades in at top (Samsung Notes inline colors)
- Back button slides in from left

### Toolbar Focus Behavior (Updated)
- **No slide-up/hide**: unified toolbar stays pinned at the top
- Optional: fade secondary actions to ~90% opacity while drawing
- Restore full opacity on idle (200ms)

### Tool Panel Open
- Duration: 250ms
- Scale: 0.9 -> 1.0 (spring)
- Alpha: 0 -> 1
- Backdrop: Fade in

### Color Selection
- Duration: 150ms
- Selected dot: Scale 1.0 -> 1.2 -> 1.0 (bounce)
- Ring: Stroke width animate in

### Template Picker Open
- Duration: 300ms
- Slide up from bottom (sheet behavior)
- Or fade in with scale for dialog variant

---

## Verification Strategy

### Test Infrastructure
- **Framework**: Android Unit Tests (JUnit) + Instrumented Tests
- **Existing Pattern**: `apps/android/app/src/test/java/` for unit tests
- **Existing Pattern**: `apps/android/app/src/androidTest/java/` for instrumented tests
- **Test Commands**: `gradlew.bat :app:test` (unit), `gradlew.bat :app:connectedTest` (instrumented)

### Manual QA Procedures
For UI components, use Playwright-style verification:
1. Build debug APK: `gradlew.bat :app:assembleDebug`
2. Install on device/emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Navigate to screen and verify visual/interactive behavior
4. Screenshot evidence saved to `.sisyphus/evidence/`

---

## Task Dependencies & Parallelization

```
Phase 0 (Design System):
  0.1 → Must complete first (all UI tasks depend on color tokens)

Phase 1 (Foundation):
  1.1-1.5 → Can run in parallel (entity definitions) - Depends on 0.1
  1.6-1.8 → Can run in parallel (DAO definitions)
  1.9-1.11 → Depends on 1.1-1.8

Phase 2 (Home Screen):
  2.1-2.3 → Can run in parallel (component creation)
  2.4 → Depends on 2.1-2.3 (screen assembly)
  2.5-2.8 → Depends on 2.4

Phase 3 (Editor):
  3.1-3.2 → Can run in parallel
  3.3 → Depends on 3.1
  3.4-3.9 → Can run in parallel after 3.3
  3.10 → Depends on all above

Phase 4-7: Sequential phases (each depends on prior)
```

---

## Implementation Tasks

### Phase 0: Design System Setup

- [x] 0.1 Add design system color tokens to Color.kt

  **What to do**:
  - Add all 9 new color tokens to `apps/android/app/src/main/java/com/onyx/android/ui/theme/Color.kt`
  - Tokens to add: bgPrimary, bgSecondary, bgTertiary, surface, surfaceLight, textPrimary, textSecondary, accentBlue, divider
  - Use camelCase naming (new convention) alongside existing PascalCase tokens
  - Values per Design System section above

  **Must NOT do**:
  - Do not modify existing color values (only add new ones)
  - Do not create a separate theme file (add to existing Color.kt)

  **Parallelizable**: NO (Foundation task - must complete first)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/theme/Color.kt` - Existing color definitions to extend
  - Plan section "Design System > Color Palette" - Token names and hex values

  **Acceptance Criteria**:
  - [ ] Color.kt contains all 9 new tokens with correct hex values
  - [ ] bgPrimary = Color(0xFF000000)
  - [ ] bgSecondary = Color(0xFF1C1C1E)
  - [ ] bgTertiary = Color(0xFF2C2C2E)
  - [ ] surface = Color(0xFF3A3A3C)
  - [ ] surfaceLight = Color(0xFF48484A)
  - [ ] textPrimary = Color(0xFFFFFFFF)
  - [ ] textSecondary = Color(0xFF98989D)
  - [ ] accentBlue = Color(0xFF0A84FF)
  - [ ] divider = Color(0xFF38383A)
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
    - Message: `feat(theme): add design system color tokens`
    - Files: `Color.kt`

- [x] 0.2 Update OnyxApplication to use OnyxDatabase.build()

  **What to do**:
  - **MUST COMPLETE BEFORE Phase 1** - All database tasks depend on this
  - Update OnyxApplication.kt:34-39 to use `OnyxDatabase.build(applicationContext)`
  - Move database configuration from OnyxApplication to OnyxDatabase.build()
  - Ensure OnyxDatabase.build() is the single source of truth for DB config

  **Current State** (OnyxApplication.kt:34-39):
  ```kotlin
  database = Room.databaseBuilder(
      applicationContext,
      OnyxDatabase::class.java,
      OnyxDatabase.DATABASE_NAME,
  ).build()
  ```

  **Target State**:
  ```kotlin
  database = OnyxDatabase.build(applicationContext)
  ```

  **Must ALSO update OnyxDatabase.build()** (OnyxDatabase.kt:51):
  - Move existing MIGRATION_1_2 into build() method
  - Ensure build() returns properly configured RoomDatabase

  **Parallelizable**: NO (Foundation task - must complete before Phase 1)

  **References**:
    - `apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt:34-39` - Current direct DB build
    - `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt:51` - OnyxDatabase.build() helper

  **Acceptance Criteria**:
    - [ ] OnyxApplication.kt:34-39 uses `OnyxDatabase.build(applicationContext)`
    - [ ] OnyxDatabase.build() contains MIGRATION_1_2 (moved from where it was)
    - [ ] App builds and runs: `gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL
    - [ ] Manual QA: App launches without database errors

  **Commit**: YES
    - Message: `refactor(db): use OnyxDatabase.build() in OnyxApplication`
    - Files: `OnyxApplication.kt`, `OnyxDatabase.kt`

### Phase 1: Foundation (Database & Models)

- [x] 1.1 Create FolderEntity and migration

  **What to do**:
  - Create `FolderEntity.kt` with fields: folderId, parentFolderId, name, color, icon, sortOrder, createdAt, updatedAt
  - Add database migration in OnyxDatabase for new `folders` table
  - Add entity to @Database annotation

  **Must NOT do**:
  - Do not modify existing table schemas
  - Do not add complex query logic in entity (that goes in DAO)

  **Parallelizable**: YES (with 1.2, 1.3, 1.4, 1.5)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteEntity.kt` - Entity pattern with @PrimaryKey, @Entity annotation
  - `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt:57-79` - Migration pattern (MIGRATION_1_2 example)
  - `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt:24-35` - @Database annotation with entities list
  - Plan section "Database Construction Strategy" - OnyxApplication uses OnyxDatabase.build()

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/entity/FolderEntity.kt`
  - [ ] Entity has @Entity(tableName = "folders") annotation
  - [ ] Migration MIGRATION_2_3 (or next version) creates folders table
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL
  - [ ] Database version incremented in @Database annotation

  **Commit**: YES
  - Message: `feat(db): add FolderEntity with migration`
  - Files: `FolderEntity.kt`, `OnyxDatabase.kt`

- [x] 1.2 Create NoteFolderEntity (junction table)

  **What to do**:
  - Create junction table entity for many-to-many note-folder relationship
  - Use composite primary key (noteId, folderId)
  - Add foreign key constraints

  **Must NOT do**:
  - Do not cascade delete notes when folder deleted (orphan to root instead)

  **Parallelizable**: YES (with 1.1, 1.3, 1.4, 1.5)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteEntity.kt` - Entity structure pattern
  - Plan section "Architecture Changes > New Database Entities" - NoteFolderEntity spec

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteFolderEntity.kt`
  - [ ] Has composite @PrimaryKey for noteId + folderId
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 1.1

- [x] 1.3 Create TemplateEntity with default templates

  **What to do**:
  - Create TemplateEntity with category, name, displayName, previewResId, bgColors, defaultDensity, defaultLineWidth
  - Create database prepopulation using Room's `RoomDatabase.Callback` with `onCreate()`
  - Default templates: Blank, Rule, Grid, Dot (Basic); Hexagonal, Cornell A/B, Legal (Education); Music Score, Guitar Tab (Music)

  **Must NOT do**:
  - Do not include actual template image assets in this task (separate task)

  **Parallelizable**: YES (with 1.1, 1.2, 1.4, 1.5)

  **References**:
  - Plan section "Template Picker > Default Templates to Bundle" - Template list
  - `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt:51-55` - Database builder location (add callback here)
  - Plan section "Database Construction Strategy" - OnyxApplication uses OnyxDatabase.build()
  - **Prepopulation mechanism** (raw SQL in callback - DAOs unavailable during onCreate):
    ```kotlin
    // Add to OnyxDatabase.build():
    .addCallback(object : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Use raw SQL execSQL() - DAOs not available yet
        db.execSQL("""
          INSERT INTO templates (templateId, category, name, displayName, previewResId, bgColors, defaultDensity, defaultLineWidth, sortOrder)
          VALUES 
            ('basic_blank', 'BASIC', 'blank', 'Blank', null, '[\"#FFFFFF\"]', 5, 5, 0),
            ('basic_rule', 'BASIC', 'rule', 'Rule', null, '[\"#FFFFFF\"]', 5, 5, 1),
            ('basic_grid', 'BASIC', 'grid', 'Grid', null, '[\"#FFFFFF\"]', 5, 5, 2),
            ('basic_dot', 'BASIC', 'dot', 'Dot', null, '[\"#FFFFFF\"]', 5, 5, 3)
            -- Add remaining templates...
        """)
      }
    })
    ```
  - Room callback docs: https://developer.android.com/reference/androidx/room/RoomDatabase.Callback

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/entity/TemplateEntity.kt`
  - [ ] Entity includes category enum storage as String
  - [ ] bgColors stored as JSON string (comma-separated hex)
  - [ ] OnyxDatabase.build() includes `.addCallback()` for prepopulation
  - [ ] Callback uses `db.execSQL()` with raw INSERT statements (not DAOs)
  - [ ] All 10 default templates inserted on first database creation
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
  - Message: `feat(db): add TemplateEntity for page templates`
  - Files: `TemplateEntity.kt`, `OnyxDatabase.kt`

- [x] 1.4 Create StylusPreferenceEntity

  **What to do**:
  - Create entity for stylus configuration persistence
  - Fields: deviceId (PK), primaryButtonAction, secondaryButtonAction, latencyOptimization, singleFingerAction, doubleFingerAction

  **Parallelizable**: YES (with 1.1, 1.2, 1.3, 1.5)

  **References**:
  - Plan section "Architecture Changes > StylusPreferenceEntity" - Full spec
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteEntity.kt` - Entity pattern

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/entity/StylusPreferenceEntity.kt`
  - [ ] deviceId has default "default" for single-device use
  - [ ] Action fields stored as String (enum serialization)
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 1.1

- [x] 1.5 Create UserPreferenceEntity with new keys

  **What to do**:
  - Create generic key-value preference entity for app settings
  - Document preference keys as constants (PreferenceKeys object)
  - Keys: SCROLLBAR_POSITION, STABILIZATION_LEVEL, QUICK_COLOR_1-5, LAST_TEMPLATE, DEFAULT_PAGE_SIZE

  **Parallelizable**: YES (with 1.1, 1.2, 1.3, 1.4)

  **References**:
  - Plan section "Preference Keys" - Full key list with values
  - Plan section "SharedPreferences Migration Plan" - Migration strategy for existing prefs
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:168-189` - Current SharedPreferences usage (getSharedPreferences, putBoolean, putFloat patterns)

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/entity/UserPreferenceEntity.kt`
  - [ ] PreferenceKeys object exists with all documented constants
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 1.1

- [x] 1.6 Create FolderDao with hierarchy queries

  **What to do**:
  - Create DAO with getRootFolders(), getSubfolders(parentId), getAllFolders()
  - Include getNoteCount(folderId) for display
  - All queries return Flow for reactive updates

  **Must NOT do**:
  - Do not implement complex recursive tree fetching in single query (Room limitation)

  **Parallelizable**: YES (with 1.7, 1.8) - Depends on 1.1

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/data/dao/NoteDao.kt` - DAO pattern with @Query, Flow returns
  - Plan section "DAO Additions > FolderDao" - Full query specs

  **Acceptance Criteria**:
    - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/dao/FolderDao.kt`
    - [ ] getRootFolders() returns `Flow<List<FolderEntity>>`
    - [ ] getSubfolders() takes parentId parameter
    - [ ] deleteById(folderId: String) method exists (for cascade delete)
    - [ ] getChildFolderIds(parentIds: List<String>) method exists (for recursive delete)
    - [ ] getChildCount(folderId: String) method exists (for chevron display)
    - [ ] Abstract function added to OnyxDatabase: `abstract fun folderDao(): FolderDao`
    - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
  - Message: `feat(db): add FolderDao for folder hierarchy`
  - Files: `FolderDao.kt`, `OnyxDatabase.kt`

- [x] 1.7 Create TemplateDao

  **What to do**:
  - Create DAO with getAllTemplates(), getTemplatesByCategory()
  - Insert methods for default template population

  **Parallelizable**: YES (with 1.6, 1.8) - Depends on 1.3

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/data/dao/NoteDao.kt` - DAO pattern
  - Plan section "DAO Additions > TemplateDao" - Query specs

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/dao/TemplateDao.kt`
  - [ ] getAllTemplates() ordered by category, sortOrder
  - [ ] Abstract function added to OnyxDatabase
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 1.6

- [x] 1.8 Create StylusPreferenceDao

  **What to do**:
  - Create DAO with getForDevice(deviceId), set(prefs) with REPLACE strategy

  **Parallelizable**: YES (with 1.6, 1.7) - Depends on 1.4

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/data/dao/NoteDao.kt:18-19` - Insert with OnConflictStrategy
  - Plan section "DAO Additions > StylusPreferenceDao"

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/dao/StylusPreferenceDao.kt`
  - [ ] set() uses OnConflictStrategy.REPLACE
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 1.6

- [x] 1.9 Update NoteRepository with folder operations

  **What to do**:
  - Add folder CRUD methods: createFolder, updateFolder, deleteFolder, moveFolder
  - Add methods: getNotesInFolder, moveNoteToFolder, addNoteToFolder, removeNoteFromFolder
  - Handle orphaning notes to root when folder deleted
  - Implement recursive subfolder deletion per "Subfolder Cascade Delete Mechanism" section

  **Must NOT do**:
  - Do not cascade delete notes (soft-orphan instead)
  - Do not allow circular folder references (validate before move)

  **Parallelizable**: NO - Depends on 1.6

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt:50-65` - createNote pattern (generate ID, insert, return)
  - `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt:209-224` - @Transaction annotation usage
  - Plan section "Subfolder Cascade Delete Mechanism" - Recursive delete implementation with code
  - Plan section "Folder Membership Behavior (Move vs Link)" - Move semantics (clears existing, inserts new)

  **Acceptance Criteria**:
  - [ ] NoteRepository has createFolder() returning Folder
  - [ ] moveNoteToFolder() deletes existing note_folders rows, then inserts new (per Move semantics)
  - [ ] addNoteToFolder() only inserts without deleting (per Link semantics)
  - [ ] deleteFolder() recursively collects descendant IDs then deletes deepest-first (per plan section)
  - [ ] deleteFolder() orphans notes to root (FK CASCADE on note_folders handles this)
  - [ ] `gradlew.bat :app:test` → All existing tests still pass
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
  - Message: `feat(repo): add folder operations to NoteRepository`
  - Files: `NoteRepository.kt`

- [x] 1.10 Create Folder domain model and mappers

  **What to do**:
  - Create Folder data class with derived fields (noteCount, hasSubfolders)
  - Create FolderIcon enum
  - Create entity↔domain mappers

  **Parallelizable**: NO - Depends on 1.1

  **References**:
  - Plan section "Data Models > Folder.kt" - Domain model spec
  - `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt` - Domain model pattern

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/model/Folder.kt`
  - [ ] FolderIcon enum with FOLDER, SCHOOL, WORK, PERSONAL, PROJECTS, ARCHIVE
  - [ ] Mapper extension functions: `FolderEntity.toDomain()`, `Folder.toEntity()`
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 1.9

- [x] 1.11 Add stabilization and eraserFilters to ToolSettings

  **What to do**:
  - Create/update ToolSettings data class with stabilizationLevel (0-10), eraserFilters
  - Create EraserFilters data class with tool type toggles
  - Create PenStyle, HighlighterStyle, LineType enums

  **Parallelizable**: NO - Depends on completion of entity layer

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ink/model/Brush.kt` - Current brush model to extend
  - `apps/android/app/src/main/java/com/onyx/android/ui/components/EraserMode.kt` - Current eraser mode
  - Plan section "Data Models > ToolSettings.kt" - Full spec

  **Acceptance Criteria**:
  - [ ] ToolSettings.kt created with all fields from plan spec
  - [ ] EraserFilters includes: pen, highlighter, shapes, images, text, stickers booleans
  - [ ] stabilizationLevel: Float with range 0f-10f
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
    - Message: `feat(model): add ToolSettings with stabilization and eraser filters`
    - Files: `ToolSettings.kt`, `EraserFilters.kt`

- [x] 1.12 Create PreferenceMigrator for SharedPreferences → Room migration

  **What to do**:
  - Create `PreferenceMigrator.kt` utility class
  - Migrate existing SharedPreferences to Room BEFORE any screen access
  - Run in OnyxApplication.onCreate() after DB init, BEFORE repository init
  - Set migration flag to prevent re-runs and race conditions

  **Must NOT do**:
  - Do NOT run migration after repositories are initialized (race condition risk)
  - Do NOT delete original SharedPreferences (backward compatibility)

  **Parallelizable**: NO - Depends on 1.5 (UserPreferenceEntity must exist)

  **References**:
    - Plan section "SharedPreferences Migration Plan" - Full timing/ordering spec
    - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:168-189` - Current SharedPreferences usage
    - OnyxApplication.kt:34-39 - Where to call migrator

  **Migration Implementation Pattern**:
  ```kotlin
  object PreferenceMigrator {
    private const val MIGRATION_FLAG = "prefs_migrated_to_room_v1"
    
    suspend fun migrateIfNeeded(context: Context, userPreferenceDao: UserPreferenceDao) {
      val prefs = context.getSharedPreferences("onyx_prefs", Context.MODE_PRIVATE)
      
      // Check if already migrated
      if (prefs.getBoolean(MIGRATION_FLAG, false)) return
      
      // Perform migration
      val showOverlay = prefs.getBoolean("show_overlay", false)
      val opacity = prefs.getFloat("opacity", 0.3f)
      val fontSize = prefs.getInt("font_size", 16)
      
      // Insert into Room
      userPreferenceDao.set(UserPreferenceEntity(PreferenceKeys.SHOW_OVERLAY, showOverlay.toString()))
      userPreferenceDao.set(UserPreferenceEntity(PreferenceKeys.OVERLAY_OPACITY, opacity.toString()))
      userPreferenceDao.set(UserPreferenceEntity(PreferenceKeys.OVERLAY_FONT_SIZE, fontSize.toString()))
      
      // Set flag IMMEDIATELY to prevent race conditions
      prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
    }
  }
  ```

  **Acceptance Criteria**:
    - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/data/migration/PreferenceMigrator.kt`
    - [ ] Migrator runs in OnyxApplication.onCreate() BEFORE repository init
    - [ ] Migration flag set immediately after completion
    - [ ] Shows "Migrating preferences..." log during migration
    - [ ] Manual QA: Install app with old SharedPreferences → verify values appear in Room

  **Commit**: YES
    - Message: `feat(db): add SharedPreferences to Room migration`
    - Files: `PreferenceMigrator.kt`, `OnyxApplication.kt`

### Phase 2: Home Screen Redesign

- [x] 2.1 Create FolderTree component

  **What to do**:
  - Create composable that renders hierarchical folder list
  - Support expand/collapse with chevron icons
  - Show folder icon, name, note count
  - Implement selection state highlighting
  - Support indentation (24dp per depth level)

  **Must NOT do**:
  - Do not load all folders at once (use lazy loading per "Folder Tree Lazy Loading Strategy" section)
  - Do not handle folder CRUD in this component (delegate to ViewModel)

  **Parallelizable**: YES (with 2.2, 2.3)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:288-319` - LazyColumn with items pattern
  - Plan section "Home Screen > Sidebar Content" - Visual spec
  - Plan section "Folder Item Component" - 48dp height, indent, color specs
  - Plan section "Folder Tree Lazy Loading Strategy" - Loading strategy with FolderTreeState structure

  **Acceptance Criteria**:
    - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/folder/FolderTree.kt`
    - [ ] Renders LazyColumn of FolderItem composables
    - [ ] Only root folders loaded initially (verify: DAO getRootFolders() called on mount)
    - [ ] Child folders loaded on-demand when parent expanded (verify: getChildFolders() called on expand)
    - [ ] Selected folder has `accentBlue` at 15% opacity background with 8dp pill shape + `accentBlue` text color (per "Folder Item Component" spec)
    - [ ] Indent is 24dp per depth level (not 16dp)
    - [ ] Chevron rotates on expand/collapse
    - [ ] Manual QA: Launch app → Folder sidebar renders with test folders

  **Commit**: YES
  - Message: `feat(ui): add FolderTree component for sidebar navigation`
  - Files: `FolderTree.kt`, `FolderItem.kt`

- [x] 2.2 Create NoteCard component with selection

  **What to do**:
  - Create card composable with thumbnail, title, date
  - Aspect ratio 3:4
  - Support multi-select with checkbox overlay
  - Long-press to enter selection mode

  **Must NOT do**:
  - Do not generate thumbnails in this component (receive as parameter)

  **Parallelizable**: YES (with 2.1, 2.3)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:292-316` - Current Card pattern
  - Plan section "Note Card Component" - Visual spec with thumbnail, title, date

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/note/NoteCard.kt`
  - [ ] Card background uses bgSecondary with divider border
  - [ ] Corner radius 12dp
  - [ ] Long-press triggers onSelectionModeStart callback
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 2.1

- [x] 2.3 Create Breadcrumb component

  **What to do**:
  - Create horizontal breadcrumb showing folder path
  - Each segment is tappable for navigation
  - Separator character: ">"
  - Truncate middle segments if too long

  **Parallelizable**: YES (with 2.1, 2.2)

  **References**:
  - Plan section "Right Pane: Note Grid > Header" - Visual spec
  - `apps/android/app/src/main/java/com/onyx/android/ui/components/BrushToolbar.kt:177-207` - Row layout pattern

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/folder/Breadcrumb.kt`
  - [ ] Takes List<Folder> as path parameter
  - [ ] Each segment triggers onNavigate(folderId) callback
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 2.1

- [x] 2.4 Redesign HomeScreen with two-pane layout

  **What to do**:
  - Implement adaptive layout: two-pane on tablet (>600dp), single-pane on phone
  - Left pane: FolderTree sidebar (280dp fixed width)
  - Right pane: NoteGrid with breadcrumb header
  - Phone: Drawer navigation for folders

  **Must NOT do**:
  - Do not break existing search functionality
  - Do not remove PDF import feature

  **Parallelizable**: NO - Depends on 2.1, 2.2, 2.3

  **References**:
    - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt` - Current implementation to refactor
    - Plan section "Home Screen (Folder Browser) > Layout" - Two-pane spec
    - Plan section "HomeScreen Query Surface" - Repository methods required for navigation

  **Acceptance Criteria**:
  - [ ] HomeScreen uses WindowSizeClass or similar for responsive layout
  - [ ] Tablet shows sidebar + grid side-by-side
  - [ ] Phone shows drawer that overlays
  - [ ] Existing search still works
  - [ ] PDF import menu item still works
  - [ ] Manual QA: Test on tablet and phone form factors

  **Commit**: YES
  - Message: `feat(ui): redesign HomeScreen with two-pane folder layout`
  - Files: `HomeScreen.kt`

- [x] 2.5 Implement folder navigation logic

  **What to do**:
  - Track currentFolderId in HomeViewModel
  - Update breadcrumb path on folder selection
  - Filter notes by current folder using `NoteRepository.getNotesForFolder(folderId)`
  - Handle "All Notes" special case (folderId = null)
  - Note counts via `NoteRepository.getNoteCountInFolder(folderId)` and `getRootNoteCount()`

  **Parallelizable**: NO - Depends on 2.4

  **References**:
    - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:349-379` - HomeViewModel pattern
    - Plan section "Technical Considerations > State Management" - HomeUiState spec
    - Plan section "HomeScreen Query Surface" - Repository methods: getNotesForFolder(), getNoteCountInFolder(), getRootNoteCount()

  **Acceptance Criteria**:
    - [ ] HomeViewModel has currentFolder: StateFlow<Folder?>
    - [ ] folderPath: StateFlow<List<Folder>> for breadcrumb
    - [ ] Uses `NoteRepository.getNotesForFolder(folderId)` for note filtering
    - [ ] Uses `getNoteCountInFolder()` and `getRootNoteCount()` for counts
    - [ ] Manual QA: Click folder → notes filter correctly

  **Commit**: YES
    - Message: `feat(viewmodel): add folder navigation to HomeViewModel`
    - Files: `HomeScreen.kt` (ViewModel section)

- [x] 2.6 Add folder context menus (rename, color, delete)

  **What to do**:
  - Long-press folder item shows context menu
  - Options: Rename, Change color, Change icon, Delete
  - Rename shows text input dialog
  - Color shows color picker
  - Delete shows confirmation with note count

  **Parallelizable**: NO - Depends on 2.4

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:232-253` - DropdownMenu pattern
  - Plan section "Folder Item Component" - Color customization

  **Acceptance Criteria**:
  - [ ] Long-press folder triggers DropdownMenu
  - [ ] Rename updates folder.name in repository
  - [ ] Delete confirms with "This folder has X notes"
  - [ ] Manual QA: Long-press folder → menu appears with working options

  **Commit**: YES
  - Message: `feat(ui): add folder context menus`
  - Files: `FolderTree.kt`, `FolderContextMenu.kt`

- [x] 2.7 Implement note multi-select mode

  **What to do**:
  - Long-press note card enters selection mode
  - Selected cards show checkbox
  - Action bar appears with: Move to folder, Add to folder, Delete, Share
  - "Move to folder" uses Move semantics (clears existing memberships)
  - "Add to folder" uses Link semantics (preserves existing memberships)
  - Tap outside or back button exits selection mode

  **Parallelizable**: NO - Depends on 2.4

  **References**:
  - Plan section "Note Grid > Long-press: Multi-select mode"
  - Plan section "Folder Membership Behavior (Move vs Link)" - Action bar behavior
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:77-83` - remember/mutableStateOf pattern

  **Acceptance Criteria**:
  - [ ] HomeUiState has selectionMode: Boolean and selectedNoteIds: Set<String>
  - [ ] NoteCard shows checkbox when in selection mode
  - [ ] Action bar appears at top with move/add/delete/share buttons
  - [ ] "Move to folder" calls NoteRepository.moveNoteToFolder() (clears + inserts)
  - [ ] "Add to folder" calls NoteRepository.addNoteToFolder() (inserts only)
  - [ ] Manual QA: Long-press note → selection mode works

  **Commit**: YES
  - Message: `feat(ui): add note multi-select mode`
  - Files: `HomeScreen.kt`, `NoteCard.kt`, `SelectionActionBar.kt`

- [x] 2.8 Add search overlay

  **What to do**:
  - Search icon in header opens full-screen search overlay
  - Debounced search input (300ms)
  - Results show note title, snippet, page number
  - Tap result navigates to note

  **Parallelizable**: NO - Depends on 2.4

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:278-333` - Current search implementation
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:352-365` - Debounce pattern

  **Acceptance Criteria**:
  - [ ] Search is full-screen overlay on phones
  - [ ] Search is inline on tablets
  - [ ] Existing search/recognition results work
  - [ ] Manual QA: Tap search → results appear for recognized text

  **Commit**: YES
  - Message: `feat(ui): add search overlay for home screen`
  - Files: `HomeScreen.kt`, `SearchOverlay.kt`

### Phase 3: Editor Toolbar Redesign (Unified Top Toolbar)

- [ ] 3.1 Refine UnifiedEditorToolbar visuals (baseline exists from Milestone A)

  **What to do**:
  - Update the existing single top toolbar system to match Samsung Notes + Notewise pill-group styling.
  - Toolbar (48dp): Back + editable title + tools (Hand, Pen, Highlighter, Eraser, Lasso) + **5 color dots inline** + Undo, Redo, Zoom (show %) + Lock + Overflow (⋮)
  - Split into three visible pill groups: left (navigation/title), center (reduced tool set + inline colors), right (actions/menu).
  - Share/Add are either inline (right cluster) **or** folded into overflow on narrow widths.
  - All elements remain in ONE row; allow horizontal scroll for tool cluster if needed.
  - **NO secondary toolbar** and **NO bottom toolbar** (already enforced in Milestone A).

  **Must NOT do**:
    - Do NOT re-introduce a separate top bar or bottom toolbar.
    - Do not implement tool panels in this task (separate 3.5).

  **Parallelizable**: YES (with 3.2)

  **References**:
    - Plan section "Unified Top Toolbar (Title + Tools)" - Layout spec
    - Plan section "Color Dot Specifications (Inline in Toolbar)" - Inline colors spec

  **Acceptance Criteria**:
    - [ ] Existing toolbar matches the updated layout and spacing spec.
    - [ ] Toolbar height remains fixed at 48dp (no collapsing).

  **Commit**: YES
    - Message: `feat(ui): refine UnifiedEditorToolbar visuals`

- [ ] 3.3 Refine toolbar focus visuals (no collapse)

  **What to do**:
  - Keep the toolbar pinned (Milestone A behavior) and only refine subtle visual feedback.
  - Optional: fade secondary actions to ~90% during active drawing (no height change).
  - View mode remains the only UI mode switch (edit ↔ view).

  **Must NOT do**:
    - Do NOT hide the toolbar or titlebar.
    - Do NOT change toolbar height on focus.

  **Parallelizable**: NO - Depends on 3.1

  **References**:
    - Plan section "Note Editor Screen > Behavior (Updated)"
    - Plan section "Toolbar Focus Behavior (Updated)" - Animation timing

  **Acceptance Criteria**:
    - [ ] Canvas tap does not hide or shrink the toolbar.
    - [ ] Title remains visible while drawing.
    - [ ] Manual QA: Draw/tap canvas → toolbar stays pinned and usable.

  **Commit**: YES
    - Message: `feat(editor): refine toolbar focus visuals`

- [ ] 3.4 Add tool long-press detection

  **What to do**:
  - Long-press on tool icon opens settings panel
  - Short tap selects the tool
  - Visual feedback: ripple on press, scale bounce on select

  **Parallelizable**: YES (with 3.5-3.9) - Depends on 3.3

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:15-17` - Gesture detection imports
  - Plan section "Tool Settings Panels (Samsung-style Floating)" - Panel trigger

  **Acceptance Criteria**:
  - [ ] ToolButton composable has onLongClick callback
  - [ ] Long-press duration: 500ms
  - [ ] Haptic feedback on long-press
  - [ ] Manual QA: Long-press pen → panel opens

  **Commit**: Groups with 3.5

- [ ] 3.5 Create FloatingToolPanel component

  **What to do**:
  - Create floating panel anchored to toolbar button position
  - 320dp width, 16dp padding, 16dp corner radius
  - Semi-transparent scrim backdrop
  - Dismiss on tap outside, swipe down, or X button
  - Smart positioning: flip up if not enough space below

  **Must NOT do**:
  - Do not implement tool-specific content (separate 3.6-3.8)

  **Parallelizable**: YES (with 3.4, 3.6-3.9) - Depends on 3.3

  **References**:
  - Plan section "Tool Settings Panels (Samsung-style Floating) > Panel Specifications"
  - Plan section "Component Specifications > FloatingToolPanel" - Composable signature
  - `apps/android/app/src/main/java/com/onyx/android/recognition/ui/OverlaySettingsDialog.kt` - Dialog pattern

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/toolbar/FloatingToolPanel.kt`
  - [ ] Panel positioned relative to anchor offset
  - [ ] Backdrop scrim dismisses on tap
  - [ ] 8dp elevation shadow
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
  - Message: `feat(ui): add FloatingToolPanel for tool settings`
  - Files: `FloatingToolPanel.kt`

- [ ] 3.6 Implement Pen settings panel with stabilization slider

  **What to do**:
  - Create panel content for pen tool
  - Live stroke preview at top
  - Pen style selector: Fountain, Ball, Calligraphy
  - Thickness slider: 1-30
  - Stabilization slider: 0-10
  - Pressure sensitivity slider: 0-10
  - Line type toggle: Stroke, Shape detection
  - Color palette with spectrum picker

  **Parallelizable**: YES (with 3.4, 3.5, 3.7, 3.8, 3.9)

  **References**:
  - Plan section "Pen Settings Panel" - Full visual spec
  - **Slider component to create** (BrushToolbar uses chips, not sliders - create new slider):
    ```kotlin
    @Composable
    fun LabeledSlider(
      value: Float,
      onValueChange: (Float) -> Unit,
      valueRange: ClosedFloatingPointRange<Float>,
      label: String,
      valueFormatter: (Float) -> String = { it.toInt().toString() }
    ) {
      Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(label, style = MaterialTheme.typography.labelMedium)
          Text(valueFormatter(value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
      }
    }
    ```
  - Material3 Slider: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#Slider

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/toolbar/PenSettingsPanel.kt`
  - [ ] LabeledSlider reusable component created
  - [ ] Live preview updates as settings change
  - [ ] All sliders have min/max labels
  - [ ] Done button closes panel and applies settings
  - [ ] Manual QA: Long-press pen → panel shows with working sliders

  **Commit**: YES
  - Message: `feat(ui): add pen settings panel with stabilization`
  - Files: `PenSettingsPanel.kt`, `LabeledSlider.kt`

- [ ] 3.7 Implement Highlighter settings panel

  **What to do**:
  - Create panel content for highlighter tool
  - Preview area
  - Draw straight line toggle: Always, Never, Hold
  - Line tip appearance: Square, Round
  - Thickness slider
  - Opacity slider: 0-100%
  - Default highlighter colors (yellows)

  **Parallelizable**: YES (with 3.4, 3.5, 3.6, 3.8, 3.9)

  **References**:
  - Plan section "Highlighter Settings Panel" - Full visual spec

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/toolbar/HighlighterSettingsPanel.kt`
  - [ ] Opacity slider shows percentage value
  - [ ] Default colors are yellow variants
  - [ ] Manual QA: Long-press highlighter → panel shows

  **Commit**: Groups with 3.6

- [ ] 3.8 Implement Eraser settings panel with filters

  **What to do**:
  - Create panel content for eraser tool
  - Preview area showing eraser cursor
  - Eraser mode: Stroke eraser, Area eraser (radio)
  - Size slider
  - Erase the following: toggle icons for Pen, Highlighter, Shape, Image, Text, Sticker
  - Erase locked objects checkbox
  - Clear Current Page button (destructive)

  **Must NOT do**:
  - Clear page button must show confirmation dialog

  **Parallelizable**: YES (with 3.4, 3.5, 3.6, 3.7, 3.9)

  **References**:
  - Plan section "Eraser Settings Panel" - Full visual spec
  - `apps/android/app/src/main/java/com/onyx/android/ui/components/EraserMode.kt` - Current eraser mode enum

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/toolbar/EraserSettingsPanel.kt`
  - [ ] Eraser mode is radio button group
  - [ ] Filter toggles are icon buttons with selected state
  - [ ] Clear Current Page shows confirmation dialog
  - [ ] Manual QA: Long-press eraser → panel shows with filter toggles

  **Commit**: Groups with 3.6

- [ ] 3.9 Create Samsung-style ColorPicker dialog

  **What to do**:
  - Full-screen color picker with two tabs: Swatches, Spectrum
  - Spectrum tab: HSV gradient picker with hue slider
  - Hex input field with copy/paste
  - RGB sliders for precision
  - Recent colors row (last 8 used)
  - Preset color grid

  **Parallelizable**: YES (with 3.4, 3.5, 3.6, 3.7, 3.8)

  **References**:
  - Plan section "Color Picker Dialog (Samsung-style)" - Full visual spec
  - `apps/android/app/src/main/java/com/onyx/android/ui/components/BrushToolbar.kt:173-207` - Current color palette

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/color/ColorPicker.kt`
  - [ ] Two tabs: Swatches and Spectrum
  - [ ] Hex input validates and updates picker
  - [ ] Recent colors persist (max 8)
  - [ ] Cancel/Done buttons
  - [ ] Manual QA: Long-press color dot → full picker opens

  **Commit**: YES
  - Message: `feat(ui): add Samsung-style ColorPicker dialog`
  - Files: `ColorPicker.kt`, `ColorDot.kt`

- [ ] 3.10 Persist tool settings to preferences

  **What to do**:
  - Save all tool settings to UserPreferenceEntity on change
  - Load settings on app start and restore to ToolSettings
  - Handle missing preferences with sensible defaults

  **Parallelizable**: NO - Depends on all panel tasks (3.6-3.9)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:168-189` - SharedPreferences pattern to migrate
  - Plan section "Preference Keys" - Key names

  **Acceptance Criteria**:
  - [ ] Pen/Highlighter/Eraser settings survive app restart
  - [ ] Quick color slots persist
  - [ ] Stabilization level persists
  - [ ] Manual QA: Change pen thickness → restart app → same thickness

  **Commit**: YES
  - Message: `feat(prefs): persist tool settings to database`
  - Files: `NoteEditorScreen.kt`, ViewModel changes

- [ ] 3.11 Create NoteEditorViewModel for toolbar/preference state management

  **What to do**:
  - **NEW FILE**: Create `apps/android/app/src/main/java/com/onyx/android/ui/viewmodel/NoteEditorViewModel.kt`
  - Current editor uses direct SharedPreferences access and screen-level mutableState
  - Migrate toolbar state, tool settings, and preferences to ViewModel
  - ViewModel receives repositories via constructor (NoteRepository, TemplateRepository via OnyxApplication)
  - Create `EditorViewModelFactory` as private class inside `NoteEditorScreen.kt` (following HomeScreen pattern)

  **Must NOT do**:
  - Do NOT try to find existing NoteEditorViewModelFactory (it doesn't exist)
  - Keep screen composable lean - business logic goes in ViewModel

  **Parallelizable**: NO - Depends on 3.10

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:337-349` - HomeViewModelFactory pattern (private class inside screen)
  - Plan section "DAO and Repository Wiring" - Repository access via OnyxApplication

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/viewmodel/NoteEditorViewModel.kt`
  - [ ] ViewModel contains: toolSettings, editorUiState, currentTool, quickColors
  - [ ] Private `EditorViewModelFactory` class inside `NoteEditorScreen.kt`
  - [ ] Factory receives repositories: `NoteRepository`, `TemplateRepository` from OnyxApplication
  - [ ] ViewModel handles all preference reads/writes (no direct SharedPreferences in screen)
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
  - Message: `feat(viewmodel): add NoteEditorViewModel for toolbar state`
  - Files: `NoteEditorViewModel.kt`, `NoteEditorScreen.kt` (add factory)

### Phase 4: Template System

- [ ] 4.1 Create TemplatePicker component

  **What to do**:
  - Create bottom sheet / dialog for template selection
  - Built-in / Custom tabs
  - Size dropdown (A3-A7, Custom)
  - Background color circles
  - Density and Line width sliders (1-10)
  - Template grid by category

  **Must NOT do**:
  - Do not implement custom template creation in this task

  **Parallelizable**: YES (with 4.2, 4.3, 4.4, 4.5)

  **References**:
  - Plan section "Template Picker (Notewise-style)" - Full layout spec
  - Plan section "Component Specifications > TemplatePicker" - Composable signature
  - `apps/android/app/src/main/java/com/onyx/android/recognition/ui/OverlaySettingsDialog.kt` - Dialog pattern

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/template/TemplatePicker.kt`
  - [ ] Shows as ModalBottomSheet
  - [ ] Template categories are expandable/collapsible
  - [ ] Apply button triggers onApply callback
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
  - Message: `feat(ui): add TemplatePicker component`
  - Files: `TemplatePicker.kt`, `TemplateCard.kt`, `SizeSelector.kt`

- [ ] 4.2 Implement template category tabs (Basic, Education, Music)

  **What to do**:
  - Section headers that expand/collapse
  - Basic: Blank, Rule, Grid, Dot
  - Education: Hexagonal Grid, Cornell A, Cornell B, Legal, Engineering
  - Music: Music Score, Guitar Tablature, Guitar Score

  **Parallelizable**: YES (with 4.1, 4.3, 4.4, 4.5)

  **References**:
  - Plan section "Template Categories" - Category list
  - Plan section "Default Templates to Bundle" - Template definitions

  **Acceptance Criteria**:
  - [ ] Three categories render with expand/collapse
  - [ ] Category headers show template count
  - [ ] Templates display in horizontal scroll or grid
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 4.1

- [ ] 4.3 Add background color selection

  **What to do**:
  - Row of color circles for page background
  - Colors: White, Yellow, Orange, Gray, Black
  - Selected color shows ring indicator
  - Updates template preview

  **Parallelizable**: YES (with 4.1, 4.2, 4.4, 4.5)

  **References**:
  - Plan section "Template Picker > Background" - Color row spec
  - `apps/android/app/src/main/java/com/onyx/android/ui/components/BrushToolbar.kt:235-259` - ColorSwatch pattern

  **Acceptance Criteria**:
  - [ ] 5 background color options
  - [ ] Selected color has visual ring indicator
  - [ ] Selection updates preview
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 4.1

- [ ] 4.4 Implement density and line width sliders

  **What to do**:
  - Density slider: 1-10, affects spacing of lines/dots/grid
  - Line width slider: 1-10, affects template line thickness
  - Both update preview in real-time

  **Parallelizable**: YES (with 4.1, 4.2, 4.3, 4.5)

  **References**:
  - Plan section "Template Picker > Density/Line width" - Slider specs

  **Acceptance Criteria**:
  - [ ] Both sliders show current value (1-10)
  - [ ] Template preview updates as sliders move
  - [ ] Values persist in template preference
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 4.1

- [ ] 4.5 Add size presets (A3, A4, A5, A6, A7, Custom)

  **What to do**:
  - Dropdown selector for page size
  - Preset dimensions per plan spec
  - Custom option shows width/height input fields
  - Display "Suitable for phones" hint for A7

  **Parallelizable**: YES (with 4.1, 4.2, 4.3, 4.4)

  **References**:
  - Plan section "Size Options (dropdown)" - Dimensions and hints

  **Acceptance Criteria**:
  - [ ] Dropdown with 6 preset sizes + Custom
  - [ ] Custom shows numeric input fields
  - [ ] A7 shows phone suitability hint
  - [ ] Size selection affects page creation
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 4.1

- [ ] 4.6 Create default template assets (SVG/PNG)

  **What to do**:
  - Create template preview images for each built-in template
  - Place in res/drawable or assets folder
  - Optimize for display at template card size

  **Must NOT do**:
  - Do not create the actual template rendering logic (separate task)

  **Parallelizable**: NO - Depends on 4.1-4.5 for specs

  **References**:
  - Plan section "Default Templates to Bundle" - Template list

  **Acceptance Criteria**:
  - [ ] Template preview images exist for all 10 default templates
  - [ ] Images sized appropriately for cards
  - [ ] Manual QA: Template picker shows preview images

  **Commit**: YES
  - Message: `feat(assets): add template preview images`
  - Files: `res/drawable/template_*.png`

- [ ] 4.7 Apply template to page functionality

  **What to do**:
  - Implement template rendering on canvas background
  - Generate template pattern based on settings (density, line width, bg color)
  - Store template selection in page metadata
  - Support: Blank, Rule, Grid, Dot patterns

  **Must NOT do**:
  - Do not implement Education/Music templates initially (Phase 2 of templates)

  **Parallelizable**: NO - Depends on 4.6

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` - Canvas rendering
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt` - Page metadata

  **Acceptance Criteria**:
  - [ ] Apply template updates page background
  - [ ] Rule template shows horizontal lines
  - [ ] Grid template shows square grid
  - [ ] Dot template shows dot pattern
  - [ ] Density/line width affect pattern
  - [ ] Manual QA: Apply Grid template → grid lines visible on canvas

  **Commit**: YES
  - Message: `feat(canvas): apply template patterns to page background`
  - Files: `InkCanvas.kt`, `TemplateRenderer.kt`

- [ ] 4.8 Persist template preferences

  **What to do**:
  - Save last used template ID to preferences
  - Save default page size preference
  - Restore last template on new page creation

  **Parallelizable**: NO - Depends on 4.7

  **References**:
  - Plan section "Preference Keys" - LAST_TEMPLATE, DEFAULT_PAGE_SIZE

  **Acceptance Criteria**:
  - [ ] New pages use last selected template
  - [ ] Size preference persists across sessions
  - [ ] Manual QA: Select Grid → create new page → Grid applied

  **Commit**: YES
  - Message: `feat(prefs): persist template preferences`
  - Files: ViewModel changes

### Phase 5: Stylus & Scrollbar

- [ ] 5.1 Create StylusSettingsPanel component

  **What to do**:
  - Create settings panel accessible from overflow menu
  - Card-based layout with sections: Stylus, Single finger, Double finger, More gestures
  - Stylus section: Latency optimization toggle, Primary/Secondary action dropdowns

  **Parallelizable**: YES (with 5.2, 5.3, 5.4)

  **References**:
  - Plan section "Stylus & Finger Settings (Notewise-style)" - Full layout spec
  - Plan section "Component Specifications > StylusSettingsPanel" - Composable signature

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/stylus/StylusSettingsPanel.kt`
  - [ ] Three card sections visible
  - [ ] ModalBottomSheet or full-screen dialog
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: YES
  - Message: `feat(ui): add StylusSettingsPanel component`
  - Files: `StylusSettingsPanel.kt`

- [ ] 5.2 Implement button action configuration

  **What to do**:
  - Primary action dropdown: Switch to eraser, Switch to last used, Hold to erase
  - Secondary action dropdown: Same options + None
  - Long hold on primary button: separate dropdown
  - Remap eraser on stylus dropdown

  **Parallelizable**: YES (with 5.1, 5.3, 5.4)

  **References**:
  - Plan section "Stylus Button Actions (dropdown options)"
  - Plan section "Data Models > StylusButtonAction enum"

  **Acceptance Criteria**:
  - [ ] Primary action dropdown with 3 options
  - [ ] Secondary action dropdown with 4 options (includes None)
  - [ ] Changes persist to StylusPreferenceEntity
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 5.1

- [ ] 5.3 Add latency optimization toggle

  **What to do**:
  - Toggle between Standard and Fast (Experimental)
  - Fast mode reduces input processing latency
  - Show warning that Fast is experimental

  **Parallelizable**: YES (with 5.1, 5.2, 5.4)

  **References**:
  - Plan section "Stylus & Finger Settings > Latency optimization"
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/StylusPreferenceEntity.kt` (to be created) - latencyOptimization field

  **Acceptance Criteria**:
  - [ ] Toggle group: Standard | Fast (Exp.)
  - [ ] (Exp.) label indicates experimental
  - [ ] Setting persists to StylusPreferenceEntity
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 5.1

- [ ] 5.4 Implement finger gesture settings (single/double)

  **What to do**:
  - Single finger radio: Ignored, Draw, Scroll
  - Double finger radio: Ignored, Zoom and pan, Scroll
  - More gestures: Double tap to change zoom toggle
  - Undo/Redo gesture display (informational)

  **Parallelizable**: YES (with 5.1, 5.2, 5.3)

  **References**:
  - Plan section "Stylus & Finger Settings > Single finger / Double finger" - Radio options
  - Plan section "Data Models > FingerAction enum"

  **Acceptance Criteria**:
  - [ ] Single finger has 3 radio options
  - [ ] Double finger has 3 radio options
  - [ ] Default: Single=Scroll, Double=Zoom and pan
  - [ ] Settings persist
  - [ ] `gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL

  **Commit**: Groups with 5.1

- [ ] 5.5 Create MovableScrollbar component

  **What to do**:
  - Floating vertical scrollbar showing current page / total pages
  - Position on left or right edge based on preference
  - Auto-hide after 2s of inactivity
  - Drag to scroll quickly through pages

  **Parallelizable**: YES (with 5.6, 5.7)

  **References**:
  - Plan section "Component Specifications > MovableScrollbar" - Composable signature
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:123-141` - Page navigation pattern

  **Acceptance Criteria**:
  - [ ] File exists: `apps/android/app/src/main/java/com/onyx/android/ui/components/scrollbar/MovableScrollbar.kt`
  - [ ] Shows "Page X of Y" or similar indicator
  - [ ] Fades out after 2s
  - [ ] Drag changes page
  - [ ] Manual QA: Scrollbar appears during navigation, hides after idle

  **Commit**: YES
  - Message: `feat(ui): add MovableScrollbar component`
  - Files: `MovableScrollbar.kt`

- [ ] 5.6 Add scrollbar position preference (left/right)

  **What to do**:
  - Settings option to place scrollbar on left or right
  - Default: Right
  - Persists to UserPreferenceEntity

  **Parallelizable**: YES (with 5.5, 5.7)

  **References**:
  - Plan section "Settings Screen > Position for scrollbar" - Left/Right option
  - Plan section "Preference Keys" - SCROLLBAR_POSITION

  **Acceptance Criteria**:
  - [ ] Settings screen has "Position for scrollbar" dropdown
  - [ ] Options: Left, Right
  - [ ] MovableScrollbar reads preference and positions accordingly
  - [ ] Manual QA: Change to Left → scrollbar moves to left edge

  **Commit**: Groups with 5.5

- [ ] 5.7 Implement "Go to page" dialog

  **What to do**:
  - Tap on scrollbar opens dialog
  - Numeric input for page number
  - Slider for quick navigation
  - Go button navigates to page

  **Parallelizable**: YES (with 5.5, 5.6)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:144-204` - AlertDialog pattern

  **Acceptance Criteria**:
  - [ ] Tap scrollbar opens AlertDialog
  - [ ] Input field accepts page number
  - [ ] Invalid numbers show error
  - [ ] Go navigates to page
  - [ ] Manual QA: Tap scrollbar → enter page → navigates

  **Commit**: Groups with 5.5

- [ ] 5.8 Handle S Pen button events in editor

  **What to do**:
  - Intercept S Pen button press events via `MotionEvent.getButtonState()`
  - Apply configured action (switch to eraser, switch to last tool, hold to erase)
  - Respect latency optimization setting

  **Must NOT do**:
  - Do not break existing touch/stylus drawing
  - Do not use Samsung-specific SDK (use standard Android MotionEvent API)

  **Parallelizable**: NO - Depends on 5.1-5.4

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` - Touch handling location
  - Plan section "S Pen Button Handling API" - Full implementation strategy with MotionEvent code
  - Android MotionEvent docs: https://developer.android.com/reference/android/view/MotionEvent#getButtonState()

  **Acceptance Criteria**:
  - [ ] Inside InkCanvas.kt `setOnTouchListener`, check `event.getToolType(0) == TOOL_TYPE_STYLUS` on ACTION_DOWN
  - [ ] Button state read via `event.buttonState and BUTTON_STYLUS_PRIMARY`
  - [ ] S Pen button triggers configured action from StylusPreferenceEntity
  - [ ] Hold-to-erase works while button held (saves/restores tool on press/release)
  - [ ] Manual QA: Press S Pen button → tool switches per setting

  **Commit**: YES
  - Message: `feat(input): handle S Pen button events`
  - Files: `InkCanvas.kt`, `StylusInputHandler.kt`

### Phase 6: Settings & Polish

- [ ] 6.1 Create Settings screen with card-based layout

  **What to do**:
  - **CREATE NEW FILE**: `apps/android/app/src/main/java/com/onyx/android/ui/screens/SettingsScreen.kt`
  - Design Settings screen with grouped cards (General, Security, Manage storage, About)
  - General card: New note name, Continue from last page, Keep screen on, Hide system bars, Scrollbar position, Toolbox, Link, Snap to align
  - Security card: Password lock
  - Storage card: Notes size, Cache size, Clear cache button
  - About card: Terms, Privacy, Help, Diagnostic data, Version
  - Add navigation from NoteEditorScreen

  **Must NOT do**:
  - Do not change underlying settings logic (UI only)

  **Parallelizable**: YES (with 6.2-6.9)

  **References**:
  - Plan section "Settings Screen (Notewise Card-based)" - Full layout spec
  - Plan section "Design System > Color Palette" - bgSecondary token (NEW - must be added to Color.kt per Phase 0)
  - `apps/android/app/src/main/java/com/onyx/android/ui/theme/Color.kt` - Existing color definitions pattern (add new tokens here)
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:541-566` - Current TopAppBar with individual icons
  - **Navigation entry point**: Refactor TopAppBar actions:
    - Keep: Back arrow, Title (editable), Share, Add page
    - Move to overflow menu (⋮): Search, Change template, Stylus & finger, Settings
    - Replace current Settings icon (which opens OverlaySettingsDialog) with overflow menu containing "Recognition Overlay" and "App Settings"

  **Acceptance Criteria**:
  - [ ] File created: `apps/android/app/src/main/java/com/onyx/android/ui/screens/SettingsScreen.kt`
  - [ ] Settings screen shows 4 card sections (General, Security, Manage storage, About)
  - [ ] Cards have rounded corners (16dp) - verify via Modifier.clip(RoundedCornerShape(16.dp)) in code
  - [ ] Settings rows have proper spacing - verify 16dp padding in code
  - [ ] TopAppBar refactored to use overflow menu (DropdownMenu) for less-used actions
  - [ ] "Settings" option in overflow menu navigates to SettingsScreen
  - [ ] Screenshot comparison: Take screenshot, compare against plan section "Settings Screen" layout

  **Commit**: YES
  - Message: `feat(ui): create Settings screen with card-based layout`
  - Files: `SettingsScreen.kt`, `NoteEditorScreen.kt` (add menu item)

- [ ] 6.2 Add all animations (transitions, states)

  **What to do**:
  - Home → Editor: Shared element thumbnail scales to full screen (300ms)
  - Toolbar focus behavior: Optional opacity fade on secondary actions (no height change)
  - Tool panel: Scale 0.9→1.0 with spring, alpha fade
  - Color selection: Scale bounce 1.0→1.2→1.0
  - Template picker: Slide up from bottom (300ms)

  **Parallelizable**: YES (with 6.1, 6.3-6.9)

  **References**:
  - Plan section "Animation Specifications" - All timing specs

  **Acceptance Criteria**:
  - [ ] All transitions use specified durations (verify via animation spec constants in code)
  - [ ] Performance verification: Run app with "Profile GPU Rendering" enabled in Developer Options → No bars exceed green line (16ms threshold)
  - [ ] Alternative: Use Android Studio Profiler → Frames tab → 95th percentile frame time <16ms
  - [ ] Manual QA: Navigate through all screens, trigger all animations, verify no visible stuttering

  **Commit**: YES
  - Message: `feat(ui): add polished animations throughout`
  - Files: Various composables

- [ ] 6.3 Implement canvas tap behavior (no hide)

  **What to do**:
  - Canvas tap dismisses floating panels/menus (if open)
  - If in View Mode, canvas tap exits to Edit Mode
  - No hiding/collapsing of the toolbar or titlebar

  **Parallelizable**: YES (with 6.1, 6.2, 6.4-6.9)

  **References**:
  - Plan section "Note Editor Screen > Behavior (Updated)"
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:15-17` - Gesture imports

  **Acceptance Criteria**:
  - [ ] Tap canvas dismisses open panels/menus
  - [ ] Tap canvas exits View Mode (if active)
  - [ ] Toolbar/titlebar remain visible at all times
  - [ ] Manual QA: Canvas tap behavior matches updated spec

  **Commit**: Groups with 6.2

- [ ] 6.4 Add haptic feedback on tool selection

  **What to do**:
  - Short vibration on tool tap
  - Longer vibration on tool long-press
  - Use HapticFeedbackType.LongPress and TextHandleMove

  **Parallelizable**: YES (with 6.1-6.3, 6.5-6.9)

  **References**:
  - Android HapticFeedback documentation
  - `apps/android/app/src/main/java/com/onyx/android/ui/components/BrushToolbar.kt` - Tool selection

  **Acceptance Criteria**:
  - [ ] Tool tap produces short haptic
  - [ ] Long-press produces distinct haptic
  - [ ] Manual QA: Feel haptic feedback when selecting tools

  **Commit**: Groups with 6.2

- [ ] 6.5 Implement page thumbnail navigation

  **What to do**:
  - Swipe left/right on scrollbar shows page thumbnails
  - Grid of thumbnails for quick navigation
  - Tap thumbnail navigates to page

  **Parallelizable**: YES (with 6.1-6.4, 6.6-6.9)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/NoteEditorScreen.kt:123-141` - Page navigation

  **Acceptance Criteria**:
  - [ ] Horizontal swipe on scrollbar opens thumbnail strip
  - [ ] Thumbnails are lazy-loaded
  - [ ] Tap navigates to page
  - [ ] Manual QA: Page thumbnails appear and work

  **Commit**: YES
  - Message: `feat(ui): add page thumbnail navigation`
  - Files: `PageThumbnailStrip.kt`

- [ ] 6.6 Add empty states for folders

  **What to do**:
  - Empty folder shows illustration + "No notes yet" message
  - Include "Create new note" button
  - Empty search results state

  **Parallelizable**: YES (with 6.1-6.5, 6.7-6.9)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/screens/HomeScreen.kt:320-332` - Current empty state

  **Acceptance Criteria**:
  - [ ] Empty folder shows centered message
  - [ ] CTA button to create note
  - [ ] Different message for empty search
  - [ ] Manual QA: Empty folder shows appropriate state

  **Commit**: Groups with 6.5

- [ ] 6.7 Add loading skeletons

  **What to do**:
  - Shimmer loading placeholders while data loads
  - Note cards skeleton
  - Folder tree skeleton
  - Template grid skeleton

  **Parallelizable**: YES (with 6.1-6.6, 6.8-6.9)

  **References**:
  - **Shimmer implementation pattern** (create new):
    ```kotlin
    @Composable
    fun ShimmerBox(modifier: Modifier = Modifier) {
      val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
      )
      val transition = rememberInfiniteTransition()
      val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing))
      )
      val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200, 0f),
        end = Offset(translateAnim.value, 0f)
      )
      Box(modifier.background(brush))
    }
    ```
  - Compose animation docs: https://developer.android.com/develop/ui/compose/animation/quick-guide

  **Acceptance Criteria**:
  - [ ] ShimmerBox reusable component created
  - [ ] Skeleton shows during initial load
  - [ ] Shimmer animation runs (verify: gradient moves left-to-right continuously)
  - [ ] Content replaces skeleton when ready
  - [ ] Manual QA: App shows skeletons before content appears

  **Commit**: Groups with 6.5

- [ ] 6.8 Accessibility: Content descriptions

  **What to do**:
  - Add contentDescription to all icons and buttons
  - Describe action/purpose, not appearance
  - Ensure screen reader can navigate UI

  **Parallelizable**: YES (with 6.1-6.7, 6.9)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/ui/components/BrushToolbar.kt:75-87` - Current contentDescription pattern

  **Acceptance Criteria**:
  - [ ] All Icon composables have contentDescription
  - [ ] Descriptions are action-oriented ("Undo", not "Back arrow")
  - [ ] TalkBack can navigate entire UI

  **Commit**: YES
  - Message: `feat(a11y): add accessibility content descriptions`
  - Files: All UI files

- [ ] 6.9 Accessibility: Focus management

  **What to do**:
  - Proper focus order in dialogs
  - Focus trap in modal sheets
  - Dismiss on escape key for keyboard users

  **Parallelizable**: YES (with 6.1-6.8)

  **References**:
  - Compose accessibility modifiers:
    - `Modifier.focusRequester()` - Request focus programmatically
    - `Modifier.focusable()` - Make composable focusable
    - `Modifier.semantics { }` - Add accessibility semantics
  - Focus management docs: https://developer.android.com/develop/ui/compose/accessibility/key-steps#focus
  - Keyboard handling in Compose: https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/focus

  **Acceptance Criteria**:
  - [ ] Tab navigation follows logical order (verify: use Tab key to cycle through UI)
  - [ ] Modal dialogs trap focus (verify: Tab does not escape dialog)
  - [ ] Escape closes modals (verify: press Esc key)
  - [ ] Manual QA: Keyboard navigation works

  **Commit**: Groups with 6.8

### Phase 7: Testing

- [ ] 7.1 Test folder CRUD operations

  **What to do**:
  - Unit tests for FolderDao queries
  - Unit tests for NoteRepository folder methods
  - Test create, read, update, delete
  - Test orphaning notes when folder deleted

  **Parallelizable**: YES (with 7.2-7.9)

  **References**:
  - `apps/android/app/src/test/java/com/onyx/android/` - Existing test directory
  - `apps/android/app/src/test/java/com/onyx/android/data/sync/LocalLamportClockTest.kt` - Test pattern

  **Acceptance Criteria**:
  - [ ] Test file exists: `FolderDaoTest.kt`
  - [ ] Tests for getRootFolders, getSubfolders
  - [ ] Tests for folder deletion with orphaning
  - [ ] `gradlew.bat :app:test` → All tests pass

  **Commit**: YES
  - Message: `test(db): add folder CRUD unit tests`
  - Files: `FolderDaoTest.kt`, `NoteRepositoryFolderTest.kt`

- [ ] 7.2 Test folder hierarchy (deep nesting)

  **What to do**:
  - Create test with 5+ levels of nested folders
  - Verify tree traversal works
  - Test performance with 100+ folders

  **Parallelizable**: YES (with 7.1, 7.3-7.9)

  **References**:
  - `apps/android/app/src/test/java/com/onyx/android/ink/canvas/TileCoordinatesTest.kt` - Unit test structure pattern
  - Android measurement: Use `measureTimeMillis` or JUnit `@Test(timeout = 1000)` for time constraints

  **Acceptance Criteria**:
  - [ ] Deep nesting test passes
  - [ ] 100 folder test completes in <1s
  - [ ] `gradlew.bat :app:test` → Pass

  **Commit**: Groups with 7.1

- [ ] 7.3 Test tool settings persistence

  **What to do**:
  - Unit tests for UserPreferenceDao
  - Test that tool settings survive repository recreation
  - Test default values when no preference exists

  **Parallelizable**: YES (with 7.1, 7.2, 7.4-7.9)

  **References**:
  - Plan section "Preference Keys" - Keys to test

  **Acceptance Criteria**:
  - [ ] Test file exists: `UserPreferenceDaoTest.kt`
  - [ ] Tests for get/set operations
  - [ ] Tests for default value handling
  - [ ] `gradlew.bat :app:test` → Pass

  **Commit**: Groups with 7.1

- [ ] 7.4 Test color picker on various devices

  **What to do**:
  - Instrumented tests for ColorPicker composable
  - Test on different screen sizes
  - Test hex input validation
  - Test spectrum picker interaction

  **Parallelizable**: YES (with 7.1-7.3, 7.5-7.9)

  **References**:
  - `apps/android/app/src/androidTest/java/com/onyx/android/ink/InkApiCompatTest.kt` - Instrumented test pattern

  **Acceptance Criteria**:
  - [ ] Instrumented test for ColorPicker
  - [ ] Hex input accepts valid colors, rejects invalid
  - [ ] `gradlew.bat :app:connectedTest` → Pass (on connected device)

  **Commit**: YES
  - Message: `test(ui): add ColorPicker instrumented tests`
  - Files: `ColorPickerTest.kt`

- [ ] 7.5 Test UI state transitions

  **What to do**:
  - Test Edit ↔ View mode transition
  - Test toolbar focus behavior (no collapse)
  - Test animation completion (opacity fades, tool selection bounce)

  **Parallelizable**: YES (with 7.1-7.4, 7.6-7.9)

  **References**:
  - Compose UI testing documentation

  **Acceptance Criteria**:
  - [ ] State transition tests pass
  - [ ] Toolbar remains visible while drawing
  - [ ] `gradlew.bat :app:test` → Pass

  **Commit**: Groups with 7.4

- [ ] 7.6 Test stabilization effect (visual smoothness)

  **What to do**:
  - Unit test for StrokeStabilizer algorithm
  - Test with various stabilization levels
  - Verify output is smoother than input

  **Parallelizable**: YES (with 7.1-7.5, 7.7-7.9)

  **References**:
  - Plan section "Stroke Stabilization Algorithm" - Algorithm spec
  - `apps/android/app/src/test/java/com/onyx/android/ink/algorithm/StrokeSplitterTest.kt` - Algorithm test pattern

  **Acceptance Criteria**:
  - [ ] Test file exists: `StrokeStabilizerTest.kt`
  - [ ] Level 0 returns identical points
  - [ ] Level 10 significantly smooths jagged input
  - [ ] `gradlew.bat :app:test` → Pass

  **Commit**: YES
  - Message: `test(ink): add stroke stabilization unit tests`
  - Files: `StrokeStabilizerTest.kt`

- [ ] 7.7 Test S Pen button configuration

  **What to do**:
  - Test StylusPreferenceDao persistence
  - Test action configuration changes
  - Integration test if S Pen available

  **Parallelizable**: YES (with 7.1-7.6, 7.8-7.9)

  **References**:
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/StylusPreferenceEntity.kt` (to be created)

  **Acceptance Criteria**:
  - [ ] Preference persistence tests pass
  - [ ] Action enum serialization works
  - [ ] `gradlew.bat :app:test` → Pass

  **Commit**: Groups with 7.6

- [ ] 7.8 Test template application

  **What to do**:
  - Unit test for template rendering
  - Test different templates (Blank, Rule, Grid, Dot)
  - Test density/line width parameters

  **Parallelizable**: YES (with 7.1-7.7, 7.9)

  **References**:
  - Plan section "Default Templates to Bundle" - Templates to test

  **Acceptance Criteria**:
  - [ ] Template renderer produces correct patterns
  - [ ] Density affects spacing correctly
  - [ ] Line width affects stroke correctly
  - [ ] `gradlew.bat :app:test` → Pass

  **Commit**: Groups with 7.6

- [ ] 7.9 Performance test with 1000+ notes

  **What to do**:
  - Create performance test fixture with 1000 notes in folders
  - Measure home screen load time
  - Measure folder navigation time
  - Ensure <1s response times

  **Parallelizable**: YES (with 7.1-7.8)

  **References**:
  - Android performance testing best practices

  **Acceptance Criteria**:
  - [ ] Home screen loads 1000 notes in <1s
  - [ ] Folder navigation is instant (<100ms)
  - [ ] No memory leaks during navigation
  - [ ] Manual QA: App remains responsive with large dataset

  **Commit**: YES
  - Message: `test(perf): add 1000+ note performance tests`
  - Files: `PerformanceTest.kt`

---

## Technical Considerations

### Performance
- Folder tree: Use LazyColumn with sticky headers
- Note grid: LazyVerticalGrid with thumbnail caching
- Thumbnails: Generate on background thread, cache to disk
- Tool panels: Reuse single instance, animate content swap
- Stabilization: Apply Catmull-Rom spline or moving average in real-time

### Stroke Stabilization Algorithm
```kotlin
// Simple moving average approach (0-10 level maps to window size)
fun smoothStroke(points: List<Point>, level: Float): List<Point> {
  if (level <= 0f) return points
  val windowSize = (level * 3).toInt().coerceIn(1, 30) // 0-10 -> 0-30
  return points.mapIndexed { index, _ ->
    val window = points.subList(
      maxOf(0, index - windowSize/2),
      minOf(points.size, index + windowSize/2 + 1)
    )
    Point(
      x = window.map { it.x }.average().toFloat(),
      y = window.map { it.y }.average().toFloat(),
      t = points[index].t,
      p = points[index].p
    )
  }
}
```

### State Management
```kotlin
// Editor UI State
sealed class EditorUiState {
  data class FullUi(
    val showTopBar: Boolean = true,
    val showPrimaryToolbar: Boolean = true,
    val showSecondaryToolbar: Boolean = true,
    val toolbarExpanded: Boolean = true
  ) : EditorUiState()
  
  data class FocusedUi(
    val showTopBar: Boolean = false,
    val showPrimaryToolbar: Boolean = true,
    val showSecondaryToolbar: Boolean = true,
    val primaryToolbarExpanded: Boolean = false,
    val secondaryToolbarExpanded: Boolean = false,
    val autoHideTimer: Job? = null
  ) : EditorUiState()
}

// Home Screen State
data class HomeUiState(
  val currentFolder: Folder? = null,
  val folderPath: List<Folder> = emptyList(),
  val folders: List<Folder> = emptyList(),
  val notes: List<Note> = emptyList(),
  val selectionMode: Boolean = false,
  val selectedNoteIds: Set<String> = emptySet(),
  val searchQuery: String = "",
  val isSearchActive: Boolean = false
)
```

### Preference Keys
```kotlin
object PreferenceKeys {
  const val SCROLLBAR_POSITION = "scrollbar_position" // "left" | "right"
  const val STABILIZATION_LEVEL = "stabilization_level" // "0" - "10"
  const val QUICK_COLOR_1 = "quick_color_1"
  const val QUICK_COLOR_2 = "quick_color_2"
  const val QUICK_COLOR_3 = "quick_color_3"
  const val QUICK_COLOR_4 = "quick_color_4"
  const val QUICK_COLOR_5 = "quick_color_5"
  const val LAST_TEMPLATE = "last_template_id"
  const val DEFAULT_PAGE_SIZE = "default_page_size" // "A4", etc.
}
```

### File Locations

```
app/src/main/java/com/onyx/android/
├── data/
│   ├── entity/
│   │   ├── FolderEntity.kt (NEW)
│   │   ├── NoteFolderEntity.kt (NEW)
│   │   ├── TemplateEntity.kt (NEW)
│   │   ├── StylusPreferenceEntity.kt (NEW)
│   │   └── UserPreferenceEntity.kt (NEW)
│   ├── dao/
│   │   ├── FolderDao.kt (NEW)
│   │   ├── TemplateDao.kt (NEW)
│   │   ├── StylusPreferenceDao.kt (NEW)
│   │   └── UserPreferenceDao.kt (NEW)
│   └── repository/
│       ├── NoteRepository.kt (MODIFY - add folder operations)
│       └── TemplateRepository.kt (NEW)
├── ui/
│   ├── components/
│   │   ├── folder/
│   │   │   ├── FolderTree.kt (NEW)
│   │   │   ├── FolderItem.kt (NEW)
│   │   │   └── Breadcrumb.kt (NEW)
│   │   ├── note/
│   │   │   ├── NoteCard.kt (NEW)
│   │   │   └── NoteGrid.kt (NEW)
│   │   ├── toolbar/
│   │   │   ├── UnifiedEditorToolbar.kt (NEW) ← Title + tools in one top bar
│   │   │   ├── FloatingToolPanel.kt (NEW)
│   │   │   └── ColorDot.kt (NEW)           ← Inline color dot component
│   │   ├── template/
│   │   │   ├── TemplatePicker.kt (NEW)
│   │   │   ├── TemplateCard.kt (NEW)
│   │   │   └── SizeSelector.kt (NEW)
│   │   ├── stylus/
│   │   │   └── StylusSettingsPanel.kt (NEW)
│   │   ├── scrollbar/
│   │   │   └── MovableScrollbar.kt (NEW)
│   │   └── color/
│   │       ├── ColorPicker.kt (NEW - Samsung style)
│   │       └── ColorDot.kt (NEW)
│   ├── screens/
│   │   ├── HomeScreen.kt (REFACTOR)
│   │   ├── NoteEditorScreen.kt (REFACTOR)
│   │   └── SettingsScreen.kt (NEW - card-based layout)
│   └── viewmodel/
│       ├── HomeViewModel.kt (REFACTOR)       ← Private class inside HomeScreen.kt:349
│       ├── NoteEditorViewModel.kt (NEW)      ← To be created (Task 3.11)
│       └── SettingsViewModel.kt (NEW)
├── ink/
│   └── stabilization/
│       └── StrokeStabilizer.kt (NEW) // For smoothing algorithm
```

---

## Definition of Done

- [ ] User can create, rename, move, and delete folders
- [ ] Folder hierarchy supports unlimited nesting depth
- [ ] User can navigate folders via sidebar and breadcrumbs
- [ ] Unified top toolbar (title + tools) with inline colors
- [ ] Toolbar remains visible/pinned at top while drawing (no collapse)
- [ ] Long-press on tools opens settings panels
- [ ] Tool settings accessible via long-press with floating panel
- [ ] Stabilization slider (0-10) visible in pen settings
- [ ] Quick colors customizable, persisted across sessions
- [ ] Samsung-style color picker with spectrum and hex input
- [ ] Template picker with categories (Basic, Education, Music)
- [ ] Template background colors, density, and line width adjustable
- [ ] Page size presets (A3-A7) and custom size
- [ ] S Pen button actions configurable
- [ ] Finger gestures configurable (single/double finger)
- [ ] Scrollbar position can be set to left or right
- [ ] Eraser filters (which tools to erase) functional
- [ ] Settings screen uses card-based layout
- [ ] All tool settings persisted
- [ ] Animations are smooth (60fps) on mid-range tablet
- [ ] UI is usable in both portrait and landscape orientations

---

## References

- Samsung Notes UX patterns (floating panels, color picker, folder organization, inline toolbar colors)
- Notewise UX patterns (templates, stabilization, S Pen config)
- Material Design 3 guidelines for motion and elevation
- iPad Pro productivity app conventions
- Existing Onyx v0 API data contracts (must remain compatible)
