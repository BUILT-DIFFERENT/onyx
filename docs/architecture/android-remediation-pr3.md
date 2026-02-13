# Android Remediation PR3 (Rendering and Performance Follow-ups)

Date: 2026-02-12  
Source review: `C:/onyx/.sisyphus/notepads/android-architecture-review.md`  
Related phase doc: `C:/onyx/docs/architecture/android-remediation-pr2.md`

## Goal
Execute PR3 from the Android remediation plan and close the remaining rendering/performance hardening items while keeping PR1/PR2 behavior intact.

## Review Findings Mapped To This Phase
- PDF bitmap re-render churn and no cross-page cache (`android-architecture-review.md`: 153, 156, 160)
- PDF text selection O(n) extraction without reusable page structure (`android-architecture-review.md`: 164, 166)
- Stroke write queue persistence divergence risk on failure (`android-architecture-review.md`: 75, 78)
- Hot-path coordinate conversion allocations in inner loops (`android-architecture-review.md`: 200, 203)
- Preserve current strengths (in-progress ink, path cache, viewport culling) while avoiding regressions (`android-architecture-review.md`: 183, 187, 293)

## PR3 Scope Checklist
- [x] Add PDF bitmap LRU caching across `(pageIndex, renderScale)` buckets.
- [x] Add optional per-page PDF text-structure caching for selection interactions.
- [x] Keep stroke write queue retry and add explicit retry logging for first-failure and retry-success/failure paths.
- [x] Reduce hot-path `Pair` allocations in coordinate conversion paths used during touch/draw loops.
- [x] Keep existing rendering strengths (path cache + viewport culling + in-progress ink view) intact.
- [x] Re-verify unresolved PR2 UX detail: eraser settings now only expose current stroke-eraser behavior.

## Implementation Notes
- PDF render/text cache implementation:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/pdf/PdfRenderer.kt`
  - Added `LruCache` for bitmaps keyed by `PdfBitmapCacheKey(pageIndex, renderScaleKey)`.
  - Added `LruCache` for `StructuredText` keyed by `pageIndex`.
  - Added synchronized access around renderer/document/cache operations.
- Hot-path transform allocation reduction:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/model/ViewTransform.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasDrawing.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasGeometry.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`
- Queue retry logging hardening:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt`
- Eraser panel scope alignment:
  - `C:/onyx/apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt`

## Validation Added For This Phase
- Unit tests:
  - `C:/onyx/apps/android/app/src/test/java/com/onyx/android/pdf/PdfRendererCacheKeyTest.kt`
    - Render-scale cache key stability and clamping behavior.
  - `C:/onyx/apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorTransformMathTest.kt`
    - Axis helper parity with existing pair-based conversions.
  - `C:/onyx/apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorViewModelTest.kt`
    - Retry-success path does not emit queue failure state.
- Compose/android tests:
  - `C:/onyx/apps/android/app/src/androidTest/java/com/onyx/android/ui/NoteEditorToolbarTest.kt`
    - Eraser long-press panel shows stroke-eraser messaging and no brush-size control.

## Previous-Phase Completion Audit
- PR1 checks remain satisfied:
  - Extracted editor VM/state/controller files are still in dedicated files.
  - Protobuf stroke point serialization remains active.
  - App container dependency access remains active.
  - Room destructive fallback remains removed.
- PR2 checks remain satisfied:
  - Title editing, page counter, and long-press delete are still wired.
  - Dead top-bar controls remain removed/replaced.
  - Read-only mode still preserves pan/zoom while blocking edit actions.
  - Eraser settings now fully match current implementation constraints.

## Final Status For This Remediation Track
- [x] PR1 (Architecture + Data Layer Hardening) complete.
- [x] PR2 (UX Gaps) complete.
- [x] PR3 (Rendering/Performance Follow-ups) complete.
