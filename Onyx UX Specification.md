# Onyx — UX Specification
This document is the definitive UX reference for Onyx. It describes what the app looks like, how it feels, and how every interaction works from the user's perspective. Code-focused agents should consult this before making any UI/UX decisions.
**Visual north star**: NoteWise (clean, dark, intuitive, approachable). **Organizational north star**: Samsung Notes (folder hierarchy with sidebar).
**Anti-pattern**: Samsung Notes' double-toolbar editor layout. Onyx uses a single top bar.
**Terminology**: The primary content unit is a "notebook" (not "note"). A notebook contains pages.
# 1. Visual Design Language
## 1.1 Personality
Clean and polished, but warm and approachable — not clinical or overly minimal. The app should feel like a premium tool that gets out of your way. Nothing intimidating, nothing flashy.
## 1.2 Design System
* No custom design system. Use Material 3 components and patterns as the foundation, but style them to match NoteWise's dark, muted aesthetic rather than stock Material You dynamic colors.
* Dark mode is the default chrome appearance (dark blue-grey surfaces, muted accent colors, high contrast text).
* Notebook background is independent of chrome theme (per-notebook setting).
## 1.3 Color Palette
* Chrome surfaces: dark blue-grey tones (similar to NoteWise's `#1a2332`-range backgrounds)
* Cards/panels: slightly lighter blue-grey
* Accent color: soft blue (for toggles, active states, selection highlights)
* Text: white/near-white for primary, muted grey for secondary
* Destructive actions: muted red
## 1.4 Typography
* System font (Roboto). No custom typeface.
## 1.5 Animations
* Smooth but snappy. Quick transitions that don't block the user.
* More Android-native than iOS-fluid. Responsive, not decorative.
* Example: tool popover opens in ~150ms with a subtle scale-up. Page scroll is physics-based.
## 1.6 Iconography
* Outlined icons for the toolbar (matching NoteWise's style). Active/selected tool gets a filled or highlighted variant.
* Icons should be simple and recognizable at small sizes on a dense toolbar.
# 2. Home / Library Screen
## 2.1 Layout
* **Grid view** of notebook covers. No list view toggle.
* Each notebook card shows:
    * Live thumbnail of the first page (rendered from actual notebook content)
    * Notebook name below the thumbnail
    * Dropdown chevron next to the name (opens context menu: Favorite, Rename, Move to, Duplicate, Delete)
* **FAB** in bottom-right: "New notebook" (icon + label, like NoteWise's FAB).
* **Plus button** also in bottom-right above the FAB for quick actions (Create folder, Import PDF, Import images).
## 2.2 Folder Navigation
* **Hamburger menu** (top-left) opens a collapsible left sidebar showing the full folder tree.
    * Sidebar shows nested folders with notebook counts, expandable/collapsible like Samsung Notes.
    * Tapping a folder navigates the main grid to that folder's contents.
    * Sidebar can be dismissed by tapping outside or swiping left.
* **Breadcrumb path** shown at top of main content area when inside a subfolder (Folder > Subfolder > Current), tappable for navigation back up.
## 2.3 Top Bar (Library)
* Left: Hamburger menu icon
* Center: Current folder name (or "All Notebooks" at root)
* Right: Search icon, three-dot overflow menu (settings, trash, etc.)
## 2.4 Sorting
* Sort options: **Date modified** (default — means last opened/edited) and **Date created**.
* Sort control in top-right area of the grid, below the top bar (like NoteWise/Samsung Notes placement).
* Sort direction toggle (ascending/descending).
## 2.5 Favorites
* Notebooks can be favorited via the context menu (long-press or dropdown chevron).
* Favorited notebooks float to the top of whatever folder the user is currently viewing.
* No separate "Favorites" tab or filter — favorites are always surfaced first within the current view.
## 2.6 Multi-Select
* Checkmark icon in the top-right (next to sort controls) enters multi-select mode.
* In multi-select: tap notebook cards to select, bottom action bar appears with: Move, Delete, Export.
## 2.7 Trash
* Soft delete. Deleted notebooks go to Trash.
* Trash auto-purges after **20 days**.
* Trash is accessible from the hamburger sidebar or the three-dot overflow menu.
* Notebooks in trash can be restored or permanently deleted.
## 2.8 Search
* Tapping the search icon opens a full-width search bar (like NoteWise's top search bar).
* Searches across: notebook titles, handwriting content (HWR index), PDF text content.
* Results show notebook thumbnails with matched context snippet.
# 3. First-Run & Note Creation
## 3.1 First Launch
* User lands on an empty home screen. No tutorial, no sample notebook.
* The FAB ("New notebook") is the obvious first action.
## 3.2 First Notebook Creation (Template Setup)
* When the user taps "New notebook" for the **first time ever**, the template picker appears as a full-screen or large modal.
* The user selects:
    * Background color
    * Template type: Blank / Lined / Dotted / Grid
    * Template density (line spacing, dot spacing, grid size)
    * Notebook mode: Paged or Infinite vertical canvas
* These choices become the **default template** for all future notebooks.
* Subsequent "New notebook" taps create a notebook with the saved default immediately (no picker), unless the user goes to Settings to change the default.
## 3.3 Subsequent Notebook Creation
* Tap "New notebook" → notebook opens instantly with the default template.
* Notebook is auto-named with the current date (e.g. "Untitled 2026-03-07"), editable in the title area.
* The mode (paged vs. infinite canvas) is locked at creation time and cannot be changed later.
# 4. Notebook Editor — Layout & Chrome
## 4.1 Overall Structure
The editor has exactly **one top bar** and a canvas. No bottom bar. No floating toolbar.
* The top bar contains everything: navigation, tools, colors, and note actions.
* Below the top bar is the full-bleed ink canvas.
* Bottom-right corner: page indicator and zoom level (small, unobtrusive, like NoteWise's "1/7  141%").
* Scrollbar on the left or right edge (configurable in settings, default right). Important because right-handed writers can accidentally trigger the scrollbar with palm rejection.
* The notebook editor is the same for paged and infinite canvas modes, except infinite canvas has no page breaks and no page indicator.
## 4.2 Top Bar — Three Zones
The top bar is a single horizontal strip with three logical zones. Reference: NoteWise's toolbar screenshots.
**Zone 1 — Left (Navigation & Page Management)**
* Back arrow (returns to library)
* Document outline (bookmark/outline icon — user marks pages as outline entries for quick-jump navigation, like a glossary or table of contents)
* Plus button (add page / import — opens a dropdown: New page, Import PDF, Import images, Scan. Below a separator: insertion position — After current page / Before current page / First page / Last page)
* Search within note
* Quick-switch notebooks (folder icon — shows a list of recent notebooks, user can pin notebooks to the top for fast switching between open notebooks without going back to library)
**Zone 2 — Center (Tools & Colors)**
* Undo / Redo buttons
* Hand/pan tool (finger-pan override — for when the user wants to pan with stylus)
* **Pen** tool icon
* **Highlighter** tool icon
* **Eraser** tool icon
* **Lasso** selection tool icon
* Separator
* **4-5 color preset circles** (the user's chosen preset palette)
* Separator
* Additional insert tools: Typed text box, Table insert, Image insert, Sticky note, Audio recording
* The active tool is visually highlighted (filled icon or accent background)
**Zone 3 — Right (Note Settings & Actions)**
* Settings/gear icon (opens in-note settings panel)
* Star/favorite toggle
* Three-dot overflow menu (template settings, export, share, note info)
If the toolbar is too wide for the screen, Zone 2 should be horizontally scrollable (with a subtle chevron/collapse indicator at the end, like NoteWise's down-arrow).
## 4.3 Tool Selection Interaction
* **Tap** a tool icon → selects that tool (pen starts drawing, eraser starts erasing, etc.).
* **Long-press** a tool icon → opens the tool's **settings popover** (appears directly below the icon, like NoteWise's popovers).
* The popover is a dark rounded card with:
    * A preview area at top showing a sample stroke/effect
    * Tool-specific settings below
    * Color palette at the bottom (for pen/highlighter)
## 4.4 Page Navigation (Paged Mode)
* Continuous smooth scroll between pages. No snap, no page-flip animation.
* Pages are stacked vertically with a small gap between them.
* Page indicator in bottom-right: `page/total` and `zoom%` (e.g. "3/7  120%").
## 4.5 Adding New Pages
* When the user scrolls past the last page and swipes up from the bottom:
    * A **circle with a plus icon** begins to appear/complete as they swipe.
    * Once the circle is fully drawn and the user releases, a new blank page is appended.
    * If they release before the circle completes, nothing happens (cancels).
* New pages can also be added via the + button in Zone 1.
## 4.6 Infinite Canvas Mode
* Same horizontal width as paged mode.
* Scrolls vertically without page breaks.
* No page indicator (infinite). Zoom level still shown in bottom-right.
# 5. Tool Popovers — Detailed Specs
All popovers follow the same visual pattern: dark rounded card, preview at top, settings below, close X in top-right.
## 5.1 Pen Popover
* **Preview area**: shows a sample stroke with current settings
* **Pen type selector**: Ballpoint / Fountain / Pencil (icon buttons in a row, like NoteWise)
* **Line type**: shows current line style
* **Hold to draw shape**: toggle with info icon (toggleable shape recognition on pen-hold)
* **Scribble to erase**: toggle with info icon
* **Thickness**: label + current value number. Slider with minus/plus buttons on each end.
* **Pressure sensitivity**: label + current value number in a rounded box. Tapping the box opens a slider popover (same slider as thickness, but without the +/- buttons).
* **Stabilization**: label + current value number in a rounded box. Same tap-to-expand slider behavior.
* **Color section**: label "Color" with + button (add custom color) and list/grid toggle. Row of 8-10 color circles. Long-press a color to remove or edit it. Tap + to open a full color picker.
## 5.2 Highlighter Popover
* **Preview area**: shows a sample highlight stroke
* **Draw straight line**: segmented control ("Hold to draw" / "Always" / "Never")
* **Line tip appearance**: segmented control ("Square" / "Round")
* **Thickness**: slider with +/- and numeric value
* **Opacity**: slider with percentage value + visual opacity gradient bar
* **Color section**: same as pen
## 5.3 Eraser Popover
* **Preview area**: shows eraser cursor circle
* **Erase entire object**: toggle (stroke eraser mode when ON, area eraser when OFF)
* **Erase locked objects**: toggle (default OFF)
* **Auto deselect**: toggle with info icon
* **Erase the following**: row of toggleable content-type icons (pen strokes, pen2, highlighter, shapes, images, text, sticky notes) — lets user filter what the eraser affects
* **Size**: slider with numeric value
* **Clear current page**: text button at bottom (destructive action)
## 5.4 Lasso Popover
* **Preview area**: shows lasso path animation (dashed line path around content)
* **Mode tabs**: "Freeform" / "Rectangle" (segmented control)
* **Select locked objects**: toggle
* **Take screenshot**: toggle (captures selection as image)
* **Full selection only**: toggle with info icon (only selects strokes fully inside the lasso, not partially intersected)
* **Select the following**: row of toggleable content-type icons (same as eraser)
## 5.5 Color Preset Circles (in top bar)
* 4-5 color circles always visible in the toolbar
* Tap a color → applies to current tool
* Long-press a color → opens color picker to change that preset slot
* Colors are shared across pen and highlighter (the tool remembers its last-used color independently)
# 6. Canvas Interactions & Gestures
## 6.1 Default Interaction Model
Opinionated, low-friction, stylus-first:
* **Stylus** → draws with the active tool
* **S Pen button hold** → switches to eraser while held
* **Single finger** → scrolls/pans the canvas
* **Two-finger pinch** → zoom
* **Two-finger double-tap** → undo
* **Three-finger double-tap** → redo
## 6.2 Eraser Cursor
* Shows a **circle outline** (not filled) at the eraser size, following the stylus position.
* Erasing happens instantly as the cursor moves. No preview/highlight of affected area before release.
## 6.3 Lasso Selection
* User draws a freeform path with the lasso tool.
* **Marching ants** (animated dashed border) appear along the lasso path.
* The lasso **auto-completes**: a straight line connects from the current stylus position back to the initial touch-down point, forming a closed shape in real-time as the user draws.
* On release: selected strokes are enclosed in a bounding box with resize handles.
* Selected content can be: moved (drag), resized (handles), converted to text, converted to LaTeX, copied, deleted.
## 6.4 Undo/Redo Feedback
* **Gesture-triggered undo/redo** (two-finger tap, three-finger tap, scribble-to-erase): shows a subtle toast notification at the bottom (e.g. "Undo" or "Redo"), fades after ~1.5s.
* **Toolbar-triggered undo/redo** (tapping the undo/redo arrows): no toast. The visual change on canvas is sufficient feedback.
## 6.5 Zoom
* Range: 50% to 1000%
* Pinch-to-zoom with two fingers
* Current zoom level shown in bottom-right
* Zoom level persisted per notebook
# 7. Page Template & Settings
## 7.1 Changing Templates
* Access via three-dot menu (Zone 3) → "Template"
* Template picker appears as a popover/bottom sheet.
* Default action button: **"Apply to current page"**
* Options/expand button reveals:
    * "Apply to all pages"
    * "Set as default template" (changes the global default for new notes)
* Template properties: background color, template type (blank/lined/dotted/grid), density, line width
* Changing a page's template does not affect existing strokes.
## 7.2 Per-Notebook Settings (via gear icon, Zone 3)
* Keep screen awake: toggle
* Hide status/navigation bars: toggle
* Scroll bar position: Left / Right (dropdown)
* Background color: color picker (independent of system theme)
* Notebook theme: Dark / Light (independent of system dark mode)
## 7.3 Auto-Contrast Behavior
* When the user changes the background color, the pen color automatically adjusts if the current pen color would be invisible on the new background (e.g. black pen on black background switches to white).
* This is a **one-time suggestion**, not enforcement. If the user manually selects a low-contrast color afterward, the app respects their choice.
# 8. Settings Screen (App-Level)
Accessible from the hamburger sidebar or library overflow menu. Full-screen settings page with grouped cards (matching NoteWise's settings layout).
## 8.1 General
* Language (dropdown)
* Theme: Dark / Light (dropdown, for app chrome only)
## 8.2 Notebooks
* New notebook name format (dropdown: "Untitled YYYY-MM-DD" / custom)
* Continue from last opened page: toggle
* Keep screen on: toggle
* Hide all system bars: toggle
* Scroll and layout → sub-page:
    * Scrollbar position: Left / Right
    * Scroll direction: Vertical (only option for now)
    * Page layout: Single page / Two-page (landscape only, future)
## 8.3 Stylus & Finger
* **Stylus section**:
    * Primary action (S Pen button): Switch to eraser (default) / Switch to last used tool / other options
    * Long hold on primary button: Hold to erase (default)
* **Single finger**: Ignored / Draw / Scroll (default: Scroll)
* **Double finger**: Ignored / Zoom and pan (default) / Scroll
* **More gestures**:
    * Undo: Double tap with two fingers (default) / other options
    * Redo: Double tap with three fingers (default) / other options
## 8.4 Default Template
* Background color picker
* Template type: Blank / Lined / Dotted / Grid
* Template density slider
* Notebook mode: Paged / Infinite canvas
# 9. Key Interactions — Summary Table
* Create notebook: FAB → opens with default template (first time shows template picker)
* Open notebook: tap thumbnail in library
* Delete notebook: context menu → Delete (moves to trash, 20-day retention)
* Favorite notebook: context menu → Favorite (floats to top of folder view)
* Switch tool: tap tool icon in top bar
* Configure tool: long-press tool icon → popover
* Change color: tap color circle in top bar, or long-press for custom color
* Undo: two-finger double-tap (toast) or toolbar arrow (no toast)
* Redo: three-finger double-tap (toast) or toolbar arrow (no toast)
* Pan: single finger drag
* Zoom: two-finger pinch, level shown in bottom-right
* Add page: swipe-up circle gesture at end of notebook, or + button in Zone 1
* Change template: three-dot menu → Template → apply to page/all/set default
* Erase: eraser tool with circle outline cursor, or S Pen button hold
* Select: lasso with marching ants, auto-completing path, bounding box on release
* Search: search icon in library top bar, or search icon in editor Zone 1
* Share notebook: three-dot overflow menu → Share (invite by email or create public link)
# 10. What Makes Onyx Different
Onyx is not trying to invent new interaction paradigms. The differentiators are:
1. **Pen feel**: The rendering pipeline (Vulkan + raw-intent-first architecture) should make the pen feel noticeably more attached and responsive than competitors, especially for small writing, math, and engineering notation.
2. **Handwriting → LaTeX workflow**: Lasso a handwritten equation → get rendered LaTeX inline. This is a killer feature for STEM students that no competitor does as smoothly.
3. **Real-time collaboration**: Google Docs-style shared editing on a handwritten note. This is rare in the space.
4. **Speed and reliability**: Offline-first, fast startup, no sync lag for local notes. The app should feel instant.
5. **Aesthetic quality**: NoteWise-level polish on Android, which is still uncommon. Most Android note apps feel like afterthoughts compared to iPad apps.
The first-30-seconds impression should be: "This feels like writing on paper, and the app doesn't fight me."
# 11. Scope Boundaries
## Included in v1
* Everything described in this document
* Pen, highlighter, eraser, lasso tools with popovers
* Paged and infinite canvas modes
* Folder organization with sidebar
* PDF import/annotation
* Image insertion
* Sticky notes
* Audio recording
* Search (title + handwriting + PDF text)
* Export to PDF
## Not in v1 (deferred)
* Brush presets / saved tool configurations
* Recent colors row
* Tagging system
* Gesture discovery / onboarding tutorial
* Two-page landscape layout
* Custom typefaces
* S Pen button customization beyond default eraser hold
