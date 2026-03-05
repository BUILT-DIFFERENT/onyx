# Device Blocker

Physical-device verification remains a separate gate from compile/lint checks.

## Current State

- Android lint/compile gates can pass in CI/local without a connected tablet.
- Full stylus/runtime verification still requires physical-device runs (`connectedDebugAndroidTest` plus guided manual checks).

## Canonical Tracking

Use these files as source of truth for device-specific status/evidence:

- `apps/android/DEVICE-TESTING.md`
- `.sisyphus/notepads/device-validation/TASK-6.3-STATUS.md`

## Commands

```bash
cd apps/android
./gradlew :app:connectedDebugAndroidTest
./verify-on-device.sh
```
