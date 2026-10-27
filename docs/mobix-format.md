# The `.mobix` native format: why it exists, and where it sits

> Read when designing, reading, writing or optimizing the native `.mobix` container, or when you need to know why the format exists and what the app's own format policy is. The bridge to desktop Xournal++ is [xopp-bridge.md](xopp-bridge.md)'s.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

**Where this file's content lives:** the container layout, `manifest.json` and the element schema are
owned by [mobix-schema-spec.md](mobix-schema-spec.md) — this file owns *why* the format exists, the
app-level format policy around it, and the engine properties its layout unlocks. Don't re-draw the
container tree here, and don't restate the bridge ([xopp-bridge.md](xopp-bridge.md)).

## Motivation: why evolve beyond `.xopp`

MobiXournal began as a strict companion to Xournal++ on Linux, storing everything in `.xopp`
(gzip-compressed XML, documented in [xopp-format.md](xopp-format.md)). That is the origin, not the
current shape: `.mobix` is where a document lives now, and `.xopp` is the interchange format whose
lossless round trip is a standing contract ([xopp-bridge.md](xopp-bridge.md)). The reason for the
native format is that `.xopp` imposes hard architectural bottlenecks:

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
| **Data tables** | Static ink lines & independent text | Living `TableElement`: editable rows, cols, alignments, cell text | **Model only** — codec and ink are live; the table tool still draws a static grid and no editor exists |
| **Stylus telemetry** | $(x, y, \text{pressure})$ only | $(x, y, \text{pressure}, \text{tilt}, \text{azimuth}, \Delta t_{\text{ms}})$ — no barrel roll, which the platform cannot report | **Stored & verified** — tilt, azimuth and $\Delta t$ are captured and written; Pen diagnostics panel prints tilt and azimuth ([stylus.md](stylus.md)). $\Delta t$ is read by stroke replay and audio-karaoke sync ([audio-inking-sync.md](audio-inking-sync.md)). None drives drawn width, which follows desktop pressure filter |
| **Stroke replay & dynamics** | Impossible (no timing information) | Real-time replay & physics-driven velocity smoothing via $\Delta t$ timestamps | **Live & verified** — playhead published, frame loop drives canvas with dimmed future ink, progressive stroke growth with glowing tip, and scrubber pop-up seeking ([audio-inking-sync.md](audio-inking-sync.md)). Replay verified on emulator |
| **Large notebooks (100+ pages)** | Monolithic XML; huge heap footprint & slow open | Paged container; instant open, random-access streaming via LRU cache | **Live** — every reader goes through the page surface (`PageSource`): counting, ids and layout come from `manifest.json`/`canvas.json`, and a page is inflated only when the canvas, find or the export asks for it, never at open ([data-path.md](data-path.md)) |
| **Binary assets & photos** | Inefficient base64 inside XML tags | Raw WebP/PNG binaries in `assets/` with content-hash deduplication | **Live** — bundled by content hash on the `.mobix` save path |
| **Audio annotations** | Sidecar `.wav` files prone to detachment | Bundled Opus/WAV in container, linked to stroke millisecond timestamps | **Live & verified** — Opus recording and playback bundled in container by content hash as `assets/aud_<sha256>.<ext>`, linked to stroke timestamps, verified on emulator ([audio-inking-sync.md](audio-inking-sync.md)) |
| **Document saving** | Entire document re-serialized | Incremental saves: writes only modified `pages/page-N.json` entries | **Live & verified** — saving back to same `.mobix` URI repacks only changed entries, verified on emulator. Multi-page notebooks, SAF `content://` round-trip, and snapshot asset preservation verified live |

The **Status today** column is the honest one: a capability is **Live** only where the open/save path
actually runs it, **Library only** where a tested engine exists with no caller in `app/src/main`, and
otherwise names what is missing and the task that will make it live. Everything in the *Native* column
is what the format is designed to hold, not a claim that it runs.

Its three structural advantages, all of which follow from the container being a ZIP of small parts
rather than one XML document (layout in [mobix-schema-spec.md](mobix-schema-spec.md)). Whether each
*runs* today is the capability table's **Status today** column above, not a claim here:

- **Streaming & fast startup** — **live**: the container is part-per-page, the **open** reads the manifest
  and nothing else, and the canvas, find and the export each read the page they are on rather than the
  document (measured below; [data-path.md](data-path.md)).
- **Deduplication of binary assets** — **live**: an image pasted across pages is stored once in
  `assets/` and referenced by content hash.
- **Atomic saves** — **live**: a save that lands back on the file the document came from repacks over it,
  writing the manifest and the pages that moved, and copying every other entry as the bytes the archive
  already holds ([data-path.md](data-path.md)).

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
   the bridge ([xopp-bridge.md](xopp-bridge.md)) has to be lossless in *both* directions: a `.xopp`
   that went through a phone is still a `.xopp`.
2. **New documents are born in the default format**, which the user picks **on first launch** (the
   onboarding flow, [chrome.md](chrome.md)) and can change under Settings → App & data; a `.mobix` is
   born on a default **canvas geometry** too ([infinite-canvas.md](infinite-canvas.md)), a `.xopp`
   always paged. The recommended format — **`.mobix`** — keeps living elements, telemetry, streaming.
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

The bridge in both directions — synthesis and revival, with the metadata block — is [xopp-bridge.md](xopp-bridge.md)'s.

## Engine optimizations enabled by the format

The container's layout is what makes these four engine properties possible. Each one is labelled with
**what actually runs today** — **live**, or **library only** (a tested engine with no caller in
`app/src/main`).

1. **Page Virtualization & LRU Streaming (Instant open on huge notebooks)** — **live**:
   - `format/mobix/MobixPagedReader.kt` walks the ZIP central directory and materialises a page (or a
     chunk) on demand into an active sliding window (e.g. current page $\pm 2$). Its callers are the page
     surface in `format/mobix/PageSource.kt`: the open, the canvas, the merge, find and the export.
   - `io/DocumentIo.kt` opens a `.mobix` through that reader and keeps it open for as long as the session
     holds the document (`prunePagedOpens`), so the editor is handed a document whose pages are read on
     demand — and a page's bundled background is adopted when that page is read
     (`io/BundledPageAssets.kt`), which is what keeps the open free of pages *and* assets.
   - Pages outside the viewport are evicted from RAM and re-read from the ZIP container on demand in
     $< 5\text{ ms}$ when scrolled into view, verified on synthetic 200-page archives in `MobixPagedReaderTest`.
   - **Measured** (`MobixPagedOpenBenchmarkTest`, report in `app/build/reports/mobix-open.txt`): on a
     200-page archive with one 1 MiB asset (204 entries) the whole-archive path opens in 4 ms inflating all
     204, the old streaming open in 3 ms inflating 203, and **the shipped open in 1 ms inflating 3**.
   - The reader, the asset sources and the save are a **published contract** ([mobix-streaming-api.md](mobix-streaming-api.md)),
     not app internals: the desktop shell opens a notebook through them with no Android on its classpath.
   - It is a property of the container's part-per-page layout; the same reader serves the infinite-canvas
     chunk mode ([infinite-canvas.md](infinite-canvas.md)) with chunks in place of pages.
2. **Incremental Delta-Writes (Zero save latency)** — **live** when the save lands back on the file the
   document came from: the manifest and the entries that moved are written, everything else — untouched
   pages, background PDFs, pictures — is copied as the bytes the archive already holds, and a page the
   source still holds is reused as it stands (`MobixPageReuse` answers from the shape of the document's
   page list), so its JSON is never parsed, rebuilt or hashed.
   The diff is derived, never flagged (a part is compared by the `sha256` its own manifest recorded);
   unknown/unmodelled entries survive untouched (container rule 4, [mobix-schema-spec.md](mobix-schema-spec.md)).
   - Measured in `MobixIncrementalSaveBenchmarkTest` (`app/build/reports/mobix-incremental-save.txt`): one
     edited page of a 200-page notebook costs **2 ms and one page opened**, against **13 ms and 399** for
     the path it replaced — constant, not proportional to the notebook. The on-device round trip is run:
     the *Document saving* row above holds what the emulator pass measured.
3. **GPU Texture Layering & Path Tessellation Cache** — **live (off-screen raster)**:
   - Static elements (paper rulings, PDF page backgrounds, committed lower layers) are rendered once into
     an off-screen GPU texture (`android.graphics.Bitmap` hardware texture on Android, Skia `Image` on Desktop).
     This is what `render/InkCache.kt` does (one bitmap per page at a zoom bucket, charged to a shared
     `BitmapBudget` beside `render/BitmapLruCache.kt` and `render/PdfPageCache`).
   - In-flight strokes are rendered solely on a lightweight transparent overlay, keeping per-frame redraw times
     under $4\text{ ms}$ (comfortably achieving 144 Hz / 240 Hz displays).
   - Vector paths for completed strokes are cached for reuse across zoom and pan; the tessellation cache
     named here is the platform `Canvas` path cache, not an app-owned one.
