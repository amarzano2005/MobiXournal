# The data path: open, save, tabs and panes

> Read when you change how a document is opened, saved, staged, cached between launches, or split across two panes.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Data path

The core loop:

```
open (SAF Uri) → UriStaging.stageIn (worker thread) → local staging file
   → FileKind.sniff (gzip / ZIP / PDF / XML / text) → GZIP|ZIP|plain InputStream
       → a ZIP is dispatched on its `mimetype` entry: `.mobix` opens as one, anything else is a
         ZIP-package `.xopp` (see "Which container" below) → XmlPullParser → Document model
     (a raw PDF instead becomes a fresh document via PdfImport.adoptPdf/documentFor)
   → render on SurfaceView (Material 3 chrome around it)
   → stylus edits mutate the model → XmlSerializer (or the .mobix encoder) → GZIP|ZIP OutputStream
     (sticky SaveFormat) → local staging file → UriStaging.stageOut (worker thread) → save (SAF Uri)
```

### Which container: `.mobix` or `.xopp`

The byte sniff reaches "this is a ZIP" and stops there, because a `.mobix` and a ZIP-package `.xopp`
are both ZIPs — so `DocumentIo.read` asks the archive what it *is*
(`MobixContainer.isMobix`, i.e. the `mimetype` entry's content, container rule 2) and opens it the way
that answer says. The verdict also fixes the **sticky SaveFormat**: a document opened as a `.mobix`
saves as one, and one opened as a `.xopp` (gzip or ZIP package) saves as the `.xopp` it was, so
travelling through this app never changes a file's format behind the user's back. Save As is the one
place the format changes, and then the name it proposes carries the new suffix (the picker's declared
type changes with it, since a `.mobix` offered to the system as `application/x-xopp` would invite
desktop Xournal++ to open something it cannot). The `desktop/` window follows the same rule through the same encoder, and its Save As decides by the **name the user types**; how it stages and moves the write is [desktop-workstation.md](desktop-workstation.md)'s.

**The write itself is platform-free** (`io/DocumentEncoder.kt` in `mobix-core`): one `write` holds the three format arms, and what is Android-shaped arrives through two seams — a `SaveTarget` (the name and the reference arithmetic: `UriSaveTarget` on a SAF id, `FileSaveTarget` on a path)
and a `SaveShape` (the container, the board geometry, the archive to carry `assets/` from and the archive to repack over) — while `DocumentIo.encode` keeps its staging file and delegates the bytes.

A `.mobix` is read as its own document, not through the `.xopp` model:

- **Its identity comes with it.** The manifest's `documentId` travels into the model
  (`Document.documentId`) and is written back on the next save, so saving an open document is an edit
  of that document rather than a new one — the difference a sync client or a later merge sees. The ids
  of the pages and elements come with it the same way, and a document that arrives *without* them (a
  `.xopp` has none) **adopts** them before the first `.mobix` write (`format/model/DocumentIdentity.kt`):
  minted into the model rather than into the file, so the file's ids are the model's own and every later
  save writes the same ones. A writer that minted them on a copy would leave the open document
  unidentified, and the next save would hand out a fresh set — which a merge reads as the whole page
  replaced rather than edited. *Which* pages get that treatment is the save's own plan: the ones it will
  write, through one function both sides call (`MobixPageReuse.identified` — below, and
  [mobix-format.md](mobix-format.md)). A page that was *read* carries element ids either way, because the
  `.mobix` decoder mints one for an element that has none (`MobixElementCodec`'s `mint`), so what a save
  has to identify is narrower than it looks: the document's own id, and the page ids the manifest records
  for the pages it rebuilds.
- **Its bundled payloads are adopted a page at a time** (a `.mobix` is self-contained, so the bytes in
  the archive *are* the background — and the recording), when the page that names them is read:
  `io/BundledPageAssets.kt` is a `PageSource` layer over the reader, the `BackgroundResolver` the
  renderer asks afterwards, and the `BundledAudio` sink a recording is written to under the sidecar name
  the stroke carries, so the file plays with no `.wav` beside it ([audio-inking-sync.md](audio-inking-sync.md)).
  The copies are keyed by the reference the document keeps, which is what the renderer looks a picture
  up by, and a background the document only *references* is resolved exactly as a gzip `.xopp`'s is.
  The archive's own PDF is adopted up front, since the model holds **one** background PDF per document;
  a page whose bytes can be reached neither way draws its bare sheet, silently — the report the eager
  walk used to give ("Background PDF not found") would cost that walk back, and is owed its own lazy
  version.
- **An infinite-canvas document travels the same road.** A `.mobix` whose content is `chunks/` opens
  as a document whose pages *are* those chunks, in manifest order, and a save keeps it chunked **at its
  own coordinates** (`MobixOpenSessions` remembers the canvas mode and the grid the open read out of the
  manifest's entry names, and hands both to the write): a chunk is named after where it sits, so a write
  deriving coordinates from the page index would move every chunk of a 2D board to the first column. An
  untouched chunk is copied out of the source archive rather than rebuilt, the chunk half of the reuse
  plan ([mobix-format.md](mobix-format.md), [infinite-canvas.md](infinite-canvas.md)). A document the
  app **creates** is born on the geometry Settings chooses for new documents (paged, or one of the three
  infinite modes), and the mode rides on the tab with the format (`OpenTab.canvasMode`, persisted in
  `TabIndex`), so its first save already writes `chunks/` rather than converting a paginated file later.
  A `.xopp` has no chunk container and is always paged. The session's own record of the mode wins for a
  document it opened, the tab's for one it did not — so neither a stale tab nor a stale session can turn
  a chunked document into a paginated rewrite. The same answer goes to the **canvas**, as a
  `CanvasGeometry` (`io/CanvasGeometry.kt`, resolved by `DocumentIo.canvasGeometryOf` and adopted with
  `setInfiniteCanvas`): the renderer cannot derive a board from its pages — a page does not know which
  cell it sits in — so the layer that read the file is the only one that can say, and a board is then
  drawn as one sheet with its tiles on it ([infinite-canvas.md](infinite-canvas.md)).
- **What it refuses, it refuses loudly.** A `.mobix` whose `formatVersion` is newer, whose
  `canvas.json` disagrees with the manifest, or that carries `pages/` and `chunks/` both fails the open
  with the reason, rather than being opened as something it is not.
- **It streams, one page at a time.** The open reads the envelope and the manifest and hands the editor
  a document whose pages *are* the reader's own view (`io/DocumentIo.kt` → `MobixDocument.openPaged`), so
  `pages.size` answers the manifest's count, `pages[150]` inflates page 150, and every `assets/` payload
  stays in the archive until a page names one or the document is written
  (`format/mobix/MobixEntrySource.kt`); before this the whole archive was read into memory and every page
  decoded (the numbers are in [mobix-format.md](mobix-format.md), *Engine optimizations*, item 1). What
  the editor carries is a **page window**: the surface is `format/mobix/PageSource.kt`
  (`pageCount`, `pageId(i)`, `pageSize(i)`, `page(i)`, `idIndex()`), backed by `MobixPagedReader` for a
  `.mobix`, where counting, id'ing and the **page size** inflate no page (`canvas.json` declares the
  paginated page size), and by the in-memory page list for a `.xopp` or a new document. Its callers are
  the merge engine — which plans each page from the manifest's `sha256` and adopts a page only one side
  changed **by part**, decoding only the pages both sides changed — the `.xopp` export and find, which
  walk the document a page at a time, and **the canvas**
  (`materialise-only-the-visible-pages-in-the-canvas`): a
  `StackedLayout` places every page from `pageSize(i)` and its `PageBox` resolves its page through the
  source only when the box is drawn, so a 200-page `.mobix` is laid out without opening a page and
  materialises exactly the pages in the viewport.

  **A background a page names and does not have is reported when that page is read.** Adoption is lazy,
  so a `.mobix` whose `assets/` entry for a page's picture or PDF is absent cannot be reported at the
  open without walking every page — the walk the paged open exists to avoid. `BundledPageAssets` records
  the reference at the ask that came back empty (`BackgroundResolver.drainMissing`), the surface drains it
  right after the frame that needed the bytes (`reportMissingBackgrounds`, `onMissingBackground`) and the
  host says so **once per reference**, so a page draws its bare sheet with an explanation instead of
  looking like a bug. A document whose backgrounds resolve up front keeps reporting them at the open, in
  the same words (`LoadedFile.Doc.missingPdf`/`missingImage`).

  **Editing** goes through the window too — `mobix-core`'s `render/PageWindow.kt`, shared by both shells
  (`edit-a-page-the-lru-window-has-evicted`): a bounded read cache over the same source plus an overlay
  of edited pages that eviction never touches. A single-page command runs through the shell's
  `windowEditPage` (`DrawingSurfaceView` on the tablet, `DesktopDocument.editPage` on the PC), which
  **materialises the page it touches first** — so a command may act on a page the window has evicted,
  without its caller knowing which pages are resident — and keeps the result in the overlay (so the edit
  is still there when the page leaves the cache, which is what lets undo and redo find it).
  `PageWindow.pin` holds a page for as long as a live gesture keeps re-reading it. A **wholesale replacement** of the document — a load, undo/redo, a mirrored pane, a
  page-list edit — calls `resetPageWindow()`, because the overlay then describes pages the document no
  longer has. Between those two, the document is the truth and the window **follows** it:
  `PageWindow.followDocument` runs from the `DrawingSurfaceView.doc` setter — the one place every edit
  lands — so a page written straight into the model (an element placement, a slider drag, an element
  swap on the selected page) cannot leave the overlay describing the page it replaced, which would let
  the *next* gesture rebuild the page from that stale copy and silently drop the write. The element ops expose a **one-page core** beside their document-wide form —
  `ElementEdits.replaceElementOn` against `replaceElement`, and
  `SelectionOps.deleteOn`/`addToTopLayerOn`/`moveOnto` — so a caller that knows the page (the text
  editor remembers its target's page, a plot's slider edits the selected page) never searches the
  document for the element.

  The reader-backed open has had its on-device pass: the page counter, the page list and the first frame of
  a 200-page notebook were seen on the emulator, and the identity adoption a save needs ran with them. What
  that pass did **not** cover is the `content://` shape — it reached the fixture as a `file://` path in the
  app's own sandbox, and a SAF URI with a persisted read grant is a different source for the adoption's
  source-matching branch to compare, and one no JVM test can build (`open-and-save-a-mobix-through-a-content-uri`).
  The save writes the pages that moved, and **the editor's identity adoption is the save's own answer**:
  `MainActivityDocuments` asks what this write will build (`DocumentIo.identifyForSave`) and
  `DrawingSurfaceView.adoptIdentity` applies it, so a two-hundred-page notebook is no longer opened to be
  told its pages already have ids.

### Staging: remote (SSHFS/FTP/cloud) documents

The picker lists files on mounted network shares, and they arrive as ordinary `content://` URIs
from a remote `DocumentsProvider` — the only difference is **latency**. So every document transfer
is staged through a local file by `io/UriStaging.kt` — driven by `io/DocumentIo.kt`, which owns the
whole document-I/O policy (staging, both background-PDF stores, and the sniff/read, encode and merge
steps) so the activity is left with intent plumbing — and run off the UI thread by
`MainActivity.inBackground`, which shows the editor's blocking "Opening…/Saving…" overlay:

- **Read** — the bytes come down once into a staging file; sniff/parse/rasterise then work against
  a local file that can be re-read at will. A failed fetch closes the half-built tab and reports it.
- **Write** — the document is serialised locally *first* and only then pushed out in one pass, so a
  link that drops mid-encode can't leave a truncated `.xopp` on the far end.

Every staging file is allocated by `io/ScratchDir.kt` under a name **no other transfer reuses**, and
deleted by its caller once read. Since transfers run on worker threads, two of them overlap easily;
the fixed `open.tmp` this replaced meant a second, slower download overwrote the first document's
bytes before they were parsed, and both tabs came up holding the same document.

A document's **background PDF** gets its own file, allocated by `io/PdfStore.kt`. Whether it came
out of a ZIP package (`XoppZip.open`), was resolved from a `pdf` background reference
(`DocumentIo.resolvePdfBackground`) or was imported (`DocumentIo.adoptPdf`), the bytes land under a name no
other document uses, and that name is what `OpenTab.pdfPath` records. This is a correctness
requirement, not housekeeping: `PdfPageCache` rasterises through a file descriptor it holds open for
as long as the document is on a canvas, so a shared fixed name (the old `background.pdf`) let the
next document opened — in the other split pane, or another tab — overwrite the bytes underneath a
live renderer, and every page not yet rasterised came back blank. Since the files are never
rewritten, two views of one document (a mirrored tab) can share a path safely. As defence in depth
`PdfPageCache.checkSource` stamps the file's size and mtime when it opens the renderer and re-checks
on every request, re-opening (and dropping the whole cache) if the bytes were replaced anyway, and
closing itself — so the page draws with no background rather than white — if the replacement can't
be opened as a PDF. Unique names would
otherwise accumulate, so `MainActivity.prunePdfCache` sweeps the store against the paths the open
tabs *and* the live surfaces still reference, on every session persist and tab close.

The open picker asks for a **persistable read+write** grant (`OpenDocumentForEditing`), so plain
Save writes back to the tab's own `OpenTab.uri` (`MainActivity.saveActiveTab`) instead of asking for
a location, and a restored tab can still reach its file after a restart. The `CreateDocument` path
takes the same grant after a successful Save As.

Reading and writing are **streaming and symmetric**: the parser builds the model element by
element; the serializer walks the model in document order and re-emits it, carrying through any
preserved-but-unrendered attributes so the file round-trips.

**A `.mobix` save repacks over the file it was opened from.** `DocumentIo` remembers each open
`.mobix`'s staged copy by document id, and a save that lands back on that same URI goes through
`MobixIncrementalSave.saveOver`: an entry whose bytes did not change is **copied verbatim** — the
compressed bytes the archive already holds, not a second deflation of the same content — and only the
manifest, the source record and the pages that moved are written again. `MobixZip` is what makes that
possible: it reads an entry's offset out of the central directory (the only record that states it, and
the one that still has the sizes when a local header left them zero) and writes the local headers, the
central directory and the end record itself, because `java.util.zip` will neither hand out a compressed
entry nor accept one it did not produce. It reads that directory **once per file** (`MobixZip.Index`):
one directory read per copied entry made a save quadratic, which the benchmark below caught at half a second on 200 pages.

**A page the save is not going to write is a page it never opens.** That is the second half, and it is
where the cost actually comes from: rebuilding a page's JSON to *hash* it costs the page its JSON, its
hash and its decode. `format/mobix/MobixPageReuse.kt` answers first, from the shape of the document's
page list — a `.mobix` held page by page keeps the reader's own list with the edited pages overlaid, so
a page an edit did not replace **is** the source's page at that index: it has an entry the manifest
names and a `sha256` the manifest recorded, and the save copies the one and writes the other without
building anything. Identity follows the same rule, one step earlier, and through **one** function
(`MobixPageReuse.identified`): a save adopts ids over the pages it will *write* and over nothing else,
because a copied page's ids came with the file and asking it for one would open it. The write takes that
answer on the copy it encodes (`MobixDocument.of`) and the editor takes the same answer on the model it
is about to snapshot (`DocumentIo.identifyForSave`, from `MainActivityDocuments`), which is what keeps
the open document and the file from disagreeing about which page needed an id. The plan is
`MobixSavePlan` — the entries to write, plus the order the whole file goes in, reused pages included.

Nothing that decides what to write is a flag. A part is compared by the `sha256` its own manifest
records, an asset by the hash in its name, the envelope byte for byte, and a copied page by the shape of
the list it is in; a recording is bundled the way a picture is (the bytes the stroke's sidecar name
resolves to in the audio store, so one entry covers every stroke that replays from it). Anywhere else —
a new document, a first save, a Save As to another target — there is no source to repack over and the
whole archive is built from the model. The payloads such a document still **references** travel with it
anyway: the model keeps the names it was read with (`Background.Pdf.bundledAsset`,
`Background.Pixmap.bundledAsset`, `Stroke.bundledAudio`), and `carriedPayloads` (in `mobix-core`) reads those
`assets/` entries back out of the staged copy the session still holds and hands them to the writer as
payloads — so a picture or a recording that no page has ever adopted (a page that was never read) is
not lost to a Save As. A document whose staged copy the session no longer holds has nothing left to
read, which is the one case a payload can still be missing; the page that needs it is what
`report-a-mobix-page-whose-background-is-missing` exists to report.

**Measured** (`MobixIncrementalSaveBenchmarkTest`, report in
`app/build/reports/mobix-incremental-save.txt`): on a two-hundred-page notebook with one edited page, the
save takes **2 ms and opens one page** — the first, which states the document's own paper and page size —
against **13 ms and 399 page openings** for the build-everything path it replaced, and the file it leaves
is the source's own bytes for every page nothing touched. On ten pages with one edited (`MobixIncrementalSaveTest`,
`MobixIncrementalSavePlanTest`): 12 of the 14 entries are copied, 2363 B of content is re-encoded instead
of 4928 B, and the finished file is 4862 B against a full write's 4926 B — smaller, since a copied entry
needs no data descriptor. A background PDF is never inflated or deflated at all.

### Tabs and the session cache

Several documents can be open at once, but there is only **one** `DrawingSurfaceView`. A tab switch
is therefore a swap, driven by `MainActivity`:

```
snapshot the surface into the outgoing OpenTab (document, save format, canvas geometry, PDF path, page)
  → TabManager.select(index)
  → load the incoming OpenTab into the surface (setPdfSource → load → goToPage)
```

Consequences worth knowing:

- **Undo history is per surface, not per document.** It is cleared by `load`, so a tab switch starts
  the incoming document with a clean history. Content, including unsaved edits, is untouched.
- **Only the active tab's document is live**; every other tab holds the snapshot taken when it was
  last showing. That is also what gets written to disk.
- **The session is cached, not saved.** `TabStore` writes `filesDir/tabs/`: a `session.index` line
  file (`TabIndex`) plus one snapshot per tab, rewritten on every tab change and in `onPause`. On
  launch it is read back, so the app reopens on the same tabs with the same unsaved edits. It is a
  restart cache keyed by tab id — the user's own file (the tab's `uri`) is still the only thing the
  desktop ever sees, and a snapshot never stands in for saving.
- **A snapshot is written in the tab's own container.** `<id>.mobix` for a document whose format is
  the native one, `<id>.xopp` (gzip XML) for every other tab, which is what `TabSnapshots` decides and
  `TabStore` names files by. It has to be that way round: a `.xopp` has nowhere to put a bundled
  asset, so writing one for a `.mobix` document drops a page's `Background.bundledAsset` and a
  stroke's `bundledAudio` at the snapshot — `hydrate` then rebuilds the model without those
  references, and a save from the restored tab writes the user's own file with no `assets/` entry. The
  reference dies at the snapshot, so nothing downstream can recover the bytes; the emulator pass that
  found it saw a bare page 2 and `ImageBackgroundCache: cannot decode pixmap background` in logcat.
  The native write is the save's own, minus the target (`MobixTabSnapshots` → `DocumentIo.encode`), so
  a snapshot carries what a Save As would and a restored document even remembers its snapshot as the
  archive to carry payloads *out of* on the next save. An install that upgraded still reads the
  `.xopp` its `.mobix` tab was left with, and the next write gives it the native shape — the sweep
  keys on the tab id, not the suffix, precisely so the two can never both be left behind.
- **Nothing is parsed or written on the main thread.** Gzip XML over a whole document takes long
  enough to trip Android's ANR watchdog, so the session is restored **lazily and off-thread**:
  `TabStore.load` reads only the small index and returns every tab as a placeholder
  (`OpenTab.hydrated = false`); `TabStore.hydrate` parses one tab's snapshot, and `MainActivity.show`
  runs it on a worker and paints the canvas when it lands, so a cold start with many tabs never waits
  on more than the index. Writing is the mirror image: `EditorPane.persist` reads the session on the
  caller's thread and queues the save onto a single-threaded writer, with `awaitPersist` in `onPause`
  so a backgrounded app still lands its snapshot. Four rules keep a placeholder from destroying
  content: `TabStore.save` skips unhydrated tabs (their file on disk is the truth); it also skips a
  tab that is still **`OpenTab.opening`** — a slow remote/cloud fetch whose placeholder document is
  on the strip while the canvas still shows the *previous* tab; `snapshotActiveTab` skips a tab whose
  parse is still in flight, for the same reason; and `TabStore.hydrate` leaves a tab **unhydrated**
  when its snapshot exists but cannot be parsed, so the unreadable bytes are never overwritten by the
  blank stand-in. Writing is atomic for the same end — a snapshot lands through a `.tmp` sibling and a
  rename, so a kill mid-write can't leave a truncated file that the next launch would fail to read.
  Handing an unhydrated tab to the other pane copies the snapshot *file* (`TabStore.adopt`) rather
  than parsing it.
- Tabs that were opened from a file keep that `content://` URI, so **Save** after a restart still
  writes back to the same document.
- **The tab overview draws previews from the same snapshots.** The top bar's grid button
  (`ui/TabOverviewPopup.kt`) shows one card per tab, each the page that tab was left on, rasterised by
  `render/PageThumbnail.kt` — `BackgroundRenderer` + `PageRenderer` into a small bitmap, so a preview
  is drawn by the same code as the canvas. `pdf`/`pixmap` backgrounds are drawn as their plain sheet
  only: those images come from asynchronous caches owned by a live surface. Opening the grid
  snapshots the active tab first (`TabsUiState.onOverview`), then requests each preview through
  `MainActivity.previewTabPage`, which hydrates (without writing back into the session) and
  rasterises on **one** shared worker — a thread per tab put every open document in memory at once and
  was an out-of-memory kill on a session of large files.

### Split view: the same thing, twice

Split view is modelled as **panes**, not as a second mode. An `EditorPane` (`panes/EditorPane.kt`)
bundles everything that used to be a per-activity singleton — the canvas, a `TabManager`, the sticky
`SaveFormat`, the pending save name and its own `TabStore` directory. `MainActivity` holds a fixed
list of two of them plus an `activePane` index; `surface`, `tabs`, `saveFormat` and `pendingSaveName`
are now *accessors* onto the pane in focus, which is why the open/save/import/audio code is still
written against "the" document and needed no changes.

The design decisions worth keeping:

- **One pane has focus; the chrome drives that pane.** A touch anywhere in a pane (observed on the
  pointer-input *initial* pass, so the canvas still receives the event) makes it active. There is no
  second toolbar and no per-pane menu — the single top bar and rail always act on the focused pane.
  The tabs are the exception that makes that ownership visible: in split view each pane's strip is its
  own lane over its own half (`SplitTabRow`), the focused pane's numbered badge wears the accent, and
  a touch anywhere in a lane — a chip, its "+", even its empty space — focuses that pane. Selecting a
  tab, adding one or closing one also focuses the pane it happened in, and a *move* to the other pane
  hands focus to the destination; a mirror leaves it where it was.
- **Each pane persists separately.** `TABS_DIRS` gives pane 0 the historical `filesDir/tabs` and pane
  1 `filesDir/tabs-right`, so an existing session still restores and the right-hand pane's documents
  survive both a split-view toggle and a restart. `onPause` snapshots and writes *both*.
- **A pane's Compose mirror is per pane too.** `ui/PaneState.kt` holds the zoom/page/layer/undo state
  a canvas pushes up through its callbacks; `EditorUiState` keeps one per pane and `EditorScreen`
  hands the active one to each region. Each surface's callbacks write into *its own* `PaneState`, so
  a background pane stays current instead of scribbling over the focused one.
- **The left pane is movable, not rebuilt.** Toggling split view (or full-page mode, or the dock
  layout) changes only *where* the panes sit, so both are held in `movableContentOf` while the split
  is on. Without it Compose disposed the left pane's `AndroidView` and rebuilt it from its tab
  record — written on tab change and pause, not on every stroke — which repainted a stale document
  over the live edits and dropped the undo history. The right pane only exists while split view is
  on, so it is disposed when the split closes; `restoreTabs` re-loads its showing tab onto the
  replacement surface when it comes back. (Undo history is per surface, so it does not survive that
  round trip — same rule as a tab switch.)
- **Touching a pane adopts the live pen.** Tool, colour, width and line style live in
  `EditorUiState`, but each change only reached the surface of the pane that was active at the time.
  A touch now pushes that state onto its canvas on the pointer-input *initial* pass, before the
  canvas sees the down, so switching panes with the highlighter selected cannot draw pen ink.
- **A document can be open in both panes as two live views.** Tabs carry an `OpenTab.docKey`; the
  mirror action copies a tab keeping that key, so "same key" means "same document". Every edit
  reaches the other views through `panes/MirrorSync.kt`: `DrawingSurfaceView.doc` is a property whose
  *setter* fires `onDocumentEdited`, so the one place every edit lands is also the one place the
  mirror is notified — no per-operation hooks to keep in sync. `MirrorSync` writes the new document
  into every tab record holding the key (background tabs included, so selecting one is already
  current — all of them take the *same* immutable instance, so this shares one graph rather than
  copying it per tab) and calls `applyMirroredDocument` on any pane showing one. That entry point deliberately
  touches *nothing* but the document — scroll, zoom and columns are left alone, which is what makes
  the two views independent — and clears the receiving surface's undo history, since its snapshots
  predate the other view's edit and undoing to one would discard it. `load`/`applyMirroredDocument`
  write the backing field directly rather than the property, which is what stops an echo loop.
- **The mirror push is coalesced.** Adopting a document costs the receiving pane a full relayout and
  render over every page, and edits arrive as fast as the pen moves, so `MirrorSync.propagate` only
  records the latest document and posts one `flush()` onto the main thread; a burst of edits fans out
  once, with the last one winning. Everything that *reads* the tab records — `snapshotActiveTab`,
  `persistTabs` — calls `mirrors.flush()` first, so the deferral is never observable and no session is
  written that predates a pending edit. `MirrorSyncTest` covers the coalescing (the scheduler is
  injected, so the test runs the pass inline).
- **Which tabs are the same document is shown, not inferred.** `tabs/DocColors.kt` assigns a palette
  colour to every `docKey` open more than once across *both* panes, and `TabStrip` draws it as a dot;
  titles are file names, so they cannot distinguish "open twice" from "two files, one name".
- The split position (`SplitLayout` in `ui/`) is a UI-only fraction: dragged, clamped to leave each
  half at least 15% of the width, and deliberately **not** persisted. The fraction is where the
  **divider's centre** sits, and both panes are measured from it (`splitPaneSpans`, shared with the
  tab lanes): a weighted split lays the halves out inside `total − handle`, so the divider crawled
  behind the finger and the halves were never the widths the gesture asked for. Drag distance is
  measured from the start of the gesture (so a burst of pointer events cannot lose movement), and a
  double-tap or the accessibility "Reset split" action returns it to 50/50 with a haptic tick. What
  the divider looks like is [chrome.md](chrome.md)'s to describe — that is where the canvas's own
  furniture lives.
  Each half re-fits its page stack to its new width as the divider moves, and repaints once the
  platform has resized the surface's buffer (see [rendering.md](rendering.md) → *When the pane under
the canvas changes size*) — without that second repaint the dragged-to half kept a cropped copy of
  the old frame until the next touch.
  Whether the workspace is split, and which half has focus, survive process death through the saved
  instance state; the fraction itself restarts at 50/50.

### In-memory document model

The native-Android analogue of the reference's `Xpp*` types — one small Kotlin data
class/module per format element, mirroring the tree in [`xopp-format.md`](xopp-format.md):

Every container and element below also carries an `extraAttrs` map of the attributes we don't
interpret, in source order, so unknown markup round-trips (see the fidelity notes above).

- `Document` — `creator`, `fileversion`, optional title/preview, `List<Page>`.
- `Page` — `width`, `height` (pt), `Background`, `List<Layer>`.
- `Background` — sealed type: `Solid(color, style)`, `Pixmap(domain, filename)`,
  `Pdf(filename, pageNo, domain?)`.
- `Layer` — ordered `List<Element>` (z-order) plus optional `name`.
- `Element` — sealed type: `Stroke`, `Text`, `Image`, `TexImage`, `RawElement`.
  - `Stroke` — `tool`, `color`, `capStyle`, `lineStyle` (plain/dash/dashdot/dot), `fill` (0..255 or
    null), `List<Point>` (each `x, y, width`), plus preserved raw attrs (`ts`, `fn`, unknowns).
  - `Text` — `font`, `size`, `x`, `y`, `color`, `content`.
  - `Image` — bbox `left/top/right/bottom`, decoded bytes.
  - `TexImage` — bbox, `latex`, `color`, the rendered PNG bytes (`data`, nullable), and
    `latexInAttribute` (which of the two on-disk shapes the source used).
  - `RawElement` — an unmodelled layer child kept verbatim: tag name, attributes, raw inner markup.

Colors are stored as Android `0xAARRGGBB` ints; the I/O layer converts to/from the on-disk
`#RRGGBBAA`. Coordinates are stored in pt (document space); the view applies a pan/zoom
transform to screen space.

This list mirrors the Kotlin types in `format/model/` — keep the two in sync when the model changes.
