# Milestone: Canvas, Rendering, Strokes & PDF Overhaul

**Version:** 2.14  
**Date:** 2026-02-13  
**Status:** Draft - Ready for Momus Re-Review (Round 21)  
**Supersedes:** Portions of `milestone-av2-advanced-features.md`, `milestone-ui-overhaul-samsung-notes.md`

---

## Revision History

| Version | Date       | Changes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| ------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1.0     | 2026-02-13 | Initial draft                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 1.1     | 2026-02-13 | Momus review corrections: fixed tile size (2024 not 2048), corrected database class (OnyxDatabase not AppDatabase - no destructive migration), identified both prediction flags, added PDF text selection parity requirements, extended timeline to 20 weeks, added Week 0 PdfiumAndroid spike, unified dependency coordinates                                                                                                                                                                                                             |
| 1.2     | 2026-02-13 | Fixed Phase 4 week numbers (15-17), expanded risk section (PDF regression, SurfaceView/Compose, gesture conflicts), added SLO observation windows and sample sizes, corrected Phase 5 database task to verify migrations not remove nonexistent destructive fallback, added Phase 5 final device validation task                                                                                                                                                                                                                           |
| 1.3     | 2026-02-13 | Rejection fixes: corrected PDF threading claim (`rememberPdfBitmap` uses `Dispatchers.Default`), added concrete PDF integration call sites (`NoteEditorScreen.kt`, `NoteEditorShared.kt`, `NoteEditorPdfContent.kt`, `NoteEditorState.kt`, `HomeScreen.kt`), defined multi-page recognition behavior using `MyScriptPageManager.kt`, and specified Room v2→v3 migration + DAO/repository updates                                                                                                                                           |
| 1.4     | 2026-02-13 | Explicitly switched Phase 0/2 PDF fork selection to `Zoltaneusz/PdfiumAndroid` (v1.10.0), updated dependency coordinates/source strategy, and removed outdated fork-selection note                                                                                                                                                                                                                                                                                                                                                         |
| 1.5     | 2026-02-13 | Momus review fixes: corrected tile cache sizing math (byte-bounded with `sizeOf` override), added PdfiumAndroid thread-safety/lifecycle rules, added async pipeline request coalescing/cancellation, defined draw/pan/scroll interaction mode architecture, added thumbnail regeneration on cache eviction, clarified Room testing artifact                                                                                                                                                                                                |
| 1.6     | 2026-02-13 | Momus round 2 fixes: Pdfium `renderPageBitmap` coordinate contract verification in spike, text selection native bridge fallback, LruCache Mutex guards, tile invalidation stroke-width expansion, per-page clipping in continuous scroll, dynamic cache sizing for budget devices, predicted-point buffer separation, PDF tile bitmap config (ARGB_8888 vs RGB_565), canonical document-space coordinate system, per-note thumbnail throttling flag, Room schema export for v3                                                             |
| 1.7     | 2026-02-13 | Momus round 3 fixes: tile bitmap ref-safety (in-use guard before recycle), Compose pointer API clarification (PointerType.Stylus + pointerInteropFilter fallback), PDF page rotation normalization, global memory budget envelope, concurrency Mutex for inFlight map, MuPDF selection contingency for internal builds, nested scroll interop fallback, thumbnail regeneration rate cap                                                                                                                                                    |
| 1.8     | 2026-02-13 | Momus round 4 fixes: Phase 0 exit artifacts (mandatory parity report), decided bitmap ref-safety Strategy A (ImageBitmap conversion), in-flight bitmap memory cap (Semaphore(4)), LazyListState page tracking policy with anti-oscillation debounce, atomic thumbnail file write (temp+rename)                                                                                                                                                                                                                                             |
| 1.9     | 2026-02-13 | Momus round 5 fixes: corrected ImageBitmap lifecycle (cache stores Android Bitmap, `asImageBitmap()` at draw time only — wrapping without copy means immediate recycle invalidates ImageBitmap), added AndroidView clipping caveat for InProgressStrokesView (`View.setClipBounds`), added explicit Semaphore try/finally cancellation safety for async tile pipeline                                                                                                                                                                      |
| 2.0     | 2026-02-13 | Momus round 6 fixes: cancellation bitmap cleanup (recycle allocated bitmaps on coroutine cancel), Mutex lock scope guidance (keep short, never hold during render), definitive 512×512 tile size policy, stable `key(page.id)` for LazyColumn items, Room index/constraint specifications, Phase 0 rotation handling + RTL/ligature text selection validation, P5.8 low-RAM device pass + gesture conflict QA script + tile render time baseline                                                                                           |
| 2.1     | 2026-02-13 | Momus round 7 fixes: recalculated global memory budget table (25-35% of memoryClass target, added other-consumer overhead estimate), updated architecture diagram to 512×512, aligned PDF tile render P95 target to <100ms (SLO match), scale bucket policy clarified (3 buckets for both caches), Room FK constraints with ON DELETE actions, thumbnail file cleanup policy, LazyColumn contentType, bitmap.isRecycled draw guard, text selection scope (single-page only), renderPageBitmap negative offset fallback                     |
| 2.2     | 2026-02-13 | Momus round 8 fixes: corrected memory budget table to actually fit 25-35% target (reduced all tier caps), single InProgressStrokesView overlay architecture, zoom-to-bucket hysteresis band, PdfiumCore singleton lifecycle ownership, uncommitted stroke buffer policy on page unload, aligned downstream cache size references to budget table                                                                                                                                                                                           |
| 2.3     | 2026-02-13 | Momus round 9 fixes: corrected scale bucket references in architecture (5→3 buckets), aligned P5.8 validation tile render baseline to use 3 scale buckets (1x/2x/4x), added explicit fork lock with repository URL (https://github.com/Zoltaneusz/PdfiumAndroid.git), clarified stale tile count math references                                                                                                                                                                                                                           |
| 2.4     | 2026-02-13 | Momus round 10 fixes: added PRE-TASK to reduce code from 5→3 zoom buckets before P1.4, added runtime downshift rule for budget tier memory exception (onTrimMemory eviction), added Phase 0 exit criterion requiring runnable spike snippets for coordinate offsets and text-geometry, added explicit P4 sequencing (schema before UI), strengthened Pdfium thread-safety default assumption                                                                                                                                               |
| 2.5     | 2026-02-13 | Momus round 11 fixes: added Pdfium API surface caveat (getPageText/getPageRotation/closePage may not exist), marked pseudocode as ILLUSTRATIVE pending spike, created P4.0 for DB schema work (moved from P5.7), P5.7 now verification-only, added Priority Missing Tests section (tile invalidation, async cancellation, multi-page coords, v2→v3 migration)                                                                                                                                                                              |
| 2.6     | 2026-02-13 | Momus round 12 fixes: added P0.2.5 dependency viability check (minSdk 28 vs fork minSdk 30), fixed Maven coordinate to JitPack path only, added text-selection BINARY GATE (Java API/JNI/Not Feasible), corrected LruCache thread-safety language (per-op safe, multi-step needs Mutex), fixed rollback contradiction (forward-only acceptable), added test scope to bucket pre-task                                                                                                                                                       |
| 2.7     | 2026-02-13 | Momus round 13 fixes: added P0.0 Pre-Gate for fork viability (Gradle resolve, CI artifact, minSdk), added P2.0 renderer-agnostic text model task (before P2.2), elevated HomeScreen PDF import to MUST-COMPLETE, added text-selection FALLBACK DECISION options, added BLOCKING emphasis to bucket pre-task test update                                                                                                                                                                                                                    |
| 2.8     | 2026-02-13 | Added milestone exit gate + explicit non-goals, tightened phase exit gates/acceptance criteria, resolved folder/tag decisions, added missing file/test paths, added Phase 0 integration smoke + PDF corpus, clarified Pdfium dependency handling, fixed cache sizing diagram mismatch, strengthened release license and memory envelope checks                                                                                                                                                                                             |
| 2.9     | 2026-02-13 | Fixed Room v3 schema specs to match noteId-based schema, added DDL alignment checklist, clarified gesture routing integration, added explicit tile-range math contract, added task-level verification bullets, removed alternate-fork suggestions to honor locked Pdfium fork                                                                                                                                                                                                                                                              |
| 2.10    | 2026-02-13 | Aligned Gradle command paths and settings file location, clarified soft vs hard delete semantics for thumbnails/tags, tightened MuPDF release-build verification path, and updated Phase 4 acceptance for delete semantics                                                                                                                                                                                                                                                                                                                 |
| 2.11    | 2026-02-13 | Fixed Room migration SQL feasibility (notes table recreation), defined selection geometry as quads in page points, added clipboard UX requirement, specified spike project execution, removed reliance on missing telemetry tools (local measurements), and updated SLO/acceptance accordingly                                                                                                                                                                                                                                             |
| 2.12    | 2026-02-13 | Corrected current-state PDF scale bucket count to 5 (pre-task reduces to 3), eliminating doc inconsistency                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 2.13    | 2026-02-13 | Added Appendix D: Acknowledged Future Backlog — 9 gap items from source analysis docs explicitly tracked (unified search, sync-readiness, editor architecture debt, MyScript backlog, hierarchical folders, PDF annotation layer, PDF text spatial index, stylus advanced interactions, competitive editor tools). Updated non-goals to reference backlog.                                                                                                                                                                                 |
| 2.14    | 2026-02-13 | Momus review fixes: replaced all hardcoded line references in Appendix D with stable task IDs and section names (B2, B3, B4, B5, B7, B8), corrected B4 scratch-out citation (`:628`→`:632`), annotated `gemini-deep-research.txt:1` citations as single-line document (B5, B8, B9), added priority justification to B6, added 4 new gap items (B10–B13: conversion UX, interactive ink reflow, template system/toolbar ergonomics, recognition overlay + lasso-convert). Expanded B11/B12 range citations to discrete `file:line` entries. |

---

## Executive Summary

This plan consolidates findings from 5 AI-driven competitor analyses (Codex, Chat, Gemini) and comprehensive codebase scouting to architect a unified overhaul of Onyx's Android canvas, ink rendering, stroke handling, and PDF subsystems. The goal is to match or exceed competitors (Samsung Notes, Notability, GoodNotes, Notewise) in perceived latency, stroke quality, and document handling.

**Key Deliverables:**

1. Sub-20ms perceived ink latency (currently 50-100ms+)
2. Production-quality stroke rendering with pressure/velocity variation
3. Tile-based PDF rendering with PdfiumAndroid (replacing MuPDF/AGPL)
4. Continuous vertical scroll for multi-page documents
5. Home/Library UX overhaul with thumbnails, folders, and batch operations

Reference context: `docs/architecture/full-project-analysis.md` and `V0-api.md` (repo root).

---

## Table of Contents

0. [Milestone Exit Gate (Definition of Done)](#0-milestone-exit-gate-definition-of-done)
1. [Current State Analysis](#1-current-state-analysis)
2. [Gap Analysis Matrix](#2-gap-analysis-matrix)
3. [Technical Architecture](#3-technical-architecture)
4. [Implementation Phases](#4-implementation-phases)
5. [Phase 1: Ink Latency & Rendering](#5-phase-1-ink-latency--rendering)
6. [Phase 2: PDF Overhaul](#6-phase-2-pdf-overhaul)
7. [Phase 3: Multi-Page & Navigation](#7-phase-3-multi-page--navigation)
8. [Phase 4: Home/Library UX](#8-phase-4-homelibrary-ux)
9. [Phase 5: Polish & Performance](#9-phase-5-polish--performance)
10. [Risk Assessment](#10-risk-assessment)
11. [Success Metrics & SLOs](#11-success-metrics--slos)
12. [Dependencies & Prerequisites](#12-dependencies--prerequisites)
13. [Appendix A: File Change Summary](#appendix-a-file-change-summary)
14. [Appendix B: Test Plan](#appendix-b-test-plan)
15. [Appendix C: Rollback Plan](#appendix-c-rollback-plan)
16. [Appendix D: Acknowledged Future Backlog](#appendix-d-acknowledged-future-backlog)

---

## 0. Milestone Exit Gate (Definition of Done)

This milestone is not shippable until ALL of the following are true:

- Release build contains no AGPL/MuPDF code or dependencies (verify via Gradle dependency report + APK/AAB scan).
- PDF import via `HomeScreen.kt` works on-device for the canonical PDF corpus.
- Existing notes (ink + PDF) render identically to pre-milestone behavior; no data loss across v2→v3 migration.
- No OOM/ANR during the budget-tier stress test (10-page PDF pan/zoom + continuous scroll).
- All phase exit gates (P0–P5) and acceptance criteria pass on at least 2 physical devices.

### Scope Boundaries (Explicit Non-Goals)

- Unified search across handwritten content and PDF text is out of scope for this milestone (PDF parity is limited to text selection; no search indexing or search UI). See Appendix D, item B1.
- Stroke compression for >1000 points is deferred (data architecture only; no implementation in this milestone).
- Continuous scroll applies to PDF-backed notes only; blank/ink-only notes remain single-page for now.
- Sync-readiness infrastructure (Lamport increment hardening, deviceId embedding, op log) is acknowledged but deferred. See Appendix D, item B2.
- Editor architecture debt (NoteEditorUi.kt split, Home VM extraction, DI hardening) is deferred. See Appendix D, item B3.
- MyScript advanced features (debounced recognition, recognizeAll, ContentPackage cleanup, configurable language, JIIX indexing, scratch-out) are deferred. See Appendix D, item B4.
- Hierarchical folders, breadcrumbs, and drag-drop folder operations are deferred; this milestone ships flat folders only. See Appendix D, item B5.
- PDF annotation persistence as a separate layer from normal ink is deferred. See Appendix D, item B6.
- PDF text hit-testing spatial index optimization is deferred. See Appendix D, item B7.
- Stylus advanced interactions (button quick-erase, hover cursor) and palm rejection implementation are deferred. See Appendix D, item B8.
- Competitive editor tools (lasso transform, shape tool, tape tool, zoom box, segment eraser) are deferred. See Appendix D, item B9.
- Handwriting-to-text conversion UX (select → convert → edit round-trip) is deferred; recognition is background-only for search. See Appendix D, item B10.
- Interactive Ink features (ink reflow, math beautification, gesture-based editing) are deferred. See Appendix D, item B11.
- Page template system (configurable grids, lines, dot patterns) and floating/dockable toolbar are deferred. See Appendix D, item B12.
- Recognition overlay display and lasso-convert pipeline are deferred. See Appendix D, item B13.

## 1. Current State Analysis

### 1.1 Ink Rendering (Scouted: `apps/android/.../ink/`)

**Architecture:** Dual-layer hybrid

- **Layer 1:** `InProgressStrokesView` (Jetpack Ink 1.0.0-alpha02) for active strokes
- **Layer 2:** Compose `Canvas` for finished strokes with path caching

**What Works:**

- Catmull-Rom spline interpolation (C1 continuous) - FIXED
- Per-point pressure-based width with gamma curve (0.6) - FIXED
- Start/end tapering over first/last 5 points - FIXED
- Variable-width filled outline rendering - FIXED
- Color caching (64-entry LRU) - FIXED
- Path cache bounded at 500 entries - FIXED
- Highlighter blend mode (Multiply + 0.35 alpha) - FIXED

**What's Broken/Missing:**
| Issue | Severity | Status |
|-------|----------|--------|
| Motion prediction disabled (`ENABLE_PREDICTED_STROKES = false`) | CRITICAL | Not fixed |
| No front-buffered rendering for active strokes | CRITICAL | Not fixed |
| Stroke pop-in on pen-up (dual-layer visual gap) | HIGH | Partially addressed |
| No tile-based caching for committed strokes | HIGH | Not implemented |
| InProgressStrokesView style doesn't match Compose rendering | MEDIUM | Not fixed |
| No frame budget management | MEDIUM | Not implemented |

### 1.2 PDF Implementation (Scouted: `apps/android/.../pdf/`)

**Current Library:** MuPDF (`com.artifex.mupdf:fitz:1.24.10`)

- **License:** AGPL-3.0 (copyleft - problematic for commercial distribution)
- **Integration:** Direct Java API via fitz package

**Rendering Pipeline:**

- Full-page bitmap rendering (no tiling)
- LruCache: 64 MiB for bitmaps, 12 entries for StructuredText
- 5 render scale buckets: 1x, 1.5x, 2x, 3x, 4x (current; will reduce to 3 in P1.4 pre-task)
- Max 16MP pixel limit
- Full-page render work runs on `Dispatchers.Default` in `rememberPdfBitmap()` (`NoteEditorShared.kt`), not directly on main
- UI still waits for each full-page bitmap result before showing crisp content at a new zoom bucket, which causes visible stutter

**Current Integration Call Sites (must be replaced in Phase 2):**

- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
  - `rememberPdfState(...)` creates `PdfRenderer(...)`
  - `DisposePdfRenderer(...)` closes renderer
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt`
  - `rememberPdfBitmap(...)` computes render bucket and requests full-page bitmap
  - `resolvePdfRenderScale(...)` + `zoomToRenderScaleBucket(...)` control scale buckets
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`
  - `PdfPageLayers(...)` draws a single scaled bitmap
  - selection handlers call `PdfRenderer` text APIs directly
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`
  - PDF state currently embeds MuPDF text/quad types

**What's Broken/Missing:**
| Issue | Severity | Status |
|-------|----------|--------|
| AGPL license requires source disclosure or replacement | CRITICAL | Not fixed |
| Full-page re-render per zoom bucket blocks visual update until bitmap is ready (jank) | HIGH | Not fixed |
| No tile-based rendering | HIGH | Not implemented |
| No thumbnail generation | MEDIUM | Not implemented |
| No PDF text search UI | MEDIUM | Not implemented |
| No outline/bookmark navigation | MEDIUM | Not implemented |
| Single-page lock (no continuous scroll) | HIGH | Not implemented |

### 1.3 Stroke Data Model (Scouted: `apps/android/.../ink/model/`, `.../data/`)

**Architecture:**

- Domain models: `Stroke`, `StrokePoint`, `StrokeStyle`, `StrokeBounds`
- Persistence: Room with `StrokeEntity` + Protobuf serialization
- Serialization: Binary Protobuf (NOT JSON/base64 - this is good)
- Database: `OnyxDatabase` (version 2, with explicit migration)

**What Works:**

- Protobuf serialization is efficient
- Lamport timestamps for CRDT ordering
- Proper separation of domain/persistence models
- Explicit migration path (MIGRATION_1_2) - no destructive fallback

**What's Broken/Missing:**
| Issue | Severity | Status |
|-------|----------|--------|
| No stroke compression for large documents | MEDIUM | Not implemented |
| ViewModels instantiated inside composables (DI issue) | LOW | Identified |

**Note:** Previous analysis incorrectly cited "destructive migration fallback in AppDatabase" - this was wrong. The actual database class is `OnyxDatabase` at `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt` and it uses explicit migrations without destructive fallback.

### 1.4 Home/Library UX (Scouted: `apps/android/.../ui/HomeScreen.kt`)

**Current State:**

- Flat list of notes sorted by `updatedAt` DESC
- Text-only cards (no thumbnails)
- Single-item delete via long-press menu
- Search via FTS on recognized handwriting text

**What's Broken/Missing:**
| Issue | Severity | Status |
|-------|----------|--------|
| No thumbnail previews | HIGH | Not implemented |
| No folder hierarchy | HIGH | Not implemented |
| No tags/labels | MEDIUM | Not implemented |
| No multi-select/batch operations | MEDIUM | Not implemented |
| No sort options (name, created) | LOW | Not implemented |

**Note:** Sort by "size" removed from scope - `NoteEntity` does not include a size column and adding it requires additional maintenance overhead.

---

## 2. Gap Analysis Matrix

### Competitor Comparison

| Feature                     | Samsung Notes | Notewise    | GoodNotes | Onyx (Current) | Gap      |
| --------------------------- | ------------- | ----------- | --------- | -------------- | -------- |
| **Ink Latency**             | <10ms (S-Pen) | <20ms       | <20ms     | 50-100ms       | CRITICAL |
| **Stroke Smoothing**        | Bezier        | Catmull-Rom | Bezier    | Catmull-Rom    | OK       |
| **Pressure Variation**      | Excellent     | Excellent   | Excellent | Good (fixed)   | OK       |
| **Start/End Taper**         | Yes           | Yes         | Yes       | Yes (fixed)    | OK       |
| **Motion Prediction**       | Yes           | Yes         | Yes       | DISABLED       | CRITICAL |
| **PDF Tiling**              | Yes           | Yes         | Yes       | No             | HIGH     |
| **Continuous Scroll**       | Yes           | Yes         | Yes       | No             | HIGH     |
| **Thumbnails**              | Yes           | Yes         | Yes       | No             | HIGH     |
| **Folders**                 | Yes           | Yes         | Yes       | No             | HIGH     |
| **PDF Text Search**         | Yes           | Yes         | Yes       | Partial        | MEDIUM   |
| **Highlighter Readability** | Excellent     | Good        | Good      | Good (fixed)   | OK       |

### AI Analysis Concordance

All 5 AI analyses agree on these critical issues:

1. **Ink latency is unacceptable** - motion prediction must be enabled
2. **PDF rendering needs tiling** - full-page bitmap is a dead end
3. **MuPDF license is problematic** - must replace with PdfiumAndroid
4. **No continuous scroll** - major UX gap vs competitors
5. **Home screen needs thumbnails** - text-only is not competitive

Divergent recommendations:
| Topic | Codex Plan | Chat Plan | This Plan Decision |
|-------|------------|-----------|-------------------|
| MyScript integration | Offscreen only | Offscreen only | **Offscreen only** (agree) |
| Rendering backend | Custom + Jetpack Ink | InkRenderer interface | **Jetpack Ink + Compose hybrid** (current, optimize) |
| Tile cache key | (pageId, tileX, tileY, scale) | (pageId, tileX, tileY, scaleBucket) | **scaleBucket** (fewer cache misses) |
| Phase duration | 20 weeks | 5 phases (unspecified) | **20 weeks** (realistic with device validation buffer) |

---

## 3. Technical Architecture

### 3.1 Target Ink Rendering Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Touch Input (Stylus/Finger)                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  MotionPredictionAdapter (RE-ENABLE)                            │
│  - Predict 2-3 frames ahead based on velocity                   │
│  - Cancel predicted segments on pen-up                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
┌─────────────────────────────┐ ┌─────────────────────────────────┐
│  InProgressStrokesView      │ │  Compose Canvas (Finished)      │
│  (Jetpack Ink Layer)        │ │  (Tile-cached Layer)            │
│  - May already use low-lat  │ │  - 512×512 tiles (decided)          │
│  - Predicted + actual pts   │ │  - Scale-bucketed (1x,2x,4x)        │
│  - Styled to match Compose  │ │  - LRU eviction (byte-bounded)       │
└─────────────────────────────┘ └─────────────────────────────────┘

**Note:** `InProgressStrokesView` is embedded via `AndroidView` in Compose, not a `SurfaceView`. Phase 1 will investigate whether Jetpack Ink already provides low-latency rendering internally before attempting custom `GLFrontBufferedRenderer` integration, which would require a `SurfaceView` and may break Compose layering.
                    │                       │
                    └───────────┬───────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Compositor (SurfaceFlinger)                   │
│                   60-120Hz output                               │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Target PDF Architecture

**CRITICAL: PDF Text Selection Parity Requirement**

The current MuPDF implementation provides text selection via `StructuredText` API used in:

- `PdfRenderer.kt`: `extractTextStructure()`, `findCharAtPagePoint()`, `getSelectionQuads()`, `extractSelectedText()`
- `NoteEditorPdfContent.kt`: Long-press drag selection with highlight overlay
- `NoteEditorState.kt`: `TextSelection` data class with quads

**PdfiumAndroid MUST provide equivalent APIs or this feature will regress.** The Week 0 spike must verify:

1. Text extraction API exists (`pdfiumCore.getPageText()` or similar)
2. Character-level position lookup is possible
3. Selection quads can be computed for highlight rendering

```
┌─────────────────────────────────────────────────────────────────┐
│                      PdfiumAndroid Core                         │
│  (Zoltaneusz/PdfiumAndroid v1.10.0)                              │
│  - Apache 2.0 / BSD license (verify in spike)                   │
│  - Native Chromium PDFium bindings                              │
│  - MUST support: text extraction, char positions, selection     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PdfTileRenderer                            │
│  - Tile size: 512x512 px (configurable)                         │
│  - Async rendering on Dispatchers.IO                            │
│  - Scale buckets: 1x, 2x, 4x (DECIDED)                          │
│  - Cache key: (pageIndex, tileX, tileY, scaleBucket)            │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PdfTileCache                               │
│  - Byte-bounded LruCache<TileKey, Bitmap> (64 MiB high-end tier) │
│  - sizeOf() uses Bitmap.allocationByteCount                      │
│  - Bitmap recycling on eviction via entryRemoved()               │
│  - Pre-fetch adjacent tiles during pan                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      LazyColumn (Continuous Scroll)             │
│  - Per-page items with ink overlay                              │
│  - Virtualized rendering (only visible pages)                   │
│  - Page gap: 16dp grey separator                                │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Target Data Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Domain Models                              │
│  Stroke, StrokePoint, StrokeStyle, StrokeBounds                 │
│  (unchanged - current design is solid)                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      StrokeRepository                           │
│  - Protobuf serialization (keep)                                │
│  - Add: stroke compression for >1000 points (DEFERRED; post-milestone) │
│  - Add: batch insert/delete operations                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Room Database (OnyxDatabase)               │
│  - Current: version 2 with explicit MIGRATION_1_2               │
│  - Target: version 3 with explicit MIGRATION_2_3                │
│  - ADD: FolderEntity for hierarchy                              │
│  - ADD: TagEntity + NoteTagCrossRef                             │
│  - ADD: ThumbnailEntity (bitmap path + hash)                    │
│  - All migrations must be explicit (no destructive fallback)    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Implementation Phases

### Global Memory Budget Envelope

**CRITICAL:** Stroke tile cache + PDF tile cache + thumbnail cache must NOT compound to OOM on mid-tier devices. Define a global budget split based on `ActivityManager.getMemoryClass()`:

| Device Tier | `memoryClass` | Stroke Tiles | PDF Tiles | Thumbnails | Total   | % of memoryClass (worst-case) |
| ----------- | ------------- | ------------ | --------- | ---------- | ------- | ----------------------------- |
| High-end    | ≥384 MB       | 32 MiB       | 64 MiB    | 16 MiB     | 112 MiB | ~29% of 384                   |
| Mid-tier    | 192-383 MB    | 24 MiB       | 48 MiB    | 8 MiB      | 80 MiB  | ~33% of 256 (midpoint)        |
| Budget      | <192 MB       | 16 MiB       | 32 MiB    | 4 MiB      | 52 MiB  | ~41% of 128                   |

- **Target: total bitmap cache ≤ 25-35% of `memoryClass` on all tiers.** The table above shows worst-case (all caches simultaneously full). Budget tier exceeds 35% target slightly at 41% — this is an **intentional exception** because: (1) budget devices rarely have all three caches at max simultaneously, (2) the thumbnail cache (4 MiB) rarely fills, and (3) we include a **runtime downshift rule**: if `ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` fires, immediately evict 50% of each cache. This provides a safety valve without over-constraining normal operation.
- **Other bitmap consumers (not in cache budget):** MyScript recognition bitmaps, Compose layer caches, preview bitmaps. Estimate ~20-30 MiB overhead. Combined with cache max, total bitmap usage should stay under `memoryClass * 0.50`.
- Split ratio: ~50% PDF tiles / ~30% stroke tiles / ~15% thumbnails (approximate, based on usage patterns: PDF tiles are largest and most frequently accessed)
- Query `ActivityManager.getMemoryClass()` once at app startup and pass budget values to each cache constructor
- Monitor: log total bitmap allocation at 10s intervals in debug builds to catch budget violations early

### Timeline Overview (20 weeks)

```
Week  0:    Phase 0 - PdfiumAndroid Parity Spike (GATING)
Week  1-5:  Phase 1 - Ink Latency & Rendering
Week  6-10: Phase 2 - PDF Overhaul (PdfiumAndroid + Tiling)
Week 11-14: Phase 3 - Multi-Page & Navigation
Week 15-17: Phase 4 - Home/Library UX
Week 18-20: Phase 5 - Polish, Performance & Device Validation
```

**Contingency:** If Phase 0 verdict is `JNI_BRIDGE_REQUIRED`, add +1–2 weeks and shift Phases 2–5 accordingly.

### Phase Dependencies

```
Phase 0 (Spike) ─── GATING ───▶ Phase 2 (PDF)
                                    │
Phase 1 (Ink) ──────────────────────┼──▶ Phase 5 (Polish)
                                    │
Phase 3 (Multi-Page) ◀── Phase 2 ───┤
                                    │
Phase 4 (Home UX) ◀── Phase 3 ──────┘
```

---

## 4.5 Phase 0: PdfiumAndroid Parity Spike (GATING)

**Duration:** 1 week  
**Goal:** Verify PdfiumAndroid can replace MuPDF without feature regression

### [ ] P0.0 Pre-Gate: Fork Viability (FIRST — before any spike work)

**This gate must pass before starting P0.1 spike tasks.**

- [ ] **Resolve exact Gradle coordinate:** Verify `com.github.Zoltaneusz:PdfiumAndroid:v1.10.0` resolves from JitPack. If not:
  - Try alternate casing: `com.github.zoltaneusz:pdfium-android:1.10.0`
  - Check if repository is public and JitPack-enabled
  - If unresolvable, escalate to repo owner/infra; do NOT change forks
- [ ] **Repository access confirmation:** Ensure `https://github.com/Zoltaneusz/PdfiumAndroid.git` is reachable for the team. If the fork is private or not publicly resolvable, set up an internal mirror or vendored source — **do NOT change the fork choice.**
- [ ] **CI artifact resolvability:** Add the dependency to a test branch and verify `./apps/android/gradlew :app:dependencies` can resolve it. If blocked by private repo or missing JitPack build, this is a hard stop.
- [ ] **Repository configuration:** Ensure JitPack is configured in `apps/android/settings.gradle.kts` (`dependencyResolutionManagement.repositories { maven("https://jitpack.io") }`). It is not present today — add it explicitly and record the change.
- [ ] **minSdk compatibility:** Confirm fork's `minSdkVersion` is ≤28 (see P0.2.5 for detailed handling if not)
- [ ] **Document outcome:** Record the exact working coordinate in `.sisyphus/notepads/pdfium-spike-report.md` section 0

**If P0.0 fails:** Escalate immediately. Do NOT proceed to P0.1 until a resolvable fork is identified. Phase 2 is blocked; Phase 1 may proceed in parallel. Adjust timeline by +1–3 weeks depending on resolution.

### [ ] P0.1 Tasks

- [ ] **Create test project** with `Zoltaneusz/PdfiumAndroid` (tag `v1.10.0`). **Location:** `apps/android/spikes/pdfium-spike/` (standalone Gradle project with its own `settings.gradle.kts` + wrapper; NOT included in `apps/android/settings.gradle.kts`).
  - **Run:** `cd apps/android/spikes/pdfium-spike && ./gradlew test` (or a minimal `./gradlew run` task) and capture output in the spike report.
- [ ] **Verify API parity:**
  - [ ] `newDocument()` / `closeDocument()` - document lifecycle
  - [ ] `openPage()` / page lifecycle
  - [ ] `renderPageBitmap()` - basic rendering
    - [ ] **Coordinate contract:** Verify whether `startX`/`startY` params accept negative values for tile offset rendering. Determine if coordinates are in device pixels or page points. If negative offsets are not supported, plan a "render full-page region then clip" fallback for tile extraction.
  - [ ] `getPageText()` or equivalent - text extraction
  - [ ] Character-level position lookup for selection (CRITICAL)
    - [ ] If `getPageText()` only returns flat text without per-character bounding boxes, evaluate whether a native JNI bridge to Pdfium's `FPDFText_GetCharBox()` / `FPDFText_GetCharOrigin()` is feasible. Document the effort estimate.
  - [ ] `getTableOfContents()` - bookmark/outline navigation
- [ ] **Verify compatibility:**
  - [ ] Android 15 / 16KB page size support
  - [ ] ABIs: arm64-v8a, armeabi-v7a, x86_64
  - [ ] Large PDF performance (>100 pages)
  - [ ] Password-protected PDF handling
  - [ ] **Thread safety:** Determine if `PdfiumCore` is thread-safe for concurrent page renders (one page per coroutine) or requires a single-threaded access pattern. Document findings explicitly — Phase 2 architecture depends on this.
  - [ ] **Page lifecycle:** Verify open/close semantics — does `openPage()` return a handle that must be closed? Can multiple pages be open simultaneously? Document the contract.
- [ ] **License verification:**
  - [ ] Confirm Apache 2.0 / BSD-3 license
  - [ ] Document attribution requirements

### [ ] P0.2 Go/No-Go Decision

**Note on documentation:** No Context7/official docs exist for `Zoltaneusz/PdfiumAndroid`. The spike must rely on the fork's README, source code, and Javadoc/KDoc in the repository itself. Inspect the `PdfiumCore` class directly for available methods.

| Outcome                  | Action                                                        |
| ------------------------ | ------------------------------------------------------------- |
| All APIs available       | Proceed to Phase 2                                            |
| Text selection missing   | Build JNI bridge on locked fork (or wrapper on locked fork)   |
| Critical incompatibility | Escalate - consider commercial license or alternative library |

### [ ] P0.2.5 Dependency Viability Check (BEFORE P0.3)

**BLOCKING CHECK:** Verify `Zoltaneusz/PdfiumAndroid` can be consumed with app `minSdk = 28`:

- Current app: `minSdk = 28` (`apps/android/app/build.gradle.kts:16`)
- Fork may require `minSdkVersion 30` in its Gradle module
- **Actions if incompatible:**
  1. **Option A (preferred):** Fork/patch the library to lower its minSdk (if only manifest min is the blocker, not actual API usage)
  2. **Option B:** Raise app minSdk to 30 (document user impact: drops Android 9-10 device support)
  3. **Option C:** Vendor the locked fork as source module and adjust minSdk/build if JitPack artifact is incompatible
- **Decision gate:** If Option B is required, get explicit approval before proceeding. Do NOT silently bump minSdk.

### [ ] P0.3 Exit Artifacts (MANDATORY before Phase 2 starts)

**FORK LOCK (DECIDED):** The Pdfium fork is locked to `Zoltaneusz/PdfiumAndroid`:

- **Repository:** `https://github.com/Zoltaneusz/PdfiumAndroid.git`
- **Maven coordinate (via JitPack):** `com.github.Zoltaneusz:PdfiumAndroid:v1.10.0`
- **Git tag:** `v1.10.0` (verify latest stable in spike; update if newer stable exists)
- **API surface checklist (verify in spike):**
  - [ ] `PdfiumCore.newDocument(fd)` / `newDocument(bytes)`
  - [ ] `openPage(doc, pageIndex)` / `closePage(doc, pageIndex)`
  - [ ] `renderPageBitmap(doc, page, bitmap, startX, startY, width, height, ...)`
  - [ ] `getPageText(doc, page)` — flat text extraction
  - [ ] Text geometry APIs (`getCharBox`, `getCharOrigin`, etc.) — may require JNI bridge
  - [ ] `getTableOfContents(doc)` — for outline/bookmarks
  - [ ] `getPageRotation(doc, page)` — for rotation normalization

The spike MUST produce a short parity report (markdown, saved to `.sisyphus/notepads/pdfium-spike-report.md`) that records:

- **Canonical PDF test corpus:** Create `.sisyphus/notepads/pdf-test-corpus.md` listing 3–5 PDFs used for ALL text/rotation/selection validation (simple Latin, rotated pages, RTL/ligatures, mixed fonts, scanned/no-text). Use the same corpus across P0, P2, and P5 acceptance.

**EXIT CRITERION (MANDATORY):** The spike is NOT complete until it includes a **runnable code snippet** demonstrating:

- Coordinate offset behavior (`renderPageBitmap` with non-zero `startX`/`startY`)
- Text geometry extraction (per-character bounding boxes or documented fallback)

Without these snippets, Phase 2 cannot start — selection parity is the biggest technical unknown.

1. **`renderPageBitmap()` coordinate contract:** Do `startX`/`startY` accept negative values? Are they device pixels or page points? Include a working code snippet. **If negative offsets are NOT supported:** document a matrix-based fallback — render the full page (or oversized region) into a bitmap and crop the tile region. This is less efficient but functionally correct. Measure the overhead vs direct tile rendering.
2. **Text selection geometry:** Does `getPageText()` return per-character bounding boxes? If not, what native APIs are available (`FPDFText_GetCharBox`, etc.)? Is a JNI bridge needed?
   - **BINARY GATE (MANDATORY VERDICT):** Record one of:
     - **JAVA_API_SUFFICIENT**: Per-char geometry available via Java API → proceed to Phase 2 without JNI work
     - **JNI_BRIDGE_REQUIRED**: JNI bridge needed → add P2.X JNI task, estimate +1-2 weeks, get approval before Phase 2
     - **NOT_FEASIBLE**: Text selection geometry not achievable with this fork → escalate, consider MuPDF fallback for selection
   - **FALLBACK DECISION (if NOT_FEASIBLE):** If text selection parity cannot be achieved with Pdfium alone:
     - **Option A (preferred):** Keep MuPDF for text selection ONLY (internal/debug builds only due to AGPL), use Pdfium for rendering. Document AGPL compliance plan.
     - **Option B:** Accept reduced text selection UX (e.g., "select all text on page" instead of character-level selection). Document user impact.
     - **Option C:** Defer full MuPDF removal until JNI bridge is built. Extend timeline by 1-2 weeks.
     - Record chosen option in spike report with stakeholder approval.
3. **Thread-safety findings:** Is `PdfiumCore` thread-safe? Can multiple pages be open concurrently? What locking is needed? **Default assumption (until spike proves otherwise): assume NOT thread-safe.** Serialize all access per-document until spike demonstrates safe concurrent access patterns.
4. **Page lifecycle contract:** Does `openPage()` return a handle? Must `closePage()` be called? Can a page be re-opened after close?
5. **License confirmation:** Exact license text and attribution requirements.
6. **Performance baseline:** Time to render a single 512×512 tile on target device(s).
7. **Rotation handling:** Verify `renderPageBitmap` behavior on rotated pages (90°/180°/270°). Does the API auto-rotate, or must the caller apply a rotation matrix? Document the coordinate space for text selection boxes on rotated pages.
8. **Text selection edge cases:** Test text selection on PDFs with ligatures, RTL text (Arabic/Hebrew), and mixed fonts. If Pdfium collapses glyph bounding boxes or returns incorrect geometry for complex scripts, document the limitations and expected UX fallback (e.g., "select-all on page" or "copy full paragraph").
9. **Go/No-Go recommendation** with rationale.

This report prevents re-litigation of spike findings during Phase 2 implementation.

### [ ] P0.4 App Integration Smoke (BEFORE Phase 2 starts)

- **Goal:** Prove Pdfium can render inside the app context (not just the spike project)
- **Actions:**
  - Add the Pdfium fork dependency to the app (debug/spike branch only)
  - Open a PDF from the canonical corpus via `HomeScreen.kt` import path
  - Render at least one page tile and verify no crash on basic zoom/pan
  - If text APIs exist, verify a simple long-press selection on the same page
- **Output:** Record results in `.sisyphus/notepads/pdfium-spike-report.md` section 4

---

## 5. Phase 1: Ink Latency & Rendering

**Duration:** 5 weeks  
**Goal:** Reduce perceived latency from 50-100ms to <20ms

### 5.1 Tasks

#### [ ] P1.1: Re-enable Motion Prediction (Week 1-2)

- **Files:** TWO flags must be toggled:
  - `InkCanvasTouch.kt` line 29: `ENABLE_PREDICTED_STROKES = true`
  - `InkCanvas.kt` line 62: `ENABLE_MOTION_PREDICTION = true`
- **Refactor:** Consider consolidating into single config source
- **Challenge:** Pen-up stabilization - predicted points must be cancelled and snapped to real endpoint
- **Implementation:**

  ```kotlin
  // On pen-up:
  // 1. Remove all predicted points from InProgressStrokesView
  // 2. Rebuild stroke with only real points
  // 3. Apply Catmull-Rom smoothing to real points only
  // 4. Commit to Compose canvas
  ```

- **Predicted-point buffer separation (CRITICAL):** Predicted points MUST be stored in a separate buffer from real input points at all times. Never append predicted points to the committed stroke point list. On each motion event: (1) replace previous predicted points with new predictions, (2) append real points to the real buffer. On pen-up: discard all predicted points, finalize only the real buffer. This prevents double-smoothing artifacts where predicted points leak into the Catmull-Rom interpolation of committed strokes.

- **Test:** Visual inspection on physical device - no "snap back" on pen lift

#### [ ] P1.2: Align In-Progress vs Finished Stroke Styles (Week 1-2)

- **Files (exact call sites):**
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasDrawing.kt`:
    - `Brush.toInkBrush(...)`
    - `computePerPointWidths(...)` (Compose reference pressure curve: `PRESSURE_GAMMA = 0.6f`)
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt`:
    - `createStrokeInput(...)` (in-progress pressure feed into Jetpack Ink)
    - `handlePointerDown(...)` + `handlePredictedStrokes(...)` (`startStroke(..., toInkBrush(...))`)
  - `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` (wiring and alpha constants)
- **Problem:** InProgressStrokesView uses Jetpack Ink's default brush; Compose uses custom variable-width path
- **Implementation contract (remove guesswork):**
  - Keep `Brush.toInkBrush(...)` as the single mapping function for family/color/size/epsilon.
  - Apply the same gamma used by Compose (`PRESSURE_GAMMA = 0.6`) before passing pressure into Jetpack Ink (`createStrokeInput(...)`) so in-progress width response matches committed stroke response.
  - Keep predicted/in-progress alpha explicitly mapped to existing constants (`PREDICTED_STROKE_ALPHA`, `IN_PROGRESS_STROKE_ALPHA`) and verify highlighter alpha parity against Compose (`HIGHLIGHTER_STROKE_ALPHA`).
  - If Jetpack Ink ignores custom pressure shaping, document this explicitly and switch to a tolerance-based match (width/error threshold) rather than claiming exact parity.
- **Alternative:** Render in-progress strokes in Compose as well (may add latency)
- **Verification path:** instrument debug logging for pressure in `[0.2, 0.5, 0.8]` and compare in-progress stroke width vs committed stroke width on same brush config (target <=10% width delta at each sample point)

#### [ ] P1.3: Front-Buffered Rendering Investigation (Week 2)

- **Goal:** Determine if `GLFrontBufferedRenderer` can reduce latency further
- **Constraint:** Jetpack Ink's `InProgressStrokesView` may already use front-buffering internally
- **Action:** Profile with Perfetto/systrace to measure actual frame latency
- **Decision gate (binary):** If P95 latency improvement vs baseline is <5ms OR GL front-buffering requires `SurfaceView` that breaks Compose layering, skip custom GL implementation. If improvement ≥5ms AND no layering regressions on Tab S9, proceed with GLFrontBufferedRenderer spike.
- **Record:** Save trace + decision in `.sisyphus/notepads/ink-latency-investigation.md`

#### [ ] P1.4: Tile-Based Stroke Caching (Week 3-4)

- **File:** New `apps/android/app/src/main/java/com/onyx/android/ink/cache/StrokeTileCache.kt`
- **Design:**

  ```kotlin
  data class StrokeTileKey(
      val pageId: String,
      val tileX: Int,
      val tileY: Int,
      val scaleBucket: Float, // 1.0, 2.0, 4.0
  )

  class StrokeTileCache(maxSizeBytes: Int = 32 * 1024 * 1024) {
      // Byte-bounded LruCache: 512×512 ARGB_8888 tile ≈ 1 MB each
      // 32 MiB budget ≈ 32 tiles at high-end tier
      private val cache = object : LruCache<StrokeTileKey, Bitmap>(maxSizeBytes) {
          override fun sizeOf(key: StrokeTileKey, value: Bitmap): Int =
              value.allocationByteCount
          override fun entryRemoved(evicted: Boolean, key: StrokeTileKey, oldValue: Bitmap, newValue: Bitmap?) {
              if (evicted) oldValue.recycle()
          }
      }

      fun getTile(key: StrokeTileKey): Bitmap?
      fun putTile(key: StrokeTileKey, bitmap: Bitmap)
      fun invalidatePage(pageId: String)
      fun invalidateStroke(pageId: String, strokeBounds: RectF)
  }
  ```

- **Tile size decision (DECIDED: 512×512):** The `V0-api.md` (repo root) specifies `tileSize: 2024`, but a single 2024×2024 ARGB_8888 tile is ~16.4 MB. With a 64 MiB cache budget this yields only ~3-4 cached tiles, which is insufficient for smooth panning. **Use 512×512 tiles (~1 MB each, ~64 tiles in cache) for both stroke and PDF tile rendering.** The 2024 value from V0-api.md applies to a different context (possibly full-page raster) and is NOT used for tiled rendering. All invalidation math, cache budgets, and memory caps in this plan assume 512×512 tiles. If a different tile size is chosen during implementation (e.g., after profiling), update the cache budget, memory cap, and Semaphore limits accordingly.
- **Cache policy:** Byte-bounded via `sizeOf` override using `Bitmap.allocationByteCount`. All bitmap evictions must call `recycle()` to release native memory.
- **Thread safety:** `android.util.LruCache` is thread-safe per individual operation (get/put/remove), but **multi-step atomic logic requires synchronization**. Guard check-then-put patterns and recycle races with a `Mutex` (Kotlin coroutines). Specifically: (1) check-if-exists + put-if-missing must be atomic, (2) get + recycle must not race with concurrent get. **Lock scope:** Keep `Mutex.withLock` blocks as SHORT as possible — only around the multi-step logic. NEVER hold the Mutex during `renderTile()` or `asImageBitmap()` — this would serialize all renders and stall frames. Pattern: `mutex.withLock { cache.get(key) ?: renderAndPut() }` is wrong; instead `val existing = cache.get(key); if (existing != null) return existing; val new = render(); mutex.withLock { cache.get(key) ?: cache.put(key, new).also { return new } }`.
- **Eviction thread caution:** `entryRemoved()` runs on the thread that mutates the cache (often UI). Keep eviction lightweight. If main-thread jank is observed, queue `recycle()` to a background dispatcher (ensuring no draw is in-flight).
- **Note on zoom crispness:** Using scale buckets (1x, 2x, 4x) means intermediate zooms will show slightly scaled tiles. Accept this tradeoff to reduce cache thrashing. If crispness is critical, consider finer buckets (1x, 1.5x, 2x, 3x, 4x) at the cost of more re-rasterization.
  - **Scale bucket policy (DECIDED):** Stroke tiles use 3 buckets (1x, 2x, 4x). PDF tiles use the SAME 3 buckets for cache simplicity. Both tile caches use `StrokeTileKey.scaleBucket` / `PdfTileKey.scaleBucket` with identical bucket values. If PDF tiles need finer granularity (e.g., text crispness at 1.5x), add 1.5x and 3x to PDF buckets ONLY, but update PDF cache budget accordingly (more buckets = more cached tiles needed).
  - **PRE-TASK (MANDATORY before P1.4):** Current code uses 5 zoom buckets (`apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt:22`). Reduce to 3 buckets (1x, 2x, 4x) BEFORE implementing tile caches, otherwise cache math and metrics won't match plan assumptions. This is a targeted refactor: find all `scaleBucket` / zoom-level quantization logic and align to 3-bucket policy. **Include test updates:** `apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorTransformMathTest.kt` (lines ~117-120) asserts 1/1.5/2/3/4 buckets — update to 1/2/4. **BLOCKING:** Failing to update this test will cause CI failures AND invalidate all downstream tile-cache metrics assumptions. This is not optional cleanup — it's gating.
  - **Zoom-to-bucket hysteresis:** To prevent rapid bucket flips during pinch-zoom near thresholds (e.g., oscillating between 1x and 2x at zoom 1.9-2.1), use a hysteresis band: switch UP at `threshold * 1.1` and DOWN at `threshold * 0.9`. Example: zoom 1.0-2.19 → bucket 1x, zoom 2.2+ → bucket 2x, zoom 1.8-2.19 (when already at 2x) → stay at 2x, zoom <1.8 → back to 1x. This prevents render thrash and flicker during gradual pinch.
- **Invalidation:** On stroke add/delete/modify, invalidate affected tiles only. **Important:** Expand invalidation bounds by `maxStrokeWidth / 2 + 2px` (anti-alias bleed margin) to prevent stale pixels from thin strokes at tile edges.
- **Dynamic sizing:** On budget devices (`ActivityManager.isLowRamDevice()` or `memoryClass < 192`), use 16 MiB stroke tile cache (per global budget table). Query `ActivityManager.getMemoryClass()` at cache creation time.
- **Bitmap ref-safety (DECIDED: Strategy A — Cache stores Android Bitmap, convert at draw time):**
  - **Critical discovery:** `asImageBitmap()` on Android **wraps** the underlying `Bitmap` without copying. Calling `recycle()` on the source `Bitmap` immediately invalidates the `ImageBitmap`. Therefore the original Strategy A (convert-then-recycle) is WRONG.
  - **Corrected approach:** Cache stores the Android `Bitmap` directly. Call `asImageBitmap()` at draw time inside `DrawScope` / `Canvas.drawImage()`. The `ImageBitmap` wrapper is ephemeral and lightweight (no allocation overhead).
  - `recycle()` is called ONLY on cache eviction (`entryRemoved` callback), NEVER immediately after conversion.
  - The eviction-vs-draw race is handled by the `Mutex` already guarding all `LruCache` access (see thread safety above). During eviction, the `Mutex` ensures no concurrent draw is reading the same entry. If sub-frame races are observed in profiling, fall back to Strategy B (refcount) below.
  - **Trade-off:** Holding Android `Bitmap` in cache means native memory is pinned until eviction. This is acceptable because the byte-bounded `sizeOf` budget already constrains total pinned memory.
  - **Draw-time safety guard:** Before calling `bitmap.asImageBitmap()` at draw time, check `bitmap.isRecycled`. If recycled (race between eviction and draw), skip the tile and request re-render. This prevents `IllegalStateException` from drawing a recycled bitmap. Pattern: `val bmp = cache.get(key); if (bmp != null && !bmp.isRecycled) { drawImage(bmp.asImageBitmap(), ...) }`.
  - _(Strategy B — refcount — kept as documented fallback only: maintain per-tile `AtomicInteger`, increment on draw start, decrement on frame complete, only recycle when refcount == 0 and evicted.)_

#### [ ] P1.5: Frame Budget Manager (Week 4)

- **File:** New `apps/android/app/src/main/java/com/onyx/android/ink/perf/FrameBudgetManager.kt`
- **Purpose:** Limit per-frame work to stay within 8ms (120Hz) or 16ms (60Hz)
- **Implementation:**

  ```kotlin
  class FrameBudgetManager(targetFps: Int = 60) {
      private val budgetMs = 1000 / targetFps

      inline fun <T> runWithinBudget(items: List<T>, action: (T) -> Unit): Int {
          val startTime = System.nanoTime()
          var processed = 0
          for (item in items) {
              if (elapsedMs(startTime) > budgetMs * 0.8) break
              action(item)
              processed++
          }
          return processed
      }
  }
  ```

### 5.2 Acceptance Criteria

| Metric              | Current  | Target         | Measurement Method                                                                                        |
| ------------------- | -------- | -------------- | --------------------------------------------------------------------------------------------------------- |
| Perceived latency   | 50-100ms | <20ms          | 240fps slow-motion video on Galaxy Tab S9, measure stylus tip to first pixel change (P95 over 50 strokes) |
| Stroke pop-in       | Visible  | Not visible    | 240fps playback review: 0 gaps >1 frame across 50 strokes (3 reviewers, physical device)                  |
| Pen-up snapback     | Occurs   | ≤1mm overshoot | 240fps playback: max overshoot distance ≤1mm; 0 visible snapback events                                   |
| Pan/zoom FPS        | 30-45    | 60 sustained   | Perfetto/systrace frame timeline, measure P95 frame time over 30s pan gesture                             |
| First stroke render | 100ms+   | <50ms          | Android Profiler method trace, measure `onStrokeFinished` to frame presentation                           |

**Device requirement:** All latency measurements must be performed on physical stylus device (e.g., Galaxy Tab S9, Pixel Tablet with USI pen). Emulator measurements are not valid.

### 5.3 Risks

| Risk                                      | Probability | Impact | Mitigation                                     |
| ----------------------------------------- | ----------- | ------ | ---------------------------------------------- |
| Motion prediction causes visual artifacts | Medium      | High   | Implement pen-up stabilization before enabling |
| Style mismatch between layers             | High        | Medium | Prioritize P1.2 alignment task                 |
| Tile cache memory pressure                | Low         | Medium | Aggressive LRU eviction, bitmap recycling      |

---

## 6. Phase 2: PDF Overhaul

**Duration:** 5 weeks (Week 6-10)
**Goal:** Replace MuPDF with PdfiumAndroid, implement tile-based rendering
**Prerequisite:** Phase 0 spike must pass (PdfiumAndroid API parity confirmed)

### 6.1 PdfiumAndroid Selection

**Selected Library Source:** `https://github.com/Zoltaneusz/PdfiumAndroid` (tag `v1.10.0`)

**Rationale:**

- Explicit project standard: use `Zoltaneusz/PdfiumAndroid`
- Includes 16KB page size support (`v1.10.0` release notes)
- Apache 2.0 / BSD license (verify in Phase 0)

**Dependency:**

```kotlin
// build.gradle.kts
// Locked fork (exact coordinate verified in P0.0):
implementation("com.github.Zoltaneusz:PdfiumAndroid:v1.10.0")

// If artifact resolution fails in CI/environment, pin the source repo at v1.10.0
// and consume as a local Gradle module (":pdfium") or included build.
// Do NOT change the fork choice.
```

### 6.2 Tasks

#### [ ] P2.0: Renderer-Agnostic Text Model (Week 6 — BEFORE P2.2)

**WHY FIRST:** Current `NoteEditorState.kt` imports MuPDF types directly (`StructuredText`, `TextChar`, `Quad` at line 6). UI code should not depend on renderer-specific classes. Create an abstraction layer BEFORE swapping renderers.

- **Files:**
  - New: `apps/android/app/src/main/java/com/onyx/android/pdf/TextSelectionModel.kt`
  - Modify: `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`
- **New model:**

  ```kotlin
  // Renderer-agnostic text representation
  data class PdfTextQuad(
      val p1: PointF,
      val p2: PointF,
      val p3: PointF,
      val p4: PointF,
  )

  data class PdfTextChar(
      val char: Char,
      val quad: PdfTextQuad,  // in page points, upright coordinate space
      val pageIndex: Int,
  )

  data class PdfTextSelection(
      val chars: List<PdfTextChar>,
      val text: String,  // concatenated for clipboard
  )

  interface PdfTextExtractor {
      suspend fun getCharacters(pageIndex: Int): List<PdfTextChar>
  }
  ```

- **Migration:** Replace all `StructuredText`, `TextChar`, `Quad` references in `NoteEditorState.kt` with the agnostic model. Implement `PdfTextExtractor` for MuPDF first (to verify model works), then swap to Pdfium implementation in P2.2.
- **Acceptance:** `NoteEditorState.kt` no longer imports any MuPDF classes after this task.

#### [ ] P2.1: PdfiumAndroid Integration (Week 6)

- **Primary file:** new `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumRenderer.kt` (replacing `PdfRenderer.kt`)
- **Mandatory integration call sites (must all be updated in Phase 2):**
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`:
    - `rememberPdfState(...)` currently instantiates `PdfRenderer(...)`
    - `DisposePdfRenderer(...)` lifecycle must close Pdfium resources
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt`:
    - replace `rememberPdfBitmap(...)` full-page path
    - retire/repurpose `resolvePdfRenderScale(...)` + `zoomToRenderScaleBucket(...)` for tile requests
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`:
    - remove direct MuPDF types from `TextSelection` and PDF state
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`:
    - replace single-bitmap draw with tile draw loop
    - keep long-press selection behavior with new text model
  - `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt`:
    - **MUST-COMPLETE (not optional cleanup):** `importPdfInternal(...)`/`createPagesFromPdf(...)` (around `HomeScreen.kt:577`) currently open MuPDF `Document`; migrate to Pdfium-based page-metadata read path. This is on the critical path because users cannot import new PDFs after MuPDF removal if this is not migrated.
- **Password-protected PDFs (IN SCOPE):** If the Pdfium API indicates a password is required, prompt the user and retry open. If password fails, show a user-facing error and abort import without crashing. Document the UX path and error state.
- **API mapping:**
  | MuPDF | PdfiumAndroid | Notes |
  |-------|---------------|-------|
  | `Document.openDocument()` | `PdfiumCore.newDocument()` | |
  | `document.loadPage()` | `pdfiumCore.openPage()` | |
  | `page.bounds` | `pdfiumCore.getPageSize()` | Used by import + editor geometry |
  | `AndroidDrawDevice` | `pdfiumCore.renderPageBitmap()` | |
  | `page.toStructuredText()` | `pdfiumCore.getPageText()` | **CRITICAL - verify in spike** |
  | `page.destroy()` | `pdfiumCore.closePage()`/doc-close lifecycle | explicit in wrapper |
  | `document.destroy()` | `pdfiumCore.closeDocument()` | |

#### [ ] P2.2: Text Selection Parity (Week 6-7)

- **CRITICAL:** Must maintain feature parity with current MuPDF text selection
- **Current call flow to preserve:**
  - `apps/android/app/src/main/java/com/onyx/android/pdf/PdfRenderer.kt`:
    - `extractTextStructure()`
    - `findCharAtPagePoint()`
    - `getSelectionQuads()`
    - `extractSelectedText()`
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`:
    - `handlePdfDragStart(...)`, `handlePdfDragMove(...)`, `handlePdfDragEnd(...)`
- **Implementation requirements:**
  - Define renderer-agnostic text interfaces in PDF layer (no MuPDF classes in UI state).
  - Preserve current long-press drag UX semantics while swapping backend.
  - If Pdfium text geometry differs, document explicit tolerance criteria and fallback behavior.
  - **Parity definition (explicit):** Long-press hit-test selects correct glyph within ≤2dp at 1x/2x/4x; drag selection updates highlight continuously; copied text matches highlighted selection on the canonical PDF corpus.
  - **Clipboard UX (explicit):** Add a minimal “Copy” action in the selection overlay (e.g., context menu/toolbar shown on drag end). Verification: clipboard text equals `PdfTextSelection.text` for the active selection.
  - **Text selection model (scope decision):** Use quads as the canonical representation (`PdfTextQuad` with 4 points) in **page points** in the **upright** coordinate space (rotation normalized). **Cross-page text selection is OUT OF SCOPE** for this milestone — selection is always within a single page. If a user drags across a page boundary, clamp to the current page edge. `ViewTransform` converts quads from page points → screen pixels for rendering.
  - **Native bridge fallback:** If `Zoltaneusz/PdfiumAndroid` only exposes flat text via `getPageText()` without per-character bounding boxes, a JNI bridge to Pdfium's native `FPDFText_GetCharBox()` / `FPDFText_GetCharOrigin()` / `FPDFText_CountChars()` may be required. The Phase 0 spike must assess this. If native bridge is needed, add ~1 week to Phase 2 timeline and create a `PdfiumTextBridge.kt` + `pdfium_text_jni.cpp` pair. Keep the fork locked; do not swap libraries.
  - **MuPDF fallback wiring (conditional):** If fallback Option A is chosen, add a dedicated `internal` product flavor (or `internalDebug` buildType) in `apps/android/app/build.gradle.kts` and scope MuPDF dependencies ONLY to that variant. Gate selection code paths with a `BuildConfig` flag and assert release variants exclude MuPDF at build time.

#### [ ] P2.3: Tile-Based Rendering (Week 7-8)

- **Files:**
  - New: `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileRenderer.kt`
  - New: `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileCache.kt`
- **Cache size:** 64 MiB (per global budget table for high-end; use tier-appropriate value from budget table), byte-bounded via `sizeOf` override using `Bitmap.allocationByteCount`
- **Eviction:** LRU with bitmap recycling via `entryRemoved()` calling `Bitmap.recycle()`
- **Pre-fetch:** Load tiles at viewport edges during pan
- **Thread-safety contract (from Phase 0 spike findings):**
  - If `PdfiumCore` is NOT thread-safe: use a single-threaded `Dispatchers.IO.limitedParallelism(1)` for all Pdfium calls, with a per-document mutex
  - If `PdfiumCore` IS thread-safe: use `Dispatchers.IO` with parallelism capped at 4 concurrent tile renders to avoid native memory spikes
  - **API surface caveat:** `Zoltaneusz/PdfiumAndroid` Java wrapper may NOT expose all expected methods (`getPageText`, `getPageRotation`, `closePage`). Phase 0 spike MUST verify actual API surface. If `closePage` is not exposed, use alternative lifecycle (e.g., pages auto-close, or use `openPage` return value to track page handles). The pseudocode below is illustrative — adjust to actual API after spike.
  - Document lifecycle must be managed at the `PdfiumRenderer` level: one `PdfiumCore` instance and one `PdfDocument` per open file, closed on editor exit
  - **PdfiumCore init/teardown ownership:** `PdfiumCore` initialization (`PdfiumCore(context)`) should happen ONCE at app startup (or lazily on first PDF open) and be held as a singleton (e.g., via Hilt `@Singleton`). Do NOT create per-document `PdfiumCore` instances — the native library init is expensive. `PdfDocument` is per-file and must be closed when switching documents or leaving the editor. On document switch: close old `PdfDocument` → open new `PdfDocument`. On editor exit: close `PdfDocument` and cancel all in-flight tile renders. Do NOT destroy `PdfiumCore` — it stays alive for the app session.
- **Cache thread-safety:** `android.util.LruCache` is thread-safe per operation, but multi-step atomic logic (check-then-put, get-then-recycle) requires `Mutex` synchronization since cache is accessed from multiple coroutines (render workers + UI reads). Double-recycle crashes are fatal — guard recycle paths carefully.
- **Bitmap config:** Use `ARGB_8888` for PDF tiles (required for text clarity and selection overlay compositing). Do NOT use `RGB_565` — it saves 50% memory but causes visible color banding on PDF text and selection highlights are harder to composite. If memory pressure is critical on budget devices, reduce cache size rather than bitmap config.
- **Dynamic cache sizing:** On budget devices (`ActivityManager.isLowRamDevice()` or `memoryClass < 192`), use 32 MiB PDF tile cache (per global budget table). Query at `PdfTileCache` creation time.
- **Tile range computation contract (explicit):**
  - Inputs: `viewportW/H` (screen px), `panX/panY` (screen px), `zoom`, `pageW/H` (page points), `tileSizePx = 512`, `scaleBucket`.
  - Convert visible screen rect → page rect: `pageRect = viewTransform.screenToPageRect(viewportRect)`.
  - Tile size in page units: `tileSizePage = tileSizePx / scaleBucket`.
  - Tile range: `minTileX = floor(pageRect.left / tileSizePage)`, `maxTileX = floor((pageRect.right - 1) / tileSizePage)`; same for Y.
  - For draw: compute each tile’s page rect, then `drawRect = viewTransform.pageRectToScreenRect(tileRectPage)`; scale tiles by `zoom / scaleBucket`.
  - Location: compute tile ranges in `NoteEditorShared.kt` (tile request generation) or a dedicated `PdfTileMath.kt` helper; keep `PdfTileRenderer` unaware of UI transforms.
  - Naming: use `pageIndex` for note page index and `pdfPageNo` for the PDF page within an asset; convert explicitly at the boundary.
- **Design (ILLUSTRATIVE — adjust to actual API after Phase 0 spike):**
  ```kotlin
  // NOTE: Method names below are illustrative. Actual Zoltaneusz/PdfiumAndroid API
  // may differ (e.g., no closePage(), page auto-closes, or different lifecycle).
  // Phase 0 spike MUST determine the actual API contract before implementation.
  class PdfTileRenderer(
      private val pdfiumCore: PdfiumCore,
      private val document: PdfDocument,
      private val tileSize: Int = 512,
  ) {
      suspend fun renderTile(
          pageIndex: Int,
          tileX: Int,
          tileY: Int,
          scale: Float,
      ): Bitmap = withContext(Dispatchers.IO) {
          val bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
          // Actual page lifecycle depends on spike findings:
          // - If openPage()/closePage() exist: use try/finally
          // - If pages auto-close or have different lifecycle: adapt accordingly
          pdfiumCore.openPage(document, pageIndex)
          try {
              pdfiumCore.renderPageBitmap(
                  document, bitmap, pageIndex,
                  startX = -(tileX * tileSize),
                  startY = -(tileY * tileSize),
                  drawSizeX = (pageWidth * scale).toInt(),
                  drawSizeY = (pageHeight * scale).toInt(),
              )
          } finally {
              // closePage() may not exist — adjust per spike findings
              // pdfiumCore.closePage(document, pageIndex)
          }
          bitmap
      }
  }
  ```

#### [ ] P2.4: Async Rendering Pipeline (Week 8-9)

- **Goal:** Never block UI thread for PDF rendering, and avoid waiting for full-page raster completion
- **File:** new `apps/android/app/src/main/java/com/onyx/android/pdf/AsyncPdfPipeline.kt`
- **Implementation:**

  ```kotlin
  class AsyncPdfPipeline(
      private val renderer: PdfTileRenderer,
      private val cache: PdfTileCache,
  ) {
      private val renderQueue = Channel<TileRequest>(capacity = 16)
      private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
      private val inFlight = ConcurrentHashMap<TileKey, Job>() // dedup

      fun requestTiles(visibleTiles: List<TileKey>) {
          // Cancel tiles no longer visible (viewport changed during pan/zoom)
          val visibleSet = visibleTiles.toSet()
          inFlight.keys.filter { it !in visibleSet }.forEach { key ->
              inFlight.remove(key)?.cancel()
          }

          scope.launch {
              for (tile in visibleTiles) {
                  // Skip if already cached or already in flight (dedup)
                  if (cache.get(tile) != null) continue
                  if (inFlight.containsKey(tile)) continue

                  val job = scope.launch {
                      try {
                          val bitmap = renderer.renderTile(...)
                          cache.put(tile, bitmap)
                          // Trigger recomposition via snapshot state
                      } catch (e: CancellationException) {
                          // CRITICAL: If bitmap was allocated before cancellation,
                          // recycle it to prevent native memory leak
                          // (bitmap is not in cache, so cache eviction won't handle it)
                          throw e
                      } finally {
                          inFlight.remove(tile)
                      }
                  }
                  inFlight[tile] = job
              }
          }
      }

      fun cancelAll() {
          inFlight.values.forEach { it.cancel() }
          inFlight.clear()
      }
  }
  ```

- **Request coalescing:** Duplicate `TileKey` requests are ignored if already in-flight or cached.
- **Viewport cancellation:** When viewport changes (pan/zoom), tiles that are no longer visible have their render jobs cancelled immediately to free resources.
- **Parallelism cap:** In-flight renders are bounded by the coroutine dispatcher's parallelism (see thread-safety contract in P2.3). Do NOT use `Channel` consumer loop — use direct `launch` per tile with dedup map to allow parallel rendering.
- **Concurrency guard:** Although `ConcurrentHashMap` is used for `inFlight`, the check-then-act pattern (`containsKey` → `launch` → `put`) is NOT atomic. Wrap `requestTiles()` and `cancelAll()` in a `Mutex.withLock` block to prevent race conditions during fast pan/zoom where multiple `requestTiles()` calls overlap. Alternatively, use `putIfAbsent` pattern: `inFlight.putIfAbsent(tile, job)?.let { job.cancel() }`.
- **In-flight bitmap memory cap:** Limit concurrent in-flight tile renders to max 4 (via `Semaphore(4)`). Each 512×512 ARGB_8888 tile is ~1 MB; 4 in-flight = ~4 MB transient allocation. This prevents memory spikes during fast zoom where many tiles are requested simultaneously. The semaphore is acquired before `renderTile()` and released in `finally`. **Cancellation safety:** The `semaphore.acquire()` → `renderTile()` → `semaphore.release()` sequence MUST use `try/finally`: `semaphore.acquire(); try { renderTile(...) } finally { semaphore.release() }`. Without `finally`, a cancelled coroutine (from viewport cancellation) will leak a semaphore permit, progressively starving the pipeline until no renders can proceed.
- **Lifecycle:** `cancelAll()` must be called on editor exit / document change.

- **UI hook points:**
  - `NoteEditorScreen.kt` (`rememberPdfState`) owns pipeline lifecycle keyed by `pdfAssetId`
  - `NoteEditorShared.kt` requests visible + prefetch tiles based on viewport/zoom
  - `NoteEditorPdfContent.kt` observes incremental tile readiness and repaints without clearing old content

#### [ ] P2.5: Keep Previous Content During Render (Week 9)

- **Problem:** Flash/blank during zoom level change when tiles at new scale aren't ready
- **Solution:** Multi-layer composable approach:
  1. **Tile reuse:** Keep previous-scale tiles visible (stretched/scaled) while new-scale tiles render. As each new tile completes, swap it in individually — don't wait for all tiles.
  2. **Full-page fallback:** If no previous tiles exist (first load), display a low-resolution full-page bitmap (stretched) as placeholder.
  3. **Crossfade:** Individual tile crossfade (150ms) as new tiles replace old ones for smooth visual transition.
- **Implementation:** Track `Map<TileKey, Pair<Bitmap, Float>>` where Float is the scale the bitmap was rendered at. On draw, use best available tile (prefer current scale, fall back to nearest scale with transform).

#### [ ] P2.6: Migration & Cleanup (Week 10)

- Remove MuPDF dependency from `apps/android/app/build.gradle.kts`
- Delete `apps/android/app/src/main/java/com/onyx/android/pdf/PdfRenderer.kt` (old)
- Replace MuPDF imports/usages in:
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt`
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorShared.kt`
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorState.kt`
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt`
  - `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt`
- Verify license compliance (Apache 2.0/BSD attribution in NOTICE)
- Verification command: ensure no residual `com.artifex.mupdf` references in `apps/android/app/src/main/java`
- Release build check (MANDATORY): run `./apps/android/gradlew :app:dependencies` and `./apps/android/gradlew :app:assembleRelease`, then scan `apps/android/app/build/outputs/apk/release/app-release.apk` (or AAB if used) with `apkanalyzer`/`zipinfo` to confirm no `com.artifex.mupdf` or `libmupdf*` artifacts are packaged. Record results in `.sisyphus/notepads/mupdf-release-scan.md`.

### 6.3 Acceptance Criteria

| Metric                               | Current        | Target                                                    | Measurement Method                                                                                                                                 |
| ------------------------------------ | -------------- | --------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Zoom jank                            | 200ms+ stutter | <100ms P95 tile render                                    | Perfetto trace, measure tile request to bitmap ready, P95 over 20 zoom cycles                                                                      |
| Tile seams                           | Unknown        | None                                                      | Corpus visual check: no 1px gaps/overdraw at tile boundaries at 1x/2x/4x                                                                           |
| Rotation correctness                 | Unknown        | Correct                                                   | Corpus test on 90°/180°/270° PDFs; render upright and selection aligns                                                                             |
| Memory (10-page PDF)                 | 64 MiB bitmaps | Total bitmap + native memory within envelope (see budget) | Android Profiler/PSS: ≤ memoryClass\*0.50 (high/mid), ≤0.41 (budget)                                                                               |
| Tile cache memory                    | N/A            | 64 MiB max (high-end tier)                                | Monitor `PdfTileCache.size()` during test                                                                                                          |
| License                              | AGPL-3.0       | Apache 2.0 / BSD                                          | Legal review of dependency license files + NOTICE                                                                                                  |
| Text selection accuracy              | Works          | Parity within tolerance                                   | Selection quads align within ≤2dp; copied text matches selection on corpus                                                                         |
| Zoom blank flash                     | Occurs         | 0 blank frames                                            | 20 zoom cycles; previous tiles persist until replacement                                                                                           |
| Password-protected PDFs              | Unknown        | Works                                                     | Prompt → open succeeds; wrong password shows error and aborts import                                                                               |
| Tile render time                     | N/A            | <100ms P95 (matches SLO)                                  | Profiler trace over 100 tile renders                                                                                                               |
| MuPDF references in Android app code | Present        | 0                                                         | `rg \"com\\.artifex\\.mupdf\" apps/android/app/src/main/java` returns no matches                                                                   |
| MuPDF in release build               | Present        | 0                                                         | `./apps/android/gradlew :app:dependencies` + scan `apps/android/app/build/outputs/apk/release/app-release.apk` for `com.artifex.mupdf`/`libmupdf*` |

**Note:** Memory target includes tile caches and other bitmap consumers (Compose caches, recognition bitmaps). Use the global memory envelope for pass/fail.

### Phase 2 Exit Gate (Required for Phase 3)

- Phase 0 spike verdict recorded (JAVA_API_SUFFICIENT / JNI_BRIDGE_REQUIRED / NOT_FEASIBLE) and mitigation chosen.
- `HomeScreen.kt` PDF import path migrated and verified on-device using the canonical PDF corpus.
- Release build passes AGPL/MuPDF dependency scan; debug/internal fallback (if any) is gated by a build flavor and documented.
- Canonical PDF corpus passes render/selection/rotation checks without visible seams or blank flashes.

---

## 7. Phase 3: Multi-Page & Navigation

**Duration:** 4 weeks (Week 11-14)
**Goal:** Continuous vertical scroll, thumbnail strip, page navigation

### 7.1 Scope & Complexity Warning

**This phase is larger than it appears.** The editor is currently single-page by design:

- `NoteEditorViewModel` manages `currentPage` + page-local strokes
- `ViewTransform` assumes single-page coordinate space
- MyScript recognition is page-scoped
- Selection/eraser gestures are page-local

Moving to continuous scroll touches ALL of these subsystems. Budget accordingly.

**Scope boundary:** Continuous scroll applies to PDF-backed notes only for this milestone. Ink-only/blank notes remain single-page.

### 7.2 Tasks

#### [ ] P3.1: LazyColumn Page Layout (Week 11)

- **Files:**
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorPdfContent.kt` (introduce page-item list layout)
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorUi.kt` (route PDF mode to continuous-scroll composable)
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` (state wiring for visible range callbacks)
- **Challenge:** `LazyColumn` + stylus input may conflict (scroll vs draw gestures)
- **Solution: Interaction Mode Architecture (single source of truth)**
  - Define `InteractionMode` enum: `DRAW`, `PAN`, `SCROLL` (plus `ERASE`, `SELECT` if needed)
  - Host as `MutableState<InteractionMode>` in `NoteEditorViewModel`
  - Tool bar selection → updates `InteractionMode`
  - Pointer input routing:
    - `DRAW` mode: stylus events routed to `InkCanvasTouch`, `LazyColumn` scroll disabled (`userScrollEnabled = false`)
    - `PAN` mode: finger gestures routed to `ViewTransform` for pan/pinch-zoom, `LazyColumn` scroll disabled
    - `SCROLL` mode: finger gestures routed to `LazyColumn`, stylus still draws (stylus always draws regardless of finger mode)
  - **Concrete wiring (must be explicit):**
    - `InkCanvasTouch.kt` should accept the current `InteractionMode` and ignore finger events when mode == `SCROLL` (while still accepting stylus).
    - `InkCanvasTransformTouch.kt` should only handle pinch-zoom in `PAN` mode; pinch gestures should be ignored in `SCROLL` to avoid competing with `LazyColumn`.
    - `NoteEditorScreen.kt` (or `NoteEditorUi.kt`) must pass `interactionMode` into the ink touch handlers so routing is deterministic.
  - **Rule:** Stylus (`MotionEvent.TOOL_TYPE_STYLUS`) always draws, finger behavior depends on mode. This matches Samsung Notes / GoodNotes UX convention.
  - **Compose pointer API:** Use `Modifier.pointerInput` with `awaitPointerEvent` checking `PointerType.Stylus` vs `PointerType.Touch` for routing. If `PointerType` does not distinguish stylus reliably on all devices, add a `Modifier.pointerInteropFilter` fallback that reads `MotionEvent.getToolType(pointerId)` directly. Test on both S-Pen (Samsung) and USI stylus (Pixel) devices.
  - **Nested scroll fallback:** If `LazyColumn` intercepts scroll gestures unexpectedly when stylus is drawing near edges, implement a `NestedScrollConnection` that consumes all scroll deltas when `InteractionMode == DRAW`. Alternatively, disable `LazyColumn` scroll entirely during draw and use programmatic `scrollToItem()` for page navigation. Test early — gesture conflicts are the highest-risk UX issue in this phase.
- **Design:**

  ```kotlin
  @Composable
  fun ContinuousScrollPdfContent(
      pages: List<PageState>,
      onPageVisible: (Int) -> Unit,
  ) {
      LazyColumn {
          itemsIndexed(pages) { index, page ->
              PageItem(
                  page = page,
                  onVisible = { onPageVisible(index) },
              )
              Spacer(Modifier.height(16.dp).background(Color.Gray))
          }
      }
  }
  ```

- **Per-page clipping (CRITICAL):** Each `PageItem` composable MUST apply `Modifier.clipToBounds()` to prevent ink strokes or PDF tile content from painting into adjacent pages. Without clipping, strokes near page edges will visually bleed into the gap or the next page's content area. Apply clip at the page content level (inside `PageItem`), not at the `LazyColumn` item level.
  - **AndroidView caveat:** `Modifier.clipToBounds()` does NOT clip the content of `AndroidView` children (such as `InProgressStrokesView`). In-progress strokes rendered via `InProgressStrokesView` will overdraw page bounds even with the modifier applied. **Fix:** Call `View.setClipBounds(Rect(0, 0, width, height))` on the `InProgressStrokesView` in its `AndroidView { ... }` factory or `update` block, matching the page content dimensions. This ensures in-progress ink is clipped identically to committed strokes and PDF tiles. **Update on resize:** `setClipBounds` must be re-applied whenever the page content dimensions change (e.g., zoom, rotation). Use pixel-precise bounds from the composable's `onSizeChanged` or `Modifier.onGloballyPositioned`.
  - **Scalability consideration:** Embedding one `InProgressStrokesView` per page inside `LazyColumn` creates heavy `AndroidView` instances that scale poorly with page count. **Preferred approach:** Use a SINGLE `InProgressStrokesView` as a document-level overlay, positioned and clipped to the active drawing page. Only the active page (where stylus is touching) needs in-progress stroke rendering; other pages show only committed strokes via Compose `Canvas`. This reduces `AndroidView` churn during scroll and avoids `N` heavy views for `N` visible pages.

- **Stable keys for page items (CRITICAL):** Use `key(page.id)` in the `LazyColumn` `itemsIndexed` block to ensure stable item identity across recompositions. Without stable keys, `LazyColumn` may reuse `AndroidView` instances across different pages during fast scroll, causing `InProgressStrokesView` to display strokes from a wrong page or crash on stale state. Pattern: `itemsIndexed(pages, key = { _, page -> page.id }) { index, page -> ... }`.
  - **contentType optimization:** Also set `contentType` for page items to reduce recomposition churn: `itemsIndexed(pages, key = { _, page -> page.id }, contentType = { _, _ -> "page" }) { ... }`. This tells `LazyColumn` that all page items share the same composition shape, enabling more efficient recycling.

- **LazyListState page tracking policy:**
  - **Dynamic item height:** Page item height changes with zoom. Capture actual height using `Modifier.onGloballyPositioned { coords -> coords.size.height }` per page item and compute visible fraction using `firstVisibleItemScrollOffset / itemHeightPx`. Treat a page as current only when `visibleFraction >= 0.5`.
  - Use `LazyListState.firstVisibleItemIndex` as the primary `currentPage` signal for page indicator UI (e.g., "Page 3 of 50").
  - `currentPage` updates only when `firstVisibleItemIndex` changes AND the item is at least 50% visible (use `firstVisibleItemScrollOffset` / item height to compute). This prevents flicker/oscillation when a page boundary sits at the viewport edge.
  - `activeRecognitionPageId` (MyScript) updates ONLY on stylus interaction (P3.2), NOT on scroll-driven `currentPage` changes. These are intentionally decoupled.
  - For programmatic jump (thumbnail tap, outline nav), use `LazyListState.animateScrollToItem(pageIndex)` — this handles smooth animation. Do NOT set `currentPage` directly; let it derive from scroll position to avoid state desync.
  - **Anti-oscillation guard:** Debounce `currentPage` updates by 100ms to prevent rapid flicker when scrolling slowly across a page boundary.

#### [ ] P3.2: Multi-Page Ink Coordinate System (Week 11-12)

- **Challenge:** Strokes currently use page-local coordinates
- **Solution: Canonical Document-Space Coordinate System**
  - Define a single document-space Y axis: `documentY = sum(pageHeights[0..pageIndex-1]) + (pageIndex * gapHeight) + pageLocalY`
  - All touch-to-page-local coordinate conversion happens in ONE place: `ViewTransform.documentToPage(documentX, documentY) → Pair<pageIndex, pageLocalPoint>`
  - `ViewTransform.pageToDocument(pageIndex, pageLocalPoint) → documentPoint` for the reverse
  - Gestures, selection, and eraser all use the same transform — no per-subsystem coordinate conversion
  - Store strokes in page-local coordinates (as today); convert only at gesture input and render time
  - **PDF page rotation:** PDF pages may have rotation metadata (0°, 90°, 180°, 270° via `/Rotate` key). `ViewTransform` must normalize page dimensions and coordinate transforms based on rotation before computing tile coordinates or stroke positions. Query rotation via Pdfium API (e.g., `getPageRotation()` or equivalent) and apply rotation matrix in `documentToPage()` / `pageToDocument()`. Strokes are stored in the normalized (upright) page space.
- **Files:** `ViewTransform.kt`, `InkCanvasTransformTouch.kt`
- **Recognition integration (blocking ambiguity removed):**
  - **Scope boundary:** Recognition remains offscreen-only; no architectural changes to MyScript beyond active-page routing and interaction triggers in this milestone.
  - `apps/android/app/src/main/java/com/onyx/android/recognition/MyScriptPageManager.kt` currently owns one active `OffscreenEditor` and switches with `onPageEnter(pageId)`/`closeCurrentPage()`.
  - `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt` currently calls `onPageEnter(...)` from `setCurrentPage(...)` and `upgradePageToMixed(...)`; this is page-navigation driven, not interaction driven.
- **Required behavior for continuous scroll: single active recognition editor**
  - Multiple pages may be visible, but recognition remains bound to exactly one active page at a time.
  - Active recognition page switches on stylus interaction start for a page (or explicit page jump), not on every visibility change.
  - `onVisiblePagesChanged(...)` must not churn `onPageEnter(...)`; it should only control stroke/page data preloading.
  - `addStroke(...)`/erase/undo operations must execute against the active recognition page id; if page changes, call `onPageEnter(newPageId)` once before sending events.
- **Concrete implementation hooks:**
  - Add `activeRecognitionPageId` state in `NoteEditorViewModel.kt`.
  - Add callback from page item touch-start in `NoteEditorPdfContent.kt` to ViewModel (`onDrawingPageInteraction(pageId)`).
  - Refactor `setCurrentPage(...)` to avoid forcibly switching recognition when merely scrolling.

#### [ ] P3.3: Virtualized Stroke Loading (Week 12-13)

- **Goal:** Only load strokes for visible pages (pageIndex ± 1)
- **File:** `NoteEditorViewModel.kt`
- **Implementation:**

  ```kotlin
  fun onVisiblePagesChanged(visibleRange: IntRange) {
      val pagesToLoad = (visibleRange.first - 1)..(visibleRange.last + 1)
      strokeRepository.loadPagesAsync(pagesToLoad)
      strokeRepository.unloadPages(outsideRange = pagesToLoad)
  }
  ```

  - **Uncommitted stroke buffer policy:** When a page is unloaded via `unloadPages()`, any uncommitted (in-progress) strokes on that page MUST be committed to the database BEFORE unloading. Do NOT silently discard uncommitted edits. If auto-commit is not desirable, retain the uncommitted buffer in memory (outside the virtualization scope) and restore it when the page is re-loaded. This edge case occurs when a user draws on a page and quickly scrolls away before pen-up or before auto-save fires.

#### [ ] P3.4: Thumbnail Strip (Week 13-14)

- **File:** New `ThumbnailStrip.kt`
- **Design:** Horizontal scrollable strip at bottom of editor
- **Thumbnail generation:** Render page at 10% scale, cache as PNG
- **Interaction:** Tap to jump, drag to reorder (future)
- **Note:** This generates PAGE thumbnails (for navigation). Phase 4 generates NOTE thumbnails (for home screen). These may share rendering logic but serve different purposes.

#### [ ] P3.5: Page Outline Navigation (Week 14)

- **File:** New `PdfOutlineSheet.kt`
- **Source:** `pdfiumCore.getTableOfContents()`
- **UI:** Bottom sheet with nested bookmark list

### 7.2 Acceptance Criteria

| Feature           | Current                | Target                                                                   |
| ----------------- | ---------------------- | ------------------------------------------------------------------------ |
| Scroll behavior   | Page-by-page           | Continuous vertical                                                      |
| Visible pages     | 1                      | 2-3 (viewport dependent)                                                 |
| Recognition scope | Nav-selected page only | Single active page selected by stylus interaction (stable during scroll) |
| Thumbnail preview | None                   | Yes (bottom strip)                                                       |
| Page jump         | Button only            | Thumbnail tap + outline                                                  |
| Gesture matrix    | Conflicts              | No mode conflicts in 5-min mixed draw/scroll/zoom session                |
| Cross-page edits  | Risk                   | Strokes/eraser/selection never apply to wrong page during fast scroll    |
| Coord correctness | N/A                    | No drift across page boundaries (unit test + manual validation)          |
| Scroll perf       | Unknown                | 50-page PDF scroll without sustained jank (<5% dropped frames)           |

### Phase 3 Exit Gate (Required for Phase 4)

- Gesture matrix passes on at least 1 stylus device (S-Pen class) without stuck modes.
- Cross-page attribution validated: no wrong-page strokes, selection, or recognition events during scroll.
- Continuous scroll + recognition stability verified with canonical PDF corpus.

---

## 8. Phase 4: Home/Library UX

**Duration:** 3 weeks
**Goal:** Thumbnails, folders, tags, batch operations

**SEQUENCING (MANDATORY):** P4.0 (Database schema work: entities, DAOs, repositories, migration) MUST land BEFORE P4.1-P4.5 UI tasks begin. UI tasks depend on real Room entities and DAO methods — do NOT use temporary fake models or mock data. The schema work gates all UI implementation in this phase.

### 8.1 Tasks

#### [ ] P4.0: Database Schema v3 — Entities, DAOs, Migration (Week 15 — FIRST)

**THIS TASK GATES ALL OTHER P4 TASKS. Complete before starting P4.1-P4.5.**

- **Files:** `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt` + new entity/DAO files
- **Versioning (explicit):**
  - Bump `OnyxDatabase` from `version = 2` to `version = 3`
  - Add `MIGRATION_2_3` and register `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`
  - Keep v2 user data valid; compatibility below v2 is out of scope for this phase
- **New entities to create:**
  - `FolderEntity` (`folderId`, `name`, `parentId`, `createdAt`)
  - `TagEntity` (`tagId`, `name`, `color`, `createdAt`)
  - `NoteTagCrossRef` (`noteId`, `tagId`) — junction table
  - `ThumbnailEntity` (`noteId`, `filePath`, `contentHash`, `generatedAt`)
- **Schema changes in `MIGRATION_2_3` (SQLite-safe, FK-aware):**
  - `CREATE TABLE folders (folderId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, parentId TEXT, createdAt INTEGER NOT NULL)`
  - `CREATE TABLE tags (tagId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color TEXT NOT NULL, createdAt INTEGER NOT NULL)`
  - `CREATE TABLE note_tag_cross_ref (noteId TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(noteId, tagId))`
  - `CREATE TABLE thumbnails (noteId TEXT NOT NULL PRIMARY KEY, filePath TEXT NOT NULL, contentHash TEXT NOT NULL, generatedAt INTEGER NOT NULL)`
  - **Recreate `notes` to add FK (cannot add FK via ALTER TABLE):**
    1. `CREATE TABLE notes_new (noteId TEXT NOT NULL PRIMARY KEY, ownerUserId TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER, folderId TEXT, FOREIGN KEY(folderId) REFERENCES folders(folderId) ON DELETE SET NULL)`
    2. `INSERT INTO notes_new (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt, folderId) SELECT noteId, ownerUserId, title, createdAt, updatedAt, deletedAt, NULL FROM notes`
    3. `DROP TABLE notes`
    4. `ALTER TABLE notes_new RENAME TO notes`
    5. Recreate indexes on `notes` (e.g., `CREATE INDEX idx_notes_folderId ON notes(folderId)`)
  - Add required indexes/foreign keys (see index/FK specs below)
- **Required indexes (specify up front to avoid slow queries):**
  - `notes.folderId` — index for folder listing queries
  - `note_tag_cross_ref.noteId` + `note_tag_cross_ref.tagId` — composite index for join queries, plus individual indexes for reverse lookups
  - `tags.name` — unique index (global; single-user app for this milestone)
  - `thumbnails.noteId` — primary key already enforces uniqueness (no extra index required)
  - `folders.parentId` — optional for future nesting (NOT used in this milestone)
- **Foreign key constraints (specify up front to avoid orphan rows):**
  - `note_tag_cross_ref.noteId` → `notes.noteId` with `ON DELETE CASCADE` (deleting a note removes all its tag associations)
  - `note_tag_cross_ref.tagId` → `tags.tagId` with `ON DELETE CASCADE` (deleting a tag removes all its note associations)
  - `thumbnails.noteId` → `notes.noteId` with `ON DELETE CASCADE` (deleting a note removes its thumbnail row)
  - `notes.folderId` → `folders.folderId` with `ON DELETE SET NULL` (deleting a folder moves its notes to root, not deletes them)
  - `folders.parentId` → `folders.folderId` with `ON DELETE CASCADE` (future nesting only; NOT used in this milestone)
  - **Note:** Room requires `@ForeignKey` annotations on entity classes AND `foreignKeys` in `CREATE TABLE` DDL for the migration. Both must be consistent.
  - **Soft-delete caveat:** FK cascades apply ONLY to hard deletes. Soft delete (`deletedAt`) does not trigger cascades, so queries must filter `deletedAt IS NULL` and purge logic must explicitly clean up rows/files.
  - **DDL alignment checklist (MANDATORY):** Ensure migration SQL column names match entity fields and existing schema: `notes.noteId`, `pages.pageId`, `strokes.strokeId`, `pages.noteId`, `strokes.pageId`.
- **Constraint decisions (LOCKED for this milestone):**
  - **Tags:** `tags.name` is globally unique (single-user assumption). No per-folder uniqueness.
  - **Folders:** Flat list only. `parentId` exists for future use but must remain `NULL` and no nested UI/logic is implemented in this milestone.
- **DAO changes required (not optional):**
  - `apps/android/app/src/main/java/com/onyx/android/data/dao/NoteDao.kt`:
    - extend list/query API for folder/tag filtering and thumbnail projection
    - add update methods for folder assignment
  - Add `FolderDao.kt`, `TagDao.kt`, `ThumbnailDao.kt`
  - Register new DAO accessors in `OnyxDatabase.kt`
- **Repository changes required:**
  - `apps/android/app/src/main/java/com/onyx/android/data/repository/NoteRepository.kt`:
    - add folder/tag/thumbnail write methods used by Home phase tasks
    - update note list read path to include thumbnail/folder/tag metadata needed by `HomeScreen.kt`
- **Deletion semantics (CRITICAL):** `notes.deletedAt` indicates soft delete today. Keep soft delete for standard delete actions. Add an explicit permanent-delete path (e.g., `purgeNote()` / “Empty trash”) that removes the `notes` row and relies on FK cascades for `note_tag_cross_ref` and `thumbnails`. All list/query paths must filter `notes.deletedAt IS NULL` so soft-deleted notes (and their cross-refs/thumbnails) are excluded from UI.
- **Basic migration test (unit):** Update `apps/android/app/src/test/java/com/onyx/android/data/OnyxDatabaseTest.kt` to verify `MIGRATION_2_3` is registered and version bumps correctly.
- **Room schema export (CRITICAL):** Ensure `room.schemaLocation` in `build.gradle.kts` is configured (annotationProcessor/ksp arg) and the v3 schema JSON is exported. Verify that `schemas/` directory contains both v2 and v3 schema JSON files after build.
- **Room 3 incompatibility note:** Room 3.x uses a different `SQLiteDriver` API and `room3-testing` artifact. This project uses Room 2.x. Do NOT introduce Room 3 APIs or test artifacts.

#### [ ] P4.1: Thumbnail Generation Pipeline (Week 15)

- **File:** New `apps/android/app/src/main/java/com/onyx/android/data/thumbnail/ThumbnailGenerator.kt`
- **Trigger:** On note save/create
- **Output:** 256x256 PNG in app cache directory
- **Storage:** `ThumbnailEntity` in Room with file path + content hash
- **Content hash algorithm:** Use SHA-256 of the generated PNG bytes (hex string) to detect stale thumbnails. This avoids mismatches across device locales and is stable across runs.
- **Cache eviction resilience:**
  - Android may clear the app cache directory at any time (low storage, user action)
  - `HomeScreen.kt` thumbnail loading must check if the file exists at the stored path
  - If missing: schedule async regeneration via `ThumbnailGenerator` and show a placeholder (e.g., note title text or generic icon) until ready
  - Content hash in `ThumbnailEntity` enables skip-regeneration if the note hasn't changed since last thumbnail
  - Regeneration should be throttled (max 3 concurrent) to avoid OOM on home screen bind with many missing thumbnails
  - Use a per-note `isRegenerating` flag (in-memory `ConcurrentHashMap<String, Boolean>`) to prevent redundant regeneration storms for the same note when cache is bulk-wiped
  - **Rate cap:** Limit total thumbnail regeneration to max 10 per minute at app startup to prevent CPU/memory spikes when cache directory is completely cleared. Use a `Semaphore(3)` for concurrency + `delay(1000)` between batches.
  - **Atomic file write:** Write thumbnails to a temp file (`{noteId}.tmp.png`) first, then `renameTo()` the final path (`{noteId}.png`). This prevents corrupt/partial thumbnails if the process is killed during write. If `renameTo()` fails (cross-filesystem), fall back to copy + delete.
- **Thumbnail file cleanup:**
  - **Soft delete (default):** `NoteRepository.deleteNote()` sets `deletedAt` and hides the note. Do NOT delete thumbnail rows/files on soft delete (supports restore).
  - **Permanent delete:** Use a new explicit purge path (`purgeNote()` or equivalent) to hard-delete the note row. On hard delete, delete thumbnail file(s) explicitly before removing the row, or perform cleanup in a `purgeNote()` transaction so cascade does not orphan files.
  - Do NOT rely on cache eviction for cleanup of intentionally deleted notes; cache eviction only handles system-initiated cleanups.

#### [ ] P4.2: Folder Organization (Flat) (Week 15-16)

- **Database:**

  ```kotlin
  @Entity
  data class FolderEntity(
      @PrimaryKey val folderId: String,
      val name: String,
      val parentId: String?, // reserved for future; keep null (flat folders only)
      val createdAt: Long,
  )

  // Add to NoteEntity:
  val folderId: String? = null
  ```

- **UI (flat folders only):** Folder cards in HomeScreen; no nesting UI or breadcrumbs in this milestone
- **Verify:** Create/delete/move notes between folders; deleting a folder moves notes to root without data loss.

#### [ ] P4.3: Tags/Labels (Week 16)

- **Database:**

  ```kotlin
  @Entity
  data class TagEntity(
      @PrimaryKey val tagId: String,
      val name: String,
      val color: String,
  )

  @Entity(primaryKeys = ["noteId", "tagId"])
  data class NoteTagCrossRef(
      val noteId: String,
      val tagId: String,
  )
  ```

- **UI:** Tag chips on note cards, filter by tag
- **Constraint:** `tags.name` is globally unique (single-user); enforce on insert/update and surface duplicate errors in UI.
- **Verify:** Creating a duplicate tag name fails with a user-facing error; tag filter updates list correctly.

#### [ ] P4.4: Multi-Select & Batch Operations (Week 17)

- **File:** Modify `HomeScreen.kt`
- **Selection state:**
  ```kotlin
  var selectionMode by mutableStateOf(false)
  var selectedNoteIds by mutableStateOf(setOf<String>())
  ```
- **Operations:** Delete selected, Move to folder, Add tag
- **Verify:** Batch operations update DB + UI consistently; selection state resets after operation.

#### [ ] P4.5: Sort & Filter Options (Week 17)

- **Sort by:** Name, Created, Modified (Size removed - NoteEntity lacks size column)
- **Filter by:** Folder, Tag, Date range
- **UI:** Dropdown in toolbar
- **Verify:** Sort order changes are stable and deterministic; filter counts match expected note totals.

### 8.2 Acceptance Criteria

| Feature          | Current       | Target                                                      |
| ---------------- | ------------- | ----------------------------------------------------------- |
| Note preview     | Text only     | Thumbnail                                                   |
| Organization     | Flat list     | Folders + Tags                                              |
| Batch delete     | No            | Yes                                                         |
| Sort options     | Modified only | 3 options                                                   |
| Migration        | Unknown       | No data loss on v2→v3 upgrade; existing notes open          |
| Cache wipe       | Unknown       | UI remains responsive; thumbnails regenerate ≤10/min        |
| Folder delete    | Unknown       | Notes move to root (`folderId = NULL`), not deleted         |
| Delete semantics | Unknown       | Soft delete hides notes; permanent delete purges rows/files |

---

## 9. Phase 5: Polish & Performance

**Duration:** 3 weeks (Week 18-20)
**Goal:** Bug fixes, performance tuning, edge cases

### 9.1 Tasks

#### [ ] P5.1: Stroke Ghosting Fix (Week 18)

- **Problem:** 1-2 frame visual duplication during pen-up
- **Solution:** Synchronize `InProgressStrokesView.removeFinishedStrokes()` with Compose recomposition
- **Implementation:** Use `InProgressStrokesView.setOnStrokeFinishedListener()`

#### [ ] P5.2: Canvas Flashing Fix (Week 18)

- **Problem:** White flash during rapid zoom
- **Solution:** Hold previous frame visible during tile render
- **Implementation:** `derivedStateOf` with previous bitmap fallback

#### [ ] P5.3: Toolbar Occlusion Fix (Week 18)

- **Problem:** Top 56dp of document hidden by toolbar
- **Solution:** Add `contentPadding` to canvas equal to toolbar height
- **File:** `NoteEditorUi.kt`

#### [ ] P5.4: Page Boundary Indicators (Week 18)

- **Add:** Subtle shadow/border around page edges
- **Add:** Edge glow when panning to limits
- **Verify:** Indicators are visible but non-intrusive at 1x/2x/4x; no overlap with toolbars.

#### [ ] P5.5: Performance Profiling (Week 19)

- **Tools:** Android Profiler, Perfetto/systrace (Macrobenchmark optional if a benchmark module is added)
- **Targets:**
  - App startup: <2s cold
  - Note open: <500ms
  - Stroke render: <16ms per frame
  - PDF tile: <100ms per tile (P95, matches SLO)

#### [ ] P5.6: Memory Leak Audit (Week 19)

- **Tools:** Android Studio Profiler; optionally LeakCanary (add `debugImplementation("com.squareup.leakcanary:leakcanary-android:<version>")` if not present)
- **Focus areas:** PDF document lifecycle, bitmap recycling, coroutine scopes

#### [ ] P5.7: Database Migration Verification & Instrumentation Tests (Week 19)

**NOTE:** Schema implementation (entities, DAOs, migration code) is done in P4.0. This task is VERIFICATION ONLY.

- **Instrumentation migration test (MANDATORY):**
  - Add instrumentation migration test using `MigrationTestHelper` with a v2 seed DB containing real note/page/stroke rows
  - Validate schema + retained data integrity after migrate-to-v3
  - **Testing artifact:** Use `androidx.room:room-testing` (Room 2.x artifact). Do NOT use `room3-testing` unless the project migrates to Room 3.x KSP.
  - `MigrationTestHelper` test pattern: `createDatabase(TEST_DB, 2)` → insert seed rows → `runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)` → query to verify data integrity
  - **Seed data must include:** At least 2 notes, 3 pages, 5 strokes with real protobuf data — verify FK cascade behavior by deleting a note and confirming cascades work
- **Verify schema export:** Confirm `schemas/` directory contains both v2 and v3 schema JSON files after P4.0 build.
- **Rollback documentation:** Document rollback SQL for `MIGRATION_2_3` (ALTER TABLE DROP COLUMN is not supported — document workaround if rollback is ever needed).
- **Rollback verification (staging):** Execute the documented rollback procedure on a copy of a v3 DB and confirm v2 reads succeed. Record results in `.sisyphus/notepads/db-rollback-drill.md`.

#### [ ] P5.8: Final Device Validation (Week 20)

- **Devices:** Galaxy Tab S9, Pixel Tablet (USI pen), one budget Android tablet
- **Tests:** Full regression of all SLOs on each device
- **Sign-off:** All acceptance criteria must pass on at least 2 devices
- **Low-RAM device pass (MANDATORY):** Run on a device with `memoryClass < 192` (budget tier per global budget table) or emulate via a low-RAM emulator profile (≤1GB RAM + `ro.config.low_ram=true`) or a debug-only override flag that forces low-RAM logic. Verify:
  - Cache budgets match budget tier: 16 MiB stroke tiles, 32 MiB PDF tiles, 4 MiB thumbnails (52 MiB total, per global budget table)
  - Log total bitmap memory against the global memory envelope (must stay under `memoryClass * 0.41` for budget tier — see table note about 41% being acceptable)
  - No OOM crashes during 10-page PDF pan/zoom stress test
- **Gesture conflict QA script (MANDATORY):** Execute the following interaction sequences on physical device and verify no accidental mode conflicts:
  1. Stylus draw → finger scroll (mid-stroke) → verify stroke is NOT interrupted
  2. Finger scroll → stylus draw (mid-scroll) → verify scroll stops cleanly and ink starts
  3. Two-finger pinch zoom → stylus draw (mid-zoom) → verify zoom completes or cancels, ink starts
  4. Palm rejection: palm resting on screen → stylus draw → verify no spurious ink or scroll
  5. All interactions in each mode (draw/pan/scroll) → verify no mode-switch jitter
- **Tile render time baseline (MANDATORY):** Record P50/P95 tile render time at each scale bucket (1x, 2x, 4x) on each test device. Save to `.sisyphus/notepads/tile-render-baseline.md`. This baseline enables regression detection across future releases.

### 9.1.1 Priority Missing Tests (add during Phase 5)

**Existing test coverage to leverage:** Transform/render math, ink geometry/taper/smoothing, PDF cache key, touch routing (unit: `apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorTransformMathTest.kt`, `apps/android/app/src/test/java/com/onyx/android/pdf/PdfRendererCacheKeyTest.kt`; instrumentation: `apps/android/app/src/androidTest/java/com/onyx/android/ink/ui/InkCanvasTouchRoutingTest.kt`).

**Highest-priority gaps to fill:**

| Test Area                           | Why Critical                                                     | Target File                                                                                                  |
| ----------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Tile invalidation bounds            | Verify stroke-width expansion prevents stale edge pixels         | `apps/android/app/src/test/java/com/onyx/android/ink/cache/StrokeTileCacheTest.kt` (new)                     |
| Async tile cancellation/leak safety | Verify `Semaphore` try/finally and bitmap cleanup on cancel      | `apps/android/app/src/test/java/com/onyx/android/pdf/AsyncPdfPipelineTest.kt` (new)                          |
| Multi-page coordinate conversion    | Verify document-space ↔ screen-space math across page boundaries | `apps/android/app/src/test/java/com/onyx/android/ui/MultiPageCoordinateTest.kt` (new)                        |
| v2→v3 migration integrity           | Verify FK cascades, data retention with real seed data           | `apps/android/app/src/androidTest/java/com/onyx/android/data/OnyxDatabaseMigrationTest.kt` (instrumentation) |
| Malformed PDF handling              | Verify graceful error handling on corrupt PDFs                   | `apps/android/app/src/test/java/com/onyx/android/pdf/MalformedPdfTest.kt` (new)                              |

### 9.2 Acceptance Criteria

All P1-P4 acceptance criteria maintained, plus:

| Metric         | Target | Measurement Method                                                                  |
| -------------- | ------ | ----------------------------------------------------------------------------------- |
| Memory leaks   | 0      | Android Studio Profiler heap dumps after 30-min soak (LeakCanary optional if added) |
| ANRs           | 0      | Logcat + `adb shell dumpsys activity anr` after stress runs                         |
| Crash rate     | 0      | 2-hour stress soak; logcat shows no crashes/tombstones                              |
| App size delta | <5 MiB | APK size comparison vs baseline                                                     |

---

## 10. Risk Assessment

### High-Risk Items

| Risk                              | Phase | Probability | Impact   | Mitigation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| --------------------------------- | ----- | ----------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Motion prediction artifacts       | P1    | Medium      | High     | Extensive device testing, feature flag                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| PdfiumAndroid API incompatibility | P0/P2 | Low         | Critical | Week 0 spike with go/no-go gate                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| PDF text selection regression     | P2    | Medium      | High     | Verify API parity in spike; keep MuPDF fallback code until parity confirmed. **Contingency:** If JNI bridge is needed and adds >1 week, keep MuPDF as selection-only backend for internal builds (AGPL concern is distribution-only) while Pdfium handles rendering. Cut over selection when bridge is ready. **LEGAL CHECKPOINT (MANDATORY):** If any MuPDF code remains in the codebase at Phase 5, conduct explicit legal review before release builds. AGPL requires source disclosure for distributed binaries — internal/debug builds are exempt but release builds are not. Document the review outcome in `.sisyphus/notepads/mupdf-legal-checkpoint.md`. |
| Multi-page coordinate bugs        | P3    | Medium      | High     | Comprehensive unit tests                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| Room migration data loss          | P4    | Low         | Critical | Test migrations on production DB copies                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |

### Medium-Risk Items

| Risk                               | Phase | Probability | Impact | Mitigation                                                                                                          |
| ---------------------------------- | ----- | ----------- | ------ | ------------------------------------------------------------------------------------------------------------------- |
| Memory pressure from dual caches   | P1+P2 | Medium      | Medium | Dynamic cache sizing based on device RAM                                                                            |
| LazyColumn scroll performance      | P3    | Medium      | Medium | Profile and optimize item composition                                                                               |
| Thumbnail generation OOM           | P4    | Low         | Medium | Generate thumbnails at low resolution                                                                               |
| SurfaceView/Compose layering       | P1    | Medium      | Medium | Investigate Jetpack Ink internals first; avoid custom GL if not needed                                              |
| Gesture conflicts (scroll vs draw) | P3    | High        | Medium | InteractionMode single-source-of-truth (P3.1); stylus always draws, finger mode-dependent; extensive manual testing |
| Bitmap flash during zoom           | P2    | Medium      | Low    | Dual-layer composable with crossfade; hold previous frame                                                           |

---

## 11. Success Metrics & SLOs

### Service Level Objectives

| Metric                | SLO    | Measurement                                                | Observation Window     | Sample Size  |
| --------------------- | ------ | ---------------------------------------------------------- | ---------------------- | ------------ |
| Ink latency (P95)     | <20ms  | 240fps slow-motion video analysis                          | 30s continuous drawing | 50+ strokes  |
| PDF tile render (P95) | <100ms | Perfetto/systrace tile render spans                        | 20 zoom cycles         | 100+ tiles   |
| App startup (P95)     | <2s    | `adb shell am start -W` + startup profiler                 | 10-run local sample    | 10+ runs     |
| Note open (P95)       | <500ms | Perfetto trace + log timing around note open               | 10-run local sample    | 10+ opens    |
| Frame drops (P95)     | <5%    | systrace frame timeline                                    | 60s pan/zoom gesture   | 3600+ frames |
| Crash-free sessions   | >99.9% | Manual soak (logcat/tombstones); Crashlytics if integrated | 2-hour soak            | 1 session    |

**Measurement Requirements:**

- Latency measurements must be on physical stylus device (Galaxy Tab S9 or equivalent)
- P95 thresholds require minimum sample sizes listed above
- Emulator measurements are not valid for SLO verification
- Telemetry (Firebase) is not assumed in this milestone; if added later, update SLO measurement methods accordingly.

### Key Performance Indicators

| KPI                    | Baseline   | Target                | Timeline | Validation Method                            |
| ---------------------- | ---------- | --------------------- | -------- | -------------------------------------------- |
| User-perceived ink lag | "Sluggish" | "Instant" (<20ms P95) | Week 5   | User testing (n=5) + 240fps measurement pass |
| Zoom smoothness        | "Choppy"   | "Smooth"              | Week 10  | 60fps sustained during 30s zoom test         |
| Document navigation    | "Tedious"  | "Intuitive"           | Week 14  | Task completion time <3s for 50-page jump    |
| Library organization   | "Basic"    | "Powerful"            | Week 17  | User testing (n=5), folder/tag task success  |

---

## 12. Dependencies & Prerequisites

### Hardware Requirements

- **Physical Android device** with stylus support for latency testing
- Recommended: Samsung Galaxy Tab S series or similar with active stylus
- Alternative: Pixel Tablet with USI stylus

### Software Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 34
- Kotlin 1.9+
- Compose BOM 2024.01+

### Blocked Tasks (from `docs/device-blocker.md`)

The following require physical device verification:

- Motion prediction tuning (P1.1)
- Front-buffer rendering (P1.3)
- Stroke ghosting fix (P5.1)
- Canvas flashing fix (P5.2)

### External Dependencies

| Dependency                                 | Version       | License          | Status                                        |
| ------------------------------------------ | ------------- | ---------------- | --------------------------------------------- |
| Jetpack Ink                                | 1.0.0-alpha02 | Apache 2.0       | In use                                        |
| PdfiumAndroid (`Zoltaneusz/PdfiumAndroid`) | 1.10.0        | Apache 2.0 / BSD | To add (verify JitPack + repo access in P0.0) |
| Room                                       | 2.6+          | Apache 2.0       | In use                                        |
| Protobuf                                   | 3.x           | BSD              | In use                                        |

---

## Appendix A: File Change Summary

### New Files

| File                                                                                   | Phase | Purpose                     |
| -------------------------------------------------------------------------------------- | ----- | --------------------------- |
| `apps/android/app/src/main/java/com/onyx/android/ink/cache/StrokeTileCache.kt`         | P1    | Tile-based stroke caching   |
| `apps/android/app/src/main/java/com/onyx/android/ink/perf/FrameBudgetManager.kt`       | P1    | Frame timing control        |
| `apps/android/app/src/main/java/com/onyx/android/pdf/PdfiumRenderer.kt`                | P2    | PdfiumAndroid wrapper       |
| `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileRenderer.kt`               | P2    | Tile-based PDF rendering    |
| `apps/android/app/src/main/java/com/onyx/android/pdf/PdfTileCache.kt`                  | P2    | PDF tile caching            |
| `apps/android/app/src/main/java/com/onyx/android/pdf/AsyncPdfPipeline.kt`              | P2    | Async render coordination   |
| `apps/android/app/src/main/java/com/onyx/android/ui/ThumbnailStrip.kt`                 | P3    | Page thumbnail navigation   |
| `apps/android/app/src/main/java/com/onyx/android/ui/PdfOutlineSheet.kt`                | P3    | Bookmark navigation         |
| `apps/android/app/src/main/java/com/onyx/android/data/thumbnail/ThumbnailGenerator.kt` | P4    | Note thumbnail creation     |
| `apps/android/app/src/main/java/com/onyx/android/data/entity/FolderEntity.kt`          | P4    | Folder data model           |
| `apps/android/app/src/main/java/com/onyx/android/data/entity/TagEntity.kt`             | P4    | Tag data model              |
| `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteTagCrossRef.kt`       | P4    | Note-tag join table model   |
| `apps/android/app/src/main/java/com/onyx/android/data/entity/ThumbnailEntity.kt`       | P4    | Thumbnail metadata model    |
| `apps/android/app/src/main/java/com/onyx/android/data/dao/FolderDao.kt`                | P4    | Folder persistence/query    |
| `apps/android/app/src/main/java/com/onyx/android/data/dao/TagDao.kt`                   | P4    | Tag + cross-ref operations  |
| `apps/android/app/src/main/java/com/onyx/android/data/dao/ThumbnailDao.kt`             | P4    | Thumbnail persistence/query |

### Modified Files

| File                                | Phase | Changes                                                     |
| ----------------------------------- | ----- | ----------------------------------------------------------- |
| `InkCanvasTouch.kt`                 | P1    | Enable motion prediction                                    |
| `InkCanvas.kt`                      | P1    | Style alignment, tile cache integration                     |
| `InkCanvasDrawing.kt`               | P1    | Compose pressure curve baseline + style parity refs         |
| `apps/android/app/build.gradle.kts` | P2    | Replace MuPDF with PdfiumAndroid                            |
| `NoteEditorScreen.kt`               | P2    | Replace renderer creation/lifecycle wiring                  |
| `NoteEditorShared.kt`               | P2    | Replace `rememberPdfBitmap` + render bucket full-page path  |
| `NoteEditorState.kt`                | P2    | Remove MuPDF-specific selection/state types                 |
| `NoteEditorPdfContent.kt`           | P2/P3 | Tile rendering + continuous scroll layout                   |
| `HomeScreen.kt`                     | P2/P4 | PDF import metadata reader migration + library UX updates   |
| `MyScriptPageManager.kt`            | P3    | Single-active-page recognition flow under continuous scroll |
| `ViewTransform.kt`                  | P3    | Multi-page coordinates                                      |
| `NoteEditorViewModel.kt`            | P3    | Virtualized loading + active recognition page routing       |
| `NoteEntity.kt`                     | P4    | Add folderId field                                          |
| `NoteDao.kt`                        | P4/P5 | Folder/tag/thumbnail query surfaces                         |
| `OnyxDatabase.kt`                   | P4/P5 | Version 3 entity registration + `MIGRATION_2_3`             |
| `NoteRepository.kt`                 | P4/P5 | Folder/tag/thumbnail repository methods                     |
| `NoteEditorUi.kt`                   | P5    | Toolbar padding fix                                         |

### Deleted Files

| File             | Phase | Reason                     |
| ---------------- | ----- | -------------------------- |
| `PdfRenderer.kt` | P2    | Replaced by PdfiumRenderer |

---

## Appendix B: Test Plan

### Unit Tests

| Area               | Test Cases                         | Coverage Target |
| ------------------ | ---------------------------------- | --------------- |
| StrokeTileCache    | LRU eviction, invalidation, bounds | 90%             |
| PdfTileRenderer    | Tile coordinates, scale buckets    | 85%             |
| FrameBudgetManager | Budget enforcement, edge cases     | 95%             |
| ThumbnailGenerator | Format, sizing, error handling     | 80%             |
| FolderEntity       | Hierarchy queries, orphan handling | 90%             |

### Integration Tests

| Scenario                   | Tools                    | Pass Criteria                                                          |
| -------------------------- | ------------------------ | ---------------------------------------------------------------------- |
| Motion prediction + pen-up | Espresso + stylus events | No visual artifacts                                                    |
| PDF zoom cycle             | Perfetto/systrace        | <100ms P95                                                             |
| PDF selection parity       | Instrumentation + UI     | Selection within ≤2dp; copied text matches corpus                      |
| Continuous scroll          | Perfetto/systrace        | 60fps                                                                  |
| Folder operations          | Room test                | Data integrity                                                         |
| Room migration v2→v3       | MigrationTestHelper      | Existing note/page/stroke rows preserved; new tables/query paths valid |

### Manual Tests (Device Required)

| Test                   | Device        | Criteria                                                 |
| ---------------------- | ------------- | -------------------------------------------------------- |
| Ink latency perception | Galaxy Tab S9 | P95 <20ms via 240fps measurement; 0 visible gaps         |
| Stroke smoothness      | Galaxy Tab S9 | No jaggies in 240fps playback across slow/fast strokes   |
| PDF text clarity       | Any tablet    | Sharp at 1x/2x/4x on corpus; no visible seams            |
| Thumbnail accuracy     | Any tablet    | Matches first page content; text legible; no crop errors |

---

## Appendix C: Rollback Plan

### Feature Flags

All major changes will be behind feature flags:

```kotlin
object FeatureFlags {
    var motionPredictionEnabled = false
    var tileCachingEnabled = false
    var pdfiumEnabled = false
    var continuousScrollEnabled = false
    var foldersEnabled = false
}
```

### Rollback Triggers

| Symptom                   | Threshold   | Action                         |
| ------------------------- | ----------- | ------------------------------ |
| Crash rate spike          | >1%         | Disable related feature flag   |
| ANR rate spike            | >0.5%       | Disable related feature flag   |
| User complaints (latency) | >10 reports | Revert motion prediction       |
| Data loss reports         | >1          | Emergency rollback, DB restore |

### Database Rollback

- **Realistic stance:** SQLite does not support `ALTER TABLE DROP COLUMN` (added in SQLite 3.35.0, but Room may not fully support it on all Android versions). True reversibility is not guaranteed.
- **Practical approach:**
  1. Keep backup of production DB before each release (mandatory)
  2. Document rollback SQL for each migration (best-effort; may require table recreation)
  3. For v2→v3: rollback would require exporting data, recreating v2 schema, re-importing — document this procedure but expect it to be emergency-only
- **Verification:** Run a rollback drill on a staging copy of the v3 DB at least once and record results in `.sisyphus/notepads/db-rollback-drill.md`.
- **Forward-only migrations are acceptable** if backups are reliable and tested.

---

## Changelog

| Version | Date       | Author       | Changes                                                                                                                                                                                                                                                                                                                                        |
| ------- | ---------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0     | 2026-02-13 | AI Assistant | Initial draft                                                                                                                                                                                                                                                                                                                                  |
| 1.1     | 2026-02-13 | AI Assistant | Momus feedback corrections                                                                                                                                                                                                                                                                                                                     |
| 1.2     | 2026-02-13 | AI Assistant | Final revisions: Phase 4 week numbers, expanded risks, SLO details                                                                                                                                                                                                                                                                             |
| 1.3     | 2026-02-13 | AI Assistant | Rejection fixes: concrete PDF/recognition integration points, corrected PDF threading claim, explicit Room v2→v3 migration/DAO-repository plan                                                                                                                                                                                                 |
| 1.4     | 2026-02-13 | AI Assistant | Updated PDF fork selection to `Zoltaneusz/PdfiumAndroid` and aligned dependency/source notes                                                                                                                                                                                                                                                   |
| 1.5     | 2026-02-13 | AI Assistant | Momus review fixes: tile cache byte-bounded sizing, Pdfium thread-safety/lifecycle rules, async pipeline dedup/cancellation, interaction mode architecture, thumbnail cache resilience, Room testing artifact clarification                                                                                                                    |
| 1.6     | 2026-02-13 | AI Assistant | Momus round 2 fixes: renderPageBitmap coord contract, text selection native bridge fallback, LruCache Mutex guards, tile invalidation bounds expansion, per-page clipping, dynamic cache sizing, predicted-point buffer separation, PDF tile bitmap config, canonical document-space coords, per-note thumbnail throttling, Room schema export |
| 1.7     | 2026-02-13 | AI Assistant | Momus round 3 fixes: tile bitmap ref-safety, Compose pointer API + pointerInteropFilter fallback, PDF page rotation normalization, global memory budget envelope, inFlight concurrency Mutex, MuPDF selection contingency, nested scroll interop fallback, thumbnail regen rate cap                                                            |
| 1.8     | 2026-02-13 | AI Assistant | Momus round 4 fixes: Phase 0 exit artifacts (parity report), decided bitmap ref-safety Strategy A (ImageBitmap), in-flight bitmap memory cap (Semaphore), LazyListState page tracking policy with anti-oscillation, atomic thumbnail file write                                                                                                |
| 1.9     | 2026-02-13 | AI Assistant | Momus round 5 fixes: corrected ImageBitmap lifecycle (cache stores Android Bitmap, convert at draw time), AndroidView clipping for InProgressStrokesView (`View.setClipBounds`), Semaphore try/finally cancellation safety in async tile pipeline                                                                                              |
| 2.0     | 2026-02-13 | AI Assistant | Momus round 6 fixes: cancellation bitmap cleanup, Mutex lock scope guidance, definitive 512×512 tile size, stable LazyColumn keys, Room index/constraint specs, Phase 0 rotation + RTL validation, P5.8 low-RAM pass + gesture QA script + tile render baseline                                                                                |
| 2.1     | 2026-02-13 | AI Assistant | Momus round 7 fixes: recalculated memory budget table, aligned P95 targets, scale bucket policy, Room FK constraints + ON DELETE, thumbnail cleanup, contentType, isRecycled guard, text selection scope, renderPageBitmap fallback                                                                                                            |
| 2.2     | 2026-02-13 | AI Assistant | Momus round 8 fixes: corrected budget table to 25-35% target, single InProgressStrokesView overlay, zoom-bucket hysteresis, PdfiumCore singleton lifecycle, uncommitted stroke buffer on unload, aligned cache sizes                                                                                                                           |
| 2.3     | 2026-02-13 | AI Assistant | Momus round 9 fixes: scale bucket references (5→3), P5.8 tile render baseline aligned to 3 buckets, explicit fork lock with URL, stale tile count math clarifications                                                                                                                                                                          |
| 2.4     | 2026-02-13 | AI Assistant | Momus round 10 fixes: PRE-TASK for 5→3 bucket code alignment, runtime downshift rule for memory exception, Phase 0 runnable spike snippet requirement, P4 sequencing (schema before UI), Pdfium thread-safety default assumption                                                                                                               |
| 2.5     | 2026-02-13 | AI Assistant | Momus round 11 fixes: Pdfium API surface caveat, ILLUSTRATIVE pseudocode, P4.0 DB schema task (moved from P5.7), P5.7 verification-only, Priority Missing Tests section                                                                                                                                                                        |
| 2.6     | 2026-02-13 | AI Assistant | Momus round 12 fixes: P0.2.5 dependency viability check (minSdk), JitPack coordinate only, text-selection BINARY GATE, LruCache thread-safety correction, rollback stance fix, test scope in bucket pre-task                                                                                                                                   |
| 2.7     | 2026-02-13 | AI Assistant | Momus round 13 fixes: P0.0 Pre-Gate (fork viability), P2.0 renderer-agnostic text model, HomeScreen import MUST-COMPLETE, text-selection FALLBACK DECISION, bucket pre-task BLOCKING emphasis                                                                                                                                                  |
| 2.8     | 2026-02-13 | AI Assistant | Added milestone exit gate + explicit non-goals, tightened phase exit gates/acceptance criteria, resolved folder/tag decisions, added missing file/test paths, added Phase 0 integration smoke + PDF corpus, clarified Pdfium dependency handling, fixed cache sizing diagram mismatch, strengthened release license and memory envelope checks |
| 2.9     | 2026-02-13 | AI Assistant | Fixed Room v3 schema specs to match noteId-based schema, added DDL alignment checklist, clarified gesture routing integration, added explicit tile-range math contract, added task-level verification bullets, removed alternate-fork suggestions to honor locked Pdfium fork                                                                  |
| 2.10    | 2026-02-13 | AI Assistant | Aligned Gradle command paths and settings file location, clarified soft vs hard delete semantics for thumbnails/tags, tightened MuPDF release-build verification path, and updated Phase 4 acceptance for delete semantics                                                                                                                     |
| 2.11    | 2026-02-13 | AI Assistant | Fixed Room migration SQL feasibility (notes table recreation), defined selection geometry as quads in page points, added clipboard UX requirement, specified spike project execution, removed reliance on missing telemetry tools (local measurements), and updated SLO/acceptance accordingly                                                 |
| 2.12    | 2026-02-13 | AI Assistant | Corrected current-state PDF scale bucket count to 5 (pre-task reduces to 3), eliminating doc inconsistency                                                                                                                                                                                                                                     |
| 2.13    | 2026-02-13 | AI Assistant | Added Appendix D: Acknowledged Future Backlog — 9 gap items from source analysis docs explicitly tracked; updated non-goals to reference backlog                                                                                                                                                                                               |

---

## Appendix D: Acknowledged Future Backlog

**Purpose:** This appendix captures known gaps identified in source analysis documents (`docs/context/chat-analysis.txt`, `docs/context/gemini-analysis.txt`, `docs/context/chat-plan-2.txt`, `docs/context/gemini-deep-research.txt`, `.sisyphus/notepads/android-architecture-review.md`, `docs/architecture/full-project-analysis.md`, `docs/architecture/branch-architecture-analysis.md`) that are **explicitly out of scope** for this milestone but must not be lost. Each item is tracked here with priority, evidence, and relationship to the current plan.

These items should be promoted to concrete tasks in a subsequent milestone plan.

### B1: Unified Search Across Handwritten + Typed PDF Content (CRITICAL)

**Priority:** Critical  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Source docs identify unified search across handwritten content AND PDF text as a key competitive feature (MyScript Notes positions this as a "magic" differentiator). The current app has PDF text selection and FTS on recognized handwriting text (`HomeScreen.kt` search), but these are separate — there is no unified search experience that spans both. PDF text is not indexed into the global search.

**Evidence:**

- `docs/context/chat-analysis.txt:594` — "Global search across the library, including handwritten content and PDF annotations"
- `docs/context/chat-analysis.txt:613` — "typed PDF text is not indexed into the same global search experience"
- `docs/context/gemini-analysis.txt:90` — unified search across ink and PDF text identified as high-priority gap
- `docs/context/chat-plan-2.txt:13` — search across all content types listed as a requirement

**Relationship to this plan:** This plan explicitly excludes unified search and PDF text search UI (non-goals section). The current milestone builds the PDF text extraction layer (P2.0 `PdfTextExtractor`) and the MyScript recognized-text pipeline, which are prerequisites for a unified search index — but the indexing and search UI are not built here.

**Recommended next steps:**

1. Build a unified `SearchIndex` that ingests both MyScript JIIX output and `PdfTextExtractor` output per page
2. Extend `NoteDao` FTS queries to include PDF text content
3. Add search results UI that highlights matches in both ink recognition and PDF text
4. Consider indexing at note-save time (background worker) for performance

---

### B2: Sync-Readiness Infrastructure (HIGH)

**Priority:** High — sync is a prerequisite for multi-device usage, which is a core product requirement. Without hardened Lamport clocks, deviceId embedding, and an op log, any future sync implementation risks data loss or silent corruption.  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Source docs identify concrete sync infrastructure issues that must be resolved before multi-device sync can work correctly: Lamport clock increment hardening, embedding `deviceId` into stroke/op metadata, and creating an operation log for CRDT-based conflict resolution.

**Evidence:**

- `.sisyphus/notepads/android-architecture-review.md:78` — Lamport clock increment concerns, deviceId missing from stroke metadata
- `docs/architecture/full-project-analysis.md:330` — sync architecture gaps (Lamport timestamps present but not hardened)
- `docs/architecture/full-project-analysis.md:332` — deviceId not embedded in operations
- `docs/architecture/full-project-analysis.md:340` — no op log for conflict resolution

**Relationship to this plan:** This plan notes that Lamport timestamps exist and are used for CRDT ordering (§1.3 Stroke Data Model) and the data model is described as "solid" (§1.3 "What Works"). However, no tasks address the hardening work needed for actual sync. The v2→v3 Room migration (P4.0) does not add sync-related columns.

**Recommended next steps:**

1. Add `deviceId TEXT NOT NULL` column to `strokes` table (or a new `operations` table)
2. Harden Lamport clock: ensure monotonic increment across app restarts (persist high-water mark)
3. Design and implement an append-only operation log for stroke CRUD events
4. Validate CRDT merge correctness with simulated multi-device scenarios

---

### B3: Editor Architecture Debt (HIGH)

**Priority:** High  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** `NoteEditorUi.kt` is a monolithic composable that handles too many concerns. `HomeViewModel` instantiation uses manual DI rather than Hilt injection. These architectural debts increase the cost and risk of future feature work.

**Evidence:**

- `.sisyphus/notepads/android-architecture-review.md:29` — NoteEditorUi.kt identified as needing split (routing, toolbar, content separation)
- `docs/architecture/branch-architecture-analysis.md:42` — editor composable complexity flagged
- `docs/architecture/full-project-analysis.md:140` — ViewModel DI issues (instantiated inside composables)
- `docs/architecture/full-project-analysis.md:638` — architectural debt in editor file organization

**Relationship to this plan:** This plan touches `NoteEditorUi.kt` only for toolbar padding fix (P5.3) and PDF mode routing (P3.1). No tasks decompose the file or address DI hardening.

**Recommended next steps:**

1. Split `NoteEditorUi.kt` into: `NoteEditorRouting.kt` (mode switching), `NoteEditorToolbar.kt` (tool selection), `NoteEditorContent.kt` (canvas/PDF host)
2. Extract `HomeViewModel` creation into Hilt `@HiltViewModel` with proper `@Inject constructor`
3. Audit all ViewModel instantiations inside composables and migrate to `hiltViewModel()` / `viewModel()` with factory

---

### B4: MyScript Backlog Items (HIGH)

**Priority:** High  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Multiple MyScript integration improvements are identified in source docs but not covered by any tasks. The current plan limits MyScript scope to active-page routing under continuous scroll (P3.2).

**Evidence:**

- `docs/architecture/full-project-analysis.md:287` — debounced recognition (batch strokes before triggering recognition to reduce CPU churn)
- `docs/architecture/full-project-analysis.md:299` — `recognizeAll` API for bulk re-recognition of existing pages
- `docs/architecture/full-project-analysis.md:303` — ContentPackage file lifecycle cleanup (orphaned `.nebo` files)
- `docs/architecture/full-project-analysis.md:305` — configurable recognition language (currently hardcoded)
- `docs/architecture/full-project-analysis.md:312` — JIIX-driven indexing (parse JIIX output for structured search data)
- `docs/architecture/full-project-analysis.md:632` — scratch-out gesture recognition (erase by scribbling over text)

**Relationship to this plan:** Recognition scope is explicitly limited to "offscreen only" and "active-page routing" (P3.2). The plan does not add debouncing, language configuration, bulk recognition, or cleanup.

**Recommended next steps:**

1. Add stroke debounce timer (e.g., 300ms after last stroke) before triggering recognition
2. Implement `recognizeAll()` for re-indexing existing notes after language change
3. Add ContentPackage cleanup on note delete (purge orphaned `.nebo` files)
4. Add language picker in settings (persist in `SharedPreferences`, pass to `MyScriptEngine`)
5. Parse JIIX output for structured word/line/paragraph data usable by search index (ties into B1)
6. Evaluate scratch-out gesture feasibility with MyScript's `InteractiveInk` API

---

### B5: Hierarchical Folder Model (MEDIUM)

**Priority:** Medium  
**Status:** Deferred — flat folders shipped in this milestone; hierarchy planned for future

**Gap:** Source docs call for hierarchical folders with breadcrumb navigation and drag-drop reordering. This plan locks folders to flat-only for this milestone, with `parentId` reserved but unused.

**Evidence:**

- `docs/context/chat-plan-2.txt:11` — hierarchical folder structure with breadcrumbs requested
- `docs/context/gemini-deep-research.txt:1` — folder hierarchy and drag-drop organization identified as competitive requirement (note: this file is a single-line document containing the full Gemini deep research report)

**Relationship to this plan:** P4.2 implements flat folders with `parentId` column present but always `NULL`. The Scope Boundaries section explicitly states "no nesting UI or breadcrumbs in this milestone." The schema (P4.0) includes `folders.parentId` index for future use.

**Recommended next steps:**

1. Enable `parentId` writes in `FolderDao` and `FolderRepository`
2. Build breadcrumb navigation UI in `HomeScreen.kt`
3. Add drag-drop folder reordering (long-press drag on folder cards)
4. Handle recursive delete semantics (cascade vs flatten children to parent)

---

### B6: PDF Annotation Persistence Separation (MEDIUM)

**Priority:** Medium — annotation separation enables annotation-specific features (visibility toggle, export, re-import survivability) that are expected by users who annotate imported PDFs. Without separation, PDF re-import or re-render could lose annotation positioning context.  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** PDF annotations (ink drawn on top of PDF pages) should be stored in a separate persistence layer from normal note ink, enabling annotation-specific features like annotation export, annotation visibility toggle, and PDF re-import without losing annotations.

**Evidence:**

- `.sisyphus/notepads/android-architecture-review.md:114` — annotation layer separation identified as needed for PDF ink persistence

**Relationship to this plan:** This plan stores PDF-overlay strokes using the same `StrokeEntity`/`StrokeRepository` as normal ink strokes, associated by `pageId`. There is no `annotationType` field or separate annotation table. The plan does not distinguish between "ink on blank page" and "ink on PDF page" at the data level.

**Recommended next steps:**

1. Add `annotationType` enum to `StrokeEntity` (or a separate `AnnotationEntity` table)
2. Support annotation visibility toggle (show/hide annotations on PDF pages)
3. Support annotation export (extract annotations as a separate PDF overlay or XFDF)
4. Ensure annotation strokes survive PDF re-import (match by page index + position)

---

### B7: PDF Text Hit-Testing Spatial Index (MEDIUM)

**Priority:** Medium  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** The current text selection hit-testing (`findCharAtPagePoint`) performs linear scan over characters. For dense PDFs with many characters per page, this becomes a performance bottleneck. A spatial index (e.g., R-tree or grid-based) for per-character bounding boxes would improve selection responsiveness.

**Evidence:**

- `.sisyphus/notepads/android-architecture-review.md:112` — linear scan for character hit-testing identified as performance concern
- `.sisyphus/notepads/android-architecture-review.md:335` — spatial index recommended for text hit-testing

**Relationship to this plan:** P2.2 defines text selection parity behavior and the `PdfTextChar` model with per-character quads, but does not specify an indexing data structure for hit-testing. The current linear scan is preserved.

**Recommended next steps:**

1. Build a grid-based spatial index (e.g., 50x50 grid cells per page) populated from `PdfTextChar` quads
2. `findCharAtPagePoint()` queries the grid cell first, then linear-scans only chars in that cell
3. Benchmark: expect O(1) grid lookup + O(k) local scan vs O(n) full scan; target <5ms for dense pages

---

### B8: Stylus Advanced Interactions (MEDIUM)

**Priority:** Medium  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Competitors support stylus button quick-erase (press button while drawing to erase) and hover cursor (showing a dot/crosshair where the pen will land before touching). Palm rejection is mentioned in P5.8 but only as a QA validation check, not as an implementation task — if the OS/OEM palm rejection is insufficient, no custom implementation is planned.

**Evidence:**

- `docs/context/gemini-deep-research.txt:1` — stylus button interactions and hover cursor identified as competitive features (note: single-line document containing full Gemini deep research report)
- `docs/context/chat-plan-2.txt:31` — stylus button erase and advanced pen interactions requested

**Relationship to this plan:** P5.8 includes a palm rejection QA validation step ("palm resting on screen → stylus draw → verify no spurious ink") but treats palm rejection as an OS-provided feature to validate, not something to implement. Stylus button mapping and hover cursor are not mentioned anywhere in the plan.

**Recommended next steps:**

1. Map stylus button press to eraser tool toggle (`MotionEvent.BUTTON_STYLUS_PRIMARY`)
2. Implement hover cursor: show a small circle at the pen's hover position using `MotionEvent.ACTION_HOVER_MOVE`
3. If OS palm rejection is insufficient on target devices, implement custom palm rejection using touch area + pressure heuristics
4. Test on S-Pen, USI, and Wacom EMR stylus types

---

### B9: Competitive Editor Tools (LOW)

**Priority:** Low  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Competitor apps (Samsung Notes, GoodNotes, Notewise) offer advanced editor tools that are not planned in this or any other milestone: lasso selection with transform (move, resize, rotate selected strokes), shape recognition tool, tape/ruler tool, zoom-box for detail work, and segment eraser (erase parts of strokes at intersection points rather than whole strokes).

**Evidence:**

- `docs/context/chat-plan-2.txt:21` — lasso transform, shape tool listed as competitive requirements
- `docs/context/chat-plan-2.txt:487` — tape/ruler tool and zoom box described
- `docs/context/chat-plan-2.txt:509` — segment eraser described as competitive differentiator
- `docs/context/gemini-deep-research.txt:1` — comprehensive editor tool comparison showing gaps (note: single-line document containing full Gemini deep research report)

**Relationship to this plan:** The plan implements basic eraser and selection modes via `InteractionMode` enum (P3.1), but does not include lasso transform, shape recognition, tape tool, zoom box, or segment eraser. These require significant architectural additions (hit-testing infrastructure, transform matrices for stroke groups, shape recognition ML/heuristics).

**Recommended next steps:**

1. **Lasso selection + transform:** Add `SelectionRegion` model, hit-test strokes against lasso polygon, apply affine transform on drag/resize/rotate
2. **Segment eraser:** Split strokes at eraser intersection points, creating new stroke segments — requires path intersection math
3. **Shape recognition:** Use simple heuristics (line detection, circle fitting) or ML model to snap free-drawn shapes
4. **Tape/ruler tool:** Render a straight-line guide overlay that constrains ink input to the line
5. **Zoom box:** Render a magnified inset of a selected region for detail work

---

### B10: Conversion as a First-Class Interaction (MEDIUM)

**Priority:** Medium — competitors (MyScript Notes, Notewise) make handwriting-to-text conversion feel like a natural extension of writing. Without a polished conversion UX, Onyx's recognition pipeline remains invisible plumbing rather than a user-facing feature.  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Source docs identify that while Onyx has recognition and search plumbing, the conversion _experience layer_ — discoverability, editing the converted result, round-tripping between ink and text, confidence display — is not yet a polished user feature.

**Evidence:**

- `docs/context/chat-analysis.txt:617` — "Conversion as a first-class interaction" — MyScript and Notewise make conversion feel like a natural extension of writing (select → convert → continue)
- `docs/context/chat-analysis.txt:619` — Onyx has recognition + search plumbing but the conversion experience layer is not visible as a polished feature

**Relationship to this plan:** This plan routes MyScript recognition per-page under continuous scroll (P3.2) but does not build any conversion UI. The recognized text is used only for background search indexing, not for user-facing convert-to-text workflows.

**Recommended next steps:**

1. Add "Convert to Text" action in lasso selection context menu
2. Display recognized text overlay (toggle) with confidence indicators
3. Support editing the converted result inline (round-trip: ink → text → editable text block)
4. Show per-word confidence so users can correct low-confidence recognitions

---

### B11: Interactive Ink Reflow and Beautification (MEDIUM)

**Priority:** Medium — MyScript's "active ink" (reflow, beautification, gesture-based editing) is a key differentiator that makes handwritten notes feel alive rather than static. Without it, Onyx treats ink as a static image overlay.  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** MyScript SDK supports "Interactive Ink" features where ink is live: text reflows when content is inserted above, math equations snap from handwriting to typeset LaTeX, and gestures (scratch-out, insert space) edit content directly. Onyx currently treats handwriting as static pixels.

**Evidence:**

- `docs/context/gemini-analysis.txt:76` — "You are currently treating handwriting as a 'static image' overlay"
- `docs/context/gemini-analysis.txt:82` — MyScript Notes ink is "alive" with reflow: "When text is inserted above, the ink moves down"
- `docs/context/gemini-analysis.txt:83` — Math beautification: "the math equations snap instantly from messy handwriting to typeset LaTeX"
- `docs/context/gemini-analysis.txt:84` — Interactive ink gestures: scratch-out to delete, vs Onyx's "clumsy Eraser Tool or Undo Button"

**Relationship to this plan:** This plan uses MyScript SDK only for background recognition (P3.2) and does not implement any Interactive Ink features. The ink rendering pipeline (Phase 1) treats strokes as immutable path data after pen-up.

**Recommended next steps:**

1. Evaluate MyScript Interactive Ink SDK APIs for reflow and beautification callbacks
2. Implement ink reflow: when content is inserted, shift strokes below the insertion point
3. Add math beautification: detect math expressions and offer typeset rendering
4. Implement Interactive Ink gestures (scratch-out to delete, caret-insert to add space)

---

### B12: Template System and UI Ergonomics (MEDIUM)

**Priority:** Medium — competitors offer configurable page templates (grid spacing, line opacity, dot grids) and floating/dockable toolbars. These are table-stakes features for note-taking apps on tablets.  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Onyx has only a white background with no page template options. Competitors (Notewise) offer configurable grids, lines, dot patterns with adjustable spacing and opacity, drawn programmatically for zoom-invariance. Additionally, the toolbar is a fixed top bar requiring reach on large tablets, while competitors offer floating/dockable toolbars.

**Evidence:**

- `docs/context/gemini-analysis.txt:103` — "The Toolbar Gap": Onyx has a static top bar; Notewise has a floating, detachable, or side-docked toolbar
- `docs/context/gemini-analysis.txt:108` — "Template Engine": Onyx has white background only
- `docs/context/gemini-analysis.txt:110` — Notewise has "Page Settings" modal with fine-grained template controls
- `docs/context/gemini-analysis.txt:112` — Template granularity: "Grid with 5mm spacing" or "Lines with 20% opacity"
- `docs/context/gemini-analysis.txt:113` — Templates drawn programmatically (shaders) for perfect zoom scaling

**Relationship to this plan:** This plan adjusts toolbar padding (P5.3) but does not redesign toolbar positioning or add page templates. The canvas rendering overhaul (Phase 1) does not include a background template rendering layer.

**Recommended next steps:**

1. Add page template engine: programmatically rendered grids (configurable spacing), lines, dot grids using Canvas draw commands
2. Store template preference per-note in Room (new `templateType`/`templateConfig` columns)
3. Implement floating/dockable toolbar with position persistence
4. Ensure templates scale correctly with zoom (render in document space, not screen space)

---

### B13: Recognition Overlay and Lasso-Convert Pipeline (MEDIUM)

**Priority:** Medium — combining recognition overlay with lasso selection for targeted conversion is a workflow that power users expect. Without it, recognition results are invisible.  
**Status:** Deferred (not scheduled in any milestone)

**Gap:** Source docs describe a pipeline where recognized text is shown as a toggleable overlay on top of ink, and lasso selection of a region triggers handwriting-to-text conversion for that specific area. This combines recognition display with spatial selection for targeted conversion.

**Evidence:**

- `docs/context/chat-plan-2.txt:509` — "Recognition overlay + lasso convert": overlay recognized text (toggle), lasso select region to convert handwriting to text block, batch recognition pipeline (throttled)

**Relationship to this plan:** This plan does not implement recognition overlay display or lasso-based conversion. MyScript recognition (P3.2) runs in the background for search indexing only. Lasso selection is listed as a deferred competitive tool in B9.

**Recommended next steps:**

1. Add toggleable recognition text overlay (render recognized text semi-transparently above ink strokes)
2. Implement lasso-select → convert workflow: select region, extract strokes, run recognition, replace with text block
3. Add batch recognition pipeline with throttling for bulk re-recognition
4. Connect overlay display to MyScript JIIX output (word/line bounding boxes)
