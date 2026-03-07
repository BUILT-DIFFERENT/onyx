# Milestone B: Web Viewer + Convex Backend (v0)

## Context
- Web is view-only in v1. No drawing, editing, or HWR.
- Convex is the metadata/sync store; all canonical page content lives in Yjs docs (R2 snapshots + Convex CRDT update deltas).
- Clerk is the only IdP. Clients call `users.upsertMe` after login.
- Storage: Cloudflare R2 for large blobs (PDFs, images, audio, Yjs snapshots). Convex holds asset references (R2 key + size + hash), never raw binary.
- `V0-api.md` is the authoritative API contract.
- Rendering: PDF.js fetches original PDF from R2 presigned URL. Canvas overlay tessellates ink strokes from Yjs doc raw point data. No tile rasters, no pre-generated images.

## Prerequisites
- Milestone A completed (Android offline authoring).
- Android Foundation work needed for web/backend contract readiness completed (at minimum: data model alignment + CRDT-readiness scaffold from `android-foundation.md`).

## Goals
- Ship a view-only web app with TanStack Start + shadcn/ui + zod.
- Implement the core v0 API surface in Convex for notebooks/pages/folders/assets/search/previews.
- Wire Clerk auth on web and ensure `users.upsertMe` is called after login.
- Render PDF backgrounds via PDF.js and ink strokes via Canvas overlay (full Yjs replay engine).

## Non-Goals
- No web editing, drawing, or input.
- No real-time CRDT sync endpoints yet (`sync.pushUpdates`, `sync.pullUpdates`, `sync.watchHead` deferred to Milestone C).
- Exception: `sync.recordSnapshot` IS implemented in Milestone B — it is a simple metadata mutation that records a snapshot URL on the notebook/page record.
- Web reads from R2 snapshots uploaded by Android. No live delta streaming.
- No sharing, public links, exports, or presence.
- No external cache/kv.

## Snapshot Bootstrap Path (Milestone B)

Before full CRDT sync exists, snapshots reach R2 via a one-way Android → R2 push:

1. Android writes the local Yjs binary file (notebook-level + per-page) as part of normal authoring.
2. On notebook close or periodic auto-save, Android uploads the full Yjs state to R2 via `assets.presignUpload` (type: `snapshot`) + `assets.confirmUpload`.
3. Android calls `sync.recordSnapshot` to record the R2 URL and state vector on the notebook/page Convex record (`snapshotUrl`, `snapshotStateVector`).
4. The web viewer reads `snapshotUrl` from the notebook/page record, fetches the snapshot from R2 via presigned URL, and decodes it with Yjs JS library.

This is one-way (Android → R2 → Web). No delta streaming. The web does NOT call `sync.pullUpdates` in Milestone B. It reads the latest snapshot only.

## Deliverables
- Web app scaffold in `apps/web/` using TanStack Start + shadcn/ui.
- Convex schema and functions covering the in-scope v0 API domains.
- PDF.js + Canvas ink rendering in web viewer (view-only).
- Global search UI backed by Convex search indexes.
- Clerk auth wired on web; `users.upsertMe` called post-login.

## v0 API Scope (Milestone B)
In scope (implement now):
- Users: `users.upsertMe`, `users.me`
- Folders: `folders.create`, `folders.rename`, `folders.move`, `folders.delete`, `folders.listMine`
- Notebooks: `notebooks.listMine`, `notebooks.listSharedWithMe`, `notebooks.create`, `notebooks.rename`, `notebooks.move`, `notebooks.setFavorite`, `notebooks.delete`, `notebooks.restore`, `notebooks.get`
- Pages: `pages.create`, `pages.attachPdf`, `pages.reorder`, `pages.delete`, `pages.get`
- Assets: `assets.presignUpload`, `assets.confirmUpload`, `assets.getDownloadUrl`
- Search: `search.upsertPageText`, `search.global`
- Previews: `previews.setFirstPagePreview`
- Snapshot recording: `sync.recordSnapshot` (metadata-only mutation to record R2 snapshot URL)

Deferred to Milestone C:
- CRDT Sync (real-time): `sync.pushUpdates`, `sync.pullUpdates`, `sync.watchHead`
- Sharing/Public links: `shares.*`, `publicLinks.*`
- Exports/Public assets: `exports.*`, `publicAssets.getDownloadUrl`
- Presence: `presence.*`

## Definition of Done
- Web app renders notebook list and notebook viewer for authenticated users.
- PDF backgrounds rendered via PDF.js from R2 presigned URLs.
- Ink strokes rendered as Canvas overlay tessellated from Yjs doc point data.
- Convex functions for in-scope APIs are deployed and callable.
- `users.upsertMe` is called on login for web.

## Task Flow (with acceptance criteria)

### B-1. Scaffold TanStack Start app
Create TanStack Start app in `apps/web/` with shadcn/ui + zod + Tailwind. Configure Turborepo integration (`package.json` scripts, `turbo.json` tasks for web build/dev/lint).
- **Done when**: `bun run dev` starts TanStack Start dev server. `bun run build` produces a production build. `bun run lint` and `bun run typecheck` pass for `@onyx/web`.

### B-2. Wire Clerk auth
Integrate Clerk on web. After login, call `users.upsertMe` Convex mutation. Store auth state in Convex client context. Protected routes redirect to login if unauthenticated.
- **Done when**: Login flow works end-to-end in dev. `users.upsertMe` creates/updates user record in Convex. Unauthenticated access to `/notebook/:id` redirects to login. Convex query auth context includes the authenticated user identity.

### B-3. Convex schema tables and indexes
Define Convex schema matching `convex/schema.ts` (as defined in the repo). Tables: `users`, `folders`, `notebooks`, `pages`, `crdtUpdates`, `shares`, `publicLinks`, `assets`, `presence`, `searchTexts`, `exports`. Milestone B only implements functions for the in-scope tables; all tables are defined now for forward compatibility.
- **Done when**: `npx convex dev` starts without schema errors. All tables and indexes defined. Schema matches the types in `V0-api.md` §1 (shared types). `convex/schema.ts` is the single source for all table definitions.

### B-4. Convex functions for in-scope APIs
Implement the functions listed in "v0 API Scope (Milestone B)" section above. Each function validates inputs with Convex validators, checks auth, and returns the documented types.
- **Done when**: Each function is callable from the Convex dashboard. Auth-required functions reject unauthenticated calls with `UNAUTHENTICATED`. Contract fixtures in `tests/contracts/fixtures/` cover at least: create-notebook roundtrip, folder CRUD roundtrip, search-upsert-then-query roundtrip.

### B-5. Web notebook list + notebook viewer routes
Routes: `/` (notebook list, grid layout), `/notebook/:notebookId` (notebook viewer with page list), `/notebook/:notebookId/:pageId` (direct page link). Notebook list shows titles, thumbnails (from preview assets), and last-modified dates. Notebook viewer shows stacked pages with ink + PDF content.
- **Done when**: Notebook list loads via `notebooks.listMine` Convex query and renders notebook cards with favorites floating to top. Clicking a notebook navigates to the viewer. Viewer shows all pages from `notebooks.get` response. Empty states render gracefully. `bun run typecheck` passes.

### B-6. PDF.js + Canvas ink rendering
PDF pages rendered via PDF.js from original PDF fetched from R2 presigned URL (`assets.getDownloadUrl`). Ink strokes rendered as Canvas overlay on top of PDF, tessellated from raw point arrays in the Yjs doc. For Milestone B, stroke data is fetched from R2 snapshots. Android uploads snapshots via `assets.presignUpload` + `sync.recordSnapshot` on notebook close / auto-save. Web fetches the snapshot URL from the notebook/page Convex record, downloads from R2 via presigned URL, and decodes with Yjs JS library. No delta streaming in Milestone B.
- **Done when**: A notebook with PDF pages and ink strokes renders correctly in the web viewer. PDF pages load from R2 via PDF.js. Ink strokes overlay the PDF at correct page-space pixel positions. Zoom scales both PDF and strokes together. `bun run build` succeeds.

### B-7. Search UI
Search bar in the top nav (matching UX Spec §2.8). Queries `search.global` Convex function. Results show notebook title, page number, snippet text. Clicking a result navigates to the specific page.
- **Done when**: Search returns results from `search.global`. Results are clickable and navigate to the correct notebook/page. Empty query shows no results. No-match query shows "No results found" state. `bun run typecheck` passes.

## Definition of Done (milestone gate)
- All B-1 through B-7 acceptance criteria met.
- `bun run lint`, `bun run typecheck`, `bun run build` pass for `@onyx/web`.
- `npx convex deploy` succeeds.
- Web app renders notebook list and viewer for authenticated users with PDF.js + Canvas rendering.
- Contract test fixtures in `tests/contracts/fixtures/` cover notebook CRUD, folder CRUD, and search roundtrips.

## Notes
- Web types derive from Convex generated types (no manual type duplication).
- Android models remain explicit Kotlin data classes with JSON serialization.
- PDF.js renders the original PDF from R2; PDFs are never auto-flattened. Flattened PDFs are only generated on explicit user export.
- All Convex error responses use the error codes defined in `V0-api.md` §5.
- The Yjs replay engine on web will be fully wired in Milestone C when `sync.pullUpdates` / `sync.watchHead` are implemented. For B-6, the web reads from R2 snapshots directly.
