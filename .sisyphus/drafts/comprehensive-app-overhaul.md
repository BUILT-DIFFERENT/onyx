# Draft: Comprehensive App Overhaul

## Requirements (confirmed)

- Create a single comprehensive overhaul plan at `.sisyphus/plans/comprehensive-app-overhaul.md`.
- Synthesize all documentation, architecture docs, deep research reports, context files, and prior milestone plans into one authoritative plan.
- Follow `docs/architecture/comprehensive-app-change-plan.md` as sequencing authority.
- Scope primarily Android app overhaul (ink, PDF, UI, architecture, testing).
- Explicitly exclude web viewer, collaboration/sync, and backend/Convex implementation work.
- Backward compatibility with older local Android DB versions is NOT required (per current policy).
- Include risk mitigations, explicit non-goals, and measurable acceptance criteria to satisfy strict plan review.

## Technical Decisions

- Ink prediction: integrate official MotionEventPredictor path and explicit pen-up handoff synchronization.
- Stroke style schema: persist smoothing + endTaper with backward-compatible defaults.
- PDF: migrate away from MuPDF (AGPL) to a PdfiumAndroid-based stack; use tiled rendering (512x512) and 3 scale buckets (1x/2x/4x).
- Pdfium fork lock confirmed from milestone source: `Zoltaneusz/PdfiumAndroid` (`com.github.Zoltaneusz:PdfiumAndroid:v1.10.0`, tag `v1.10.0`).
- Memory budget target: 25-35% of memoryClass for bitmap caches.
- MyScript: offscreen architecture (not EditorView) with debounced recognition and content cleanup.
- Lamport clock: harden to monotonic persistence.
- UI: responsive toolbar, touch targets >= 48x48dp; decomposition of `NoteEditorUi.kt`.
- Testing: hybrid strategy (TDD for logic/data; manual verification for device-dependent behavior) with additional UI/E2E coverage (Paparazzi, Maestro, Compose UI tests, baseline profiles).

## Research Findings

- Ink pipeline files and patterns:
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/MotionPredictionAdapter.kt` uses reflection to `android.view.MotionEventPredictor` and `androidx.input.motionprediction.MotionEventPredictor`.
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` wraps `InProgressStrokesView` and uses frame-synced handoff.
  - `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt` defines `StrokeStyle` with `smoothingLevel` and `endTaperStrength`.
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasDrawing.kt` implements Catmull-Rom smoothing and tapering.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt` hosts tool settings and brush sliders.
- PDF pipeline files and patterns:
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileCache.kt` LRU cache with mutex and recycle-on-evict.
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileRenderer.kt` single-threaded tile renderer with cancellation cleanup.
  - `apps/android/app/src/main/java/com/onyx/android/pdf/AsyncPdfPipeline.kt` scheduling, semaphore concurrency, cancellation recycle.
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumRenderer.kt` bitmap caches for page and thumbnails.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt` draw-time safety checks + crossfade.
- Found occurrences of `InProgressStrokesView` in ink touch and transform handlers and tests.
- Librarian: `Zoltaneusz/PdfiumAndroid` repository not found. Main ecosystem repos are `barteksc/PdfiumAndroid` (Apache 2.0) and forks (tibbi, oothp, johngray1965) with varying ABI/Android 15 16KB support.
- Context7 queries did not surface MotionEventPredictor or SplashScreen; fallback is in-repo adapter and AndroidX input motionprediction package.

## Momus Review Findings (Round 1)

- Verdict: REJECT (needed stronger repo-anchored execution details).
- Critical gaps identified by Momus:
  - Feature-flag architecture not concrete enough.
  - Test harness and CI gate tasks referenced placeholder docs instead of concrete repo files.
  - Phase 2 treated Pdfium as migration despite existing Pdfium/tile/native-text pipeline already present.
  - Task verification lacked concrete commands/artifacts for several high-risk tasks.
- Remediations applied to plan:
  - Added "Current Repository Baseline (Verified)" section to avoid re-migrating already-complete work.
  - Expanded Task 0.2 with explicit file anchors (`FeatureFlags.kt`, `FeatureFlagStore.kt`, replacement of hardcoded flags in ink/editor files).
  - Expanded Task 0.4 with concrete harness paths (`apps/android/maestro/flows/editor-smoke.yaml`, `apps/android/benchmark/`, CI updates in `.github/workflows/ci.yml`).
  - Reframed Phase 2 as hardening/delta from current implementation and added exact tests/commands.
  - Added specific CI/env anchors (`.github/workflows/ci.yml`, `package.json`, `turbo.json`) and on-device script gate (`apps/android/verify-on-device.sh`).

## Momus Review Findings (Round 2)

- Verdict: REJECT (remaining ambiguity in cross-cutting execution details).
- Additional gaps identified by Momus:
  - 0.4 test harness still needed firmer execution model and expected artifacts.
  - 4.3 settings persistence assumed SharedPreferences source, but tool settings are currently in-memory Compose state.
  - 3.4 Hilt migration lacked concrete AppContainer conversion anchors.
  - 6.1 sync-readiness lacked concrete target files/schema hooks for lamport/oplog scaffolding.
  - 1.1 prediction task needed explicit policy on replacing separate predicted overlay flow.
- Remediations applied to plan:
  - 0.4 now defines PR-vs-release-vs-nightly execution model and concrete artifact expectations.
  - 4.3 now targets in-memory editor state in `NoteEditorScreen.kt` / `NoteEditorUi.kt`, with explicit DeviceIdentity SharedPreferences exclusion.
  - 3.4 now anchors migration to `AppContainer.kt`, `OnyxApplication.kt`, and `requireAppContainer()` call sites.
  - 6.1 now anchors lamport/oplog changes to concrete entity/repository/database files and new scaffold files.
  - 1.1 now explicitly requires replacing/removing separate predicted overlay path if it causes ghosting/duplication.

## Momus Review Findings (Round 3)

- Verdict: REJECT (final remaining execution gaps).
- Additional gaps identified by Momus:
  - Needed concrete bootstrap steps for Hilt migration (Gradle/plugin and ViewModel conversion approach).
  - Needed concrete setup/wiring details for Paparazzi + Maestro + macrobenchmark in 0.4.
  - Needed canonical feature-flag list with defaults and safe-path mapping.
  - Needed concrete recognition/search code anchors and per-source automated acceptance checks.
  - Needed reproducible perf metric emission/collection protocol (log format and p95 method).
- Remediations applied to plan:
  - Added canonical flag catalog, debug route gating, and safe-path mappings in 0.2.
  - Added structured perf event schema and percentile computation requirements in 0.3.
  - Added explicit harness wiring expectations (Gradle/plugin/dependencies, CI topology) in 0.4.
  - Added Hilt bootstrap checklist and concrete migration anchors in 3.4.
  - Added recognition/search anchors and per-source automated verification requirements in 5.1/5.2.

## Open Questions

- Device matrix and performance targets (min SDK, stylus hardware, FPS/latency/jank thresholds).
- PDF feature scope: encrypted PDFs, link handling, text selection/search, annotation editing parity.
- UI source-of-truth: Samsung Notes parity vs current navigation style; minimum lovable screen inventory.
- Release strategy: staged rollout with feature flags vs big-bang.
- Observability requirements (crash reporting, performance regression thresholds, memory/jank gates).

## Scope Boundaries

- INCLUDE: Android editor (ink, PDF, transforms, UI), data model/persistence, testing/CI, performance baselines.
- EXCLUDE: web viewer, collaboration/sync, Convex backend changes, OEM-only SDK features.

## Test Strategy Decision

- Infrastructure exists: YES (JUnit, Espresso, Room test helpers, Compose UI tests present).
- Proposed approach: Hybrid (TDD for logic/data; manual device verification for stylus/perf), with Paparazzi + Maestro + baseline profiles for regression gates.
