# Android App — Expert Architecture Review

**Date**: 2026-02-12  
**Scope**: Full review of `apps/android/` code vs `.sisyphus/plans/`  
**Status**: Code read-only — this document lists recommended changes only.

---

## Executive Summary

The Android app is a solid MVP scaffold with ~38 Kotlin files implementing offline-first note-taking with ink, PDF, and MyScript recognition. The code is well-organized and broadly aligns with the Plan A specification. However, there are significant architectural, performance, and design gaps that should be addressed before advancing to Plan Av2 or the UI Overhaul milestone. This review covers 7 areas with 42 actionable recommendations.

---

## 1. Architecture & Code Structure

### 1.1 — ViewModel is private to the UI file (HIGH)
**Current**: `NoteEditorViewModel` is declared `private class` inside `NoteEditorScreen.kt` (line 55). This violates separation of concerns — the ViewModel, UndoController, and data classes (NoteEditorTopBarState, NoteEditorToolbarState, etc.) are all crammed into a single 775-line file that mixes business logic with Compose wiring.

**Recommendation**: Extract `NoteEditorViewModel` into its own file at `viewmodel/NoteEditorViewModel.kt`. Move `UndoController` to `viewmodel/UndoController.kt`. Move state data classes (`NoteEditorTopBarState`, `NoteEditorToolbarState`, `NoteEditorContentState`, `TextSelection`) to a `ui/state/` package.

**Rationale**: Plan A (line 162–263) specifies a standalone ViewModel class. The current approach makes unit testing impossible (private class) and increases merge conflicts when multiple features touch the editor.

### 1.2 — No Dependency Injection framework (MEDIUM)
**Current**: Manual DI via `OnyxApplication` singleton with `lateinit var` properties. ViewModels get dependencies via `(LocalContext.current.applicationContext as OnyxApplication)`.

**Recommendation**: Stay with manual DI for Plan A (as the plan specifies), but refactor `OnyxApplication` to expose dependencies via an interface or a simple `AppContainer` object rather than casting `applicationContext`. This makes testing easier and prepares for Hilt migration noted in the plan.

### 1.3 — NoteEditorUi.kt is a 1,147-line monolith (HIGH)
**Current**: `NoteEditorUi.kt` contains the toolbar, color picker, brush size control, tool settings dialog, palette row, fixed page background, read-only blocker, and all helper functions in one file.

**Recommendation**: Split into:
- `ui/editor/EditorScaffold.kt` — Scaffold + content routing
- `ui/editor/toolbar/EditorToolbar.kt` — Top bar with pill groups
- `ui/editor/toolbar/ToolSettingsPanel.kt` — Floating settings dialogs
- `ui/editor/toolbar/ColorPicker.kt` — Color picker dialog
- `ui/editor/toolbar/PaletteRow.kt` — Color swatch row
- `ui/editor/canvas/FixedPageBackground.kt` — Paper background
- `ui/editor/ReadOnlyBlocker.kt` — Input blocker

### 1.4 — Missing error handling in ViewModel operations (MEDIUM)
**Current**: `loadNote()`, `createNewPage()`, `addStroke()` lack try-catch. If `noteDao.getById(noteId)` returns null, `_noteTitle.value` silently becomes empty.

**Recommendation**: Add error states to the ViewModel. Propagate failures to the UI via a `_error` StateFlow. At minimum, log unexpected nulls.

### 1.5 — No Hilt/Dagger preparation (LOW)
**Current**: ViewModelFactory pattern is manual. Each ViewModel has its own Factory class.

**Recommendation**: For now, keep manual DI but add `@Inject` annotations as comments/TODOs on constructor parameters to make Hilt migration straightforward.

---

## 2. User Notes — Data Persistence & Saving

### 2.1 — Stroke serialization uses JSON in ByteArray (HIGH — Performance)
**Current**: `StrokeSerializer.serializePoints()` converts `List<StrokePoint>` → JSON string → `ByteArray`. For a stroke with 500 points × 7 fields, this generates ~15KB of JSON per stroke. Room stores this in a Base64-encoded TEXT column via `Converters`.

**Recommendation**: Switch to Protocol Buffers or a compact binary format. The plan (line 44) notes "JSON format for v0 (Protocol Buffers in future)" — but even for v0, the current double-encoding (JSON → UTF-8 bytes → Base64 text) is wasteful. Consider:
1. Store JSON as TEXT directly (skip ByteArray + Base64)
2. Or use a compact binary format now (MessagePack, FlatBuffers)

### 2.2 — No auto-save / title editing (MEDIUM)
**Current**: Notes are created with `title = ""` and there's no UI to edit the title. The plan (line 72) says "User-editable; auto-set from first line of recognition if empty (future)".

**Recommendation**: Add a title editing field in the top bar (as Samsung Notes does — tap title to edit). Wire recognition results to auto-populate empty titles. This is a core UX feature that should not be deferred.

### 2.3 — No note deletion from Home Screen (LOW)
**Current**: `HomeScreen` shows notes in a list but provides no way to delete, rename, or organize them. `NoteRepository.deleteNote()` exists (soft delete) but is never called from UI.

**Recommendation**: Add long-press context menu on note rows with: Rename, Delete, Move to Folder (when folders land).

### 2.4 — Stroke write queue lacks error handling (MEDIUM)
**Current**: `strokeWriteQueue` in `NoteEditorViewModel` silently drops failures. If a DB write fails, the stroke is lost from persistence but remains in the UI state.

**Recommendation**: Add retry logic (at least 1 retry) and error logging. Consider showing a "save failed" indicator.

### 2.5 — No offline sync preparation in stroke storage (MEDIUM)
**Current**: `createdLamport` is hardcoded to `0L` everywhere. The plan specifies Lamport clock increment per-device in Plan C.

**Recommendation**: While full Lamport clock is Plan C, at minimum wire `DeviceIdentity.getDeviceId()` into stroke metadata and auto-increment a local counter. This prevents data loss when sync is added — retroactively assigning Lamport timestamps to existing strokes is error-prone.

### 2.6 — Database migration strategy is destructive (HIGH)
**Current**: `OnyxDatabase.build()` uses `.fallbackToDestructiveMigration()`. Any schema change wipes all user data.

**Recommendation**: Replace with proper `Migration` objects before shipping to users. At minimum, add `Migration(1, 2)` placeholder. Plan Av2 explicitly requires `MIGRATION_1_2` for the `wordPositionsJson` field addition.

---

## 3. PDF Handling

### 3.1 — PdfRenderer leaks native resources (HIGH)
**Current**: `PdfRenderer` opens a MuPDF `Document` in its constructor and relies on `close()` being called. But in `HomeScreenViewModel.importPdfInternal()`, a separate `Document.openDocument()` is used without going through `PdfRenderer`, and pages are created/destroyed in a loop. If an exception occurs mid-loop, some pages may leak.

**Recommendation**: Wrap all MuPDF `Document`, `Page`, `Device` operations in `use {}` / try-finally blocks consistently. Consider making `PdfRenderer` implement `Closeable` and using it with `use {}`.

### 3.2 — PDF rendering on every zoom change (MEDIUM — Performance)
**Current**: `rememberPdfBitmap()` re-renders the PDF bitmap whenever `renderScale` changes, which is bucketed but still triggers on zoom gestures. MuPDF `renderPage()` creates a new `Bitmap.createBitmap()` every time, allocating large pixel buffers.

**Recommendation**: 
1. Cache rendered bitmaps per (pageIndex, renderScale) in an LRU cache
2. Reuse bitmap objects via `BitmapPool` / `Bitmap.reconfigure()`
3. Render at lower quality during active pinch-zoom, then high quality on settle

### 3.3 — No PDF page caching across page navigation (MEDIUM)
**Current**: When navigating between PDF pages, the previous page's bitmap is discarded and re-rendered when returning.

**Recommendation**: Keep an LRU cache of 3-5 rendered page bitmaps to enable instant back/forward navigation.

### 3.4 — PDF text selection is fragile (LOW)
**Current**: `findCharAtPagePoint()` iterates through ALL characters on a page linearly. `getSelectionQuads()` also iterates all characters. For text-heavy PDFs, this is O(n) per touch event.

**Recommendation**: Build a spatial index (R-tree or grid) for character positions on first text extraction. Cache the structured text per page.

### 3.5 — No PDF annotation persistence separate from ink (LOW)
**Current**: Ink strokes on PDF pages are stored identically to ink-only pages. When the page is exported or shared, there's no way to distinguish "this stroke is an annotation on a PDF" from "this stroke is original ink content."

**Recommendation**: Add an `annotationLayer: Boolean` field to `StrokeEntity` or use page `kind = "mixed"` consistently. Plan Av2 doesn't address this, but collaboration/sharing (Plan C) will need it.

---

## 4. Performance

### 4.1 — Stroke rendering is O(n*m) per frame (HIGH)
**Current**: `InkCanvas` renders ALL strokes on every frame in the `Canvas` composable. `drawStrokePoints()` iterates through every point of every stroke, doing `pageToScreen()` coordinate conversion per point. For 100 strokes × 200 points = 20,000 line segments per frame.

**Recommendation**:
1. **Render completed strokes to an offscreen bitmap** and only re-render when strokes change (dirty flag). Draw only the in-progress stroke to the Canvas live.
2. **Use `Path` batching** — build a single `Path` per stroke instead of individual `drawLine()` calls.
3. **Viewport culling** — skip strokes whose bounds don't intersect the visible viewport.
4. **Consider Vulkan/OpenGL** for the stroke rendering layer long-term (as Samsung Notes does).

### 4.2 — Smoothing is applied on every render frame (MEDIUM)
**Current**: `smoothStrokePoints()` creates a new `ArrayList` and performs the 3-point average on every draw call. For completed strokes that never change, this is wasted work.

**Recommendation**: Compute smoothed points once at stroke creation time (in `buildStroke()`) and cache them. Store both raw and smoothed point arrays.

### 4.3 — In-progress stroke points accumulate without limit (LOW)
**Current**: `runtime.activeStrokePoints[pointerId]` grows unbounded during a stroke. For very long strokes (e.g., a continuous line across the page), this list can have thousands of entries that are all re-rendered each frame.

**Recommendation**: For in-progress rendering, only keep the last N points in the active list (e.g., 50) and commit earlier segments to the Canvas bitmap.

### 4.4 — Coordinate conversion allocates Pair objects (LOW)
**Current**: `ViewTransform.pageToScreen()` and `screenToPage()` return `Pair<Float, Float>`, allocating a new object per call. In hot loops (stroke rendering, touch events), this creates GC pressure.

**Recommendation**: Use inline functions returning to pre-allocated arrays, or use destructuring with inline classes. Kotlin `Pair` is not value-inlined.

### 4.5 — JSON serialization of every stroke point field (MEDIUM)
**Current**: `StrokeSerializer` serializes all 7 fields per point (x, y, t, p, tx, ty, r) even when many are null. With `explicitNulls = false`, null fields are omitted, but the JSON overhead (field names, delimiters) is still significant.

**Recommendation**: Use a columnar binary format: pack all x values, then all y values, etc. This compresses better and avoids per-field JSON overhead. Or use Protocol Buffers as the plan suggests for v1.

### 4.6 — No frame rate management (LOW)
**Current**: The Canvas recomposes on every `activeStrokeRenderVersion` increment, which happens on every touch move event. On a 120Hz display, this is 120 draw calls per second.

**Recommendation**: Cap the Canvas invalidation rate to 60fps (or the display refresh rate) using a frame callback mechanism.

---

## 5. UI/UX Changes

### 5.1 — Toolbar is horizontally overloaded (HIGH)
**Current**: The single top toolbar row contains: Back, Grid, Add Page, Search, Inbox | Undo, Redo | Pen, Highlighter, Eraser | 5 Color Swatches | Prev Page, Next Page, New Page | View/Edit Toggle. That's ~18 interactive elements in one row.

**Recommendation**: Follow the Samsung Notes / Notewise pattern from the UI Overhaul plan:
1. **Left pill group**: Back + title (tappable to edit)
2. **Center pill group**: Drawing tools (pen, highlighter, eraser) + color palette
3. **Right pill group**: Undo/Redo + page nav + overflow menu
4. Move Search, Grid, and Inbox to an overflow/settings menu

### 5.2 — No page indicator (MEDIUM)
**Current**: Page navigation is via prev/next buttons but there's no "Page 3 of 12" indicator visible.

**Recommendation**: Add a page counter in the toolbar (e.g., "3/12") that's tappable to open a page thumbnail grid.

### 5.3 — Grid view button is a no-op (LOW)
**Current**: `IconButton(onClick = {}, ...)` for Grid, Search, and Inbox buttons in the toolbar. These are wired to empty lambdas.

**Recommendation**: Either implement the functionality or remove the buttons. Dead buttons confuse users and fail accessibility audits.

### 5.4 — Color picker needs a visual spectrum (MEDIUM)
**Current**: Color picker is a hex input text field only. No visual color wheel, spectrum, or preset swatches beyond the 5-dot palette.

**Recommendation**: The UI Overhaul plan specifies a "Superior spectrum + swatches with hex input" from Samsung Notes. Implement a basic HSV color wheel with the current hex input as an advanced option.

### 5.5 — No dark mode canvas (LOW)
**Current**: Note paper is always white (`NOTE_PAPER = Color(0xFFFDFDFD)`). The toolbar chrome uses a dark Notewise theme but the canvas doesn't adapt.

**Recommendation**: Support dark canvas mode (dark paper, light ink) as a user preference. This is important for OLED tablet displays.

### 5.6 — Eraser mode UI is confusing (MEDIUM)
**Current**: Eraser settings dialog has "Soft eraser" and "Object eraser" buttons but the `eraserMode` state is local and not wired to the actual eraser behavior. The eraser always does stroke-level erasure.

**Recommendation**: Either implement segment eraser (Plan Av2) or remove the Soft/Object buttons to avoid misleading users. For MVP, show only "Stroke eraser" with a size slider.

### 5.7 — Read-only mode blocks all input (LOW)
**Current**: `ReadOnlyInputBlocker` uses an invisible `AndroidView` that intercepts ALL touch events. This also blocks pinch-to-zoom and pan.

**Recommendation**: In read-only mode, allow zoom/pan gestures but block drawing. Filter `MotionEvent` tool types instead of blanket blocking.

### 5.8 — No haptic feedback on tool selection (LOW)
**Current**: Tool selection, color selection, and eraser toggle provide no tactile feedback.

**Recommendation**: Add `HapticFeedbackType.LongPress` on tool toggle and light haptics on undo/redo.

---

## 6. Stroke Rendering & Smoothing

### 6.1 — Simple 3-point average smoothing is insufficient (HIGH)
**Current**: `smoothStrokePoints()` uses a simple 3-point moving average: `(previous + current + next) / 3`. This produces slightly smoother lines but still shows angular artifacts at direction changes and doesn't account for speed or pressure.

**Recommendation**: Implement a multi-pass smoothing pipeline:
1. **Catmull-Rom spline interpolation** — generates smooth curves through control points
2. **Ramer-Douglas-Peucker simplification** — reduces point count while preserving shape
3. **Speed-adaptive smoothing** — apply more smoothing at high speed, less at low speed for detail retention
4. **Stabilization slider** — the UI has a stabilization slider but it only adjusts `minWidthFactor/maxWidthFactor`. It should control the smoothing algorithm's window size.

### 6.2 — Stabilization slider doesn't actually stabilize (HIGH)
**Current**: `resolveStabilization()` and `applyStabilization()` modify `minWidthFactor` and `maxWidthFactor` — these control **pressure-to-width mapping**, not stroke smoothing. The slider is labeled "Stabilization" but only affects line thickness variation.

**Recommendation**: True stabilization should apply a moving average or low-pass filter to the INPUT points during drawing (before they're recorded). Options:
1. **Lazy Nezumi-style**: Hold the cursor position behind the actual pen position, creating a rubber-band effect
2. **Moving window average on input**: Average the last N points where N = stabilization level
3. **Dead zone**: Ignore small movements below a threshold scaled by stabilization level

### 6.3 — No line start/end tapering (MEDIUM)
**Current**: Strokes have uniform width at start and end. Natural handwriting tapers — thin at start, full width in middle, thin at end.

**Recommendation**: Apply start/end tapering based on the first/last N points. Scale width from 0 → full over the first 3-5 points, and full → 0 over the last 3-5 points.

### 6.4 — drawLine() per segment creates rendering artifacts (MEDIUM)
**Current**: `drawStrokePoints()` draws individual line segments with `StrokeCap.Round`. At segment junctions with different widths, this creates visible "beading" artifacts — each segment's round cap overlaps with the next.

**Recommendation**: Use `Path`-based rendering:
1. Build a `Path` along the stroke centerline
2. Use `drawPath()` with varying stroke width (via multiple thin sub-paths or a custom shader)
3. Or render as a filled polygon (compute outline points offset by width/2 on each side of the centerline)

### 6.5 — Highlighter doesn't blend correctly (MEDIUM)
**Current**: Highlighter color has alpha applied via `applyOpacity()`, but overlapping highlighter strokes create darker regions where segments overlap. This is because each `drawLine()` call composites independently.

**Recommendation**: Render each highlighter stroke to a temporary layer/bitmap with full opacity, then composite the entire stroke onto the canvas at the target alpha. This gives Samsung Notes-style clean highlighting.

### 6.6 — Pressure width curve is linear (LOW)
**Current**: `pressureWidth()` uses a linear interpolation between `minWidthFactor` and `maxWidthFactor`. Real stylus pressure response is typically non-linear — low pressure should produce thinner lines more quickly.

**Recommendation**: Apply a pressure curve (e.g., `pressure^0.7` or a configurable bezier curve). Allow users to adjust the pressure curve in settings.

### 6.7 — InProgressStrokesView is invisible (alpha = 0) — wasted resource (MEDIUM)
**Current**: `InProgressStrokesView` is created and used for input routing but rendered invisible (`alpha = 0f`). All actual rendering happens via the Compose `Canvas`. The `InProgressStrokesView` still allocates GPU surfaces and processes strokes internally.

**Recommendation**: Either:
1. Use `InProgressStrokesView` for ACTUAL low-latency rendering of in-progress strokes (its designed purpose) and render committed strokes via Canvas
2. Or remove `InProgressStrokesView` entirely and handle touch events directly via a plain `View.setOnTouchListener()`

Option 1 is preferred — it gives true front-buffer low-latency rendering for the in-progress stroke while Canvas handles committed strokes. This is the Jetpack Ink intended usage pattern.

---

## 7. Missing Features vs Plans

### 7.1 — Plan A gaps in current code
| Feature | Plan A Spec | Current Code | Status |
|---------|-------------|-------------|--------|
| Title editing | User-editable title | No UI for editing | ❌ Missing |
| Note deletion UI | Soft delete | Repository exists, no UI | ❌ Missing |
| Recognition search UI | Search in Home Screen | Search field exists, results shown | ✅ Present |
| PDF size warning | Soft warning dialog | Implemented | ✅ Present |
| Undo/Redo | 50-action stack | Implemented | ✅ Present |
| Page navigation | Prev/Next/New | Implemented | ✅ Present |
| Ink/PDF/Mixed page types | Three kinds | Implemented with auto-upgrade | ✅ Present |
| Brush size slider | In tool settings | Implemented | ✅ Present |
| Highlighter opacity | In tool settings | Implemented | ✅ Present |
| Eraser hit testing | Stroke-level | Implemented with bounds check | ✅ Present |
| MyScript recognition | Real-time per-page | Implemented | ✅ Present |
| Device identity | UUID in SharedPrefs | Implemented | ✅ Present |
| FTS search index | RecognitionFtsEntity | Entity exists, DAO wired | ✅ Present |
| InkSurface interface | Abstract ink surface | Interface exists, unused | ⚠️ Dead code |
| ViewTransform | Zoom/pan with coordinate conversion | Fully implemented | ✅ Present |

### 7.2 — Plan Av2 prerequisites missing
| Feature | Required Before Av2 | Current State |
|---------|---------------------|---------------|
| InkCanvas.kt in ink/ui/ | Av2 references this file | ✅ Exists |
| StrokeEntity with bounds | Av2 needs spatial queries | ✅ Exists |
| Lamport clock increment | Av2 segment eraser uses createdLamport | ❌ Always 0 |
| Database migration support | Av2 adds MIGRATION_1_2 | ❌ Destructive fallback |
| geometryKind routing | Av2 infinite canvas checks this | ✅ Field exists |

### 7.3 — UI Overhaul prerequisites missing
| Feature | Required | Current State |
|---------|----------|---------------|
| Folder entities/DAOs | UI Overhaul adds folders | ❌ Not created |
| Template entities/DAOs | UI Overhaul adds templates | ❌ Not created |
| UserPreferenceEntity | Settings persistence | ❌ Not created |
| StylusPreferenceEntity | S Pen configuration | ❌ Not created |
| Design tokens (Color.kt) | New semantic colors | ❌ Only basic palette |

---

## Prioritized Change List

### Critical (Do before shipping or Plan Av2)
1. **Extract ViewModel out of UI file** → testability, maintainability
2. **Replace destructive DB migration** → user data preservation
3. **Cache stroke smoothing results** → performance
4. **Implement offscreen bitmap for committed strokes** → rendering performance
5. **Fix stabilization slider** → actually controls smoothing, not just width factors
6. **Split NoteEditorUi.kt** → maintainability (1,147 lines is too large)

### High Priority (Do before UI Overhaul)
7. **Add title editing in editor** → core user feature
8. **Use InProgressStrokesView for actual low-latency rendering** → intended purpose
9. **Switch to Path-based stroke rendering** → visual quality
10. **Add viewport culling for stroke rendering** → performance
11. **Implement note deletion/management in Home Screen** → core feature
12. **Fix highlighter blending** → visual quality
13. **Remove dead toolbar buttons** → UX clarity

### Medium Priority (Plan Av2 / UI Overhaul timeline)
14. **Implement Catmull-Rom spline smoothing** → writing quality
15. **Add stroke start/end tapering** → natural look
16. **Implement PDF bitmap caching** → navigation performance
17. **Add auto-save title from recognition** → convenience
18. **Add page indicator ("3/12")** → navigation UX
19. **Wire Lamport clock increment** → sync preparation
20. **Implement pressure curve control** → customization
21. **Add error states to ViewModel** → robustness

### Low Priority (Quality of life)
22. **Add read-only mode with zoom/pan** → usability
23. **Add haptic feedback** → tactile UX
24. **Add dark canvas mode** → OLED display support
25. **Optimize coordinate conversion (avoid Pair allocation)** → GC pressure
26. **Add PDF character spatial index** → text selection performance

---

## Summary

The codebase is a competent MVP with strong foundations in offline-first architecture, ink capture, and PDF handling. The main concerns are:

1. **Performance**: Stroke rendering is brute-force per-frame — will not scale beyond ~100 strokes without frame drops. The offscreen bitmap pattern is the standard solution used by all production note-taking apps.

2. **Code organization**: The ViewModel-in-UI-file and monolithic UI file patterns will cause increasing pain as features are added. Splitting now is much cheaper than splitting later.

3. **Stroke quality**: The smoothing and rendering pipeline is functional but produces noticeably inferior results compared to Samsung Notes or GoodNotes. The key upgrades are spline interpolation, Path-based rendering, and proper highlighter compositing.

4. **User data safety**: Destructive migration will erase user notes on any schema change. This must be fixed before real users.

5. **Stabilization is mislabeled**: The slider controls pressure-to-width mapping, not drawing stabilization. This is a UX bug that will confuse users who expect it to steady their hand.
