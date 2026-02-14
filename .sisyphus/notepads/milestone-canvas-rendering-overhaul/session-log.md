# Milestone Canvas Rendering Overhaul - Notepad

**Session ID:** ses_3a84bceafffeq9AcLXH5kMXuR8
**Started:** 2026-02-13T15:52:32.867Z
**Plan:** `/home/gamer/onyx/.sisyphus/plans/milestone-canvas-rendering-overhaul.md`

## Session Progress

### Session 1

- Completed P0.0 pre-gate dependency viability and repository wiring.
- Fixed Android build blockers (JDK 25 parsing, native lib merge conflict, API mismatch in MyScript manager).
- Stabilized spike API surface tests and produced pass result with report artifacts.
- Created canonical corpus document (`.sisyphus/notepads/pdf-test-corpus.md`).
- Updated spike report with P0.1/P0.2/P0.2.5/P0.3/P0.4 status and explicit remaining runtime gaps.

### Session 2

- Completed P1.1 implementation wiring: re-enabled motion prediction flags in both touch and runtime paths.
- Completed P1.2 implementation updates: shared pressure gamma shaping between committed and in-progress pipelines and aligned highlighter alpha mapping for in-progress/predicted strokes.
- Completed P1.4 pre-task (blocking): reduced PDF render zoom buckets from 5 levels to 3 (`1x/2x/4x`) and updated transform math tests.
- Added P1.3 investigation artifact (`.sisyphus/notepads/ink-latency-investigation.md`) with interim decision `PENDING_DEVICE_PROFILE` due missing physical-device Perfetto capture.

### Session 3

- Implemented `P1.4` core cache module: added `StrokeTileCache` with byte-bounded `LruCache`, mutex-guarded multi-step put/get-or-put paths, page/stroke invalidation, low-RAM size policy, and bucket hysteresis helper.
- Implemented `P1.5`: added `FrameBudgetManager` with injectable clock, utilization guard, and over-budget checks for frame work shedding.
- Added essential tests:
  - `StrokeTileCacheTest` for invalidation bounds math, tile range coverage, hysteresis thresholds, and low-RAM size selection.
  - `FrameBudgetManagerTest` for budget enforcement, edge conditions, and constructor guards.
- Applied zoom bucket hysteresis in `NoteEditorShared.kt` for PDF render bucket selection to reduce threshold oscillation.

### Session 4

- Implemented `P2.3` tile rendering foundations:
  - Added `PdfTileCache` (byte-bounded `LruCache`, low-RAM sizing, mutex-guarded put/clear paths).
  - Added `PdfTileRenderer` with tile range math helpers and serialized Pdfium tile rendering (`512x512`, `ARGB_8888`).
- Implemented `P2.4` async tile pipeline:
  - Added `AsyncPdfPipeline` with viewport-driven cancellation, in-flight dedup, mutex-protected request map, and semaphore-capped concurrency.
  - Wired pipeline into `rememberPdfTiles(...)` in `NoteEditorShared.kt` with visible + prefetch tile request generation.
- Updated editor integration:
  - `NoteEditorState.kt` now carries PDF tile state (`pdfTiles`, active bucket, tile size).
  - `NoteEditorScreen.kt` passes viewport + transform into tile request pipeline.
  - `NoteEditorPdfContent.kt` now draws tile layers incrementally and keeps prior-scale tiles visible while current bucket tiles arrive.
- Completed missing `P2.2` UX requirement:
  - Added minimal PDF selection copy action (`Copy` button) wired to clipboard text from `PdfTextSelection.text`.
- Added essential tests:
  - `PdfTileRendererMathTest`
  - `PdfTileCacheTest`
  - `AsyncPdfPipelineTest`
