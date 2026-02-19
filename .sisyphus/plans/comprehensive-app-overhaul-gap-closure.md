# Comprehensive App Overhaul: Gap Closure Plan

Date: 2026-02-18 (Updated: 2026-02-18)
Owner: Onyx engineering
Status: Proposed (Momus-refined v8)

## Source Reference

This plan is a gap-focused companion to the authoritative plan:

- `.sisyphus/plans/comprehensive-app-overhaul.md`

All task IDs below map directly to that plan (for example `0.2`, `3.4`, `6.2`). Additional `G-H.*` IDs cover codebase health improvements discovered during refinement.

## Purpose

Close the still-unchecked items that are either:

- missing in code,
- partially implemented,
- blocked on physical-device or non-code evidence.

**Additionally**, address codebase health, quality, testability, and cleanliness issues to simplify development and improve confidence in changes.

---

## IMPORTANT: Documentation vs Reality Reconciliation

**The following notepad files contain claims that do NOT match repository reality:**

| Notepad File                                                             | Claim                                                 | Reality                                                                                                                                                         |
| ------------------------------------------------------------------------ | ----------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `.sisyphus/notepads/comprehensive-app-overhaul/feature-flags-catalog.md` | Feature flags implemented, FeatureFlags.kt etc. exist | Directory `apps/android/app/src/main/java/com/onyx/android/config/` does NOT exist. Hardcoded constants (`ENABLE_MOTION_PREDICTION`) still in `InkCanvas.kt:68` |
| `.sisyphus/notepads/device-validation/TASK-6.3-STATUS.md`                | Claims feature flags "Implemented"                    | Same files do not exist                                                                                                                                         |

**Action Required**: These notepads must be corrected to reflect actual state. Feature flags are NOT implemented - they remain in Gap G-0.2-A as Missing.

**CRITICAL: Authoritative plan reference path mismatch:**

The authoritative plan `.sisyphus/plans/comprehensive-app-overhaul.md` at line 215 references:

- `WRONG: .sisyphus/notepads/feature-flags-catalog.md`
- `CORRECT: .sisyphus/notepads/comprehensive-app-overhaul/feature-flags-catalog.md`

This must be fixed in the authoritative plan OR the notepad file must be moved. Add to G-H.7-A scope.

---

## Gap Matrix - Feature Gaps (Original)

> **Trailhead Reference**: All Gap IDs below map to tasks in `.sisyphus/plans/comprehensive-app-overhaul.md`.
> Each gap includes a "Plan Ref" pointer to the exact section and task number in the authoritative plan.

| Gap ID  | Source Task                         | Plan Ref                                   | Status  | Current Evidence                                                                                                                                                                                 | Closure Criteria                                                                            | Verification                                                      |
| ------- | ----------------------------------- | ------------------------------------------ | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| G-0.2-A | 0.2 Feature flags and kill switches | Phase 0, Task 0.2 (lines 176-220)          | Missing | Directory `apps/android/app/src/main/java/com/onyx/android/config/` does NOT exist. Hardcoded `ENABLE_MOTION_PREDICTION` at `InkCanvas.kt:68`, `ENABLE_PREDICTED_STROKES` at `InkCanvasTouch.kt` | Implement runtime flag layer, wire usage in ink/editor paths, persist values across restart | `grep -r "ENABLE_" apps/android/` returns 0 hardcoded             |
| G-0.2-B | 0.2 Feature flags and kill switches | Phase 0, Task 0.2 (lines 192-195)          | Missing | No `DeveloperFlagsScreen.kt` anywhere in repo. No route in `navigation/Routes.kt` for developer flags                                                                                            | Add debug-only `DeveloperFlagsScreen` entry and verify toggles affect runtime behavior      | `find . -name "DeveloperFlagsScreen.kt"` finds file               |
| G-0.4-A | 0.4 Test harness uplift             | Phase 0, Task 0.4 (lines 252-297)          | Partial | `apps/android/maestro/flows/editor-smoke.yaml` exists; `apps/android/benchmark/` exists; no Paparazzi screenshot test in `apps/android/app/src/test/`                                            | Add screenshot harness + first golden and reproducible verification path                    | `bun run android:test` includes snapshot tests                    |
| G-0.4-B | 0.4 Test harness uplift             | Phase 0, Task 0.4 (lines 271-274, 288-289) | Missing | `.github/workflows/ci.yml` has no Android job or steps                                                                                                                                           | Add required CI steps: `bun run android:lint` and `bun run android:test`                    | PR triggers Android job in CI                                     |
| G-1.1-A | 1.1 Prediction hardening            | Phase 1, Task 1.1 (lines 301-326)          | Partial | `InkCanvasTouch.kt` has `handlePredictedStrokes` function; `InkCanvas.kt:68` has `ENABLE_MOTION_PREDICTION = true` hardcoded                                                                     | Consolidate to single official prediction integration path behind runtime flag              | Flag toggle changes prediction behavior                           |
| G-1.2-A | 1.2 Pen-up handoff                  | Phase 1, Task 1.2 (lines 328-350)          | Partial | `InkCanvas.kt` has pending stroke bookkeeping; artifact-free handoff not yet proven                                                                                                              | Complete frame-aligned handoff and add stress validation for rapid pen up/down + undo       | No visible stroke gap in slow-mo capture                          |
| G-1.3-A | 1.3 Style schema + presets          | Phase 1, Task 1.3 (lines 352-375)          | Partial | `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt` has style fields; settings persistence untracked                                                                           | Wire preset persistence end-to-end and validate restart survival                            | Preset survives app kill + restart                                |
| G-2.1-A | 2.1 Pdfium adapter boundary         | Phase 2, Task 2.1 (lines 450-481)          | Partial | `apps/android/app/build.gradle.kts:153` has Pdfium; adapter interfaces incomplete                                                                                                                | Finalize `PdfRenderEngine`/`PdfTextEngine` boundary and remove direct UI coupling           | UI imports adapter interface, not Pdfium directly                 |
| G-2.2-A | 2.2 Scheduler/perf                  | Phase 2, Task 2.2 (lines 483-499+)         | Partial | `AsyncPdfPipeline.kt` exists; p95 evidence incomplete                                                                                                                                            | Capture/verify tile-visible p95 and bounded queue behavior under stress                     | p95 < 120ms in benchmark log                                      |
| G-2.3-A | 2.3 Cache race hardening            | Phase 2, Task 2.3                          | Partial | `PdfTileCacheTest.kt` exists at `apps/android/app/src/test/java/com/onyx/android/pdf/`; device smoke unmet                                                                                       | Prove no recycle/draw crash under instrumentation/device stress runs                        | `connectedDebugAndroidTest` passes                                |
| G-2.4-A | 2.4 Visual continuity               | Phase 2, Task 2.4                          | Partial | `PdfBucketCrossfadeContinuityTest.kt` exists at `apps/android/app/src/androidTest/`; journey evidence incomplete                                                                                 | Validate continuity and blur-settle behavior in instrumented/device runs                    | No black flash in video capture                                   |
| G-2.5-A | 2.5 PDF interaction parity          | Phase 2, Task 2.5                          | Partial | `TextSelectionModel.kt`, `PdfiumNativeTextBridge.kt` exist; clipboard integration incomplete                                                                                                     | Complete and verify text selection handles + copy reliability                               | Copy to clipboard works in instrumented test                      |
| G-3.1-A | 3.1 UI decomposition                | Phase 3, Task 3.1                          | Partial | `NoteEditorUi.kt` exists as monolith (large file)                                                                                                                                                | Extract focused UI modules and preserve behavior parity                                     | `NoteEditorUi.kt` < 500 lines after extraction                    |
| G-3.2-A | 3.2 Toolbar/accessibility           | Phase 3, Task 3.2                          | Partial | Toolbar exists in `NoteEditorUi.kt`; accessibility validation incomplete                                                                                                                         | Ensure 48x48dp target compliance and compact-mode primary control access                    | Accessibility Scanner app passes (see verification details below) |
| G-3.3-A | 3.3 Home ViewModel extraction       | Phase 3, Task 3.3                          | Partial | `HomeScreen.kt:265` uses `requireAppContainer()` directly                                                                                                                                        | Complete ViewModel-driven state ownership and error/retry coverage                          | `grep requireAppContainer HomeScreen.kt` returns 0                |
| G-3.4-A | 3.4 Hilt migration                  | Phase 3, Task 3.4                          | Missing | `HomeScreen.kt:265` and `NoteEditorScreen.kt:103` use `requireAppContainer()`. No `@HiltViewModel` or `@HiltAndroidApp` annotations anywhere                                                     | Complete Hilt bootstrap + modules; remove runtime-critical app container usage              | `grep @HiltAndroidApp` finds OnyxApplication                      |
| G-3.5-A | 3.5 Splash startup path             | Phase 3, Task 3.5                          | Missing | No SplashScreen API usage in `MainActivity.kt` or themes                                                                                                                                         | Integrate SplashScreen path and capture startup benchmark trend                             | SplashScreen API import present in MainActivity                   |
| G-4.1-A | 4.1 Folder/template model hardening | Phase 4, Task 4.1                          | Partial | Folder migration exists; template model integration untracked                                                                                                                                    | Land template metadata + migration and verify referential integrity                         | Migration test passes                                             |
| G-4.3-A | 4.3 Room settings migration         | Phase 4, Task 4.3                          | Partial | Settings persistence untracked; `NoteEditorScreen.kt` has ephemeral `rememberBrushState`                                                                                                         | Complete DB integration, restore/persist path, and migration coverage                       | Settings survive app restart                                      |
| G-5.1-A | 5.1 MyScript hardening              | Phase 5, Task 5.1                          | Partial | `MyScriptEngine.kt`, `MyScriptPageManager.kt` exist; debounce/recovery incomplete                                                                                                                | Add debounced scheduling + resilient failure recovery path                                  | Recognition survives engine restart                               |
| G-5.2-A | 5.2 Unified search                  | Phase 5, Task 5.2                          | Partial | `NoteRepository.kt` has `searchNotes()` for recognition; PDF/metadata unification missing                                                                                                        | Implement one mixed-source query surface and navigation to exact geometry                   | Search returns results from ink+PDF+metadata                      |
| G-5.3-A | 5.3 Conversion/overlay controls     | Phase 5, Task 5.3                          | Missing | No overlay toggle or conversion workflow found                                                                                                                                                   | Add overlay controls and editable conversion flow                                           | Overlay toggle visible in UI                                      |
| G-6.1-A | 6.1 Lamport/oplog primitives        | Phase 6, Task 6.1                          | Partial | `LamportClockTest.kt`, `OperationLogEntityTest.kt` exist in test; DB schema at version 3 without oplog                                                                                           | Integrate oplog entities/DAO into DB schema + tests and verify monotonic behavior           | Oplog table in Room schema                                        |
| G-6.2-A | 6.2 CI and perf budgets             | Phase 6, Task 6.2                          | Missing | `.github/workflows/ci.yml` has no Android gates; `turbo.json` missing `$TURBO_DEFAULT$` + `.env*` in inputs                                                                                      | Enforce Android gates in CI and finalize turbo env-input hygiene                            | CI Android job runs on PR                                         |
| G-6.3-A | 6.3 Device blocker closure          | Phase 6, Task 6.3                          | Blocked | `.sisyphus/notepads/device-validation/TASK-6.3-STATUS.md` lists 10 compile errors in instrumentation tests                                                                                       | Fix instrumentation compile errors and archive successful device evidence                   | `connectedDebugAndroidTest` compiles and runs                     |
| G-7.1-A | 7.1 Segment eraser                  | Phase 7, Task 7.1                          | Partial | `StrokeSplitter.kt` exists at `apps/android/app/src/main/java/com/onyx/android/ink/algorithm/`; tool integration incomplete                                                                      | Wire deterministic mid-stroke erase with lossless undo/redo                                 | Eraser+undo cycle produces identical strokes                      |
| G-7.2-A | 7.2 Lasso transform tooling         | Phase 7, Task 7.2                          | Partial | `LassoGeometry.kt`, `LassoRenderer.kt` exist; undo integration incomplete                                                                                                                        | Complete selection + move/resize + undo integration                                         | Lasso move+undo restores original position                        |
| G-7.3-A | 7.3 Template system polish          | Phase 7, Task 7.3                          | Partial | `PageTemplateBackground.kt` exists; persistence incomplete                                                                                                                                       | Complete persistence + reopen behavior and zoom/pan stability verification                  | Template persists across app restart                              |

---

## Gap Matrix - Codebase Health (New)

| Gap ID  | Category       | Status  | Current Evidence                                                                                                                                                       | Closure Criteria                                                                                | Verification                                     |
| ------- | -------------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| G-H.1-A | Test Infra     | Missing | No `vitest.config.ts` anywhere in repo (verified via glob). All TS packages use `--passWithNoTests`. `packages/validation/src/index.ts` is `export {}` (empty)         | Add vitest config, add minimal zod schemas to validation pkg, then add real tests               | `bunx vitest run` shows test count > 0           |
| G-H.1-B | Test Infra     | Missing | `tests/contracts/` contains only README.md and .gitkeep. `packages/validation/src/index.ts` exports nothing to validate against                                        | Implement zod schemas first (prereq), then contract fixtures                                    | `bunx vitest run tests/contracts` passes         |
| G-H.1-C | Test Infra     | Missing | No shared test utilities package                                                                                                                                       | Create `packages/test-utils` with reusable test helpers                                         | `@onyx/test-utils` importable                    |
| G-H.2-A | CI/Build       | Partial | `turbo.json` line 4: `globalDependencies: [".env"]` exists but task-level `inputs` arrays missing `$TURBO_DEFAULT$` + `.env*`                                          | Add `["$TURBO_DEFAULT$", ".env*"]` to inputs for `build`, `test`, `e2e` tasks                   | `.env.local` change causes cache miss            |
| G-H.2-B | CI/Build       | Missing | `.github/workflows/ci.yml` has single `build` job for web only (lines 8-31); no Android job                                                                            | Add Android CI job with lint, test, ktlint, detekt gates                                        | PR triggers `android` job                        |
| G-H.2-C | CI/Build       | Missing | No Android instrumentation workflow file                                                                                                                               | Add optional `android-instrumentation.yml` workflow                                             | Manual dispatch runs emulator tests              |
| G-H.3-A | Code Quality   | Partial | All 6 TS packages have `--passWithNoTests` (verified in package.json files)                                                                                            | Either add real tests or change to `exit 1` with TODO                                           | No `--passWithNoTests` in grep output            |
| G-H.3-B | Code Quality   | Partial | `convex/schema.ts` is 2 lines: `export default {};`. `convex/functions/` has only `.gitkeep`                                                                           | Implement actual Convex schema; create at least one function for msw mocking target             | `convex/functions/notes.ts` exports query        |
| G-H.3-C | Code Quality   | Partial | `packages/shared/src/index.ts` and `packages/validation/src/index.ts` both export `{}` (empty)                                                                         | Implement zod schemas for API contract (NOT Room entities - see schema-audit.md)                | Schemas parse fixture JSON successfully          |
| G-H.4-A | Dev Experience | Missing | No `vitest.workspace.ts` or `vitest.config.ts` at root. Turbo's `test` task runs per-package scripts which use vitest but each package has no config                   | Create `vitest.config.ts` at root; update root `package.json` test script                       | `bunx vitest run` works from root                |
| G-H.4-B | Dev Experience | Missing | `apps/web/package.json` dev script is `"tsc -w -p tsconfig.json"` (typecheck watch), not a dev server. No TanStack Start server wiring                                 | Document current dev workflow; note web app is scaffold without runtime                         | `docs/development/getting-started.md` exists     |
| G-H.5-A | Dependencies   | Missing | `apps/web/package.json` devDependencies (lines 27-33) has no `@testing-library/*`, no `jsdom`                                                                          | Add testing-library deps; create `apps/web/vitest.config.ts` with jsdom environment             | `jsdom` in devDeps                               |
| G-H.5-B | Dependencies   | Missing | No msw in repo. `convex/functions/` is empty (only .gitkeep) so no API surface to mock yet                                                                             | Add msw; create minimal Convex function stub as mock target; handler intercepts `/api/query`    | MSW handler file exists                          |
| G-H.5-C | Dependencies   | Partial | `apps/android/app/build.gradle.kts` has mockk, coroutines-test, Compose test; no Robolectric                                                                           | Evaluate Robolectric for faster Android unit tests                                              | Decision documented                              |
| G-H.6-A | Cleanup        | Partial | `apps/web/vite.config.ts` has 5 LSP errors for missing modules. `apps/web/package.json` devDependencies missing: vite, @vitejs/plugin-react, vite-tsconfig-paths, etc. | Add exact packages: `vite`, `@tanstack/react-start`, `@vitejs/plugin-react`, etc. (see details) | `lsp_diagnostics` shows 0 errors in vite.config  |
| G-H.7-A | Documentation  | Partial | `.sisyphus/notepads/comprehensive-app-overhaul/feature-flags-catalog.md` claims files exist that don't                                                                 | Correct notepad to reflect actual state (NOT IMPLEMENTED)                                       | Notepad says "NOT IMPLEMENTED" for feature flags |
| G-H.8-A | Documentation  | Partial | Device verification scripts use wrong DB name: `onyx.db` but actual DB is `onyx_notes.db` (see `OnyxDatabase.kt:57`)                                                   | Fix DB path in verify-on-device.sh, DEVICE-TESTING.md, device-blocker.md                        | DB path matches `DATABASE_NAME` constant         |

---

## Execution Order (Revised)

### Wave 0 - Testability Foundation (NEW - Do First)

These items enable faster iteration and confidence for all subsequent waves.

**Dependency chain for Wave 0:**

1. G-H.2-A (turbo env fix) - independent
2. G-H.6-A (vite deps) - independent
3. G-H.3-C (implement minimal validation schemas) - prereq for tests
4. G-H.4-A (vitest config at root) - after schemas exist
5. G-H.1-A (first real tests) - after vitest config
6. G-H.5-A (testing-library) - after vitest working
7. G-H.3-B (minimal Convex schema + function) - prereq for msw
8. G-H.5-B (msw with mock target) - after Convex stub exists

### Wave 1 - Safety Foundations

1. G-H.7-A (fix notepad documentation lies)
2. G-H.8-A (fix database name in verification scripts)
3. G-0.2-A, G-0.2-B (runtime flags + debug controls) - ACTUALLY implement these
4. G-0.4-B, G-6.2-A, G-H.2-B (Android required CI gates)
5. G-3.4-A (Hilt migration baseline)

### Wave 2 - Core Runtime Gaps

1. G-1.1-A, G-1.2-A, G-1.3-A (ink correctness + preset persistence)
2. G-2.1-A, G-2.2-A, G-2.3-A, G-2.4-A, G-2.5-A (PDF hardening/parity)
3. G-3.1-A, G-3.2-A, G-3.3-A, G-3.5-A (UI architecture/usability/startup)

### Wave 3 - Product Surface Completion

1. G-4.1-A, G-4.3-A (template/settings DB completion)
2. G-5.1-A, G-5.2-A, G-5.3-A (recognition + unified search)
3. G-6.1-A (lamport/oplog integration)
4. G-H.1-B (contract test fixtures - after validation schemas exist)

### Wave 4 - Advanced Features + Release Gate

1. G-7.1-A, G-7.2-A, G-7.3-A (advanced tools)
2. G-6.3-A, G-H.2-C (device blocker closure + instrumentation CI)

### Wave 5 - Codebase Polish (Parallel/Ongoing)

1. G-H.1-C - Shared test utilities package
2. G-H.3-A - Remove passWithNoTests masking (after tests exist)
3. G-H.4-B - Document dev workflows
4. G-H.5-C - Evaluate Robolectric for Android

---

## Detailed TODOs - Codebase Health Gaps

### G-H.2-A: Fix turbo.json env hashing

**What to do:**

- Add `.env*` to `inputs` array for tasks: `build`, `test`, `e2e`
- **CRITICAL**: Use `$TURBO_DEFAULT$` to extend defaults, NOT override them
- This ensures `.env*` is added to existing default inputs (source files, etc.)
- Note: `globalDependencies: [".env"]` already exists at line 4 but only covers exact `.env` file; `.env*` pattern also covers `.env.local`, `.env.production`, etc.

**Exact JSON patch** (apply to turbo.json):

```json
// BEFORE (current state):
"build": {
  "dependsOn": ["^build"],
  "outputs": ["dist/**", ".next/**", "build/**", "app/build/**"]
}

// AFTER (correct change):
"build": {
  "dependsOn": ["^build"],
  "inputs": ["$TURBO_DEFAULT$", ".env*"],
  "outputs": ["dist/**", ".next/**", "build/**", "app/build/**"]
}

// Similarly for test:
"test": {
  "dependsOn": ["build"],
  "inputs": ["$TURBO_DEFAULT$", ".env*"],
  "outputs": ["coverage/**"]
}

// And e2e:
"e2e": {
  "dependsOn": ["build"],
  "inputs": ["$TURBO_DEFAULT$", ".env*"],
  "outputs": ["playwright-report/**", "test-results/**"]
}
```

**Files to modify:**

- `turbo.json` (lines 6-24, add inputs arrays)

**References:**

- `turbo.json:4` - existing globalDependencies
- `AGENTS.md` - env policy: "relevant tasks must include `.env*` in `inputs`"
- Turborepo docs: `$TURBO_DEFAULT$` includes all default inputs (git-tracked files in package)

**Acceptance Criteria:**

- [ ] `turbo.json` tasks `build`, `test`, `e2e` have `"inputs": ["$TURBO_DEFAULT$", ".env*"]`
- [ ] `bun run build && touch .env.test && bun run build` shows second build is "cache MISS" (not HIT)
- [ ] `bun run build` still correctly hashes source files (not ONLY env files)

**Commit:** YES

- Message: `chore(turbo): add env file hashing to task inputs`
- Files: `turbo.json`

---

### G-H.6-A: Fix web vite.config.ts LSP errors

**What to do:**

- Add missing devDependencies to `apps/web/package.json` based on exact imports in `vite.config.ts`:

**Import → Package mapping** (verified from vite.config.ts lines 1-5):
| Import Statement | Package to Install |
|------------------|-------------------|
| `import { defineConfig } from 'vite'` | `vite` |
| `import { tanstackStart } from '@tanstack/react-start/plugin/vite'` | `@tanstack/react-start` |
| `import react from '@vitejs/plugin-react'` | `@vitejs/plugin-react` |
| `import tsconfigPaths from 'vite-tsconfig-paths'` | `vite-tsconfig-paths` |
| `import tailwindcss from '@tailwindcss/vite'` | `@tailwindcss/vite` |

- Run `bun install`

**Files to modify:**

- `apps/web/package.json` (devDependencies section, lines 27-33)

**References:**

- `apps/web/vite.config.ts` lines 1-5 - the failing imports (exact import paths shown above)
- Current devDependencies: `@types/react`, `@types/react-dom`, `autoprefixer`, `postcss`, `tailwindcss`

**Acceptance Criteria:**

- [ ] `apps/web/vite.config.ts` has no LSP errors (verified via `lsp_diagnostics`)
- [ ] `bun run typecheck --filter=@onyx/web` passes
- [ ] All 5 packages listed above are in devDependencies

**Note:** Web dev server (`bun run dev --filter=@onyx/web`) currently runs `tsc -w`, NOT a Vite server. This is expected for the current scaffold state.

**Commit:** YES

- Message: `fix(web): add missing vite plugin dependencies`
- Files: `apps/web/package.json`

---

### G-H.3-C: Implement minimal validation schemas (PREREQ for tests)

**Contract Layer Clarification (CRITICAL)**

The zod schemas represent the **JSON-serializable API contract** used for:

1. Convex function payloads
2. Contract test fixtures (JSON files)
3. Cross-platform data exchange

**Source of Truth Hierarchy:**

1. **V0-api.md** (if conflicts exist) - the authoritative sync API spec
2. **docs/schema-audit.md** - verified alignment between Room entities and V0 API
3. **Android entities** - implementation reference, but NOT the contract definition

**`deletedAt` Semantics (CANONICAL - Single Source of Truth):**

The Room entity `NoteEntity.kt:17` uses `val deletedAt: Long? = null` which means:

- `null` → not deleted
- A number → deleted at that Unix ms timestamp

**For contract surfaces (zod, Convex, fixtures, MSW), use "absent OR number" semantics:**

- **Absent** means not deleted (field omitted from JSON/object)
- **Present number** means deleted at that timestamp

This choice aligns with Convex's type system which uses `v.optional(v.number())` (absent vs present), not `v.union(v.null(), v.number())`.

| Surface       | Representation                | Example (not deleted) | Example (deleted)             |
| ------------- | ----------------------------- | --------------------- | ----------------------------- |
| Room entity   | `Long? = null`                | `deletedAt = null`    | `deletedAt = 1234567890`      |
| Zod schema    | `z.number().int().optional()` | Field absent          | `{ deletedAt: 1234567890 }`   |
| Convex schema | `v.optional(v.number())`      | Field absent          | `{ deletedAt: 1234567890 }`   |
| JSON fixture  | Optional number field         | `{}` (omit field)     | `{ "deletedAt": 1234567890 }` |
| MSW mock      | Optional number field         | `{}` (omit field)     | `{ deletedAt: 1234567890 }`   |

**IMPORTANT**: Do NOT use `null` in JSON fixtures or MSW mocks - omit the field entirely.

**NoteSchema fields (EXACTLY these, no more):**
| Field | Type | Notes |
|-------|------|-------|
| `noteId` | `z.string().uuid()` | Primary key |
| `ownerUserId` | `z.string()` | Clerk user ID |
| `title` | `z.string()` | Note title |
| `createdAt` | `z.number().int()` | Unix ms |
| `updatedAt` | `z.number().int()` | Unix ms |
| `deletedAt` | `z.number().int().optional()` | Soft delete (absent = not deleted) |
| ~~`folderId`~~ | **EXCLUDED** | Local-only (not in schema-audit.md) |

**PageSchema fields (EXACTLY these, no more):**
| Field | Type | Notes |
|-------|------|-------|
| `pageId` | `z.string().uuid()` | Primary key |
| `noteId` | `z.string().uuid()` | FK to note |
| `kind` | `z.enum(["ink", "pdf", "mixed", "infinite"])` | Page type |
| `geometryKind` | `z.enum(["fixed", "infinite"])` | Geometry type |
| `width` | `z.number()` | In points |
| `height` | `z.number()` | In points |
| `unit` | `z.literal("pt")` | Always "pt" |
| `pdfAssetId` | `z.string().nullable().optional()` | For PDF pages |
| `pdfPageNo` | `z.number().int().nullable().optional()` | PDF page number |
| `contentLamportMax` | `z.number().int()` | Sync tracking |
| `updatedAt` | `z.number().int()` | Unix ms |
| ~~`indexInNote`~~ | **EXCLUDED** | Local-only (per schema-audit.md line 70) |

**StrokeSchema fields (METADATA ONLY - excludes points):**
| Field | Type | Notes |
|-------|------|-------|
| `strokeId` | `z.string().uuid()` | Primary key |
| `pageId` | `z.string().uuid()` | FK to page |
| `style` | `StrokeStyleSchema` | Nested object (see below) |
| `bounds` | `BoundsSchema` | Nested object (see below) |
| `createdAt` | `z.number().int()` | Unix ms |
| `createdLamport` | `z.number().int()` | Lamport clock |
| ~~`strokeData`~~ | **EXCLUDED** | ByteArray, not JSON-serializable |
| ~~`points`~~ | **DEFERRED** | Points are in `V0-api.md:149-153` but require base64 or array serialization; defer to sync implementation |

**Why StrokeSchema is "metadata only":**

- `V0-api.md:149-153` defines `StrokeAdd` with `points` array
- Room entity stores `strokeData` as ByteArray (protobuf)
- For contract tests, we only need to validate the JSON-serializable metadata
- Full stroke sync payload (with points) will be defined when implementing sync (Milestone C)
- If points are needed sooner, add `points: z.array(PointSchema).optional()` with PointSchema from V0-api.md:137-143

**Supporting schemas (nested objects):**

```typescript
// StrokeStyleSchema (matches V0-api.md:127-134)
const StrokeStyleSchema = z.object({
  tool: z.enum(['pen', 'highlighter', 'eraser']),
  color: z.string().optional(), // hex color
  baseWidth: z.number(),
  minWidthFactor: z.number(),
  maxWidthFactor: z.number(),
  nibRotation: z.boolean(),
});

// BoundsSchema (matches V0-api.md:107-110)
const BoundsSchema = z.object({
  x: z.number(),
  y: z.number(),
  w: z.number(),
  h: z.number(),
});
```

**What to do:**

- Add actual zod schemas to `packages/validation/src/`:
  - `schemas/note.ts` - NoteSchema with EXACTLY the fields listed above
  - `schemas/page.ts` - PageSchema with EXACTLY the fields listed above
  - `schemas/stroke.ts` - StrokeSchema (metadata only) with EXACTLY the fields listed above
  - `schemas/common.ts` - StrokeStyleSchema, BoundsSchema
  - `index.ts` - re-export all schemas

**Files to create:**

- `packages/validation/src/schemas/note.ts`
- `packages/validation/src/schemas/page.ts`
- `packages/validation/src/schemas/stroke.ts`
- `packages/validation/src/schemas/common.ts`

**Files to modify:**

- `packages/validation/src/index.ts` (currently `export {}`)

**References:**

- `docs/schema-audit.md` - **PRIMARY**: Canonical field mapping between Room and API contract
- `docs/schema-audit.md:70` - Confirms `indexInNote` is local-only
- `docs/architecture/data-model.md` - High-level table list
- `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteEntity.kt` - Android Note shape (note: has `folderId` which is LOCAL-ONLY)
- `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt` - Android Page shape (note: has `indexInNote` which is LOCAL-ONLY)
- `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt` - Android Stroke shape (note: `strokeData` is ByteArray)

**Example schema structure (Note):**

```typescript
// packages/validation/src/schemas/note.ts
import { z } from 'zod';

// Note: folderId is intentionally excluded - it's a local-only field
// deletedAt uses "absent OR number" semantics - omit field if not deleted
// See docs/schema-audit.md for canonical sync API contract
export const NoteSchema = z.object({
  noteId: z.string().uuid(),
  ownerUserId: z.string(),
  title: z.string(),
  createdAt: z.number().int(), // Unix ms
  updatedAt: z.number().int(), // Unix ms
  deletedAt: z.number().int().optional(), // Absent = not deleted
});

export type Note = z.infer<typeof NoteSchema>;
```

**Acceptance Criteria:**

- [ ] `packages/validation/src/index.ts` exports zod schemas for Note, Page, Stroke, StrokeStyle, Bounds
- [ ] `bun run typecheck --filter=@onyx/validation` passes
- [ ] NoteSchema has EXACTLY 6 fields (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt)
- [ ] PageSchema has EXACTLY 11 fields (pageId, noteId, kind, geometryKind, width, height, unit, pdfAssetId, pdfPageNo, contentLamportMax, updatedAt)
- [ ] StrokeSchema has EXACTLY 6 fields (strokeId, pageId, style, bounds, createdAt, createdLamport)
- [ ] `folderId` is NOT in NoteSchema (verified by `.parse({...folderId: "x"})` failing with extra key error using `strict()`)
- [ ] `indexInNote` is NOT in PageSchema
- [ ] `strokeData` and `points` are NOT in StrokeSchema

**Commit:** YES

- Message: `feat(validation): add zod schemas for core entities`
- Files: `packages/validation/src/**`

---

### G-H.4-A: Add vitest config at root

**What to do:**

- Create `vitest.config.ts` at repo root
- Configure to run tests in `apps/web/src`, `packages/*/src`, AND `tests/contracts/src`
- Note: `tests/contracts/` is NOT in package.json workspaces, so must be explicitly included in root config
- Note: `convex/` is NOT in workspaces array in `package.json`, so exclude from vitest config
- Add `setupFiles` for MSW integration (created in G-H.5-B)

**Test discovery strategy:**

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: [
      'apps/web/src/**/*.test.{ts,tsx}',
      'packages/*/src/**/*.test.{ts,tsx}',
      'tests/contracts/src/**/*.test.ts', // Explicitly include non-workspace tests
    ],
    exclude: ['**/node_modules/**', 'convex/**'],
    // MSW setup - this file is created in G-H.5-B
    // Comment out initially, uncomment after G-H.5-B is done:
    // setupFiles: ['./tests/setup.ts'],
  },
});
```

**Files to create:**

- `vitest.config.ts` (at repo root)

**References:**

- `package.json:6-9` - workspaces: `apps/*`, `packages/*` (convex and tests/ not included)
- Root devDependencies already has `vitest: ^1.6.0`

**Acceptance Criteria:**

- [ ] `vitest.config.ts` exists at root with explicit include globs
- [ ] `bunx vitest run` executes and discovers tests in packages + contracts
- [ ] Config includes `apps/web`, `packages/*`, `tests/contracts` but NOT `convex/`
- [ ] Config has commented placeholder for `setupFiles` (to be enabled after G-H.5-B)

**Commit:** YES

- Message: `test(infra): add vitest configuration`
- Files: `vitest.config.ts`

---

### G-H.1-A: Add first real TS tests (AFTER G-H.3-C, G-H.4-A)

**What to do:**

- After schemas exist, add tests:
  - `packages/validation/src/__tests__/schemas.test.ts` - test the zod schemas
- After vitest config exists, verify tests run

**Files to create:**

- `packages/validation/src/__tests__/schemas.test.ts`

**References:**

- `packages/validation/src/schemas/*.ts` - schemas to test (from G-H.3-C)
- `vitest.config.ts` - config (from G-H.4-A)

**Acceptance Criteria:**

- [ ] `packages/validation/src/__tests__/schemas.test.ts` exists
- [ ] `bunx vitest run` shows test count > 0
- [ ] Tests actually validate something (not just placeholders)

**Commit:** YES

- Message: `test(validation): add schema validation tests`
- Files: `packages/validation/src/__tests__/schemas.test.ts`

---

### G-H.5-A: Add testing-library dependencies (AFTER G-H.4-A)

**What to do:**

- Add to `apps/web/package.json` devDependencies:
  - `@testing-library/react`
  - `@testing-library/jest-dom`
  - `@testing-library/user-event`
  - `jsdom`
- Create `apps/web/vitest.config.ts` with jsdom environment (for React component tests)
- Note: This config extends root config but sets jsdom environment for DOM testing

**Files to create:**

- `apps/web/vitest.config.ts`

**Files to modify:**

- `apps/web/package.json` (devDependencies)

**References:**

- `apps/web/package.json:27-33` - current devDependencies

**Acceptance Criteria:**

- [ ] Testing-library packages in `apps/web/package.json` devDependencies
- [ ] `apps/web/vitest.config.ts` exists with `environment: 'jsdom'`
- [ ] `bunx vitest run` from root discovers web tests (when they exist)

**Commit:** YES

- Message: `deps(web): add testing-library for component tests`
- Files: `apps/web/package.json`, `apps/web/vitest.config.ts`

---

### G-H.3-B: Implement minimal Convex schema and function (PREREQ for msw)

**What to do:**

- Replace `convex/schema.ts` placeholder with actual schema (at least `notes` table)
- Create one minimal Convex function in `convex/functions/`:
  - `convex/functions/notes.ts` with a simple query (e.g., `listNotes`)
- This provides an actual API surface for msw to mock

**Notes table field specification** (based on docs/schema-audit.md NoteEntity):

```typescript
// convex/schema.ts
import { defineSchema, defineTable } from 'convex/server';
import { v } from 'convex/values';

export default defineSchema({
  notes: defineTable({
    noteId: v.string(), // UUID string
    ownerUserId: v.string(), // Clerk user ID
    title: v.string(),
    createdAt: v.number(), // Unix ms timestamp
    updatedAt: v.number(), // Unix ms timestamp
    deletedAt: v.optional(v.number()), // Unix ms timestamp, absent = not deleted
    // Note: folderId is NOT included - it's local-only per docs/schema-audit.md
  })
    .index('by_owner', ['ownerUserId'])
    .index('by_noteId', ['noteId']),
});
```

**Notes query function specification:**

```typescript
// convex/functions/notes.ts
import { query } from '../_generated/server';

export const list = query({
  args: {},
  handler: async (ctx) => {
    // For now, return all notes (auth will be added later)
    return await ctx.db.query('notes').collect();
  },
});
```

**Expected query return shape** (matches NoteSchema from G-H.3-C):

```typescript
// Each note returned by list() has this shape:
{
  _id: Id<"notes">,           // Convex internal ID
  _creationTime: number,       // Convex internal
  noteId: string,              // Our UUID
  ownerUserId: string,
  title: string,
  createdAt: number,
  updatedAt: number,
  deletedAt?: number,          // Absent if not deleted
}
```

**Files to create:**

- `convex/functions/notes.ts`

**Files to modify:**

- `convex/schema.ts` (currently `export default {}`)

**References:**

- `docs/architecture/data-model.md` - canonical data model (table list)
- `docs/schema-audit.md` - canonical field definitions for NoteEntity
- `convex/README.md` - planned structure
- Context7: `/get-convex/convex-backend` for schema syntax

**Acceptance Criteria:**

- [ ] `convex/schema.ts` defines `notes` table with fields: noteId, ownerUserId, title, createdAt, updatedAt, deletedAt
- [ ] `convex/functions/notes.ts` exports `list` query
- [ ] No TypeScript errors in convex directory
- [ ] Query return shape matches NoteSchema (minus Convex internal fields)
- [ ] **Function path verified**: After running `bunx convex codegen`, check `convex/_generated/api.ts` contains `functions: { notes: { list: ... } }` - this confirms the path is `functions/notes:list`

**Commit:** YES

- Message: `feat(convex): add initial schema and notes query`
- Files: `convex/schema.ts`, `convex/functions/notes.ts`

---

### G-H.5-B: Add msw for API mocking (AFTER G-H.3-B)

**What to do:**

- Add `msw` to root `package.json` devDependencies
- Create mock handler scaffold that can intercept Convex client calls
- Wire MSW to Vitest via setup file
- Create a proof-of-wiring test that verifies MSW is intercepting requests

**Convex HTTP API Format Reference:**

Per Convex documentation (https://docs.convex.dev/http-api), the HTTP API uses:

- Endpoint: `POST https://<deployment>.convex.cloud/api/query` (or `/api/mutation`)
- Request body: `{ "path": "functionPath", "args": {...}, "format": "json" }`
- Response: `{ "status": "success", "value": ... }` or `{ "status": "error", "errorMessage": "..." }`

**EXACT Convex function path format (CANONICAL):**

Convex function paths use colon notation: `{file}:{function}`

Examples:

- `functions/notes.ts` with `export const list = query(...)` → path is `functions/notes:list`
- If using default export or different structure, verify by checking Network tab

**For this plan, use:** `functions/notes:list` (matches the function created in G-H.3-B)

**MSW-Vitest Wiring (CRITICAL):**

1. Create `tests/mocks/server.ts` with MSW setup:

```typescript
// tests/mocks/server.ts
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);
```

2. Create `tests/setup.ts` vitest setup file:

```typescript
// tests/setup.ts
import { beforeAll, afterEach, afterAll } from 'vitest';
import { server } from './mocks/server';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

3. Update `vitest.config.ts` to use setup file:

```typescript
// vitest.config.ts (add to existing config)
export default defineConfig({
  test: {
    setupFiles: ['./tests/setup.ts'],
    // ... existing config
  },
});
```

**What makes requests hit `/api/query`:**

- The Convex React client (`useQuery`, `useMutation`) internally makes HTTP POST requests to the Convex deployment's `/api/query` and `/api/mutation` endpoints
- MSW intercepts these at the network level when running in Node.js (vitest)
- The handler routes based on `body.path` which contains the Convex function path

**Handler implementation:**

```typescript
// tests/mocks/handlers.ts
import { http, HttpResponse } from 'msw';

// Convex HTTP API format: POST /api/query with body { path, args, format }
// Function paths use colon notation: "functions/notes:list"
export const handlers = [
  http.post('*/api/query', async ({ request }) => {
    const body = (await request.json()) as { path: string; args: unknown };

    // Route based on exact function path
    if (body.path === 'functions/notes:list') {
      return HttpResponse.json({
        status: 'success',
        value: [
          {
            noteId: '550e8400-e29b-41d4-a716-446655440000',
            ownerUserId: 'user_test123',
            title: 'Test Note',
            createdAt: 1708300800000,
            updatedAt: 1708300800000,
            // deletedAt omitted = not deleted (absent OR number semantics)
          },
        ],
      });
    }

    // Fail explicitly for unknown paths (helps debugging)
    return HttpResponse.json(
      { status: 'error', errorMessage: `Unknown query path: ${body.path}` },
      { status: 400 },
    );
  }),

  http.post('*/api/mutation', async ({ request }) => {
    const body = (await request.json()) as { path: string; args: unknown };
    // Add mutation handlers as needed
    return HttpResponse.json({ status: 'success', value: null });
  }),
];
```

**Proof-of-wiring test (REQUIRED):**

```typescript
// tests/mocks/__tests__/msw-wiring.test.ts
import { describe, it, expect } from 'vitest';

describe('MSW wiring', () => {
  it('intercepts Convex query requests', async () => {
    // Direct fetch to verify MSW is intercepting
    // In real tests, this would be the Convex client making this call
    const response = await fetch('https://test.convex.cloud/api/query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: 'functions/notes:list',
        args: {},
        format: 'json',
      }),
    });

    const data = await response.json();
    expect(data.status).toBe('success');
    expect(data.value).toHaveLength(1);
    expect(data.value[0].noteId).toBe('550e8400-e29b-41d4-a716-446655440000');
  });

  it('returns error for unknown paths', async () => {
    const response = await fetch('https://test.convex.cloud/api/query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: 'unknown:function',
        args: {},
        format: 'json',
      }),
    });

    const data = await response.json();
    expect(data.status).toBe('error');
    expect(data.errorMessage).toContain('Unknown query path');
  });
});
```

**Files to create:**

- `tests/mocks/handlers.ts` - MSW handlers for Convex endpoints
- `tests/mocks/server.ts` - MSW server setup for Node.js tests
- `tests/setup.ts` - Vitest setup file that starts/stops MSW server
- `tests/mocks/__tests__/msw-wiring.test.ts` - Proof-of-wiring test

**Files to modify:**

- `package.json` (root, devDependencies)
- `vitest.config.ts` (add setupFiles)

**References:**

- `convex/functions/notes.ts` - the function to mock (from G-H.3-B)
- Convex HTTP API docs: https://docs.convex.dev/http-api
- MSW v2 docs: https://mswjs.io/docs/getting-started
- MSW Node.js integration: https://mswjs.io/docs/integrations/node

**Acceptance Criteria:**

- [ ] `msw` in root devDependencies
- [ ] `tests/mocks/handlers.ts` exists with handler for `*/api/query`
- [ ] Handler uses EXACT path format: `functions/notes:list`
- [ ] `tests/mocks/server.ts` exports `setupServer` for test use
- [ ] `tests/setup.ts` exists and wires MSW lifecycle to vitest
- [ ] `vitest.config.ts` has `setupFiles: ['./tests/setup.ts']`
- [ ] `tests/mocks/__tests__/msw-wiring.test.ts` exists
- [ ] `bunx vitest run tests/mocks` passes (proof of wiring works)

**Commit:** YES

- Message: `deps: add msw for API mocking in tests`
- Files: `package.json`, `tests/mocks/**`

---

### G-H.7-A: Correct notepad documentation (Wave 1 - first!)

**What to do:**

- Update `.sisyphus/notepads/comprehensive-app-overhaul/feature-flags-catalog.md`:
  - Change "Status: IMPLEMENTED" to "Status: NOT IMPLEMENTED (PLANNED)"
  - Change "Files Created" section to "Files To Create"
  - Note that actual implementation is tracked in G-0.2-A
- Update `.sisyphus/notepads/device-validation/TASK-6.3-STATUS.md`:
  - Change "Feature Flags (Verified) - Implemented" to "Feature Flags - NOT IMPLEMENTED"
  - Reference this plan for actual status
- **ALSO**: Fix the broken reference in the authoritative plan:
  - `.sisyphus/plans/comprehensive-app-overhaul.md` line 215 references wrong path
  - Change `.sisyphus/notepads/feature-flags-catalog.md` to `.sisyphus/notepads/comprehensive-app-overhaul/feature-flags-catalog.md`

**Files to modify:**

- `.sisyphus/notepads/comprehensive-app-overhaul/feature-flags-catalog.md`
- `.sisyphus/notepads/device-validation/TASK-6.3-STATUS.md`
- `.sisyphus/plans/comprehensive-app-overhaul.md` (line 215 - fix reference path)

**Acceptance Criteria:**

- [ ] Notepads accurately reflect reality (feature flags NOT implemented)
- [ ] No false claims about existing files/functionality
- [ ] Authoritative plan reference path is correct (verified by `grep "notepads/comprehensive-app-overhaul/feature-flags" .sisyphus/plans/comprehensive-app-overhaul.md`)

**Commit:** YES

- Message: `docs: correct feature-flags notepad to reflect actual state`
- Files: `.sisyphus/notepads/**/*.md`, `.sisyphus/plans/comprehensive-app-overhaul.md`

---

### G-H.2-B: Add Android CI job

**What to do:**

- Add new job to `.github/workflows/ci.yml`:
  - Name: `android`
  - Setup: JDK 17 + Android SDK with license acceptance
  - Steps: `bun run android:lint`, `bun run android:test`, `bun run ktlint`, `bun run detekt`
- Current CI (lines 8-31) only has a `build` job for web/TS checks
- New `android` job should run in parallel with existing `build` job

**Files to modify:**

- `.github/workflows/ci.yml`

**References:**

- `.github/workflows/ci.yml` - current workflow (single `build` job, lines 8-31)
- `apps/android/README.md` - Java/SDK requirements (JDK 17+)
- `package.json:27-30` - Android script definitions (`android:build`, `android:test`, `android:lint`, `ktlint`, `detekt`)

**Complete Android job specification** (use exactly this):

```yaml
android:
  runs-on: ubuntu-latest
  steps:
    - name: Checkout
      uses: actions/checkout@v4

    - name: Setup Java
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '17'

    - name: Setup Android SDK
      uses: android-actions/setup-android@v3

    - name: Accept Android SDK licenses
      run: yes | sdkmanager --licenses || true

    - name: Setup Bun
      uses: oven-sh/setup-bun@v2

    - name: Install dependencies
      run: bun install

    - name: Android Lint
      run: bun run android:lint

    - name: Android Unit Tests
      run: bun run android:test

    - name: ktlint
      run: bun run ktlint

    - name: detekt
      run: bun run detekt
```

**Why these specific actions:**

- `actions/setup-java@v4` with `temurin` distribution: Free, well-maintained JDK
- `android-actions/setup-android@v3`: Official community action for Android SDK setup on GitHub Actions
- License acceptance step: Required for SDK components to work

**Acceptance Criteria:**

- [ ] `.github/workflows/ci.yml` is valid YAML
- [ ] CI workflow has `android` job with JDK 17 + Android SDK setup
- [ ] Android job runs: android:lint, android:test, ktlint, detekt
- [ ] Push to branch triggers Android checks (verify in Actions tab)
- [ ] Job completes successfully (not stuck on SDK license prompts)

**Commit:** YES

- Message: `ci: add Android quality gates to PR workflow`
- Files: `.github/workflows/ci.yml`

---

### G-H.1-B: Implement contract test fixtures (AFTER G-H.3-C validation schemas)

**Contract Layer Clarification (CRITICAL)**

Fixtures are JSON files representing the **API contract** (same layer as zod schemas).
They must be parseable by zod schemas from `@onyx/validation`.

**JSON Shape Definition:**

Fixtures follow `docs/schema-audit.md` field names with JSON-safe types:

```json
// note.fixture.json example (not deleted - omit deletedAt field)
{
  "noteId": "550e8400-e29b-41d4-a716-446655440000",
  "ownerUserId": "user_123",
  "title": "Test Note",
  "createdAt": 1708300800000,
  "updatedAt": 1708300800000
}

// note-deleted.fixture.json example (deleted - include deletedAt)
{
  "noteId": "550e8400-e29b-41d4-a716-446655440001",
  "ownerUserId": "user_123",
  "title": "Deleted Note",
  "createdAt": 1708300800000,
  "updatedAt": 1708300800000,
  "deletedAt": 1708387200000
}

// stroke.fixture.json example (metadata only, no strokeData ByteArray)
{
  "strokeId": "550e8400-e29b-41d4-a716-446655440001",
  "pageId": "550e8400-e29b-41d4-a716-446655440002",
  "style": {
    "tool": "pen",
    "color": "#000000",
    "baseWidth": 2.0,
    "minWidthFactor": 0.5,
    "maxWidthFactor": 1.5,
    "nibRotation": false
  },
  "bounds": { "x": 0, "y": 0, "w": 100, "h": 50 },
  "createdAt": 1708300800000,
  "createdLamport": 1
}
```

**What to do:**

- Create JSON fixtures in `tests/contracts/fixtures/`:
  - `note.fixture.json` - follows NoteSchema
  - `page.fixture.json` - follows PageSchema
  - `stroke.fixture.json` - follows StrokeSchema (metadata only)
- Create test file `tests/contracts/src/schema-validation.test.ts`:
  - Import schemas from `@onyx/validation`
  - Load fixtures via fs/import
  - Parse with zod `.parse()` and assert no errors
- Optionally create `tests/contracts/package.json` if needed for workspace

**Files to create:**

- `tests/contracts/fixtures/note.fixture.json`
- `tests/contracts/fixtures/page.fixture.json`
- `tests/contracts/fixtures/stroke.fixture.json`
- `tests/contracts/src/schema-validation.test.ts`
- `tests/contracts/package.json` (if needed for workspace)

**References:**

- `packages/validation/src/schemas/*.ts` - schemas to validate against (from G-H.3-C)
- `tests/contracts/README.md` - contract test docs
- `docs/schema-audit.md` - **PRIMARY**: Canonical JSON field shapes and types

**Acceptance Criteria:**

- [ ] Fixtures are valid JSON and match zod schemas from `@onyx/validation`
- [ ] `bunx vitest run tests/contracts` passes
- [ ] Test uses `.parse()` (not `.safeParse()`) so schema mismatches throw errors
- [ ] Schema changes that break fixtures cause test failures

**Commit:** YES

- Message: `test: implement contract test fixtures for schema validation`
- Files: `tests/contracts/**`

---

### G-H.2-C: Add Android instrumentation CI workflow

**What to do:**

- Create `.github/workflows/android-instrumentation.yml`
- Configure triggers: `workflow_dispatch`, optional schedule
- Use `reactivecircus/android-emulator-runner` action
- Run `connectedDebugAndroidTest`

**Complete workflow specification:**

```yaml
# .github/workflows/android-instrumentation.yml
name: Android Instrumentation Tests

on:
  workflow_dispatch:
    inputs:
      api_level:
        description: 'Android API level'
        required: false
        default: '30'
        type: choice
        options:
          - '29'
          - '30'
          - '31'
          - '33'
  # Optional: schedule for nightly runs
  # schedule:
  #   - cron: '0 2 * * *'

jobs:
  instrumentation:
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Accept Android SDK licenses
        run: yes | sdkmanager --licenses || true

      - name: Setup Bun
        uses: oven-sh/setup-bun@v2

      - name: Install dependencies
        run: bun install

      - name: Enable KVM (for emulator performance)
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run instrumentation tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ github.event.inputs.api_level || '30' }}
          arch: x86_64
          target: google_apis
          profile: pixel_5
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim
          disable-animations: true
          script: |
            cd apps/android
            ./gradlew :app:connectedDebugAndroidTest --stacktrace 2>&1 | tee instrumentation-output.log

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: instrumentation-test-results
          path: |
            apps/android/app/build/reports/androidTests/
            apps/android/instrumentation-output.log
          retention-days: 14
```

**Emulator configuration rationale:**

- `api-level: 30` - Android 11, good balance of compatibility and features
- `arch: x86_64` - Fastest emulation on GitHub runners
- `target: google_apis` - Includes Google APIs needed for some tests
- `profile: pixel_5` - Standard device profile
- `emulator-options`: Headless mode for CI, swiftshader for software rendering

**Files to create:**

- `.github/workflows/android-instrumentation.yml`

**References:**

- `.github/workflows/ci.yml` - base workflow patterns
- `apps/android/DEVICE-TESTING.md` - device test guidance
- reactivecircus/android-emulator-runner docs: https://github.com/ReactiveCircus/android-emulator-runner

**Acceptance Criteria:**

- [ ] Workflow file exists and is valid YAML (validate: `bunx yaml-lint .github/workflows/android-instrumentation.yml` OR use GitHub's syntax highlighting to confirm no red squiggles)
- [ ] Manual trigger works (`workflow_dispatch`)
- [ ] Emulator boots (check for "Boot completed" in logs)
- [ ] `connectedDebugAndroidTest` runs (may have failures, but runs)
- [ ] Artifacts uploaded: `instrumentation-test-results` contains HTML report or log
- [ ] Evidence: `.sisyphus/evidence/G-H.2-C-01-workflow-run.png` screenshot of successful workflow run

**Commit:** YES

- Message: `ci: add Android instrumentation test workflow`
- Files: `.github/workflows/android-instrumentation.yml`

---

### G-H.1-C: Create shared test utilities package

**What to do:**

- Create `packages/test-utils/` with standard package structure
- Add to workspace

**Files to create:**

- `packages/test-utils/package.json`
- `packages/test-utils/src/index.ts`
- `packages/test-utils/src/factories/note.ts` - factory for test notes
- `packages/test-utils/tsconfig.json`

**References:**

- `packages/validation/` - example package structure

**Acceptance Criteria:**

- [ ] `packages/test-utils` exists and builds
- [ ] Other packages can import from `@onyx/test-utils`
- [ ] At least one factory function exists

**Commit:** YES

- Message: `feat(test-utils): add shared test utilities package`
- Files: `packages/test-utils/**`

---

### G-H.3-A: Remove passWithNoTests masking (AFTER G-H.1-A)

**What to do:**

- After real tests exist, remove `--passWithNoTests` from test scripts
- For packages that still have no tests, change to `"test": "exit 1"` with a clear error message:
  ```json
  "test": "echo 'ERROR: No tests implemented for this package yet' && exit 1"
  ```
- This ensures test debt is visible and fails CI until addressed

**Files to modify:**

- `apps/web/package.json:15`
- `packages/contracts/package.json:12`
- `packages/shared/package.json:12`
- `packages/ui/package.json:12`
- `packages/config/package.json:12`
- `packages/validation/package.json:15`

**Acceptance Criteria:**

- [ ] No `--passWithNoTests` flags remain (verified: `grep -r "passWithNoTests" apps/ packages/` returns 0 matches)
- [ ] Packages with tests: `bun run test` passes with actual test execution
- [ ] Packages without tests: script is `exit 1` to make test debt visible
- [ ] CI catches missing tests (packages with `exit 1` fail the pipeline)
- [ ] Test debt is visible in output

**Commit:** YES

- Message: `chore: remove passWithNoTests flags`
- Files: Multiple `package.json` files

---

### G-H.4-B: Document dev workflows

**What to do:**

- Create `docs/development/getting-started.md`
- Document:
  - Setup steps (bun install, env vars)
  - How to run tests (`bun run test`, `bun run android:test`)
  - How to run lints (`bun run lint`, `bun run android:lint`)
  - Note that web app dev currently is `tsc -w` (no server)

**Files to create:**

- `docs/development/getting-started.md`

**References:**

- `README.md` - current commands
- `AGENTS.md` - agent guidance

**Acceptance Criteria:**

- [ ] `docs/development/getting-started.md` exists
- [ ] New contributor can follow steps to run tests
- [ ] Current limitations (web scaffold state) documented

**Commit:** YES

- Message: `docs: add development getting started guide`
- Files: `docs/development/getting-started.md`

---

### G-H.5-C: Evaluate Robolectric for Android

**What to do:**

- Research Robolectric compatibility
- If beneficial, add to `apps/android/app/build.gradle.kts`
- Convert one test as proof of concept
- Document decision

**Files to potentially modify:**

- `apps/android/app/build.gradle.kts` (testImplementation)
- One test file conversion

**References:**

- `apps/android/app/build.gradle.kts:155-169` - current test dependencies
- `apps/android/app/src/test/` - unit tests (33 Kotlin test files)

**Acceptance Criteria:**

- [ ] Decision documented with pros/cons
- [ ] If added: gradle dependency and one converted test
- [ ] `bun run android:test` still passes

**Commit:** YES (if adding)

- Message: `test(android): evaluate/add Robolectric for unit tests`

---

### G-H.8-A: Fix database name in verification scripts

**What to do:**

- The actual database name is `onyx_notes.db` (defined in `OnyxDatabase.kt:57`)
- But verification scripts reference `onyx.db` which does not exist
- Fix all occurrences to use correct database name

**Files to modify:**

- `apps/android/verify-on-device.sh` (line 112)
- `apps/android/DEVICE-TESTING.md` (line 110)
- `docs/device-blocker.md` (line 325)

**Exact changes:**

```bash
# WRONG (current):
/data/data/com.onyx.android/databases/onyx.db

# CORRECT (fix to):
/data/data/com.onyx.android/databases/onyx_notes.db
```

**References:**

- `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt:57` - actual DB name: `DATABASE_NAME = "onyx_notes.db"`

**Acceptance Criteria:**

- [ ] `grep -r "onyx\.db" apps/android docs/` returns 0 matches
- [ ] `grep -r "onyx_notes\.db" apps/android docs/` returns matches in verification files
- [ ] `./verify-on-device.sh` database query step works on device

**Commit:** YES

- Message: `fix(docs): correct database filename in verification scripts`
- Files: `apps/android/verify-on-device.sh`, `apps/android/DEVICE-TESTING.md`, `docs/device-blocker.md`

---

## Verification Checklist

### Minimum verification for every wave:

```bash
# TypeScript/Web
bun run lint
bun run typecheck
bun run test

# Android
bun run android:lint
bun run android:test
bun run ktlint
bun run detekt
```

### Wave 0 complete verification:

```bash
# Verify vitest works
bunx vitest run  # Should show test count > 0

# Verify turbo cache invalidation
bun run build
touch .env
bun run build  # Should show MISS, not HIT

# Verify web typecheck (not dev server - that's scaffold)
bun run typecheck --filter=@onyx/web  # Should pass
```

### Additional required verification before calling the overhaul complete:

```bash
cd apps/android && node ../../scripts/gradlew.js :app:connectedDebugAndroidTest
cd apps/android && ./verify-on-device.sh
```

---

## Exit Rule

This gap-closure plan is done only when:

1. Each feature gap ID (G-0._ through G-7._) is either:
   - closed by code + test evidence in repository, or
   - explicitly marked as non-code blocked with documented risk acceptance.

2. Each codebase health gap ID (G-H.\*) is either:
   - completed with artifacts in repository, or
   - explicitly deferred with rationale documented.

3. Documentation matches reality (no false claims in notepads).

4. All verification commands pass.

5. CI pipeline enforces all quality gates on PRs.

---

## Evidence Artifact Conventions

**All device-based and visual verifications MUST store evidence artifacts.**

**Location:** `.sisyphus/evidence/`

**Naming Convention:**

| Evidence Type   | Filename Pattern                    | Example                               |
| --------------- | ----------------------------------- | ------------------------------------- |
| Screenshot      | `{gap-id}-{step}-{description}.png` | `G-1.2-A-01-pen-up-handoff.png`       |
| Video capture   | `{gap-id}-{step}-{description}.mp4` | `G-2.4-A-01-no-black-flash.mp4`       |
| Log output      | `{gap-id}-{step}-{description}.log` | `G-2.2-A-01-p95-benchmark.log`        |
| Terminal output | `{gap-id}-{step}-{description}.txt` | `G-6.3-A-01-connectedTest-output.txt` |

**Required for these gap types:**

- G-1.2-A (pen-up handoff) - slow-motion video showing no visible stroke gap
- G-2.4-A (visual continuity) - video showing no black flash during PDF transitions
- G-2.2-A (scheduler/perf) - benchmark log showing p95 < 120ms
- G-3.2-A (accessibility) - Accessibility Scanner JSON report (see detailed procedure below)
- G-6.3-A (device blocker closure) - `connectedDebugAndroidTest` output log
- Any task with "video capture" or "screenshot" in acceptance criteria

**G-3.2-A Accessibility Verification Procedure (DETAILED):**

Tool: **Google Accessibility Scanner** (Android app)

- Install: `adb install` from Play Store or APK
- Package: `com.google.android.apps.accessibility.auditor`

Steps:

1. Install Accessibility Scanner on test device
2. Enable the scanner in Android Settings > Accessibility
3. Open Onyx app and navigate to NoteEditorUi (toolbar visible)
4. Tap the floating Accessibility Scanner button
5. Scanner analyzes current screen

Pass criteria:

- [ ] All touch targets ≥ 48x48dp (scanner shows no "Touch target" warnings)
- [ ] Sufficient color contrast (scanner shows no "Contrast" warnings)
- [ ] Content labels present (scanner shows no "Content labeling" warnings)

Evidence artifacts:

- `.sisyphus/evidence/G-3.2-A-01-accessibility-scanner-results.png` - screenshot of scanner results
- `.sisyphus/evidence/G-3.2-A-02-toolbar-screenshot.png` - screenshot of toolbar for reference

Alternative tool (if Accessibility Scanner unavailable):

- Use Android Studio Layout Inspector to measure touch target sizes
- Manually verify each toolbar button is ≥ 48x48dp
- Document measurements in `.sisyphus/evidence/G-3.2-A-01-touch-targets.txt`

**Example evidence capture workflow:**

```bash
# For video evidence (slow-motion pen-up test)
adb shell screenrecord /sdcard/test.mp4 &
# perform test actions
adb shell killall screenrecord
adb pull /sdcard/test.mp4 .sisyphus/evidence/G-1.2-A-01-pen-up-handoff.mp4

# For screenshot evidence
adb exec-out screencap -p > .sisyphus/evidence/G-3.2-A-01-toolbar-accessibility.png

# For test output evidence
bun run android:test 2>&1 | tee .sisyphus/evidence/G-6.3-A-01-unit-tests.log
```

**Audit checklist for "done" verification:**

- [ ] Evidence file exists at expected path
- [ ] Filename matches naming convention
- [ ] File is non-empty and valid (can open video/image/read log)
- [ ] Content demonstrates the acceptance criterion being met

---

## Appendix: File Reference Map

### Files to Create

| Path                                                | Gap ID  | Purpose                            |
| --------------------------------------------------- | ------- | ---------------------------------- |
| `vitest.config.ts`                                  | G-H.4-A | Root vitest config                 |
| `packages/validation/src/schemas/*.ts`              | G-H.3-C | Zod schemas (prereq for tests)     |
| `packages/validation/src/schemas/common.ts`         | G-H.3-C | Supporting schemas (Style, Bounds) |
| `packages/validation/src/__tests__/schemas.test.ts` | G-H.1-A | Schema tests                       |
| `apps/web/vitest.config.ts`                         | G-H.5-A | Web vitest config with jsdom       |
| `convex/functions/notes.ts`                         | G-H.3-B | Minimal Convex function            |
| `tests/mocks/handlers.ts`                           | G-H.5-B | MSW mock handlers                  |
| `tests/mocks/server.ts`                             | G-H.5-B | MSW server setup                   |
| `tests/setup.ts`                                    | G-H.5-B | Vitest setup file for MSW          |
| `tests/mocks/__tests__/msw-wiring.test.ts`          | G-H.5-B | Proof-of-wiring test               |
| `tests/contracts/fixtures/*.json`                   | G-H.1-B | Contract test fixtures             |
| `tests/contracts/src/schema-validation.test.ts`     | G-H.1-B | Contract validation tests          |
| `packages/test-utils/**`                            | G-H.1-C | Shared test utilities              |
| `.github/workflows/android-instrumentation.yml`     | G-H.2-C | Android emulator CI                |
| `docs/development/getting-started.md`               | G-H.4-B | Dev workflow docs                  |
| `.sisyphus/evidence/`                               | Various | Evidence artifact directory        |

### Files to Modify

| Path                                                                     | Gap ID           | Change                                       |
| ------------------------------------------------------------------------ | ---------------- | -------------------------------------------- |
| `turbo.json`                                                             | G-H.2-A          | Add `["$TURBO_DEFAULT$", ".env*"]` to inputs |
| `.github/workflows/ci.yml`                                               | G-H.2-B          | Add Android job with SDK setup               |
| `apps/web/package.json`                                                  | G-H.5-A, G-H.6-A | Add deps                                     |
| `package.json` (root)                                                    | G-H.5-B          | Add msw                                      |
| `vitest.config.ts`                                                       | G-H.5-B          | Add setupFiles for MSW                       |
| `convex/schema.ts`                                                       | G-H.3-B          | Replace placeholder with real schema         |
| `packages/validation/src/index.ts`                                       | G-H.3-C          | Export schemas                               |
| `.sisyphus/plans/comprehensive-app-overhaul.md`                          | G-H.7-A          | Fix notepad reference path (line 215)        |
| `.sisyphus/notepads/comprehensive-app-overhaul/feature-flags-catalog.md` | G-H.7-A          | Correct false claims                         |
| `.sisyphus/notepads/device-validation/TASK-6.3-STATUS.md`                | G-H.7-A          | Correct false claims                         |
| Multiple `package.json` files                                            | G-H.3-A          | Remove passWithNoTests                       |
| `apps/android/verify-on-device.sh`                                       | G-H.8-A          | Fix DB name: `onyx.db` → `onyx_notes.db`     |
| `apps/android/DEVICE-TESTING.md`                                         | G-H.8-A          | Fix DB name: `onyx.db` → `onyx_notes.db`     |
| `docs/device-blocker.md`                                                 | G-H.8-A          | Fix DB name: `onyx.db` → `onyx_notes.db`     |

### Verified Existing Files (Referenced)

| Path                                                                                            | Verification                                 |
| ----------------------------------------------------------------------------------------------- | -------------------------------------------- |
| `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt:68`                        | Contains `ENABLE_MOTION_PREDICTION = true`   |
| `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt:265`                          | Contains `requireAppContainer()`             |
| `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt:103`                    | Contains `requireAppContainer()`             |
| `apps/android/app/src/test/java/com/onyx/android/pdf/PdfTileCacheTest.kt`                       | Exists (33 Kotlin test files total)          |
| `apps/android/app/src/androidTest/java/com/onyx/android/ui/PdfBucketCrossfadeContinuityTest.kt` | Exists                                       |
| `packages/validation/src/index.ts`                                                              | Contains `export {}` (empty)                 |
| `convex/schema.ts`                                                                              | Contains `export default {}` (placeholder)   |
| `convex/functions/.gitkeep`                                                                     | Only file in functions/                      |
| `turbo.json:4`                                                                                  | Has `globalDependencies: [".env"]`           |
| `apps/android/app/src/main/java/com/onyx/android/data/OnyxDatabase.kt:57`                       | `DATABASE_NAME = "onyx_notes.db"` (verified) |
