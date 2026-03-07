# Android Foundation Plan

## Overview

This plan covers Android technical foundation work that runs alongside and after the Editor Feel Milestone (`editor-feel-refactor.md`). It is organized into six phases that can be started in parallel where noted.

**Authority hierarchy:**
1. `editor-feel-refactor.md` — ink/editor feel work (immediate priority, not covered here)
2. This plan — remaining Android technical foundation
3. `PLAN.md` — architecture and roadmap reference
4. Warp UX Spec (plan ID: `ad9feeea-4daa-4ca9-b975-17f3dbbb0ad4`) — UI/UX authority for all editor and library UI decisions

**Scope:** Android app only. Web viewer: `milestone-b-web-viewer.md`. Collaboration/sync: `milestone-c-collaboration-sharing.md`.

**Out of scope:** Ink/editor feel (in `editor-feel-refactor.md`), web viewer, Convex backend, real-time collaboration.

**Completion gates:**
- `bun run android:lint` passes (primary gate)
- `bun run android:test` passes for tests actively touched in each phase (known pre-existing drift in untouched test files is acceptable — see AGENTS.md)
- No Sev-1 regressions in PDF, library, or search flows

**Device matrix:**
- Tier A (primary): Samsung tablet with S Pen
- Tier B (baseline): Pixel-class phone
- Tier C (stress): Low-RAM emulator

**Performance SLOs:**
- PDF tile first-visible: p95 ≤ 120ms after viewport settles
- Frame jank: ≤ 3% janky frames during sustained draw or PDF pan/zoom
- Memory: bitmap caches within 25–35% of memoryClass

---

## Phase A — Infrastructure & Quality Gates

Can start immediately in parallel with editor-feel work.

### A.1 Feature flags and kill switches

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/config/FeatureFlags.kt` (new)
- `apps/android/app/src/main/java/com/onyx/android/config/FeatureFlagStore.kt` (new)
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`

Create a unified runtime flag layer backed by DataStore. Flag catalog with defaults:
- `ink.prediction.enabled` = false → safe path: no prediction
- `ink.handoff.sync.enabled` = true → safe path: immediate finalize
- `pdf.tile.throttle.enabled` = true → safe path: unthrottled scheduler
- `ui.editor.compact.enabled` = true

Add a debug-only `DeveloperFlagsScreen` composable accessible from toolbar overflow in debug builds only.

**Must not:** ship risky renderer behavior without a runtime rollback toggle.

**Acceptance criteria:**
- Flags visible in debug menu and persist across restart
- Rollback to safe path without reinstall
- `bun run android:lint` passes after refactor
- Tests directly related to feature flags pass

### A.2 Performance instrumentation baseline

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/ink/perf/PerfInstrumentation.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/perf/FrameBudgetManager.kt`

Extend instrumentation to emit structured debug events:
- Tag: `OnyxPerf`
- Event keys: `frame_ms`, `stroke_finalize_ms`, `tile_render_ms`, `tile_queue_depth`, `jank_percent`

Capture baseline p50/p95 metrics on Tier A/B/C devices. Record in `.sisyphus/notepads/baseline-metrics-android.md` with raw sample counts and exact journey definitions.

Journeys to benchmark (each ≥300 frames):
- **Sustained draw**: Open note → draw 50 continuous strokes → measure `frame_ms` and `stroke_finalize_ms`
- **PDF pan/zoom**: Open 20-page PDF note → pan through 10 pages → pinch-zoom 3 times → measure `tile_render_ms` and `jank_percent`
- **Note open**: Cold-launch → open existing 10-page note → measure time-to-interactive

**Acceptance criteria:**
- Baseline report committed with samples ≥ 300 frames for each of the 3 journeys above
- SLO thresholds recorded with numeric targets for each metric

### A.3 Test harness: Paparazzi + Maestro + macrobenchmark

**Files:**
- `apps/android/app/build.gradle.kts`
- `.github/workflows/ci.yml`
- `apps/android/maestro/flows/editor-smoke.yaml` (new)
- `apps/android/benchmark/` (new module)

Add Paparazzi screenshot test plugin with first golden for editor top bar. Add Maestro flow covering: app launch → create note → draw stroke → verify toolbar action. Add macrobenchmark module for startup and open-note journeys. Wire `bun run android:lint` as required CI gate and `bun run android:test` as informational (non-blocking while known drift remains) in `.github/workflows/ci.yml`.

**Acceptance criteria:**
- All three harnesses run locally and in CI
- Golden diff produces actionable artifact output
- PR CI fails if `android:lint` fails; `android:test` results are published as informational

---

## Phase B — PDF Engine Hardening

**Current state:** Pdfium already integrated (`com.github.Zoltaneusz:PdfiumAndroid:v1.10.0`). Tile/cache/async pipeline exists in `apps/android/app/src/main/java/com/onyx/android/pdf/`. This phase is hardening and architecture cleanup, not first-time migration.

### B.1 Renderer adapter boundary

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumRenderer.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`

Introduce `PdfRenderEngine` / `PdfTextEngine` interfaces so editor UI depends on interfaces, not concrete Pdfium wrappers. Route all callers through the adapter. Confirm no MuPDF/AGPL transitive dependency via `gradlew :app:dependencies`.

**Acceptance criteria:**
- Adapter allows renderer swap in tests
- `gradlew :app:dependencies` confirms `Zoltaneusz/PdfiumAndroid` present, no MuPDF/artifex entries
- `PdfiumDocumentSessionTest` and `PdfiumNativeTextBridgeTest` pass

### B.2 Tile scheduler — frame-aligned and bounded

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/pdf/AsyncPdfPipeline.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt`

Move tile request trigger from raw gesture event storms to frame-aligned cadence. Harden bounded in-flight request queue with explicit stale-cancellation invariants. Add metrics counters for queue depth and tile-visible latency.

**Must not:** allow unbounded in-flight requests during flings.

**Acceptance criteria:**
- In-flight queue remains bounded under stress scroll
- p95 tile-visible latency ≤ 120ms
- `AsyncPdfPipelineTest` passes

### B.3 Cache lifecycle race hardening

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileCache.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`

Guard LRU access/recycle paths with mutex-safe lifecycle handling. Check `bitmap.isRecycled` at draw time before use. Add concurrency test scenarios for eviction + cancellation overlap.

**Must not:** recycle bitmaps still referenced for draw.

**Acceptance criteria:**
- No recycle/draw crash in stress tests
- Memory stays within 25–35% memoryClass budget under repeated open/close/zoom
- `PdfTileCacheTest` passes

### B.4 Visual continuity — scale bucket hysteresis

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`

Enforce 1x/2x/4x scale bucket policy with hysteresis band. Keep previous content visible until replacement tile is ready. Controlled crossfade on bucket transitions — no blank or black flash.

**Acceptance criteria:**
- No black/blank transition in normal navigation
- Zoom-in does not remain blurry beyond one settle window

### B.5 PDF interaction parity

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`
- `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumNativeTextBridge.kt`
- `apps/android/app/src/main/java/com/onyx/android/pdf/TextSelectionModel.kt`

Add page-jump input. Improve text selection quality and clipboard interaction. Validate gesture coexistence with draw and pan modes — draw-mode gesture routing must not degrade.

**Acceptance criteria:**
- Page jump works on large PDFs
- Text selection handles and clipboard copy succeed reliably

---

## Phase C — UI Architecture

For all UI/layout decisions, consult the Warp UX Spec (plan ID: `ad9feeea-4daa-4ca9-b975-17f3dbbb0ad4`) before implementing. Note: canonical editor UI files are under `apps/android/app/src/main/java/com/onyx/android/ui/editor/`; `ui/editor/components/` contains legacy duplicates and is not source of truth (see AGENTS.md).

### C.1 Decompose NoteEditorUi into focused composables

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/ui/` (editor/ subdirectory)

Split into dedicated composables: `EditorTopBar`, `ToolSettingsPanel`, `ColorPickerDialog`, `EditorScaffold`. State hoisting predictable and independently testable. No business logic in composables.

**Acceptance criteria:**
- Component boundaries implemented
- Behavior parity with existing editor preserved
- `NoteEditorViewModelTest` and `NoteEditorToolbarTest` pass

### C.2 Responsive top bar — three zones per UX spec

**Files:** Editor toolbar components under `ui/editor/`

Implement the three-zone top bar per UX Spec §4.2: Zone 1 (navigation/page), Zone 2 (tools/colors, horizontally scrollable when wide), Zone 3 (note settings/actions). All primary controls ≥ 48×48dp touch target. Chevron overflow indicator at Zone 2 end when clipped.

**Acceptance criteria:**
- All controls reachable in all screen widths
- Accessibility scan confirms touch target compliance
- `NoteEditorTopBarTest` passes

### C.3 Home screen ViewModel extraction

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt`

Extract inline Home state into a dedicated `HomeViewModel`. Normalize loading/error/empty state flows. Note: `HomeScreenViewModel` currently lives inside `HomeScreen.kt` same file (per AGENTS.md).

**Acceptance criteria:**
- Home UI state is ViewModel-driven
- Error/retry behavior covered by tests

### C.4 Hilt DI migration

**Files:**
- `apps/android/app/build.gradle.kts`
- `apps/android/build.gradle.kts`
- `apps/android/app/src/main/java/com/onyx/android/AppContainer.kt`
- `apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt`

Add Hilt Gradle plugin and annotation processing (note: `com.google.dagger.hilt.android` must be declared with version in root `apps/android/build.gradle.kts` with `apply false`, per AGENTS.md). Replace `AppContainer` with Hilt modules for database/repository/device identity/PDF/MyScript. Annotate with `@HiltAndroidApp` and `@AndroidEntryPoint`. Use `@HiltViewModel`. Remove all `requireAppContainer()` call sites.

**Must not:** break startup ordering or lazy MyScript initialization semantics.

**Acceptance criteria:**
- No `requireAppContainer()` in primary UI screens
- App launches and opens note successfully
- `bun run android:lint` passes after migration
- Tests directly affected by Hilt migration pass (run with `./gradlew :app:testDebugUnitTest --tests "..."`)

### C.5 Startup — SplashScreen API

**Files:** `apps/android/app/build.gradle.kts`, `MainActivity.kt`

Adopt Android SplashScreen API for predictable cold-start UX. No blocking work on UI thread. No blank window on startup handoff. Measure before/after startup via benchmark module from A.3.

**Acceptance criteria:**
- Cold start visibly stable with no blank flash
- Startup benchmark shows non-regressing trend

---

## Phase D — Library & Organization

For home/library UI decisions, consult the Warp UX Spec §2 (Home / Library Screen).

### D.1 Folder and template data model

Folder model changes:
- `FolderEntity` supports `parentFolderId: String?` for arbitrary nesting (already exists — verify self-referential FK constraint in Room)
- Folders are **server-synced** via Convex `folders` table (not local-only). The Room `FolderEntity` is a local cache of the Convex state.
- `NotebookEntity.folderId` FK constraint enforced (cascade delete or set-null on folder delete — choose set-null per UX spec: notebooks move to root)
- Add Room migration incrementing DB version to add any missing columns/indexes

Template metadata model:
- `PageTemplateEntity` (or extend `PageEntity`) with: `templateType` (blank/lined/dotted/grid), `templateDensity: Float`, `templateLineWidth: Float`, `backgroundColorHex: String`
- Default template stored in `EditorSettingsEntity` per UX Spec §3.2/§7: `defaultTemplateType`, `defaultTemplateDensity`, `defaultNotebookMode` (paged/infinite)
- Room migration for template columns

**Migration policy:** greenfield rules apply (per AGENTS.md). Destructive migrations are acceptable during this phase when they unblock schema alignment; prioritize clear current-state schema over backward compatibility.

**Acceptance criteria:**
- Room migration compiles and `OnyxDatabaseMigrationTest` passes
- Folder CRUD test: create nested folder → move notebook into it → delete parent folder → notebook.folderId is null (set-null cascade)
- Template CRUD test: create page with template → reopen → template properties match original
- Default template test: change default template → create new notebook → new notebook's first page uses updated defaults

### D.2 Thumbnail pipeline and batch operations

Thumbnail pipeline:
- Thumbnail generated on background coroutine (`Dispatchers.Default`) after: notebook save, page edit commit, notebook close
- Thumbnail is a scaled-down bitmap (256×362px, A4 aspect ratio) of the first page, rendered from committed stroke data + template background
- Stored as a file in app internal storage, path referenced in `NotebookEntity.thumbnailPath`
- `ThumbnailGenerator.kt` (already exists) hardened with: skip if notebook unchanged since last thumbnail, cancel in-flight generation if notebook is re-edited, LRU cache for loaded bitmaps

Batch operations (UX Spec §2.6):
- Multi-select mode activated via top-right checkmark icon
- Bottom action bar with: Move (to folder picker), Delete (soft-delete to trash), Export (PDF generation)
- Batch delete: all selected notebooks get `deletedAt` timestamp → appear in Trash
- Batch move: all selected notebooks get `folderId` updated → navigate back to folder

**Acceptance criteria:**
- Thumbnail test: create notebook → draw stroke → close → reopen home → thumbnail matches first page content (visual regression or pixel-sample test)
- Thumbnail performance: generation completes within 500ms for a 10-stroke page on Tier B device
- Batch delete test: select 3 notebooks → delete → all 3 appear in Trash with `deletedAt` set → restore one → it reappears in original folder
- Batch move test: select 2 notebooks → move to folder X → both notebooks' `folderId` equals folder X's ID
- Large list scroll: 100 notebooks in grid view, no dropped frames during fast scroll (verified via `jank_percent` < 3%)

### D.3 Room-backed editor settings

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` (current `rememberBrushState`)
- new `EditorSettingsEntity` / `EditorSettingsDao` under `data/entity/` and `data/dao/`

Persist editor tool settings currently in-memory: brush/tool selection, widths, eraser size. Wire through repository. Keep startup restore idempotent.

Note per AGENTS.md: `EditorSettingsEntity/Dao/Repository` may partially exist. Full completion requires `OnyxDatabase` entity+migration and `NoteEditorScreen` consuming `viewModel.editorSettings` instead of local-only `rememberBrushState`.

**Acceptance criteria:**
- Tool settings persist across app restart
- `OnyxDatabaseMigrationTest` passes

---

## Phase E — Recognition & Unified Search

### E.1 MyScript pipeline hardening

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptEngine.kt`
- `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt`
- `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt`

Add debounced recognition scheduling — never on the hot path. Configurable recognition language. Graceful degradation on session failures without editor lockup.

**Acceptance criteria:**
- Recognition scheduling is debounced and observable
- Recovery path exists for session failures
- `MyScriptCoordinateTest` passes

### E.2 Unified search surface

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt` (`searchNotes`)
- `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt`
- `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumNativeTextBridge.kt`

One search entry point combining: handwriting recognition text, PDF text content, and notebook metadata/titles. Source-type labels on results. Result model fields: `sourceType`, `notebookId`, `pageId`, `snippet`, `score`. Index rebuild path for corrupted/outdated states.

**Acceptance criteria:**
- Single search bar returns mixed-source results
- Tapping result navigates to correct notebook/page location
- Automated tests cover all three source types

### E.3 Conversion and recognition overlays

Recognition overlay toggle that stays correctly aligned on zoom/pan. Lasso → text conversion flow with editable output. Lasso → LaTeX conversion per UX Spec §6.3. Scratch-out gesture if available via MyScript SDK.

**Depends on:** E.1 and E.2 complete.

**Acceptance criteria:**
- Overlay toggleable and does not misalign on zoom/pan
- Conversion output editable and persistable

---

## Phase F — Reliability & Release

### F.1 Data safety and CRDT-readiness primitives

**Files:**
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasStroke.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt`
- `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt`
- `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt`
- `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt`
- `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt`
- `apps/android/app/src/main/java/com/onyx/android/data/entity/OfflineQueueEntity.kt` (new)
- `apps/android/app/src/main/java/com/onyx/android/data/dao/OfflineQueueDao.kt` (new)

Prepare the local storage layer for Y-Octo CRDT integration:
- Yjs binary file scaffold: utility class for reading/writing per-page and per-notebook Yjs binary files (format: 4-byte magic `ONYX` + uint32 version + raw Yjs encoded state array) to app internal storage.
- `OfflineQueueEntity` / `OfflineQueueDao`: Room table for queuing pending Yjs update binaries when offline. Fields: `id`, `notebookId`, `pageId?`, `updateBinary` (base64), `createdAt`. WorkManager will drain this queue via `sync.pushUpdates` when online.
- Validate deterministic resource cleanup on document close and app shutdown.
- Room remains the metadata/settings cache; Yjs binary files are the authoritative content store.

**Must not:** implement full sync behavior in this scope. This is the local scaffold only.

**Acceptance criteria:**
- Yjs binary file read/write utility compiles and round-trip test passes (write file → read back → identical bytes)
- OfflineQueueEntity scaffold compiles, DAO queries are test-covered
- Resource cleanup on document close does not leak file handles

### F.2 CI gates and performance budgets

**Files:** `.github/workflows/ci.yml`, `turbo.json`, `package.json`

Enforce `bun run android:lint` as the required PR CI gate. `bun run android:test` runs in CI as informational (non-blocking) until all pre-existing test drift is resolved. Publish pass/fail thresholds for jank, latency, memory. Ensure `turbo.json` hashes `.env*` for relevant tasks.

**Acceptance criteria:**
- PR CI fails if `android:lint` fails
- `android:test` runs in CI as informational; new test failures introduced by PRs must be fixed before merge
- Perf budget failures surface as actionable test output
- `turbo.json` includes `.env*` hashing for affected tasks

### F.3 Physical-device verification and rollout

**Files:** `apps/android/verify-on-device.sh`, `apps/android/DEVICE-TESTING.md`

Execute physical-device verification tasks and collect evidence. Use feature flags (from A.1) for staged rollout. Produce go/no-go release checklist with fallback paths. Archive evidence under `.sisyphus/notepads/device-validation/`.

**Must not:** mark phase complete without physical-device evidence for gated items.

**Acceptance criteria:**
- All blocker tasks closed or explicitly risk-accepted with documentation
- Rollout checklist signed with feature-flag fallback path documented
