# Feature Metadata Contracts

Date: 2026-03-07 (updated)

## Scope

Documents the contract decisions for metadata features that were evaluated during architecture alignment.

## Resolution

- **Gesture settings**: Local-only. Stored in Android DataStore/Room. No Convex table, no cross-device sync. No validation schema (local implementation detail).
- **Template settings**: Per-page fields (`templateType`, `templateDensity`, `templateLineWidth`, `backgroundColorHex`) stored on the `pages` table in Convex and in the Page data model. No separate `templateScopes` table. Template defaults are an Android-local editor setting.
- **Export tracking**: The `exports` table in `convex/schema.ts` stores export registration (`notebookId`, `exportAssetId`, `mode`, `createdByUserId`, `createdAt`). Validation schema: `packages/validation/src/schemas/export.ts`. Fixture: `tests/contracts/fixtures/export.fixture.json`.

## Canonical Sources

- Convex schema: `convex/schema.ts` (tables: `pages`, `exports`)
- Validation schemas: `packages/validation/src/schemas/export.ts`
- Contract fixtures: `tests/contracts/fixtures/export.fixture.json`
