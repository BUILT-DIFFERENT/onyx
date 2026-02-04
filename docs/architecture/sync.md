# Realtime & Sync

All operations are Convex queries/mutations/actions.

Planned endpoints:
- `ops.submitBatch`
- `ops.listPageOps`
- `ops.watchPageHead`
- `commits.create`
- `commits.list`

Conflict rules:
- Ordered by `(lamport, deviceId, opId)`
- Android offline queue with deterministic replay on reconnect

## Realtime clients and types
- Realtime subscriptions are required; we use the Convex Android client and keep Kotlin models explicit.
- Convex schema is the source of truth.
- Web types can derive from Convex generated types.
- Android uses Kotlin data classes with JSON serialization.
- Contract tests use JSON fixtures and tolerate additive schema changes via `ignoreUnknownKeys` and defaults.
- OpenAPI/codegen is intentionally not chosen because it removes explicit Kotlin models.
