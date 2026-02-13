# Issues & Problems - Canvas Rendering Overhaul

## Blockers Found

1. Gradle failed under Java 25 (`IllegalArgumentException: 25.0.2`) during Kotlin script evaluation.
   - Mitigation: `scripts/gradlew.js` now auto-selects JDK 17 on Linux/macOS when available.

2. Android build failed merging native libs (`libc++_shared.so`) from MyScript + Pdfium.
   - Mitigation: `pickFirsts += "**/libc++_shared.so"` in app packaging config.

3. `MyScriptPageManager.kt` used non-existent API `eraseStrokes`.
   - Mitigation: switched to `OffscreenEditor.erase(arrayOf(...))`.

## Remaining Runtime Gaps

- No connected-device verification yet for Pdfium tile rendering + in-app import/selection.
- Thread-safety and coordinate offset behavior still require runtime measurement.
