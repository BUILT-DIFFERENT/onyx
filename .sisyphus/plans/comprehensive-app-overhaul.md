# Comprehensive App Overhaul: Onyx Android (Authoritative Plan)

Date: 2026-02-16
Owner: Onyx engineering
Status: Proposed for execution

## Context

### Original Request

Create one authoritative overhaul plan at `.sisyphus/plans/comprehensive-app-overhaul.md` that fully synthesizes all architecture docs, deep research reports, and prior milestone plans, and is detailed enough to pass strict review without hidden gaps.

### Interview and Research Summary

**Key findings integrated into this plan:**

- Milestone A is code-complete, but several high-impact items remain blocked on physical device validation.
- Ink rendering quality improved, but prediction, front-buffer behavior, and pen-up handoff correctness still need hardening.
- PDF path needs licensing-safe migration from MuPDF to Pdfium-based rendering, with tile scheduling and memory race hardening.
- Pdfium fork selection is already decided by prior approved plan: `Zoltaneusz/PdfiumAndroid` at `v1.10.0`.
- Critical UI/architecture debt exists: `NoteEditorUi.kt` decomposition, Home screen state architecture cleanup, DI cleanup, and robust settings persistence.
- Unified search and recognition need to converge into one user-facing query surface.
- Existing plans contain strong sub-plans but are fragmented; this file is now the single source of truth.

### Source of Truth Hierarchy

1. `docs/architecture/comprehensive-app-change-plan.md` (top-level sequencing authority)
2. This plan (`.sisyphus/plans/comprehensive-app-overhaul.md`)
3. Prior milestone plans in `.sisyphus/plans/` (reference detail only)
4. Context deep-research reports in `docs/context/`

### Scope Boundaries

**IN SCOPE**

- Android editor overhaul: ink, transforms, PDF pipeline, UI, organization, recognition/search, reliability, testing and rollout gates.
- Local data model changes and migrations needed by Android features.
- Feature flags, CI quality gates, and performance instrumentation.

**OUT OF SCOPE**

- Web viewer work (`apps/web`) beyond compatibility checks.
- Collaboration/sync backend implementation (Convex runtime logic).
- OEM-only SDK behaviors that are not broadly available.
- Backward compatibility with older local Android DB versions (explicitly not required by current policy).

### Current Repository Baseline (Verified)

- Pdfium is already integrated in app dependencies: `apps/android/app/build.gradle.kts` (`com.github.Zoltaneusz:PdfiumAndroid:v1.10.0`).
- JitPack is already configured for Android builds: `apps/android/settings.gradle.kts`.
- PDF tile + async pipeline already exists in code:
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileCache.kt`
  - `apps/android/app/src/main/java/com/onyx/android/pdf/AsyncPdfPipeline.kt`
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileRenderer.kt`
- Native text geometry bridge already exists:
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumNativeTextBridge.kt`
- Runtime feature behavior is currently controlled by hardcoded constants, not a unified flag service:
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
- CI currently runs web/general checks only (`bun run lint/typecheck/test/build/e2e`) in `.github/workflows/ci.yml`; Android quality gates are not yet required there.

## Work Objectives

### Core Objective

Deliver a stable, low-latency, commercial-grade Android note/PDF editor with measurable parity improvements in ink feel, PDF responsiveness, organization workflows, and search quality while preserving release safety.

### Concrete Deliverables

- Low-latency ink pipeline with robust prediction and artifact-free pen-up handoff.
- Pdfium-based, tile-first PDF rendering pipeline with concurrency-safe cache behavior.
- Responsive editor and library UI with clear architecture boundaries and accessibility baselines.
- Unified search surface across handwriting recognition, PDF text, and note metadata.
- Release-quality validation matrix (automated + physical device evidence).

### Definition of Done

- [ ] All phases complete with acceptance criteria met.
- [ ] `bun run android:lint` passes.
- [ ] `bun run android:test` passes.
- [ ] `bun run detekt` and `bun run ktlint` pass.
- [ ] Device gate evidence captured for all tasks marked "physical-device required".
- [ ] No open Sev-1/Sev-2 regressions in ink, PDF, or document safety paths.

### Non-Negotiable Guardrails

- Official prediction integration path must be used (MotionEventPredictor adapter + in-progress stroke integration).
- Pen-up handoff must avoid visible disappearance between wet and committed layers.
- PDF tile lifecycle must prevent draw/recycle races under cancellation and eviction.
- All high-risk feature work behind runtime flags with kill-switch behavior.
- No bypass of commit hooks; all CI quality gates remain enforced.

### Must Not Have (Explicit Non-Goals)

- No hidden expansion into backend sync implementation.
- No unbounded redesign work without acceptance criteria.
- No major module re-architecture unless tied to measurable stability/perf goals.
- No OEM-exclusive features in core release path.

## Verification Strategy (Mandatory)

### Test Decision

- Infrastructure exists: YES.
- Strategy: Hybrid.
  - TDD for math/algorithms/data migrations/cache lifecycle.
  - Compose/instrumented/UI automation for critical user flows.
  - Manual physical-device verification for stylus/latency/rendering parity.

### Performance and Reliability Gates

- Ink latency target: p95 perceived latency <= 20ms on Tier-A stylus hardware.
- Frame stability target: <= 3% janky frames in sustained draw and PDF pan/zoom journeys.
- PDF tile first-visible target: p95 <= 120ms after viewport settles.
- Memory budget target: bitmap caches remain within configured 25-35% of memory class.
- No black/blank transition flashes during normal zoom/page navigation.

### Device Matrix (Default)

- Tier A (stylus primary): Samsung tablet class device with S-Pen.
- Tier B (touch baseline): Pixel-class phone (non-stylus touch).
- Tier C (stress): Low-RAM emulator configuration.

### Evidence Requirements

- Automated command output captured for each phase.
- Screenshots/video clips for visual parity claims.
- Before/after performance logs for high-risk rendering changes.
- Migration and rollback validation notes in `.sisyphus/notepads/`.

## Task Flow

```text
Phase 0 -> Phase 1 -> Phase 2 -> Phase 3 -> Phase 4 -> Phase 5 -> Phase 6
                                \-> Phase 7 (after Phase 6 gate, optional for release cut)
```

## Parallelization Plan

| Group | Parallel Tasks | Constraint                                                            |
| ----- | -------------- | --------------------------------------------------------------------- |
| A     | 0.2, 0.3, 0.4  | Independent setup work after 0.1 scope lock                           |
| B     | 1.3, 1.5       | Style schema and spatial index can proceed after 1.1 started          |
| C     | 2.3, 2.4       | Cache hardening and visual continuity can run after 2.2 adapter layer |
| D     | 3.3, 3.4, 3.5  | UI architecture cleanup streams can run in parallel                   |
| E     | 4.2, 4.3       | Thumbnails and settings migration can run after schema locks          |

## TODOs

### Phase 0 - Program Baseline and Gates

- [ ] 0.1 Lock scope, authority, and non-goals

  **What to do**
  - Freeze this plan as the single implementation authority.
  - Cross-map prior milestone tasks to this plan and mark superseded items.
  - Publish explicit IN/OUT scope and guardrails in team notes.

  **Must NOT do**
  - Do not start implementation before this alignment is complete.

  **Parallelizable**: NO (root dependency)

  **References**
  - `docs/architecture/comprehensive-app-change-plan.md` - sequencing authority.
  - `.sisyphus/plans/milestone-canvas-rendering-overhaul.md` - detailed rendering prior art.
  - `.sisyphus/plans/milestone-ui-overhaul-samsung-notes.md` - UI capability targets.

  **Acceptance Criteria**
  - [x] Supersession mapping documented.
  - [ ] Scope guardrails approved and visible.

  **Commit**: NO (documentation-only alignment)

- [ ] 0.2 Feature flags and kill switches

  **What to do**
  - Define canonical flag catalog with default + rollback mapping:
    - `ink.prediction.enabled` (default: false) -> safe path: no prediction.
    - `ink.handoff.sync.enabled` (default: true) -> safe path: immediate finalize (debug only).
    - `ink.frontbuffer.enabled` (default: false) -> safe path: existing in-progress render path.
    - `pdf.tile.throttle.enabled` (default: true) -> safe path: unthrottled current scheduler.
    - `ui.editor.compact.enabled` (default: true) -> safe path: existing toolbar mode.
  - Create a unified runtime flag layer:
    - `apps/android/app/src/main/java/com/onyx/android/config/FeatureFlags.kt`
    - `apps/android/app/src/main/java/com/onyx/android/config/FeatureFlagStore.kt`
  - Replace hardcoded constants in:
    - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt`
    - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`
    - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
  - Add debug-only flag controls reachable from `apps/android/app/src/main/java/com/onyx/android/MainActivity.kt` via explicit route/action:
    - New composable route: `DeveloperFlagsScreen`.
    - Entry rule: visible only in debug/internalDebug builds.
    - Navigation trigger: toolbar overflow action "Developer Flags" in debug builds.
  - Persist flag values for restart-stable repro (SharedPreferences/DataStore-backed store).

  **Must NOT do**
  - Do not ship risky renderer behavior without a runtime rollback toggle.

  **Parallelizable**: YES (with 0.3, 0.4)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` - current runtime ink behavior gates.
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt` - current prediction gate point.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` - current stacked-page gate point.
  - `apps/android/app/src/main/java/com/onyx/android/MainActivity.kt` - debug-entry integration point.
  - `docs/architecture/comprehensive-app-change-plan.md` - required high-risk flags.

  **Acceptance Criteria**
  - [ ] Runtime flags visible in debug menu.
  - [ ] Flag state persists across restart.
  - [ ] Rollback to safe path possible without reinstall.
  - [ ] `ENABLE_*` hardcoded constants removed from ink/editor runtime paths.
  - [ ] Canonical flag table committed to `.sisyphus/notepads/feature-flags-catalog.md`.
  - [ ] `bun run android:test` passes after flag refactor.

  **Commit**: YES
  - Message: `feat(android): add runtime feature-flag gates for overhaul paths`

- [ ] 0.3 Instrumentation baseline and SLO report

  **What to do**
  - Instrument frame timing, stroke finalize timing, tile render timing, in-flight tile counts.
  - Emit structured debug logs with stable tags/keys:
    - Tag: `OnyxPerf`
    - Event keys: `frame_ms`, `stroke_finalize_ms`, `tile_render_ms`, `tile_queue_depth`, `jank_percent`.
  - Define p95 computation procedure in report:
    - minimum sample size per journey (>= 300 frames for draw/zoom, >= 100 tile renders for PDF journey)
    - percentile calculation method and script/steps used.
  - Capture baseline metrics for Tier A/B/C devices.
  - Store report in `.sisyphus/notepads/baseline-metrics-android.md`.

  **Must NOT do**
  - Do not claim perf improvements without before/after logs.

  **Parallelizable**: YES (with 0.2, 0.4)

  **References**
  - `apps/android/app/src/test/java/com/onyx/android/ink/perf/FrameBudgetManagerTest.kt` - frame budget validation baseline.
  - `apps/android/app/src/test/java/com/onyx/android/pdf/AsyncPdfPipelineTest.kt` - tile pipeline behavior baseline.
  - `docs/device-blocker.md` - physical-device pending items.

  **Acceptance Criteria**
  - [x] Baseline report committed.
  - [x] SLOs recorded with numeric thresholds.
  - [ ] Report includes raw samples, p50/p95, sample counts, and exact command/journey definitions.

  **Commit**: YES
  - Message: `chore(android): add perf instrumentation and baseline SLO report`

- [ ] 0.4 Test harness uplift (Paparazzi, Maestro, baseline profiles)

  **What to do**
  - Lock execution model so implementers do not guess:
    - Required on every PR: `bun run android:lint`, `bun run android:test`.
    - Required before release cut: instrumentation/device verification (`connectedDebugAndroidTest` + `verify-on-device.sh`).
    - Optional/nightly: heavier emulator/perf journeys.
  - Add screenshot test harness in app module and first golden:
    - `apps/android/app/src/test/java/com/onyx/android/ui/snapshots/` (new)
    - Add Paparazzi dependencies/plugin wiring in `apps/android/app/build.gradle.kts`.
  - Add Maestro flow assets:
    - `apps/android/maestro/flows/editor-smoke.yaml` (new)
      - Must include: app launch, create note, draw stroke, open PDF page, verify toolbar action.
    - Add Maestro install/invocation step in CI job docs/workflow notes.
  - Add baseline-profile/macrobenchmark module and wire in Android settings:
    - `apps/android/settings.gradle.kts` (include module)
    - `apps/android/benchmark/` (new)
      - Must include: startup journey + open-note journey + report output artifact.
    - Add benchmark module Gradle setup and baseline-profile dependencies.
  - Extend CI to execute Android gates in `.github/workflows/ci.yml`:
    - `bun run android:lint`
    - `bun run android:test`
    - Optional separate Android instrumentation workflow (`.github/workflows/android-instrumentation.yml`) for emulator/device-heavy checks.
  - Define one explicit startup/editor-open benchmark journey and persist results under `.sisyphus/notepads/benchmark-baseline.md`.

  **Must NOT do**
  - Do not over-expand tests outside acceptance-critical journeys.

  **Parallelizable**: YES (with 0.2, 0.3)

  **References**
  - `.github/workflows/ci.yml` - current CI baseline to extend.
  - `apps/android/package.json` - Android script entrypoints currently available.
  - `apps/android/DEVICE-TESTING.md` - on-device verification protocol to align with harness outputs.

  **Acceptance Criteria**
  - [ ] Screenshot, E2E, and macrobenchmark jobs run in CI or local reproducibly.
  - [ ] `bun run android:lint` and `bun run android:test` are required CI steps in `.github/workflows/ci.yml`.
  - [x] `apps/android/maestro/flows/editor-smoke.yaml` exists and is runnable with Maestro CLI.
  - [ ] First screenshot golden is checked in and verified by test output.
  - [x] Instrumentation gate command is documented and runnable: `cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest`.
  - [x] CI topology documented: PR-required gates vs optional/nightly instrumentation/perf gates.
  - [ ] Failing golden diff produces actionable artifact output.

  **Commit**: YES
  - Message: `test(android): establish screenshot e2e and macrobenchmark harnesses`

### Phase 1 - Ink Latency, Transform Correctness, Style Fidelity

- [ ] 1.1 Official prediction path hardening

  **What to do**
  - Harden `MotionPredictionAdapter` usage path and fallback behavior.
  - Align implementation to official path: predicted samples must be merged into active in-progress stroke updates rather than separate transient overlay strokes.
  - Replace/remove separate predicted overlay flow in `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt` (`handlePredictedStrokes`) if it causes duplicate/ghost trails.
  - Gate behavior with prediction feature flag.

  **Must NOT do**
  - Do not ship prediction enabled by default until Tier-A device validation passes.
  - Do not keep dual predicted-stroke pipelines (active-stroke + parallel overlay) in production path.

  **Parallelizable**: NO (foundation for 1.2)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/MotionPredictionAdapter.kt` - predictor adapter.
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` - in-progress stroke rendering integration.
  - `docs/context/1-deep-research-report.md` - prediction/handoff remediation guidance.

  **Acceptance Criteria**
  - [ ] Predicted stroke path enabled behind flag.
  - [ ] No duplicate committed stroke after pen-up.
  - [ ] Tier-A stylus capture confirms lower perceived lag.

  **Commit**: YES
  - Message: `fix(ink): harden motion prediction integration with safe fallback`

- [ ] 1.2 Pen-up handoff synchronization correctness

  **What to do**
  - Implement explicit pending-removal handoff state so finished wet stroke remains visible until committed draw is visible.
  - Align removal to frame callback timing.
  - Add stress tests for rapid pen-up/down and undo.

  **Must NOT do**
  - Do not remove finished wet stroke synchronously on pen-up.

  **Parallelizable**: NO (depends on 1.1)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt` - stroke finish events.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt` - committed stroke path.
  - `docs/context/1-deep-research-report.md` - required frame-waited handoff model.

  **Acceptance Criteria**
  - [ ] No visible stroke disappearance in slow-motion capture.
  - [ ] Undo/redo remains consistent after rapid stroke sequences.

  **Commit**: YES
  - Message: `fix(ink): synchronize wet-to-committed handoff on frame boundary`

- [ ] 1.3 Stroke style schema evolution and preset persistence

  **What to do**
  - Ensure `StrokeStyle` and `Brush` carry smoothing and taper fields with safe defaults.
  - Persist and load 3 pen presets and 2 highlighter presets.
  - Add migration and serialization coverage for style defaults.

  **Must NOT do**
  - Do not break rendering of existing stored strokes.

  **Parallelizable**: YES (with 1.5)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt` - persisted style schema.
  - `apps/android/app/src/main/java/com/onyx/android/ink/model/Brush.kt` - UI brush model.
  - `docs/context/deep-research-report.md` - schema evolution requirements.

  **Acceptance Criteria**
  - [ ] Existing notes render without crashes/regressions.
  - [ ] Presets survive app restart.
  - [ ] Unit tests verify default-fill behavior.

  **Commit**: YES
  - Message: `feat(ink): persist smoothing taper and tool presets safely`

- [ ] 1.4 Transform engine stabilization

  **What to do**
  - Move transform updates to frame-aligned path.
  - Preserve focal point through pinch, rotate, and orientation changes.
  - Add rubber-band edge handling and inertial pan stabilization.

  **Must NOT do**
  - Do not process high-frequency transform writes directly on event storms.

  **Parallelizable**: NO

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ink/model/ViewTransform.kt` - transform state model.
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTransformTouch.kt` - gesture transform updates.
  - `docs/context/2-deep-research-report.md` - transform parity remediation.

  **Acceptance Criteria**
  - [ ] No pinch jump/drift in repeated zoom cycles.
  - [ ] Orientation change preserves visible document focal area.

  **Commit**: YES
  - Message: `fix(ink): stabilize transform updates and focal-point behavior`

- [ ] 1.5 Spatial index for selection and large-document hit-testing

  **What to do**
  - Introduce quadtree/grid spatial index for stroke hit-tests and lasso candidate lookup.
  - Keep index updates synchronized with stroke add/remove/undo/redo.
  - Add reproducible stress fixture documentation at `.sisyphus/notepads/spatial-index-stress-fixture.md` (stroke count, page count, gesture script).
  - Benchmark against baseline using fixture-defined dataset and record results.

  **Must NOT do**
  - Do not leave stale index nodes after undo/redo.

  **Parallelizable**: YES (with 1.3)

  **References**
  - `docs/context/1-deep-research-report.md` - spatial indexing recommendation.
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt` - hit-testing interaction points.

  **Acceptance Criteria**
  - [ ] Lasso candidate retrieval scales sub-linearly on stress fixture.
  - [ ] Selection correctness parity with baseline tests.
  - [ ] `bun run android:test` passes including `StrokeTileCacheTest` and `EraserGeometryTest`.
  - [ ] Benchmark results logged in `.sisyphus/notepads/spatial-index-benchmark.md`.

  **Commit**: YES
  - Message: `perf(ink): add spatial index for selection and hit-testing`

### Phase 2 - PDF Engine Hardening and Rendering Performance

#### Pdfium Fork Lock (Decided)

- Repository: `https://github.com/Zoltaneusz/PdfiumAndroid.git`
- Maven coordinate (JitPack): `com.github.Zoltaneusz:PdfiumAndroid:v1.10.0`
- Git tag: `v1.10.0`
- Source authority: `.sisyphus/plans/milestone-canvas-rendering-overhaul.md` (sections `P0.0`, `P0.3`, `P2.1`)
- Policy: do not switch forks silently; if resolution fails, escalate and mirror/vendor the same locked fork.

#### Current PDF Baseline vs Required Delta

- Already present in repo:
  - Pdfium dependency in `apps/android/app/build.gradle.kts`.
  - Tile/cache/async pipeline in `apps/android/app/src/main/java/com/onyx/android/pdf/`.
  - Native text geometry bridge in `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumNativeTextBridge.kt`.
- This phase is therefore a hardening and architecture-delta phase, not first-time migration.
- Required delta in this plan:
  - Extract renderer adapter boundary so editor UI depends on interfaces, not concrete Pdfium wrapper classes.
  - Close concurrency/race gaps under cancellation/eviction pressure.
  - Enforce measurable visual continuity and queue-bounding behavior.
  - Verify dependency/ABI/CI guarantees continuously.

- [ ] 2.1 Pdfium integration lock and adapter layer

  **What to do**
  - Enforce locked fork details from prior milestone: `Zoltaneusz/PdfiumAndroid` / `com.github.Zoltaneusz:PdfiumAndroid:v1.10.0` / tag `v1.10.0`.
  - Introduce explicit adapter boundary (`PdfRenderEngine` / `PdfTextEngine`) in `apps/android/app/src/main/java/com/onyx/android/pdf/` and route editor UI through it.
  - Refactor integration callers in:
    - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt`
    - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`
  - Confirm ABI packaging, Android 15 page-size compatibility, and artifact resolvability from JitPack.
  - Add dependency audit check to ensure MuPDF does not re-enter transitive graph.

  **Must NOT do**
  - Do not couple UI directly to native Pdfium wrappers.

  **Parallelizable**: NO

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumRenderer.kt` - current renderer contract.
  - `docs/architecture/comprehensive-app-change-plan.md` - migration requirement and quality gates.
  - `docs/context/1-deep-research-report.md` - tile and lifecycle priorities.
  - `.sisyphus/plans/milestone-canvas-rendering-overhaul.md` - locked fork decision and viability checks.

  **Acceptance Criteria**
  - [ ] Pdf engine source pinned to locked fork and documented.
  - [ ] Adapter abstraction allows renderer swap in tests.
  - [ ] Native ABI smoke test passes on Tier-A/Tier-B targets.
  - [ ] Coordinate `com.github.Zoltaneusz:PdfiumAndroid:v1.10.0` resolves in CI.
  - [ ] `cd apps/android && node ../../scripts/gradlew.js :app:dependencies` confirms Pdfium coordinate present and no MuPDF/artifex dependencies.
  - [ ] `bun run android:test` passes including `PdfiumDocumentSessionTest` and `PdfiumNativeTextBridgeTest`.

  **Commit**: YES
  - Message: `refactor(pdf): isolate pdfium behind renderer adapter boundary`

- [ ] 2.2 Tile scheduler and viewport-driven frame-aligned requests

  **What to do**
  - Keep request source in `NoteEditorShared.kt`, but move trigger semantics to frame-aligned cadence (not raw gesture event storms).
  - Harden bounded request queue behavior in `AsyncPdfPipeline.kt` with explicit stale cancellation invariants.
  - Keep prefetch radius explicit and configurable.
  - Add metrics counters for queue depth, stale-cancel count, and tile-visible latency.

  **Must NOT do**
  - Do not allow unbounded in-flight requests during flings.

  **Parallelizable**: NO (depends on 2.1)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/pdf/AsyncPdfPipeline.kt` - async scheduling and cancellation.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt` - request trigger and scale bucket interplay.
  - `docs/context/1-deep-research-report.md` - frame-aligned debouncing guidance.

  **Acceptance Criteria**
  - [ ] In-flight queue remains bounded in stress scroll.
  - [ ] p95 tile-visible latency meets target.
  - [x] `bun run android:test` passes including `AsyncPdfPipelineTest` and `PdfTileRendererMathTest`.
  - [x] Perf logs captured in `.sisyphus/notepads/pdf-scheduler-baseline.md` with before/after comparison.

  **Commit**: YES
  - Message: `perf(pdf): frame-align tile scheduling and bound request churn`

- [ ] 2.3 Cache lifecycle race hardening

  **What to do**
  - Guard LRU access/recycle paths with mutex-safe lifecycle handling.
  - Validate draw paths check bitmap validity before draw.
  - Add concurrency test scenarios for eviction + cancellation overlap.

  **Must NOT do**
  - Do not recycle bitmaps that may still be referenced for draw.

  **Parallelizable**: YES (with 2.4)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileCache.kt` - LRU and recycle behavior.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt` - draw-time guards.
  - `docs/context/1-deep-research-report.md` - bitmap race issue notes.

  **Acceptance Criteria**
  - [ ] No recycle/draw crash in stress tests.
  - [ ] Memory remains inside budget under repeated open/close/zoom.
  - [x] `bun run android:test` passes including `PdfTileCacheTest`.
  - [ ] `cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest --tests "*PdfiumIntegrationSmokeTest"` passes on at least one physical device.

  **Commit**: YES
  - Message: `fix(pdf): harden tile cache lifecycle against recycle races`

- [ ] 2.4 Visual continuity and scale bucket policy

  **What to do**
  - Enforce 1x/2x/4x bucket policy with hysteresis.
  - Keep previous content visible until replacement tile is ready.
  - Apply controlled crossfade to avoid flash/blank transitions.

  **Must NOT do**
  - Do not show black or blank frame on normal zoom transitions.

  **Parallelizable**: YES (with 2.3)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt` - tile compositing behavior.
  - `docs/context/2-deep-research-report.md` - continuity/parity requirements.

  **Acceptance Criteria**
  - [ ] No black/blank transition in navigation journey.
  - [ ] Zoom-in does not remain blurry beyond one settle window.
  - [x] `apps/android/app/src/androidTest/java/com/onyx/android/ui/` contains an instrumentation assertion for bucket crossfade continuity.

  **Commit**: YES
  - Message: `feat(pdf): add stable bucket hysteresis and continuity crossfade`

- [ ] 2.5 PDF interaction parity (text selection and navigation)

  **What to do**
  - Add page-jump input and thumbnail strip navigation.
  - Improve text selection quality and clipboard interaction.
  - Validate interaction coexistence with drawing and panning modes.

  **Must NOT do**
  - Do not degrade draw-mode gesture routing while adding selection handles.

  **Parallelizable**: NO (after 2.2 stable)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt` - PDF interaction layer.
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumNativeTextBridge.kt` - char-box extraction and rotation normalization path.
  - `apps/android/app/src/main/java/com/onyx/android/pdf/TextSelectionModel.kt` - selection geometry model.
  - `docs/architecture/comprehensive-app-change-plan.md` - required missing PDF UX primitives.

  **Acceptance Criteria**
  - [ ] Page jump and thumbnail jump work on large PDFs.
  - [ ] Text selection handles and clipboard copy succeed reliably.
  - [ ] `cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest --tests "*PdfiumRendererTextExtractionTest"` passes.

  **Commit**: YES
  - Message: `feat(pdf): ship page navigation and robust text selection flows`

### Phase 3 - UI Architecture and Editor Usability

- [ ] 3.1 Decompose `NoteEditorUi.kt` and define UI boundaries

  **What to do**
  - Split editor UI into dedicated components (`EditorToolbar`, `ToolSettingsPanel`, `ColorPickerDialog`, `EditorScaffold`).
  - Keep state hoisting predictable and testable.
  - Preserve behavior parity while reducing file complexity.

  **Must NOT do**
  - Do not mix business logic back into UI composables.

  **Parallelizable**: NO

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt` - decomposition target.
  - `docs/context/deep-research-report.md` - architecture debt callout.

  **Acceptance Criteria**
  - [ ] Component boundaries implemented and documented.
  - [ ] Existing editor behavior remains unchanged in regression suite.
  - [ ] `bun run android:test` passes including `NoteEditorViewModelTest` and `NoteEditorTransformMathTest`.
  - [ ] `cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest --tests "*NoteEditorToolbarTest"` passes.

  **Commit**: YES
  - Message: `refactor(ui): split note editor ui into focused composable modules`

- [ ] 3.2 Responsive toolbar, touch target compliance, and interaction polish

  **What to do**
  - Implement compact/expanded toolbar layouts with overflow handling.
  - Ensure 48x48dp touch target minimum for primary controls.
  - Add haptic feedback on key tool toggles where appropriate.

  **Must NOT do**
  - Do not hide primary draw/erase/color/undo controls in compact mode.

  **Parallelizable**: YES (with 3.3, 3.5)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt` - toolbar behavior.
  - `docs/context/2-deep-research-report.md` - touch target and ergonomics requirements.

  **Acceptance Criteria**
  - [ ] All critical controls reachable in compact mode.
  - [ ] Accessibility scan confirms touch target thresholds.
  - [ ] `cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest --tests "*NoteEditorTopBarTest" --tests "*NoteEditorToolbarTest"` passes.

  **Commit**: YES
  - Message: `feat(ui): responsive toolbar with accessible touch targets`

- [ ] 3.3 Home screen architecture cleanup and ViewModel extraction

  **What to do**
  - Extract inline Home screen state creation into dedicated ViewModel.
  - Normalize UI state loading/error/empty flows.
  - Align navigation and filtering behavior with folder and search model.

  **Must NOT do**
  - Do not duplicate repository logic in composables.

  **Parallelizable**: YES (with 3.2, 3.4)

  **References**
  - `docs/context/deep-research-report.md` - Home screen state management debt.
  - `docs/architecture/system-overview.md` - client architecture boundaries.

  **Acceptance Criteria**
  - [ ] Home UI state is ViewModel-driven.
  - [ ] Error/retry behavior covered by UI tests.
  - [ ] `cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest --tests "*HomeNotesListTest"` passes.

  **Commit**: YES
  - Message: `refactor(home): extract home screen state into viewmodel`

- [ ] 3.4 DI migration to Hilt boundaries

  **What to do**
  - Add Hilt bootstrap prerequisites in `apps/android/app/build.gradle.kts`:
    - Hilt Gradle plugin and annotation processing wiring.
    - Hilt runtime/compiler dependencies.
  - Replace current AppContainer wiring with Hilt across concrete entry points:
    - `apps/android/app/src/main/java/com/onyx/android/AppContainer.kt`
    - `apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt`
    - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` (`requireAppContainer()` usage)
    - `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt` (`requireAppContainer()` usage)
  - Convert ViewModel factory wiring to Hilt (`@HiltViewModel`) and remove manual `ViewModelProvider.Factory` use where present.
  - Add Hilt modules for database/repository/device identity/pdf password store/myscript engine providers.
  - Convert application bootstrap to `@HiltAndroidApp` and move manual dependency graph construction out of `OnyxApplication`.
  - Update Android tests to use Hilt test rule where needed for ViewModel/repository injection.
  - Keep startup ordering deterministic and preserve lazy MyScript initialization semantics.

  **Must NOT do**
  - Do not perform broad modularization beyond DI stabilization.

  **Parallelizable**: YES (with 3.3)

  **References**
  - `docs/context/deep-research-report.md` - Hilt migration recommendation.
  - `apps/android/app/build.gradle.kts` - plugin/dependency integration point.
  - `apps/android/app/src/main/java/com/onyx/android/AppContainer.kt` - current DI abstraction.
  - `apps/android/app/src/main/java/com/onyx/android/OnyxApplication.kt` - current manual composition root.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` - manual dependency retrieval path.
  - `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt` - manual dependency retrieval path.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt` - factory pattern to replace with Hilt.

  **Acceptance Criteria**
  - [ ] No remaining runtime-critical `AppContainer` wiring in app flow.
  - [ ] DI graph resolves on cold start and editor open path.
  - [ ] `requireAppContainer()` call sites removed from primary UI screens.
  - [ ] `OnyxApplication` annotated/configured for Hilt bootstrap and app launches.
  - [ ] Manual `ViewModelProvider.Factory` wiring removed for migrated screens.
  - [ ] `bun run android:test` and `bun run android:lint` pass after DI migration.

  **Commit**: YES
  - Message: `refactor(di): migrate android app wiring to hilt modules`

- [ ] 3.5 Startup polish via SplashScreen path

  **What to do**
  - Adopt SplashScreen startup path for predictable cold start UX.
  - Ensure startup handoff to first screen has no blank window.
  - Measure startup time before/after baseline profile tuning.

  **Must NOT do**
  - Do not add blocking startup work to UI thread.

  **Parallelizable**: YES (with 3.2)

  **References**
  - `docs/context/2-deep-research-report.md` - startup polish recommendation.
  - `apps/android/settings.gradle.kts` - benchmark module integration point.
  - `apps/android/app/build.gradle.kts` - startup/perf instrumentation integration point.

  **Acceptance Criteria**
  - [ ] Cold start path visibly stable and instrumented.
  - [ ] Startup benchmark shows non-regressing trend.

  **Commit**: YES
  - Message: `feat(android): integrate splash startup path and startup metrics`

### Phase 4 - Library Organization, Templates, Preferences

- [ ] 4.1 Folder and template data model hardening

  **What to do**
  - Finalize folder model and note-folder relationship constraints.
  - Add/verify migrations and referential integrity for organization entities.
  - Include template metadata model required for page background presets.

  **Must NOT do**
  - Do not rely on destructive migrations.

  **Parallelizable**: NO

  **References**
  - `.sisyphus/plans/milestone-ui-overhaul-samsung-notes.md` - folder/template functional target.
  - `docs/schema-audit.md` - compatibility expectations and schema quality.

  **Acceptance Criteria**
  - [ ] Migration tests pass for organization schema updates.
  - [ ] CRUD operations preserve referential integrity.

  **Commit**: YES
  - Message: `feat(data): harden folder template schema with tested migrations`

- [ ] 4.2 Thumbnail generation and batch operations

  **What to do**
  - Build deterministic thumbnail generation/update flow.
  - Add multi-select batch move/delete/tag operations.
  - Ensure scrolling and batch actions remain performant on large lists.

  **Must NOT do**
  - Do not block main thread on thumbnail generation.

  **Parallelizable**: YES (with 4.3)

  **References**
  - `.sisyphus/plans/milestone-ui-overhaul-samsung-notes.md` - library UX targets.
  - `docs/context/deep-research-report.md` - organizational workflow gaps.

  **Acceptance Criteria**
  - [ ] Thumbnails are visible and updated after edits.
  - [ ] Batch operations complete correctly for multi-selection.

  **Commit**: YES
  - Message: `feat(library): add thumbnail pipeline and batch organization actions`

- [ ] 4.3 Room-backed settings migration

  **What to do**
  - Persist editor settings that are currently ephemeral/in-memory in UI state:
    - Brush/tool selection and widths from `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` (`rememberBrushState`).
    - Relevant editor panel/default settings from `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt`.
  - Add Room-backed settings table + DAO under `apps/android/app/src/main/java/com/onyx/android/data/entity/` and `.../dao/`.
  - Wire read/write points through `NoteRepository` or dedicated settings repository.
  - Keep startup restore idempotent.
  - Keep `DeviceIdentity` SharedPreferences (`apps/android/app/src/main/java/com/onyx/android/device/DeviceIdentity.kt`) unchanged in this task; that is a separate concern.

  **Must NOT do**
  - Do not claim a SharedPreferences migration for tool prefs unless a concrete legacy source exists.
  - Do not lose user settings during restore/persist cycles.

  **Parallelizable**: YES (with 4.2)

  **References**
  - `.sisyphus/plans/milestone-ui-overhaul-samsung-notes.md` - settings persistence requirements.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` - current in-memory brush/tool state.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt` - settings-related UI controls.
  - `apps/android/app/src/main/java/com/onyx/android/device/DeviceIdentity.kt` - existing SharedPreferences usage (out of scope for tool-pref migration).

  **Acceptance Criteria**
  - [ ] Editor tool settings persist across app restart for note editor sessions.
  - [ ] New settings write/read through Room path.
  - [ ] `bun run android:test` passes including `NoteEditorViewModelTest`.
  - [ ] `cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest --tests "*OnyxDatabaseMigrationTest"` passes.

  **Commit**: YES
  - Message: `refactor(settings): move editor preferences from sharedprefs to room`

### Phase 5 - Recognition, Conversion, and Unified Search

- [ ] 5.1 MyScript pipeline hardening

  **What to do**
  - Add debounced recognition scheduling and content package lifecycle cleanup.
  - Support configurable recognition language settings.
  - Ensure failures degrade gracefully without editor lockup.

  **Must NOT do**
  - Do not block inking/render loop on recognition pipeline work.

  **Parallelizable**: YES (with 5.2)

  **References**
  - `docs/context/deep-research-report.md` - MyScript reliability recommendations.
  - `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptEngine.kt` - recognition engine lifecycle.
  - `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt` - per-page recognition orchestration.
  - `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt` - recognition persistence/update entry points.

  **Acceptance Criteria**
  - [ ] Recognition scheduling is debounced and observable.
  - [ ] Recovery path exists for recognition session failures.
  - [ ] `bun run android:test` passes including `MyScriptCoordinateTest`.

  **Commit**: YES
  - Message: `fix(recognition): harden myscript scheduling and lifecycle cleanup`

- [ ] 5.2 Unified search index and query surface

  **What to do**
  - Build one query surface combining handwriting recognition text, PDF text, and metadata.
  - Add source-type labels and geometry-aware result navigation.
  - Add index rebuild path for corrupted/outdated index states.
  - Define unified result data model fields: `sourceType`, `noteId`, `pageId`, `snippet`, `score`, `geometry`.
  - Wire current search path from `NoteRepository.searchNotes(...)` into unified mixed-source query orchestration.

  **Must NOT do**
  - Do not ship separate disconnected search entry points.

  **Parallelizable**: YES (with 5.1)

  **References**
  - `docs/architecture/comprehensive-app-change-plan.md` - unified index requirement.
  - `docs/context/deep-research-report.md` - search UX gaps.
  - `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt` - current recognition search path.
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumNativeTextBridge.kt` - PDF text geometry source.
  - `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt` - current search UI integration point.

  **Acceptance Criteria**
  - [ ] Single search bar returns mixed-source results.
  - [ ] Tapping result navigates to correct location.
  - [ ] Automated verification exists for each source type:
    - recognition text match
    - PDF text match
    - metadata/title match
  - [ ] `bun run android:test` passes with unified-search tests.

  **Commit**: YES
  - Message: `feat(search): unify handwriting pdf and metadata search results`

- [ ] 5.3 Conversion and recognition overlay controls

  **What to do**
  - Add recognition overlay toggles with clear visibility controls.
  - Add selection/lasso to text conversion flow with editable output.
  - Add scratch-out gesture support if available via recognition SDK path.

  **Must NOT do**
  - Do not enable overlay by default if it harms readability.

  **Parallelizable**: NO (depends on 5.1, 5.2)

  **References**
  - `.sisyphus/plans/milestone-av2-advanced-features.md` - conversion and advanced interaction context.
  - `docs/context/deep-research-report.md` - JIIX and overlay recommendations.

  **Acceptance Criteria**
  - [ ] Overlay is toggleable and does not misalign on zoom/pan.
  - [ ] Conversion output is editable and persistable.

  **Commit**: YES
  - Message: `feat(recognition): add overlay and conversion workflows`

### Phase 6 - Reliability, Release Safety, and Rollout

- [ ] 6.1 Data safety and sync-readiness primitives

  **What to do**
  - Harden lamport monotonic seeding where strokes/pages are created and persisted:
    - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasStroke.kt`
    - `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt`
    - `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt`
    - `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt`
    - `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt`
  - Add operation log scaffold for future sync integration:
    - `apps/android/app/src/main/java/com/onyx/android/data/entity/OperationLogEntity.kt` (new)
    - `apps/android/app/src/main/java/com/onyx/android/data/dao/OperationLogDao.kt` (new)
    - `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt` (entity/dao + migration wiring)
  - Validate deterministic resource cleanup on document close and app shutdown.

  **Must NOT do**
  - Do not implement full sync behavior in this scope.

  **Parallelizable**: YES (with 6.2)

  **References**
  - `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt` - current stroke/page persistence path.
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt` - createdLamport persistence field.
  - `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt` - contentLamportMax field.
  - `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt` - schema/migration integration point.
  - `apps/android/app/src/test/java/com/onyx/android/device/DeviceIdentityTest.kt` - device identity persistence baseline.
  - `docs/context/1-deep-research-report.md` - lamport hardening reminder.

  **Acceptance Criteria**
  - [ ] Lamport values are monotonic across restarts.
  - [ ] Operation log scaffold compiles and is test-covered.
  - [ ] `bun run android:test` passes with new lamport/oplog tests added.

  **Commit**: YES
  - Message: `chore(data): harden lamport seeding and add operation-log scaffold`

- [ ] 6.2 CI gates, regression enforcement, and perf budgets

  **What to do**
  - Enforce lint/test/static checks and screenshot/macrobenchmark/E2E gates.
  - Publish pass/fail thresholds for jank, latency, memory, and crash-free runs.
  - Ensure env hygiene follows turbo policy (`env`/`globalEnv`, `.env*` in inputs).
  - Update `.github/workflows/ci.yml` to run Android quality gates (currently missing):
    - `bun run android:lint`
    - `bun run android:test`
  - Update `turbo.json` so relevant tasks hash `.env*` and document env policy changes explicitly.

  **Must NOT do**
  - Do not weaken gates to pass unstable changes.

  **Parallelizable**: YES (with 6.1)

  **References**
  - `AGENTS.md` - quality and env policy requirements.
  - `.github/workflows/ci.yml` - actual workflow to modify.
  - `package.json` - canonical repo command entrypoints.
  - `turbo.json` - cache/env hashing behavior.

  **Acceptance Criteria**
  - [ ] Required CI gates are green.
  - [ ] Perf budget failures are surfaced as actionable test output.
  - [ ] PR CI fails if `bun run android:lint` or `bun run android:test` fails.
  - [ ] `turbo.json` includes `.env*` hashing for relevant tasks affected by env.

  **Commit**: YES
  - Message: `ci(android): enforce overhaul quality gates and perf budgets`

- [ ] 6.3 Physical-device blocker closure and rollout

  **What to do**
  - Execute blocked physical-device verification tasks and collect evidence.
  - Use feature flags for staged rollout and fallback.
  - Create go/no-go release checklist for editor/PDF/library/search quality bars.
  - Run scripted device verification flow from `apps/android/verify-on-device.sh` and capture output artifacts.

  **Must NOT do**
  - Do not mark overhaul complete without physical-device evidence for gated items.

  **Parallelizable**: NO (final gate)

  **References**
  - `docs/device-blocker.md` - required blocked verification list.
  - `apps/android/DEVICE-TESTING.md` - executable on-device validation protocol.
  - `apps/android/verify-on-device.sh` - scripted verification entrypoint.
  - `docs/architecture/full-project-analysis.md` - release-priority risk list.

  **Acceptance Criteria**
  - [ ] All blocker tasks closed or explicitly risk-accepted.
  - [ ] Rollout checklist signed with feature-flag fallback path.
  - [ ] `cd apps/android && ./verify-on-device.sh` completes and evidence is archived under `.sisyphus/notepads/device-validation/`.

  **Commit**: YES
  - Message: `docs(release): close device blockers and finalize rollout gates`

### Phase 7 - Advanced Competitive Features (Post-Core Stability)

- [ ] 7.1 Segment eraser

  **What to do**
  - Implement stroke splitting with undo/redo integrity.
  - Ensure style continuity across split stroke fragments.

  **Must NOT do**
  - Do not destabilize core eraser path for default users.

  **Parallelizable**: YES (with 7.2)

  **References**
  - `.sisyphus/plans/milestone-av2-advanced-features.md` - prior split-eraser direction.

  **Acceptance Criteria**
  - [ ] Mid-stroke erase yields deterministic fragment behavior.
  - [ ] Undo/redo remains lossless.

  **Commit**: YES
  - Message: `feat(ink): add segment eraser with split-stroke undo safety`

- [ ] 7.2 Lasso transform tooling

  **What to do**
  - Add lasso select, move, and resize transforms.
  - Integrate with spatial index and undo stack.

  **Must NOT do**
  - Do not bypass index/update synchronization rules.

  **Parallelizable**: YES (with 7.1)

  **References**
  - `.sisyphus/plans/milestone-av2-advanced-features.md` - lasso interaction baseline.

  **Acceptance Criteria**
  - [ ] Lasso selection remains responsive on large documents.
  - [ ] Transform operations are fully undoable.

  **Commit**: YES
  - Message: `feat(ink): add lasso move and resize transform tooling`

- [ ] 7.3 Template system polish

  **What to do**
  - Finalize grid/lined/dotted templates with user-adjustable density.
  - Ensure rendering parity across zoom and export paths.

  **Must NOT do**
  - Do not regress PDF/ink compositing layers.

  **Parallelizable**: YES

  **References**
  - `.sisyphus/plans/milestone-ui-overhaul-samsung-notes.md` - template and stylus preferences vision.

  **Acceptance Criteria**
  - [ ] Template visuals remain stable through zoom/pan.
  - [ ] Template selection persists and survives reopen.

  **Commit**: YES
  - Message: `feat(editor): ship configurable page template backgrounds`

## Commit Strategy

| Stage                    | Commit Cadence                    | Rule                                            |
| ------------------------ | --------------------------------- | ----------------------------------------------- |
| Core rendering changes   | Small atomic commits per task     | Each commit includes tests for touched behavior |
| Schema/migration updates | One commit per migration boundary | Migration tests required in same commit         |
| UI restructuring         | Progressive commits by component  | Keep behavior parity and snapshot diffs         |
| Release gating           | Dedicated CI/docs commit          | No mixed feature code in release-gate commit    |

## Success Criteria and Exit Checklist

### Mandatory Commands

```bash
bun run android:lint
bun run android:test
bun run ktlint
bun run detekt
```

### Final Checklist

- [ ] Ink handoff artifact-free on Tier-A stylus device.
- [ ] PDF navigation and zoom transitions are stable with no blank/black flashes.
- [ ] Library organization and search flows are complete and discoverable.
- [ ] Migration and data-safety checks are green.
- [ ] CI gates are green and perf budgets pass.
- [ ] Device-blocked items are resolved with evidence.

## Risk Register and Mitigations

- **Risk**: Pdfium integration drift or ABI/device incompatibility.
  - **Mitigation**: pin source/commit, smoke-test ABI matrix, retain feature-flag fallback.
- **Risk**: Performance regressions hidden by functional pass.
  - **Mitigation**: enforce perf thresholds and baseline comparisons in CI.
- **Risk**: Scope creep from UI parity ambitions.
  - **Mitigation**: keep explicit non-goals and phase gates.
- **Risk**: Device-only bugs missed in emulator.
  - **Mitigation**: mandatory physical-device evidence for gated tasks.

## Defaults Applied

- Default rollout model: staged enablement with feature flags, not big-bang release.
- Default device tiers: stylus tablet + touch phone + low-RAM emulator.
- Default Pdfium handling: locked fork (`Zoltaneusz/PdfiumAndroid:v1.10.0`) plus adapter isolation and ABI validation.

## Decisions Needed (if overriding defaults)

- If you want different device tiers or stricter perf budgets, provide target numbers.
