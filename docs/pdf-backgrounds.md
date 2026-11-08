# PDF backgrounds: the page, its cache and the portable reference

> Read when you change how a PDF page is shown behind the ink, how its pixels are cached, or how the reference to it is written so it still resolves on the next machine.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

What the app reads out of a PDF and writes into one is [pdf.md](pdf.md)'s.

## Vector PDF pages — retired (2026-10-11)

**Vector PDF pages — retired (2026-10-11).** *Decision (2026-10-10), reversed (2026-10-11).* A first
revision drew a `pdf` background's page as **vector geometry** on the drawing thread (PDFBox's
`PDFRenderer.renderPageToGraphics`, replayed every frame) behind an `AppSettings.vectorPdf` toggle,
with a `PdfVectorGuard` to send a page back to raster when its replay overran a time budget. That is
a full content-stream replay **per frame on the UI thread**, and on a born-digital
PDF it made panning, pinching and — above all — drawing and hovering visibly laggy: the frame (and the
input behind it) was spent replaying the page instead of being served. Gating the replay to frames at
rest only moved the stall to every settle. So the whole on-screen vector path was **removed**
(`PdfVectorSource`, `PdfVectorBackground`, `PdfVectorGuard`, `PdfPageClip` and the setting all went),
and a `pdf` background now always goes through the raster cache and its tiles above — exactly like any
other PDF page, and exactly the smooth, cached rendering desktop Xournal++ uses. Nothing is lost in
sharpness: the whole-page bitmap is bucketed and capped, and past that ceiling the **visible tiles are
rasterised at the true on-screen resolution**, so text and line art stay sharp however far you zoom.
The *export* path kept vector output: `PdfVectorPainter` and `PdfBackgroundPainter` write the page
into the file as vectors, never onto the screen — see *Export PDF* below.
Both bitmap caches allocate through **one** `BitmapBudget` (`BitmapBudget.shared`, sized at startup
from `ActivityManager.memoryClass`), so a PDF-backed document has a single memory bound. A cache
`charge`s each bitmap it rasterises; over the total, the budget asks its clients to `trim` — the
*other* clients first, the one that just allocated last, so this frame's pixels survive. `InkCache.trim` gives back off-screen pages first,
`PdfPageCache.trim` its least-recently-used entries (pinned tiles last), and
`ElementRenderer.trim` its least-recently-drawn embedded images (keyed by element *identity*, since
an `ImageElement`'s `equals` compares whole byte arrays; `ElementRenderer.close` recycles them and
leaves the budget when the surface is torn down). Trimmed bitmaps are dropped but never recycled:
another thread's trim can hit a bitmap the drawing thread holds for this frame. **No lock is held across a charge, in either direction**: `BitmapBudget` never calls a client
back while holding its lock, and `BitmapLruCache.put` releases the *cache* lock before charging —
because a charge reaches into every other client's `trim`, which takes that client's lock. Holding
one cache's lock across it inverts the order between two caches, and any two live caches over one
budget can hit it: a fast scroll in each had the drawing thread inside cache A waiting on B while
A's rasteriser sat inside B waiting on A, hanging the main thread in `doFrame`
until the system killed the app for not responding. (The case that found it was a document mirrored
into both split panes, two `PdfPageCache`s over one PDF; the panes now share one, but the lock rule
stands on its own.) `BitmapCacheDeadlockTest` (connected suite — it needs real `Bitmap.byteCount`)
races two caches over one budget and fails if they lock up.

## PDF backgrounds (`render/`)

**PDF (`render/`).** A `<background type="pdf">` page shows its PDF page as the background image:
`PdfPageCache` wraps the framework `PdfRenderer` (dependency-free, serialised — `PdfRenderer` is
not thread-safe) and `BackgroundRenderer` draws the rasterised page.

**One cache and one text index per PDF file, not per view.** Callers take a cache through
`PdfPageCache.shared(file)`, which keys live instances by absolute path and refcounts them; `close()`
releases one claim and only the last holder shuts the renderer down (`DrawingSurfaceView.setPdfSource`
owns exactly one claim, so being handed the instance it already holds gives the surplus one back).
A directly constructed `PdfPageCache` is unshared and closes on the first `close()`. The extracted
text layer is shared the same way through `PdfTextIndexCache` (soft-referenced by path, so an unused
index is reclaimable, and `forget` drops it when the bytes behind a path are rewritten by an import or
a merge). Without this, mirroring a PDF-backed document into both panes opened a second `PdfRenderer`
over the same file, rasterised every page twice against one shared bitmap budget, and extracted and
retained a second copy of the whole word index. The cache is keyed by page,
target-width bucket and (for tiles) grid cell, **LRU** under a heap-proportional byte budget (`PdfPageCache.budget`, a quarter
of the heap clamped to 24–192 MB — a page's cost varies ~64× between zoom levels, so counting pages
budgets nothing). Rasterisation never happens on the drawing frame: `request` returns whatever
resolution is already cached for that page (the nearest width, upscaled, or nothing) and queues the
exact size on a single worker thread, which fires `onPageReady` so the view redraws sharp. The view
also `prefetch`es one page either side of the viewport, so scrolling a long document meets a filled
cache. Evicted bitmaps are *not* recycled — the drawing thread may still hold one for the frame in
flight; the GC reclaims them. Past the whole-page raster ceiling `requestTiles` supplies
viewport-sized tiles rendered at the true on-screen scale (see the zoom paragraph above), so text
keeps sharpening as you zoom. A `.xopp` whose PDF isn't present falls back to a
plain sheet. **Import PDF** (`PdfImport`, invoked from `MainActivity` on the tablet and from the window's own import on the PC) copies the picked PDF into
app cache and builds pages from it (`PdfImport.pagesFor`) — one page per PDF page, sized from the PDF,
with the `filename`+`domain` on the first of them only and `pageno` thereafter (the desktop on-disk
convention). Both shells ask the same question **after** the file has been read — which pages, as a
`PageRanges` run typed into `PdfImportPrompt`'s card (the tablet) or the window's `ImportPdfDialog` —
and an `ImportPdfMode` picked there decides where they land:
`REPLACE` makes them the whole `Document` (`PdfImport.documentFor` → `load`), `APPEND` adds them after
the open document's pages as one undoable edit (`PageOps.appendPages` → `DrawingSurfaceView.appendPages`,
which also drops a lone untouched blank sheet so it isn't stranded in front of the PDF). Because the
`filename`/`domain` convention — and `XoppZip`'s single embedded `bg.pdf` — allow exactly **one** PDF
per document, `APPEND` onto a document that already has a PDF background does not add a second
reference: `MainActivity.appendMergedPdf` **merges** the two PDFs into one via `DocumentIo.merge`. `PdfMerger.join`
concatenates the current background PDF and the incoming one with PDFBox's `PDFMergerUtility` (source
pages imported verbatim, so they rasterise and re-export unchanged); the joined file is written to
`filesDir` — not the cache, so the link a plain `Save` records survives — under a name allocated by
that pane's joined-PDF `PdfStore`, so a merge never writes the file it is reading. The
new pages are sized from the incoming PDF with `PdfImport.pagesFor(reference = null, pageNoOffset =
<existing PDF page count>)`, and `DrawingSurfaceView.appendPdfPages` then re-points the document's one
reference at the joined file (`documentWithPdfReference`) **and** appends the pages in a **single
undoable edit** — the two halves must move together, since the appended `pageno` values index the
joined document. Existing pages keep their `pageno`, being at the front of the join. `Save`
(`ORIGINAL`) links the joined PDF by path; `Save As` (`ZIPPED`) embeds it as `bg.pdf` through the
usual `documentWithPdfDomain` rewrite, which is the easy case. **Export
PDF** (`PdfExporter`) writes a **vector** PDF with **PDFBox** (`com.tom-roush:pdfbox-android`, the one
non-framework runtime dependency — see the note below): a `pdf`-backed page whose source PDF is
available (`PdfPageCache.source`, the cached import) is **imported verbatim so its original vector
content is preserved** (`PDDocument.importPage`), and the ink is appended over it as a **vector
overlay** (`PdfVectorPainter`, an `APPEND`-mode content stream); every other page becomes a fresh sheet
whose background ruling is drawn as vectors (`PdfBackgroundPainter`) with the same overlay. A
`pixmap`-backed page additionally gets its picture embedded over that sheet — `PdfExporter` decodes it
synchronously through `ImageBackgroundCache.render` at 2× the page's point size and draws it to the
page box, so an image-backed page prints what the editor shows; a picture that no longer decodes
leaves the bare sheet. The geometry is **not** in the painter: `PdfPagePlan` (in `mobix-core`)
compiles the page into the ordered ops — fills, paths, text runs, image placements — at scale 1 (the
`.xopp` unit == the PDF unit, 1/72"), flipping y into PDF's bottom-left space through the shared
`PdfPageTransform`, and `PdfVectorPainter` replays those ops through the Android port of PDFBox, one
arm per op. Pen strokes taper per segment, the highlighter is one constant-width translucent path,
text elements use the base-14 fonts (see **Fonts in generated PDFs** below), and images embed
losslessly. That split is why the PC writes the same page with `org.apache.pdfbox`: one geometry, two
writers, no second opinion about a stroke (below). **Nothing is rasterised** except bitmaps that were
already raster (user images and `pixmap` backgrounds), so a no-op import→export round-trips a PDF at
~its original size and fidelity instead of bloating from a raster flatten. **Rotated source pages**
(`/Rotate` 90/180/270) are handled: since the on-screen renderer already applies `/Rotate`, ink is
authored in the page's *visual* space, so `PdfExporter` pre-multiplies the overlay content stream by a
`PdfOverlayMatrix` (a pure, unit-tested `cm` matrix — the inverse of the display rotation, with the
crop-box origin folded in) that maps visual coordinates into the page's unrotated content space; the
viewer's `/Rotate` then cancels back to the drawn position. For `/Rotate 0` the matrix is just the
crop-origin shift. A background that no longer resolves (a missing PDF, a picture that will not
decode) leaves the bare sheet, never a blank file. A chunked `.mobix` is written as the pages its
parts stand for, because a PDF holds pages and this exporter does no sheet slicing.

**The PC writes the same page with the same plan.** `PdfPagePlan.paper` compiles the sheet and its
ruling into ops beside the elements, so the paper is compiled with the page rather than by a shell's painter: `DesktopPdfExport`
writes each page as paper, then picture, then ink, and `DesktopPdfPainter` replays every op through
`org.apache.pdfbox` (see [stack.md](stack.md) for why the two shells carry two libraries). A page over an
imported PDF keeps that PDF's **own vectors** on the window: the source page is imported verbatim and only the
overlay is written over it, with `PdfOverlayMatrix` (now in `mobix-core`, so one rotation rule serves both
shells) cancelling a `/Rotate` back to the drawn position — an annotated 200-page scan neither bloats nor
loses its text layer, and `DesktopPdfExportVectorsTest` proves it by reading the source's words back out of
the exported file. `PageBackdrop`'s **raster** is the fallback, for a source this window cannot open, and
the only path for a photograph-backed page. **Neither shell writes annotation dictionaries**: the marks are
the page's own content — no `/Highlight`, `/Ink` or `/Text` is added — so every reader shows them, both
shells produce the same file, and a reader's annotation pane stays empty. A **board** (a chunked document) is
refused in words: a PDF needs pages, and the window has no sheet slicer.
The window's pages are read through its page window, so a paged `.mobix` inflates one sheet at a time and an
unsaved edit is in the export.

**A page that names no PDF is still on one.** A document records the reference on a single page and every
other pdf page carries a `pageno` alone, and desktop reads that shape by inheritance — so the window
resolves a page's picture through the document's reference rather than through the page itself
(`PdfPageReferences`), and the same rule paints the canvas, the page list's thumbnails and the export. Told
no better, the window used to read each page on its own: a three-page import showed its first sheet and
left the other two blank, and an export of it lost their vectors too. Removing the page that carries the
reference hands it to the first pdf page that survives, so a delete cannot take the PDF out of the
document, and a reorder changes only the order the export writes.

## Relative PDF references {#relative-pdf-references}

A background reference is only useful if it still resolves on the machine that opens the file next.
An absolute Linux path means nothing on Android, and a `content://` URI means nothing anywhere but
the device that issued it — so **a path relative to the `.xopp` is the portable form, and the
default this app writes whenever it can.** Desktop Xournal++ has no `relative` domain: a relative
path is carried under `domain="absolute"` and resolved against the document's own folder
(`LoadHandler::getAbsoluteFilepath`), so what we write is exactly what desktop already reads.

The string logic lives in `format/PdfReference.kt` — pure and unit-tested (`PdfReferenceTest`), with no
Android types, so the same helpers serve both filesystem paths and SAF **document ids**
(`primary:Docs/notes.xopp`), which are `/`-joined paths behind a volume root.

**Reading** (`DocumentIo.resolvePdfBackground`, which now takes the source URI of the `.xopp` being
opened) handles four shapes, in order:

| Reference | Resolved as |
|---|---|
| `content://…` | opened directly (what this app records for a picked PDF) |
| `/abs/path.pdf` | opened as a file, if it exists on this device |
| `bg.pdf`, `scans/bg.pdf`, `../bg.pdf` | **relative** to the `.xopp`'s own folder |
| `domain="attach"` on a non-zip document | the `<name>.xopp.<filename>` sibling |

The last two need a folder, which `openSibling` derives from the source URI: a `file://` URI
relativises on the filesystem, a `content://` one on its SAF document id via
`DocumentsContract.buildDocumentUriUsingTree`/`buildDocumentUri`. Providers with **opaque** ids
(Downloads' `msf:1234`) have no path to relativise, so resolution returns null and the pages come up
blank with the existing "Background PDF not found" note — the reference itself is still written back
untouched on save, so nothing is silently dropped.

**Writing** (`DocumentIo.portableReference`, applied on every `ORIGINAL` save) rewrites the
reference to be relative to the save destination whenever the PDF sits in the same folder — matching
SAF document-id volumes, or two filesystem paths. It is a heuristic, not a preference: a resolvable
relative path is strictly better than a `content://` URI no desktop can read, and there is nothing
for the user to get wrong. Three cases are deliberately left alone: an **already relative**
reference (so a desktop-authored document round-trips byte-identically), an **attach** reference
(the ZIP path owns that), and anything that **won't relativise** (different folder, different
volume, opaque id) — those keep their absolute reference rather than becoming a broken relative one.

**Round-trip status.** The parse/serialize halves are locked in by fixtures in
`PdfBackgroundRoundTripTest` (a relative path under `domain="absolute"`, and an attach reference on
a non-zip document) and the derivation by `PdfReferenceTest`.

The **desktop check** was run against **Xournal++ 1.3.6** on Linux, headlessly, via its export CLI
(`xournalpp FILE.xopp -p out.pdf`, which renders the resolved background into the exported PDF — a
blank page means the reference didn't resolve). Results:

| Fixture | Desktop 1.3.6 |
|---|---|
| `domain="absolute"` + bare sibling filename (`bhm_prior.pdf`) | ✅ background renders |
| The multi-page shape this app writes — first page carries `filename`+`domain`, later pages only `pageno` | ✅ all 3 pages render the right PDF page, strokes on top |
| `domain="attach"` on a non-zip document, `<name>.xopp.bg.pdf` sibling | ✅ background renders |
| Unresolvable reference (`nope.pdf`) | ⚠️ desktop reports *"The background file … could not be found"* and aborts the export (exit 2) |

So the reference forms we read and write are exactly what desktop resolves, and the inheritance
shape (`filename` only on the first page) is accepted.

**An unresolvable reference survives a desktop save.** Verified interactively on the same 1.3.6
build (X11/openbox GUI): opening a `.xopp` whose background PDF is missing pops a *"Missing PDF
background file"* dialog offering **Select another PDF** / **Remove PDF Background** / **Cancel**.
Dismissing it with *Cancel* leaves the reference in place — the page renders as a grey sheet reading
"PDF background missing", strokes still draw on it, and after editing and saving, the `<background>`
element comes back **byte-identical**, filename and `domain` untouched. Confirmed for both shapes:
a bare relative sibling name (`missing-scan.pdf`) and a dead absolute path
(`/nonexistent/dir/gone.pdf`). Desktop only rewrites or drops the reference if the user explicitly
picks one of the other two buttons, so this app's "write the reference back untouched when it can't
be resolved" behaviour matches desktop exactly and a missing PDF never silently loses its link.
