# Onyx Architecture Resolution

> **⚠️ HISTORICAL DOCUMENT — DO NOT USE AS REFERENCE**
>
> This document captured architecture decisions made on 2026-03-07. All decisions have been
> applied to the canonical documents (`PLAN.md`, `V0-api.md`, `convex/schema.ts`, milestone plans).
>
> **Do not reference this file for current architecture.** Use:
> - `PLAN.md` — architecture, data model, roadmap
> - `V0-api.md` — API contracts
> - `convex/schema.ts` — Convex schema
> - `.sisyphus/plans/` — implementation plans
>
> Known stale elements in this document (left as historical record):
> - §4 lists `commits`, `exportMetadata`, `templateDefaults` tables — these were superseded.
>   Canonical schema uses `exports`, per-page template fields, and `sync.recordSnapshot`.
> - §5 lists `folders.list`, `pages.createInkPage`, `commits.*` endpoints — canonical endpoints
>   are in `V0-api.md` (e.g., `folders.listMine`, `pages.create`, `sync.recordSnapshot`).

This document locks all ambiguous decisions and specifies exactly what changes to each plan document. Once approved, every plan/spec/schema file will be updated to match.
# 1. Sync Model — Y-Octo / Yjs CRDTs (Final)
## Architecture
* **Android**: Y-Octo (Rust, compiled via NDK/JNI) — binary-compatible with Yjs
* **Web**: Yjs (JavaScript)
* **Convex**: Stores CRDT update deltas (binary blobs, small) per page + metadata
* **R2**: Stores full Yjs document snapshots (periodic), PDFs, images, audio, exported PDFs
## CRDT Document Structure
Two levels of Yjs Docs for performance (avoid loading entire notebook CRDT to view one page):
**Notebook-level Yjs Doc** — owns page ordering + notebook metadata:
* `pages: Y.Array<{pageId, templateType, templateDensity, templateLineWidth, backgroundColorHex, widthPx, heightPx, pdfAssetId?, pdfPageNo?}>`
* `metadata: Y.Map` — notebook title, coverColor, createdAt, updatedAt
**Per-page Yjs Doc** — owns all page content:
* `strokes: Y.Map<strokeId, StrokeData>` — canonical stroke map
* `objects: Y.Map<objectId, ObjectData>` — TextBlocks, Images, StickyNotes, Tables, AudioAttachments, Shapes
* `deletedStrokes: Y.Map<strokeId, {deletedAt, deletedBy}>` — tombstones for undo/history
* `deletedObjects: Y.Map<objectId, {deletedAt, deletedBy}>` — tombstones
## Sync Flow
**On notebook open:**
1. Fetch latest Yjs snapshot from R2 (`snapshotUrl` in Notebook record)
2. Fetch all CRDT update deltas from Convex since the snapshot's state vector
3. Apply updates to local Yjs doc
4. Subscribe to Convex real-time for new deltas
**During editing:**
1. All mutations produce Yjs updates locally (applied immediately to local doc)
2. Yjs update (binary delta) pushed to Convex `crdtUpdates` table
3. WorkManager queues R2 snapshot write on time/op-count threshold (5s shared, 20s private)
**Offline → reconnect:**
1. Flush local queued Yjs updates to Convex in order
2. Receive any remote updates accumulated during offline
3. Yjs merges automatically (CRDT guarantee — no conflict resolution logic needed)
## What This Replaces
All references to the following are removed from every document:
* Lamport clocks / `(lamport, deviceId, opId)` ordering
* `eraseLamport` gating on AreaErase
* `contentLamportMax` on pages
* `baseLamport` on commits
* Deterministic replay-time resolution
* Explicit `StrokeAdd` / `AreaErase` / `StrokeTombstone` op types in the API
Operations are now implicit inside Yjs updates. The API deals with binary CRDT deltas, not individual typed operations.
# 2. Naming — "Notebook" Everywhere
* Entity is **Notebook**, not "Note"
* Hierarchy: `User → Folder → Notebook → Page`
* All API types, Convex tables, milestone plans, UX spec references updated to "Notebook"
* Convex table: `notebooks` (not `notes`)
* V0-api types: `NotebookId`, `NotebookMeta` (not `NoteId`, `NoteMeta`)
* URL structure: `/notebook/:notebookId`, `/notebook/:notebookId/:pageId`, `/view/:linkToken`
# 3. Data Model — Canonical Structure
## 3.1 Coordinate System
* **Unit**: pixels (px) everywhere — strokes, objects, page dimensions, PDF overlay
* **Page size (paged mode)**: 794 × 1123 px (A4 at 96 DPI) — default, can vary per page
* **Infinite canvas**: 794 px wide (same as paged), infinite vertical scroll. Tile size: 2048 px
* **Stroke coordinates**: absolute pixel positions within the page coordinate space (origin = top-left of page)
* **Zoom**: Client-side transform only. Stroke data always stored in page-space pixels. Zoom = viewport transform applied at render time. Strokes remain pixel-perfect at any zoom level because the renderer scales the viewport, not the data.
* **PDF overlay alignment**: PDF pages are rasterized to match page pixel dimensions. Strokes drawn on PDF pages use the same pixel coordinate space as the rasterized PDF. On export (flatten), strokes are composited at the PDF's native resolution.
## 3.2 Stroke Data (inside per-page Yjs Doc)
```warp-runnable-command
StrokeData {
  id: string (UUID)
  toolType: "pen" | "highlighter"
  penType?: "ballpoint" | "fountain" | "pencil"  // only for toolType=pen
  colorHex: string
  thickness: float  // base width in px
  opacity: float  // 0.0-1.0, mainly for highlighter
  pressureSensitivity: float  // 0.0-1.0, maps pressure to width variation
  tiltSensitivity: float  // 0.0-1.0
  stabilization: float  // 0.0-1.0, smoothing level
  points: Array<RawPoint>
  isShape: boolean  // true if shape-recognized and snapped
  shapeData?: { shapeType: "line"|"rectangle"|"ellipse"|"triangle"|"circle"|"arrow", controlPoints: float[] }
  createdAt: long (unix ms)
}
RawPoint {
  x: float  // px, page-space
  y: float  // px, page-space
  pressure: float  // 0.0-1.0, from S Pen
  tiltX: float  // radians
  tiltY: float  // radians
  timestamp: long  // unix ms
}
```
Raw points are canonical. Display geometry (variable-width Bezier curves, smoothed paths) is derived from raw points at render time.
## 3.3 Object Types (inside per-page Yjs Doc)
All objects share a common base:
```warp-runnable-command
ObjectBase {
  id: string (UUID)
  type: "textBlock" | "image" | "stickyNote" | "table" | "audioAttachment" | "shape"
  x: float  // px, page-space
  y: float  // px, page-space
  width: float  // px
  height: float  // px
  rotation: float  // degrees
  zIndex: int
  locked: boolean
  createdAt: long (unix ms)
  updatedAt: long (unix ms)
}
```
**TextBlock** (extends ObjectBase):
```warp-runnable-command
{ content: string, isLatex: boolean, latexSource?: string, fontSize: float, color: string, align: "start"|"center"|"end", bold: boolean, italic: boolean, underline: boolean }
```
**Image** (extends ObjectBase):
```warp-runnable-command
{ assetId: string, mimeType: string, originalWidth: int, originalHeight: int }
```
Actual image binary stored in R2. `assetId` references the `assets` table in Convex.
**StickyNote** (extends ObjectBase):
```warp-runnable-command
{ text: string, backgroundColor: string (hex), style: "square" | "rounded", fontSize: float }
```
**Table** (extends ObjectBase):
```warp-runnable-command
{ rows: int, cols: int, cells: Array<{ row: int, col: int, content: string }>, borderColor: string, headerRow: boolean, colWidths: float[], rowHeights: float[] }
```
**AudioAttachment** (extends ObjectBase):
```warp-runnable-command
{ assetId: string, durationMs: int, mimeType: string, linkedNotebookId?: string }
```
Audio binary stored in R2. No ink-to-audio time sync in v1 (dumb attachment — record and playback only).
**Shape** (extends ObjectBase) — for recognized shapes:
```warp-runnable-command
{ shapeType: "line"|"rectangle"|"ellipse"|"triangle"|"circle"|"arrow", strokeColor: string, strokeWidth: float, fillColor?: string }
```
## 3.4 Entity Changes to PLAN.md §5
* **Notebook** (was `Notebook` — keep name, add `isFavorite: Boolean`)
* **Folder** — `parentFolderId` for nesting, synced to server via Convex `folders` table
* **Page** — dimensions in px, templateType, templateDensity, templateLineWidth, backgroundColorHex
* **Stroke** — remove `yjsOpId` (Yjs handles identity internally). Raw points with per-point pressure/tilt/timestamp.
* **Add**: StickyNote, Table as first-class content types
* **AudioRecording** — in v1 scope, stored as R2 blob with Convex asset metadata
## 3.5 Favorites
* `isFavorite: Boolean` field on Notebook entity (persisted in Room + synced via Convex)
* Client-side sort: favorites float to top of current view (folder or all-notebooks)
* If viewing all notebooks: all favorites at top, then non-favorites sorted by user preference
* If viewing a folder: only that folder's favorites at top
# 4. Convex Schema — Full Overhaul
The existing scaffold schema is replaced entirely. New tables:
* `users` — Clerk user profiles
* `folders` — server-synced folder hierarchy with `parentFolderId`
* `notebooks` — (was `notes`) with `folderId`, `isFavorite`, `snapshotUrl`, `snapshotStateVector`
* `pages` — page metadata per notebook
* `crdtUpdates` — binary Yjs update deltas per page (small, <10KB each)
* `assets` — metadata for R2 blobs (PDFs, images, audio, snapshots, exports)
* `shares` — email-based sharing
* `publicLinks` — public share links
* `commits` — snapshot restore points (reference Yjs state vector + R2 snapshot URL)
* `searchTexts` — full-text search index (HWR + PDF text)
* `presence` — ephemeral cursor positions for live collaboration
* `exportMetadata` — export job tracking
* `templateDefaults` — user's default template settings
Removed tables:
* `pageObjects` — objects live inside per-page Yjs Doc, not a separate table
* `gestureSettings` — local-only, stays in Android DataStore/Room
* `templateScopes` — replaced by simpler `templateDefaults` + per-page data in Yjs Doc
* `searchIndexTokens` — replaced by simpler `searchTexts`
# 5. V0 API — CRDT Rewrite
## Replaced endpoints
The entire ops model changes:
* **Remove**: `ops.submitBatch`, `ops.listPageOps`, `ops.watchPageHead` (Lamport-based)
* **Add**: `sync.pushUpdates` — push Yjs binary update deltas to Convex
* **Add**: `sync.pullUpdates` — fetch Yjs updates since a given state vector
* **Add**: `sync.watchHead` — subscribe to new updates for a page (reactive query)
## New endpoints
* `folders.list`, `folders.create`, `folders.rename`, `folders.delete`, `folders.move`
* `presence.update` — push cursor position + active tool
* `presence.watch` — subscribe to all collaborator presence for a notebook
* Audio recording: included in `assets.presignUpload` (type: `audio`)
## Commits change
* `commits.create` — takes `snapshotAssetId` + Yjs `stateVector` (binary) instead of `baseLamport`
* `commits.restore` — loads snapshot, applies to Yjs doc, creates new commit at head
## Full API surface (v0)
**Users**: `users.upsertMe`, `users.me`
**Folders**: `folders.list`, `folders.create`, `folders.rename`, `folders.delete`, `folders.move`
**Notebooks**: `notebooks.listMine`, `notebooks.listSharedWithMe`, `notebooks.create`, `notebooks.rename`, `notebooks.delete`, `notebooks.get`, `notebooks.setFavorite`
**Pages**: `pages.createInkPage`, `pages.attachPdfPage`, `pages.get`, `pages.reorder`, `pages.delete`
**Sync**: `sync.pushUpdates`, `sync.pullUpdates`, `sync.watchHead`
**Commits**: `commits.create`, `commits.list`, `commits.get`, `commits.restore`
**Assets**: `assets.presignUpload`, `assets.confirmUpload`, `assets.getDownloadUrl`
**Shares**: `shares.grantByEmail`, `shares.revoke`, `shares.list`
**Public links**: `publicLinks.create`, `publicLinks.disable`, `publicLinks.resolve`
**Search**: `search.upsertPageText`, `search.global`
**Presence**: `presence.update`, `presence.watch`
**Previews**: `previews.setFirstPagePreview`
**Exports**: `exports.register`, `exports.getLatest`, `publicAssets.getDownloadUrl`
# 6. Web Viewer — PDF + Stroke Rendering
* Web downloads the original PDF from R2 via presigned URL and renders it (using pdf.js or Canvas)
* Ink strokes rendered as Canvas overlay on top of the PDF (Canvas chosen over SVG for performance with many strokes)
* Web loads per-page Yjs doc (snapshot + updates) to get current stroke state — **full CRDT replay engine** in JS
* Strokes are tessellated client-side from raw point data into variable-width paths
* Web subscribes to Convex `sync.watchHead` for live updates
* **No auto-flattening** of PDFs to R2. The original PDF + separate stroke data is the canonical storage.
* Flattened PDF is only generated on explicit user export action
* Max 3 concurrent editors per notebook
# 6.5 Final Micro-Decisions
* **Undo/redo limit**: 100 undo + 100 redo operations per page (FIFO eviction of oldest entries when limit exceeded)
* **Presence update cadence**: Push cursor position every 500ms while actively drawing; stop pushing after 5s idle
* **Snapshot cadence**: Debounced from last edit — 5s after last edit for shared notebooks, 20s after last edit for private notebooks
* **Android local storage**: Room for metadata + settings + search FTS. Yjs doc stored as a raw binary file in app internal storage. Binary file format: 4-byte magic header `ONYX`, 4-byte version (uint32, big-endian), followed by raw Yjs encoded state array.
* **Table cell editing**: Tap on a table cell → software keyboard pops up for text input
# 7. Storage Model
* **Convex**: metadata, CRDT deltas (<10KB each), search indexes, shares, user data, presence, folder hierarchy
* **R2 (S3-compatible)**: PDFs (original, never auto-flattened), images, audio recordings, Yjs doc snapshots (periodic), exported PDFs (user-initiated), notebook thumbnails
* **Android local**: Room for local metadata cache, offline queue, editor settings, search FTS. Local Yjs doc file for offline editing.
* Large blobs (images, audio, PDFs) are NEVER stored in Convex. Convex only holds asset metadata (R2 key, size, content type, hash).
# 8. Pen Types
Three named pen instruments with distinct behavior:
* **Ballpoint**: low latency, mild pressure→width response, strong wobble suppression, consistent line weight
* **Fountain**: stronger width/orientation dynamics, pressure produces dramatic thick-thin variation, slight ink pooling at slow points
* **Pencil**: minimal smoothing, raw textured feel, lighter opacity, no taper
Highlighter is a separate tool (not a pen type).
# 9. Feature Additions — v1 Scope
## Sticky Notes
* Insertable from Zone 2 toolbar
* Colored background (preset colors: yellow, pink, blue, green, purple)
* Square or rounded style
* Editable text content
* Resizable, movable, lockable
* Stored as ObjectData in per-page Yjs Doc
## Tables
* Insertable from Zone 2 toolbar
* Default: 3×3 grid, resizable
* Cells contain plain text (no rich text in v1)
* Add/remove rows and columns via context menu
* Resize columns by dragging borders
* Optional header row (bold, different background)
* Stored as ObjectData in per-page Yjs Doc
## Audio Recording
* Record in-app, tied to notebook
* Playback UI with play/pause, seek bar, duration
* Audio binary stored in R2 (referenced by assetId in Yjs Doc)
* No ink-to-audio time sync in v1 (just a dumb attachment)
* Insertable from Zone 2 toolbar (microphone icon)
## Circle Gesture for Adding Pages (UX Spec §4.5)
Exact mechanics:
1. User scrolls past the last page and continues swiping up
2. A circular progress indicator (thin stroke, accent color) begins drawing clockwise from the 12 o'clock position
3. The circle diameter is 64dp, centered horizontally at the bottom of the viewport
4. Swipe distance threshold: 120dp of overscroll to complete the full circle
5. The circle fills proportionally: at 60dp overscroll the circle is half-drawn, at 120dp it's complete
6. If the user releases **before** the circle completes: circle animates back to empty (150ms ease-out), no page added
7. If the user releases **after** the circle completes: haptic feedback (light tap), circle fills with accent color (100ms), new blank page appends with 200ms slide-in animation
8. The + icon appears in the center of the circle at 50% completion and scales up to full size at 100%
9. Fallback: pages can also be added via the + button in Zone 1 (this gesture is a convenience, not the only path)
# 10. Recognition Behavior
## Shape Recognition
* Uses IInk SDK's native confidence score directly (no normalization)
* No user-configurable threshold in v1 — uses SDK defaults
* If shape recognized: raw stroke replaced with geometric primitive
* If not recognized: raw stroke preserved unchanged
## LaTeX Conversion (Lasso → Convert)
* Lasso selected strokes → send to IInk math recognizer
* If recognition succeeds: create TextBlock with `isLatex=true`, `latexSource` populated, replacing selected strokes
* If recognition fails or low confidence: show toast "Recognition failed — try again with clearer writing"
* Original strokes are preserved (not deleted) until the TextBlock is confirmed
* User can always Undo to restore original handwriting
## Text Conversion (Lasso → Convert to Text)
* Same flow as LaTeX but with text recognizer
* If recognition fails: show toast "Recognition failed"
* User can Undo to restore handwriting
## MyScript SDK
* Android: offline IInk SDK 4.3 (bundled recognition models)
* Web: MyScript cloud API — NOT integrated in v1 (no web editing/recognition)
* English only for initial license; multi-language deferred
# 11. Folder Sync
* Folders are synced to the server via Convex `folders` table
* Folder hierarchy (arbitrary nesting via `parentFolderId`) is consistent across devices
* Convex `notebooks` table includes `folderId` field
* Folder delete: notebooks in deleted folder move to root (`folderId` set to null)
# 12. Files to Update
## PLAN.md
* §5 Data Model: add isFavorite, StickyNote, Table types, update Stroke to remove yjsOpId, add coordinate system section, clarify pen types
* §6 Feature Set: add pen types (Ballpoint/Fountain/Pencil), add StickyNote and Table to content types, add circle gesture spec, add favorites
* §8 Sync Architecture: complete rewrite for Y-Octo/Yjs CRDT model (remove all Lamport references)
* §10 PDF Support: clarify no auto-flatten, web renders from R2
* §13 Web Viewer: full CRDT replay engine, Canvas overlay for strokes, PDF from R2
* §16 Open Questions: update sync model as resolved (CRDT, not Lamport), add new resolved items
* Global: ensure "Notebook" naming everywhere
## V0-api.md
* Complete rewrite: replace Lamport op-log model with CRDT delta model
* Add folder CRUD endpoints
* Add presence endpoints
* Add audio recording support in asset types
* Add sync endpoints (pushUpdates, pullUpdates, watchHead)
* Rename Note→Notebook everywhere
* Add page management endpoints (reorder, delete)
* Add favorites endpoint
## convex/schema.ts
* Full overhaul per §4 above
## Onyx UX Specification.md
* Confirm pen types: Ballpoint / Fountain / Pencil
* Confirm Sticky Notes and Tables in Zone 2
* Add circle gesture exact spec
* Add favorites as persistent field
* Rename Note→Notebook where applicable
## Milestone plans (.sisyphus/plans/)
* editor-feel-refactor.md: update pen types, minor naming
* android-foundation.md: minor naming updates
* milestone-b-web-viewer.md: CRDT model, PDF from R2, Canvas stroke rendering, full replay engine
* milestone-c-collaboration-sharing.md: CRDT sync, presence table, remove Lamport references
