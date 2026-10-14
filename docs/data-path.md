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
desktop Xournal++ to open something it cannot).

A `.mobix` is read as its own document, not through the `.xopp` model:

- **Its identity comes with it.** The manifest's `documentId` travels into the model
  (`Document.documentId`) and is written back on the next save, so saving an open document is an edit
  of that document rather than a new one — the difference a sync client or a later merge sees. The ids
  of the pages and elements come with it the same way, and a document that arrives *without* them (a
  `.xopp` has none) **adopts** them before the first `.mobix` write (`Document.withIdentity`,
  `format/model/DocumentIdentity.kt`): minted into the model rather than into the file, so the file's
  ids are the model's own and every later save writes the same ones. A writer that minted them on a
  copy would leave the open document unidentified, and the next save would hand out a fresh set — which
  a merge reads as the whole page replaced rather than edited.
- **Its bundled backgrounds are adopted into the stores on the way in** (a `.mobix` is self-contained,
  so the bytes in the archive *are* the background), keyed by the reference the document keeps, which
  is what the renderer looks a picture up by; a background the document only references is resolved as
  a gzip `.xopp`'s is. A bundle the archive does not actually hold is reported — those pages would be
  blank, and "missing" is a different thing to explain than "empty".
- **What it refuses, it refuses loudly.** A `.mobix` whose `formatVersion` is newer, whose
  `canvas.json` disagrees with the manifest, or that stores its content in `chunks/` (an infinite
  canvas this build cannot lay out) fails the open with the reason, rather than being opened as
  something it is not.
- **It is read whole today, and the decision is to stop that.** Every entry is inflated on open — the
  number is in [mobix-format.md](mobix-format.md) (*Engine optimizations*, item 1) — while
  `format/mobix/MobixPagedReader.kt` already opens only the envelope. The surface the editor is to get
  is a **page window**: the manifest supplies `pageCount` and the page ids without inflating a page,
  and a page is materialised when the canvas shows it. Two children carry it:
  `stream-the-mobix-open-path-and-defer-asset-payloads` (the open path) and
  `give-the-editor-a-page-window` (the editor's lazy read, which then reaches export, search and merge).

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

### Tabs and the session cache

Several documents can be open at once, but there is only **one** `DrawingSurfaceView`. A tab switch
is therefore a swap, driven by `MainActivity`:

```
snapshot the surface into the outgoing OpenTab (document, save format, PDF path, page)
  → TabManager.select(index)
  → load the incoming OpenTab into the surface (setPdfSource → load → goToPage)
```

Consequences worth knowing:

- **Undo history is per surface, not per document.** It is cleared by `load`, so a tab switch starts
  the incoming document with a clean history. Content, including unsaved edits, is untouched.
- **Only the active tab's document is live**; every other tab holds the snapshot taken when it was
  last showing. That is also what gets written to disk.
- **The session is cached, not saved.** `TabStore` writes `filesDir/tabs/`: a `session.index` line
  file (`TabIndex`) plus one `<id>.xopp` gzip snapshot per tab, rewritten on every tab change and in
  `onPause`. On launch it is read back, so the app reopens on the same tabs with the same unsaved
  edits. It is a restart cache keyed by tab id — the user's own file (the tab's `uri`) is still the
  only thing the desktop ever sees, and a snapshot never stands in for saving.
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
- **Each pane persists separately.** `TABS_DIRS` gives pane 0 the historical `filesDir/tabs` and pane
  1 `filesDir/tabs-right`, so an existing session still restores and the right-hand pane's documents
  survive both a split-view toggle and a restart. `onPause` snapshots and writes *both*.
- **A pane's Compose mirror is per pane too.** `ui/PaneState.kt` holds the zoom/page/layer/undo state
  a canvas pushes up through its callbacks; `EditorUiState` keeps one per pane and `EditorScreen`
  hands the active one to each region. Each surface's callbacks write into *its own* `PaneState`, so
  a background pane stays current instead of scribbling over the focused one.
- **Turning split view off doesn't destroy the pane.** Its canvas is disposed, so when it comes back
  `restoreTabs` finds a non-empty session and re-loads the showing tab onto the *replacement*
  surface. (Undo history is per surface, so it does not survive that round trip — same rule as a tab
  switch.)
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
  half at least 15% of the width, and deliberately **not** persisted.

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
