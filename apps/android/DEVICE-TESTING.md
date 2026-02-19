# Device Verification for Milestone A

**Status**: 8 tasks require physical Android device with active stylus

## Quick Start

When you have a device available:

```bash
cd apps/android
./verify-on-device.sh
```

The script will guide you through all verification steps (~50 minutes).

## Prerequisites

### Hardware
- Android tablet with active stylus (API 30+)
- Recommended devices:
  - Remarkable 2 (~$300)
  - Boox Tab Ultra (~$600)
  - Samsung Galaxy Tab S9 (~$800)
- USB cable for adb connection

### Software
- USB debugging enabled on device
- adb installed and working

### Verify Setup
```bash
adb devices
```
Should show your connected device.

## What the Script Does

### Automated Steps
1. Checks device connection
2. Installs main APK (`app-debug.apk`)
3. Installs test APK (`app-debug-androidTest.apk`)
4. Runs Ink API compatibility test
5. Launches app for manual testing
6. Verifies persistence after force-stop
7. Checks database for recognition text
8. Pushes test PDF to device

### Manual Steps (Guided by Script)
1. Create note and draw with stylus
2. Write "hello world" in cursive
3. Search for "hello"
4. Verify note appears
5. Import PDF and annotate
6. Verify annotations persist

## Expected Results

### If All Tests Pass
- Ink API test: PASSED (no fallback needed)
- End-to-end workflow: works correctly
- PDF workflow: annotations persist
- Recognition: searchable text in database

**Action**: Update plan file, mark 8 tasks complete

### If Ink API Test Fails
- Implement `LowLatencyInkView` fallback
- See plan file line 2197-2201 for implementation
- Estimated effort: 4-6 hours

### If Recognition Fails
- Check MyScript ATK license
- Debug with MyScript SDK logs
- Estimated effort: 2-4 hours

## Manual Verification (Without Script)

If you prefer manual testing:

### 1. Install APKs
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

### 2. Run Ink API Test
```bash
./gradlew :app:connectedDebugAndroidTest --tests "*.InkApiCompatTest"
```

### 3. Manual Testing
Launch app:
```bash
adb shell am start -n com.onyx.android/.MainActivity
```

Test workflow:
- Create note, draw strokes, search, verify

### 4. Check Persistence
```bash
adb shell am force-stop com.onyx.android
adb shell am start -n com.onyx.android/.MainActivity
```

Verify strokes still visible.

### 5. Check Database
```bash
adb shell "run-as com.onyx.android sqlite3 /data/data/com.onyx.android/databases/onyx_notes.db 'SELECT recognizedText FROM recognition_index;'"
```

Should show recognized text.

## Troubleshooting

### Device Not Detected
```bash
adb kill-server
adb start-server
adb devices
```

### APK Install Fails
```bash
adb uninstall com.onyx.android
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Test Fails
Check logcat:
```bash
adb logcat | grep -i "onyx\|ink\|myscript"
```

### Recognition Not Working
- Verify MyScript ATK certificate is valid
- Check handwriting is clear cursive
- Wait ~2 seconds after pen-up for recognition

## Files to Update After Verification

When all tests pass, update:

**Plan file**: `.sisyphus/plans/milestone-a-offline-ink-myscript.md`

Mark these as `[x]`:
- Line 814: App launches on physical tablet
- Line 816: Can draw strokes with stylus (low latency)
- Line 820: MyScript produces recognized text
- Line 2102: 3.2a Ink API Fallback Decision (REQUIRED)
- Line 5128: 8.2 End-to-end flow verification
- Line 5150: 8.3 PDF workflow verification
- Line 5262: Ink capture with pressure/tilt
- Line 5266: Recognition produces searchable text

**Boulder file**: `.sisyphus/boulder.json`

Update:
```json
{
  "completion": "89/89 (100%)",
  "tasks_complete": 89,
  "tasks_blocked": 0,
  "runtime_verification": "100%"
}
```

## Time Estimates

- Setup: 10 minutes
- Automated tests: 5 minutes
- Manual workflow testing: 15 minutes
- PDF testing: 10 minutes
- Verification: 10 minutes

**Total: ~50 minutes**

## Contact

If you encounter issues during device testing, check:
- `.sisyphus/notepads/milestone-a-offline-ink-myscript/` for detailed documentation
- `docs/device-blocker.md` for blocker explanation
- Test logs in `apps/android/build/reports/androidTests/`
