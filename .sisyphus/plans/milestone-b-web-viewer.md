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

## Task Flow (Trimmed)
1. Scaffold TanStack Start app in `apps/web/` with shadcn/ui + zod.
2. Add Clerk auth to web and call `users.upsertMe` after login.
3. Implement Convex schema tables and indexes for in-scope domains.
4. Implement Convex functions for in-scope APIs.
5. Build web note list + note viewer routes.
6. Implement tile-based PDF and ink rendering (view-only).
7. Implement search UI using Convex queries.

## Notes
- Web types should derive from Convex generated types.
- Android models remain explicit Kotlin data classes with JSON serialization.
- Use Convex subscriptions for live updates; do not add a separate realtime system.
