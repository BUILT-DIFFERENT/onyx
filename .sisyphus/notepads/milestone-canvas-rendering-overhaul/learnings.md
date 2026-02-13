# Learnings - Canvas Rendering Overhaul

## Technical Discoveries

- Pdfium wrapper Java API exposes rendering/navigation basics but omits text geometry APIs.
- Native symbols confirm text geometry exists underneath (`FPDFText_GetCharBox`, `FPDFText_GetCharOrigin`, `FPDFText_CountChars`).
- Fork commit from `min_SDK_28` branch is currently the viable path for app compatibility.
- Local JVM reflection tests must avoid class initialization for `PdfiumCore` (use `Class.forName(..., initialize = false, ...)`).
