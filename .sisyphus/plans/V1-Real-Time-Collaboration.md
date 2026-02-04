# V1 Real-Time Collaboration

This file captures high-level real-time and collaboration behaviors removed from earlier drafts, aligned to the current repo (Clerk + Convex + TanStack Start, S3-compatible storage). It is reference-only; implementation belongs to Milestone C.

## Realtime Model
- Realtime is Convex subscriptions only (no separate websocket layer).
- Core endpoints: `ops.submitBatch`, `ops.listPageOps`, `ops.watchPageHead`.
- Clients call `users.upsertMe` after login.

## Sync & Ordering
- Operations are ordered by `(lamport, deviceId, opId)`.
- Android is offline-first: local writes → op queue → deterministic replay on reconnect.
- Device identity is explicit and used in ordering.

## Snapshot/Commit Flow
- Commits are created via `commits.create` and listed via `commits.list`.
- Clients load latest snapshot, then backfill ops after the snapshot cursor.
- Scheduled functions create snapshots on cadence and drive preview updates.

## Collaboration Scope (v1)
- Multi-user editing on the same page with near-real-time convergence.
- Conflict resolution is deterministic via the Lamport/device/op tuple.
- Web remains view-only in v0, but subscribes to page head updates for live refresh.

## Sharing & Public Links
- Share to existing users by email.
- Public links are unguessable, view-only, and resolved in Convex.
- Public assets served via presigned GET.

## Rendering (Web)
- Web renders from pre-generated tile rasters (PDF + ink) and previews.
- No raw PDF download and no pdf.js in v0.

## Data & Types
- Convex schema is the source of truth.
- Web types derive from Convex generated types.
- Android uses explicit Kotlin data classes with JSON serialization.
- Contract tests use JSON fixtures and tolerate additive changes via `ignoreUnknownKeys` and defaults.
- OpenAPI/codegen is intentionally not chosen (keep Kotlin models explicit).

## Background Jobs (Convex)
- Snapshot cadence, preview generation, exports, retention cleanup.
