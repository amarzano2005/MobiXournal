# PDF: backgrounds, export, text and the outline

> Read when you change what the app writes into a PDF or reads out of one: base-14 fonts in generated PDFs, text selection, the magnetic highlighter, or the Contents outline.
> For the task: [Annotating a PDF, and recording](guide-pdf-and-audio.md), [Which format your notebook is in](guide-formats-and-conversion.md), [What works, and what is not there](guide-limitations.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

A page's PDF **background** — the renderer, its caches, and the portable reference that keeps it resolvable — is [pdf-backgrounds.md](pdf-backgrounds.md)'s.

## Fonts in generated PDFs

**Fonts in generated PDFs.** A PDF's built-in **base-14** fonts (`PDType1Font.HELVETICA` and friends) only
encode WinAnsi, so any codepoint outside it is lost wherever text is typeset **into** a PDF. Two paths
do that: the **text import**'s generated background, where the source is arbitrary UTF-8 and the glyphs
only exist in the generated PDF, and the **page export itself**, which replays `PdfPagePlan`'s `PdfText`
ops as text rather than painting glyphs into an image — so a `<text>` element's codepoints leave through
the same sanitiser. So the text-import path draws with fonts
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

### Unicode in exported text, and the decision

`PdfTextEncodingAuditTest` pushes a text box through the plan and records what comes out:

| Input | Today | Why |
|---|---|---|
| `café ñ ½ ²` | unchanged | inside `0x20..0xFF` |
| `α`, `Ж`, `H₂`, `e` + U+0301 | `?`, `?`, `H?`, `e?` | the guard is a code range, not an encoding |
| `… – — ‘ ’ “ ” € ™ Š ž ƒ ‹ › † ‡ • ‰` | 18 × `?` | WinAnsi **does** encode all of these |
| `\u0080` | passes through | WinAnsi's `0x80` is `€`: a control character becomes a currency sign, or `showText` throws |
| `😀` | `??` | a surrogate pair, one substitute per half |

The guard is therefore both too strict and too permissive, and it is **silent**: the page exports and
the text is gone or wrong. The decision is to **reuse the faces already bundled for the text import** —
DejaVu Sans through `PDType0Font.load(doc, stream, subset = true)`, which also writes a `ToUnicode` map,
so the text is drawn *and* selectable *and* extractable. A run whose characters all encode in WinAnsi
keeps its base-14 face, so an ordinary export stays small and familiar; a run with anything else is
typeset in the bundled face. The licence question is already settled and shipped
(`app/src/main/assets/fonts/LICENSE.txt`). Where even DejaVu has no glyph, the fallback must be
**visible** — U+FFFD from `GlyphSanitizer` — never a silent `?`. Broken out as `pdf-unicode-text-ops`,
`desktop-pdf-unicode-fonts` and `pdf-unicode-extraction-test`; until the first lands, the table above is
what the export does. Sanitising now keeps one bad emoji from failing an export while the same page
draws fine on screen.

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

The geometry is **shared, not forked**: `CharBox`, `PdfWord`, `PdfWordGrouper`, `PdfTextIndex` and the
outline tree live in `mobix-core/src/main/java/com/mobixournal/render/`, and each shell keeps only its
own PDFBox glue. A document's text is read in **one traversal** on both sides: PDFBox's stripper is
handed the whole page range and each line is filed under the page it came from, where asking it page
by page re-parsed the file once per page. The tablet also coalesces requests by **source revision** —
path, length and modification time (`PdfAnalysisJobs`) — so two panes asking about one file join a
single walk, the walks run on a two-thread pool, and a merge (new bytes) is a new key rather than an
answer left over from the old file. A result whose canvas has moved to another PDF is dropped. Each
shell's glue is only that: the reader (`PdfTextExtractor`/`PdfOutlineExtractor` over `pdfbox-android`;
`DesktopPdfText`/
`DesktopPdfOutline` over `org.apache.pdfbox`, one `PDDocument.load` per file, cached by reference and
length in `DesktopPdfIndexCache` and read off the UI thread). Both shells therefore resolve a pointer on
the same word boxes. In the window, **Select text (PDF)** (`Shift+S`) anchors at `anchorWord`, drags an
inclusive reading-order range, washes it and puts the words on the system clipboard (`Ctrl+C` or the
selection's Copy bar); `Esc`, a click off the text or a page change clears it, and it writes nothing.

## The magnetic highlighter

A highlighter drag over a born-digital page is tested against the same `PdfWord` boxes text selection
uses (`PdfTextIndex`, page-local pt) by `render/MagneticHighlighter.kt` — pure geometry, JVM-tested. Only
a drag that is clearly horizontal qualifies: less than 8 pt, steeper than `0.6 ×` its own width, or over
no text stays exactly as drawn. When it does qualify, the words the pen line crosses are clustered into
lines and each line becomes one constant-width band as tall as its glyph boxes plus 1.5 pt of air. The
canvas previews those bands during the drag (`DrawingSurfacePaint.drawCurrent`) and commits them at
lift-off as ordinary highlighter strokes, so one gesture is still one undo step and the band round-trips
through `.mobix` and `.xopp` as ink. Snapping is a convenience, never a correction the user did not ask
for: a margin mark is not silently turned into a sentence. The window commits and previews the same
bands from the same object (`DesktopPdfIndex`'s words, `PenPointer.pdfBands` as the live preview), so a
stylus highlight snaps exactly as a mouse one does.

## PDF Contents (the outline)

**PDF Contents (the outline).** The same worker that extracts the text layer also reads the PDF's
**`/Outlines` bookmark tree** (`PdfOutlineExtractor`, PDFBox's `PDOutlineItem.findDestinationPage`),
turning it into a flat, Android-free `PdfOutline` of `PdfOutlineEntry(title, pageIndex, depth)` rows.
The two walks are done in one pass on purpose — they load and parse the same bytes — and both results
are cached by file (`PdfOutlineCache` mirrors `PdfTextIndexCache`), so mirroring a document into the
second pane reuses them instead of walking the PDF twice. A destination-less entry inherits the page
of its nearest descendant that has one, so a chapter header still leads to the chapter's first page;
an entry that points nowhere at all is shown but not tappable. `setPdfOutline` publishes the outline
to the chrome through `onPdfOutlineChanged` (pane state), and the page list lane's foot grows a
**Contents** glyph (`PdfContentsButton`) that opens `PdfContentsDialog` — an indented, page-numbered
list. A tap calls `goToPdfPage`, which picks the page by `Background.Pdf.pageNo` rather than by index
(a merged PDF renumbers every appended page, and a desktop-authored file may order them freely).

This is deliberately **navigation only, never document content**: the `.xopp` format has nowhere to
keep a bookmark tree, so the outline is read from the PDF and the file is untouched by it — the same
reasoning that keeps desktop Xournal++'s PDF text selection out of the saved document. The window's
**Contents** section (`PageSidebar`, under the layers) prints the same rows, indented by depth, only
when opened, and resolves a click through `Background.Pdf.pageNo` — never an offset — so a merged PDF
still lands on the right page.

## Joined PDFs: lifetime, publishing and refusing a blank sheet

A document holds **one** PDF reference, so a second import is *joined* into the first
(`DesktopPdfMerge` on the PC, `PdfMerger` on the tablet) and every arriving page keeps the page number it
has inside the joined file. What that file's lifetime is, and what a save does with it, follows the
container:

- **`.mobix` and a zipped `.xopp` carry the bytes**, so the joined file is scratch and may go.
- **A gzip `.xopp` names the PDF by path**, so the file it names has to outlive the session. The tablet
  pins it in its joined-PDF store (`PdfStore.pin`) once a save in that format succeeded, and the sweep
  that runs when a tab closes keeps pinned files whatever its byte budget says. The window publishes it
  instead: a save to a gzip `.xopp` copies a merge output out of the temp folder into
  `%APPDATA%/MobiXournal/pdf` (`DesktopPdfSources`) and re-points the reference at the copy. A PDF the
  reader picked is never moved, and saving the same document twice reuses the published copy.
- **Every join is a new file.** The window's join used to be named after the base PDF, so two tabs built
  on one source overwrote each other's background; each join now allocates a fresh name.

**Appending to a document that carries its background.** A `.mobix` bundles its PDF and keeps no
external file for it, so "the reference has no file" is not "this notebook has no PDF": the window
reads the bundled bytes and joins them like any other source. A notebook whose pages are only paper
still simply names the picked file. A reference that resolves to **nothing** — the original was moved
or deleted — is refused with the pages left exactly as they were, because appending would re-point the
document at the new PDF and leave every existing page naming a file that cannot be opened. The re-point
lands on the page that *carries* the reference (a notebook with a cover has one) and clears a bundled
copy, so the joined file wins over it.

**A sheet with no background is refused, not printed blank.** Both exporters name the sheet when a
PDF-backed page has no source, no such page number, or no readable bytes: the tablet's `PdfExporter`
throws before writing a byte, and the window's `DesktopPdfExport` refuses before replacing the target,
so the PDF that was there keeps its contents and relinking the PDF is the whole retry.

**The sheet is the crop.** An import sizes a page by the PDF page's **effective rotated CropBox**, which
is the box the export imports that page by, so a mark drawn on screen lands over the ink it was drawn
over. Reading the media box instead made every cropped PDF's sheet larger than its picture.

Evidence: these rules are covered by JVM and renderer tests — `DesktopPdfDurabilityTest`,
`DesktopPdfMergeTest`, `DesktopPdfImportTest`, `DesktopPdfExportTest`, `PdfStoreTest` and
`PdfExportTest`. The tablet's side of the pin is tested on the store only; driving a joined notebook
through close, prune and reopen on an emulator is still owed.
