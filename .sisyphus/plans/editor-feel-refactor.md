# Context

The current Onyx Android app already has the difficult foundation pieces in place: Vulkan inking, PDF support, MyScript integration scaffolding, and broad test coverage. The problem is not missing technology; it is that the editor no longer has a single coherent interaction model, and the handwriting experience does not yet feel premium.

The key product issue is that live ink, predicted ink, and committed ink are currently treated as different visual systems. This causes tip lag, ghost prediction artifacts, visible handoff differences on pen-up, and excessive smoothing/identity drift. For STEM/engineering students, preserving exact handwritten intent matters more than aggressive beautification.

The intended outcome of this change is to make raw stylus input authoritative, keep the pen tip visually attached to the stylus, make prediction invisible, separate live-preview rendering from committed rendering, and restructure editor state ownership so future work (semantic recognition, sync, collaboration) can build on a simpler, more reliable editor core.

# Recommended Approach

## 1. Reframe the editor around three lanes

Implement the editor-feel milestone around three explicit lanes:

1. **Hot path lane** — stylus capture, light preview smoothing, invisible prediction suffix, live render submit, final stroke commit.
2. **Semantic async lane** — MyScript recognition, search indexing, future shape/text/math conversions after commit only.
3. **UI/chrome lane** — toolbar, panels, note settings, page management, PDF controls.

Do not allow semantic or UI concerns to sit inside the hot path.

### Critical files to reshape
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTransformTouch.kt`

### New boundaries to introduce
- **EditorInteractionSession**: owns transient editing state only (active tool, hover, transform, lasso, gesture state, active pointer/stroke state, stylus-button state, temporary preview state).
- **PageEditorController**: page-scoped orchestration for a single page's stroke/object/selection/viewport lifecycle.
- **MultiPageViewportController**: virtualization, page loading/unloading, stacked-page orchestration, but not low-level editing state.

Single-page and stacked-page editor flows should share one page interaction model. Multi-page mode should be composition/virtualization over page controllers, not a second editor product.

## 2. Make raw stroke input authoritative

Persist and operate on raw stylus samples as canonical note data. Derived display geometry should be rebuilt from raw input.

### Data model direction
Treat strokes as having three representations, even if implemented incrementally:
- **RawStroke** — authoritative captured points, pressure, tilt, timestamps.
- **DisplayStroke** — preview/committed geometry used for rendering.
- **RecognitionStroke** — raw-derived stroke data sent to MyScript.

Do not let smoothed/beautified display geometry become the canonical stroke representation.

### Critical files to modify
- `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt`
- `apps/android/app/src/main/java/com/onyx/android/data/serialization/StrokeSerializer.kt`
- any persistence/entity mapping for strokes in the repository/data layer

### Existing code/patterns to reuse
- existing stroke model and serialization layer in `Stroke.kt` and `StrokeSerializer.kt`
- current pending handoff seam in `InkCanvas.kt` (`pendingCommittedStrokes`) as the temporary transition point between live and committed representations

## 3. Replace the current smoothing/prediction model with a tip-preserving pipeline

### Current problem
The current system smooths too early and in the wrong place:
- `CausalStrokeSmoothing.kt` buffers points before emission, which can cause tip lag.
- live preview, prediction, and committed stroke appearance are not generated from one coherent stroke model.
- `VkNativeBridge.kt` currently hardcodes native smoothing to `0f`, so renderer behavior and brush settings are out of sync.

### New pipeline
1. Raw sample intake.
2. **Preview smoothing only**: light causal filtering, with newest 1–2 points staying close to raw.
3. **Prediction suffix only**: invisible ephemeral extension of the same active stroke identity.
4. **Committed stroke rebuild**: optional higher-quality smoothing/fitting from raw data after enough samples exist or on pen-up.

### Concrete implementation priorities
#### Quick wins
1. Remove visible ghost prediction and make prediction an ephemeral suffix on the active stroke.
2. Stop delaying visible tip output behind a 4-point holdback model.
3. Pass real smoothing/taper/brush parameters through `VkNativeBridge.startStroke()` instead of hardcoding smoothing to `0f`.
4. Eliminate the duplicate visibility window during live→committed handoff where possible.

#### Deeper renderer work
5. Unify preview and committed stroke geometry generation so handoff does not visibly pop.
6. Push more stroke shaping responsibility into the native renderer or, alternatively, centralize all geometry generation before submission so preview and commit use the same tessellation policy.

### Critical files to modify
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/CausalStrokeSmoothing.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasDrawing.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/vk/VkNativeBridge.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/vk/VkInkSurfaceView.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`

### Existing code/patterns to reuse
- `pendingCommittedStrokes` and current finish callbacks in `InkCanvas.kt`
- existing `MotionPredictionAdapter` integration, but change its visual application
- existing `InkCanvasTouchRoutingTest.kt`, geometry tests, and stroke math tests as regression protection

## 4. Redesign brush/tool feel around instrument families, not scalar presets

The app already has enough brush controls; the problem is perceptual tuning and inconsistent renderer usage.

### Direction
Keep a small set of named tools with distinct behavior:
- **Ballpoint**: low latency, mild pressure width response, strong wobble suppression.
- **Fountain**: stronger width/orientation dynamics.
- **Pencil**: less smoothing, more raw feel.
- **Highlighter**: near-constant width, no pen-like taper, dedicated compositing behavior.

Do not expose every low-level parameter prominently in the primary flow.

### Critical files to modify
- `apps/android/app/src/main/java/com/onyx/android/ink/model/Brush.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/model/BrushPreset.kt`
- `apps/android/app/src/main/java/com/onyx/android/config/BrushPresetStore.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/editor/components/PaletteRow.kt`
- toolbar/tool settings components under `apps/android/app/src/main/java/com/onyx/android/ui/editor/`

### Existing code/patterns to reuse
- existing preset storage and tool settings panel structure
- current stylus button eraser path and shortcut callbacks in `InkCanvasCallbacks`

## 5. Keep MyScript fully off the hot path and phase semantic features lightly first

### Principle
MyScript should never participate in immediate draw/render latency.

### Near-term approach
For the editor-feel milestone, only support lightweight semantic experiments later in the phase:
- selected handwriting → text via Text Recognizer
- selected equation → LaTeX via Math Recognizer
- isolated shape recognition via Shape Recognizer

Do not deeply expand OffscreenEditor-driven semantic editing until the core handwriting experience is strong.

### Long-term direction
Prototype `OffscreenEditor` Raw Content later for persistent incremental recognition, richer gesture callbacks, and search/indexing with ranges, but keep it behind the semantic async lane.

### Critical files to modify later in/after the milestone
- `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptEngine.kt`
- `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt`
- recognition/indexing repository/DAO files

### Existing code/patterns to reuse
- current engine singleton + page manager split
- current recognition Room entities/DAO for later indexing work

## 6. Simplify default editor interaction behavior

Bias toward an opinionated, low-friction tablet workflow:
- stylus draws
- stylus button holds eraser
- finger pans/zooms
- two-finger tap undo
- three-finger tap redo
- advanced toggles/settings remain available but not central to the first-run/default editing flow

### Critical files to modify
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTransformTouch.kt`
- editor toolbar/tool settings files

## 7. Split the implementation into clear milestones

### Milestone 1 — Premium pen feel core
Goal: eliminate the most obvious feel problems without deep feature expansion.

Tasks:
1. **Raw stroke canonical**: `Stroke.kt` stores raw `Point[]` (x, y, pressure, tilt, timestamp) as the persisted representation. `StrokeSerializer` round-trips raw points losslessly. `StrokeEntity` schema includes raw point columns. Verification: `StrokeSerializerTest` round-trip test passes for raw points with pressure/tilt; committed stroke retrieved from Room matches original raw input within float tolerance.
2. **Invisible prediction**: `InkCanvasTouch.kt` appends predicted points as an ephemeral suffix on the active stroke path only (not persisted, not committed). On pen-up, predicted points are discarded before commit. Verification: add a unit test asserting `pendingCommittedStrokes` never contains points flagged as predicted; `InkCanvasStrokeTest` confirms committed stroke point count equals raw input point count.
3. **Tip-preserving preview smoothing**: `CausalStrokeSmoothing.kt` modified so the newest 1-2 points stay within 1px of raw input position. Older tail points are smoothed. The current 4-point holdback is removed. Verification: add a geometry test asserting the last 2 emitted points are within 1px Euclidean distance of the corresponding raw input points.
4. **Real brush params to renderer**: `VkNativeBridge.startStroke()` receives actual `Brush.smoothingFactor`, `Brush.taperStart`, `Brush.taperEnd`, `Brush.pressureResponse` from the active brush instead of hardcoded `0f`. Verification: add a test that calls `startStroke()` with a non-default brush and asserts the native call receives those values (mock-verify the JNI call arguments).
5. **Reduced handoff pop**: The live preview stroke and committed stroke use the same geometry generation path. `InkCanvasDrawing.kt` generates display points once; the committed texture atlas receives the same tessellation. Verification: add a test comparing the display-point arrays from preview and commit for an identical stroke — arrays must match within float tolerance.
6. **Default interaction mode**: `NoteEditorState.kt` initializes with: stylus draws, finger pans/zooms, stylus button = eraser hold, two-finger tap = undo, three-finger tap = redo. No modal toggle required for first-run editing. Verification: `InkCanvasTouchRoutingTest` confirms stylus TYPE_STYLUS routes to draw, finger TYPE_TOUCH routes to pan, and two-finger tap fires undo callback.

**Milestone 1 gate**: `bun run android:lint` passes. All 6 verification tests listed above pass. Manual check on Samsung tablet: writing "∫f(x)dx" at small size shows no ghost prediction, no visible pop on pen-up, and tip stays visually attached during slow strokes.

### Milestone 2 — Editor architecture cleanup
Goal: reduce centralization and unify single-page/multi-page editor behavior.

Tasks:
1. **EditorInteractionSession**: New class `ui/editor/EditorInteractionSession.kt` owns all transient editing state: active tool, hover state, transform state, lasso state, gesture state, active pointer/stroke, stylus-button state, temporary preview state. `NoteEditorViewModel` delegates to this session for anything not persisted. Verification: `NoteEditorViewModel` no longer directly holds `activeTool`, `hoverPosition`, `transformState`, or `lassoState` — these are accessed via `interactionSession`. Compile passes.
2. **PageEditorController**: New class `ui/editor/PageEditorController.kt` owns page-scoped lifecycle: stroke list, object list, selection state, viewport (for single page), undo/redo stack for that page. Verification: a new `PageEditorControllerTest` exercises add-stroke → undo → redo → verify stroke list state.
3. **MultiPageViewportController**: New class `ui/editor/MultiPageViewportController.kt` handles page virtualization, loading/unloading, stacked-page scroll position, page-to-screen coordinate mapping. It delegates per-page editing to `PageEditorController` instances. Verification: `NoteEditorScreen` stacked-page mode uses `MultiPageViewportController` and single-page mode uses a direct `PageEditorController`. Both compile and `android:lint` passes.
4. **Split ViewModel**: `NoteEditorViewModel` reduced to: note-level state (note metadata, page list, current page index), persistence coordination, and navigation. Page-level editing state moves to `PageEditorController`. Verification: `NoteEditorViewModel` line count reduced by >30% from current. `NoteEditorViewModelTest` passes.
5. **Unified page interaction**: Single-page and stacked-page modes share the same `PageEditorController` → `EditorInteractionSession` path. No separate touch routing or tool dispatch for the two modes. Verification: `InkCanvasTouchRoutingTest` runs identically for both single and stacked configurations (parameterized test).

**Milestone 2 gate**: `bun run android:lint` passes. All 5 new/updated tests pass. `NoteEditorScreen` stacked-page and single-page modes both function correctly (create note → draw stroke → undo → switch page).

### Milestone 3 — Lightweight semantic enhancements
Goal: add useful recognition without compromising pen feel.

Tasks:
1. **Selection → text conversion**: Lasso-selected strokes are sent to `MyScriptEngine` for text recognition. Recognized text creates a `TextBlock` page object replacing the selected strokes. Verification: instrumented test sends known stroke data (fixture) to the recognition pipeline and asserts a `TextBlock` is created with non-empty text content. Recognition runs on a background coroutine (not main/UI thread — verified via `Dispatchers` assertion in test).
2. **Selection → LaTeX conversion**: Lasso-selected strokes sent to `MyScriptEngine` math recognizer. Result creates a `TextBlock` with `isLatex=true` and `latexSource` populated. Verification: instrumented test sends known equation stroke data and asserts `latexSource` is non-null and contains LaTeX-like content (e.g., contains `\frac` or `\int` for the fixture).
3. **Shape recognition**: When shape recognition is enabled in pen settings, pen-up triggers `MyScriptEngine` shape recognizer on the last stroke. If a shape is recognized with high confidence (>0.8), the raw stroke is replaced with the recognized geometric primitive. Verification: unit test sends a near-perfect rectangle stroke fixture and asserts the output is a shape object with `kind=rectangle`. Low-confidence inputs (<0.8) are not replaced — raw stroke is preserved.
4. **Background indexing hooks**: After each stroke commit, a debounced coroutine (500ms after last commit) sends uncommitted strokes to `MyScriptEngine` for HWR. Recognized text tokens are written to the Room FTS table. Verification: test verifies that after stroke commit, FTS table contains recognized tokens within 2s (integration test with fake engine). Hot path `handleTouchEvent` timing is unaffected — measured via trace section assertion (median <4ms).

**Milestone 3 gate**: `bun run android:lint` passes. All 4 verification tests pass. Recognition pipeline never blocks the hot path (verified by instrumented timing test).

# Verification

## Code-level verification
Reuse and extend existing tests before introducing new feature breadth:

### Existing tests to reuse/expand
- `apps/android/app/src/test/java/com/onyx/android/ink/ui/InkCanvasGeometryTest.kt`
- `apps/android/app/src/test/java/com/onyx/android/ink/ui/InkCanvasStrokeTest.kt`
- related stroke/render math tests under `apps/android/app/src/test/java/com/onyx/android/ink/ui/`
- `apps/android/app/src/androidTest/java/com/onyx/android/ink/ui/InkCanvasTouchRoutingTest.kt`
- `apps/android/app/src/test/java/com/onyx/android/recognition/MyScriptCoordinateTest.kt`
- `apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorViewModelTest.kt`
- `apps/android/app/src/androidTest/java/com/onyx/android/ui/NoteEditorToolbarTest.kt`
- `apps/android/app/src/androidTest/java/com/onyx/android/ui/NoteEditorTopBarTest.kt`

### New tests to add
1. Raw stroke persistence excludes prediction-only points.
2. Preview smoothing keeps newest visible tip points near raw input.
3. Live→committed stroke handoff does not duplicate or visibly diverge in geometry identity.
4. Stylus-active interaction blocks accidental finger gesture cancellation appropriately.
5. Recognition/indexing scheduling never blocks the hot path and degrades gracefully on failure.

## Manual verification on device
Use a real Samsung tablet with S Pen and verify:
1. Slow handwriting: tip stays visually attached, especially on tiny annotations and engineering notation.
2. Fast strokes: no visible ghost leader or translucent prediction artifact.
3. Corners and symbols: committed stroke preserves user intent and does not over-round.
4. Pen-up: no visible pop, width shift, or double-draw handoff.
5. Highlighter feels distinct from pen and maintains stable width.
6. Lasso/selection uses canonical stroke geometry and behaves reliably at high zoom.
7. Finger pan/zoom + stylus draw does not accidentally cancel active writing.
8. Note remains usable if recognition is delayed or unavailable.

## Optional benchmark/follow-up validation
- measure frame pacing and latency variance during sustained writing sessions
- compare side-by-side captures for current vs refactored ink path on tiny writing, math symbols, corners, and long fast strokes
