# Web (view-only)

TanStack Start + shadcn/ui + zod. v0 is view-only with live subscriptions for updates.

Planned areas:
- `app/` routes and layouts
- `public/` static assets
- `tests/e2e/` Playwright specs

## Tailwind
Tailwind is configured via `tailwind.config.ts` and `postcss.config.cjs` with styles in `src/styles.css`.
`src/index.ts` imports `src/styles.css` to wire the pipeline.
