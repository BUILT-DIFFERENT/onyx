# Comprehensive App Change Plan (Single Source of Truth)

Date: 2026-02-16
Owner: Onyx engineering
Status: Proposed

## Objective

Deliver a stable, low-latency Android note/PDF editor that closes the core parity gaps (ink feel, transform stability, PDF performance, library organization, recognition/search), while keeping release safety and CI quality gates intact.

## Scope

In scope:
- Android editor UX/performance fixes (ink, transforms, tool UI, PDF)
- Data model and persistence changes required to support those fixes
- Validation, CI, rollout, and feature flags
- Required backend/web/shared work needed for full product operation

Out of scope:
- OEM-only features (for example, true screen-off memo behavior)
- Unsupported/locked hardware SDK features unless explicitly approved (for example, Samsung-only Air Actions)
- Backward compatibility with older local Android DB versions (per current policy)

## Non-Negotiable Standards

- Official Android ink prediction path:
  - MotionEventPredictor.newInstance(view) + 
ecord() + predict()
  - InProgressStrokesView.addToStroke(..., prediction)
  - Handoff sync: keep finished wet strokes visible until committed layer draw is present, then remove
- Turborepo env hygiene:
  - All new env vars in 	urbo.json (globalEnv or task nv)
  - Relevant tasks must include .env* in inputs for cache correctness
- CI quality bar:
  - lint + typecheck + tests green
  - no bypass of commit hooks

## Plan (Phased)

### Phase 0: Baseline and Gates

- Establish performance baselines: ink latency, zoom/pan smoothness, PDF tile render time, memory
- Add instrumentation: frame timing, tile in-flight counts, stroke finalize timing
- Define acceptance SLOs:
  - no visible stroke handoff artifacts
  - stable zoom/pan without drift or jump
  - no black flash/blank frame during page switch
- Introduce feature flags for high-risk paths:
  - ink.prediction.enabled
  - ink.handoff.sync.enabled
  - ink.frontbuffer.enabled
  - pdf.tile.request.throttle.enabled

### Phase 1: Core Editor Experience (P0)

- Responsive editor toolbar:
  - expanded + compact modes
  - overflow handling for narrow widths
  - preserve primary actions in compact mode
- Transform stability:
  - frame-aligned transform updates (not event-storm driven)
  - focal-point preserving zoom/pan
  - stable orientation change behavior (preserve page focus)
- Low-latency ink path:
  - remove separate predicted-stroke overlay model
  - integrate official prediction API into active stroke updates
  - explicit pen-up handoff synchronization
- Stroke style correctness:
  - persist smoothing and ndTaper in stroke style model
  - map UI sliders to renderer parameters
  - keep defaults backward-compatible
- Pen settings reliability:
  - numeric values, live preview, reset defaults
  - clear anchoring and close affordance
- Input polish:
  - anti-aliasing quality maintained across zoom levels
  - hover preview (brush cursor) when hardware supports it
  - touch-target sizing and edge-safe placement
- Selection and undo/redo performance:
  - spatial index for hit testing and lasso on large docs
  - keep action stack operations fast and memory-stable

### Phase 2: PDF Performance and Interaction Parity (P0/P1)

- Viewport-driven tile requests with frame-aligned debouncing
- Scale bucket policy:
  - immediate upshift on zoom-in
  - hysteresis on step-down
- Visual continuity:
  - retain previous content until replacement render is ready
  - avoid black/blank transitions
- Cache safety:
  - bitmap lifecycle guards under LRU eviction/concurrency
  - avoid draw/recycle races
- Missing PDF UX primitives:
  - page jump input
  - thumbnail navigation strip
  - text selection quality + clipboard
- Optional: dark-mode ergonomics via PDF smart inversion

### Phase 3: Home/Library and Organization (P1)

- Folder-capable library UX:
  - folder tree + breadcrumb path
  - note counts + robust folder CRUD
- Visual scanning improvements:
  - note thumbnails
  - metadata-rich cards/list
  - stronger information hierarchy/contrast
- Organization workflows:
  - move notes between folders
  - multi-select batch operations
- Settings persistence:
  - tool presets and preferences via DB-backed preference model
  - migrate existing shared preferences at startup

### Phase 4: Recognition, Conversion, and Unified Search (P1/P2)

- Build one semantic index across:
  - MyScript recognized handwriting
  - PDF typed text
  - note metadata (title, etc.)
- Unified FTS query surface + result model with source type and geometry
- Conversion workflows:
  - select/lasso region → convert to text
  - editable conversion result
- Optional recognition overlay:
  - toggle, opacity/font controls
  - transform-aware positioning
- Debounced persistence + bulk rebuild paths

### Phase 5: Reliability and Data Safety (P0/P1)

- Explicit Room migrations + migration tests (no destructive fallback)
- Deterministic PDF native resource cleanup
- Sync-readiness primitives:
  - monotonically seeded Lamport handling
  - device identity propagation
  - operation log model planning
- Remove dead/no-op UI actions and align all visible controls with real behavior

### Phase 6: Advanced Competitive Features (P2, After Core Stability)

- Segment eraser (stroke splitting with undo/redo safety)
- Lasso transforms (move/resize/rotate selected strokes)
- Template system (grid/line/dot with configurable density/opacity)
- Optional floating/dockable toolbar ergonomics
- Zoom-box/tape/ruler class features as separate scoped epics

## Validation and CI (Continuous)

- Tests:
  - unit: smoothing/taper, transform math, cache lifecycle, DB migrations
  - instrumented Compose: compact toolbar presence, settings behavior, accessibility
  - visual regression: Paparazzi screenshot goldens
  - E2E: Maestro editor/PDF flows
  - web visual checks: Playwright snapshots where applicable
- Performance validation:
  - Baseline Profiles and macrobenchmark journeys
- CI gates:
  - lint + typecheck + test gates
  - artifacts for screenshot diffs and emulator runs

## Prioritization

- Critical first:
  - responsive toolbar, transforms, ink prediction + handoff, smoothing/taper persistence, PDF tile scheduling + cache safety, explicit migrations, baseline test matrix
- Next:
  - PDF navigation UX, library folders + thumbnails, unified search index, performance baselines
- Later:
  - conversion workflows, sync readiness, advanced tools

## Acceptance Criteria (Release Gate)

- Editor:
  - no control loss in compact/narrow layouts
  - no visible stroke handoff artifacts in normal usage
  - ink settings produce deterministic, visible behavior
- PDF:
  - no black/blank transition flashes during normal navigation
  - zoom-in does not remain blurry while waiting for scale correction
- Library:
  - users can organize and find notes through folder and visual browsing flows
- Search/Recognition:
  - one search surface returns relevant matches across handwriting + PDF + metadata
- Reliability:
  - migration path verified; no silent data loss regressions in tested upgrades
- Quality:
  - lint/typecheck/tests green in CI for required pipelines

## Open Decisions (Need Product Sign-off)

- Home/library direction:
  - align to current Material baseline vs fully emulate recorded visual style
- Front-buffer renderer:
  - ship now vs hold behind experimental flag until broader device validation
- Advanced tools scope:
  - segment eraser/lasso/templates in core delivery vs post-core milestone

## Implementation Notes

- Use existing milestone docs in .sisyphus/plans/ as detailed execution references.
- Treat this document as the top-level roadmap and sequencing authority.
- If any milestone doc conflicts with this plan, update that milestone doc to align.
