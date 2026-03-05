# System Overview

Onyx is a monorepo with an Android-first product surface and shared contract packages.

## Surfaces

- `apps/android`: primary note editor/runtime (Kotlin, Compose, Room, MyScript, Pdfium).
- `apps/web`: web scaffold for viewer/search-adjacent surfaces (TanStack Start + React).
- `convex`: backend schema/functions scaffold.

## Shared Packages

- `packages/validation`: canonical runtime/contract schemas.
- `packages/contracts`: fixture-driven contract tests.
- `packages/shared`, `packages/ui`, `packages/config`, `packages/test-utils`: shared workspace libraries.

## Build and Task Orchestration

- Workspace package manager/runtime: Bun.
- Monorepo task graph/cache: Turborepo (`turbo.json`).
- Android Gradle helper: `scripts/gradlew.js` (run from `apps/android/` or with that cwd).

## Data/Contract Source of Truth

- API/schema contracts: `packages/validation/src/schemas/*`.
- Contract fixtures: `tests/contracts/fixtures/*`.
- Android persistence is implementation detail and may evolve independently during greenfield waves.
