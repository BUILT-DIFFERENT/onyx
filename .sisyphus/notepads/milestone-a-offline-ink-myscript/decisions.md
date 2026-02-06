# Decisions - Milestone A: Offline Ink & MyScript

## Session ses_3d61ead53ffezOq2Om8O5yU1Lo - 2026-02-04T18:19:18.592Z

### Starting Strategy

- Begin with Phase 1: Project Foundation (tasks 1.1-1.10)
- Android project already exists, will verify and enhance as needed
- Following orchestrator pattern: delegate implementation, verify results

## Session ses_98b1f4b7-inkcanvas-fixes - 2026-02-04

### Ink API Usage

- Use `addToStroke(MotionEvent, pointerId, strokeId)` instead of `MutableStrokeInputBatch.add(...)` because add is not accessible in alpha02
- Replace restricted `cancelUnfinishedStrokes()` with per-stroke `cancelStroke(strokeId, event)` to satisfy lint restrictions

## Session ses_3d3ac8bbcffeGzABtYQo6ByP1u - 2026-02-05

### Task 3.2a: Ink API Fallback Decision - PROVISIONAL PASS

**Decision**: Use `InProgressStrokesView` (Jetpack Ink API) - **PROVISIONAL**, pending device verification

**Rationale**:

- Jetpack Ink tested in AOSP on API 30+, expected to work
- Test infrastructure complete (`InkApiCompatTest.kt`)
- Compilation successful, no API availability warnings
- Fallback (`LowLatencyInkView.kt`) available if device test fails

**Status**:

- ✅ Test infrastructure: COMPLETE
- ⚠️ Device execution: BLOCKED (no physical tablet available)
- ✅ Risk assessment: LOW (Jetpack Ink is production-ready)
- ✅ Fallback plan: DOCUMENTED (4-6 hour implementation if needed)

**Next Action (when device available)**:

```bash
./gradlew :app:connectedDebugAndroidTest --tests "*.InkApiCompatTest"
# If PASS: No action, decision confirmed
# If FAIL: Implement LowLatencyInkView.kt fallback
```

**Implementation Status**: Task 3.3 (stroke capture) already uses InProgressStrokesView

See: `.sisyphus/notepads/milestone-a-offline-ink-myscript/task-3.2a-provisional-status.md` for full analysis
