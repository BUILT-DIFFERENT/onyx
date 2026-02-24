# Ink Latency Budget (PERF-01)

Date: 2026-02-24

## Objective

- Keep perceived inking latency consistently low during active handwriting.
- Track regressions via repeatable benchmark and trace sections.

## Current runtime hooks

- Prediction path:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
  - motion prediction adapter is enabled by feature flag and latency mode.
- Frame telemetry:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/gl/InkGlRenderer.kt`
  - renderer logs p50/p95 frame duration and transform-to-frame latency.

## Budget targets (scaffold)

- `InkCanvas#handleTouchEvent` trace-section median: <= 4ms on target hardware.
- `InkGlRenderer#onDrawFrame` trace-section p95: <= 16.7ms.
- Worst jank burst should not exceed 100ms in active inking scenarios.

## Measurement

- Macrobenchmark:
  - `C:/onyx/apps/android/benchmark/src/main/java/com/onyx/android/benchmark/InkLatencyBenchmark.kt`
- Metrics captured:
  - `FrameTimingMetric`
  - `TraceSectionMetric("InkCanvas#handleTouchEvent")`
  - `TraceSectionMetric("InkGlRenderer#onDrawFrame")`

## Follow-up

- Wire CI budget assertions for these trace metrics on benchmark devices.
- Add stylus-input specific scenario fixtures once deterministic device lab routing is in place.

