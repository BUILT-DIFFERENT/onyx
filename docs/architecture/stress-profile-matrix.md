# Stress Profile Matrix (PERF-04)

Date: 2026-02-24

## Purpose

Provide reproducible heavy-load scenarios for note and PDF workloads with frame/memory metrics.

## Scenario matrix (MVP)

1. Large note scroll stress
- Flow: open home, select note, repeated long scroll up/down.
- Metrics: frame pacing + memory usage.
- Benchmark: `C:/onyx/apps/android/benchmark/src/main/java/com/onyx/android/benchmark/LargeNoteStressBenchmark.kt`

2. Active editor frame pacing
- Flow: open note, repeated interaction in editor viewport.
- Metrics: frame pacing.
- Benchmark: `C:/onyx/apps/android/benchmark/src/main/java/com/onyx/android/benchmark/InkingFrameRateBenchmark.kt`

3. Ink latency trace sections
- Flow: open note, repeated editor input events.
- Metrics: trace sections + frame timing.
- Benchmark: `C:/onyx/apps/android/benchmark/src/main/java/com/onyx/android/benchmark/InkLatencyBenchmark.kt`

## Baseline acceptance scaffolding

- No OOM during benchmark iterations.
- No sustained frame-time collapse across repeated interaction loops.
- Track output artifacts for trend/regression comparison in CI device runs.

## Follow-up

- Add deterministic synthetic fixture import for:
  - thousands of strokes/page
  - multi-hundred page documents
  - mixed PDF+ink notes
- Promote matrix into hard thresholds once lab devices are stable.

