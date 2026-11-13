# Desktop workstation: the `desktop/` window

> Read when you change the desktop Compose Multiplatform window: its shell and chrome, its tool strips, or how
> it paints a page. What a drag *does* is [desktop-editing.md](desktop-editing.md); what the window does with a
> file is [desktop-documents.md](desktop-documents.md); what the tests prove is
> [testing.md](testing.md).
> For the task: [Installing on Windows 11](guide-install-windows.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What the window is

A **`desktop` Gradle module** (Compose Multiplatform 1.7 on Skiko) that opens the app's own documents and
paints their pages in a native window. Every word the window shows is **English**, like the tablet's UI —
[`ui-modernization.md`](ui-modernization.md) owns that rule. It is the **second shell of the product**,
not a viewer beside it: the model and the codecs are platform-free, so this window opens, edits, saves
and exports the same document the tablet does, through the same code (`mobix-core`) rather than a copy
of it. Windows 11 is the only platform it is built and run on — a product decision, not a limit of the
code.

What it does today:
- Opens **every container the app writes** — gzip `.xopp`, ZIP `.xopp` and native `.mobix` — from the
  toolbar or a path argument, lists its pages, and zooms 25 %–3 200 %. Its **bytes** identify it (`FileKind`,
  a ZIP's `mimetype`), never the extension; its path is canonicalised, so no 8.3 alias survives.
- **Navigates like a workstation, not like a phone**: a header carrying one tab per open document (each
  keeps its own page, zoom and fit), a collapsible 240 dp sidebar of page thumbnails, the current page's
  layers and — for a PDF with an outline — a collapsible **Contents** section filled only when opened
  (`outlineRows`: indented rows leading to their page) — opened by the document itself, only for **more than
  one page**, with `Ctrl + B` remembered per document (`DesktopSidebar`) — the page alone on the
  true-black desk, and a 24 dp status bar with the page counter, the zoom, the fit mode, the pen stream, the
  display's refresh rate with its frame budget (`120 Hz · 8.3 ms frame`) and the container. The chrome is
  the app's **own glass** (the shared tokens, `DesktopTheme`) over a `#000000` desk: a 48 dp header carrying the
  **hamburger menu**, 30 dp tab capsules in the tablet's chip glass (the active one ringed in the accent), the
  search field always beside them, which `Ctrl + F` only hands the keyboard ([search.md](search.md) owns it), the strip's own **`+`** and, on the right,
  **undo, redo, Save and Export PDF** — the mobile bar's four commands, on 36 dp
  capsules with 20 dp glyphs; the band is 8 dp taller than the tablet's token because it carries that row of
  controls as well as the tabs (`DesktopTheme.headerHeight`), under a **native caption painted in the
  band's own composite colour** (`DesktopTitleBar.kt`: immersive dark mode, the caption, border and text
  colours through DWM). It carries **no app name and no icon** (`captionIcon` keeps the art for the taskbar
  only), with the tablet's own glyphs — monoline tools and the chrome's filled marks, one catalogue each — Inter
  with tabular figures, one cast shadow under each band and one under the sheet (its halo is drawn in **screen space**, around the page's on-screen rectangle: a shadow is chrome, so the zoom must not scale it and the thumbnail raster, whose surface *is* the page, must not pay for it). The art is the
  tablet's launcher icon **without its navy field**, enlarged inside its tile so the mark reads at taskbar
  size (`desktop/icon/`).
  Wheel scrolls, `Ctrl` + wheel zooms **at the cursor**, middle-drag or `Space` + drag pans, and each has a
  key too (`docs/ui-modernization.md` owns the table). A drag at the canvas's border **scrolls the view**,
  eased rather than stepped (`EdgePan.kt`), so a marquee, a figure or a reflow reaches past one screenful.
  A tab opens in **`AUTO` fit**, which asks what the page *is*: a sheet (A4 is 1.41 high) is shown whole, and
  a long strip — the tablet writes one 850 × 2976 pt page for a lecture that fills the sheet — is fitted to
  its **width**, because the whole page lands at 27 %, where a 12 pt hand is three pixels tall. The status
  bar names the fit in use, `Ctrl + 0` still means the whole sheet, and a width-fitted page stays fitted
  while you read down it. Thumbnails come from the
  same `PageView` that paints the page — a preview cannot disagree with what it previews — and are cached
  once per page per tab.  The sidebar's layer list carries an **eye per layer** (`LayerEye`): switching one off
  skips it in the painter *and* in the thumbnail, and it stays listed in the muted ink — switched off, not gone;
  that is **view** state, per tab and page (`DesktopTab.hiddenOn`), nothing written to the file. The same stack
  has a door on the band — **Layers**, in the capsule beside Pages — where the picked layer wears the steel and
  receives new ink, and the rows add, rename, reorder, merge, delete and carry a selection onto it, one step each.
  A thumbnail is painted **once the hand stops**, on the sidebar's own lane: an edit drops the page's
  bitmap and `thumbnailRevision` re-renders it 600 ms later — **29–39 ms** a page, where painting it in
  composition dropped a frame at every pause between two words. It goes onto a **raster Skia surface**
  through `drawPageContent`, never a second `ImageComposeScene` (a scene rendered from another thread while
  the window's is live tears it down); until it lands the preview shows the page the burst found.
- Shows a `.mobix` **page by page**: the list is lazy and pages are inflated only as they scroll into
  view, so a notebook opens without reading it; a pressed-for stroke count is left out, never guessed.
- Paints solid/ruled/graph/dotted/**isometric** paper (`config`'s `r1`, else `BackgroundGrid`'s defaults;
  an isometric sheet strokes `BackgroundGrid.isometric`'s segments — the mesh the tablet and the SVG/PDF
  exports also draw — so a ±30° sheet is ruled by shared geometry), pen strokes with their pressure taper
  (`InkSmoothing.kt`), highlighter bands with multiply, dashed/dotted line styles, and text boxes.
- **Writes the document back** (`Ctrl + S`; `Ctrl + Shift + S` is Save As). The bytes are
  `DocumentEncoder.write` from `mobix-core` — the same three format arms the tablet saves through —
  staged into a temp file beside the target and then moved over it, so a crash mid-write cannot leave a
  truncated notebook. It encodes a **snapshot** taken when the command was given, so the page stays drawn and
  keeps taking strokes while the bytes go out; the commit is a rename, and the canvas is still for exactly
  that. A `.mobix` is read from a **copy** in the cache, so the file itself is never held open and a save
  never closes the reader; a `.xopp` holds no handle at all. Either way the tab keeps the model it wrote and
  stays dirty until the next save publishes the rest. The
  **container is the one it was opened as** (`DesktopContainer` → `SaveFormat`), and Save As is the only
  place it changes: the save window (`SaveAsPicker.kt`) asks which of the three in its own type box, opened
  on the document's own container, and names the file with the picked suffix (`note` is `note.mobix`). That
  type is written whatever the name field ends up saying; a suffix typed over used to undo it in silence.
  A save writes the document **with its edits** and a `.mobix` repack streams untouched pages out of the
  archive it opened from, so only the pages that changed are rebuilt. The tab is re-homed onto the file it wrote.
- **Exports it too** — `Ctrl + E` (or the menu's *Export PDF…*) writes one PDF; the menu's **Export As**
  writes one PNG per page (2 px per point, painted by the canvas's own painter), one SVG per page, or the
  notebook as a `.pmobix`; all use the shared plan and see the window's unsaved pages.
- **Reads a PDF's text and outline** — one `PDDocument.load` per file feeds `DesktopPdfText`/`DesktopPdfOutline`
  off the UI thread, cached by reference and length (`DesktopPdfIndexCache`, soft-referenced), and published on
  the tab as `DesktopPdfIndex`: the words **Select text (PDF)** selects and the magnetic highlighter snaps to,
  and the Contents rows above. A scanned, corrupt or missing PDF answers an empty index, never an exception.
- **Owns the PDF's pages as the document's pages** — the sidebar lists every sheet with the PDF page it
  carries drawn on it, a row's close glyph drops that sheet, **Move page up / Move page down** in the Pages
  menu reorder them, and a thumbnail **dragged** onto another row lands there (the pointer's row, with the
  list scrolling under it while the pointer rests against an edge — `PageReorder`,
  `PageSidebarDragTest`); each is one `PageOps` edit and one undo step (`DesktopPdfPageListTest`).
  **Manage pages…** takes a run at once: *pages 3 to 7* moved so its first page starts where you type, or
  removed, refused when that would empty the document (`PageRanges`, `DesktopPageRangeTest`). Several rows
  are also picked **in the list itself** — a plain click picks one, **Ctrl** adds or drops one, **Shift**
  takes the run from the last one clicked — and the pick is what a drag then carries, as one block, and
  what the bar that replaces the layers list deletes, in a single undo step either way
  (`PageSelection`, `DesktopPages`, `PageSelectionSceneTest`); the list's **`+` row** inserts a blank sheet,
  **Duplicate page** copies one under fresh ids, and the bar's **Copy/Cut/Paste** carry pages between tabs
  (`DesktopPageInsertTest`, `DesktopPageClipboardTest`). A page
  that names no PDF of its own is drawn through the reference the document carries on one page
  (`PdfPageReferences`, the shape desktop Xournal++ reads by inheritance) — that is what makes pages two
  onwards of an import show at all — and removing the page that carries the reference hands it to the
  first pdf page that survives, so a delete never takes the PDF out of the document. A save and a PDF
  export both follow the order the list has.
- **Imports a PDF as pages to write on** — the menu's *Import PDF* turns a `.pdf` into a document of its
  own (one page per PDF page, the sheet the PDF page's size, in a new tab) or adds its pages after the open
  document as **one undo step** that drops a lone untouched blank sheet. It asks **which** pages first
  (`Pages 3 to 7 of 412`), so bringing a chapter out of a long scan does not mean importing the rest
  (`ImportPdfRequest`, `DesktopPdfImportRangeTest`), and a range that cannot be used says why instead of
  importing something else. A document already backed by a PDF
  has the second one **joined** into it, since a `.xopp` references exactly one, and a save carries the PDF
  with the document: a `.mobix` normally carries it inside, a zipped `.xopp` attaches it, a plain `.xopp` names the file
  beside it.
- **Draws and erases on the page** — pen (`B`), highlighter (`H`) and eraser (`E`). A primary drag is
  fed to the shared `InkPointerRouter` (`mobix-core`) as a stream of `InkPointer`s, so what a press
  means, how hard the pen draws and when a drag is a stroke rather than a tap are the same decisions the
tablet makes; the window only converts a Compose pointer into that vocabulary. **Rest the pen still for
300 ms** and the stroke perfects itself: the preview is replaced by the recognised figure (`ShapeDwell`,
the rule both shells share), a stroke that grows afterwards gets its own ink back, and the lift writes a
closed polygon as a living shape or a line, arrow or arc as the cleaned stroke. The stroke is
previewed while it is drawn and committed on lift as **one edit** through the tab's page window
  (`PageWindow`, moved into `mobix-core` and shared with the tablet's canvas): the edited page is
  materialised on its own, kept in an overlay eviction never touches, and adopted into the document the
  save writes. So a `.mobix` edit inflates exactly the page it touched, the stroke lands in the file (the
  page's thumbnail catches up when the burst goes quiet, above), and a lift costs the stroke and not the
  sheet — `PagePicture` records only the ink appended on top, see *A stroke costs the stroke*. A press
  that lands on the desk starts nothing, because the desk is not paper. The eraser is the shared `PageEraser`
  in standard mode — it rubs out the part of a stroke it crosses and splits it in two, at a tip the pen width
  sizes — and a highlighter band keeps its multiply. Its bites are gathered one frame at a time
  (`EraseBatch`): one edit per *move* meant two or three page copies and picture records per frame. Pressure is the shared filter's, and the driver's own stream is read beside it
  ([desktop-tablet.md](desktop-tablet.md) owns what each stream carries). `DesktopPointerSceneTest` proves
  the wire: real pointer events pushed through an `ImageComposeScene` into this canvas, and the stroke that
  reaches the model carries the filtered pressure ramp (0.2 → 0.5 → 1.0 becomes 0.8 → 2.0 → 4.0 pt at a
  4 pt pen).
- **Takes it back and puts it again** — `Ctrl + Z` / `Ctrl + Shift + Z`, each tab with its own
  `EditHistory` of document snapshots. One gesture is one step (a stroke records on its lift, an eraser
  drag on its first bite, and a drag that touches no ink records nothing), undo restores the page window
  with the document so the page that comes back is the page that was there, and both commands are
  **unavailable** — not merely no-ops — while the stack is empty, which is the state a header control
  would grey out. A save starts a fresh history: it closed the archive whose pages the old steps named.
- **Keeps one ink per tool, as the tablet does** — the pen and the highlighter each own their colour, their
  width, their **stroke pattern** (solid, dashed, dash-dot, dotted — the tablet's pen settings, offered in
  the colour flyout and baked into every stroke it writes) and their three favourites (`DesktopInks`,
  `DesktopFavourites`), so a yellow highlighter leaves the pencil black, `1`–`3` take the terna of the tool
  in hand, and both inks are remembered in the settings file. **A figure, a circuit or a placement draws
  in the pen's ink whatever tool is in hand** (`DesktopShellState.shapeInk`): a marker never turns a
  rectangle yellow, exactly as on the tablet, and the highlighter stays its own case. The palette itself is
  shared, because a palette is what the user has, not what a tool is; the editable custom slot is its
  property too, not the ink's. `DesktopInkSplitTest` pins the pair.
- **Paints living entities** — tables, plots, parametric shapes, circuit components and formulas — by running
  the same compiler the Android canvas and every export use (`LivingInk`, platform-free in `mobix-core`). A
  table on the PC is the table on the tablet; an entity this build cannot draw is reported, not drawn blank.
- **Paints page pictures**: a pixmap background from the file the page names, and an annotated **PDF page**
  rasterised with PDFBox (about 144 dpi) under the ink; a `.mobix`'s bundled bytes win over the original.
- **Shows the strips where the Settings say**: a **52 dp rail capsule in a 64 dp lane** beside the page
  and a **48 dp ribbon capsule in a 56 dp band** across the top — **Left**, **Right** or **Top**
  (`stripPlacement`), re-laid live. Both **take a lane** (2026-10-28): the lane and the ribbon's band are
  the header's own material, the canvas is handed what is left, and no bar lies on the sheet.
  `Ctrl+,` opens the panel that sets it — [`desktop-settings.md`](desktop-settings.md)
  owns what each section answers; the header has no chooser, because moving the strips is a setting.
  The capsule stands **in** its band — its own 6 dp lift, the family's rim and the nearly solid `STRIP_GLASS_ALPHA` fill — and the band under it is the window's glass at `DESKTOP_BAND_ALPHA`, so the
  two layers read as the tablet's rail: a pill of glass in a bar. Each
  header control is the same object at 36 dp. Both placements draw the same slots in the order, glyphs and
  shortcuts of [`ui-modernization.md`](ui-modernization.md) §3, the picked slot a 10 dp squircle in the
  `#38383E` and a name-and-hotkey pill on hover.
  **A slot is a tool *group*, as on the tablet**: the **eraser slot** holds its two members (partial,
  whole-stroke), the **Text slot** holds prose and formula, and pen, highlighter, the **hand**, vertical
  space, the **guides** (one slot for the setsquare and the compass, laid on the page where you look,
  drawn in the shared amber with an aim readout while it is held), each STEM slot and **every figure**
  (Line, Arrow, Double arrow, Rectangle, Ellipse, the
  triangle, the trapezoid and the Polygon, then Axes) stand alone — nine figure slots, one click each,
  the tablet's rail geometry. A grouped slot wears a chevron, and the chevron (or a right-click, a hold, a **second click on the slot already
  picked**, or `Alt`+`Down`) opens its **glass picker**. **One click never opens a submenu**: the first
  click arms the slot, the second asks; a circuit slot obeys the same rule, since its family palette is
  its submenu; a figure slot's picker is its own variants (4 triangle kinds, 3 trapezoid ones, 3–12 sides)
  and the slot wears the chosen one. Picking a row re-faces the slot and closes the picker, a click on a
  slot whose picker is open closes it too (that click is the press the popup dismisses on, so it may not
  reopen what it just closed), `Esc` closes it as well, and no key ever changes meaning. **A tool whose behaviour has not landed gets no slot** (`ToolGroupsTest` checks both ways).
  The slot is **remembered**: the pick is written as `interface.pickedTool` and the window reopens on it, with the colour and width that slot keeps, so a session resumes at the pen, the marker or the eraser it
  was left on instead of always at the pencil.
- **Records and plays back audio**, in the tablet's own capsule: a microphone/stop, a pause and a play
  sitting **immediately left of Save**, with the take's `mm:ss` and level beside them, and the take
  indicator plus any one-sentence report on the status bar. Recordings are `.wav` sidecars in
  `%APPDATA%\MobiXournal\audio`, adopted on open and written back on save, so an edit here cannot lose them
  ([`audio.md`](audio.md) owns the format). A clip makes the capsule the **transport** — readout, scrubber,
  speed (1×–2×) — and while it sounds the page is painted **per stroke**, the writing following the
  recording. **Audio tap** — the pill's Play, or `Y` — replays from the point touched, and a take or a clip belongs to **its tab**; Settings → Audio picks the devices.
- **Opens its file commands from a menu shaped like the tablet's**: the header's hamburger opens one
  glass panel — four **tiles** of art over a word (New, Open, Save, Save As), then the tablet's **two rows
  of chips**: **Import PDF** and **Export PDF** in the document red (the same file, coming in and going
  out), and **Export As** in the accent, which raises a card of its own with the three answers — **PNG**, **SVG** and **`.pmobix`** (one file per page or the whole notebook, into a folder you pick); every file dialog here is the Windows shell's own — Explorer's surface, a type box, a folder mode — with Java's dialogs as the fallback where the shell has none.
  **A hairline**, then the tablet's **Pen block**: its heading (the pen glyph beside the word) opens the
  Stylus page, and under it the pen's **drawing** answers in the page's own order — **Pressure
  sensitivity**, the **preset carousel** with its × and `+`, **Minimum pressure**, **Multiplier**, and
  **Reset to defaults** as a command row in the accent. Which **stream** the pen is read from is not in the
  menu: that is the driver's fact, not the stroke's, and it stays on the Stylus page the heading opens.
  The rows are the page's rows at the menu's 34 dp
  line, which is what keeps *Pressure sensitivity* from reading as a heading over its own sliders. Picking a
  preset applies it and closes the panel (the hand was mid-drawing); the name field the `+` opens is the one
  the Stylus page opens, so the menu is the only popup that takes the keyboard. A second hairline, then the
  **page list** and **Settings** rows, each wearing its own mark. Its commands keep their 10 dp inset by
  padding themselves, so a row's hover spans the panel's whole width, as a menu's does. The block carries **nothing about the ink**: a
  colour and a width live in the strip's capsule, and a reset beside a pressure preset made two different
  things share one word. Page as PNG is absent because the window has no such command yet, not because it
  has a different menu (`MenuPanel`; `DesktopMenuTest` clicks every row by
  the panel's own metrics). The tab
  strip's **`+`** makes a new page (A4, graph ruling, one empty layer — `DesktopDocuments`); the document is
  dirty from birth, its `Untitled N.mobix` name waiting for Save to ask where it goes. A window that starts
  with no file named on the command line opens on the **entry screen**, offering the last run's unsaved work.
- **The band owns the page, from one section**: left of the search field, one **Pages** button wearing the
  tablet's ruled sheet (`ChromeGlyph.DESCRIPTION`) opens the phone's menu — **Add/Remove page**, the two page
  steps, **Page background** and **Page format** (A4/A5/Letter/Legal, mm/in/pt, **Default**, **Set as default**),
  the tablet's `PageOps` at one undo step each (`DesktopPagesTest`); the strip ends with its last tool.
- **Marks the pins a connection would join, and joins them**: while a connector is being **drawn** — the
  Line or Arrow tool, or a circuit family — every compatible pin wears an accent dot and the figure's own
  pins show as it is laid down, as the tablet draws them — but only when the page holds something to join; ends are magnetised onto the nearest pin
  (`snapWireEnds`, `circuitSpan`; an arrow reaches every family, `FigurePins`), a selected connector shows
  its targets while it is dragged (`SelectionMagnet`, `selectionMoving`), and a selected **figure** wears its own pins at rest while the page holds one that could join it (`selectionPins`) — none with nothing to join.
  A dragged part lights the pin its magnet has caught and holds it across the drag (`PinMagnet`), and a wire being drawn or a part being placed lights one pin per end (`EndsMagnet`, `magnetTargets`), so every join is visible before the release. A **selected** part keeps its pins marked, so the dots do not vanish with the slot that placed it. A press
  **on** a pin picks the part up, and on a selected part's own pin the drag **moves** it, not scales
  (`PinPress`, `pressesOwnPin`: a scale never magnetises). A moved
  part's **own** pins are never its targets (by position, not id), and a moved selection aims at the
  **ends of the page's wires** too (`collectMoveTargets`), closing that joint (`SelectionMovePaintTest`).
  The rule is **circuit↔line or circuit↔circuit**: a *moved wire* keeps to pins (`PinTarget.isWireEnd`) —
  a line is a connector, not a terminal, so two lines dragged near each other stay two lines.
- **One material and one icon set, not a lookalike**: every band takes the band lift, every floating
  surface the drawn rim — and every one is a **menu that never takes the keyboard** (`FloatingSurface`,
  `DesktopPopupFocusTest`), so the shortcuts survive a flyout or a hover pill. Both keep the **shared fill
  with no gradient of the window's own** — a sheen made the same charcoal read a shade lighter on the PC.
  Glyphs are the tablet's `mobix-core` `ToolGlyphCatalogue` strokes, drawn in the chrome's ink;
  `ChromeMaterialRenderTest` proves the lift, the flat fill and the rim as pixels.
- Consumes **`mobix-core`**, the platform-free module — model, XML/JSON layers, `.xopp` and `.mobix`
  codecs, shared paper/ink geometry, and the `.mobix` engine behind
  [`mobix-streaming-api.md`](mobix-streaming-api.md). `DesktopMobixOpenTest` opens a 40-page notebook
  through it without the Android app on the classpath, which is also how the window opens them.

### Opening, saving and the crash snapshot

What the window does with a **file** — the loader's verdict on a file's bytes, the sentences it shows for a
refused open and a refused save, the dirty dot and the question it raises, and the snapshot that brings an
interrupted session back — is [desktop-documents.md](desktop-documents.md)'s.

### What a drag does

The figure slots, the eraser's two members, vertical space, placing a living element and the selection — with
its handles, its action bar and the inspector lane — are [desktop-editing.md](desktop-editing.md)'s: they are
the window's editing surface and read as one topic, which is why they no longer live in this file.

### A stroke costs the stroke

The page is a Skia picture (PagePicture), and a picture is immutable. The picture is a **base**
plus **one tail picture per lift**: each tail holds elements appended to the last layer, so a long word
does not stutter. Edits to existing ink (erase, reflow, moved stroke) re-record the sheet and start a new
base; every stroke's outline is cached by identity (StrokePathCache), and the run folds into a base every
160 elements. PagePictureTest pins counters, proves base plus tails matches direct paint, and checks append
rules; DesktopWritingCostTest pins frame costs.

### Scrolling and zooming a dense page

The page is recorded **once** per document state, not per viewport: `PagePicture` records `udiff.xopp` at
**document scale** and every frame replays it under the canvas's own matrix, so a scroll is a translate and
a zoom is a scale. Scaling a *picture* is not resampling (the commands are vectors) and `PagePictureTest`
measures the difference against the direct paint rather than assuming it; `--probe-scroll` with `--diagnose`
measured **3.6–4.3 ms mean before** and **0.0–0.2 ms after** ([desktop-tablet.md](desktop-tablet.md) owns
the numbers).

### What the window does not do

- No search inside a **picture** (no text layer); typed text, PDF words and ink are ([search.md](search.md)).
- No **absolute pen mapping** — the readings are folded into the Compose pointer, already transformed by
  the driver's own active area — and the installer is `scripts/desktop-package.ps1` (`docs/tools.md`).

### The graphics tablet

The pen's two Windows streams, the packet path that draws a stroke, the dial's COM handler and the hand's own
gain are [desktop-tablet.md](desktop-tablet.md)'s. The window's side is one rule: the driver's packets draw
the stroke, the mouse Windows emulates never does, one contact is one stroke. The **hand** pans from the same
packets (`PenPacketPan`), one viewport write per frame; a held **Space** takes them from the stroke path.
The pointer over the sheet is the **tool's**: a ring wherever the tool lays ink at a width, the text cursor
for Text (it writes words, not a band), and the hand for the hand.

## How it is tested

Every test runs on a plain JVM host — no emulator, no device — and the pixel renders under
`desktop/build/desktop-render/` are what the assertions read. What each suite proves, and how to run them, is
[testing.md](testing.md).

Why the core is shared, and what has moved into it, is [`codebase.md`](codebase.md#the-shared-core-no-android-in-mobix-core)'s.

## Decisions

Three decisions taken on 2026-10-21, owned by [`ui-modernization.md`](ui-modernization.md): the chrome is
the app's **own glass on a true-black desk** (Mica/Vibrancy would tint the window with the wallpaper and stop
it matching the tablet); **three tool placements ship** — a vertical rail on the left (the default), the
same rail on the right edge for a left hand, and a horizontal ribbon under the header — chosen in
**Settings**, not by a header chooser, where the order of the tools in the strip is set too; and the
tab/view model is Compose **snapshot state**, redrawn by the state it reads instead of a second copy.
