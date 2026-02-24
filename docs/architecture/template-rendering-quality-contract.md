# Template Rendering Quality Contract

Date: 2026-02-24  
Scope: Android template backgrounds (`blank/grid/lined/dotted`) in editor canvas rendering.

## Goals

1. Keep template marks page-locked during zoom/pan (no "swim" artifacts).
2. Keep visual density stable in page units.
3. Preserve legibility across low/high zoom with bounded stroke/dot sizing behavior.

## Rendering Invariants

1. Coordinate lock:
   - Pattern primitives are generated in page coordinates.
   - Screen conversion is exclusively `screen = page * zoom + pan`.
2. Density:
   - Spacing controls operate in page units; transform changes must not mutate pattern spacing.
3. Stroke and dot sizing:
   - Grid/lined stroke width scales inversely with zoom (`1 / zoom` baseline).
   - Dotted template dot radius is bounded to avoid disappearing dots at high zoom and oversized blobs at low zoom.
4. Empty state:
   - `blank` template produces no primitives.
5. Bounds safety:
   - Non-positive page dimensions or spacing produce no primitives.

## Regression Checks

1. Unit tests:
   - Pattern count checks per template kind.
   - Transform mapping checks for page->screen coordinate stability.
   - Spacing range contract checks per kind.
2. Manual visual checks:
   - Pan at fixed zoom on dense grid; intersections should remain page-anchored.
   - Zoom sweep (`50%` -> `400%`) on dotted template; dot distribution remains uniform.

## Current Gaps

1. No automated screenshot diff gate for anti-alias behavior across GPU classes.
2. No per-device rendering threshold matrix yet.
