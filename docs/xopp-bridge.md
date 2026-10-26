# The `.mobix` ↔ `.xopp` bridge: synthesis, revival and the metadata block

> Read when you change how a `.mobix` leaves this app as a `.xopp`, or how a `.xopp` comes back with its living
> elements intact.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

**Where this file's content lives:** this spoke owns the bridge in both directions — `XoppSynthesis` with
`render/LivingInk.kt` on the way out, `MobixMetaRevival` on the way in, and the `mobix:meta` comment block
that carries the semantics across. The container is [mobix-format.md](mobix-format.md)'s, the `.xopp` schema
is [xopp-format.md](xopp-format.md)'s, and the element schema is [mobix-elements.md](mobix-elements.md)'s.

## Dual-format bridge (`.mobix` ↔ `.xopp`)

To maintain complete workflow parity with desktop Linux Xournal++, MobiXournal provides a bidirectional
bridge:

```text
┌─────────────────┐       Lossless Ingestion       ┌─────────────────┐
│   .xopp File    │ ─────────────────────────────> │  .mobix Model   │
│ (Desktop Linux) │ <───────────────────────────── │ (Living Models) │
└─────────────────┘       Synthesis Export         └─────────────────┘
```

**Status: the format is openable and savable, the compiler is written in both directions, and the block
that carries the semantics is written with it, and the export is wired.** The import half is `format/mobix/MobixDocument.kt` with
`MobixBridgeFile.kt` — a `.xopp` becomes a `.mobix` document, its living elements revived where the
block still proves them (`MobixMetaRevival`). The export half is `render/XoppSynthesis.kt` in the
desktop-shared `mobix-core`, which
flattens a document holding living entities into ink through one compiler per family (`TableInk`,
`PlotInk`, `ShapeInk`, `CircuitInk` — the same compilers the canvas draws through, `render/LivingInk.kt`,
so an export is the screen and not a second opinion about it) and hands the result to the **ordinary** `.xopp`
writer, so there is one emitter and not a second one to keep in step. A `.mobix` opens and saves as itself (the type is registered, the sticky format is per document, its
bundled backgrounds are adopted a page at a time — [data-path.md](data-path.md)), and the export is wired:
every `.xopp` write goes through the synthesis (`io/DocumentIo.encode`), so Save As to Original or
Zipped is how a `.mobix` is handed to the desktop — the tab keeps its own format and the export is a
copy on disk. An ink-only document passes through byte for byte (the synthesis is the identity on it),
and what this build could not draw is reported after a successful export rather than dropped in silence
(`ui/ExportGapsDialog.kt`).

1. **Import `.xopp` → `.mobix`**:
   - Every `.xopp` construct maps to its element in [mobix-elements.md](mobix-elements.md):
     strokes (with their `ts`/`fn` audio links), texts, images, `texImage`s and backgrounds are
     ingested one-to-one; pages and layers survive as pages and layers, names included. A layer's
     *visibility* survives nowhere: it is a view-only editor state and neither format stores it.
   - Anything the model does not understand — an unknown element, an unknown attribute, an unknown
     markup block — is **preserved verbatim** (the `RawElement`/`extraAttrs` rule the `.xopp` path
     already enforces, [xopp-format.md](xopp-format.md)), so an import is lossless even for markup
     from a newer Xournal++. The test for it is the whole round trip: the `.xopp` written back out of
     the converted document is the same XML that went in, element for element, `ts`/`fn` included.
   - **Identity is assigned exactly once, at conversion.** A `.xopp` has no ids, so every page and
     every element gets one (`ElementId.withIds`) *and it is written into the file from that first
     save on* (`format/model/ElementIds.kt`). That matters more than it looks: a reader that finds an
     element without an id mints one, so an import that left them out would hand out fresh ids on
     every open and a merge would see the whole page as new. The ids are minted into the **model**
     before the write (`Document.withIdentity`, `format/model/DocumentIdentity.kt`) rather than into a
     copy inside the encoder, so they are the open document's own: it adopts them at the first
     `.mobix` save and every save after that — and every edit after that — carries them along.
   - **The first page's background becomes the document's `defaultPaper`** — the desktop's own rule
     for what a new page starts on — and a ruling's parameters travel verbatim in its `config` string
     rather than being parsed and re-spelled.
   - **Nothing is bundled by the conversion.** A `.xopp` references its pictures by path and its PDFs
     by a `pdf` domain, and the converted document keeps those references as they were; the app's own
     stores supply the bytes at save time when they can, which is what makes the file
     self-contained (the document layer's `backgroundBytes`).
   - **What the source was is recorded, not modelled**: the `.xopp` root's `creator` and
     `fileVersion`, its `<preview>` thumbnail and the source's name go into
     `xopp-bridge/metadata.json`, so a file that travelled through this app still identifies itself
     the way its owner's Xournal++ did instead of claiming to be ours. A `.mobix` created natively
     writes no such record.
   - If the `.xopp` carries MobiXournal bridge metadata (below), the living elements it describes are
     revived instead of being re-derived from the flattened strokes.

**What a `.mobix` save writes.** The manifest is not copied from what was read: `pages[]` is derived
from the pages being written, each page's `sha256` is the digest of the stored bytes, and a page whose
bytes did not change **keeps its `modified` stamp** — a save that edited one page must not look like an
edit of all of them to anything that compares two copies. Page ids are stable (they are the manifest's,
not the position's, so a reordered page is still the same page), entries this build does not model are
written back untouched, and a payload read from a file and never inspected is re-written rather than
orphaned.

**Merging two copies is library code, not a product.** `MobixMergeEngine` in `format/mobix/` reconciles
three sides (base, local, remote) per page and per element id; nothing in the app calls it, there is no
sync UI, and no account. It stays because the identity work above is what makes it possible, and it is
tested as a library.

**What a reader refuses, rather than opening something it cannot keep:** a page (or chunk) the manifest
names but the archive does not carry (a corrupt file, never an empty page), a `formatVersion` from a
newer schema, a `canvas.json` that disagrees with the manifest about the mode, and a file that carries
both `pages/` and `chunks/` — the entries and the declared mode have to agree with each other. The
canvas *engine* does not yet draw that geometry unbounded ([infinite-canvas.md](infinite-canvas.md)).
`minAppVersion` is a
**caller's** question, not this layer's: the document layer exposes
`manifest.requiresAppNewerThan(appVersion)`, and whether that means "open read-only" or "refuse" is the
app's policy ([mobix-schema-spec.md](mobix-schema-spec.md), rule 4).
2. **Export `.mobix` → `.xopp` (Solution A: Zero-friction in-app export)**:
   - Available directly from MobiXournal's overflow menu / Save As dialog on both Android and Desktop.
   - Every living entity is **replaced in place** by the ink it stands for, in its own layer and at its
     own z-position; nothing else about the document changes, so the `.xopp` half of the model — and
     the `.xopp` → `.mobix` direction above — is untouched by the export.
   - `PlotElement` is compiled by `MultiFunctionPlotter` (frame, axes, ticks, curves, the analysis
     summary) when its curves are all Cartesian; a parametric or polar curve is placed by the plotter's
     geometry nowhere, so it is sampled directly and the shared frame is fitted around what came out.
     `TableElement` is compiled into grid rules plus one text box per non-empty cell; a filled cell
     is a closed rectangle stroke carrying the desktop `fill` alpha, and a second stroke underneath
     when its colour differs from the rule's (a `.xopp` stroke has one colour and one interior alpha).
   - `ParametricShapeElement` becomes its vertices closed on themselves — the geometry the file stores,
     not the kind's angles re-derived — with its angle and side-length labels; `CircuitElement` is
     recovered from the **symbol library** and placed by the element's own **pins**, drawn before the
     rotation and turned afterwards, one stroke per disconnected part so nothing is drawn across a
     capacitor's gap. A component's `value` is written beside it, and the ink is the element's own
     `strokeColor`, so the drawing that leaves this app is the drawing that was on screen.
   - **Lossless metadata tag**: The `.xopp` writer injects an application metadata block carrying the
     living elements, each with a digest of the ink it became, so the export can be *re-imported* with
     its semantics instead of only as the drawing — the block, the escaping a comment forces and the
     verification rule are all specified [below](#the-bridge-metadata-block-and-its-two-hazards).
   - **The synthesized `.xopp` is a real document, not a rendering**: it opens, prints and edits in
     desktop Xournal++ like any other file, because every element is emitted as ordinary strokes and
     text — the metadata is additive.

#### What the export loses, and what it refuses

Flattening is a translation, not a copy, so the export states its losses in two places rather than
hiding them.

**Reported, never substituted.** `XoppSynthesis.synthesize` returns its `.xopp` document *and* a list of
what it could not draw (`SynthesisResult.uncompiled`, each entry naming the type, the element's own
wording and why):

- a `symbol`/`shape` key this build has no drawing for — there is no ink to invent, and a plausible
  substitute would be a lie in the user's own file;
- a curve whose formula does not parse, reported **per curve** — a plot that lost one of three curves
  is a different loss from a plot that drew nothing, and only the curves that parse are drawn;
- an element preserved verbatim from a **newer** `.mobix` whose body is JSON: it has `.xopp` spelling
  nowhere, which is exactly why the writer skips such an element. Its `.xopp`-syntax sibling (a
  `RawElement` from a `.xopp`) is carried through untouched.

**Lost because the format has nowhere to put it.** A `Stroke`'s `tilt`, `azimuth` and `dtMs` do not
survive (`.xopp` stores a vertex's `x`, `y` and width only, [xopp-format.md](xopp-format.md)); the
widths do, and they are the filtered widths the app draws with, so the exported drawing is the one on
screen and only the telemetry behind it is gone ([stylus.md](stylus.md)). Every entity above also stops
being *editable*: a `.xopp` holds a drawing of a table, and the recipe that would let it be edited again
lives only in the `.mobix` — which is what the metadata block below exists to recover. A document that
is already all ink exports **unchanged**, and one with nothing to draw is left alone rather than being
given an empty frame.

### The bridge metadata block, and its two hazards

The living elements cannot be recovered from the ink they were flattened into, so the export carries
them in the file *beside* the drawing — one tagged comment at the end of the root element
(`Xopp.BRIDGE_COMMENT`, written by `XoppWriter` and read back by `Xopp.parseXml` into
`Document.bridge`):

```xml
<!-- mobix:meta {"version":1,"doc":"f47ac10b-…","elements":[ … ]} -->
```

Each entry of `elements` claims that one entity was written into the ink at an **index range** of a
page's layer, and records the digest of exactly that ink (`format/mobix/MobixMeta.kt`,
`MobixMetaDigest`). An index range because a `.xopp` has no element ids at all, and the range is the
only handle the flattening leaves behind; a digest because an assertion with no way to check it is
exactly what a stale block makes wrong.

```json
{"page":0,"layer":0,"at":0,"count":16,"sha256":"d919330e…","element":{"type":"table","id":"tbl_1", … }}
```

`version` is the block's schema version (this build writes 1, and refuses a newer one whole), `doc` is
the exporting document's identity — recorded, not decided on, since an import cannot always know which
`.mobix` the file came from — and `element` is the entity exactly as the `.mobix` element schema spells
it ([mobix-elements.md](mobix-elements.md)), **id included**: identity is what a merge keys on, so a
revived entity comes back as the same element rather than as a new one. The ink around it is a
`.xopp`'s, and gets fresh ids on import like any other stroke.

**What the digest is.** The ink in `.xopp`'s own spelling, taken through the writer's fixed point: the
exporter has freshly compiled strokes while the importer has ones parsed back out of the file, so the
two agree only if both are put through the same writer, and the extra round trip is what makes the
value independent of *when* it was computed. It is therefore a statement about the writer as much as
about the ink — change how a stroke is spelled and older blocks stop verifying, which the rule below
turns into a discarded claim rather than a wrong drawing.

Two traps about that line the implementation does not get to choose about, and the answers it gives:

- **An XML comment cannot contain `--`.** A block holding a formula (`x--1`), a label or any JSON
  string with two hyphens side by side would produce a document a conforming parser — desktop
  Xournal++ — refuses to open. So the payload is escaped on the way in and unescaped on the way out by
  one rule (`format/xml/XmlComment.kt`): **every `-` becomes `\-`**, which cannot leave a `--` behind
  and is its own inverse. The block is a *payload*, never text to be read as-is.
- **A round trip through the desktop may drop it.** Xournal++ ignores XML comments and does not promise
  to write them back: save the file there and the block is gone, or stale. It is therefore treated as a
  **claim to be verified, never as truth**.

**The verification rule.** On import (`MobixMetaRevival`), an entry is honoured only where the document
in front of us still proves it: the page and layer exist, the index range fits, the ink in it hashes to
the recorded digest, and no earlier entry has already claimed that ink. Everything else is dropped, and
the ink is read as the ordinary strokes it is:

| The file | What the import does |
|---|---|
| block intact, ink untouched | the entities come back, with the ids the block recorded |
| one entity's ink edited, deleted or moved | that claim is dropped; every other claim still applies |
| two entries claiming one stretch of ink | the first wins — two entities cannot both have been written there |
| a `version` from a newer build, unreadable JSON, or no block at all | nothing is revived; the file imports as the drawing it is |
| an entry naming something that is not a living entity | dropped on its own; the rest of the block still applies |

That is what "lossless" means for this bridge, and it is deliberately narrower than "bit-identical":
geometry is always preserved exactly (it is compiled into real elements), and semantics are recovered
only while nothing downstream touched them. A file edited in Xournal++ opens correctly — as the edit,
which is what it now is — and never as a table that swallowed it.
