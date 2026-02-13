# Ink Latency Investigation (P1.3)

**Date:** 2026-02-13  
**Status:** Blocked on physical-device Perfetto capture

## Scope

- Investigate whether `GLFrontBufferedRenderer` can reduce pen latency beyond current `InProgressStrokesView` path.
- Produce binary decision for milestone gate.

## Findings

1. AndroidX low-latency front-buffer APIs (`GLFrontBufferedRenderer` / `CanvasFrontBufferedRenderer`) are built around a `SurfaceView` host.
2. The current editor composition uses Compose + `AndroidView(InProgressStrokesView)` layering.
3. Replacing this path with custom GL front-buffer rendering would introduce a second `SurfaceView` integration risk (z-order/clipping/input interop) before any measured gain is proven.

## Measurement Blocker

- Required Perfetto latency capture on physical stylus hardware is not available in this environment.
- Emulator results are not accepted for this milestone's latency SLO validation.

## Decision

- **Decision code:** `PENDING_DEVICE_PROFILE`
- **Interim direction:** keep existing `InProgressStrokesView` path; do not start custom GL front-buffer implementation yet.
- **Go condition:** measured P95 latency improvement >= 5ms on physical device without layering regressions.
- **No-go condition:** improvement < 5ms or any Compose layering regression.

## Next Step When Device Is Available

1. Capture baseline Perfetto trace during stylus drawing/pan flow.
2. Prototype GL front-buffer spike in isolated branch.
3. Re-capture Perfetto and compare P95 input-to-photon delta.
4. Update this file with final binary decision (`GO` or `NO_GO`).
