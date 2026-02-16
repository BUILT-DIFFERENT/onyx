# Onyx Android Remediation Design Doc  
Deep technical plan to fix the Onyx issues observed in the videos, grounded in the current `BUILT-DIFFERENT/onyx` codebase and aligned with “production-grade” behavior benchmarks (e.g., Notewise-class zoom/pan and ink feel)

## Scope, success criteria, and constraints

This document focuses on the Android Onyx app in `BUILT-DIFFERENT/onyx` (offline-first, Jetpack Compose UI, PDF-backed pages, ink overlay). The goal is not to “clone” Notewise feature-for-feature immediately, but to close the core experiential gaps visible in the videos: transform smoothness (zoom/pan), ink feel (latency + stroke quality), PDF clarity (no pixelation/black flashes), and UI polish (touch targets, settings clarity, predictable state).

Success criteria are measurable:

On the canvas side, the user should be able to pinch-zoom and pan at 60–120 Hz without discontinuities, drift, or perceptible frame drops during normal documents, and without “jumping” clarity changes. On the ink side, pen-down to pixel latency should feel immediate; stroke finalization should not “pop” or ghost; erasing should be deterministic and artifact-free. On the UI side, tool buttons must meet minimum touch target sizing and spacing guidance (48×48 dp targets), and tool settings must show numeric values and previews to avoid “slider does nothing” confusion. citeturn7search2

Constraints worth calling out:

The codebase already uses a dual-layer ink approach (native/low-latency view for in-progress strokes plus Compose canvas for committed strokes) and a PDF rendering pipeline with caching and tiling. The plan below fixes the remaining gaps by tightening these systems, not by ripping everything out at once. For ultra-low-latency drawing, AndroidX’s low-latency front-buffer rendering is available on Android 10+ (API 29+). citeturn8search0

## Current architecture map relevant to the reported issues

The current implementation is split cleanly into three subsystems that correspond almost one-to-one with the video issues:

The transform system centers on `ViewTransform` (screen↔page coordinate conversion), gesture detection in `InkCanvasTransformTouch.kt`, and transform application/clamping logic in `NoteEditorShared.kt` (e.g., “fit to viewport,” “constrain to viewport,” “apply transform gesture,” zoom limits, render-scale bucketing). This is where zoom drift, discontinuity, lack of rubber-banding, and rotation re-layout instability originate.

The ink pipeline is implemented in the `ink/ui` package. The Compose side constructs paths, applies smoothing and variable-width rendering (`InkCanvasDrawing.kt`), and draws committed strokes. In-progress strokes are rendered via `InProgressStrokesView` (Android View) layered over Compose. This dual-layer design is the root of “stroke pop-in” and “ghost/duplicate” artifacts when the ownership of a stroke transitions from in-progress to committed.

The PDF pipeline lives under `pdf/` and editor composables. Rendering is done via a `PdfDocumentRenderer` implementation (`PdfiumRenderer`) plus a tile renderer and an async tile pipeline. Visual issues like “pixelation when zoomed” and “black flashes between pages” are typically produced when the wrong render scale is chosen (undersampling) or when caches invalidate with no retained fallback frame.

The plan below targets each subsystem in the lowest-risk order: fix transform math + render scale selection first (to remove the most visible jank), then unify ink rendering handoff (to eliminate ghosting and latency perception), then refine PDF fallback behavior and UI polish.

## Transform and navigation remediation  
Fixing zoom discontinuity, drift, pan stutter, edge behavior, and rotation instability

### Root causes to address

The video’s “discrete zoom / discontinuous scaling” and “pixelation while zooming” typically come from using quantized render-scale buckets that lag behind the user’s actual zoom, forcing the app to scale up too-low-resolution content before a higher-resolution render arrives. The key remediation is: never let the render scale fall meaningfully below the active zoom for the regions the user is inspecting.

Drift during pinch-to-zoom is almost always one of three issues: incorrect anchoring (pivot math), clamping or re-centering that fights the user mid-gesture, or mixed coordinate spaces (screen-space vs page-space) applied inconsistently. Rubber-banding absence is not a correctness bug, but the UX baseline in premium note apps includes edge affordances that communicate limits.

Rotation/layout shift is usually caused by “fit to viewport” re-running on size changes and resetting pan/zoom rather than preserving the user’s focus point.

### Design changes

#### Preserve focal point on rotation

Change the viewport-size effect so it does not naively refit the page. Instead, preserve the page-space coordinate under the viewport center (or pinch centroid if mid-gesture), then recompute the new pan after rotation so that coordinate stays stable.

Implementation sketch:

- Before a size change takes effect, compute `centerPage = transform.screenToPage(viewportWidth/2, viewportHeight/2)`.
- After size update, compute `newPan = centerScreen - centerPage*newZoom`, maintaining the same `zoom`.
- Re-run `constrainTransformToViewport` only once after this recomputation, and only if you want hard bounds; with rubber-banding, you’ll animate back.

This directly prevents the “portrait rotation breaks temporarily” behavior reported.

#### Rubber-band edges + inertial pan consistency

Introduce elastic overscroll rather than hard-clamp-only panning. The simplest version:

- Maintain a “soft bounds” transform that can exceed the hard bounds by up to `overscrollPxMax` with non-linear resistance.
- On gesture end (and after fling ends), animate back to the hard constrained transform with a spring.

This requires adding an explicit “transform gesture end” callback emitted by the touch layer whenever the last gesture pointer is lifted or when a transform gesture transitions into a fling. On Compose, drive the snap-back animation via `Animatable<ViewTransform>` (or separate animatables for panX/panY/zoom), but only after the gesture ends to avoid fighting the user.

#### Fix render-scale selection: stop undersampling during zoom

PDF clarity must not degrade at the exact moment the user zooms in to inspect something. The selection policy should be:

- Choose render scale ≥ current zoom * deviceScaleFactorForPDF, at least for the tiles that intersect the viewport.
- Allow downshifts only with hysteresis (to prevent thrash) when zooming out—not when zooming in.

Practically, change the “bucket hysteresis” rule so stepping up happens immediately (or with minimal slack), while stepping down is delayed. This preserves crispness and eliminates the “pixelated until it catches up” perception.

### Implementation notes in the current codebase

- Update the zoom→render-scale mapping logic used by PDF layers (`zoomToRenderScaleBucket` / related helpers) to ensure the active bucket never trails behind zoom when zooming in; apply hysteresis only on step-down.
- Ensure `resolvePdfRenderScale(...)` is used as a safety clamp for extremely large pages, but do not allow that clamp to silently undersample without an explicit UI cap (e.g., “Max zoom for this PDF reached”).
- Add a “fit to screen” action (toolbar button or two-finger double-tap) and make “auto-fit” opt-in, not automatic on any viewport change.

## Ink pipeline remediation  
Fixing perceived latency, segmentation artifacts, pop-in/ghosting, pressure pipeline, and eraser correctness

### Root causes to address

The biggest experiential gap described in the video (“lines chase the cursor,” “segmented/tessellated stroke,” “ghost erasing,” “stroke pop-in”) is classic dual-renderer mismatch:

- The in-progress renderer and committed renderer differ in geometry, smoothing, tapering, and blending, so the stroke visibly changes on pen-up.
- If the in-progress stroke is removed before the committed stroke is visible (or vice versa), you get a one-frame gap or a one-to-two frame overlap, perceived as flicker/ghost.

Separately, the “smoothing slider does nothing” problem is typically because the UI control is wired to a parameter that doesn’t drive the actual smoothing algorithm. Tool settings must be semantically aligned with the real pipeline stages.

### Design changes

#### Make motion prediction real (and correct)

The codebase currently wraps motion prediction via a reflection adapter; the correct AndroidX API contract is:

- Obtain a predictor using `MotionEventPredictor.newInstance(view)`.
- Call `record(event)` for every motion event the view receives.
- Call `predict()` to get a predicted `MotionEvent` for the next frame. citeturn7search0

Remediation: replace the reflection-based constructor path with a direct dependency on `androidx.input:input-motionprediction` and instantiate via `newInstance(view)` in the actual view that receives the events (the in-progress strokes view). citeturn7search0

Pay special attention to pen-up handoff: predicted events must be canceled/ignored once the real stream ends (ACTION_UP/CANCEL) to avoid overshoot.

#### Front-buffer rendering for active strokes on API 29+

To close the latency gap further, integrate `GLFrontBufferedRenderer` behind a feature flag:

- Use a `SurfaceView` as the rendering surface.
- Render active strokes into the front buffer via `renderFrontBufferedLayer`.
- Periodically commit the scene into the multi-buffered layer via `commit()` to avoid long-lived tearing risk. citeturn8search0

This matches the high-end behavior expectation: active ink appears with minimal compositor delay, while the stable scene remains correct.

The API is explicitly designed for a two-layer system: active front-buffered content plus a more traditional multi-buffered layer. citeturn8search0

Implementation strategy in Onyx:

- Keep the existing Compose canvas drawing for committed strokes as the “multi-buffered scene.”
- Use the front buffer only for the currently active stroke(s); on commit (pen-up), the stroke is transferred once into the committed list and the front buffer is cleared.

This eliminates the “stroke pop-in” and “ghost overlap” because you control the exact frame the ownership changes.

#### Unify geometry between in-progress and committed strokes

Even if you don’t adopt front-buffer rendering immediately, you should make both renderers use the same brush model:

- Ensure the in-progress brush config (pressure response, base width, smoothing/stabilization, end taper) mirrors the committed renderer’s configuration.
- If `InProgressStrokesView` can’t match a feature (e.g., variable-width outline), then the committed renderer must degrade to match for that stroke, or you implement custom in-progress rendering.

This reduces visible “snap” on pen-up.

#### Fix the “stabilization/smoothing” settings semantics

In Notewise-style UX, stabilization controls the smoothing filter, and pressure sensitivity controls width response. The Onyx settings panel should reflect this and drive real parameters:

- Add `stabilization` to `Brush` and persist it into `StrokeStyle` at stroke creation time.
- Use stabilization to influence either:
  - a point filter pre-spline (e.g., exponential smoothing / one-euro filter), and/or
  - spline tension/subdivision and simplification tolerance.

This guarantees the slider has visible effect and is testable.

#### Eraser: implement both “stroke erase” and “segment erase” modes

The video notes “no partial erase,” while advanced apps offer both object erase and pixel/segment erase. Your codebase already has the geometric infrastructure (stroke bounds, point-to-segment distance, hit testing) to implement this cleanly:

- Keep **Stroke Eraser** as current behavior (tap/drag removes whole strokes).
- Add **Segment Eraser**:
  - When eraser intersects a stroke polyline, split the stroke into remaining segments.
  - Persist new stroke segments with new IDs; delete the original.
  - Add this as a tool mode toggle in the eraser settings.

This also removes “ghost residue” complaints because the user can refine erasure precisely rather than repeatedly deleting/recreating.

### UI feedback improvements for ink/tools

Tool settings must include:

- Numeric values next to sliders (brush size in pt, stabilization 0–10, opacity 0–100%).
- A live preview strip (draw a sample stroke with the currently selected tool settings).
- Clear messaging that settings affect future strokes, unless you intentionally add “apply to selected strokes” later.

## PDF rendering remediation  
Fixing black flashes, zoom pixelation, and “bitmap-only” feel while keeping performance

### Root causes to address

The reported PDF issues split into two categories:

First is frame continuity and fallback: “black flashes between pages” and “flashing during transitions” happen when the page bitmap/tiles are cleared and the next page has no immediately drawable cached content.

Second is resolution management: “text pixelated when zoomed” happens when the render scale is too low for the current zoom and the system shows an upscaled bitmap before higher-res tiles arrive.

### Design changes

#### Always show something: retain previous frame until replacement is ready

Instead of clearing the page bitmap/tiles immediately on page change, keep rendering the last available content until one of these is ready:

- a low-res new page bitmap, or
- at least one tile of the new page (for the viewport), or
- a placeholder skeleton explicitly designed (not black).

Then crossfade to the new content when ready.

This is a small UX change with outsized perceived quality improvement.

#### Make tile scale follow zoom-in aggressively

As described in the transform section, the tile scale choice must step up promptly as zoom increases. When zoom changes, request visible tiles at the new scale without waiting for large hysteresis thresholds. For performance, allow a coarse tier system (e.g., 1×, 2×, 4×, 8×), but always choose the ceiling tier for the active zoom-in moment.

#### Improve highlighting/transparency and layering

Notewise-class PDF annotation keeps text readable. Your PDF selection highlight should remain translucent and composited correctly, and ink layers should have stable z-order:

- PDF base (bitmap or tiles)
- highlighter strokes (multiply blend)
- pen strokes
- selection outlines / handles (if you add lasso later)

On Android, multiply blending and alpha choices may look different depending on the rendering pipeline, but the goal is simple: highlight never fully obscures underlying glyph edges.

#### Add direct page navigation affordances

The video notes the lack of direct page jump. Add:

- Tap-to-edit page number (“1 / 12”) that opens a numeric entry dialog.
- Optional thumbnail strip (lazy) for PDFs.

This is a UI-only feature, does not require changing the PDF core.

## UI/UX polish remediation  
Touch targets, tool affordances, splash/loading transitions, and settings persistence

### Touch target and spacing compliance

The toolbar in the video is described as cramped with small icons. Ensure all actionable icons satisfy minimum touch target sizing and padding: 48×48 dp targets with appropriate spacing. citeturn7search2

Implementation: wrap each icon in a `Box`/`Surface` with explicit `size(48.dp)` (or larger on tablets), and use internal padding so the icon graphic can remain 20–24 dp but the target meets guidance.

### Add a real splash/loading transition

The video notes no splash or loading transition. On Android, implement SplashScreen via `androidx.core:core-splashscreen`:

- Set a “starting theme” in the manifest.
- Call `installSplashScreen()` before `super.onCreate()` in the launcher activity. citeturn7search5

Use the splash keep-on-screen condition only to cover real initialization (e.g., PDF engine warm-up, MyScript asset availability), not as an artificial delay.

### Tool settings persistence

Users expect the pen/highlighter/eraser configuration to persist per tool. Implement a `ToolPreferencesStore` backed by `DataStore`:

- Store `Brush` parameters separately per tool type.
- Update on change; load on app start; hydrate editor state when opening a note.
- For future “favorites”/recent colors, store a small ring buffer of colors.

### Improve settings clarity: numeric values + preview

The video points out sliders with “no numeric values.” Treat this as a correctness issue, not just polish: numeric labels make settings learnable and debuggable.

Add:

- “Brush size: 2.0 pt”
- “Stabilization: 6 / 10”
- “Opacity: 20%”

And render a small preview line that updates live with the same brush code path used by ink rendering (so it’s guaranteed truthful).

## Verification, instrumentation, and rollout strategy

### Performance and correctness instrumentation

To ensure changes actually resolve the video issues:

- Add a “debug HUD” (developer setting) that shows:
  - current zoom percentage
  - render scale bucket (PDF)
  - tile request count / in-flight renders
  - stylus predicted-event availability
- Add traces around:
  - stroke finalize time
  - tile render time
  - page switch time-to-first-paint

For motion prediction, explicitly log whether prediction is available and whether `predict()` returns non-null events; the AndroidX contract makes it clear `predict()` can return null if not possible. citeturn7search0

### Test plan

Keep tests aligned to failure modes:

- Unit tests for transform anchoring (pinch about centroid should keep that page point stable).
- Unit tests for zoom→render-scale bucket selection (no undersampling when zooming in).
- Compose UI tests ensuring toolbar buttons meet minimum touch target size (snapshot or semantics bounds checks).
- Instrumentation tests for page switching and PDF tile presence (no “empty frame”).

### Rollout sequencing

Roll out in this order to reduce risk and make improvements visible early:

First, fix zoom/render-scale policy and rotation stability, because these changes immediately remove the most obvious “jank” and pixelation.

Second, correct motion prediction by switching to `MotionEventPredictor.newInstance(view)` and wiring record/predict correctly. citeturn7search0

Third, eliminate ink pop-in/ghost by controlling the ownership transition frame; if needed, adopt `GLFrontBufferedRenderer` behind a flag on API 29+ for best latency. citeturn8search0

Fourth, polish: toolbar touch targets, numeric settings UI, splash, and persistence. Touch target guidance is explicit and should be met across the editor UI. citeturn7search2 The SplashScreen implementation steps are standard and should be used for a professional launch experience. citeturn7search5