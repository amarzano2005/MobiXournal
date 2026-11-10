# Golden `.mobix` v1 fixtures

Versioned `.mobix` files that a release must keep opening. Each one exercises one
compatibility construct; `MobixV1FixturesTest` drives every file through the
container, the paged reader, the document layer, a save to a new file and an
incremental save.

Regenerate with `python3 scripts/make-mobix-fixtures.py` (dependency-free and
deterministic: fixed ZIP timestamps, no clock, no locale). The script is the
source of truth for the bytes — a fixture and this table are changed together.

| Fixture | What it exercises |
|---|---|
| `01-minimal-single-page.mobix` | A hand-written document with the three required manifest fields only, no `canvas.json` and no optional envelope fields; defaults must fill the rest. |
| `02-multi-page-order.mobix` | Three pages whose manifest order is spliced relative to the entry names, with part ids unrelated to position: the manifest, not the file names, is the page order. |
| `03-bundled-assets.mobix` | A bundled picture used by two elements, a bundled PDF background and a bundled recording, each under `assets/` named by the SHA-256 of its bytes. |
| `04-incremental-save.mobix` | A document already saved incrementally: per-page `modified` stamps differ, and every `pages[].sha256` is the digest of the entry's stored bytes, which is what lets a later save copy the untouched pages. |
| `05-unknown-entry.mobix` | Two archive entries this build does not model (`future/thing.bin`, `index/search.idx`); both must survive a save. |
| `06-unknown-envelope-fields.mobix` | Unknown fields on the manifest, on a page part, on `canvas.json` and inside `canvas.json`'s `origin`; all must keep their value and order. |
| `07-unknown-element-type.mobix` | An element `type` with no model (`3dPlotElement`) and a `.xopp`-carried element (`"syntax": "xml"`); both must be kept whole. |
| `08-unknown-top-level-field.mobix` | A `stroke` and a `text` carrying extra top-level keys. **Known gap**: the model has no channel for them, so a save drops them. |
| `09-unknown-nested-fields.mobix` | A `table` cell and a `plot` curve carrying extra keys, plus an extra key inside `shadeIntegral`. **Known gap**: the same drop, one level deeper. |
| `10-chunked-document.mobix` | An infinite-canvas document: `chunks[]` with signed coordinates instead of `pages[]`, and a `canvas.json` that declares the tile size. |
| `11-unknown-vocabulary-values.mobix` | Three pages, one per unknown value: `tool: "FOUNTAIN_PEN"` (the page is refused), a table cell with `align: "JUSTIFY"` and a plot curve with `kind: "FOURIER"` (both elements are preserved whole). |
| `12-unknown-background-type.mobix` | A page background and a `defaultPaper` of type `gradient`. **Known gap**: the reader falls back to a plain sheet, so the written background is a different one. |

The gaps are asserted as gaps by the test — never as preservation that works —
and they are the inputs of the child tasks the format-evolution scopes produced.
