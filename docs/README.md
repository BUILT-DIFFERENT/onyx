# Docs

Architecture and runbooks for Onyx v0.

## Architecture
- `docs/architecture/system-overview.md`
- `docs/architecture/data-model.md`
- `docs/architecture/storage.md`
- `docs/architecture/sync.md`
- `docs/architecture/identity.md`
- `docs/architecture/testing.md`
- `docs/architecture/observability.md`
- `docs/architecture/migrations-backups.md`
- `docs/architecture/ci-cd.md`

## Android Remediation Tracking
- `docs/architecture/android-remediation-pr2.md`
- `docs/architecture/android-remediation-pr3.md`

## Branch Review and Planning
- `docs/architecture/branch-architecture-analysis.md`

- [`system-overview.md`](architecture/system-overview.md) — System topology and client roles
- [`data-model.md`](architecture/data-model.md) — Convex tables and canonical data model
- [`sync.md`](architecture/sync.md) — Realtime sync, Lamport ordering, offline queue
- [`storage.md`](architecture/storage.md) — Blob storage and presigned URLs
- [`identity.md`](architecture/identity.md) — Clerk auth and device identity
- [`ci-cd.md`](architecture/ci-cd.md) — GitHub Actions pipeline
- [`testing.md`](architecture/testing.md) — Test strategy (unit, instrumentation, E2E, contract)
- [`observability.md`](architecture/observability.md) — Logging and monitoring
- [`migrations-backups.md`](architecture/migrations-backups.md) — Schema migrations and backup policy
- [`full-project-analysis.md`](architecture/full-project-analysis.md) — Complete project analysis with prioritised change list

## Android Remediation Tracking

- [`android-remediation-pr2.md`](architecture/android-remediation-pr2.md) — PR2: UX gaps (title editing, deletion, page counter, read-only mode)
- [`android-remediation-pr3.md`](architecture/android-remediation-pr3.md) — PR3: Rendering and performance (PDF caching, hot-path allocations, retry logging)

## Rendering Analysis

- [`rendering-discrepancies.md`](architecture/rendering-discrepancies.md) — Detailed gap analysis: Onyx vs Notewise (stroke rendering, performance, visual artifacts, document handling, UI)

## Other

- [`schema-audit.md`](schema-audit.md) — Android Room ↔ V0 API sync compatibility audit
- [`device-blocker.md`](device-blocker.md) — Tasks blocked pending physical device verification
