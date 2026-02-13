# PdfiumAndroid Spike Report

**Date:** 2026-02-13
**Plan:** `milestone-canvas-rendering-overhaul.md`
**Phase:** P0.0–P0.4

---

## Section 0: P0.0 Pre-Gate Results

### 0.1 Gradle Coordinate Resolution

| Candidate | Result | Notes |
|---|---|---|
| `com.github.Zoltaneusz:PdfiumAndroid:v1.10.0` | Resolves, but incompatible | AAR manifest minSdk = 30 |
| `com.github.Zoltaneusz:PdfiumAndroid:b68e47459ab90501fd377aa6456618bc87f06d3c` | ✅ Working | Commit from `min_SDK_28` branch, minSdk = 28 |

**Locked coordinate in app:**

```kotlin
implementation("com.github.Zoltaneusz:PdfiumAndroid:b68e47459ab90501fd377aa6456618bc87f06d3c")
```

### 0.2 Repository Accessibility

- Repo URL reachable: `https://github.com/Zoltaneusz/PdfiumAndroid.git`
- JitPack repo configured in `apps/android/settings.gradle.kts`
- Dependency resolution verified with:

```bash
cd apps/android
node ../../scripts/gradlew.js :app:dependencies
```

### 0.3 Build Environment Viability

- Initial failure was Java 25 parsing in Gradle/Kotlin script evaluation.
- Fixed by adding Linux/macOS JDK fallback in `scripts/gradlew.js` (prefers JDK 17).
- After fix, Gradle dependency/build/lint commands succeed without manually exporting `JAVA_HOME`.

### 0.4 minSdk Compatibility (P0.2.5 gate input)

- App minSdk: 28
- Locked Pdfium coordinate minSdk: 28 (manifest extracted from AAR)
- **Status:** ✅ Compatible, no app minSdk bump needed.

---

## Section 1: P0.1 API Parity Spike

### 1.1 Spike Project Status

- Standalone spike project exists at: `apps/android/spikes/pdfium-spike/`
- Command verified:

```bash
cd apps/android
node ../../scripts/gradlew.js -p C:/onyx/apps/android/spikes/pdfium-spike -c C:/onyx/apps/android/spikes/pdfium-spike/settings.gradle.kts test
```

- Result: ✅ **BUILD SUCCESSFUL** (47 tasks, tests pass)
- Evidence artifacts:
  - `apps/android/spikes/pdfium-spike/spike/build/test-results/testDebugUnitTest/`
  - `apps/android/spikes/pdfium-spike/spike/src/test/kotlin/com/onyx/spike/pdfium/PdfiumApiSurfaceTest.kt`

### 1.2 API Surface Findings

| Requirement | Finding |
|---|---|
| `newDocument()` / `closeDocument()` | ✅ Present |
| `openPage()` | ✅ Present |
| explicit `closePage()` public API | ⚠️ Not exposed publicly (native close methods exist) |
| `renderPageBitmap()` | ✅ Present (`int startX, int startY, int width, int height`) |
| `getTableOfContents()` | ✅ Present |
| `getPageText()` | ❌ Not present in Java API |
| char geometry (`getCharBox`, `getCharOrigin`) | ❌ Not present in Java API |
| `getPageRotation()` Java API | ❌ Not present |

### 1.3 Native Symbol Findings

From `libjniPdfium.so` / `libpdfium.so` symbol scan:

- `FPDFText_GetCharBox`, `FPDFText_GetCharOrigin`, `FPDFText_CountChars` found in native libs.
- `FPDFPage_GetRotation` present in native lib.
- Indicates Java wrapper omits text/rotation APIs that exist in native pdfium.

### 1.4 ABI / Packaging

AAR includes all required ABIs:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

App integration needed one packaging fix due duplicate `libc++_shared.so` (MyScript + Pdfium).

---

## Section 2: P0.2 Go/No-Go Decision

### Verdict

**GO with condition:** `JNI_BRIDGE_REQUIRED`

### Rationale

- Rendering/navigation/document lifecycle APIs are sufficient for Phase 2 foundation.
- Text selection parity cannot be achieved via current Java wrapper alone.
- Native primitives required for per-character geometry exist in the underlying pdfium libs.

### Binary Gate Result

- **Result:** `JNI_BRIDGE_REQUIRED`
- **Impact:** Add JNI bridge task in Phase 2 for text geometry + rotation APIs.

---

## Section 2.5: Dependency Viability Check

- Option A (preferred) achieved: consume minSdk-compatible commit from locked fork.
- Option B (raise app minSdk to 30) **not required**.
- Option C (vendor source) **not required currently**.

---

## Section 3: P0.3 Exit Artifacts

### 3.1 Canonical PDF Corpus

Created:

- `.sisyphus/notepads/pdf-test-corpus.md`

Contains fixed 5-file corpus for P0/P2/P5 (Latin, rotated, RTL, ligatures, image-heavy no-text proxy).

### 3.2 Runnable Snippet: Coordinate Offset Contract Probe

```kotlin
val pageIndex = 0
pdfiumCore.openPage(doc, pageIndex)
val tileBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

// Probe offset behavior (negative/positive) in device pixels.
pdfiumCore.renderPageBitmap(
    doc,
    tileBitmap,
    pageIndex,
    startX = -256,
    startY = -256,
    width = 512,
    height = 512,
    renderAnnot = true,
)
```

**Status:** snippet compiles against the API surface; device/runtime execution still required to confirm negative offset semantics.

### 3.3 Runnable Snippet: Text Geometry JNI Bridge Shape

```kotlin
external fun nativeTextCountChars(docPtr: Long, pagePtr: Long): Int
external fun nativeTextGetCharBox(docPtr: Long, pagePtr: Long, charIndex: Int): FloatArray
```

**Status:** required because Java API lacks `getPageText`/`getCharBox`/`getCharOrigin`.

### 3.4 Remaining P0.3 Runtime Gaps

Device/runtime validation still required for:

1. Negative `startX/startY` behavior in `renderPageBitmap`.
2. Thread-safety under concurrent page renders.
3. Rotation-normalized coordinate mapping for selection.
4. Tile render performance baseline on target hardware.
5. RTL/ligature edge-case selection behavior with real documents.

---

## Section 4: P0.4 App Integration Smoke

### 4.1 Build-Level Smoke (Completed)

- `bun run android:build` ✅ passes with Pdfium dependency integrated.
- `bun run android:lint` ✅ passes.

### 4.2 Integration Fixes Applied

- Added JitPack repo in `apps/android/settings.gradle.kts`.
- Added Pdfium dependency in `apps/android/app/build.gradle.kts`.
- Resolved native merge conflict by adding:

```kotlin
packaging {
  jniLibs {
    pickFirsts += "**/libc++_shared.so"
  }
}
```

### 4.3 On-Device Smoke (Completed)

Connected Android device used: `SM-X610 (Android 16)`.

- Executed connected instrumentation suite:

```bash
cd apps/android
node ../../scripts/gradlew.js :app:connectedDebugAndroidTest
```

- Added and validated integration smoke coverage in:
  - `apps/android/app/src/androidTest/java/com/onyx/android/pdf/PdfiumIntegrationSmokeTest.kt`
  - Test flow: create PDF -> import through `PdfAssetStorage` -> read metadata through `PdfiumDocumentInfoReader` -> render page at 1x and 2x through `PdfiumRenderer`.
- Result: ✅ render path succeeds on-device with no crash and valid bitmap output.

---

## Files Updated During P0 Work

- `apps/android/settings.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `scripts/gradlew.js`
- `apps/android/spikes/pdfium-spike/spike/src/test/kotlin/com/onyx/spike/pdfium/PdfiumApiSurfaceTest.kt`
- `apps/android/app/src/androidTest/java/com/onyx/android/pdf/PdfiumIntegrationSmokeTest.kt`
- `.sisyphus/notepads/pdf-test-corpus.md`
- `.sisyphus/notepads/pdfium-spike-report.md`
