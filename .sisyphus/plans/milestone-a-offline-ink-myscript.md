# Milestone A: Android Offline Editor (Core MVP)

## Context

### Original Request

Build the foundational native Android tablet app for an offline-first note-taking application with low-latency ink using Android Ink API, MuPDF PDF viewing/markup, and on-device MyScript recognition.

### Refinement Summary

This plan has been refined into micro-tasks (~35 tasks) for a developer new to Android. Advanced features (infinite canvas, segment eraser, batch recognition) are in Plan Av2.

### Key Decisions

- **Target API**: 30 (Android 11), Compile SDK 35
- **UI Stack**: Jetpack Compose only
- **Ink API**: Android Ink API (Jetpack Ink) for low-latency drawing
  - **SDK Compatibility Note**: Jetpack Ink tests show `@SdkSuppress(minSdkVersion = 35)` but the library itself may work on lower APIs
  - **Decision**: Target API 30 but verify InProgressStrokesView works on test device
  - **Fallback Strategy**: If InProgressStrokesView fails on API 30, implement `CanvasFrontBufferedRenderer` directly (same low-latency approach, lower-level API)
  - **Verification**: Task 3.2 includes runtime compatibility check
- **Brush model v1**: Simple round brush only (pen + highlighter tools)
- **Stroke storage**: Raw points + style (not smoothed curves, allows future smoothing evolution)
- **Multi-page notes**: Yes (notebook-style)
- **Page canvas**: Fixed size (not infinite)
- **Eraser**: Stroke eraser only (segment eraser in Av2)
- **Recognition**: Realtime only, search index only (no text overlay)
- **PDF**: Unencrypted only, soft size warning
- **Verification**: Manual on tablet (primary), automated tests (secondary)

### v0 API Contract Alignment

Data models align with v0 API (`V0-api.md`) for seamless sync in Milestone C:

- **IDs**: strings (UUID format) - `V0-api.md:31-41`
- **Timestamps**: Unix ms (Long in Kotlin) - `V0-api.md:44`
- **NoteKind**: `"ink" | "pdf" | "mixed" | "infinite"` - `V0-api.md:47`
  - Note: Plan A uses "ink" for standard canvas pages (not "page")
- **PageGeometry**: `V0-api.md:49-51` - fixed size or infinite tile
- **StrokeStyle**: `V0-api.md:127-134` - matches Brush model
- **Point fields**: x, y, t, p?, tx?, ty?, r? - `V0-api.md:136-144`
  - `tx/ty` = tilt, `r` = rotation (not velocity)
- **Bounds**: Required for StrokeAdd ops - `V0-api.md:153`
- **Serialization**: JSON format for v0 (Protocol Buffers in future)

### Key Constants & Defaults

**Page Geometry Defaults (Fixed Pages):**
| Property | Ink Page | PDF Page |
|----------|----------|----------|
| width | 612 pt (8.5 inches) | From PDF mediaBox |
| height | 792 pt (11 inches) | From PDF mediaBox |
| unit | "pt" (points, 72pt = 1 inch) | "pt" |
| geometryKind | "fixed" | "fixed" |
| origin | (0, 0) = top-left corner | (0, 0) = top-left corner |
| coordinate direction | x→right, y→down | x→right, y→down |

**Pixel Mapping (Screen ↔ Page Coordinates):**

- Base DPI: 72 (1pt = 1px at 1x zoom)
- At zoom level Z: 1pt = Z pixels
- Transform: `screenPx = pageCoord * zoom + panOffset`
- Inverse: `pageCoord = (screenPx - panOffset) / zoom`

**Entity Field Semantics:**
| Field | Source | Lifecycle |
|-------|--------|-----------|
| `noteId` | `java.util.UUID.randomUUID().toString()` | Generated on note creation |
| `pageId` | `java.util.UUID.randomUUID().toString()` | Generated on page creation |
| `strokeId` | `java.util.UUID.randomUUID().toString()` | Generated on stroke completion |
| `ownerUserId` | Device ID (from DeviceIdentity) | Set to device UUID until user auth in Plan C |
| `title` | Empty string `""` initially | User-editable; auto-set from first line of recognition if empty (future) |
| `createdAt` | `System.currentTimeMillis()` | Set once on creation |
| `updatedAt` | `System.currentTimeMillis()` | Updated on any modification |
| `createdLamport` | `0L` for Plan A | Incremented per-device in Plan C |

### App Composition & Dependency Injection (DI) Plan

**Singleton Lifetimes & Ownership:**

| Component        | Lifetime    | Owner                | Initialization                                   |
| ---------------- | ----------- | -------------------- | ------------------------------------------------ |
| `OnyxDatabase`   | Application | `OnyxApplication.kt` | Created in `onCreate()`, stored as property      |
| `NoteRepository` | Application | `OnyxApplication.kt` | Created after database, owns ALL data operations |
| `DeviceIdentity` | Application | `OnyxApplication.kt` | Created in `onCreate()`, uses SharedPreferences  |
| `MyScriptEngine` | Application | `OnyxApplication.kt` | Created in `onCreate()`, initialized lazily      |

**Repository Design Decision:**

- **Single `NoteRepository`** handles notes, pages, strokes, and recognition
- This matches task 4.9 definition where `NoteRepository` takes all DAOs
- Simpler for Plan A; can split into domain-specific repositories in future if needed

**Application Class:**

```kotlin
class OnyxApplication : Application() {
  // Singletons - initialized in onCreate()
  lateinit var database: OnyxDatabase
  lateinit var noteRepository: NoteRepository
  lateinit var deviceIdentity: DeviceIdentity
  lateinit var myScriptEngine: MyScriptEngine

  // MyScript page managers are per-ViewModel (not singleton)
  // Each NoteEditorViewModel creates its own MyScriptPageManager

  override fun onCreate() {
    super.onCreate()

    // Database (Room - singleton)
    database = Room.databaseBuilder(
      applicationContext, OnyxDatabase::class.java, OnyxDatabase.DATABASE_NAME
    ).build()

    // Device identity (SharedPreferences backed)
    deviceIdentity = DeviceIdentity(applicationContext)

    // Repository (owns ALL data operations - notes, pages, strokes, recognition)
    // NOTE: StrokeSerializer is an `object` (singleton), not a class - access via object reference
    noteRepository = NoteRepository(
      noteDao = database.noteDao(),
      pageDao = database.pageDao(),
      strokeDao = database.strokeDao(),
      recognitionDao = database.recognitionDao(),
      deviceIdentity = deviceIdentity,
      strokeSerializer = StrokeSerializer  // Object reference, not instantiation
    )

    // MyScript engine (singleton, lazily initialized on first use)
    myScriptEngine = MyScriptEngine(applicationContext)
  }
}
```

**Target File**: `apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt`

**AndroidManifest.xml Registration (REQUIRED):**

The `OnyxApplication` class MUST be registered in `AndroidManifest.xml` to be used:

```xml
<!-- apps/android/app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".OnyxApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.OnyxAndroid">

        <!-- Activities, etc. -->

    </application>
</manifest>
```

**Key Point**: The `android:name=".OnyxApplication"` attribute tells Android to use our custom Application class instead of the default. Without this, the singletons won't be initialized and `LocalContext.current.applicationContext as OnyxApplication` will fail with ClassCastException.

**ViewModel Factory Pattern:**

```kotlin
// ViewModels receive dependencies via factory
class NoteEditorViewModel(
  private val noteRepository: NoteRepository,
  private val myScriptPageManager: MyScriptPageManager
  // NOTE: No separate RecognitionRepository - NoteRepository handles all data operations
) : ViewModel() {

  // Current page state
  private var currentPageId: String? = null

  init {
    // Wire recognition updates to repository
    // MyScriptPageManager emits recognition via callback
    myScriptPageManager.onRecognitionUpdated = { pageId, text ->
      viewModelScope.launch {
        noteRepository.updateRecognition(pageId, text, "myscript-4.3")
      }
    }
  }

  // === Page Lifecycle ===
  fun navigateToPage(pageId: String) {
    // Close previous page's MyScript context
    if (currentPageId != null && currentPageId != pageId) {
      myScriptPageManager.closeCurrentPage()
    }

    currentPageId = pageId
    myScriptPageManager.onPageEnter(pageId)

    // Load existing strokes for this page
    viewModelScope.launch {
      val strokes = noteRepository.getStrokesForPage(pageId)
      // ... update UI state with strokes

      // Re-feed existing strokes to MyScript for recognition continuity
      strokes.forEach { stroke ->
        myScriptPageManager.addStroke(stroke)
      }
    }
  }

  // === Stroke Lifecycle ===
  fun onPenUp(stroke: Stroke) {
    requireNotNull(currentPageId) { "No page active" }

    // 1. Save stroke to database
    viewModelScope.launch {
      noteRepository.saveStroke(currentPageId!!, stroke)
    }

    // 2. Feed to MyScript for recognition
    myScriptPageManager.addStroke(stroke)
    // Recognition triggers automatically via listener → onRecognitionUpdated callback
  }

  fun onStrokeErased(strokeId: String, remainingStrokes: List<Stroke>) {
    viewModelScope.launch {
      noteRepository.deleteStroke(strokeId)
    }
    myScriptPageManager.onStrokeErased(strokeId, remainingStrokes)
    // Recognition updates automatically
  }

  fun onUndo(currentStrokes: List<Stroke>) {
    myScriptPageManager.onUndo(currentStrokes)
  }

  fun onRedo(currentStrokes: List<Stroke>) {
    myScriptPageManager.onRedo(currentStrokes)
  }

  override fun onCleared() {
    myScriptPageManager.closeCurrentPage()
    super.onCleared()
  }

  companion object {
    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as OnyxApplication

        // Check if MyScript engine is initialized before creating page manager
        // This protects against race conditions if ViewModel is created before
        // Application.onCreate() completes the async initialization
        if (!app.myScriptEngine.isInitialized()) {
          Log.w("NoteEditorViewModel", "MyScript not initialized - recognition unavailable")
        }

        val pageManager = MyScriptPageManager(
          engine = app.myScriptEngine.getEngine(),  // Access via getter
          context = app
          // NOTE: displayMetrics not needed - we use fixed pt→mm conversion
        )
        NoteEditorViewModel(
          noteRepository = app.noteRepository,
          myScriptPageManager = pageManager
        )
      }
    }
  }
}
```

**Composable Access Pattern:**

```kotlin
@Composable
fun NoteEditorScreen(noteId: String) {
  val app = LocalContext.current.applicationContext as OnyxApplication
  val viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory)
  // ... use viewModel
}
```

**DI Strategy Note:** Plan A uses manual DI via Application class. If migrating to Hilt/Dagger in future, these patterns translate directly.

### Ink API Fallback Strategy (InProgressStrokesView → CanvasFrontBufferedRenderer)

**Problem**: Jetpack Ink `InProgressStrokesView` tests show `@SdkSuppress(minSdkVersion = 35)` but our target is API 30.

**Decision**: Attempt `InProgressStrokesView` first; implement `CanvasFrontBufferedRenderer` fallback if needed.

**Compatibility Check (in Task 3.2):**

```kotlin
fun isInProgressStrokesViewSupported(): Boolean {
  // Try to instantiate and verify it renders correctly on device
  return try {
    val testView = InProgressStrokesView(context)
    // If this throws or causes issues, fallback is needed
    true
  } catch (e: Exception) {
    Log.w("InkCompat", "InProgressStrokesView not supported: ${e.message}")
    false
  }
}
```

**Fallback Implementation (if needed):**

If `InProgressStrokesView` fails on API 30, create `LowLatencyInkView` using `CanvasFrontBufferedRenderer`:

```kotlin
/**
 * Fallback low-latency ink view using CanvasFrontBufferedRenderer.
 * Used if InProgressStrokesView is not compatible with target SDK.
 *
 * Reference: androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
 * Requires: androidx.graphics:graphics-core:1.0.0
 */
@RequiresApi(Build.VERSION_CODES.Q) // API 29+
class LowLatencyInkView(
  context: Context,
  private val surfaceView: SurfaceView
) {
  private val renderer: CanvasFrontBufferedRenderer<StrokeSegment>

  private val callback = object : CanvasFrontBufferedRenderer.Callback<StrokeSegment> {
    override fun onDrawFrontBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, param: StrokeSegment) {
      // Draw the new segment (low-latency front buffer)
      drawStrokeSegment(canvas, param)
    }

    override fun onDrawMultiBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, params: Collection<StrokeSegment>) {
      // Draw all committed segments (double-buffered)
      params.forEach { drawStrokeSegment(canvas, it) }
    }
  }

  init {
    renderer = CanvasFrontBufferedRenderer(surfaceView, callback)
  }

  fun onNewStrokeSegment(segment: StrokeSegment) {
    renderer.renderFrontBufferedLayer(segment)
  }

  fun commitStroke() {
    renderer.commit()
  }

  fun cancel() {
    renderer.cancel()
  }
}
```

**Target File (if fallback needed):** `apps/android/app/src/main/java/com/onyx/android/ink/ui/LowLatencyInkView.kt`

### Coordinate Conversion Pipeline

**Three coordinate systems in use:**

| System                   | Unit                       | Used By                     | Example                              |
| ------------------------ | -------------------------- | --------------------------- | ------------------------------------ |
| **Page Coordinates**     | pt (points, 72pt = 1 inch) | Stroke storage, V0 API      | x=306, y=396 (center of letter page) |
| **Screen Coordinates**   | px (pixels)                | Touch input, Compose Canvas | x=612, y=792 (at zoom=2.0)           |
| **MyScript Coordinates** | mm (millimeters)           | OffscreenEditor             | x=108, y=140 (center in mm)          |

**Conversion Functions:**

```kotlin
// Page ↔ Screen (ViewTransform)
// Already defined in ViewTransform data class

// Page → Screen: pageToScreen(pt) = pt * zoom + pan
// Screen → Page: screenToPage(px) = (px - pan) / zoom

// Page ↔ MyScript (new)
// pt → mm: pt * 25.4 / 72 = pt * 0.3528
// mm → pt: mm * 72 / 25.4 = mm * 2.8346
```

**MyScript Coordinate Converter:**

```kotlin
/**
 * Converts between our page coordinates (pt) and MyScript coordinates (mm).
 * Used when feeding strokes to OffscreenEditor and interpreting recognition results.
 *
 * NOTE: We use a fixed pt→mm ratio (25.4/72), NOT the sample's DisplayMetricsConverter.
 * DisplayMetricsConverter is for px↔mm (screen DPI dependent) - not applicable here.
 * See: myscript-examples/.../DisplayMetricsConverter.kt for comparison.
 */
object MyScriptCoordinateConverter {
  private const val INCH_TO_MM = 25.4f
  private const val PT_PER_INCH = 72f
  private const val PT_TO_MM = INCH_TO_MM / PT_PER_INCH  // 0.3528
  private const val MM_TO_PT = PT_PER_INCH / INCH_TO_MM  // 2.8346

  // Page (pt) → MyScript (mm)
  fun ptToMm(pt: Float): Float = pt * PT_TO_MM

  // MyScript (mm) → Page (pt)
  fun mmToPt(mm: Float): Float = mm * MM_TO_PT

  // Convert Stroke to PointerEvents in mm coordinates
  fun strokeToPointerEvents(stroke: Stroke): Array<PointerEvent> {
    return stroke.points.mapIndexed { index, point ->
      val eventType = when (index) {
        0 -> PointerEventType.DOWN
        stroke.points.lastIndex -> PointerEventType.UP
        else -> PointerEventType.MOVE
      }
      PointerEvent(
        eventType = eventType,
        x = ptToMm(point.x),  // Convert pt → mm
        y = ptToMm(point.y),  // Convert pt → mm
        t = point.t,
        f = point.p ?: 0.5f,
        pointerType = PointerType.PEN,
        pointerId = 0
      )
    }.toTypedArray()
  }
}
```

**Complete Flow:**

1. **Touch Input** (screen px) → `ViewTransform.screenToPage()` → **Page pt** → Store in Stroke
2. **Stroke Storage** (page pt) → Save to Room
3. **Rendering** (page pt) → `ViewTransform.pageToScreen()` → **Screen px** → Draw to Canvas
4. **MyScript** (page pt) → `MyScriptCoordinateConverter.ptToMm()` → **mm** → Feed to OffscreenEditor

### StrokeStyle to Jetpack Ink Brush Mapping

**Our StrokeStyle (V0 API aligned):**

```kotlin
data class StrokeStyle(
  val color: String,       // "#RRGGBB" or "#RRGGBBAA"
  val baseWidth: Float,    // Width in pt at 1x zoom
  val tool: String         // "pen" | "highlighter"
)
```

**Jetpack Ink Brush:**

```kotlin
// From Jetpack Ink API (androidx.ink.brush.Brush)
class Brush(
  val family: Family,      // Defines stroke shape/behavior
  val size: Float,         // Stroke width
  val color: ColorLong,    // ARGB color
  val epsilon: Float       // Simplification tolerance
)
```

**Mapping Implementation:**

```kotlin
/**
 * Convert our StrokeStyle to Jetpack Ink Brush for InProgressStrokesView.
 *
 * PRESSURE HANDLING:
 * - Brush.family determines how pressure affects stroke width
 * - StockBrushes.pressurePen(): Width varies with pressure (pen tool)
 * - StockBrushes.highlighter(): Constant width, semi-transparent
 * - StockBrushes.marker(): Constant width, opaque
 *
 * The pressure value is passed via StrokeInput.create(..., pressure = 0.5f)
 * and the BrushFamily interprets it to vary stroke width internally.
 */
fun StrokeStyle.toJetpackBrush(zoom: Float = 1f): Brush {
  val colorLong = Color.parseColor(color).toLong() or 0xFF000000L

  return Brush.createWithColorIntArgb(
    family = when (tool) {
      "pen" -> StockBrushes.pressurePen()       // Width varies with pressure
      "highlighter" -> StockBrushes.highlighter()  // Transparent overlay, constant width
      else -> StockBrushes.pressurePen()
    },
    colorIntArgb = android.graphics.Color.parseColor(color),
    size = baseWidth * zoom,  // Base size at pressure=0.5; varies with pressure
    epsilon = 0.1f            // Default simplification
  )
}

/**
 * Create StrokeInput with pressure from MotionEvent.
 *
 * The pressure value (0.0 to 1.0) is passed to StrokeInput.create() and
 * the BrushFamily (e.g., StockBrushes.pressurePen()) interprets it to
 * vary the rendered stroke width.
 *
 * Reference: androidx.ink.strokes.StrokeInput
 * Reference: https://github.com/androidx/androidx/blob/androidx-main/ink/ink-strokes/src/jvmTest/kotlin/androidx/ink/strokes/StrokeInputTest.kt
 */
fun MotionEvent.toStrokeInput(index: Int = 0): StrokeInput {
  return StrokeInput.create(
    x = getX(index),
    y = getY(index),
    elapsedTimeMillis = eventTime - downTime,
    toolType = InputToolType.STYLUS,
    strokeUnitLengthCm = 0.1f,  // Physical size for rendering
    pressure = getPressure(index).coerceIn(0f, 1f),  // Pen pressure from hardware
    tiltRadians = getAxisValue(MotionEvent.AXIS_TILT, index),
    orientationRadians = getAxisValue(MotionEvent.AXIS_ORIENTATION, index)
  )
}
```

**Where Used:**

- `InProgressStrokesView.startStroke(event, pointerId, brush)` - Create brush from current tool settings
- `DrawScope.drawStroke()` - Convert for finished stroke rendering

**Color Handling:**

```kotlin
// Parse our "#RRGGBB" or "#RRGGBBAA" format
fun parseStrokeColor(colorString: String): Long {
  val color = android.graphics.Color.parseColor(colorString)
  // Jetpack Ink uses ColorLong with SRGB colorspace
  return Color.valueOf(color).pack()
}

// For highlighter, ensure alpha for transparency
fun getHighlighterColor(colorString: String): Long {
  val baseColor = android.graphics.Color.parseColor(colorString)
  // Apply 40% alpha for highlighter effect
  val alpha = 0.4f
  return Color.valueOf(
    Color.red(baseColor) / 255f,
    Color.green(baseColor) / 255f,
    Color.blue(baseColor) / 255f,
    alpha
  ).pack()
}
```

### Build Configuration Requirements

**Gradle/SDK Configuration** (to set in `apps/android/app/build.gradle.kts`):

```kotlin
android {
    namespace = "com.onyx.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.onyx.android"
        minSdk = 28    // Android 9 (Pie)
        targetSdk = 30 // Android 11
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Compose BOM (Bill of Materials) - ensures consistent versions
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Core Compose dependencies (version from BOM)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Low-latency graphics for fallback renderer (LowLatencyInkView)
    // Required if InProgressStrokesView doesn't work on targetSdk 30
    // Provides: CanvasFrontBufferedRenderer for front-buffered rendering
    implementation("androidx.graphics:graphics-core:1.0.0")

    // kotlinx.serialization for JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}

// In settings.gradle.kts or root build.gradle.kts:
plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}

// In app/build.gradle.kts:
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" // For Room
}
```

### ViewTransform State Ownership

**The `ViewTransform` state (zoom, pan) is shared across all coordinate-dependent operations:**

```kotlin
/**
 * ViewTransform holds the current zoom/pan state for a page.
 * This is the SINGLE SOURCE OF TRUTH for all coordinate conversions:
 * - Ink canvas rendering
 * - PDF rendering
 * - Stroke capture (screen → page)
 * - Eraser hit testing (screen → page)
 * - MyScript pointer events (page → screen for MyScript, which uses screen coords)
 * - Text selection hit testing (screen → page)
 */
data class ViewTransform(
  val zoom: Float = 1f,    // 1.0 = 72 DPI (1pt = 1px), 2.0 = 144 DPI
  val panX: Float = 0f,    // Pan offset in screen pixels
  val panY: Float = 0f     // Pan offset in screen pixels
)

// Owned by: NoteEditorViewModel (or equivalent ViewModel/State holder)
// Lifetime: Per page (reset on page navigation)
// Updates: Pinch-to-zoom and two-finger pan gestures

// All coordinate conversions use this shared state:
class CoordinateConverter(private val transform: ViewTransform) {
  fun pageToScreen(pageX: Float, pageY: Float): Pair<Float, Float> {
    return Pair(
      pageX * transform.zoom + transform.panX,
      pageY * transform.zoom + transform.panY
    )
  }

  fun screenToPage(screenX: Float, screenY: Float): Pair<Float, Float> {
    return Pair(
      (screenX - transform.panX) / transform.zoom,
      (screenY - transform.panY) / transform.zoom
    )
  }
}
```

**Usage by Component:**
| Component | Coordinate Direction | Purpose |
|-----------|---------------------|---------|
| InkCanvas | page → screen | Render strokes at current zoom |
| StrokeCapture | screen → page | Save stroke points in page coords |
| PdfRenderer | page → screen | Render PDF at current zoom |
| EraserTool | screen → page | Hit test in page coords |
| MyScriptEngine | page → screen | Feed pointer events in screen coords |
| TextSelection | screen → page | Hit test text in page coords |

### Test PDF Assets

**For objective verification of PDF text selection and ink alignment, create test PDFs:**

**Method 1: Generate programmatically (recommended):**

```kotlin
// Use MuPDF or iText to generate test PDFs:
fun generateTestAlignmentPdf(outputPath: String) {
  // Create 612x792 pt (US Letter) PDF with:
  // 1. Crosshairs at center (306, 396)
  // 2. Grid lines every 100pt
  // 3. Text "0,0" at (0, 0), "100,100" at (100, 100), etc.
  // 4. Known text "Hello World" at (100, 700) for selection test
}
```

**Method 2: Manual creation with reference coordinates:**

1. Create PDF in any tool (Figma, Illustrator, Word)
2. Set page size to US Letter (8.5" × 11")
3. Draw crosshairs at exact center
4. Add text with known content at known positions
5. Export to PDF (no compression, standard fonts)

**Store test PDFs in:** `apps/android/app/src/androidTest/assets/test_pdfs/`

- `test_alignment.pdf` - Grid and crosshairs for ink alignment verification
- `test_selection.pdf` - Known text at known coordinates for text selection verification

### PDF Asset Storage Strategy

**How `pdfAssetId` maps to persisted PDF files:**

```kotlin
/**
 * PDF files are stored in app-private internal storage.
 * The pdfAssetId is a UUID that maps to a file in the pdf_assets/ subdirectory.
 */
object PdfAssetStorage {
  // Storage directory: context.filesDir/pdf_assets/
  // Example: /data/data/com.onyx.android/files/pdf_assets/{uuid}.pdf

  fun getStorageDir(context: Context): File {
    return File(context.filesDir, "pdf_assets").apply { mkdirs() }
  }

  fun generateAssetId(): String = UUID.randomUUID().toString()

  fun getFileForAsset(context: Context, assetId: String): File {
    return File(getStorageDir(context), "$assetId.pdf")
  }

  // Import PDF from content URI (file picker result):
  suspend fun importPdf(context: Context, sourceUri: Uri): String {
    val assetId = generateAssetId()
    val destFile = getFileForAsset(context, assetId)

    context.contentResolver.openInputStream(sourceUri)?.use { input ->
      destFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }

    return assetId  // Store this in PageEntity.pdfAssetId
  }

  // Load PDF for rendering:
  fun loadPdf(context: Context, assetId: String): Document {
    val file = getFileForAsset(context, assetId)
    return Document.openDocument(file.absolutePath)
  }

  // Delete PDF (when note is deleted):
  suspend fun deletePdf(context: Context, assetId: String) {
    val file = getFileForAsset(context, assetId)
    file.delete()
  }
}
```

**Storage Mapping:**
| Field | Value | File Path |
|-------|-------|-----------|
| `pdfAssetId` | UUID string (e.g., `"abc123-..."`) | `{filesDir}/pdf_assets/{uuid}.pdf` |
| Resolution | `PdfAssetStorage.getFileForAsset(context, assetId)` | Returns `File` object |

**Lifecycle:**

1. User picks PDF → Copy to internal storage → Generate `pdfAssetId`
2. Store `pdfAssetId` in `PageEntity.pdfAssetId`
3. On page load → Resolve file via `PdfAssetStorage.loadPdf(context, assetId)`
4. On note delete → Delete PDF file via `PdfAssetStorage.deletePdf(context, assetId)`

**No separate asset table needed** - The file path is derived directly from `pdfAssetId`.

### Room Database Schema & Migration Strategy

**Initial schema (version 1) includes ALL tables upfront:**

```kotlin
@Database(
  entities = [
    NoteEntity::class,
    PageEntity::class,
    StrokeEntity::class,
    RecognitionIndexEntity::class,
    RecognitionFtsEntity::class  // FTS table included from version 1
  ],
  version = 1,
  exportSchema = true  // Generate schema JSON for tracking
)
@TypeConverters(Converters::class)
abstract class OnyxDatabase : RoomDatabase() {
  abstract fun noteDao(): NoteDao
  abstract fun pageDao(): PageDao
  abstract fun strokeDao(): StrokeDao
  abstract fun recognitionDao(): RecognitionDao

  companion object {
    // Database file name
    const val DATABASE_NAME = "onyx_notes.db"

    // Singleton builder (use in DI module)
    fun build(context: Context): OnyxDatabase {
      return Room.databaseBuilder(context, OnyxDatabase::class.java, DATABASE_NAME)
        .fallbackToDestructiveMigration()  // OK for v1 development
        .build()
    }
  }
}
```

**Schema Evolution Strategy:**

- **Plan A (v1)**: All tables created together, no migrations needed
- **Future versions**: Add Migration objects when schema changes
- `exportSchema = true`: Room generates `schemas/1.json` for version control
- Development builds use `fallbackToDestructiveMigration()` (acceptable data loss during dev)
- Production builds will use explicit Migration objects

**FTS Table Notes:**

- FTS table is declared in `@Database(entities = [...])` from version 1
- Room generates triggers automatically via `@Fts4(contentEntity = ...)`
- No runtime schema mismatch possible because FTS is in initial schema

---

## Work Objectives

### Core Objective

Build a functional Android tablet app that captures stylus ink with low latency, persists strokes locally, performs realtime handwriting recognition, renders PDFs with ink overlay, and supports local full-text search.

### Concrete Deliverables

1. **Android App** (`apps/android/`) - Kotlin, Jetpack Compose, Material 3
2. **Ink Surface** - Android Ink API (Jetpack Graphics) with low-latency rendering
3. **Persistence** - Room database with sync-compatible schema (raw points + style)
4. **Recognition** - MyScript realtime recognition → search index
5. **PDF Viewer** - MuPDF with text selection and ink overlay
6. **Local Search** - FTS over recognized text
7. **Device Identity** - UUID generation and persistence

### Definition of Done

- [ ] App builds: `./gradlew :app:assembleDebug` (from `apps/android/` directory) - **FIXED with JDK 17**
- [ ] App launches on physical tablet ⚠️ **BLOCKED**: Requires physical device (see device-blocker.md)
- [ ] Can create multi-page notes
- [ ] Can draw strokes with stylus (low latency) ⚠️ **BLOCKED**: Requires physical stylus with pressure/tilt (see device-blocker.md)
- [ ] Can undo/redo strokes
- [ ] Can zoom/pan canvas
- [ ] Strokes persist across app restarts
- [ ] MyScript produces recognized text ⚠️ **BLOCKED**: Recognition requires real pen strokes (see device-blocker.md)
- [ ] Search finds notes by recognized text
- [ ] PDF pages render with ink overlay

### Must Have

- Multi-page notes (add/navigate pages)
- Page canvas with ink rendering
- Stroke capture (pressure, tilt)
- Stroke eraser
- Undo/redo
- Zoom/pan for ink canvas
- Brush customization (color, size)
- Room database (sync-compatible schema)
- MyScript realtime recognition → search index
- PDF import via file picker
- PDF viewing with text selection
- Ink overlay on PDF
- Local FTS search
- Device ID persistence

### Must NOT Have (Guardrails)

- NO infinite canvas (2048×2048 tiles) → Plan Av2
- NO segment eraser → Plan Av2
- NO batch recognition with throttling → Plan Av2
- NO encrypted PDF support → Out of scope
- NO recognition text overlay display → Plan Av2
- NO backend/sync integration → Plan C
- NO sharing features → Plan C
- NO iOS app → No test device

---

## Verification Strategy

### Test Decision

- **Primary**: Manual verification on physical tablet
- **Secondary**: JUnit 5 unit tests + MockK for mocking

### Verification Patterns by Feature Type

- **UI/Ink**: Visual check on tablet with stylus
- **Persistence**: Unit test + app restart test
- **Recognition**: Write text → verify in search results

---

## TODOs

### Phase 1: Project Foundation

- [x] 1.1 Create monorepo directory structure

  **What to do**:
  - Create `apps/android/` directory
  - Create root `.gitignore` with Android/Node patterns

  **Verification**:
  - [ ] Directory structure exists: `ls apps/android packages`

  **Commit**: Group with 1.3
  - Message: `chore: initialize monorepo structure`

- [x] 1.3 Create Turborepo configuration

  **What to do**:
  - Create `turbo.json` with basic pipeline
  - Add build, test, lint tasks

  **Verification**:
  - [ ] `turbo.json` exists with valid JSON

  **Commit**: Group with 1.1

---

- [x] 1.5 Create Android project with Gradle Kotlin DSL

  **What to do**:
  - Use Android Studio to create new project in `apps/android/`
  - Select "Empty Compose Activity" template
  - Configure:
    - Package: `com.onyx.android`
    - Language: Kotlin
    - Min SDK: 28
    - Build config: Kotlin DSL

  **Key Gradle Files:**

  `apps/android/settings.gradle.kts`:

  ```kotlin
  pluginManagement {
      repositories {
          google()
          mavenCentral()
          gradlePluginPortal()
      }
  }

  dependencyResolutionManagement {
      repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
      repositories {
          google()
          mavenCentral()
          // NOTE: MyScript SDK (com.myscript:iink) is on Maven Central
          // No additional repository needed!
      }
  }

  rootProject.name = "onyx-android"
  include(":app")

  plugins {
      id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
      id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
  }
  ```

  **Running Gradle Commands:**
  - ALL `./gradlew` commands must be run from `apps/android/` directory
  - Windows: Use `gradlew.bat` or `.\gradlew`
  - Unix: Use `./gradlew`
  - The `:app:` prefix refers to the `app` module within the `apps/android/` Gradle project

  **Command examples (from `C:\onyx-v2\apps\android\`):**

  ```bash
  # From apps/android/ directory:
  ./gradlew :app:assembleDebug     # Build debug APK
  ./gradlew :app:test              # Run unit tests
  ./gradlew :app:connectedTest     # Run instrumented tests
  ./gradlew sync                   # Sync Gradle files
  ```

  **Post-creation Gradle Configuration** (update `apps/android/app/build.gradle.kts`):

  ```kotlin
  plugins {
      id("com.android.application")
      id("org.jetbrains.kotlin.android")
      id("org.jetbrains.kotlin.plugin.serialization")  // For JSON serialization
      id("com.google.devtools.ksp")                    // For Room
  }

  android {
      namespace = "com.onyx.android"
      compileSdk = 35  // Latest stable

      defaultConfig {
          applicationId = "com.onyx.android"
          minSdk = 28      // Android 9 (Pie) - for Jetpack Ink support
          targetSdk = 30   // Android 11 - for test tablet
          versionCode = 1
          versionName = "1.0"

          testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }

      buildFeatures {
          compose = true
      }

      composeOptions {
          kotlinCompilerExtensionVersion = "1.5.8"
      }

      kotlinOptions {
          jvmTarget = "17"
      }
  }

  dependencies {
      // Compose BOM (ensures consistent versions)
      val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
      implementation(composeBom)

      // Core Compose (versions from BOM)
      implementation("androidx.compose.ui:ui")
      implementation("androidx.compose.ui:ui-graphics")
      implementation("androidx.compose.ui:ui-tooling-preview")
      implementation("androidx.compose.material3:material3")
      implementation("androidx.activity:activity-compose:1.8.2")
      implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

      // kotlinx.serialization for JSON
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

      // Navigation
      implementation("androidx.navigation:navigation-compose:2.7.7")

      // Debugging
      debugImplementation("androidx.compose.ui:ui-tooling")
  }
  ```

---

- [x] 1.6 UI foundation (baseline)

  **What to do**:
  - Add a basic Compose scaffold with a single, always-visible top toolbar.
  - Include: back button, note title (editable later), and overflow/settings menu.
  - Add a simple horizontal tool row (pen, highlighter, eraser placeholders).
  - Ensure the toolbar never collapses when drawing.
  - Keep everything minimal and functional; visuals are refined in the UI overhaul milestone.

  **Verification**:
  - [ ] App renders with a single top toolbar and a canvas area below.
  - [ ] Drawing does not hide the toolbar.

  **Commit**: Group with initial app scaffolding

  ````

  **Root settings.gradle.kts** (update to add KSP and serialization plugins):
  ```kotlin
  pluginManagement {
      repositories {
          google()
          mavenCentral()
          gradlePluginPortal()
      }
  }

  plugins {
      id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
      id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
  }
  ````

  **Verification** (run from `apps/android/` directory):
  - [ ] `cd apps/android && ./gradlew :app:assembleDebug` succeeds (or `gradlew.bat` on Windows)
  - [ ] App launches on tablet showing default Compose screen
  - [ ] `compileSdk = 35` in build.gradle.kts
  - [ ] `minSdk = 28` and `targetSdk = 30` in build.gradle.kts
  - [ ] kotlinx-serialization-json dependency present
  - [ ] KSP plugin configured (for Room in Phase 4)

  **Commit**: YES
  - Message: `feat(android): scaffold app with Jetpack Compose`

---

- [x] 1.6 Configure JUnit 5 and MockK for testing

  **What to do**:
  - Add to `apps/android/app/build.gradle.kts`:

    ```kotlin
    android {
      testOptions {
        unitTests.all {
          it.useJUnitPlatform()  // Enable JUnit 5 platform
        }
      }
    }

    dependencies {
      // JUnit 5 (Jupiter)
      testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
      testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
      testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")

      // MockK for mocking in Kotlin
      testImplementation("io.mockk:mockk:1.13.8")

      // Coroutines test support
      testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    }
    ```

  - Create example test to verify setup at `app/src/test/java/com/onyx/android/ExampleTest.kt`:

    ```kotlin
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.Assertions.*

    class ExampleTest {
      @Test
      fun `basic test works`() {
        assertEquals(4, 2 + 2)
      }
    }
    ```

  **Verification** (run from `apps/android/` directory):
  - [ ] `./gradlew :app:test` runs and passes 1 test
  - [ ] Test output shows "JUnit Jupiter" (not JUnit 4)
  - [ ] Console shows: `ExampleTest > basic test works PASSED`

  **Commit**: YES
  - Message: `chore(android): setup JUnit 5 and MockK testing`

---

- [x] 1.7 Add Material 3 and configure theme

  **What to do**:
  - Ensure Material 3 dependency: `androidx.compose.material3:material3` (from BOM in task 1.5)
  - Create custom theme in `ui/theme/`:

  **Implementation Pattern** (concrete file structure):

  ```kotlin
  // File: ui/theme/Color.kt
  package com.onyx.android.ui.theme

  import androidx.compose.ui.graphics.Color

  // Primary brand colors
  val OnyxPrimary = Color(0xFF1A1A2E)      // Deep navy
  val OnyxSecondary = Color(0xFF16213E)    // Dark blue
  val OnyxTertiary = Color(0xFF0F3460)     // Medium blue
  val OnyxSurface = Color(0xFFFAFAFA)      // Light surface
  val OnyxOnSurface = Color(0xFF1A1A2E)    // Text on light
  val OnyxSurfaceDark = Color(0xFF1A1A2E)  // Dark surface
  val OnyxOnSurfaceDark = Color(0xFFFAFAFA) // Text on dark

  // Ink colors for brush palette
  val InkBlack = Color(0xFF000000)
  val InkBlue = Color(0xFF0D47A1)
  val InkRed = Color(0xFFB71C1C)
  val InkGreen = Color(0xFF1B5E20)
  val InkYellow = Color(0xFFF9A825)
  ```

  ```kotlin
  // File: ui/theme/Theme.kt
  package com.onyx.android.ui.theme

  import androidx.compose.foundation.isSystemInDarkTheme
  import androidx.compose.material3.*
  import androidx.compose.runtime.Composable

  private val LightColorScheme = lightColorScheme(
    primary = OnyxPrimary,
    secondary = OnyxSecondary,
    tertiary = OnyxTertiary,
    surface = OnyxSurface,
    onSurface = OnyxOnSurface
  )

  private val DarkColorScheme = darkColorScheme(
    primary = OnyxTertiary,
    secondary = OnyxSecondary,
    tertiary = OnyxPrimary,
    surface = OnyxSurfaceDark,
    onSurface = OnyxOnSurfaceDark
  )

  @Composable
  fun OnyxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
  ) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography(),  // Use default Material 3 typography
      content = content
    )
  }
  ```

  ```kotlin
  // File: MainActivity.kt - wrap content in theme
  setContent {
    OnyxTheme {
      Surface(modifier = Modifier.fillMaxSize()) {
        OnyxNavHost()  // From task 1.8
      }
    }
  }
  ```

  **Verification**:
  - [ ] App displays with Material 3 styling (rounded corners, elevation shadows)
  - [ ] Theme colors are applied (check FAB uses primary color)
  - [ ] Dark mode toggle works (Settings → Display → Dark theme)

  **Commit**: Group with 1.8

---

- [x] 1.8 Create navigation structure

  **What to do**:
  - Add Navigation Compose dependency: `androidx.navigation:navigation-compose` (already in task 1.5)
  - Create NavHost with routes:
    - `home` - Notes list
    - `editor/{noteId}` - Note editor
  - Create placeholder composables for each screen

  **Implementation Pattern** (concrete navigation setup):

  ```kotlin
  // File: navigation/OnyxNavHost.kt
  package com.onyx.android.navigation

  import androidx.compose.runtime.Composable
  import androidx.navigation.NavHostController
  import androidx.navigation.NavType
  import androidx.navigation.compose.NavHost
  import androidx.navigation.compose.composable
  import androidx.navigation.compose.rememberNavController
  import androidx.navigation.navArgument

  // Route constants
  object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{noteId}"

    fun editor(noteId: String) = "editor/$noteId"
  }

  @Composable
  fun OnyxNavHost(
    navController: NavHostController = rememberNavController()
  ) {
    NavHost(
      navController = navController,
      startDestination = Routes.HOME
    ) {
      composable(Routes.HOME) {
        HomeScreen(
          onNavigateToEditor = { noteId ->
            navController.navigate(Routes.editor(noteId))
          }
        )
      }

      composable(
        route = Routes.EDITOR,
        arguments = listOf(
          navArgument("noteId") { type = NavType.StringType }
        )
      ) { backStackEntry ->
        val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
        NoteEditorScreen(
          noteId = noteId,
          onNavigateBack = { navController.popBackStack() }
        )
      }
    }
  }
  ```

  **Navigation Pattern:**
  - `HomeScreen` calls `onNavigateToEditor(noteId)` after creating note
  - `NoteEditorScreen` receives `noteId` as parameter and loads note from database
  - Back navigation uses `popBackStack()` to return to Home

  **Verification**:
  - [ ] Can navigate from Home to Editor (tap FAB → Editor screen appears)
  - [ ] Can navigate back (tap back button/gesture → Home screen appears)
  - [ ] Editor receives correct noteId (log `noteId` in Editor composable)

  **Commit**: YES
  - Message: `feat(android): add navigation and theme setup`

---

- [x] 1.9 Create Home screen with FAB

  **What to do**:
  - Create `HomeScreen` composable
  - Add FAB (FloatingActionButton) for "New Note"
  - Add placeholder notes list (empty for now)
  - **Note Creation Flow**: FAB creates note immediately via `repository.createNote()`:
    1. FAB tap → Call `repository.createNote()` (returns NoteWithFirstPage with UUID)
    2. Repository creates NoteEntity + first PageEntity in database
    3. Navigate to Editor with the real `noteId`
    4. Editor loads the already-created note
  - This ensures noteId exists before navigation (simpler than "create on first save")

  **Task Dependency Note:**
  - This task references `repository.createNote()` which is defined in Phase 4 (task 4.9).
  - **Implementation order**: Complete Phase 4 first, then wire up this screen.
  - **During Phase 1 development**: Use a stub `HomeScreenViewModel` with a TODO comment:
    ```kotlin
    // TODO: Wire to NoteRepository.createNote() after Phase 4 completion
    fun onCreateNote(): String = UUID.randomUUID().toString()
    ```

  **Verification**:
  - [ ] Home screen displays FAB in bottom-right
  - [ ] Tapping FAB → note created in database:
    - Using `run-as` (debug builds): `adb shell run-as com.onyx.android sqlite3 databases/onyx_notes.db "SELECT noteId FROM notes"`
    - Or use Android Studio App Inspection → Database Inspector
    - Or add temporary debug log: `Log.d("HomeScreen", "Created note: $noteId")`
  - [ ] Tapping FAB navigates to Editor with valid noteId

  **Commit**: Group with 1.10

---

- [x] 1.10 Create Note Editor screen shell

  **What to do**:
  - Create `NoteEditorScreen` composable
  - Add top app bar with back navigation
  - Add placeholder for ink canvas area
  - Add bottom toolbar placeholder (for tools)

  **Verification**:
  - [ ] Editor screen displays with app bar
  - [ ] Back button returns to Home

  **Commit**: YES
  - Message: `feat(android): add Home and Editor screens`

---

### Phase 2: Ink Interface & Models

- [x] 2.1 Define StrokePoint data class

  **What to do**:
  - Create `ink/model/StrokePoint.kt`:
    ```kotlin
    data class StrokePoint(
      val x: Float,
      val y: Float,
      val t: Long,             // timestamp offset or absolute, Unix ms
      val p: Float? = null,    // pressure 0..1
      val tx: Float? = null,   // tilt x (radians)
      val ty: Float? = null,   // tilt y (radians)
      val r: Float? = null     // rotation/azimuth (optional)
    )
    ```
  - Ensure fields align with v0 API Point definition (`V0-api.md:136-144`)

  **References**:
  - `V0-api.md:136-144` - Point type definition

  **Verification**:
  - [ ] Data class compiles
  - [ ] Field names match v0 API: x, y, t, p?, tx?, ty?, r?
  - [ ] Types match: Float for coordinates, Long for timestamp

  **Commit**: Group with 2.2-2.5

---

- [x] 2.2 Define Stroke data class

  **What to do**:
  - Create `ink/model/Stroke.kt`:

    ```kotlin
    import kotlinx.serialization.Serializable
    import kotlinx.serialization.SerialName

    data class Stroke(
      val id: String,                    // UUID string
      val points: List<StrokePoint>,
      val style: StrokeStyle,            // Matches v0 API StrokeStyle
      val bounds: StrokeBounds,          // Required for sync ops
      val createdAt: Long,               // Unix ms
      val createdLamport: Long = 0       // For segment eraser in Av2/C
    )

    @Serializable
    data class StrokeStyle(
      val tool: Tool,
      val color: String = "#000000",     // hex color string
      val baseWidth: Float,              // base width in page units
      val minWidthFactor: Float = 0.85f, // pressure min (±15%)
      val maxWidthFactor: Float = 1.15f, // pressure max
      val nibRotation: Boolean = false   // future: nib angle
    )

    @Serializable
    data class StrokeBounds(
      val x: Float,
      val y: Float,
      val w: Float,
      val h: Float
    )

    @Serializable
    enum class Tool(val apiValue: String) {
      @SerialName("pen") PEN("pen"),
      @SerialName("highlighter") HIGHLIGHTER("highlighter")
      // Note: v0 API currently only defines "pen" (V0-api.md:131)
      // Highlighter is a client-side extension stored as "highlighter"
      // Older sync clients may not recognize "highlighter" - handle gracefully in Plan C
    }
    ```

  - **Serialization Notes**:
    - `StrokeStyle`, `StrokeBounds`, and `Tool` have `@Serializable` for use with kotlinx.serialization
    - `Tool` uses `@SerialName` to serialize as distinct lowercase strings ("pen", "highlighter")
    - `Stroke` itself is NOT serializable (points are serialized separately via StrokeSerializer)
  - **Tool Enum Mapping**:
    - PEN → "pen" (opaque stroke, normal rendering)
    - HIGHLIGHTER → "highlighter" (rendered with 50% opacity and larger width in UI)
    - Storage: Each tool type has distinct serialized value
    - Rendering: Highlighter applies `alpha = 0.5f` and `baseWidth * 2` during draw
    - v0 API only defines "pen" (`V0-api.md:131`), "highlighter" is client extension for Plan A
  - Matches v0 API StrokeStyle (`V0-api.md:127-134`)
  - Bounds required for StrokeAdd ops (`V0-api.md:153`)
  - `createdLamport` for segment eraser collaboration safety (Av2/C)

  **References**:
  - `V0-api.md:127-134` - StrokeStyle definition
  - `V0-api.md:153` - bounds in StrokeAdd
  - `.sisyphus/plans/milestone-av2-advanced-features.md:16-17` - createdLamport for segment eraser

  **Verification**:
  - [ ] Data class compiles
  - [ ] ID is String (not Long/Int)
  - [ ] StrokeStyle has all v0 API fields
  - [ ] Bounds struct matches v0 API format
  - [ ] `@Serializable` annotations present on StrokeStyle, StrokeBounds, Tool
  - [ ] Tool enum serializes to lowercase "pen" string

  **Commit**: Group with 2.1, 2.3-2.5

---

- [x] 2.3 Define NoteKind enum

  **What to do**:
  - Create `ink/model/NoteKind.kt`:
    ```kotlin
    enum class NoteKind(val value: String) {
      INK("ink"),        // Standard ink-only page
      PDF("pdf"),        // PDF-backed page (no ink)
      MIXED("mixed"),    // PDF with ink annotations
      INFINITE("infinite") // Infinite canvas (Av2)
    }
    ```
  - Matches v0 API `NoteKind` (`V0-api.md:47`)
  - Note: v0 API uses "ink" not "page" for standard canvas pages

  **References**:
  - `V0-api.md:47` - NoteKind type definition

  **Verification**:
  - [ ] Enum compiles
  - [ ] Values match v0 API exactly: ink, pdf, mixed, infinite
  - [ ] String serialization uses lowercase values

  **Commit**: Group with 2.1-2.5

---

- [x] 2.4 Define Brush configuration (UI state)

  **What to do**:
  - Create `ink/model/Brush.kt`:
    ```kotlin
    /**
     * Brush represents the current UI brush state.
     * When creating a Stroke, convert Brush -> StrokeStyle for persistence.
     */
    data class Brush(
      val tool: Tool = Tool.PEN,
      val color: String = "#000000",     // hex color
      val baseWidth: Float = 2.0f,       // in page units (pt)
      val minWidthFactor: Float = 0.85f,
      val maxWidthFactor: Float = 1.15f
    ) {
      fun toStrokeStyle(): StrokeStyle = StrokeStyle(
        tool = tool,
        color = color,
        baseWidth = baseWidth,
        minWidthFactor = minWidthFactor,
        maxWidthFactor = maxWidthFactor,
        nibRotation = false
      )
    }
    ```
  - Brush is UI state; StrokeStyle is persisted with strokes
  - Conversion ensures v0 API compatibility

  **References**:
  - `V0-api.md:127-134` - StrokeStyle that Brush converts to

  **Verification**:
  - [ ] Data class compiles
  - [ ] `toStrokeStyle()` produces valid StrokeStyle

  **Commit**: Group with 2.1-2.5

---

- [x] 2.5 Define InkSurface interface

  **What to do**:
  - Create `ink/InkSurface.kt`:
    ```kotlin
    interface InkSurface {
      fun setBrush(brush: Brush)
      fun setTool(tool: Tool)
      fun getStrokes(): List<Stroke>
      fun addStroke(stroke: Stroke)
      fun removeStroke(strokeId: String)
      fun clear()
      fun undo(): Boolean
      fun redo(): Boolean
      fun setOnStrokeAddedListener(listener: (Stroke) -> Unit)
    }
    ```

  **Verification**:
  - [ ] Interface compiles

  **Commit**: YES
  - Message: `feat(android): define ink models and surface interface`

---

- [x] 2.6 Define ViewTransform and CoordinateConverter

  **What to do**:
  - Create `ink/model/ViewTransform.kt`:

    ```kotlin
    package com.onyx.android.ink.model

    /**
     * ViewTransform holds the current zoom/pan state for a page.
     * This is the SINGLE SOURCE OF TRUTH for all coordinate conversions:
     * - Ink canvas rendering
     * - PDF rendering
     * - Stroke capture (screen → page)
     * - Eraser hit testing (screen → page)
     * - MyScript pointer events (page → screen for MyScript, which uses screen coords)
     * - Text selection hit testing (screen → page)
     */
    data class ViewTransform(
      val zoom: Float = 1f,    // 1.0 = 72 DPI (1pt = 1px), 2.0 = 144 DPI
      val panX: Float = 0f,    // Pan offset in screen pixels
      val panY: Float = 0f     // Pan offset in screen pixels
    ) {
      companion object {
        val DEFAULT = ViewTransform()
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4.0f
      }

      /**
       * Convert page coordinates (pt) to screen pixels.
       * Used by: InkCanvas, PdfRenderer for rendering.
       */
      fun pageToScreen(pageX: Float, pageY: Float): Pair<Float, Float> {
        return Pair(
          pageX * zoom + panX,
          pageY * zoom + panY
        )
      }

      /**
       * Convert screen pixels to page coordinates (pt).
       * Used by: StrokeCapture, EraserTool, TextSelection for input handling.
       */
      fun screenToPage(screenX: Float, screenY: Float): Pair<Float, Float> {
        return Pair(
          (screenX - panX) / zoom,
          (screenY - panY) / zoom
        )
      }

      /**
       * Convert stroke width from page units to screen pixels.
       */
      fun pageWidthToScreen(pageWidth: Float): Float {
        return pageWidth * zoom
      }
    }
    ```

  - Create `ink/CoordinateConverter.kt` (OPTIONAL - for dependency injection scenarios):

    ```kotlin
    package com.onyx.android.ink

    import com.onyx.android.ink.model.ViewTransform

    /**
     * Coordinate converter using shared ViewTransform state.
     * This class is OPTIONAL - ViewTransform has built-in conversion methods.
     * Use this wrapper when you need to inject a converter as a dependency.
     */
    class CoordinateConverter(private val transform: ViewTransform) {

      fun pageToScreen(pageX: Float, pageY: Float) = transform.pageToScreen(pageX, pageY)
      fun screenToPage(screenX: Float, screenY: Float) = transform.screenToPage(screenX, screenY)
      fun pageWidthToScreen(pageWidth: Float) = transform.pageWidthToScreen(pageWidth)
    }
    ```

  **Coordinate Conversion Strategy:**
  - **Primary API**: Use `ViewTransform.pageToScreen()` and `ViewTransform.screenToPage()` directly
  - **Usage**: `val (screenX, screenY) = viewTransform.pageToScreen(point.x, point.y)`
  - **Optional Wrapper**: `CoordinateConverter` is available for dependency injection scenarios
  - **Consistency**: ALL coordinate-dependent code uses `ViewTransform` methods

  **Ownership and Lifecycle**:
  - **Owned by**: `NoteEditorViewModel` (or equivalent state holder for the editor screen)
  - **Lifetime**: Per page (reset to default on page navigation)
  - **Updates**: Modified by pinch-to-zoom and two-finger pan gestures (task 3.8)
  - **Consumers**: InkCanvas, PdfRenderer, StrokeCapture, EraserTool, MyScriptEngine, TextSelection

  **Wiring Pattern** (how components access ViewTransform):

  ```kotlin
  // In NoteEditorViewModel:
  class NoteEditorViewModel : ViewModel() {
    private val _viewTransform = MutableStateFlow(ViewTransform.DEFAULT)
    val viewTransform: StateFlow<ViewTransform> = _viewTransform.asStateFlow()

    fun updateZoom(newZoom: Float) {
      _viewTransform.update { it.copy(zoom = newZoom.coerceIn(ViewTransform.MIN_ZOOM, ViewTransform.MAX_ZOOM)) }
    }

    fun updatePan(deltaX: Float, deltaY: Float) {
      _viewTransform.update { it.copy(panX = it.panX + deltaX, panY = it.panY + deltaY) }
    }

    fun resetTransformForPage() {
      _viewTransform.value = ViewTransform.DEFAULT
    }
  }

  // In NoteEditorScreen composable:
  @Composable
  fun NoteEditorScreen(viewModel: NoteEditorViewModel) {
    val viewTransform by viewModel.viewTransform.collectAsState()

    InkCanvas(
      strokes = strokes,
      viewTransform = viewTransform,  // Passed to canvas
      onZoomPan = { zoom, panX, panY -> viewModel.updateZoom(zoom); viewModel.updatePan(panX, panY) }
    )
  }
  ```

  **Verification**:
  - [ ] `ViewTransform` data class compiles with DEFAULT companion object
  - [ ] `CoordinateConverter` compiles with pageToScreen/screenToPage methods
  - [ ] Unit test: screenToPage(pageToScreen(100, 100)) ≈ (100, 100) at any zoom/pan
  - [ ] Unit test: at zoom=2, pageToScreen(100, 100) = (200, 200) + pan offset

  **Commit**: Group with 2.1-2.5
  - Message: `feat(android): define ink models and surface interface`

---

### Phase 3: Ink Engine Implementation

**SDK Version Compatibility (CRITICAL):**
| SDK | Version | minSdk | compileSdk | Repository | Notes |
|-----|---------|--------|------------|------------|-------|
| Jetpack Ink | 1.0.0-alpha01 | 21 | 35 | Maven Central (`mavenCentral()`) | Alpha - API may change |
| MyScript SDK | 2.0.x | 21 | 35 | Local file or MyScript Maven | Requires license certificate |
| Kotlin | 1.9.x+ | - | - | - | For kotlinx.serialization compatibility |

**Repository Configuration** (already set in task 1.1):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    // Note: Jetpack Ink alpha artifacts are on Maven Central
  }
}
```

**Compatibility Checks (Before Starting Phase 3):**

- [x] Verify minSdk ≥ 21 in `apps/android/app/build.gradle.kts` (minSdk = 28 ✅)
- [x] Verify compileSdk = 35 (✅)
- [x] Verify Kotlin version ≥ 1.9.0 (for sealed class support) (1.9.22 ✅)
- [x] Verify Compose BOM includes compatible versions (2024.02.00 ✅)

- [x] 3.1 Add Jetpack Ink API dependencies

  **What to do**:
  - Add to `apps/android/app/build.gradle.kts`:
    ```kotlin
    dependencies {
      // Jetpack Ink - Alpha (API may change in future releases)
      implementation("androidx.ink:ink-authoring:1.0.0-alpha01")
      implementation("androidx.ink:ink-brush:1.0.0-alpha01")
      implementation("androidx.ink:ink-geometry:1.0.0-alpha01")
      implementation("androidx.ink:ink-rendering:1.0.0-alpha01")
    }
    ```

  **Version Pinning:**
  - Version 1.0.0-alpha01 (first public alpha, Jan 2025)
  - Breaking changes may occur in future alpha releases
  - Monitor: https://developer.android.com/jetpack/androidx/releases/ink

  **Known Compatibility:**
  - Requires `minSdk = 21`
  - Tested with `compileSdk = 35`
  - Works with Compose 1.6.x+

  **References**:
  - Jetpack Ink API: https://developer.android.com/develop/ui/compose/touch-input/stylus-input/about-ink-api
  - Release notes: https://developer.android.com/jetpack/androidx/releases/ink

  **Verification** (run from `apps/android/` directory):
  - [ ] `./gradlew sync` succeeds
  - [ ] `./gradlew :app:dependencies | grep "ink-authoring"` shows `1.0.0-alpha01`
  - [ ] Build compiles: `./gradlew :app:assembleDebug`

  **Commit**: Group with 3.2

---

- [x] 3.2 Create basic InkCanvas composable

  **What to do**:
  - Create `ink/ui/InkCanvas.kt` composable
  - Integrate `InProgressStrokesView` for low-latency rendering (in-progress strokes)
  - Handle basic touch/stylus input
  - Implement finished stroke rendering layer (persisted strokes)

  **Jetpack Ink API Guidance** (concrete implementation pattern):

  ```kotlin
  // Key classes and their roles:
  // 1. InProgressStrokesView - Android View for low-latency front buffer rendering
  // 2. InProgressStroke - Stroke being actively drawn (before pen-up)
  // 3. StrokeInput - Individual point data during drawing
  // 4. FinishedStroke - Completed stroke after pen-up

  // Integration pattern in Compose:
  @Composable
  fun InkCanvas(modifier: Modifier = Modifier) {
    AndroidView(
      factory = { context ->
        InProgressStrokesView(context).apply {
          // Configure for stylus input
          setOnTouchListener { view, event -> handleTouchEvent(event) }
        }
      },
      modifier = modifier
    )
  }

  // Touch event handling:
  fun handleTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        // Deliver input events as soon as they arrive (low latency).
        view.requestUnbufferedDispatch(event)
        // Start new stroke with InProgressStrokesView.startStroke()
        // Returns InProgressStroke handle
      }
      MotionEvent.ACTION_MOVE -> {
        // Add points with inProgressStroke.addToStroke(StrokeInput(...))
        // Points render immediately via front buffer
      }
      MotionEvent.ACTION_UP -> {
        // Finish stroke with inProgressStroke.finishStroke()
        // Returns FinishedStroke for persistence
      }
    }
    return true
  }
  ```

  **Finished Stroke Rendering (persisted strokes after pen-up and reload):**

  ```kotlin
  /**
   * Two-layer rendering architecture:
   *
   * Layer 1 (Front Buffer): InProgressStrokesView
   *   - Handles ONLY in-progress strokes (during active drawing)
   *   - Low-latency rendering via hardware front buffer
   *   - Clears after pen-up (stroke moves to Layer 2)
   *
   * Layer 2 (Canvas): Compose Canvas / SurfaceView
   *   - Renders ALL finished strokes (in-memory + loaded from DB)
   *   - Standard double-buffered rendering
   *   - Redraws when strokes list changes or zoom/pan changes
   */

  @Composable
  fun InkCanvas(
    strokes: List<Stroke>,           // From repository (persisted strokes)
    viewTransform: ViewTransform,     // Shared zoom/pan state
    modifier: Modifier = Modifier
  ) {
    Box(modifier = modifier) {
      // Layer 2: Finished strokes (underneath)
      Canvas(modifier = Modifier.fillMaxSize()) {
        strokes.forEach { stroke ->
          drawStroke(stroke, viewTransform)
        }
      }

      // Layer 1: In-progress strokes (on top)
      AndroidView(
        factory = { context -> InProgressStrokesView(context) },
        modifier = Modifier.fillMaxSize()
      )
    }
  }

  // Draw a single finished stroke to Canvas:
  fun DrawScope.drawStroke(stroke: Stroke, transform: ViewTransform) {
    if (stroke.points.size < 2) return

    val path = Path()
    val firstPoint = stroke.points.first()
    val (startX, startY) = transform.pageToScreen(firstPoint.x, firstPoint.y)
    path.moveTo(startX, startY)

    for (i in 1 until stroke.points.size) {
      val point = stroke.points[i]
      val (x, y) = transform.pageToScreen(point.x, point.y)
      path.lineTo(x, y)
    }

    drawPath(
      path = path,
      color = Color(android.graphics.Color.parseColor(stroke.style.color)),
      style = Stroke(
        width = stroke.style.baseWidth * transform.zoom,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )
  }
  ```

  **Data Flow:**
  1. App opens → Load strokes from database → Pass to InkCanvas as `strokes` parameter
  2. User draws → InProgressStrokesView renders in real-time
  3. Pen-up → Convert to Stroke → Save to database → Add to `strokes` list → Layer 2 renders
  4. Page reload → Load strokes from database → Layer 2 renders all persisted strokes

  **Input/Gesture Ownership Plan (CRITICAL):**

  The InkCanvas uses a layered architecture where touch events must be routed correctly between:
  - **Drawing (InProgressStrokesView)** - Handles stylus/pen input
  - **Zoom/Pan (Compose gestures)** - Handles multi-touch gestures

  **Event Routing Strategy:**

  | Input Type      | Tool Type                           | Pointer Count | Handler                         | Action                        |
  | --------------- | ----------------------------------- | ------------- | ------------------------------- | ----------------------------- |
  | Stylus          | `TOOL_TYPE_STYLUS`                  | 1             | InProgressStrokesView           | Draw stroke                   |
  | Finger (single) | `TOOL_TYPE_FINGER`                  | 1             | InProgressStrokesView           | Draw stroke (if pen mode off) |
  | Finger (multi)  | `TOOL_TYPE_FINGER`                  | 2+            | Compose detectTransformGestures | Zoom/pan                      |
  | Palm            | `TOOL_TYPE_FINGER` + palm rejection | any           | None (rejected)                 | Ignore                        |

  **Implementation Pattern:**

  ```kotlin
  /**
   * Touch event dispatcher that routes events based on tool type and pointer count.
   *
   * Key insight: Check tool type FIRST, then pointer count.
   * Stylus always goes to drawing, regardless of other pointers.
   */
  class InkInputRouter(
    private val inProgressStrokesView: InProgressStrokesView,
    private val onZoomPan: (zoom: Float, panX: Float, panY: Float) -> Unit
  ) {
    // Track active pointers for gesture detection
    private val activePointers = mutableMapOf<Int, Int>()  // pointerId -> toolType

    // Pointer IDs currently used for drawing
    private val drawingPointers = mutableSetOf<Int>()

    fun onTouchEvent(event: MotionEvent): Boolean {
      val pointerIndex = event.actionIndex
      val pointerId = event.getPointerId(pointerIndex)
      val toolType = event.getToolType(pointerIndex)

      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
          activePointers[pointerId] = toolType

          // Stylus ALWAYS goes to drawing
          if (toolType == MotionEvent.TOOL_TYPE_STYLUS) {
            drawingPointers.add(pointerId)
            return inProgressStrokesView.startStroke(event, pointerId, currentBrush)
          }

          // Finger: check if it's a multi-touch gesture
          val fingerCount = activePointers.values.count { it == MotionEvent.TOOL_TYPE_FINGER }
          if (fingerCount >= 2) {
            // Cancel any in-progress finger drawing, switch to zoom/pan
            drawingPointers.filter { activePointers[it] == MotionEvent.TOOL_TYPE_FINGER }
              .forEach { id ->
                inProgressStrokesView.cancelStroke(activeStrokeIds[id]!!)
                drawingPointers.remove(id)
              }
            return handleZoomPan(event)
          } else if (drawingPointers.isEmpty()) {
            // Single finger, no active drawing → start drawing
            drawingPointers.add(pointerId)
            return inProgressStrokesView.startStroke(event, pointerId, currentBrush)
          }
        }

        MotionEvent.ACTION_MOVE -> {
          // Route to appropriate handler based on current mode
          if (drawingPointers.contains(pointerId)) {
            return inProgressStrokesView.addToStroke(event, pointerId)
          } else if (activePointers.size >= 2) {
            return handleZoomPan(event)
          }
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
          val canceled = (event.flags and MotionEvent.FLAG_CANCELED) == MotionEvent.FLAG_CANCELED
          if (drawingPointers.contains(pointerId)) {
            drawingPointers.remove(pointerId)
            return if (canceled) {
              // Remove last motion set for this pointer and re-render
              inProgressStrokesView.cancelStroke(activeStrokeIds[pointerId]!!)
            } else {
              inProgressStrokesView.finishStroke(event, pointerId)
            }
          }
          activePointers.remove(pointerId)
        }

        MotionEvent.ACTION_CANCEL -> {
          // Cancel and remove affected in-progress strokes
          inProgressStrokesView.cancelUnfinishedStrokes()
          drawingPointers.clear()
          activePointers.clear()
        }
      }
      return false
    }

    private fun handleZoomPan(event: MotionEvent): Boolean {
      // Calculate pinch zoom and pan from multi-touch event
      // Delegate to Compose gesture handler via callback
      // ...
    }
  }
  ```

  **Zoom/Pan with Drawing Coexistence:**

  ```kotlin
  @Composable
  fun InkCanvas(
    strokes: List<Stroke>,
    viewTransform: ViewTransform,
    onViewTransformChange: (ViewTransform) -> Unit,
    modifier: Modifier = Modifier
  ) {
    var inProgressStrokesView by remember { mutableStateOf<InProgressStrokesView?>(null) }

    Box(
      modifier = modifier
        // Zoom/pan gesture detector (for multi-touch)
        .pointerInput(Unit) {
          detectTransformGestures { centroid, pan, zoom, rotation ->
            // Only process if not currently drawing
            if (inProgressStrokesView?.hasActiveStrokes() != true) {
              onViewTransformChange(
                viewTransform.copy(
                  zoom = (viewTransform.zoom * zoom).coerceIn(0.5f, 5f),
                  panX = viewTransform.panX + pan.x,
                  panY = viewTransform.panY + pan.y
                )
              )
            }
          }
        }
    ) {
      // Layer 2: Finished strokes
      Canvas(modifier = Modifier.fillMaxSize()) {
        strokes.forEach { stroke -> drawStroke(stroke, viewTransform) }
      }

      // Layer 1: In-progress strokes (uses InkInputRouter internally)
      AndroidView(
        factory = { context ->
          InProgressStrokesView(context).also { view ->
            inProgressStrokesView = view
            // Touch handling is done via InkInputRouter above
          }
        },
        modifier = Modifier.fillMaxSize()
      )
    }
  }
  ```

  **Pointer ID Tracking:**
  - Each `MotionEvent` can have multiple pointers (multi-touch)
  - `event.getPointerId(pointerIndex)` returns stable ID for duration of touch
  - Track which pointer IDs are "drawing" vs "gesturing"
  - When 2nd finger touches, cancel any finger drawing and switch to zoom/pan
  - Stylus strokes are NEVER cancelled by finger touches (stylus has priority)

  **Palm Rejection (Best Practice):**
  - Expect `ACTION_CANCEL` and `FLAG_CANCELED` for unintended touches.
  - Keep per-pointer motion-set history so canceled strokes can be removed and the scene re-rendered.
  - Filter palms by contact size when needed: `event.getSize(index) > PALM_THRESHOLD`.
  - Do not rely solely on hardware palm rejection.

  **Jetpack Ink API Reference (CRITICAL - Verified Sources):**

  > **Alpha API Warning**: Jetpack Ink is alpha (1.0.0-alpha01). API may change.
  > These references are from the androidx/androidx repository.

  **Primary Source Code:**
  - `InProgressStrokesView` class: [github.com/androidx/androidx/.../InProgressStrokesView.kt](https://github.com/androidx/androidx/blob/androidx-main/ink/ink-authoring/src/androidMain/kotlin/androidx/ink/authoring/InProgressStrokesView.kt)
  - Test Activity (usage example): [github.com/androidx/androidx/.../InProgressStrokesViewTestActivity.kt](https://github.com/androidx/androidx/blob/androidx-main/ink/ink-authoring/src/androidDeviceTest/kotlin/androidx/ink/authoring/InProgressStrokesViewTestActivity.kt)
  - Touch Handler pattern: [github.com/androidx/androidx/.../StrokeGestureCallback.kt](https://github.com/androidx/androidx/blob/androidx-main/ink/ink-authoring/src/androidMain/kotlin/androidx/ink/authoring/StrokeGestureCallback.kt)

  **Key API Methods (from InProgressStrokesView):**

  ```kotlin
  // Starting a stroke
  fun startStroke(event: MotionEvent, pointerId: Int, brush: Brush): InProgressStrokeId
  fun startStroke(input: StrokeInput, brush: Brush): InProgressStrokeId

  // Adding points to in-progress stroke
  fun addToStroke(event: MotionEvent, pointerId: Int): Boolean
  fun addToStroke(event: MotionEvent, pointerId: Int, strokeId: InProgressStrokeId)

  // Finishing a stroke
  fun finishStroke(event: MotionEvent, pointerId: Int): Boolean
  fun finishStroke(input: StrokeInput, strokeId: InProgressStrokeId)

  // Canceling strokes
  fun cancelStroke(strokeId: InProgressStrokeId)
  fun cancelUnfinishedStrokes()

  // Getting completed strokes
  fun getFinishedStrokes(): Map<InProgressStrokeId, Stroke>
  fun removeFinishedStrokes(strokeIds: Set<InProgressStrokeId>)

  // Listener for finished strokes
  fun addFinishedStrokesListener(listener: InProgressStrokesFinishedListener)
  ```

  **Integration Pattern (from Test Activity):**

  ```kotlin
  // From InProgressStrokesViewTestActivity.kt
  class MyActivity : Activity() {
    lateinit var inProgressStrokesView: InProgressStrokesView

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      inProgressStrokesView = InProgressStrokesView(this)
      setContentView(inProgressStrokesView)
    }
  }
  ```

  **Min SDK Requirement:**
  - Tests show `@SdkSuppress(minSdkVersion = 35)` - may require Android 15
  - Verify compatibility with target device before implementation

  **Verification**:
  - [ ] Canvas displays in Editor screen
  - [ ] Can draw with finger/stylus (basic lines appear during drawing)
  - [ ] After pen-up, stroke remains visible (transferred to finished layer)
  - [ ] After app restart, strokes are visible (loaded from database and rendered)

  **Commit**: YES
  - Message: `feat(android): add basic ink canvas with Jetpack Ink`

---

- [ ] 3.2a Ink API Fallback Decision (REQUIRED)

  **What to do**:

  > This task validates InProgressStrokesView compatibility and decides whether to implement fallback.
  > **Must be completed BEFORE proceeding to task 3.3** - blocking dependency.

  **Step 1: Compatibility Test (on target device):**

  ```kotlin
  // In InkApiCompatTest.kt (androidTest)
  // File: apps/android/app/src/androidTest/java/com/onyx/android/ink/InkApiCompatTest.kt

  import android.content.Context
  import android.view.MotionEvent
  import androidx.ink.authoring.InProgressStrokesView
  import androidx.ink.brush.Brush
  import androidx.ink.brush.StockBrushes
  import androidx.test.core.app.ActivityScenario
  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import org.junit.Assert.assertNotNull
  import org.junit.Test
  import org.junit.runner.RunWith

  /**
   * Test Activity for InProgressStrokesView compatibility testing.
   * Must be declared in androidTest/AndroidManifest.xml
   */
  class InkApiTestActivity : android.app.Activity()

  @RunWith(AndroidJUnit4::class)
  class InkApiCompatTest {

    // Test brush: Stock pen brush with default settings
    private val testBrush: Brush = StockBrushes.markerLatest.toBuilder()
      .setSize(5f)  // 5mm brush
      .setColor(0xFF000000.toInt())  // Black
      .build()

    @Test
    fun inProgressStrokesView_instantiates_and_renders() {
      val context = ApplicationProvider.getApplicationContext<Context>()

      // Test 1: Can instantiate without exception
      val view = InProgressStrokesView(context)
      assertNotNull(view)

      // Test 2: Can attach to activity and accept touch input
      val scenario = ActivityScenario.launch(InkApiTestActivity::class.java)
      scenario.onActivity { activity ->
        activity.setContentView(view)

        // Simulate touch event
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        val strokeId = view.startStroke(downEvent, 0, testBrush)

        // If we get here, InProgressStrokesView works
        assertNotNull(strokeId)

        view.cancelStroke(strokeId)
        downEvent.recycle()
      }
    }
  }
  ```

  **Required: AndroidManifest for test activity:**

  ```xml
  <!-- apps/android/app/src/androidTest/AndroidManifest.xml -->
  <manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
      <activity android:name="com.onyx.android.ink.InkApiTestActivity"
                android:exported="false" />
    </application>
  </manifest>
  ```

  **Step 2: Run test on target device:**

  ```bash
  # Run on connected device (must be Boox or target e-ink device)
  # Note: Module path is :apps:android:app (not :apps:android)
  ./gradlew :apps:android:app:connectedDebugAndroidTest --tests "*.InkApiCompatTest"
  ```

  **Step 3: Decision based on result:**

  | Test Result                                 | Action                                            |
  | ------------------------------------------- | ------------------------------------------------- |
  | **PASS**                                    | Skip fallback, proceed with InProgressStrokesView |
  | **FAIL** (SecurityException, API not found) | Implement LowLatencyInkView fallback (see below)  |
  | **FAIL** (rendering issues)                 | Implement LowLatencyInkView fallback (see below)  |

  **Step 4: If fallback needed, create `LowLatencyInkView.kt`:**
  - Copy implementation from Context section (line ~302-335)
  - Target: `apps/android/app/src/main/java/com/onyx/android/ink/ui/LowLatencyInkView.kt`
  - Replace InProgressStrokesView usage in InkCanvas with LowLatencyInkView
  - Maintain same public API (startStroke, addToStroke, finishStroke)

  **Acceptance Criteria (Concrete - Pass/Fail):**
  - [ ] `InkApiCompatTest` exists in androidTest source set
  - [ ] Test was executed on target device (log output captured)
  - [ ] Decision documented: "PASS - using InProgressStrokesView" OR "FAIL - implementing fallback"
  - [ ] If FAIL: `LowLatencyInkView.kt` exists and InkCanvas uses it
  - [ ] If FAIL: Drawing works on target device with fallback implementation
  - [ ] If PASS: Task 3.3 can proceed with InProgressStrokesView

  **Parallelizable**: NO (must complete before 3.3)

  **Commit**: YES
  - Message: `test(android): add Ink API compatibility test`
  - If fallback implemented: additional commit `feat(android): add LowLatencyInkView fallback for older APIs`

---

- [x] 3.2b Stylus event pipeline best practices

  **What to do**:
  - Use `pointerInteropFilter` for any Compose drawing surfaces that consume `MotionEvent`.
  - Filter input by tool type (`TOOL_TYPE_STYLUS`, `TOOL_TYPE_ERASER`) and map eraser tool to eraser mode.
  - Capture axis data: `AXIS_PRESSURE`, `AXIS_TILT`, `AXIS_ORIENTATION`, `AXIS_DISTANCE`.
  - Normalize pressure to `0..1` (clamp if device returns values > 1).
  - Implement hover preview when `AXIS_DISTANCE > 0` (brush size/position indicator).
  - Call `requestUnbufferedDispatch()` on `ACTION_DOWN` for stylus events to minimize batching.
  - Use front-buffer rendering only for active strokes; avoid full-screen panning/zooming on the front buffer.
  - Avoid allocations in the move path (reuse stroke point buffers, avoid lambdas/boxing).

  **Verification**:
  - [ ] Stylus + eraser inputs are correctly detected.
  - [ ] Pressure is normalized and logged in range `0..1`.
  - [ ] Hover shows a preview without touching the surface.

  **Commit**: YES
  - Message: `feat(ink): add stylus event best practices`

---

- [x] 3.2c Cancel + palm rejection handling

  **What to do**:
  - Track motion sets per pointer; keep a minimal history of points for cancellation.
  - On `ACTION_CANCEL` or when `FLAG_CANCELED` is set, remove the last motion set for that pointer and re-render.
  - Use contact size heuristics for palms (`event.getSize(index) > PALM_THRESHOLD`) when needed.

  **Verification**:
  - [ ] Cancel events remove the corresponding in-progress stroke.
  - [ ] Palm contact does not leave stray strokes.

  **Commit**: YES
  - Message: `feat(ink): handle cancel/palm rejection`

---

- [x] 3.2d Motion prediction (optional, recommended)

  **What to do**:
  - Use `MotionEventPredictor` to generate predicted points during `ACTION_MOVE`.
  - Render predicted points only in the front buffer; replace with real points on arrival.
  - Do not persist predicted points.

  **Verification**:
  - [ ] Predicted points reduce visible latency without affecting final strokes.

  **Commit**: YES
  - Message: `feat(ink): add motion prediction`

---

- [x] 3.2e Edge-to-edge + gesture nav safety

  **What to do**:
  - Enable edge-to-edge content and respect insets around the canvas.
  - Use `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
  - Ensure navigation gestures do not leave accidental strokes (pair with cancel handling).

  **Verification**:
  - [ ] Swiping system bars does not leave stray ink.

  **Commit**: YES
  - Message: `feat(ui): edge-to-edge + gesture nav safety`

---

- [x] 3.2f Zero‑flicker pen‑up commit

  **Goal**: When the user lifts the stylus, the stroke stays on screen with no flicker, gaps, or frame of disappearance.

  **What to do**:
  - On `ACTION_UP`, finish the in‑progress stroke and **immediately** add it to the in‑memory finished stroke list before clearing the front buffer.
  - Trigger a finished‑layer redraw in the same frame (or next vsync at most).
  - Persist to DB asynchronously, but **do not wait** for DB before rendering.
  - Keep finished stroke rendering in a stable, double‑buffered layer (Compose Canvas or SurfaceView).
  - If using the fallback renderer, call `commit()` on pen‑up and immediately redraw the finished layer.

  **Implementation notes**:
  - Front buffer renders only in‑progress strokes.
  - Finished layer always contains the authoritative stroke list.
  - Update the stroke list immutably to force redraw (`strokes = strokes + newStroke`).
  - Avoid any “clear” that removes the in‑progress layer before the finished layer draws.

  **Verification**:
  - [ ] Rapid pen-up/down does not cause a visible flicker.
  - [ ] No frame where the stroke disappears after pen-up.

  **Commit**: YES
  - Message: `feat(ink): zero-flicker pen-up commit`

---

- [x] 3.3 Implement stroke capture with pressure/tilt

  **What to do**:
  - Capture MotionEvent properties:
    - `getPressure()` → `StrokePoint.p`
    - `getAxisValue(MotionEvent.AXIS_TILT)` → `StrokePoint.tx`
    - `getAxisValue(MotionEvent.AXIS_ORIENTATION)` → `StrokePoint.r`
  - Map to StrokePoint fields (see mapping below)
  - Build Stroke on ACTION_UP
  - **Compute StrokeBounds** on stroke completion (see below)

  **Tilt/Orientation to StrokePoint Field Mapping**:

  ```kotlin
  // MotionEvent axis values and their mapping to StrokePoint fields:
  //
  // getPressure() → p (0.0 to 1.0, normalized pressure; clamp if > 1.0)
  //
  // AXIS_TILT → tx (tilt angle in radians, 0 = perpendicular to screen)
  //   - AXIS_TILT is the angle between the stylus axis and the perpendicular to the screen
  //   - Range: 0 (perpendicular) to PI/2 (parallel to screen)
  //   - We store directly as tx (tilt X approximation)
  //
  // AXIS_ORIENTATION → r (rotation/azimuth in radians)
  //   - AXIS_ORIENTATION is the angle around the perpendicular axis (clockwise from 12 o'clock)
  //   - Range: -PI to PI radians
  //   - We store directly as r (rotation)
  //
  // ty (tilt Y) → null for v1
  //   - Would require decomposing AXIS_TILT with AXIS_ORIENTATION into X/Y components
  //   - Not needed for Plan A; can be added in Av2 if nib simulation requires it

  fun captureStrokePoint(event: MotionEvent, pointerIndex: Int): StrokePoint {
    // Convert screen coordinates to page coordinates
    val (pageX, pageY) = screenToPage(
      event.getX(pointerIndex),
      event.getY(pointerIndex)
    )

    return StrokePoint(
      x = pageX,
      y = pageY,
      t = event.eventTime,                                    // timestamp in ms
      p = event.getPressure(pointerIndex).takeIf { it > 0 },  // null if no pressure
      tx = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
            .takeIf { it > 0 },                               // null if no tilt
      ty = null,                                              // not captured in v1
      r = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)
            .takeIf { it != 0f }                              // null if no orientation
    )
  }
  ```

  **StrokeBounds Computation** (on stroke completion):

  ```kotlin
  // Compute bounding box from stroke points:
  fun computeBounds(points: List<StrokePoint>): StrokeBounds {
    if (points.isEmpty()) return StrokeBounds(0f, 0f, 0f, 0f)

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    for (point in points) {
      if (point.x < minX) minX = point.x
      if (point.y < minY) minY = point.y
      if (point.x > maxX) maxX = point.x
      if (point.y > maxY) maxY = point.y
    }

    return StrokeBounds(
      x = minX,
      y = minY,
      w = maxX - minX,
      h = maxY - minY
    )
  }

  // Called on ACTION_UP to finalize stroke:
  fun onStrokeComplete(points: List<StrokePoint>, style: StrokeStyle): Stroke {
    return Stroke(
      id = UUID.randomUUID().toString(),
      points = points,
      style = style,
      bounds = computeBounds(points),  // Compute here
      createdAt = System.currentTimeMillis(),
      createdLamport = 0  // Plan A always 0
    )
  }
  ```

  **References**:
  - Stylus input: https://developer.android.com/develop/ui/compose/touch-input/stylus-input/use-stylus-input
  - MotionEvent AXIS values: https://developer.android.com/reference/android/view/MotionEvent#AXIS_TILT

  **Verification**:
  - [ ] Draw with stylus on tablet
  - [ ] Log shows pressure values vary with force (0.1 light → 0.9 heavy)
  - [ ] Log shows tilt values when tilting stylus (0 perpendicular → ~1.0 tilted)
  - [ ] Log shows orientation values when rotating stylus (-PI to PI)
  - [ ] Log shows computed bounds: `{x: minX, y: minY, w: width, h: height}`
  - [ ] Bounds correctly encompass all stroke points

  **Commit**: Group with 3.4-3.5

---

- [x] 3.4 Implement pen tool with variable width

  **What to do**:
  - Use `StockBrushes.pressurePen()` as the BrushFamily (automatically varies width with pressure)
  - Pass pressure via `StrokeInput.create(..., pressure = event.pressure)`
  - The BrushFamily internally maps pressure to width variation

  **Pressure-to-Width API (Jetpack Ink):**

  ```kotlin
  // 1. Create brush with pressure-sensitive family
  val brush = Brush.createWithColorIntArgb(
    family = StockBrushes.pressurePen(),  // <-- Key: pressure-sensitive family
    colorIntArgb = Color.BLACK,
    size = 4f,        // Base size at pressure=0.5
    epsilon = 0.1f
  )

  // 2. Pass pressure in StrokeInput
  val input = StrokeInput.create(
    x = event.x,
    y = event.y,
    elapsedTimeMillis = event.eventTime - event.downTime,
    toolType = InputToolType.STYLUS,
    pressure = event.pressure.coerceIn(0f, 1f)  // <-- Key: pressure from hardware
  )

  // 3. InProgressStrokesView uses brush + input to render variable-width stroke
  inProgressStrokesView.startStroke(input, pointerId, brush)
  ```

  **How Pressure Affects Width:**
  - BrushFamily handles the mapping internally
  - `pressurePen()` increases width with pressure
  - Light pressure → thinner line
  - Heavy pressure → thicker line
  - Base `size` in Brush is the width at medium pressure (~0.5)

  **Reference:**
  - `StockBrushes.pressurePen()`: https://github.com/androidx/androidx/blob/androidx-main/ink/ink-brush/src/jvmAndAndroidMain/kotlin/androidx/ink/brush/StockBrushes.kt
  - `StrokeInput.create(pressure=)`: https://github.com/androidx/androidx/blob/androidx-main/ink/ink-strokes/src/jvmTest/kotlin/androidx/ink/strokes/StrokeInputTest.kt#L86-L99
  - `Brush` class with pressure behaviors: https://github.com/androidx/androidx/blob/androidx-main/ink/ink-brush/src/jvmAndAndroidMain/kotlin/androidx/ink/brush/Brush.kt

  **Verification**:
  - [ ] Light pressure (p < 0.3) = visibly thinner line than heavy pressure
  - [ ] Heavy pressure (p > 0.7) = visibly thicker line
  - [ ] Draw circle → No visible jagged edges at 1x zoom
  - [ ] Manual visual inspection confirms smooth curves

  **Commit**: Group with 3.3, 3.5

---

- [x] 3.5 Implement brush color and size picker

  **What to do**:
  - Add toolbar with color palette (5-10 colors)
  - Add size slider or preset sizes
  - Update Brush state when changed

  **Verification**:
  - [ ] Can change ink color
  - [ ] Can change ink size
  - [ ] New strokes use selected brush

  **Commit**: YES
  - Message: `feat(android): implement stroke capture with pressure and brush picker`

---

- [x] 3.6 Implement stroke eraser tool

  **What to do**:
  - Add eraser button to toolbar
  - When eraser active, detect stroke intersection
  - Remove entire stroke on touch

  **Eraser Hit-Testing Algorithm:**

  ```kotlin
  // Screen-to-page coordinate conversion (uses shared ViewTransform):
  fun screenToPage(screenX: Float, screenY: Float): Pair<Float, Float> {
    val pageX = (screenX - viewTransform.panX) / viewTransform.zoom
    val pageY = (screenY - viewTransform.panY) / viewTransform.zoom
    return Pair(pageX, pageY)
  }

  // Hit-testing algorithm:
  // 1. Convert eraser touch point from screen to page coordinates
  // 2. Check each stroke's bounding box first (fast rejection)
  // 3. For strokes with overlapping bounds, check point-to-segment distance

  fun findStrokeToErase(screenX: Float, screenY: Float): Stroke? {
    val (pageX, pageY) = screenToPage(screenX, screenY)
    val hitRadius = 10f / viewTransform.zoom  // 10 screen pixels, scaled to page coords

    for (stroke in strokes) {
      // Fast rejection: expand bounds by hitRadius
      val expandedBounds = stroke.bounds.expand(hitRadius)
      if (!expandedBounds.contains(pageX, pageY)) continue

      // Detailed check: point-to-polyline distance
      for (i in 0 until stroke.points.size - 1) {
        val p1 = stroke.points[i]
        val p2 = stroke.points[i + 1]
        val dist = pointToSegmentDistance(pageX, pageY, p1.x, p1.y, p2.x, p2.y)
        if (dist <= hitRadius) {
          return stroke  // Hit! Return first matching stroke
        }
      }
    }
    return null
  }

  // Point-to-segment distance (standard algorithm):
  fun pointToSegmentDistance(px: Float, py: Float,
                             x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val t = maxOf(0f, minOf(1f, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
    val nearestX = x1 + t * dx
    val nearestY = y1 + t * dy
    return sqrt((px - nearestX).pow(2) + (py - nearestY).pow(2))
  }
  ```

  **Constants:**
  - Hit radius: 10 screen pixels (scaled by zoom for page coordinates)
  - Algorithm: Point-to-polyline distance with bounds pre-filtering

  **Verification**:
  - [ ] Can toggle eraser mode (eraser button changes visual state)
  - [ ] Touching a stroke removes it entirely (within 10px radius)
  - [ ] Touching empty area does nothing
  - [ ] Can switch back to pen
  - [ ] Erased stroke is removed from strokes list and database

  **Commit**: YES
  - Message: `feat(android): implement stroke eraser tool`

---

- [x] 3.7 Implement undo/redo

  **What to do**:
  - Maintain undo stack (list of actions)
  - Maintain redo stack
  - Add undo/redo buttons to toolbar

  **Undo/Redo Semantics:**

  ```kotlin
  // Action types for undo/redo:
  sealed class InkAction {
    data class AddStroke(val stroke: Stroke) : InkAction()
    data class RemoveStroke(val stroke: Stroke) : InkAction()
  }

  // Undo/redo stacks (in-memory, not persisted):
  private val undoStack = mutableListOf<InkAction>()
  private val redoStack = mutableListOf<InkAction>()

  // When stroke is drawn:
  fun onStrokeCompleted(stroke: Stroke) {
    undoStack.add(InkAction.AddStroke(stroke))
    redoStack.clear()  // New action clears redo stack
    repository.saveStroke(stroke)  // Persist immediately
    triggerRecognition(stroke)     // Update recognition
  }

  // When stroke is erased:
  fun onStrokeErased(stroke: Stroke) {
    undoStack.add(InkAction.RemoveStroke(stroke))
    redoStack.clear()
    repository.deleteStroke(stroke.id)
    triggerRecognition()  // Re-run recognition for page
  }

  // Undo:
  fun undo(): Boolean {
    val action = undoStack.removeLastOrNull() ?: return false
    when (action) {
      is InkAction.AddStroke -> {
        repository.deleteStroke(action.stroke.id)  // Remove from DB
        strokes.remove(action.stroke)              // Remove from memory
      }
      is InkAction.RemoveStroke -> {
        repository.saveStroke(action.stroke)       // Re-add to DB
        strokes.add(action.stroke)                 // Re-add to memory
      }
    }
    redoStack.add(action)
    updatePageTimestamp()      // Update page.updatedAt
    triggerRecognition()       // Recompute recognition
    return true
  }

  // Redo: (inverse of undo)
  fun redo(): Boolean {
    val action = redoStack.removeLastOrNull() ?: return false
    when (action) {
      is InkAction.AddStroke -> {
        repository.saveStroke(action.stroke)
        strokes.add(action.stroke)
      }
      is InkAction.RemoveStroke -> {
        repository.deleteStroke(action.stroke.id)
        strokes.remove(action.stroke)
      }
    }
    undoStack.add(action)
    updatePageTimestamp()
    triggerRecognition()
    return true
  }

  // Timestamp update helper:
  fun updatePageTimestamp() {
    val now = System.currentTimeMillis()
    repository.updatePage(currentPageId, updatedAt = now)
    repository.updateNote(currentNoteId, updatedAt = now)
  }
  ```

  **Persistence Rules:**
  - Strokes are persisted immediately on draw/erase (not batched)
  - Undo/redo modify database in real-time (no deferred writes)
  - `page.updatedAt` and `note.updatedAt` are updated on each undo/redo
  - `page.contentLamportMax` is NOT updated in Plan A (Lamport clocks start in Plan C)
  - Recognition index is recomputed on each undo/redo (full page re-recognition)

  **Undo Stack Limits:**
  - Max 50 actions per page (older actions discarded)
  - Stack is cleared on page navigation (not persisted across pages)

  **Verification**:
  - [ ] Draw stroke → Undo → Stroke disappears from screen AND database
  - [ ] Redo → Stroke reappears on screen AND in database
  - [ ] Multiple undo/redo works (5+ levels)
  - [ ] Undo after erase → Erased stroke reappears
  - [ ] New stroke after undo → Redo stack is cleared
  - [ ] `note.updatedAt` changes after undo/redo

  **Recognition Updates on Undo/Redo (Verifiable Acceptance Criteria):**
  - [ ] Draw "hello" → Wait for recognition → Undo stroke → Recognition database updates within 1s:
    - Query: `SELECT recognizedText FROM recognition_index WHERE pageId = ?`
    - Before undo: Contains "hello" or similar
    - After undo: Empty or null (stroke removed)
  - [ ] Redo the stroke → Recognition database restores within 1s:
    - After redo: Contains "hello" again
  - [ ] Draw "A", draw "B", undo once → Recognition shows only "A":
    - Partial undo correctly updates recognition text
  - [ ] Draw "cat" → Erase stroke → Undo erase → Recognition shows "cat" again:
    - Undo of erase triggers recognition re-feed
  - [ ] Rapid undo/redo (5 times in 2s) → No crashes, final recognition state is consistent:
    - Debounce handles rapid operations gracefully

  **Commit**: YES
  - Message: `feat(android): implement undo/redo for strokes`

---

- [x] 3.8 Implement zoom and pan

  **What to do**:
  - Add pinch-to-zoom gesture handling
  - Add pan/scroll gestures
  - Maintain canvas transform matrix

  **References**:
  - Compose gestures: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures

  **Verification**:
  - [ ] Two-finger pinch zooms canvas
  - [ ] Two-finger drag pans canvas
  - [ ] Strokes remain in correct position after zoom/pan

  **Commit**: YES
  - Message: `feat(android): implement zoom and pan for ink canvas`

---

### Phase 4: Persistence

**Task Dependencies (Execution Order):**
| Task | Depends On | Notes |
|------|------------|-------|
| 4.1 | - | Room dependencies |
| 4.2-4.5a | 4.1 | Entity definitions (can be parallel) |
| 4.6 | 4.2-4.5a | DAOs reference entities |
| 4.7 | 4.6 | Database class references DAOs |
| 4.8 | 4.2 | Serializer uses Stroke/StrokePoint types |
| 4.11 | - | DeviceIdentity (no dependencies) |
| 4.9 | 4.6, 4.7, 4.8, **4.11** | Repository uses DAOs + DeviceIdentity |
| 4.12 | 4.7, 4.9, 4.11 | OnyxApplication wires all singletons |
| 4.10 | 4.9, **4.12** | Editor integration uses Repository via OnyxApplication |

**Recommended order**: 4.1 → 4.11 → 4.2-4.5a → 4.6 → 4.7 → 4.8 → 4.9 → **4.12** → 4.10

- [x] 4.1 Add Room database dependencies

  **What to do**:
  - Add to `apps/android/app/build.gradle.kts`:

    ```kotlin
    plugins {
      id("com.google.devtools.ksp")  // Already added in task 1.5
    }

    // Room schema export location (for migration tracking):
    ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
    }

    dependencies {
      implementation("androidx.room:room-runtime:2.6.1")
      implementation("androidx.room:room-ktx:2.6.1")
      ksp("androidx.room:room-compiler:2.6.1")
    }
    ```

  - This generates `apps/android/app/schemas/1.json` when database version 1 is built
  - Add `schemas/` to `.gitignore` or commit for version control (recommended: commit)

  **Verification**:
  - [ ] Dependencies sync successfully: `./gradlew sync`
  - [ ] After first build: `ls apps/android/app/schemas/` shows `1.json`

  **Commit**: Group with 4.2-4.5

---

- [x] 4.2 Define NoteEntity (sync-compatible)

  **What to do**:
  - Create `data/entity/NoteEntity.kt`:
    ```kotlin
    @Entity(tableName = "notes")
    data class NoteEntity(
      @PrimaryKey val noteId: String,  // UUID.randomUUID().toString()
      val ownerUserId: String,         // DeviceIdentity.getDeviceId() - see Entity Field Semantics
      val title: String,               // "" initially, user-editable
      val createdAt: Long,             // System.currentTimeMillis()
      val updatedAt: Long,             // System.currentTimeMillis(), update on any change
      val deletedAt: Long? = null      // null = active, non-null = soft-deleted
    )
    ```

  **Field Semantics** (see Key Constants & Defaults section):
  - `noteId`: Generated via `java.util.UUID.randomUUID().toString()` on creation
  - `ownerUserId`: Set to device UUID from DeviceIdentity (task 4.11) until user auth in Plan C
  - `title`: Empty string `""` by default; user can edit in UI; future: auto-set from first recognition line
  - `createdAt`: Set once at creation, never changes
  - `updatedAt`: Updated whenever note or its pages/strokes change
  - `deletedAt`: null for active notes; set to timestamp for soft delete (allows undo delete)

  **Verification**:
  - [ ] Entity compiles
  - [ ] Schema matches v0 API
  - [ ] noteId is String type (not Long/Int)
  - [ ] title defaults to empty string

  **Commit**: Group with 4.1, 4.3-4.5

---

- [x] 4.3 Define PageEntity (sync-compatible)

  **What to do**:
  - Create `data/entity/PageEntity.kt`:
    ```kotlin
    @Entity(tableName = "pages")
    data class PageEntity(
      @PrimaryKey val pageId: String,  // UUID string
      val noteId: String,
      val kind: String,                // "ink" | "pdf" | "mixed" | "infinite" (matches v0 API NoteKind)
      val geometryKind: String,        // "fixed" | "infinite" (matches v0 API PageGeometry.kind)
      val indexInNote: Int,
      val width: Float,                // page width in units (for fixed) or tile size (for infinite)
      val height: Float,               // page height in units (for fixed) or tile size (for infinite)
      val unit: String = "pt",         // "pt" for fixed, "px" for infinite
      val pdfAssetId: String? = null,
      val pdfPageNo: Int? = null,
      val updatedAt: Long,
      val contentLamportMax: Long = 0
    )
    ```
  - **kind values**: Use "ink" (not "page") per v0 API `NoteKind` definition
  - **geometryKind values**: "fixed" for standard pages, "infinite" for infinite canvas (Av2)
    - "fixed": width/height define page bounds
    - "infinite": width/height define tile size for rendering
  - **geometry fields**: width, height, unit for PageGeometry support

  **References**:
  - `V0-api.md:47` - NoteKind values: ink, pdf, mixed, infinite
  - `V0-api.md:49-51` - PageGeometry structure with `kind: "fixed" | "infinite"`
  - `V0-api.md:93-100` - PageMeta with geometry

  **Verification**:
  - [ ] Entity compiles
  - [ ] kind field accepts: "ink", "pdf", "mixed", "infinite"
  - [ ] geometryKind field accepts: "fixed", "infinite"
  - [ ] Geometry fields (width, height, unit) are present

  **Commit**: Group with 4.1-4.5

---

- [x] 4.4 Define StrokeEntity with blob serialization

  **What to do**:
  - Create `data/entity/StrokeEntity.kt`:
    ```kotlin
    @Entity(tableName = "strokes")
    data class StrokeEntity(
      @PrimaryKey val strokeId: String,  // UUID string
      val pageId: String,
      val strokeData: ByteArray,         // JSON serialized points (see 4.8)
      val style: String,                 // JSON: StrokeStyle (tool, color, baseWidth, factors)
      val bounds: String,                // JSON: { x, y, w, h }
      val createdAt: Long,               // Unix ms
      val createdLamport: Long = 0       // For segment eraser in Av2/C (createdLamport <= eraseLamport guard)
    )
    ```
  - Create TypeConverters for ByteArray
  - **createdLamport**: Required for segment eraser collaboration safety in Av2/C
    - When syncing, AreaErase ops only affect strokes where `createdLamport <= eraseLamport`

  **References**:
  - `V0-api.md:147-154` - StrokeAdd op with createdLamport
  - `.sisyphus/plans/milestone-av2-advanced-features.md:16-17` - segment eraser Lamport guard

  **Verification**:
  - [ ] Entity compiles
  - [ ] All fields present: strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport

  **Commit**: Group with 4.1-4.5

---

- [x] 4.5 Define RecognitionIndexEntity

  **What to do**:
  - Create `data/entity/RecognitionIndexEntity.kt`:
    ```kotlin
    @Entity(tableName = "recognition_index")
    data class RecognitionIndexEntity(
      @PrimaryKey val pageId: String,
      val noteId: String,
      val recognizedText: String? = null,
      val recognizedAtLamport: Long? = null,
      val recognizerVersion: String? = null,
      val updatedAt: Long
    )
    ```

  **Verification**:
  - [ ] Entity compiles

  **Commit**: Group with 4.5a

---

- [x] 4.5a Define RecognitionFtsEntity (FTS table)

  **What to do**:
  - Create `data/entity/RecognitionFtsEntity.kt`:
    ```kotlin
    /**
     * FTS4 table for full-text search on recognized text.
     *
     * Room FTS Content Sync: The @Fts4(contentEntity) annotation creates an FTS table
     * that automatically syncs with RecognitionIndexEntity. Room generates triggers
     * to keep the FTS index updated when RecognitionIndexEntity rows are inserted/updated/deleted.
     *
     * The FTS table's rowid automatically maps to the content entity's rowid,
     * allowing JOIN queries to link back to the source entity (and its pageId/noteId).
     */
    @Fts4(contentEntity = RecognitionIndexEntity::class)
    @Entity(tableName = "recognition_fts")
    data class RecognitionFtsEntity(
      val recognizedText: String
    )
    ```

  **Note**: This entity MUST be defined before OnyxDatabase (task 4.7) since OnyxDatabase includes it in the entities list.

  **Verification**:
  - [ ] Entity compiles
  - [ ] `@Fts4(contentEntity = ...)` annotation references RecognitionIndexEntity

  **Commit**: YES
  - Message: `feat(android): define Room entities for notes, pages, strokes`

---

- [x] 4.6 Create DAOs with Flow queries

  **What to do**:
  - Create `NoteDao`, `PageDao`, `StrokeDao`, `RecognitionDao`
  - Use Flow for reactive queries
  - Include ALL methods required by NoteRepository (task 4.9)

  **Complete DAO Specifications:**

  ```kotlin
  @Dao
  interface NoteDao {
    // === Queries ===
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE noteId = :noteId")
    suspend fun getById(noteId: String): NoteEntity?

    // === Inserts ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    // === Updates ===
    @Query("UPDATE notes SET updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateTimestamp(noteId: String, updatedAt: Long)

    @Query("UPDATE notes SET title = :title, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateTitle(noteId: String, title: String, updatedAt: Long)

    // === Deletes ===
    @Query("UPDATE notes SET deletedAt = :timestamp WHERE noteId = :noteId")
    suspend fun softDelete(noteId: String, timestamp: Long)
  }

  @Dao
  interface PageDao {
    // === Queries ===
    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY indexInNote ASC")
    fun getPagesForNote(noteId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE pageId = :pageId")
    suspend fun getById(pageId: String): PageEntity?

    @Query("SELECT MAX(indexInNote) FROM pages WHERE noteId = :noteId")
    suspend fun getMaxIndexForNote(noteId: String): Int?

    // === Inserts ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity)

    // === Updates ===
    @Query("UPDATE pages SET updatedAt = :updatedAt WHERE pageId = :pageId")
    suspend fun updateTimestamp(pageId: String, updatedAt: Long)

    @Query("UPDATE pages SET kind = :kind, updatedAt = :updatedAt WHERE pageId = :pageId")
    suspend fun updateKind(pageId: String, kind: String, updatedAt: Long = System.currentTimeMillis())

    // === Deletes ===
    @Query("DELETE FROM pages WHERE pageId = :pageId")
    suspend fun delete(pageId: String)
  }

  @Dao
  interface StrokeDao {
    // === Queries ===
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    suspend fun getByPageId(pageId: String): List<StrokeEntity>

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    fun getByPageIdFlow(pageId: String): Flow<List<StrokeEntity>>

    @Query("SELECT * FROM strokes WHERE strokeId = :strokeId")
    suspend fun getById(strokeId: String): StrokeEntity?

    // === Inserts ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stroke: StrokeEntity)

    // === Deletes ===
    @Query("DELETE FROM strokes WHERE strokeId = :strokeId")
    suspend fun delete(strokeId: String)

    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun deleteAllForPage(pageId: String)
  }

  @Dao
  interface RecognitionDao {
    // === Queries ===
    @Query("SELECT * FROM recognition_index WHERE pageId = :pageId")
    suspend fun getByPageId(pageId: String): RecognitionIndexEntity?

    // === Search (FTS) ===
    @Query("""
      SELECT ri.* FROM recognition_index ri
      INNER JOIN recognition_fts fts ON ri.rowid = fts.docid
      WHERE fts.recognizedText MATCH :query
    """)
    fun search(query: String): Flow<List<RecognitionIndexEntity>>

    // === Inserts ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recognition: RecognitionIndexEntity)

    // === Updates ===
    @Query("""
      UPDATE recognition_index
      SET recognizedText = :text,
          recognizerVersion = :version,
          updatedAt = :updatedAt
      WHERE pageId = :pageId
    """)
    suspend fun updateRecognition(
      pageId: String,
      text: String?,
      version: String?,
      updatedAt: Long
    )

    // === Deletes ===
    @Query("DELETE FROM recognition_index WHERE pageId = :pageId")
    suspend fun deleteByPageId(pageId: String)
  }
  ```

  **Method Requirements Summary:**
  | DAO | Required Methods | Used By |
  |-----|-----------------|---------|
  | NoteDao | `getById`, `insert`, `updateTimestamp`, `softDelete`, `getAllNotes` | Repository, Home screen |
  | PageDao | `getById`, `insert`, `updateTimestamp`, `updateKind`, `getPagesForNote`, `delete` | Repository, Editor, PDF import |
  | StrokeDao | `getById`, `getByPageId`, `insert`, `delete`, `deleteAllForPage` | Repository, Ink canvas |
  | RecognitionDao | `getByPageId`, `insert`, `updateRecognition`, `search`, `deleteByPageId` | Repository, Search |

  **Verification**:
  - [ ] All 4 DAOs compile without errors
  - [ ] All methods listed in NoteRepository (task 4.9) have corresponding DAO methods
  - [ ] Flow queries return correct reactive types

  **Commit**: Group with 4.7

---

- [x] 4.7 Create Room Database class

  **What to do**:
  - Create `data/OnyxDatabase.kt`:

    ```kotlin
    @Database(
      entities = [
        NoteEntity::class,
        PageEntity::class,
        StrokeEntity::class,
        RecognitionIndexEntity::class,
        RecognitionFtsEntity::class  // FTS table included from version 1
      ],
      version = 1,
      exportSchema = true  // Generate schema JSON for version control
    )
    @TypeConverters(Converters::class)
    abstract class OnyxDatabase : RoomDatabase() {
      abstract fun noteDao(): NoteDao
      abstract fun pageDao(): PageDao
      abstract fun strokeDao(): StrokeDao
      abstract fun recognitionDao(): RecognitionDao

      companion object {
        const val DATABASE_NAME = "onyx_notes.db"

        fun build(context: Context): OnyxDatabase {
          return Room.databaseBuilder(context, OnyxDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()  // OK for v1 development
            .build()
        }
      }
    }

    // TypeConverters for custom types:
    class Converters {
      @TypeConverter
      fun byteArrayToString(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.DEFAULT)

      @TypeConverter
      fun stringToByteArray(str: String): ByteArray = Base64.decode(str, Base64.DEFAULT)
    }
    ```

  **Note**: FTS table (`RecognitionFtsEntity`) is included in `entities` list from version 1 to avoid migration issues. See "Room Database Schema & Migration Strategy" in Context section.

  **Verification**:
  - [ ] Database compiles
  - [ ] Room generates implementation
  - [ ] Schema includes all 5 tables (notes, pages, strokes, recognition_index, recognition_fts)
  - [ ] `schemas/1.json` is generated (with `exportSchema = true`)

  **Commit**: YES
  - Message: `feat(android): create Room database with DAOs`

---

- [x] 4.8 Implement stroke serialization (JSON format)

  **What to do**:
  - Create `data/serialization/StrokeSerializer.kt`
  - **Format**: JSON (v0 decision - Protocol Buffers reserved for future)
  - Use kotlinx.serialization for JSON encoding/decoding
  - Serialize StrokePoint list to JSON string, then to ByteArray:

    ```kotlin
    // JSON Schema for points array (with explicitNulls = false):
    // [{"x":0.0,"y":0.0,"t":123456789,"p":0.5}, ...]  // nulls omitted
    // Not: [{"x":0.0,"y":0.0,"t":123456789,"p":0.5,"tx":null,"ty":null,"r":null}]

    @Serializable
    data class SerializablePoint(
      val x: Float,
      val y: Float,
      val t: Long,
      val p: Float? = null,
      val tx: Float? = null,
      val ty: Float? = null,
      val r: Float? = null
    )

    object StrokeSerializer {
      // JSON config:
      // - ignoreUnknownKeys: forward compatibility
      // - encodeDefaults = false: don't write default values
      // - explicitNulls = false: omit null fields entirely (not "null")
      private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false  // Key: null fields are omitted, not written as "null"
      }

      fun serializePoints(points: List<StrokePoint>): ByteArray {
        val serializablePoints = points.map { it.toSerializable() }
        return json.encodeToString(serializablePoints).toByteArray(Charsets.UTF_8)
      }

      fun deserializePoints(data: ByteArray): List<StrokePoint> {
        val serializablePoints: List<SerializablePoint> =
          json.decodeFromString(String(data, Charsets.UTF_8))
        return serializablePoints.map { it.toStrokePoint() }
      }

      fun serializeStyle(style: StrokeStyle): String = json.encodeToString(style)
      fun deserializeStyle(data: String): StrokeStyle = json.decodeFromString(data)

      fun serializeBounds(bounds: StrokeBounds): String = json.encodeToString(bounds)
      fun deserializeBounds(data: String): StrokeBounds = json.decodeFromString(data)
    }
    ```

  - **Store raw points + style** (not smoothed curves)
  - This allows smoothing/tuning to evolve without data migration
  - Field names MUST match v0 API exactly for sync compatibility

  **References**:
  - `V0-api.md:136-144` - Point field names for JSON keys (x, y, t, p, tx, ty, r)
  - `V0-api.md:127-134` - StrokeStyle field names
  - `V0-api.md:153` - bounds format (x, y, w, h)

  **Verification**:
  - [ ] Unit test: serialize 100 points → deserialize → exact same data
  - [ ] Unit test: JSON output contains fields: x, y, t (required), p, tx, ty, r (optional)
  - [ ] Unit test: style JSON contains: tool, color, baseWidth, minWidthFactor, maxWidthFactor
  - [ ] Unit test: bounds JSON contains: x, y, w, h
  - [ ] Unit test: null optional fields are omitted from JSON (not "null")
  - [ ] `./gradlew :app:test --tests "*StrokeSerializerTest*"` passes (from `apps/android/`)

  **Commit**: Group with 4.9

---

- [x] 4.9 Create Repository pattern

  **What to do**:
  - Create `data/repository/NoteRepository.kt`
  - Wrap DAOs with business logic
  - Handle stroke serialization/deserialization
  - Implement CRUD operations for notes, pages, strokes

  **Prerequisites**:
  - Task 4.11 (DeviceIdentity) must be completed first - repository constructor requires `deviceIdentity` parameter

  **Repository Responsibilities (Detailed):**

  ```kotlin
  class NoteRepository(
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val strokeDao: StrokeDao,
    private val recognitionDao: RecognitionDao,
    private val deviceIdentity: DeviceIdentity,
    private val strokeSerializer: StrokeSerializer
  ) {

    // === Note Creation ===
    suspend fun createNote(): NoteWithFirstPage {
      val now = System.currentTimeMillis()
      val note = NoteEntity(
        noteId = UUID.randomUUID().toString(),
        ownerUserId = deviceIdentity.getDeviceId(),  // Device UUID until Plan C auth
        title = "",  // Empty, user can edit later
        createdAt = now,
        updatedAt = now,
        deletedAt = null
      )
      noteDao.insert(note)

      // Create first page automatically:
      val firstPage = createPageForNote(note.noteId, indexInNote = 0)
      return NoteWithFirstPage(note, firstPage.pageId)
    }

    /**
     * Return type for createNote() - includes first page ID for immediate access.
     * Used by PDF import to delete the blank first page before adding PDF pages.
     */
    data class NoteWithFirstPage(
      val note: NoteEntity,
      val firstPageId: String
    )

    // === Page Creation ===
    suspend fun createPageForNote(noteId: String, indexInNote: Int): PageEntity {
      val now = System.currentTimeMillis()
      val page = PageEntity(
        pageId = UUID.randomUUID().toString(),
        noteId = noteId,
        kind = "ink",          // Default for new pages
        geometryKind = "fixed",
        indexInNote = indexInNote,
        width = 612f,          // US Letter width in pt (8.5 inches * 72)
        height = 792f,         // US Letter height in pt (11 inches * 72)
        unit = "pt",
        pdfAssetId = null,
        pdfPageNo = null,
        updatedAt = now,
        contentLamportMax = 0  // Lamport clocks start in Plan C
      )
      pageDao.insert(page)

      // Initialize recognition index for this page:
      recognitionDao.insert(RecognitionIndexEntity(
        pageId = page.pageId,
        noteId = noteId,
        recognizedText = null,
        recognizedAtLamport = null,
        recognizerVersion = null,
        updatedAt = now
      ))

      // Update note timestamp:
      noteDao.updateTimestamp(noteId, now)
      return page
    }

    // === Page from PDF ===
    suspend fun createPageFromPdf(
      noteId: String,
      indexInNote: Int,
      pdfAssetId: String,
      pdfPageNo: Int,
      pdfWidth: Float,  // From MuPDF page.bounds
      pdfHeight: Float
    ): PageEntity {
      val now = System.currentTimeMillis()
      val page = PageEntity(
        pageId = UUID.randomUUID().toString(),
        noteId = noteId,
        kind = "pdf",          // PDF-backed page
        geometryKind = "fixed",
        indexInNote = indexInNote,
        width = pdfWidth,      // From PDF mediaBox
        height = pdfHeight,
        unit = "pt",
        pdfAssetId = pdfAssetId,
        pdfPageNo = pdfPageNo,
        updatedAt = now,
        contentLamportMax = 0
      )
      pageDao.insert(page)
      noteDao.updateTimestamp(noteId, now)
      return page
    }

    // === Stroke Operations ===
    suspend fun saveStroke(pageId: String, stroke: Stroke) {
      val now = System.currentTimeMillis()
      val entity = StrokeEntity(
        strokeId = stroke.id,
        pageId = pageId,
        strokeData = strokeSerializer.serializePoints(stroke.points),
        style = strokeSerializer.serializeStyle(stroke.style),
        bounds = strokeSerializer.serializeBounds(stroke.bounds),
        createdAt = stroke.createdAt,
        createdLamport = stroke.createdLamport
      )
      strokeDao.insert(entity)

      // Update page timestamp:
      pageDao.updateTimestamp(pageId, now)

      // Update note timestamp (get noteId from page):
      val page = pageDao.getById(pageId)
      noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun deleteStroke(strokeId: String) {
      val stroke = strokeDao.getById(strokeId)
      strokeDao.delete(strokeId)

      val now = System.currentTimeMillis()
      pageDao.updateTimestamp(stroke.pageId, now)
      val page = pageDao.getById(stroke.pageId)
      noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun getStrokesForPage(pageId: String): List<Stroke> {
      return strokeDao.getByPageId(pageId).map { entity ->
        Stroke(
          id = entity.strokeId,
          points = strokeSerializer.deserializePoints(entity.strokeData),
          style = strokeSerializer.deserializeStyle(entity.style),
          bounds = strokeSerializer.deserializeBounds(entity.bounds),
          createdAt = entity.createdAt,
          createdLamport = entity.createdLamport
        )
      }
    }

    // === Recognition Updates ===
    /**
     * Update recognized text for a page.
     * Called by MyScriptPageManager.onRecognitionUpdated callback.
     *
     * @param pageId The page whose recognition was updated
     * @param text The recognized text (plain text, not JIIX)
     * @param recognizerVersion Version string (e.g., "myscript-4.3")
     */
    suspend fun updateRecognition(pageId: String, text: String?, recognizerVersion: String?) {
      val now = System.currentTimeMillis()
      recognitionDao.updateRecognition(
        pageId = pageId,
        text = text,
        version = recognizerVersion,
        updatedAt = now
      )

      // Update page and note timestamps
      updatePageTimestamp(pageId)
    }

    // === Timestamp Updates ===
    suspend fun updatePageTimestamp(pageId: String) {
      val now = System.currentTimeMillis()
      pageDao.updateTimestamp(pageId, now)
      val page = pageDao.getById(pageId)
      noteDao.updateTimestamp(page.noteId, now)
    }

    // === Page Kind Upgrade (PDF → MIXED) ===
    suspend fun upgradePageToMixed(pageId: String) {
      val page = pageDao.getById(pageId)
      if (page.kind == "pdf") {
        pageDao.updateKind(pageId, "mixed")
        updatePageTimestamp(pageId)
      }
    }

    // === Page Deletion ===
    /**
     * Deletes a page and all its associated data.
     * Used by PDF import to remove blank first page before adding PDF pages.
     */
    suspend fun deletePage(pageId: String) {
      // Delete all strokes for this page
      strokeDao.deleteAllForPage(pageId)

      // Delete recognition index for this page
      recognitionDao.deleteByPageId(pageId)

      // Delete the page itself
      pageDao.delete(pageId)
    }

    // === Soft Delete ===
    suspend fun deleteNote(noteId: String) {
      val now = System.currentTimeMillis()
      noteDao.softDelete(noteId, now)
    }
  }
  ```

  **Field Maintenance Rules:**
  | Field | When Updated | Value |
  |-------|--------------|-------|
  | `note.updatedAt` | Any change to note, pages, or strokes | `System.currentTimeMillis()` |
  | `page.updatedAt` | Any change to page or its strokes | `System.currentTimeMillis()` |
  | `page.indexInNote` | On page creation; incremented for each new page | Sequential: 0, 1, 2, ... |
  | `page.kind` | On creation ("ink" or "pdf"); upgraded to "mixed" when ink added to PDF | "ink", "pdf", "mixed" |
  | `page.contentLamportMax` | NOT updated in Plan A | Always 0 |
  | `stroke.createdLamport` | NOT updated in Plan A | Always 0 |

  **Verification**:
  - [ ] Unit test: `createNote()` returns valid NoteEntity with UUID
  - [ ] Unit test: `createNote()` also creates first page with indexInNote=0
  - [ ] Unit test: `saveStroke()` serializes and persists correctly
  - [ ] Unit test: `saveStroke()` updates page.updatedAt and note.updatedAt
  - [ ] Unit test: `getStrokesForPage()` deserializes all strokes
  - [ ] Unit test: `deleteNote()` sets deletedAt (soft delete)
  - [ ] Unit test: `createPageFromPdf()` sets kind="pdf" and correct dimensions
  - [ ] Unit test: `upgradePageToMixed()` changes kind from "pdf" to "mixed"
  - [ ] `./gradlew :app:test --tests "*NoteRepositoryTest*"` passes (from `apps/android/`)

  **Commit**: YES
  - Message: `feat(android): add stroke serialization and repository`

---

- [x] 4.10 Integrate persistence with Editor

  **What to do**:
  - Save strokes to database when drawing (on pen-up, call `repository.saveStroke()`)
  - Load strokes from database when opening note (call `repository.getStrokesForPage()`)
  - **Note already exists**: Editor receives `noteId` from navigation (note was created in task 1.9)
  - Load existing strokes for the current page on Editor open
  - Update `page.updatedAt` and `note.updatedAt` on each stroke save

  **Data Flow**:
  1. Editor opens with `noteId` → Load note and pages from database
  2. Display first page → Load strokes for that page
  3. User draws stroke → On pen-up → Save stroke to database
  4. User navigates pages → Load strokes for new page

  **Verification**:
  - [ ] Create note → Draw strokes → Close app → Reopen → Strokes are preserved
  - [ ] `note.updatedAt` changes after drawing stroke
  - [ ] Multiple pages: strokes on page 1 and page 2 are independent

  **Commit**: YES
  - Message: `feat(android): integrate persistence with note editor`

---

- [x] 4.11 Implement Device ID generation

  **What to do**:
  - Create `device/DeviceIdentity.kt`
  - Generate UUID on first launch
  - Store in SharedPreferences/EncryptedSharedPreferences
  - Create DeviceInfo data class

  **Implementation Pattern**:

  ```kotlin
  // File: device/DeviceIdentity.kt
  package com.onyx.android.device

  import android.content.Context
  import android.content.SharedPreferences
  import java.util.UUID

  /**
   * Generates and persists a unique device identifier.
   * This ID is used as ownerUserId until user authentication in Plan C.
   */
  class DeviceIdentity(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
      PREFS_NAME,
      Context.MODE_PRIVATE
    )

    /**
     * Get or generate the device ID.
     * Generated once on first launch, persisted in SharedPreferences.
     */
    fun getDeviceId(): String {
      val existingId = prefs.getString(KEY_DEVICE_ID, null)
      if (existingId != null) {
        return existingId
      }

      // Generate new ID on first launch
      val newId = UUID.randomUUID().toString()
      prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
      return newId
    }

    companion object {
      private const val PREFS_NAME = "onyx_device_identity"
      private const val KEY_DEVICE_ID = "device_id"
    }
  }
  ```

  **Note**: This task MUST be completed before task 4.9 (NoteRepository) because the repository constructor requires a DeviceIdentity instance to set `ownerUserId` on new notes.

  **Verification**:
  - [ ] First launch generates ID (log `deviceIdentity.getDeviceId()`)
  - [ ] Second launch returns same ID
  - [ ] Uninstall/reinstall generates new ID
  - [ ] ID is valid UUID format (matches regex `^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

  **Commit**: YES
  - Message: `feat(android): implement device identity with UUID`

---

- [x] 4.12 Create OnyxApplication and register in AndroidManifest

  **What to do**:
  - Create `apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt`
  - Register in `AndroidManifest.xml` with `android:name=".OnyxApplication"`
  - Initialize all singletons (database, repository, deviceIdentity, myScriptEngine)

  **Implementation** (reference from DI Plan section above):

  ```kotlin
  // File: apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt
  package com.onyx.android

  import android.app.Application
  import androidx.room.Room
  import com.onyx.android.data.OnyxDatabase
  import com.onyx.android.data.NoteRepository
  import com.onyx.android.data.StrokeSerializer
  import com.onyx.android.device.DeviceIdentity
  import com.onyx.android.recognition.MyScriptEngine

  class OnyxApplication : Application() {
    lateinit var database: OnyxDatabase
    lateinit var noteRepository: NoteRepository
    lateinit var deviceIdentity: DeviceIdentity
    lateinit var myScriptEngine: MyScriptEngine

    override fun onCreate() {
      super.onCreate()

      database = Room.databaseBuilder(
        applicationContext, OnyxDatabase::class.java, OnyxDatabase.DATABASE_NAME
      ).build()

      deviceIdentity = DeviceIdentity(applicationContext)

      noteRepository = NoteRepository(
        noteDao = database.noteDao(),
        pageDao = database.pageDao(),
        strokeDao = database.strokeDao(),
        recognitionDao = database.recognitionDao(),
        deviceIdentity = deviceIdentity,
        strokeSerializer = StrokeSerializer  // Object reference, not instantiation
      )

      myScriptEngine = MyScriptEngine(applicationContext)

      // CRITICAL: Initialize MyScript engine immediately after creation
      // This must happen before any ViewModel calls getEngine()
      val initResult = myScriptEngine.initialize()
      if (initResult.isFailure) {
        Log.e("OnyxApp", "MyScript initialization failed: ${initResult.exceptionOrNull()?.message}")
        // App can still run but recognition features will be unavailable
        // ViewModels should check isInitialized() before calling getEngine()
      } else {
        Log.i("OnyxApp", "MyScript engine initialized successfully")
      }
    }
  }
  ```

  **AndroidManifest.xml** (update existing):

  ```xml
  <!-- apps/android/app/src/main/AndroidManifest.xml -->
  <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      <application
          android:name=".OnyxApplication"
          android:allowBackup="true"
          android:icon="@mipmap/ic_launcher"
          android:label="@string/app_name"
          android:theme="@style/Theme.OnyxAndroid">
          <!-- ... activities ... -->
      </application>
  </manifest>
  ```

  **Why This Is Critical**:
  - Without `android:name=".OnyxApplication"`, Android uses default Application class
  - This causes `LocalContext.current.applicationContext as OnyxApplication` to fail with ClassCastException
  - All ViewModels depend on OnyxApplication for dependency injection

  **Depends On**: 4.7 (OnyxDatabase), 4.9 (NoteRepository), 4.11 (DeviceIdentity)

  **Verification**:
  - [ ] App launches without crash
  - [ ] Log statement in `onCreate()` confirms OnyxApplication is instantiated
  - [ ] `LocalContext.current.applicationContext as OnyxApplication` works in Composable

  **Commit**: YES
  - Message: `feat(android): create OnyxApplication with DI singletons`

---

### Phase 5: Recognition

**MyScript SDK Version Compatibility:**
| Component | Version | minSdk | Notes |
|-----------|---------|--------|-------|
| MyScript iink SDK | 4.3.0 | 23 | Latest stable (Jan 2025) - uses OffscreenEditor API |
| Recognition assets | 4.3 | - | Download from https://download.myscript.com/iink/recognitionAssets_iink_4.3 |
| Certificate | Per-app license | - | Obtain from MyScript developer portal |

**Architecture Decision: OffscreenEditor Pattern**

- We use `OffscreenEditor` instead of `Editor` + `EditorView`
- **Why**: Our app uses Jetpack Ink for rendering (low-latency, front-buffer)
- **OffscreenEditor**: Processes strokes without MyScript rendering, returns recognition results
- **Reference**: `myscript-examples/samples/offscreen-interactivity/` (cloned to project)

- [x] 5.1 Add MyScript SDK dependency

  **What to do**:
  - Verify `mavenCentral()` is in `settings.gradle.kts` (should already be there from task 1.5):
    ```kotlin
    dependencyResolutionManagement {
      repositories {
        google()
        mavenCentral()  // MyScript iink SDK is published here
      }
    }
    ```
  - Add dependency to `apps/android/app/build.gradle.kts`:
    ```kotlin
    dependencies {
      // MyScript Interactive Ink SDK v4.3.0 (from Maven Central)
      implementation("com.myscript:iink:4.3.0")
    }
    ```

  **Version Pinning:**
  - Version 4.3.0 (latest stable as of Jan 2025)
  - minSdk: 23 (MyScript v4.x requirement - up from 21 in v2.x)
  - compileSdk: 35 (compatible)
  - Kotlin: 1.9.x+ (compatible)

  **New Features in v4.x (vs v2.x):**
  - `OffscreenEditor` API for custom rendering integration
  - `OffscreenGestureHandler` for gesture recognition
  - `ItemIdHelper` for stroke ID management
  - `MathSolverController` for math solving
  - Handwriting generation (requires additional license)

  **License/Certificate:**
  - Requires per-app certificate from MyScript developer portal
  - Certificate file: `MyCertificate.java` (Java class format, not bytes)
  - Place at: `apps/android/app/src/main/java/com/myscript/certificate/MyCertificate.java`

  **Recognition Assets (v4.3) - AUTHORITATIVE PATH:**

  **DECISION: Bundle in APK for Plan A (see `.sisyphus/plans/initial-setup.md` for full details)**

  **Directory layout in this repo:**
  - `assets/myscript-assets/recognition-assets/conf/en_CA.conf`
  - `assets/myscript-assets/recognition-assets/conf/en_US/en_US.conf`
  - `assets/myscript-assets/recognition-assets/resources/en_CA/*.res`
  - `assets/myscript-assets/recognition-assets/resources/en_US/*.res`

  | Step          | Location                                                                                                        |
  | ------------- | --------------------------------------------------------------------------------------------------------------- |
  | Download      | https://download.myscript.com/iink/recognitionAssets_iink_4.3 (use the package that includes `en_CA` + `en_US`) |
  | Bundle in APK | `apps/android/app/src/main/assets/myscript-assets/recognition-assets/`                                          |
  | Runtime copy  | `{filesDir}/myscript-recognition-assets/` (done by `MyScriptEngine.initialize()`)                               |
  | Engine config | `configuration-manager.search-path` → `{filesDir}/myscript-recognition-assets/`                                 |

  **Why Bundle (not download on first launch):**
  - Simpler for Plan A (no network handling, progress UI, error states)
  - Works fully offline from first launch
  - Download option can be added in Plan C if APK size is a concern

  **See**: `.sisyphus/plans/initial-setup.md` for detailed extraction steps

  **Prerequisites**:
  - Complete `.sisyphus/plans/initial-setup.md` steps (certificate + recognition assets placement)
  - Ensure minSdk is 23+ in build.gradle.kts

  **References**:
  - MyScript Get Started (v4.2): https://developer.myscript.com/docs/interactive-ink/4.2/android/fundamentals/get-started/
  - OffscreenEditor sample: `myscript-examples/samples/offscreen-interactivity/`
  - `.sisyphus/plans/initial-setup.md` - Certificate and recognition assets placement instructions

  **Verification** (run from `apps/android/` directory):
  - [ ] Dependencies sync successfully: `./gradlew sync` completes without error
  - [ ] `./gradlew :app:dependencies | grep iink` shows `com.myscript:iink:4.3.0`
  - [ ] Certificate class exists: `ls apps/android/app/src/main/java/com/myscript/certificate/MyCertificate.java`
  - [ ] Recognition assets bundled (US): `ls apps/android/app/src/main/assets/myscript-assets/recognition-assets/conf/en_US/en_US.conf`
  - [ ] Recognition assets bundled (CA): `ls apps/android/app/src/main/assets/myscript-assets/recognition-assets/conf/en_CA.conf`
  - [ ] Build compiles: `./gradlew :app:assembleDebug`

  **Commit**: Group with 5.2

---

- [ ] 5.2 Initialize MyScript Engine

  **What to do**:
  - Create `recognition/MyScriptEngine.kt` using OffscreenEditor pattern (v4.3):

    ```kotlin
    import com.myscript.certificate.MyCertificate
    import com.myscript.iink.*

    /**
     * MyScript Engine wrapper - Application singleton.
     *
     * OWNERSHIP MODEL:
     * - MyScriptEngine: Application singleton, owns Engine only
     * - MyScriptPageManager: Per-ViewModel, owns OffscreenEditor per page
     *
     * This class:
     * - Creates and holds the Engine singleton
     * - Configures recognition assets path
     * - Exposes getEngine() for MyScriptPageManager to create OffscreenEditors
     *
     * Does NOT:
     * - Create OffscreenEditor (that's MyScriptPageManager's job)
     * - Manage ContentPackage/ContentPart (per-page, owned by MyScriptPageManager)
     *
     * Reference: myscript-examples/samples/offscreen-interactivity/
     */
    class MyScriptEngine(private val context: Context) {
      private var engine: Engine? = null

      /**
       * Get the Engine instance for creating page-specific OffscreenEditors.
       * Used by NoteEditorViewModel to create MyScriptPageManager instances.
       *
       * IMPORTANT: Call initialize() before getEngine().
       * Throws if engine not initialized.
       */
      fun getEngine(): Engine {
        return engine ?: throw IllegalStateException(
          "MyScriptEngine not initialized. Call initialize() first."
        )
      }

      /**
       * Check if engine is ready for use.
       */
      fun isInitialized(): Boolean = engine != null

      fun initialize(): Result<Unit> {
        return try {
          // Create engine with certificate (v4.x uses Java class, not bytes)
          engine = Engine.create(MyCertificate.getBytes())

          // Configure recognition assets path
          // Assets are bundled in APK at: assets/myscript-assets/recognition-assets/
          // Copied at first launch to: {filesDir}/myscript-recognition-assets/
          val conf = engine!!.configuration
          val assetsPath = File(context.filesDir, "myscript-recognition-assets")

          // Copy bundled assets to filesDir if not already done
          if (!assetsPath.exists()) {
            copyAssetsToFilesDir(context, "myscript-assets/recognition-assets", assetsPath)
          }

          conf.setStringArray("configuration-manager.search-path",
            arrayOf(assetsPath.absolutePath))
          // Use Canadian assets by default; allow US as fallback.
          conf.setString("lang", "en_CA")

          // NOTE: OffscreenEditor and ContentPackage are NOT created here.
          // They are per-page, managed by MyScriptPageManager.
          // MyScriptPageManager uses engine.createOffscreenEditor() per page.

          Log.i("MyScript", "MyScript v4.3 Engine initialized")
          Result.success(Unit)
        } catch (e: Exception) {
          Log.e("MyScript", "Engine initialization failed: ${e.message}")
          Result.failure(e)
        }
      }

      fun close() {
        engine?.close()
        engine = null
      }

      /**
       * Copy bundled assets from APK to filesDir.
       * Called once at first initialization.
       */
      private fun copyAssetsToFilesDir(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        targetDir.mkdirs()

        for (filename in files) {
          val srcPath = "$assetPath/$filename"
          val targetFile = File(targetDir, filename)

          // Check if it's a directory (has children)
          val children = assetManager.list(srcPath)
          if (children != null && children.isNotEmpty()) {
            // Recurse into subdirectory
            copyAssetsToFilesDir(context, srcPath, targetFile)
          } else {
            // Copy file
            assetManager.open(srcPath).use { input ->
              targetFile.outputStream().use { output ->
                input.copyTo(output)
              }
            }
          }
        }
      }
    }
    ```

  **Key API Differences from v2.x:**
  | v2.x | v4.x |
  |------|------|
  | `Editor` + `EditorView` | `OffscreenEditor` (no view) |
  | `engine.createEditor(renderer)` | `engine.createOffscreenEditor(scaleX, scaleY)` |
  | Pointer events via touch handler | `offscreenEditor.addStrokes(pointerEvents, processGestures)` |
  | `editor.export_(block, MimeType)` | `offscreenEditor.export_(blockIds, MimeType)` |

  **Coordinate System (AUTHORITATIVE SPECIFICATION):**

  **Three Coordinate Spaces:**
  | Space | Unit | Origin | Used By |
  |-------|------|--------|---------|
  | Screen | pixels (px) | Top-left of view | MotionEvent, touch handling |
  | Page | points (pt, 1/72 inch) | Top-left of page | Stroke storage, rendering |
  | MyScript | millimeters (mm) | Top-left of content | OffscreenEditor.addStrokes() |

  **Conversion Pipeline:**

  ```
  MotionEvent (px) → ViewTransform.screenToPage() → Stroke (pt) → ptToMm() → MyScript (mm)
  ```

  **Conversions:**

  ```kotlin
  // Screen px → Page pt (with zoom/pan)
  fun screenToPage(screenX: Float, screenY: Float): Pair<Float, Float> {
    val pageX = (screenX - panX) / zoom
    val pageY = (screenY - panY) / zoom
    return Pair(pageX, pageY)
  }

  // Page pt → MyScript mm (fixed ratio, no DPI needed)
  // 1 pt = 1/72 inch, 1 inch = 25.4 mm
  // Therefore: 1 pt = 25.4 / 72 mm ≈ 0.3528 mm
  fun ptToMm(pt: Float): Float = pt * 25.4f / 72f

  // MyScript mm → Page pt (for any outputs from MyScript)
  fun mmToPt(mm: Float): Float = mm * 72f / 25.4f
  ```

  **IMPORTANT:**
  - `DisplayMetricsConverter` from MyScript sample is for px↔mm (screen DPI dependent)
  - We use pt↔mm conversion instead (fixed ratio, DPI independent)
  - This is because we store strokes in page pt, not screen px

  **OffscreenEditor Scale Parameters:**
  - We pass `scaleX=1f, scaleY=1f` to `engine.createOffscreenEditor()`
  - Then apply our own pt→mm conversion before calling `addStrokes()`
  - This gives us full control over coordinate conversion

  **Reference Files (with verified line numbers):**
  - `myscript-examples/samples/offscreen-interactivity/.../InkViewModel.kt:685-698` - `addStroke()` method (feed strokes to OffscreenEditor)
  - `myscript-examples/samples/offscreen-interactivity/.../InkViewModel.kt:317-337` - `IOffscreenEditorListener.contentChanged()` implementation
  - `myscript-examples/samples/offscreen-interactivity/.../InkViewModel.kt:572` - `historyManager.undo()` usage
  - `myscript-examples/samples/offscreen-interactivity/.../InkViewModel.kt:598` - `historyManager.redo()` usage
  - `myscript-examples/samples/offscreen-interactivity/.../InkViewModel.kt:701-702` - History ID retrieval pattern

  **MyScript Content Lifecycle Per Page (Complete Specification - v4.3.0 OffscreenEditor):**

  ```kotlin
  /**
   * MyScript content lifecycle is tied to PAGE, not note.
   * Each page has its own recognition context using OffscreenEditor.
   *
   * OWNERSHIP (v4.x OffscreenEditor Pattern):
   * - Engine: Singleton (Application-scoped)
   * - ContentPackage: One per page, stored in filesDir
   * - ContentPart: Active part for current page
   * - OffscreenEditor: One per page, manages ink input (NO rendering/view)
   * - ItemIdHelper: Maps between MyScript stroke IDs and our stroke IDs
   */
  class MyScriptPageManager(
    private val engine: Engine,
    private val context: Context
    // NOTE: displayMetrics not needed - we use fixed pt→mm conversion
  ) {
    private var currentPageId: String? = null
    private var currentPackage: ContentPackage? = null
    private var currentPart: ContentPart? = null
    private var offscreenEditor: OffscreenEditor? = null
    private var itemIdHelper: ItemIdHelper? = null

    // Map MyScript stroke IDs to our stroke UUIDs
    private val strokeIdsMapping = mutableMapOf<String, String>()

    // Coordinate conversion: pt → mm (fixed ratio, DPI independent)
    // 1 pt = 1/72 inch, 1 inch = 25.4 mm
    // Therefore: 1 pt = 25.4 / 72 mm ≈ 0.3528 mm
    private fun ptToMm(pt: Float): Float = pt * 25.4f / 72f

    /**
     * Called when navigating TO a page.
     * Loads or creates ContentPackage for this page.
     */
    fun onPageEnter(pageId: String) {
      // Close previous page's content if different
      if (currentPageId != pageId) {
        closeCurrentPage()
      }

      currentPageId = pageId
      val packagePath = File(context.filesDir, "myscript/page_$pageId.iink")
      packagePath.parentFile?.mkdirs()

      currentPackage = if (packagePath.exists()) {
        engine.openPackage(packagePath)
      } else {
        engine.createPackage(packagePath)
      }

      // Get or create content part (Raw Content for freeform ink)
      currentPart = if (currentPackage!!.partCount > 0) {
        currentPackage!!.getPart(0)
      } else {
        currentPackage!!.createPart("Raw Content")
      }

      // Create OffscreenEditor with scale=1 (we do our own pt→mm conversion)
      offscreenEditor = engine.createOffscreenEditor(1f, 1f)
      offscreenEditor?.part = currentPart
      offscreenEditor?.setListener(recognitionListener)

      // ItemIdHelper for stroke ID mapping
      // API: engine.createItemIdHelper(editor) - NOT ItemIdHelper(engine, editor)
      // Reference: myscript-examples/.../InkViewModel.kt:448
      itemIdHelper = engine.createItemIdHelper(offscreenEditor!!)
      strokeIdsMapping.clear()
    }

    /**
     * Called when navigating AWAY from a page.
     * Saves and closes ContentPackage.
     */
    fun closeCurrentPage() {
      offscreenEditor?.close()
      itemIdHelper?.close()
      currentPart?.close()
      currentPackage?.save()
      currentPackage?.close()
      offscreenEditor = null
      itemIdHelper = null
      currentPart = null
      currentPackage = null
      currentPageId = null
      strokeIdsMapping.clear()
    }

    /**
     * Add stroke to recognizer using OffscreenEditor.addStrokes().
     * Strokes are in page coordinates (pt), converted to mm for MyScript.
     * Returns MyScript's stroke ID for mapping.
     */
    fun addStroke(stroke: Stroke): String? {
      // Convert our Stroke (pt coords) to MyScript PointerEvents (mm coords)
      val pointerEvents = stroke.points.mapIndexed { index, point ->
        val eventType = when (index) {
          0 -> PointerEventType.DOWN
          stroke.points.lastIndex -> PointerEventType.UP
          else -> PointerEventType.MOVE
        }
        PointerEvent(
          eventType = eventType,
          x = ptToMm(point.x),      // Convert pt → mm
          y = ptToMm(point.y),      // Convert pt → mm
          t = point.t,              // timestamp in ms
          f = point.p ?: 0.5f,      // pressure (0.0-1.0)
          pointerType = PointerType.PEN,
          pointerId = 0
        )
      }.toTypedArray()

      val myScriptStrokeIds = offscreenEditor?.addStrokes(pointerEvents, true)
      return myScriptStrokeIds?.firstOrNull()?.also { msStrokeId ->
        strokeIdsMapping[msStrokeId] = stroke.id
      }
    }

    /**
     * Called on ERASE stroke.
     * Uses OffscreenEditor's erase capability or clear + re-feed.
     */
    fun onStrokeErased(erasedStrokeId: String, remainingStrokes: List<Stroke>) {
      // Option 1 (Simple for Plan A): Clear and re-feed all strokes
      offscreenEditor?.clear()
      strokeIdsMapping.clear()
      remainingStrokes.forEach { stroke ->
        addStroke(stroke)
      }
      // Recognition triggers automatically via listener
    }

    /**
     * Called on UNDO.
     * Use OffscreenEditor's undo if available, else clear + re-feed.
     */
    fun onUndo(currentStrokes: List<Stroke>) {
      // Try native undo first
      val history = offscreenEditor?.historyManager
      if (history?.canUndo() == true) {
        history.undo()
        return
      }

      // Fallback: clear and re-feed
      offscreenEditor?.clear()
      strokeIdsMapping.clear()
      currentStrokes.forEach { stroke ->
        addStroke(stroke)
      }
    }

    /**
     * Called on REDO.
     */
    fun onRedo(currentStrokes: List<Stroke>) {
      // Try native redo first
      val history = offscreenEditor?.historyManager
      if (history?.canRedo() == true) {
        history.redo()
        return
      }

      // Fallback: clear and re-feed
      offscreenEditor?.clear()
      strokeIdsMapping.clear()
      currentStrokes.forEach { stroke ->
        addStroke(stroke)
      }
    }

    /**
     * Get recognized text as plain text.
     */
    fun exportText(): String? {
      return offscreenEditor?.export_(emptyArray(), MimeType.TEXT)
    }

    /**
     * Get recognition result as JIIX JSON.
     */
    fun exportJIIX(): String? {
      return offscreenEditor?.export_(emptyArray(), MimeType.JIIX)
    }

    private val recognitionListener = object : IOffscreenEditorListener {
      override fun partChanged(editor: OffscreenEditor) {
        // Part changed (new part loaded)
      }

      override fun contentChanged(editor: OffscreenEditor, blockIds: Array<out String>) {
        // Recognition result available
        val text = exportText()
        Log.d("MyScript", "Recognition updated: $text")
        // Emit to observers for database storage
        onRecognitionUpdated?.invoke(currentPageId ?: return, text ?: "")
      }

      override fun onError(editor: OffscreenEditor, blockId: String, err: EditorError, message: String) {
        Log.e("MyScript", "Recognition error: $message")
      }
    }

    // Callback for recognition updates (set by ViewModel/Repository)
    var onRecognitionUpdated: ((pageId: String, text: String) -> Unit)? = null
  }

  /**
   * Extension to convert our Stroke to MyScript PointerEvents.
   */
  private fun Stroke.toPointerEvents(): List<PointerEvent> {
    return points.mapIndexed { index, point ->
      val eventType = when (index) {
        0 -> PointerEventType.DOWN
        points.lastIndex -> PointerEventType.UP
        else -> PointerEventType.MOVE
      }
      PointerEvent(
        eventType = eventType,
        x = point.x,
        y = point.y,
        t = point.t,
        f = point.p ?: 0.5f,
        pointerType = PointerType.PEN,
        pointerId = 0
      )
    }
  }
  ```

  **Lifecycle Events Summary (v4.x OffscreenEditor):**
  | Event | MyScript Action | Recognition Update |
  |-------|----------------|-------------------|
  | Page enter | Load/create ContentPackage, create OffscreenEditor | Re-feed existing strokes (if any) |
  | Page exit | Save & close ContentPackage, close OffscreenEditor | None |
  | Stroke added | `offscreenEditor.addStrokes(pointerEvents, true)` | Auto via `IOffscreenEditorListener.contentChanged` |
  | Stroke erased | Clear + re-feed remaining | Auto via listener |
  | Undo | `historyManager.undo()` or clear + re-feed | Auto via listener |
  | Redo | `historyManager.redo()` or clear + re-feed | Auto via listener |
  | App background | Save ContentPackage | None |
  | App terminate | Save & close ContentPackage | None |

  **Storage:**
  - ContentPackage files: `{filesDir}/myscript/page_{pageId}.iink`
  - One .iink file per page with ink history
  - Survives app restarts (MyScript internal format, not our strokes)

  **References**:
  - MyScript Engine API (v4.x): https://developer.myscript.com/docs/interactive-ink/4.0/android/fundamentals/get-started/
  - OffscreenEditor sample: `myscript-examples/samples/offscreen-interactivity/`
  - `.sisyphus/plans/initial-setup.md` - Certificate and recognition assets setup

  **Verification**:
  - [ ] Engine initializes without exception on tablet
  - [ ] `adb logcat | grep MyScript` shows "MyScript initialized successfully"
  - [ ] No certificate error in logs
  - [ ] No "recognition assets not found" error in logs

  **Commit**: YES
  - Message: `feat(android): initialize MyScript SDK`

---

- [x] 5.3 Feed strokes to recognizer

  **What to do**:
  - On pen-up, convert Stroke to MyScript PointerEvents
  - Use `offscreenEditor.addStrokes()` to feed strokes
  - Store stroke ID mapping for future erase operations

  **OffscreenEditor Stroke Input API (v4.3.0):**

  ```kotlin
  // Key classes (v4.x OffscreenEditor pattern):
  // - OffscreenEditor: No rendering, just recognition
  // - PointerEvent: Stroke point with eventType (DOWN/MOVE/UP)
  // - ItemIdHelper: Maps between MyScript IDs and our stroke IDs

  // OWNERSHIP:
  // - MyScriptEngine: Application singleton, owns Engine instance
  // - MyScriptPageManager: Per-ViewModel, created in ViewModel factory
  // - OffscreenEditor: Created in MyScriptPageManager.onPageEnter()

  // In NoteEditorViewModel (see DI section at top of file):
  // - myScriptPageManager is injected via factory pattern
  // - NOT retrieved from myScriptEngine

  /**
   * Feed stroke to MyScript recognizer via OffscreenEditor.
   * Called on pen-up after stroke is complete.
   *
   * Location: NoteEditorViewModel.onPenUp()
   */
  fun onPenUp(stroke: Stroke) {
    // myScriptPageManager is a ViewModel property (see factory in DI section)
    // It was created in the ViewModel factory with:
    //   MyScriptPageManager(
    //     engine = app.myScriptEngine.getEngine(),
    //     context = app
    //     // NOTE: No displayMetrics needed - we use fixed pt→mm conversion
    //   )

    // Ensure page is active before feeding strokes
    requireNotNull(currentPageId) { "No page active - call navigateToPage() first" }

    // MyScriptPageManager.addStroke() handles:
    // 1. Converting Stroke to PointerEvents
    // 2. Coordinate conversion (pt → mm via fixed ratio: mm = pt * 25.4 / 72)
    // 3. Calling offscreenEditor.addStrokes()
    // 4. Mapping MyScript stroke ID to our stroke.id
    myScriptPageManager.addStroke(stroke)

    // Recognition triggers automatically via IOffscreenEditorListener.contentChanged()
    // No need to manually call triggerRecognition()
  }
  ```

  **PointerEvent Construction (for reference):**

  ```kotlin
  // This happens inside MyScriptPageManager.addStroke()
  val pointerEvents = stroke.points.mapIndexed { index, point ->
    val eventType = when (index) {
      0 -> PointerEventType.DOWN
      stroke.points.lastIndex -> PointerEventType.UP
      else -> PointerEventType.MOVE
    }

    // Our strokes use page coordinates (pt)
    // MyScriptPageManager.converter handles pt → mm conversion
    PointerEvent(
      eventType = eventType,
      x = point.x,
      y = point.y,
      t = point.t,            // timestamp in ms
      f = point.p ?: 0.5f,    // pressure (0.0-1.0)
      pointerType = PointerType.PEN,
      pointerId = 0
    )
  }.toTypedArray()
  ```

  **Coordinate Conversion (reference only - see task 5.2 for authoritative spec):**
  - Our strokes: page coordinates (pt)
  - MyScript: millimeters (mm)
  - Conversion: `mm = pt * 25.4 / 72` (fixed ratio, no DPI dependency)
  - See "Coordinate System" section in task 5.2 for complete specification

  **Integration Point (NoteEditorViewModel):**

  ```kotlin
  // In NoteEditorViewModel:

  // 1. On page navigation, enter the page in MyScript
  fun navigateToPage(pageId: String) {
    currentPageId = pageId
    myScriptPageManager.onPageEnter(pageId)
    // ... load strokes from repository
  }

  // 2. On pen-up, feed stroke to recognizer
  fun onPenUp(stroke: Stroke) {
    myScriptPageManager.addStroke(stroke)
    // Recognition triggers automatically via listener
  }

  // 3. On ViewModel cleared, close current page
  override fun onCleared() {
    myScriptPageManager.closeCurrentPage()
    super.onCleared()
  }
  ```

  **References**:
  - `myscript-examples/samples/offscreen-interactivity/.../InkViewModel.kt:685-698` - OffscreenEditor stroke feeding pattern

  **Verification**:
  - [ ] Write "hello" → Log shows "addStrokes() called with N pointer events"
  - [ ] `offscreenEditor.isEmpty` returns `false` after stroke added
  - [ ] Stroke ID mapping contains entry: MyScript ID → our stroke UUID

  **Commit**: Group with 5.4

---

- [ ] 5.4 Implement realtime recognition

  **What to do**:
  - Listen for recognition via `IOffscreenEditorListener.contentChanged()`
  - Extract text from recognition result
  - Emit to observers for database storage

  **OffscreenEditor Recognition Listener (v4.3.0):**

  ```kotlin
  // Recognition happens automatically after strokes are added
  // Use IOffscreenEditorListener to receive callbacks
  // (Already set up in MyScriptPageManager.onPageEnter())

  private val offscreenEditorListener = object : IOffscreenEditorListener {
    override fun partChanged(editor: OffscreenEditor) {
      // Part changed (new part loaded) - typically ignore
    }

    override fun contentChanged(editor: OffscreenEditor, blockIds: Array<out String>) {
      // Recognition result available - this is the main callback
      // blockIds: IDs of content blocks that changed

      // Export as plain text
      val text = editor.export_(emptyArray(), MimeType.TEXT)
      Log.d("MyScript", "Recognition result: $text")

      // Export as JIIX for detailed structure (optional)
      val jiix = editor.export_(emptyArray(), MimeType.JIIX)
      // JIIX contains: words, characters, bounding boxes, alternatives

      // Emit to observers for database storage
      onRecognitionUpdated?.invoke(currentPageId!!, text ?: "")
    }

    override fun onError(editor: OffscreenEditor, blockId: String, err: EditorError, message: String) {
      Log.e("MyScript", "Recognition error [$err]: $message")
      // EditorError enum: UNSUPPORTED_ORIENTATION, INVALID_BLOCK, etc.
    }
  }

  // To get recognized text on-demand (without waiting for callback):
  fun getRecognizedText(): String {
    return offscreenEditor?.export_(emptyArray(), MimeType.TEXT) ?: ""
  }

  // No need for waitForIdle() with OffscreenEditor
  // Recognition is synchronous after addStrokes()
  ```

  **JIIX Format (for detailed recognition data):**

  ```json
  {
    "type": "Raw Content",
    "elements": [
      {
        "type": "Stroke",
        "id": "stroke-123",
        "words": [
          {
            "label": "hello",
            "candidates": ["hello", "hallo", "helloo"],
            "bounding-box": { "x": 10, "y": 20, "width": 50, "height": 15 }
          }
        ]
      }
    ]
  }
  ```

  **Integration with ViewModel:**

  ```kotlin
  // In NoteEditorViewModel init{} block (see DI section at top of file):
  init {
    myScriptPageManager.onRecognitionUpdated = { pageId, text ->
      viewModelScope.launch {
        // Uses NoteRepository directly (no separate RecognitionRepository)
        noteRepository.updateRecognition(pageId, text, "myscript-4.3")
      }
    }
  }
  ```

  **References**:
  - `myscript-examples/samples/offscreen-interactivity/.../InkViewModel.kt:offScreenEditorListener` - Listener implementation
  - MyScript JIIX format: https://developer.myscript.com/docs/interactive-ink/4.0/reference/jiix/

  **Verification**:
  - [ ] Write "hello" → `contentChanged()` callback fires
  - [ ] `export_(emptyArray(), MimeType.TEXT)` returns "hello" or similar
  - [ ] Log shows: "Recognition result: hello"
  - [ ] `onRecognitionUpdated` callback invoked with (pageId, "hello")

  **Commit**: YES
  - Message: `feat(android): implement realtime MyScript recognition`

---

- [ ] 5.5 Store recognized text in database

  **What to do**:
  - On recognition complete, update RecognitionIndexEntity
  - Store text for the current page
  - Handle recognition updates from erase, undo/redo, and page switches

  **Verification**:
  - [ ] Write text → Check database → recognizedText is populated

  **Recognition After Erase (Verifiable Acceptance Criteria):**
  - [ ] Draw "hello" → Wait for recognition → Erase all strokes → Database updates:
    - Query: `SELECT recognizedText FROM recognition_index WHERE pageId = ?`
    - Before erase: Contains "hello"
    - After erase: Empty string or null
  - [ ] Draw "hello world" → Erase "world" strokes only → Recognition updates to "hello":
    - Partial erase correctly updates recognition
  - [ ] Erase stroke immediately after drawing (no recognition yet) → No stale recognition stored:
    - Fast erase scenario handled correctly

  **Page Switch Isolation (Verifiable Acceptance Criteria):**
  - [ ] Page 1: Draw "cat" → Wait for recognition
        Page 2: Navigate → Draw "dog" → Wait for recognition
        Page 1: Navigate back → Recognition still shows "cat" (not "dog"):
    - Each page has isolated recognition context
  - [ ] Page 1: Draw "hello" → Navigate to Page 2 (no strokes) → Navigate back:
    - Page 1 recognition still shows "hello"
    - Page 2 recognition is empty/null
  - [ ] Page 1: Recognition in progress → Navigate to Page 2 → Navigate back:
    - Page 1 recognition completes correctly (no corruption from navigation)

  **MyScript State Integrity (Verifiable):**
  - [ ] Page 1: Draw → Navigate to Page 2 → Draw → Kill app (swipe away):
    - Restart app → Both pages have correct recognition (ContentPackage saved on exit)
  - [ ] Page 1: Draw "test" → Navigate away → Navigate back:
    - ContentPackage reloads from `{filesDir}/myscript/page_{pageId}.iink`
    - Recognition consistent with strokes

  **Commit**: YES
  - Message: `feat(android): store recognition results in database`

---

### Phase 6: PDF

- [ ] 6.1 Add MuPDF dependency

  **What to do**:
  - Add to `apps/android/app/build.gradle.kts`:
    ```kotlin
    dependencies {
      implementation("com.artifex.mupdf:fitz:1.24.10")
    }
    ```
  - **Repository: Maven Central (DECISION)**
    - MuPDF 1.24.10 is published on Maven Central (no authentication needed)
    - `mavenCentral()` should already be in your repositories block
    - No GitHub Packages configuration required
    ```kotlin
    // settings.gradle.kts - no changes needed if mavenCentral() exists
    dependencyResolutionManagement {
      repositories {
        google()
        mavenCentral()  // MuPDF 1.24.10 is here
      }
    }
    ```

  **Licensing Decision (CRITICAL):**
  - MuPDF is licensed under **AGPL-3.0**
  - For Plan A (local prototype), AGPL is acceptable
  - For production/distribution: Either:
    1. Open-source the entire app under AGPL-compatible license, OR
    2. Purchase commercial license from Artifex (https://artifex.com/licensing)
  - **Decision for Plan A**: Use AGPL version; defer commercial licensing to Plan C (production release)

  **Version Pinning:**
  - MuPDF 1.24.10 (latest stable as of Jan 2025)
  - minSdk: 21 (MuPDF requirement)
  - compileSdk: 35 (compatible)

  **References**:
  - MuPDF Android: https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html
  - MuPDF Maven: https://central.sonatype.com/artifact/com.artifex.mupdf/fitz
  - Licensing: https://artifex.com/licensing/mupdf

  **Verification** (run from `apps/android/` directory):
  - [ ] `./gradlew sync` succeeds (no "could not resolve" errors)
  - [ ] `./gradlew :app:dependencies | grep mupdf` shows `com.artifex.mupdf:fitz:1.24.10`
  - [ ] Build compiles: `./gradlew :app:assembleDebug` succeeds

  **Commit**: Group with 6.1a

---

- [ ] 6.1a Create PdfAssetStorage helper

  **What to do**:
  - Create `pdf/PdfAssetStorage.kt` for managing PDF file storage:

    ```kotlin
    /**
     * Manages PDF file storage in internal app directory.
     * PDF files are stored as: {filesDir}/pdf_assets/{assetId}.pdf
     *
     * The assetId is a UUID that is referenced by PageEntity.pdfAssetId
     * Multiple pages can share the same pdfAssetId (multi-page PDF).
     */
    class PdfAssetStorage(private val context: Context) {

      private val pdfDir: File
        get() = File(context.filesDir, "pdf_assets").also { it.mkdirs() }

      /**
       * Import a PDF from a content URI (e.g., from file picker).
       * Copies the PDF to internal storage.
       *
       * @param uri Content URI from Storage Access Framework
       * @return Asset ID (UUID string) for the imported PDF
       */
      fun importPdf(uri: Uri): String {
        val assetId = UUID.randomUUID().toString()
        val targetFile = File(pdfDir, "$assetId.pdf")

        context.contentResolver.openInputStream(uri)?.use { input ->
          targetFile.outputStream().use { output ->
            input.copyTo(output)
          }
        } ?: throw IOException("Failed to open PDF from URI: $uri")

        return assetId
      }

      /**
       * Get the File for a given asset ID.
       * Use this with MuPDF Document.openDocument().
       */
      fun getFileForAsset(assetId: String): File {
        return File(pdfDir, "$assetId.pdf")
      }

      /**
       * Delete a PDF asset (e.g., when note is deleted).
       */
      fun deleteAsset(assetId: String) {
        File(pdfDir, "$assetId.pdf").delete()
      }

      /**
       * Check if asset exists.
       */
      fun assetExists(assetId: String): Boolean {
        return File(pdfDir, "$assetId.pdf").exists()
      }
    }
    ```

  - **Storage location**: `{filesDir}/pdf_assets/{uuid}.pdf`
  - **Asset ID**: UUID string stored in `PageEntity.pdfAssetId`
  - **Multi-page PDFs**: All pages of a PDF share the same `pdfAssetId`

  **Verification**:
  - [ ] Class compiles
  - [ ] Unit test: `importPdf()` copies file and returns valid UUID
  - [ ] Unit test: `getFileForAsset()` returns correct path
  - [ ] Unit test: `deleteAsset()` removes file from disk

  **Commit**: Group with 6.2

---

- [ ] 6.2 Implement PDF loading from file picker

  **What to do**:
  - Add "Import PDF" button to Home screen (alongside FAB for new ink note)
  - Use Storage Access Framework to pick file
  - Copy PDF to app storage via `PdfAssetStorage.importPdf()`
  - Warn if file > 50MB or > 100 pages

  **PDF Import Workflow (Complete Specification):**

  **UI Entry Point:**
  - Home screen has two actions:
    1. FAB "+" → Create new ink note (task 1.9)
    2. "Import PDF" button (overflow menu or secondary FAB) → Opens file picker

  **Import Flow:**

  ```kotlin
  // 1. User taps "Import PDF"
  // 2. Open Storage Access Framework picker
  val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "application/pdf"
  }

  // 3. On file selected:
  fun onPdfSelected(uri: Uri) {
    // 3a. Copy PDF to internal storage (uses PdfAssetStorage from task 6.1a)
    val pdfAssetId = pdfAssetStorage.importPdf(uri)

    // 3b. Open PDF to get page count and dimensions
    val document = Document.openDocument(pdfAssetStorage.getFileForAsset(pdfAssetId).absolutePath)
    val pageCount = document.countPages()

    // 3c. Check size limits
    val fileSize = pdfAssetStorage.getFileForAsset(pdfAssetId).length()
    if (fileSize > 50 * 1024 * 1024 || pageCount > 100) {
      // Show warning dialog, but allow proceeding
      showWarningDialog("Large PDF", "This PDF has $pageCount pages. Performance may be affected.")
    }

    // 3d. Create new note for this PDF
    val noteWithPage = repository.createNote()  // Returns NoteWithFirstPage

    // 3e. Replace first page with PDF page 0, add remaining pages
    repository.deletePage(noteWithPage.firstPageId)  // Remove blank page

    for (pageIndex in 0 until pageCount) {
      val pdfPage = document.loadPage(pageIndex)
      val bounds = pdfPage.bounds

      repository.createPageFromPdf(
        noteId = noteWithPage.note.noteId,
        indexInNote = pageIndex,
        pdfAssetId = pdfAssetId,
        pdfPageNo = pageIndex,
        pdfWidth = bounds.x1 - bounds.x0,  // width in pt
        pdfHeight = bounds.y1 - bounds.y0  // height in pt
      )
      pdfPage.destroy()
    }
    document.destroy()

    // 3f. Navigate to editor with new note
    navController.navigate(Routes.editor(noteWithPage.note.noteId))
  }
  ```

  **PDF → Note/Page Mapping:**
  | PDF | Note/Page |
  |-----|-----------|
  | 1 PDF file | 1 Note |
  | PDF page N | PageEntity with `kind="pdf"`, `pdfPageNo=N` |
  | All PDF pages | Separate PageEntity for each, same `pdfAssetId` |
  | PDF dimensions | `PageEntity.width/height` from `page.bounds` |

  **Decision: New note per PDF**
  - Each PDF import creates a NEW note (not added to existing)
  - Rationale: Cleaner mental model; mixing ink-only and PDF pages in one note adds UX complexity
  - Future: Could add "Add PDF to existing note" if needed

  **Multi-page PDF handling:**
  - Each PDF page becomes a separate `PageEntity` with same `pdfAssetId`
  - `pdfPageNo` field distinguishes which page (0-indexed)
  - All pages share the single copied PDF file

  **Verification**:
  - [ ] Can select PDF from device via file picker
  - [ ] Large PDF (> 50MB or > 100 pages) shows warning but still imports
  - [ ] After import: new note appears in Home screen
  - [ ] Note has correct number of pages (one per PDF page)
  - [ ] Each page has `kind="pdf"` and correct `pdfPageNo`
  - [ ] Database query: `SELECT COUNT(*) FROM pages WHERE noteId='X'` = PDF page count

  **Commit**: YES
  - Message: `feat(android): add PDF import via file picker`

---

- [ ] 6.3 Render PDF page to canvas

  **What to do**:
  - Create `pdf/PdfRenderer.kt`
  - Load PDF with MuPDF
  - Render page to Bitmap
  - Display in composable

  **MuPDF API Guidance** (concrete implementation):

  ```kotlin
  // Key MuPDF classes:
  // - Document: Represents a PDF file
  // - Page: Single page within document
  // - Matrix: Transform for scaling (zoom) and translation (pan)
  // - Pixmap: Rasterized page content
  // - AndroidDrawDevice: Draws to Android Bitmap

  // Load PDF:
  val document = Document.openDocument(pdfFilePath)
  val pageCount = document.countPages()

  // Render a page to Bitmap:
  fun renderPage(pageIndex: Int, zoom: Float): Bitmap {
    val page = document.loadPage(pageIndex)

    // Get page bounds in points (pt)
    val bounds = page.bounds // RectF with width/height in pt
    val pageWidth = bounds.x1 - bounds.x0  // width in pt
    val pageHeight = bounds.y1 - bounds.y0 // height in pt

    // Create transform matrix (scale by zoom, 72 DPI base)
    val matrix = Matrix(zoom) // zoom factor

    // Calculate pixel dimensions
    val pixelWidth = (pageWidth * zoom).toInt()
    val pixelHeight = (pageHeight * zoom).toInt()

    // Create bitmap and draw
    val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
    val device = AndroidDrawDevice(bitmap)
    page.run(device, matrix, null) // null = no clip
    device.close()
    page.destroy()

    return bitmap
  }
  ```

  **Store page geometry from PDF bounds** (`width: Float, height: Float, unit: "pt"`).

  **Verification**:
  - [ ] PDF page displays correctly
  - [ ] Text is readable
  - [ ] Log shows correct page dimensions in pt (e.g., 612x792 for US Letter)

  **Commit**: Group with 6.4

---

- [ ] 6.4 Implement PDF zoom and pan

  **What to do**:
  - Apply same zoom/pan gestures as ink canvas
  - Re-render at higher resolution when zoomed (DPI scaling)
  - Zoom range: 0.5x to 4x

  **References**:
  - MuPDF rendering: https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html

  **Verification**:
  - [ ] Can zoom PDF (pinch gesture)
  - [ ] At 2x zoom: text remains legible without pixelation
  - [ ] At 4x zoom: can read small print text
  - [ ] Pan works after zoom (two-finger drag)

  **Commit**: YES
  - Message: `feat(android): render PDF pages with zoom/pan`

---

- [ ] 6.5 Implement PDF text selection

  **What to do**:
  - Use MuPDF text extraction APIs
  - Handle long-press to start selection
  - Display selection handles

  **MuPDF Text Selection API Guidance** (concrete implementation):

  ```kotlin
  // Key classes for text selection:
  // - StructuredText: Text content extracted from page
  // - TextBlock, TextLine, TextChar: Hierarchical text structure
  // - Quad: Bounding quadrilateral for text regions

  // Extract text structure from page:
  val page = document.loadPage(pageIndex)
  val text = page.toStructuredText()

  // Hit test to find character at touch point:
  fun findCharAtPoint(x: Float, y: Float): TextChar? {
    // x, y are in page coordinates (pt)
    for (block in text.blocks) {
      for (line in block.lines) {
        for (char in line.chars) {
          if (char.quad.contains(x, y)) {
            return char
          }
        }
      }
    }
    return null
  }

  // Get selection quads between two chars:
  fun getSelectionQuads(startChar: TextChar, endChar: TextChar): List<Quad> {
    // Walk text structure from start to end, collect all quads
    val quads = mutableListOf<Quad>()
    // ... implementation
    return quads
  }

  // Convert screen coordinates to page coordinates for hit-testing:
  // CRITICAL: Must account for BOTH zoom AND pan offsets
  fun screenToPage(screenX: Float, screenY: Float, zoom: Float, panX: Float, panY: Float): Pair<Float, Float> {
    val pageX = (screenX - panX) / zoom
    val pageY = (screenY - panY) / zoom
    return Pair(pageX, pageY)
  }

  // Hit test for text selection start (on long-press):
  fun findCharAtScreenPoint(screenX: Float, screenY: Float, zoom: Float, panX: Float, panY: Float): TextChar? {
    val (pageX, pageY) = screenToPage(screenX, screenY, zoom, panX, panY)
    return findCharAtPoint(pageX, pageY)  // Uses page coordinates
  }

  // Highlight selection (draw filled quads with alpha):
  // CRITICAL: Transform quads from page coordinates to screen coordinates
  // accounting for BOTH zoom AND pan offsets
  fun drawSelection(canvas: Canvas, quads: List<Quad>, zoom: Float, panX: Float, panY: Float) {
    val paint = Paint().apply {
      color = Color.argb(80, 0, 120, 255) // semi-transparent blue
      style = Paint.Style.FILL
    }
    quads.forEach { quad ->
      val path = Path()
      // Transform each quad vertex: screen = page * zoom + pan
      path.moveTo(quad.ul.x * zoom + panX, quad.ul.y * zoom + panY)
      path.lineTo(quad.ur.x * zoom + panX, quad.ur.y * zoom + panY)
      path.lineTo(quad.lr.x * zoom + panX, quad.lr.y * zoom + panY)
      path.lineTo(quad.ll.x * zoom + panX, quad.ll.y * zoom + panY)
      path.close()
      canvas.drawPath(path, paint)
    }
  }
  ```

  **Verification (Objective):**
  - [ ] **Test PDF**: Use test PDF with known text at known coordinates:
    - Create `test_selection.pdf` with text "Hello World" at position (100, 100) pt
  - [ ] Long-press at (100, 100) → Selection starts at "H"
  - [ ] Drag to (150, 100) → Selection expands to "Hello"
  - [ ] Selected text extracted matches "Hello" (log output)
  - [ ] Selection highlight quads cover expected area (visual + log quad coordinates)

  **Commit**: YES
  - Message: `feat(android): add PDF text selection`

---

- [ ] 6.6 Overlay ink on PDF

  **What to do**:
  - Create composite view with PDF layer + ink layer
  - **Coordinate System**:
    - PDF pages use **points (pt)** as unit (72pt = 1 inch)
    - Ink strokes are stored in page coordinate space (pt)
    - PageGeometry stores `{ kind: "fixed", width: X, height: Y, unit: "pt" }`
    - Zoom/pan transforms apply to both layers uniformly
    - When saving: stroke coordinates are in pt, relative to page origin (0,0 = top-left)
  - Maintain coordinate alignment between PDF and ink layers
  - Save ink as separate page type (MIXED)

  **Coordinate Alignment Implementation:**

  ```kotlin
  // Both PDF and ink use the same coordinate system:
  // - Origin: (0, 0) = top-left of page
  // - Unit: points (pt), 72pt = 1 inch
  // - Direction: x→right, y→down

  // Rendering transform (shared by PDF and ink layers):
  data class ViewTransform(
    val zoom: Float,      // 1.0 = 72 DPI, 2.0 = 144 DPI
    val panX: Float,      // pan offset in screen pixels
    val panY: Float
  )

  // Convert page coordinate to screen pixel:
  fun pageToScreen(pageX: Float, pageY: Float, transform: ViewTransform): Pair<Float, Float> {
    val screenX = pageX * transform.zoom + transform.panX
    val screenY = pageY * transform.zoom + transform.panY
    return Pair(screenX, screenY)
  }

  // Convert screen pixel to page coordinate (for stroke capture):
  fun screenToPage(screenX: Float, screenY: Float, transform: ViewTransform): Pair<Float, Float> {
    val pageX = (screenX - transform.panX) / transform.zoom
    val pageY = (screenY - transform.panY) / transform.zoom
    return Pair(pageX, pageY)
  }

  // Apply same transform to both layers:
  fun renderComposite(canvas: Canvas, transform: ViewTransform) {
    // 1. Render PDF bitmap at current zoom
    val pdfBitmap = renderPdfPage(pageIndex, transform.zoom)
    canvas.drawBitmap(pdfBitmap, transform.panX, transform.panY, null)

    // 2. Render ink strokes with same transform
    strokes.forEach { stroke ->
      drawStroke(canvas, stroke, transform)
    }
  }
  ```

  **References**:
  - `V0-api.md:49-51` - PageGeometry definition
  - `V0-api.md:93-100` - PageMeta with geometry field

  **Verification (Objective with Test PDF):**
  - [ ] **Test PDF**: Create `test_alignment.pdf` with:
    - Cross-hairs at page center (306, 396 pt for US Letter)
    - Grid lines at 100pt intervals
    - Text labels at known coordinates
  - [ ] Draw ink stroke at crosshair intersection → Zoom 2x → Stroke still on crosshair
  - [ ] Draw ink at (100, 100) pt → Pan to corner → Zoom out → Stroke still at grid intersection
  - [ ] Log saved stroke coordinates → Confirm values in pt (not pixels)
  - [ ] Close/reopen note → Ink appears at same position relative to PDF
  - [ ] Page kind is set to "mixed" when ink added to PDF page

  **Commit**: YES
  - Message: `feat(android): overlay ink layer on PDF`

---

### Phase 7: Search

- [ ] 7.1 Add FTS table for search

  **What to do**:
  - Create FTS4 virtual table in Room:
    ```kotlin
    /**
     * FTS4 table for full-text search on recognized text.
     *
     * Room FTS Content Sync: The @Fts4(contentEntity) annotation creates an FTS table
     * that automatically syncs with RecognitionIndexEntity. Room generates triggers
     * to keep the FTS index updated when RecognitionIndexEntity rows are inserted/updated/deleted.
     *
     * The FTS table's rowid automatically maps to the content entity's rowid,
     * allowing JOIN queries to link back to the source entity (and its pageId/noteId).
     */
    @Fts4(contentEntity = RecognitionIndexEntity::class)
    @Entity(tableName = "recognition_fts")
    data class RecognitionFtsEntity(
      val recognizedText: String
    )
    ```
  - **Important**: Room's FTS content sync uses rowid mapping, NOT explicit pageId column
  - The search query (7.2) uses a subquery on rowid to link FTS matches back to RecognitionIndexEntity

  **References**:
  - Room FTS: https://developer.android.com/training/data-storage/room/defining-data#fts
  - Room FTS content sync: https://developer.android.com/reference/androidx/room/Fts4#contentEntity()

  **Verification**:
  - [ ] FTS table created in database
  - [ ] FTS triggers generated by Room (verify in generated schema)

  **Commit**: Group with 7.2

---

- [ ] 7.2 Implement search query

  **What to do**:
  - Add search method to DAO:
    ```kotlin
    /**
     * Search recognized text using FTS4.
     *
     * Room FTS with contentEntity uses rowid mapping:
     * - FTS table's rowid = content entity's rowid (auto-managed by Room)
     * - We query FTS for matching rowids, then JOIN to get full entity
     *
     * The docid in FTS4 = rowid of the content entity.
     */
    @Query("""
      SELECT ri.* FROM recognition_index ri
      INNER JOIN recognition_fts fts ON ri.rowid = fts.docid
      WHERE fts.recognizedText MATCH :query
    """)
    fun search(query: String): Flow<List<RecognitionIndexEntity>>
    ```
  - **Note**: This query uses `docid` (FTS4's internal rowid) to join back to the content entity
  - Room automatically maintains rowid sync between FTS and content tables

  **Verification**:
  - [ ] Query returns matching results
  - [ ] Query correctly links FTS matches to RecognitionIndexEntity (includes pageId, noteId)

  **Commit**: YES
  - Message: `feat(android): add FTS search for recognized text`

---

- [ ] 7.3 Create search UI in Home screen

  **What to do**:
  - Add search bar at top of Home screen
  - Show search results as list
  - Display note title and text snippet

  **Search Data Pipeline (complete flow)**:
  1. **UI Data Model** (what the UI displays):
     ```kotlin
     data class SearchResultItem(
       val noteId: String,
       val noteTitle: String,
       val pageId: String,
       val pageNumber: Int,       // 1-based for display
       val snippetText: String,   // Matched text excerpt (first 100 chars)
       val matchScore: Double     // FTS ranking score for ordering
     )
     ```
  2. **Repository Method** (composes FTS hits with note metadata):

     ```kotlin
     // In NoteRepository.kt
     fun searchNotes(query: String): Flow<List<SearchResultItem>> {
       return recognitionDao.search(query)
         .map { recognitionHits ->
           // For each FTS hit, fetch note title and page info
          recognitionHits.mapNotNull { recognition ->
              // NOTE: Use getById() - the DAO method name (not getByPageId/getByNoteId)
              val page = pageDao.getById(recognition.pageId)
              val note = page?.noteId?.let { noteDao.getById(it) }

             if (note == null || page == null) return@mapNotNull null

             // Create snippet: first 100 chars, with match highlighted
             val snippet = recognition.recognizedText.take(100)

             SearchResultItem(
               noteId = note.noteId,
               noteTitle = note.title.ifEmpty { "Untitled Note" },
               pageId = recognition.pageId,
               pageNumber = page.indexInNote + 1,  // 1-based for display
               snippetText = snippet,
               matchScore = 1.0  // FTS4 doesn't provide score in Room
             )
           }
           // Group by noteId to deduplicate (same note, different pages)
           // Show only first match per note, or group matches
           .distinctBy { it.noteId }
           .sortedByDescending { it.matchScore }
         }
     }
     ```

  3. **ViewModel** (exposes search state to UI):

     ```kotlin
     // In HomeViewModel.kt
     class HomeViewModel(private val repository: NoteRepository) : ViewModel() {
       private val _searchQuery = MutableStateFlow("")
       val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

       val searchResults: StateFlow<List<SearchResultItem>> = _searchQuery
         .debounce(300)  // Wait for typing to stop
         .flatMapLatest { query ->
           if (query.length < 2) flowOf(emptyList())
           else repository.searchNotes(query)
         }
         .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

       fun onSearchQueryChange(query: String) {
         _searchQuery.value = query
       }
     }
     ```

  4. **UI Composable** (displays search results):

     ```kotlin
     @Composable
     fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
       val query by viewModel.searchQuery.collectAsState()
       val results by viewModel.searchResults.collectAsState()

       Column {
         OutlinedTextField(
           value = query,
           onValueChange = viewModel::onSearchQueryChange,
           placeholder = { Text("Search notes...") },
           leadingIcon = { Icon(Icons.Default.Search, null) },
           modifier = Modifier.fillMaxWidth().padding(16.dp)
         )

         if (results.isNotEmpty()) {
           LazyColumn {
             items(results) { result ->
               SearchResultRow(result, onClick = { /* navigate */ })
             }
           }
         } else if (query.length >= 2) {
           Text("No results found", modifier = Modifier.padding(16.dp))
         }
       }
     }
     ```

  **Duplicate Handling**:
  - Multiple pages in same note may match → `distinctBy { it.noteId }` shows only first match
  - Alternative: Group by noteId and show expandable sections with all matching pages

  **Verification**:
  - [ ] Search bar appears on Home
  - [ ] Typing shows matching notes
  - [ ] Results show note title + snippet
  - [ ] Duplicate pages from same note are deduplicated

  **Commit**: Group with 7.4

---

- [ ] 7.4 Navigate from search result

  **What to do**:
  - On search result tap, navigate to note/page
  - Optionally highlight matching page

  **Verification**:
  - [ ] Tap result → Opens correct note

  **Commit**: YES
  - Message: `feat(android): add search UI with navigation`

---

### Phase 8: Integration & Sync Verification

- [ ] 8.1 Multi-page note support

  **What to do**:
  - Add page navigation (prev/next buttons or swipe)
  - Add "New Page" button
  - Display page indicator (e.g., "Page 2 of 5")

  **Verification**:
  - [ ] Can add multiple pages
  - [ ] Can navigate between pages
  - [ ] Each page has independent strokes

  **Commit**: YES
  - Message: `feat(android): add multi-page note support`

---

- [ ] 8.2 End-to-end flow verification ⚠️ **BLOCKED**: Requires physical device for complete workflow (see device-blocker.md)

  **What to do**:
  - Create new note
  - Draw strokes (minimum 5 strokes)
  - Write recognizable text ("hello world")
  - Search for "hello" and find note
  - Close and reopen app
  - Verify everything persisted

  **Verification**:
  - [ ] New note created with valid UUID in Home screen list
  - [ ] All 5 strokes visible after save
  - [ ] Search for "hello" returns the note with snippet
  - [ ] After app force-stop and relaunch: note appears in list
  - [ ] Open note: all strokes exactly as drawn
  - [ ] Recognition text still in database: `adb shell "sqlite3 /data/data/com.onyx.android/databases/*.db 'SELECT recognizedText FROM recognition_index'"`

  **Commit**: NO (verification only)

---

- [ ] 8.3 PDF workflow verification ⚠️ **BLOCKED**: Requires physical device for PDF import and annotation workflow (see device-blocker.md)

  **What to do**:
  - Import PDF
  - Add ink annotations
  - Navigate pages
  - Verify persistence

  **Verification**:
  - [ ] PDF with annotations persists correctly

  **Commit**: NO (verification only)

---

- [ ] 8.4 Verify sync-compatible schema

  **What to do**:
  - Review all entity schemas against v0 API contract (`V0-api.md`)
  - Create schema audit document in `docs/schema-audit.md`
  - Verify field-by-field alignment:

  | Entity       | Field                          | Type                              | v0 API Reference                             |
  | ------------ | ------------------------------ | --------------------------------- | -------------------------------------------- |
  | NoteEntity   | noteId                         | String (UUID)                     | `V0-api.md:35` (NoteId type)                 |
  | NoteEntity   | ownerUserId                    | String                            | `V0-api.md:77` (NoteMeta.ownerUserId)        |
  | NoteEntity   | timestamps                     | Long (Unix ms)                    | `V0-api.md:43` (UnixMs type)                 |
  | PageEntity   | kind                           | "ink"\|"pdf"\|"mixed"\|"infinite" | `V0-api.md:47` (NoteKind type)               |
  | PageEntity   | geometryKind                   | "fixed"\|"infinite"               | `V0-api.md:49-51` (PageGeometry type)        |
  | PageEntity   | geometry (width, height, unit) | Float, String                     | `V0-api.md:49-51` (PageGeometry type)        |
  | PageEntity   | contentLamportMax              | Long                              | `V0-api.md:103` (PageMeta.contentLamportMax) |
  | StrokeEntity | strokeId                       | String (UUID)                     | `V0-api.md:149` (StrokeAdd.strokeId)         |
  | StrokeEntity | style JSON                     | StrokeStyle fields                | `V0-api.md:127-134` (StrokeStyle type)       |
  | StrokeEntity | bounds JSON                    | {x, y, w, h}                      | `V0-api.md:153` (StrokeAdd.bounds)           |
  | StrokeEntity | createdLamport                 | Long                              | `V0-api.md:150` (StrokeAdd.createdLamport)   |
  | StrokePoint  | fields                         | x, y, t, p?, tx?, ty?, r?         | `V0-api.md:136-144` (Point type)             |

  **References**:
  - `V0-api.md` - Complete v0 API contract
  - `.sisyphus/plans/milestone-c-collaboration-sharing.md` - Sync requirements

  **Verification**:
  - [ ] Schema audit document created at `docs/schema-audit.md`
  - [ ] All IDs verified as String (UUID format)
  - [ ] All timestamps verified as Long (Unix ms)
  - [ ] NoteKind values verified: ink, pdf, mixed, infinite (not "page")
  - [ ] PageEntity.geometryKind verified: "fixed" or "infinite"
  - [ ] StrokePoint field names verified: t (not timestamp), p (not pressure), tx/ty (not tiltX/tiltY)
  - [ ] StrokeStyle fields verified: tool, color, baseWidth, minWidthFactor, maxWidthFactor
  - [ ] Tool enum mapping verified: PEN → "pen", HIGHLIGHTER → "highlighter" (distinct values for storage)
  - [ ] Bounds format verified: x, y, w, h (not x0, y0, x1, y1)

  **Commit**: YES
  - Message: `docs(android): verify sync-compatible schema alignment`

---

## Commit Strategy

| Phase | Commit Message                                                           | Key Files         |
| ----- | ------------------------------------------------------------------------ | ----------------- |
| 1     | `chore: initialize monorepo structure`                                   | Root config files |
| 1     | `feat(android): scaffold app with Jetpack Compose`                       | apps/android/     |
| 1     | `chore(android): setup JUnit 5 and MockK testing`                        | build.gradle.kts  |
| 1     | `feat(android): add navigation and theme setup`                          | ui/               |
| 1     | `feat(android): add Home and Editor screens`                             | screens/          |
| 2     | `feat(android): define ink models and surface interface`                 | ink/model/        |
| 3     | `feat(android): add basic ink canvas with Jetpack Ink`                   | ink/ui/           |
| 3     | `feat(android): implement stroke capture with pressure and brush picker` | ink/              |
| 3     | `feat(android): implement stroke eraser tool`                            | ink/              |
| 3     | `feat(android): implement undo/redo for strokes`                         | ink/              |
| 3     | `feat(android): implement zoom and pan for ink canvas`                   | ink/              |
| 4     | `feat(android): define Room entities for notes, pages, strokes`          | data/entity/      |
| 4     | `feat(android): create Room database with DAOs`                          | data/             |
| 4     | `feat(android): add stroke serialization and repository`                 | data/             |
| 4     | `feat(android): integrate persistence with note editor`                  | editor/           |
| 4     | `feat(android): implement device identity with UUID`                     | device/           |
| 5     | `feat(android): initialize MyScript SDK`                                 | recognition/      |
| 5     | `feat(android): implement realtime MyScript recognition`                 | recognition/      |
| 5     | `feat(android): store recognition results in database`                   | recognition/      |
| 6     | `feat(android): add PDF import via file picker`                          | pdf/              |
| 6     | `feat(android): render PDF pages with zoom/pan`                          | pdf/              |
| 6     | `feat(android): add PDF text selection`                                  | pdf/              |
| 6     | `feat(android): overlay ink layer on PDF`                                | pdf/              |
| 7     | `feat(android): add FTS search for recognized text`                      | data/             |
| 7     | `feat(android): add search UI with navigation`                           | screens/          |
| 8     | `feat(android): add multi-page note support`                             | editor/           |
| 8     | `docs(android): verify sync-compatible schema alignment`                 | docs/             |

---

## Success Criteria

### Verification Commands

```bash
# All Gradle commands run from apps/android/ directory
cd apps/android

# Build Android app
./gradlew :app:assembleDebug  # Expected: BUILD SUCCESSFUL

# Run tests
./gradlew :app:test           # Expected: All tests pass
```

### Final Checklist

- [ ] All "Must Have" features present
- [ ] All "Must NOT Have" items absent
- [ ] App builds and runs on tablet (BUILD VERIFIED - APK exists; RUNTIME requires physical device)
- [ ] Multi-page notes work
- [ ] Ink capture with pressure/tilt ⚠️ **BLOCKED**: Requires physical stylus hardware (see device-blocker.md)
- [ ] Stroke eraser works
- [ ] Undo/redo works
- [ ] Zoom/pan works
- [ ] Recognition produces searchable text ⚠️ **BLOCKED**: Requires runtime verification with MyScript on real strokes (see device-blocker.md)
- [ ] PDF viewing with text selection and ink overlay
- [ ] Local search finds notes by content
- [ ] Device ID persists correctly
- [ ] Schema matches v0 API contract (verified via docs/schema-audit.md)
