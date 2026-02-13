# Android Remediation PR2 (UX Gaps)

Date: 2026-02-12  
Source review: `C:/onyx/.sisyphus/notepads/android-architecture-review.md`

## Goal
Execute PR2 from the Android remediation plan so UX gaps can be compared directly against the review findings.

## Review Findings Mapped To This Phase
- Title editing missing (`android-architecture-review.md`: 62, 65, 265, 312)
- Note deletion from Home missing (`android-architecture-review.md`: 68, 70, 266)
- Page counter missing (`android-architecture-review.md`: 170, 173, 325)
- Dead top-bar controls (`android-architecture-review.md`: 178)
- Read-only mode blocks all input (`android-architecture-review.md`: 195, 198, 331)
- Eraser mode UI misleading (`android-architecture-review.md`: 190, 191)

## PR2 Scope Checklist
- [x] Add editable note title in editor top bar and persist via repository.
- [x] Add visible page counter (`current/total`) with accessibility semantics.
- [x] Replace dead Grid/Search/Inbox controls with one working overflow action.
- [x] Add long-press delete action for notes in Home screen and wire to repository delete.
- [x] Keep pan/zoom available in read-only mode while blocking stylus edit actions.
- [x] Simplify eraser settings to the currently implemented stroke eraser behavior.

## Implementation Notes
- Title edit wiring:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt`
- Home delete flow:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt`
- Read-only interaction policy:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`

## Validation Added For This Phase
- Repository unit test for title updates:
  - `C:/onyx/apps/android/app/src/test/java/com/onyx/android/data/repository/NoteRepositoryTest.kt`
- Compose/android tests:
  - Title edit and page counter:
    - `C:/onyx/apps/android/app/src/androidTest/java/com/onyx/android/ui/NoteEditorTopBarTest.kt`
  - Home note delete action visibility + callback:
    - `C:/onyx/apps/android/app/src/androidTest/java/com/onyx/android/ui/HomeNotesListTest.kt`
  - Read-only pan/zoom behavior check:
    - `C:/onyx/apps/android/app/src/androidTest/java/com/onyx/android/ui/NoteEditorReadOnlyModeTest.kt`
  - Ink touch routing read-only stylus blocking:
    - `C:/onyx/apps/android/app/src/androidTest/java/com/onyx/android/ink/ui/InkCanvasTouchRoutingTest.kt`

## PR2 Closeout Delta
- Overflow menu no longer contains placeholder text. It now exposes implemented actions:
  - Rename note (opens inline title editing).
  - Switch mode (view/edit toggle) using existing screen callback.
- Added PR2 dead-control guardrails in UI tests:
  - `C:/onyx/apps/android/app/src/androidTest/java/com/onyx/android/ui/NoteEditorTopBarTest.kt`
    - Verifies overflow actions are functional.
    - Verifies legacy Grid/Search/Inbox placeholder copy is absent.
    - Verifies unavailable top-bar actions are disabled.

## Previous-Phase Completion Audit
- PR1 architecture/data work remains intact:
  - ViewModel/state/controller extraction is preserved.
  - Protobuf stroke point serialization remains active.
  - App container dependency access remains active.
  - Room destructive fallback remains removed.
- Added remaining PR1-style validation coverage:
  - `C:/onyx/apps/android/app/src/test/java/com/onyx/android/data/OnyxDatabaseTest.kt`
    - Migration executes without schema mutation SQL.
  - `C:/onyx/apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorViewModelTest.kt`
    - Error state emission on load-note failure.
    - Error state emission on create-page failure.
    - Stroke write queue retry failure reporting.

## Final Status For This Remediation Track
- [x] PR1 (Architecture + Data Layer Hardening) complete.
- [x] PR2 (UX Gaps: title/delete/page counter/read-only/eraser/no-dead-controls) complete.
