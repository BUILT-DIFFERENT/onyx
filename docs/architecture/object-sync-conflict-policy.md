# Object Sync Conflict Policy (Contract Scaffold)

Date: 2026-02-24

## Scope

This document defines contract-level conflict metadata for page objects. It is intentionally schema-only in this wave; runtime sync/reconciliation engines are deferred.

## Canonical Sources

- Validation schema: `C:/onyx/packages/validation/src/schemas/pageObject.ts` (`sync` field)
- Convex schema: `C:/onyx/convex/schema.ts` (`pageObjects.sync`)
- Fixture coverage: `C:/onyx/tests/contracts/fixtures/page-object-shape-conflict.fixture.json`

## Metadata Fields

Optional `sync` object per page object:

- `objectRevision`: current logical revision for the object
- `parentRevision?`: revision ancestry pointer used for conflict detection
- `lastMutationId`: deterministic mutation identifier (client-scoped)
- `conflictPolicy`: `lastWriteWins | manualResolve`

## Contract-Level Resolution Rules

For equal `objectId` writes from different sources:

1. If either side has higher `objectRevision`, higher revision wins.
2. If revisions tie, compare `updatedAt`; newer wins.
3. If both tie, apply deterministic winner by lexical compare of `lastMutationId`.

Policy handling:

- `lastWriteWins`: auto-resolve via rule ordering above.
- `manualResolve`: preserve both branches in conflict handling pipeline (runtime deferred), but do not drop either update at decode time.

## Deferred Runtime Work

- Persisting and incrementing revision metadata from Android edit operations.
- Convex mutation/query APIs that apply the policy live.
- Web conflict-UI affordances for `manualResolve` branches.
