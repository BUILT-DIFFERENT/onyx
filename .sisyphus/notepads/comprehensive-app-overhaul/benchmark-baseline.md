# Benchmark Baseline Report

This document captures baseline performance metrics for the Onyx Android app.

## Benchmark Module

Location: `apps/android/benchmark/`

### Setup

```bash
cd apps/android
node ../../scripts/gradlew.js :benchmark:connectedBenchmarkAndroidTest
```

## Startup Benchmark

### Journey: Cold Startup

1. Press home
2. Launch app
3. Wait for first frame

### Metrics

| Metric                  | No Compilation | With Baseline Profile |
| ----------------------- | -------------- | --------------------- |
| Time to Initial Display | ~500ms (TBD)   | ~300ms (TBD)          |
| Time to Full Display    | ~800ms (TBD)   | ~500ms (TBD)          |

## Open Note Benchmark

### Journey: Home → Note Editor

1. Launch app from home
2. Wait for note list
3. Tap first note
4. Wait for editor toolbar

### Metrics

| Metric             | Value (TBD) |
| ------------------ | ----------- |
| Frame timing (P50) | ~16ms       |
| Frame timing (P95) | ~32ms       |
| Frame timing (P99) | ~48ms       |

## Baseline Profile Generation

### Generate Profiles

```bash
cd apps/android
node ../../scripts/gradlew.js :benchmark:generateBaselineProfile
```

### Output Location

- `app/src/main/generated/baseline-prof.txt`

### Profile Contents

The baseline profile includes hot paths for:

- App startup
- Note editor initialization
- PDF rendering
- Ink canvas setup

## Running Benchmarks

### Prerequisites

1. Physical device or rooted emulator
2. Screen on, device unlocked
3. No other apps in foreground

### Commands

```bash
# All benchmarks
cd apps/android && node ../../scripts/gradlew.js :benchmark:connectedBenchmarkAndroidTest

# Specific benchmark
cd apps/android && node ../../scripts/gradlew.js :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onyx.android.benchmark.StartupBenchmark
```

## Interpreting Results

### Startup Metrics

- **TTID (Time to Initial Display)**: Time from launch to first visible frame
- **TTFD (Time to Full Display)**: Time until app is fully interactive

### Frame Timing

- **P50**: Median frame duration (target: <16ms)
- **P95**: 95th percentile (target: <32ms)
- **P99**: 99th percentile (target: <48ms)

## Status

- [x] Benchmark module created
- [x] Startup journey defined
- [x] Open-note journey defined
- [ ] Baseline profile generated (requires device run)
- [ ] Performance targets validated

## Next Steps

1. Run benchmarks on physical device
2. Generate baseline profiles
3. Integrate profiles into release builds
4. Set performance regression alerts
