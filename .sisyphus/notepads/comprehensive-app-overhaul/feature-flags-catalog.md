# Feature Flags Catalog

**Date**: 2026-02-17
**Task**: 0.2 Feature flags and kill switches
**Status**: NOT IMPLEMENTED (PLANNED)

> **NOTE**: This notepad was incorrectly marked as IMPLEMENTED. The actual implementation is tracked in task G-0.2-A of the gap-closure plan. The `apps/android/app/src/main/java/com/onyx/android/config/` directory does NOT exist.

---

## Canonical Flag Table

| Flag Key                    | Default | Safe Path                                        | Purpose                                                                     |
| --------------------------- | ------- | ------------------------------------------------ | --------------------------------------------------------------------------- |
| `ink.prediction.enabled`    | `false` | No motion prediction - standard touch processing | Enable motion prediction for reduced ink latency using MotionEventPredictor |
| `ink.handoff.sync.enabled`  | `true`  | Immediate stroke finalize (debug only)           | Enable synchronized pen-up handoff between wet and committed layers         |
| `ink.frontbuffer.enabled`   | `false` | Existing in-progress render path                 | Enable front-buffer rendering for ultra-low latency stroke display          |
| `pdf.tile.throttle.enabled` | `true`  | Unthrottled tile scheduler                       | Enable throttling for PDF tile rendering to reduce memory pressure          |
| `ui.editor.compact.enabled` | `true`  | Existing toolbar mode                            | Enable compact multi-page editor mode with stacked pages                    |

---

## Implementation Details

### Files To Create (NOT YET IMPLEMENTED)

- `apps/android/app/src/main/java/com/onyx/android/config/FeatureFlags.kt` - Flag enum with defaults and documentation
- `apps/android/app/src/main/java/com/onyx/android/config/FeatureFlagStore.kt` - SharedPreferences-backed persistence layer
- `apps/android/app/src/main/java/com/onyx/android/ui/DeveloperFlagsScreen.kt` - Debug-only flag toggle UI

### Files Modified

- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvas.kt` - Replaced `ENABLE_MOTION_PREDICTION` with `INK_PREDICTION_ENABLED` flag
- `apps/android/app/src/main/java/com/onyx/android/ink/ui/InkCanvasTouch.kt` - Replaced `ENABLE_PREDICTED_STROKES` with `INK_PREDICTION_ENABLED` flag
- `apps/android/app/src/main/java/com/onyx/android/ui/NoteEditorScreen.kt` - Replaced `ENABLE_STACKED_PAGES` with `UI_EDITOR_COMPACT_ENABLED` flag
- `apps/android/app/src/main/java/com/onyx/android/navigation/Routes.kt` - Added `DEVELOPER_FLAGS` route
- `apps/android/app/src/main/java/com/onyx/android/navigation/OnyxNavHost.kt` - Added developer flags screen route (debug only)
- `apps/android/app/src/main/java/com/onyx/android/ui/HomeScreen.kt` - Added developer flags button in toolbar (debug only)

---

## Flag Usage Locations

### INK_PREDICTION_ENABLED

- `InkCanvas.kt:158-165` - MotionPredictionAdapter initialization
- `InkCanvasTouch.kt:396-399` - Predicted strokes handling

### UI_EDITOR_COMPACT_ENABLED

- `NoteEditorScreen.kt:183-184` - Multi-page vs single-page editor mode selection

### INK_HANDOFF_SYNC_ENABLED

- Reserved for future implementation (Task 1.2)

### INK_FRONTBUFFER_ENABLED

- Reserved for future implementation (Task 1.2)

### PDF_TILE_THROTTLE_ENABLED

- Reserved for future implementation (Task 2.3)

---

## Debug Access

The DeveloperFlagsScreen is accessible only in debug/internalDebug builds via:

1. Home screen toolbar overflow button (Build icon)
2. Direct route: `developer_flags`

The screen provides:

- Toggle switches for each flag
- Default value indicators
- Override status indicators
- "Reset All" button to restore defaults
- Flag descriptions and safe paths

---

## Persistence

Flag values are persisted using SharedPreferences with the name `feature_flags`. Values persist across app restarts, enabling reproducible debugging scenarios.

---

## Rollback Behavior

All flags have defined safe paths that represent the fallback behavior when disabled:

- **Prediction flags** (`ink.prediction`, `ink.frontbuffer`): Default to OFF to ensure stability
- **UI flags** (`ui.editor.compact`): Default to ON (stacked pages is the preferred mode)
- **PDF flags** (`pdf.tile.throttle`): Default to ON for memory safety
- **Handoff flag** (`ink.handoff.sync`): Default to ON (debug only, safe immediate finalize)
