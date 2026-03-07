# V1 Real-Time Collaboration

This file captures high-level real-time and collaboration behaviors, aligned to the current architecture (Clerk + Convex + TanStack Start, Cloudflare R2, Y-Octo/Yjs CRDTs). It is reference-only; implementation belongs to Milestone C.

## Realtime Model
- Realtime is Convex reactive subscriptions only (no separate WebSocket layer).
- Core sync endpoints: `sync.pushUpdates`, `sync.pullUpdates`, `sync.watchHead`, `sync.recordSnapshot`.
- Clients call `users.upsertMe` after login.

## Sync & Conflict Resolution
- **Y-Octo/Yjs CRDTs**: Android uses Y-Octo (Rust/JNI), web uses Yjs (JS). Both produce the same binary CRDT update format.
- **Two-level CRDT documents**: notebook-level Yjs doc (page ordering + metadata) + per-page Yjs doc (strokes, objects, deletedStrokes, deletedObjects).
- Conflict resolution is **automatic via CRDT merge semantics**. No Lamport clocks, no deterministic replay ordering, no manual conflict handling.
- Android is offline-first: all writes go to local Y-Octo doc first → Yjs update binary queued in Room `OfflineQueueEntity` → flushed via `sync.pushUpdates` when online.
- On reconnect after offline: WorkManager drains queue in insertion order. Remote updates fetched via `sync.pullUpdates` and merged automatically by Y-Octo.

## Snapshot Flow
- Snapshots are full Yjs doc state uploads to R2 (via presigned PUT).
- After upload, client calls `sync.recordSnapshot` to record the snapshot URL and state vector in Convex.
- Snapshot cadence is debounced from last edit: 5s for shared/public notebooks, 20s for private notebooks.
- `sync.pullUpdates` returns only CRDT deltas newer than the client's state vector. Clients fetch snapshots from R2 when opening a notebook for the first time or when catching up after a long offline period.
- Background job: `crdtUpdates` entries below the latest snapshot's state vector are eligible for cleanup (retention job runs every 10 minutes).

## Collaboration Scope (v1)
- Max 3 concurrent editors per notebook.
- Multi-user editing on the same page with automatic CRDT convergence.
- **Presence**: each active editor pushes cursor position + active tool to Convex `presence` table every 500ms while drawing; stops after 5s idle. Viewers subscribe to `presence.watch` for the notebook to display collaborator cursors. Entries auto-expire after 10s of no updates.
- Web remains view-only in v1, but subscribes to `sync.watchHead` for live page refresh.

## Sharing & Public Links
- Share to existing users by email (`shares.grantByEmail`). Roles: viewer, editor.
- Public links are unguessable (high-entropy `linkToken`), view-only, and resolved via `publicLinks.resolve` (unauthenticated).
- Public assets served via presigned GET (`publicAssets.getDownloadUrl`).

## Rendering (Web)
- PDF.js fetches original PDF from R2 presigned URL and renders to canvas layer.
- Ink strokes rendered as Canvas overlay, tessellated from raw point arrays in the Yjs doc.
- TextBlocks rendered as KaTeX (for LaTeX) or plain text.
- Images, StickyNotes, Tables, Shapes rendered from Yjs doc `objects` map.
- Live updates: subscribe to `sync.watchHead` → pull new Yjs deltas → apply to local Yjs doc → re-render.

## Data & Types
- Convex schema (`convex/schema.ts`) is the source of truth for all server-side tables.
- Web types derive from Convex generated types.
- Android uses explicit Kotlin data classes with JSON serialization for metadata. Y-Octo handles CRDT binary operations natively via JNI.
- Contract tests use JSON fixtures and tolerate additive changes via `ignoreUnknownKeys` and defaults.
- OpenAPI/codegen is intentionally not chosen (keep Kotlin models explicit).

## Background Jobs (Convex)
- CRDT delta retention (clean up `crdtUpdates` below snapshot state vector).
- Trash cleanup (permanently delete notebooks with `deletedAt` > 20 days, cascade to all related tables).
- Presence expiry (handled inline by `presence.watch` query filter; optional periodic cleanup job).
