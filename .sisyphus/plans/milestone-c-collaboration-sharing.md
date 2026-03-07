# Milestone C: Collaboration + Sharing (v0)

## Context
- Convex remains the system of record for all user-visible state.
- Realtime is via Convex subscriptions only.
- Clerk is the only IdP; clients call `users.upsertMe` after login.
- Android is authoring; web is view-only in v0.
- Storage is S3-compatible object storage with presigned PUT/GET from Convex.
- V0-api.md is the authoritative API contract.

## Prerequisites
- Milestone A and B completed.

## Goals
- Implement ops sync, commits, sharing, public links, and exports per V0-api.md.
- Add background jobs for snapshot cadence, preview generation, exports, retention cleanup.
- Wire contract tests with JSON fixtures to prevent schema drift.

## Non-Goals
- No web editing UI.
- No external cache/kv.

## Scope (v0 API)
Ops/Collab:
- `ops.submitBatch`
- `ops.listPageOps`
- `ops.watchPageHead`

Commits/Timeline:
- `commits.create`
- `commits.list`
- `commits.get`
- `commits.restore`

Sharing/Public:
- `shares.grantByEmail`
- `shares.revoke`
- `shares.list`
- `publicLinks.create`
- `publicLinks.disable`
- `publicLinks.resolve`

Exports:
- `exports.register`
- `exports.getLatest`
- `publicAssets.getDownloadUrl`

## Definition of Done
- Android can sync ops with deterministic replay and conflict resolution by `(lamport, deviceId, opId)`.
- Web can subscribe to page head updates and render updates from tiles/previews.
- Sharing and public links function per V0-api.md.
- Exports are recorded and downloadable via presigned URLs.
- Contract tests exist with JSON fixtures and tolerate additive schema changes via `ignoreUnknownKeys` and defaults.

## Task Flow (with acceptance criteria)

### C-1. Extend Convex schema for ops, commits, shares, public links, exports
Add tables: `ops`, `commits`, `shares`, `publicLinks`, `exports`. Add indexes: `ops.by_page_lamport` (pageId + lamport for cursor-based paging), `commits.by_page`, `shares.by_note`, `publicLinks.by_linkId`, `exports.by_note`.
- **Done when**: `npx convex dev` starts without schema errors. All new tables match `V0-api.md` §1 types. Existing Milestone B tables unmodified.

### C-2. Ops endpoints with cursor-based paging
Implement `ops.submitBatch`, `ops.listPageOps`, `ops.watchPageHead` per V0-api.md §3.4. `submitBatch` validates Lamport ordering (reject if op.lamport ≤ current page contentLamportMax). `listPageOps` supports `afterLamport` cursor with configurable `limit` (default 100, max 500). `watchPageHead` is a reactive query returning `contentLamportMax`.
- **Done when**: `ops.submitBatch` with 5 ops returns `{accepted: 5, newContentLamportMax: N}`. `ops.listPageOps` with `afterLamport=0` returns all ops in Lamport order. `ops.listPageOps` with `afterLamport=3` returns only ops with lamport >3. `ops.submitBatch` with out-of-order Lamport throws `CONFLICT`. Contract fixture covers submit-then-list roundtrip.

### C-3. Commits (snapshots/restore points)
Implement `commits.create`, `commits.list`, `commits.get`, `commits.restore` per V0-api.md §3.5. `commits.create` requires a valid `snapshotAssetId` (asset must exist and be type `snapshot`). `commits.restore` creates a new commit at head with the restored snapshot.
- **Done when**: Create commit → list commits returns it. Restore from older commit creates new head commit. `commits.get` returns correct entry. Contract fixture covers create-restore roundtrip.

### C-4. Sharing and public links
Implement `shares.grantByEmail`, `shares.revoke`, `shares.list`, `publicLinks.create`, `publicLinks.disable`, `publicLinks.resolve` per V0-api.md §3.3. `grantByEmail` looks up user by email (lowercased); throws `EMAIL_NOT_FOUND` if user doesn’t exist. `publicLinks.resolve` is unauthenticated and returns view-only note data; throws `PUBLIC_LINK_DISABLED` if revoked.
- **Done when**: Grant share → grantee sees note in `notes.listSharedWithMe`. Revoke share → grantee no longer sees it. Create public link → resolve returns note. Disable link → resolve throws `PUBLIC_LINK_DISABLED`. Auth tests: non-owner/editor cannot grant shares. Contract fixtures cover share grant-revoke and public link create-resolve-disable cycles.

### C-5. Exports and public asset download
Implement `exports.register`, `exports.getLatest`, `publicAssets.getDownloadUrl` per V0-api.md §3.6/§3.9. Export registration requires a valid `exportAssetId`. Public asset download validates the `linkId` is active before minting a presigned GET URL.
- **Done when**: Register export → getLatest returns it. Public asset download with valid linkId returns a presigned URL. Public asset download with disabled linkId throws `PUBLIC_LINK_DISABLED`. Contract fixture covers register-then-getLatest.

### C-6. Scheduled Convex jobs
Implement scheduled functions for:
- **Snapshot cadence**: Create commits for active pages on schedule (every 5s for shared notes, 20s for private). Skip if no new ops since last commit.
- **Preview generation**: Debounced first-page preview update (120s per note, as enforced by `previews.setFirstPagePreview`).
- **Retention cleanup**: Delete ops older than the latest commit's baseLamport (keep ops only above the snapshot watermark). Delete soft-deleted notes older than 20 days.
- **Done when**: Snapshot job creates commit only when `contentLamportMax` > last commit's `baseLamport`. Retention job removes ops below watermark without affecting ops above it. Preview debounce rejects updates within 120s window. Each job has a unit test with a mocked Convex context.

### C-7. Android sync integration
Wire the Android app to use Convex client for sync:
- On note open: fetch latest commit (snapshot), apply ops after `baseLamport`, subscribe to `ops.watchPageHead`.
- On stroke commit: queue `StrokeAdd` op locally, flush via `ops.submitBatch` when online.
- Offline queue: `OperationLogEntity` + `OperationLogDao` (Room) stores pending ops. WorkManager drains queue in Lamport order on connectivity restore.
- Conflict handling: if `ops.submitBatch` returns `CONFLICT` (stale Lamport), fetch remote ops, rebase local queue, retry.
- **Done when**: Unit test simulates: go offline → commit 3 strokes → go online → verify `ops.submitBatch` called with 3 ops in Lamport order. Integration test (with mock Convex): open note → receive remote op via subscription → verify stroke appears in local page model. `bun run android:lint` passes.

### C-8. Web live subscriptions
Web viewer subscribes to `ops.watchPageHead` for the currently viewed page. When `contentLamportMax` changes, fetch new ops via `ops.listPageOps` and update the rendered page.
- **Done when**: Open note in web viewer → submit op via Convex dashboard → viewer updates within 2s without manual refresh. `bun run typecheck` passes.

### C-9. Contract tests with JSON fixtures
Add contract tests in `packages/contracts/` with JSON fixtures in `tests/contracts/fixtures/` for:
- Op submission and listing roundtrip
- Commit create/restore roundtrip
- Share grant/revoke/list roundtrip
- Public link create/resolve/disable roundtrip
- Export register/getLatest roundtrip
Tests validate response shapes against zod schemas from `packages/validation/`. Tests use `ignoreUnknownKeys` and default values to tolerate additive schema changes.
- **Done when**: `bun run test` passes with all contract tests green. Fixtures are committed under `tests/contracts/fixtures/`. Adding a new optional field to a Convex table does not break existing contract tests.

## Definition of Done (milestone gate)
- All C-1 through C-9 acceptance criteria met.
- Android can sync ops with deterministic replay and conflict resolution by `(lamport, deviceId, opId)`.
- Web subscribes to page head updates and renders remote changes.
- Sharing and public links function per V0-api.md.
- Exports are recorded and downloadable via presigned URLs.
- Contract tests exist with JSON fixtures.
- `bun run lint`, `bun run typecheck`, `bun run test` pass.
- `bun run android:lint` passes.
- `npx convex deploy` succeeds.

## Notes
- Web types derive from Convex generated types.
- Android uses Kotlin data classes with JSON serialization.
- OpenAPI/codegen is intentionally not chosen; Kotlin models stay explicit.
- Sync ordering is deterministic: `(lamport, deviceId, opId)`. No last-write-wins.
- `AreaErase` resolves at replay time against strokes where `createdLamport ≤ eraseLamport`.
