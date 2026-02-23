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

- [ ] `EDIT-01` Shape tool
  - Status: `Missing`
  - Competitor behavior: Notewise exposes dedicated shape drawing with predictable geometry output.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Tooling is limited to pen/highlighter/eraser/lasso.
  - What is missing: Shape tool, shape object model, shape rendering/edit handles.
  - Exact change needed: Expand tool and object models to include shape primitives (line/rect/ellipse) and add toolbar entry plus creation/edit actions.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for shape serialization and Android UI tests for create/transform/undo.

- [ ] `EDIT-02` Image insert tool
  - Status: `Missing`
  - Competitor behavior: Samsung Notes and Notewise allow image insertion as editable canvas objects.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: No insert-image action in editor toolbar.
  - What is missing: Media picker integration, image object transforms, persistence/export behavior.
  - Exact change needed: Add image insert command, object bounds metadata, and object transform handles with storage references.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Integration test for image insert->reopen->move->delete lifecycle.

- [ ] `EDIT-03` Text object tool
  - Status: `Missing`
  - Competitor behavior: Competitor editors provide explicit typed text objects separate from handwriting conversion.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\ConvertedTextBlock.kt`
  - What exists now: Recognized-text overlays exist; no authored text-box workflow.
  - What is missing: Text object insertion, editing, and object-level persistence.
  - Exact change needed: Introduce text object entity and editing UI, keeping recognition overlays as a separate data path.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Editor UI tests for text insert/edit/move/delete and persistence reload.

- [ ] `EDIT-04` Rich insert menu
  - Status: `Missing`
  - Competitor behavior: Samsung Notes uses a consolidated insert menu (image/table/GIF/audio and related actions).
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Direct tool buttons only.
  - What is missing: Unified insert-entry UX and command routing.
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
  - Status: `Partial`
  - Competitor behavior: Notewise exposes fountain/ball/calligraphy style presets directly in the pen UI.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\BrushPreset.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\config\BrushPresetStore.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Preset types exist in model/store, but toolbar selection flow is incomplete.
  - What is missing: User-facing preset picker and active preset persistence affordance.
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
  - Status: `Missing`
  - Competitor behavior: Notewise supports multi-finger undo/redo and configurable double-tap behavior.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\UndoController.kt`
  - What exists now: Undo/redo is toolbar-driven.
  - What is missing: Shortcut gesture recognizers and user-configurable double-tap behavior.
  - Exact change needed: Add two-finger and three-finger undo/redo recognizers plus configurable double-tap action map.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Gesture instrumentation tests for undo/redo and double-tap action mapping.

- [ ] `GEST-03` Visible zoom percentage indicator
  - Status: `Missing`
  - Competitor behavior: Samsung/Notewise show current zoom percentage in-editor.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorScaffold.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`
  - What exists now: Zoom state exists internally without explicit percentage UI.
  - What is missing: Persistent zoom percentage affordance in editor chrome.
  - Exact change needed: Add zoom HUD component bound to active canvas transform scale.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Compose UI test verifying zoom percentage updates as scale changes.

- [ ] `GEST-04` Zoom presets and lock-zoom behavior
  - Status: `Missing`
  - Competitor behavior: Notewise provides 50/100/200/... presets plus lock-zoom mode.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`
  - What exists now: Pinch zoom bounds exist, but no preset controls or lock toggle.
  - What is missing: Preset quick actions, fit-content action, and lock-zoom guard in gesture layer.
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

## Cross-Surface Backlog (Android + Convex + Web)

Proposed public API/interface/type updates to align with backlog items:

- `Tool` expansion beyond `PEN/HIGHLIGHTER/ERASER/LASSO` in `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt` to include object-capable tools.
- `PageTemplateConfig` expansion in `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt` for `paperSize`, `lineWidth`, `category`, and apply-scope semantics.
- New eraser mode/type-filter model (mode, radius, target filters, clear-page behavior) shared by editor state and sync metadata.
- New gesture preference model persisted for editor input settings (stylus and finger mapping + shortcut actions).
- New export/share contract surface for flattened vs layered annotation output metadata (Android + Convex consumers).

- [ ] `XSURF-01` Convex schema/contracts for new feature metadata
  - Status: `Missing`
  - Competitor behavior: Not a UI feature by itself; required to support parity features across devices.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\docs\architecture\system-overview.md`
  - What exists now: Current contracts cover existing note/page/stroke surface but not expanded object/gesture/export metadata.
  - What is missing: Canonical schema and fixture coverage for new metadata introduced by this backlog.
  - Exact change needed: Add Convex schema and contract fixtures for object tools, template scope, gesture settings, and export metadata before cross-device sync rollout.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract fixture test suite and schema drift checks in CI.

- [ ] `XSURF-02` Web implications tracking for view-only surface
  - Status: `Missing`
  - Competitor behavior: Not competitor-visible directly; needed so web does not break on new Android-authored metadata.
  - Current Onyx evidence: `C:\onyx\apps\web\README.md`, `C:\onyx\docs\architecture\system-overview.md`
  - What exists now: Web is view-only and does not yet define behavior for incoming advanced object metadata.
  - What is missing: Compatibility matrix and fallback rendering rules for unsupported editor features.
  - Exact change needed: Define and implement web decode/fallback behavior for each new metadata field with explicit unsupported-feature affordances.
  - Surface impact: `Web`, `Convex`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Web contract decoding tests for mixed-feature notes and fallback rendering snapshots.

## Balanced Priority Waves

Balanced split policy: 32 total items, split into 16 `Wave Foundation` and 16 `Wave Parity`.

### Wave Foundation (16 items)

- `HOME-01`, `HOME-02`
- `EDIT-01`, `EDIT-09`, `EDIT-10`, `EDIT-12`
- `PDF-01`, `PDF-03`
- `GEST-01`, `GEST-04`
- `TPL-01`, `TPL-04`
- `REC-02`, `REC-03`
- `XSURF-01`, `XSURF-02`

Foundation intent: unblock architecture and persistence contracts first while delivering a minimum parity baseline for home navigation, erasing, gestures, templates, export, and cross-surface compatibility.

### Wave Parity (16 items)

- `HOME-03`
- `EDIT-02`, `EDIT-03`, `EDIT-04`, `EDIT-05`, `EDIT-06`, `EDIT-07`, `EDIT-08`, `EDIT-11`
- `PDF-02`
- `GEST-02`, `GEST-03`
- `TPL-02`, `TPL-03`, `TPL-05`
- `REC-01`

Parity intent: complete visible competitor equivalence and deeper editing UX after foundation contracts and data shapes are stable.

## Acceptance Gates

1. Completeness check: every comparison feature appears exactly once in this backlog as `Missing` or `Partial`.
2. Evidence check: every checklist item references concrete Onyx code paths.
3. Priority check: every checklist item is assigned to either `Wave Foundation` or `Wave Parity` with balanced split.
4. Index check: `C:\onyx\docs\README.md` includes a clickable link to this document.
5. Guidance check: `C:\onyx\AGENTS.md` includes the editor canonical-path clarification.
6. Repository quality gates:
   - `bun run typecheck`
   - `bun run lint`
   - `bun run android:lint`
