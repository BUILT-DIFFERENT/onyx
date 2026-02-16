Technical Architecture Report: Project Onyx – High-Performance Android Note-Taking Application Engineering Analysis
1. Executive Summary and Strategic Vision
The development of Project Onyx represents a significant engineering challenge within the Android ecosystem: the synthesis of an OEM-grade organizational framework, a creatively fluid third-party editing engine, and an industrial-strength handwriting recognition backend. The current market landscape for Android note-taking applications is bifurcated. On one side stands Samsung Notes, a utility deeply integrated into the hardware layer of Galaxy devices, offering unparalleled stability, S-Pen optimization, and hierarchical organization.1 On the other side lies Notewise, a modern, engine-driven application that prioritizes rendering aesthetics, infinite canvas capabilities, and extensive tool customization.3
Project Onyx aims to bridge this divide by leveraging the MyScript iink SDK as the cognitive engine for handwriting recognition (HWR) while implementing a custom, low-latency graphics pipeline to rival the responsiveness of native tools. This report provides a comprehensive architectural blueprint for Onyx, dissecting the functional requirements derived from Samsung Notes and Notewise, and translating them into concrete engineering specifications.
The core technical hypothesis of this report is that a standard implementation of the MyScript iink SDK (using its default EditorView) will fail to meet the user's requirement for a "Notewise-like" editor. The default SDK view is rigid, optimized for text conversion (Nebo-style) rather than freeform creativity. Therefore, the recommended architecture for Onyx is a Hybrid Offscreen Implementation. This approach utilizes MyScript strictly as a background recognition service while the application layer handles rendering via the Android Jetpack Ink API (currently in Alpha) and SurfaceView technologies. This architecture allows for the implementation of custom brushes, fluid zooming, and specific S-Pen hardware events that generic SDK implementations cannot support.5
Furthermore, the requirement for "specific search capabilities" necessitates a unified indexing strategy. Users demand the ability to search a query like "mitochondria" and receive results from both typed PDF textbooks and handwritten lecture notes simultaneously. This requires a complex ingestion pipeline that synchronizes MyScript's JIIX (JSON Interactive Ink Exchange) export format with a SQLite FTS4/FTS5 (Full-Text Search) virtual table, mapping vector coordinates from the recognition engine to the screen space of the PDF renderer for precise highlighting.8
This document details the exact mechanisms required to achieve sub-16ms drawing latency, replicate Samsung’s nested folder structure using recursive SQL queries, and implement the advanced toolsets (Tape, Zoom Box) characteristic of Notewise.
2. Benchmark Analysis: The Organizational Robustness of Samsung Notes
To build Onyx, one must first deconstruct the "Homescreen" experience of Samsung Notes. This application is not merely a drawing tool; it is a comprehensive file management system optimized for the Android filesystem and the S-Pen digitizer.
2.1 The Hierarchical File System Architecture
The primary requirement for the Onyx homescreen is to mimic Samsung Notes' layout and folder organization features. Unlike many modern applications that rely on flat, tag-based architectures (e.g., Google Keep), Samsung Notes employs a strict, nested directory structure. This design choice appeals to students and professionals who organize content by semester, subject, and topic.1
2.1.1 Recursive Folder Logic
Samsung Notes allows for infinite nesting depth (e.g., Root > 2025 > Math > Calculus > Homework). Implementing this in Onyx requires a relational database schema (likely Room Persistence Library) that supports self-referencing entities. A flat table structure is insufficient. The schema must utilize a parent-child relationship where a Folder entity contains a nullable parent_id.
When rendering this UI, Samsung Notes does not simply query all folders. It queries WHERE parent_id IS NULL for the root view, and then dynamically loads children upon navigation. This lazy-loading approach is critical for performance; querying the entire tree structure for a user with 5,000 notes would cause perceptible UI frame drops. The navigation stack must be managed manually or via the Android Navigation Component to preserve the "breadcrumb" trail, allowing users to navigate up the hierarchy efficiently.
2.1.2 Visual Presentation: Grid vs. List Paradigms
Samsung Notes offers two distinct viewing modes, and Onyx must support both to meet the requirement:
The Grid View (Thumbnail-Centric): This view prioritizes visual recognition. The critical engineering challenge here is thumbnail generation. Samsung Notes likely generates a low-resolution bitmap of the first page of a note whenever the note is closed or saved. These thumbnails are cached on the disk. Onyx cannot attempt to render the actual note content in the RecyclerView; it must rely on these pre-generated artifacts. The grid layout must be adaptive, changing column counts based on device orientation (Portrait vs. Landscape) and screen width (Phone vs. Tablet).1
The List View (Metadata-Centric): This view prioritizes density and sorting details (Date Modified, Title, Size). This requires the database query to be optimized for sorting on indexed columns (last_modified_timestamp, title_collate_nocase).
2.1.3 Interaction Design: Drag-and-Drop
A defining feature of the Samsung UX is the ability to move notes into folders via drag-and-drop. In the Android ecosystem, this is implemented using the ItemTouchHelper class attached to the RecyclerView. However, standard implementations only support reordering. To support dropping into a folder (nesting), Onyx must implement a custom OnItemTouchListener that detects when a dragged item hovers over a folder item for a threshold duration (e.g., 500ms), triggering a "highlight" state on the folder, and then executing a database update transaction (UPDATE notes SET parent_id = target_folder_id) upon release.1
2.2 S-Pen Hardware Integration
Samsung Notes distinguishes itself through deep integration with the Wacom EMR digitizer found in Galaxy devices. Standard Android apps often treat the stylus as a generic finger pointer, but Onyx requires "Samsung features," implying support for specific hardware events.12
2.2.1 Button Events and Air Actions
The S-Pen button is a hardware key. In Samsung Notes, pressing and holding this button switches the active tool to the Eraser. This "Quick Erase" function is vital for workflow speed.
Implementation Mechanism: Android exposes this via KeyEvent. The S-Pen button typically broadcasts KEYCODE_BUTTON_SECONDARY or KEYCODE_STEM_1. The Onyx editor must override onKeyDown and onKeyUp. When onKeyDown detects the button press, the rendering engine must temporarily swap the Brush object to an EraserBrush. On onKeyUp, it reverts.
Air Actions: Newer S-Pens support Bluetooth Low Energy (BLE) gestures (Air Actions). However, accessing these requires the Samsung S-Pen Remote SDK. Without this specific SDK (which has restrictions), generic Android apps cannot detect "Air Gestures" (like waving the pen). Onyx should prioritize the standard button events (Eraser) over Air Actions to ensure broader compatibility across non-Samsung devices (like Boox or Pixel Tablet) while maintaining the core utility for Samsung users.14
2.2.2 Hover States
Samsung Notes utilizes the "Hover" state (MotionEvent.ACTION_HOVER_MOVE) to display tooltips or preview the stroke size before the pen touches the glass. Onyx should implement a "Brush Cursor" that draws a circle representing the current brush size at the coordinate of the hover event. This provides immediate visual feedback to the user, a hallmark of professional creative software.2
3. Benchmark Analysis: The Creative Fluidity of Notewise
While Samsung Notes excels at organization, the user's requirement for an "editor like Notewise" points to a desire for a superior rendering engine and highly customizable tools. Notewise is celebrated for its performance on large canvases and its specific feature set that aids digital study.3
3.1 The Rendering Engine: Beyond Standard Views
Notewise achieves its distinct "smoothness" and "infinite zoom" capabilities by likely bypassing the standard Android Canvas View hierarchy for its main rendering loop. Standard Views struggle with zooming high-resolution bitmaps (PDFs) and thousands of vector strokes simultaneously.
To replicate the "Notewise feel," Onyx must implement a Tile-Based Rendering System.
Infinite Canvas Logic: Instead of a single massive view, the canvas is virtually infinite. The viewport (the screen) only renders the visible coordinates.
Tiling: The PDF background must be sliced into tiles (e.g., 256x256 or 512x512 pixels) at different zoom levels (similar to Google Maps). When the user zooms in, the engine loads higher-resolution tiles. This prevents OutOfMemoryError crashes that plague simpler apps when loading 500-page textbooks.15 Notewise's ability to handle large files suggests an aggressive implementation of this tiling strategy using BitmapRegionDecoder.16
3.2 Key Tool Implementations
The user specifically requested "Notewise features," which implies the implementation of its signature tools: the Tape Tool and the Zoom Box.
3.2.1 The Tape Tool (Active Recall)
The Tape Tool allows users to draw over text to hide it, then tap to reveal it. This is not just a thick highlighter; it is an interactive object.
Technical Implementation: The Tape is a stroke with a special property state (isCovered: Boolean).
Rendering: It is drawn on a layer above the PDF and handwriting but below the UI chrome.
Interaction: The app must maintain a spatial index (like a QuadTree or R-Tree) of all tape strokes. When a single tap event (ACTION_DOWN + ACTION_UP within a small radius) is detected, the engine queries the spatial index. If a tape stroke intersects the tap, its state toggles (isCovered =!isCovered). Visually, this changes the alpha channel of the stroke color or renders a "shadow" underneath to imply it is lifted.3
3.2.2 The Zoom Box (Precision Writing)
The Zoom Box is essential for neat handwriting on tablets. It displays a magnified view of a small section of the page at the bottom of the screen.
Technical Implementation: This requires a Dual-Camera rendering setup.
Main View: Renders the full page.
Zoom Window: Renders the same content (PDF + Strokes) but with a different transformation matrix (Scale x2 or x3) focused on the "Zoom Box Cursor" location.
Input Mapping: Strokes drawn in the Zoom Window at the bottom must be mathematically projected back to the actual page coordinates. If the user draws at screen coordinate $(x, y)$ in the Zoom Window, the engine must transform this to $(x', y')$ on the document page using the inverse of the zoom matrix. This ensures the ink appears in the correct location on the main document.18
3.3 UI Customization: The Floating Toolbar
Notewise breaks away from the fixed toolbars of Samsung Notes, offering a floating, collapsible, and reorderable toolbar. This requires a flexible UI architecture, likely using ConstraintLayout with dynamic views that can be dragged by the user. The toolbar state (position, expanded/collapsed, tool order) must be persisted in SharedPreferences or DataStore so the user's environment is restored upon relaunch.3
4. Core Technology: The MyScript iink SDK Integration
The user explicitly mandates the use of the MyScript iink SDK. This decision dictates the fundamental capabilities of the app, particularly regarding text conversion and search. However, integrating MyScript to behave like Notewise is non-trivial.20
4.1 The Integration Dilemma: Editor vs. Offscreen
MyScript provides two primary modes of operation. Choosing the correct one is the most critical architectural decision for Project Onyx.
4.1.1 Option A: The Platform EditorView (Nebo Style)
The SDK provides a ready-made EditorView that handles capture, rendering, and recognition.
Pros: Extremely fast implementation. You get "Reflow" (double tap to convert handwriting to typeset text that wraps), interactive diagrams, and math solving out of the box.
Cons: Rigid UI. You cannot change how the ink looks beyond basic color/width. You cannot implement "Creative Brushes" (watercolor, dashed lines) or the "Tape Tool" easily because the view's rendering loop is closed source. You cannot easily overlay a PDF in the way Notewise does.
Verdict: This is suitable for a Nebo clone, but unsuitable for an "Editor like Notewise."
4.1.2 Option B: The "Offscreen" Architecture (Recommended for Onyx)
To achieve the customization of Notewise with the intelligence of MyScript, Onyx must use the Offscreen Interactivity or Batch Processing pattern.
Mechanism: Onyx manages its own SurfaceView for capturing and rendering ink (using Jetpack Ink API). The ink strokes (x, y, t, p data) are effectively "forked."
Path 1 (Visual): Sent immediately to the GPU for rendering (sub-16ms latency).
Path 2 (Cognitive): Sent asynchronously to the MyScript engine (running in a background thread or service). The engine processes the strokes and updates its internal model.
Benefit: Onyx retains full control over the visual presentation (custom brushes, smooth zooming, Tape tool) while MyScript builds the "Digital Semantic" layer in the background.
Data Exchange: When the user saves or searches, Onyx requests a JIIX export from the MyScript engine. This JSON file contains the recognized text candidates and their bounding boxes, which are then used for the search index.6
4.2 Handling the "JIIX" Format
JIIX (JSON Interactive Ink Exchange) is the lingua franca of Onyx. It hierarchically describes the content.
Structure: A JIIX export for a page contains words, chars, and strokes.
Search Utility: Each word object in JIIX includes a label (the text string) and a bounding-box (x, y, width, height).
Gap: The bounding box coordinates in JIIX are in MyScript's internal coordinate space (often millimeters). Onyx must implement a Coordinate Transformation Matrix to convert these Millimeter coordinates into the PDF Page Point coordinates used by the renderer. Without this calibration, search highlights will appear drifted or scaled incorrectly.24
5. Performance Engineering: Achieving Sub-16ms Latency
The "feel" of a note-taking app is defined by its latency—the delay between the physical tip of the stylus moving and the digital line appearing on screen. Samsung Notes sets the benchmark at approximately 6-9ms (hardware dependent). To compete, Onyx cannot rely on standard Android canvas drawing (Canvas.drawLine), which often results in 30-50ms latency due to the view hierarchy composition pipeline.25
5.1 The Graphics Pipeline: SurfaceView vs. TextureView
Standard Android Views are composited. When invalidate() is called, the system traverses the view tree, redraws the dirty region into a buffer, and then the WindowManager composites this buffer with the status bar, nav bar, etc., before sending it to the display. This adds frames of latency.
SurfaceView is the required technology for Onyx.
Mechanism: SurfaceView punches a hole in the window hierarchy and provides a dedicated drawing surface that is composited directly by SurfaceFlinger (the system compositor).
Front-Buffered Rendering: Onyx must utilize Front-Buffered Rendering. In standard double-buffering, you draw to a back buffer, then swap. Front-buffering allows the app to draw directly to the screen buffer while the beam is scanning (racing the beam). This is risky (can cause tearing) but provides the absolute lowest latency for the "tip" of the ink stroke.5
5.2 The Jetpack Ink API (Alpha) Implementation
Google has released the Jetpack Ink API specifically to solve this problem for apps like Onyx. Instead of writing custom OpenGL C++ code, Onyx should utilize this library.7
5.2.1 The InProgressStrokesView
This component is designed to render the "wet" ink (the stroke currently being drawn).
Mesh Generation: It doesn't draw lines; it generates a triangle mesh that expands and contracts based on pressure data from the stylus (MotionEvent.getPressure()). This creates the "fountain pen" look with variable width.
Prediction: It integrates with the androidx.input.motionprediction library. This library uses Kalman filters to predict where the pen will be in the next 16ms based on its current velocity and trajectory. The renderer draws a faint "predicted" segment ahead of the actual data, reducing the perceived latency to near zero.
Table: Latency Optimization Stack
Component
Technology
Benefit
Input
MotionEvent Batching
Processes all historical points between frames for smooth curves.
Logic
Motion Prediction
Extrapolates stroke path to cover the 16ms render gap.
Render
Front-Buffered SurfaceView
Bypasses View composition for direct screen access.
Shape
Dynamic Tessellation
Generates smooth meshes instead of jagged lines.

5.2.2 The TextureView Compromise
While SurfaceView is faster, it has Z-ordering issues (it likes to sit behind or in front of everything). If Onyx requires complex UI elements (like the Notewise floating toolbar) to sit semi-transparently over the canvas, SurfaceView can be problematic. However, modern Android versions have improved SurfaceView behavior (setZOrderMediaOverlay). If visual glitches occur, TextureView is the fallback. It behaves like a normal View (allows transparency/animation) but incurs a ~16ms latency penalty. For a "Samsung-like" feel, SurfaceView is non-negotiable.26
6. The Unified Search Capability
The user requires "specific search capabilities," implied to be the ability to search across handwritten notes and PDF text simultaneously. This is a complex backend challenge involving the fusion of two disparate data types.
6.1 The Data Ingestion Pipeline
Onyx must maintain a shadow database that indexes all content.
PDF Indexing: When a PDF is imported, Onyx must use a library like PdfBox-Android or iText to strip the text content.
Extraction: For each page, extract text strings and their bounding box coordinates.
Storage: Insert into a Room database table: PDF_Index (doc_id, page_num, text, rect_x, rect_y,...).
Handwriting Indexing: As the user writes, the "Offscreen" MyScript engine processes the strokes.
Trigger: On ACTION_UP (stroke finish) or periodic auto-save.
Export: Request JIIX export from MyScript.
Storage: Parse JIIX JSON. Insert "label" (recognized text) and "bounding-box" into the same database schema: Handwriting_Index (doc_id, page_num, text, rect_x, rect_y,...).24
6.2 The FTS (Full-Text Search) Architecture
To make this searchable instantly, Onyx must use SQLite FTS4 or FTS5 (virtual tables). Standard SQL LIKE %query% is too slow for thousands of pages.
Table: Unified Search Schema Strategy
Column
Description
rowid
Primary Key.
content_text
The indexable text (e.g., "mitochondria power cell").
source_type
Enum: PDF_TEXT or INK_LABEL.
geometry_json
JSON string containing bounding box coordinates [x,y,w,h].
page_ref
Foreign Key to the specific page/document.

Search Execution Flow:
User types "Mitochondria".
App queries FTS Virtual Table: SELECT * FROM Unified_Index WHERE content_text MATCH 'Mitochondria'.
Database returns mixed results: 5 hits from PDF text, 3 hits from Handwritten notes.
Result Visualization: The app iterates through the results.
If source_type == PDF_TEXT, it draws a highlight rect on the PDF layer.
If source_type == INK_LABEL, it draws a highlight rect on the Ink layer.
Crucial Step: The coordinates from the database must be transformed through the current Viewport Matrix (Zoom/Pan) to align correctly on screen.9
7. Gap Analysis and Risk Assessment
While the proposed architecture is robust, there are specific "Gaps" between what is technically possible for a third-party app (Onyx) and what OEM apps (Samsung Notes) can do.
7.1 The "Screen-Off Memo" Gap
Samsung Feature: Pulling the pen out while the screen is off wakes the device into a special low-power black canvas mode.
Gap: This requires system-level permissions to intercept the hardware wake event and bypass the secure lock screen. A third-party app cannot securely bypass the lock screen to show a canvas without unlocking the device first.
Mitigation: Onyx can implement a "Lock Screen Widget" (Android 14+) or a "Quick Settings Tile" that launches the app immediately after the user authenticates, but the true "Screen-Off" experience is likely unreachable without root or OEM partnership.13
7.2 The "Air Action" Gap
Samsung Feature: Waving the pen to scroll or change tools.
Gap: Accessing the gyroscope/accelerometer data inside the S-Pen requires the Samsung S-Pen Remote SDK. This SDK is distinct from standard Android APIs. Using it may restrict Onyx's distribution or compatibility with non-Samsung devices.
Recommendation: Focus on the generic KeyEvent (Button Press) which is standard across Wacom EMR styluses (Samsung, Wacom One, Staedtler), ensuring broader hardware support.14
7.3 The PDF Rendering Gap
Challenge: Notewise handles large PDFs smoothly. Standard PdfRenderer is slow and memory-hungry.
Gap: Implementing a high-performance, tiled PDF renderer that does not flicker during zoom is one of the hardest engineering tasks in Android.
Mitigation: Onyx should consider licensing a commercial PDF SDK (like PSPDFKit or Foxit) if engineering resources are limited, as building a tiled renderer from scratch that rivals Notewise is a 6-12 month sub-project. If building custom, heavily leverage LruCache for bitmaps and background threads for rendering tiles.15
7.4 The MyScript "Rotation" Gap
Challenge: In Notewise, you can select ink and rotate it freely.
Gap: MyScript's internal model is block-based (paragraphs, lines). Rotating a paragraph of text often breaks the recognition context or reflow logic.
Mitigation: When the user selects ink for rotation, Onyx may need to temporarily convert the data from MyScript's "Semantic Block" to a dumb "Graphics Object" (SVG/Bitmap). Once the rotation is finished, the data is re-sent to MyScript. This might degrade recognition accuracy for rotated text (as HWR engines expect horizontal alignment), representing a trade-off between "Editability" and "Searchability".3
8. Conclusion and Roadmap
Project Onyx is a viable engineering endeavor that addresses a clear market gap. By decoupling the rendering pipeline (using Jetpack Ink/SurfaceView) from the recognition engine (MyScript Offscreen), Onyx can achieve the visual fidelity of Notewise. By implementing a recursive Room database and FTS indexing strategy, it can match the organizational power of Samsung Notes.
Phase 1 Recommendations:
Prototype the Renderer: Build a SurfaceView app using Jetpack Ink API. Verify latency is <20ms on a Galaxy Tab.
Prototype the Integration: Connect this renderer to MyScript in "Batch Mode." verifying that strokes drawn on the SurfaceView result in recognized text in the logs.
Database Design: strictly model the Folder hierarchy in Room to ensure the "Samsung Homescreen" requirement is met before UI work begins.
Success depends on rigorous adherence to low-latency graphics principles and a disciplined approach to the hybrid data model, ensuring that the "Ink" seen by the user and the "Text" seen by the search engine remain perfectly synchronized.
Sources
Samsung Notes Analysis: 1
Notewise Analysis: 3
MyScript SDK: 6
Graphics & Performance: 5
Search & Data: 8
Works cited
Organize notes and PDFs in Samsung Notes, accessed February 12, 2026, https://www.samsung.com/us/support/answer/ANS10004548/
Frequently asked questions about Samsung Notes, accessed February 12, 2026, https://www.samsung.com/us/support/answer/ANS10002888/
What's New | Notewise - The Unparalleled Writing Experience, accessed February 12, 2026, https://www.notewise.dev/updates
Notewise | The Unparalleled Writing Experience, accessed February 12, 2026, https://www.notewise.dev/
Advanced stylus features | Jetpack Compose | Android Developers, accessed February 12, 2026, https://developer.android.com/develop/ui/compose/touch-input/stylus-input/advanced-stylus-features
MyScript/interactive-ink-additional-examples-android - GitHub, accessed February 12, 2026, https://github.com/MyScript/interactive-ink-additional-examples-android
Introducing Ink API, a new Jetpack library for stylus apps - Android Developers Blog, accessed February 12, 2026, https://android-developers.googleblog.com/2024/10/introducing-ink-api-jetpack-library.html
Android PDF Library with Text Search | Apryse documentation, accessed February 12, 2026, https://docs.apryse.com/android/guides/search/text
Define data using Room entities | App data and files - Android Developers, accessed February 12, 2026, https://developer.android.com/training/data-storage/room/defining-data
Full text search example in Android - sqlite - Stack Overflow, accessed February 12, 2026, https://stackoverflow.com/questions/29815248/full-text-search-example-in-android
Organize the Home screen on your Galaxy phone or tablet - Samsung, accessed February 12, 2026, https://www.samsung.com/us/support/answer/ANS10001898/
Use Samsung Notes features and settings, accessed February 12, 2026, https://www.samsung.com/us/support/answer/ANS10001384/
Master the S Pen of your Galaxy Tablet - Samsung, accessed February 12, 2026, https://www.samsung.com/us/support/answer/ANS10002017/
Programming the S Pen Button on Galaxy Note phones | Samsung US - YouTube, accessed February 12, 2026, https://www.youtube.com/watch?v=CSX9ypSkr-4
PDFViewerFragment - AndroidX Library for viewing PDF | by Adi Andrea - Medium, accessed February 12, 2026, https://adiandrea.medium.com/pdfviewerfragment-androidx-library-for-viewing-pdf-419ef76bf43e
Compare Notewise VS Samsung Notes | Techjockey.com, accessed February 12, 2026, https://www.techjockey.com/us/compare/notewise-vs-samsung-notes
Notewise 3.0 Has Arrived: Smarter, Faster, and More Powerful Than Ever, accessed February 12, 2026, https://www.notewise.dev/post/notewise-3-0-release
Best Note-Taking App For Samsung Tablet | 2026 Beginner's Guide To Notewise App, accessed February 12, 2026, https://www.youtube.com/watch?v=c8Q_8fOOkPs
Notewise Best Note-Taking App 2025 – Top 15 Powerful Features - YouTube, accessed February 12, 2026, https://www.youtube.com/watch?v=PO_3xjVc4dE
Handwriting recognition & digital ink SDK - MyScript, accessed February 12, 2026, https://www.myscript.com/sdk/
About MyScript iink SDK, accessed February 12, 2026, https://developer.myscript.com/docs/interactive-ink
MyScript iink SDK 3.0: A new chapter for handwriting recognition, accessed February 12, 2026, https://www.myscript.com/blog/iink-sdk-3-0-handwriting-recognition/
Content types | MyScript Developer, accessed February 12, 2026, https://developer.myscript.com/docs/interactive-ink/3.0/overview/content-types/
Text recognition candidates | MyScript Developer, accessed February 12, 2026, https://developer.myscript.com/docs/interactive-ink/2.0/android/advanced/text-recognition-candidates/
Why use SurfaceView or TextureView for custom drawing? Their Canvas isn't hardware accelerated. : r/androiddev - Reddit, accessed February 12, 2026, https://www.reddit.com/r/androiddev/comments/2104qd/why_use_surfaceview_or_textureview_for_custom/
Demystifying Android's Surface: Your Secret Weapon for High-Performance Graphics, accessed February 12, 2026, https://www.droidcon.com/2025/11/05/demystifying-androids-surface-your-secret-weapon-for-high-performance-graphics-%F0%9F%9A%80/
Android TextureView vs. SurfaceView: Choosing the Right Approach for Video and Graphics Rendering | by Steven | Medium, accessed February 12, 2026, https://medium.com/@seungbae2/android-textureview-vs-surfaceview-choosing-the-right-approach-for-video-and-graphics-rendering-51bb7d7aebd1
Modules | Views | Android Developers, accessed February 12, 2026, https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/ink-api-modules
Difference between TextureView, SurfaceView, Texture and Surface - Stack Overflow, accessed February 12, 2026, https://stackoverflow.com/questions/57512168/difference-between-textureview-surfaceview-texture-and-surface
SQLite FTS5 Extension, accessed February 12, 2026, https://www.sqlite.org/fts5.html
S Pen Actions : r/samsungnotes - Reddit, accessed February 12, 2026, https://www.reddit.com/r/samsungnotes/comments/1jlt8lh/s_pen_actions/
Introducing Notewise 3.3: Your Creative Notes for 2026 - Reddit, accessed February 12, 2026, https://www.reddit.com/r/notewise/comments/1ptgmll/introducing_notewise_33_your_creative_notes_for/
Get started - MyScript Developer, accessed February 12, 2026, https://developer.myscript.com/docs/interactive-ink/2.0/windows/fundamentals/get-started/
Add inking to your app with the Ink API | Views - Android Developers, accessed February 12, 2026, https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/about-ink-api
Search text in PDF with Android library | Nutrient SDK, accessed February 12, 2026, https://www.nutrient.io/guides/android/features/text-search/
Custom Android PDF Text Search - by Williammmm Kim - Medium, accessed February 12, 2026, https://medium.com/@williammmm.kim/custom-android-pdf-text-search-a04d2960cde3
Querying a Full Text Search Table in Android Room Database - Stack Overflow, accessed February 12, 2026, https://stackoverflow.com/questions/68713141/querying-a-full-text-search-table-in-android-room-database
