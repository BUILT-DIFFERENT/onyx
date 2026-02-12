# Full Project Analysis — Onyx v0

Date: 2026-02-12
Scope: Architecture, code, plans, docs, performance, UX, and roadmap

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Review Status](#2-architecture-review-status)
3. [Android App Analysis](#3-android-app-analysis)
4. [Stroke Rendering & Smoothing](#4-stroke-rendering--smoothing)
5. [PDF Handling Strategy](#5-pdf-handling-strategy)
6. [MyScript Integration](#6-myscript-integration)
7. [Note Storage & Data Model](#7-note-storage--data-model)
8. [Sync & Collaboration Strategy](#8-sync--collaboration-strategy)
9. [Sharing & Public Links](#9-sharing--public-links)
10. [Performance Roadmap](#10-performance-roadmap)
11. [UI/UX Recommendations](#11-uiux-recommendations)
12. [Web App & Backend](#12-web-app--backend)
13. [Documentation Gaps](#13-documentation-gaps)
14. [Prioritised Change List](#14-prioritised-change-list)

---

## 1. Executive Summary

Onyx is an offline-first note-taking platform with an Android authoring client, Convex backend, and a planned view-only web client. The project is in early v0 development. Milestone A (Android offline editor + MyScript) is code-complete with 52 tests across 15 files; three remediation PRs have hardened architecture, UX, and rendering.

**Strengths:**
- Clean MVVM architecture with extracted ViewModel, state types, and undo controller
- Protobuf serialization for strokes (efficient wire format)
- AppContainer-based DI ready for Hilt migration
- LRU-cached PDF rendering with MuPDF
- Modular ink pipeline (separate files for touch, drawing, geometry, transforms)
- MyScript integration with per-page ContentPackage lifecycle
- Well-defined V0 API contract with Lamport-ordered operations
- Comprehensive milestone plans covering offline → sync → collab → sharing

**Key Risks:**
- No physical device verification for stylus/ink features (8 tasks blocked)
- Convex backend is scaffold-only (no functions implemented)
- Web app has no routes or components implemented
- Shared packages are empty scaffolds
- Single 3-point smoothing is insufficient for professional-grade feel
- 300+ page PDF support needs tiling, not full-page bitmap rendering
- MyScript clear+re-feed undo is O(n) and will degrade with stroke count

---

## 2. Architecture Review Status

The `android-architecture-review.md` identified 42 recommendations across 7 areas. Three remediation PRs addressed the findings:

### PR1 — Architecture & Data Layer Hardening ✅
| Finding | Status |
|---------|--------|
| Extract ViewModel from UI file | ✅ `NoteEditorViewModel.kt` created |
| Add explicit error state flow | ✅ Error emission via StateFlow |
| Replace destructive Room migration | ✅ `MIGRATION_1_2` explicit, schema v2 |
| Switch to protobuf serialization | ✅ `StrokeSerializer.kt` uses kotlinx-protobuf |
| Introduce AppContainer for DI | ✅ Interface + `requireAppContainer()` |
| Record dev-phase compat policy | ✅ Documented in AGENTS.md |

### PR2 — UX Gaps ✅
| Finding | Status |
|---------|--------|
| Title editing missing | ✅ Inline title editing in top bar |
| Note deletion from Home | ✅ Long-press context menu + confirm |
| Page counter missing | ✅ `current/total` with a11y semantics |
| Dead top-bar controls | ✅ Replaced with functional overflow menu |
| Read-only blocks all input | ✅ Pan/zoom preserved, stylus blocked |
| Eraser mode UI misleading | ✅ Simplified to stroke-eraser only |

### PR3 — Rendering & Performance ✅
| Finding | Status |
|---------|--------|
| PDF re-render churn | ✅ LRU bitmap cache by (page, scale) |
| No text structure cache | ✅ LRU cache (12 entries) |
| Stroke write queue retry | ✅ Explicit retry logging |
| Hot-path Pair allocations | ✅ Inline axis helpers in ViewTransform |
| Preserve rendering strengths | ✅ Path cache + viewport culling intact |

### Remaining Unaddressed Items

These were not critical for the MVP but should be addressed before v1:

| # | Finding | Severity | Recommended Phase |
|---|---------|----------|-------------------|
| 1 | **No Hilt DI** — AppContainer works but won't scale | Medium | Pre-Milestone B |
| 2 | **Synchronous app startup** — blocking MyScript asset copy | Medium | Milestone Av2 |
| 3 | **Stroke smoothing insufficient** — 3-point average only | High | Milestone Av2 |
| 4 | **No stroke tapering** — width is constant per stroke | Medium | Milestone Av2 |
| 5 | **Motion prediction disabled** — 50ms+ latency gap | Medium | Milestone Av2 |
| 6 | **Highlighter blending** — uses fixed alpha, not multiply blend | Low | Milestone Av2 |
| 7 | **No haptic feedback** — no pen-down or tool-switch haptics | Low | UI Overhaul |
| 8 | **Dark mode absent** | Low | UI Overhaul |
| 9 | **Color parsing per-frame** — `parseColor()` on every draw | Medium | Next sprint |
| 10 | **Path cache unbounded** — grows indefinitely in long sessions | Medium | Next sprint |
| 11 | **Lamport clock uninitialised** — always 0, no sync readiness | High | Pre-Milestone C |
| 12 | **PDF text selection unused** — selected text only logged | Low | Milestone Av2 |

---

## 3. Android App Analysis

### Architecture Assessment

The current architecture is sound for a single-developer MVP:

```
OnyxApplication (AppContainer)
├── OnyxDatabase (Room, v2)
│   ├── NoteDao / PageDao / StrokeDao / RecognitionDao
│   └── MIGRATION_1_2
├── NoteRepository (single source of truth)
├── DeviceIdentity (UUID, SharedPreferences)
└── MyScriptEngine (singleton, lazy init)

MainActivity
└── OnyxNavHost
    ├── HomeScreen (ViewModel inline)
    │   └── HomeScreenContent (search, list, PDF import)
    └── NoteEditorScreen
        ├── NoteEditorViewModel (Factory injected)
        │   ├── UndoController
        │   └── MyScriptPageManager
        ├── NoteEditorUi (toolbar, tools, panels)
        ├── NoteEditorPdfContent (PDF + ink overlay)
        └── InkCanvas (modular rendering pipeline)
```

### Recommended Structural Changes

1. **Hilt migration** — Replace AppContainer with `@HiltAndroidApp`, `@HiltViewModel`, and `@Inject`. This eliminates manual factory wiring and prepares for WorkManager + navigation injection.

2. **HomeScreen ViewModel extraction** — HomeScreen currently creates its ViewModel inline with `remember {}`. Extract to a proper `HomeViewModel` with `hiltViewModel()` for testability.

3. **NoteEditorUi decomposition** — The 44.9KB file has suppressions for LongMethod, CyclomaticComplexity, and TooManyFunctions. Split into:
   - `EditorToolbar.kt` — Tool switching, palette, undo/redo
   - `ToolSettingsPanel.kt` — Brush size, stabilization, opacity
   - `ColorPickerDialog.kt` — Hex input, preview, palette
   - `EditorScaffold.kt` — Layout orchestration only

4. **Startup optimization** — Move MyScript asset copy to a coroutine with `Dispatchers.IO`. Show a lightweight splash while assets initialize. Use `SplashScreen` API for cold start.

5. **Repository split** — `NoteRepository` handles notes, pages, strokes, and recognition. Consider splitting into `NoteRepository` (notes/pages), `StrokeRepository` (strokes/serialization), and `RecognitionRepository` (recognition/search) for single-responsibility.

---

## 4. Stroke Rendering & Smoothing

### Current State

The ink pipeline uses a **3-point moving average filter** with quadratic Bézier curve construction:

```
Raw Points → Smooth (3-point avg) → Quadratic Bézier Path → Cached → Draw
```

**Issues:**
- 3-point average is too aggressive — it flattens sharp corners and creates visible lag on fast strokes
- Smoothing is recomputed on every render for cached strokes (waste)
- No tapering (width start/end variation) — strokes look mechanical
- Pressure curve is linear (0.85–1.15× baseWidth) — feels dead
- Motion prediction is disabled — creates 50ms+ visible latency gap on pen-up
- Color parsed from hex string on every frame

### Recommended Improvements

1. **Replace 3-point average with Catmull-Rom spline interpolation:**
   - Catmull-Rom provides C¹ continuity (smooth tangents) without over-smoothing
   - Passes through control points exactly — preserves user intent
   - Tension parameter (0.5 default) controls smoothness vs. sharpness
   - Much better for handwriting than moving average

2. **Add variable-width stroke rendering:**
   - Use a non-linear pressure curve: `width = base * (min + (max - min) * pow(pressure, gamma))`
   - Default gamma = 0.6 makes light pressure more responsive
   - Add start/end taper: fade width over first/last 3-5 points
   - Use tilt data (already captured) to modulate width for calligraphy effects

3. **Enable and fix motion prediction:**
   - The `MotionPredictionAdapter` already exists but is disabled
   - Re-enable with proper pen-up handoff: cancel predicted strokes, snap to final real point
   - Render predicted points at reduced alpha (already implemented at 0.2)
   - Reduces perceived latency by 30-60ms

4. **Compute smoothing once, not per-frame:**
   - Move smoothing to stroke finalisation (on pen-up)
   - Store smoothed points in the path cache entry
   - In-progress strokes can use raw points with light smoothing

5. **Implement front-buffer rendering for in-progress strokes:**
   - Use `GLFrontBufferedRenderer` (Android 10+) for active drawing
   - Renders to a small damaged region directly to front buffer
   - Eliminates compositor latency (reduces by ~16ms at 60Hz)
   - Fall back to `InProgressStrokesView` on older devices

6. **Cache Color.parseColor results:**
   - Parse once on stroke creation, store as `Int` in a `Map<String, Int>`
   - Eliminates per-frame string parsing (currently O(strokes) per frame)

---

## 5. PDF Handling Strategy

### Current State

MuPDF renders full-page bitmaps cached by `(pageIndex, renderScaleKey)` in a 64MB LRU cache. Text structure is cached in a 12-entry LRU. This works for small documents but will fail at 300+ pages.

### Problems at Scale (300+ Pages)

1. **Memory:** At 1080×1440 px per page, a single bitmap = ~6MB. The 64MB cache holds ~10 pages. Scrolling through 300 pages causes constant cache thrashing.

2. **Rendering latency:** MuPDF renders the full page on every scale change. At zoom > 2×, this means rendering 4K+ bitmaps synchronously.

3. **No visible page recycling:** All pages in a note share one renderer. Long notes create memory pressure from stroke data alone.

### Recommended Strategy: Tile-Based Rendering

1. **Tile the PDF into 512×512 px chunks:**
   - Each tile is rendered independently and cached
   - Only visible tiles are rendered (viewport-based culling)
   - Zoom changes only re-render affected tiles
   - Tiles can be rendered asynchronously on `Dispatchers.IO`

2. **Multi-resolution tile pyramid:**
   - Level 0: Full resolution (1:1 mapping to PDF points)
   - Level 1: 50% resolution (overview)
   - Level 2: 25% resolution (thumbnail)
   - Show low-res tiles while high-res tiles load (progressive rendering)

3. **Async rendering pipeline:**
   ```
   Viewport Change → Compute visible tiles → Check cache →
   → Cache hit: draw immediately
   → Cache miss: draw low-res fallback, queue high-res render
   → Render complete: invalidate canvas region, draw high-res tile
   ```

4. **Page recycling for long documents:**
   - Only keep 3-5 pages in memory (current ± 2)
   - Lazy-load page strokes from Room when entering viewport
   - Save/evict page data when scrolling away

5. **Separate ink layer from PDF layer:**
   - Render PDF tiles to one Canvas
   - Render ink strokes to an overlay Canvas
   - Composite in the final draw pass
   - This avoids re-rendering PDF when only strokes change

6. **Large document considerations:**
   - 300 page × 100 strokes/page = 30,000 strokes total
   - At 100 points/stroke = 3M points × 28 bytes = ~84MB of stroke data
   - Use memory-mapped file or paged Room queries, not load-all
   - Consider off-heap storage for stroke geometry

---

## 6. MyScript Integration

### Current State

The MyScript iink SDK v4.3.0 integration is well-structured:
- `MyScriptEngine` — application singleton, handles asset extraction and engine lifecycle
- `MyScriptPageManager` — per-page context, manages ContentPackage/OffscreenEditor lifecycle
- Coordinate conversion: page pt → mm (1pt = 0.3528mm)
- Stroke ID mapping between Onyx UUIDs and MyScript IDs
- Recognition results exported as plain text and persisted to Room FTS

### Issues & Recommendations

1. **Clear+re-feed undo is O(n):**
   - When undo/redo falls back (native history unavailable), ALL strokes are cleared and re-fed
   - With 100+ strokes, this creates visible lag
   - **Fix:** Maintain a shadow stroke list in `MyScriptPageManager` and use incremental updates when possible. Track which strokes MyScript knows about vs. which were undone.

2. **ContentPackage file growth:**
   - Each page creates a `.iink` file that grows with recognition data
   - No cleanup of old ContentPackage files for deleted pages
   - **Fix:** Add `deletePageContext(pageId)` method and call from note/page deletion

3. **Recognition is synchronous with pen-up:**
   - Recognition triggers on every stroke addition via the `contentChanged` listener
   - For fast writing, this creates recognition thrash
   - **Fix:** Debounce recognition results (500ms after last stroke) before persisting to Room

4. **No batch recognition:**
   - Milestone Av2 plans batch recognition but the current architecture doesn't support it
   - **Fix:** Add a `recognizeAll()` method that iterates pages and feeds accumulated strokes. Use WorkManager for background processing.

5. **Language configuration is hardcoded:**
   - `en_CA` with US fallback (line 62 of MyScriptEngine.kt)
   - **Fix:** Make language configurable via user settings. MyScript supports 100+ languages.

6. **JIIX export unused:**
   - `exportJIIX()` exists but is never called
   - JIIX contains word boundaries, character alternatives, and spatial data
   - **Fix:** Use JIIX for word-level search indexing and smart text selection

7. **No conversion/gesture recognition:**
   - MyScript supports shape recognition, math conversion, and gesture shortcuts
   - **Fix:** Enable gesture recognition for scratch-out (erase by scribbling) as a natural editing gesture

---

## 7. Note Storage & Data Model

### Current Local Storage

```
NoteEntity: id (UUID), title, createdAt, updatedAt, lamportClock
PageEntity: id (UUID), noteId (FK), indexInNote, type (INK|PDF|MIXED), pdfAssetId?, pdfPageIndex?
StrokeEntity: id (UUID), pageId (FK), data (protobuf bytes), createdAt, createdLamport
RecognitionIndexEntity: pageId (PK), recognizedText
RecognitionFtsEntity: pageId (PK), recognizedText (FTS4)
```

### Sync Readiness Gaps

1. **Lamport clock is always 0** — `NoteEntity.lamportClock` is initialized to 0 and never incremented. For sync to work, every mutation must increment the clock: `newLamport = max(localLamport, receivedLamport) + 1`.

2. **No operation log** — The current model stores final state (strokes), not operations. For CRDT-style sync, we need an `ops` table matching the V0 API:
   ```
   OpEntity: id, pageId, type (StrokeAdd|StrokeTombstone|AreaErase),
             lamport, deviceId, payload (protobuf), createdAt
   ```

3. **No offline queue** — Mutations go directly to Room. For sync, we need a pending queue that replays to Convex on reconnect.

4. **deviceId not embedded in entities** — `DeviceIdentity` generates a UUID but it's not stored with strokes or operations. Needed for `(lamport, deviceId, opId)` ordering.

### Recommended Approach for Note Saving

1. **Auto-save on pen-up** (already working via stroke write queue)
2. **Debounced title save** (300ms after last keystroke, already implemented)
3. **Add operation logging:** Every stroke add/erase creates an `OpEntity` alongside the state change. This creates a replayable log for sync.
4. **Background sync worker:** WorkManager periodic task checks for pending ops and submits batches to Convex when online.
5. **Conflict resolution:** On receiving remote ops, replay against local state using Lamport ordering. The deterministic `(lamport, deviceId, opId)` tuple ensures all clients converge.

---

## 8. Sync & Collaboration Strategy

### Architecture (from V1-Real-Time-Collaboration.md)

```
Android → ops.submitBatch → Convex → ops.watchPageHead → All Clients
                                  ↓
                          Snapshot Worker (5s/20s)
                                  ↓
                          commits.create (snapshot + R2 blob)
```

### Recommended Implementation Approach

1. **Phase 1: One-way sync (Android → Convex)**
   - Android writes ops locally AND submits to Convex when online
   - Convex stores ops in `ops` table
   - Web reads from Convex snapshots (view-only)
   - No conflict resolution needed yet (single writer)

2. **Phase 2: Bi-directional sync (multi-device)**
   - Convex subscription on `ops.watchPageHead` pushes new ops to Android
   - Android replays remote ops against local state
   - Lamport clock ensures deterministic ordering
   - Tombstone-based deletion (strokes are never physically deleted during sync)

3. **Phase 3: Real-time collaboration (multi-user)**
   - Convex subscriptions provide < 100ms latency for op delivery
   - Each user maintains their own Lamport counter
   - Operations are commutative by design:
     - StrokeAdd: Always succeeds (append-only)
     - StrokeTombstone: Idempotent (tombstone on already-deleted = no-op)
     - AreaErase: Guarded by `createdLamport ≤ eraseLamport` (prevents retroactive erasure)
   - Cursor/presence: Separate Convex table with TTL, not part of op log

4. **Offline resilience:**
   - Android Room is the local source of truth
   - Pending ops queue in Room with `syncStatus: PENDING | SYNCED | CONFLICT`
   - On reconnect: submit all PENDING ops in Lamport order
   - Convex validates and rejects ops with stale Lamport (client must merge and retry)

### Why Convex Over Custom WebSocket

- Built-in subscriptions eliminate custom WS infrastructure
- Automatic reconnection and retry
- Server-side validation in mutations
- Scheduled functions for snapshots/previews without separate worker infrastructure
- Type-safe function definitions
- No state synchronisation between WS server and database

---

## 9. Sharing & Public Links

### Planned Model (from V0 API)

```
shares: { noteId, userId, role (viewer|editor), grantedBy, grantedAt }
publicLinks: { noteId, token, createdBy, createdAt, expiresAt? }
```

### Recommended Implementation

1. **Share by email:**
   - Convex mutation `shares.grantByEmail(noteId, email, role)`
   - If user exists: add share record
   - If user doesn't exist: create pending invite, resolve on first login
   - Android UI: Share sheet with email input and role picker

2. **Public links (view-only):**
   - Generate cryptographically random token (32 bytes, base64url)
   - Resolve in Convex: `publicLinks.resolve(token)` → returns note metadata + presigned URLs
   - Web renders from snapshot tiles (no raw PDF download)
   - Optional expiry for security

3. **Access control:**
   - All Convex mutations check `ctx.auth` identity
   - Note operations check ownership OR active share record
   - Public link access is read-only, no auth required (token is the credential)

4. **Revocation:**
   - `shares.revoke(noteId, userId)` — immediate effect
   - `publicLinks.revoke(token)` — immediate effect
   - Existing open sessions see stale data until next subscription update

---

## 10. Performance Roadmap

### Critical Performance Work (Required Before Beta)

| # | Area | Change | Impact |
|---|------|--------|--------|
| 1 | **Ink latency** | Enable front-buffer rendering via `GLFrontBufferedRenderer` | −16ms per frame |
| 2 | **Ink latency** | Re-enable motion prediction with pen-up fix | −30-60ms perceived |
| 3 | **Stroke rendering** | Cache `Color.parseColor()` results, parse once | Eliminate per-frame string parsing |
| 4 | **Stroke rendering** | Bound path cache, evict on page change | Prevent memory leak |
| 5 | **PDF rendering** | Tile-based rendering (512×512 chunks) | Support 300+ pages |
| 6 | **PDF rendering** | Async tile pipeline with progressive loading | Smooth scrolling |
| 7 | **PDF memory** | Page recycling (keep ±2 pages in memory) | Reduce memory 10× |
| 8 | **Stroke storage** | Paged Room queries for large documents | Avoid loading 30K strokes |
| 9 | **Recognition** | Debounce recognition updates (500ms) | Reduce CPU thrash |
| 10 | **Startup** | Async MyScript init with SplashScreen API | Eliminate blocking startup |

### Measurement Strategy

- Use Android GPU Profiler to measure frame times during drawing
- Target: < 8ms per frame (120Hz capable)
- Use Android Profiler to track memory during 300-page PDF navigation
- Target: < 200MB RSS during large document editing
- Use `Debug.startMethodTracing()` for stroke rendering hot path
- Target: < 2ms per stroke path construction

---

## 11. UI/UX Recommendations

### Current State

The UI is functional but developer-oriented. The UI overhaul plan (Samsung Notes + Notewise hybrid) outlines the target, but several quick wins can be applied before the full overhaul:

### Immediate Improvements

1. **Toolbar refinement:**
   - Current: All tools visible simultaneously, settings via long-press dropdown
   - Recommended: Floating bottom toolbar pill (Samsung Notes style) with only active tool expanded. Reduces cognitive load and frees canvas space.

2. **Tool switching feel:**
   - Add haptic feedback on tool select (`HapticFeedbackType.LongPress`)
   - Add 100ms scale animation on tool toggle (1.0 → 1.1 → 1.0)
   - Show brief tool name toast on switch (fade after 500ms)

3. **Color picker:**
   - Current: Hex input with preset swatches
   - Add: HSV wheel picker for intuitive color selection
   - Add: Recent colors row (last 5 used)
   - Add: Long-press swatch to replace with current color

4. **Page navigation:**
   - Current: Arrow buttons with counter
   - Add: Page thumbnail strip (horizontal scrollable, lazy-loaded)
   - Add: Pinch-out gesture to enter page overview
   - Add: Drag to reorder pages

5. **Canvas interaction:**
   - Add two-finger double-tap to fit page to screen
   - Add double-tap to zoom to 200% at tap point
   - Add edge-of-screen indicators when panned off-canvas

6. **Selection & clipboard:**
   - PDF text selection currently logs only — wire to system clipboard
   - Add lasso selection for strokes (select, move, copy, delete)
   - Add copy-as-image for selected region

### Dark Mode

- Define semantic color tokens matching the UI overhaul plan
- Use `isSystemInDarkTheme()` for automatic switching
- Ensure ink colors remain legible on dark background (invert white → dark grey, not black)
- PDF rendering needs no change (bitmaps are pre-rendered)

---

## 12. Web App & Backend

### Web App Status

The web app (`apps/web/`) is a scaffold with:
- TanStack Start configured with Vite
- Tailwind CSS + PostCSS
- Playwright for E2E tests
- No routes, components, or layouts implemented

### Recommended Web Implementation Order

1. **Auth flow:** Clerk integration, `users.upsertMe` post-login
2. **Note list page:** Fetch from Convex `notes.listMine()`, display as card grid
3. **Note viewer:** Render from Convex snapshots/tiles, read-only
4. **Search:** Full-text search via Convex `search.query()`
5. **Share/public link viewer:** Token-based access, no auth required

### Convex Backend Status

Schema, functions, jobs, and migrations are all `.gitkeep` placeholders. Implementation should follow the V0 API contract exactly. Recommended implementation order:

1. `schema.ts` — Define all tables per V0 API spec
2. `functions/users.ts` — `upsertMe` (Clerk token → user record)
3. `functions/notes.ts` — CRUD (create, listMine, rename, delete)
4. `functions/pages.ts` — CRUD (createInkPage, attachPdfPage)
5. `functions/ops.ts` — `submitBatch`, `listPageOps`, `watchPageHead`
6. `functions/commits.ts` — Snapshot create/list
7. `functions/search.ts` — Full-text search over recognition text
8. `jobs/snapshots.ts` — Periodic snapshot generation
9. `jobs/previews.ts` — First-page preview generation

### Shared Packages

All five packages (`config`, `contracts`, `shared`, `ui`, `validation`) are empty scaffolds. Priority:
1. `@onyx/validation` — Zod schemas for API types (shared between web and Convex)
2. `@onyx/contracts` — JSON fixtures for schema drift testing
3. `@onyx/shared` — Utility functions (timestamp formatting, UUID generation)
4. `@onyx/ui` — shadcn/ui components (button, card, dialog, etc.)
5. `@onyx/config` — Shared lint/format/tsconfig (already functional via extends)

---

## 13. Documentation Gaps

### Files That Need Updates

1. **README.md** — Says "No production code yet" but Milestone A is code-complete. Needs status update reflecting actual progress.

2. **docs/README.md** — Contains Windows-style paths (`C:/onyx/docs/...`). Should use relative paths.

3. **Architecture docs** — Several docs are 1-3 line placeholders:
   - `testing.md` — 3 lines, no detail on test strategy or coverage targets
   - `ci-cd.md` — 4 lines, no pipeline diagram or deployment details
   - `observability.md` — 2 lines, no metrics or alerting strategy
   - `migrations-backups.md` — 2 lines, no backup schedule or retention policy
   - `identity.md` — 4 lines, no auth flow diagram or token management
   - `storage.md` — 4 lines, no bucket structure or lifecycle rules
   - `data-model.md` — 5 lines, no ER diagram or field definitions
   - `sync.md` — Adequate detail for v0 (Lamport ordering, offline queue)

4. **Missing docs:**
   - No ink rendering architecture doc
   - No MyScript integration guide
   - No performance benchmarks or targets
   - No contribution guide
   - No API changelog

### Recommended Doc Updates

- Update `README.md` status section to reflect Milestone A completion
- Fix `docs/README.md` paths to use relative references
- Expand architecture docs with diagrams and implementation details as features are built
- Add `docs/architecture/ink-rendering.md` documenting the stroke pipeline
- Add `docs/architecture/myscript-integration.md` documenting recognition setup

---

## 14. Prioritised Change List

Changes are grouped by urgency and dependency order.

### P0 — Foundation (Do Now)

1. Update `README.md` to reflect actual project status (Milestone A complete, code-complete)
2. Fix `docs/README.md` path references
3. Initialize Lamport clock properly — increment on every local mutation
4. Embed `deviceId` in stroke entities for sync readiness
5. Bound the path cache in `InkCanvas` — evict on page change or cap at 500 entries
6. Cache `Color.parseColor()` results — parse once per unique color

### P1 — Drawing Feel (Milestone Av2)

7. Replace 3-point average smoothing with Catmull-Rom spline interpolation
8. Add pressure curve with configurable gamma (default 0.6)
9. Add start/end tapering for natural stroke appearance
10. Re-enable motion prediction with proper pen-up handoff
11. Implement front-buffer rendering via `GLFrontBufferedRenderer`
12. Add stabilization that affects smoothing, not just width variation
13. Use tilt data for calligraphy-style width modulation

### P2 — PDF at Scale

14. Implement tile-based PDF rendering (512×512 chunks)
15. Add multi-resolution tile pyramid for progressive loading
16. Implement page recycling (keep ±2 pages in memory)
17. Add async tile rendering pipeline with low-res fallback
18. Implement paged Room queries for stroke loading (limit + offset)
19. Wire PDF text selection to system clipboard

### P3 — Recognition Quality

20. Debounce MyScript recognition updates (500ms after last stroke)
21. Fix clear+re-feed undo — maintain shadow stroke list for incremental updates
22. Add ContentPackage cleanup for deleted pages
23. Make recognition language configurable
24. Use JIIX export for word-level search indexing
25. Enable scratch-out gesture recognition

### P4 — Architecture & Sync Prep

26. Migrate from AppContainer to Hilt
27. Extract HomeScreen ViewModel
28. Decompose NoteEditorUi.kt into focused composables
29. Add operation logging table (OpEntity) alongside state changes
30. Build offline sync queue with WorkManager
31. Async MyScript initialization with SplashScreen API

### P5 — UI Polish

32. Floating bottom toolbar pill (Samsung Notes style)
33. Page thumbnail strip with drag-to-reorder
34. HSV color wheel picker + recent colors
35. Haptic feedback on tool switching
36. Two-finger double-tap to fit page
37. Lasso selection for strokes
38. Dark mode with semantic color tokens

### P6 — Backend & Web

39. Implement Convex schema matching V0 API
40. Build core Convex functions (users, notes, pages, ops)
41. Build web auth flow with Clerk
42. Build web note list and viewer
43. Implement `@onyx/validation` Zod schemas
44. Build contract test fixtures

### P7 — Collaboration (Milestone C)

45. Implement `ops.submitBatch` and `ops.watchPageHead`
46. Build bi-directional sync with Lamport conflict resolution
47. Implement sharing by email
48. Build public link generation and resolution
49. Add cursor/presence indicators for real-time collaboration
50. Build snapshot and preview background jobs

---

*This analysis was generated from a complete review of all source files, architecture documents, milestone plans, and remediation tracking in the Onyx repository.*
