# Milestone B: Web Viewer + Convex Backend (v0)

## Context
- Web is view-only in v0 and must rely on Convex subscriptions for live updates.
- Convex is the system of record; all canonical state lives in Convex tables.
- Clerk is the only IdP. Clients call `users.upsertMe` after login.
- Storage is S3-compatible object storage with presigned PUT/GET from Convex.
- V0-api.md is the authoritative API contract (no raw PDF download in web v0).

## Prerequisites
- Milestone A completed (Android offline authoring).

## Goals
- Ship a view-only web app with TanStack Start + shadcn/ui + zod.
- Implement the core v0 API surface in Convex for notes/pages/assets/tiles/search/previews.
- Wire Clerk auth on web and ensure `users.upsertMe` is called after login.
- Render PDF and ink via tile rasters (no raw PDF download, no pdf.js).

## Non-Goals
- No web editing.
- No ops/collab, commits/timeline, sharing, exports, or public links yet.
- No external cache/kv.

## Deliverables
- Web app scaffold in `apps/web/` using TanStack Start + shadcn/ui.
- Convex schema and functions covering the in-scope v0 API domains.
- Tile-based PDF and ink rendering in web (view-only).
- Global search UI backed by Convex search indexes.
- Clerk auth wired on web; `users.upsertMe` called post-login.

## v0 API Scope (Milestone B)
In scope (implement now):
- Users: `users.upsertMe`, `users.me`
- Notes: `notes.listMine`, `notes.listSharedWithMe`, `notes.create`, `notes.rename`, `notes.delete`, `notes.get`
- Pages: `pages.createInkPage`, `pages.attachPdfPage`, `pages.get`
- Assets: `assets.presignUpload`, `assets.confirmUpload`, `assets.getDownloadUrl`
- Search: `search.upsertPageText`, `search.global`
- Tiles: `tiles.getManifest`, `tiles.getManifestBatch`, `tiles.upsertManifest`
- Previews: `previews.setFirstPagePreview`

Deferred to Milestone C:
- Ops/Collab: `ops.submitBatch`, `ops.listPageOps`, `ops.watchPageHead`
- Commits: `commits.*`
- Sharing/Public links: `shares.*`, `publicLinks.*`
- Exports/Public assets: `exports.*`, `publicAssets.getDownloadUrl`

## Definition of Done
- Web app renders notes list and note viewer for authenticated users.
- Tile-based PDF and ink rendering works for existing notes.
- Convex functions for in-scope APIs are deployed and callable.
- `users.upsertMe` is called on login for web.
- No raw PDF download on web.

## Task Flow (with acceptance criteria)

### B-1. Scaffold TanStack Start app
Create TanStack Start app in `apps/web/` with shadcn/ui + zod + Tailwind. Configure Turborepo integration (`package.json` scripts, `turbo.json` tasks for web build/dev/lint).
- **Done when**: `bun run dev` starts TanStack Start dev server. `bun run build` produces a production build. `bun run lint` and `bun run typecheck` pass for `@onyx/web`.

### B-2. Wire Clerk auth
Integrate Clerk on web. After login, call `users.upsertMe` Convex mutation. Store auth state in Convex client context. Protected routes redirect to login if unauthenticated.
- **Done when**: Login flow works end-to-end in dev. `users.upsertMe` creates/updates user record in Convex. Unauthenticated access to `/note/:id` redirects to login. Convex query auth context includes the authenticated user identity.

### B-3. Convex schema tables and indexes
Define Convex schema for: `users`, `notes`, `pages`, `assets`, `searchTexts`, `tileManifests`, `previews`. Add indexes: `notes.by_owner`, `notes.by_shared`, `pages.by_note`, `searchTexts.by_note` (search index), `tileManifests.by_page`.
- **Done when**: `npx convex dev` starts without schema errors. All tables and indexes defined. Schema matches the types in `V0-api.md` §1 (shared types). `convex/schema.ts` is the single source for all table definitions.

### B-4. Convex functions for in-scope APIs
Implement the functions listed in "v0 API Scope (Milestone B)" section above. Each function validates inputs with Convex validators, checks auth, and returns the documented types.
- **Done when**: Each function (`notes.listMine`, `notes.create`, `notes.rename`, `notes.delete`, `notes.get`, `pages.createInkPage`, `pages.attachPdfPage`, `pages.get`, `assets.presignUpload`, `assets.confirmUpload`, `assets.getDownloadUrl`, `search.upsertPageText`, `search.global`, `tiles.getManifest`, `tiles.getManifestBatch`, `tiles.upsertManifest`, `previews.setFirstPagePreview`) is callable from the Convex dashboard. Auth-required functions reject unauthenticated calls with `UNAUTHENTICATED`. Contract fixtures in `tests/contracts/fixtures/` cover at least: create-note roundtrip, search-upsert-then-query roundtrip.

### B-5. Web note list + note viewer routes
Routes: `/` (note list, grid layout), `/note/:noteId` (note viewer with page list), `/note/:noteId/:pageId` (direct page link). Note list shows note titles, thumbnails (from preview assets), and last-modified dates. Note viewer shows stacked pages with ink + PDF content.
- **Done when**: Note list loads via `notes.listMine` Convex query and renders note cards. Clicking a note navigates to the viewer. Viewer shows all pages from `notes.get` response. Empty states render gracefully (no notes, no pages). `bun run typecheck` passes.

### B-6. Tile-based PDF and ink rendering
PDF pages rendered from pre-generated tile rasters (no raw PDF download, no pdf.js). Ink strokes rendered as SVG or Canvas overlay on top of tile rasters. Tile rasters fetched via `tiles.getManifest` → `assets.getDownloadUrl`. Zoom uses higher-resolution tiles when available.
- **Done when**: A note with PDF pages and ink strokes renders correctly in the web viewer. Tiles load progressively (low-res first, high-res on zoom). No raw PDF binary is downloaded by the web client. `bun run build` succeeds.

### B-7. Search UI
Search bar in the top nav (matching UX Spec §2.8). Queries `search.global` Convex function. Results show note title, page number, snippet text. Clicking a result navigates to the specific page.
- **Done when**: Search returns results from `search.global`. Results are clickable and navigate to the correct note/page. Empty query shows no results. No-match query shows "No results found" state. `bun run typecheck` passes.

## Definition of Done (milestone gate)
- All B-1 through B-7 acceptance criteria met.
- `bun run lint`, `bun run typecheck`, `bun run build` pass for `@onyx/web`.
- `npx convex deploy` succeeds.
- Web app renders note list and viewer for authenticated users with tile-based rendering.
- No raw PDF download on web.
- Contract test fixtures in `tests/contracts/fixtures/` cover note CRUD and search roundtrips.

## Notes
- Web types derive from Convex generated types (no manual type duplication).
- Android models remain explicit Kotlin data classes with JSON serialization.
- Use Convex subscriptions for live updates; do not add a separate realtime system.
- All Convex error responses use the error codes defined in `V0-api.md` §5.
