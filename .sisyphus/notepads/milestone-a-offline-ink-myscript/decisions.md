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
