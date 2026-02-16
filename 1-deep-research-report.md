# Onyx vs Notewise: Technical Remediation Plan for the BUILT-DIFFERENT/onyx Android Codebase

## Executive summary

This document translates the competitive gaps highlighted in the Onyx vs Notewise comparison into an implementation-ready remediation plan for the **BUILT-DIFFERENT/onyx** Android app. The codebase already contains major building blocks for a modern note/PDF editor (Jetpack Compose UI, Jetpack Ink for in‑progress strokes, PDF tiling with async pipeline, and tool configuration UX). The remaining “Notewise‑level” gaps concentrate in three areas: **perceived ink latency**, **stroke handoff artifacts (pop‑in/ghosting) caused by dual rendering**, and **zoom/pan smoothness under load (PDF + ink) due to work scheduling and state churn**. fileciteturn55file3L1-L1 fileciteturn52file0L1-L1 citeturn11search1turn10search0turn9search0

The most important technical pivot is to stop treating “prediction” as a separate, second stroke system, and instead wire prediction into the APIs designed for it: **`androidx.input.motionprediction.MotionEventPredictor`** plus **`InProgressStrokesView.addToStroke(..., prediction)`**. The Jetpack Ink docs explicitly recommend supplying a predicted `MotionEvent` to achieve the best performance. citeturn9search0turn11search1

A second pivot is to treat low-latency ink as an explicit rendering subsystem (front-buffer vs multi-buffer). Android now documents an end-to-end low-latency pattern using **`GLFrontBufferedRenderer`** / **`CanvasFrontBufferedRenderer`**, and ties it directly to stylus event delivery (optionally using `requestUnbufferedDispatch`). citeturn10search0turn10search3turn15search0

## Baseline architecture and gap map

### Ink pipeline as implemented

The current editor uses a **hybrid ink stack**: a Compose `Canvas` for committed strokes plus a Jetpack Ink `InProgressStrokesView` embedded via `AndroidView` for “wet” strokes. fileciteturn55file3L1-L1 citeturn11search1

This architecture is defensible (it’s a common pattern to keep in-progress strokes off Compose for latency), but it creates a known failure mode: **handoff artifacts** at pen-up (pop‑in and/or transient double-draw), because the wet renderer and dry renderer are different surfaces and don’t share a single submission timeline. Jetpack Ink explicitly supports keeping finished strokes rendered *inside* `InProgressStrokesView` until the app calls `removeFinishedStrokes`, which is the hook we’ll use to synchronize handoff. citeturn11search1

### Gesture routing as implemented

The codebase already has a strong foundation for fixing the “pinch draws accidentally” problem: transform gestures are separated from stylus drawing and use explicit routing rules (finger-only detection, two-pointer transform baseline, velocity tracking). fileciteturn51file0L1-L1

That said, Notewise-level polish requires additional behaviors: **focal-point zoom anchoring**, momentum, and stable zoom display. The foundational math exists in shared editor helpers (anchored pan computation / constraints) and can be reused for the gesture callback path (more on that below). fileciteturn52file2L1-L1

### PDF pipeline as implemented

The codebase has already moved beyond “no PDF support.” It includes:
- a **Pdfium-backed renderer** with bitmap caching and tile rendering interfaces fileciteturn54file0L1-L1
- a **tile cache** designed as a memory-bounded LRU store using `Bitmap.allocationByteCount` fileciteturn53file0L1-L1
- an **async pipeline** that coalesces tile requests, limits in-flight renders, and emits tile updates via a `SharedFlow` fileciteturn52file0L1-L1

From a performance standpoint, however, PDF is still the main driver of “zoom/pan jitter” when the view transform updates at high frequency. Two facts matter:
- `LruCache.entryRemoved` and other callbacks are not guaranteed to be synchronized with user code; Android explicitly warns that `entryRemoved` (and `create`) may run while other threads access the cache. This makes race-free bitmap lifecycle management and draw-time safety checks non-optional. citeturn12search0turn12search1
- If tile request computation fires on every micro-movement of a pinch/scroll sequence, the *scheduler* becomes the bottleneck (not Pdfium). The pipeline must be driven at a stable cadence (frame-aligned throttling) rather than “every event.”

## Remediation design for low-latency ink and stroke quality

### Replace “predicted strokes” with first-class MotionEvent prediction wiring

#### Why this change is necessary

The current code contains infrastructure suggestive of prediction support (a prediction adapter, a predicted-stroke alpha, etc.). fileciteturn50file3L1-L1

However, the optimal approach on Android is now well-defined:
- Use **`androidx.input.motionprediction.MotionEventPredictor`** to `record()` real events and `predict()` the event expected by the next rendered frame. citeturn9search0turn9search1
- Pass the predicted event into **`InProgressStrokesView.addToStroke(..., prediction)`**, which Jetpack Ink explicitly “strongly recommends” for best performance (lower perceived latency). citeturn11search1turn9search0

This eliminates an entire class of bugs (ghosting, snapback, double-smoothing) caused by representing predictions as a *different* stroke rather than “extra samples in the same in-progress stroke.”

#### Concrete code changes

**Step A: add/standardize predictor creation**

Replace/refactor `MotionPredictionAdapter` into a direct wrapper around AndroidX:

```kotlin
// New file: com/onyx/android/ink/ui/MotionEventPrediction.kt
class MotionEventPrediction(private val view: View) {
  private val predictor = MotionEventPredictor.newInstance(view)

  fun record(event: MotionEvent) = predictor.record(event)
  fun predict(): MotionEvent? = predictor.predict()
}
```

This uses the public API: `newInstance(view)`, `record(event)`, `predict()`. citeturn9search0turn9search1

**Step B: wire prediction into stroke updates**

In the touch handler, for an active stroke, call:

```kotlin
val prediction = runtime.motionPredictor?.predict()
view.addToStroke(event, pointerId, strokeId, prediction)
// after use:
prediction?.recycle()
```

The `MotionEvent.recycle()` guidance is part of the core `MotionEvent` contract—objects are pooled and should be recycled when you obtain new instances (predictions are typically newly allocated). citeturn13search2turn9search0

**Step C: stop generating separate predicted strokes**

Delete or feature-flag off any “start a second, translucent predicted stroke” approach. With Jetpack Ink’s prediction parameter, that work becomes redundant and actively harmful (it increases draw calls and creates synchronization complexity). citeturn11search1turn9search0

#### Pen-up stabilization and “predicted point leakage” rule

When using prediction, **never persist predicted points** in the durable `Stroke.points`. Prediction is only a rendering assist; the durable model should only store real pointer samples plus history points. This aligns with the purpose of the predictor: bridging device-to-display latency, not changing the underlying data. citeturn9search0turn11search1

### Use unbuffered dispatch selectively and correctly

Android’s current stylus guidance connects low latency systems (front-buffer rendering) with `requestUnbufferedDispatch()` to receive input as soon as it arrives. citeturn15search0turn10search0

But there’s a critical tradeoff: Android documents that **resampled coordinates are always added to batches**, and resampling does **not** occur if unbuffered dispatch is requested. That can increase jitter for some interactions if applied too broadly. citeturn15search2

Implementation rule:
- Call `requestUnbufferedDispatch(event)` only for **stylus strokes during inking**, not for finger panning/scrolling.
- Gate it behind a debug toggle to compare latency vs jitter on target hardware, because behavior depends on OEM pipelines.

### Fix pen-up pop-in and single-frame ghosting via explicit handoff synchronization

Jetpack Ink’s `InProgressStrokesView` will keep rendering finished strokes until `removeFinishedStrokes` is called. citeturn11search1  
Today, the code removes finished strokes immediately, which can cause a visible gap if Compose doesn’t draw the committed stroke until the next frame. fileciteturn55file3L1-L1

Implement a **two-phase handoff**:
- On `finishStroke`: keep the finished stroke in `InProgressStrokesView` (do not immediately remove).
- After Compose has had a chance to draw the same stroke from the committed model, remove the finished stroke IDs from the view.

A practical implementation in Compose:
1. Maintain `runtime.pendingRemovalStrokeIds: MutableSet<InProgressStrokeId>`.
2. `finishStroke` adds to this set.
3. A `LaunchedEffect(pendingRemovalCount)` calls `withFrameNanos {}` twice (or once depending on testing), then calls `removeFinishedStrokes()` for those IDs.

This approach uses the API exactly as intended: the wet renderer persists a little longer to cover the gap, then yields. citeturn11search1

### Optional: front-buffer rendering spike (only if measurement proves meaningful)

Android now ships explicit building blocks for front-buffered rendering:
- `GLFrontBufferedRenderer` and `CanvasFrontBufferedRenderer` in `androidx.graphics:graphics-core` provide a two-layer rendering model: front buffer for active content and multi-buffer for committed content. citeturn10search0turn10search3
- The official “Advanced stylus features” guidance shows how to render to the front buffer for ACTION_DOWN/MOVE, and commit to the multi buffer on ACTION_UP, optionally paired with `requestUnbufferedDispatch`. citeturn15search0turn10search0

Because this is a significant architectural shift (SurfaceView/GL thread, Compose interop), treat it as a spike with a go/no-go gate:
- If prediction+handoff reduces perceived latency enough (<20ms P95 in slow-motion measurement), skip front-buffer.
- If not, implement a `SurfaceView` overlay dedicated to ink only, leaving PDF + UI in Compose.

## Remediation design for zoom/pan smoothness and PDF performance

### Make transform updates frame-aligned, not event-aligned

The heaviest performance trap in zoom/pan systems is driving expensive “what tiles do I need?” logic on every pointer delta. The solution is to:
- store high-frequency deltas (zoom/pan) as “latest intent”
- apply them at a stable cadence (per frame), canceling/overwriting intermediate requests

Implementation outline:
1. The touch layer emits transform intents (zoomChange, panDelta, centroid).
2. The ViewModel (or a dedicated controller) accumulates them in a `MutableStateFlow<TransformGesture>`.
3. A job collects that flow with `sample(16ms)` (or `conflate()` + `withFrameNanos`) and applies `applyTransformGesture(...)` once per frame.

This leverages the existing anchored-zoom math (centroid anchoring) and makes the app behave like Notewise under aggressive pinch-zoom. fileciteturn52file2L1-L1 fileciteturn51file0L1-L1

### Drive PDF tile requests from visible rect changes, but with debouncing

The existing async pipeline is structurally correct: it deduplicates in-flight tiles, cancels stale jobs, and uses a bounded concurrency semaphore. fileciteturn52file0L1-L1

Two improvements bring it closer to Notewise:
1. **Tile-request debouncing**: only call `requestTiles()` after transform has stabilized for a frame boundary (see above). This avoids request storms.
2. **Progressive refinement policy**: keep last-scale tiles visible while new-bucket tiles load, and replace tiles individually (optionally with a lightweight per-tile crossfade). This prevents “blank flashes” during zoom.

### Enforce bitmap lifecycle safety under LRU eviction

Because Android warns that `LruCache.entryRemoved` can occur without synchronization against other cache users, and because Bitmaps are native-memory objects, your PDF and stroke tile caches must obey two invariants:
- never draw a bitmap that might be recycled
- never recycle a bitmap that might still be in active use

At minimum:
- guard every draw with `if (!bitmap.isRecycled) draw…`
- ensure cache access patterns that combine “check then put” are serialized (Mutex) to avoid double-render/double-recycle races

These requirements follow directly from the Android LruCache contract. citeturn12search0turn12search1

### Upgrade zoom range and bucket strategy deliberately

Notewise’s perceived quality at extreme zoom comes from two things: (a) allowing large zoom, and (b) re-rendering at an appropriate resolution. If Onyx increases zoom max without adding higher-resolution buckets, text will simply scale up and blur.

Recommended policy:
- Increase UI zoom limit (e.g., up to 8× or 10×) for parity.
- Add a high bucket (e.g., 8×) **only if** memory budgets allow; otherwise accept blur and show a “rendering…” indicator for crisp tiles.
- Keep buckets stable with hysteresis to avoid bucket flipping near thresholds.

This is primarily product tuning; the technical block is memory. For front-buffer and tiles, Android guarantees RGBA_8888 style formats in the low-latency rendering stack; use that as the default for fidelity. citeturn10search0turn10search3

## UI/tooling parity upgrades beyond the current baseline

### Tool configuration: align UI state to rendering state

The current UI already supports per-tool panels (pen, highlighter, eraser) and palette handling, which is a large step toward Notewise-level polish. fileciteturn56file3L1-L1

Two improvements are still needed for “pro” feel:
- **Persist tool presets** (e.g., 3 pen presets, 2 highlighter presets) as first-class objects rather than “current brush only.” This matches how users work in Notewise (rapidly switching between tuned pens).
- **Add eraser modes**: today the eraser is effectively “stroke erase.” Add:
  - stroke eraser (current)
  - pixel/segment eraser (requires geometric splitting / raster mask)
  - object eraser (future: selection model)

### Selection performance: introduce spatial indexing for hit testing

Notewise’s lasso is fast because it doesn’t linearly scan every stroke each time once documents get large. Onyx already stores bounds on strokes, which enables a spatial index.

Implement:
- a quadtree or grid index keyed by stroke bounds for hit testing
- rebuild incrementally on stroke add/remove
- use it for eraser hit tests and future lasso selection

This directly targets “selection lag on large stroke groups,” one of the competitor pain points, and also improves eraser responsiveness.

### Undo/redo correctness and performance

Undo/redo exists and is wired through an action stack. fileciteturn46file4L1-L1 fileciteturn46file0L1-L1

To keep undo instant under heavy load:
- Add “lazy persistence” for undo batches (coalesce multiple undo operations into a single DB transaction).
- Ensure rendering invalidation is dirty-region based (tile invalidation once tile caching is introduced for ink).

## Verification strategy, performance targets, and rollout switches

### Measurement standards for ink latency

“Feels fast” and “is fast” must converge. Android’s official stylus guidance frames prediction and low-latency rendering as solutions to the input-to-display gap. citeturn9search0turn15search0

Adopt two metrics:
- **Perceived latency P95 < 20ms** measured by 240fps video (stylus tip vs first pixel change) across at least 50 strokes.
- **No handoff artifacts**: 0 visible 1-frame gaps or duplicates across the same sample.

### Regression test anchors already in the repo

The codebase already includes meaningful test hooks we should expand:
- ink API compatibility / touch routing tests fileciteturn55file4L1-L1 fileciteturn55file0L1-L1
- Pdfium integration smoke test fileciteturn54file2L1-L1
- PDF cache/pipeline unit tests (AsyncPdfPipeline / PdfTileCache) fileciteturn52file3L1-L1 fileciteturn53file3L1-L1

Add new tests specifically for:
- prediction does not leak into committed stroke points
- handoff sequencing doesn’t double-draw
- tile-request throttling doesn’t starve rendering under fast zoom

### Feature flags for safe rollout

Gate each high-risk step behind flags so you can ship improvements incrementally:
- `ink.prediction.enabled`
- `ink.handoff.sync.enabled`
- `pdf.tile.request.throttle.enabled`
- `ink.frontbuffer.enabled` (spike-only until proven)

This matches Android’s reality: device/OEM variation is large, and even correct implementations can behave differently under different input stacks.

### Implementation sequencing

A practical sequence that fixes the “big three” issues first:
1. **Prediction rewrite**: MotionEventPredictor + predicted parameter to `addToStroke` (remove predicted-stroke overlay). citeturn9search0turn11search1  
2. **Handoff sync**: delay `removeFinishedStrokes` until Compose has drawn. citeturn11search1  
3. **Frame-aligned transform + tile requests**: sample transforms per frame; request tiles at that cadence. fileciteturn52file0L1-L1  
4. Only then consider **front-buffer rendering** if targets still aren’t met. citeturn10search0turn15search0