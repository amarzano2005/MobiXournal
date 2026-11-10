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
| `08-unknown-top-level-field.mobix` | A `stroke` and a `text` carrying extra top-level keys (`pressureSeed`, `tiltCurve`, `lineHeight`, `hyphenation`); the element's `unknown` map keeps them and writes them back after the known fields. |
| `09-unknown-nested-fields.mobix` | A `table` cell and a `plot` curve carrying extra keys, plus an extra key inside `shadeIntegral`: every codec that owns an object keeps the keys it did not read. |
| `10-chunked-document.mobix` | An infinite-canvas document: `chunks[]` with signed coordinates instead of `pages[]`, and a `canvas.json` that declares the tile size. |
| `11-unknown-vocabulary-values.mobix` | Three pages, one per unknown value: `tool: "FOUNTAIN_PEN"`, a table cell with `align: "JUSTIFY"` and a plot curve with `kind: "FOURIER"`. All three elements are preserved whole and the page opens — a value this build would misread is not drawn, it is kept. |
| `12-unknown-background-type.mobix` | A page background and a `defaultPaper` of type `gradient`: drawn as the plain sheet the fallback always was, written back field for field including its `type`. |
| `13-required-capability-unknown.mobix` | `requiredCapabilities: ["example.org/stem.connector:1"]` — a required feature from a namespace this build knows nothing about, so the gate refuses to write. |
| `14-optional-capability-unknown.mobix` | The same feature as an `optionalCapabilities` hint over a `3dPlotElement` this build has no model for: the document stays editable elsewhere and the element is preserved. |
| `15-capability-higher-version.mobix` | `org.mobixournal/stem.connector:2` required while this build implements `:1`: a later version is a superset, so `:1` does not satisfy it. |
| `16-capability-additive-order.mobix` | Both lists in a writer's own order, mixing `org.mobixournal` with a foreign namespace and carrying no duplicate: the order survives a save. |
| `17-third-party-writer.mobix` | A file written elsewhere: no `minAppVersion`, a foreign capability namespace, and a construct with no model. |

Every construct in this corpus is asserted as **preserved**: the test says what a
release must keep, and a change of promise changes this table and the capability
table in `docs/mobix-format.md` together.
