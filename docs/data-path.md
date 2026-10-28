# The data path: opening, reading and saving

> Read when you change how a document's bytes are sniffed, read, encoded or written.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

Remote staging, the tab session cache and split view are [document-session.md](document-session.md)'s;
this spoke owns the bytes — what the file is, how it is read into the model, and how the model is
written back.

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
desktop Xournal++ to open something it cannot). The `desktop/` window follows the same rule through the same encoder, and its Save As switches it through the **type chosen in its save window** — which names the file in the same dialog; how it stages and moves the write is [desktop-documents.md](desktop-documents.md)'s.

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
  write, through one function both sides call (`MobixPageReuse.identified` — in
  [document-session.md](document-session.md), and [mobix-format.md](mobix-format.md)). A page that was
  *read* carries element ids either way, because the
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
  (`format/mobix/MobixPagedReader.kt`); before this the whole archive was read into memory and every page
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
