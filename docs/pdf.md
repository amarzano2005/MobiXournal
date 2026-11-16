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
ops as text rather than painting glyphs into an image — so a `<text>` element is typeset by the same
faces (see *Unicode in exported text* below). The text-import path draws with fonts
**bundled as app assets** and embedded per document by `PdfFonts`
(`PDType0Font.load(doc, stream, subset = true)`): PDFBox subsets them into the output, so a CJK or
Cyrillic import renders identically everywhere without bloating the file with a whole face.

The bundled family is **DejaVu Sans** — regular, **bold**, oblique and bold-oblique, the four faces
markdown emphasis needs — and **DejaVu Sans Mono** (monospace, which also sets markdown code), chosen
for broad Unicode coverage at a modest ~3 MB and a permissive licence — Bitstream Vera + Arev, with the
DejaVu changes in the public domain, compatible with the Apache-2.0/OFL-only rule that already ruled
out iText. The app carries them in `app/src/main/assets/fonts/`; the window carries the **same five
files** under `desktop/src/main/resources/fonts/`, where `DesktopPdfFonts` reads them off the classpath,
and each set has the full licence text beside it as `LICENSE.txt`. Codepoints even DejaVu lacks are
**substituted, never fatal**: `GlyphSanitizer` (pure, unit-tested, in `mobix-core` so both emitters run
the one rule; encodability injected as a predicate) maps each unencodable codepoint to U+FFFD — or `?`,
or a space — memoising per codepoint, and iterates by codepoint so an astral character becomes one
substitute rather than two surrogate halves. `PdfFonts.Embedded.measurer` is also what feeds
`TextPaginator`'s injected measurement, so wrapping is measured against the exact font that draws.
Markdown needs the same guarantee across *five* faces at once, so `MarkdownPdfWriter` resolves every
`RunStyle` to an `Embedded` up front and exposes that map both as the layout's `SizedMeasurer` and as
the draw-time font lookup — one table, so a line can never be wider on the page than the width it was
wrapped to.

### Unicode in exported text

A `<text>` element leaves through `PdfPagePlan`, which now decides the **face of each run** and keeps the
characters. A run whose every character encodes in `WinAnsi` — Latin-1's printable range plus the 27
characters CP1252 adds above U+00FF, so `…`, `–`, `€`, `™`, `Š`, `ž`, `ƒ`, `‹`, `›`, `†`, `‡`, `•`, `‰`
and their neighbours — keeps its base-14 family and costs the file nothing. A run holding anything else
(`α`, `Ж`, `H₂`, `e` + U+0301, an emoji, a C1 control) asks for the **bundled** DejaVu face of its
family and slant (`PdfFontRef.Bundled`), which each shell embeds per document and subsets into it
(`PDType0Font.load(doc, stream, subset = true)`): that also writes the `ToUnicode` map, so the run is
drawn **and** selectable **and** extractable. `PdfBundledFace` names the asset path in `mobix-core`
(`fonts/DejaVuSans.ttf`, its bold, oblique and bold-oblique companions, and `fonts/DejaVuSansMono.ttf`),
so the plan stays platform-free and `PdfFonts` puts the file in the document; the licence ships beside
the assets (`app/src/main/assets/fonts/LICENSE.txt` — Bitstream Vera + Arev, DejaVu changes in the
public domain).

Nothing is sanitised at plan time any more. The emitter, the only layer that knows which face it can
draw, keeps `GlyphSanitizer` as the **last resort**: a codepoint even DejaVu lacks becomes the visible
U+FFFD, never `?`.

The guard this replaced was a code **range** (`0x20..0xFF`) applied to the text, and a range is wrong in
both directions — it threw away all 27 of those CP1252 characters, and it let DEL and the C1 controls
through, where WinAnsi decodes `0x80` as `€`. Both halves are measured: `PdfTextEncodingAuditTest` pins
the decision (which face each fixture run asks for, and that a pure-WinAnsi run plans exactly what it
planned before), and `PdfUnicodeExportTest` pins the round trip — paint, **save**, reload, extract with
PDFBox's stripper.

| Input | Face | What the produced PDF does |
|---|---|---|
| `café ñ ½ ²`, and the 18 CP1252 punctuation characters | base-14 | unchanged text, and no font file added |
| `α`, `Ж`, `H₂`, `e` + U+0301, `😀` | bundled DejaVu Sans | drawn, and extracted back character for character |
| `\u0080` (a C1 control) | bundled DejaVu Sans | a substitute glyph, not `€` |

**Both shells, one fixture.** The window resolves the same reference the same way: `DesktopPdfPainter`
loads a `PdfFontRef.Bundled` run through `DesktopPdfFonts` (`PDType0Font.load(doc, stream, true)`, cached per document), so it embeds, subsets and writes the `ToUnicode` map exactly as the app does, and falls back to the same shared `GlyphSanitizer`. The run both are measured against lives in `PdfUnicodeFixture` (`mobix-core` test fixtures) and is read by two tests — `PdfUnicodeExportTest` for
the app's PDFBox port and `DesktopPdfUnicodeTest` for the window's `org.apache.pdfbox` — so a shell that
drifts is a failing test, not an export the owner finds wrong a release later. Both also take the four
cases the old range guard got wrong **one at a time**, so a failure names the one that broke: a character
WinAnsi encodes above U+00FF, a C1 control (`\u0085`, which WinAnsi reads as `…`), a surrogate pair and a
combining mark. Both assert the embedded face is a **subset** as well as `ToUnicode`-mapped: a page of
text that ships a 759 kB face whole is a file nobody can mail.

## Exporting chosen pages

An export used to be the document, always. `ExportPages` (`mobix-core`) is the rule for *which* pages a shell writes:
**all** (the answer both cards open on), **the page in view**, or a set typed in the import card's own grammar
(`PageRanges.selection`). The answer is a list of **notebook** indices, walked in the notebook's order rather than the
order the pages were named in; the per-page work is untouched by the subset — an imported page keeps its source PDF's
vectors and text — and a page whose PDF this export cannot read still refuses the whole file in words, with the bytes
that were there left alone. A page the document no longer has is skipped, and an answer that names nothing is refused
before anything is staged.

**The index goes with them.** `PdfOutlineExport` (`mobix-core`) turns the notebook's tree into the rows both exporters
write as the file's bookmark pane: a destination is the notebook page's **position among the pages written**, so a
reorder reorders the outline with it. A row whose page was not exported keeps its place as a heading while exported
rows still hang under it, and is dropped when nothing does — a bookmark never becomes a link to a sheet the reader did not choose. Titles are the reader's, duplicates included.

## PDF text selection

**PDF text selection.** An imported PDF's **text layer** is extracted on import (off the UI thread)
by `PdfTextExtractor` — a `PDFTextStripper` subclass that turns each positioned glyph into a
`CharBox` and groups them into `PdfWord`s (`PdfWordGrouper`, breaking on whitespace, wide gaps, and
line changes) — into a `PdfTextIndex` threaded to the surface via `setPdfTextIndex` (mirroring
`setPdfSource`). This reuses the **same PDFBox dependency** as export, so **no OCR engine** is needed
for born-digital PDFs; a scanned image-only page yields no words (`hasAnyText` false), and OCR for
that case is a tracked follow-up. Boxes are page-local top-left points (the `.xopp` frame), so they
map to the screen through a `PageBox` exactly like strokes. Selecting a passage is a **gesture, not a mode**:
a finger held still on a word selects an inclusive reading-order range with whatever tool the pen is on
(`PdfLongPress.kt`), so no rail slot has to be picked first. `EditorTool.TEXT_SELECT` and its
`ActiveTool.TEXT_SELECT`/`GestureIntent.SELECT_TEXT` mappings stay decodable for a stored selection or a shortcut (the window's `Shift+S` entry), but the tablet's Select slot no longer offers the tool. The hold is: the press is only a candidate for the platform's long-press window, a move past the touch slop hands it back to the pan, a second finger hands it to the pinch, a pen keeps its own route, and whatever the press began is dropped uncommitted, so no stroke and no undo step survive it. The window reads the same way by **mouse double click** (`PageCanvasPress.kt`): the first click of the pair inks nothing whatever tool is in hand, a click that travels is a drag and hands the stroke to that tool from its own press, and the second press anchors the word and a held drag extends it — with a text box, a figure or a placed element under the pointer answering first. `beginTextSelect`/`textSelectMove` resolve pointer positions
to word indices (`PdfTextSelection`, below), the range is drawn as one washed band per line (`PdfSelectionSpans`: the line's union height, the spaces between its words inside it, columns never bridged, no border per word, nothing animated — only the boundary advances), the
`TextSelectionBar` — **Copy, Highlight and Underline** in compact pills on the shared 40 dp target (`DesignTokens.COMPACT_ACTION_HEIGHT_DP`) rather than a nested Material button, and no Deselect word — puts the text on the Android system clipboard (`copyTextSelection`), **Highlight** writes the passage in the highlighter's ink (`highlightTextSelection`, see [the magnetic highlighter](#the-magnetic-highlighter)) and **Underline** rules it in the pen's (`underlineTextSelection`); on a read-only document the two writing words stay in place, dimmed, with the reason assistive input reads. A **hold that finds no words** brings the pane's own line — reading, a scan with no words, or a failed read with a **Try again** — from one `PdfTextAvailability`, so a walk still running is never called a page without text. The bar
**floats against the words** and waits for the range to **settle**: the surface reports the selected
word boxes' union (`textSelectionScreenRect` → `onTextSelectionReport` → `PaneState`), and
`PdfTextActionPhase` keeps the capsule away while a pointer or a handle still moves the range.
`PdfTextBarPlacement` hangs it from the **band the endpoint is on** — above it when the pane ends below,
clear of the handle, inside the pane, nowhere when no selected line is visible — and the window's
`PdfTextCopyBar` reads the same rule from `PdfTextCopyUi.spans`/`active`/`settled` and washes the same
bands at `DesignTokens.TEXT_SELECTION_WASH_ALPHA`, so neither shell draws a box per word and a passage
never slides or fades as one block. The selection is a **view-only** overlay derived from the PDF,
never part of the `.xopp` document, so it does not affect round-trip.

**The hold's precedence is the canvas's own** (`CanvasGestureRouter`'s order): a grip, an object
selection's lift, a placement, a guide, the marquee and the grab line keep their press, and a pen, a
second finger or a wordless spot never arm; because it writes nothing it runs ahead of the read-only guard.

The geometry is **shared, not forked**: `CharBox`, `PdfWord`, `PdfWordGrouper`, `PdfTextIndex` and the
outline tree live in `mobix-core/src/main/java/com/mobixournal/render/`, each shell keeping only its
PDFBox glue — `PdfTextExtractor`/`PdfOutlineExtractor` over `pdfbox-android`,
`DesktopPdfText`/`DesktopPdfOutline` over `org.apache.pdfbox` with one `PDDocument.load` per file in
`DesktopPdfIndexCache`, off the UI thread. Text is read in **one traversal** on both sides, and the
tablet coalesces requests by **source revision** (`PdfAnalysisJobs`) so two panes asking about one file
join a single walk on a two-thread pool. In the window a press must hit a word (`hitWord`), drags an
inclusive reading-order range and copies it (`Ctrl+C` or the bar); `Esc`, a click off the text, a stroke
and a page change clear it, and neither bar carries a **Deselect** — both offer it as an accessibility
action.

**A press must hit a word; a drag may approach one.** `PdfTextSelection` (`mobix-core`) is the gesture's pure
half: `begin` answers only when the point lands on a word — inside its box, or within the press radius
`PdfHitTolerance` bounds — so whitespace selects nothing instead of the nearest word, while `extend` keeps
nearest-word anchoring for a drag over a line's gap. A `PdfTextRange` keeps the grab and the pointer's word
apart (crossing them reverses the range, not the grab), names the `PdfSheetRef` a page draws — its source plus
`Background.Pdf.pageNo`, never its place in the notebook — and refuses another sheet (`PdfSheetRefTest`).
**The passage's ends are handles.** `PdfTextHandles` (`mobix-core`) puts a dot at the reading start's left edge and one at the reading end's right edge, each drawn at `DesignTokens.TEXT_SELECTION_HANDLE_DP` screen dp and grabbed inside `TEXT_SELECTION_HANDLE_TOUCH_DP` converted at the page's own scale, so the grip is the same at 30 % and at 300 %; dragging one moves that end to `PdfTextIndex.anchorWord`'s nearest word — crossing the other end reverses the passage while the grabbed dot stays the one in the hand, and a counted nudge (the window's arrow key and its accessibility actions) is the same move by one word. A press on a dot is asked before the pan and before the tool (`beginTextHandleDrag`, `pdfTextHandleDrag`), the tablet paints the dots from `PdfTextSelectionHandles`, the window from the focusable `Selection start`/`Selection end` nodes in `DesktopPdfTextOverlay`, whose `avoid` box also keeps the capsule off the endpoint the reader just released. The range never leaves its sheet, and it is dropped when the page list, the PDF source or the page changes (`PageCommands.onPagesChanged`, `DesktopPdfIndex`), with one range per pane.

**Selection ownership.** A PDF word range (sheet, endpoints, plain-text projection) is neither a model-element
selection (stable element refs) nor a focused editor range: one canvas owner per pane, Copy goes to the focused
editor then the active owner and never a stale range; words never become elements or clipboard objects, and
Copy/Highlight/Underline are the base commands of an extensible bar ([selection-actions.md](selection-actions.md)).

## The magnetic highlighter

A highlighter drag over a born-digital page is tested against the same `PdfWord` boxes text selection
uses (`PdfTextIndex`, page-local pt) by `render/MagneticHighlighter.kt` — pure geometry, JVM-tested. Only
a drag that is clearly horizontal qualifies: less than 8 pt, steeper than `0.6 ×` its own width, or over
no text stays exactly as drawn. When it does qualify, the words the pen line crosses are clustered into
lines and each line becomes one constant-width band as tall as its glyph boxes plus 1.5 pt of air. The
canvas previews those bands during the drag (`DrawingSurfacePaint.drawCurrent`) and commits them at
lift-off as ordinary highlighter strokes, so one gesture is still one undo step and the band round-trips
through `.mobix` and `.xopp` as ink. Snapping is a convenience, never a correction the user did not ask
for: a margin mark is not silently turned into a sentence. The window commits and previews the same bands from the same object (`DesktopPdfIndex`'s words, `PenPointer.pdfBands` as the live preview), so a
stylus highlight snaps exactly as a mouse one does.

**Highlight and Underline are one selection's two doors.** The words a selection covers become ordinary strokes on
the tab's **active layer** through the normal commit path, as **one** undo step for a multi-line selection and
the same ink in a PDF export: Highlight takes `MagneticHighlighter.selectionStrokes` — the drag's bands, without
its three-line cap, because a selection may run down a page — in the highlighter's colour; Underline takes
`PdfTextUnderline.strokes`, one constant-width, plain, round-capped `Tool.PEN` rule per line segment, `GAP_PT`
below that segment's own word boxes, in the **pen's** colour and width (`underlineTextSelection`,
`DesktopPdfTextUnderline`). No PDF annotation, no format element: `.mobix`, `.xopp` and an exported PDF carry both
as ink. Copy writes nothing, a read-only document has neither verb, and the model has **no layer lock** to bypass.

## PDF Contents (the outline)

**PDF Contents (the outline).** The same worker that extracts the text layer also reads the PDF's **`/Outlines` bookmark
tree** (`PdfOutlineExtractor`), and both results are cached by file (`PdfOutlineCache` mirrors `PdfTextIndexCache`). An
entry with no destination inherits the page of its nearest descendant that has one. `setPdfOutline` publishes it to the
chrome (`onPdfOutlineChanged`), and the page list lane's foot grows a **Contents** glyph (`PdfContentsButton`) opening
`PdfContentsDialog`, an indented, page-numbered list that folds and filters ([guide-pdf-and-audio.md](guide-pdf-and-
audio.md)). Where a row goes is resolved once for both shells (`OutlineResolution`); what a target means, and why one
that finds nothing leaves its row dimmed, is [mobix-data-model.md](mobix-data-model.md#the-outline-tree)'s.

**Reading a PDF's index still writes nothing**: the PDF file itself is untouched by it.

**The tree is the notebook's, seeded once at import.** `PdfOutlineSeeding` turns the extracted rows into `OutlineTree`
nodes: every row mints its own `bkm_` id, every destination is written as `OutlineTarget.Sheet` — a header included,
with the page it inherited from its first pointing descendant — shifted by the base's page count on a joined import. `imported` adds **one** node titled after the file (final `.pdf` dropped, editable in the card) opening the first page
that arrived, the PDF's rows under it — option off and they join the tree at the top; pages and tree are one undoable
edit, a failed import writes neither, and opening never re-seeds.

**A merge keeps every chapter; a page edit never removes a row.** Branches merge by node id (`MobixOutlineMerge`): a node
only one side has is kept, a shared one keeps the local title and place with children unioned; a move changes no target, a
duplicate mints a page id, a delete keeps the row untargeted (`OutlinePageOps`), and the rows themselves are edited by
`OutlineEdits` — a title is required, a deleted chapter promotes its children, a refusal records no step.

A row is drawn by one rule for both shells, `ContentsRowLayout` (`mobix-core`): the indent a level buys is capped by the
row's own width so the title never drops under its minimum, the page number keeps a column reserved from the widest
number in the list so no title shifts the numbers, depth 0 is the heading face, and a row announces its title and its
page; `ContentsRowLayoutTest` and the window's `DesktopPdfContentsRowTest` hold it. A row the notebook owns does not
act on a tap of its own controls: its **⋯** opens a neutral menu whose Rename and Delete are the only doors to an edit,
and Delete adds nothing but a question above the list (the rows stay behind it), while a source-PDF row keeps the same
action gutter empty because the file gives it no command this app may run.

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

**One rasterisation at a time, in the whole window.** PDFBox is explicit that it is not thread-safe, and the
drawing machinery behind a `PDFRenderer` — the font mapper, the glyph cache — is shared state: two renders at
once answer one document's fonts or widths to the other, and the page arrives with the right layout and the
wrong glyphs. The window has three surfaces that rasterise the same file (the canvas, the page list, the
import card's previews), and they overlapped the moment a PDF-backed document was scrolled with the card
open. Every PDFBox raster in the window therefore goes through `PdfRenderGate`, a lock and nothing else: the
rasters are cached, so serialising them costs no visible latency. The tablet serialises the import card the
same way (`PdfThumbnailCache` renders one page at a time) and needs no wider gate — its `PdfRenderer` draws
pages through the platform, not through PDFBox's shared caches.

**A font the file names and does not carry is rebuilt from its own ToUnicode.** An export can write a
TrueType subset and then not write the program it points at (a truncated download, a half-finished
LibreOffice export), and the file still carries the text operators, the `/Widths` and the `/ToUnicode`: the
layout comes out exactly right and the letters come out as whatever a substitute font has at those codes —
a slide of `#` and `&` over a table that reads perfectly, which a reader reports as *the PDF opens broken*.
`PdfFontRepair` replaces exactly that font dictionary — the page's content stream, vectors, images and
colours are never rebuilt — and gives it a program we do have: the bundled DejaVu face the name asks for when
the family is one the window carries (`PdfDejaVuFaces`: DejaVu Sans in its four styles and DejaVu Sans Mono),
otherwise a base-14 face of the same style (Helvetica, Times, Courier). Its `/Differences` names come from the
font's own ToUnicode and its `/Widths` stay, so the letters are the document's and no advance moves. Style is
not decoration to drop: rebuilding a slide's bold heading, italic note and monospaced listing as one plain
Helvetica is what made a repaired page look bold and stretched in the wrong places. A code the ToUnicode does
not name stays blank, and a family we do not carry changes family — those characters are not recoverable from
the file. `PdfFontRepairTest` holds the three halves: a font without its program draws its letters after the
repair, the rebuilt face keeps the weight, slant and spacing of the one it replaces, and a font with no
ToUnicode is left untouched. Both shells repair (`render/PdfFontRepair.kt` on the tablet): the window does it
on the rasteriser's private document, and the tablet, whose `PdfRenderer` can only be given a file, does it on a copy in the app cache (`render/PdfRepairFiles.kt`) — the document's own bytes are never rewritten on either.
The tablet asks a cheaper question first: `PdfFontProgramScan` reads the bytes and compares every
`/FontFile2` reference with the object headers in the file, so a PDF that carries its programs is opened
without PDFBox at all (measured on this repository's JVM: 4–17 ms of scanning against 41–119 ms of parsing for
files up to 2 MB, and 76 ms against 300 ms for a 19 MB one). The copy is written only when a reference
dangles, which is what a truncated export looks like.

Evidence: these rules are covered by JVM and renderer tests — `DesktopPdfDurabilityTest`,
`DesktopPdfMergeTest`, `DesktopPdfImportTest`, `DesktopPdfExportTest`, `PdfStoreTest` and
`PdfExportTest`. The tablet's side of the pin is tested on the store only; driving a joined notebook
through close, prune and reopen on an emulator is still owed.

## Importing a PDF: the slow half, and the tab it lands on

Reading a PDF is the import that can take seconds — a 456 kB file became 1500 sheets in the emulator run
below — so the work is split from what it produces. `preparePdfImport` copies the bytes into the store,
builds the pages and, when the document already sits on a PDF, merges the two files into the joined one;
it asks nothing of the canvas and runs on a worker. What it returns is a `PdfImportPlan`, and
`applyPdfImport` puts it on the canvas on the UI thread — as **one** undoable edit, because the pages are
already built. That second half is the only one that touches a `DrawingSurfaceView`.

Each entry point reaches the split from where it already has the bytes: the **import card** (Menu → Import
PDF, which asks which pages and where they go) builds its plan inside `inBackground`, behind the card's
progress note; a PDF **handed over by another app or dropped on the page** (`importPdf`, `onExternalPdf`) is
fetched and prepared on the staging worker, where the whole adoption once ran; and a PDF **opened as a new
document** (`loadDocument`) has its plan built by the staging worker that has just read the file, so the
open's callback only applies it.

**The pages land on the tab that asked.** An append continues *that* document, so `applyPdfImport` looks the
tab up by id and, if the reader is on another one, brings it back onto the canvas before applying; a tab
closed while its pages were being read has nowhere to put them and the import is dropped. The progress note
is modal, so this rule makes the window's own decision explicit instead of a side effect of the note.

**A failed import changes nothing.** The plan is built before anything is applied, so a corrupt file, a
merge that cannot read its base, or a copy that cannot be written leaves the open document exactly as it
was: the copy made on the way is deleted, and the note says the import failed rather than letting it half
land.

Verified on the `Pixel_Tablet` emulator: a 120-page and a 1500-page PDF opened from Open…, a 3-page PDF
appended by the card onto the 120-page document and again onto the 1500-page one (1503 pages); the merge ran
behind the `Importing huge.pdf…` note with no ANR or exception in `logcat`; and a corrupt `.pdf` reported
`PDF import failed: file not in PDF format or corrupted`, the document still at its 1623 pages.
