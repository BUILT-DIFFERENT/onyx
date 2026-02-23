# AGENTS

- Do not bypass commit hooks.
- Run type check and lint.
- Fix all lint errors.
- Use context 7 to get relevant data.
- Skills only provide generic advice, not real docs.
- Android Hilt setup note: if `com.google.dagger.hilt.android` is applied in `apps/android/app/build.gradle.kts`, also declare that plugin with version in `apps/android/build.gradle.kts` (`apply false`), and ensure the Compose host activity is annotated with `@AndroidEntryPoint` for `hiltViewModel()`.
- If anything in the project is confusing or surprising, update this `AGENTS.md` with the clarification before finishing the task.
- Environment variables must be declared in `turbo.json` (`env`/`globalEnv`) and relevant tasks must include `.env*` in `inputs` for correct cache invalidation.
- Current Android dev phase policy: backward compatibility with older local app DB versions is not required.
- Treat this as a super-greenfield project for now: do not optimize for preserving existing user data or migration compatibility; schema and data-model changes can be made directly and we will reconcile before ship.
- Editor settings persistence note: `EditorSettingsEntity/Dao/Repository` may already exist in tree during partial work, but persistence is not complete until `OnyxDatabase` includes the entity+migration and `NoteEditorScreen` consumes `viewModel.editorSettings` instead of local-only `rememberBrushState` defaults.
- Home screen architecture note: `HomeScreenViewModel` currently lives in `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt` (same file), not in a separate `HomeViewModel.kt` file.
- Editor source-of-truth note: canonical editor UI files are under `apps/android/app/src/main/java/com/onyx/android/ui/editor/`; `apps/android/app/src/main/java/com/onyx/android/ui/editor/components/` contains legacy duplicate components and is not source of truth.
- Android unit test suite note: `bun run android:test` currently has pre-existing failures in `apps/android/app/src/test/java/com/onyx/android/data/repository/NoteRepositoryTest.kt` (constructor/signature drift and unresolved fields); this is separate from editor overlay changes.
- Android unit test targeting note: even `:app:testDebugUnitTest --tests ...` can fail before test execution due unrelated compile errors in `apps/android/app/src/test/java/com/onyx/android/ui/NoteEditorViewModelTest.kt` (`match` unresolved), so prefer `android:lint` as the verification gate unless those test sources are fixed.
- Gradle wrapper helper note: `scripts/gradlew.js` resolves `gradlew` relative to the current working directory, so invoke it from `apps/android/` (or pass `cwd=apps/android`) rather than repo root.
- Android instrumentation compile note: current `:app:compileDebugAndroidTestKotlin` failures may differ from older tracker entries; recent constructor drift is around `templateState`/`onTemplateChange` in `NoteEditor*` androidTests, and there can also be unrelated `InkCanvasTouchRoutingTest` `lassoSelection` callsite drift.
- Android unit test stack clarification: app unit tests currently use JUnit 5 (`org.junit.jupiter`) + MockK and there are 38 files under `apps/android/app/src/test` (not the older 33-file JUnit4/Mockito snapshot).
- Android native build artifact note: `apps/android/app/.cxx/` is generated output and must remain ignored/untracked; if it appears in git, remove from index with `git rm -r --cached apps/android/app/.cxx`.
- Ink renderer migration note: stroke rendering now flows through `apps/android/app/src/main/java/com/onyx/android/ink/gl/GlInkSurfaceView` + `InkGlRenderer` (OpenGL), while lasso/selection overlays remain Compose in `InkCanvas.kt`; do not reintroduce `androidx.ink` dependencies.

## Context7 Library IDs (Project Stack)

Use these IDs directly with `mcp__context7__query-docs` to avoid repeated `resolve-library-id` lookups.

### Android app

- Jetpack Compose (UI): `/websites/developer_android_develop_ui_compose`
- AndroidX (Jetpack umbrella): `/androidx/androidx`
- AndroidX API reference (Kotlin): `/websites/developer_android_reference_kotlin_androidx`

### Web app

- React docs: `/reactjs/react.dev`
- Vite docs: `/vitejs/vite`
- Tailwind CSS docs: `/tailwindlabs/tailwindcss.com`
- TanStack Start (framework): `/websites/tanstack_start_framework_react`

### Monorepo + backend

- Turborepo: `/vercel/turborepo`
- Convex backend: `/get-convex/convex-backend`
- Convex LLM/docs mirror: `/llmstxt/convex_dev_llms_txt`

## Repository Doc Map (Quick Lookup)

- Primary docs index: `docs/README.md`
- Plans: `.sisyphus/plans/`
- Working notes and deep task logs: `.sisyphus/notepads/`
- Architecture overview: `docs/architecture/system-overview.md`
- Architecture deep dives (sync, storage, identity, data model, testing, CI/CD): `docs/architecture/`
- Testing guidance: `docs/architecture/testing.md`
- Contract test fixtures guidance: `tests/contracts/README.md`
- Android setup/build prerequisites: `apps/android/README.md`
- Android device testing and on-device verification flow: `apps/android/DEVICE-TESTING.md`
- Android Gradle wrapper helper details: `scripts/README.md` and `scripts/gradlew.js`
- Web app overview: `apps/web/README.md`
- Convex backend overview: `convex/README.md`
- Operational runbooks: `docs/runbooks/README.md`
- Infra operation docs: `infra/backups/README.md`, `infra/observability/README.md`, `infra/storage/README.md`
- Monorepo task commands and pipeline config: `package.json` (scripts) and `turbo.json` (tasks/cache/env)
- Known device blocker context: `docs/device-blocker.md`
