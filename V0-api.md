Below is a **V0 API contract** you can implement in Convex (TypeScript) + R2 that matches the clarified behavior: **Android authoring**, **web view-only**, **pen-up ops**, **snapshots 5s/20s**, **eraseLamport gating**, **first-page preview upload debounced (120s)**, **web does not download raw PDFs in v0** (renders from tiles/previews), **global search** over extracted PDF text + ink recognition text (extracted on Android).

I’m writing this as **Convex function signatures + shared types**, since that’s the cleanest “contract” for Android + web.

---

## 0) Contract basics

### Auth

* All authenticated routes use Clerk → Convex auth integration.
* Public link route is unauthenticated, uses `linkId` token.

### Storage

* R2 stores large blobs: pdfs, snapshots, rasters (tiles/previews), exports.
* Convex stores metadata + indexes + op headers + search text.

### Clock / ordering

* Ops are totally ordered by `(lamport, deviceId, opId)`.
* Devices maintain Lamport clock.
* `StrokeAdd` defines `createdLamport` for that stroke.
* `AreaErase` applies only to strokes where `stroke.createdLamport <= eraseLamport`.

---

## 1) Shared types (contract)

```ts
// IDs
type IdStr = string; // Convex Id<"table"> is concrete in code, but V0 contract uses strings

type UserId = IdStr;
type NoteId = IdStr;
type PageId = IdStr;
type AssetId = IdStr;
type DeviceId = string; // UUID from client
type OpId = string;     // UUID from client
type CommitId = IdStr;
type PublicLinkId = string; // high entropy token

type UnixMs = number;

type Role = "viewer" | "editor";

type NoteKind = "ink" | "pdf" | "mixed" | "infinite";

type PageGeometry =
  | { kind: "fixed"; width: number; height: number; unit: "pt" | "px" } // for PDF pages, recommend pt
  | { kind: "infinite"; tileSize: number; unit: "px" }; // tileSize 2024 fixed

type PageRef = { noteId: NoteId; pageId: PageId };

type AssetType =
  | "pdf"
  | "snapshot"
  | "tileRaster"
  | "previewThumb"
  | "previewPage"
  | "exportPdf"
  | "jiix";

type AssetMeta = {
  assetId: AssetId;
  ownerUserId: UserId;
  type: AssetType;
  r2Key: string;
  contentType: string;
  size?: number;
  sha256?: string;
  createdAt: UnixMs;
};

type NoteMeta = {
  noteId: NoteId;
  ownerUserId: UserId;
  title: string;
  createdAt: UnixMs;
  updatedAt: UnixMs;
  deletedAt?: UnixMs;

  // Preview shown in lists (v0: first page only)
  previewThumbAssetId?: AssetId; // small
  previewPageAssetId?: AssetId;  // low-ish res first page
  previewUpdatedAt?: UnixMs;

  // Sharing flags for cadence decisions client-side
  hasPublicLink?: boolean;
  shareCount?: number;
};

type PageMeta = {
  pageId: PageId;
  noteId: NoteId;
  kind: NoteKind;
  pdfAssetId?: AssetId;  // for pdf/mixed
  pdfPageNo?: number;    // 0-based
  geometry: PageGeometry;
  updatedAt: UnixMs;

  // Deterministic content versioning
  contentLamportMax: number;

  // Optional: pointer to most recent snapshot/commit
  latestCommitId?: CommitId;
};

type ShareEntry = {
  noteId: NoteId;
  granteeUserId: UserId;
  role: Role;
  grantedByUserId: UserId;
  createdAt: UnixMs;
  revokedAt?: UnixMs;
};

type PublicLinkEntry = {
  noteId: NoteId;
  linkId: PublicLinkId;
  createdAt: UnixMs;
  createdByUserId: UserId;
  revokedAt?: UnixMs;
};

// ---- Ops ----
type StrokeStyle = {
  tool: "pen";
  color?: string;        // optional v0
  baseWidth: number;     // in page units
  minWidthFactor: number; // e.g. 0.85 (±15%)
  maxWidthFactor: number; // e.g. 1.15
  nibRotation: boolean;
};

type Point = {
  x: number;
  y: number;
  t: number; // ms offset or absolute, your choice; contract just says number
  p?: number; // pressure 0..1
  tx?: number; // tilt x
  ty?: number; // tilt y
  r?: number;  // rotation / azimuth optional
};

type OpPayload =
  | {
      type: "StrokeAdd";
      strokeId: string;
      createdLamport: number;
      points: Point[];     // raw points
      style: StrokeStyle;
      bounds: { x: number; y: number; w: number; h: number };
    }
  | {
      type: "AreaErase";
      eraseLamport: number; // equals op.lamport typically
      geometry: { kind: "rect"; x: number; y: number; w: number; h: number } // v0: rect
              | { kind: "lasso"; points: {x:number;y:number}[] };             // optional if you want
      mode: "stroke" | "segment";
      // v0 semantics: re-resolve at replay-time, but only strokes createdLamport <= eraseLamport
    }
  | {
      type: "StrokeTombstone";
      strokeIds: string[]; // used by undo/repair if needed
    }
  | {
      type: "PreviewUpdate";
      // first page preview assets, debounced (120s)
      previewThumbAssetId?: AssetId;
      previewPageAssetId?: AssetId;
    }
  | {
      type: "PageMetadata";
      patch: Partial<Pick<PageMeta, "updatedAt" | "contentLamportMax">>;
    };

type OpHeader = {
  opId: OpId;
  pageId: PageId;
  noteId: NoteId;
  authorUserId: UserId;
  deviceId: DeviceId;
  lamport: number;
  createdAt: UnixMs;
  payloadInline?: OpPayload;     // v0: inline for most ops
  payloadAssetId?: AssetId;      // optional future if large
};

// ---- Commits/Snapshots ----
type CommitEntry = {
  commitId: CommitId;
  pageId: PageId;
  noteId: NoteId;
  authorUserId: UserId;
  createdAt: UnixMs;
  baseLamport: number;           // latest op included in snapshot
  snapshotAssetId: AssetId;      // required for v0 restore points
};
```

---

## 2) R2 upload/download contract

V0 uses Convex actions to mint **2-hour presigned URLs**.

```ts
type PresignRequest = {
  type: AssetType;
  contentType: string;
  size?: number;
  sha256?: string;
  noteId?: NoteId;
  pageId?: PageId;
};

type PresignResponse = {
  assetId: AssetId;     // reserved row in Convex
  r2Key: string;
  uploadUrl: string;    // PUT
  headers?: Record<string, string>;
  expiresAt: UnixMs;
};

type DownloadUrlResponse = {
  assetId: AssetId;
  downloadUrl: string;  // GET
  expiresAt: UnixMs;
};
```

Rules:

* Client calls `assets.presignUpload()` → PUT to R2 → calls `assets.confirmUpload()`.
* Client calls `assets.getDownloadUrl(assetId)` to fetch GET URL (2h).
* Public-link viewers never get direct permanent R2 URLs; they get short-lived URLs minted via backend.

---

## 3) Convex functions (V0)

### 3.1 Notes

```ts
// query
notes.listMine(): NoteMeta[]

// query
notes.listSharedWithMe(): NoteMeta[]

// mutation
notes.create({ title: string }): { noteId: NoteId }

// mutation
notes.rename({ noteId, title }: { noteId: NoteId; title: string }): void

// mutation
notes.delete({ noteId }: { noteId: NoteId }): void // tombstone

// query
notes.get({ noteId }: { noteId: NoteId }): { note: NoteMeta; myRole: Role; pages: PageMeta[] }
```

Authorization:

* Must be owner/editor/viewer as appropriate.
* `delete/rename` owner or editor (per your rule).

### 3.2 Pages

```ts
// mutation
pages.createInkPage({ noteId, geometry }: { noteId: NoteId; geometry: PageGeometry }): { pageId: PageId }

// mutation
pages.attachPdfPage({
  noteId,
  pdfAssetId,
  pdfPageNo,
  geometry,
}: {
  noteId: NoteId;
  pdfAssetId: AssetId;
  pdfPageNo: number;
  geometry: PageGeometry; // fixed in pt recommended
}): { pageId: PageId }

// query
pages.get({ pageId }: { pageId: PageId }): PageMeta
```

### 3.3 Sharing

```ts
// mutation (owner/editor)
shares.grantByEmail({
  noteId,
  emailLower,
  role,
}: {
  noteId: NoteId;
  emailLower: string;
  role: Role;
}): { granteeUserId: UserId }

// mutation (owner/editor)
shares.revoke({ noteId, granteeUserId }: { noteId: NoteId; granteeUserId: UserId }): void

// query
shares.list({ noteId }: { noteId: NoteId }): Array<{ emailLower: string; role: Role; createdAt: UnixMs }>

// mutation (owner/editor)
publicLinks.create({ noteId }: { noteId: NoteId }): { linkId: PublicLinkId }

// mutation (owner/editor)
publicLinks.disable({ linkId }: { linkId: PublicLinkId }): void

// query (public, unauth)
publicLinks.resolve({ linkId }: { linkId: PublicLinkId }): { note: NoteMeta; pages: PageMeta[] } // view-only
```

### 3.4 Ops stream (pen-up) + deterministic ordering

```ts
// mutation (editor)
ops.submitBatch({
  noteId,
  pageId,
  deviceId,
  ops,
}: {
  noteId: NoteId;
  pageId: PageId;
  deviceId: DeviceId;
  ops: Array<{
    opId: OpId;
    lamport: number;
    payload: OpPayload;
    createdAt?: UnixMs; // server can set
  }>;
}): { accepted: number; newContentLamportMax: number }

// query (viewer+)
ops.listPageOps({
  pageId,
  afterLamport,
  limit,
}: {
  pageId: PageId;
  afterLamport?: number;
  limit?: number;
}): OpHeader[]

// query (viewer+, reactive subscription)
ops.watchPageHead({ pageId }: { pageId: PageId }): { contentLamportMax: number; latestOpLamport?: number }
```

V0 semantics you locked in:

* `AreaErase` replay-time resolution, filter strokes by `createdLamport <= eraseLamport`.
* Segment erase splits strokes (client-side model), but the op itself can stay as “erase geometry” in v0.

### 3.5 Commits / snapshots (restore points)

```ts
// mutation (editor)
commits.create({
  noteId,
  pageId,
  baseLamport,
  snapshotAssetId,
}: {
  noteId: NoteId;
  pageId: PageId;
  baseLamport: number;
  snapshotAssetId: AssetId;
}): { commitId: CommitId }

// query (viewer+)
commits.list({ pageId }: { pageId: PageId }): CommitEntry[]

// query (viewer+)
commits.get({ commitId }: { commitId: CommitId }): CommitEntry
```

Restore behavior is client-side:

* Client can load an older commit (snapshot + ops up to lamport) as **personal view**.
* “Restore version” creates a new commit at head (or sets new head by writing a new snapshot/commit).

Optional helper:

```ts
// mutation (editor)
commits.restore({
  noteId,
  pageId,
  fromCommitId,
  deviceId,
}: {
  noteId: NoteId;
  pageId: PageId;
  fromCommitId: CommitId;
  deviceId: DeviceId;
}): { newCommitId: CommitId }
```

(Implementation can simply upload a new snapshot equal to the restored state and append a restore commit.)

### 3.6 Assets (R2 presign + confirm)

```ts
// action (auth required unless public link)
assets.presignUpload(req: PresignRequest): PresignResponse

// mutation
assets.confirmUpload({
  assetId,
  size,
  sha256,
}: {
  assetId: AssetId;
  size?: number;
  sha256?: string;
}): void

// action (auth or public-link token)
assets.getDownloadUrl({ assetId }: { assetId: AssetId }): DownloadUrlResponse
```

For public link, you can support:

```ts
// action (public)
publicAssets.getDownloadUrl({
  linkId,
  assetId,
}: {
  linkId: PublicLinkId;
  assetId: AssetId;
}): DownloadUrlResponse
```

### 3.7 Previews (first page preview + thumb)

Debounced to max once per 120s per note (your rule). Both Android and web may generate; web uploads.

```ts
// mutation (editor)
previews.setFirstPagePreview({
  noteId,
  previewThumbAssetId,
  previewPageAssetId,
  generatedBy, // "android" | "web"
}: {
  noteId: NoteId;
  previewThumbAssetId?: AssetId;
  previewPageAssetId?: AssetId;
  generatedBy: "android" | "web";
}): { applied: boolean; nextAllowedAt: UnixMs }
```

Server behavior:

* Enforce debounce window (120s) per note.
* If within debounce, `applied=false` and returns `nextAllowedAt`.

### 3.8 Search (global)

Web shows results; Android is responsible for producing:

* handwriting recognized text
* PDF embedded text extracted during import/indexing
  and uploading to Convex.

```ts
type SearchHit = {
  noteId: NoteId;
  noteTitle: string;
  pageId: PageId;
  pageNo?: number;          // for PDF notes
  kind: NoteKind;
  // v0: approximate highlight
  highlight?: { x: number; y: number; w: number; h: number; unit: "pt" | "px" };
  snippetText: string;      // plain text
  updatedAt: UnixMs;
};

// query (auth)
search.global({
  query,
  limit,
}: {
  query: string;
  limit?: number;
}): SearchHit[]

// mutation (editor/owner; typically Android)
search.upsertPageText({
  noteId,
  pageId,
  contentLamportMax,
  recognizedText,   // handwriting
  pdfText,          // embedded pdf text extracted elsewhere
  source,
  extractedAt,
}: {
  noteId: NoteId;
  pageId: PageId;
  contentLamportMax: number;
  recognizedText?: string;
  pdfText?: string;
  source: "handwriting" | "pdfText" | "both";
  extractedAt: UnixMs;
}): void
```

Ranking rule (your preference): bias toward **recent notes** (e.g., combine text score with recency boost on `note.updatedAt`).

### 3.9 Export (download annotated PDF)

You want: public viewers can download an annotated PDF; flattened vs not is okay.

V0 simplest contract: export is generated by Android (or later a worker), uploaded to R2.

```ts
// mutation (editor)
exports.register({
  noteId,
  pageId,
  exportAssetId,
  mode,
}: {
  noteId: NoteId;
  pageId: PageId;
  exportAssetId: AssetId;
  mode: "flattened" | "overlay"; // overlay = not flattened (v0 best-effort)
}): void

// query (viewer+ or public link)
exports.getLatest({ noteId }: { noteId: NoteId }): { exportAssetId?: AssetId; mode?: string; createdAt?: UnixMs }
```

Public download uses `publicAssets.getDownloadUrl(linkId, exportAssetId)`.

---

## 4) Client obligations (V0 rules)

### Android (authoring)

* Generates `StrokeAdd` on pen-up.
* Generates `AreaErase` ops (stroke or segment).
* Maintains a local page model that can:

  * apply ops in deterministic order
  * split strokes for segment erase
  * support per-user undo by appending inverse ops
* Creates snapshot blobs on cadence:

  * shared/public notes: ~5s while actively drawing
  * private notes: ~20s while actively drawing
* Extracts PDF embedded text “eventually” for large PDFs and pushes via `search.upsertPageText`.
* Uploads export PDFs.

### Web (view-only)

* Subscribes to ops and commits.
* Renders page from:

  * snapshots + ops replay, or
  * pre-rendered tiles/previews (depending on what you implement first)
* Generates and uploads **first-page preview tile + thumb** if changes happen, but server-enforced debounce (120s).
* Does **not** download raw PDFs in v0.

---

## 5) Minimal error contract (V0)

Use consistent error codes in Convex throws:

```ts
type ApiErrorCode =
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "VALIDATION_ERROR"
  | "EMAIL_NOT_FOUND"          // share-by-email to non-user
  | "PUBLIC_LINK_DISABLED"
  | "DEBOUNCED"
  | "CONFLICT";                // e.g., duplicate opId, invalid lamport ordering, etc.
```

---
