# Decisions - Canvas Rendering Overhaul

## Phase 0: PdfiumAndroid Spike

- Locked dependency coordinate to `com.github.Zoltaneusz:PdfiumAndroid:b68e47459ab90501fd377aa6456618bc87f06d3c` (minSdk 28 compatible).
- Set P0 binary gate outcome to `JNI_BRIDGE_REQUIRED` for text selection parity.
- Keep app minSdk at 28 (no Option B bump to 30).
- Added JNI packaging pick-first for `**/libc++_shared.so` to resolve MyScript/Pdfium native merge collision.
- Keep JitPack as required repository in `apps/android/settings.gradle.kts`.
