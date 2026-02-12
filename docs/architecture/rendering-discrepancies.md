# Rendering Discrepancies & Missing Features — Onyx vs Notewise

Date: 2026-02-12
Scope: Detailed analysis of visual, performance, and UX gaps observed between Onyx and Notewise, with root causes and remediation status.

---

## Table of Contents

1. [Stroke Rendering & Interpolation](#1-stroke-rendering--interpolation)
2. [Latency & Performance](#2-latency--performance)
3. [Visual Artifacts & Glitches](#3-visual-artifacts--glitches)
4. [Document & Page Handling](#4-document--page-handling)
5. [UI & Overlay Observations](#5-ui--overlay-observations)
6. [Remediation Summary](#6-remediation-summary)

---

## 1. Stroke Rendering & Interpolation

### 1.1 Polygonal/Jagged Lines

**Symptom:** Curved strokes (e.g., the letter "s" or loops) render as a series of straight, connected line segments rather than smooth curves. Notewise produces fluid strokes with clear Bézier curve interpolation.

**Root Cause:** The original smoothing algorithm was a **3-point moving average filter** that averaged adjacent points' positions:
```kotlin
// OLD: 3-point moving average — flattens corners, creates jagged output
x = (previous.x + current.x + next.x) / 3f
y = (previous.y + current.y + next.y) / 3f
```
This approach:
- Over-smooths sharp corners (flattens intentional direction changes)
- Under-smooths gentle curves (too few interpolated points between samples)
- Does not provide C¹ tangent continuity, so curve segments visually "kink" at joins

**Fix Applied:** Replaced with **Catmull-Rom spline interpolation** (`catmullRomSmooth()`):
- Provides C¹ continuity — smooth tangents at every control point
- Passes through all original points exactly — preserves user intent
- Generates 8 subdivision points between each pair of input points
- Tension parameter (0.5) balances smoothness vs. sharpness
- Standard matrix form: `q(t) = 0.5 * [(-t + 2t² - t³)·v0 + (2 - 5t² + 3t³)·v1 + (t + 4t² - 3t³)·v2 + (-t² + t³)·v3]`

**Status:** ✅ Fixed — `InkCanvasDrawing.kt`

---

### 1.2 Lack of Pressure Sensitivity / Variable Width

**Symptom:** Strokes in Onyx have a uniform, static pixel width regardless of input speed or pressure. Notewise displays variable stroke width with tapering at endpoints, mimicking real ink.

**Root Cause (pre-fix):** Although pressure data was captured from the stylus/touch input (`event.getPressure()`), the rendering pipeline used only the **average pressure across all points** in a stroke to compute a single uniform width. Even after per-point width computation was added, the path was still drawn with `drawPath(Stroke(width = averageWidth))` — a single uniform stroke width — so per-point widths were computed but never visually applied.

**Fixes Applied:**

1. **Non-linear pressure curve with gamma correction** (`computePerPointWidths()`):
   - Uses `pressure^0.6` (gamma curve) instead of linear mapping
   - Makes light pressure more responsive — the first 30% of pressure range maps to more width variation
   - Result: more natural "ink-like" feel where gentle strokes are thin and heavy strokes are thick

2. **Variable-width filled outline rendering** (`buildVariableWidthOutline()`):
   - Replaced `drawPath(Stroke(width))` with a filled outline path
   - For each sample point, compute the perpendicular normal using central/forward/backward differences
   - Offset left and right edges by half the per-point width along normals
   - Walk forward along left edge, add semicircular end cap, backward along right edge, close
   - Drawn with `drawPath(Fill)` — no stroke width parameter needed
   - Per-point widths are now truly reflected in the rendered output

3. **Start/end tapering** (`computeTaperFactor()`):
   - First and last 5 points fade width from 15% of full width to 100%
   - Creates natural pen-down and pen-lift taper like real handwriting
   - Taper length adapts to stroke length (short strokes taper less to remain visible)

4. **Stroke bounds include width padding:**
   - `calculateBounds()` now expands by half the maximum stroke width
   - Prevents viewport culling from clipping wide strokes at page edges

**Status:** ✅ Fixed — `InkCanvasDrawing.kt`, `InkCanvasGeometry.kt`, `InkCanvasStroke.kt`

---

### 1.3 Stroke "Pop-in" Effect

**Symptom:** Strokes appear to finish rendering only after the finger/stylus lifts, often snapping from a "draft" visual state to a different finalized state. Notewise shows ink flowing continuously in its final state as drawn.

**Root Cause:** The Onyx rendering uses a dual-layer approach:
1. **In-progress strokes** are rendered by `InProgressStrokesView` (Jetpack Ink API) — a native Android view layered on top of the Compose canvas
2. **Finished strokes** are rendered by the Compose `Canvas` using cached paths in `drawStrokesInWorldSpace()`

On pen-up:
- `InProgressStrokesView` removes the in-progress stroke (`view.removeFinishedStrokes`)
- The stroke is rebuilt as a persisted `Stroke` and rendered via Compose
- There is a **single frame gap** where neither layer shows the stroke, or the visual style differs between the two rendering systems

Additionally, **motion prediction is disabled** (`ENABLE_PREDICTED_STROKES = false`), which means the in-progress rendering already lags behind the stylus by the full input sampling latency (~16–50ms).

**Remaining Work:**
- Re-enable motion prediction with proper pen-up handoff (cancel predicted segments, snap to real endpoint)
- Align visual style between `InProgressStrokesView` (Ink API brush) and Compose canvas (path + stroke style)
- Consider rendering finished strokes into the same layer as in-progress strokes for seamless transition

**Status:** 🟡 Partially Addressed (smoothing/tapering reduce visual difference between states; prediction still disabled)

---

## 2. Latency & Performance

### 2.1 Input Lag

**Symptom:** Perceptible delay between the user's input motion and the line appearing on screen, most visible during rapid drawing.

**Root Causes:**
1. **Motion prediction disabled:** The `MotionPredictionAdapter` is implemented and ready but gated off (`ENABLE_PREDICTED_STROKES = false`, `ENABLE_MOTION_PREDICTION = false`). This adds 30–60ms of perceived latency.
2. **No front-buffer rendering:** Drawing uses standard double-buffered compositor, which adds ~16ms at 60Hz. `GLFrontBufferedRenderer` (available on Android 10+) would bypass this.
3. **Synchronous stroke processing:** Touch input → coordinate conversion → path building happens on the UI thread. For rapid strokes with many historical events, this can cause jank.

**Recommended Fixes:**
| # | Fix | Impact | Priority |
|---|-----|--------|----------|
| 1 | Re-enable motion prediction with pen-up stabilization | −30–60ms perceived latency | P1 |
| 2 | Implement `GLFrontBufferedRenderer` for active strokes | −16ms per frame | P1 |
| 3 | Request unbuffered dispatch for all stylus events | Already partially implemented | P2 |
| 4 | Process historical touch events in batch | Reduces per-frame work | P2 |

**Status:** ❌ Not Yet Fixed — Requires physical device testing to tune prediction handoff

---

### 2.2 Rendering Stutter (Pan/Zoom Choppiness)

**Symptom:** When panning or zooming, the canvas frame rate appears low, causing "choppy" motion. Notewise maintains high refresh rate (60–120fps).

**Root Causes:**
1. **PDF bitmap re-rendering on zoom:** When zoom level changes, `PdfRenderer.renderPage()` is called synchronously to produce a new full-page bitmap at the new scale. At high zoom, this renders 4K+ bitmaps on the main thread.
2. **`Color.parseColor()` per frame:** Before the fix, every visible stroke's color was parsed from hex string on every draw call, adding O(strokes) string parsing per frame.
3. **Path cache miss on first draw:** When strokes are first rendered after page load, all paths must be built. With many strokes, this can cause a multi-frame hitch.
4. **No frame budget management:** There is no mechanism to limit per-frame work to stay within 8ms (120Hz) or 16ms (60Hz) budget.

**Fixes Applied:**
- ✅ Color caching via `ColorCache` (LRU with synchronized access)
- ✅ Path cache bounding at 500 entries (prevents memory pressure)

**Remaining Work:**
| # | Fix | Impact | Priority |
|---|-----|--------|----------|
| 1 | Async PDF tile rendering (see §4.2) | Eliminates zoom-on-main-thread | P2 |
| 2 | Progressive path building (build N paths per frame) | Smooth first-render | P2 |
| 3 | Frame budget manager | Prevents jank | P3 |

**Status:** 🟡 Partially Fixed (color caching + cache bounds done; PDF tiling + async pending)

---

## 3. Visual Artifacts & Glitches

### 3.1 Stroke Ghosting / Duplication

**Symptom:** Text appears to duplicate or shift, leaving a "ghost" of the previous frame momentarily visible.

**Root Cause:** The dual-layer rendering architecture creates a window where strokes exist in both layers simultaneously:
1. `InProgressStrokesView` renders the in-progress stroke
2. On pen-up, the stroke is immediately added to `pendingCommittedStrokes`
3. The Compose `Canvas` redraws and includes the new stroke from `pendingCommittedStrokes`
4. Meanwhile, `InProgressStrokesView` may not have removed the finished stroke yet

The deduplication logic (`LinkedHashMap` merge of persisted + pending strokes) prevents logical duplication, but the **visual overlap** between the two rendering layers can cause ghosting for 1–2 frames.

**Recommended Fixes:**
- Ensure `view.removeFinishedStrokes()` is called BEFORE `invalidateActiveStrokeRender()` (currently the order is correct but timing may vary)
- Add a one-frame delay before showing the Compose-rendered version
- Consider using `InProgressStrokesView.setOnStrokeFinishedListener()` to synchronize removal

**Status:** ❌ Not Yet Fixed — Requires real-device verification to observe and tune

---

### 3.2 Artifacts on Complex / Overlapping Strokes

**Symptom:** Lines break or flicker (z-fighting) where strokes overlap, especially with highlighter strokes underneath.

**Root Cause:**
1. **Fixed alpha for highlighter:** The highlighter tool uses `alphaMultiplier` for blending but does not use a proper **multiply blend mode**. When multiple semi-transparent strokes overlap, their alpha compounds, creating visual artifacts.
2. **Draw order:** Strokes are drawn in insertion order. If a highlighter stroke is drawn first and a pen stroke is drawn on top, the composite depends on the alpha channel behavior of Compose's `drawPath`.
3. **Mask path EVEN_ODD fill:** The page boundary mask uses `FillType.EVEN_ODD`, which works correctly for the rectangular mask but could interact unexpectedly with stroke paths that extend to the boundary.

**Recommended Fixes:**
| # | Fix | Impact | Priority |
|---|-----|--------|----------|
| 1 | Use `BlendMode.Multiply` for highlighter strokes | Correct translucent overlap | P2 |
| 2 | Sort strokes: highlighters first, then pens | Consistent z-order | P3 |
| 3 | Render highlighter strokes to a separate layer | Eliminates alpha stacking | P3 |

**Status:** ✅ Fixed — `BlendMode.Multiply` + `0.35f` alpha applied for highlighter strokes in `drawStrokesInWorldSpace()`

---

### 3.3 Canvas Flashing

**Symptom:** Subtle white-out or flash when the view is reset or moved aggressively.

**Root Cause:** When the view transform changes rapidly:
1. The Compose `Canvas` redraws with the new transform
2. The PDF bitmap may be stale (cached at a different zoom level) — the cache key uses `renderScaleCacheKey` which quantizes zoom to thousandths. Rapid zoom changes may alternate between cached and uncached states.
3. The `InProgressStrokesView` mask path is recalculated in `update {}` — if the view dimensions are momentarily 0 or the mask is `null`, strokes can extend outside the page boundary for one frame.
4. No "hold previous frame" logic — if rendering takes too long, the canvas shows a blank frame.

**Recommended Fixes:**
- Keep the previous PDF bitmap visible while the new one renders at the target zoom
- Add a dirty flag to avoid unnecessary mask path recalculation
- Implement a "render previous frame" fallback for long-running draws

**Status:** ❌ Not Yet Fixed — Requires async PDF rendering pipeline

---

## 4. Document & Page Handling

### 4.1 Single Page Lock (No Multi-Page Scrolling)

**Symptom:** Onyx is locked to the first page of the PDF. No vertical scroll to subsequent pages. Notewise shows a continuous vertical scroll layout where pages flow into each other with a grey gap.

**Root Cause:** The current architecture models pages as **discrete entities** accessed by `PageEntity.indexInNote`. Navigation is via page-forward/page-back buttons in the toolbar. The `NoteEditorPdfContent` composable renders a single `PdfRenderer.renderPage(pageIndex)` bitmap at a time.

There is no `LazyColumn` or vertical scroll container that stacks multiple page bitmaps vertically. The entire canvas (PDF + ink overlay) is for one page at a time.

**Required Changes for Continuous Scroll:**
| # | Component | Change | Effort |
|---|-----------|--------|--------|
| 1 | `NoteEditorPdfContent` | Wrap in `LazyColumn` with per-page items | Medium |
| 2 | `PdfRenderer` | Pre-render adjacent pages (pageIndex ± 1) | Low |
| 3 | `InkCanvas` | Support multi-page coordinate system | High |
| 4 | `NoteEditorViewModel` | Load strokes for visible pages only | Medium |
| 5 | Page gap rendering | Add grey separator between pages | Low |
| 6 | `ViewTransform` | Extend to track vertical scroll offset across pages | Medium |

**Status:** ❌ Not Yet Implemented — Major architectural change for Milestone Av2 or B

---

### 4.2 Zoom Clarity / PDF Pixelation

**Symptom:** When zooming in, PDF text undergoes momentary blur or pixelation before sharpening. Notewise maintains vector-level sharpness throughout.

**Root Cause:** PDF pages are rendered as **full-page bitmaps** at a specific zoom-quantized scale:
```kotlin
val cacheKey = PdfBitmapCacheKey(pageIndex, renderScaleCacheKey(zoom))
// renderScaleCacheKey rounds to nearest 0.001
```
When the user zooms in:
1. The cached bitmap at the previous zoom level is stretched (bilinear filtering via `FilterQuality.High`)
2. A new bitmap is rendered at the new zoom level
3. The new bitmap replaces the old one in the cache
4. Between step 1 and 3, the user sees a blurry stretched version

**Recommended Fixes:**
| # | Fix | Impact | Priority |
|---|-----|--------|----------|
| 1 | Tile-based rendering (512×512 px chunks) | Only re-render visible region | P2 |
| 2 | Multi-resolution pyramid (full + 50% + 25%) | Show low-res immediately | P2 |
| 3 | Async tile pipeline on `Dispatchers.IO` | Non-blocking zoom | P2 |
| 4 | Keep previous bitmap visible during render | No flash | P1 |

**Status:** ❌ Not Yet Fixed — Requires tile-based PDF rendering pipeline

---

## 5. UI & Overlay Observations

### 5.1 Toolbar Occlusion

**Symptom:** The floating toolbar obscures the top portion of the document. The canvas does not have padding to account for it. Notewise separates UI elements from the canvas workspace.

**Root Cause:** The toolbar is rendered as a `TopAppBar` (or similar) in the `NoteEditorUi` scaffold. The canvas content (`NoteEditorPdfContent` + `InkCanvas`) is placed below the toolbar in the Compose layout hierarchy, but the PDF rendering does not account for the toolbar's height:
- `panY` starts at 0, which aligns the PDF's top edge with the top of the canvas area
- If the canvas extends behind the toolbar, the first ~56dp of the PDF is hidden
- No explicit `contentPadding` or `Modifier.padding()` is applied to offset the content

**Recommended Fixes:**
| # | Fix | Impact | Priority |
|---|-----|--------|----------|
| 1 | Add top padding to canvas equal to toolbar height | Prevents occlusion | P1 |
| 2 | Use `Scaffold`'s `contentPadding` for toolbar-aware layout | System-integrated | P2 |
| 3 | Make toolbar collapsible or auto-hide during drawing | More canvas space | P3 |

**Status:** ❌ Not Yet Fixed — UI layout adjustment needed in `NoteEditorUi.kt`

---

### 5.2 Missing Selection/Edit Visual Cues

**Symptom:** Notewise shows clear visual indicators of the active tool and page boundaries. Onyx lacks visual feedback for page edges or scroll limits.

**Root Cause:**
1. **No page boundary indicators:** The page mask (`buildOutsidePageMaskPath`) clips strokes at the page edge but does not draw a visible border or shadow around the page boundary.
2. **No scroll limit feedback:** When panning reaches the edge of the page, there is no bounce, shadow, or edge glow to indicate the limit.
3. **Active tool indicator:** Tool selection state exists but may not be visually prominent enough.

**Recommended Fixes:**
| # | Fix | Impact | Priority |
|---|-----|--------|----------|
| 1 | Draw page boundary with subtle shadow/border | Clear page extent | P2 |
| 2 | Add edge glow/bounce when panning to limits | Spatial awareness | P3 |
| 3 | Enhance active tool highlight in toolbar | Clearer tool state | P3 |

**Status:** ❌ Not Yet Fixed — UI polish items for P5

---

## 6. Remediation Summary

### Changes Made in This PR

| # | Issue | Fix | File |
|---|-------|-----|------|
| 1 | Polygonal/jagged lines | Catmull-Rom spline interpolation (C¹ continuous) | `InkCanvasDrawing.kt` |
| 2 | No pressure sensitivity | Per-point width with gamma curve (γ=0.6) | `InkCanvasDrawing.kt` |
| 3 | No stroke tapering | Start/end taper over first/last 5 points | `InkCanvasDrawing.kt` |
| 4 | Uniform stroke width rendering | Variable-width filled outline path (replaces stroked center line) | `InkCanvasDrawing.kt` |
| 5 | Color parsing per frame | LRU `ColorCache` (64 entries, thread-safe) | `InkCanvasDrawing.kt` |
| 6 | Unbounded path cache | Capped at 500 entries with iterator-based eviction (no allocation) | `InkCanvasDrawing.kt` |
| 7 | Stroke bounds miss width | `calculateBounds()` pads by max stroke width | `InkCanvasGeometry.kt`, `InkCanvasStroke.kt` |
| 8 | MyScript erase is O(n) | Direct `eraseStrokes()` with fallback to clear+re-feed | `MyScriptPageManager.kt` |
| 9 | MyScript init blocks startup | Async initialization on background thread; `@Volatile engine` field | `OnyxApplication.kt`, `MyScriptEngine.kt` |
| 10 | Tests | 30+ unit tests for smoothing, tapering, outline, pipeline | `CatmullRomSmoothTest.kt`, `StrokeTaperingTest.kt`, `VariableWidthOutlineTest.kt` |
| 11 | Highlighter blending | `BlendMode.Multiply` + alpha for highlighter strokes | `InkCanvasDrawing.kt` |
| 12 | MyScript erase O(n) lookup | Reverse mapping for O(1) stroke ID lookup on erase | `MyScriptPageManager.kt` |
| 13 | Single-point strokes unerasable | `findStrokeToErase` now checks single-point proximity | `InkCanvasGeometry.kt` |
| 14 | Pressure lost on dedup | `addPointDeduped` updates pressure when position unchanged | `InkCanvasTouch.kt` |
| 15 | Outline size mismatch | `buildVariableWidthOutline` validates widths/samples size | `InkCanvasDrawing.kt` |
| 16 | Additional tests | 37+ more tests for bounds, eraser, color, transforms, coordinates | Multiple test files |

### Remaining Work (By Priority)

| Priority | Issue | Required Change |
|----------|-------|-----------------|
| P1 | Input lag / motion prediction | Re-enable `MotionPredictionAdapter` with pen-up fix |
| P1 | Stroke pop-in | Align in-progress vs finished rendering styles |
| P1 | Toolbar occlusion | Add content padding for toolbar height |
| P2 | PDF zoom clarity | Tile-based PDF rendering with async pipeline |
| P2 | Pan/zoom stutter | Async PDF re-render, keep previous bitmap visible |
| P2 | Single page lock | Continuous vertical scroll with `LazyColumn` |
| P2 | Page boundary indicators | Draw visible page border and edge glow |
| P3 | Canvas flashing | Hold previous frame, dirty-flag mask path |
| P3 | Stroke ghosting | Synchronize dual-layer stroke removal |
| P3 | Front-buffer rendering | `GLFrontBufferedRenderer` for active strokes |
| P5 | Scroll limit feedback | Edge glow/bounce animation |
| P5 | Active tool indicator | Enhanced toolbar highlight |

### Cross-References

- Full project analysis: [`docs/architecture/full-project-analysis.md`](full-project-analysis.md) §4, §10
- Performance roadmap: [`docs/architecture/full-project-analysis.md`](full-project-analysis.md) §10
- PR3 rendering fixes: [`docs/architecture/android-remediation-pr3.md`](android-remediation-pr3.md)
- Device testing blockers: [`docs/device-blocker.md`](../device-blocker.md)
