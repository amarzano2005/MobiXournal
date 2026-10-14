# The `.mobix` native format and `.xopp` bridge

> Read when designing, reading, writing, or optimizing the native .mobix container, implementing the dual-format bridge with desktop Xournal++, or managing format-level performance optimizations.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

**Where this file's content lives:** the container layout, `manifest.json` and the element schema are
owned by [mobix-schema-spec.md](mobix-schema-spec.md) — this file owns *why* the format exists, the
`.xopp` bridge, and the app-level policy around it. Don't re-draw the container tree here.

## Motivation: why evolve beyond `.xopp`

MobiXournal began as a strict companion to Xournal++ on Linux, storing everything in `.xopp`
(gzip-compressed XML, documented in [xopp-format.md](xopp-format.md)). While this achieves
interoperability, `.xopp` imposes hard architectural bottlenecks:

1. **Dead geometry**: Any complex object (function plots, geometric figures, circuit symbols, tables)
   must be flattened into raw strokes (`<stroke>`) or text boxes. The semantic intent is lost:
   a user cannot re-open a `.xopp` and adjust $f(x) = \sin(x)$ or edit a table cell.
2. **Missing stylus metadata**: `.xopp` has no schema fields for pen tilt, azimuth, barrel roll, or
   per-point timestamps. Advanced pen engines and velocity-based smoothing cannot persist their data.
3. **Monolithic I/O**: An XML file cannot be streamed or paged. Loading or saving a 500-page document
   requires decompressing and parsing the entire document tree in memory, which degrades mobile performance.
4. **Embedded binary bloat**: Images in `.xopp` are base64-encoded inside XML tags, which costs a
   third more bytes before compression and a fresh string allocation per image. Deflate reclaims
   almost all of the size (see the measurement below), so this is a decode-time and memory cost
   more than a disk one.

The solution is a **two-tier architecture**: a modern native container format (`.mobix`) with an
uncompromising **lossless export/import bridge to `.xopp`**.

---

## What `.mobix` does beyond `.xopp`

| Capability | `.xopp` (Current) | `.mobix` (Native) | Status today |
|---|---|---|---|
| **Mathematical plots** | Baked into dead strokes & text boxes | Living `PlotElement`: formula $f(x)$, range, samples, re-editable anytime | **Live** — created, drawn, re-edited in place ([mobix-elements.md](mobix-elements.md)) |
| **Data tables** | Static ink lines & independent text | Living `TableElement`: editable rows, cols, alignments, cell text | **Model only** — codec and ink are live; the table tool still draws a static grid and no editor exists (Step 2: `insert-a-living-table-from-the-table-tool`) |
| **Stylus telemetry** | $(x, y, \text{pressure})$ only | $(x, y, \text{pressure}, \text{tilt}, \text{azimuth}, \text{barrel roll}, \Delta t_{\text{ms}})$ | **Stored, unconsumed** — tilt, azimuth and $\Delta t$ are captured and written; nothing reads them, and no barrel-roll axis is captured at all (`finish-the-pen-telemetry-payoff`) |
| **Stroke replay & dynamics** | Impossible (no timing information) | Real-time replay & physics-driven velocity smoothing via $\Delta t$ timestamps | **Not built** — `render/KaraokeStrokeAnimator.kt` has no caller (`replay-the-writing-in-time-with-its-recording`) |
| **Large notebooks (100+ pages)** | Monolithic XML; huge heap footprint & slow open | Paged container; instant open, random-access streaming via LRU cache | **Library only** — `MobixPagedReader` is written and tested and called by nothing; opening still inflates and parses every page (`open-a-mobix-page-by-page-not-whole`) |
| **Binary assets & photos** | Inefficient base64 inside XML tags | Raw WebP/PNG binaries in `assets/` with content-hash deduplication | **Live** — bundled by content hash on the `.mobix` save path |
| **Audio annotations** | Sidecar `.wav` files prone to detachment | Bundled Opus/WAV in container, linked to stroke millisecond timestamps | **Not self-contained** — the stroke's `audioRef` is written, the bytes stay a sidecar (`carry-the-audio-recording-inside-the-document`) |
| **Document saving** | Entire document re-serialized | Incremental saves: writes only modified `pages/page-N.json` entries | **Library only** — `MobixIncrementalSaver` is written and tested and called by nothing; every save repacks the whole archive (`repack-only-the-pages-a-save-changed`) |

The **Status today** column is the honest one: a capability is **Live** only where the open/save path
actually runs it, **Library only** where a tested engine exists with no caller in `app/src/main`, and
otherwise names what is missing and the task that will make it live. Everything in the *Native* column
is what the format is designed to hold, not a claim that it runs.

Its three structural advantages, all of which follow from the container being a ZIP of small parts
rather than one XML document (layout in [mobix-schema-spec.md](mobix-schema-spec.md)). Whether each
*runs* today is the capability table's **Status today** column above, not a claim here:

- **Streaming & fast startup** — **not live yet**: the container is part-per-page and `MobixPagedReader`
  can read one page at a time, but the open path loads every page (`open-a-mobix-page-by-page-not-whole`).
- **Deduplication of binary assets** — **live**: an image pasted across pages is stored once in
  `assets/` and referenced by content hash.
- **Atomic saves** — **not live yet**: changes to page 3 *should* repack only `page-0003.json`, but
  every save repacks the whole archive (`repack-only-the-pages-a-save-changed`).

### Measured: `.mobix` against `.xopp` on one document

The size case is a number, not a claim. `MobixXoppSizeBenchmarkTest` writes one document through
the real writers (`io/DocumentIo.encode`) in all three formats and reports bytes on disk plus write
and read-back wall time — run `./gradlew testDebugUnitTest --tests com.mobixournal.format.MobixXoppSizeBenchmarkTest`,
report in `app/build/reports/mobix-vs-xopp.txt`. The document is 8 pages / 160 ink strokes plus one
embedded picture (120 kB) and a PDF background (250 kB), both fixed incompressible blobs, so the
container overhead is what is measured (bytes, then write/read ms — median of 5 runs on the host):

| case | `MOBIX` | `ORIGINAL` (gzip) | `ZIPPED` (self-contained) |
|---|---|---|---|
| ink-only | 6 792 B, 12/20 ms | 2 717 B, 42/22 ms | 3 108 B, 11/16 ms |
| picture + PDF | 377 984 B, 17/14 ms | 124 975 B, 18/8 ms | 375 546 B, 20/10 ms |

`ORIGINAL` **links** the PDF instead of carrying it, so its number is a different comparison. What
the numbers say, including where they contradict this doc:

- An **ink-only** `.mobix` is **larger** (6.8 kB vs 2.7 kB gzipped): per-page JSON is more verbose
  than one gzipped XML. The size case is about pictures and long notebooks, not ink.
- With a picture and a PDF, a self-contained `.mobix` and a ZIP-package `.xopp` land **within ~1 %**
  of each other. Base64 inflates an embedded `<image>` by a third on disk, but deflate reclaims
  nearly all of it (the body's 6-bit alphabet), so the "embedded binary bloat" above is a
  decode-time cost, **not** a size win against a compressed `.xopp`. The native format's payoff is
  **structure**: per-page streaming, incremental saves, and asset dedup (one PDF entry behind the
  eight pages here, not eight).

---

## Where `.mobix` sits in the app

### Which format a document is written in

`.mobix` is a *save format*, not a replacement for `.xopp`; the two are interchangeable and the user
decides, once, what new work is made of. The policy is sticky per document, which is what keeps the
desktop round trip safe:

1. **A document remembers its own format.** An opened `.xopp` is saved back as `.xopp` — same gzip
   XML, same desktop interoperability — unless the user asks otherwise. This is the current
   `SaveFormat` behaviour ([data-path.md](data-path.md)) extended with a third value, and it is why
   the bridge below has to be lossless in *both* directions: a `.xopp` that went through a phone is
   still a `.xopp`.
2. **New documents are born in the default format**, which the user picks **on first launch** (the
   onboarding flow, [chrome.md](chrome.md)) and can change at any time under Settings → App & data.
   The default offered first — and the recommended one — is **`.mobix`**, because living elements,
   telemetry and page streaming only exist there.
3. **"Save As" switches the format in either direction**, and that switch is the documented way to
   convert: a `.xopp` opened from a colleague's desktop can become the user's own `.mobix`, and a
   `.mobix` can be flattened out to `.xopp` to hand back to Xournal++.

A **first-launch choice, not a silent default**, is the point: the format is the boundary of what the
app can do ([AGENTS.md](../AGENTS.md)), so which side of the boundary a document lives on is the
user's call, made once with the trade-off visible rather than discovered when a file reaches a laptop.

### Opening `.mobix`

`.mobix` is the app's own type: it is registered as a document handler (extension and MIME, in the
manifest alongside the `.xopp`/PDF/image filters) so a tap in a file manager opens it here rather than
nowhere. Because a `.mobix` and a ZIP-package `.xopp` are both ZIP archives, the classification is the
`mimetype` entry's content, not the leading bytes — see container rule 2 in
[mobix-schema-spec.md](mobix-schema-spec.md).

Conversion is **never automatic on open**: opening a `.xopp` does not write a `.mobix` next to it, and
opening a `.mobix` does not touch the `.xopp` it may have come from. A file the user did not ask to
create does not appear on their disk (and in their sync client's history).

---

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
block still proves them (`MobixMetaRevival`). The export half is `render/XoppSynthesis.kt`, which
flattens a document holding living entities into ink through one compiler per family (`TableInk`,
`PlotInk`, `ShapeInk`, `CircuitInk` — the same compilers the canvas draws through, `render/LivingInk.kt`,
so an export is the screen and not a second opinion about it) and hands the result to the **ordinary** `.xopp`
writer, so there is one emitter and not a second one to keep in step. A `.mobix` opens and saves as itself (the type is registered, the sticky format is per document, its
bundled backgrounds are adopted on the way in — [data-path.md](data-path.md)), and the export is wired:
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
edit of all of them to a sync client ([sync-and-storage.md](sync-and-storage.md)). Page ids are stable
(they are the manifest's, not the position's, so a reordered page is still the same page), entries this
build does not model are written back untouched, and a payload read from a file and never inspected is
re-written rather than orphaned.

**What a reader refuses, rather than opening something it cannot keep:** a page the manifest names but
the archive does not carry (a corrupt file, never an empty page), a `formatVersion` from a newer
schema, a `canvas.json` that disagrees with the manifest about the mode, and an infinite-canvas
document — its content lives in `chunks/`, the page model cannot hold it, and opening it as an empty
page list would let a save rewrite a chunked document as a paginated one. The canvas engine lifting
that last refusal is a later step ([infinite-canvas.md](infinite-canvas.md)), tracked as
`render-and-edit-an-infinite-canvas-document`. `minAppVersion` is a
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

**Reported, never substituted.** `render/XoppSynthesis.kt` returns its `.xopp` document *and* a list of
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

### Opening `.mobix` directly in desktop Xournal++ (Solution C: Lua plugin)

While desktop Xournal++ (C++/GTK3) does not natively know the `.mobix` file type, Xournal++ provides a
**Lua plugin API** (`~/.config/xournalpp/plugins/`).

MobiXournal ships an official companion plugin (`xournalpp-mobix-plugin`), **as a published artifact,
not as this app's code**:

```text
~/.config/xournalpp/plugins/mobix-importer/
├── plugin.ini                   # Declares plugin name, author, menu entry
└── main.lua                     # Unpacks .mobix, parses JSON, and injects pages into Xournal++
```

- **User workflow**: In desktop Xournal++, the user clicks **Plugin → Import MobiXournal document…**
  and selects the `.mobix` file.
- **Under the hood**: The Lua script unzips the archive into a temporary cache, translates
  `manifest.json` and `pages/*.json` into standard Xournal++ document elements using the Lua document
  API — or, where that API cannot express a construct, **synthesizes an equivalent `.xopp` and calls
  `app.openFile`**, which is the fallback that makes the plugin work regardless of how complete the
  in-app Lua bindings are.
- **Not part of this repo's build, and not a dependency of the app.** It is a separate deliverable
  that lives with the plugin ecosystem; the app never requires it, and the `.xopp` export above is
  the mechanism guaranteed to work today. Its limits are the desktop API's, not the format's, so it
  is deliberately allowed to lag the app.

---

## Engine optimizations enabled by the format

Migrating to the `.mobix` format directly enables four major performance optimizations in the engine.
Each one is labelled with **what actually runs today** — **Live**, **library only** (a tested engine with
no caller in `app/src/main`), or **not built** — and names the task that makes it live.

1. **Page Virtualization & LRU Streaming (Instant open on huge notebooks)** — **library only**:
   - `format/mobix/MobixPagedReader.kt` walks the ZIP central directory and materialises a page (or a
     chunk) on demand into an active sliding window (e.g. current page $\pm 2$ adjacent pages), but
     **no file in `app/src/main` calls it**: `io/DocumentIo.kt` still opens a `.mobix` through
     `MobixContainer.open` + `MobixDocument.open` + `toDocument()`, so a 200-page notebook is fully
     inflated and parsed before the editor sees a page. `open-a-mobix-page-by-page-not-whole` is what
     makes it live.
   - Pages outside the viewport are evicted from RAM and re-read from the ZIP container on demand
     in $< 5\text{ ms}$ when scrolled into view, verified across synthetic 200-page archives in `MobixPagedReaderTest`.
   - **Measured** (`MobixPagedOpenBenchmarkTest`): a 200-page document with a 1 MiB asset opens in 15 ms
     inflating all 204 entries on the whole-archive path, 8 ms inflating 203 on the streaming open (the
     asset is deferred), and 1 ms on the reader, which inflates 8.
   - This is a property of the container's part-per-page layout; the same reader serves the
     infinite-canvas chunk mode ([infinite-canvas.md](infinite-canvas.md)) with chunks in place of pages.
2. **Incremental Delta-Writes (Zero save latency)** — **library only**:
   - `format/mobix/MobixIncrementalSaver.kt` re-compresses only modified page entries (`pages/page-N.json`)
     and the updated `manifest.json` into the ZIP archive, but **no save path uses it**: the `MOBIX`
     branch of `io/DocumentIo.kt` encode always calls `MobixDocument.of(...).toBytes()`, so every save
     repacks the whole archive. `repack-only-the-pages-a-save-changed` wires it in.
   - Unmodified pages, heavy background PDFs, and image assets are streamed directly from the source archive
     without re-compression; unknown/unmodelled entries survive untouched (container rule 4,
     [mobix-schema-spec.md](mobix-schema-spec.md)).
   - Evaluated in `MobixIncrementalSaveTest`: on a 10-page notebook with 1 page modified, only 2 entries
     (manifest + dirty page) are rewritten while 11 entries (9 pages, canvas, preserved payload) are streamed
     byte-for-byte, cutting compression overhead by $> 80\%$. Saves complete in milliseconds without UI freezes.
3. **Zero-Allocation Inking Loop (No GC stutter)** — **not built**:
   - High-rate stylus events (120 Hz–240 Hz on modern tablets and stylus digitizers) can trigger frequent
     JVM Garbage Collection pauses if objects (`StrokePoint`, temporary event wrappers) are allocated per touch.
   - The live stroke is still a growing `ArrayList<StrokePoint>` (`render/DrawingSurfaceStrokes.kt`),
     so a `StrokePoint` and its wrappers are allocated per touch today. The pre-allocated, pooled
     primitive-array path this item describes is not implemented.
4. **GPU Texture Layering & Path Tessellation Cache** — **live (off-screen raster)**:
   - Static elements (paper rulings, PDF page backgrounds, committed lower layers) are rendered once into
     an off-screen GPU texture (`android.graphics.Bitmap` hardware texture on Android, Skia `Image` on Desktop).
     This is what `render/InkCache.kt` does (one bitmap per page at a zoom bucket, charged to a shared
     `BitmapBudget` beside `render/BitmapLruCache.kt` and `render/PdfPageCache`).
   - In-flight strokes are rendered solely on a lightweight transparent overlay, keeping per-frame redraw times
     under $4\text{ ms}$ (comfortably achieving 144 Hz / 240 Hz displays).
   - Vector paths for completed strokes are cached for reuse across zoom and pan; the tessellation cache
     named here is the platform `Canvas` path cache, not an app-owned one.
