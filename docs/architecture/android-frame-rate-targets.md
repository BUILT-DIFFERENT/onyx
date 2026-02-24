# Android Frame-Rate Targets (PERF-02)

Date: 2026-02-24

## Policy

- Capable devices (120Hz panels): target 120fps during active inking/panning.
- Baseline devices: maintain at least 60fps during active inking/panning.
- Any sustained drop below 60fps on baseline tier is a regression.

## Measurement path

- Macrobenchmark scenario:
  - `apps/android/benchmark/src/main/java/com/onyx/android/benchmark/InkingFrameRateBenchmark.kt`
- Metric:
  - `FrameTimingMetric()` from AndroidX Macrobenchmark.
- Workload:
  - open app
  - open first note
  - perform repeated viewport pans to stress editor frame pacing.

## Follow-up for full enforcement

- Parse benchmark output into CI thresholds by device tier.
- Add fail gates for frame-time percentiles once dedicated benchmark devices are wired in CI.

