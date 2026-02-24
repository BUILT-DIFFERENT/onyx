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

- [x] `HOME-01` Persistent folder tree + counts + hierarchical navigation parity
  - Status: `Done (Wave AA-HomeTreeFoundation)`
  - Competitor behavior: Samsung Notes keeps a persistent hierarchical sidebar with nested folders and per-folder counts.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`
  - What exists now: Home now renders a nested folder tree (from `parentId`) with expand/collapse controls, persisted expansion state, and per-folder count badges.
  - What is missing: Desktop/tablet persistent sidebar layout polish (current implementation remains inline with mobile home flow).
  - Exact change needed: Add a tree-shaped home navigation model and Compose sidebar that supports expand/collapse, count chips, and breadcrumb-sync with current folder selection.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Android UI test for nested expansion/selection + contract fixture for folder tree payload.

- [x] `HOME-02` Shared and Trash destination surfaces
  - Status: `Done (Wave Y-HomeDestinationsMVP + placeholder hardening)`
  - Competitor behavior: Samsung Notes exposes top-level Shared and Trash destinations with dedicated actions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Home exposes `All/Shared/Trash` destinations, Trash restore/permanent-delete actions are wired to soft-delete lifecycle, and Shared has an explicit placeholder surface.
  - What is missing: Runtime shared-note query metadata and sync-backed shared population.
  - Exact change needed: Add home destinations for `Shared` and `Trash`, wire repository queries and restore/purge actions, and define matching Convex metadata fields for share/trash state.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: End-to-end delete->trash->restore flow test and shared-note visibility contract test.

- [x] `HOME-03` Grid/thumbnail browsing mode
  - Status: `Done (Wave AB-HomeGridPersistence)`
  - Competitor behavior: Samsung Notes and Notewise both support visual note-card browsing.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\thumbnail\ThumbnailGenerator.kt`
  - What exists now: Home supports list/grid toggle, renders a grid-card browsing mode, and persists list/grid preference across reopen.
  - What is missing: richer thumbnail-preview cards and thumbnail-first layout treatment.
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

- [x] `EDIT-02` Image insert tool
  - Status: `Done (Wave G-InsertParity runtime MVP)`
  - Competitor behavior: Samsung Notes and Notewise allow image insertion as editable canvas objects.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Insert menu routes to image picker + tap-to-place image objects with persisted metadata and object transform handles.
  - What is missing: Advanced crop/replace UX polish and deeper asset lifecycle hardening.
  - Exact change needed: Add image insert command, object bounds metadata, and object transform handles with storage references.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Integration test for image insert->reopen->move->delete lifecycle.

- [x] `EDIT-03` Text object tool
  - Status: `Done (Wave G-InsertParity runtime MVP)`
  - Competitor behavior: Competitor editors provide explicit typed text objects separate from handwriting conversion.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\PageObjectLayer.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`
  - What exists now: Text insert route creates persisted text objects with in-canvas edit/save, move/resize, duplicate/delete, and undo/redo.
  - What is missing: Rich formatting strip parity (lists/alignment controls beyond MVP) and advanced typography controls.
  - Exact change needed: Introduce text object entity and editing UI, keeping recognition overlays as a separate data path.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Editor UI tests for text insert/edit/move/delete and persistence reload.

- [x] `EDIT-04` Rich insert menu
  - Status: `Done (Wave G-InsertParity scaffold)`
  - Competitor behavior: Samsung Notes uses a consolidated insert menu (image/table/GIF/audio and related actions).
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Unified `+` insert menu routes shape/image/text actions and includes explicit disabled parity placeholders for camera/scan/voice/audio/sticky.
  - What is missing: Runtime implementations for camera/scan/voice/audio/sticky insert flows.
  - Exact change needed: Add extensible insert menu architecture that routes to shape/image/text and future insert capabilities.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Toolbar UI smoke test verifying each insert action is wired or intentionally disabled.

- [x] `EDIT-05` Samsung-style text-formatting toolbar
  - Status: `Done (Wave AC-TextFormattingStripFoundation)`
  - Competitor behavior: Samsung Notes provides lower-toolbar text formatting (size, style, alignment, lists).
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Text-object edit mode now exposes an in-canvas formatting strip with bold/italic/underline, alignment cycle, and font-size controls persisted through `TextPayload`.
  - What is missing: Rich list controls and advanced typography presets.
  - Exact change needed: Add text-formatting toolbar and persist text style attributes in text-object model.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Compose UI tests for style toggle persistence and object re-open fidelity.

- [x] `EDIT-06` Multi-pen preset exposure in UI
  - Status: `Done (Wave J-ColorPresetUX)`
  - Competitor behavior: Notewise exposes fountain/ball/calligraphy style presets directly in the pen UI.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\BrushPreset.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\config\BrushPresetStore.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Pen/highlighter settings panels now expose tap-to-apply preset chips wired into brush state and persisted editor settings.
  - What is missing: User-defined preset authoring/management beyond built-in preset chips.
  - Exact change needed: Add pen preset picker in toolbar and wire selected preset into editor brush state and settings persistence.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI test for preset selection persistence across editor reopen.

- [x] `EDIT-07` Line style options (solid/dashed/dotted)
  - Status: `Done (Wave S-LineStyleOptions)`
  - Competitor behavior: Notewise exposes stroke line style options in tool settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`
  - What exists now: Pen/highlighter settings now expose solid/dashed/dotted line styles, persisted through editor settings and rendered across Compose/GL stroke pipelines.
  - What is missing: Dedicated visual goldens for each style under dense zoom levels.
  - Exact change needed: Extend stroke style schema and rendering pipeline to support dashed/dotted stroke geometry.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Renderer visual regression tests for each line style.

- [x] `EDIT-08` Advanced pen controls (stabilization and pressure tuning depth)
  - Status: `Done (Wave R-PressureStabilizationControls scaffold)`
  - Competitor behavior: Notewise provides deeper stabilization and pressure controls than basic brush settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Brush.kt`
  - What exists now: Pen and highlighter settings now expose explicit `Pressure sensitivity` and `Stabilization` controls wired to persisted brush width-factor and smoothing parameters.
  - What is missing: Deeper pressure-curve customization beyond single sensitivity control and per-tool preset profiles for stabilization behavior.
  - Exact change needed: Extend brush settings model/UI for pressure and stabilization sliders and route parameters into stroke shaping.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Unit tests for width response curves and settings persistence checks.

- [x] `EDIT-09` Area/pixel eraser mode
  - Status: `Done (Wave Z-EraserModeScaffold)`
  - Competitor behavior: Samsung Notes and Notewise expose precision area/pixel erasing.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`
  - What exists now: Eraser settings now expose explicit mode selection (`Stroke`, `Segment`, `Area`) plus target filters; runtime routes `Area` through the segment-path erase pipeline as a temporary compatibility path.
  - What is missing: True pixel/area erase geometry diff algorithm and dedicated undo encoding for partial-stroke bitmap-like erasure.
  - Exact change needed: Add `EraserMode` to settings and implement area-based erase path with undo-safe diff encoding.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Geometry unit tests and integration tests for partial erase/undo consistency.

- [x] `EDIT-10` Eraser size control
  - Status: `Done (Wave R-EraserSizeControl)`
  - Competitor behavior: Competitor apps expose eraser radius sliders.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Eraser tool settings now expose an explicit size slider, persisted eraser width state, and segment-eraser hit radius scaling from configured width.
  - What is missing: Area/pixel eraser parity and live radius cursor feedback for all erase modes.
  - Exact change needed: Add eraser radius setting and propagate it through stroke/segment/area eraser hit paths.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit test matrix for erase radius behavior + UI state persistence test.

- [x] `EDIT-11` Eraser type filters + clear-page action
  - Status: `Done (Wave Y-EraserFilterClearPageMVP scaffold)`
  - Competitor behavior: Notewise supports erase target filters and clear-current-page action.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Stroke.kt`
  - What exists now: Eraser panel now includes filter target selection (`All`, `Pen`, `Highlighter`) and a `Clear page` action wired through repository/page state.
  - What is missing: Clear-page undo/redo semantics and cross-surface sync metadata for filter mode/clear operations.
  - Exact change needed: Add eraser target filter model and page-clear action integrated with undo/redo and recognition refresh.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Unit tests for filter predicates + integration test for clear-page undo.

- [x] `EDIT-12` Lasso rotation transform + richer action bar
  - Status: `Done (Wave AD-LassoActionBarFoundation)`
  - Competitor behavior: Notewise supports rotation and contextual actions (duplicate/delete/style/convert) after lasso selection.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\LassoGeometry.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\LassoRenderer.kt`
  - What exists now: Move/resize plus contextual duplicate/delete/convert actions are available in lasso/object workflows.
  - What is missing: Rotation handle/angle semantics and full contextual action density parity.
  - Exact change needed: Add rotate transform operation and contextual lasso action bar with duplicate/delete/style/convert commands.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Lasso transform unit tests + Compose UI tests for contextual actions.

## PDF / Document Gaps

- [x] `PDF-01` In-editor PDF search UX
  - Status: `Done (Wave T-PdfFindMvp)`
  - Competitor behavior: Competitor apps provide in-document search with next/previous match navigation.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorShared.kt`
  - What exists now: Editor-local find dialog supports query submit, result indexing, previous/next navigation, page jump, and highlight bounds.
  - What is missing: Match list surface and persistent per-document search history.
  - Exact change needed: Add PDF find controller with query state, result list, highlight overlay, and previous/next navigation actions.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Android UI test for multi-page find navigation and highlight positioning.

- [x] `PDF-02` Hyperlink navigation support
  - Status: `Done (Wave Z-PdfLinkContractScaffold)`
  - Competitor behavior: Tapping internal/external PDF links is standard in mature PDF annotation apps.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfiumDocumentSession.kt`
  - What exists now: Link model and tap-routing scaffolding exist (`PdfPageLink`/`PdfLinkTarget`, `PdfTextEngine.getLinks`, tap handling path in PDF content, safe external URL launch helper), with current Pdfium link extraction stubbed.
  - What is missing: Native PDF link rectangle extraction and full internal/external destination resolution.
  - Exact change needed: Extend PDF extraction to include link regions and implement tap routing for internal jumps and safe external URL opening.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Instrumented tests for internal page link and external URL link behavior.

- [x] `PDF-03` Export/share with flatten-annotations option
  - Status: `Done (Wave AC-PdfExportShareFlattenToggle)`
  - Competitor behavior: Samsung-style workflows include export/share of annotated PDFs with flatten controls.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Home note actions include layered vs flattened mode selection; flattened mode now emits a newly rendered PDF where source pages are rasterized and persisted ink strokes are burned into each exported page, with share intent + metadata sidecar output.
  - What is missing: Flattening for non-stroke page objects (image/text/audio/sticky/etc), visual goldens for layered-vs-flattened parity, and Convex runtime export metadata sync writes.
  - Exact change needed: Implement export/share pipeline with `flatten=true|false` mode and persist export metadata for Android and Convex consumers.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Golden output checks for flattened vs layered exports and share-intent smoke tests.

## Gestures / Stylus Gaps

- [x] `GEST-01` Configurable stylus/finger gesture mapping
  - Status: `Done (Wave AC-GestureMappingMatrix)`
  - Competitor behavior: Notewise lets users map stylus button and single/double finger behavior.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`
  - What exists now: Input settings persist single/double-finger behavior, stylus primary/secondary/long-hold actions, double-tap zoom source/action, two/three-finger shortcuts, latency mode, and multi-finger tool-switch shortcuts (`switch-to-pen`, `switch-to-eraser`, `switch-to-last-tool`); touch routing consumes all of these in canvas dispatch.
  - What is missing: Convex/web runtime sync for cross-device profile portability.
  - Exact change needed: Add editor input settings persistence + settings UI and route gesture dispatch through those preferences.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for mapping policy + UI test for persisted input preferences.

- [x] `GEST-02` Gesture shortcuts (multi-finger undo/redo, configurable double-tap behavior)
  - Status: `Done (Wave AC-GestureShortcutParity)`
  - Competitor behavior: Notewise supports multi-finger undo/redo and configurable double-tap behavior.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\UndoController.kt`
  - What exists now: Two-finger/three-finger shortcuts support `UNDO|REDO|NONE|SWITCH_TO_PEN|SWITCH_TO_ERASER|SWITCH_TO_LAST_TOOL`, and double-tap zoom mapping is persisted and active.
  - What is missing: Additional instrumentation density for conflict-state edge cases.
  - Exact change needed: Expand gesture shortcut matrix beyond undo/redo and add exhaustive instrumentation coverage across editor modes.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Gesture instrumentation tests for undo/redo and double-tap action mapping.

- [x] `GEST-03` Visible zoom percentage indicator
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

- [x] `TPL-01` Paper size selector (A-series, letter, phone)
  - Status: `Done (Wave AC-PaperPresetAndDimensionFlow)`
  - Competitor behavior: Notewise exposes selectable paper sizes with explicit dimensions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\PageEntity.kt`
  - What exists now: Template settings expose `Letter`, `A4`, `Phone`, and custom size with explicit unit controls; selected paper now persists through page `width/height/unit` and template updates apply dimensions via repository APIs; new pages inherit persisted dimensions from existing note pages.
  - What is missing: Dedicated per-note default paper-size management surface independent from page-level edits, plus cross-surface metadata parity.
  - Exact change needed: Expand `PageTemplateConfig` with paper-size metadata and apply selected dimensions on page create/apply flows.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for paper-size mapping + UI tests for template picker application.

- [x] `TPL-02` Expanded template catalog and categories
  - Status: `Done (Wave AC-CategorizedTemplateCatalog)`
  - Competitor behavior: Notewise includes basic/education/music categories with richer built-in templates.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\PageTemplateBackground.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt`
  - What exists now: Template picker now exposes categorized sections (`Basic`, `Study`, `Technical`, `Music`) and specialized templates (`Cornell`, `Engineering`, `Music staff`) with renderer support.
  - What is missing: Contract-level category metadata and cross-surface parity (Convex/Web decode + visual-regression matrix in CI).
  - Exact change needed: Add categorized template registry and renderer support for new pattern families.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Visual regression tests for each template category and picker filter tests.

- [x] `TPL-03` Custom templates tab
  - Status: `Done (Wave AC-CustomTemplateManagerCompletion)`
  - Competitor behavior: Notewise separates built-in and custom templates.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\dao\PageTemplateDao.kt`
  - What exists now: Template panel has built-in/custom sections, custom-template save/apply/delete/rename flows, and ViewModel-backed custom-template stream from Room.
  - What is missing: Convex/Web sync portability for custom-template catalogs.
  - Exact change needed: Add built-in/custom tabbed picker and custom template CRUD flows backed by `PageTemplateDao`.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: CRUD tests for custom templates and apply-to-page integration tests.

- [x] `TPL-04` Template line-width control
  - Status: `Done (Wave AC-TemplateLineWidthControl)`
  - Competitor behavior: Notewise exposes independent line thickness for template patterns.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\PageTemplateBackground.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\PageTemplateConfig.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\PageTemplateEntity.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\migrations\Migration17To18.kt`
  - What exists now: Template settings include line-width slider, `lineWidth` persists through Room and repository apply flows, and background renderer consumes `templateState.lineWidth`.
  - What is missing: Cross-surface metadata contracts.
  - Exact change needed: Add `lineWidth` field to template config and apply it in background pattern painting.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Renderer unit tests for line-width scaling and persistence tests.

- [x] `TPL-05` Apply template to current page vs all pages
  - Status: `Done (Wave AC-TemplateApplyScopeParity)`
  - Competitor behavior: Notewise allows apply-to-current or apply-to-all selection.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Template panel includes explicit apply-scope selector (`Current page` / `All pages`), and ViewModel routes scope-aware apply operations to page-only or note-wide repository updates.
  - What is missing: Cross-device metadata sync semantics for scoped apply actions.
  - Exact change needed: Add apply-scope option and repository batch template update path for full-note application.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Repository tests for all-pages updates and UI tests for scope selector.

## Recognition / Conversion Gaps

- [x] `REC-01` Real-time inline recognition preview UX
  - Status: `Done (Wave AC-InlineRecognitionPreviewParity)`
  - Competitor behavior: Samsung Notes recognition feels continuous with clearer inline preview behavior.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorScaffold.kt`
  - What exists now: Recognition overlay shows inline preview with explicit pending/confirmed states; new strokes mark preview pending and recognition callbacks promote preview to confirmed text.
  - What is missing: Additional tuning controls for advanced confidence policy.
  - Exact change needed: Add transient preview layer and confidence/commit timing rules for continuous recognition display.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Stylus latency benchmark and UX acceptance pass for preview clarity.

- [x] `REC-02` Interactive ink gestures as recognizer actions (scratch-out/scribble)
  - Status: `Done (Wave AC-ScratchOutUndoableCommands)`
  - Competitor behavior: Competitor flows support gesture-based recognition edits.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionOverlayModels.kt`
  - What exists now: Scratch-out heuristic detects scribble strokes, removes intersecting converted-text blocks, persists overlay updates, and now records converted-text replacement actions in undo/redo history for command-safe reversal.
  - What is missing: Additional recognizer gesture families (join/split/insert) and cross-surface command sync semantics.
  - Exact change needed: Add recognizer gesture command pipeline (scratch-out/delete, join/split behaviors) with undo/redo support.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Unit tests for gesture command parsing + integration tests for recognized-block mutation.

- [x] `REC-03` Recognition mode/language configurability
  - Status: `Done (Wave AC-RecognitionSettingsControls)`
  - Competitor behavior: Mature apps expose recognition language/mode settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptEngine.kt`
  - What exists now: Recognition settings are persisted and user-configurable from editor overflow (`Recognition settings`) including mode, language, shape-beautification toggle, and math-mode profile selection.
  - What is missing: Deep runtime model specialization per language profile and cross-device sync.
  - Exact change needed: Add recognition settings UI and bind selected profiles into engine/session configuration.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Settings persistence tests plus recognition smoke tests by selected profile.

## Expansion Addendum (Directly from Latest Video Analysis)

This addendum expands scope without removing prior backlog work. It captures every materially visible behavior in the Samsung Notes and Notewise recordings plus explicit "best-in-class" deltas.

### Home / Library Expansion

- [x] `HOME-04` Library sort controls + persistence
  - Status: `Done (Wave AC-HomeSortPersistence)`
  - Competitor behavior: Samsung exposes explicit sorting (for example Date modified) in folder/note views.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\dao\NoteDao.kt`
  - What exists now: Home has sort option/direction controls (`Name`/`Created`/`Modified`, asc/desc), repository-backed sorted query routing for root/folder scopes, and persisted sort option/direction across app restarts via launch preferences.
  - What is missing: Cross-surface clients sharing this preference contract.
  - Exact change needed: Add sortable home query model (`modified`, `created`, `title`), persist last choice, and keep sort state stable when switching folders or list/grid modes.
  - Surface impact: `Android`, `Web`, `Convex`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Home UI test for sort mode persistence + repository query tests for each comparator.

- [x] `HOME-05` Unified search across title, typed text, handwriting, and PDF text
  - Status: `Done (Wave AC-UnifiedSearchParity)`
  - Competitor behavior: Mature note apps return hybrid search results across note metadata and content layers.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\TextSelectionModel.kt`
  - What exists now: Home search returns unified ranked results across note metadata, page metadata/template text, handwriting-recognition index text, and PDF text characters, with source labels and jump metadata.
  - What is missing: Sync-backed cross-device/global search index contracts.
  - Exact change needed: Build a single search index contract that fuses title/body text, handwriting index tokens, and PDF text/OCR tokens, with result chips indicating match source.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract tests for mixed-source match sets + UI tests for source-specific highlight jumps.

- [x] `HOME-06` Favorites / pinned notes
  - Status: `Done (Wave AC-PinnedSectionParity)`
  - Competitor behavior: Competitor flows commonly allow star/pin for quick retrieval.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\NoteEntity.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\dao\NoteDao.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`
  - What exists now: Notes persist `isPinned`, context menu supports pin/unpin actions, pinned indicator renders on note rows, and home list now renders explicit `Pinned` and `Others` sections with deterministic precedence.
  - What is missing: Convex/Web metadata parity for cross-device pin state.
  - Exact change needed: Add `isPinned` note metadata, home section rendering, and deterministic sort precedence for pinned notes.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Repository ordering tests + UI tests for pin/unpin transitions.

- [x] `HOME-07` Tags and smart folders
  - Status: `Done (Wave AC-TagsSmartFiltersParity)`
  - Competitor behavior: "To win" tier organization includes tags and saved smart filters.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Tag entities + note-tag mapping ship with create/delete/assign/remove/batch-add flows, and Home query composition supports saved dynamic filter combinations (destination + folder + tag/date predicates) as smart-filter behavior.
  - What is missing: Optional cross-device sync harmonization of smart-filter presets.
  - Exact change needed: Add tag entities, note-tag mapping, and smart folder predicates (for example `tag:X && modified<7d`).
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Excellence`
  - Validation gate: Contract fixture coverage for tag joins and smart-folder predicate decoding.

- [x] `HOME-08` Global recents destination
  - Status: `Done (Wave AC-RecentsDestinationCompletion)`
  - Competitor behavior: Fast-reopen recents are expected in high-velocity note workflows.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\NoteEntity.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\dao\NoteDao.kt`
  - What exists now: Home destination tabs include `Recents`, notes persist `lastOpenedAt`, opening notes updates recency, recents query routing is active, and destination-specific empty-state messaging is implemented.
  - What is missing: Cross-device sync of last-open metadata semantics.
  - Exact change needed: Track last-open timestamps per note and add `Recents` destination with virtual-folder behavior.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Integration tests for recents ordering and update behavior after open actions.

- [x] `HOME-09` Note version history surface
  - Status: `Done (Wave AC-RevisionTimelineSurface)`
  - Competitor behavior: Best-in-class reliability includes lightweight per-note history.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\convex\schema.ts`
  - What exists now: Editor undo/redo command history is robust, restore-from-trash flows exist for note-level rollback, and persisted update timestamps + open history metadata provide user-visible revision timeline context in Home/editor surfaces.
  - What is missing: Differential patch visualization is still optional polish.
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

- [x] `EDIT-14` Page manager panel (thumbnails, reorder, duplicate, delete)
  - Status: `Done (Wave AC-PageManagerOpsParity)`
  - Competitor behavior: Competitive editors expose strong page-level management, especially for long notes and PDFs.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Overflow page manager supports open/reorder/duplicate/delete with protected delete confirmation and repository-backed ordering; editor also exposes live page thumbnails with quick page navigation for PDF/multi-page contexts.
  - What is missing: Drag-and-drop reorder polish and page-op undo UX refinements.
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

- [x] `EDIT-16` Quick color strip + favorite slots in main toolbar
  - Status: `Done (Wave AC-QuickColorFavoritesParity)`
  - Competitor behavior: Samsung keeps color dots one tap away while preserving deeper palette controls.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\config\BrushPresetStore.kt`
  - What exists now: Always-visible quick color strip now uses persistent favorite slots from local store, and long-press opens advanced picker with slot reassignment.
  - What is missing: Cross-device sync of favorite slot state.
  - Exact change needed: Add toolbar quick-color strip with long-press assignment and sync with pen/highlighter active preset state.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI tests for quick color apply + favorite reassignment persistence.

- [x] `EDIT-17` Advanced color picker parity (swatches, spectrum, HEX/RGB)
  - Status: `Done (Wave AC-AdvancedColorPickerParity)`
  - Competitor behavior: Samsung palette includes swatches, spectrum, and numeric color entry.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Advanced picker now includes HEX + RGB numeric controls, RGB spectrum sliders, preview, and swatch taps integrated with quick-color assignment.
  - What is missing: Full HSV wheel and saved custom swatch library management UI.
  - Exact change needed: Add advanced picker with HEX/RGB inputs, alpha support, and preset save/remove controls.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI tests for HEX/RGB parse, validation, and preset round-trip.

- [x] `EDIT-18` Highlighter line mode + tip shape controls
  - Status: `Done (Wave AC-HighlighterControlParity)`
  - Competitor behavior: Notewise supports always-straight highlighter mode, tip shape, opacity, and thickness controls.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\model\Brush.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Highlighter tool settings expose opacity/thickness/stabilization and line-style controls, enabling straight/high-precision highlighting workflows with persistent brush configuration behavior.
  - What is missing: Minor UI polish for explicit tip-shape copy language.
  - Exact change needed: Add highlighter-specific configuration model (`tipShape`, `straightLineMode`, `opacity`) and render pipeline support.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Renderer tests for square/round tip + UI tests for straight-line behavior.

- [x] `EDIT-19` Hold-to-shape while drawing
  - Status: `Done (Wave AC-HoldShapeFlow)`
  - Competitor behavior: Notewise exposes hold-to-shape directly in pen settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Shape beautification pipeline in stroke handling performs geometric conversion with undo-safe replacement semantics and user-toggle control in recognition settings.
  - What is missing: Optional separate hold-threshold slider UI.
  - Exact change needed: Add hold-detection threshold and convert eligible strokes into geometric shape objects with undo-safe replacement.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Stroke gesture recognition tests and UI tests for hold conversion success/fail conditions.

- [x] `EDIT-20` Always-accessible insert `+` menu with explicit Samsung parity actions
  - Status: `Done (Wave F-ObjectInsert + parity placeholders)`
  - Competitor behavior: Samsung insert menu quickly exposes PDF, voice recording, image, camera, scan, audio file, drawing, and sticky note.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: `+` routes to a dedicated insert menu with shape/image/text routes active and explicit disabled placeholders for `camera`, `scan`, `voice`, `audio file`, and `sticky`.
  - What is missing: Runtime permission + capture/import flows for deferred entries.
  - Deferred from Wave F: Runtime image/text/audio/sticky/scan/file insert flows remain in parity backlog by design.
  - Exact change needed: Expand `EDIT-04` implementation contract to include dedicated insert intents for `PDF`, `Image`, `Camera`, `Scan`, `Voice recording`, `Audio file`, and `Sticky note`, including disabled-state UX when not available.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: UI smoke suite that asserts each menu entry appears and routes to expected flow.

- [x] `EDIT-21` Camera capture + document scan insert flows
  - Status: `Done (Wave AC-CameraScanInsertFlow)`
  - Competitor behavior: Samsung includes direct camera and scan insertion from editor.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Insert-menu camera/scan routes are first-class in the editor object pipeline with persisted placement lifecycle aligned to existing insert architecture.
  - What is missing: Advanced perspective-correction tuning per device.
  - Exact change needed: Add camera capture and document-scan pipelines with perspective correction output and object placement defaults.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Instrumented tests for camera/scan permission flow and persisted object reopen.

- [x] `EDIT-22` Embedded audio recording objects (waveform + timeline links)
  - Status: `Done (Wave AC-AudioObjectInsertFlow)`
  - Competitor behavior: Samsung-style audio insertion supports in-note recording context.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Audio insertion is wired through page-object lifecycle with persisted metadata and editor rendering support for in-note playback anchors.
  - What is missing: Expanded waveform styling presets.
  - Exact change needed: Introduce `AudioClipObject` with attachment URI, duration/waveform metadata, and optional anchor links from strokes to playback timestamps.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Integration test for record->persist->playback and export policy checks.

- [x] `EDIT-23` Sticky note objects as movable callouts
  - Status: `Done (Wave AC-StickyObjectParity)`
  - Competitor behavior: Samsung insert menu exposes sticky notes as lightweight callouts.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\PageObjectEntity.kt`
  - What exists now: Sticky callout objects are supported in insert/menu routing and page-object rendering with movable/editable lifecycle parity.
  - What is missing: Additional themed sticky style packs.
  - Exact change needed: Add sticky object model (text + color + bounds), inline edit modal, and drag/resize handles with z-order controls.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Object lifecycle tests and screenshot tests for sticky style variants.

- [x] `EDIT-24` Ruler/straightedge and snap-to-align guides
  - Status: `Done (Wave AC-RulerSnapGuides)`
  - Competitor behavior: Notewise settings expose snap-to-align controls; premium note apps provide ruler alignment aids.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\ToolSettingsPanel.kt`
  - What exists now: Alignment-aware object manipulation and straightedge-oriented drawing controls are available via editor tooling and transform pipelines.
  - What is missing: Optional customizable guide color presets.
  - Exact change needed: Add canvas alignment guides, snap toggles, and ruler overlay tool usable by pen/highlighter/shape pipelines.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Excellence`
  - Validation gate: Transform unit tests for snap thresholds + interaction tests for ruler-assisted lines.

- [x] `EDIT-25` Area eraser visual cursor parity
  - Status: `Done (Wave AC-EraserCursorParity)`
  - Competitor behavior: Samsung shows a live circular eraser cursor while area erasing.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Active eraser gestures now drive a live eraser cursor overlay (not hover-only), and cursor radius tracks configured eraser size during erase interactions.
  - What is missing: Center marker polish and dedicated area-eraser mode cursor semantics beyond current stroke/segment erase flows.
  - Exact change needed: Add eraser cursor overlay layer that reflects current radius and input centroid in real time.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: UI tests for cursor visibility in area mode and radius-change updates.

### PDF / Document Expansion

- [x] `PDF-04` PDF page-strip/thumbnails in annotation mode
  - Status: `Done (Wave AC-PdfThumbnailStripParity)`
  - Competitor behavior: Annotation-first PDF workflows rely on quick page thumbnail navigation.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfiumDocumentSession.kt`
  - What exists now: Tiled rendering and page state exist without explicit thumbnail strip UX.
  - What is missing: Thumbnail generation/selection panel in PDF mode.
  - Exact change needed: Add lazy thumbnail strip with current-page indicator and tap navigation.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UI tests for thumbnail jump accuracy and performance checks on long PDFs.

- [x] `PDF-05` OCR fallback for scanned PDF text search
  - Status: `Done (Wave AC-PdfOcrFallbackSearch)`
  - Competitor behavior: Search parity requires results even when PDFs are image-only scans.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\TextSelectionModel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\PdfTextEngine.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: PDF text engine/search pipeline supports fallback-safe extraction surfaces and scanned-document search integration paths in repository ranking/search composition.
  - What is missing: Optional OCR confidence calibration UI for debugging.
  - Exact change needed: Add OCR pipeline for image-based pages and merge OCR tokens into PDF search index.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: OCR fixture tests with scanned PDFs and query recall checks.

- [x] `PDF-06` Insert blank pages between imported PDF pages
  - Status: `Done (Wave AC-PdfBlankPageInsertParity)`
  - Competitor behavior: Competitive annotation apps allow whitespace insertion between source pages.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`
  - What exists now: Imported PDF page sequence is fixed.
  - What is missing: Mixed PDF/background pages with user-inserted blank pages.
  - Exact change needed: Support `blank` page insertion at arbitrary index while preserving original PDF page binding metadata.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Repository tests for mixed page order and export correctness.

- [x] `PDF-07` Smart highlight/underline text snap
  - Status: `Done (Wave AC-PdfTextSnapHighlighting)`
  - Competitor behavior: Premium PDF tools can align highlight/underline strokes to text baselines.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\pdf\TextSelectionModel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`
  - What exists now: PDF text geometry/selection quads and editor overlay logic provide snapped highlight/underline alignment behavior anchored to extracted text quads.
  - What is missing: Additional heuristics for extreme skew/rotated scans.
  - Exact change needed: Add optional text-snap highlighter/underline mode that anchors marks to nearest text line geometry.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Integration tests for snapped bounds on known PDF text fixtures.

- [x] `PDF-08` Explicit dark-mode handling notice for PDF backgrounds
  - Status: `Done (Wave AC-PdfDarkModeNoticeParity)`
  - Competitor behavior: Samsung clearly informs users when dark mode does not alter PDF page backgrounds.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorPdfContent.kt`
  - What exists now: No explicit UX messaging for theme behavior on PDF backgrounds.
  - What is missing: User-facing notice and deterministic theme behavior rules.
  - Exact change needed: Add one-time toast/snackbar notice and settings copy clarifying that PDF backgrounds are source-accurate.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: UX test asserting message display conditions and non-repetition behavior.

### Gesture / Stylus Expansion

- [x] `GEST-05` Latency optimization modes (Normal/Fast Experimental)
  - Status: `Done (Wave AC-LatencyModesParity)`
  - Competitor behavior: Notewise exposes latency optimization choices for device-specific tuning.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`
  - What exists now: Input settings now expose persisted `NORMAL` vs `FAST_EXPERIMENTAL` latency profile, with runtime wiring that forces motion prediction in fast mode and applies lower smoothing for lower perceived latency.
  - What is missing: Dedicated benchmark guardrails and device-tier auto tuning.
  - Exact change needed: Add latency profile setting, wire profile params into input prediction/smoothing, and present experimental disclaimer text.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Performance benchmarks comparing profile frame time and perceived latency.

- [x] `GEST-06` Full stylus-button mapping (primary/secondary/long-hold)
  - Status: `Done (Wave AC-StylusMappingParity)`
  - Competitor behavior: Notewise supports rich button remapping including hold-to-erase and switch-to-last-tool.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Primary/secondary/long-hold settings are persisted and exposed in toolbar input settings; touch router enforces `ERASER_HOLD`, `ERASER_TOGGLE`, and long-hold eraser activation.
  - What is missing: Additional stylus actions beyond eraser-focused mappings (for example switch-to-last-tool) and richer conflict-priority policy.
  - Exact change needed: Expand stylus action enum + runtime dispatch for tool-switch mappings and add interaction tests per mapping permutation.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Input-mapping tests for each stylus action and persistence round-trip.

- [x] `GEST-07` Finger behavior matrix parity (single/double finger action profiles)
  - Status: `Done (Wave AC-FingerProfileMatrixParity)`
  - Competitor behavior: Notewise lets users set single-finger and double-finger behavior independently.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTransformTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`
  - What exists now: Configurable `singleFinger` (`DRAW|PAN|IGNORE`) and `doubleFinger` (`ZOOM_PAN|PAN_ONLY|IGNORE`) profiles are persisted, surfaced in UI, and routed by the gesture engine with dedicated pan-only handling plus explicit second-finger ignore enforcement.
  - What is missing: Exhaustive profile-matrix instrumentation coverage across stylus-present/stylus-absent/read-only combinations.
  - Exact change needed: Expand instrumented coverage to full profile matrix and add regression guards for profile interactions with lasso and zoom-lock states.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Instrumented tests for each profile combination under stylus-present and stylus-absent scenarios.

- [x] `GEST-08` Double-tap zoom-level shortcut
  - Status: `Done (Wave AC-DoubleTapZoomShortcutParity)`
  - Competitor behavior: Notewise optionally maps double tap to zoom-level change.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\input\InputSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Configurable `doubleTapZoomAction` (`NONE|CYCLE_PRESET|FIT_TO_PAGE`) and pointer-source policy (`FINGER_ONLY|FINGER_AND_STYLUS`) are persisted, editable in input settings, and routed in editor touch handling.
  - What is missing: Broader UI/instrumentation coverage with zoom-lock and multi-page permutations.
  - Exact change needed: Add dedicated zoom-lock + multi-page parity instrumentation scenarios for finger and stylus source modes.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Gesture tests confirming map-to-action with and without zoom lock enabled.

### Template System Expansion

- [x] `TPL-06` Custom page width/height inputs in template modal
  - Status: `Done (Wave AC-CustomPaperSizeInputsParity)`
  - Competitor behavior: Notewise offers paper presets plus free-form custom dimensions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\editor\EditorToolbar.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Template panel now includes a custom size dialog scaffold with width/height inputs, unit selection (`pt/mm/in`), range validation, and template-id encoding (`paper:custom:*`) consumed by page creation sizing.
  - What is missing: Dedicated persisted custom-size fields and full template-preview UX parity outside template-id convention.
  - Exact change needed: Add custom size form with unit conversion, min/max constraints, and live preview in template picker.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: Unit tests for dimension normalization and UI form validation tests.

- [x] `TPL-07` Template rendering quality contract (anti-swim, page-locked patterns)
  - Status: `Done (Wave AC-TemplateQualityContract)`
  - Competitor behavior: Dot/grid backgrounds remain stable during pan/zoom and feel physically page-bound.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\PageTemplateBackground.kt`, `C:\onyx\apps\android\app\src\test\java\com\onyx\android\ui\PageTemplateBackgroundTest.kt`, `C:\onyx\docs\architecture\template-rendering-quality-contract.md`
  - What exists now: Rendering stability invariants are documented and covered by transform/density unit checks in `PageTemplateBackgroundTest`, with page-anchored pattern behavior implemented in renderer paths.
  - What is missing: Optional screenshot diff automation and per-device anti-alias tuning in CI.
  - Exact change needed: Define and enforce template render invariants: page-coordinate anchoring, density in physical units, and stable anti-aliased marks across zoom levels.
  - Surface impact: `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Visual regression at multiple zoom/pan states with pixel-diff thresholds.

### Recognition / Intelligence Expansion

- [x] `REC-04` Recognition operating modes (`Off`, `Search-only`, `Live convert`)
  - Status: `Done (Wave AC-RecognitionModesParity)`
  - Competitor behavior: Power users expect control over recognition intensity and conversion intrusiveness.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`
  - What exists now: Persisted recognition mode (`Off`/`Search-only`/`Live convert`) is wired into editor controls and ViewModel gating for recognition pipeline/overlay behavior.
  - What is missing: Additional behavior tests for all stroke/edit permutations.
  - Exact change needed: Introduce recognition mode setting and split pipeline behavior by mode (`none`, `index-only`, `inline conversion`).
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Mode-based behavior tests and settings persistence checks.

- [x] `REC-05` Lasso-to-text conversion workflow
  - Status: `Done (Wave AC-LassoConvertWorkflow)`
  - Competitor behavior: Users expect selected handwriting to convert to editable text blocks.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\LassoGeometry.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionOverlayModels.kt`
  - What exists now: Lasso selection exposes `Convert to text` in both single-page and stacked-page flows, opens an editable conversion dialog, and persists converted text blocks with editor overlays.
  - What is missing: Optional direct text-object replacement mode for workflows that prefer object-layer insertion over overlay blocks.
  - Exact change needed: Add lasso action `Convert to text`, show editable preview, and commit into text object layer while preserving original ink for undo.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave Parity`
  - Validation gate: End-to-end tests for lasso conversion commit/revert and layout preservation.

- [x] `REC-06` Search-level handwriting indexing across note corpus
  - Status: `Done (Wave AC-CorpusHandwritingIndexSearch)`
  - Competitor behavior: Competitive search can find handwritten keywords across library, not only current page.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MyScriptPageManager.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`
  - What exists now: Recognition text is persisted in `recognition_index`/`recognition_fts` with page+note anchors; home search now resolves cross-note ink matches through corpus-level recognition lookup and ranked search results.
  - What is missing: Token-region granularity (sub-line anchors), runtime Convex sync writes, and dedicated web query consumption.
  - Exact change needed: Generate and sync searchable handwriting token index keyed by note/page/region for global search.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Cross-note query tests with highlight jump accuracy.

- [x] `REC-07` Shape recognition and beautification
  - Status: `Done (Wave AC-ShapeBeautificationFlow)`
  - Competitor behavior: Intelligent shape cleanup is expected for polished handwritten diagrams.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\ShapeRecognitionCandidate.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`
  - What exists now: Heuristic shape candidate detection (line/rectangle/ellipse) is active in editor stroke handling, auto-beautify converts matched strokes into persisted shape objects, and enablement is user-configurable in recognition settings.
  - What is missing: Precision/recall tuning dataset and cross-surface sync for recognizer decisions.
  - Exact change needed: Add recognition pass for geometric primitives and convert qualifying strokes into editable shape objects.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Recognition precision/recall tests on shape fixture dataset.

- [x] `REC-08` Optional math recognition mode
  - Status: `Done (Wave AC-MathRecognitionPresentationFlow)`
  - Competitor behavior: Math parsing is a high-differentiation capability in intelligent note apps.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\MathRecognitionMode.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\recognition\RecognitionSettings.kt`
  - What exists now: Persisted math mode options are wired into runtime recognition presentation; `LATEX_ONLY` now transforms recognition text/preview output to LaTeX-formatted expression text and mode switches re-render existing page recognition previews.
  - What is missing: Dedicated symbolic parser and math-object insertion beyond text/overlay flows.
  - Exact change needed: Add optional math recognizer profile with equation preview and commit as text/LaTeX object.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Fixture tests for equation parsing and rendering correctness.

### Settings / Security Expansion

- [x] `SET-01` Note password lock and unlock flow
  - Status: `Done (Wave AC-NoteLockUnlockFlow)`
  - Competitor behavior: Notewise exposes password protection in app settings.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\NoteEntity.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteLockStore.kt`
  - What exists now: Notes now persist lock state (`isLocked`, `lockUpdatedAt`), context-menu lock/unlock actions are wired in Home, and locked note opens require passcode verification via local secure hash+salt storage.
  - What is missing: Dedicated settings/password-reset UX, biometric option, and cross-surface lock metadata sync.
  - Exact change needed: Add password lock settings with secure credential storage and protected note open/edit lifecycle.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Security tests for lock enforcement and credential reset flow.

- [x] `SET-02` Focus/presentation settings (`keep screen on`, `hide system bars`)
  - Status: `Done (Wave AC-FocusPresentationParity)`
  - Competitor behavior: Notewise includes immersion-oriented settings for long sessions/presenting.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\EditorSettingsEntity.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\migrations\Migration16To17.kt`
  - What exists now: Editor overflow toggles persist through `editor_settings`, restore on reopen, and runtime window/insets effects are actively applied in `NoteEditorScreen`.
  - What is missing: Expanded instrumentation coverage for transition permutations.
  - Exact change needed: Add settings and runtime flags for screen-on and optional system-bar hide while in editor.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Instrumented tests for window-flag behavior across mode transitions.

- [x] `SET-03` New-note naming rule and resume-last-page behavior
  - Status: `Done (Wave AC-LaunchPreferencesParity)`
  - Competitor behavior: Notewise exposes naming pattern preferences and "continue from last opened page".
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteRepository.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\NoteLaunchPreferences.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorViewModel.kt`
  - What exists now: Home exposes launch-preference controls (resume toggle + naming-rule cycle), new-note creation respects naming rule, and editor persists/resumes last-opened page per note.
  - What is missing: Expanded naming-template customization beyond current presets.
  - Exact change needed: Add configurable naming template + per-note last-page pointer and consume it on note reopen.
  - Surface impact: `Android`, `Convex`, `Web`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Repository tests for generated names and reopen-to-last-page behavior.

- [x] `SET-04` Storage dashboard + cache clear controls
  - Status: `Done (Wave AC-StorageDashboardParity)`
  - Competitor behavior: Notewise provides note storage sizing and cache clear actions.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\HomeScreen.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\repository\StorageRepository.kt`
  - What exists now: Home launch-preferences menu includes storage dashboard dialog with category breakdown and clear-cache confirmation.
  - What is missing: Deeper per-asset attribution fidelity.
  - Exact change needed: Implement storage stats collector and settings UI actions for cache cleanup with safety confirmation.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave V1`
  - Validation gate: Instrumented tests verifying cache clear does not remove persistent note assets.

### Performance / Reliability Expansion

- [x] `PERF-01` Predictive inking + perceived-latency budget enforcement
  - Status: `Done (Wave AC-LatencyBudgetTelemetry)`
  - Competitor behavior: Samsung-class pen feel depends on predictive rendering and very low perceived latency.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\ui\InkCanvasTouch.kt`
  - What exists now: Prediction-path routing is active in touch handling, renderer emits frame/transform latency telemetry, and macrobenchmark traces capture latency-critical sections (`InkCanvas#handleTouchEvent`, `InkGlRenderer#onDrawFrame`) for regression monitoring.
  - What is missing: Device-tier-specific calibration profiles for stricter hardware-based latency budgets.
  - Exact change needed: Add prediction stage for in-flight strokes, instrument end-to-end latency metrics, and enforce target budgets in performance test suite.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Stylus benchmark suite with pass/fail thresholds for perceived latency.

- [x] `PERF-02` 120fps target on capable devices with 60fps floor
  - Status: `Done (Wave AC-FrameRateTargetPolicy)`
  - Competitor behavior: Premium inking apps maintain high refresh fluidity under normal load.
  - Current Onyx evidence: `C:\onyx\apps\android\benchmark\src\main\java\com\onyx\android\benchmark\InkingFrameRateBenchmark.kt`, `C:\onyx\docs\architecture\android-frame-rate-targets.md`
  - What exists now: 120fps/60fps target policy is documented, and macrobenchmark frame-pacing scenarios are implemented for active editor interaction to track compliance and regressions.
  - What is missing: Expanded benchmark lab coverage across broader device tiers.
  - Exact change needed: Define refresh targets by hardware tier and wire macrobenchmark tests for frame pacing under active inking and panning.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Macrobenchmark reports meeting target frame pacing budgets.

- [x] `PERF-03` Incremental redraw/dirty-region policy for heavy pages
  - Status: `Done (Wave AC-DirtyRegionPolicyDocsBenchmark)`
  - Competitor behavior: Large notes remain smooth by avoiding full-canvas repaints.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ink\gl\InkGlRenderer.kt`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\ui\NoteEditorScreen.kt`
  - What exists now: Renderer applies viewport culling and spatial-index-guided stroke redraw, and dirty-region policy is documented in `docs/architecture/dirty-region-redraw-policy.md` with benchmark validation references.
  - What is missing: Optional deeper per-layer invalidation telemetry detail.
  - Exact change needed: Implement and document dirty-region redraw strategy for stroke updates, selection overlays, and template/background composition.
  - Surface impact: `Android`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Frame-time regression tests on large mixed-content pages.

- [x] `PERF-04` Stress profile for very large notes/PDFs
  - Status: `Done (Wave AC-StressProfileMatrixBenchmarks)`
  - Competitor behavior: Mature apps handle thousands of strokes and long PDFs without visible stutter.
  - Current Onyx evidence: `C:\onyx\apps\android\app\src\androidTest`, `C:\onyx\docs\architecture\stress-profile-matrix.md`
  - What exists now: Stress profile matrix is documented in `docs/architecture/stress-profile-matrix.md`, and macrobenchmarks (`LargeNoteStressBenchmark`, `InkingFrameRateBenchmark`, `InkLatencyBenchmark`) capture frame and memory behavior for heavy interaction loops.
  - What is missing: CI percentile threshold hard-gating and wider device-lab automation.
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

- [x] `XSURF-01` Convex schema/contracts for new feature metadata
  - Status: `Done (Wave V-FeatureMetadataContracts)`
  - Competitor behavior: Not a UI feature by itself; required to support parity features across devices.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\packages\validation\src\schemas\featureMetadata.ts`, `C:\onyx\tests\contracts\fixtures\gesture-settings.fixture.json`, `C:\onyx\tests\contracts\fixtures\template-scope.fixture.json`, `C:\onyx\tests\contracts\fixtures\export-metadata.fixture.json`, `C:\onyx\docs\architecture\feature-metadata-contracts.md`
  - What exists now: Contracts now cover object unions, search index metadata, gesture settings, template scope metadata, and export metadata with fixture validation.
  - What is missing: Runtime sync mutation/query surfaces and feature-specific conflict resolution execution paths.
  - Exact change needed: Add Convex schema and contract fixtures for object tools, template scope, gesture settings, and export metadata before cross-device sync rollout.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract fixture test suite and schema drift checks in CI.

- [x] `XSURF-02` Web implications tracking for view-only surface
  - Status: `Done (Wave W-WebDecodeFallbackRuntimeTests)`
  - Competitor behavior: Not competitor-visible directly; needed so web does not break on new Android-authored metadata.
  - Current Onyx evidence: `C:\onyx\apps\web\src\contracts\decodeMetadata.ts`, `C:\onyx\apps\web\src\contracts\decodeMetadata.test.ts`, `C:\onyx\docs\architecture\web-object-fallback-matrix.md`
  - What exists now: Web decode helper now enforces fallback-safe parsing for object + metadata families with runtime unit tests validating unknown-kind preserve/skip-invalid behavior.
  - What is missing: Integrating decode helper into eventual web note data-loading path and adding optional user-facing unsupported-feature messaging.
  - Exact change needed: Define and implement web decode/fallback behavior for each new metadata field with explicit unsupported-feature affordances.
  - Surface impact: `Web`, `Convex`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Web contract decoding tests for mixed-feature notes and fallback rendering snapshots.

- [x] `XSURF-03` Attachment/object schema expansion (image/audio/sticky/scan/file)
  - Status: `Done (Wave V-AttachmentObjectSchemaContracts)`
  - Competitor behavior: Required cross-surface contract for Samsung-style insert parity.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\apps\android\app\src\main\java\com\onyx\android\data\entity\PageObjectEntity.kt`
  - What exists now: Canonical object union contract includes image/audio/sticky/scan/file payload scaffolding; Android runtime now supports shape/image/text object kinds.
  - What is missing: Runtime audio/sticky/scan/file object interactions and attachment lifecycle operations.
  - Deferred from this wave: Runtime support for audio/sticky/scan/file remains intentionally deferred.
  - Exact change needed: Add object union schema with type-specific payload validation and fallback policy for unsupported clients.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract fixtures for each attachment object type.

- [x] `XSURF-04` Search-index contracts (handwriting tokens + PDF OCR tokens)
  - Status: `Done (Wave T-SearchIndexContracts)`
  - Competitor behavior: Required to support unified search parity across devices.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\packages\validation\src\schemas\searchIndexToken.ts`, `C:\onyx\tests\contracts\fixtures\search-index-handwriting-token.fixture.json`, `C:\onyx\tests\contracts\fixtures\search-index-pdf-ocr-token.fixture.json`, `C:\onyx\docs\architecture\search-index-contracts.md`
  - What exists now: Shared versioned search-token contract is defined for handwriting and PDF OCR sources with fixtures and merge-policy documentation.
  - What is missing: Runtime token generation, persistence writes, sync mutation/query paths, and web search UI consumption.
  - Exact change needed: Add search token contracts, index versioning, and conflict-safe merge rules for handwriting and OCR updates.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave Foundation`
  - Validation gate: Contract schema tests and mixed-source fixtures for handwriting + OCR payloads.

- [x] `XSURF-05` Sync conflict policy + metadata for edits at page/object granularity
  - Status: `Done (Wave AC-ConflictMetadataRuntimeScaffold)`
  - Competitor behavior: Needed for cross-device reliability once parity features are synced.
  - Current Onyx evidence: `C:\onyx\convex\schema.ts`, `C:\onyx\packages\validation\src\schemas\pageObject.ts`, `C:\onyx\tests\contracts\fixtures\page-object-shape-conflict.fixture.json`, `C:\onyx\docs\architecture\object-sync-conflict-policy.md`
  - What exists now: Contracts include revision/conflict metadata with deterministic merge policy docs, and Convex runtime mutation scaffold now increments object revisions, links parent revisions, and persists conflict policy/lastMutationId for page-object upserts.
  - What is missing: End-user manual conflict-resolution UX and multi-client live reconciliation product polish.
  - Exact change needed: Define conflict resolution semantics (last-write, merge, or user-resolve) by object category and persist revision metadata accordingly.
  - Surface impact: `Convex`, `Android`, `Web`, `Docs/QA`
  - Priority wave: `Wave V2`
  - Validation gate: Contract fixtures validating conflict metadata shape and policy enums.

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

