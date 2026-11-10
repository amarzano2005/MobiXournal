# The `.mobix` data model, field by field

> Read when you need the exact field, type, unit, requiredness or default of `manifest.json`, `canvas.json`, a page or a chunk.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

Normative: an implementation that disagrees with a **MUST** here produces a file this format calls
invalid, and one that fills a **default** differently reads a valid file as a different document. The
container around these parts is [mobix-schema-spec.md](mobix-schema-spec.md)'s; the elements inside a
layer are [mobix-elements.md](mobix-elements.md)'s.

**Units, once:** lengths and coordinates are typographic **points** (`pt`, 1/72 inch), times are **UTC
epoch milliseconds**, colours are `#RRGGBBAA` strings, and a boolean is a JSON boolean. Nothing in this
format stores device pixels.

## `manifest.json`

| Field | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `formatVersion` | integer | **yes** | — | the schema shape; `1` is this one |
| `documentId` | string | **yes** | — | the document's identity across saves, tabs and merges |
| `canvasMode` | string | **yes** | — | `PAGINATED` (content in `pages/`), `INFINITE_VERTICAL`, `INFINITE_HORIZONTAL` or `INFINITE_2D` (content in `chunks/`) |
| `minAppVersion` | string | no | `"0.0.0"` | the writer's floor: a reader below it may show the document but not save it |
| `created` | integer | no | `0` | when the document was first written |
| `modified` | integer | no | `0` | when the document was last written |
| `title` | string or null | no | `null` | the user's title, never used as a file name |
| `pages` | array of parts | no | `[]` | the pages, **in document order** |
| `chunks` | array of parts | no | `[]` | the occupied tiles of an infinite mode |
| `defaultPaper` | background object | no | `null` | what a new page starts on, in the same shape a page's `background` has ([mobix-schema-spec.md](mobix-schema-spec.md) → *Backgrounds*) |
| `requiredCapabilities` | array of strings | no | `[]` | see [mobix-capability contract](mobix-schema-spec.md#declared-capabilities) |
| `optionalCapabilities` | array of strings | no | `[]` | the same grammar, a weaker promise |

`defaultPaper` being a full background object rather than a paper name is deliberate: what a new page
starts on and what this page is on cannot drift apart, and a ruling's parameters ride verbatim in
`config` — the comma-separated string desktop Xournal++ writes — instead of being parsed and
re-serialised into a spelling mismatch.

**A part** (`pages[]` and `chunks[]` hold the same shape):

| Field | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `id` | string | **yes** | — | the part's identity; two saves of one page keep it |
| `entry` | string | **yes** | — | the archive entry that holds the part's bytes |
| `modified` | integer | no | `0` | the part's last-write time, which is what a merge compares |
| `sha256` | string | no | `""` | the digest of the entry's **stored** bytes; empty means no claim, and a claim is checked |

## `canvas.json`

Present in every infinite mode and optional in a paginated one; `mode` MUST agree with the manifest's
`canvasMode`, and a disagreement is a corrupt document rather than a document with two answers.

| Field | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `mode` | string | **yes** | — | the same enum as the manifest's `canvasMode` |
| `widthPt` | number | no | `0.0` | the sheet's width; the fixed dimension in a width-locked mode |
| `heightPt` | number | no | `0.0` | the sheet's height |
| `chunkSizePt` | number | no | `1024.0` | the tile edge; a value of zero or below is read as the default, never as an error |
| `origin.pageNo` | integer | no | `1` | the 1-based sheet page an export starts at |
| `origin.offsetPt` | number | no | `0.0` | how far into that sheet the document's `(0, 0)` sits |

## The page object

| Field | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `widthPt` | number | no | `0.0` | this page's width, which may differ from the sheet's |
| `heightPt` | number | no | `0.0` | this page's height |
| `background` | background object | no | the manifest's `defaultPaper` | what is printed under the ink |
| `layers` | array of layers | no | `[]` | bottom first |
| `layers[].name` | string | no | absent | a label for the layer manager, not an identity |
| `layers[].elements` | array | no | `[]` | the elements, bottom first |

## The chunk object

A chunk is a page object in every respect — same fields, same defaults — with two differences that
come from where it lives rather than from anything inside it:

- its **coordinates** are its name's: `chunks/chunk_<cx>_<cy>.json`, both signed integers, so
  `chunk_-3_2.json` is a tile of its own and not a sign-less twin of `chunk_3_2.json`;
- `widthPt`/`heightPt` describe the **tile**, whose edge is `chunkSizePt`, not the whole board.

## Rules that are not field-local

1. **`pages[]` is the document order.** A reader MUST NOT sort entries by name: a page list that was
   reordered or spliced in another editor would come back in the wrong order.
2. **`chunks[]` is the set of occupied tiles.** A tile with nothing on it is absent rather than empty,
   and a reader MUST NOT invent the tiles between two occupied ones.
3. **The two lists are exclusive with the mode.** `PAGINATED` documents carry `pages` and no `chunks`;
   an infinite mode carries `chunks` and no `pages`. A file with the other list non-empty is corrupt.
4. **Part identity is `id`, never the entry name.** A part may be renamed on disk by a writer that
   renumbers, and the manifest is what says which page this is; repeating the id inside the page would
   give the document two places to disagree.
5. **Z-order is layer order, then element order.** `layers[0]` paints first, `elements[0]` paints before
   `elements[1]`, and there is no z field for an element to carry.
6. **`modified` is bookkeeping, not a clock.** A merge compares part and document stamps to decide which
   side moved; a reader MUST NOT refuse a document for a stamp it did not expect.
7. **Unknown fields are preserved** at every level — manifest, part, canvas, page, layer — with their
   values and their order, written back last. This is the retention rule of
   [mobix-elements.md](mobix-elements.md) applied to the envelope, and it is what lets a newer writer
   add a field without bumping `formatVersion`.

## What a hand-written document may leave out

A minimal manifest needs **only** the three fields no reader can invent — `formatVersion`, `documentId`
and `canvasMode` — and fixture 01 in the golden corpus is exactly that file: everything else takes the
default in the tables above, and the document opens with one page, no title, no capabilities and no
paper of its own.

The other direction is not symmetric: a reader MUST refuse a manifest that omits one of those three, a
`formatVersion` this build does not understand, a `canvasMode` it does not know, a `pages[]` entry whose
`entry` names nothing in the archive, and a capability identifier that does not parse. Absent is a
default; unreadable is a corrupt file.
