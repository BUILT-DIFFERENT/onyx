# Test Execution Model

This document defines the test execution model for the Onyx Android app. All gates are mandatory unless explicitly marked as optional.

## PR-Required Gates

These gates must pass before any PR can be merged.

### Lint

```bash
bun run android:lint
```

Runs ktlint, Android lint, and detekt. Must have zero errors.

### Unit Tests

```bash
bun run android:test
```

Runs all unit tests via JUnit 5. All tests must pass.

## Release-Required Gates

These gates must pass before any release cut.

### Instrumentation Tests

```bash
cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest
```

Runs Android instrumentation tests on a connected device or emulator.

### Device Verification

```bash
cd apps/android && ./verify-on-device.sh
```

Executes the full device verification protocol. See `DEVICE-TESTING.md` for details.

## Optional/Nightly Gates

These gates are resource-intensive and run on a schedule.

### Screenshot Tests (Paparazzi)

```bash
cd apps/android && node ../../scripts/gradlew.js :app:recordPaparazziDebug
cd apps/android && node ../../scripts/gradlew.js :app:verifyPaparazziDebug
```

Record and verify screenshot golden images. Golden diffs are output to `app/build/test-results/paparazzi/`.

### E2E Tests (Maestro)

```bash
maestro test apps/android/maestro/flows/editor-smoke.yaml
```

Runs Maestro E2E flow. Requires Maestro CLI installed. Install via:

```bash
brew tap mobile-dev-inc/tap
brew install maestro
```

### Macrobenchmarks

```bash
cd apps/android && node ../../scripts/gradlew.js :benchmark:connectedBenchmarkAndroidTest
```

Runs startup and open-note benchmarks. Results output to `benchmark/build/outputs/`.

## Test Types Summary

| Type            | Tool           | When    | CI Step        |
| --------------- | -------------- | ------- | -------------- |
| Lint            | ktlint, detekt | PR      | Required       |
| Unit            | JUnit 5        | PR      | Required       |
| Instrumentation | Espresso       | Release | Manual trigger |
| Screenshot      | Paparazzi      | Nightly | Optional       |
| E2E             | Maestro        | Nightly | Optional       |
| Performance     | Macrobenchmark | Nightly | Optional       |
