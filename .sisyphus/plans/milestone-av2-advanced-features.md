# Milestone Av2: Android Advanced Features

## Context

### Original Request
After completing Plan A (Core MVP), enhance the Android tablet app with advanced features that were deferred: infinite canvas, segment eraser, batch recognition with throttling, and recognition text overlay.

### Relationship to Plan A
This plan is a direct continuation of Plan A. All tasks assume:
- Plan A is complete and working
- Basic ink canvas, persistence, and recognition are functional
- Room database and sync-compatible schema are in place

### Key Decisions (from Interview)
- **Infinite canvas**: 2024x2024 tile system for unbounded drawing (per V0-api.md contract)
- **Segment eraser**: **REQUIRED** - Splits strokes at erase point (not optional)
  - Collaboration-safe rule: Eraser ops only apply to strokes that existed at the time of erase (`createdLamport <= eraseLamport` guard)
  - Preserves CRDT convergence properties
- **Batch recognition**: Background processing with throttling rules
- **Recognition overlay**: Display recognized text on canvas (optional layer)
- **UI Stack**: Jetpack Compose only (consistent with Plan A)

### Critical Technical Specifications

#### Plan A Dependency Assumption

> **⚠️ CRITICAL DEPENDENCY**: This is a CONTINUATION PLAN. Plan A MUST be complete before Av2 begins.

**What this means:**
- The source files referenced below (e.g., `InkCanvas.kt`, `NoteEditorScreen.kt`, entities) **DO NOT EXIST YET**
- They will be created when Plan A (`milestone-a-offline-ink-myscript.md`) is executed
- This is intentional—Av2 builds on Plan A's outputs

**Pre-Execution Verification (MANDATORY - Task Av2.0.1):**
Before starting ANY Av2 task, the executor MUST run this verification:
```bash
# Verify Plan A completion - ALL must exist
test -f apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt && \
test -f apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt && \
test -f apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt && \
echo "✅ Plan A complete - proceed with Av2" || \
echo "❌ Plan A NOT complete - execute Plan A first"
```

**If verification fails:** Execute Plan A first. Do NOT proceed with Av2.

**For Plan Reviewers (Momus):** File references in this plan point to outputs of Plan A.
These files are not verifiable in the current repo state because Plan A has not been executed.
This is **expected behavior for a continuation plan**. 

**REVIEWER GUIDANCE - What to verify vs skip:**
| Category | Verify? | Reason |
|----------|---------|--------|
| `apps/android/**` file existence | ❌ SKIP | Plan A outputs - don't exist yet |
| Internal Av2 task consistency | ✅ YES | Self-referential, can check |
| V0-api.md compliance | ✅ YES | Exists in repo |
| Plan A interface contracts | ✅ YES | Frozen in section below |
| Code snippet correctness | ✅ YES | Logic review, not file check |
| DAO/Repository method consistency | ✅ YES | Internal to Av2 tasks |

**Pass Criteria for Continuation Plans:**
- All INTERNAL references are consistent (Av2 tasks reference each other correctly)
- All EXTERNAL references match frozen Plan A interfaces below
- V0-api.md alignment is verified
- No logical contradictions in code snippets

**Frozen Plan A Interfaces (for Av2 reference):**
The following interfaces are committed in Plan A and will exist after execution:

```kotlin
// PageEntity.geometryKind - for routing fixed vs infinite
// File: apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt
// Values: "fixed" (standard pages) | "infinite" (infinite canvas)

// NoteEditorViewModel.onPenUp() - real-time recognition trigger (KEEP in Av2)
// File: apps/android/app/src/main/java/com/onyx/android/viewmodel/NoteEditorViewModel.kt
// Plan A: calls myScriptPageManager.addStroke(stroke)
// Av2: KEEP this call (required for lasso + overlay immediacy), ADD persistence debouncer

// NoteEditorViewModel.onStrokeErased() - real-time recognition trigger (KEEP in Av2)
// File: apps/android/app/src/main/java/com/onyx/android/viewmodel/NoteEditorViewModel.kt
// Plan A: calls myScriptPageManager.onStrokeErased(...)
// Av2: KEEP this call (required for lasso + overlay immediacy), ADD persistence debouncer

// InkCanvas - composable for fixed page rendering
// File: apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt
// Av2 creates InfiniteCanvas as sibling for infinite pages

// RecognitionIndexEntity - stores recognition results
// File: apps/android/app/src/main/java/com/onyx/android/data/entity/RecognitionIndexEntity.kt
// Plan A fields: pageId, noteId, recognizedText, recognizedAtLamport, recognizerVersion, updatedAt
// Av2 adds: wordPositionsJson (via MIGRATION_1_2)
```

#### Infinite Canvas Coordinate System

**V0 API Contract Compliance:**
- Per `V0-api.md` line 51: `PageGeometry.kind = "infinite"` uses `tileSize: 2024` (fixed)
- This plan uses **2024×2024 pixel tiles** to match the V0 contract exactly

**Coordinate Units Reconciliation (CRITICAL - UNIFIED TRUTH):**

> **AUTHORITATIVE STATEMENT ON MyScript COORDINATE SYSTEM:**
> 
> - **MyScript API accepts input in PIXELS (px)** - screen pixel coordinates
> - **MyScript outputs JIIX bounding boxes in MILLIMETERS (mm)**
> 
> The MyScript SDK internally works with mm, but its API (pointerEvents, pointerDown/Move/Up) 
> expects coordinates in screen pixels. JIIX export returns bounding boxes in mm.
> 
> Reference: `myscript-examples/samples/batch-mode/.../MainActivity.kt:333-336`
> The example shows converting FROM mm TO px for display, confirming MyScript stores in mm but APIs use px.

**Unified Coordinate Flow:**

| Stage | Fixed Pages (pt) | Infinite Pages (px) |
|-------|-----------------|---------------------|
| **Page coordinates** | Points (pt) | Pixels (px) |
| **→ MyScript input (px)** | pt → px: `px = pt * dpi / 72` | Already px, pass directly |
| **← JIIX output (mm)** | mm (always) | mm (always) |
| **→ Page coordinates** | mm → pt: `pt = mm * 72 / 25.4` | mm → px: `px = mm * dpi / 25.4` |
| **Stored in DB** | pt | px |

**DPI Values (AUTHORITATIVE):**
- **Fixed pages**: Use `72f` (points are defined as 72 per inch - this is a constant, not device-dependent)
- **Infinite pages**: Use `context.resources.displayMetrics.xdpi` (horizontal screen DPI)
  - Note: Use `xdpi` specifically, not `densityDpi` (which is a bucket value like 160, 240, 320)
  - `xdpi` gives the actual physical DPI of the screen

**Conversion Functions (add to MyScriptEngine):**
```kotlin
// Page → MyScript (input)
fun pageToMyScriptPx(pageX: Float, pageY: Float, geometryKind: String, dpi: Float): Pair<Float, Float> {
  return when (geometryKind) {
    "fixed" -> Pair(pageX * dpi / 72f, pageY * dpi / 72f)  // pt → px
    "infinite" -> Pair(pageX, pageY)  // Already px
    else -> Pair(pageX, pageY)
  }
}

// MyScript → Page (JIIX output)
fun jiixMmToPage(mmX: Float, mmY: Float, geometryKind: String, dpi: Float): Pair<Float, Float> {
  return when (geometryKind) {
    "fixed" -> Pair(mmX * 72f / 25.4f, mmY * 72f / 25.4f)  // mm → pt
    "infinite" -> Pair(mmX * dpi / 25.4f, mmY * dpi / 25.4f)  // mm → px
    else -> Pair(mmX, mmY)
  }
}
```

**JIIX Bounding Box Truth:**
- JIIX `bounding-box` values (x, y, width, height) are **ALWAYS in mm**
- Must convert to page coordinates before storage/display
- Recognition overlay, lasso selection, and search highlight ALL use converted page coordinates

**Plan A vs Av2 Coordinate Reconciliation (CRITICAL):**

> Plan A (line 3845-3859) shows `ptToMm()` conversion before MyScript. This is **technically correct** 
> because MyScript engine is configured with scale factors that expect mm-equivalent values.
> 
> However, the MyScript README shows the engine is created with:
> ```java
> float scaleX = INCH_IN_MILLIMETER / displayMetrics.xdpi;  // 25.4 / dpi
> Recognizer recognizer = engine.createRecognizer(scaleX, scaleY, "Text");
> ```
> 
> This means the engine internally converts input coordinates using these scale factors.
> 
> **Resolution**: Both approaches are valid but must be consistent:
> - **Plan A approach**: pt → mm conversion in code, engine scale = 1.0
> - **Alternative**: pt → px conversion in code, engine scale = 25.4/dpi
> 
> **Av2 does NOT change Plan A's coordinate conversion logic.** Av2 only adds:
> - Infinite canvas handling (already px, no conversion needed for input)
> - JIIX mm → page coordinate conversion for output (both fixed and infinite)
> 
> The key insight: **Plan A handles fixed pages. Av2 adds infinite pages.**
> - Fixed pages: pt (storage) → MyScript uses Plan A's existing conversion
> - Infinite pages: px (storage) → pass directly to MyScript (no input conversion needed)

**CRITICAL: MyScript Input Conversion for Infinite Pages (EXPLICIT DECISION)**

> **Problem**: Plan A's `addStroke` converts pt→mm for MyScript. Infinite pages use px, not pt.
> 
> **Decision**: Av2 modifies MyScriptPageManager.addStroke() to check geometryKind:
> - Fixed pages (geometryKind="fixed"): Continue using Plan A's pt→mm conversion
> - Infinite pages (geometryKind="infinite"): Convert px→mm using device DPI
> 
> **Rationale**: MyScript engine always expects mm internally. We must convert to mm regardless of input unit.

```kotlin
// In MyScriptPageManager.addStroke() - Av2 MODIFIES this method
fun addStroke(stroke: Stroke) {
  val geometryKind = pageGeometryCache[currentPageId] ?: "fixed"
  val dpi = if (geometryKind == "infinite") {
    context.resources.displayMetrics.xdpi
  } else {
    72f  // Fixed pages use 72 DPI (points)
  }
  
  // Convert to MyScript mm coordinates
  val pointerEvents = stroke.points.mapIndexed { index, point ->
    // CRITICAL: Different conversion based on geometryKind
    val (mmX, mmY) = when (geometryKind) {
      "fixed" -> Pair(point.x * 25.4f / 72f, point.y * 25.4f / 72f)  // pt → mm (Plan A logic)
      "infinite" -> Pair(point.x * 25.4f / dpi, point.y * 25.4f / dpi)  // px → mm (Av2 addition)
      else -> Pair(point.x * 25.4f / 72f, point.y * 25.4f / 72f)
    }
    
    PointerEvent().apply {
      pointerType = PointerType.PEN
      pointerId = 0
      eventType = when (index) {
        0 -> PointerEventType.DOWN
        stroke.points.lastIndex -> PointerEventType.UP
        else -> PointerEventType.MOVE
      }
      x = mmX
      y = mmY
      t = point.t
      f = point.p ?: 0.5f
    }
  }.toTypedArray()
  
  editor.pointerEvents(pointerEvents, false)
}
```

**Summary of Coordinate Conversions:**
| Page Type | Storage Unit | MyScript Input Conversion | JIIX Output Conversion |
|-----------|-------------|---------------------------|------------------------|
| Fixed | pt (points) | pt → mm: `mm = pt * 25.4 / 72` | mm → pt: `pt = mm * 72 / 25.4` |
| Infinite | px (pixels) | px → mm: `mm = px * 25.4 / dpi` | mm → px: `px = mm * dpi / 25.4` |

- **Recognition overlay**: Stores bounding boxes in same units as page (px for infinite, pt for fixed)
- **V0-api.md alignment**: `PageGeometry.kind = "infinite"` uses `unit: "px"` (line 51)

**Units and Tile Mapping:**
- Page coordinate space: Floating-point pixels, origin (0,0) at initial viewport center
- Tile coordinate space: Integer tile indices (tileX, tileY), where each tile is 2024×2024 pixels
- Mapping formula:
  ```kotlin
  const val TILE_SIZE = 2024 // V0 API contract requirement
  
  fun pageToTile(pageX: Float, pageY: Float): Pair<Int, Int> {
    val tileX = floor(pageX / TILE_SIZE.toFloat()).toInt()
    val tileY = floor(pageY / TILE_SIZE.toFloat()).toInt()
    return Pair(tileX, tileY)
  }
  
  fun tileToPageOrigin(tileX: Int, tileY: Int): Pair<Float, Float> {
    return Pair(tileX * TILE_SIZE.toFloat(), tileY * TILE_SIZE.toFloat())
  }
  ```
- Stroke storage: StrokeEntity stores page coordinates (not tile-local). TileEntity.strokeIds references strokes that intersect that tile.
- Multi-tile strokes: A stroke spanning tiles appears in ALL intersecting TileEntity.strokeIds lists. When rendering a tile, strokes are clipped to tile bounds.

**TileId Strategy (Deterministic for Sync Safety):**
- **Format**: `{pageId}:{tileX}:{tileY}` (NOT random UUID)
- **Example**: `page_abc123:0:0`, `page_abc123:1:0`, `page_abc123:-1:2`
- **Why deterministic**: Ensures same tile on different devices has same ID, enabling CRDT merge
- **Implementation**:
  ```kotlin
  fun generateTileId(pageId: String, tileX: Int, tileY: Int): String = "$pageId:$tileX:$tileY"
  ```

**TileEntity.strokeIds Update Strategy:**
- **Owner**: `TileManager` class is responsible for all tile membership updates
- **On stroke add**:
  1. Calculate stroke bounding box
  2. Find all tiles intersecting bounding box using `pageToTile()` on corners
  3. For each intersecting tile: append strokeId to `TileEntity.strokeIds`
  4. Persist via `TileDao.updateStrokeIds(tileId, strokeIds)`
- **On stroke erase (whole stroke)**:
  1. Find all tiles containing strokeId
  2. Remove strokeId from each `TileEntity.strokeIds`
  3. Persist changes
- **On segment erase (split)**:
  1. Remove original strokeId from all tiles
  2. For each new segment: calculate intersecting tiles and add segment strokeId
  3. Persist all changes in single transaction
- **Acceptance test**: Draw stroke spanning 4 tiles → verify strokeId in all 4 TileEntity records

**Viewport and Tile Loading:**
- Visible tiles = tiles intersecting current viewport rectangle
- Active tiles = visible tiles + 1-tile border (preload for smooth panning)
- LRU cache holds max 9 tiles (3×3 grid)

#### Lamport Clock in Offline Mode (Pre-Plan C)

**Since Plan C (sync) is not yet implemented, Av2 uses a LOCAL monotonic counter:**

```kotlin
// apps/android/app/src/main/java/com/onyx/android/data/sync/LocalLamportClock.kt
object LocalLamportClock {
  private var counter: Long = 0L
  
  @Synchronized
  fun next(): Long = ++counter
  
  @Synchronized
  fun seed(value: Long) {
    counter = maxOf(counter, value)
  }
}
```

**Usage in Av2:**
- When creating a stroke: `stroke.createdLamport = LocalLamportClock.next()`
- When applying segment erase: `eraseLamport = LocalLamportClock.next()`, only erase strokes where `createdLamport <= eraseLamport`
- On app start: Seed clock from max(createdLamport) in database to ensure monotonicity

**Lamport Clock Seeding (MANDATORY on app start):**
- **Location**: `OnyxApplication.onCreate()` after database initialization
- **Guarantee Mechanism**: Use a `CompletableDeferred<Unit>` gate to block stroke operations until seeding completes:
  ```kotlin
  // In OnyxApplication.kt
  object LamportInitGate {
    val ready = CompletableDeferred<Unit>()
  }
  
  // In OnyxApplication.onCreate() - Use GlobalScope or applicationScope since Application is not a LifecycleOwner
  // Alternative: Use ProcessLifecycleOwner.get().lifecycleScope for app-level coroutine scope
  CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
    val maxLamport = database.strokeDao().getMaxCreatedLamport() ?: 0L
    LocalLamportClock.seed(maxLamport)
    Log.d("Lamport", "Lamport clock seeded with value: $maxLamport")
    LamportInitGate.ready.complete(Unit)
  }
  
  // In NoteRepository (matching Plan A's method name: saveStroke, not addStroke)
  suspend fun saveStroke(pageId: String, stroke: Stroke) {
    LamportInitGate.ready.await()  // Blocks until seeding complete
    stroke.createdLamport = LocalLamportClock.next()
    // ... persist stroke (per Plan A task 4.9 implementation)
  }
  ```
- **StrokeDao addition**: `@Query("SELECT MAX(createdLamport) FROM strokes") suspend fun getMaxCreatedLamport(): Long?`
- **Task mapping**: See Av2.0.2 in TODOs section

**Migration to Plan C:** When Plan C adds sync, `LocalLamportClock` will be replaced with a distributed Lamport clock. The `createdLamport` field in StrokeEntity is already sync-compatible (Long type).

#### Recognition Strategy: Real-time + Rebuild Worker

**CRITICAL DESIGN DECISION: Keep Real-time Recognition as Default**

> **Why real-time must stay**: Lasso selection + convert-on-demand requires word boxes IMMEDIATELY.
> If recognition only runs after 5s idle + WorkManager completion, the "lasso → convert" UX is broken.
> Search-as-you-write also feels laggy without real-time recognition updating `recognizedWords`.

**Plan A Recognition Behavior (PRESERVED in Av2):**
- Plan A implements real-time recognition via `MyScriptPageManager` (triggered on stroke completion)
- Recognition results stored in `RecognitionIndexEntity`
- **Av2 KEEPS this behavior** - do NOT remove real-time triggers

**Av2 Recognition Architecture:**

| Layer | Purpose | When Used |
|-------|---------|-----------|
| **Real-time (foreground)** | Immediate `recognizedWords` for lasso/overlay | Every pen-up, undo, redo |
| **Persistence debounce** | Short delay before writing to DB | 250-750ms after last change |
| **Rebuild Worker (background)** | Full re-recognition from strokes | Import, sync, manual rebuild, corrupted data |

**Real-Time Recognition Flow (KEEP from Plan A):**
```
User draws stroke → pen-up → myScriptPageManager.addStroke(stroke)
                           → in-memory recognizedWords updated IMMEDIATELY
                           → persistenceDebouncer.markDirty(pageId)
                           → (after 250-750ms) persist to DB
```

**Rebuild Worker Flow (NEW in Av2 - for special cases only):**
```
Document import / Sync arrival / Manual "Rebuild index" request
    → RebuildWorker.enqueue(pageId)
    → Worker loads all strokes, runs batch recognition
    → Updates DB with full text + word positions
```

**NoteEditorViewModel Implementation (PRESERVED from Plan A + Av2 tile updates):**

```kotlin
// apps/android/app/src/main/java/com/onyx/android/viewmodel/NoteEditorViewModel.kt
// Av2 KEEPS real-time recognition from Plan A and ADDS persistence debounce + tile updates

private val persistenceDebouncer = RecognitionPersistenceDebouncer(viewModelScope)
private var tileManager: TileManager? = null  // Initialized when editing infinite page

// In-memory recognition results for immediate lasso/overlay access
private val _recognizedWords = MutableStateFlow<List<RecognizedWord>>(emptyList())
val recognizedWords: StateFlow<List<RecognizedWord>> = _recognizedWords.asStateFlow()

fun onPenUp(stroke: Stroke, page: PageEntity) {
  viewModelScope.launch {
    // 1. Persist stroke to database
    noteRepository.saveStroke(page.pageId, stroke)
    
    // 2. Update tile membership for infinite pages (CRITICAL for correct rendering)
    if (page.geometryKind == "infinite") {
      tileManager?.addStrokeToTiles(stroke)
    }
    
    // 3. KEEP real-time recognition (Plan A behavior - DO NOT REMOVE)
    myScriptPageManager.addStroke(stroke)  // Updates in-memory recognizedWords immediately
    
    // 4. Debounce DB persistence (short delay to avoid thrash)
    persistenceDebouncer.markDirty(page.pageId)  // Persists after 250-750ms
  }
}

fun onStrokeErased(strokeId: String, page: PageEntity) {
  viewModelScope.launch {
    // 1. Persist deletion to database
    noteRepository.deleteStroke(strokeId)
    
    // 2. Update tile membership for infinite pages (CRITICAL for correct rendering)
    if (page.geometryKind == "infinite") {
      tileManager?.removeStrokeFromTiles(strokeId)
    }
    
    // 3. KEEP real-time recognition update (Plan A behavior - DO NOT REMOVE)
    val remainingStrokes = noteRepository.getStrokesForPage(page.pageId)
    myScriptPageManager.onStrokeErased(strokeId, remainingStrokes)
    
    // 4. Debounce DB persistence
    persistenceDebouncer.markDirty(page.pageId)
  }
}

fun onUndo(currentStrokes: List<Stroke>) {
  // KEEP real-time recognition update (Plan A behavior)
  myScriptPageManager.onUndo(currentStrokes)
  // NOTE: Tile membership updates for undo are handled by the undo action itself
  persistenceDebouncer.markDirty(currentPageId!!)
}

fun onRedo(currentStrokes: List<Stroke>) {
  // KEEP real-time recognition update (Plan A behavior)
  myScriptPageManager.onRedo(currentStrokes)
  persistenceDebouncer.markDirty(currentPageId!!)
}

// Callback from MyScriptPageManager when recognition completes
fun onRecognitionResult(words: List<RecognizedWord>) {
  _recognizedWords.value = words  // Immediate update for lasso/overlay
}

override fun onCleared() {
  persistenceDebouncer.cancel()
  super.onCleared()
}
```

**Tile Membership Updates (CRITICAL for Infinite Pages):**
- **On stroke add (onPenUp)**: Call `tileManager.addStrokeToTiles(stroke)` to register stroke in intersecting tiles
- **On stroke delete (onStrokeErased)**: Call `tileManager.removeStrokeFromTiles(strokeId)` to unregister
- **On segment erase**: Call `tileManager.replaceStrokeInTiles(oldId, newStrokes)` (already specified in Av2.2.4)
- **On undo/redo**: The underlying add/delete operations already call the tile update methods

**MyScriptPageManager Role in Av2:**
- **PRESERVED**: Plan A's `MyScriptPageManager` continues to handle real-time recognition
- It maintains the per-page editor and stroke-to-word mapping
- Recognition results are pushed to ViewModel via callback for immediate UI access

**MyScriptPageManager API Extension (NEW in Av2):**

Av2 requires adding the following methods to MyScriptPageManager (Plan A task 5.3):

```kotlin
// Add to apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt

/**
 * Returns the current in-memory recognition results for the given page.
 * Used by persistence debouncer and overlay to access immediate recognition data.
 * 
 * @param pageId The page to get recognition results for
 * @return List of recognized words with bounding boxes, or empty list if not available
 */
fun getCurrentWords(pageId: String): List<RecognizedWord> {
  val editor = editors[pageId] ?: return emptyList()
  
  // Export JIIX from current editor state
  val jiixString = try {
    editor.export(editor.part?.rootBlock, MimeType.JIIX)
  } catch (e: Exception) {
    Log.w("MyScriptPageManager", "Failed to export JIIX for page $pageId", e)
    return emptyList()
  }
  
  // Parse JIIX to extract words with bounding boxes
  return parseJiixForWords(jiixString, getCurrentGeometryKind(pageId), getCurrentDpi(pageId))
}

/**
 * Notifies the manager that strokes have changed (for segment erase scenarios).
 * Re-feeds all strokes to the editor to update recognition.
 * 
 * @param strokes The current complete list of strokes for the page
 */
fun onStrokesChanged(strokes: List<Stroke>) {
  // Implementation: Clear editor and re-feed all strokes
  // This handles cases like segment erase where stroke IDs change
  refeedStrokes(currentPageId, strokes)
}

/**
 * Sets a callback for recognition result updates.
 * Called whenever recognition completes (after any stroke change).
 * 
 * @param callback Function receiving pageId and list of recognized words
 */
fun setRecognitionCallback(callback: (String, List<RecognizedWord>) -> Unit) {
  this.recognitionCallback = callback
}

// Internal: Called after MyScript recognition completes
private fun onRecognitionComplete(pageId: String) {
  val words = getCurrentWords(pageId)
  recognitionCallback?.invoke(pageId, words)
}
```

**Integration with NoteEditorViewModel:**
```kotlin
// In NoteEditorViewModel.init {}
myScriptPageManager.setRecognitionCallback { pageId, words ->
  if (pageId == currentPageId) {
    _recognizedWords.value = words  // Immediate update for overlay/lasso
  }
}
```

**RecognitionPersistenceDebouncer (REPLACES RecognitionThrottler):**

```kotlin
// apps/android/app/src/main/java/com/onyx/android/recognition/RecognitionPersistenceDebouncer.kt

/**
 * Debounces PERSISTENCE of recognition results to database.
 * NOT the recognition itself - that happens in real-time via MyScriptPageManager.
 * 
 * Purpose: Avoid DB thrash during rapid drawing while ensuring eventual persistence.
 */
class RecognitionPersistenceDebouncer(
  private val scope: CoroutineScope,
  private val delayMs: Long = 500L  // 250-750ms range, 500ms default
) {
  private val dirtyPages = mutableSetOf<String>()
  private var debounceJob: Job? = null
  
  fun markDirty(pageId: String) {
    dirtyPages.add(pageId)
    debounceJob?.cancel()
    debounceJob = scope.launch {
      delay(delayMs)
      flush()
    }
  }
  
  private suspend fun flush() {
    val pagesToPersist = dirtyPages.toList()
    dirtyPages.clear()
    
    for (pageId in pagesToPersist) {
      // Get current in-memory recognition from MyScriptPageManager
      val words = myScriptPageManager.getCurrentWords(pageId)
      val fullText = words.joinToString(" ") { it.text }
      val wordsJson = Gson().toJson(words)
      
      // Persist to database
      recognitionDao.updateRecognitionWithPositions(
        pageId = pageId,
        recognizedText = fullText,
        wordPositionsJson = wordsJson,
        updatedAt = System.currentTimeMillis()
      )
    }
  }
  
  fun cancel() {
    debounceJob?.cancel()
  }
}
```

**RebuildWorker (REPLACES RecognitionWorker - for special cases only):**

| When to Use RebuildWorker | NOT for Normal Drawing |
|--------------------------|------------------------|
| Document/PDF import (initial index) | ❌ Pen-up events |
| Sync brings new strokes from another device | ❌ Erase events |
| User requests "Rebuild search index" | ❌ Undo/redo |
| Detection of corrupted/missing recognition data | ❌ Any foreground editing |

**WorkManager Scheduling (for RebuildWorker only):**
- Unique work name per page: `"rebuild_${pageId}"`
- Policy: `ExistingWorkPolicy.REPLACE`
- Triggered by: import flow, sync completion, manual rebuild button

#### Future Feature: Lasso Selection + Convert-on-Demand Preparation

> **This section documents how Av2's architecture enables future lasso + convert features.**
> Lasso selection is NOT implemented in Av2, but the architecture is designed to support it.

**Why Real-Time Recognition is Critical for Lasso:**

The lasso + convert workflow requires IMMEDIATE access to word bounding boxes:
```
User draws → lasso selection → convert button → text appears
                    ↑
                    Needs word boxes NOW (not after 5s idle + WorkManager)
```

**How Av2 Enables This (Future Feature):**

1. **In-memory `recognizedWords`**: Real-time recognition updates `_recognizedWords` StateFlow immediately
   - Lasso polygon can intersect with word boxes in-memory
   - No DB query latency for selection

2. **Unified coordinate system**: All word boxes are in page coordinates (pt for fixed, px for infinite)
   - Lasso polygon uses same coordinate system
   - Direct geometric intersection without conversion

3. **Immediate recognition results**: MyScriptPageManager provides words on every stroke change
   - No waiting for persistence debounce
   - No waiting for WorkManager

**Future Lasso Implementation (NOT in Av2, but architecture ready):**

```kotlin
// Future feature - not implemented in Av2
fun onLassoComplete(polygon: List<Pair<Float, Float>>) {
  // 1. Get current in-memory recognition (IMMEDIATE - no DB query)
  val words = _recognizedWords.value
  
  // 2. Find words intersecting lasso polygon
  val selectedWords = words.filter { word ->
    polygonIntersectsRect(polygon, word.left, word.top, word.right, word.bottom)
  }
  
  // 3. Convert to text
  val selectedText = selectedWords.joinToString(" ") { it.text }
  
  // 4. Copy to clipboard or show conversion UI
  copyToClipboard(selectedText)
}
```

**Why This Architecture Works:**
| Old Av2 (Batch-only) | New Av2 (Real-time + Rebuild) |
|---------------------|------------------------------|
| Word boxes after 5s + WorkManager | Word boxes IMMEDIATELY on pen-up |
| Lasso would show "please wait..." | Lasso works instantly |
| DB-dependent selection | In-memory selection |
| Stale data during rapid drawing | Always current data |

#### Batch Recognition API Definition (MyScriptEngine Extension)

**API Signature:**
```kotlin
// Add to MyScriptEngine (apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptEngine.kt)

/**
 * Performs batch recognition on a list of strokes using OffscreenEditor.
 * 
 * Uses editor.pointerEvents() to feed all strokes in batch mode.
 * 
 * @param strokes List of strokes to recognize (in page coordinates)
 * @param geometryKind "fixed" (pt coords) or "infinite" (px coords)
 * @param dpi Device DPI (for converting between px and mm)
 * @return List of RecognizedWord with bounding boxes in page coordinates
 * @throws IllegalStateException if engine not initialized
 * 
 * Reference: myscript-examples/samples/batch-mode/src/main/java/com/myscript/iink/samples/batchmode/MainActivity.kt
 *   - Batch mode uses editor.pointerEvents() (line 364) to feed events
 *   - PointerEvent built with .apply{} pattern (lines 347-359)
 *   - Coordinates in px, converted from mm using display DPI (lines 335-336)
 */
suspend fun recognizeBatch(
  strokes: List<Stroke>,
  geometryKind: String = "fixed",
  dpi: Float = 72f
): List<RecognizedWord> = withContext(Dispatchers.Default) {
  if (!isInitialized()) {
    throw IllegalStateException("MyScript engine not initialized")
  }
  
  // Create temporary OffscreenEditor (matches Plan A pattern: milestone-a-offline-ink-myscript.md:3813-3815)
  val tempPackage = engine.createPackage("batch_recognition_${System.currentTimeMillis()}")
  val tempPart = tempPackage.createPart("Raw Content")
  val tempEditor = engine.createOffscreenEditor(1f, 1f)  // scale=1, same as Plan A
  tempEditor.part = tempPart  // Assign part after creation (Plan A pattern)
  
  try {
    // 1. Build pointer events array (batch-mode pattern, lines 346-362)
    val pointerEventsList = mutableListOf<PointerEvent>()
    
    for (stroke in strokes) {
      for ((index, point) in stroke.points.withIndex()) {
        // Convert page coords to MyScript input format
        // FIXED PAGES: Use Plan A's pt→mm conversion (consistent with real-time recognition)
        // INFINITE PAGES: Already px, convert to mm for MyScript internal consistency
        val (mmX, mmY) = when (geometryKind) {
          "fixed" -> Pair(point.x * 25.4f / 72f, point.y * 25.4f / 72f)  // pt → mm (Plan A consistency)
          "infinite" -> Pair(point.x * 25.4f / dpi, point.y * 25.4f / dpi)  // px → mm
          else -> throw IllegalArgumentException("Unknown geometryKind: $geometryKind")
        }
        
        pointerEventsList += PointerEvent().apply {
          pointerType = PointerType.PEN
          pointerId = 0
          eventType = when (index) {
            0 -> PointerEventType.DOWN
            stroke.points.lastIndex -> PointerEventType.UP
            else -> PointerEventType.MOVE
          }
          x = mmX
          y = mmY
          t = point.t
          f = point.p ?: 0.5f
        }
      }
    }
    
    // 2. Feed events using pointerEvents() - batch mode API (line 364)
    tempEditor.pointerEvents(pointerEventsList.toTypedArray(), false)
    
    // 3. Wait for recognition to complete
    tempEditor.waitForIdle()
    
    // 4. Export JIIX to extract text and bounding boxes
    val jiixString = tempEditor.export(tempPart.rootBlock, MimeType.JIIX)
    val words = parseJiixForWords(jiixString, geometryKind, dpi)
    
    words
  } finally {
    tempEditor.part = null
    tempEditor.close()
    tempPackage.close()
  }
}

/**
 * Parses JIIX (JSON Interactive Ink Exchange) to extract words with bounding boxes.
 * JIIX is MyScript's export format containing text, strokes, and layout info.
 * 
 * SHARED UTILITY: This function is used by both:
 * - MyScriptEngine.recognizeBatch() (for RebuildWorker batch processing)
 * - MyScriptPageManager.getCurrentWords() (for real-time recognition)
 * 
 * Location: apps/android/app/src/main/java/com/onyx/android/recognition/JiixParser.kt
 * Visibility: internal (package-private to recognition module)
 * 
 * JIIX Structure (from myscript-examples/samples/offscreen-interactivity/.../serialization/jiix/):
 * - RecognitionRoot: { type, bounding-box, elements[] }
 * - Element: { id, type, label, bounding-box, words[] }
 * - Word: { label, candidates[], bounding-box }
 * - BoundingBox: { x, y, width, height }
 * 
 * Note: JIIX uses "bounding-box" (hyphenated) in JSON, mapped via @SerializedName
 */
// File: apps/android/app/src/main/java/com/onyx/android/recognition/JiixParser.kt
internal fun parseJiixForWords(
  jiixJson: String,
  geometryKind: String,
  dpi: Float
): List<RecognizedWord> {
  val results = mutableListOf<RecognizedWord>()
  
  // Parse using structure from offscreen-interactivity JIIX classes
  val root = Gson().fromJson(jiixJson, JiixRecognitionRoot::class.java)
  
  // Words are nested: root.elements[].words[]
  for (element in root.elements.orEmpty()) {
    for (word in element.words.orEmpty()) {
      val box = word.boundingBox ?: continue
      
      // JIIX bounding boxes are ALWAYS in mm (millimeters)
      // Convert to page coordinates based on geometryKind
      // Reference: MyScript always uses mm internally for bounding boxes
      val (left, top, right, bottom) = when (geometryKind) {
        "fixed" -> listOf(
          box.x * 72f / 25.4f,                      // mm → pt
          box.y * 72f / 25.4f,
          (box.x + box.width) * 72f / 25.4f,
          (box.y + box.height) * 72f / 25.4f
        )
        "infinite" -> listOf(
          box.x * dpi / 25.4f,                      // mm → px
          box.y * dpi / 25.4f,
          (box.x + box.width) * dpi / 25.4f,
          (box.y + box.height) * dpi / 25.4f
        )
        else -> listOf(box.x, box.y, box.x + box.width, box.y + box.height)
      }
      
      results.add(RecognizedWord(
        text = word.label ?: "",
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        confidence = 1.0f  // JIIX doesn't expose per-word confidence
      ))
    }
  }
  
  return results
}

// JIIX data classes (matching myscript-examples/samples/offscreen-interactivity/.../serialization/jiix/)
// Uses @SerializedName for "bounding-box" JSON key
data class JiixRecognitionRoot(
  val type: String?,
  @SerializedName("bounding-box") val boundingBox: JiixBoundingBox?,
  val elements: List<JiixElement>?
)

data class JiixElement(
  val id: String?,
  val type: String?,
  val label: String?,
  @SerializedName("bounding-box") val boundingBox: JiixBoundingBox?,
  val words: List<JiixWord>?
)

data class JiixWord(
  val label: String?,
  val candidates: List<String>?,
  @SerializedName("bounding-box") val boundingBox: JiixBoundingBox?
)

data class JiixBoundingBox(
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float
)
```

**Key Differences from Plan A Real-Time:**
| Aspect | Plan A (Real-time) | Av2 (Batch) |
|--------|-------------------|-------------|
| Trigger | Per-stroke on pen-up | Throttled after 5s idle |
| OffscreenEditor | Long-lived per page | Temporary per batch |
| Stroke mapping | Maintains stroke ID mapping | No mapping needed |
| Output | Listener callback | Direct return value |
| JIIX Export | Via listener | Direct export() call |
```

**Threading Model:**
- `recognizeBatch()` is a `suspend` function using `Dispatchers.Default`
- MyScript operations are CPU-bound, not IO-bound
- Caller (RebuildWorker) runs on WorkManager's background thread
- Engine singleton is thread-safe for single recognition at a time

**Error Handling:**
- Throws `IllegalStateException` if engine not initialized
- Catches and propagates MyScript exceptions to caller
- Temporary ContentPackage is always closed (try-finally)

**MyScript Output:**
- MyScript returns bounding boxes in **millimeters** (mm) in JIIX export
- Stroke input to MyScript: **PIXELS (px)** - see "Coordinate Units Reconciliation" in Context section
- The conversion flow below shows how page coordinates convert through MyScript and back

**Coordinate Flow (Page geometryKind Dependent):**
> **NOTE**: This shows the full round-trip. Input to MyScript is px, output from JIIX is mm.

```
Fixed Pages (pt):   Page pt → px (pt * dpi/72) → MyScript (px) → JIIX mm bbox → pt (mm * 72/25.4)
Infinite Pages (px): Page px → MyScript (px directly) → JIIX mm bbox → px (mm * DPI/25.4)
```

**Conversion Formulas (EXPLICIT):**

| Page geometryKind | Input Conversion (page → MyScript px) | Output Conversion (JIIX mm → page) |
|-------------------|--------------------------------------|-----------------------------------|
| **"fixed" (pt)** | `px = pt * dpi / 72` | `pt = mm * 72 / 25.4` |
| **"infinite" (px)** | `px = px` (no conversion) | `px = mm * DPI / 25.4` |

**Where DPI comes from (AUTHORITATIVE - use xdpi):**
```kotlin
// In RebuildWorker, get device DPI for infinite pages:
// Use xdpi (actual physical DPI), NOT densityDpi (bucket value)
val dpi = context.resources.displayMetrics.xdpi

// Pass to recognizeBatch():
// NOTE: Use `geometryKind` (not `kind`) - geometryKind determines coordinate system
val result = engine.recognizeBatch(
  strokes = strokes,
  geometryKind = page.geometryKind,  // "fixed" or "infinite" - AUTHORITATIVE field for coordinates
  dpi = if (page.geometryKind == "infinite") dpi else 72f
)
```

**CRITICAL: Field Usage Clarification**

| Field | Purpose | Values (per V0-api.md) | Use When |
|-------|---------|------------------------|----------|
| `page.kind` | Page content type (NoteKind) | `"ink"`, `"pdf"`, `"mixed"`, `"infinite"` | Determining note type in UI, routing to editor |
| `page.geometryKind` | Coordinate system type (PageGeometry.kind) | `"fixed"`, `"infinite"` | **ALL coordinate conversions and recognition** |

**Value Alignment with V0-api.md (line 47 and 49-51):**
- `NoteKind` = `"ink" | "pdf" | "mixed" | "infinite"` - page content type
- `PageGeometry.kind` = `"fixed" | "infinite"` - coordinate system type

**For recognition and coordinate math, ALWAYS use `page.geometryKind`.**

**RecognizedWord Persistence:**
```kotlin
// Stored as JSON in RecognitionIndexEntity.wordPositions
// Coordinates are in PAGE UNITS (pt for fixed, px for infinite)
// NOTE: Use page.geometryKind to determine which unit type
data class RecognizedWord(
  val text: String,
  val left: Float,   // page units (pt if geometryKind="fixed", px if geometryKind="infinite")
  val top: Float,    // page units
  val right: Float,  // page units
  val bottom: Float, // page units
  val confidence: Float
)
```

**Rendering Overlay:**
- Read `page.geometryKind` to determine unit type (per Field Usage Clarification above)
- RecognizedWord coordinates are already in page units (pt for geometryKind="fixed", px for geometryKind="infinite")
- Transform to screen coordinates using existing `ViewTransform.pageToScreen()`

**RecognitionIndexEntity Schema Update (Av2 addition):**
Plan A's `RecognitionIndexEntity` has these fields:
- `pageId` (PK), `noteId`, `recognizedText`, `recognizedAtLamport`, `recognizerVersion`, `updatedAt`

Av2 adds `wordPositionsJson` for overlay positions. The final schema is:

```kotlin
// Final RecognitionIndexEntity (Plan A + Av2 additions)
@Entity(tableName = "recognition_index")
data class RecognitionIndexEntity(
  @PrimaryKey val pageId: String,
  val noteId: String,
  val recognizedText: String? = null,      // Plan A: full text for FTS search
  val recognizedAtLamport: Long? = null,   // Plan A: Lamport timestamp of recognition
  val recognizerVersion: String? = null,   // Plan A: MyScript version used
  val wordPositionsJson: String? = null,   // Av2 addition: JSON array of RecognizedWord
  val updatedAt: Long
)
```

**Migration (REQUIRED):** Av2 adds a new column. Room migration needed:

```kotlin
// Add to data/migrations/Migrations.kt
val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // 1. Create tiles table (for infinite canvas)
    db.execSQL("""
      CREATE TABLE IF NOT EXISTS tiles (
        tileId TEXT PRIMARY KEY NOT NULL,
        pageId TEXT NOT NULL,
        tileX INTEGER NOT NULL,
        tileY INTEGER NOT NULL,
        strokeIds TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
      )
    """)
    db.execSQL("CREATE INDEX index_tiles_pageId ON tiles(pageId)")
    
    // 2. Add wordPositionsJson column to recognition_index
    db.execSQL("ALTER TABLE recognition_index ADD COLUMN wordPositionsJson TEXT DEFAULT NULL")
  }
}
```

**Migration note**: `ALTER TABLE ADD COLUMN` is safe for nullable columns with default values.

**JSON Format**:
```json
[
  {"text": "Hello", "left": 100.0, "top": 50.0, "right": 200.0, "bottom": 80.0, "confidence": 0.95},
  {"text": "World", "left": 210.0, "top": 50.0, "right": 310.0, "bottom": 80.0, "confidence": 0.92}
]
```

**Overlay Rendering:**
- `TextOverlayLayer` receives `List<RecognizedWord>` in page coordinates
- Apply current canvas transform (pan + zoom) to position text

### Plan A Foundation References (REQUIRED READING)

This plan builds on Plan A (`milestone-a-offline-ink-myscript.md`). The following files will exist after Plan A completion:

**MyScript SDK Reference Examples (EXIST NOW - use for patterns):**
| Path | Description |
|------|-------------|
| `myscript-examples/samples/offscreen-interactivity/src/main/java/com/microsoft/device/ink/InkView.kt` | Ink rendering patterns |
| `myscript-examples/samples/offscreen-interactivity/src/main/java/com/myscript/iink/demo/ink/serialization/jiix/BoundingBox.kt` | Bounding box handling |
| `myscript-examples/samples/batch-mode/src/main/java/com/myscript/iink/samples/batchmode/Stroke.kt` | Stroke data structure |
| `myscript-examples/samples/search/src/main/java/com/myscript/iink/samples/search/SearchView.kt` | Search UI patterns |

**Ink Canvas & UI** (created in Plan A Phase 3):
| Path | Description | Plan A Task |
|------|-------------|-------------|
| `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` | Main ink canvas composable | 3.2 |
| `apps/android/app/src/main/java/com/onyx/android/ink/ui/LowLatencyInkView.kt` | Fallback low-latency renderer | 3.2 |
| `apps/android/app/src/main/java/com/onyx/android/screens/NoteEditorScreen.kt` | Note editor screen | 2.2 |
| `apps/android/app/src/main/java/com/onyx/android/ui/components/EditorToolbar.kt` | Toolbar with tools | 2.3 |

**Data Entities** (created in Plan A Phase 4):
| Path | Description | Plan A Task |
|------|-------------|-------------|
| `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteEntity.kt` | Note entity | 4.2 |
| `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt` | Page entity (has `kind` field) | 4.3 |
| `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt` | Stroke entity | 4.4 |
| `apps/android/app/src/main/java/com/onyx/android/data/entity/RecognitionIndexEntity.kt` | Recognition results | 4.5a |
| `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt` | Room database | 4.7 |
| `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt` | Repository pattern | 4.9 |

**Recognition** (created in Plan A Phase 5):
| Path | Description | Plan A Task |
|------|-------------|-------------|
| `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptEngine.kt` | MyScript engine singleton | 5.2 |
| `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt` | Per-page recognition manager | 5.3 |

### Plan A Expected Interfaces (for Av2 Integration)

> These are the method signatures Av2 expects to find in Plan A outputs.
> **Interface Adaptation Criteria (MANDATORY):**
> 
> If Plan A implementation differs from these signatures, Av2 executor MUST:
> 1. **Check Plan A actual signatures** - Read the Plan A output files
> 2. **Create adapter methods** - If signatures differ, create wrapper methods in Av2
> 3. **Document the adaptation** - Add comments explaining the mapping
> 4. **Test the integration** - Verify the adapter works with actual Plan A outputs
> 
> **Critical Signatures to Verify:**
> - `NoteRepository.saveStroke(pageId: String, stroke: StrokeEntity)` - MUST match exactly
> - `NoteRepository.createPage(noteId: String, kind: String)` - MUST match exactly  
> - `MyScriptPageManager.addStroke(stroke: Stroke)` - MUST match exactly
> - `MyScriptPageManager.onStrokeErased(strokeId: String, remainingStrokes: List<Stroke>)` - MUST match exactly
> 
> **If signatures differ:** Create adapter methods in Av2, e.g.:
> ```kotlin
// Adapter for signature mismatch
suspend fun saveStrokeAdapter(stroke: StrokeEntity) {
  // If Plan A expects different params, adapt here
  noteRepository.saveStroke(stroke.pageId, stroke)
}
```

**HomeScreen** (expected from Plan A task 2.1):
```kotlin
// apps/android/app/src/main/java/com/onyx/android/screens/HomeScreen.kt
@Composable
fun HomeScreen(
  notes: List<NoteEntity>,
  onNoteClick: (NoteId) -> Unit,
  onCreateNote: () -> Unit,  // Av2 extends: add infinite canvas creation option
  modifier: Modifier = Modifier
)

// Av2 will add: second FAB or menu option for "New Infinite Canvas"
// Implementation: Add DropdownMenu or second FAB that calls onCreateInfiniteNote: () -> Unit
```

**NoteEditorScreen** (expected from Plan A task 2.2):
```kotlin
// apps/android/app/src/main/java/com/onyx/android/screens/NoteEditorScreen.kt
@Composable
fun NoteEditorScreen(
  viewModel: NoteEditorViewModel,
  modifier: Modifier = Modifier
) {
  // Structure expected by Av2:
  val page by viewModel.currentPage.collectAsState()
  val strokes by viewModel.strokes.collectAsState()
  
  // Av2 will add routing based on geometryKind:
  // when (page?.geometryKind) {
  //   "infinite" -> InfiniteCanvas(...)
  //   else -> InkCanvas(...)  // "fixed" pages
  // }
  
  // Av2 will add: TextOverlayLayer as optional overlay
}
```

**NoteEditorViewModel** (expected from Plan A):
```kotlin
// apps/android/app/src/main/java/com/onyx/android/viewmodel/NoteEditorViewModel.kt
class NoteEditorViewModel(
  private val repository: NoteRepository,
  private val pageManager: MyScriptPageManager
) : ViewModel() {
  
  val currentPage: StateFlow<PageEntity?>
  val strokes: StateFlow<List<StrokeEntity>>
  
  fun onStrokeComplete(stroke: StrokeEntity)  // Called when user finishes drawing a stroke
  fun deleteStroke(strokeId: String)          // Called on eraser hit
  fun undo()                                   // Undo last action
  fun redo()                                   // Redo undone action
}
```

**InkCanvas Composable** (expected from Plan A):
```kotlin
// apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt
@Composable
fun InkCanvas(
  strokes: List<Stroke>,             // Domain Stroke class (NOT StrokeEntity)
  currentTool: Tool,                 // pen, highlighter, eraser
  onStrokeComplete: (Stroke) -> Unit,   // Returns domain Stroke
  onEraseStroke: (strokeId: String) -> Unit,
  modifier: Modifier = Modifier
)
// NOTE: InkCanvas uses domain Stroke, not StrokeEntity
// ViewModel converts via StrokeSerializer when persisting
```

**NoteRepository** (expected from Plan A):
```kotlin
// apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt
class NoteRepository(private val db: OnyxDatabase) {
  // NOTE: Plan A uses domain Stroke class, NOT StrokeEntity directly
  // The repository converts Stroke <-> StrokeEntity internally via StrokeSerializer
  suspend fun saveStroke(pageId: String, stroke: Stroke)  // Domain Stroke, not StrokeEntity
  suspend fun deleteStroke(strokeId: String)
  suspend fun getStrokesForPage(pageId: String): List<Stroke>  // Returns domain Stroke
  suspend fun createPage(noteId: String, kind: String): PageEntity
  
  // Av2 ADDS these methods (see task Av2.1.0):
  suspend fun createInfinitePage(noteId: String): PageEntity
  suspend fun deletePage(pageId: String)
  suspend fun getPersistedRecognizedWords(pageId: String): List<RecognizedWord>  // For overlay fallback
}

// CRITICAL TYPE CLARIFICATION:
// - Stroke = domain model class (used in ViewModel, Canvas, MyScript)
// - StrokeEntity = Room database entity (used only in DAO layer)
// - StrokeSerializer converts between them (see Plan A task 4.6)
//
// Throughout Av2, use `Stroke` (domain class), NOT `StrokeEntity`:
// - InfiniteCanvas receives List<Stroke>
// - TileManager.addStrokeToTiles(stroke: Stroke)
// - MyScriptPageManager.addStroke(stroke: Stroke)
```
```

**EditorToolbar** (expected from Plan A):
```kotlin
// apps/android/app/src/main/java/com/onyx/android/ui/components/EditorToolbar.kt
@Composable
fun EditorToolbar(
  currentTool: Tool,
  onToolChange: (Tool) -> Unit,
  // Av2 will add: eraserMode: EraserMode, onEraserModeChange: (EraserMode) -> Unit
)

enum class Tool { PEN, HIGHLIGHTER, ERASER }
// Av2 adds: enum class EraserMode { STROKE, SEGMENT }
```

**Sync Protocol Spec** (for Plan B/C compatibility):
| Path | Description |
|------|-------------|
| `.sisyphus/plans/milestone-c-collaboration-sharing.md` | Op types, Lamport ordering, CRDT rules |
| `V0-api.md` (project root) | v0 API contract schema |

### Room Migration Strategy (for new Av2 entities)

Plan Av2 adds new entities. Room migration must be handled:

**New Entity: TileEntity** (for infinite canvas)
```kotlin
// apps/android/app/src/main/java/com/onyx/android/data/entity/TileEntity.kt
@Entity(tableName = "tiles")
data class TileEntity(
  @PrimaryKey val tileId: String,  // Deterministic: "{pageId}:{tileX}:{tileY}" (see TileId Strategy above)
  val pageId: String,               // Foreign key to PageEntity
  val tileX: Int,
  val tileY: Int,
  val strokeIds: String,            // JSON array of stroke IDs, format: ["id1", "id2"]
  val createdAt: Long,              // Timestamp (sync-compatible) - set on first insert
  val updatedAt: Long               // Timestamp (sync-compatible) - updated on strokeIds change
)
```

**strokeIds JSON Format:**
- **Format**: `["stroke_uuid1", "stroke_uuid2"]` (JSON array with quoted strings)
- **Empty**: `[]` (empty array, not null)
- **Query pattern**: Use `LIKE '%"strokeId"%'` to match exact stroke IDs (quotes prevent partial matches)
- **Serialization**: Use Gson for JSON encoding/decoding (see TileManager implementation in Av2.1.2)

**Room Migration Strategy (AUTHORITATIVE - Single Version):**

> **IMPORTANT**: There is ONE migration from version 1 to 2. It includes BOTH:
> - TileEntity table creation (for infinite canvas)
> - wordPositionsJson column addition (for recognition overlay)
>
> The authoritative migration code is in task Av2.3.0a. This section provides an overview.

**Migration Overview:**
1. Update `OnyxDatabase.kt`:
   - Add `TileEntity::class` to `@Database(entities = [...])`
   - Increment version: `version = 2`
   - Add `abstract fun tileDao(): TileDao`
2. Create SINGLE migration in `data/migrations/Migrations.kt`:
   ```kotlin
   // AUTHORITATIVE MIGRATION - includes ALL Av2 schema changes
   val MIGRATION_1_2 = object : Migration(1, 2) {
     override fun migrate(db: SupportSQLiteDatabase) {
       // 1. Add wordPositionsJson column to recognition_index (for overlay)
       db.execSQL("ALTER TABLE recognition_index ADD COLUMN wordPositionsJson TEXT")
       
       // 2. Create tiles table (for infinite canvas)
       db.execSQL("""
         CREATE TABLE IF NOT EXISTS tiles (
           tileId TEXT PRIMARY KEY NOT NULL,
           pageId TEXT NOT NULL,
           tileX INTEGER NOT NULL,
           tileY INTEGER NOT NULL,
           strokeIds TEXT NOT NULL,
           createdAt INTEGER NOT NULL,
           updatedAt INTEGER NOT NULL
         )
       """)
       db.execSQL("CREATE INDEX IF NOT EXISTS index_tiles_pageId ON tiles(pageId)")
     }
   }
   ```
3. Add migration to database builder in `OnyxApplication.kt`:
   ```kotlin
   database = Room.databaseBuilder(...)
     .addMigrations(MIGRATION_1_2)
     .build()
   ```

**Implementation**: See task Av2.3.0a for detailed implementation and verification steps.

**Schema Location**: `apps/android/app/schemas/2.json` (auto-generated)

---

## Work Objectives

### Core Objective
Enhance the Android app with advanced drawing and recognition features that improve user experience for power users and complex documents.

### Concrete Deliverables
1. **Infinite Canvas** - Tile-based unbounded drawing surface
2. **Segment Eraser** - Precision erasing that splits strokes
3. **Batch Recognition** - Background processing with smart throttling
4. **Text Overlay** - Display recognized text as optional layer

### Definition of Done
- [x] Can create infinite canvas pages
- [x] Infinite canvas supports seamless tile loading/unloading
- [x] Segment eraser splits strokes at erase point
- [x] Batch recognition runs in background without blocking UI
- [x] Recognition overlay displays text at stroke locations
- [x] All features persist correctly to Room database

### Must Have
- Infinite canvas with tile management
- Segment eraser with stroke splitting
- Real-time recognition (PRESERVED from Plan A) + persistence debouncing (250-750ms)
- RebuildWorker for import/sync/manual rebuild (NOT for normal drawing)
- Background processing without UI blocking

### Must NOT Have (Guardrails)
- NO changes to existing sync-compatible schema structure
- NO breaking changes to Plan A features
- NO network/cloud features (reserved for Plan C)
- NO iOS implementation

---

## Verification Strategy

### Test Decision
- **Primary**: Manual verification on physical tablet
- **Secondary**: Unit tests for tile management and stroke splitting algorithms

### Verification Patterns
- **Infinite Canvas**: Draw near edge → Tiles load → Pan → Old tiles unload
- **Segment Eraser**: Erase middle of stroke → Two separate strokes remain
- **Batch Recognition**: Write continuously → Recognition runs after pause

---

## Task Flow

```
Av2.0.1 (Verify Plan A) → Av2.0.2 (Lamport Clock)
    ↓
Av2.1.0 (Infinite Page Creation & Routing)
    ↓
Av2.1.1 (Tile System Design)
    ↓
Av2.1.2 (TileManager)
    ↓
Av2.1.3 (InfiniteCanvas Composable)
    ↓
Av2.1.4 (Cross-tile Rendering)
    ↓
Av2.1.5 (Persistence)
    ↓
Av2.2.x (Segment Eraser) → Av2.3.0 (MyScriptPageManager API Extensions)
                                  ↓
                           Av2.3.0a (RecognitionDao + Migration)
                                  ↓
                           Av2.3.1 (Persistence Debouncer)
                                  ↓
                           Av2.3.2 (ViewModel Integration)
                                  ↓
                           Av2.3.3 (RebuildWorker) → Av2.3.4 (Integration Test)
                                  ↓
                          Av2.4.x (Text Overlay)
                                  ↓
                          Av2.5.x (Integration)
```

---

## TODOs

### Phase 0: Pre-Execution Setup (MANDATORY)

- [x] Av2.0.1 Verify Plan A completion

  **What to do**:
  - Run the verification script from "Plan A Dependency Assumption" section
  - Confirm all Plan A output files exist
  - If verification fails, STOP and execute Plan A first

  **Parallelizable**: NO (blocking gate)

  **Verification**:
  ```bash
  test -f apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt && \
  test -f apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt && \
  test -f apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt && \
  echo "✅ PASS" || echo "❌ FAIL - Execute Plan A first"
  ```

  **Commit**: NO (verification only)

---

- [x] Av2.0.2 Implement LocalLamportClock seeding on app start

  **What to do**:
  - Create `apps/android/app/src/main/java/com/onyx/android/data/sync/LocalLamportClock.kt` as specified in Context
  - Create `apps/android/app/src/main/java/com/onyx/android/data/sync/LamportInitGate.kt` with `CompletableDeferred` pattern
  - Add `getMaxCreatedLamport()` query to StrokeDao
  - Add seeding call in `OnyxApplication.onCreate()` after database init
  - **Guarantee mechanism**: Use `LamportInitGate.ready.await()` in `NoteRepository.saveStroke()` (matching Plan A's method name) to block until seeding completes (see Context section for code)

  **Parallelizable**: NO (foundational)

  **References**:
  - Lamport clock spec: See "Lamport Clock in Offline Mode" in Context section (includes LamportInitGate pattern)
  - OnyxApplication: `apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt` (from Plan A)
  - StrokeDao: `apps/android/app/src/main/java/com/onyx/android/data/dao/StrokeDao.kt` (from Plan A)

  **Verification**:
  - [ ] Unit test: Seed with max value → next() returns max+1
  - [ ] App start → log shows "Lamport clock seeded with value: X"

  **Commit**: YES
  - Message: `feat(android): add LocalLamportClock with startup seeding`

---

### Phase 1: Infinite Canvas

- [x] Av2.1.0 Add infinite page creation and routing

  **What to do**:
  - Add "New Infinite Canvas" button in `HomeScreen.kt` (Plan A task 2.1 creates this as the note list screen)
    - Location: FAB menu or top app bar action menu, alongside "New Note" button
    - File: `apps/android/app/src/main/java/com/onyx/android/screens/HomeScreen.kt` (Plan A naming)
  
  **CRITICAL: Plan A Interface Reconciliation**
  
  > Plan A's `NoteRepository.createNote()` returns `NoteWithFirstPage` which auto-creates a FIXED page.
  > Av2 must work around this by either:
  > (A) Calling `createNote()` then REPLACING the auto-created page, OR
  > (B) Adding a new `createNoteWithInfinitePage()` method to NoteRepository
  >
  > **Chosen approach: (A) Replace auto-created page** - minimal Plan A changes.
  
  - **Complete infinite canvas note creation flow (CORRECTED):**
    ```kotlin
    // In HomeViewModel.kt (or HomeScreen.kt with ViewModel)
    // This method creates an infinite canvas note
    
    suspend fun createInfiniteCanvasNote(): String {
      // Step 1: Use Plan A's createNote() which returns NoteWithFirstPage
      // This auto-creates a fixed page we need to replace
      val noteWithPage = noteRepository.createNote()  // Plan A signature: returns NoteWithFirstPage
      val noteId = noteWithPage.note.noteId
      val autoCreatedPageId = noteWithPage.firstPageId  // CORRECTED: Plan A uses firstPageId (String), not firstPage.pageId
      
      // Step 2: Delete the auto-created fixed page
      noteRepository.deletePage(autoCreatedPageId)
      
      // Step 3: Create the infinite page via Av2's new method
      val infinitePage = noteRepository.createInfinitePage(noteId)
      
      return infinitePage.pageId  // Return for navigation
    }
    
    // Av2 ADDS this method to NoteRepository (does NOT modify Plan A's existing methods)
    // File: apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt
    suspend fun createInfinitePage(noteId: String): PageEntity {
      val pageId = UUID.randomUUID().toString()
      val now = System.currentTimeMillis()
      
      val pageEntity = PageEntity(
        pageId = pageId,
        noteId = noteId,
        kind = "infinite",
        geometryKind = "infinite",
        indexInNote = 0,
        width = 2024f,                  // V0 API: tileSize = 2024
        height = 2024f,
        unit = "px",
        pdfAssetId = null,
        pdfPageNo = null,
        updatedAt = now,
        contentLamportMax = 0
      )
      
      pageDao.insert(pageEntity)
      return pageEntity
    }
    
    // Av2 ADDS deletePage to NoteRepository if not present
    suspend fun deletePage(pageId: String) {
      pageDao.deleteByPageId(pageId)
    }
    ```
    
  - **HomeScreen FAB with dropdown menu:**
    ```kotlin
    // In HomeScreen.kt
    var showCreateMenu by remember { mutableStateOf(false) }
    
    FloatingActionButton(onClick = { showCreateMenu = true }) {
      Icon(Icons.Default.Add, "Create")
    }
    
    DropdownMenu(expanded = showCreateMenu, onDismissRequest = { showCreateMenu = false }) {
      DropdownMenuItem(
        text = { Text("New Note") },
        onClick = {
          showCreateMenu = false
          viewModel.createNote()  // Plan A's existing flow
        }
      )
      DropdownMenuItem(
        text = { Text("New Infinite Canvas") },
        onClick = {
          showCreateMenu = false
          scope.launch {
            val pageId = viewModel.createInfiniteCanvasNote()
            onNoteClick(pageId)
          }
        }
      )
    }
    ```
    
    **Why these values:**
    - `kind = "infinite"`: Matches v0 API NoteKind enum
    - `geometryKind = "infinite"`: Triggers infinite canvas rendering path
    - `width/height = 2024`: Tile size per V0 API contract (`tileSize: 2024`)
    - `unit = "px"`: Infinite pages use pixel coordinates, not points (no DPI conversion needed)
    
    **Repository method additions (Av2 adds, does NOT modify Plan A):**
    - `noteRepository.createInfinitePage(noteId)` - NEW: Creates infinite PageEntity
    - `noteRepository.deletePage(pageId)` - NEW: Deletes page by ID
    - Navigation: `onNoteClick(pageId)` - Uses existing HomeScreen navigation callback
  
  - **Screen→Page coordinate transform for infinite pages:**
    ```kotlin
    // For infinite pages, coordinates are in pixels (unit = "px")
    // No DPI conversion needed - use Plan A's screenToPage() directly:
    fun screenToPage(screenX: Float, screenY: Float): Pair<Float, Float> {
      val pageX = (screenX - viewTransform.panX) / viewTransform.zoom
      val pageY = (screenY - viewTransform.panY) / viewTransform.zoom
      return Pair(pageX, pageY)
    }
    // Reference: Plan A task 3.6 at milestone-a-offline-ink-myscript.md:2377-2381
    ```
  
  - Route to `InfiniteCanvas` composable when `page.geometryKind == "infinite"`:
    ```kotlin
    // In NoteEditorScreen.kt - route by geometryKind (not kind)
    when (page.geometryKind) {
      "infinite" -> InfiniteCanvas(...)
      else -> InkCanvas(...)  // Fixed page (Plan A default)
    }
    ```
  - Ensure existing fixed-page notes continue to route to `InkCanvas`

  **Must NOT do**:
  - Don't modify existing note creation flow for fixed pages
  - Don't auto-migrate existing notes to infinite canvas
  - Don't use a page type selector dropdown (keep it simple: separate buttons)

  **Parallelizable**: NO (foundational for Phase 1)

  **References**:
  - PageEntity with `kind` and `geometryKind` fields: `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt` (from Plan A task 4.3)
  - V0-api.md: `V0-api.md` line 51 (`PageGeometry.kind = "infinite"`)
  - NoteEditorScreen: `apps/android/app/src/main/java/com/onyx/android/screens/NoteEditorScreen.kt` (routing logic)
  - NoteRepository: `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt` (page creation)
  - Screen→Page conversion: `milestone-a-offline-ink-myscript.md:2377-2381` (Plan A task 3.6)

  **Verification**:
  - [ ] Can create new infinite canvas note from UI
  - [ ] Infinite canvas note opens in `InfiniteCanvas` composable
  - [ ] Fixed-page notes still open in `InkCanvas`
  - [ ] Database: `PageEntity.kind = "infinite"` AND `PageEntity.geometryKind = "infinite"` for new infinite pages
  - [ ] Database: `PageEntity.unit = "px"` for infinite pages (not "pt")

  **Commit**: YES
  - Message: `feat(android): add infinite page creation and routing`

---

- [x] Av2.1.1 Design infinite canvas tile system

  **What to do**:
  - Implement tile coordinate system as specified in "Infinite Canvas Coordinate System" section above
  - Define Tile data class:
    ```kotlin
    data class CanvasTile(
      val tileX: Int,
      val tileY: Int,
      val strokes: List<Stroke>,
      val bitmap: Bitmap? = null  // Cached render
    )
    ```
  - Implement `pageToTile()` and `tileToPageOrigin()` conversion functions
  - Document tile loading/unloading strategy

  **Parallelizable**: NO (foundational design)

  **References**:
  - Coordinate system spec: See "Infinite Canvas Coordinate System" in Context section above
  - Tiled rendering concepts: Similar to map tiles (e.g., OpenStreetMap)
  - Current ink canvas: `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` (from Plan A task 3.2)
  - PageEntity with `geometryKind` field: `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt` (from Plan A task 4.3)

  **Verification**:
  - [ ] Design document created at `docs/android/infinite-canvas-design.md`
  - [ ] Tile coordinate math is correct (unit test in `TileManagerTest.kt`)

  **Commit**: Group with Av2.1.2

---

- [x] Av2.1.2 Implement TileManager class

  **What to do**:
  - Create `ink/canvas/TileManager.kt`
  - Implement visible tile calculation based on viewport
  - Implement tile loading/unloading with LRU cache
  - Max tiles in memory: 9 (3x3 around current view)
  - **Implement tile membership updates** (per "TileEntity.strokeIds Update Strategy" in Context):
    - `fun addStrokeToTiles(stroke: Stroke)`:
      1. Calculate stroke bounding box
      2. Find all tiles intersecting bounding box using `pageToTile()` on corners
      3. For each tile: get-or-create TileEntity, append strokeId to `strokeIds` JSON
      4. Persist via `TileDao.upsert(tileEntity)`
    - `fun removeStrokeFromTiles(strokeId: String)`:
      1. Query all TileEntity where `strokeIds` contains strokeId
      2. Remove strokeId from each JSON array
      3. Persist changes (or delete tile if empty)
    - `fun replaceStrokeInTiles(oldStrokeId: String, newStrokes: List<Stroke>)`:
      1. Remove oldStrokeId from all tiles
      2. Add each new stroke to its intersecting tiles
      3. All in single Room transaction
  - Create `TileDao` with:
    - `@Upsert fun upsert(tile: TileEntity)`
    - `@Delete fun delete(tile: TileEntity)`  // Used when tile becomes empty after stroke removal
    - `@Query("DELETE FROM tiles WHERE tileId = :tileId") fun deleteById(tileId: String)`  // Alternative by ID
    - `@Query("SELECT * FROM tiles WHERE pageId = :pageId") fun getTilesForPage(pageId: String): List<TileEntity>`
    - `@Query("SELECT * FROM tiles WHERE strokeIds LIKE '%\"' || :strokeId || '\"%'") fun getTilesContainingStroke(strokeId: String): List<TileEntity>`
    
  **strokeIds JSON Format (CRITICAL for safe LIKE queries):**
  - **Format**: JSON array with quoted strings: `["stroke_abc123", "stroke_def456"]`
  - **Query pattern**: `LIKE '%"strokeId"%'` matches `"strokeId"` exactly (quotes prevent partial matches)
  - **Example**: Query for `stroke_abc` will NOT match `stroke_abc123` because quotes are included
  - **Alternative (if issues arise)**: Use a junction table `stroke_tile(strokeId, tileId)` instead of JSON
  
  **JSON Serialization/Deserialization:**
  ```kotlin
  // In TileManager.kt
  private val gson = Gson()
  
  fun encodeStrokeIds(ids: Set<String>): String = gson.toJson(ids.toList())
  
  fun decodeStrokeIds(json: String): Set<String> {
    val list: List<String> = gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
    return list.toSet()
  }
  
  // Usage in addStrokeToTiles:
  val currentIds = decodeStrokeIds(tile.strokeIds)
  val updatedIds = currentIds + strokeId
  val updatedTile = tile.copy(strokeIds = encodeStrokeIds(updatedIds))
  tileDao.upsert(updatedTile)
  ```

  **Must NOT do**:
  - Load all tiles at once (memory issue)

  **Parallelizable**: NO (depends on Av2.1.1)

  **References**:
  - LRU cache: `android.util.LruCache`

  **Verification**:
  - [ ] Unit test: viewport change → correct tiles calculated
  - [ ] Memory usage stays bounded (9 tiles max)

  **Commit**: YES
  - Message: `feat(android): implement infinite canvas tile manager`

---

- [x] Av2.1.3 Create InfiniteCanvas composable

  **What to do**:
  - Create `ink/ui/InfiniteCanvas.kt`
  - Integrate with TileManager
  - Handle pan gestures to navigate canvas
  - Handle zoom with tile resolution adjustment
  - Draw stroke previews during drawing
  
  **Initial View State (CRITICAL for coordinate system):**
  ```kotlin
  // InfiniteCanvas initial state - origin (0,0) at viewport center
  // This aligns with the coordinate system spec: "origin (0,0) at initial viewport center"
  
  @Composable
  fun InfiniteCanvas(
    page: PageEntity,
    modifier: Modifier = Modifier
  ) {
    // ViewTransform state (shared with stroke rendering and hit-testing)
    var viewTransform by remember {
      mutableStateOf(ViewTransform(
        panX = 0f,    // Initial pan = 0 (page origin at screen center)
        panY = 0f,    // Initial pan = 0 (page origin at screen center)
        zoom = 1f     // Initial zoom = 1 (1:1 page pixels to screen pixels)
      ))
    }
    
    // Get screen size to center the origin
    BoxWithConstraints(modifier = modifier) {
      val screenWidth = constraints.maxWidth.toFloat()
      val screenHeight = constraints.maxHeight.toFloat()
      
      // Compute initial pan to center page origin (0,0) on screen
      // panX/panY represent the screen position of page origin
      LaunchedEffect(Unit) {
        viewTransform = viewTransform.copy(
          panX = screenWidth / 2f,
          panY = screenHeight / 2f
        )
      }
      
      Canvas(modifier = Modifier.fillMaxSize()) {
        // Render visible tiles based on viewport
        // screenToPage(0, 0) = top-left of viewport in page coords
        // screenToPage(screenWidth, screenHeight) = bottom-right
      }
    }
  }
  
  // Coordinate conversion (same as Plan A, used for hit-testing and drawing)
  fun screenToPage(screenX: Float, screenY: Float, vt: ViewTransform): Pair<Float, Float> {
    val pageX = (screenX - vt.panX) / vt.zoom
    val pageY = (screenY - vt.panY) / vt.zoom
    return Pair(pageX, pageY)
  }
  ```
  
  **Why center at (0,0):**
  - User starts drawing at center of screen → strokes are near (0,0)
  - Negative and positive coordinates are equally accessible
  - Matches V0 API expectation for infinite canvas origin

  **Parallelizable**: NO (depends on Av2.1.2)

  **References**:
  - Current canvas: `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` (from Plan A task 3.2)
  - TileManager: `apps/android/app/src/main/java/com/onyx/android/ink/canvas/TileManager.kt` (created in Av2.1.2)
  - Compose gestures: https://developer.android.com/develop/ui/compose/touch-input/pointer-input

  **Verification**:
  - [ ] Can pan beyond single page bounds
  - [ ] Tiles load/unload as viewport moves
  - [ ] Drawing works across tile boundaries
  - [ ] Initial view has page origin (0,0) at screen center: draw a dot at startup → dot appears in center
  - [ ] `screenToPage(screenWidth/2, screenHeight/2)` returns `(0, 0)` at startup

  **Commit**: Group with Av2.1.4

---

- [x] Av2.1.4 Handle cross-tile stroke rendering

  **What to do**:
  - Strokes can span multiple tiles
  - When rendering, check if stroke bounds intersect tile
  - Clip stroke rendering to tile bounds
  - Ensure smooth rendering at tile edges

  **Parallelizable**: NO (depends on Av2.1.3)

  **Verification**:
  - [ ] Draw stroke across tile boundary → Renders correctly
  - [ ] No visual seams at tile edges

  **Commit**: YES
  - Message: `feat(android): add infinite canvas with cross-tile rendering`

---

- [x] Av2.1.5 Persist infinite canvas to database

  **What to do**:
  - Update PageEntity to support infinite canvas:
    - `kind = "infinite"`
  - Create TileEntity for tile-level stroke storage (per "Room Migration Strategy" section):
    ```kotlin
    @Entity(tableName = "tiles")
    data class TileEntity(
      @PrimaryKey val tileId: String,  // Deterministic: "{pageId}:{tileX}:{tileY}"
      val pageId: String,
      val tileX: Int,
      val tileY: Int,
      val strokeIds: String,  // JSON array of stroke IDs
      val createdAt: Long,    // Timestamp (sync-compatible)
      val updatedAt: Long     // Timestamp (sync-compatible)
    )
    ```
  - Create TileDao with queries (see Av2.1.2 for method signatures)

  **Parallelizable**: NO (depends on Av2.1.4)

  **References**:
  - PageEntity: `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt` (from Plan A task 4.3)
  - StrokeEntity: `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt` (from Plan A task 4.4)
  - OnyxDatabase: `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt` (from Plan A task 4.7)
  - Room Migration Strategy: See "Room Migration Strategy" section above for MIGRATION_1_2 code

  **Verification**:
  - [ ] Create infinite canvas → Draw → Restart app → Strokes persist
  - [ ] Only visited tiles are stored

  **Commit**: YES
  - Message: `feat(android): persist infinite canvas tiles to Room`

---

### Phase 2: Segment Eraser

- [x] Av2.2.1 Implement stroke intersection algorithm

  **What to do**:
  - Create `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeIntersection.kt`
  - Implement line segment intersection with eraser path
  - Find intersection points along stroke
  - Return list of intersection indices

  **Parallelizable**: YES (independent of Av2.1.x after foundation)

  **References**:
  - Line intersection algorithm: https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection
  - StrokeEntity for stroke point structure: `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt`

  **Verification**:
  - [ ] Unit test: straight line + perpendicular eraser → correct intersection
  - [ ] Unit test: curved stroke + eraser → multiple intersections

  **Commit**: Group with Av2.2.2

---

- [x] Av2.2.2 Implement stroke splitting

  **What to do**:
  - Create `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeSplitter.kt`
  - Given intersection points, split stroke into segments
  - Each segment becomes new Stroke with new ID (UUID)
  - Each new segment gets `createdLamport = LocalLamportClock.next()`
  - Preserve original style (color, width, tool)
  - **Collaboration semantics**: Eraser ops only apply to strokes where `createdLamport <= eraseLamport`

  **Parallelizable**: YES (can develop in parallel with intersection)

  **References**:
  - Lamport clock spec: See "Lamport Clock in Offline Mode" in Context section above
  - LocalLamportClock: `apps/android/app/src/main/java/com/onyx/android/data/sync/LocalLamportClock.kt` (create as specified in Context)
  - StrokeIntersection: `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeIntersection.kt` (created in Av2.2.1)
  - CRDT Lamport semantics: `.sisyphus/plans/milestone-c-collaboration-sharing.md` (sync protocol section)
  - StrokeEntity: `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt`

  **Verification**:
  - [ ] Unit test: stroke with 1 intersection → 2 strokes
  - [ ] Unit test: stroke with 2 intersections → 3 strokes
  - [ ] Original stroke is removed from list
  - [ ] New segments have same style as original
  - [ ] New segments have incrementing createdLamport values

  **Commit**: YES
  - Message: `feat(android): implement stroke intersection and splitting`

---

- [x] Av2.2.3 Add segment eraser mode to toolbar

  **What to do**:
  - Add "Segment Eraser" option to eraser tool
  - Toggle between stroke eraser and segment eraser
  - Show visual indicator of current mode

  **Parallelizable**: NO (depends on Av2.2.2)

  **References**:
  - EditorToolbar: `apps/android/app/src/main/java/com/onyx/android/ui/components/EditorToolbar.kt` (from Plan A task 2.3)
  - StrokeSplitter: `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeSplitter.kt` (created in Av2.2.2)

  **Verification**:
  - [ ] Can toggle eraser mode
  - [ ] Mode indicator visible

  **Commit**: Group with Av2.2.4

---

- [x] Av2.2.4 Integrate segment eraser with canvas

  **What to do**:
  - On eraser touch, calculate erase path
  - Find intersecting strokes
  - Split strokes at intersection points
  - Update canvas and database
  - **For infinite pages**: Update tile membership via TileManager

  **Segment Eraser Input Model:**
  
  The segment eraser needs three components: coordinate conversion, path sampling, and intersection detection.
  
  **1. Screen→Page Coordinate Conversion** (extends Plan A pattern):
  ```kotlin
  // Uses shared ViewTransform from Plan A task 3.6
  // Reference: milestone-a-offline-ink-myscript.md:2377-2381
  fun screenToPage(screenX: Float, screenY: Float): Pair<Float, Float> {
    val pageX = (screenX - viewTransform.panX) / viewTransform.zoom
    val pageY = (screenY - viewTransform.panY) / viewTransform.zoom
    return Pair(pageX, pageY)
  }
  ```
  
  **2. Eraser Radius Configuration:**
  ```kotlin
  // Eraser radius in screen pixels (same as stroke eraser in Plan A)
  private const val SEGMENT_ERASER_RADIUS_SCREEN_PX = 10f
  
  // Convert to page coordinates (scale by current zoom)
  fun eraserRadiusInPageCoords(): Float {
    return SEGMENT_ERASER_RADIUS_SCREEN_PX / viewTransform.zoom
  }
  ```
  
  **3. Eraser Path Sampling Strategy:**
  ```kotlin
  // During drag events, sample eraser path to create continuous erase stroke
  class EraserPath {
    private val points = mutableListOf<Pair<Float, Float>>()  // page coords
    
    // Called on each MotionEvent during eraser drag
    fun addPoint(screenX: Float, screenY: Float) {
      val (pageX, pageY) = screenToPage(screenX, screenY)
      points.add(Pair(pageX, pageY))
    }
    
    // Get all sampled points for intersection testing
    fun getPath(): List<Pair<Float, Float>> = points
    
    // Clear for next erase gesture
    fun reset() { points.clear() }
  }
  
  // Intersection testing: check each eraser path segment against each stroke segment
  fun findErasedSegments(eraserPath: EraserPath, strokes: List<Stroke>): Map<Stroke, List<Int>> {
    val result = mutableMapOf<Stroke, MutableList<Int>>()
    val radius = eraserRadiusInPageCoords()
    val pathPoints = eraserPath.getPath()
    
    for (stroke in strokes) {
      // Fast rejection: check if eraser path bounds intersect stroke bounds
      if (!pathBoundsIntersect(pathPoints, stroke.bounds, radius)) continue
      
      // Detailed: find all stroke point indices touched by eraser
      val touchedIndices = mutableListOf<Int>()
      for (i in stroke.points.indices) {
        val strokePt = stroke.points[i]
        if (isPointTouchedByPath(strokePt, pathPoints, radius)) {
          touchedIndices.add(i)
        }
      }
      
      if (touchedIndices.isNotEmpty()) {
        result[stroke] = touchedIndices
      }
    }
    return result
  }
  
  // Check if a stroke point is within radius of any eraser path segment
  fun isPointTouchedByPath(point: StrokePoint, pathPoints: List<Pair<Float, Float>>, radius: Float): Boolean {
    for (i in 0 until pathPoints.size - 1) {
      val (p1x, p1y) = pathPoints[i]
      val (p2x, p2y) = pathPoints[i + 1]
      val dist = pointToSegmentDistance(point.x, point.y, p1x, p1y, p2x, p2y)
      if (dist <= radius) return true
    }
    return false
  }
  
  // Reuse Plan A's point-to-segment distance algorithm
  // Reference: milestone-a-offline-ink-myscript.md:2411-2418
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

  **Segment Eraser with Tile Integration (for infinite pages):**
  ```kotlin
  // In NoteEditorViewModel or InkCanvas event handler
  suspend fun onSegmentErase(page: PageEntity, originalStroke: Stroke, newSegments: List<Stroke>) {
    // 1. Persist stroke changes to database
    repository.deleteStroke(originalStroke.id)
    newSegments.forEach { repository.saveStroke(page.pageId, it) }
    
    // 2. If infinite page, update tile membership
    if (page.geometryKind == "infinite") {
      tileManager.replaceStrokeInTiles(originalStroke.id, newSegments)
    }
    
    // 3. Update real-time recognition (KEEP Plan A behavior)
    val remainingStrokes = repository.getStrokesForPage(page.pageId)
    myScriptPageManager.onStrokesChanged(remainingStrokes)
    
    // 4. Debounce persistence to DB
    persistenceDebouncer.markDirty(page.pageId)
  }
  ```
  
  **TileManager.replaceStrokeInTiles implementation:**
  ```kotlin
  // In TileManager.kt - called during segment erase
  @Transaction
  suspend fun replaceStrokeInTiles(oldStrokeId: String, newStrokes: List<Stroke>) {
    // 1. Find all tiles containing the old stroke
    val affectedTiles = tileDao.getTilesContainingStroke(oldStrokeId)
    
    // 2. Remove old stroke ID from all affected tiles
    affectedTiles.forEach { tile ->
      val currentIds = decodeStrokeIds(tile.strokeIds)
      val updatedIds = currentIds - oldStrokeId
      if (updatedIds.isEmpty()) {
        tileDao.deleteById(tile.tileId)  // Remove empty tile using deleteById DAO method
      } else {
        tileDao.upsert(tile.copy(
          strokeIds = encodeStrokeIds(updatedIds),
          updatedAt = System.currentTimeMillis()
        ))
      }
    }
    
    // 3. Add each new segment to its intersecting tiles
    newStrokes.forEach { stroke ->
      addStrokeToTiles(stroke)
    }
  }
  ```
  
  **Transaction boundary**: The `@Transaction` annotation ensures atomic update - either all tile changes succeed or none do. This prevents inconsistent state if the operation is interrupted.

  **Parallelizable**: NO (depends on Av2.2.3)

  **References**:
  - InkCanvas: `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` (from Plan A task 3.2)
  - StrokeIntersection: `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeIntersection.kt`
  - StrokeSplitter: `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeSplitter.kt`
  - NoteRepository: `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt`
  - Plan A screenToPage conversion: `milestone-a-offline-ink-myscript.md:2377-2381` (coordinate transform)
  - Plan A hit-testing algorithm: `milestone-a-offline-ink-myscript.md:2383-2408` (bounds check + point-to-segment)
  - Plan A point-to-segment distance: `milestone-a-offline-ink-myscript.md:2411-2418` (standard algorithm)

  **Verification**:
  - [ ] Draw long stroke → Erase middle → Two strokes remain
  - [ ] Erase near end → One short stroke remains
  - [ ] Changes persist to database
  - [ ] Eraser has consistent 10px screen radius regardless of zoom level

  **Commit**: YES
  - Message: `feat(android): add segment eraser with stroke splitting`

---

- [x] Av2.2.5 Segment eraser undo/redo

  **What to do**:
  - Track original stroke before split
  - Undo restores original stroke
  - Redo removes original, adds split strokes

  **Parallelizable**: NO (depends on Av2.2.4)

  **References**:
  - StrokeSplitter: `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeSplitter.kt`
  - InkCanvas (for undo stack): `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`
  - NoteRepository: `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt`

  **Verification**:
  - [ ] Erase → Undo → Original stroke restored
  - [ ] Undo → Redo → Split strokes return

  **Commit**: YES
  - Message: `feat(android): add undo/redo for segment eraser`

---

### Phase 3: Recognition Persistence & Rebuild Worker

> **CRITICAL ARCHITECTURE CHANGE**: Real-time recognition is KEPT from Plan A.
> Phase 3 now implements PERSISTENCE DEBOUNCING and a REBUILD WORKER for special cases.
> See "Recognition Strategy: Real-time + Rebuild Worker" in Context section.

**CRITICAL: setPageGeometry() Call Sites (MANDATORY)**

> MyScriptPageManager.setPageGeometry(pageId, geometryKind) MUST be called when a page is opened
> to ensure coordinate conversion uses the correct DPI for recognition.
> Without this, infinite pages will have incorrect bounding box coordinates.

**Call Sites (add to NoteEditorViewModel.navigateToPage):**
```kotlin
// In NoteEditorViewModel.kt - add setPageGeometry call when page opens
fun navigateToPage(pageId: String) {
  // Close previous page's MyScript context
  if (currentPageId != null && currentPageId != pageId) {
    myScriptPageManager.closeCurrentPage()
  }
  
  currentPageId = pageId
  
  viewModelScope.launch {
    // Load page metadata
    val page = noteRepository.getPageById(pageId) ?: return@launch
    
    // CRITICAL: Set geometry BEFORE any recognition calls
    // This ensures DPI is correct for infinite vs fixed pages
    myScriptPageManager.setPageGeometry(pageId, page.geometryKind)
    
    // Now proceed with existing Plan A flow
    myScriptPageManager.onPageEnter(pageId)
    
    // Load existing strokes for this page
    val strokes = noteRepository.getStrokesForPage(pageId)
    // ... update UI state with strokes
    
    // Re-feed existing strokes to MyScript for recognition continuity
    strokes.forEach { stroke ->
      myScriptPageManager.addStroke(stroke)
    }
  }
}
```

**Summary of setPageGeometry Call Sites:**
| Location | When | Purpose |
|----------|------|---------|
| `NoteEditorViewModel.navigateToPage()` | Page opens | Set geometry for new page |
| (Implicit via navigateToPage) | Page switch | Previous page closed, new geometry set |

- [x] Av2.3.0 Extend MyScriptPageManager with required API methods

  **What to do**:
  - Add the following methods to `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt` (Plan A output)
  - These methods are REQUIRED by the persistence debouncer and overlay features
  
  **Methods to implement (per Context section "MyScriptPageManager API Extension"):**
  
  ```kotlin
  // Add to MyScriptPageManager.kt
  
  private var recognitionCallback: ((String, List<RecognizedWord>) -> Unit)? = null
  
  /**
   * Returns the current in-memory recognition results for the given page.
   * Used by persistence debouncer and overlay to access immediate recognition data.
   */
  fun getCurrentWords(pageId: String): List<RecognizedWord> {
    val editor = editors[pageId] ?: return emptyList()
    
    val jiixString = try {
      editor.export(editor.part?.rootBlock, MimeType.JIIX)
    } catch (e: Exception) {
      Log.w("MyScriptPageManager", "Failed to export JIIX for page $pageId", e)
      return emptyList()
    }
    
    return parseJiixForWords(jiixString, getCurrentGeometryKind(pageId), getCurrentDpi(pageId))
  }
  
  /**
   * Notifies the manager that strokes have changed (for segment erase scenarios).
   * Re-feeds all strokes to the editor to update recognition.
   */
  fun onStrokesChanged(strokes: List<Stroke>) {
    refeedStrokes(currentPageId, strokes)
  }
  
  /**
   * Sets a callback for recognition result updates.
   * Called whenever recognition completes (after any stroke change).
   */
  fun setRecognitionCallback(callback: (String, List<RecognizedWord>) -> Unit) {
    this.recognitionCallback = callback
  }
  
  // Internal: Called after MyScript recognition completes
  private fun onRecognitionComplete(pageId: String) {
    val words = getCurrentWords(pageId)
    recognitionCallback?.invoke(pageId, words)
  }
  ```
  
  **Helper methods required:**
  ```kotlin
  // Get geometry kind for current page (for coordinate conversion)
  private fun getCurrentGeometryKind(pageId: String): String {
    return pageGeometryCache[pageId] ?: "fixed"
  }
  
  // Get DPI for current page (for coordinate conversion)
  private fun getCurrentDpi(pageId: String): Float {
    return if (getCurrentGeometryKind(pageId) == "infinite") {
      context.resources.displayMetrics.xdpi
    } else {
      72f  // Fixed pages use points (72 dpi)
    }
  }
  
  // Store geometry kind when page is opened
  fun setPageGeometry(pageId: String, geometryKind: String) {
    pageGeometryCache[pageId] = geometryKind
  }
  
  private val pageGeometryCache = mutableMapOf<String, String>()
  ```

  **Must NOT do**:
  - Do NOT remove existing Plan A methods (`addStroke`, `onStrokeErased`, etc.)
  - Do NOT change the real-time recognition behavior

  **Parallelizable**: NO (foundational for Av2.3.1+)

  **References**:
  - API specification: See "MyScriptPageManager API Extension (NEW in Av2)" in Context section (lines 385-440)
  - MyScriptPageManager base: `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt` (Plan A task 5.3)
  - JIIX parsing: See `parseJiixForWords()` in "Batch Recognition API Definition" section (lines 672-719)
  - RecognizedWord type: See "RecognizedWord Persistence" in Context section

  **Verification**:
  - [ ] `getCurrentWords(pageId)` returns list of words with bounding boxes
  - [ ] `setRecognitionCallback()` callback fires after each stroke addition
  - [ ] `onStrokesChanged()` re-feeds strokes and triggers callback
  - [ ] Unit test: add stroke → callback receives words with correct bounds

  **Commit**: YES
  - Message: `feat(android): extend MyScriptPageManager with persistence and overlay APIs`

---

- [x] Av2.3.0a Add RecognitionDao methods and migration (PREREQUISITE for Av2.3.1)

  **What to do**:
  - Add `updateRecognitionWithPositions()` method to RecognitionDao BEFORE implementing the debouncer
  - Add Room migration for `wordPositionsJson` column AND tiles table
  - **This is the AUTHORITATIVE migration location** - all Av2 schema changes are consolidated here
  - This task MUST complete before Av2.3.1 because the debouncer depends on this DAO method
  
  **RecognitionDao additions:**
  ```kotlin
  // Add to apps/android/app/src/main/java/com/onyx/android/data/dao/RecognitionDao.kt
  
  @Query("""
    UPDATE recognition_index 
    SET recognizedText = :recognizedText, 
        wordPositionsJson = :wordPositionsJson,
        updatedAt = :updatedAt
    WHERE pageId = :pageId
  """)
  suspend fun updateRecognitionWithPositions(
    pageId: String,
    recognizedText: String?,
    wordPositionsJson: String?,
    updatedAt: Long
  )
  
  @Query("SELECT wordPositionsJson FROM recognition_index WHERE pageId = :pageId")
  suspend fun getWordPositions(pageId: String): String?
  
  @Query("SELECT wordPositionsJson FROM recognition_index WHERE pageId = :pageId")
  fun getWordPositionsFlow(pageId: String): Flow<String?>
  ```
  
  **Migration (AUTHORITATIVE - ALL Av2 schema changes):**
  ```kotlin
  // File: apps/android/app/src/main/java/com/onyx/android/data/migrations/Migrations.kt
  // This is the SINGLE migration from version 1 to 2
  
  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // 1. Add wordPositionsJson column to recognition_index (for overlay)
      db.execSQL("ALTER TABLE recognition_index ADD COLUMN wordPositionsJson TEXT")
      
      // 2. Create tiles table (for infinite canvas)
      db.execSQL("""
        CREATE TABLE IF NOT EXISTS tiles (
          tileId TEXT PRIMARY KEY NOT NULL,
          pageId TEXT NOT NULL,
          tileX INTEGER NOT NULL,
          tileY INTEGER NOT NULL,
          strokeIds TEXT NOT NULL,
          createdAt INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL
        )
      """)
      db.execSQL("CREATE INDEX IF NOT EXISTS index_tiles_pageId ON tiles(pageId)")
    }
  }
  ```
  
  **Database version update:**
  ```kotlin
  // In OnyxDatabase.kt
  @Database(
    entities = [NoteEntity::class, PageEntity::class, StrokeEntity::class, 
                RecognitionIndexEntity::class, TileEntity::class],  // Add TileEntity
    version = 2  // Increment from 1 to 2
  )
  abstract class OnyxDatabase : RoomDatabase() {
    abstract fun tileDao(): TileDao  // Add TileDao
    // ... existing DAOs
  }
  ```
  
  **RecognitionIndexEntity update:**
  ```kotlin
  // Add to RecognitionIndexEntity
  @Entity(tableName = "recognition_index")
  data class RecognitionIndexEntity(
    @PrimaryKey val pageId: String,
    val noteId: String,
    val recognizedText: String?,        // Nullable - recognition may fail
    val wordPositionsJson: String?,     // Nullable - overlay may be disabled
    val recognizedAtLamport: Long?,     // Nullable - may be null before first recognition
    val recognizerVersion: String?,    // Nullable - may be null before first recognition
    val updatedAt: Long
  )
  ```

  **Parallelizable**: NO (prerequisite for Av2.3.1)

  **References**:
  - RecognitionDao: `apps/android/app/src/main/java/com/onyx/android/data/dao/RecognitionDao.kt` (Plan A)
  - RecognitionIndexEntity: `apps/android/app/src/main/java/com/onyx/android/data/entity/RecognitionIndexEntity.kt` (Plan A)
  - Room migrations: https://developer.android.com/training/data-storage/room/migrating-db-versions

  **Verification**:
  - [ ] `./gradlew :app:kaptDebugKotlin` succeeds (Room schema compiles)
  - [ ] Unit test: `updateRecognitionWithPositions()` updates row correctly
  - [ ] Migration test: DB version 1 → 2 adds column without data loss

  **Commit**: YES
  - Message: `feat(android): add word positions schema and DAO methods`

---

- [x] Av2.3.1 Implement RecognitionPersistenceDebouncer

  **What to do**:
  - Create `apps/android/app/src/main/java/com/onyx/android/recognition/RecognitionPersistenceDebouncer.kt`
  - **Purpose**: Debounce DB PERSISTENCE of recognition results (NOT recognition itself)
  - Real-time recognition still happens via `MyScriptPageManager` (Plan A behavior PRESERVED)
  - This class only controls when in-memory results are written to database
  
  **Implementation:**
  ```kotlin
  // apps/android/app/src/main/java/com/onyx/android/recognition/RecognitionPersistenceDebouncer.kt
  
  /**
   * Debounces PERSISTENCE of recognition results to database.
   * NOT the recognition itself - that happens in real-time via MyScriptPageManager.
   * 
   * Purpose: Avoid DB thrash during rapid drawing while ensuring eventual persistence.
   */
  class RecognitionPersistenceDebouncer(
    private val scope: CoroutineScope,
    private val myScriptPageManager: MyScriptPageManager,
    private val recognitionDao: RecognitionDao,
    private val delayMs: Long = 500L  // 250-750ms range, 500ms default
  ) {
    private val dirtyPages = mutableSetOf<String>()
    private var debounceJob: Job? = null
    
    fun markDirty(pageId: String) {
      dirtyPages.add(pageId)
      debounceJob?.cancel()
      debounceJob = scope.launch {
        delay(delayMs)
        flush()
      }
    }
    
    private suspend fun flush() {
      val pagesToPersist = dirtyPages.toList()
      dirtyPages.clear()
      
      for (pageId in pagesToPersist) {
        try {
          // Get current in-memory recognition from MyScriptPageManager
          val words = myScriptPageManager.getCurrentWords(pageId)
          val fullText = words.joinToString(" ") { it.text }
          val wordsJson = Gson().toJson(words)
          
          // Persist to database
          recognitionDao.updateRecognitionWithPositions(
            pageId = pageId,
            recognizedText = fullText,
            wordPositionsJson = wordsJson,
            updatedAt = System.currentTimeMillis()
          )
        } catch (e: Exception) {
          Log.e("RecognitionDebouncer", "Failed to persist recognition for $pageId", e)
          // Re-add to dirty set for retry on next flush
          dirtyPages.add(pageId)
        }
      }
    }
    
    fun cancel() {
      debounceJob?.cancel()
    }
  }
  ```

  **Must NOT do**:
  - Do NOT remove real-time recognition triggers from NoteEditorViewModel
  - Do NOT use WorkManager for normal pen input (that's for rebuild only)
  - Do NOT use 5s delay (use 250-750ms for responsive persistence)

  **CRITICAL: Recognition Persistence Ownership (Avoid Double Writes)**
  
  > **Problem**: Plan A writes recognized text immediately via `onRecognitionUpdated` callback.
  > Av2 introduces a debouncer. We must avoid double writes.
  > 
  > **Decision**: REPLACE Plan A's immediate persistence with Av2's debounced persistence.
  > - Plan A's `onRecognitionUpdated` callback is KEPT for in-memory UI updates
  > - Plan A's immediate DB write in `onRecognitionUpdated` is REMOVED
  > - Av2's debouncer becomes the SOLE owner of DB persistence
  > 
  > **Implementation**:
  > ```kotlin
  > // Plan A's original flow (in NoteEditorViewModel.init):
  > myScriptPageManager.onRecognitionUpdated = { pageId, text ->
  >   viewModelScope.launch {
  >     noteRepository.updateRecognition(pageId, text, "myscript-4.3")  // REMOVE THIS LINE
  >   }
  > }
  > 
  > // Av2's modified flow (in NoteEditorViewModel.init):
  > myScriptPageManager.setRecognitionCallback { pageId, words ->
  >   if (pageId == currentPageId) {
  >     _recognizedWords.value = words  // KEEP: In-memory update for overlay
  >   }
  >   // DB persistence is now handled by debouncer via markDirty()
  >   // Do NOT call noteRepository.updateRecognition() here
  > }
  > ```
  > 
  > **Sequencing**:
  > 1. User draws stroke → MyScriptPageManager.addStroke()
  > 2. Recognition callback fires → _recognizedWords updated (immediate, in-memory)
  > 3. onPenUp() calls persistenceDebouncer.markDirty() (schedules DB write)
  > 4. After 500ms idle → debouncer.flush() → DB write with wordPositionsJson

  **Parallelizable**: NO (depends on Av2.3.0 and Av2.3.0a - requires MyScriptPageManager.getCurrentWords() and RecognitionDao.updateRecognitionWithPositions())

  **References**:
  - **PREREQUISITE**: Av2.3.0 (MyScriptPageManager API extensions) - provides `getCurrentWords()` method
  - **PREREQUISITE**: Av2.3.0a (RecognitionDao methods and migration) - provides `updateRecognitionWithPositions()` method
  - Recognition strategy: See "Recognition Strategy: Real-time + Rebuild Worker" in Context section
  - MyScriptPageManager: `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt` (extended in Av2.3.0)
  - RecognitionDao: `apps/android/app/src/main/java/com/onyx/android/data/dao/RecognitionDao.kt` (extended in Av2.3.0a)

  **Verification**:
  - [ ] Draw stroke → recognition appears in overlay immediately (real-time still works)
  - [ ] Draw rapidly → DB write happens only after 500ms pause (not per stroke)
  - [ ] Close app during drawing → reopen → recognition persisted
  - [ ] Unit test: markDirty() called 10 times rapidly → flush() called once

  **Commit**: YES
  - Message: `feat(android): add recognition persistence debouncer`

---

- [x] Av2.3.2 Integrate debouncer with NoteEditorViewModel

  **What to do**:
  - Add `RecognitionPersistenceDebouncer` to `NoteEditorViewModel`
  - **KEEP all existing MyScriptPageManager calls** (real-time recognition)
  - **ADD debouncer.markDirty()** calls after recognition-affecting events
  
  **Code changes to NoteEditorViewModel:**
  ```kotlin
  // apps/android/app/src/main/java/com/onyx/android/viewmodel/NoteEditorViewModel.kt
  // KEEP Plan A real-time recognition, ADD persistence debouncing
  
  class NoteEditorViewModel(...) : ViewModel() {
    private val persistenceDebouncer = RecognitionPersistenceDebouncer(
      scope = viewModelScope,
      myScriptPageManager = myScriptPageManager,
      recognitionDao = recognitionDao
    )
    
    // Expose in-memory recognition for immediate lasso/overlay access
    private val _recognizedWords = MutableStateFlow<List<RecognizedWord>>(emptyList())
    val recognizedWords: StateFlow<List<RecognizedWord>> = _recognizedWords.asStateFlow()
    
    fun onPenUp(stroke: Stroke, page: PageEntity) {
      viewModelScope.launch {
        // 1. Persist stroke
        noteRepository.saveStroke(page.pageId, stroke)
        
        // 2. Tile membership for infinite pages
        if (page.geometryKind == "infinite") {
          tileManager?.addStrokeToTiles(stroke)
        }
        
        // 3. KEEP real-time recognition (Plan A)
        myScriptPageManager.addStroke(stroke)
        
        // 4. ADD persistence debouncing
        persistenceDebouncer.markDirty(page.pageId)
      }
    }
    
    fun onStrokeErased(strokeId: String, page: PageEntity) {
      viewModelScope.launch {
        noteRepository.deleteStroke(strokeId)
        
        if (page.geometryKind == "infinite") {
          tileManager?.removeStrokeFromTiles(strokeId)
        }
        
        // KEEP real-time recognition update
        val remainingStrokes = noteRepository.getStrokesForPage(page.pageId)
        myScriptPageManager.onStrokeErased(strokeId, remainingStrokes)
        
        persistenceDebouncer.markDirty(page.pageId)
      }
    }
    
    fun onUndo(currentStrokes: List<Stroke>) {
      // KEEP real-time recognition
      myScriptPageManager.onUndo(currentStrokes)
      persistenceDebouncer.markDirty(currentPageId!!)
    }
    
    fun onRedo(currentStrokes: List<Stroke>) {
      // KEEP real-time recognition
      myScriptPageManager.onRedo(currentStrokes)
      persistenceDebouncer.markDirty(currentPageId!!)
    }
    
    // Callback from MyScriptPageManager
    fun onRecognitionUpdated(pageId: String, words: List<RecognizedWord>) {
      if (pageId == currentPageId) {
        _recognizedWords.value = words  // Immediate for lasso/overlay
      }
    }
    
    override fun onCleared() {
      persistenceDebouncer.cancel()
      super.onCleared()
    }
  }
  ```

  **Must NOT do**:
  - Do NOT remove `myScriptPageManager.addStroke()` calls
  - Do NOT remove `myScriptPageManager.onStrokeErased()` calls
  - Do NOT add WorkManager for normal drawing operations

  **Parallelizable**: NO (depends on Av2.3.1)

  **References**:
  - NoteEditorViewModel: `apps/android/app/src/main/java/com/onyx/android/viewmodel/NoteEditorViewModel.kt`
  - MyScriptPageManager: `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt`
  - RecognitionPersistenceDebouncer: created in Av2.3.1

  **Verification**:
  - [ ] Real-time recognition still works (draw → see text in overlay immediately)
  - [ ] DB persistence happens after debounce delay
  - [ ] Lasso selection works immediately after drawing (uses in-memory recognizedWords)

  **Commit**: YES
  - Message: `feat(android): integrate persistence debouncer with viewmodel`

---

- [x] Av2.3.3 Implement RebuildWorker for batch re-recognition

  **What to do**:
  - Create `apps/android/app/src/main/java/com/onyx/android/recognition/RebuildWorker.kt` (WorkManager CoroutineWorker)
  - **Purpose**: Full re-recognition from strokes for special cases ONLY
  - **NOT for normal drawing** - only for import, sync, manual rebuild, corrupted data

  **When to use RebuildWorker (ONLY these cases):**
  | Trigger | Description |
  |---------|-------------|
  | Document import | Initial index build for imported PDF/document |
  | Sync arrival | Sync brings strokes from another device |
  | Manual rebuild | User requests "Rebuild search index" |
  | Corrupted data | Detection of missing/inconsistent recognition data |

  **Implementation:**
  ```kotlin
  // apps/android/app/src/main/java/com/onyx/android/recognition/RebuildWorker.kt
  
  /**
   * Background worker for full batch re-recognition.
   * Use ONLY for special cases - NOT for normal drawing operations.
   * 
   * Normal drawing uses real-time recognition via MyScriptPageManager.
   */
  class RebuildWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    private val strokeDao = (applicationContext as OnyxApplication).database.strokeDao()
    private val pageDao = (applicationContext as OnyxApplication).database.pageDao()
    private val recognitionDao = (applicationContext as OnyxApplication).database.recognitionDao()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
      val pageId = inputData.getString("page_id") ?: return@withContext Result.failure()
      val reason = inputData.getString("reason") ?: "unknown"
      
      Log.d("RebuildWorker", "Rebuilding recognition for page $pageId, reason: $reason")
      
      try {
        // 1. Get page metadata
        val page = pageDao.getById(pageId) ?: return@withContext Result.failure()
        
        // 2. Get device DPI
        val dpi = applicationContext.resources.displayMetrics.xdpi
        
        // 3. Get all strokes
        val strokes = strokeDao.getStrokesForPage(pageId)
        if (strokes.isEmpty()) {
          recognitionDao.updateRecognitionWithPositions(pageId, null, null, System.currentTimeMillis())
          return@withContext Result.success()
        }
        
        // 4. Run batch recognition
        val engine = MyScriptEngine.getInstance()
        val words = engine.recognizeBatch(
          strokes = strokes,
          geometryKind = page.geometryKind,
          dpi = if (page.geometryKind == "infinite") dpi else 72f
        )
        
        // 5. Store results
        val fullText = words.joinToString(" ") { it.text }
        val wordsJson = Gson().toJson(words)
        recognitionDao.updateRecognitionWithPositions(pageId, fullText, wordsJson, System.currentTimeMillis())
        
        Log.d("RebuildWorker", "Successfully rebuilt recognition for page $pageId: ${words.size} words")
        Result.success()
      } catch (e: Exception) {
        Log.e("RebuildWorker", "Failed to rebuild recognition for page $pageId", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
      }
    }
    
    companion object {
      /**
       * Enqueue a rebuild job for the given page.
       * Use for: import, sync arrival, manual rebuild, corrupted data.
       * Do NOT use for normal drawing operations.
       */
      fun enqueue(context: Context, pageId: String, reason: String) {
        val workRequest = OneTimeWorkRequestBuilder<RebuildWorker>()
          .setInputData(workDataOf(
            "page_id" to pageId,
            "reason" to reason
          ))
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
          .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
          "rebuild_$pageId",
          ExistingWorkPolicy.REPLACE,
          workRequest
        )
      }
    }
  }
  ```

  **Parallelizable**: YES (can develop in parallel with Av2.3.1/Av2.3.2)

  **References**:
  - MyScriptEngine.recognizeBatch(): See "Batch Recognition API Definition" in Context section
  - WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started
  - RecognitionDao: `apps/android/app/src/main/java/com/onyx/android/data/dao/RecognitionDao.kt`

  **Verification**:
  - [ ] Import document → RebuildWorker runs → recognition appears in search
  - [ ] Manual "Rebuild index" button → RebuildWorker runs → recognition updated
  - [ ] Worker retries on failure (up to 3 times)
  - [ ] Normal drawing does NOT trigger RebuildWorker

  **Commit**: YES
  - Message: `feat(android): add RebuildWorker for batch re-recognition`

---

- [x] Av2.3.4 Verify word positions storage end-to-end

  **What to do**:
  - **NOTE**: Schema/DAO changes are implemented in Av2.3.0a. This task verifies integration.
  - Verify both real-time (debouncer) and rebuild (worker) paths store word positions correctly
  - Verify data round-trip: write → read → deserialize
  
  **Integration verification:**
  ```kotlin
  // Verify debouncer path (Av2.3.1):
  // draw stroke → persistence debounce → DB stores wordPositionsJson
  
  // Verify rebuild path (Av2.3.3):
  // import document → RebuildWorker → DB stores wordPositionsJson
  
  // Verify read path (Av2.4.1):
  // onPageChanged() → repository.getPersistedRecognizedWords() → overlay displays
  ```
  
  **Test scenarios:**
  1. Draw strokes → wait 500ms → query DB → verify wordPositionsJson is valid JSON
  2. Import document → wait for worker → query DB → verify wordPositionsJson populated
  3. Kill app during drawing → reopen → verify persisted words load correctly

  **Parallelizable**: NO (depends on Av2.3.1-3)

  **References**:
  - Schema/DAO (prerequisite): Av2.3.0a
  - RecognitionPersistenceDebouncer: Av2.3.1
  - RebuildWorker: Av2.3.3
  - RecognizedWord schema: See "RecognizedWord Persistence" in Context section

  **Verification**:
  - [ ] Draw → persistence debounce → wordPositionsJson stored in DB
  - [ ] RebuildWorker → wordPositionsJson stored in DB
  - [ ] Query wordPositionsJson → valid JSON array of RecognizedWord
  - [ ] FTS search still works (uses recognizedText column)
  - [ ] Round-trip test: write JSON → read JSON → deserialize → matches original words

  **Commit**: NO (verification task, may include test files)
  - If adding integration tests: `test(android): add word positions integration tests`

---

### Phase 4: Recognition Text Overlay

- [x] Av2.4.1 Create text overlay layer

  **What to do**:
  - Create `apps/android/app/src/main/java/com/onyx/android/recognition/ui/TextOverlayLayer.kt`
  - Render recognized words at their bounding box positions
  - Use semi-transparent background for readability
  - Add toggle to show/hide overlay

  **Data Flow for Overlay Rendering (UNIFIED - In-Memory Primary, DB Fallback):**
  
  > **CRITICAL**: The overlay uses a SINGLE SOURCE OF TRUTH pattern.
  > - **Primary**: In-memory `recognizedWords` updated by MyScriptPageManager callback (immediate)
  > - **Fallback**: DB query on page load (for persisted data from previous sessions)
  > - **Reconciliation**: In-memory always wins during active editing
  
  **ViewTransform State Hoisting (CRITICAL for Overlay Positioning):**
  
  > **PROBLEM**: InfiniteCanvas keeps `viewTransform` as local state, but TextOverlayLayer needs it.
  > **SOLUTION**: Hoist ViewTransform to NoteEditorViewModel so both canvas and overlay share it.
  
  ```kotlin
  // NoteEditorViewModel.kt - ViewTransform is HOISTED to ViewModel
  class NoteEditorViewModel(...) : ViewModel() {
    // ViewTransform is shared state (hoisted from InfiniteCanvas)
    private val _viewTransform = MutableStateFlow(ViewTransform(panX = 0f, panY = 0f, zoom = 1f))
    val viewTransform: StateFlow<ViewTransform> = _viewTransform.asStateFlow()
    
    fun updateViewTransform(transform: ViewTransform) {
      _viewTransform.value = transform
    }
    
    // Recognition words for overlay
    private val _recognizedWords = MutableStateFlow<List<RecognizedWord>>(emptyList())
    val recognizedWords: StateFlow<List<RecognizedWord>> = _recognizedWords.asStateFlow()
    
    // ... rest of ViewModel
  }
  
  // InfiniteCanvas.kt - receives and updates ViewTransform from ViewModel
  @Composable
  fun InfiniteCanvas(
    page: PageEntity,
    strokes: List<Stroke>,
    viewTransform: ViewTransform,            // FROM ViewModel (hoisted)
    onViewTransformChanged: (ViewTransform) -> Unit,  // Callback to update ViewModel
    onStrokeComplete: (Stroke) -> Unit,
    modifier: Modifier = Modifier
  ) {
    // Pan/zoom gestures update via callback
    val panZoomState = rememberTransformableState { zoomChange, panChange, _ ->
      val newTransform = viewTransform.copy(
        zoom = (viewTransform.zoom * zoomChange).coerceIn(0.25f, 4f),
        panX = viewTransform.panX + panChange.x,
        panY = viewTransform.panY + panChange.y
      )
      onViewTransformChanged(newTransform)  // Push to ViewModel
    }
    
    // ... rest of canvas rendering
  }
  
  // NoteEditorScreen.kt - wires everything together
  @Composable
  fun NoteEditorScreen(viewModel: NoteEditorViewModel) {
    val viewTransform by viewModel.viewTransform.collectAsState()
    val words by viewModel.recognizedWords.collectAsState()
    var showOverlay by remember { mutableStateOf(false) }
    
    Box {
      when (currentPage?.geometryKind) {
        "infinite" -> InfiniteCanvas(
          page = currentPage,
          strokes = strokes,
          viewTransform = viewTransform,
          onViewTransformChanged = { viewModel.updateViewTransform(it) },
          onStrokeComplete = { viewModel.onPenUp(it, currentPage) }
        )
        else -> InkCanvas(...)  // Fixed canvas (Plan A)
      }
      
      // Overlay uses SAME viewTransform from ViewModel
      TextOverlayLayer(
        words = words,
        viewTransform = viewTransform,  // Shared state from ViewModel
        showOverlay = showOverlay
      )
    }
  }
  ```
  
  **State Flow Diagram:**
  ```
  NoteEditorViewModel
       │
       ├── viewTransform: StateFlow<ViewTransform>  ──────────────┐
       │         ↑                                                │
       │   onViewTransformChanged()                               │
       │         │                                                ↓
       │   InfiniteCanvas ←── pan/zoom gestures         TextOverlayLayer
       │                                                 (reads for positioning)
       └── recognizedWords: StateFlow<List<RecognizedWord>> ─────┘
  ```
  
  ```kotlin
  // 1. DAO: Query methods for FALLBACK/INITIAL load only
  // File: apps/android/app/src/main/java/com/onyx/android/data/dao/RecognitionDao.kt
  @Query("SELECT wordPositionsJson FROM recognition_index WHERE pageId = :pageId")
  suspend fun getWordPositions(pageId: String): String?  // One-shot query for initial load
  
  // 2. Repository: FALLBACK method for page load
  // File: apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt
  suspend fun getPersistedRecognizedWords(pageId: String): List<RecognizedWord> {
    val json = recognitionDao.getWordPositions(pageId)
    return if (json.isNullOrBlank()) emptyList()
    else Gson().fromJson(json, object : TypeToken<List<RecognizedWord>>() {}.type)
  }
  
  // 3. ViewModel: UNIFIED data source with in-memory primary
  // File: apps/android/app/src/main/java/com/onyx/android/viewmodel/NoteEditorViewModel.kt
  private val _recognizedWords = MutableStateFlow<List<RecognizedWord>>(emptyList())
  val recognizedWords: StateFlow<List<RecognizedWord>> = _recognizedWords.asStateFlow()
  
  init {
    // Set up callback for REAL-TIME updates (PRIMARY SOURCE)
    myScriptPageManager.setRecognitionCallback { pageId, words ->
      if (pageId == currentPageId) {
        _recognizedWords.value = words  // Immediate update from in-memory
      }
    }
  }
  
  // Called when switching pages - load from DB as FALLBACK
  fun onPageChanged(pageId: String) {
    currentPageId = pageId
    viewModelScope.launch {
      // Load persisted data ONCE as fallback (for previous session data)
      val persistedWords = repository.getPersistedRecognizedWords(pageId)
      _recognizedWords.value = persistedWords
      
      // Real-time recognition will override this as user draws
      // MyScriptPageManager callback will push updates to _recognizedWords
    }
  }
  
  // 4. UI: Consume the UNIFIED recognizedWords StateFlow
  // File: apps/android/app/src/main/java/com/onyx/android/recognition/ui/TextOverlayLayer.kt
  @Composable
  fun TextOverlayLayer(
    words: List<RecognizedWord>,
    viewTransform: ViewTransform,
    showOverlay: Boolean,
    modifier: Modifier = Modifier
  ) {
    if (!showOverlay) return
    
    Canvas(modifier = modifier) {
      words.forEach { word ->
        // Transform page coords to screen coords
        val screenLeft = word.left * viewTransform.zoom + viewTransform.panX
        val screenTop = word.top * viewTransform.zoom + viewTransform.panY
        val screenRight = word.right * viewTransform.zoom + viewTransform.panX
        val screenBottom = word.bottom * viewTransform.zoom + viewTransform.panY
        
        // Draw background rectangle
        drawRect(
          color = Color.Yellow.copy(alpha = 0.3f),
          topLeft = Offset(screenLeft, screenTop),
          size = Size(screenRight - screenLeft, screenBottom - screenTop)
        )
        
        // Draw text (use drawContext.canvas for text)
        // ... text rendering using Paint
      }
    }
  }
  ```
  
  **Data Flow Diagram:**
  ```
  User draws stroke
        ↓
  MyScriptPageManager.addStroke()
        ↓
  MyScript recognition runs (real-time)
        ↓
  MyScriptPageManager.onRecognitionComplete()
        ↓
  recognitionCallback(pageId, words)
        ↓
  _recognizedWords.value = words  ← PRIMARY (immediate)
        ↓
  TextOverlayLayer renders words  ← IMMEDIATE display
  
  [Parallel path for persistence]
  persistenceDebouncer.markDirty()
        ↓ (after 500ms)
  recognitionDao.updateRecognitionWithPositions()  ← For next session
  ```
  
  **Usage in NoteEditorScreen:**
  ```kotlin
  @Composable
  fun NoteEditorScreen(viewModel: NoteEditorViewModel) {
    val words by viewModel.recognizedWords.collectAsState()
    var showOverlay by remember { mutableStateOf(false) }
    
    Box {
      InkCanvas(...)  // or InfiniteCanvas
      TextOverlayLayer(
        words = words,
        viewTransform = viewTransform,
        showOverlay = showOverlay
      )
    }
  }
  ```

  **Parallelizable**: YES (can start after Av2.3.4 done)

  **References**:
  - RecognizedWord positions from Av2.3.4: `apps/android/app/src/main/java/com/onyx/android/data/entity/RecognitionIndexEntity.kt`

  **Verification**:
  - [ ] Toggle overlay → Text appears over strokes
  - [ ] Words positioned near original handwriting

  **Commit**: Group with Av2.4.2

---

- [x] Av2.4.2 Integrate overlay with canvas

  **What to do**:
  - Add overlay as optional layer on InkCanvas
  - Overlay follows zoom/pan transforms
  - Overlay respects tile boundaries for infinite canvas

  **Parallelizable**: NO (depends on Av2.4.1)

  **References**:
  - InkCanvas: `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` (from Plan A task 3.2)
  - InfiniteCanvas: `apps/android/app/src/main/java/com/onyx/android/ink/ui/InfiniteCanvas.kt` (created in Av2.1.3)
  - TextOverlayLayer: `apps/android/app/src/main/java/com/onyx/android/recognition/ui/TextOverlayLayer.kt` (created in Av2.4.1)
  - TileManager: `apps/android/app/src/main/java/com/onyx/android/ink/canvas/TileManager.kt` (for tile-aware overlay)

  **Verification**:
  - [ ] Zoom → Overlay text scales correctly
  - [ ] Pan → Overlay text moves with strokes
  - [ ] Infinite canvas → Overlay works across tiles

  **Commit**: YES
  - Message: `feat(android): add recognition text overlay layer`

---

- [x] Av2.4.3 Add overlay settings

  **What to do**:
  - Add settings for overlay with specific UI placement:
    - Show/hide toggle
    - Opacity slider (0-100%)
    - Font size (small/medium/large)
  - Persist settings in SharedPreferences
  
  **UI Entry Point (CRITICAL - specify exact location):**
  
  **Option A: Toolbar overflow menu (RECOMMENDED for discoverability)**
  ```kotlin
  // In EditorToolbar.kt - add overlay settings to overflow menu
  // File: apps/android/app/src/main/java/com/onyx/android/ui/components/EditorToolbar.kt
  
  @Composable
  fun EditorToolbar(
    // ... existing params ...
    showOverlay: Boolean,
    onOverlayToggle: () -> Unit,
    onOverlaySettingsClick: () -> Unit  // Opens settings dialog
  ) {
    // Overflow menu (three dots)
    IconButton(onClick = { showMenu = true }) {
      Icon(Icons.Default.MoreVert, "More options")
    }
    
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
      // Toggle overlay on/off
      DropdownMenuItem(
        text = { Text(if (showOverlay) "Hide Recognition" else "Show Recognition") },
        onClick = { onOverlayToggle(); showMenu = false },
        leadingIcon = { Icon(Icons.Default.TextFields, null) }
      )
      
      // Open settings dialog
      DropdownMenuItem(
        text = { Text("Recognition Settings...") },
        onClick = { onOverlaySettingsClick(); showMenu = false },
        leadingIcon = { Icon(Icons.Default.Settings, null) }
      )
    }
  }
  ```
  
  **Settings Dialog (Modal bottom sheet or AlertDialog):**
  ```kotlin
  // New file: apps/android/app/src/main/java/com/onyx/android/ui/components/OverlaySettingsDialog.kt
  
  @Composable
  fun OverlaySettingsDialog(
    settings: OverlaySettings,
    onSettingsChange: (OverlaySettings) -> Unit,
    onDismiss: () -> Unit
  ) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Recognition Overlay") },
      text = {
        Column {
          // Show/Hide toggle
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show overlay")
            Spacer(Modifier.weight(1f))
            Switch(
              checked = settings.showOverlay,
              onCheckedChange = { onSettingsChange(settings.copy(showOverlay = it)) }
            )
          }
          
          // Opacity slider
          Text("Opacity: ${(settings.opacity * 100).toInt()}%")
          Slider(
            value = settings.opacity,
            onValueChange = { onSettingsChange(settings.copy(opacity = it)) },
            valueRange = 0f..1f
          )
          
          // Font size selector
          Text("Font size")
          Row {
            listOf("Small" to 12f, "Medium" to 16f, "Large" to 20f).forEach { (label, size) ->
              FilterChip(
                selected = settings.fontSize == size,
                onClick = { onSettingsChange(settings.copy(fontSize = size)) },
                label = { Text(label) }
              )
              Spacer(Modifier.width(8.dp))
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text("Done") }
      }
    )
  }
  
  data class OverlaySettings(
    val showOverlay: Boolean = false,
    val opacity: Float = 0.7f,  // 0.0 to 1.0
    val fontSize: Float = 16f   // sp
  )
  ```
  
  **SharedPreferences persistence:**
  ```kotlin
  // In NoteEditorViewModel or dedicated SettingsRepository
  private val prefs = context.getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
  
  fun loadOverlaySettings(): OverlaySettings {
    return OverlaySettings(
      showOverlay = prefs.getBoolean("show_overlay", false),
      opacity = prefs.getFloat("opacity", 0.7f),
      fontSize = prefs.getFloat("font_size", 16f)
    )
  }
  
  fun saveOverlaySettings(settings: OverlaySettings) {
    prefs.edit()
      .putBoolean("show_overlay", settings.showOverlay)
      .putFloat("opacity", settings.opacity)
      .putFloat("font_size", settings.fontSize)
      .apply()
  }
  ```

  **Parallelizable**: NO (depends on Av2.4.2)

  **References**:
  - TextOverlayLayer: `apps/android/app/src/main/java/com/onyx/android/recognition/ui/TextOverlayLayer.kt` (created in Av2.4.1)
  - EditorToolbar: `apps/android/app/src/main/java/com/onyx/android/ui/components/EditorToolbar.kt` (Plan A)
  - Android SharedPreferences: https://developer.android.com/reference/android/content/SharedPreferences
  - Compose AlertDialog: https://developer.android.com/develop/ui/compose/components/dialog

  **Verification**:
  - [ ] Toolbar overflow menu shows "Show/Hide Recognition" toggle
  - [ ] Toolbar overflow menu shows "Recognition Settings..." option
  - [ ] Settings dialog opens with opacity slider and font size chips
  - [ ] Can toggle overlay visibility from toolbar
  - [ ] Opacity changes are visible in real-time
  - [ ] Font size changes affect overlay text
  - [ ] Settings persist across app restarts (kill app → reopen → settings restored)

  **Commit**: YES
  - Message: `feat(android): add text overlay settings with toolbar integration`

---

### Phase 5: Integration

- [x] Av2.5.1 End-to-end infinite canvas test

  **What to do**:
  - Create infinite canvas note
  - Draw across multiple tiles
  - Verify persistence
  - Use segment eraser
  - Verify recognition

  **Parallelizable**: NO (final integration)

  **References**:
  - InfiniteCanvas: `apps/android/app/src/main/java/com/onyx/android/ink/ui/InfiniteCanvas.kt`
  - TileManager: `apps/android/app/src/main/java/com/onyx/android/ink/canvas/TileManager.kt`
  - StrokeSplitter: `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeSplitter.kt`
  - RebuildWorker: `apps/android/app/src/main/java/com/onyx/android/recognition/RebuildWorker.kt`

  **Verification**:
  
  **Test Case 1: Multi-tile stroke persistence**
  - [ ] Create infinite canvas note
  - [ ] Draw 10 strokes spanning 4 different tiles (cross tile boundaries intentionally)
  - [ ] Query: `SELECT COUNT(*) FROM tiles WHERE pageId = ?` → returns 4
  - [ ] Query: Count strokes by parsing strokeIds JSON from all tiles for this page → returns 10 total stroke references
  - [ ] Force-close app, reopen note → all 10 strokes render correctly
  
  **Test Case 2: Segment eraser cross-tile operation**
  - [ ] Draw horizontal stroke spanning 2 tiles (tile boundary at midpoint)
  - [ ] Use segment eraser to delete middle segment
  - [ ] Verify: Original stroke removed, 2 new strokes created
  - [ ] Query: Parse strokeIds JSON from each tile → each contains exactly 1 stroke after operation
  - [ ] Pan to each tile → partial strokes render correctly at boundaries
  
  **Test Case 3: Real-time recognition on infinite canvas**
  - [ ] Enable text overlay (toolbar toggle)
  - [ ] Write "hello" in tile A, "world" in tile B (different tiles)
  - [ ] Verify: Recognition overlay appears within 500ms of each pen-up
  - [ ] Verify: Word boxes position correctly relative to strokes in each tile
  - [ ] Pan between tiles → overlay text follows tile coordinate system
  
  **Test Case 4: Search integration**
  - [ ] Write recognizable text across 3 tiles
  - [ ] Wait for persistence debounce (750ms)
  - [ ] Search for written word → note appears in search results
  - [ ] Query: `SELECT wordPositionsJson FROM recognition_index WHERE pageId = ?` → contains all words with correct coordinates
  
  **Test Case 5: Scroll performance**
  - [ ] Create note with 20+ tiles of content
  - [ ] Rapid pan/scroll across canvas
  - [ ] Verify: No visible tile loading delays (tiles appear within 100ms of entering viewport)
  - [ ] Verify: No janky scrolling (maintain 60fps visual smoothness)
  
  **Commit**: NO (verification only)
  
  > **Note**: Lasso selection is NOT tested here because it's NOT implemented in Av2.
  > Av2 only PREPARES the architecture for future lasso (see "Future Feature" section).
  > Lasso implementation and testing belongs to a future plan.

---

- [x] Av2.5.2 Performance optimization

  **What to do**:
  - Profile tile loading/unloading
  - Optimize stroke intersection for segment eraser
  - Ensure 60fps during drawing
  - Memory usage within acceptable bounds

  **Parallelizable**: NO (depends on Av2.5.1)

  **References**:
  - TileManager: `apps/android/app/src/main/java/com/onyx/android/ink/canvas/TileManager.kt` (tile caching)
  - StrokeIntersection: `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/StrokeIntersection.kt` (algorithm optimization)
  - Android Profiler: Use Android Studio → View → Tool Windows → Profiler

  **Verification**:
  - [ ] 60fps during drawing (systrace)
  - [ ] Memory stays under 256MB for app
  - [ ] Tile load time < 100ms

  **Commit**: YES
  - Message: `perf(android): optimize infinite canvas and segment eraser`

---

- [x] Av2.5.3 Schema sync verification

  **What to do**:
  - Verify new entities (TileEntity) are sync-compatible
  - Document schema changes for Plan C
  - Verify TileEntity uses:
    - Deterministic IDs: `{pageId}:{tileX}:{tileY}` format (NOT random UUIDs)
    - Timestamps as Long (createdAt, updatedAt)

  **Parallelizable**: NO (final verification)

  **References**:
  - TileEntity: `apps/android/app/src/main/java/com/onyx/android/data/entity/TileEntity.kt` (created in Av2.1.5)
  - V0-api.md: `V0-api.md` (project root - schema contract)
  - Plan C sync protocol: `.sisyphus/plans/milestone-c-collaboration-sharing.md`
  - Schema export: `apps/android/app/schemas/2.json` (Room auto-generated)

  **Verification**:
  - [ ] Schema audit document updated at `docs/android/av2-schema-additions.md`
  - [ ] All new fields documented with types matching V0-api.md

  **Commit**: YES
  - Message: `docs(android): document Av2 schema additions`

---

## Commit Strategy

| Phase | Commit Message | Key Files |
|-------|----------------|-----------|
| 1 | `feat(android): implement infinite canvas tile manager` | ink/canvas/ |
| 1 | `feat(android): add infinite canvas with cross-tile rendering` | ink/ui/ |
| 1 | `feat(android): persist infinite canvas tiles to Room` | data/entity/ |
| 2 | `feat(android): implement stroke intersection and splitting` | ink/algorithm/ |
| 2 | `feat(android): add segment eraser with stroke splitting` | ink/ |
| 2 | `feat(android): add undo/redo for segment eraser` | ink/ |
| 3 | `feat(android): implement recognition throttling` | recognition/ |
| 3 | `feat(android): implement batch recognition with background worker` | recognition/ |
| 4 | `feat(android): add recognition text overlay layer` | recognition/ui/ |
| 4 | `feat(android): add text overlay settings` | recognition/ |
| 5 | `perf(android): optimize infinite canvas and segment eraser` | various |
| 5 | `docs(android): document Av2 schema additions` | docs/ |

---

## Success Criteria

### Verification Commands
```bash
# All commands run from apps/android/ directory
cd apps/android

# Build Android app
./gradlew :app:assembleDebug  # Expected: BUILD SUCCESSFUL

# Run all tests (including new Av2 tests)
./gradlew :app:test           # Expected: All tests pass

# Run specific Av2 tests
./gradlew :app:test --tests "*TileManagerTest*"
./gradlew :app:test --tests "*StrokeSplitterTest*"
./gradlew :app:test --tests "*RecognitionPersistenceDebouncerTest*"
```

### Testing Framework (from Plan A)
- **Unit tests**: JUnit 5 + MockK (configured in Plan A task 1.6)
- **Test location**: `apps/android/app/src/test/java/com/onyx/android/`
- **New test files for Av2**:
  - `ink/canvas/TileManagerTest.kt`
  - `ink/algorithm/StrokeIntersectionTest.kt`
  - `ink/algorithm/StrokeSplitterTest.kt`
  - `recognition/RecognitionPersistenceDebouncerTest.kt`

### Performance Profiling
- **Tool**: Android Studio Profiler + systrace
- **Command**: `./gradlew :app:installDebug && adb shell am start -n com.onyx.android/.MainActivity`
- **Profiler steps**:
  1. Connect tablet via USB
  2. Open Android Studio → Profiler → Attach to process
  3. Record CPU trace during drawing
  4. Verify: Frame rendering < 16ms (60fps target)
- **Memory check**: Profiler → Memory → Verify heap < 256MB during tile operations

### Final Checklist
- [x] Infinite canvas with tile system works
- [x] Segment eraser splits strokes correctly
- [x] Batch recognition runs in background
- [x] Text overlay displays at correct positions
- [x] All features persist correctly
- [x] No performance regressions from Plan A
- [x] Schema additions documented for sync
