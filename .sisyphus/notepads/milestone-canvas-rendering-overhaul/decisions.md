# Decisions - Canvas Rendering Overhaul

## Phase 0: PdfiumAndroid Spike

- Locked dependency coordinate to `com.github.Zoltaneusz:PdfiumAndroid:b68e47459ab90501fd377aa6456618bc87f06d3c` (minSdk 28 compatible).
- Set P0 binary gate outcome to `JNI_BRIDGE_REQUIRED` for text selection parity.
- Keep app minSdk at 28 (no Option B bump to 30).
- Added JNI packaging pick-first for `**/libc++_shared.so` to resolve MyScript/Pdfium native merge collision.
- Keep JitPack as required repository in `apps/android/settings.gradle.kts`.
- Re-enable motion prediction flags (`ENABLE_MOTION_PREDICTION`, `ENABLE_PREDICTED_STROKES`) with predicted-point discard on pen-up preserved.
- Share a single pressure gamma function (`PRESSURE_GAMMA = 0.6`) between committed and in-progress stroke pipelines.
- Reduce PDF render scale buckets to `1x/2x/4x` (P1.4 pre-task) to align cache policy assumptions.
- Set P1.3 front-buffer investigation state to `PENDING_DEVICE_PROFILE`; do not start custom GL front-buffer implementation before physical Perfetto evidence.
