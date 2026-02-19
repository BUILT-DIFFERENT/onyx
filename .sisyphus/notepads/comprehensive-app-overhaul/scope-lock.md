# Scope Lock: Comprehensive App Overhaul

Date: 2026-02-17
Status: LOCKED
Authority: This document freezes scope boundaries and maps prior milestone plans to the authoritative implementation plan.

---

## 1. Authority Hierarchy (Source of Truth Chain)

| Rank | Document                                             | Role                                                           |
| ---- | ---------------------------------------------------- | -------------------------------------------------------------- |
| 1    | `docs/architecture/comprehensive-app-change-plan.md` | Top-level sequencing authority (roadmap)                       |
| 2    | `.sisyphus/plans/comprehensive-app-overhaul.md`      | Authoritative implementation plan (tasks, acceptance criteria) |
| 3    | Prior milestone plans (see §2)                       | Reference detail only - superseded where conflicts exist       |
| 4    | Context deep-research reports in `docs/context/`     | Background research - informative only                         |

**Rule**: If any prior milestone doc conflicts with the comprehensive plan, the comprehensive plan wins. Update conflicting prior docs to align or mark as superseded.

---

## 2. Prior Milestone Plans Mapping

### 2.1 Supersession Mapping Table

| Prior Plan                               | Status        | Tasks Covered by Comprehensive Plan                                                                                                                                                                                                                                                                                                                                | Tasks Deferred (Future Work)                                                                                                                                                      | Notes                                                                                                                  |
| ---------------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `milestone-a-offline-ink-myscript.md`    | CODE-COMPLETE | All core tasks complete. Device validation pending.                                                                                                                                                                                                                                                                                                                | N/A                                                                                                                                                                               | Forms baseline for overhaul. 8 tasks blocked on physical device verification.                                          |
| `milestone-canvas-rendering-overhaul.md` | SUPERSEDED    | P1: Ink latency (Tasks 1.1-1.5 cover prediction, handoff, styles, transforms, spatial index). P2: PDF hardening (Tasks 2.1-2.5 cover Pdfium adapter, tile scheduler, cache lifecycle, visual continuity, text selection). P3: Multi-page navigation (partial - via PDF interaction parity). P4: Home/Library (Tasks 4.1-4.3 cover folders, thumbnails, batch ops). | Infinite canvas (deferred to post-core). Stroke compression >1000 points. Segment eraser (Phase 7). Lasso transforms (Phase 7). Template system polish (Phase 7).                 | Detailed technical specs preserved as reference. Non-goals in Appendix D now tracked in comprehensive plan Phases 6-7. |
| `milestone-ui-overhaul-samsung-notes.md` | SUPERSEDED    | Folder model + junction table (Task 4.1). Thumbnail generation (Task 4.2). Room-backed settings (Task 4.3). Toolbar responsiveness (Task 3.2). Pen settings panels (Task 1.3 style schema).                                                                                                                                                                        | S Pen button configuration. Color picker spectrum UI. Template picker UI. StylusPreferenceEntity. Hierarchical folder breadcrumbs + drag-drop. Floating/dockable toolbar.         | Design system tokens and UI specs preserved as reference for implementation detail.                                    |
| `milestone-av2-advanced-features.md`     | DEFERRED      | N/A (all tasks are advanced features)                                                                                                                                                                                                                                                                                                                              | Infinite canvas (2024×2024 tiles). Segment eraser with Lamport guard. Recognition overlay display. Lasso select + convert. TileEntity + TileManager. Local Lamport clock seeding. | Entire milestone deferred to post-core stability (Phase 7 or later). Requires Plan A baseline validated first.         |
| `milestone-b-web-viewer.md`              | OUT OF SCOPE  | Compatibility checks only (verify web viewer still renders tiles from Convex)                                                                                                                                                                                                                                                                                      | Web editing. Convex backend implementation. Clerk auth on web. Tile-based PDF rendering on web.                                                                                   | Entire milestone is web-specific. Comprehensive plan focuses on Android editor overhaul.                               |
| `milestone-c-collaboration-sharing.md`   | OUT OF SCOPE  | N/A                                                                                                                                                                                                                                                                                                                                                                | Ops sync. Commits/timeline. Sharing + public links. Exports. Background jobs for snapshots.                                                                                       | Collaboration/sync backend explicitly out of scope for comprehensive plan.                                             |

### 2.2 Detailed Task Mapping

#### From `milestone-canvas-rendering-overhaul.md`

| Prior Task                    | Comprehensive Plan Coverage                          |
| ----------------------------- | ---------------------------------------------------- |
| P0.0-P0.4 Pdfium spike        | Task 2.1 (Pdfium integration lock and adapter layer) |
| P1.1 Motion prediction        | Task 1.1 (Official prediction path hardening)        |
| P1.2 Front-buffer rendering   | Task 1.2 (Pen-up handoff synchronization)            |
| P1.3 Stroke style evolution   | Task 1.3 (Stroke style schema evolution)             |
| P1.4 Transform engine         | Task 1.4 (Transform engine stabilization)            |
| P1.5 Spatial index            | Task 1.5 (Spatial index for selection)               |
| P2.1-P2.5 PDF pipeline        | Tasks 2.1-2.5 (PDF Engine Hardening phase)           |
| P3.1-P3.4 Multi-page          | Task 2.5 (PDF interaction parity - navigation)       |
| P4.0-P4.5 Schema + Home UX    | Tasks 4.1-4.3 (Library Organization phase)           |
| P5.1-P5.8 Polish + validation | Task 6.3 (Physical-device blocker closure)           |

#### From `milestone-ui-overhaul-samsung-notes.md`

| Prior Task                      | Comprehensive Plan Coverage                          |
| ------------------------------- | ---------------------------------------------------- |
| FolderEntity + NoteFolderEntity | Task 4.1 (Folder and template data model)            |
| UserPreferenceEntity + DAOs     | Task 4.3 (Room-backed settings migration)            |
| Thumbnail generation            | Task 4.2 (Thumbnail generation and batch operations) |
| Batch move/delete/tag           | Task 4.2 (same)                                      |
| Editor toolbar decomposition    | Task 3.1 (Decompose NoteEditorUi.kt)                 |
| Responsive toolbar              | Task 3.2 (Responsive toolbar, touch targets)         |
| Home ViewModel extraction       | Task 3.3 (Home screen architecture cleanup)          |
| DI migration to Hilt            | Task 3.4 (DI migration to Hilt boundaries)           |

---

## 3. IN SCOPE Boundaries

### 3.1 Explicit IN Scope Items

| Domain                 | Coverage                                                                                                |
| ---------------------- | ------------------------------------------------------------------------------------------------------- |
| **Ink Rendering**      | Low-latency pipeline, motion prediction, pen-up handoff, stroke styles, smoothing, taper                |
| **Transforms**         | Frame-aligned updates, focal-point preservation, pinch/zoom/rotate stability                            |
| **PDF Pipeline**       | Pdfium integration, tile rendering, cache lifecycle, visual continuity, text selection, page navigation |
| **Editor UI**          | Toolbar decomposition, responsive layouts, touch targets, tool settings panels                          |
| **Home/Library**       | Folder model, thumbnails, batch operations, settings persistence                                        |
| **Recognition/Search** | MyScript pipeline hardening, unified search across handwriting + PDF + metadata                         |
| **Data Model**         | Room migrations, schema evolution, operation log scaffold                                               |
| **Quality Gates**      | Feature flags, CI gates, performance instrumentation, device validation                                 |

### 3.2 Covered by Phase

| Phase   | Scope Summary                                                 |
| ------- | ------------------------------------------------------------- |
| Phase 0 | Baseline, gates, feature flags, instrumentation, test harness |
| Phase 1 | Ink latency, transforms, style schema, spatial index          |
| Phase 2 | PDF engine hardening (Pdfium, tiles, cache, text selection)   |
| Phase 3 | UI architecture (decomposition, toolbar, Home cleanup, DI)    |
| Phase 4 | Library organization (folders, thumbnails, settings)          |
| Phase 5 | Recognition, conversion, unified search                       |
| Phase 6 | Reliability, release safety, rollout gates                    |
| Phase 7 | Advanced competitive features (post-core, optional)           |

---

## 4. OUT OF SCOPE Boundaries

### 4.1 Explicit OUT OF Scope Items

| Item                                                  | Reason                                              | Reference                                              |
| ----------------------------------------------------- | --------------------------------------------------- | ------------------------------------------------------ |
| Web viewer work                                       | Web is view-only in v0; overhaul focuses on Android | `comprehensive-app-change-plan.md:19`                  |
| Collaboration/sync backend                            | Convex runtime logic not part of editor overhaul    | `comprehensive-app-overhaul.md:42-43`                  |
| OEM-only SDK behaviors                                | Not broadly available                               | `comprehensive-app-overhaul.md:44`                     |
| Backward compatibility with older Android DB versions | Explicit policy: not required                       | `comprehensive-app-overhaul.md:45`, `AGENTS.md`        |
| Infinite canvas (2024×2024 tiles)                     | Post-core advanced feature                          | `milestone-av2-advanced-features.md`, Phase 7 optional |
| Segment eraser                                        | Post-core advanced feature                          | Phase 7 optional                                       |
| Lasso transforms                                      | Post-core advanced feature                          | Phase 7 optional                                       |
| Web editing                                           | v0 scope is view-only                               | `milestone-b-web-viewer.md`                            |
| Ops/commits/sharing                                   | Milestone C scope                                   | `milestone-c-collaboration-sharing.md`                 |

### 4.2 Non-Negotiable Guardrails (from Comprehensive Plan)

These rules are NOT open for discussion during implementation:

1. **Prediction integration**: Must use official MotionEventPredictor adapter + in-progress stroke integration
2. **Pen-up handoff**: Must avoid visible disappearance between wet and committed layers
3. **PDF tile lifecycle**: Must prevent draw/recycle races under cancellation and eviction
4. **Feature flags**: All high-risk work behind runtime flags with kill-switch behavior
5. **Commit hooks**: No bypass; all CI quality gates enforced
6. **Turborepo env hygiene**: All env vars in `turbo.json`, `.env*` in task inputs

---

## 5. Non-Goals (Explicit)

The following are explicitly NOT part of this overhaul:

1. **Hidden expansion into backend sync implementation** - Convex sync is Milestone C
2. **Unbounded redesign work without acceptance criteria** - All changes must tie to measurable goals
3. **Major module re-architecture** - Unless tied to stability/performance goals
4. **OEM-exclusive features in core release path** - Samsung-only, etc.
5. **Backward DB compatibility** - Per current policy

---

## 6. Alignment Verification Checklist

Before ANY implementation begins, verify:

- [x] Authority hierarchy confirmed (this document §1)
- [x] All prior milestone plans reviewed and mapped (§2)
- [x] IN SCOPE items explicitly documented (§3)
- [x] OUT OF SCOPE items explicitly documented (§4)
- [x] Non-negotiable guardrails documented (§4.2)
- [x] Non-goals documented (§5)
- [ ] Team sign-off obtained (pending)
- [ ] No implementation started before alignment complete

---

## 7. References

- `docs/architecture/comprehensive-app-change-plan.md` - Top-level roadmap
- `.sisyphus/plans/comprehensive-app-overhaul.md` - Authoritative task plan
- `docs/context/1-deep-research-report.md` - Ink/remediation research
- `docs/context/2-deep-research-report.md` - Transform/parity research
- `docs/device-blocker.md` - Physical device validation requirements
- `AGENTS.md` - Quality and env policy requirements

---

## 8. Amendment Log

| Date       | Change                              | Author             |
| ---------- | ----------------------------------- | ------------------ |
| 2026-02-17 | Initial scope lock document created | Task 0.1 execution |

---

**LOCK STATUS**: FROZEN

This document represents the locked scope for the comprehensive app overhaul. Any scope changes require explicit amendment and team sign-off.
