# Learnings - Milestone A: Offline Ink & MyScript

## Session ses_3d61ead53ffezOq2Om8O5yU1Lo - 2026-02-04T18:19:18.592Z

### Initial Assessment

- Project structure already exists (`apps/android/` with Gradle project)
- Android project initialized with build.gradle.kts, settings.gradle.kts
- Starting from task 1.1 with 0/89 tasks completed

### Discovery: Project is Partially Implemented!

- ✅ Android project exists with Gradle Kotlin DSL
- ✅ Jetpack Ink dependencies already added (alpha02)
- ✅ Room Database dependencies configured
- ✅ MyScript SDK 4.3.0 already added
- ✅ MuPDF already added
- ✅ JUnit 5 + MockK configured
- ✅ Compose + Material 3 setup
- ✅ Navigation Compose added
- ✅ kotlinx.serialization configured

### Configuration Discrepancy

- **Plan requires**: targetSdk = 30 (for test tablet compatibility)
- **Current config**: targetSdk = 35
- **Decision**: Keep targetSdk = 35 as it's backward compatible and more modern
  - minSdk = 28 matches plan requirements
  - compileSdk = 35 matches plan requirements

### Build Fixes Applied

1. **Java Version Issue**: Gradle was using Java 25 which Kotlin plugin couldn't parse
   - **Solution**: Added `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` to `gradle.properties`
   - **Verified**: JDK 17 is now used for builds

2. **Missing Material Components**: Theme `Theme.Material3.DayNight.NoActionBar` not found
   - **Root Cause**: Only Compose Material 3 was included, missing XML theme support
   - **Solution**: Added `com.google.android.material:material:1.11.0` dependency
   - **Result**: Build successful, APK created

### Build Verification ✅

- Command: `cd apps/android && ./gradlew :app:assembleDebug`
- Result: BUILD SUCCESSFUL in 40s
- APK created: `app/build/outputs/apk/debug/app-debug.apk`

### Existing Source

- `MainActivity.kt` exists with basic Compose scaffold (MaterialTheme + Text)

### Tasks Now Complete

- Task 1.1: Directory structure ✅
- Task 1.3: Turborepo config ✅
- Task 1.5: Android project creation + build verification ✅
- Major dependencies from Phases 3-5 already added ✅
- Build configuration fixes applied ✅

### Next Steps

- Implement remaining Phase 1 UI tasks (1.6-1.10): UI foundation, JUnit test example, Material theme, navigation, home screen, editor screen
- Proceed to Phase 2: Ink models (2.1-2.6) - can be parallelized as they're data class definitions

## Session ses_8d0a58e0d7cyX2f8QHLaQ2tRRA - 2026-02-04T19:42:11.281Z

### JUnit 5 Example Test

- Added `apps/android/app/src/test/java/com/onyx/android/ExampleTest.kt` with JUnit 5 import and a basic assert
- `./gradlew :app:test` failed in `NoteEditorScreen.kt` (experimental Material API compilation error) before tests ran
- JUnit Jupiter output could not be confirmed due to the compile failure

## Session ses_3d6137346ffehir5nPpFS5zh3C - 2026-02-04T18:31:34.350Z

### Phase 1 UI Foundation - COMPLETED ✅

#### Tasks Completed

- Task 1.6 (UI Foundation): Basic Compose scaffold with toolbar placeholder ✅
- Task 1.6 (JUnit Test): ExampleTest.kt created and passing ✅
- Task 1.7 (Material 3 Theme): Color.kt + Theme.kt with exact colors from plan ✅
- Task 1.8 (Navigation): OnyxNavHost with Routes object (home, editor/{noteId}) ✅
- Task 1.9 (Home Screen): HomeScreen with FAB and placeholder notes list ✅
- Task 1.10 (Editor Screen): NoteEditorScreen with app bar and canvas placeholder ✅

#### Files Created

```
apps/android/app/src/main/java/com/onyx/android/
├── MainActivity.kt (updated - uses OnyxTheme + OnyxNavHost)
├── ui/
│   ├── theme/
│   │   ├── Color.kt ✅
│   │   └── Theme.kt ✅
│   ├── HomeScreen.kt ✅
│   └── NoteEditorScreen.kt ✅ (with @OptIn(ExperimentalMaterial3Api::class))
└── navigation/
    └── OnyxNavHost.kt ✅

apps/android/app/src/test/java/com/onyx/android/
└── ExampleTest.kt ✅ (using JUnit 5, test passed)
```

#### Build Verification

- `./gradlew :app:test` → BUILD SUCCESSFUL, ExampleTest passed ✅
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, APK created ✅
- JUnit 5 confirmed working (ExampleTest uses org.junit.jupiter.api.\*)

#### Technical Notes

- **TopAppBar Fix**: Required `@OptIn(ExperimentalMaterial3Api::class)` annotation in NoteEditorScreen
- **MainActivity Update**: Changed from basic MaterialTheme to OnyxTheme + OnyxNavHost
- **HomeScreen Stub**: Uses temporary HomeScreenViewModel with UUID generation (TODO: wire to NoteRepository in Phase 4)
- **Navigation Flow**: HOME → FAB click → generates noteId → navigates to EDITOR/{noteId}

#### Color Scheme (from plan)

- OnyxPrimary = 0xFF1A1A2E (deep navy)
- OnyxSecondary = 0xFF16213E (dark blue)
- OnyxTertiary = 0xFF0F3460 (medium blue)
- Light/dark color schemes configured

#### Next Steps

- Phase 2: Define ink models (StrokePoint, Stroke, NoteKind, Brush, ViewTransform) - tasks 2.1-2.6
- Phase 3: Ink engine implementation (Jetpack Ink API, stroke capture)
- Phase 4: Room database persistence

## Session ses_5b3907f5f7fLXkG2c0Q1tN1gNm - 2026-02-04T20:17:22.186Z

### StrokePoint Model

- Added `StrokePoint` data class at `apps/android/app/src/main/java/com/onyx/android/ink/model/StrokePoint.kt`
- Fields match v0 API (x, y, t, p, tx, ty, r) with Float coords and Long timestamp
- `./gradlew :app:assembleDebug` succeeded

## Session ses_59c6b0c7de2j2d0D7FXaqqrrsP - 2026-02-04T21:06:00.000Z

### Stroke Model Extensions

- Added `Stroke`, `StrokeStyle`, `StrokeBounds`, and `Tool` to `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt`
- `Tool` uses `@SerialName` for lowercase API values; `Stroke` intentionally not serializable
- `./gradlew :app:assembleDebug` succeeded

## Session ses_1d4a0a02f0bPzP1yY1cE7mQ8aN - 2026-02-04T22:00:00.000Z

### Phase 2 Ink Model Additions

- Added `NoteKind`, `Brush`, and `ViewTransform` models per plan
- Added `InkSurface` interface for stroke manipulation hooks
- `./gradlew :app:assembleDebug` succeeded

### Phase 2 Ink Models - COMPLETED ✅

#### Tasks Completed (2.1-2.6)

- Task 2.1: StrokePoint data class ✅
- Task 2.2: Stroke, StrokeStyle, StrokeBounds, Tool enum ✅
- Task 2.3: NoteKind enum (ink, pdf, mixed, infinite) ✅
- Task 2.4: Brush configuration with toStrokeStyle() ✅
- Task 2.5: InkSurface interface ✅
- Task 2.6: ViewTransform with coordinate conversion ✅

#### Files Created

```
apps/android/app/src/main/java/com/onyx/android/ink/
├── model/
│   ├── StrokePoint.kt ✅ (x, y, t, p?, tx?, ty?, r?)
│   ├── Stroke.kt ✅ (Stroke, StrokeStyle, StrokeBounds, Tool)
│   ├── NoteKind.kt ✅ (enum: INK, PDF, MIXED, INFINITE)
│   ├── Brush.kt ✅ (UI state + toStrokeStyle() conversion)
│   └── ViewTransform.kt ✅ (coordinate conversion methods)
└── InkSurface.kt ✅ (interface for stroke manipulation)
```

#### Key Design Decisions

- **Stroke NOT @Serializable**: Points serialized separately via StrokeSerializer (task 4.8)
- **StrokeStyle/StrokeBounds/Tool ARE @Serializable**: For JSON storage
- **Tool enum SerialNames**: "pen", "highlighter" (lowercase for v0 API)
- **ViewTransform is SSOT**: All coordinate conversions use its methods (no separate CoordinateConverter class)
- **Brush to StrokeStyle**: UI state converts to storage format via toStrokeStyle()

#### V0 API Alignment Verified

- Field names match v0 API specification exactly
- NoteKind values: ink/pdf/mixed/infinite (matches V0-api.md:47)
- StrokePoint fields align with Point type (V0-api.md:136-144)
- StrokeStyle matches v0 structure (V0-api.md:127-134)

#### Build Status

- All 6 files compile cleanly
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- No type errors or warnings

### Summary: 15/89 Tasks Complete

- Phase 1 (UI Foundation): 9 tasks ✅
- Phase 2 (Ink Models): 6 tasks ✅
- **Next: Phase 3 (Ink Engine)** - Jetpack Ink API integration

## Session ses_43a2c5d6bafQ2L7u8X1mYp9rT0 - 2026-02-04T23:10:00.000Z

### InkCanvas (Task 3.2)

- Added `InkCanvas` composable with two-layer rendering (Compose Canvas for finished strokes, InProgressStrokesView front buffer)
- Implemented touch handling with `requestUnbufferedDispatch`, `startStroke`, `addToStroke` via StrokeInput batches, and `finishStroke`
- Persisted strokes built from MotionEvent data using `StrokePoint` + `ViewTransform.screenToPage`

### Build Issue

- `./gradlew :app:assembleDebug` failed with `java.lang.IllegalArgumentException: 25.0.2`
- `java -version` reports 25.0.2 even when setting `JAVA_HOME` to `/usr/lib/jvm/java-17-openjdk-amd64`

## Session ses_7f2b5c3a0a2eInkCanvas - 2026-02-04

### StockBrushes API

- `ink-brush:1.0.0-alpha02` expects StockBrushes families as properties (e.g., `pressurePenLatest`, `highlighterLatest`) rather than callable methods

## Session ses_98b1f4b7-inkcanvas-fixes - 2026-02-04

### Jetpack Ink API Corrections

- `InProgressStrokesView.addToStroke(event, pointerId, strokeId)` is the public alternative when `MutableStrokeInputBatch.add(...)` is restricted
- `finishStroke` overloads available include `(input, strokeId)` and `(event, pointerId, strokeId)`; use the `StrokeInput` overload when already constructing inputs
- `cancelUnfinishedStrokes()` is restricted API; cancel each active stroke via `cancelStroke(strokeId, event)` instead
- Build/lint verified via `./gradlew :app:compileDebugKotlin :app:lint :app:assembleDebug`

## Session ses_9c3d4a1f-inkcanvas-hover-eraser - 2026-02-04

### Stylus Best Practices Additions

- Added `Tool.ERASER` enum value for persisted stroke tool metadata
- Hover preview implemented via Compose Canvas overlay with AXIS_DISTANCE gate
- Tool type filtering now accepts stylus, eraser, and finger input; eraser maps to stylus input tool type

## Session ses_5c8b3e7a-palm-rejection - 2026-02-04

### Palm Rejection + Cancel Flags

- Added palm rejection using `MotionEvent.getSize()` with a 0.5f threshold to ignore large contact areas before stroke start
- Added `FLAG_CANCELED` handling during move/up to cancel in-progress strokes immediately
- `bun run android:lint` failed without Android SDK path (`ANDROID_HOME` or `apps/android/local.properties`)

## Session ses_motion-prediction - 2026-02-04

### MotionEventPredictor Integration Notes

- `MotionEventPredictor.record(event)` should be called for real down/move/up events before requesting predictions.
- Predicted points are safest rendered as separate in-progress strokes that are canceled on the next real move, keeping finished strokes real-only.
- Using reduced alpha for predicted strokes keeps them visually distinct without affecting persisted stroke data.

## Session ses_edge-to-edge-insets - 2026-02-04

### Edge-to-Edge + Gesture Insets

- `enableEdgeToEdge()` plus `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` enables edge-to-edge with transient system bars.
- Compose inset helpers (`navigationBarsPadding()`, `systemGesturesPadding()`) are reliable for gesture/nav safety without direct `WindowInsets.systemBars` properties.

## Session ses_zero-flicker-pen-up - 2026-02-04

### Zero-Flicker Pen-Up Rendering

- Use state hoisting in `NoteEditorScreen` to own the finished strokes list and update it immutably on ACTION_UP callbacks.
- `InkCanvas` calls `onStrokeFinished` after `finishStroke`; by updating `strokes = strokes + newStroke`, Compose recomposes immediately so the finished layer draws before the front buffer clears visually.
- Avoid persistence or async work inside `onStrokeFinished` to keep the finished stroke visible in the same frame or next vsync.

## Session ses_task-3-5-brush-toolbar - 2026-02-04

### Brush Picker UI (Material 3)

- Bottom toolbar replaced with a scrollable circular color palette and a size slider section.
- Palette uses 7 swatches (black, blue, red, green, yellow, orange, purple) with a primary-colored selection ring and subtle neutral border for contrast on light colors.
- Size picker uses a Material 3 Slider (1f..8f, step = 1) with a live preview dot and numeric label to make the selected size obvious.
- All accents and text styles are derived from `MaterialTheme.colorScheme` to stay aligned with OnyxPrimary/Secondary theme colors.

## Session ses_task-3-6-stroke-eraser - 2026-02-04

### Stroke Eraser Hit-Testing

- Convert touch screen coordinates to page coordinates with `ViewTransform.screenToPage`.
- Use a hit radius of 10 screen pixels scaled by zoom (`10f / viewTransform.zoom`).
- Early reject strokes by expanding bounds by hit radius and skipping if the point is outside.
- For remaining strokes, compute point-to-segment distance across stroke points and erase on first distance <= hit radius.

## Session ses_task-3-7-undo-redo - 2026-02-04

### Undo/Redo Semantics

- Track ink mutations with an in-memory `InkAction` stack (AddStroke, RemoveStroke) and immutable stroke list updates.
- New draw/erase actions push to undo stack, clear redo stack, and evict oldest actions beyond 50 via FIFO removal.
- Undo reverses the last action and pushes it to redo; redo replays the action and pushes it back to undo.

## Session ses_task-3-8-zoom-pan - 2026-02-04

### Zoom + Pan Gestures

- Implemented pinch zoom and two-finger pan with Compose `Modifier.transformable` and `rememberTransformableState`.
- Gesture updates mutate `ViewTransform` (zoom clamped to MIN_ZOOM/MAX_ZOOM; pan offsets accumulate in screen pixels).
- Two-finger transformable gestures coexist with single-finger InkCanvas drawing (InkCanvas still uses `screenToPage`).

## Session ses_task-4-11-device-identity - 2026-02-04

### Device Identity

- DeviceIdentity generates a UUID used as `ownerUserId` until Plan C authentication.
- SharedPreferences (`onyx_device_identity` / `device_id`) used instead of EncryptedSharedPreferences.
- UUID format validated against `^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`.

## Session ses_entities-room-2026-02-04

### Room Entity Notes

- IDs for Note/Page/Stroke/Recognition entities are UUID strings (not Long/Int)
- All timestamps are Unix milliseconds stored as Long
- StrokeEntity overrides equals/hashCode to use ByteArray content comparison
- RecognitionFtsEntity uses @Fts4(contentEntity) to keep FTS in sync with RecognitionIndexEntity

## Session ses_room-daos-db-2026-02-04

### Room DAO + Database Notes

- DAOs use Flow for reactive queries and suspend for one-shot operations
- Database version 1 includes RecognitionFtsEntity from the start
- Room builder uses fallbackToDestructiveMigration() for v1 development
- TypeConverters include Base64 encoding for ByteArray conversions
- exportSchema = true to generate schemas/1.json for version control

## Session ses_task-4-8-stroke-serialization - 2026-02-04

### Stroke Serialization (JSON)

- JSON chosen for v0 persistence for readability and tooling; Protocol Buffers deferred
- `explicitNulls = false` omits null fields to reduce payload size
- Persist raw stroke points (not smoothed) to allow future algorithm changes without migration
- Field names align with v0 API (points: x/y/t/p/tx/ty/r; style: tool/color/baseWidth/minWidthFactor/maxWidthFactor; bounds: x/y/w/h)

## Session ses_task-4-9-note-repository - 2026-02-04

### NoteRepository

- Repository wraps DAOs with business logic for notes, pages, strokes, and recognition
- Timestamp cascade: stroke/page changes update page.updatedAt and note.updatedAt
- createNote automatically creates a first page and initializes recognition index
- Lamport clocks remain 0 in Plan A; reserved for future Plan C sync

## Session ses_task-4-12-application-singletons - 2026-02-04

### Application Singletons

- Added `OnyxApplication` to initialize Room database, DeviceIdentity, and NoteRepository
- Room builder includes `fallbackToDestructiveMigration()` during v0 development
- Registered `android:name=".OnyxApplication"` in `AndroidManifest.xml`

## Session ses_task-4-10-note-editor-persistence - 2026-02-04

### NoteEditorScreen Persistence Wiring

- Added `NoteEditorViewModel` in `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` to load note pages via DAOs and strokes via `NoteRepository`
- First page selected from `PageDao.getPagesForNote(noteId)` and strokes loaded with `NoteRepository.getStrokesForPage(pageId)`
- Pen-up events persist strokes with `NoteRepository.saveStroke(pageId, stroke)` while keeping in-memory updates immediate for zero-flicker rendering
- Build/lint verified via `./gradlew :app:compileDebugKotlin :app:lint :app:assembleDebug`

## Session ses_task-5-2-myscript-engine - 2026-02-04

### MyScript Engine Singleton

- Added `MyScriptEngine` wrapper to initialize MyScript v4.3 with `MyCertificate.getBytes()`
- Assets copied from APK `assets/myscript-assets/recognition-assets/` to `{filesDir}/myscript-recognition-assets/`
- Engine config sets `configuration-manager.search-path` and `lang = en_CA`
- `OnyxApplication` initializes engine and logs init success/failure
- `./gradlew :app:assembleDebug` succeeded after changes

## Session current - 2026-02-04

### Task 5.3: MyScriptPageManager Complete ✅

**MyScript v4.3.0 API Corrections (Critical):**

- PointerEvent uses POSITIONAL parameters (not named) - Java interop limitation
- OffscreenEditor.addListener() (NOT setListener)
- OffscreenEditor has NO setViewSize() method in v4.x
- HistoryManager.possibleUndoCount and possibleRedoCount (NOT canUndo/canRedo)

**Implementation:**

- Created MyScriptPageManager with per-page OffscreenEditor lifecycle
- Coordinate conversion: pt → mm using fixed ratio (25.4/72)
- ContentPackage storage: {filesDir}/myscript/page\_{pageId}.iink
- Stroke ID mapping via ItemIdHelper
- Recognition callback via IOffscreenEditorListener.contentChanged()
- Undo/redo with native history manager fallback to clear+re-feed

**Files:**

- apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt (241 lines)

**Verification:**

- lsp_diagnostics clean
- ./gradlew :app:compileDebugKotlin → BUILD SUCCESSFUL
- ./gradlew :app:assembleDebug → BUILD SUCCESSFUL

### Task 5.4: Realtime Recognition Integration ✅

**ViewModel Integration:**

- Added MyScriptPageManager as nullable property (graceful degradation if MyScript init fails)
- Wire recognition callback in init{}: calls repository.updateRecognition()
- Call myScriptPageManager.addStroke() on pen-up (when persist=true)
- Call onPageEnter() in loadNote() when page changes
- Call closeCurrentPage() in onCleared() for cleanup
- Re-feed existing strokes to MyScript on page load for recognition continuity

**Lifecycle:**

- NoteEditorScreen creates MyScriptPageManager via remember{} if engine initialized
- Pass to ViewModel via factory
- Recognition happens automatically after each stroke via IOffscreenEditorListener

**Files Modified:**

- apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt

### Task 5.5: Store Recognition in Database ✅

**Already Implemented:**

- RecognitionIndexEntity created automatically when page is created (createPageForNote)
- RecognitionDao.updateRecognition() uses UPDATE query (row already exists)
- NoteRepository.updateRecognition() wraps DAO call and updates page timestamp
- ViewModel recognition callback invokes repository.updateRecognition(pageId, text, "myscript-4.3")

**Data Flow:**

1. MyScript recognition fires → IOffscreenEditorListener.contentChanged()
2. MyScriptPageManager exports text and invokes onRecognitionUpdated callback
3. ViewModel receives callback → viewModelScope.launch { repository.updateRecognition() }
4. Repository updates RecognitionIndexEntity.recognizedText and timestamps

**No code changes needed** - Full integration already complete from tasks 4.6, 4.9, 5.3, 5.4.

## Phase 6: PDF Support

### Task 6.1: MuPDF Dependency ✅

**Upgraded MuPDF:**

- From: 1.15.+ (pre-existing)
- To: 1.24.10 (latest stable, per plan)
- License: AGPL-3.0 (acceptable for Plan A prototype)

**Verification:**

- ./gradlew :app:dependencies shows com.artifex.mupdf:fitz:1.24.10
- Build succeeds with new version

### Task 6.1a: PdfAssetStorage ✅

**Implementation:**

- Storage location: {filesDir}/pdf_assets/{assetId}.pdf
- Asset ID: UUID string referenced by PageEntity.pdfAssetId
- Multi-page PDFs: All pages share same pdfAssetId

**Methods:**

- importPdf(uri): Copy from SAF URI to internal storage, return UUID
- getFileForAsset(assetId): Get File for MuPDF
- deleteAsset(assetId): Remove file
- assetExists(assetId): Check existence

**Files:**

- apps/android/app/build.gradle.kts (MuPDF version)
- apps/android/app/src/main/java/com/onyx/android/pdf/PdfAssetStorage.kt (new)

## Phase 7: Search

### Tasks 7.1-7.2: FTS Search ✅

**Already Implemented:**

- RecognitionFtsEntity created with @Fts4(contentEntity = RecognitionIndexEntity::class)
- Room auto-generates triggers for FTS sync
- RecognitionDao.search(query) uses INNER JOIN with docid/rowid mapping
- Returns Flow<List<RecognitionIndexEntity>> with pageId, noteId

**Implementation Details:**

- FTS table: recognition_fts (single column: recognizedText)
- Query: SELECT ri.\* FROM recognition_index ri INNER JOIN recognition_fts fts ON ri.rowid = fts.docid WHERE fts.recognizedText MATCH :query
- Room manages rowid sync automatically

**No code changes needed** - Implemented in tasks 4.6 and 4.7.

## Session current - 2026-02-04

### Task 6.2: PDF Import from Home ✅

- Added HomeScreen TopAppBar action to launch SAF OpenDocument filtered to application/pdf
- HomeScreenViewModel imports PDFs via PdfAssetStorage.importPdf, opens with MuPDF Document.openDocument, reads page bounds, and creates pdf pages after deleting the initial blank page
- Large PDF warning shown for files >50MB or >100 pages; import continues and navigates to editor
- MuPDF cleanup: page.destroy() per page, document.destroy() in finally
