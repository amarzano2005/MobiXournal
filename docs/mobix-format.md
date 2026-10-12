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
4. **Embedded binary bloat**: Images in `.xopp` are base64-encoded inside XML tags, inflating file size
   and requiring expensive string allocations.

The solution is a **two-tier architecture**: a modern native container format (`.mobix`) with an
uncompromising **lossless export/import bridge to `.xopp`**.

---

## What `.mobix` does beyond `.xopp`

| Capability | `.xopp` (Current) | `.mobix` (Native) |
|---|---|---|
| **Mathematical plots** | Baked into dead strokes & text boxes | Living `PlotElement`: formula $f(x)$, range, samples, re-editable anytime |
| **Data tables** | Static ink lines & independent text | Living `TableElement`: editable rows, cols, alignments, cell text |
| **Stylus telemetry** | $(x, y, \text{pressure})$ only | $(x, y, \text{pressure}, \text{tilt}, \text{azimuth}, \text{barrel roll}, \Delta t_{\text{ms}})$ |
| **Stroke replay & dynamics** | Impossible (no timing information) | Real-time replay & physics-driven velocity smoothing via $\Delta t$ timestamps |
| **Large notebooks (100+ pages)** | Monolithic XML; huge heap footprint & slow open | Paged container; instant open, random-access streaming via LRU cache |
| **Binary assets & photos** | Inefficient base64 inside XML tags | Raw WebP/PNG binaries in `assets/` with content-hash deduplication |
| **Audio annotations** | Sidecar `.wav` files prone to detachment | Bundled Opus/WAV in container, linked to stroke millisecond timestamps |
| **Document saving** | Entire document re-serialized | Incremental saves: writes only modified `pages/page-N.json` entries |

Its three structural advantages, all of which follow from the container being a ZIP of small parts
rather than one XML document (layout in [mobix-schema-spec.md](mobix-schema-spec.md)):

- **Streaming & fast startup**: The app reads `manifest.json` and loads only visible pages
  (`page-0001.json`). Navigating to page 200 does not parse pages 1–199.
- **Deduplication of binary assets**: If an image is pasted across multiple pages, it is stored once
  in `assets/` and referenced by hash.
- **Atomic saves**: Changes to page 3 only require repacking `page-0003.json` inside the archive,
  saving battery and flash write cycles.

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

**Status: the import half is implemented** (`format/mobix/MobixDocument.kt`, `MobixBridgeFile.kt`,
`MobixDocumentTest`), in both directions — a `.xopp` becomes a `.mobix` document and can be read back
into the model. The synthesis export below is not written yet.

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
     every open and a merge would see the whole page as new.
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
that last refusal is a later step ([infinite-canvas.md](infinite-canvas.md)). `minAppVersion` is a
**caller's** question, not this layer's: the document layer exposes
`manifest.requiresAppNewerThan(appVersion)`, and whether that means "open read-only" or "refuse" is the
app's policy ([mobix-schema-spec.md](mobix-schema-spec.md), rule 4).
2. **Export `.mobix` → `.xopp` (Solution A: Zero-friction in-app export)**:
   - Available directly from MobiXournal's overflow menu / Save As dialog on both Android and Desktop.
   - `PlotElement` is compiled by `MultiFunctionPlotter` into standard `<stroke>` paths and `<text>` labels.
   - `TableElement` is compiled into border strokes and text elements.
   - `ParametricShapeElement` and `CircuitElement` are compiled into strokes (and their text, for a
     labelled component); a `Stroke`'s tilt/azimuth survive as its **computed** per-point width, and
     `dtMs`/`tilt`/`azimuth` themselves are not representable — see the lossy-field rule below.
   - **Lossless metadata tag**: The `.xopp` writer injects an application metadata block carrying the
     living elements so the export can be *re-imported* without the semantics being lost.
   - **The synthesized `.xopp` is a real document, not a rendering**: it opens, prints and edits in
     desktop Xournal++ like any other file, because every element is emitted as ordinary strokes and
     text — the metadata is additive.

### The bridge metadata block, and its two hazards

The living elements are carried in a metadata block, not in the drawn geometry:

```xml
<!-- mobix:meta {"version": 1, "doc": "f47ac10b-…", "elements": [ … ]} -->
```

Two things about that line are traps, and the implementation has to handle both:

- **XML comments cannot contain `--`.** A comment is `<!-- … -->` with no `--` inside, so a metadata
  block holding a formula, a Unicode label or a JSON string with a double hyphen would produce a
  document Xournal++ refuses to parse. The encoding therefore escapes `--` (and a trailing `-`)
  before it goes into the comment, and the reader unescapes on the way back — the block is a *payload*,
  not something to be read as-is.
- **A round trip through the desktop may drop it.** Xournal++ ignores XML comments and does not
  promise to write them back: save the file there and the metadata is gone, or stale. So the block is
  treated as a **claim to be verified, never as truth**: each living element records the SHA-256 of
  the strokes it was synthesized into. On re-import, the metadata is honoured only for the elements
  whose referenced strokes are still present, byte-identical and unmodified; anything else is
  discarded and the flattened geometry is read as ordinary ink — the drawing the user actually sees
  is never reinterpreted behind their back.

That is what "lossless" means for this bridge, and it is deliberately narrower than "bit-identical":
geometry is always preserved exactly (it is compiled into real elements), and semantics are preserved
as long as nothing downstream edited them. A file that has been edited in Xournal++ opens correctly —
as a drawing, which is what it now is.

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

Migrating to the `.mobix` format directly enables four major performance optimizations in the engine:

1. **Page Virtualization & LRU Streaming (Instant open on huge notebooks)**:
   - Rather than loading a 200-page XML document into an in-memory DOM, the app initialises an
     active sliding window (e.g. current page $\pm 2$ adjacent pages).
   - Pages outside the viewport are evicted from RAM and re-read from the ZIP container in $< 5\text{ ms}$
     when scrolled into view.
   - This is a property of the container's part-per-page layout; the same reader serves the
     infinite-canvas chunk mode ([infinite-canvas.md](infinite-canvas.md)) with chunks in place of pages.
2. **Incremental Delta-Writes (Zero save latency)**:
   - During auto-save or explicit save, the app only re-compresses modified page entries (`page-N.json`)
     into the ZIP archive. Unmodified pages, heavy background PDFs, and image assets are left untouched —
     which is also why an unmodelled entry has to survive a rewrite (container rule 4,
     [mobix-schema-spec.md](mobix-schema-spec.md)).
   - Saves complete in milliseconds without UI freezes or battery drain.
3. **Zero-Allocation Inking Loop (No GC stutter)**:
   - High-rate stylus events (120 Hz–240 Hz on modern tablets and stylus digitizers) can trigger frequent
     JVM Garbage Collection pauses if objects (`StrokePoint`, temporary event wrappers) are allocated per touch.
   - Point buffers are pre-allocated and pooled. The dynamic stroke is drawn directly from primitive arrays
     until touch-up commits the element.
4. **GPU Texture Layering & Path Tessellation Cache**:
   - Static elements (paper rulings, PDF page backgrounds, committed lower layers) are rendered once into
     an off-screen GPU texture (`android.graphics.Bitmap` hardware texture on Android, Skia `Image` on Desktop).
   - In-flight strokes are rendered solely on a lightweight transparent overlay, keeping per-frame redraw times
     under $4\text{ ms}$ (comfortably achieving 144 Hz / 240 Hz displays).
   - Vector paths for completed strokes are tessellated and cached in GPU memory; zooming and panning reuse
     GPU transformation matrices without re-rasterizing vector math.
