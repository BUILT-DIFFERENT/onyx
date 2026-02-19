# CI Topology

This document describes the CI pipeline structure for the Onyx project.

## Workflow Files

| File                                            | Purpose                       | Trigger           |
| ----------------------------------------------- | ----------------------------- | ----------------- |
| `.github/workflows/ci.yml`                      | Main CI pipeline              | Push to main, PRs |
| `.github/workflows/android-instrumentation.yml` | Android device/emulator tests | Manual, scheduled |

## Main CI Pipeline (`ci.yml`)

### Stages

1. **Checkout & Setup**
   - Checkout code
   - Setup Bun runtime
   - Install dependencies

2. **Web/Backend Quality Gates**
   - Lint (oxlint, eslint)
   - Typecheck (TypeScript)
   - Test (Vitest)
   - Build (Turborepo)
   - E2E (Playwright)

3. **Android Quality Gates**
   - Lint: `bun run android:lint`
   - Test: `bun run android:test`

### Required Status Checks

The following must pass for PR merge:

- Lint
- Typecheck
- Test
- Android Lint
- Android Test

## Android Instrumentation Pipeline (`android-instrumentation.yml`)

### Purpose

Runs heavier Android tests that require emulator or device.

### Triggers

- Manual dispatch (`workflow_dispatch`)
- Scheduled (nightly at 2 AM UTC)

### Jobs

1. **Instrumentation Tests**
   - Runs `connectedDebugAndroidTest`
   - Uses Android emulator

2. **Screenshot Tests**
   - Records Paparazzi golden images
   - Verifies against existing goldens
   - Uploads diff artifacts on failure

3. **Macrobenchmarks**
   - Runs startup benchmarks
   - Runs open-note benchmarks
   - Uploads performance reports

### Artifacts

On failure, the following artifacts are uploaded:

- `paparazzi-diff/`: Screenshot diff images
- `test-results/`: JUnit XML reports
- `benchmark-results/`: Performance metrics

## Branch Protection Rules

Main branch requires:

- PR required gates passing
- At least 1 approval
- No stale reviews

## Local Reproduction

All CI steps can be run locally:

```bash
# Web/Backend
bun run lint
bun run typecheck
bun run test
bun run build
bun run e2e

# Android
bun run android:lint
bun run android:test
cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest
```
