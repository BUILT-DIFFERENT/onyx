# Schema Audit

Canonical contract schemas for this repo are defined in:

- `packages/validation/src/schemas/*`
- `tests/contracts/fixtures/*`

This repository currently treats Android Room entities as implementation detail, not contract authority.

## Usage

- Add or update schema definitions in `packages/validation` first.
- Keep fixtures in `tests/contracts/fixtures` aligned with schema changes.
- Validate via:

```bash
bun run test --filter=@onyx/validation
bun run test --filter=@onyx/contracts
```
