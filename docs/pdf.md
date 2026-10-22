# PDF: backgrounds, export, text and the outline

> Read when you change anything PDF: the background renderer and its caches, fonts in generated PDFs, text selection, the Contents outline, or how a background reference is written portably.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

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
`PdfVectorPainter`/`PdfBackgroundPainter` (the *export* path) still emit real vector PDF pages; only
the on-screen replay is gone.
Both bitmap caches allocate through **one** `BitmapBudget` (`BitmapBudget.shared`, sized at startup
from `ActivityManager.memoryClass`), so a PDF-backed document has a single memory bound rather than
two independent guesses. A cache `charge`s each bitmap it rasterises; when the total goes over, the
budget asks its clients to `trim` — the *other* clients first, the one that just allocated last, so
the pixels being drawn this frame survive. `InkCache.trim` gives back off-screen pages first,
`PdfPageCache.trim` its least-recently-used entries (pinned tiles last), and
`ElementRenderer.trim` its least-recently-drawn embedded images (keyed by element *identity*, since
an `ImageElement`'s `equals` compares whole byte arrays; `ElementRenderer.close` recycles them and
leaves the budget when the surface is torn down or a one-shot thumbnail is finished). Trimmed bitmaps are dropped
but never recycled: another thread's trim can hit a bitmap the drawing thread holds for the current
frame. **No lock is held across a charge, in either direction.** `BitmapBudget` never calls a client
back while holding its own lock, and `BitmapLruCache.put` releases the *cache* lock after the insert
and before charging — because a charge reaches into every other client's `trim`, which takes that
client's lock. Holding one cache's lock across it inverts the order between two caches, and any two
live caches over one budget can hit it: a fast scroll in each had the drawing thread inside cache A
waiting on B while A's rasteriser sat inside B waiting on A, hanging the main thread in `doFrame`
until the system killed the app for not responding. (The case that found it was a document mirrored
into both split panes, which then meant two `PdfPageCache`s over one PDF; the panes now share one —
see below — but the lock rule stands on its own, since a pane's PDF and image caches charge the same
budget.)
`BitmapCacheDeadlockTest` (connected suite — the accounting needs real `Bitmap.byteCount`) races two
caches over one budget and fails if they lock up.

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
plain sheet. **Import PDF** (`PdfImport`, invoked from `MainActivity`) copies the picked PDF into
app cache and builds pages from it (`PdfImport.pagesFor`) — one page per PDF page, sized from the PDF,
with the `filename`+`domain` on the first of them only and `pageno` thereafter (the desktop on-disk
convention). An `ImportPdfMode` chosen up front (the `ImportPdfDialog`) decides where they land:
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
PDF** (`PdfExporter`) flattens the document back out with **PDFBox** (`com.tom-roush:pdfbox-android`,
the one non-framework runtime dependency — see the note below): a `pdf`-backed page whose source PDF
is available (`PdfPageCache.source`, the cached import) is **imported verbatim so its original vector
content is preserved** (`PDDocument.importPage`), and the annotations are appended over it as a
**vector overlay** (`PdfVectorPainter`, an `APPEND`-mode content stream); every other page becomes a
fresh sheet whose background ruling is drawn as vectors (`PdfBackgroundPainter`) with the same
overlay. A `pixmap`-backed page additionally gets its picture embedded over that sheet —
`PdfExporter` decodes it synchronously through `ImageBackgroundCache.render` at 2× the page's point
size (~150 dpi) and draws it to the page box, so an image-backed document flattens to what the
editor shows; a picture that no longer decodes leaves the bare sheet. The geometry is **not** in the
painter: `PdfPagePlan` (in `mobix-core`) compiles the page into the ordered ops — fills, paths, text
runs, image placements — at scale 1 (the `.xopp` unit == the PDF unit, 1/72"), flipping y into PDF's
bottom-left space through the shared `PdfPageTransform`, and `PdfVectorPainter` replays those ops
through the Android port of PDFBox, one arm per op. Pen strokes taper per segment, the highlighter is
one constant-width translucent path, `.xopp` text elements use the base-14 fonts (see **Fonts in
generated PDFs** below), and images embed losslessly. That split is why the desktop can emit the same
page with `org.apache.pdfbox`: one geometry, two writers, no second opinion about a stroke. **Nothing is rasterised** except
bitmaps that were already raster (user images and `pixmap` backgrounds), so a no-op import→export round-trips a PDF at ~its original size
and fidelity instead of bloating ~10× from a raster flatten. **Rotated source pages** (`/Rotate`
90/180/270) are handled: since the on-screen renderer already applies `/Rotate`, annotations are
authored in the page's *visual* space, so `PdfExporter` pre-multiplies the overlay content stream by
a `PdfOverlayMatrix` (a pure, unit-tested `cm` matrix — the inverse of the display rotation, with the
crop-box origin folded in) that maps visual coordinates into the page's unrotated content space; the
viewer's `/Rotate` then cancels back to the drawn position, so strokes, text, and images all land
correctly. For `/Rotate 0` the matrix is just the crop-origin shift.

## Fonts in generated PDFs

**Fonts in generated PDFs.** The PDF **base-14** fonts (`PDType1Font.HELVETICA` and friends) only
encode WinAnsi, so `PdfVectorPainter` drops any codepoint outside `0x20..0xFF`. That is acceptable
for `.xopp` `<text>` elements — desktop Xournal++ owns their font description and the element is
preserved in the file regardless — but **not** for **text import**, where the source is arbitrary
UTF-8 and the glyphs only exist in the generated PDF. So the text-import path draws with fonts
**bundled as app assets** and embedded per document by `PdfFonts`
(`PDType0Font.load(doc, stream, subset = true)`): PDFBox subsets them into the output, so a CJK or
Cyrillic import renders identically everywhere without bloating the file with a whole face.

The bundled family is **DejaVu Sans** — regular, **bold**, oblique and bold-oblique, the four faces
markdown emphasis needs — and **DejaVu Sans Mono** (monospace, which also sets markdown code), chosen
for broad Unicode coverage at a modest ~3 MB and a permissive licence — Bitstream Vera + Arev, with the
DejaVu changes in the public domain, compatible with the Apache-2.0/OFL-only rule that already ruled
out iText. They live in `app/src/main/assets/fonts/` with the full licence text beside them as
`LICENSE.txt`. Codepoints even DejaVu lacks are **substituted, never fatal**: `GlyphSanitizer` (pure,
unit-tested; encodability injected as a predicate) maps each unencodable codepoint to U+FFFD — or `?`,
or a space — memoising per codepoint, and iterates by codepoint so an astral character becomes one
substitute rather than two surrogate halves. `PdfFonts.Embedded.measurer` is also what feeds
`TextPaginator`'s injected measurement, so wrapping is measured against the exact font that draws.
Markdown needs the same guarantee across *five* faces at once, so `MarkdownPdfWriter` resolves every
`RunStyle` to an `Embedded` up front and exposes that map both as the layout's `SizedMeasurer` and as
the draw-time font lookup — one table, so a line can never be wider on the page than the width it was
wrapped to.

## PDF text selection

**PDF text selection.** An imported PDF's **text layer** is extracted on import (off the UI thread)
by `PdfTextExtractor` — a `PDFTextStripper` subclass that turns each positioned glyph into a
`CharBox` and groups them into `PdfWord`s (`PdfWordGrouper`, breaking on whitespace, wide gaps, and
line changes) — into a `PdfTextIndex` threaded to the surface via `setPdfTextIndex` (mirroring
`setPdfSource`). This reuses the **same PDFBox dependency** as export, so **no OCR engine** is needed
for born-digital PDFs; a scanned image-only page yields no words (`hasAnyText` false), and OCR for
that case is a tracked follow-up. Boxes are page-local top-left points (the `.xopp` frame), so they
map to the screen through a `PageBox` exactly like strokes. The **Select text (PDF)** tool
(`EditorTool.TEXT_SELECT` → `ActiveTool.TEXT_SELECT` → `GestureIntent.SELECT_TEXT`) drags to select
an inclusive reading-order word range: `beginTextSelect`/`textSelectMove` resolve pointer positions
to word indices (`PdfTextIndex.anchorWord`), the range is highlighted (`drawTextSelection`), and the
`TextSelectionBar`'s Copy puts the text on the Android system clipboard (`copyTextSelection`). The
selection is a **view-only** overlay derived from the PDF — it isn't part of the `.xopp` document, so
it doesn't affect round-trip (matching how desktop selects a PDF background's text).

## PDF Contents (the outline)

**PDF Contents (the outline).** The same worker that extracts the text layer also reads the PDF's
**`/Outlines` bookmark tree** (`PdfOutlineExtractor`, PDFBox's `PDOutlineItem.findDestinationPage`),
turning it into a flat, Android-free `PdfOutline` of `PdfOutlineEntry(title, pageIndex, depth)` rows.
The two walks are done in one pass on purpose — they load and parse the same bytes — and both results
are cached by file (`PdfOutlineCache` mirrors `PdfTextIndexCache`), so mirroring a document into the
second pane reuses them instead of walking the PDF twice. A destination-less entry inherits the page
of its nearest descendant that has one, so a chapter header still leads to the chapter's first page;
an entry that points nowhere at all is shown but not tappable. `setPdfOutline` publishes the outline
to the chrome through `onPdfOutlineChanged` (pane state), and the Pages menu grows a **Contents…**
entry that opens `PdfContentsDialog` — an indented, page-numbered list. Tapping a row calls
`goToPdfPage`, which finds the document page whose `Background.Pdf.pageNo` matches rather than
assuming the PDF index and the `.xopp` page index agree (merging a second PDF in renumbers every
appended page, and a desktop-authored file may order its pages freely).

This is deliberately **navigation only, never document content**: the `.xopp` format has nowhere to
keep a bookmark tree, so the outline is read from the PDF and the file is untouched by it — the same
reasoning that keeps desktop Xournal++'s PDF text selection out of the saved document.

## Relative PDF references {#relative-pdf-references}

A background reference is only useful if it still resolves on the machine that opens the file next.
An absolute Linux path means nothing on Android, and a `content://` URI means nothing anywhere but
the device that issued it — so **a path relative to the `.xopp` is the portable form, and the
default this app writes whenever it can.** Desktop Xournal++ has no `relative` domain: a relative
path is carried under `domain="absolute"` and resolved against the document's own folder
(`LoadHandler::getAbsoluteFilepath`), so what we write is exactly what desktop already reads.

The string logic lives in `io/PdfReference.kt` — pure and unit-tested (`PdfReferenceTest`), with no
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
