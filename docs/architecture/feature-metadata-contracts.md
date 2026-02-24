# Feature Metadata Contracts (Gesture, Template, Export)

Date: 2026-02-24

## Scope

Defines cross-surface schema contracts for metadata required by backlog alignment:

- Gesture settings (`InputSettings` parity contract)
- Template application scope metadata
- Export metadata (`flattened` vs `layered`)

This wave is contract + fixture only. Runtime sync/query/UI flows remain separate.

## Canonical Sources

- Validation schemas: `C:/onyx/packages/validation/src/schemas/featureMetadata.ts`
- Convex schema tables in `C:/onyx/convex/schema.ts`:
  - `gestureSettings`
  - `templateScopes`
  - `exportMetadata`
- Contract fixtures:
  - `C:/onyx/tests/contracts/fixtures/gesture-settings.fixture.json`
  - `C:/onyx/tests/contracts/fixtures/template-scope.fixture.json`
  - `C:/onyx/tests/contracts/fixtures/export-metadata.fixture.json`

## Notes

- Gesture enum values intentionally mirror Android `InputSettings`.
- Template scope supports `currentPage`, `allPages`, and `newPages`.
- Export mode explicitly carries `flattened | layered` for downstream consumers.
