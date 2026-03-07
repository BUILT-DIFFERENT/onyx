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
- Offline-first operation with optional real-time collaboration powered by Y-Octo/Yjs CRDT-based sync
- MyScript IInk SDK integration for handwriting recognition, shape recognition, and LaTeX equation recognition — recognition only, rendering is handled by the custom pipeline
- Flexible organization (folders, notebooks, pages) with HWR-indexed search

---

## 2. Target Users & Platform

| Dimension | Detail |
|---|---|
| Primary users | Engineering / STEM students |
| Primary device | Samsung tablets with Samsung S Pen |
| Input model | Stylus-first. Finger drawing is disabled by default; optional single-finger draw mode can be enabled in settings |
| Primary platform | Android (Kotlin + Jetpack Compose) |
| Secondary platform | Web viewer (TanStack Start, read-only) |

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
| Handwriting recognition | MyScript IInk SDK 4.3 (offline, recognition only, no rendering) |
| CRDT engine | Y-Octo (Rust, compiled via Android NDK/JNI) — binary-compatible with Yjs on web. Yjs doc stored as raw binary file on device (magic header `ONYX` + uint32 version + encoded state array) |

### 3.2 Backend

| Service | Role |
|---|---|
| Convex | Metadata, user data, folder hierarchy, sharing settings, CRDT update deltas (<10 KB each), search index, presence |
| Cloudflare R2 | Large blobs: PDFs (original, never auto-flattened), images, audio recordings, Yjs doc snapshots, exported PDFs |

Convex stores asset references (R2 key + size + hash); it never stores raw binary data directly. PDFs are never auto-flattened to R2 — the original PDF plus separate stroke data in the Yjs doc is the canonical storage. Flattened PDFs are only generated on explicit user export.

### 3.3 Auth

- Clerk — chosen for v0. WorkOS considered for future enterprise SSO.
- Google Sign-In as the primary OAuth provider

### 3.4 Web

| Layer | Technology |
|---|---|
| Framework | TanStack Start |
| PDF rendering | PDF.js renders original PDF from R2 presigned URL |
| Ink rendering | Canvas overlay on top of PDF, strokes tessellated from raw point data |
| CRDT engine | Yjs (JavaScript) — binary-compatible with Y-Octo on Android |
| Real-time | Convex real-time subscriptions (sync.watchHead) |
| HWR recognition | Not integrated in v1 (web is view-only, no drawing) |

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

1. **Offline-first**: The app is fully functional without a network connection. All writes go to the local Yjs doc and Room first; sync to Convex is opportunistic.
2. **Rendering isolation**: The Vulkan ink canvas is a separate NDK layer. Compose UI renders chrome (toolbar, panels) around it without touching the ink surface.
3. **Recognition decoupled from rendering**: IInk SDK receives stroke data for recognition purposes only. It never drives pixel output.
4. **Blob references, not blobs in the DB**: Convex holds only metadata and CRDT deltas. All binary assets live in R2; Convex records the R2 key, size, and hash.
5. **CRDT as source of truth**: Y-Octo/Yjs is the authoritative representation of all page content (strokes, objects). Room is a local cache and metadata store. Conflict resolution is automatic via CRDT merge semantics.
6. **Two-level CRDT documents**: A notebook-level Yjs doc owns page ordering and metadata. Each page has its own Yjs doc for content (strokes, objects). This avoids loading the full notebook CRDT when opening a single page.

---

## 5. Data Model

### 5.0 Coordinate System

- **Unit**: pixels (px) everywhere — strokes, objects, page dimensions, PDF overlay, positions
- **Page size (paged mode)**: 794 × 1123 px (A4 at 96 DPI) — default; each page stores its own dimensions
- **Infinite canvas**: 794 px wide (same as paged), infinite vertical scroll; internal tile size 2048 px
- **Stroke coordinates**: absolute pixel positions within the page coordinate space (origin = top-left of page)
- **Zoom**: client-side viewport transform only. Stroke/object data always stored in page-space pixels. The renderer scales the viewport, not the stored data.
- **PDF overlay**: PDF pages are rasterized to match page pixel dimensions at render time. Strokes drawn on PDF-backed pages share the same px coordinate space. On export (user-initiated), strokes are composited onto the PDF at the PDF’s native resolution.

### 5.1 Entities

#### User
| Field | Type | Notes |
|---|---|---|
| id | String (UUID) | Primary key (Clerk user ID) |
| email | String | Unique, lowercase |
| displayName | String | |
| avatarUrl | String? | |

#### Folder
| Field | Type | Notes |
|---|---|---|
| id | String | |
| ownerId | String | FK -> User |
| parentFolderId | String? | Nullable — root folder has no parent. Arbitrary nesting. |
| name | String | |
| createdAt | Instant | |
| updatedAt | Instant | |

Folders are server-synced via Convex `folders` table. Consistent across all devices.

#### Notebook
| Field | Type | Notes |
|---|---|---|
| id | String | |
| ownerId | String | FK -> User |
| folderId | String? | FK -> Folder. Null = root level |
| title | String | |
| coverColor | String | Hex color |
| isFavorite | Boolean | Favorites float to top of current folder view |
| notebookMode | Enum | paged / infinite_canvas |
| createdAt | Instant | |
| updatedAt | Instant | |
| deletedAt | Instant? | Null = not deleted; soft delete to Trash |
| snapshotUrl | String? | R2 URL of latest notebook-level Yjs snapshot |
| snapshotStateVector | ByteArray? | Yjs state vector of the snapshot (used to fetch only deltas since snapshot) |

#### Page
| Field | Type | Notes |
|---|---|---|
| id | String | |
| notebookId | String | FK -> Notebook |
| order | Int | Sort order within notebook |
| templateType | Enum | blank / lined / dotted / grid |
| templateDensity | Float | Lines/dots per px |
| templateLineWidth | Float | px |
| backgroundColorHex | String | Per-page background color |
| widthPx | Int | Default: 794 (A4 at 96 DPI) |
| heightPx | Int | Default: 1123 (A4 at 96 DPI). 0 = infinite canvas page |
| pdfAssetId | String? | R2 asset ID if this page has a PDF background |
| pdfPageNo | Int? | 0-based index into the PDF |
| pageSnapshotUrl | String? | R2 URL of latest per-page Yjs doc snapshot |
| pageSnapshotStateVector | ByteArray? | Yjs state vector of the per-page snapshot |

#### Stroke (inside per-page Yjs doc; Room is a local query cache)
| Field | Type | Notes |
|---|---|---|
| id | String | UUID, assigned by client |
| pageId | String | FK -> Page |
| toolType | Enum | pen / highlighter |
| penType | Enum? | ballpoint / fountain / pencil (only for toolType=pen) |
| colorHex | String | |
| thickness | Float | Base width in px |
| opacity | Float | 0.0–1.0 (mainly for highlighter) |
| pressureSensitivity | Float | 0.0–1.0 |
| tiltSensitivity | Float | 0.0–1.0 |
| stabilization | Float | 0.0–1.0 smoothing level |
| points | RawPoint[] | Per-point: x, y (px), pressure, tiltX, tiltY (radians), timestamp (unix ms) |
| isShape | Boolean | True if IInk shape-recognized and snapped |
| shapeData | ShapeData? | Set when isShape=true |
| createdAt | Instant | |

Strokes live inside the per-page Yjs doc (`strokes: Y.Map<id, StrokeData>`). The Room `strokes` table is a materialized read cache for local queries (search, hit-testing). The Yjs doc is authoritative.

#### PageObject (inside per-page Yjs doc; Room is a local query cache)

All page objects share a base schema:

| Field | Type | Notes |
|---|---|---|
| id | String | UUID |
| pageId | String | FK -> Page |
| type | Enum | textBlock / image / stickyNote / table / audioAttachment / shape |
| x | Float | px, page-space |
| y | Float | px, page-space |
| width | Float | px |
| height | Float | px |
| rotation | Float | degrees |
| zIndex | Int | |
| locked | Boolean | Locked objects not affected by eraser/lasso |
| createdAt | Instant | |
| updatedAt | Instant | |

**TextBlock** (type = textBlock):
| Field | Type | Notes |
|---|---|---|
| content | String | Plain text |
| isLatex | Boolean | Renders as KaTeX if true |
| latexSource | String? | Raw LaTeX string |
| fontSize | Float | px |
| color | String | Hex |
| align | Enum | start / center / end |
| bold | Boolean | |
| italic | Boolean | |
| underline | Boolean | |

**Image** (type = image):
| Field | Type | Notes |
|---|---|---|
| assetId | String | References Convex `assets` table; binary in R2 |
| mimeType | String | |
| originalWidth | Int | px |
| originalHeight | Int | px |

**StickyNote** (type = stickyNote):
| Field | Type | Notes |
|---|---|---|
| text | String | |
| backgroundColor | String | Hex (presets: yellow, pink, blue, green, purple) |
| style | Enum | square / rounded |
| fontSize | Float | px |

**Table** (type = table):
| Field | Type | Notes |
|---|---|---|
| rows | Int | |
| cols | Int | |
| cells | CellData[] | Array of {row, col, content: String} |
| borderColor | String | Hex |
| headerRow | Boolean | First row bold with distinct background |
| colWidths | Float[] | px, one per column |
| rowHeights | Float[] | px, one per row |

Tapping a table cell pops up the software keyboard for text input.

**AudioAttachment** (type = audioAttachment):
| Field | Type | Notes |
|---|---|---|
| assetId | String | References Convex `assets` table; binary in R2 |
| durationMs | Int | |
| mimeType | String | |

No ink-to-audio time sync in v1 (dumb attachment — record and playback only).

**Shape** (type = shape, created by IInk shape recognition):
| Field | Type | Notes |
|---|---|---|
| shapeType | Enum | line / rectangle / ellipse / triangle / circle / arrow |
| strokeColor | String | Hex |
| strokeWidth | Float | px |
| fillColor | String? | Hex, optional |
| controlPoints | Float[] | Shape-specific geometry points in px |

#### Share
| Field | Type | Notes |
|---|---|---|
| id | String | |
| notebookId | String | FK -> Notebook |
| granteeUserId | String | FK -> User |
| role | Enum | viewer / editor |
| grantedByUserId | String | FK -> User |
| createdAt | Instant | |
| revokedAt | Instant? | |

#### PublicLink
| Field | Type | Notes |
|---|---|---|
| id | String | |
| notebookId | String | FK -> Notebook |
| linkToken | String | High-entropy URL token |
| createdByUserId | String | FK -> User |
| createdAt | Instant | |
| revokedAt | Instant? | |

---

## 6. Feature Set

### 6.1 Notebook Modes

| Mode | Description |
|---|---|
| Paged | Stacked discrete pages; used for imported PDFs and standard notebooks |
| Infinite vertical canvas | 794 px wide (same as paged), scrolls vertically without page breaks. Internal tile size: 2048 px |
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
Three named instruments with distinct feel:
- **Ballpoint**: low latency, mild pressure→width response, strong wobble suppression, consistent line weight
- **Fountain**: strong width/orientation dynamics, pressure produces thick-thin variation, slight pooling at slow points
- **Pencil**: minimal smoothing, raw textured feel, lighter opacity, no taper

Shared pen properties:
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
- Select one or more strokes and objects
- Move selection
- Resize selection (stroke thickness does not scale with resize)
- Convert selection to typed text (in-place replacement via IInk HWR; shows toast "Recognition failed" on failure; user can Undo)
- Convert selection to LaTeX: IInk math recognizer returns LaTeX string, rendered inline as TextBlock (isLatex=true); shows toast "Recognition failed" on failure; user can Undo

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

### 6.5 Per-Notebook Settings (in-editor)

| Setting | Options |
|---|---|
| Keep screen awake | Toggle |
| Hide status / navigation bars | Toggle |
| Scroll bar position | Left / Right |
| Background color | Color picker (independent of system theme) |
| Default pen color | Auto-contrast suggested: dark background suggests switching to light pen color (one-time suggestion, not enforcement) |
| Notebook theme | Dark / Light (independent of system dark mode) |

### 6.6 Zoom

- Range: 50% to 1000%
- Pinch-to-zoom gesture (two fingers — stylus input only for drawing)
- Zoom level persisted per notebook

### 6.7 Content Types

| Content Type | Source |
|---|---|
| Handwritten strokes | Primary input via S Pen |
| Typed text blocks | Inserted manually or converted from handwriting via lasso |
| LaTeX blocks | Converted from handwriting via lasso + IInk equation recognition |
| Images | Camera or gallery (Android) |
| PDF pages | Imported as page backgrounds (original PDF stored in R2, never auto-flattened) |
| Audio recordings | Recorded in-app, tied to notebook, playback only (binary in R2) |
| Sticky notes | Colored floating notes, insertable from toolbar |
| Tables | Grid tables with plain-text cells, keyboard input, insertable from toolbar |
| Shapes | Created by IInk shape recognition from drawn strokes |

### 6.8 Undo / Redo

- 100 undo + 100 redo operations per page (FIFO eviction of oldest entry when limit exceeded)
- Undo/redo history persisted to local storage — survives app restarts
- Undo/redo writes new Yjs updates to the CRDT doc; collaborators see the reverted state

### 6.9 Adding Pages (Circle Gesture)

When the user scrolls past the last page and continues swiping up:
1. A circular progress indicator (64dp diameter, accent color stroke, thin) begins drawing clockwise from 12 o’clock
2. Centered horizontally at the bottom of the viewport
3. 120dp overscroll = complete circle (proportional fill: 60dp = half-circle)
4. Release before complete: circle animates back to empty (150ms ease-out), no page added
5. Release after complete: haptic feedback (light tap), circle fills solid (100ms), new blank page appends with 200ms slide-in
6. The ’+’ icon appears in the circle center at 50% fill, scales up to full size at 100%
7. Fallback: pages also added via ’+’ button in Zone 1

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

Onyx uses a snapshot + delta model built on Y-Octo/Yjs CRDTs. Android uses Y-Octo (Rust/JNI); the web viewer uses Yjs (JS) — both produce and consume the same binary CRDT update format.

**Two-level CRDT document structure:**
- **Notebook-level Yjs doc**: owns `pages` array (page ordering + page metadata) and notebook `metadata` map
- **Per-page Yjs doc**: owns `strokes: Y.Map`, `objects: Y.Map`, `deletedStrokes: Y.Map`, `deletedObjects: Y.Map`

This avoids loading all page content just to open a notebook or browse page thumbnails.

### 8.2 Sync Flow

```
On notebook open (per level):
  1. Load local Yjs binary file (ONYX magic + uint32 version + encoded state)
  2. Fetch latest snapshot from R2 if local file is absent or older than snapshot
  3. Call sync.pullUpdates(stateVector) to fetch Convex deltas since snapshot
  4. Apply updates to local Yjs doc; persist updated binary file
  5. Subscribe to sync.watchHead for live deltas

During editing:
  1. All mutations applied locally to Yjs doc immediately (offline-first)
  2. Yjs update binary delta queued locally (Room OfflineQueueEntity)
  3. If online: WorkManager flushes queue via sync.pushUpdates immediately
  4. Snapshot cadence: WorkManager schedules R2 snapshot write debounced from last edit
     - Shared/public notebooks: 5s after last edit
     - Private notebooks: 20s after last edit

On reconnect after offline:
  1. WorkManager drains OfflineQueueEntity in insertion order via sync.pushUpdates
  2. Pull remote updates accumulated during offline period via sync.pullUpdates
  3. Yjs merges automatically (CRDT guarantee — no conflict resolution logic)
```

### 8.3 Convex Responsibilities (`crdtUpdates` table)

- Store Yjs update binary payloads (small, typically <10 KB each)
- Index by `(notebookId, pageId, createdAt)` for efficient state-vector-based pulls
- Serve reactive subscriptions (sync.watchHead) for live delta notification
- Store all metadata: notebooks, pages, folders, shares, publicLinks, assets, presence, search

### 8.4 Cloudflare R2 Responsibilities

- Full Yjs doc snapshots (notebook-level + per-page)
- Original PDFs (never auto-flattened — flattening only on user-initiated export)
- Images, audio recordings
- Exported (flattened) PDFs
- Notebook thumbnail images

### 8.5 Android Local Storage

- **Room**: notebook/page/folder/share metadata cache, editor settings, search FTS (IInk HWR tokens + PDF text), OfflineQueueEntity for pending sync
- **Yjs binary files** (app internal storage): one file per Yjs doc (notebook-level + per-page). Format: 4-byte magic `ONYX`, 4-byte uint32 version big-endian, then raw Yjs encoded state array. Versioned to detect incompatible format changes.

### 8.6 Offline Behavior

- Full read/write access while offline using local Yjs files + Room
- All writes produce Yjs updates stored in OfflineQueueEntity
- On network restore, WorkManager drains queue; Convex and remote clients converge via CRDT merge
- No data loss if the app is killed offline — queue is persisted

---

## 9. Recognition Pipeline (IInk)

IInk SDK 4.3 is used strictly as a recognition engine. It does not render anything; all pixel output comes from the Vulkan pipeline.

### 9.1 Recognition Modes

| Mode | Trigger | Output | Failure behavior |
|---|---|---|---|
| HWR (background indexing) | Debounced 500ms after pen-up | Text tokens stored in Room FTS + Convex searchTexts | Silent skip; retry on next edit |
| Shape recognition | Pen-up when shape mode enabled | Replaces stroke with IInk geometric primitive (SDK native confidence, SDK defaults) | Stroke preserved unchanged if not recognized |
| Scribble-to-erase gesture | Real-time, gesture mode enabled | Identifies scribble; triggers stroke deletion | Silent skip |
| Hold-to-draw-shape | Hold threshold at stroke end | Shape snap via IInk | Stroke preserved unchanged |
| LaTeX equation recognition | Lasso → convert to LaTeX | TextBlock (isLatex=true, latexSource populated) | Toast: "Recognition failed — try again with clearer writing"; handwriting preserved; user can Undo |
| Handwriting-to-text | Lasso → convert to text | TextBlock (plain text) | Toast: "Recognition failed"; handwriting preserved; user can Undo |

### 9.2 Stroke Data Flow to IInk

Strokes are delivered to IInk as `InkModel` input after pen-up. IInk operates on a copy of the stroke data; the rendering pipeline never waits on IInk results.

### 9.3 HWR Search Index

- After each stroke is committed, a debounced coroutine (500ms after last pen-up) sends strokes to IInk HWR
- Recognized text tokens are stored in Room FTS5 table (unicode61 tokenizer) linked to pageId
- Tokens are also pushed to Convex `searchTexts` table via `search.upsertPageText` for global cross-device search
- Local Room FTS queries: no network required for search on locally cached notebooks
- Web search uses Convex `search.global` (Convex built-in full-text search index)
- MyScript cloud API for web is NOT integrated in v1 (web is view-only, no drawing)

---

## 10. PDF Support

### 10.1 Import

- PDFs are imported into paged view mode only
- Each PDF page becomes one Page entity; the PDF page is rendered as the background at display time
- PdfiumAndroid renders PDF pages to bitmaps cached per zoom level
- The original PDF binary is uploaded to R2 via presigned PUT; the `assets` Convex record holds the R2 key
- **PDFs are never auto-flattened.** Original PDF + separate Yjs stroke data = canonical storage.

### 10.2 Text Extraction

- Apache PDFBox Android extracts text from PDF pages at import time (background, async)
- Extracted text indexed in Room FTS5 for local search
- Also pushed to Convex `searchTexts` via `search.upsertPageText` for cross-device search
- No in-app PDF text selection UI in v1

### 10.3 Export (User-Initiated)

- User explicitly requests PDF export
- Android renders each page's strokes via Vulkan to a bitmap, composites with original PDF page via PDFBox
- Exported (flattened) PDF uploaded to R2; registered via `exports.register`; user receives presigned download URL
- Public link viewers can also download the exported PDF via `publicAssets.getDownloadUrl`

### 10.4 Web

- PDF.js fetches and renders the original PDF from R2 (via `assets.getDownloadUrl` presigned URL)
- Ink strokes rendered as Canvas overlay on top of the PDF page
- Stroke geometry tessellated client-side from raw point data in the Yjs doc

---

## 11. Collaboration

### 11.1 Access Model

| Access Type | Capabilities |
|---|---|
| Owner | Full access, can manage shares |
| Editor | Can draw, edit, delete strokes; can manage shares/public links |
| Viewer | Read-only; can scroll and zoom; cannot write |

### 11.2 Sharing Methods

- **Invite by email**: creates a Share record (granteeUserId, role, grantedByUserId); invited user must have an Onyx account
- **Public link**: creates a PublicLink record with a high-entropy linkToken; anyone with the link gets view access per PublicLink record

### 11.3 Real-Time Collaboration

- Max 3 concurrent editors per notebook
- Each collaborator subscribes to `sync.watchHead` via Convex reactive query; new updates fetched via `sync.pullUpdates`
- **Presence (cursors)**: Each active editor pushes cursor position + active tool to Convex `presence` table every 500ms while drawing; stops after 5s idle. Viewers subscribe to `presence.watch` for the notebook to display collaborator cursors.
- Conflict resolution is automatic via Yjs CRDT — concurrent strokes from different users merge without data loss

### 11.4 Offline → Reconnect

- While offline, an editor continues writing normally against local Yjs doc
- On reconnect, WorkManager drains the OfflineQueueEntity via `sync.pushUpdates`
- Remote updates accumulated during the offline period fetched via `sync.pullUpdates` and merged by Yjs automatically

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
| By handwriting content | Room FTS5 (IInk HWR tokens) | No (Android) / Yes (web via Convex) |
| By PDF text content | Room FTS5 (PDFBox extracted text) | No (Android) / Yes (web via Convex) |

Search results surface both notebooks and individual pages. Tapping a result navigates to the specific page and, where applicable, highlights the region containing the matched text.

---

## 13. Web Viewer

The web viewer is read-only in v1.

### 13.1 Rendering Architecture

The web viewer uses a **full CRDT replay engine** (Yjs JS library) to reconstruct page state:
1. Fetch notebook-level Yjs snapshot from R2 + deltas from Convex (`sync.pullUpdates`)
2. Reconstruct page list and metadata from notebook Yjs doc
3. For each visible page, fetch per-page Yjs snapshot + deltas
4. Render strokes: tessellate raw point arrays into variable-width Bezier paths on an HTML Canvas
5. Render PDF background: PDF.js fetches original PDF from R2 presigned URL; rendered to canvas layer below ink
6. Subscribe to `sync.watchHead` for live updates; pull new deltas and re-render affected regions

### 13.2 Responsibilities

- Render notebook pages with ink strokes (Canvas, tessellated from raw point data)
- Render PDF backgrounds via PDF.js (original PDF from R2)
- Render TextBlocks (KaTeX for LaTeX, plain text otherwise)
- Render Images, StickyNotes, Tables, Shapes
- Support zoom and pan
- Live updates via `sync.watchHead` Convex subscription
- Search results via `search.global`

### 13.3 Out of Scope (Web, v1)

- Editing, drawing, or any input
- Audio playback
- Lasso / tool interactions
- HWR recognition

### 13.4 URL Structure

```
/view/:linkToken                    — public share link
/notebook/:notebookId               — authenticated user's own notebook
/notebook/:notebookId/:pageId       — direct page link
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

**Goal**: Notebooks live in the cloud; multiple devices; offline-first sync.

- [ ] Convex backend schema and functions (all tables in scope)
- [ ] Cloudflare R2 bucket setup and upload/download helpers
- [ ] Clerk auth + Google Sign-In
- [ ] Y-Octo CRDT integration with Android (Rust compiled via NDK, JNI bindings to Kotlin)
- [ ] WorkManager sync jobs: delta push, snapshot write, offline queue drain
- [ ] Snapshot + delta load sequence on notebook open

**Exit criteria**: A user can install the app on two devices, write on one, and see the notebooks appear on the other after coming online.

---

### Phase 5 — Web Viewer & Collaboration

**Goal**: Read-only web viewer plus real-time collaboration plumbing across clients.

- [ ] Web viewer: TanStack Start app, view-only (Milestone B)
- [ ] PDF.js rendering of original PDFs from R2 + Canvas overlay for ink strokes in web viewer
- [ ] LaTeX rendering in web viewer (KaTeX)
- [ ] Real-time multi-cursor collaboration via Convex + Yjs (Milestone C)
- [ ] Share by email (invite, view/edit roles)
- [ ] Share by public link (view-only mode, revocable)
- [ ] Live updates in web viewer via Convex subscriptions

**Exit criteria**: User A shares a notebook with User B; User A and other granted editors can edit in real time, and User B can view live updates from a web browser while Android editors continue writing.

---

### Phase 6 — Polish & Advanced Features

**Goal**: Feature-complete experience, polished UI, full customization.

- [ ] Infinite vertical canvas mode
- [ ] Two-page layout (paged mode, landscape)
- [ ] Full toolbar / gesture customization UI
- [ ] Highlighter advanced modes: straight-line always / never / hold-to-snap
- [ ] Per-notebook settings: screen awake, hide bars, scroll bar side
- [ ] Dark / light notebook mode independent of system theme
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
| 16.6 | Convex CRDT delta retention policy | **Resolved** | Yjs update deltas in `crdtUpdates` table below the latest snapshot's state vector are eligible for cleanup. Retention job deletes stale deltas. R2 snapshots are the long-term record |
| 16.7 | Web viewer rendering | **Resolved** | PDF.js renders original PDF from R2 presigned URL; Canvas overlay tessellates ink strokes from Yjs doc point data. See milestone-b-web-viewer.md |
| 16.8 | Two-finger gesture conflict | **Resolved** | Two-finger touch is allowed for pan/zoom/undo. Stylus-only policy applies to drawing. MotionEvent filtering confirmed working in InkCanvasTouch.kt |
| 16.9 | Rendering engine | **Resolved** | Vulkan via Android NDK — confirmed over OpenGL ES for lower CPU overhead, explicit pipeline control, and long-term investment |
