# Android PR1 Implementation Plan

Date: 2026-02-12
Scope: Architecture and data-layer hardening for editor + stroke persistence.

## Goal

Implement PR #1 from the approved plan:

1. Extract editor ViewModel/controller/state types from `NoteEditorScreen.kt`.
2. Add explicit editor error state flow from ViewModel to UI.
3. Replace destructive Room fallback with explicit migration wiring.
4. Switch stroke point payload serialization from JSON bytes to protobuf bytes.
5. Introduce app-container abstraction for dependency access from UI screens.
6. Record dev-phase compatibility policy in `AGENTS.md`.

## Planned File Changes

## New files

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt`
  - Move `NoteEditorViewModel` and `NoteEditorViewModelFactory` out of `NoteEditorScreen.kt`.
  - Add `_errorMessage: MutableStateFlow<String?>`, `errorMessage`, and `clearError()`.
  - Add guarded error handling for:
    - `loadNote()`
    - `createNewPage()`
    - queued stroke writes (`enqueueStrokeWrite` and queue worker)
  - Add single retry for queued stroke persistence task failures.

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`
  - Move editor UI/state models:
    - `TextSelection`
    - `NoteEditorTopBarState`
    - `NoteEditorToolbarState`
    - `NoteEditorContentState`
    - `NoteEditorPageState`
    - `NoteEditorPdfState`
    - `NoteEditorUiState`
    - `BrushState`
    - `StrokeCallbacks`

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUndoController.kt`
  - Move `UndoController`.

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/AppContainer.kt`
  - Add `AppContainer` interface.
  - Add `Context.requireAppContainer()` helper.

## Updated files

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
  - Remove moved classes/data classes.
  - Keep composable screen orchestration only.
  - Use `requireAppContainer()` instead of raw app cast.
  - Observe and render ViewModel error state via dismissible dialog.

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt`
  - Use `requireAppContainer()` instead of raw app cast.

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt`
  - Implement `AppContainer`.
  - Use `OnyxDatabase.build(...)` for DB creation.
  - Remove direct destructive migration builder usage.

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt`
  - Remove `fallbackToDestructiveMigration()`.
  - Bump DB version from `1` to `2`.
  - Add explicit `MIGRATION_1_2` and register via `addMigrations`.
  - Remove unused Base64 converters.

- `C:/onyx/apps/android/app/src/main/java/com/onyx/android/data/serialization/StrokeSerializer.kt`
  - Use protobuf encoding/decoding for point arrays.
  - Keep JSON for style/bounds.
  - Add explicit protobuf field numbering on serializable point model.

- `C:/onyx/apps/android/app/build.gradle.kts`
  - Add protobuf serialization dependency:
    - `org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.2`

- `C:/onyx/apps/android/app/src/test/java/com/onyx/android/data/serialization/StrokeSerializerTest.kt`
  - Replace JSON-point-format assertions with protobuf-oriented tests.
  - Keep style/bounds JSON tests.

- `C:/onyx/apps/android/app/src/test/java/com/onyx/android/data/repository/NoteRepositoryTest.kt`
  - Keep repository tests passing with protobuf serializer.
  - Add/adjust test(s) to reflect no behavior regression for save/load stroke.

- `C:/onyx/AGENTS.md`
  - Add explicit project note:
    - Dev phase allows breaking local DB compatibility with older app versions.

## Validation Checklist

Run from `C:/onyx/apps/android`:

1. `node ../../scripts/gradlew.js :app:lint :app:ktlintCheck :app:detekt`
2. `node ../../scripts/gradlew.js :app:test`

Run from `C:/onyx`:

3. `bun run android:lint` (workspace/turbo path check)

Expected result:

- All checks pass.
- No fallback destructive migration remains in source.
- Stroke point serializer round-trips through protobuf.
- Extracted editor files compile and tests pass.

## Comparison Notes (for later review)

- This document is the PR1 intended change contract.
- Any delta between this plan and final code should be explained in PR notes.
