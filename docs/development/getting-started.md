# Getting Started

Quick setup and daily workflow for the Onyx monorepo.

## Prerequisites

- Bun (workspace package manager/runtime)
- Java 17 or 21 (`JAVA_HOME` set; Java 25 is not supported for Android builds)
- Android SDK (for Android work)
- Git

## Install

```bash
git clone <repository-url>
cd onyx
bun install
```

## Daily Commands

```bash
# JS/TS + web gates
bun run lint
bun run typecheck
bun run test

# Android gate
bun run android:lint

# Android unit tests (optional/targeted while test-source drift is being fixed)
bun run android:test
```

## Build Commands

```bash
bun run build
bun run build:all
bun run android:build
```

## Development Mode

```bash
# Web workspace dev task (currently type-watch)
bun run dev
```

`apps/web` currently uses `tsc -w` for `dev` and is still scaffold-oriented.

## Android Notes

- Prefer running Gradle from `apps/android`.
- `scripts/gradlew.js` resolves wrapper relative to current working directory.

```bash
cd apps/android
./gradlew :app:assembleDebug
```

## Environment and Cache Hygiene

- Declare required env vars in `turbo.json` (`globalEnv`/task `env`).
- Include `.env*` in relevant task `inputs` for cache invalidation.

## Where to Read Next

- `docs/README.md`
- `docs/architecture/system-overview.md`
- `docs/architecture/testing.md`
- `apps/android/README.md`
- `apps/web/README.md`
- `convex/README.md`
