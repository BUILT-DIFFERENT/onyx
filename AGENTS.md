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
