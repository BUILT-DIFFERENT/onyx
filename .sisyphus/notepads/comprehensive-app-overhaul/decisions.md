# Decisions

## 2026-02-19 - G-7.3-A Template persistence shape

- Persist template state per page by storing/upserting one template row per page (`templateId = "<pageId>-template"`) and updating `pages.templateId`.
- Keep template drawing in page coordinate space and apply the same zoom/pan transform used by page content to avoid drift.
- Use kind-specific density ranges in the toolbar (`grid 20..60`, `lined 30..50`, `dotted 10..20`) to keep presets usable and predictable.
