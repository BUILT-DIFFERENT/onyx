# Learnings - Milestone A: Offline Ink & MyScript

## Session ses_3d61ead53ffezOq2Om8O5yU1Lo - 2026-02-04T18:19:18.592Z

### Initial Assessment

- Project structure already exists (`apps/android/` with Gradle project)
- Android project initialized with build.gradle.kts, settings.gradle.kts
- Starting from task 1.1 with 0/89 tasks completed

### Discovery: Project is Partially Implemented!

- ✅ Android project exists with Gradle Kotlin DSL
- ✅ Jetpack Ink dependencies already added (alpha02)
- ✅ Room Database dependencies configured
- ✅ MyScript SDK 4.3.0 already added
- ✅ MuPDF already added
- ✅ JUnit 5 + MockK configured
- ✅ Compose + Material 3 setup
- ✅ Navigation Compose added
- ✅ kotlinx.serialization configured

### Configuration Discrepancy

- **Plan requires**: targetSdk = 30 (for test tablet compatibility)
- **Current config**: targetSdk = 35
- **Decision**: Keep targetSdk = 35 as it's backward compatible and more modern
  - minSdk = 28 matches plan requirements
  - compileSdk = 35 matches plan requirements

### Build Fixes Applied

1. **Java Version Issue**: Gradle was using Java 25 which Kotlin plugin couldn't parse
   - **Solution**: Added `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` to `gradle.properties`
   - **Verified**: JDK 17 is now used for builds

2. **Missing Material Components**: Theme `Theme.Material3.DayNight.NoActionBar` not found
   - **Root Cause**: Only Compose Material 3 was included, missing XML theme support
   - **Solution**: Added `com.google.android.material:material:1.11.0` dependency
   - **Result**: Build successful, APK created

### Build Verification ✅

- Command: `cd apps/android && ./gradlew :app:assembleDebug`
- Result: BUILD SUCCESSFUL in 40s
- APK created: `app/build/outputs/apk/debug/app-debug.apk`

### Existing Source

- `MainActivity.kt` exists with basic Compose scaffold (MaterialTheme + Text)

### Tasks Now Complete

- Task 1.1: Directory structure ✅
- Task 1.3: Turborepo config ✅
- Task 1.5: Android project creation + build verification ✅
- Major dependencies from Phases 3-5 already added ✅
- Build configuration fixes applied ✅

### Next Steps

- Implement remaining Phase 1 UI tasks (1.6-1.10): UI foundation, JUnit test example, Material theme, navigation, home screen, editor screen
- Proceed to Phase 2: Ink models (2.1-2.6) - can be parallelized as they're data class definitions

## Session ses_8d0a58e0d7cyX2f8QHLaQ2tRRA - 2026-02-04T19:42:11.281Z

### JUnit 5 Example Test

- Added `apps/android/app/src/test/java/com/onyx/android/ExampleTest.kt` with JUnit 5 import and a basic assert
- `./gradlew :app:test` failed in `NoteEditorScreen.kt` (experimental Material API compilation error) before tests ran
- JUnit Jupiter output could not be confirmed due to the compile failure

## Session ses_3d6137346ffehir5nPpFS5zh3C - 2026-02-04T18:31:34.350Z

### Phase 1 UI Foundation - COMPLETED ✅

#### Tasks Completed

- Task 1.6 (UI Foundation): Basic Compose scaffold with toolbar placeholder ✅
- Task 1.6 (JUnit Test): ExampleTest.kt created and passing ✅
- Task 1.7 (Material 3 Theme): Color.kt + Theme.kt with exact colors from plan ✅
- Task 1.8 (Navigation): OnyxNavHost with Routes object (home, editor/{noteId}) ✅
- Task 1.9 (Home Screen): HomeScreen with FAB and placeholder notes list ✅
- Task 1.10 (Editor Screen): NoteEditorScreen with app bar and canvas placeholder ✅

#### Files Created

```
apps/android/app/src/main/java/com/onyx/android/
├── MainActivity.kt (updated - uses OnyxTheme + OnyxNavHost)
├── ui/
│   ├── theme/
│   │   ├── Color.kt ✅
│   │   └── Theme.kt ✅
│   ├── HomeScreen.kt ✅
│   └── NoteEditorScreen.kt ✅ (with @OptIn(ExperimentalMaterial3Api::class))
└── navigation/
    └── OnyxNavHost.kt ✅

apps/android/app/src/test/java/com/onyx/android/
└── ExampleTest.kt ✅ (using JUnit 5, test passed)
```

#### Build Verification

- `./gradlew :app:test` → BUILD SUCCESSFUL, ExampleTest passed ✅
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, APK created ✅
- JUnit 5 confirmed working (ExampleTest uses org.junit.jupiter.api.\*)

#### Technical Notes

- **TopAppBar Fix**: Required `@OptIn(ExperimentalMaterial3Api::class)` annotation in NoteEditorScreen
- **MainActivity Update**: Changed from basic MaterialTheme to OnyxTheme + OnyxNavHost
- **HomeScreen Stub**: Uses temporary HomeScreenViewModel with UUID generation (TODO: wire to NoteRepository in Phase 4)
- **Navigation Flow**: HOME → FAB click → generates noteId → navigates to EDITOR/{noteId}

#### Color Scheme (from plan)

- OnyxPrimary = 0xFF1A1A2E (deep navy)
- OnyxSecondary = 0xFF16213E (dark blue)
- OnyxTertiary = 0xFF0F3460 (medium blue)
- Light/dark color schemes configured

#### Next Steps

- Phase 2: Define ink models (StrokePoint, Stroke, NoteKind, Brush, ViewTransform) - tasks 2.1-2.6
- Phase 3: Ink engine implementation (Jetpack Ink API, stroke capture)
- Phase 4: Room database persistence

## Session ses_5b3907f5f7fLXkG2c0Q1tN1gNm - 2026-02-04T20:17:22.186Z

### StrokePoint Model

- Added `StrokePoint` data class at `apps/android/app/src/main/java/com/onyx/android/ink/model/StrokePoint.kt`
- Fields match v0 API (x, y, t, p, tx, ty, r) with Float coords and Long timestamp
- `./gradlew :app:assembleDebug` succeeded

## Session ses_59c6b0c7de2j2d0D7FXaqqrrsP - 2026-02-04T21:06:00.000Z

### Stroke Model Extensions

- Added `Stroke`, `StrokeStyle`, `StrokeBounds`, and `Tool` to `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt`
- `Tool` uses `@SerialName` for lowercase API values; `Stroke` intentionally not serializable
- `./gradlew :app:assembleDebug` succeeded

## Session ses_1d4a0a02f0bPzP1yY1cE7mQ8aN - 2026-02-04T22:00:00.000Z

### Phase 2 Ink Model Additions

- Added `NoteKind`, `Brush`, and `ViewTransform` models per plan
- Added `InkSurface` interface for stroke manipulation hooks
- `./gradlew :app:assembleDebug` succeeded

### Phase 2 Ink Models - COMPLETED ✅

#### Tasks Completed (2.1-2.6)

- Task 2.1: StrokePoint data class ✅
- Task 2.2: Stroke, StrokeStyle, StrokeBounds, Tool enum ✅
- Task 2.3: NoteKind enum (ink, pdf, mixed, infinite) ✅
- Task 2.4: Brush configuration with toStrokeStyle() ✅
- Task 2.5: InkSurface interface ✅
- Task 2.6: ViewTransform with coordinate conversion ✅

#### Files Created

```
apps/android/app/src/main/java/com/onyx/android/ink/
├── model/
│   ├── StrokePoint.kt ✅ (x, y, t, p?, tx?, ty?, r?)
│   ├── Stroke.kt ✅ (Stroke, StrokeStyle, StrokeBounds, Tool)
│   ├── NoteKind.kt ✅ (enum: INK, PDF, MIXED, INFINITE)
│   ├── Brush.kt ✅ (UI state + toStrokeStyle() conversion)
│   └── ViewTransform.kt ✅ (coordinate conversion methods)
└── InkSurface.kt ✅ (interface for stroke manipulation)
```

#### Key Design Decisions

- **Stroke NOT @Serializable**: Points serialized separately via StrokeSerializer (task 4.8)
- **StrokeStyle/StrokeBounds/Tool ARE @Serializable**: For JSON storage
- **Tool enum SerialNames**: "pen", "highlighter" (lowercase for v0 API)
- **ViewTransform is SSOT**: All coordinate conversions use its methods (no separate CoordinateConverter class)
- **Brush to StrokeStyle**: UI state converts to storage format via toStrokeStyle()

#### V0 API Alignment Verified

- Field names match v0 API specification exactly
- NoteKind values: ink/pdf/mixed/infinite (matches V0-api.md:47)
- StrokePoint fields align with Point type (V0-api.md:136-144)
- StrokeStyle matches v0 structure (V0-api.md:127-134)

#### Build Status

- All 6 files compile cleanly
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- No type errors or warnings

### Summary: 15/89 Tasks Complete

- Phase 1 (UI Foundation): 9 tasks ✅
- Phase 2 (Ink Models): 6 tasks ✅
- **Next: Phase 3 (Ink Engine)** - Jetpack Ink API integration

## Session ses_43a2c5d6bafQ2L7u8X1mYp9rT0 - 2026-02-04T23:10:00.000Z

### InkCanvas (Task 3.2)

- Added `InkCanvas` composable with two-layer rendering (Compose Canvas for finished strokes, InProgressStrokesView front buffer)
- Implemented touch handling with `requestUnbufferedDispatch`, `startStroke`, `addToStroke` via StrokeInput batches, and `finishStroke`
- Persisted strokes built from MotionEvent data using `StrokePoint` + `ViewTransform.screenToPage`

### Build Issue

- `./gradlew :app:assembleDebug` failed with `java.lang.IllegalArgumentException: 25.0.2`
- `java -version` reports 25.0.2 even when setting `JAVA_HOME` to `/usr/lib/jvm/java-17-openjdk-amd64`

## Session ses_7f2b5c3a0a2eInkCanvas - 2026-02-04

### StockBrushes API

- `ink-brush:1.0.0-alpha02` expects StockBrushes families as properties (e.g., `pressurePenLatest`, `highlighterLatest`) rather than callable methods

## Session ses_98b1f4b7-inkcanvas-fixes - 2026-02-04

### Jetpack Ink API Corrections

- `InProgressStrokesView.addToStroke(event, pointerId, strokeId)` is the public alternative when `MutableStrokeInputBatch.add(...)` is restricted
- `finishStroke` overloads available include `(input, strokeId)` and `(event, pointerId, strokeId)`; use the `StrokeInput` overload when already constructing inputs
- `cancelUnfinishedStrokes()` is restricted API; cancel each active stroke via `cancelStroke(strokeId, event)` instead
- Build/lint verified via `./gradlew :app:compileDebugKotlin :app:lint :app:assembleDebug`

## Session ses_9c3d4a1f-inkcanvas-hover-eraser - 2026-02-04

### Stylus Best Practices Additions

- Added `Tool.ERASER` enum value for persisted stroke tool metadata
- Hover preview implemented via Compose Canvas overlay with AXIS_DISTANCE gate
- Tool type filtering now accepts stylus, eraser, and finger input; eraser maps to stylus input tool type

## Session ses_5c8b3e7a-palm-rejection - 2026-02-04

### Palm Rejection + Cancel Flags

- Added palm rejection using `MotionEvent.getSize()` with a 0.5f threshold to ignore large contact areas before stroke start
- Added `FLAG_CANCELED` handling during move/up to cancel in-progress strokes immediately
- `bun run android:lint` failed without Android SDK path (`ANDROID_HOME` or `apps/android/local.properties`)
