# Dirty-Region Redraw Policy (PERF-03)

Date: 2026-02-24

## Goal

Define incremental redraw behavior for heavy pages so mixed ink/object/PDF content does not force full-scene repaint by default.

## Current implementation shape

- Renderer culling/spatial index:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/gl/InkGlRenderer.kt`
  - committed strokes are bucketed into spatial cells (`SPATIAL_CELL_SIZE`) and only visible buckets are drawn.
- Overlay layers:
  - selection/lasso/hover overlays are rendered separately from committed stroke mesh submission.

## Contract (current wave)

- Committed stroke rendering must use viewport+margin culling before draw submission.
- Active stroke meshes are dynamic and limited to in-progress/finished handoff windows.
- Selection/lasso/hover overlays may redraw every frame, but must not invalidate committed stroke cache structures.
- Any future page-object or PDF overlay integration should preserve this split:
  - cached base content
  - dynamic interaction overlays

## Validation path

- Frame timing regressions tracked through:
  - `C:/onyx/apps/android/benchmark/src/main/java/com/onyx/android/benchmark/InkingFrameRateBenchmark.kt`
  - `C:/onyx/apps/android/benchmark/src/main/java/com/onyx/android/benchmark/LargeNoteStressBenchmark.kt`

