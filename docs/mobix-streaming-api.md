# The `.mobix` streaming API — the contract a second shell opens documents with

> Read when you call `mobix-core` from outside the engine: opening a `.mobix`, reading pages on demand,
> writing one back, or merging two. The engine's design is [`mobix-format.md`](mobix-format.md); the
> repository layout is [`codebase.md`](codebase.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

The engine lived inside the Android app, so its "API" was whatever `io/` happened to call — and a
desktop shell could not open a `.mobix` at all. This spoke owns the **published contract** that changed
that: the small set of types `mobix-core` promises, and the boundary that keeps the rest a
implementation detail. A shell implements against the table below, not against the package.

## The four verbs

| You want to | Call | You get |
|---|---|---|
| open a file without reading it | `MobixEntrySource.fromFile(file)` then `MobixPagedReader(source)` | the manifest, the canvas, and the page count — from the crate's own index, no page decoded |
| show page *N* | `reader.getPage(n)` (or `reader.ensureWindow(n, radius)`) | a `Page` with ink, living entities and its bundled payloads resolved; the previous pages stay cached |
| read a payload | `reader.assetSource` | bytes for a still, a **stream** for a recording |
| write the document back | `DocumentEncoder.write(document, SaveFormat.MOBIX, target, out, appVersion, shape)` | the file itself, in any of the three containers — the app calls this too, so one writer serves both shells |
| write it *as an archive* | `MobixDocument.open(...)` → `encode(...)`, or `MobixIncrementalSave.saveOver(...)` | the archive's entries, or a repack that copies unchanged parts and rebuilds only the dirty ones |

`PageSource.of(reader)` is the same reader seen as the surface the editor carries (count, id, page,
window); `PageSource.of(document)` gives the eager read the same shape, so a view is written once for
both and tests can drive the paged path. `MobixMergeEngine` and `MobixConflictDetector` are the sync
verbs — three-way page merge and its conflicts — and are public for the same reason: a second shell
that syncs must not reimplement them.

**`PageWindow` is part of the contract too**, because a shell that can *edit* needs the other half of
streaming: a bounded read cache over a `PageSource` plus an overlay of edited pages eviction never
touches. `page(i)` answers an edit if there is one and materialises the page otherwise, `edit(i) { … }`
returns the page it produced and keeps it, `set`/`revert` are the undo pair, `pin`/`unpin` hold a page for
a live gesture, and `applyTo`/`followDocument` keep the overlay and the document in step. It costs one
page however long the document is, which is what lets the desktop window edit a 200-page notebook
without opening it; `DesktopDocument.window` is one per tab, and the tablet's canvas holds its own. Its
gates are `PageWindowTest` (app module) and `DesktopEditingTest` (desktop module, through the published
type).

## What the contract promises

- **Opening is O(1) in pages.** `MobixPagedReader` reads the mimetype, `manifest.json` and `canvas.json`
  and stops. A page is inflated the first time it is asked for, kept in an LRU of `cacheBudget` pages,
  and evictable explicitly (`evict`, `clearCache`). `DesktopMobixOpenTest` pins this from **another
  module**: 40 pages open with zero page entries read.
- **A page carries the manifest's identity.** The id lives in `pages[]`/`chunks[]` and is not repeated
  in the page JSON; the reader attaches it, so `page.id` is stable across a save.
- **A chunk is a page.** `parts` is `pages[]` or `chunks[]` by canvas mode and its coordinates are read
  out of the entry name, so a board comes back at its own place instead of at its index.
- **Assets are addressed by content.** `MobixAssetSink` names a payload by the SHA-256 of its own bytes,
  so a picture pasted ten times is stored once and no name can go stale. `MobixAssetSource` is the read
  side, and it streams: a 160 MB recording is copied into the archive without being held.
- **Unknown entries survive.** A full write emits every entry it was given, in order, so a document from
  a newer build keeps the parts this build does not model.
- **A save can be a repack.** `MobixIncrementalSave.saveOver` copies an unchanged page or chunk **as the
  archive stores it** and rebuilds only what the document changed, reporting the split
  (`streamedEntries`, `rewrittenEntries`). The raw-copy channel is internal — see below.

## What stays internal, and why

Publishing the engine would have frozen implementation details as API. Three groups were deliberately
left out, and each has a genuine reason rather than a lazy one:

1. **The ZIP internals.** `MobixZip` and its `Index` (offsets into the source file, the central
   directory) are how a repack finds an entry, and they stay private. What *is* public is the narrow
   result: `MobixEntrySource.rawEntry` hands back a `MobixRawEntry` — the stored bytes and the three ZIP
   fields a verbatim write repeats — and nothing else. That shape was chosen by a failure worth
   recording: the first cut kept the channel internal and reached it through an `as? RawEntrySource`
   cast, so a source that *wrapped* another (the counting fixture) silently answered `null` and turned
   every repack through it back into a full rewrite — the test that asserted "copied, not inflated"
   caught it. Putting the method on the interface makes the question part of the type, and the KDoc
   states the rule: a decorator must forward it.
2. **The per-family element codecs** (`MobixTableCodec`, `MobixPlotCodec`, `MobixShapeCodec`,
   `MobixCircuitCodec`) and the JSON builder (`MobixJson`). A shell encodes an element through
   `MobixElementCodec`, which dispatches to the right family; the families are how that dispatch is
   written, not separate things to call.
3. **Test instrumentation.** `CountingEntrySource` lives in `testFixtures`, not in the library: it
   counts inflations and verbatim copies so the suites can prove laziness and cheap repacks, which is no
   production concern.

`MobixDocument` (whole-archive read/write) and `MobixPageCodec`/`MobixElementCodec` *are* public — the
first because a small document is cheaper read whole than paged, the others because writing a document
means writing its pages. `MobixSavePlan` is public for the same reason: it is what a full write produces
and what an incremental one consumes.

## Proving the boundary

`mobix-core`'s own suites run inside the module and can see `internal`, so they cannot answer "is this
usable from outside?". `desktop/src/test/java/com/mobixournal/desktop/DesktopMobixOpenTest.kt` does: it
compiles against `mobix-core` as an ordinary consumer and opens a 40-page notebook with the reader, the
window and the counting source from the test fixtures. If a needed type ever slides back to `internal`,
that suite stops compiling — which is the guard this contract has instead of a doc that goes stale.
