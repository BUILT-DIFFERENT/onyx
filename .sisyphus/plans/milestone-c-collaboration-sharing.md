# Milestone C: Collaboration + Sharing (v0)

## Context
- Convex stores metadata, CRDT update deltas, and asset references. Yjs docs (R2 snapshots + Convex deltas) are the authoritative page content store.
- Sync uses Y-Octo/Yjs CRDTs. Android uses Y-Octo (Rust/JNI); web uses Yjs (JS). Both produce the same binary CRDT update format.
- Realtime notifications are via Convex reactive subscriptions (`sync.watchHead`).
- Conflict resolution is automatic via CRDT merge semantics — no Lamport clocks, no manual conflict handling.
- Clerk is the only IdP; clients call `users.upsertMe` after login.
- Android is authoring; web is view-only in v1.
- Storage: Cloudflare R2 for large blobs. Convex holds R2 key + size + hash.
- `V0-api.md` is the authoritative API contract.

## Prerequisites
- Milestones A and B completed.

## Goals
- Implement CRDT sync endpoints (`sync.pushUpdates`, `sync.pullUpdates`, `sync.watchHead`, `sync.recordSnapshot`).
- Implement sharing, public links, presence, and exports per V0-api.md.
- Add background jobs for snapshot retention, trash cleanup, and presence expiry.
- Wire Android sync integration with Y-Octo and WorkManager.
- Wire web live subscriptions for real-time page updates.
- Add contract tests with JSON fixtures to prevent schema drift.

## Non-Goals
- No web editing UI.
- No external cache/kv.

## Scope (v0 API)
CRDT Sync:
- `sync.pushUpdates`
- `sync.pullUpdates`
- `sync.watchHead`
- `sync.recordSnapshot`

Sharing/Public:
- `shares.grantByEmail`
- `shares.revoke`
- `shares.list`
- `publicLinks.create`
- `publicLinks.disable`
- `publicLinks.resolve`

Presence:
- `presence.update`
- `presence.watch`

Exports:
- `exports.register`
- `exports.getLatest`
- `publicAssets.getDownloadUrl`

## Definition of Done
- Android can sync page content via Y-Octo CRDT: push local Yjs updates, pull remote updates, merge automatically.
- Web can subscribe to `sync.watchHead` and re-render pages when new updates arrive.
- Sharing and public links function per V0-api.md.
- Presence cursors visible to collaborators (max 3 concurrent editors per notebook).
- Exports are recorded and downloadable via presigned URLs.
- Contract tests exist with JSON fixtures.

## Task Flow (with acceptance criteria)

### C-1. Implement CRDT sync endpoints in Convex
Implement `sync.pushUpdates`, `sync.pullUpdates`, `sync.watchHead`, `sync.recordSnapshot` per V0-api.md §3.5.

> Note: `sync.recordSnapshot` was implemented in Milestone B for snapshot bootstrap. Milestone C may enhance it (e.g., triggering delta cleanup) but does not need to reimplement the core mutation.

`sync.pushUpdates`: accepts array of base64-encoded Yjs update binaries (<10 KB each). Stores each as a row in `crdtUpdates` table. Returns `{ accepted: number }`. Rejects updates >10 KB with `PAYLOAD_TOO_LARGE`.

`sync.pullUpdates`: accepts a base64-encoded Yjs state vector plus optional pagination cursor. Returns `crdtUpdates` rows for the (notebookId, pageId) pair newer than the client's known state. Supports `limit` parameter (default 100). Returns `{ updates, hasMore, nextCursor? }`.

`sync.watchHead`: reactive query returning `{ latestUpdateAt, updateCount }` for a (notebookId, pageId) pair. Clients subscribe to this to detect when new updates are available.

`sync.recordSnapshot`: records that a snapshot was uploaded to R2. Updates `notebooks.snapshotUrl` / `notebooks.snapshotStateVector` (for notebook-level) or `pages.pageSnapshotUrl` / `pages.pageSnapshotStateVector` (for per-page). Does not store the snapshot binary in Convex.

- **Done when**: `sync.pushUpdates` with 3 updates returns `{ accepted: 3 }`. `sync.pullUpdates` with an empty state vector returns all updates across pagination (cursor loop). `sync.pullUpdates` with a recent state vector returns only new updates. `sync.watchHead` reactive query updates when new pushes arrive. `sync.recordSnapshot` updates the snapshot URL on the notebook/page record. `sync.pushUpdates` with >10 KB payload throws `PAYLOAD_TOO_LARGE`. Contract fixture covers push → pull roundtrip.

### C-2. Sharing and public links
Implement `shares.grantByEmail`, `shares.revoke`, `shares.list`, `publicLinks.create`, `publicLinks.disable`, `publicLinks.resolve` per V0-api.md §3.6.

`grantByEmail` looks up user by email (lowercased); throws `EMAIL_NOT_FOUND` if user doesn't exist. Updates `notebooks.shareCount` denormalized field.

`publicLinks.resolve` is unauthenticated and returns view-only notebook data; throws `PUBLIC_LINK_DISABLED` if revoked. Updates `notebooks.hasPublicLink` denormalized field on create/disable.

- **Done when**: Grant share → grantee sees notebook in `notebooks.listSharedWithMe`. Revoke share → grantee no longer sees it. Create public link → resolve returns notebook + pages. Disable link → resolve throws `PUBLIC_LINK_DISABLED`. Auth tests: non-owner/editor cannot grant shares. Contract fixtures cover share grant-revoke and public link create-resolve-disable cycles.

### C-3. Presence
Implement `presence.update`, `presence.watch` per V0-api.md §3.8.

`presence.update`: upserts a row in `presence` table for (userId, notebookId). Called every 500ms while drawing; stops after 5s idle.

`presence.watch`: reactive query returning all `presence` entries for a notebookId where `updatedAt` is within the last 10 seconds (auto-expiry via query filter).

Max 3 concurrent editors per notebook. `sync.pushUpdates` should check active editor count and reject with `FORBIDDEN` if exceeded.

- **Done when**: User A draws → presence entry created. User B subscribes to `presence.watch` → sees User A's cursor position. User A stops drawing for >10s → User B's `presence.watch` no longer includes User A. Editor count limit test: 4th editor's `sync.pushUpdates` returns `FORBIDDEN`.

### C-4. Exports and public asset download
Implement `exports.register`, `exports.getLatest`, `publicAssets.getDownloadUrl` per V0-api.md §3.7/§3.11.

Export registration requires a valid `exportAssetId` (confirmed upload). Public asset download validates the `linkToken` is active before minting a presigned GET URL.

- **Done when**: Register export → `exports.getLatest` returns it. Public asset download with valid linkToken returns a presigned URL. Public asset download with disabled linkToken throws `PUBLIC_LINK_DISABLED`. Contract fixture covers register-then-getLatest.

### C-5. Scheduled Convex jobs
Implement scheduled functions for:

- **CRDT delta retention**: Delete `crdtUpdates` entries covered by the latest recorded snapshot for a (notebookId, pageId) scope. Run every 10 minutes. Use a conservative cutoff (snapshot-record time) and keep a safety tail of recent updates. Skip if snapshot metadata is missing/inconsistent for the scope.
- **Trash cleanup**: Permanently delete notebooks where `deletedAt` is older than 20 days. Delete associated R2 assets (pages, snapshots, crdtUpdates, shares, publicLinks, exports, searchTexts).
- **Presence expiry**: Handled inline by `presence.watch` query filter (no separate job needed). Optionally, a periodic job can clean up stale rows to reduce table size.

- **Done when**: CRDT retention job deletes eligible old updates without affecting recent updates for the same scope. Trash cleanup job permanently removes expired notebooks and cascades to all related tables. Each job has a unit test with a mocked Convex context.

### C-6. Android CRDT sync integration
Wire the Android app to use Y-Octo for local CRDT operations and Convex for remote sync:

- On notebook open: load local Yjs binary file → fetch R2 snapshot if local is stale → `sync.pullUpdates(stateVector)` → apply updates to local Y-Octo doc → persist updated binary file → subscribe to `sync.watchHead`.
- On stroke commit / object mutation: apply to local Y-Octo doc → extract Yjs update binary → queue in Room `OfflineQueueEntity` → if online, flush immediately via `sync.pushUpdates`.
- Offline queue: `OfflineQueueEntity` + `OfflineQueueDao` stores pending Yjs update binaries. WorkManager drains queue in insertion order on connectivity restore via `sync.pushUpdates`.
- Snapshot cadence: WorkManager schedules R2 snapshot upload debounced from last edit — 5s (shared/public), 20s (private). Uploads full Yjs state to R2 → `sync.recordSnapshot`.
- Conflict resolution: automatic via Y-Octo CRDT merge. No manual handling needed. If `sync.pushUpdates` fails (network error), updates stay in offline queue and retry.

- **Done when**: Unit test simulates: go offline → commit 3 strokes → go online → verify `sync.pushUpdates` called with 3 Yjs updates in order. Integration test (with mock Convex): open notebook → receive remote update via `sync.watchHead` subscription → verify strokes from remote update appear in local Yjs doc. `bun run android:lint` passes.

### C-7. Web live CRDT subscriptions
Web viewer subscribes to `sync.watchHead` for the currently viewed notebook/page. When `latestUpdateAt` changes, pull new Yjs deltas via paginated `sync.pullUpdates` (cursor loop), apply to local Yjs doc, and re-render affected strokes/objects on the Canvas overlay.

- **Done when**: Open notebook in web viewer → push a Yjs update via Convex dashboard → viewer updates within 2s without manual refresh. PDF background remains stable during re-render. `bun run typecheck` passes.

### C-8. Contract tests with JSON fixtures
Add contract tests in `packages/contracts/` with JSON fixtures in `tests/contracts/fixtures/` for:
- CRDT sync push and pull roundtrip
- Share grant/revoke/list roundtrip
- Public link create/resolve/disable roundtrip
- Export register/getLatest roundtrip
- Presence update/watch roundtrip

Tests validate response shapes against zod schemas from `packages/validation/`. Tests use `ignoreUnknownKeys` and default values to tolerate additive schema changes.

- **Done when**: `bun run test` passes with all contract tests green. Fixtures are committed under `tests/contracts/fixtures/`. Adding a new optional field to a Convex table does not break existing contract tests.

## Definition of Done (milestone gate)
- All C-1 through C-8 acceptance criteria met.
- Android can sync notebook content via Y-Octo CRDT with automatic conflict resolution.
- Web subscribes to `sync.watchHead` and renders remote changes via Yjs replay.
- Sharing and public links function per V0-api.md.
- Presence cursors work for up to 3 concurrent editors.
- Exports are recorded and downloadable via presigned URLs.
- Contract tests exist with JSON fixtures.
- `bun run lint`, `bun run typecheck`, `bun run test` pass.
- `bun run android:lint` passes.
- `npx convex deploy` succeeds.

## Notes
- Web types derive from Convex generated types.
- Android uses Kotlin data classes with JSON serialization for metadata. Y-Octo handles CRDT binary operations natively via JNI.
- OpenAPI/codegen is intentionally not chosen; Kotlin models stay explicit.
- Sync is CRDT-based: Y-Octo/Yjs binary updates pushed/pulled through Convex. Conflict resolution is automatic via CRDT merge semantics.
- Room is a local metadata/settings cache on Android. Yjs binary files are the authoritative content store.
