# Task 3.5: SplashScreen Polish - Learnings

## Implementation Summary

### SplashScreen Integration

- Added `androidx.core:core-splashscreen:1.0.1` dependency
- Created splash theme in `themes.xml` with `Theme.SplashScreen` parent
- Added splash screen drawable resources (background and icon)
- Integrated `SplashScreen.installSplashScreen()` in MainActivity before `super.onCreate()`
- Used `setKeepOnScreenCondition` to keep splash visible until app is ready

### Files Created/Modified

1. `apps/android/app/build.gradle.kts` - Added SplashScreen dependency
2. `apps/android/app/src/main/res/values/themes.xml` - Added splash theme
3. `apps/android/app/src/main/res/values/colors.xml` - Added splash colors
4. `apps/android/app/src/main/res/drawable/splash_background.xml` - Splash background
5. `apps/android/app/src/main/res/drawable/splash_icon.xml` - Splash icon
6. `apps/android/app/src/main/AndroidManifest.xml` - Set splash theme on MainActivity
7. `apps/android/app/src/main/java/com/onyx/android/MainActivity.kt` - Integrated SplashScreen API

### Startup Timing Instrumentation

- Added timing logs using `OnyxPerf` tag in:
  - `OnyxApplication.kt` - Application initialization timing
  - `MainActivity.kt` - Activity startup timing with:
    - `onCreate` start time
    - `super.onCreate` duration
    - `setContent` duration
    - Total startup time

### Pre-existing Issues Fixed

During implementation, several pre-existing codebase issues were discovered and fixed:

1. Missing `PerfInstrumentation` class - created stub implementation
2. Missing `TransformGesture` data class - created implementation
3. Missing `onGoToPage` callback in `NoteEditorTopBarState` - added property
4. Missing `navigateToPage` method in `NoteEditorViewModel` - added implementation
5. Type mismatch in `PdfTilesOverlay` - fixed to accept `ValidatingTile`
6. Missing `@InstallIn` annotation on `AppContainerEntryPoint`
7. Various detekt issues (LongParameterList, UnusedParameter, etc.)

## Build Environment Notes

- Java 25 (system default) is incompatible with Android Gradle Plugin
- Must use Java 17 for Android builds
- Gradle daemon caches Java version - need to stop daemon when switching
- Direct Java invocation: `/usr/lib/jvm/java-17-openjdk-amd64/bin/java -jar gradle/wrapper/gradle-wrapper.jar`

## Pre-existing Test Failures

The codebase has 20 pre-existing test failures unrelated to SplashScreen:

- `VariableWidthOutlineTest` - 8 failures (stroke outline tests)
- `PdfTileCacheTest` - 11 failures (tile cache tests)
- `AsyncPdfPipelineTest` - 1 failure (max queue size test)

These failures existed before the SplashScreen implementation and are not caused by the changes made in this task.

## Lint Status

- `bun run android:lint` passes with Java 17
- ktlint passes
- detekt passes
- Android lint passes
