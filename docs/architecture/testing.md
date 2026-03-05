# Testing Guide

This file documents the current verification policy for this repository.

## Primary Gates

Run from repo root unless noted.

```bash
bun run lint
bun run typecheck
bun run android:lint
```

Notes:

- Root `lint`/`lint:all` run formatting first.
- `android:lint` runs Android format (`ktlintFormat`) before lint checks.

## Android Tests

`bun run android:test` exists but currently has known pre-existing test-source drift in parts of `apps/android/app/src/test`.

Current practical gate for day-to-day Android verification is:

```bash
bun run android:lint
```

When actively fixing Android test sources, use:

```bash
cd apps/android
./gradlew :app:testDebugUnitTest
```

## Web/TS Tests

```bash
bun run test
```

Current package behavior:

- packages without tests intentionally use non-failing placeholder scripts.
- Vitest packages use `--passWithNoTests` to avoid false negatives in package-local runs.

## Device Validation

Physical device validation details and outstanding blockers:

- `apps/android/DEVICE-TESTING.md`
- `docs/device-blocker.md`
