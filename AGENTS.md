# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Commands

All commands run from repo root unless noted.

### JS/TS/Web (excludes Android)
```
bun run lint          # format + lint (JS/TS/Web only)
bun run typecheck     # TypeScript type checking
bun run test          # vitest (JS/TS packages)
bun run build         # build all non-Android packages
bun run dev           # dev mode (web workspace, currently tsc -w)
bun run e2e           # Playwright E2E (web)
```

### Android
```
bun run android:lint   # ktlintFormat + lint (primary verification gate)
bun run android:build  # Gradle assembleDebug
bun run android:test   # unit tests (has known pre-existing drift — see below)
bun run ktlint         # ktlint only
bun run detekt         # detekt only
```

### Running a single Android test
```
cd apps/android
./gradlew :app:testDebugUnitTest --tests "com.onyx.android.path.to.TestClass"
```

### All platforms
```
bun run lint:all       # format + lint (all workspaces including Android)
bun run build:all      # build everything
bun run test:all       # test everything
```

### Gradle notes
- `scripts/gradlew.js` resolves the wrapper relative to cwd — invoke from `apps/android/` or pass `cwd=apps/android`.
- Android requires JDK 17+ (`JAVA_HOME` set). Java 25 is not supported.
- Android SDK resolved from `ANDROID_HOME` / `ANDROID_SDK_ROOT`, then `%LOCALAPPDATA%\Android\Sdk` on Windows.
- Android scripts pass `--no-problems-report` to suppress Gradle deprecation noise.

### Pre-commit hooks
`simple-git-hooks` runs `bun run lint:all && bun run typecheck` on pre-commit. Bypass with `--no-verify` when needed.

### Environment variables
- Declare new env vars in `turbo.json` (`globalEnv` or task-level `env`).
- Include `.env*` in relevant task `inputs` for cache invalidation.
- Update `.env.example` when adding or renaming variables.
- Keep `envMode` as `strict` in `turbo.json`.

## Architecture

### Monorepo structure
- **`apps/android`** — Android authoring app (Kotlin, Jetpack Compose, Room, Vulkan ink renderer, MyScript IInk SDK, PdfiumAndroid). This is the primary product surface.
- **`apps/web`** — Web viewer scaffold (TanStack Start + shadcn/ui + zod). View-only in v0.
- **`convex`** — Backend schema and functions scaffold (Convex). System of record for all canonical state.
- **`packages/validation`** — Canonical runtime/contract schemas (zod).
- **`packages/contracts`** — Fixture-driven contract tests.
- **`packages/shared`**, **`packages/ui`**, **`packages/config`**, **`packages/test-utils`** — Shared workspace libraries (scaffolded).
- **`tests/contracts/fixtures/`** — JSON fixtures for contract tests.
- **`docs/`** — Architecture docs, MyScript SDK mirror, reference screenshots.
- **`infra/`** — Observability, storage, backup notes (no IaC yet).
- **`.sisyphus/plans/`** — Active implementation plans.

### Tooling
- Package manager/runtime: **Bun**
- Monorepo orchestration: **Turborepo** (`turbo.json`)
- TS linting: oxlint (quick) + eslint (full). Formatting: Prettier.
- Android linting: ktlint + detekt.
- Testing: vitest (TS), JUnit 5 + MockK (Android).

### Android app architecture
The Android app follows MVVM with Compose:
- **UI layer**: Jetpack Compose screens (`ui/`) with ViewModels. Single-activity architecture.
- **Ink layer**: Custom Vulkan renderer (`ink/vk/VkInkSurfaceView`, `VkNativeBridge`). Touch handling in `ink/ui/InkCanvas*.kt`. No OpenGL — the active renderer is Vulkan only.
- **Data layer**: Room database (`data/OnyxDatabase.kt`), entities, DAOs, repositories.
- **Recognition**: MyScript IInk SDK 4.3 (recognition only, no rendering). Engine singleton + page manager pattern.
- **PDF**: PdfiumAndroid for rendering, Apache PDFBox for text extraction. Tile-based async pipeline.

### Three-lane editor model (core architectural principle)
1. **Hot path** — Stylus capture → preview smoothing → invisible prediction → live render → stroke commit. No blocking work.
2. **Semantic async** — MyScript recognition, search indexing, text/math/shape conversions. Never on the hot path.
3. **UI/chrome** — Toolbar, panels, note settings, page management, PDF controls.

### Data model principles
- **Raw stylus input is canonical**. Display geometry is derived from raw input, not the other way around.
- **Offline-first**: All writes go to Room first; sync is opportunistic.
- **Blob references, not blobs in DB**: Convex stores metadata + CRDT deltas. Binary assets (PDFs, images, audio, snapshots) go to S3-compatible storage (Cloudflare R2).
- **CRDT sync**: Y-Octo/Yjs binary updates. Conflict resolution is automatic via CRDT merge semantics — no Lamport clocks or manual conflict handling.

### Key files (editor)
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` — Main editor screen
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorViewModel.kt` — Editor state/logic
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` — Ink canvas composable
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt` — Touch/stylus input routing
- `apps/android/app/src/main/java/com/onyx/android/ink/vk/VkInkSurfaceView.kt` — Vulkan surface
- `apps/android/app/src/main/java/com/onyx/android/ink/vk/VkNativeBridge.kt` — JNI bridge to native Vulkan renderer

## Project Status & Plans

### Current state
Milestone A (Android offline editor + MyScript recognition) is code-complete. The Android app has a functional offline editor with Vulkan ink rendering, PDF support, handwriting recognition scaffolding, Room persistence, and ~52 tests. The immediate priority is the **Editor Feel Milestone** — fixing pen feel before any new features.

### Plan hierarchy (read in this order)
1. **`PLAN.md`** (repo root) — Architecture, data model, feature set, tech stack, phased roadmap.
2. **`Onyx UX Specification.md`** (repo root) — Definitive UX reference for all screens, tools, interactions, visual design.
3. **`.sisyphus/plans/editor-feel-refactor.md`** — Immediate priority. Three-lane architecture, raw stroke model, smoothing pipeline.
4. **`.sisyphus/plans/android-foundation.md`** — PDF hardening, UI architecture, library/org, recognition/search, CI.
5. **`.sisyphus/plans/milestone-b-web-viewer.md`** — Web viewer + Convex backend (after Android foundation).
6. **`.sisyphus/plans/milestone-c-collaboration-sharing.md`** — Sync, sharing, public links, exports (after web viewer).
7. **`V0-api.md`** (repo root) — Authoritative V0 API contract for Convex functions.

### Reference docs
- `docs/README.md` — Docs index
- `docs/architecture/system-overview.md` — High-level architecture
- `docs/architecture/testing.md` — Verification gates and test strategy
- `docs/architecture/competitive-gap-backlog.md` — Feature parity tracking (checkmarks can drift — verify against code)
- `docs/Myscript/` — Local MyScript SDK documentation mirror (prefer over external browsing)
- `docs/images/` — NoteWise and Samsung Notes reference screenshots (visual north star)

## Conventions & Gotchas

### Android
- **Ink renderer is Vulkan only.** `ink/vk/` package. No `gl/` package exists — any references to `GlInkSurfaceView` or `InkGlRenderer` are stale. Do not reintroduce `androidx.ink` dependencies.
- **Editor source of truth**: Canonical editor UI is under `ui/editor/`. The `ui/editor/components/` subdirectory contains legacy duplicates and is NOT the source of truth.
- **HomeScreenViewModel** lives inside `HomeScreen.kt` (same file), not a separate file.
- **No standalone SettingsScreen.kt** exists yet. Editor/home preferences are in `NoteEditorScreen.kt` and `HomeScreen.kt`.
- **Test drift**: `android:test` has pre-existing failures from constructor/signature drift. Use `android:lint` as the primary verification gate. Fix test sources when actively working in those areas.
- **Unit tests use JUnit 5** (`org.junit.jupiter`) + MockK.
- **`apps/android/app/.cxx/`** is generated native build output — must stay gitignored.
- **ktlint `property-naming`** rejects leading underscores on private backing fields. Use non-underscore names for new fields.
- **detekt `MaxLineLength`** can fail after ktlintFormat collapses expression bodies. Prefer block-body functions in hotspots.
- **`kotlinx.coroutines.flow.combine`** with >5 flows resolves to vararg signature. Use nested `combine` for type safety.
- **Hilt setup**: If `com.google.dagger.hilt.android` is applied in `apps/android/app/build.gradle.kts`, it must also be declared (with version, `apply false`) in `apps/android/build.gradle.kts`. Compose host activity needs `@AndroidEntryPoint` for `hiltViewModel()`.

### Greenfield policy
- This is a greenfield project. Backward DB compatibility is not required until before ship.
- Schema and data model changes can be made directly. No destructive migration guards needed yet.
- Do not optimize for preserving existing user data during development.

### Contract/schema source of truth
- API schemas: `packages/validation/src/schemas/*`
- Contract fixtures: `tests/contracts/fixtures/*`
- V0 API contract: `V0-api.md` (repo root)

### Backlog tracking
- `docs/architecture/competitive-gap-backlog.md` checkmarks can drift from reality. Always verify against source code before treating status as canonical.
- Evidence paths in the backlog may be stale after refactors. Check current symbols before relying on listed paths.

### If something is confusing
Update this `AGENTS.md` with the clarification before finishing the task.
