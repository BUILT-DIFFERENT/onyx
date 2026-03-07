# Onyx — Architecture & Feature Plan

> Living reference document. Last updated: 2026-03-07.

---

## Table of Contents

1. [App Overview](#1-app-overview)
2. [Target Users & Platform](#2-target-users--platform)
3. [Tech Stack](#3-tech-stack)
4. [Architecture Overview](#4-architecture-overview)
5. [Data Model](#5-data-model)
6. [Feature Set](#6-feature-set)
7. [Rendering Pipeline](#7-rendering-pipeline)
8. [Sync Architecture](#8-sync-architecture)
9. [Recognition Pipeline (IInk)](#9-recognition-pipeline-iink)
10. [PDF Support](#10-pdf-support)
11. [Collaboration](#11-collaboration)
12. [Organization & Search](#12-organization--search)
13. [Web Viewer](#13-web-viewer)
14. [Current Status & Priority](#14-current-status--priority-as-of-2026-03-07)
15. [Phased Roadmap](#15-phased-roadmap)
16. [Open Questions & Decisions](#16-open-questions--decisions)

---

## 1. App Overview

Onyx is a handwritten note-taking application targeting engineering and STEM students. It delivers a best-in-class stylus experience on Android (Samsung tablets with S Pen) while providing a web viewer for passive consumption of notes.

**Primary goals:**

- Lowest achievable input-to-ink latency via a custom Vulkan rendering pipeline with motion prediction
- Full pressure and tilt sensitivity leveraging the S Pen hardware
- Offline-first operation with optional real-time collaboration powered by CRDT-based sync
- MyScript IInk SDK integration for handwriting recognition, shape recognition, and LaTeX equation recognition — recognition only, rendering is handled by the custom pipeline
- Flexible organization (folders, notebooks, pages) with HWR-indexed search

---

## 2. Target Users & Platform

| Dimension | Detail |
|---|---|
| Primary users | Engineering / STEM students |
| Primary device | Samsung tablets with Samsung S Pen |
| Input model | Stylus-only — finger drawing is disabled |
| Primary platform | Android (Kotlin + Jetpack Compose) |
|| Secondary platform | Web viewer (TanStack Start, read-only) |

---

## 3. Tech Stack

### 3.1 Android

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI framework | Jetpack Compose |
| Ink rendering | Vulkan (via Android NDK) — custom pipeline |
| Local database | Room (SQLite) — metadata, CRDT state, offline op queue |
| Background sync | WorkManager |
| PDF rendering | PdfiumAndroid (Zoltaneusz/PdfiumAndroid:v1.10.0) |
| PDF text extraction | Apache PDFBox Android |
| Handwriting recognition | MyScript IInk SDK 4.3 (recognition only, no rendering) |
| CRDT engine | Y-Octo (Rust, compiled via Android NDK/JNI) — binary compatible with Yjs on web |

### 3.2 Backend

| Service | Role |
|---|---|
| Convex | Metadata, user data, sharing settings, CRDT operation deltas (small payloads only) |
| Cloudflare R2 | Large blobs: PDFs, images, audio recordings, periodic stroke snapshots |

Convex stores blob references (R2 URL + version hash); it never stores raw binary data directly.

### 3.3 Auth

- Clerk (preferred) or WorkOS — decision deferred to Phase 4
- Google Sign-In as the primary OAuth provider

### 3.4 Web

| Layer | Technology |
|---|---|
| Framework | TanStack Start |
|| PDF rendering | Tile rasters (no raw PDF download in viewer) |
| Real-time | Convex real-time subscriptions |
| HWR recognition | MyScript cloud API via WebSocket / REST (secondary, online-only) |

---

## 4. Architecture Overview

```
+-------------------------------------------------------------+
|                        Android App                          |
|                                                             |
|  +-------------+   +--------------+   +-----------------+  |
|  |  Compose UI  |   |  Vulkan Ink  |   |  IInk SDK 4.3   |  |
|  |  (toolbar,   |   |  Canvas      |   |  (recognition   |  |
|  |   panels)    |   |  (NDK layer) |   |   only)         |  |
|  +------+-------+   +------+-------+   +--------+--------+  |
|         |                  |                     |           |
|  +------v------------------v---------------------v--------+  |
|  |                   ViewModel / State                     |  |
|  +---------------------------+------------------------------+  |
|                              |                              |
|  +---------------------------v------------------------------+  |
|  |            Room DB  +  Y-Octo CRDT Doc (Rust/JNI)        |  |
|  |         (metadata, op log, offline queue)               |  |
|  +---------------------------+------------------------------+  |
|                              | WorkManager                  |
+------------------------------+------------------------------+
                               |
               +---------------+----------------+
               v               v                v
          Convex RT        Convex DB        Cloudflare R2
         (live deltas)   (metadata,       (PDFs, images,
                          shares)          audio, snapshots)
               |
               v
         +-----------+
         | TanStack  |
         | Start     |
         | Web Viewer|
         | (read-only)|
         +-----------+
```

### Key Architectural Principles

1. **Offline-first**: The app is fully functional without a network connection. All writes go to Room and the local Yjs doc first; sync is opportunistic.
2. **Rendering isolation**: The Vulkan ink canvas is a separate NDK layer. Compose UI renders chrome (toolbar, panels) around it without touching the ink surface.
3. **Recognition decoupled from rendering**: IInk SDK receives stroke data for recognition purposes only. It never drives pixel output.
4. **Blob references, not blobs in the DB**: Convex holds only metadata and CRDT deltas. All binary assets live in R2; Convex records the URL and a version hash.

---

## 5. Data Model

### 5.1 Entities

#### User
| Field | Type | Notes |
|---|---|---|
| id | String (UUID) | Primary key |
| email | String | Unique |
| displayName | String | |
| avatarUrl | String? | |
| authProvider | Enum | clerk / workos |

#### Folder
| Field | Type | Notes |
|---|---|---|
| id | String | |
| ownerId | String | FK -> User |
| parentFolderId | String? | Nullable — root folder has no parent |
| name | String | |
| createdAt | Instant | |

#### Notebook
| Field | Type | Notes |
|---|---|---|
| id | String | |
| ownerId | String | FK -> User |
| folderId | String? | FK -> Folder |
| title | String | |
| coverColor | String | Hex color |
| createdAt | Instant | |
| updatedAt | Instant | |
| snapshotUrl | String? | R2 URL of latest Yjs snapshot |
| snapshotVersion | Long | Used to detect stale snapshots |

#### Page
| Field | Type | Notes |
|---|---|---|
| id | String | |
| notebookId | String | FK -> Notebook |
| order | Int | Sort order within notebook |
| templateType | Enum | blank / lined / dotted / grid |
| templateDensity | Float | Lines/dots per unit |
| templateLineWidth | Float | |
| backgroundColorHex | String | Per-page override |
| widthPx | Int | Default: A4 width at 96dpi (794px) |
| heightPx | Int | Default: A4 height at 96dpi (1123px) |

#### Stroke
| Field | Type | Notes |
|---|---|---|
| id | String | |
| pageId | String | FK -> Page |
| yjsOpId | String | Yjs operation ID for CRDT merge |
| toolType | Enum | pen / highlighter |
| colorHex | String | |
| thickness | Float | |
| pressure | Float[] | Per-point pressure values |
| tilt | Float[] | Per-point tilt values |
| points | Float[] | Interleaved x, y coordinates |
| createdAt | Instant | |

Strokes are the source of truth inside the Yjs document. The Room table is a materialized projection for local queries; the Yjs doc is authoritative.

#### TextBlock
| Field | Type | Notes |
|---|---|---|
| id | String | |
| pageId | String | FK -> Page |
| content | String | Plain text or LaTeX source |
| positionX | Float | |
| positionY | Float | |
| width | Float | |
| isLatex | Boolean | Renders as inline math if true |
| latexSource | String? | Raw LaTeX string |

#### Attachment
| Field | Type | Notes |
|---|---|---|
| id | String | |
| pageId | String | FK -> Page |
| type | Enum | image / audio / pdf_page |
| r2Url | String | |
| positionX | Float | |
| positionY | Float | |
| width | Float | |
| height | Float | |

#### AudioRecording
| Field | Type | Notes |
|---|---|---|
| id | String | |
| notebookId | String | FK -> Notebook |
| r2Url | String | |
| durationSeconds | Int | |
| createdAt | Instant | |

#### Share
| Field | Type | Notes |
|---|---|---|
| id | String | |
| notebookId | String | FK -> Notebook |
| accessType | Enum | view / edit |
| linkToken | String? | Null if invite-only |
| invitedEmail | String? | Null if public link |
| createdAt | Instant | |

#### Collaborator
| Field | Type | Notes |
|---|---|---|
| id | String | |
| notebookId | String | FK -> Notebook |
| userId | String | FK -> User |
| role | Enum | viewer / editor |

---

## 6. Feature Set

### 6.1 Note Modes

| Mode | Description |
|---|---|
| Paged | Stacked discrete pages; used for imported PDFs and standard notebooks |
| Infinite vertical canvas | Same horizontal width as paged mode, scrolls vertically without page breaks |
| Two-page layout | Paged mode only; shows two pages side-by-side (tablet landscape) |

### 6.2 Page Templates

| Template | Configurable Properties |
|---|---|
| Blank | Background color only |
| Lined | Line spacing density, line width, color |
| Dotted | Dot spacing density, dot size, color |
| Grid | Cell size density, line width, color |

All template properties are stored per-page and can be changed at any time without affecting existing strokes.

### 6.3 Tools

#### Pen
- Configurable thickness
- Pressure sensitivity (S Pen hardware data)
- Tilt sensitivity
- Stabilization / stroke smoothing (configurable level)
- Color picker with presets
- Optional shape recognition mode (via IInk, toggleable)

#### Highlighter
- Configurable thickness
- Configurable opacity
- Color picker with presets
- End cap style: square or round
- Straight-line mode: always straight / never straight / hold-to-snap

#### Lasso
- Select one or more strokes
- Move selection
- Resize selection
- Convert selection to typed text (in-place replacement, powered by IInk HWR)
- Convert selection to LaTeX equation: IInk recognizes the equation, renders it inline as a TextBlock (isLatex = true); lasso the block again to copy the raw LaTeX string to the clipboard

#### Eraser
- Toggle between stroke eraser (deletes full strokes) and area eraser (deletes any stroke segment crossing the eraser area)
- Configurable eraser size

#### Gestures
- Scribble-to-erase: draw a scribble pattern over strokes to erase them (toggleable, powered by IInk gesture recognition)
- Hold-to-draw-shape: hold pen still at end of stroke to snap to nearest recognized shape (toggleable, powered by IInk shape recognition)
- Two-finger tap / double-tap: undo

### 6.4 Toolbar & Customization

- Customizable tool slots: user can assign any tool to any slot
- Customizable color preset palette: configurable count and color values
- S Pen button default: hold to activate eraser (customizable in a later phase)

### 6.5 Per-Note Settings (in-editor)

| Setting | Options |
|---|---|
| Keep screen awake | Toggle |
| Hide status / navigation bars | Toggle |
| Scroll bar position | Left / Right |
| Background color | Color picker (independent of system theme) |
| Default pen color | Auto-contrast enforced: dark background prevents black pen; white background prevents white pen |
| Note theme | Dark / Light (independent of system dark mode) |

### 6.6 Zoom

- Range: 50% to 1000%
- Pinch-to-zoom gesture (two fingers — stylus input only for drawing)
- Zoom level persisted per note

### 6.7 Content Types

| Content Type | Source |
|---|---|
| Handwritten strokes | Primary input via S Pen |
| Typed text blocks | Inserted manually or converted from handwriting via lasso |
| LaTeX blocks | Converted from handwriting via lasso + IInk equation recognition |
| Images | Camera or gallery (Android) |
| PDF pages | Imported as note backgrounds |
| Audio recordings | Recorded in-app, tied to notebook, playback only |

### 6.8 Undo / Redo

- Unlimited undo/redo within a session
- Operation log is persisted to Room — undo/redo state survives app restarts
- Undo/redo operations are themselves CRDT operations, keeping collaborators consistent

---

## 7. Rendering Pipeline

The ink rendering pipeline is the most performance-critical component of the app. The goal is sub-20ms perceived latency from stylus contact to visible ink.

### 7.1 Architecture

```
S Pen Hardware
     |
     |  MotionEvent (batched, high-frequency)
     v
InkCanvasTouch (Compose touch handler)
     |
     |  Raw point stream (x, y, pressure, tilt, timestamp)
     v
Motion Predictor
     |
     |  Predicted points injected ahead of hardware
     v
Stroke Smoother / Stabilizer
     |
     |  Smoothed point stream
     v
Vulkan NDK Layer
     |  - Renders in-progress stroke to offscreen buffer
     |  - Composites committed strokes from stroke texture atlas
     |  - Presents frame via SurfaceView / ANativeWindow
     v
Display (committed at VSync)
```

### 7.2 Key Design Decisions

- **Front-rendering**: In-progress strokes are drawn immediately to an overlay layer. The committed stroke atlas is updated once the stroke is finalized (pen-up event).
- **Stroke texture atlas**: All committed strokes on a page are composited into a single texture. Partial re-renders update only the affected tile region.
- **Motion prediction**: Android provides `MotionEvent.getHistoricalX/Y` for batched events. Additional prediction (1-2 frames ahead) is applied to reduce perceived lag on 60Hz and 120Hz panels.
- **Pressure / tilt mapping**: Raw S Pen pressure (0.0-1.0) and tilt (azimuth + altitude) are mapped to stroke width and opacity curves configurable per brush preset.
- **Causal stroke smoothing**: A real-time smoothing pass is applied during drawing; a separate higher-quality smoothing pass is applied on pen-up before committing the stroke to the atlas.

### 7.3 Vulkan Specifics

- Minimum API level: Android 10 (API 29) — confirmed floor; guarantees Vulkan 1.1 on Samsung devices
- Shader pipeline: custom vertex + fragment shaders for variable-width Bezier rendering
- Memory: dedicated device-local memory for stroke atlas; host-visible staging buffers for point upload

---

## 8. Sync Architecture

### 8.1 Overview

Onyx uses a snapshot + delta model on top of Y-Octo CRDTs (binary-compatible with Yjs), with Convex handling live delta streaming and Cloudflare R2 holding full snapshots. Android uses Y-Octo (Rust/JNI); the web viewer uses Yjs (JavaScript) — both read/write the same binary CRDT format.

```
On first load of a notebook:
  1. Fetch latest snapshot from R2 (snapshotUrl in Notebook record)
  2. Apply all Convex-stored CRDT deltas since snapshotVersion
  3. Initialize local Yjs doc from merged state
  4. Subscribe to live Convex delta stream

During editing:
  1. All mutations produce Yjs operations locally (applied immediately)
  2. Yjs delta (encoded update) pushed to Convex
  3. WorkManager queues R2 snapshot write on a time/op-count threshold

On reconnect after offline:
  1. Flush local Room op queue to Convex in order
  2. Receive any remote deltas since last sync
  3. Yjs handles merge automatically (CRDT guarantee)
```

### 8.2 Convex Schema Responsibilities

- Store CRDT delta payloads (binary blobs, small — typically <10 KB each)
- Store Notebook metadata including snapshotUrl and snapshotVersion
- Store Share and Collaborator records
- Serve real-time subscriptions for live collaboration cursors

### 8.3 Cloudflare R2 Responsibilities

- Full Yjs document snapshots (periodic, triggered by WorkManager)
- Imported PDFs
- Inserted images
- Audio recordings

### 8.4 Offline Behavior

- The app reads exclusively from Room and the local Yjs doc while offline
- All write operations are appended to an offline op queue in Room
- On network restoration, WorkManager drains the queue in order and applies deltas to Convex
- No data is lost if the app is killed while offline — the queue persists across restarts

---

## 9. Recognition Pipeline (IInk)

IInk SDK 4.3 is used strictly as a recognition engine. It does not render anything; all pixel output comes from the Vulkan pipeline.

### 9.1 Recognition Modes

| Mode | Trigger | Output |
|---|---|---|
| HWR (handwriting recognition) | Background indexing after pen-up | Text tokens stored in search index |
| Shape recognition | Pen-up when shape recognition is enabled | Snaps stroke to nearest geometric shape |
| Scribble-to-erase gesture | Real-time, when gesture mode enabled | Identifies scribble pattern; triggers stroke deletion |
| Hold-to-draw-shape gesture | Hold duration threshold at stroke end | Triggers shape snap via shape recognition |
| LaTeX equation recognition | Lasso selection -> convert to LaTeX | Returns LaTeX string; app renders it as inline math |
| Handwriting-to-text | Lasso selection -> convert to text | Returns plain text string; replaces selection with TextBlock |

### 9.2 Stroke Data Flow to IInk

Strokes are delivered to IInk as `InkModel` input after pen-up. IInk operates on a copy of the stroke data; the rendering pipeline never waits on IInk results.

### 9.3 HWR Search Index

- After each stroke is committed and recognized, IInk text tokens are stored in a local Room FTS (full-text search) table linked to the pageId
- Search queries run against the FTS table locally; no network required for search on locally stored notes
- On web, HWR search uses the MyScript cloud API (online only)

---

## 10. PDF Support

### 10.1 Import

- PDFs are imported into paged view mode
- Each PDF page becomes one Page entity with the PDF page rendered as the background
- PdfiumAndroidKt renders PDF pages to bitmaps, cached per zoom level
- The original PDF binary is uploaded to R2; the Notebook record holds the R2 URL

### 10.2 Text Extraction

- Apache PDFBox Android extracts text from PDF pages at import time
- Extracted text is indexed in Room FTS for search
- Text layer is used for search only — no in-app PDF text selection UI in this iteration

### 10.3 Export

- Flatten strokes onto PDF pages: render each page's strokes to a bitmap, compose with original PDF page via PDFBox
- Output PDF is compiled and uploaded to R2; user receives a share link or download

### 10.4 Web

- PDF.js renders the PDF in the web viewer
- Ink overlay is rendered as SVG or Canvas on top of the PDF page

---

## 11. Collaboration

### 11.1 Access Model

| Access Type | Capabilities |
|---|---|
| Owner | Full access, can manage shares |
| Editor | Can draw, edit, delete strokes; cannot manage shares |
| Viewer | Read-only; can scroll and zoom; cannot write |

### 11.2 Sharing Methods

- **Invite by email**: creates a Collaborator record; invited user must have an Onyx account
- **Public link**: creates a Share record with a linkToken; anyone with the link can access at the specified accessType

### 11.3 Real-Time Collaboration

- Each collaborator's Yjs doc receives live deltas via Convex real-time subscription
- Multi-cursor display: each active editor's current pen position is broadcast as a presence event (ephemeral, not stored in Yjs)
- Conflict resolution is automatic via Yjs CRDT semantics — concurrent strokes from different users are merged without loss

### 11.4 Offline -> Reconnect

- While offline, a user with edit access continues to write normally
- On reconnect, local Yjs updates are pushed to Convex; remote deltas accumulated during the offline period are fetched and merged
- The resulting merged state is eventually consistent across all collaborators

---

## 12. Organization & Search

### 12.1 Hierarchy

```
User
 +-- Folder (nestable, arbitrary depth)
      +-- Notebook
           +-- Page
```

- Folders can be nested to arbitrary depth via the `parentFolderId` self-reference
- Notebooks without a folder are in the root level (folderId = null)
- Pages are ordered within notebooks via the `order` field; reordering is a drag-and-drop operation

### 12.2 Search

| Search Type | Index Source | Network Required |
|---|---|---|
| By title | Room (notebook/page title columns) | No |
| By handwriting content | Room FTS (IInk HWR tokens) | No (Android) / Yes (web) |
| By PDF text content | Room FTS (PDFBox extracted text) | No |

Search results surface both notebooks and individual pages. Tapping a result navigates to the specific page and, where applicable, highlights the region containing the matched text.

---

## 13. Web Viewer

The web viewer is a read-only experience in this implementation.

### 13.1 Responsibilities

- Display notebook pages with rendered strokes (SVG or Canvas)
- Render PDF backgrounds via PDF.js
- Display typed text blocks and LaTeX blocks (rendered via KaTeX or MathJax)
- Display inserted images
- Support zoom and pan
- Live updates via Convex real-time subscriptions (viewer sees collaborator edits in real time)

### 13.2 Out of Scope (Web, this iteration)

- Editing or drawing
- Audio playback
- Lasso / tool interactions

### 13.3 URL Structure

```
/view/:linkToken          — public share link (view or edit access per Share record)
/note/:notebookId         — authenticated user's own notebook
/note/:notebookId/:pageId — direct page link
```

---

## 14. Current Status & Priority (as of 2026-03-07)

### Where we are

The Android codebase already has a substantial technical foundation: Vulkan ink renderer, custom ink canvas, MyScript integration scaffolding, PDF support, Room database, note/page/folder structure, editor UI, tool state management, and tests across many core areas.

**The main problem is not missing technology — it is that the handwriting/editor experience does not yet feel premium.** Live ink, predicted ink, and committed ink behave like three different visual systems, causing tip lag, ghost prediction artifacts, visible pop on pen-up, and excessive smoothing/identity drift. For STEM/engineering students who need high fidelity for small writing, math symbols, and precise annotations, this is the #1 blocker.

### Core architectural principle

**Preserve raw intent first. Beautify second. Recognize asynchronously.**

Raw stylus input drives persistence, selection/hit-testing, MyScript input, undo/redo, and future sync/collab. Display geometry is derived from raw input, not the other way around.

### Three-lane editor model

1. **Hot path lane** — stylus capture, light preview smoothing, invisible prediction suffix, live render submit, final stroke commit.
2. **Semantic async lane** — MyScript recognition, search indexing, text/math/shape conversions (never on the hot path).
3. **UI/chrome lane** — toolbar, panels, note settings, page management, PDF controls.

### Immediate priority: Editor Feel Milestone

Before any new features, fix the pen feel:
1. Raw stroke data as canonical (persist raw input as source of truth)
2. Invisible prediction (no ghost stroke — prediction is ephemeral suffix on active stroke)
3. Tip-preserving preview smoothing (newest 1-2 points stay near raw; smooth older tail)
4. Real brush params passed through to renderer
5. Reduced live→commit handoff pop
6. One default "best handwriting" interaction mode

Then: editor architecture cleanup (EditorInteractionSession, PageEditorController, MultiPageViewportController), then lightweight semantic features, then sync/collaboration.

Detailed refactor plan: see `.sisyphus/plans/editor-feel-refactor.md`.
Detailed UX specification: see Warp plan "Onyx UX Specification".

---

## 15. Phased Roadmap

### Phase 1 — Core Android App (MVP) — COMPLETE

**Goal**: Fully functional local-only note-taking app.

- [x] Project setup: Kotlin + Compose + Room + Vulkan rendering pipeline skeleton
- [x] Paged note view with blank / lined / dotted / grid templates
- [x] Pen tool: pressure, tilt (S Pen), configurable thickness and color
- [x] Highlighter tool (basic: thickness, opacity, color)
- [x] Eraser: stroke eraser and area eraser, configurable size
- [x] Lasso tool: select strokes, move selection
- [x] Undo / redo: in-session and persistent across restarts
- [x] Local storage only (no sync): Room for all state
- [x] Folder / notebook / page organization UI
- [x] Basic toolbar with color presets
- [x] Two-finger tap / double-tap to undo

**Exit criteria**: A user can create notebooks in folders, write with the S Pen using pressure sensitivity, erase, undo, and their work persists across app restarts.

---

### Phase 2 — PDF & Content — PARTIALLY COMPLETE

**Goal**: Rich content beyond freehand strokes.

- [x] PDF import (PdfiumAndroidKt); pages become note backgrounds in paged view
- [x] PDF text extraction (PDFBox) and FTS indexing for search
- [x] Image insertion from camera and gallery
- [ ] Insert pages from other notebooks or PDFs
- [ ] PDF export: flatten strokes, compile to PDF
- [ ] Audio recording tied to notebook; playback UI

**Exit criteria**: User can import a PDF lecture slide deck, annotate it, export the annotated PDF, and insert a photo of a whiteboard.

---

### Phase 3 — Recognition (IInk Integration) — SCAFFOLD COMPLETE

**Goal**: Smart input features powered by IInk SDK 4.3.

- [x] IInk SDK integration (recognition-only mode; no rendering)
- [x] Shape recognition (toggleable under pen settings)
- [x] Scribble-to-erase gesture (toggleable)
- [x] Hold-to-draw-shape gesture (toggleable)
- [ ] HWR indexing pipeline: strokes -> IInk -> FTS tokens (scaffold exists, runtime incomplete)
- [ ] Lasso -> convert handwriting to typed text (in-place) (scaffold exists, needs editor-feel milestone first)
- [ ] Lasso -> convert handwriting to LaTeX (inline render + clipboard copy) (scaffold exists, needs editor-feel milestone first)

**Exit criteria**: User can write a math equation, lasso it, and get a rendered LaTeX block; searching handwritten content returns relevant pages.

---

### Phase 4 — Sync & Backend

**Goal**: Notes live in the cloud; multiple devices; offline-first sync.

- [ ] Convex backend schema and functions
- [ ] Cloudflare R2 bucket setup and upload/download helpers
- [ ] Clerk (or WorkOS) auth + Google Sign-In
- [ ] Y-Octo CRDT integration with Android (Rust compiled via NDK, JNI bindings to Kotlin)
- [ ] WorkManager sync jobs: delta push, snapshot write, offline queue drain
- [ ] Snapshot + delta load sequence on notebook open

**Exit criteria**: A user can install the app on two devices, write on one, and see the notes appear on the other after coming online.

---

### Phase 5 — Collaboration

**Goal**: Real-time shared editing and public sharing.

- [ ] Real-time multi-cursor editing via Convex + Yjs
- [ ] Share by email (invite, view/edit roles)
- [ ] Share by public link (view/edit modes, revocable)
- [ ] Web viewer: TanStack Start app, read-only
- [ ] Tile-based PDF and ink rendering in web viewer (no pdf.js, no raw PDF download)
- [ ] LaTeX rendering in web viewer (KaTeX or MathJax)
- [ ] Live updates in web viewer via Convex subscriptions

**Exit criteria**: User A shares a notebook with User B via link; User B can view and edit (if granted) in real time from a web browser while User A edits on Android.

---

### Phase 6 — Polish & Advanced Features

**Goal**: Feature-complete experience, polished UI, full customization.

- [ ] Infinite vertical canvas mode
- [ ] Two-page layout (paged mode, landscape)
- [ ] Full toolbar / gesture customization UI
- [ ] Highlighter advanced modes: straight-line always / never / hold-to-snap
- [ ] Per-note settings: screen awake, hide bars, scroll bar side
- [ ] Dark / light note mode independent of system theme
- [ ] Zoom range 50%-1000% with persisted zoom level
- [ ] Search UI: combined title + HWR + PDF content results
- [ ] S Pen button customization (beyond default hold-to-erase)
- [ ] Auto-contrast pen color enforcement (dark/light background)
- [ ] Configurable template density and line width in-editor

**Exit criteria**: All features described in §6 are implemented and tested on target Samsung devices.

---

## 16. Open Questions & Decisions

| # | Question | Status | Notes |
|---|---|---|---|
| 16.1 | CRDT engine on Android | **Resolved** | Y-Octo (Rust/JNI) — native performance, no JS runtime, binary-compatible with Yjs on web |
| 16.2 | Auth provider: Clerk vs. WorkOS | **Resolved** | Clerk chosen for v0. WorkOS considered for future enterprise SSO |
| 16.3 | Lasso resize behavior | **Resolved** | Stroke thickness does not scale with resize — selection is repositioned/resized, thickness stays constant |
| 16.4 | Stroke smoothing algorithm | **Resolved** | Tip-preserving causal smoothing (newest 1-2 points near raw, older tail smoothed). See editor-feel-refactor.md |
| 16.5 | Offline HWR language support | **Resolved** | English only for initial IInk license; multi-language deferred |
| 16.6 | Convex delta retention policy | **Resolved** | Ops below the latest commit’s baseLamport are eligible for cleanup. Retention job in Milestone C deletes old ops. Snapshots are the long-term record |
| 16.7 | Web viewer stroke format | **Resolved** | Tile-based rasters for PDF; SVG or Canvas overlay for ink strokes. No raw PDF download. See milestone-b-web-viewer.md |
| 16.8 | Two-finger gesture conflict | **Resolved** | Two-finger touch is allowed for pan/zoom/undo. Stylus-only policy applies to drawing. MotionEvent filtering confirmed working in InkCanvasTouch.kt |
| 16.9 | Rendering engine | **Resolved** | Vulkan via Android NDK — confirmed over OpenGL ES for lower CPU overhead, explicit pipeline control, and long-term investment |
