# Object Sync — CRDT Model

Date: 2026-03-07 (updated)

## Scope

Documents the sync/conflict resolution model for page objects.

## Resolution

All page objects (TextBlock, Image, StickyNote, Table, AudioAttachment, Shape) live inside per-page Yjs docs as entries in the `objects: Y.Map<objectId, ObjectData>` structure.

Conflict resolution is **automatic via CRDT merge semantics**. There is no per-object `objectRevision`, `conflictPolicy`, or `lastMutationId`. The old revision-based conflict model was removed when the architecture shifted from Lamport clocks to Y-Octo/Yjs CRDTs.

- **Concurrent edits to different objects on the same page**: merge automatically (no conflict).
- **Concurrent edits to the same object from different devices**: Yjs Map last-writer-wins at the field level (each field is independently mergeable).
- **Deletions**: tombstoned in `deletedObjects: Y.Map<objectId, { deletedAt }>` inside the Yjs doc.

## Canonical Sources

- CRDT doc structure: `PLAN.md` §8.1, `V0-api.md` §0 (Sync Model)
- Validation schema: `packages/validation/src/schemas/pageObject.ts` (no `sync` field)
