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

## Task Flow (Trimmed)
1. Extend Convex schema for ops, commits, shares, public links, exports, and derived indexes.
2. Implement ops endpoints and cursor-based paging as specified in V0-api.md.
3. Implement commit creation/list/get/restore.
4. Implement sharing and public link endpoints.
5. Implement exports and public asset download endpoints.
6. Add scheduled Convex jobs for snapshots, previews, exports, and retention cleanup.
7. Wire Android sync to use ops.submitBatch and commits.list, using Convex Android client.
8. Add web subscriptions for page head updates.
9. Add contract tests with JSON fixtures to prevent schema drift.

## Notes
- Web types should derive from Convex generated types.
- Android uses Kotlin data classes with JSON serialization.
- OpenAPI/codegen is intentionally not chosen; Kotlin models stay explicit.
