# Canonical PDF Test Corpus (P0/P2/P5)

**Created:** 2026-02-13
**Owner:** Milestone `milestone-canvas-rendering-overhaul.md`
**Purpose:** Fixed corpus for Pdfium parity checks (text, rotation, selection geometry, and no-text fallback behavior).

## Corpus Set (5 PDFs)

| ID | Category | Source | Why this file |
|---|---|---|---|
| C1 | Simple Latin text | https://mozilla.github.io/pdf.js/web/compressed.tracemonkey-pldi-09.pdf | Baseline multipage Latin document for render quality + basic text extraction behavior. |
| C2 | Rotated pages | https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/rotation.pdf | Explicit rotation validation (90/180/270 handling and coordinate normalization). |
| C3 | RTL / complex script | https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/ArabicCIDTrueType.pdf | Arabic shaping and right-to-left text behavior for selection geometry checks. |
| C4 | Ligatures / text shaping | https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/copy_paste_ligatures.pdf | Verifies ligature handling and character mapping edge cases. |
| C5 | Image-heavy (no-text proxy) | https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/images_1bit_grayscale.pdf | Validates behavior when extractable text is absent or minimal (selection fallback UX). |

## Local Storage Convention

Store downloaded files under:

`apps/android/spikes/pdfium-spike/test-pdfs/`

Suggested filenames:

- `C1-compressed-tracemonkey.pdf`
- `C2-rotation.pdf`
- `C3-arabic-cid-truetype.pdf`
- `C4-copy-paste-ligatures.pdf`
- `C5-images-1bit-grayscale.pdf`

## Download Command (Linux/macOS)

```bash
mkdir -p apps/android/spikes/pdfium-spike/test-pdfs
curl -L "https://mozilla.github.io/pdf.js/web/compressed.tracemonkey-pldi-09.pdf" -o apps/android/spikes/pdfium-spike/test-pdfs/C1-compressed-tracemonkey.pdf
curl -L "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/rotation.pdf" -o apps/android/spikes/pdfium-spike/test-pdfs/C2-rotation.pdf
curl -L "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/ArabicCIDTrueType.pdf" -o apps/android/spikes/pdfium-spike/test-pdfs/C3-arabic-cid-truetype.pdf
curl -L "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/copy_paste_ligatures.pdf" -o apps/android/spikes/pdfium-spike/test-pdfs/C4-copy-paste-ligatures.pdf
curl -L "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/images_1bit_grayscale.pdf" -o apps/android/spikes/pdfium-spike/test-pdfs/C5-images-1bit-grayscale.pdf
```

## Validation Use Matrix

- **P0:** API parity + coordinate/text-geometry feasibility.
- **P2:** Tile renderer + selection parity implementation.
- **P5:** Final regression/acceptance on physical devices.
