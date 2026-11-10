# The `.mobix` native format: why it exists, and where it sits

> Read when designing, reading, writing or optimizing the native `.mobix` container, or when you need to know why the format exists and what the app's own format policy is. The bridge to desktop Xournal++ is [xopp-bridge.md](xopp-bridge.md)'s.
> For the task: [Installing on Android](guide-install-android.md), [Your first notebook](guide-first-notebook.md), [Which format your notebook is in](guide-formats-and-conversion.md), [The words this guide uses](guide-glossary.md).

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

| Capability | `.xopp` (Current) | `.mobix` (Native) | Where it runs today |
|---|---|---|---|
| **Mathematical plots** | Baked into dead strokes & text boxes | Living `PlotElement`: formula $f(x)$, range, samples, re-editable anytime | **live** — created, drawn, re-edited in place ([mobix-elements.md](mobix-elements.md)) |
| **Data tables** | Static ink lines & independent text | Living `TableElement`: editable rows, cols, alignments, cell text | **live** — the table tool places a living table and its fixed cell editor writes rows, headers, alignment and cell text as one undo step ([mobix-living-entities.md](mobix-living-entities.md)) |
| **Stylus telemetry** | $(x, y, \text{pressure})$ only | $(x, y, \text{pressure}, \text{tilt}, \text{azimuth}, \Delta t_{\text{ms}})$ — no barrel roll, which the platform cannot report | **stored, and read by two features** — tilt, azimuth and $\Delta t$ are captured and written; Pen diagnostics panel prints tilt and azimuth ([stylus.md](stylus.md)). $\Delta t$ is read by stroke replay and audio-karaoke sync ([audio.md](audio.md)). None drives drawn width, which follows desktop pressure filter |
| **Stroke replay & dynamics** | Impossible (no timing information) | Real-time replay & physics-driven velocity smoothing via $\Delta t$ timestamps | **live**, exercised on a device — playhead published, frame loop drives canvas with dimmed future ink, progressive stroke growth with glowing tip, and scrubber pop-up seeking ([audio.md](audio.md)). Replay verified on emulator |
| **Large notebooks (100+ pages)** | Monolithic XML; huge heap footprint & slow open | Paged container; instant open, random-access streaming via LRU cache | **live** — every reader goes through the page surface (`PageSource`): counting, ids and layout come from `manifest.json`/`canvas.json`, and a page is inflated only when the canvas, find or the export asks for it, never at open ([data-path.md](data-path.md)) |
| **Binary assets & photos** | Inefficient base64 inside XML tags | Raw WebP/PNG binaries in `assets/` with content-hash deduplication | **live** — bundled by content hash on the `.mobix` save path |
| **Audio annotations** | Sidecar `.wav` files prone to detachment | Bundled Opus/WAV in container, linked to stroke millisecond timestamps | **live**, exercised on a device — Opus recording and playback bundled in container by content hash as `assets/aud_<sha256>.<ext>`, linked to stroke timestamps, verified on emulator ([audio.md](audio.md)) |
| **Document saving** | Entire document re-serialized | Incremental saves: writes only modified `pages/page-N.json` entries | **live**, exercised on a device — saving back to same `.mobix` URI repacks only changed entries, verified on emulator. Multi-page notebooks, SAF `content://` round-trip, and snapshot asset preservation verified live |

This column answers **where the code runs**, in the two wiring words [architecture.md](architecture.md)
defines: **live** for behaviour wired end to end in an app path, **library only** for a tested engine
with no caller. It says nothing about whether a capability can be relied on — that is the support
matrix's question, answered once for the whole product in
[guide-limitations.md](guide-limitations.md). The page used to grade each row in a vocabulary of its
own — four words, two of them "verified" compounds — so a reader had to reconcile two systems; the
column now carries the wiring word alone, and the support grade is stated in the matrix only.

Everything in the *Native* column is what the format is designed to hold, not a claim that it runs.

Its three structural advantages, all of which follow from the container being a ZIP of small parts
rather than one XML document (layout in [mobix-schema-spec.md](mobix-schema-spec.md)). Whether each
*runs* today is the capability table's **Where it runs today** column above, not a claim here:

- **Streaming & fast startup** — **live**: the container is part-per-page, the **open** reads the manifest
  and nothing else, and the canvas, find and the export each read the page they are on rather than the
  document (measured below; [data-path.md](data-path.md)).
- **Deduplication of binary assets** — **live**: an image pasted across pages is stored once in
  `assets/` and referenced by content hash.
- **Atomic saves** — **live**: a save that lands back on the file the document came from repacks over it,
  writing the manifest and the pages that moved, and copying every other entry as the bytes the archive
  already holds ([data-path.md](data-path.md)).

## What an open-and-save does to a v1 file

The compatibility promise is a claim about a *file*, not about a version of the app, so it is measured
against a versioned corpus of real documents instead of argued in prose. `MobixV1FixturesTest` drives
every fixture under `mobix-core/src/test/resources/fixtures/mobix-v1/` through the container, the paged
reader, the document layer, a save to a new file and an incremental save, and
`scripts/make-mobix-fixtures.py` regenerates the corpus deterministically. Each fixture's own README says
which construct it exercises.

| Construct in the file | What opening and saving it does | Fixture |
|---|---|---|
| an archive entry this build does not model | kept, byte for byte, on both save paths | `05-unknown-entry.mobix` |
| an unknown field on `manifest.json`, a page part, `canvas.json` or its `origin` | kept, value and position included | `06-unknown-envelope-fields.mobix` |
| an element `type` this build does not model, and a `.xopp`-carried element | kept whole, as its own JSON | `07-unknown-element-type.mobix` |
| an unknown top-level field on a known element type | kept on the element, written back after the fields this build spells | `08-unknown-top-level-field.mobix` |
| an unknown field inside a known element's sub-object or array item | kept by the codec that owns that object, at that level | `09-unknown-nested-fields.mobix` |
| an unknown vocabulary value on a geometry element (`tool`, `lineStyle`) | the element is preserved whole and the page still opens | `11-unknown-vocabulary-values.mobix` |
| an unknown vocabulary value inside a living family (`align`, `kind`) | the element is preserved whole | `11-unknown-vocabulary-values.mobix` |
| an unknown background `type` | drawn as the plain sheet it falls back to, written back field for field including its `type` | `12-unknown-background-type.mobix` |
| a `pages[]` order that differs from the entry names | kept — a save renumbers the entries by position | `02-multi-page-order.mobix` |
| a chunked document | read page by page and written back as chunks at their own coordinates | `10-chunked-document.mobix` |

No row drops anything: a construct this build cannot draw is still carried, which is the difference
between an unsupported paper and a lost one. The retention rule, with the per-family inventory, is
[mobix-elements.md](mobix-elements.md)'s; what a reader may do with a file whose declared requirements
it cannot meet is the capability contract in [mobix-schema-spec.md](mobix-schema-spec.md).

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

### What these measurements record, and what they leave out

A number is reusable only if its conditions travel with it. This is what each measurement on this page can
and cannot tell you, stated rather than implied:

| Measurement | Machine | Environment recorded | Repetitions | Method | Not recorded |
|---|---|---|---|---|---|
| `.mobix` vs `.xopp` size and write/read time | a development host, JVM | none | median of 5 | bytes on disk and wall time around `DocumentIo.encode` | CPU, OS, JDK version, heap |
| Paged open, 200-page archive | a development host, JVM | none | none | entries inflated per open, three paths compared | CPU, OS, JDK version, repeat count |
| Incremental save, 200-page notebook | a development host, JVM | none | none | milliseconds and pages opened, against the replaced path | CPU, OS, JDK version, repeat count |
| *Document saving* and *Audio annotations* rows | Android emulator (`Pixel_Tablet` AVD) | the AVD image, not a device | none | a manual save/round-trip pass | device hardware, Android build, thermal state, repeat count |
| "Under 4 ms per frame" and "under 5 ms a page" | unstated | none | none | no benchmark is named for either figure | everything above |

Host numbers and emulator numbers are not comparable: an emulator's CPU and I/O are the host's, shared with
whatever else is running, so a figure from it is a functional check rather than a performance claim. Where a
row says *not recorded*, nothing in the repository was found to reconstruct it — the honest reading is
"measured once, conditions unknown", not a reproducible benchmark.

That table audits the figures already here. The rules a new measurement follows — which fields its report
must carry, how it is warmed and aggregated, and when two runs may be compared at all — are
[benchmarks.md](benchmarks.md)'s, and every report now opens with the conditions it ran under.

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
2. **New documents are born in the default format**, which the user picks **on first launch** — the
   tablet in its tour ([chrome.md](chrome.md)), the window in a card of its own — and can change under
   Settings → App & data on either shell. A PDF opened from outside is a new document, so it lands in
   this format too, never in `.xopp` behind the reader's back. Every document the app creates is paged,
   in either format. The recommended format — **`.mobix`** — keeps living elements, telemetry, streaming.
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

## What a reader must refuse

An archive is untrusted input: a notebook can be downloaded, synced or handed over by someone else, and
a reader that allocates whatever a file asks for turns a 20 kB download into a crash. The audit behind
this table covered `MobixContainer.open` (eager), `MobixPagedReader` (lazy), `mimetypeOf`, the JSON
parser, `MobixZipRewrite`/`MobixIncrementalSave` and the asset path, and it found that the **writer** is
bounded while the reader is not:

| Threat | What happens today | State |
|---|---|---|
| a manifest declaring thousands of parts, or one entry inflating to gigabytes | one cap policy (`MobixSafetyCaps`) bounds the archive at 20 000 entries, 256 MiB per entry, 1 GiB of payload in total and 1000× expansion per entry (deflate's own ceiling is ~1032:1), all checked while reading; `mimetypeOf` reads its probe under a 256-byte cap | enforced |
| a page whose JSON nests deeply | `JsonParser` refuses nesting past 64 levels, and every document boundary turns that into a malformed document instead of a stack overflow | enforced |
| duplicate or ambiguous entry names | `open` refuses a duplicate, a `.`/`..` segment, an absolute path, a backslash and a control character, naming the entry | enforced |
| `../`, absolute or backslashed entry names | never extracted by this reader, and now refused at open rather than carried, so a tool that unpacked the archive cannot inherit one | enforced |
| a page with a million elements, or a stroke with a million points | 50 000 elements a page and 500 000 points a stroke, counted from the JSON before one model object is built; a document's assets add up to at most 1 GiB | enforced |
| a part whose bytes disagree with `pages[].sha256` | a page, chunk or asset is hashed when it is materialised (never when the archive is listed) and refused when it disagrees with the manifest's record or with the digest its own name claims; SHA-256 measures ~0.8 ms per MiB here, a fraction of the inflation it follows | enforced |
| an encrypted entry, an unsupported method, a bad CRC, a truncated archive | the JDK's ZIP and end-of-file exceptions are caught at the container boundary and reported as a malformed document, with the original kept as the cause | enforced |
| code execution or a network fetch driven by content | nothing in a document is evaluated, and an `attrs` reference is a string the app resolves against its own stores | hardened |
| a document too large to write | `ClassicZipLimits` refuses the incremental writer's ZIP64 boundaries, and a save that would cross them fails instead of writing a broken archive | hardened (writer) |

The policy the gaps are closed against: **a motivated cap for each**, not a blanket size limit, because
a legitimate 200-page notebook with recordings is large by design. Entry count, bytes per entry, bytes
per archive, expansion ratio, JSON nesting depth, elements per page, points per stroke and cumulative
asset bytes each get a threshold with the document that motivated it; a hostile file is **rejected
explicitly** (a malformed-document error naming the entry and the rule), never partially read, and a
rejected document is never written over in a degraded form. Every row above has a committed file behind it in
`mobix-core/src/test/resources/fixtures/mobix-hostile/`, refused for its own reason in bounded time and
allocation; the row-by-row checklist a third-party reader runs is
[`spec/CONFORMANCE.md`](../spec/CONFORMANCE.md).

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
   - It is a property of the container's part-per-page layout; the same reader serves a chunked document
     ([chunked-documents.md](chunked-documents.md)) with chunks in place of pages.
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
   The raw incremental writer rejects ZIP64 sentinel sizes/offsets and entry-count overflow before
   publication (`ClassicZipLimitsTest`). A save that exceeds these classic ZIP limits fails safely;
   Save As to a new file uses the standard writer instead. The raw path does not emit ZIP64.

3. **GPU Texture Layering & Path Tessellation Cache** — **live (off-screen raster)**:
   - Static elements (paper rulings, PDF page backgrounds, committed lower layers) are rendered once into
     an off-screen GPU texture (`android.graphics.Bitmap` hardware texture on Android, Skia `Image` on Desktop).
     This is what `render/InkCache.kt` does (one bitmap per page at a zoom bucket, charged to a shared
     `BitmapBudget` beside `render/BitmapLruCache.kt` and `render/PdfPageCache`).
   - In-flight strokes are rendered solely on a lightweight transparent overlay, keeping per-frame redraw times
     under $4\text{ ms}$ (comfortably achieving 144 Hz / 240 Hz displays).
   - Vector paths for completed strokes are cached for reuse across zoom and pan; the tessellation cache
     named here is the platform `Canvas` path cache, not an app-owned one.
