# Canvas Overhaul Status Reconciliation (2026-03-04)

Purpose: reconcile `.sisyphus/plans/milestone-canvas-rendering-overhaul.md` checkbox state against current repository implementation.

Legend:
- `Done` = implemented in code and/or tests with concrete evidence.
- `Partial` = some implementation exists but acceptance evidence is incomplete.
- `Blocked` = primarily pending physical-device/runtime gate.

## Phase 0

| Task | Plan Checkbox | Actual | Evidence |
|---|---|---|---|
| P0.0 Pre-gate fork viability | `[x]` section contains unchecked sub-items | Done | `apps/android/settings.gradle.kts` has `jitpack.io`; `apps/android/app/build.gradle.kts` has `com.github.Zoltaneusz:PdfiumAndroid:v1.10.0`; spike/session verification in `.sisyphus/notepads/milestone-canvas-rendering-overhaul/session-log.md`. |
| P0.1 Spike tasks | `[x]` section contains unchecked sub-items | Done | `apps/android/spikes/pdfium-spike/` exists and was validated per `.sisyphus/notepads/milestone-canvas-rendering-overhaul/verification.md`. |
| P0.2 Go/No-Go | `[x]` | Done | Spike notes + dependency wired in app module. |
| P0.2.5 Dependency viability check | `[x]` | Done | Pdfium dependency resolves in project build config. |
| P0.3 Exit artifacts | `[x]` | Done | `.sisyphus/notepads/pdf-test-corpus.md`, `.sisyphus/notepads/pdfium-spike-report.md`. |
| P0.4 App integration smoke | `[x]` | Partial | Integration compiles and lint/test gates pass, but final device/runtime validation still referenced as pending in milestone issues. |

## Phase 1

| Task | Plan Checkbox | Actual | Evidence |
|---|---|---|---|
| P1.1 Re-enable motion prediction | `[x]` | Done | Ink touch/runtime prediction paths present; session log marks complete. |
| P1.2 Align in-progress vs finished styles | `[x]` | Done | Session notes + stroke rendering path updates and tests. |
| P1.3 Front-buffered rendering investigation | `[x]` | Done (investigation outcome) | `.sisyphus/notepads/ink-latency-investigation.md` captures decision/status. |
| P1.4 Tile-based stroke caching | `[x]` | Done | `ink/cache/StrokeTileCache.kt` + `StrokeTileCacheTest.kt`. |
| P1.5 Frame budget manager | `[ ]` | Done | `ink/perf/FrameBudgetManager.kt` + `FrameBudgetManagerTest.kt`; verification note includes Session 3 validation. |

## Phase 2

| Task | Plan Checkbox | Actual | Evidence |
|---|---|---|---|
| P2.0 Renderer-agnostic text model | `[x]` | Done | Pdf text model/types are renderer-agnostic in current pdf/ui state layer. |
| P2.1 Pdfium integration | `[x]` | Done | `PdfiumAndroid` dependency configured; Pdfium session/renderer classes in `apps/android/app/src/main/java/com/onyx/android/pdf/`. |
| P2.2 Text selection parity | `[x]` | Done | `NoteEditorPdfContent.kt` selection + copy flow; tests updated for selection state. |
| P2.3 Tile-based rendering | `[x]` | Done | `PdfTileRenderer.kt`, `PdfTileCache.kt`, `PdfTileRendererMathTest.kt`, `PdfTileCacheTest.kt`. |
| P2.4 Async pipeline | `[x]` | Done | `AsyncPdfPipeline.kt` + `AsyncPdfPipelineTest.kt`; wired in `rememberPdfTiles(...)`. |
| P2.5 Keep previous content during render | `[ ]` | Done | Decision and code path keep previous-scale tiles while new bucket renders (`NoteEditorPdfContent.kt`, milestone decisions note). |
| P2.6 Migration & cleanup | `[ ]` | Partial | MuPDF removed from runtime dependency path; Pdfium path active. Some release/device validation evidence still pending in milestone notes. |

## Phase 3

| Task | Plan Checkbox | Actual | Evidence |
|---|---|---|---|
| P3.1 LazyColumn page layout | `[ ]` | Done | `ui/editor/EditorScaffold.kt` documents and implements multi-page `LazyColumn` continuous scroll. |
| P3.2 Multi-page ink coordinate system | `[ ]` | Done | Page-aware editor/viewmodel state and recognition page routing (`activeRecognitionPageId`, page-index transform handling). |
| P3.3 Virtualized stroke loading | `[ ]` | Partial | Visible page tracking hooks exist (`onVisiblePagesChanged`), but full acceptance/perf evidence not clearly captured in one canonical report. |
| P3.4 Thumbnail strip | `[ ]` | Done | `ui/ThumbnailStrip.kt` + integration in editor scaffold/screen. |
| P3.5 Page outline navigation | `[ ]` | Done | `ui/PdfOutlineSheet.kt` + `onLoadOutline` integration in `NoteEditorScreen.kt`. |

## Phase 4

| Task | Plan Checkbox | Actual | Evidence |
|---|---|---|---|
| P4.0 DB schema/migration | `[ ]` | Done | `OnyxDatabase.kt` now at v19 with migrations; migration tests in `OnyxDatabaseMigrationTest.kt`. |
| P4.1 Thumbnail generation pipeline | `[ ]` | Done | `data/thumbnail/ThumbnailGenerator.kt`, `ThumbnailDao`, repository integration. |
| P4.2 Folder organization | `[ ]` | Done | `FolderEntity`, `FolderDao`, tree UI and state in `HomeScreen.kt`. |
| P4.3 Tags/labels | `[ ]` | Done | `TagEntity`, `NoteTagCrossRef`, tag CRUD/filtering in repo/home UI. |
| P4.4 Multi-select/batch ops | `[ ]` | Done | Home multi-select actions and repository batch operations. |
| P4.5 Sort/filter options | `[ ]` | Done | Sort/filter state and query paths in Home screen and repository/DAO. |

## Phase 5

| Task | Plan Checkbox | Actual | Evidence |
|---|---|---|---|
| P5.1 Stroke ghosting fix | `[ ]` | Done | Ink canvas/touch code contains ghosting-specific guard/fix comments and routing changes. |
| P5.2 Canvas flashing fix | `[ ]` | Partial | Prior-content tile retention is implemented; end-to-end visual validation on device still tracked as pending. |
| P5.3 Toolbar occlusion fix | `[ ]` | Done | Toolbar/scaffold refinements and UI tests (`NoteEditorToolbarTest.kt`). |
| P5.4 Page boundary indicators | `[ ]` | Done | Page boundary indicator rendering hooks in PDF/editor content. |
| P5.5 Performance profiling | `[ ]` | Partial | Perf-related docs/tests exist, but canonical latest device profiling evidence is not consolidated in one current report. |
| P5.6 Memory leak audit | `[ ]` | Partial | Cache lifecycle controls exist; explicit leak-audit signoff artifact not found as a final gate doc. |
| P5.7 Migration verification/tests | `[ ]` | Done | Extensive migration test coverage in androidTest and unit tests. |
| P5.8 Final device validation | `[ ]` | Blocked | Device-validation notes still indicate runtime/device signoff pending for full gate closure. |

## Overall Reconciliation Summary

- Plan checkbox state is stale and under-reports implemented work from P1-P4 and parts of P5.
- Remaining true closure risk is concentrated in final runtime/device validation and a few acceptance-evidence artifacts (P3.3, P5.2, P5.5, P5.6, P5.8).
- Recommendation: treat this file as canonical status until the plan checkboxes are fully synchronized.
