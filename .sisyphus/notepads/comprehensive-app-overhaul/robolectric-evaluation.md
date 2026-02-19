# Robolectric Evaluation for Onyx Android

**Date**: 2026-02-19
**Evaluator**: Sisyphus-Junior
**Task ID**: G-H.5-C

## Research Summary

### What Robolectric provides (official docs)

- Runs Android unit tests on the JVM by shadowing Android framework APIs (no emulator/device required).
- Supports Activity/service/view lifecycle testing through Robolectric controllers and shadows.
- Typical setup is `testImplementation("org.robolectric:robolectric:<version>")` plus AndroidX test artifacts for Android component tests.
- Primary value proposition is fast Android-framework behavior tests compared with instrumentation tests.

Context7 sources reviewed:

- `/robolectric/robolectric` README dependency/setup snippets and lifecycle examples.

### Current production usage snapshot (GitHub search)

`grep_app` examples show active usage in production Android repos, typically for framework-adjacent unit tests:

- `Kotlin/kotlinx.coroutines` (`ui/kotlinx-coroutines-android/build.gradle.kts`) uses Robolectric + `androidx.test:core`.
- `anthonycr/Lightning-Browser` and `Helium314/HeliBoard` include Robolectric in `testImplementation` for JVM-side Android tests.
- `tutao/tutanota` and `d4rken-org/sdmaid-se` include Robolectric in app modules for non-emulator Android behavior tests.

Common pattern: add Robolectric only when tests execute real Android framework behavior (Activities, resources, framework components), not for pure domain logic.

## Current Test Analysis (Onyx)

**Unit test files examined**: 38 Kotlin files under `apps/android/app/src/test`.

### Current stack observed in codebase

- JUnit 5 (`org.junit.jupiter`), MockK, coroutines-test.
- No existing Robolectric usage.
- Heavy emphasis on pure logic tests (geometry/math, repository mapping, serializer behavior, PDF scheduler logic).

### Tests that might theoretically benefit from Robolectric

These use Android types, but currently mock or use data-only types and do not require framework execution:

- `apps/android/app/src/test/java/com/onyx/android/data/sync/LamportClockTest.kt` (`Context`, `SharedPreferences` mocked).
- `apps/android/app/src/test/java/com/onyx/android/device/DeviceIdentityTest.kt` (`Context`, `SharedPreferences` mocked).
- `apps/android/app/src/test/java/com/onyx/android/pdf/PdfTileCacheTest.kt` and `apps/android/app/src/test/java/com/onyx/android/pdf/AsyncPdfPipelineTest.kt` (`Bitmap` mocked).
- `apps/android/app/src/test/java/com/onyx/android/pdf/TextSelectionModelTest.kt` (`PointF` data use only).
- `apps/android/app/src/test/java/com/onyx/android/data/OnyxDatabaseTest.kt` (`SupportSQLiteDatabase` mocked).

### Tests that do not need Robolectric

Most of the suite:

- Ink geometry/render math (`LassoTransformTest`, `StrokeSplitterTest`, `VariableWidthOutlineTest`, etc.).
- View transform math and editor utility logic.
- Repository/serialization mapping tests.
- PDF helper/math tests that are JVM-safe with mocks/fakes.

### Net fit assessment

- Current suite is already designed to avoid direct Android framework execution.
- Existing Android-specific behavior is either mocked or covered in `androidTest` instrumentation scope.
- Introducing Robolectric now would add maintenance surface with low immediate test coverage gain.

## Decision Matrix

### Pros of adding Robolectric now

1. Enables direct JVM tests for Android framework behavior (Activity/lifecycle/resource behavior) without emulator.
2. Could reduce mocking for specific framework-heavy classes in future tests.
3. Creates an intermediate layer between pure unit tests and slower instrumentation tests.

### Cons / tradeoffs for current codebase

1. Additional dependency and runner/config complexity in a suite that is currently mostly framework-agnostic.
2. Limited immediate ROI: current 38 unit tests rarely need real Android runtime semantics.
3. Potential maintenance overhead with AGP/SDK/Robolectric compatibility over time.
4. Current verification policy already prefers `android:lint` due known unit test compile drift; Robolectric does not resolve those unrelated failures.

## Recommendation

**Decision**: SKIP (for now)

**Rationale**:

Onyx's current unit tests are predominantly pure logic + mocked Android interfaces and do not have a strong unmet need for framework-level JVM simulation. Adding Robolectric now would increase build/test maintenance complexity with minimal immediate coverage gain. Re-evaluate when we add targeted Activity/Compose host/resource behavior tests that cannot be expressed cleanly with current mocks or are too costly as instrumentation tests.

## If/when to revisit

Adopt Robolectric when at least one of these becomes true:

1. Multiple new tests require real lifecycle/resource/system-service behavior in JVM unit scope.
2. Instrumentation-only coverage causes unacceptable feedback latency for Android UI/system integration behaviors.
3. A concrete flaky/mocked area is identified where Robolectric materially improves confidence.
