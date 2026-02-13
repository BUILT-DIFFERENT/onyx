# Ink Latency Investigation (P1.3)

**Date:** 2026-02-13  
**Status:** Completed - `NO_GO` for custom GL front-buffer path

## Scope

- Investigate whether `GLFrontBufferedRenderer` can reduce pen latency beyond current `InProgressStrokesView` path.
- Produce binary decision for milestone gate.

## Findings

1. AndroidX low-latency front-buffer APIs (`GLFrontBufferedRenderer` / `CanvasFrontBufferedRenderer`) are built around a `SurfaceView` host.
2. The current editor composition uses Compose + `AndroidView(InProgressStrokesView)` layering.
3. Replacing this path with custom GL front-buffer rendering would introduce a second `SurfaceView` integration risk (z-order/clipping/input interop) before any measured gain is proven.

## Measurement Note

- Physical stylus Perfetto profiling is still recommended for future optimization work.
- It is not required to decide this milestone gate because integration constraints already fail the adoption criteria.

## Decision

- **Decision code:** `NO_GO`
- **Direction:** keep existing `InProgressStrokesView` path; do not implement custom GL front-buffer renderer in this milestone.
- **Go condition:** measured P95 latency improvement >= 5ms on physical device without layering regressions.
- **No-go condition:** improvement < 5ms or any Compose layering regression.

## Follow-up (Optional)

1. Capture baseline Perfetto trace during stylus drawing/pan flow.
2. Prototype GL front-buffer spike in isolated branch.
3. Re-capture Perfetto and compare P95 input-to-photon delta.
4. Re-open this decision only if layering constraints are solved and measured gain is >= 5ms.
