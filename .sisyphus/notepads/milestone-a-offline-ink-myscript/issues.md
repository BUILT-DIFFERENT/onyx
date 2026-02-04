# Issues - Milestone A: Offline Ink & MyScript

## Session ses_3d61ead53ffezOq2Om8O5yU1Lo - 2026-02-04T18:19:18.592Z

(No issues encountered yet)

## Session ses_7f2b5c3a0a2eInkCanvas - 2026-02-04

- `./gradlew :app:lint :app:assembleDebug` fails with pre-existing InkCanvas.kt compile errors at lines 66, 119, and 134 (type mismatch, private access, overload mismatch)

## Session ses_ink_api_compat_test - 2026-02-04

- Task 3.2a BLOCKED - requires physical device to run connectedDebugAndroidTest

## Session ses_task-4-10-note-editor-persistence - 2026-02-04

- Manual persistence verification (restart app, confirm strokes load) not run here; requires emulator/device

## Session ses_task-5-2-myscript-engine - 2026-02-04

- `./gradlew :app:lint :app:ktlintCheck :app:detekt` failed due to pre-existing detekt findings in `NoteEditorScreen.kt`, `InkCanvas.kt`, `OnyxNavHost.kt`, `Color.kt`, and `ExampleTest.kt`

## Session current - 2026-02-04

### Remaining Blocked Tasks (Device-Required Verification)

**Cannot proceed without physical device:**

1. **Task 3.2a** - InProgressStrokesView API 30 compatibility test
   - Requires androidTest on physical tablet with API 30+
   - Would verify if InProgressStrokesView works or needs fallback

2. **Task 8.2** - End-to-end flow verification
   - Requires physical device to:
     - Draw strokes with stylus (verify pressure/tilt capture)
     - Write recognizable text for MyScript
     - Verify app restart persistence
     - Check database with adb shell commands

3. **Task 8.3** - PDF workflow verification
   - Requires physical device to:
     - Import PDF via file picker
     - Add ink annotations
     - Navigate pages
     - Verify persistence

4. **Final Checklist Items (lines 5258-5270)**
   - All require runtime verification on tablet:
     - Ink capture with pressure/tilt (stylus hardware needed)
     - Recognition produces text (MyScript needs real strokes)
     - App launches and runs (emulator insufficient for stylus testing)

**Code Complete Status:**
- ✅ All implementation tasks complete (62 feature tasks)
- ✅ Schema audit complete (task 8.4)
- ⚠️ Verification tasks blocked (requires physical Remarkable 2 or equivalent)

**Workaround:**
- Can mark milestone A as "code complete" pending device verification
- APK builds successfully and is ready for device testing
- All Room entities, DAOs, ViewModels, UI screens implemented
