# AGENTS

- Do not bypass commit hooks.
- Run type check and lint.
- Fix all lint errors.
- Use context 7 to get relevant data.
- Skills only provide generic advice, not real docs.
- Environment variables must be declared in `turbo.json` (`env`/`globalEnv`) and relevant tasks must include `.env*` in `inputs` for correct cache invalidation.

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
