# Task 3.4: DI Migration to Hilt - Progress Notes

## Status: Partially Complete

## Completed
- [x] Hilt plugin added to root build.gradle.kts (version 2.50)
- [x] Hilt plugin added to app/build.gradle.kts
- [x] Hilt dependencies added (hilt-android, hilt-compiler, hilt-navigation-compose)
- [x] Created Hilt modules:
  - DatabaseModule.kt - provides OnyxDatabase and DAOs
  - RepositoryModule.kt - provides NoteRepository and ThumbnailGenerator
  - DeviceIdentityModule.kt - provides DeviceIdentity
  - PdfPasswordStoreModule.kt - provides PdfPasswordStore
  - MyScriptEngineModule.kt - provides MyScriptEngine with async init
- [x] OnyxApplication annotated with @HiltAndroidApp
- [x] OnyxApplication uses @Inject for all AppContainer dependencies
- [x] NoteEditorViewModel migrated to @HiltViewModel with SavedStateHandle
- [x] NoteEditorScreen uses hiltViewModel()
- [x] HomeScreenViewModel migrated to @HiltViewModel
- [x] HomeScreen uses hiltViewModel()
- [x] Removed manual ViewModel factories
- [x] Updated unit tests for NoteEditorViewModel

## Blocking Issues (Pre-existing)
The build fails with errors in NoteEditorUi.kt that are UNRELATED to the Hilt migration:
- Unresolved reference: ValidatingTile
- Unresolved reference: onGoToPage
- Cannot infer type errors

These errors existed before this task and need to be resolved separately.

## Key Files Modified
- apps/android/build.gradle.kts - added Hilt plugin
- apps/android/app/build.gradle.kts - added Hilt plugin and dependencies
- apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt - @HiltAndroidApp
- apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt - @HiltViewModel
- apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt - hiltViewModel()
- apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt - @HiltViewModel, hiltViewModel()
- apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorViewModelTest.kt - updated for Hilt

## Key Files Created
- apps/android/app/src/main/java/com/onyx/android/di/DatabaseModule.kt
- apps/android/app/src/main/java/com/onyx/android/di/RepositoryModule.kt
- apps/android/app/src/main/java/com/onyx/android/di/DeviceIdentityModule.kt
- apps/android/app/src/main/java/com/onyx/android/di/PdfPasswordStoreModule.kt
- apps/android/app/src/main/java/com/onyx/android/di/MyScriptEngineModule.kt

## Architecture Decisions
1. MyScriptEngine is initialized asynchronously in a background thread (same as before)
2. ThumbnailGenerator is created in RepositoryModule with PdfAssetStorage
3. AppContainer interface is preserved for backward compatibility with EntryPoint pattern
4. NoteEditorViewModel uses SavedStateHandle for navigation arguments (noteId, pageId)

## Learnings
1. Hilt ViewModels must be `internal` or `public`, not `private`
2. SavedStateHandle keys must match navigation argument names exactly
3. @ApplicationContext qualifier is needed for Context injection
4. MyScriptEngine async init pattern works well with Singleton scope
5. Pre-existing codebase issues can block verification of DI changes

## Next Steps
1. Fix pre-existing compilation errors in NoteEditorUi.kt
2. Run full test suite once build passes
3. Run lint checks
4. Verify cold start DI graph resolution
5. Remove AppContainer from primary screens (currently using EntryPoint)
