# MyScript SDK Docs Mirror

This directory contains a local mirror of the MyScript Interactive Ink Android 4.3 Javadocs plus an AI-friendly plaintext companion layer.

## Use This First

- Raw HTML mirror: `docs/Myscript/SDK/`
- Plaintext mirror: `docs/Myscript/SDK/_ai/text/`
- Page manifest: `docs/Myscript/SDK/_ai/manifest.json`
- Quick catalog: `docs/Myscript/SDK/_ai/catalog.tsv`
- Symbol/member index extracted from `index-all.html`: `docs/Myscript/SDK/_ai/symbols.tsv`

## Terminal Usage

- Search symbols: `rg "Editor|ContentPart|addBlock" docs/Myscript/SDK/_ai/text`
- Find the best page first: `rg "addBlock" docs/Myscript/SDK/_ai/symbols.tsv`
- Open a normalized page: `Get-Content docs/Myscript/SDK/_ai/text/com/myscript/iink/Editor.txt -TotalCount 120`

## Notes

- The upstream Javadoc stylesheet references `resources/fonts/dejavu.css`, but that file returns `404` on the source site too.
- The `_ai` directory is generated from the raw mirror by `scripts/normalize-myscript-sdk-docs.ps1`.