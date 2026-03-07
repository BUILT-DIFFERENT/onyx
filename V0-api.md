Below is the **V0 API contract** for Convex (TypeScript) + R2, aligned with the CRDT-based sync architecture in `PLAN.md`. **Android authoring**, **web view-only** (v1), **Y-Octo/Yjs CRDT sync**, **snapshot cadence debounced from last edit (5s shared / 20s private)**, **PDF.js + Canvas overlay on web**, **global search** over HWR + PDF text.

Written as **Convex function signatures + shared types** — the canonical contract for Android + web clients.

---

## 0) Contract Basics

### Auth

* All authenticated routes use Clerk → Convex auth integration.
* Public link routes are unauthenticated, use `linkToken`.

### Storage

* **Convex**: metadata, CRDT update deltas (<10 KB each), folder hierarchy, sharing, search indexes, presence, asset references.
* **Cloudflare R2**: large blobs — PDFs (original, never auto-flattened), images, audio recordings, Yjs doc snapshots, exported PDFs, notebook thumbnails.
* Convex never stores raw binary data. It holds R2 keys + size + hash.

### Sync Model

* **Y-Octo/Yjs CRDTs** — Android uses Y-Octo (Rust/JNI), web uses Yjs (JS). Both produce the same binary CRDT update format.
* **Two-level CRDT docs**: notebook-level Yjs doc (page ordering + metadata) + per-page Yjs doc (strokes, objects, deletedStrokes, deletedObjects).
* Conflict resolution is automatic via CRDT merge semantics — no Lamport clocks, no deterministic replay ordering.
* Clients push Yjs update binaries to Convex; Convex stores them and notifies subscribers.

### Coordinate System

* All coordinates in **pixels (px)** — strokes, objects, page dimensions, PDF overlay.
* Default page: 794 × 1123 px (A4 at 96 DPI). Infinite canvas: 794 px wide, infinite vertical.
* Strokes stored in absolute page-space pixels (origin = top-left). Zoom is client-side viewport transform only.

---

## 1) Shared Types (Contract)

```ts
// IDs
type IdStr = string;

type UserId = IdStr;
type NotebookId = IdStr;
type PageId = IdStr;
type FolderId = IdStr;
type AssetId = IdStr;
type DeviceId = string;   // UUID from client
type PublicLinkToken = string; // high-entropy URL token

type UnixMs = number;

type Role = "viewer" | "editor";

type NotebookMode = "paged" | "infinite_canvas";

type TemplateType = "blank" | "lined" | "dotted" | "grid";

type PenType = "ballpoint" | "fountain" | "pencil";

type ToolType = "pen" | "highlighter";

type PageObjectType = "textBlock" | "image" | "stickyNote" | "table" | "audioAttachment" | "shape";

type ShapeType = "line" | "rectangle" | "ellipse" | "triangle" | "circle" | "arrow";

// --- Notebook & Page ---

type NotebookMeta = {
  notebookId: NotebookId;
  ownerUserId: UserId;
  folderId?: FolderId;
  title: string;
  coverColor: string;        // hex
  isFavorite: boolean;
  notebookMode: NotebookMode;
  createdAt: UnixMs;
  updatedAt: UnixMs;
  deletedAt?: UnixMs;        // soft delete
  snapshotUrl?: string;       // R2 URL of latest notebook-level Yjs snapshot
  snapshotStateVector?: string; // base64-encoded Yjs state vector

  // Preview (first page thumbnail)
  previewThumbAssetId?: AssetId;
  previewPageAssetId?: AssetId;
  previewUpdatedAt?: UnixMs;

  // Sharing metadata (for cadence decisions)
  hasPublicLink?: boolean;
  shareCount?: number;
};

type PageMeta = {
  pageId: PageId;
  notebookId: NotebookId;
  order: number;
  templateType: TemplateType;
  templateDensity: number;
  templateLineWidth: number;
  backgroundColorHex: string;
  widthPx: number;           // default 794
  heightPx: number;          // default 1123; 0 = infinite canvas
  pdfAssetId?: AssetId;
  pdfPageNo?: number;        // 0-based
  pageSnapshotUrl?: string;
  pageSnapshotStateVector?: string; // base64-encoded
  updatedAt: UnixMs;
};

type FolderMeta = {
  folderId: FolderId;
  ownerUserId: UserId;
  parentFolderId?: FolderId;
  name: string;
  createdAt: UnixMs;
  updatedAt: UnixMs;
};

// --- CRDT Sync ---

type CrdtUpdate = {
  updateId: IdStr;
  notebookId: NotebookId;
  pageId?: PageId;           // null = notebook-level doc; set = per-page doc
  authorUserId: UserId;
  deviceId: DeviceId;
  updateBinary: string;      // base64-encoded Yjs update binary (<10 KB)
  createdAt: UnixMs;
};

// --- Yjs Doc Internal Structure (not stored in Convex — lives inside Yjs binary) ---
// Notebook-level Yjs doc:
//   pages: Y.Array<{ pageId, order, templateType, ... }>
//   metadata: Y.Map<string, any>  (title, coverColor, etc.)
//
// Per-page Yjs doc:
//   strokes: Y.Map<strokeId, StrokeData>
//   objects: Y.Map<objectId, PageObjectData>
//   deletedStrokes: Y.Map<strokeId, { deletedAt: number }>
//   deletedObjects: Y.Map<objectId, { deletedAt: number }>

// --- Assets ---

type AssetType =
  | "pdf"
  | "image"
  | "audio"
  | "snapshot"
  | "exportPdf"
  | "thumbnail";

type AssetMeta = {
  assetId: AssetId;
  ownerUserId: UserId;
  notebookId?: NotebookId;
  type: AssetType;
  r2Key: string;
  contentType: string;
  size?: number;
  sha256?: string;
  createdAt: UnixMs;
};

// --- Sharing ---

type ShareEntry = {
  notebookId: NotebookId;
  granteeUserId: UserId;
  role: Role;
  grantedByUserId: UserId;
  createdAt: UnixMs;
  revokedAt?: UnixMs;
};

type PublicLinkEntry = {
  notebookId: NotebookId;
  linkToken: PublicLinkToken;
  createdByUserId: UserId;
  createdAt: UnixMs;
  revokedAt?: UnixMs;
};

// --- Presence ---

type PresenceEntry = {
  notebookId: NotebookId;
  pageId: PageId;
  userId: UserId;
  cursorX: number;           // px, page-space
  cursorY: number;           // px, page-space
  activeTool: string;        // "pen" | "highlighter" | "eraser" | "lasso"
  activeColor?: string;      // hex
  updatedAt: UnixMs;
};

// --- Search ---

type SearchHit = {
  notebookId: NotebookId;
  notebookTitle: string;
  pageId: PageId;
  pageNo?: number;
  highlight?: { x: number; y: number; w: number; h: number };
  snippetText: string;
  updatedAt: UnixMs;
};
```

---

## 2) R2 Upload/Download Contract

V0 uses Convex actions to mint **2-hour presigned URLs**.

```ts
type PresignRequest = {
  type: AssetType;
  contentType: string;
  size?: number;
  sha256?: string;
  notebookId?: NotebookId;
  pageId?: PageId;
};

type PresignResponse = {
  assetId: AssetId;
  r2Key: string;
  uploadUrl: string;      // PUT
  headers?: Record<string, string>;
  expiresAt: UnixMs;
};

type DownloadUrlResponse = {
  assetId: AssetId;
  downloadUrl: string;    // GET
  expiresAt: UnixMs;
};
```

Rules:

* Client calls `assets.presignUpload()` → PUT to R2 → calls `assets.confirmUpload()`.
* Client calls `assets.getDownloadUrl(assetId)` to fetch GET URL (2h TTL).
* Public-link viewers get short-lived URLs minted via `publicAssets.getDownloadUrl`.

---

## 3) Convex Functions (V0)

### 3.1 Users

```ts
// mutation (called after Clerk login)
users.upsertMe(): { userId: UserId }

// query
users.me(): { userId: UserId; email: string; displayName: string; avatarUrl?: string }
```

### 3.2 Folders

```ts
// mutation
folders.create({ name: string; parentFolderId?: FolderId }): { folderId: FolderId }

// mutation
folders.rename({ folderId: FolderId; name: string }): void

// mutation
folders.move({ folderId: FolderId; parentFolderId?: FolderId }): void

// mutation
folders.delete({ folderId: FolderId }): void
// On delete: notebooks in folder get folderId set to null (moved to root)

// query
folders.listMine(): FolderMeta[]
```

### 3.3 Notebooks

```ts
// query
notebooks.listMine(): NotebookMeta[]

// query
notebooks.listSharedWithMe(): NotebookMeta[]

// mutation
notebooks.create({
  title: string;
  folderId?: FolderId;
  notebookMode: NotebookMode;
  coverColor?: string;
}): { notebookId: NotebookId }

// mutation
notebooks.rename({ notebookId: NotebookId; title: string }): void

// mutation
notebooks.move({ notebookId: NotebookId; folderId?: FolderId }): void

// mutation
notebooks.setFavorite({ notebookId: NotebookId; isFavorite: boolean }): void

// mutation
notebooks.delete({ notebookId: NotebookId }): void  // soft-delete (sets deletedAt)

// mutation
notebooks.restore({ notebookId: NotebookId }): void  // undelete from trash

// query
notebooks.get({ notebookId: NotebookId }): {
  notebook: NotebookMeta;
  myRole: Role;
  pages: PageMeta[];
}
```

Authorization:

* `delete/rename/move/setFavorite` — owner or editor.
* `restore` — owner only.
* `get` — owner, editor, or viewer (via share).

### 3.4 Pages

```ts
// mutation
pages.create({
  notebookId: NotebookId;
  templateType?: TemplateType;
  widthPx?: number;
  heightPx?: number;
  insertAfterPageId?: PageId;  // null = append at end
}): { pageId: PageId }

// mutation
pages.attachPdf({
  notebookId: NotebookId;
  pdfAssetId: AssetId;
  pdfPageNo: number;
  widthPx: number;
  heightPx: number;
}): { pageId: PageId }

// mutation
pages.reorder({
  notebookId: NotebookId;
  pageIds: PageId[];  // new ordering
}): void

// mutation
pages.delete({ pageId: PageId }): void

// query
pages.get({ pageId: PageId }): PageMeta
```

### 3.5 CRDT Sync

```ts
// mutation (editor)
sync.pushUpdates({
  notebookId: NotebookId;
  pageId?: PageId;              // null = notebook-level doc
  deviceId: DeviceId;
  updates: Array<{
    updateBinary: string;       // base64-encoded Yjs update (<10 KB)
  }>;
}): { accepted: number }

// query (viewer+)
sync.pullUpdates({
  notebookId: NotebookId;
  pageId?: PageId;
  stateVector: string;          // base64-encoded Yjs state vector from client
  limit?: number;               // default 100
  cursor?: string;              // opaque pagination cursor from previous response
}): {
  updates: Array<{
    updateBinary: string;
    createdAt: UnixMs;
  }>;
  hasMore: boolean;
  nextCursor?: string;          // present when hasMore=true
}

// query (viewer+, reactive subscription)
sync.watchHead({
  notebookId: NotebookId;
  pageId?: PageId;
}): {
  latestUpdateAt: UnixMs;
  updateCount: number;
}

// mutation (editor) — write snapshot to R2, record URL in Convex
sync.recordSnapshot({
  notebookId: NotebookId;
  pageId?: PageId;
  snapshotAssetId: AssetId;     // R2 asset containing full Yjs doc state
  stateVector: string;          // base64-encoded state vector of this snapshot
}): void
```

Client sync flow:

1. On notebook open: load local Yjs binary → fetch R2 snapshot if stale → loop `sync.pullUpdates(stateVector, cursor)` until `hasMore=false` → apply → subscribe to `sync.watchHead`.
2. During editing: mutate local Yjs doc → queue update binary → `sync.pushUpdates` when online.
3. Snapshot cadence (debounced from last edit): shared/public = 5s, private = 20s. Client uploads full Yjs state to R2 → `sync.recordSnapshot`.
4. Conflict resolution: automatic via Yjs CRDT merge. No manual conflict handling.

### 3.6 Sharing

```ts
// mutation (owner/editor)
shares.grantByEmail({
  notebookId: NotebookId;
  emailLower: string;
  role: Role;
}): { granteeUserId: UserId }

// mutation (owner/editor)
shares.revoke({
  notebookId: NotebookId;
  granteeUserId: UserId;
}): void

// query
shares.list({ notebookId: NotebookId }): Array<{
  emailLower: string;
  displayName: string;
  role: Role;
  createdAt: UnixMs;
}>

// mutation (owner/editor)
publicLinks.create({ notebookId: NotebookId }): { linkToken: PublicLinkToken }

// mutation (owner/editor)
publicLinks.disable({ linkToken: PublicLinkToken }): void

// query (public, unauthenticated)
publicLinks.resolve({ linkToken: PublicLinkToken }): {
  notebook: NotebookMeta;
  pages: PageMeta[];
}
```

### 3.7 Assets (R2 Presign + Confirm)

```ts
// action (auth required)
assets.presignUpload(req: PresignRequest): PresignResponse

// mutation
assets.confirmUpload({
  assetId: AssetId;
  size?: number;
  sha256?: string;
}): void

// action (auth or public-link token)
assets.getDownloadUrl({ assetId: AssetId }): DownloadUrlResponse

// action (public, unauthenticated)
publicAssets.getDownloadUrl({
  linkToken: PublicLinkToken;
  assetId: AssetId;
}): DownloadUrlResponse
```

### 3.8 Presence

```ts
// mutation (editor)
presence.update({
  notebookId: NotebookId;
  pageId: PageId;
  cursorX: number;
  cursorY: number;
  activeTool: string;
  activeColor?: string;
}): void
// Called every 500ms while drawing; stops after 5s idle.

// query (viewer+, reactive subscription)
presence.watch({
  notebookId: NotebookId;
}): PresenceEntry[]
// Returns all active cursors for the notebook. Entries auto-expire after 10s of no updates.
```

### 3.9 Previews

Debounced to max once per 120s per notebook.

```ts
// mutation (editor)
previews.setFirstPagePreview({
  notebookId: NotebookId;
  previewThumbAssetId?: AssetId;
  previewPageAssetId?: AssetId;
  generatedBy: "android" | "web";
}): { applied: boolean; nextAllowedAt: UnixMs }
```

Server behavior:

* Enforce 120s debounce window per notebook.
* If within debounce, `applied=false` with `nextAllowedAt`.

### 3.10 Search (Global)

Android produces HWR text and PDF extracted text; pushes to Convex.

```ts
// query (auth)
search.global({
  query: string;
  limit?: number;
}): SearchHit[]

// mutation (editor/owner; typically Android)
search.upsertPageText({
  notebookId: NotebookId;
  pageId: PageId;
  recognizedText?: string;   // HWR output
  pdfText?: string;          // PDF extracted text
  source: "handwriting" | "pdfText" | "both";
  extractedAt: UnixMs;
}): void
```

Ranking: bias toward recent notebooks (text relevance + recency boost on `notebook.updatedAt`).

`search.global` queries both the `search_content` index (handwriting) and `search_pdf_content` index (PDF text) and merges results by relevance + recency.

### 3.11 Exports (User-Initiated PDF)

```ts
// mutation (editor)
exports.register({
  notebookId: NotebookId;
  exportAssetId: AssetId;
  mode: "flattened";         // v0: flattened only (strokes baked into PDF)
}): void

// query (viewer+ or public link)
exports.getLatest({
  notebookId: NotebookId;
}): { exportAssetId?: AssetId; mode?: string; createdAt?: UnixMs }
```

Public download uses `publicAssets.getDownloadUrl(linkToken, exportAssetId)`.

---

## 4) Client Obligations (V0 Rules)

### Android (Authoring)

* All page content lives inside Yjs docs (strokes in `Y.Map`, objects in `Y.Map`).
* On pen-up: stroke committed to local Yjs doc → Yjs update binary queued → flushed via `sync.pushUpdates`.
* Object mutations (TextBlock, Image, StickyNote, Table, AudioAttachment, Shape): applied to Yjs `objects` map → update binary queued.
* Undo/redo: 100 undo + 100 redo ops per page (FIFO eviction). Undo writes new Yjs updates.
* Offline queue: pending Yjs updates stored in Room `OfflineQueueEntity`. WorkManager drains on connectivity.
* Snapshots: debounced from last edit — 5s (shared/public), 20s (private). Upload full Yjs state to R2 → `sync.recordSnapshot`.
* HWR/PDF text: pushed to Convex via `search.upsertPageText`.
* Room is metadata/settings cache only. Yjs binary file is the authoritative page content store.
* Yjs binary file format: 4-byte magic `ONYX` + 4-byte uint32 version (big-endian) + raw Yjs encoded state array.

### Web (View-Only, v1)

* Full Yjs replay engine: fetch R2 snapshot → `sync.pullUpdates(stateVector)` → reconstruct page content.
* PDF rendering: PDF.js fetches original PDF from R2 presigned URL → renders to canvas layer.
* Ink rendering: Canvas overlay tessellates strokes from Yjs doc raw point arrays.
* Live updates: subscribe to `sync.watchHead` → pull new deltas → re-render.
* No drawing, editing, or HWR in v1.

---

## 5) Minimal Error Contract (V0)

```ts
type ApiErrorCode =
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "VALIDATION_ERROR"
  | "EMAIL_NOT_FOUND"          // share-by-email to non-user
  | "PUBLIC_LINK_DISABLED"
  | "DEBOUNCED"                // preview debounce window
  | "PAYLOAD_TOO_LARGE";       // CRDT update > 10 KB
```

---

## 6) Background Jobs (Convex Scheduled Functions)

* **CRDT delta retention**: delete `crdtUpdates` entries covered by the latest recorded snapshot for that document scope (`notebookId` + optional `pageId`). Run periodically (e.g., every 10 minutes).
  - Implementation contract: retention is conservative. The job only deletes updates older than the latest snapshot-record time for that scope and keeps a safety tail of recent updates.
  - If snapshot metadata is missing or inconsistent, skip deletion for that scope.
* **Trash cleanup**: permanently delete notebooks where `deletedAt` is older than 20 days. Delete associated R2 assets.
* **Presence expiry**: remove `presence` entries older than 10s (can be handled inline by `presence.watch` query filtering).
