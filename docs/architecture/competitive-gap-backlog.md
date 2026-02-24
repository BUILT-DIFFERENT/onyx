# Competitive Gap Backlog (Samsung Notes vs Notewise vs Onyx)

Date: 2026-02-23  
Scope: Full backlog of missing and partially implemented competitor-parity features, with Android-first execution details and explicit Convex/Web implications.

## Baseline

Current parity highlights already in Onyx:

- Multi-page stacked editor flow and page jump/to-table-of-contents affordances.
- OpenGL ink pipeline with pressure-aware stroke rendering.
- Lasso selection with move/resize and undo/redo integration.
- PDF import/render pipeline with tiled async rendering and PDF text selection.
- Template persistence for blank/grid/lined/dotted with density and background color.
- Recognition pipeline and converted-text overlay flow.

Evidence paths:

- `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
- `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorScaffold.kt`
- `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`
- `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\PageTemplateBackground.kt`
- `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`
- `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`

## Home / Library Gaps

- [ ] `HOME-01` Persistent folder tree + counts + hierarchical navigation parity
  - Status: `Partial`
  - Competitor behavior: Samsung Notes keeps a persistent hierarchical sidebar with nested folders and per-folder counts.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`
  - What exists now: Folder filtering exists, but navigation is list-first and not a persistent expandable hierarchy.
  - What is missing: Sidebar-style folder tree, nested expansion state, and count badges.
  - Exact change needed: Add a tree-shaped home navigation model and Compose sidebar that supports expand/collapse, count chips, and breadcrumb-sync with current folder selection.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Android UI test for nested expansion/selection + contract fixture for folder tree payload.

- [ ] `HOME-02` Shared and Trash destination surfaces
  - Status: `Missing`
  - Competitor behavior: Samsung Notes exposes top-level Shared and Trash destinations with dedicated actions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Notes can be soft-deleted, but no user-visible Trash/Shared surfaces.
  - What is missing: Trash list (restore/permanent delete) and shared-note list/actions.
  - Exact change needed: Add home destinations for `Shared` and `Trash`, wire repository queries and restore/purge actions, and define matching Convex metadata fields for share/trash state.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: End-to-end delete->trash->restore flow test and shared-note visibility contract test.

- [ ] `HOME-03` Grid/thumbnail browsing mode
  - Status: `Missing`
  - Competitor behavior: Samsung Notes and Notewise both support visual note-card browsing.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\thumbnail\ThumbnailGenerator.kt`
  - What exists now: Home is list-first; thumbnail generation exists but is not the default browsing UX.
  - What is missing: Grid card mode, preview thumbnails, and mode toggle persistence.
  - Exact change needed: Add list/grid switch, card layout, and thumbnail cache invalidation hooks for note updates.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Screenshot tests for list/grid states and scrolling perf pass on large datasets.

## Editor / Tooling Gaps

- [x] `EDIT-01` Shape tool
  - Status: `Done (Wave F-ObjectInsert)`
  - Competitor behavior: Notewise exposes dedicated shape drawing with predictable geometry output.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Shape insert flow (line/rectangle/ellipse) is available through the insert menu with persisted page-object backing and undo/redo.
  - What is missing: Advanced shape styling controls and rotation handles.
  - Exact change needed: Expand tool and object models to include shape primitives (line/rect/ellipse) and add toolbar entry plus creation/edit actions.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for shape serialization and Android UI tests for create/transform/undo.

- [ ] `EDIT-02` Image insert tool
  - Status: `Partial (Wave G-InsertParity runtime MVP)`
  - Competitor behavior: Samsung Notes and Notewise allow image insertion as editable canvas objects.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Insert menu routes to image picker + tap-to-place image objects with persisted metadata and object transform handles.
  - What is missing: Production asset lifecycle/export integration and polished image crop/replace UX.
  - Exact change needed: Add image insert command, object bounds metadata, and object transform handles with storage references.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Integration test for image insert->reopen->move->delete lifecycle.

- [ ] `EDIT-03` Text object tool
  - Status: `Partial (Wave G-InsertParity runtime MVP)`
  - Competitor behavior: Competitor editors provide explicit typed text objects separate from handwriting conversion.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\ConvertedTextBlock.kt`
  - What exists now: Text insert route creates persisted text objects with in-canvas edit/save, move/resize, duplicate/delete, and undo/redo.
  - What is missing: Rich formatting strip parity (lists/alignment controls beyond MVP) and advanced typography controls.
  - Exact change needed: Introduce text object entity and editing UI, keeping recognition overlays as a separate data path.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Editor UI tests for text insert/edit/move/delete and persistence reload.

- [ ] `EDIT-04` Rich insert menu
  - Status: `Strong Partial (Wave G-InsertParity)`
  - Competitor behavior: Samsung Notes uses a consolidated insert menu (image/table/GIF/audio and related actions).
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Unified `+` insert menu routes shape/image/text actions and includes explicit disabled parity placeholders for camera/scan/voice/audio/sticky.
  - What is missing: Runtime implementations for camera/scan/voice/audio/sticky insert flows.
  - Exact change needed: Add extensible insert menu architecture that routes to shape/image/text and future insert capabilities.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Toolbar UI smoke test verifying each insert action is wired or intentionally disabled.

- [ ] `EDIT-05` Samsung-style text-formatting toolbar
  - Status: `Missing`
  - Competitor behavior: Samsung Notes provides lower-toolbar text formatting (size, style, alignment, lists).
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: No dedicated text-format strip.
  - What is missing: Formatting commands bound to text object selection/edit state.
  - Exact change needed: Add text-formatting toolbar and persist text style attributes in text-object model.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Compose UI tests for style toggle persistence and object re-open fidelity.

- [ ] `EDIT-06` Multi-pen preset exposure in UI
  - Status: `Strong Partial (Wave J-ColorPresetUX)`
  - Competitor behavior: Notewise exposes fountain/ball/calligraphy style presets directly in the pen UI.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\BrushPreset.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\config\BrushPresetStore.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Pen/highlighter settings panels now expose tap-to-apply preset chips wired into brush state and persisted editor settings.
  - What is missing: User-defined preset authoring/management beyond built-in preset chips.
  - Exact change needed: Add pen preset picker in toolbar and wire selected preset into editor brush state and settings persistence.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI test for preset selection persistence across editor reopen.

- [ ] `EDIT-07` Line style options (solid/dashed/dotted)
  - Status: `Missing`
  - Competitor behavior: Notewise exposes stroke line style options in tool settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`
  - What exists now: Solid stroke rendering only.
  - What is missing: Style enum and renderer support for non-solid stroke patterns.
  - Exact change needed: Extend stroke style schema and rendering pipeline to support dashed/dotted stroke geometry.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Renderer visual regression tests for each line style.

- [ ] `EDIT-08` Advanced pen controls (stabilization and pressure tuning depth)
  - Status: `Partial`
  - Competitor behavior: Notewise provides deeper stabilization and pressure controls than basic brush settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Brush.kt`
  - What exists now: Size/smoothing/taper exist, but pressure and stabilization are not fully surfaced as distinct user controls.
  - What is missing: Separate pressure-sensitivity and stabilization controls with explicit labels and persistence.
  - Exact change needed: Extend brush settings model/UI for pressure and stabilization sliders and route parameters into stroke shaping.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Unit tests for width response curves and settings persistence checks.

- [ ] `EDIT-09` Area/pixel eraser mode
  - Status: `Missing`
  - Competitor behavior: Samsung Notes and Notewise expose precision area/pixel erasing.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`
  - What exists now: Stroke and segment erase behavior only.
  - What is missing: Pixel/area eraser algorithm and mode toggle.
  - Exact change needed: Add `EraserMode` to settings and implement area-based erase path with undo-safe diff encoding.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Geometry unit tests and integration tests for partial erase/undo consistency.

- [ ] `EDIT-10` Eraser size control
  - Status: `Missing`
  - Competitor behavior: Competitor apps expose eraser radius sliders.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: No explicit eraser diameter setting in UI.
  - What is missing: Radius slider and application to hit-testing.
  - Exact change needed: Add eraser radius setting and propagate it through stroke/segment/area eraser hit paths.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit test matrix for erase radius behavior + UI state persistence test.

- [ ] `EDIT-11` Eraser type filters + clear-page action
  - Status: `Missing`
  - Competitor behavior: Notewise supports erase target filters and clear-current-page action.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`
  - What exists now: Eraser targets stroke data uniformly.
  - What is missing: Filter-by-type erase behavior and one-step page clear command.
  - Exact change needed: Add eraser target filter model and page-clear action integrated with undo/redo and recognition refresh.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Unit tests for filter predicates + integration test for clear-page undo.

- [ ] `EDIT-12` Lasso rotation transform + richer action bar
  - Status: `Partial`
  - Competitor behavior: Notewise supports rotation and contextual actions (duplicate/delete/style/convert) after lasso selection.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\LassoGeometry.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\LassoRenderer.kt`
  - What exists now: Move/resize exists, but rotation and full contextual command bar are incomplete.
  - What is missing: Rotation handle/angle semantics and full action strip near selection bounds.
  - Exact change needed: Add rotate transform operation and contextual lasso action bar with duplicate/delete/style/convert commands.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Lasso transform unit tests + Compose UI tests for contextual actions.

## PDF / Document Gaps

- [ ] `PDF-01` In-editor PDF search UX
  - Status: `Missing`
  - Competitor behavior: Competitor apps provide in-document search with next/previous match navigation.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`
  - What exists now: PDF text extraction/selection exists; no editor-local search surface.
  - What is missing: Search query UI, result indexing, and viewport jumps.
  - Exact change needed: Add PDF find controller with query state, result list, highlight overlay, and previous/next navigation actions.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Android UI test for multi-page find navigation and highlight positioning.

- [ ] `PDF-02` Hyperlink navigation support
  - Status: `Missing`
  - Competitor behavior: Tapping internal/external PDF links is standard in mature PDF annotation apps.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfiumDocumentSession.kt`
  - What exists now: Outline navigation exists; link hit-testing/tap routing is not implemented.
  - What is missing: Link rectangle extraction, click handling, and destination handling rules.
  - Exact change needed: Extend PDF extraction to include link regions and implement tap routing for internal jumps and safe external URL opening.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Instrumented tests for internal page link and external URL link behavior.

- [ ] `PDF-03` Export/share with flatten-annotations option
  - Status: `Missing`
  - Competitor behavior: Samsung-style workflows include export/share of annotated PDFs with flatten controls.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: PDF import and annotation rendering exist; no share/export pipeline with flatten option.
  - What is missing: Export job, flatten toggle, generated asset metadata contract.
  - Exact change needed: Implement export/share pipeline with `flatten=true|false` mode and persist export metadata for Android and Convex consumers.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Golden output checks for flattened vs layered exports and share-intent smoke tests.

## Gestures / Stylus Gaps

- [ ] `GEST-01` Configurable stylus/finger gesture mapping
  - Status: `Missing`
  - Competitor behavior: Notewise lets users map stylus button and single/double finger behavior.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`
  - What exists now: Input behavior is mostly hardcoded.
  - What is missing: User-configurable gesture mapping surface and persisted settings model.
  - Exact change needed: Add editor input settings persistence + settings UI and route gesture dispatch through those preferences.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for mapping policy + UI test for persisted input preferences.

- [ ] `GEST-02` Gesture shortcuts (multi-finger undo/redo, configurable double-tap behavior)
  - Status: `Strong Partial (Wave M-MultiFingerShortcuts)`
  - Competitor behavior: Notewise supports multi-finger undo/redo and configurable double-tap behavior.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\UndoController.kt`
  - What exists now: Configurable two-finger and three-finger tap shortcuts are persisted (`UNDO|REDO|NONE`) and routed to editor undo/redo; configurable double-tap zoom mapping is also persisted and active.
  - What is missing: Additional shortcut permutations (for example gesture-to-tool switching), plus broader coverage for zoom-lock/read-only permutations in instrumentation.
  - Exact change needed: Expand gesture shortcut matrix beyond undo/redo and add exhaustive instrumentation coverage across editor modes.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Gesture instrumentation tests for undo/redo and double-tap action mapping.

- [ ] `GEST-03` Visible zoom percentage indicator
  - Status: `Done (Wave G-ZoomPill)`
  - Competitor behavior: Samsung/Notewise show current zoom percentage in-editor.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorScaffold.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`
  - What exists now: Persistent page/zoom pill shows live zoom percentage in both single-page and stacked-page editor modes.
  - What is missing: Optional compact/minimized variants for very small screens.
  - Exact change needed: Add zoom HUD component bound to active canvas transform scale.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Compose UI test verifying zoom percentage updates as scale changes.

- [x] `GEST-04` Zoom presets and lock-zoom behavior
  - Status: `Done (Wave H-PageZoomJump)`
  - Competitor behavior: Notewise provides 50/100/200/... presets plus lock-zoom mode.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`
  - What exists now: Page/zoom pill menu includes presets (50/100/200/300/400), fit action, lock/unlock toggle, and go-to-page; zoom-change paths respect lock guard in single and stacked editors.
  - What is missing: Dedicated settings persistence for lock state.
  - Exact change needed: Add zoom action menu (50/100/200/300/400/fit/lock) and block pinch scale changes while lock is enabled.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Gesture tests for lock behavior and UI tests for preset actions.

## Template System Gaps

- [ ] `TPL-01` Paper size selector (A-series, letter, phone)
  - Status: `Missing`
  - Competitor behavior: Notewise exposes selectable paper sizes with explicit dimensions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\PageEntity.kt`
  - What exists now: Page dimensions exist but are not exposed through template paper-size presets.
  - What is missing: Paper-size enum/model and picker UI.
  - Exact change needed: Expand `PageTemplateConfig` with paper-size metadata and apply selected dimensions on page create/apply flows.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for paper-size mapping + UI tests for template picker application.

- [ ] `TPL-02` Expanded template catalog and categories
  - Status: `Partial`
  - Competitor behavior: Notewise includes basic/education/music categories with richer built-in templates.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\PageTemplateBackground.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt`
  - What exists now: Basic templates (blank/lined/grid/dotted).
  - What is missing: Category model and specialized templates (Cornell, engineering, music score, etc.).
  - Exact change needed: Add categorized template registry and renderer support for new pattern families.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Visual regression tests for each template category and picker filter tests.

- [ ] `TPL-03` Custom templates tab
  - Status: `Missing`
  - Competitor behavior: Notewise separates built-in and custom templates.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\dao\PageTemplateDao.kt`
  - What exists now: Template rows can be stored, but no dedicated custom-template manager in UI.
  - What is missing: Custom tab, template create/import flow, and reusable custom-template lifecycle.
  - Exact change needed: Add built-in/custom tabbed picker and custom template CRUD flows backed by `PageTemplateDao`.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: CRUD tests for custom templates and apply-to-page integration tests.

- [ ] `TPL-04` Template line-width control
  - Status: `Missing`
  - Competitor behavior: Notewise exposes independent line thickness for template patterns.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\PageTemplateBackground.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt`
  - What exists now: Template density and color controls exist.
  - What is missing: Line-width parameter and renderer support.
  - Exact change needed: Add `lineWidth` field to template config and apply it in background pattern painting.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Renderer unit tests for line-width scaling and persistence tests.

- [ ] `TPL-05` Apply template to current page vs all pages
  - Status: `Missing`
  - Competitor behavior: Notewise allows apply-to-current or apply-to-all selection.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`
  - What exists now: Template updates are scoped to current page.
  - What is missing: Bulk-apply workflow and all-pages update operation.
  - Exact change needed: Add apply-scope option and repository batch template update path for full-note application.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Repository tests for all-pages updates and UI tests for scope selector.

## Recognition / Conversion Gaps

- [ ] `REC-01` Real-time inline recognition preview UX
  - Status: `Partial`
  - Competitor behavior: Samsung Notes recognition feels continuous with clearer inline preview behavior.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Recognition pipeline with converted text overlays.
  - What is missing: Explicit pending/confirmed inline preview UX while writing.
  - Exact change needed: Add transient preview layer and confidence/commit timing rules for continuous recognition display.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Stylus latency benchmark and UX acceptance pass for preview clarity.

- [ ] `REC-02` Interactive ink gestures as recognizer actions (scratch-out/scribble)
  - Status: `Missing`
  - Competitor behavior: Competitor flows support gesture-based recognition edits.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`
  - What exists now: Erasing exists, but recognizer-intent gestures are not interpreted as conversion commands.
  - What is missing: Gesture classifier and command mapping into recognized text edits.
  - Exact change needed: Add recognizer gesture command pipeline (scratch-out/delete, join/split behaviors) with undo/redo support.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for gesture command parsing + integration tests for recognized-block mutation.

- [ ] `REC-03` Recognition mode/language configurability
  - Status: `Partial`
  - Competitor behavior: Mature apps expose recognition language/mode settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptEngine.kt`
  - What exists now: Recognition settings are internally represented but not fully user-configurable.
  - What is missing: Settings UX for language/mode profile selection.
  - Exact change needed: Add recognition settings UI and bind selected profiles into engine/session configuration.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Settings persistence tests plus recognition smoke tests by selected profile.

## Expansion Addendum (Directly from Latest Video Analysis)

This addendum expands scope without removing prior backlog work. It captures every materially visible behavior in the Samsung Notes and Notewise recordings plus explicit "best-in-class" deltas.

### Home / Library Expansion

- [ ] `HOME-04` Library sort controls + persistence
  - Status: `Missing`
  - Competitor behavior: Samsung exposes explicit sorting (for example Date modified) in folder/note views.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`
  - What exists now: List-first browsing with limited sort affordances.
  - What is missing: Sort selector, sort direction, and per-surface persistence.
  - Exact change needed: Add sortable home query model (`modified`, `created`, `title`), persist last choice, and keep sort state stable when switching folders or list/grid modes.
  - Surface impact: `Android`, `Web`, `Convex`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Home UI test for sort mode persistence + repository query tests for each comparator.

- [ ] `HOME-05` Unified search across title, typed text, handwriting, and PDF text
  - Status: `Missing`
  - Competitor behavior: Mature note apps return hybrid search results across note metadata and content layers.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfTextExtractor.kt`
  - What exists now: Typed and recognition pathways exist but are not unified into one global search experience.
  - What is missing: Indexed multi-source search pipeline and unified result ranking/highlighting.
  - Exact change needed: Build a single search index contract that fuses title/body text, handwriting index tokens, and PDF text/OCR tokens, with result chips indicating match source.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract tests for mixed-source match sets + UI tests for source-specific highlight jumps.

- [ ] `HOME-06` Favorites / pinned notes
  - Status: `Missing`
  - Competitor behavior: Competitor flows commonly allow star/pin for quick retrieval.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\NoteEntity.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`
  - What exists now: No dedicated pin/star metadata or pinned lane in home.
  - What is missing: Pin action, pinned section, and ordering rules.
  - Exact change needed: Add `isPinned` note metadata, home section rendering, and deterministic sort precedence for pinned notes.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Repository ordering tests + UI tests for pin/unpin transitions.

- [ ] `HOME-07` Tags and smart folders
  - Status: `Missing`
  - Competitor behavior: "To win" tier organization includes tags and saved smart filters.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Folder organization only.
  - What is missing: Tagging model, assignment UI, and dynamic query folders.
  - Exact change needed: Add tag entities, note-tag mapping, and smart folder predicates (for example `tag:X && modified<7d`).
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Excellence`
  - Validation gate: Contract fixture coverage for tag joins and smart-folder predicate decoding.

- [ ] `HOME-08` Global recents destination
  - Status: `Missing`
  - Competitor behavior: Fast-reopen recents are expected in high-velocity note workflows.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\NoteEntity.kt`
  - What exists now: No dedicated Recents lane.
  - What is missing: Recents query endpoint and home destination with clear sorting semantics.
  - Exact change needed: Track last-open timestamps per note and add `Recents` destination with virtual-folder behavior.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Integration tests for recents ordering and update behavior after open actions.

- [ ] `HOME-09` Note version history surface
  - Status: `Missing`
  - Competitor behavior: Best-in-class reliability includes lightweight per-note history.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\convex\schema.ts`
  - What exists now: Undo/redo in active session; no long-lived revision timeline.
  - What is missing: Revision checkpoints, restore actions, and retention policy.
  - Exact change needed: Persist incremental note revisions with restore-to-point workflow and basic diff metadata.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Excellence`
  - Validation gate: Revision restore integration tests and retention policy unit tests.

### Editor / Tooling Expansion

- [x] `EDIT-13` Explicit Edit mode vs View mode toggle
  - Status: `Done (Wave H-ModeSnackbar)`
  - Competitor behavior: Notewise shows mode-switch snackbars and simplified non-edit chrome.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorScaffold.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Explicit read-only/view mode toggle is wired through editor state, editing actions are gated, and mode-switch user feedback is surfaced as snackbars.
  - What is missing: Optional simplified view-mode chrome polish.
  - Exact change needed: Add `EditorMode` state (`Edit`, `View`), suppress editing gestures in `View`, and emit clear snackbar confirmations on mode changes.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: UI tests asserting input lockout in view mode and full restoration in edit mode.

- [ ] `EDIT-14` Page manager panel (thumbnails, reorder, duplicate, delete)
  - Status: `Strong Partial (Wave I-PageManagerOps)`
  - Competitor behavior: Competitive editors expose strong page-level management, especially for long notes and PDFs.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Overflow now opens a dedicated page manager dialog with per-page open/reorder (up/down)/duplicate/delete actions, plus protected delete confirmation and repository-backed persistent ordering.
  - What is missing: Thumbnail-rich drag-and-drop board and undo stack integration for page-level operations.
  - Exact change needed: Evolve dialog into a thumbnail-first drawer/sheet with drag reorder and explicit undo for destructive page actions.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Page-order persistence tests and UI drag/drop reorder tests.

- [x] `EDIT-15` Persistent page/zoom pill + menu parity
  - Status: `Done (Wave H-PageZoomJump)`
  - Competitor behavior: Notewise uses a bottom-right pill showing page index plus zoom %, with menu actions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorScaffold.kt`
  - What exists now: Bottom-right page/zoom pill is present in single-page and stacked-page editors with page index + zoom percentage, menu actions, and jump-to-page numeric entry.
  - What is missing: Final visual parity polish.
  - Exact change needed: Replace standalone zoom indicator with a combined page/zoom pill that opens presets, fit-content, and lock-zoom actions.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Compose tests for pill state updates across page navigation and zoom changes.

- [ ] `EDIT-16` Quick color strip + favorite slots in main toolbar
  - Status: `Strong Partial (Wave J-ColorPresetUX)`
  - Competitor behavior: Samsung keeps color dots one tap away while preserving deeper palette controls.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\config\BrushPresetStore.kt`
  - What exists now: Always-visible quick color strip now uses persistent favorite slots from local store, and long-press opens advanced picker with slot reassignment.
  - What is missing: Cross-device sync of favorite slot state.
  - Exact change needed: Add toolbar quick-color strip with long-press assignment and sync with pen/highlighter active preset state.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI tests for quick color apply + favorite reassignment persistence.

- [ ] `EDIT-17` Advanced color picker parity (swatches, spectrum, HEX/RGB)
  - Status: `Strong Partial (Wave J-ColorPresetUX)`
  - Competitor behavior: Samsung palette includes swatches, spectrum, and numeric color entry.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Advanced picker now includes HEX + RGB numeric controls, RGB spectrum sliders, preview, and swatch taps integrated with quick-color assignment.
  - What is missing: Full HSV wheel and saved custom swatch library management UI.
  - Exact change needed: Add advanced picker with HEX/RGB inputs, alpha support, and preset save/remove controls.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI tests for HEX/RGB parse, validation, and preset round-trip.

- [ ] `EDIT-18` Highlighter line mode + tip shape controls
  - Status: `Missing`
  - Competitor behavior: Notewise supports always-straight highlighter mode, tip shape, opacity, and thickness controls.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Brush.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Highlighter controls do not expose all parity options.
  - What is missing: Straight-line mode toggle and tip geometry selector.
  - Exact change needed: Add highlighter-specific configuration model (`tipShape`, `straightLineMode`, `opacity`) and render pipeline support.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Renderer tests for square/round tip + UI tests for straight-line behavior.

- [ ] `EDIT-19` Hold-to-shape while drawing
  - Status: `Missing`
  - Competitor behavior: Notewise exposes hold-to-shape directly in pen settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: No stroke-to-shape hold conversion toggle.
  - What is missing: Gesture detect window and shape replacement workflow.
  - Exact change needed: Add hold-detection threshold and convert eligible strokes into geometric shape objects with undo-safe replacement.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Stroke gesture recognition tests and UI tests for hold conversion success/fail conditions.

- [x] `EDIT-20` Always-accessible insert `+` menu with explicit Samsung parity actions
  - Status: `Done for architecture + shape route (Wave F-ObjectInsert)`
  - Competitor behavior: Samsung insert menu quickly exposes PDF, voice recording, image, camera, scan, audio file, drawing, and sticky note.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: `+` routes to a dedicated insert menu with shape/image/text routes active and explicit disabled placeholders for `camera`, `scan`, `voice`, `audio file`, and `sticky`.
  - What is missing: Runtime permission + capture/import flows for deferred entries.
  - Deferred from Wave F: Runtime image/text/audio/sticky/scan/file insert flows remain in parity backlog by design.
  - Exact change needed: Expand `EDIT-04` implementation contract to include dedicated insert intents for `PDF`, `Image`, `Camera`, `Scan`, `Voice recording`, `Audio file`, and `Sticky note`, including disabled-state UX when not available.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: UI smoke suite that asserts each menu entry appears and routes to expected flow.

- [ ] `EDIT-21` Camera capture + document scan insert flows
  - Status: `Missing`
  - Competitor behavior: Samsung includes direct camera and scan insertion from editor.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: No first-class camera/scan objects in editor content model.
  - What is missing: Capture intents, scan post-processing, and inserted object lifecycle.
  - Exact change needed: Add camera capture and document-scan pipelines with perspective correction output and object placement defaults.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Instrumented tests for camera/scan permission flow and persisted object reopen.

- [ ] `EDIT-22` Embedded audio recording objects (waveform + timeline links)
  - Status: `Missing`
  - Competitor behavior: Samsung-style audio insertion supports in-note recording context.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: No editor-native audio annotation object.
  - What is missing: Record/stop UI, object rendering, playback, and timestamp metadata.
  - Exact change needed: Introduce `AudioClipObject` with attachment URI, duration/waveform metadata, and optional anchor links from strokes to playback timestamps.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Integration test for record->persist->playback and export policy checks.

- [ ] `EDIT-23` Sticky note objects as movable callouts
  - Status: `Missing`
  - Competitor behavior: Samsung insert menu exposes sticky notes as lightweight callouts.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\PageObjectEntity.kt`
  - What exists now: No sticky/callout object type.
  - What is missing: Sticky object schema, style options, and edit interactions.
  - Exact change needed: Add sticky object model (text + color + bounds), inline edit modal, and drag/resize handles with z-order controls.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Object lifecycle tests and screenshot tests for sticky style variants.

- [ ] `EDIT-24` Ruler/straightedge and snap-to-align guides
  - Status: `Missing`
  - Competitor behavior: Notewise settings expose snap-to-align controls; premium note apps provide ruler alignment aids.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Freehand positioning without alignment guides.
  - What is missing: Guide rendering, object snapping thresholds, and optional ruler overlay.
  - Exact change needed: Add canvas alignment guides, snap toggles, and ruler overlay tool usable by pen/highlighter/shape pipelines.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Excellence`
  - Validation gate: Transform unit tests for snap thresholds + interaction tests for ruler-assisted lines.

- [ ] `EDIT-25` Area eraser visual cursor parity
  - Status: `Missing`
  - Competitor behavior: Samsung shows a live circular eraser cursor while area erasing.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Area erase path planned, but no explicit eraser cursor overlay contract.
  - What is missing: Cursor overlay, center marker, and size feedback tied to eraser radius.
  - Exact change needed: Add eraser cursor overlay layer that reflects current radius and input centroid in real time.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: UI tests for cursor visibility in area mode and radius-change updates.

### PDF / Document Expansion

- [ ] `PDF-04` PDF page-strip/thumbnails in annotation mode
  - Status: `Missing`
  - Competitor behavior: Annotation-first PDF workflows rely on quick page thumbnail navigation.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfiumDocumentSession.kt`
  - What exists now: Tiled rendering and page state exist without explicit thumbnail strip UX.
  - What is missing: Thumbnail generation/selection panel in PDF mode.
  - Exact change needed: Add lazy thumbnail strip with current-page indicator and tap navigation.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI tests for thumbnail jump accuracy and performance checks on long PDFs.

- [ ] `PDF-05` OCR fallback for scanned PDF text search
  - Status: `Missing`
  - Competitor behavior: Search parity requires results even when PDFs are image-only scans.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfTextExtractor.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Text extraction path assumes embedded selectable text.
  - What is missing: OCR path and index persistence for scanned content.
  - Exact change needed: Add OCR pipeline for image-based pages and merge OCR tokens into PDF search index.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: OCR fixture tests with scanned PDFs and query recall checks.

- [ ] `PDF-06` Insert blank pages between imported PDF pages
  - Status: `Missing`
  - Competitor behavior: Competitive annotation apps allow whitespace insertion between source pages.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`
  - What exists now: Imported PDF page sequence is fixed.
  - What is missing: Mixed PDF/background pages with user-inserted blank pages.
  - Exact change needed: Support `blank` page insertion at arbitrary index while preserving original PDF page binding metadata.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Repository tests for mixed page order and export correctness.

- [ ] `PDF-07` Smart highlight/underline text snap
  - Status: `Missing`
  - Competitor behavior: Premium PDF tools can align highlight/underline strokes to text baselines.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfTextExtractor.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`
  - What exists now: Freehand annotation only.
  - What is missing: Text-line detection and snap behavior in highlighter mode.
  - Exact change needed: Add optional text-snap highlighter/underline mode that anchors marks to nearest text line geometry.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Integration tests for snapped bounds on known PDF text fixtures.

- [ ] `PDF-08` Explicit dark-mode handling notice for PDF backgrounds
  - Status: `Missing`
  - Competitor behavior: Samsung clearly informs users when dark mode does not alter PDF page backgrounds.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`
  - What exists now: No explicit UX messaging for theme behavior on PDF backgrounds.
  - What is missing: User-facing notice and deterministic theme behavior rules.
  - Exact change needed: Add one-time toast/snackbar notice and settings copy clarifying that PDF backgrounds are source-accurate.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UX test asserting message display conditions and non-repetition behavior.

### Gesture / Stylus Expansion

- [ ] `GEST-05` Latency optimization modes (Normal/Fast Experimental)
  - Status: `Missing`
  - Competitor behavior: Notewise exposes latency optimization choices for device-specific tuning.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorSettings.kt`
  - What exists now: Single rendering/input profile.
  - What is missing: User-selectable latency profile and performance guardrails.
  - Exact change needed: Add latency profile setting, wire profile params into input prediction/smoothing, and present experimental disclaimer text.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Performance benchmarks comparing profile frame time and perceived latency.

- [ ] `GEST-06` Full stylus-button mapping (primary/secondary/long-hold)
  - Status: `Strong Partial (Wave K-InputSettingsRouting)`
  - Competitor behavior: Notewise supports rich button remapping including hold-to-erase and switch-to-last-tool.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Primary/secondary/long-hold settings are persisted and exposed in toolbar input settings; touch router enforces primary/secondary eraser-hold behavior and long-hold eraser activation.
  - What is missing: Additional stylus actions beyond `ERASER_HOLD|NO_ACTION` (for example switch-to-last-tool) and richer conflict-priority policy.
  - Exact change needed: Expand stylus action enum + runtime dispatch for advanced mappings and add interaction tests per mapping permutation.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Input-mapping tests for each stylus action and persistence round-trip.

- [ ] `GEST-07` Finger behavior matrix parity (single/double finger action profiles)
  - Status: `Strong Partial+ (Wave N-DoubleFingerPanOnly)`
  - Competitor behavior: Notewise lets users set single-finger and double-finger behavior independently.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Configurable `singleFinger` (`DRAW|PAN|IGNORE`) and `doubleFinger` (`ZOOM_PAN|PAN_ONLY|IGNORE`) profiles are persisted, surfaced in UI, and routed by the gesture engine with dedicated pan-only handling plus explicit second-finger ignore enforcement.
  - What is missing: Exhaustive profile-matrix instrumentation coverage across stylus-present/stylus-absent/read-only combinations.
  - Exact change needed: Expand instrumented coverage to full profile matrix and add regression guards for profile interactions with lasso and zoom-lock states.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Instrumented tests for each profile combination under stylus-present and stylus-absent scenarios.

- [ ] `GEST-08` Double-tap zoom-level shortcut
  - Status: `Strong Partial (Wave L-DoubleTapZoom)`
  - Competitor behavior: Notewise optionally maps double tap to zoom-level change.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Configurable `doubleTapZoomAction` (`NONE|CYCLE_PRESET|FIT_TO_PAGE`) is persisted, editable in input settings, and routed in editor touch handling.
  - What is missing: Additional policy controls (e.g. stylus double-tap mapping and per-mode overrides) plus broader UI test coverage with zoom-lock permutations.
  - Exact change needed: Extend gesture policy matrix and add dedicated zoom-lock + multi-page parity instrumentation scenarios.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Gesture tests confirming map-to-action with and without zoom lock enabled.

### Template System Expansion

- [ ] `TPL-06` Custom page width/height inputs in template modal
  - Status: `Missing`
  - Competitor behavior: Notewise offers paper presets plus free-form custom dimensions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\TemplatePickerDialog.kt`
  - What exists now: Preset-like dimensions can exist in data model but no custom width/height editor flow.
  - What is missing: Dimension inputs, unit handling, and validation rules.
  - Exact change needed: Add custom size form with unit conversion, min/max constraints, and live preview in template picker.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Unit tests for dimension normalization and UI form validation tests.

- [ ] `TPL-07` Template rendering quality contract (anti-swim, page-locked patterns)
  - Status: `Missing`
  - Competitor behavior: Dot/grid backgrounds remain stable during pan/zoom and feel physically page-bound.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\PageTemplateBackground.kt`
  - What exists now: Template rendering exists without explicit stability constraints documented as acceptance criteria.
  - What is missing: Rendering invariants for coordinate locking and anti-alias quality.
  - Exact change needed: Define and enforce template render invariants: page-coordinate anchoring, density in physical units, and stable anti-aliased marks across zoom levels.
  - Surface impact: `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Visual regression at multiple zoom/pan states with pixel-diff thresholds.

### Recognition / Intelligence Expansion

- [ ] `REC-04` Recognition operating modes (`Off`, `Search-only`, `Live convert`)
  - Status: `Missing`
  - Competitor behavior: Power users expect control over recognition intensity and conversion intrusiveness.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`
  - What exists now: Recognition is enabled path-first with limited user control.
  - What is missing: Mode selector and mode-specific pipeline branching.
  - Exact change needed: Introduce recognition mode setting and split pipeline behavior by mode (`none`, `index-only`, `inline conversion`).
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Mode-based behavior tests and settings persistence checks.

- [ ] `REC-05` Lasso-to-text conversion workflow
  - Status: `Missing`
  - Competitor behavior: Users expect selected handwriting to convert to editable text blocks.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\LassoGeometry.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\ConvertedTextBlock.kt`
  - What exists now: Recognition overlays exist, but lasso-triggered conversion workflow is not exposed.
  - What is missing: Context action, preview, confirmation, and resulting text-object insertion semantics.
  - Exact change needed: Add lasso action `Convert to text`, show editable preview, and commit into text object layer while preserving original ink for undo.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: End-to-end tests for lasso conversion commit/revert and layout preservation.

- [ ] `REC-06` Search-level handwriting indexing across note corpus
  - Status: `Missing`
  - Competitor behavior: Competitive search can find handwritten keywords across library, not only current page.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Recognition is page-local for conversion overlays.
  - What is missing: Persisted handwriting token index with note/page anchors.
  - Exact change needed: Generate and sync searchable handwriting token index keyed by note/page/region for global search.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Cross-note query tests with highlight jump accuracy.

- [ ] `REC-07` Shape recognition and beautification
  - Status: `Missing`
  - Competitor behavior: Intelligent shape cleanup is expected for polished handwritten diagrams.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptEngine.kt`
  - What exists now: Raw stroke rendering without recognition-assisted shape snapping.
  - What is missing: Shape classifier and replacement heuristics.
  - Exact change needed: Add recognition pass for geometric primitives and convert qualifying strokes into editable shape objects.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Recognition precision/recall tests on shape fixture dataset.

- [ ] `REC-08` Optional math recognition mode
  - Status: `Missing`
  - Competitor behavior: Math parsing is a high-differentiation capability in intelligent note apps.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptEngine.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`
  - What exists now: No dedicated math-mode UX or semantic output.
  - What is missing: Math recognition pipeline and insertion/preview workflow.
  - Exact change needed: Add optional math recognizer profile with equation preview and commit as text/LaTeX object.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Fixture tests for equation parsing and rendering correctness.

### Settings / Security Expansion

- [ ] `SET-01` Note password lock and unlock flow
  - Status: `Missing`
  - Competitor behavior: Notewise exposes password protection in app settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\settings\SettingsScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\NoteEntity.kt`
  - What exists now: No note-level lock setting.
  - What is missing: Password gate model, protected note launch flow, and lock-state indicators.
  - Exact change needed: Add password lock settings with secure credential storage and protected note open/edit lifecycle.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Security tests for lock enforcement and credential reset flow.

- [ ] `SET-02` Focus/presentation settings (`keep screen on`, `hide system bars`)
  - Status: `Missing`
  - Competitor behavior: Notewise includes immersion-oriented settings for long sessions/presenting.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\settings\SettingsScreen.kt`
  - What exists now: Default OS behavior; no explicit controls.
  - What is missing: User toggles and editor window/flags integration.
  - Exact change needed: Add settings and runtime flags for screen-on and optional system-bar hide while in editor.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Instrumented tests for window-flag behavior across mode transitions.

- [ ] `SET-03` New-note naming rule and resume-last-page behavior
  - Status: `Missing`
  - Competitor behavior: Notewise exposes naming pattern preferences and "continue from last opened page".
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\settings\SettingsScreen.kt`
  - What exists now: Static/default note naming and basic open logic.
  - What is missing: Naming template settings and page-resume metadata.
  - Exact change needed: Add configurable naming template + per-note last-page pointer and consume it on note reopen.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Repository tests for generated names and reopen-to-last-page behavior.

- [ ] `SET-04` Storage dashboard + cache clear controls
  - Status: `Missing`
  - Competitor behavior: Notewise provides note storage sizing and cache clear actions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\settings\SettingsScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\StorageRepository.kt`
  - What exists now: No user-facing storage footprint summary.
  - What is missing: Storage breakdown (notes/images/PDF/audio/cache) and clear-cache affordance.
  - Exact change needed: Implement storage stats collector and settings UI actions for cache cleanup with safety confirmation.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Instrumented tests verifying cache clear does not remove persistent note assets.

### Performance / Reliability Expansion

- [ ] `PERF-01` Predictive inking + perceived-latency budget enforcement
  - Status: `Partial`
  - Competitor behavior: Samsung-class pen feel depends on predictive rendering and very low perceived latency.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`
  - What exists now: OpenGL rendering with pressure-aware strokes.
  - What is missing: Explicit predictive path, latency telemetry, and CI performance thresholds.
  - Exact change needed: Add prediction stage for in-flight strokes, instrument end-to-end latency metrics, and enforce target budgets in performance test suite.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Stylus benchmark suite with pass/fail thresholds for perceived latency.

- [ ] `PERF-02` 120fps target on capable devices with 60fps floor
  - Status: `Missing`
  - Competitor behavior: Premium inking apps maintain high refresh fluidity under normal load.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`
  - What exists now: No explicit FPS target policy in acceptance criteria.
  - What is missing: Device-class performance targets and measurement pipeline.
  - Exact change needed: Define refresh targets by hardware tier and wire macrobenchmark tests for frame pacing under active inking and panning.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Macrobenchmark reports meeting target frame pacing budgets.

- [ ] `PERF-03` Incremental redraw/dirty-region policy for heavy pages
  - Status: `Partial`
  - Competitor behavior: Large notes remain smooth by avoiding full-canvas repaints.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Renderer exists, but no documented dirty-region guarantees for all tool/object types.
  - What is missing: Dirty-region invalidation contract across ink/object/PDF overlays.
  - Exact change needed: Implement and document dirty-region redraw strategy for stroke updates, selection overlays, and template/background composition.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Frame-time regression tests on large mixed-content pages.

- [ ] `PERF-04` Stress profile for very large notes/PDFs
  - Status: `Missing`
  - Competitor behavior: Mature apps handle thousands of strokes and long PDFs without visible stutter.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\androidTest`, `C:\onyx\docs\architecture\testing.md`
  - What exists now: No published stress matrix tied to competitor-scale workloads.
  - What is missing: Reproducible load scenarios and pass/fail thresholds.
  - Exact change needed: Add performance fixtures for `N` strokes/page, `M` pages/note, and mixed PDF+ink workloads with memory/frame budget assertions.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: CI perf suite with stable thresholds and regression alerts.

## Cross-Surface Backlog (Android + Convex + Web)

Proposed public API/interface/type updates to align with backlog items:

- `Tool` expansion beyond `PEN/HIGHLIGHTER/ERASER/LASSO` in `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt` to include object-capable tools.
- `PageTemplateConfig` expansion in `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt` for `paperSize`, `lineWidth`, `category`, and apply-scope semantics.
- New eraser mode/type-filter model (mode, radius, target filters, clear-page behavior) shared by editor state and sync metadata.
- New gesture preference model persisted for editor input settings (stylus and finger mapping + shortcut actions).
- New export/share contract surface for flattened vs layered annotation output metadata (Android + Convex consumers).

- [ ] `XSURF-01` Convex schema/contracts for new feature metadata
  - Status: `Strong Partial+ (Wave G attachment object contracts landed)`
  - Competitor behavior: Not a UI feature by itself; required to support parity features across devices.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\docs\architecture\system-overview.md`
  - What exists now: Contracts now cover shape/image/text/audio/sticky/scan/file page-object metadata with fixture validation.
  - What is missing: Gesture/template/export metadata surfaces still need canonical contract fixtures.
  - Exact change needed: Add Convex schema and contract fixtures for object tools, template scope, gesture settings, and export metadata before cross-device sync rollout.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract fixture test suite and schema drift checks in CI.

- [ ] `XSURF-02` Web implications tracking for view-only surface
  - Status: `Strong Partial+ (fallback matrix expanded for attachment object kinds)`
  - Competitor behavior: Not competitor-visible directly; needed so web does not break on new Android-authored metadata.
  - Current Onyx evidence: `C:\onyx\apps\web\README.md`, `C:\onyx\docs\architecture\system-overview.md`
  - What exists now: Web is view-only and does not yet define behavior for incoming advanced object metadata.
  - What is missing: Compatibility matrix and fallback rendering rules for unsupported editor features.
  - Exact change needed: Define and implement web decode/fallback behavior for each new metadata field with explicit unsupported-feature affordances.
  - Surface impact: `Web`, `Convex`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Web contract decoding tests for mixed-feature notes and fallback rendering snapshots.

- [ ] `XSURF-03` Attachment/object schema expansion (image/audio/sticky/scan/file)
  - Status: `Strong Partial (runtime image/text MVP + full attachment contract scaffolding)`
  - Competitor behavior: Required cross-surface contract for Samsung-style insert parity.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\PageObjectEntity.kt`
  - What exists now: Canonical object union contract includes image/audio/sticky/scan/file payload scaffolding; Android runtime now supports shape/image/text object kinds.
  - What is missing: Runtime audio/sticky/scan/file object interactions and attachment lifecycle operations.
  - Deferred from this wave: Runtime support for audio/sticky/scan/file remains intentionally deferred.
  - Exact change needed: Add object union schema with type-specific payload validation and fallback policy for unsupported clients.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract fixtures for each attachment object type.

- [ ] `XSURF-04` Search-index contracts (handwriting tokens + PDF OCR tokens)
  - Status: `Missing`
  - Competitor behavior: Required to support unified search parity across devices.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfTextExtractor.kt`
  - What exists now: No shared schema for recognition/OCR index payloads.
  - What is missing: Versioned index schema and merge strategy across update sources.
  - Exact change needed: Add search token contracts, index versioning, and conflict-safe merge rules for handwriting and OCR updates.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Schema migration tests and mixed-source index merge fixtures.

- [ ] `XSURF-05` Sync conflict policy + metadata for edits at page/object granularity
  - Status: `Missing`
  - Competitor behavior: Needed for cross-device reliability once parity features are synced.
  - Current Onyx evidence: `C:\onyx\docs\architecture\system-overview.md`, `C:\onyx\convex\schema.ts`
  - What exists now: Baseline sync assumptions without expanded object/attachment conflict policy.
  - What is missing: Conflict strategy definitions and metadata fields for reconciling concurrent edits.
  - Exact change needed: Define conflict resolution semantics (last-write, merge, or user-resolve) by object category and persist revision metadata accordingly.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Contract tests covering concurrent edit conflict scenarios.

## Balanced Priority Waves

Expanded split policy: preserve the original Foundation/Parity backbone and add explicit `Wave V1`, `Wave V2`, and `Wave Excellence` phases for the newly expanded scope.

### Wave Foundation (Architecture + baseline parity)

- Existing backbone wave members remain in place.
- Added in this expansion:
  - `HOME-04`, `HOME-05`
  - `EDIT-13`, `EDIT-15`, `EDIT-20`, `EDIT-25`
  - `GEST-06`, `GEST-07`
  - `TPL-07`
  - `REC-04`
  - `PERF-01`, `PERF-02`, `PERF-03`, `PERF-04`
  - `XSURF-03`, `XSURF-04`

Foundation intent: unblock data contracts and performance fundamentals while shipping the minimum competitor-parity spine across library, editor modes, insert routes, gestures, and search structure.

### Wave Parity (Visible Samsung/Notewise equivalence)

- Existing parity members remain in place.
- Added in this expansion:
  - `HOME-06`, `HOME-08`
  - `EDIT-14`, `EDIT-16`, `EDIT-17`, `EDIT-18`, `EDIT-19`, `EDIT-21`, `EDIT-22`, `EDIT-23`
  - `PDF-04`, `PDF-06`, `PDF-08`
  - `GEST-05`, `GEST-08`
  - `TPL-06`
  - `REC-05`

Parity intent: complete direct UI/interaction parity from observed videos, including tool panels, insert workflows, PDF navigation, and configurable gesture behavior.

### Wave V1 (Competitive-plus workflows)

- `REC-06`
- `SET-01`, `SET-02`, `SET-03`, `SET-04`

V1 intent: deliver high-value daily workflows that materially improve retention and professional usability (security, session continuity, global handwriting findability, and maintainability controls).

### Wave V2 (Differentiators that surpass parity)

- `HOME-07`, `HOME-09`
- `PDF-05`, `PDF-07`
- `REC-07`, `REC-08`
- `XSURF-05`

V2 intent: deliver intelligence and resilience features that move Onyx from parity to differentiation.

### Wave Excellence (Optional premium systems)

- `EDIT-24`

Excellence intent: targeted premium interaction systems that are not required for parity but elevate professional diagramming and precision authoring UX.

## Acceptance Gates

1. Completeness check: every visibly demonstrated feature from the Samsung/Notewise recordings appears in this backlog (new item or explicit mapping to an existing item).
2. Evidence check: every checklist item references concrete Onyx code paths.
3. Priority check: every checklist item is assigned to one of `Wave Foundation`, `Wave Parity`, `Wave V1`, `Wave V2`, or `Wave Excellence`.
4. Traceability check: PRD/backlog tracker must include source-observation to backlog-ID mapping for each recorded competitor behavior cluster.
5. Index check: `C:\onyx\docs\README.md` includes a clickable link to this document.
6. Guidance check: `C:\onyx\AGENTS.md` includes the editor canonical-path clarification.
7. Repository quality gates:
   - `bun run typecheck`
   - `bun run lint`
   - `bun run android:lint`
