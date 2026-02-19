# Getting Started

Quick guide to setting up and developing Onyx.

## Prerequisites

Required tools:

| Tool        | Version | Notes                                   |
| ----------- | ------- | --------------------------------------- |
| Bun         | Latest  | Package manager and runtime             |
| Java        | 17+     | **NOT Java 25** - breaks Android builds |
| Android SDK | Latest  | For Android development                 |
| Git         | Any     | Version control                         |

### Java Version Warning

**Java 25 is NOT supported** for Android development in this project. The Android Gradle Plugin does not support Java 25 yet.

- **Error**: Build fails with "25.0.2" error
- **Solution**: Use Java 17 or Java 21

Set `JAVA_HOME` explicitly:

```bash
# Linux/macOS
export JAVA_HOME=/path/to/java-17

# Windows (cmd)
setx JAVA_HOME "C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot"

# Windows (PowerShell)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot", "User")
```

## Initial Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd onyx
```

### 2. Install Dependencies

```bash
bun install
```

This installs all workspace dependencies for:

- `apps/android` - Android authoring app
- `apps/web` - View-only web app
- `packages/*` - Shared libraries
- `convex` - Backend functions

### 3. Environment Variables

Environment variables must be declared in `turbo.json` for correct cache invalidation. The project uses `.env` files for configuration.

Key points:

- Use `globalEnv` for variables affecting many tasks
- Use task-level `env` for task-specific variables
- Include `.env*` in task `inputs` for cache invalidation
- Update `.env.example` when adding new variables

## Running Tests

### TypeScript/Web Tests

```bash
# Run all tests (excludes Android)
bun run test

# Run tests for specific package
bun run test --filter=@onyx/validation
```

**Current test coverage:**

- `@onyx/validation` - 30 schema tests
- `@onyx/contracts` - 3 contract tests
- MSW integration tests

**Expected behavior:** Packages without tests will fail with "No tests implemented" - this is expected for scaffolded packages.

### Android Tests

```bash
# Lint + compile verification (RECOMMENDED)
bun run android:lint

# Unit tests (BLOCKED by Java 25 issue)
bun run android:test
```

**Important:** `bun run android:test` is currently blocked by a Java 25 environment issue. Use `bun run android:lint` as the verification gate instead.

Pre-existing test failures exist in:

- `NoteRepositoryTest.kt` - constructor/signature drift
- `NoteEditorViewModelTest.kt` - unresolved `match` symbol

## Running Lints

### TypeScript

```bash
# Full typecheck
bun run typecheck

# Quick typecheck (faster)
bun run typecheck:quick

# Linter
bun run lint

# Quick lint (oxlint)
bun run lint:quick

# Auto-fix lint issues
bun run lint:fix
```

### Android

```bash
# All Android lints (lint + ktlint + detekt)
bun run android:lint

# Kotlin style only
bun run ktlint

# Static analysis only
bun run detekt
```

## Building

```bash
# Build all packages (excludes Android)
bun run build

# Build including Android
bun run build:all

# Build specific package
bun run build --filter=@onyx/validation

# Android build only
bun run android:build
```

## Development Workflow

### Web App

```bash
# Start dev mode (type watch only - no dev server yet)
bun run dev
```

**Note:** The web app is currently scaffolded. `bun run dev` runs `tsc -w` (TypeScript watch mode), not a full dev server. This is a current limitation.

### Android App

1. Open `apps/android/` in Android Studio, OR
2. Use Gradle wrapper from command line:

```bash
# From apps/android directory
cd apps/android
./gradlew :app:assembleDebug
```

**Gradle wrapper helper:** The `scripts/gradlew.js` script resolves `gradlew` relative to the current working directory. Run it from `apps/android/` or pass `cwd=apps/android`.

## Current Limitations

### Web App Scaffold

- **Dev mode**: `bun run dev` runs `tsc -w` (type watch), not a dev server
- **Routes/components**: TanStack Start scaffold configured, no routes/components yet
- **Status**: Early viewer scaffold (Milestone B)

### Android Test Gaps

- **Unit tests**: Blocked by Java 25 environment issue
- **Pre-existing failures**: `NoteRepositoryTest`, `NoteEditorViewModelTest` have compile errors
- **Verification gate**: Use `bun run android:lint` instead of `bun run android:test`

### Test Coverage

Several packages have no tests yet:

- `@onyx/shared`
- `@onyx/ui`
- `@onyx/config`

Packages with tests:

- `@onyx/validation` - 30 schema tests
- `@onyx/contracts` - 3 contract tests

### Convex Backend

- Schema and functions are scaffolded
- Implementation pending (Milestone B)

## Troubleshooting

### Java 25 Breaks Android Builds

**Error:**

```
Build failed with "25.0.2" error
```

**Solution:**

1. Install Java 17 or Java 21
2. Set `JAVA_HOME` to the correct JDK
3. Restart your terminal
4. Verify: `java -version`

### Tests Fail with "No tests implemented"

**Expected behavior** for packages without tests. The test script exits with error when no test files exist.

**Workaround:** Run tests for specific packages that have tests:

```bash
bun run test --filter=@onyx/validation
```

### Gradle Wrapper Not Found

**Error:**

```
ENOENT: gradlew not found
```

**Solution:** Run from `apps/android/` directory:

```bash
cd apps/android
../scripts/gradlew.js :app:assembleDebug
```

### Cache Not Invalidating on .env Changes

**Problem:** Build uses cached output after `.env` change.

**Solution:** Ensure `.env*` is in task `inputs` in `turbo.json`:

```json
{
  "tasks": {
    "build": {
      "inputs": ["$TURBO_DEFAULT$", "$TURBO_ROOT$/.env*"]
    }
  }
}
```

### Pre-commit Hooks Failing

Pre-commit runs `bun run lint && bun run typecheck`. If failing:

1. Run linters individually to identify issues:

   ```bash
   bun run lint
   bun run typecheck
   ```

2. Auto-fix lint issues:

   ```bash
   bun run lint:fix
   ```

3. For Android lint issues:
   ```bash
   bun run android:lint
   ```

## Useful Commands Reference

| Command                 | Description                        |
| ----------------------- | ---------------------------------- |
| `bun install`           | Install all dependencies           |
| `bun run dev`           | Start dev mode (web: type watch)   |
| `bun run build`         | Build all packages (excl. Android) |
| `bun run build:all`     | Build all packages (incl. Android) |
| `bun run test`          | Run TypeScript tests               |
| `bun run lint`          | Run TypeScript linter              |
| `bun run typecheck`     | Run TypeScript type check          |
| `bun run android:build` | Build Android app                  |
| `bun run android:lint`  | Android lint + ktlint + detekt     |
| `bun run android:test`  | Android unit tests (blocked)       |
| `bun run e2e`           | Run Playwright E2E tests           |

## Next Steps

1. Review architecture: `docs/architecture/system-overview.md`
2. Android setup details: `apps/android/README.md`
3. Web app overview: `apps/web/README.md`
4. Convex backend: `convex/README.md`
5. Testing guidance: `docs/architecture/testing.md`
