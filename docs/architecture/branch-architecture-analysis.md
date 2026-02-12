# Branch Architecture Analysis and Foundation Plan

Date: 2026-02-12  
Scope: repository-wide review focused on Android authoring foundation, plans in `.sisyphus/plans/`, and remediation notes in `.sisyphus/notepads/android-architecture-review.md`.

## 1) Executive Assessment

This branch has made meaningful progress on the Android foundation (ViewModel extraction, persistence/error handling hardening, protobuf stroke point serialization, and some PDF cache work), but the repo-level architecture is still uneven:

- Android is significantly ahead of web/Convex implementation.
- Collaboration/sync/sharing are still mostly design-level scaffolds.
- UI architecture and rendering internals still need one more structural pass to safely support “amazing drawing feel” and large document workloads.

**Bottom line:** the branch is directionally correct for Milestone A, but a robust platform for collaboration/sync and high-scale PDF annotation requires a dedicated “Foundation Hardening” milestone before feature acceleration.

## 2) Plan Alignment Review (`.sisyphus/plans`)

### Strong alignment (already moving in the right direction)
- Android offline-first editor core is implemented and test-backed.
- MyScript certificate + bundled recognition assets path is implemented.
- Remediation PR2/PR3 themes (title editing, read-only routing, retry/error handling, some performance work) are present.

### Partial alignment / gap areas
- Collaboration model from `V1-Real-Time-Collaboration.md` is not implemented end-to-end yet.
- Convex schema/functions are still placeholder-level in this branch.
- Web viewer remains very early; production-grade PDF/ink rendering path and live update flow are not yet in place.

## 3) Architecture Review Criticism Closure Check

Based on the review file and branch state, several major criticisms appear addressed (at least partially), while some remain open.

### Addressed or largely addressed
- ViewModel extracted from UI monolith into dedicated file.
- Error state channeling and retry logging for stroke queue improved.
- Title editing and page counter have landed.
- Dead top-bar controls were reduced/reworked.
- Read-only input routing improved to preserve non-edit interactions.
- Stroke point serialization moved toward protobuf.
- PDF renderer/text caching work introduced.

### Still open / still risky
- `NoteEditorUi.kt` remains a very large monolith and should be split by feature domain.
- Stabilization/smoothing semantics are still not clearly mapped to true stroke stabilization behavior.
- Large-PDF strategy is not yet fully end-to-end (memory budgets, tile scheduling, prefetch, cache invalidation policy, degradation paths).
- Collaboration data model and op pipeline are not implemented in a way that can be stress-tested yet.

## 4) What I would change (priority order)

## P0 — Foundation hardening (must-do before scaling features)

1. **Split editor UI architecture by bounded contexts**
   - Break `NoteEditorUi` into toolbar, panel, page chrome, and canvas orchestration modules.
   - Keep ViewModel/business state independent of Compose widgets.

2. **Define a strict stroke pipeline contract**
   - Stages: input sampling → prediction (optional) → smoothing → variable-width geometry → render cache.
   - Version each stage so behavior changes are testable and reversible.

3. **Adopt explicit page render budget manager**
   - Hard caps for bitmap memory by device class.
   - Priority queues for visible pages, near-viewport pages, background pages.

4. **Introduce operation/event model now (even offline-only)**
   - Local op log with deterministic replay contract now, even before cloud sync is live.
   - This de-risks collaboration and undo/redo consistency later.

## P1 — User intent and product-feel upgrades

1. **Unobtrusive UI mode system**
   - Minimal mode for pure writing, expanded mode for structure/actions.
   - Progressive disclosure for advanced controls.

2. **Tool semantics consistency**
   - Stabilization slider should control actual smoothing, not line-width behavior.
   - Eraser behavior labels must map exactly to implemented capability.

3. **Page model upgrades**
   - Add robust page overview/thumbnail navigation for long notebooks and PDFs.
   - Fast jump-to-page + recent pages stack.

## P2 — Scale and collaboration readiness

1. **Lamport/device/op metadata fully embedded in local storage**
2. **Snapshot and compaction strategy for long-lived notes**
3. **Sharing/access model and permission graph finalized before implementation**

## 5) How I would structure the code

- `core/`
  - `model/` (note/page/stroke/value objects)
  - `ops/` (operation envelopes, replay, merge)
  - `render/` (smoothing, geometry, tile cache contracts)
- `features/editor/`
  - `ui/` compose modules (toolbar/panels/canvas/page-nav)
  - `state/` UI state only
  - `domain/` editor use-cases
- `data/local/`
  - room entities/dao/mappers
- `data/sync/`
  - outbound queue, inbound apply, conflict resolution
- `integration/myscript/`
  - isolated boundary adapter + lifecycle manager

This structure isolates volatile UX code from durable core logic and makes collaboration rollout less risky.

## 6) How I would save user notes

Use a **hybrid model**:

- **Authoritative local page snapshot**: compact representation of latest page state for instant open.
- **Append-only local op log**: every user edit as an operation for replay/audit/sync.
- **Periodic compaction**: fold old ops into fresh snapshot after thresholds (count/time/size).

Key rules:
- Writes are batched and atomic per gesture/transaction.
- Every op has `(noteId, pageId, deviceId, lamport, opId, timestamp)`.
- Undo/redo references op boundaries, not arbitrary object diffs.

## 7) How I would handle PDFs (300+ pages target)

1. **Tile-based renderer, not full-page-only bitmaps**
   - Tile pyramid by zoom levels.
   - Render only visible + small prefetch window.

2. **Strict memory control**
   - LRU across `(doc, page, zoomBucket, tileRect)`.
   - Device-tier budgets and adaptive downgrade under pressure.

3. **Asynchronous text extraction/indexing**
   - Build per-page text structures lazily and cache with eviction.
   - Background indexing jobs with cancellation/backpressure.

4. **Annotation layering**
   - Keep ink overlay independent from PDF bitmap cache.
   - Compose final view from cached PDF tiles + vector ink layer.

## 8) Planned collaboration/sync features

Use an **op-based deterministic model**:

- Client emits ordered ops with local optimistic apply.
- Server validates and re-broadcasts canonical order.
- Clients reconcile by replay from last stable snapshot cursor.

Conflict policy:
- Deterministic ordering by `(lamport, deviceId, opId)`.
- Idempotent apply; duplicate ops ignored.
- Tombstones for deletions (later compacted).

## 9) Sharing model

Use two primitives:
- **Direct share** (user-to-user ACL with role: owner/editor/viewer).
- **Public links** (unguessable token, default view-only, revocable, optional expiry).

Operational requirements:
- Permission checks at every read/write endpoint.
- Fast permission cache with invalidation on ACL change.
- Link audit trail (created/revoked/accessed).

## 10) Realtime collaboration design

- Keep Convex subscriptions as realtime transport.
- Scope subscriptions by note/page presence.
- Broadcast minimal op payloads, not full snapshots.
- Presence channel separate from content ops (cursor/tool/page focus only).
- Reconnection flow: fetch page head + missing ops + replay.

## 11) Performance upgrades

1. **Frame pacing and render coalescing** for high-frequency input.
2. **Avoid hot-path allocations** (already partly improved; continue across render/input loops).
3. **Background serialization + bounded queues** with explicit drop/backpressure policy.
4. **Perf instrumentation dashboards**: frame time, input latency, queue depth, cache hit rate.
5. **Tiered quality modes** for lower-end devices.

## 12) UI changes for clean/intuitive/unobtrusive experience

- Pill-group top bar with clear hierarchy and reduced clutter.
- Contextual tool settings panel near interaction point.
- Persistent but subtle page indicator + fast page jump.
- Better touch targets and stylus-first affordances.
- Optional “focus mode” that hides chrome while writing.

## 13) Stroke rendering and smoothing strategy

Recommended pipeline:
1. Point stream normalization (resample by time/distance).
2. Speed-aware smoothing (e.g., one-euro filter or adaptive Catmull-Rom).
3. Optional simplification (RDP with conservative tolerance).
4. Pressure/tilt mapping into width/shape.
5. GPU-friendly path tessellation and cached segments.

Important UX mapping:
- **Stabilization slider** should adjust smoothing aggressiveness/latency tradeoff.
- **Thickness/pressure sliders** should remain separate and clearly labeled.

## 14) MyScript SDK handling review

Current setup direction is good (v4 certificate class, bundled assets, app-level engine + per-page editor manager). To make handwriting recognition/search truly core workflow quality:

- Add robust page lifecycle stress tests (rapid page switching, process death recovery).
- Version recognition outputs and index writes so reprocessing is deterministic.
- Add background re-recognition jobs for imported PDFs and edited legacy pages.
- Keep recognition resource loading observable (startup timing, failure states, fallback language behavior).

## 15) Recommended next milestone plan

### Milestone F0 — “Foundation Hardening” (2–4 weeks)
- Editor UI modularization.
- Stroke pipeline formalization + stabilization semantics fix.
- PDF tile rendering + memory budget manager.
- Local op-log contract with deterministic replay tests.

### Milestone F1 — “Sync/Collab Skeleton”
- Convex schema + minimal ops endpoints + subscription loop.
- Device/Lamport metadata end-to-end.
- Snapshot cursor/backfill flow.

### Milestone F2 — “Sharing + Realtime UX”
- ACL and public link model.
- Presence indicators + collaborator cursors.
- Share sheet and permission management UI.

## 16) Final recommendation

Do not rush new feature surface area yet. Prioritize a clean internal contract for stroke/render/op pipelines, plus PDF scalability architecture. That foundation will pay off across handwriting quality, realtime convergence, and long-document performance.


## 17) Concrete implementation backlog (recommended)

### Track A — Ink quality and feel (highest user impact)
1. Implement adaptive smoothing pipeline (one-euro + Catmull-Rom fallback) behind a runtime flag.
2. Rebind stabilization slider to smoothing strength/latency (not width variance).
3. Add stroke-segment cache and frame-coalesced invalidation to reduce jank on fast stylus movement.
4. Add telemetry counters: input-to-render latency p50/p95, dropped frame count, and active-stroke point counts.

**Acceptance criteria**
- Sustained drawing at 120Hz on target tablet without visible hitching in 5-minute stress run.
- Stabilization slider produces measurable path smoothness change without changing static brush width.

### Track B — PDF scale and reliability (300+ pages)
1. Introduce tile pyramid cache keyed by `(docId, page, zoomBucket, tileRect)`.
2. Add viewport-aware prefetch queue with cancellation when user scroll direction changes.
3. Add memory pressure fallback ladder (tile size downshift, reduced prefetch window, cache shrink).
4. Move text extraction/indexing to background workers with bounded queue and cancellation.

**Acceptance criteria**
- 300-page PDF opens under target startup budget and keeps scroll latency stable after 10 minutes.
- No OOM in stress runs with repeated zoom/page jumps.

### Track C — Data model for sync/collaboration
1. Add local op envelope table with `(deviceId, lamport, opId, pageId, type, payload, committedAt)`.
2. Make snapshot+compaction job deterministic and idempotent.
3. Add replay verifier tests: same op sequence always yields identical page state hash.
4. Add conflict fixtures for interleaved erase/add/update operations.

**Acceptance criteria**
- Reconnect replay convergence test passes for randomized op order permutations.
- Duplicate op delivery is safely ignored (idempotence proof by test).

### Track D — Sharing + realtime UX foundations
1. Implement ACL model (`owner/editor/viewer`) and revocable public links with expiry.
2. Add presence stream (cursor/tool/page focus) separate from content ops.
3. Add reconnect flow: snapshot cursor + backfill + live tail subscription.
4. Add user-facing share management panel with explicit role labels.

**Acceptance criteria**
- Permission checks enforced on all read/write paths.
- Multi-user session converges under packet delay and reconnect simulation.

### Track E — Editor UX structure
1. Split `NoteEditorUi` into bounded modules (toolbar, page nav, tool panels, canvas scene).
2. Add focus mode and reduce persistent chrome noise.
3. Add page overview grid + jump-to-page for long documents.
4. Keep all unimplemented controls hidden (no dead buttons).

**Acceptance criteria**
- Reduced editor file/module complexity and faster feature-level test cycles.
- Usability pass confirms primary actions are reachable in <=2 taps.

## 18) Suggested sequencing and ownership

- **Sprint 1**: Track A + E structural split (foundation for quality and velocity).
- **Sprint 2**: Track B PDF scale path + perf instrumentation.
- **Sprint 3**: Track C sync data contracts and deterministic replay suite.
- **Sprint 4**: Track D sharing/realtime MVP with guarded rollout flags.

This ordering prioritizes drawing feel and performance first, then introduces distributed-state complexity after local determinism is solid.
