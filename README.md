# Onyx (rewrite)

v0 focuses on an offline-first Android authoring client and a view-only web client. Convex is the system of record and realtime sync layer. Blob storage is raw objects only.

## Repo layout
- `apps/android` Android authoring app (Kotlin + Room + Convex client)
- `apps/web` View-only web app (TanStack Start + shadcn/ui + zod)
- `convex` Convex schema, functions, jobs, and migrations
- `packages` Shared libraries (contracts, validation, ui, config, etc.)
- `docs` Architecture, sync, storage, testing, and runbooks
- `infra` Observability, storage, and backup notes (v0, no IaC yet)
- `tests` Contract fixtures and cross-cutting tests

## v0 architecture (summary)
- Identity: Clerk only. Clients call `users.upsertMe` after login. Device identity is explicit (`deviceId`) and used in Lamport ordering.
- System of record: Convex stores all canonical state (notes, pages, ops, commits, shares, public links, assets metadata, exports metadata, tiles/previews).
- Realtime & sync: Convex queries/mutations/actions only. Offline queue on Android with deterministic replay.
- Storage: S3-compatible object storage for raw blobs; Convex issues presigned URLs and tracks metadata.
- Background jobs: Convex scheduled functions for snapshots, previews, exports, retention cleanup.
- Observability: BetterStack; correlation IDs passed from clients to Convex logs.

## Status
This repo is a structural scaffold for the rewrite. No production code yet.

## Tooling (planned)
- Turborepo + Bun workspaces
- TypeScript, zod, TanStack Start, shadcn/ui
- Playwright E2E
- Linting: `oxlint` + `tsgo` for quick checks, `eslint` for full lint (pre-commit)
- Formatting: Prettier
- Android: ktlint + detekt

## Commands
JS/Convex/Web (excludes Android by default):
- `bun run lint`
- `bun run typecheck`
- `bun run test`
- `bun run build`
- `bun run e2e`

Android:
- `bun run android:build`
- `bun run android:test`
- `bun run android:lint`
- `bun run ktlint`
- `bun run detekt`
