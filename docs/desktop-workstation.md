# Desktop workstation: the `desktop/` window

> Read when you change the desktop Compose Multiplatform window: its shell and chrome, its tool strips, how
> it opens a document, or how it paints a page.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What the window is

A **`desktop` Gradle module** (Compose Multiplatform 1.7 on Skiko) that opens the app's own documents and
paints their pages in a native window. Every word the window shows is **English**, like the tablet's UI —
[`ui-modernization.md`](ui-modernization.md) owns that rule. It is the feasibility proof this spoke was
written against: the model and the codecs are platform-free, so a PC window can show the same page the
Android editor shows. Windows 11 is the only platform it is built and run on.

What it does today:
- Opens **every container the app writes** — gzip `.xopp`, self-contained ZIP `.xopp` and native `.mobix` —
  from the toolbar or a path argument, lists its pages, and zooms 25 %–3 200 %. The file is identified by
  its **bytes** (`FileKind` plus a ZIP's `mimetype`), never its extension, so a rename still opens.
- **Navigates like a workstation, not like a phone**: a header carrying one tab per open document (each
  tab keeps its own page, zoom and fit, so going back to a tab lands where you left it), a collapsible
  240 dp sidebar of page thumbnails and the current page's layers — opened by the document itself, only for
  **more than one page**, with `Ctrl + B` remembered per document (`DesktopSidebar`) — the page alone on the
  true-black desk, and a 24 dp status bar with the page counter, the zoom, the fit mode, the pen stream, the
  display's refresh rate with its frame budget (`120 Hz · 8.3 ms frame`) and the container. The chrome is
  the app's **own glass** (the shared tokens, `DesktopTheme`) over a `#000000` desk: a 48 dp header carrying the
  **hamburger menu**, 30 dp tab capsules (the active one rimmed like the object it is), the strip's own
  **`+`** and, on the right, **undo, redo, Save and Export PDF** — the mobile bar's four commands, on 36 dp
  capsules with 20 dp glyphs; the band is 8 dp taller than the tablet's token because it carries that row of
  controls as well as the tabs (`DesktopTheme.headerHeight`), under a **native caption painted in the
  band's own composite colour** (`DesktopTitleBar.kt`: immersive dark mode, the caption, border and text
  colours through DWM). It carries **no app name and no icon** (`captionIcon` keeps the art for the taskbar
  only), with the tablet's own glyphs — monoline tools and the chrome's filled marks, one catalogue each — Inter
  with tabular figures, one cast shadow under each band and one under the sheet. The art is the
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
  once per page per tab.  The sidebar's layer list carries an **eye per layer** (`LayerEye`): switching one off skips it in the
  painter *and* in the thumbnail, and the layer stays listed in the muted ink — switched off, not gone.
  That is **view** state, per tab and page (`DesktopTab.hiddenOn`), nothing written to the file; a hidden
  layer's thumbnail is painted fresh, so a preview never shows a layer the sheet is missing.
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
  truncated notebook. A save over the document's own file stages first, releases the archive and opens
  it again after the move, because Windows refuses to replace a file the process still holds and a
  `.mobix` tab holds its own for as long as its pages stay lazy. The **container is the one it was
  opened as** (`DesktopContainer` → `SaveFormat`), and Save As is the only place it changes: the name
  the user gives it decides (`.mobix` writes the archive, `.xopp` the gzip form, a packaged `.xopp` stays
  packaged, an unrecognised name keeps what the document had), and the tab is re-homed onto the file it
  just wrote. A save writes the document **with its edits**: a page the window drew on is the page the
  file gets, and a `.mobix` repack still streams the pages nobody touched out of the archive.
- **Exports it too** — `Ctrl + E` (or the menu's *Export PDF…*) writes one PDF, and *Export pages as SVG…*
  one file per page beside the notebook; both use the shared plan and see the window's unsaved pages.
- **Draws and erases on the page** — pen (`B`), highlighter (`H`) and eraser (`E`). A primary drag is
  fed to the shared `InkPointerRouter` (`mobix-core`) as a stream of `InkPointer`s, so what a press
  means, how hard the pen draws and when a drag is a stroke rather than a tap are the same decisions the
  tablet makes; the window only converts a Compose pointer into that vocabulary. The stroke is
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
  and a **48 dp ribbon capsule in a 56 dp band** under the header — Side, Top or Both, on either edge
  (`stripPlacement`, `railSide`), re-laid live. `Ctrl+,` opens the panel that sets it, and
  [`desktop-settings.md`](desktop-settings.md) owns what each of its sections answers and which of them
  the window cannot yet; the header has no chooser, because moving the strips is a setting.
  The lane is **air, not glass over glass**: painting it too stacked two sheets, rail #161618.
  The strip is a **capsule** with its own 6 dp lift and the family's rim, so
  its fill is the window's band `#0C0C0D` (the glass at `DESKTOP_BAND_ALPHA`, blacker than the tablet's
  `#131314`), and each header control is the same object at 36 dp. Both placements draw the same slots,
  in the order, glyphs and shortcuts of [`ui-modernization.md`](ui-modernization.md) §3, with the picked
  slot a 10 dp squircle in the steel `#38383E` and a name-and-hotkey pill on hover.
  **A slot is a tool *group*, as on the tablet**: the **eraser slot** holds its two members (partial,
  whole-stroke), the **Text slot** holds prose and formula, and pen, highlighter, the **hand**, vertical
  space, each STEM slot and **every figure** (Line, Arrow, Double arrow, Rectangle, Ellipse, Spline, Axes)
  stand alone — seven figure slots, one click each and no submenu, the tablet's rail geometry. A grouped
  slot wears a chevron, and the chevron (or a right-click, a hold, a **second click on the slot already
  picked**, or `Alt`+`Down`) opens its **glass picker**. **One click never opens a submenu**: the first
  click arms the slot, the second asks; a circuit slot obeys the same rule, since its family palette is
  its submenu. Picking a row re-faces the slot and closes the picker, `Esc` closes it too, and no key ever
  changes meaning. **A tool whose behaviour has not landed gets no slot** (`ToolGroupsTest` checks both ways).
- **Opens its file commands from a menu shaped like the tablet's**: the header's hamburger opens one
  glass panel — four **tiles** of art over a word (New, Open, Save, Save As), **Export PDF as a
  document-red chip** and Export SVG in the accent, then the tablet's **Pen block** (its heading opens the
  colour panel, and one **Default** chip puts the tool back on its factory ink), a hairline, the page
  list's row and **Settings**. Import PDF and Page as PNG are absent because the window has no such
  command yet, not because it has a different menu (`MenuPanel`; `DesktopMenuTest` clicks every row by
  the panel's own metrics). The tab
  strip's **`+`** makes a new page (A4, graph ruling, one empty layer — `DesktopDocuments`) and a window
  that starts without a recovered snapshot or a file opens one, so the default screen is a sheet to write
  on; the document is dirty from birth, its `Untitled N.xopp` name waiting for Save to ask where it goes.
- **Owns the page, from one section**: after a divider, one **Pages** slot wearing the tablet's ruled sheet
  (`ChromeGlyph.DESCRIPTION`) opens the phone's menu — **Add/Remove page**, **Previous/Next**, **Page
  background** and **Page format** (A4/A5/Letter/Legal, mm/in/pt, **Default** and **Set as default**). Its
  commands are the tablet's `PageOps`, one undo step each (`DesktopPagesTest` adds, removes and steps).
- **Marks the pins a connection would join, and joins them**: while a connector is armed — the Line tool or
  a circuit family — every pin wears an accent dot and the part being pulled out shows its own, as the
  tablet draws them; the ends of a wire and of a placed part are **magnetised** onto the nearest pin
  (`snapWireEnds`, `circuitSpan`), and so is a part **selected after insertion** when it is dragged
  (`SelectionMagnet`), so what was aimed at is what is joined (`CircuitGestureTest`, `SelectionMagnetTest`).
  A **selected** part keeps its pins marked, so the dots do not vanish with the slot that placed it. A press
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

### Opening, and what a failed open says

`DocumentLoader` picks a file's family by its bytes and its `mimetype` entry, and `describeOpenFailure`
decides what the window says when the open is refused. Three cases, three sentences: an
`UnsupportedOperationException` is the loader's own refusal and keeps its words (`Formato non
supportato`); an `OutOfMemoryError` is reported as memory, because a page taller than the heap is not a
corrupt notebook; and a **`LinkageError`** is reported as *the window being stale*, not the file being
broken. That last case is real — a run produced `NoClassDefFoundError:
com/mobixournal/desktop/DocumentLoader$openMobix$1`, and the notebook was untouched. It is a fact of how
the preview is launched: `:desktop:run` executes `build/classes`, so a Gradle build during a session
rewrites the class files under the live JVM and any synthetic class the JVM had not loaded yet is gone.
Closing the window and relaunching is the fix; the same file opens in ~40 ms in a fresh process.

A failed open **never takes the document on screen with it**: the report is a card at the top of the
canvas, dismissed with a click or `Esc`, while the page, the tab and the sidebar stay as they were. Its
fill is opaque rather than the floating family's 0.88, because it straddles the desk and the sheet — at
0.88 the part over the page read a tone lighter, as two materials split down the middle.

### Saving, and what a refused save says

A save can be refused before anything is written, and the sentence says which case it is
(`describeSaveFailure`). A **packaged `.xopp` whose background lives inside the package** is the one this
window cannot write faithfully — it has no image or PDF store to bundle one back out of — so it is refused
with the two ways round it (a gzip `.xopp` or a `.mobix`) rather than written with a reference to an entry
it did not produce; the file stays byte-identical, because only a staging file that is never committed existed. An **I/O failure** says so and adds that the document on
disk was left as it was — the staged-then-moved write makes that true. A **`LinkageError`** is the window
being stale, as on an open. `DesktopSaveTest` pins all three, and byte-compares the original to prove a
refused save touched nothing.

A notebook **opened from Explorer** arrives as the command line's tail, and an unquoted path is split at every run of spaces — with the run **squeezed into one**, so `…/09)  21-10-2026/…` reaches `main` as `09) 21-10-2026`. `initialDocument` therefore reads the line Windows actually wrote (`rawCommandLine`, `kernel32!GetCommandLineW`) before it rearranges the arguments, so a double-clicked notebook opens with no dialog at all; `--args` splits its value on spaces too, and only when the line answers nothing are those pieces put back — `launchDocument` searching by name as a last resort, never by guessing.

### Unsaved edits: the dot, the question, and a snapshot

A tab is **dirty** when the document holds commands its file does not, and the window does not answer that
with a flag somebody clears: it compares the history **state id** with the **save point** recorded on open and
on every save (`EditHistory.stateId`). Undoing back to the saved state makes the tab clean again, and a new
edit gets a new id even when the bytes match. The strip draws it as one accent dot after the label — a status
readout, absent when the tab is clean.

Closing a dirty tab (its `×`, `Ctrl+W`) or the window (its X) is a **question**, not a close: *Save*,
*Discard*, *Cancel*, in the floating glass over the page, naming the document at stake. The X goes through
the same path (`requestExit`), so it cannot bypass the question; with several dirty tabs the dialog stays up
and asks about the next one, and the window closes only when none is left. A failed save **keeps the tab open
and dirty** with the reason on screen — the one outcome that cannot lose the work. `Ctrl+S` is unavailable
while the file already holds the document.

While a tab is dirty the window also writes a **crash snapshot**: a self-contained `.mobix` in
`%APPDATA%\MobiXournal\recovery\`, keyed by a digest of the file's path, with a two-line `.origin` sidecar
naming the tab and the file. It is written once per quiet burst of edits (three seconds), not per stroke,
because a snapshot is the whole notebook. A start that finds one opens it as `… (recovered)`, dirty, standing
for the original path: `loadRecovered` keeps the **origin** as the save target and the **snapshot** as the
repack source, so a save rebuilds the pages from the recovered copy instead of copying the file's older ones
back over them, and it is encoded on a background thread — a whole notebook on the UI thread froze the window
in the middle of a pause. Saving, discarding or closing the tab deletes the snapshot; nothing in `recovery/`
is a save.

### Figures, whole-stroke erasing and vertical space

Four more gestures beside the ink, all built on `mobix-core` so the window has no geometry of its own:

- **Seven geometry slots draw figures.** Line, Arrow, Double arrow, Axes (the shared
  `ShapeKind.COORDINATE_AXIS`), Rectangle, Ellipse and Spline each turn one drag into an ordinary
  constant-width stroke through `ShapeBuilder` — the tablet's own builder — so a figure made here
  round-trips through `.xopp` as ink. The preview is the **same draft list** the sheet already paints,
  `Shift` snaps the drag's direction to 45°, a drag shorter than 4 pt draws nothing, and `Esc` cancels
  through `DesktopShellState.liveDrag`. With a geometry slot picked the tablet's **packets stand
  aside**: a pen contact must not ink under a figure being dragged.
- **The eraser's two members are two slots.** `Eraser` (`E`) cuts the disc's path out of the stroke it
  crosses; `Eraser (whole)` (`Shift+E`) removes every stroke the disc touches. They differ by
  `DesktopTool.eraserMode()` (`EraserMode.STANDARD` / `WHOLE_STROKE`), which `PageInkGesture` passes to
  the same shared `PageEraser`, and one erased gesture is still one undo step either way.
- **Vertical space reflows the page.** `Vertical space` (`Shift+V`) drops a grab line where the press
  landed, painted as a hairline across the sheet above the ink; each move re-applies
  `VerticalSpaceOps.shiftBelow` to the page **as it was at grab time**, so the block follows the pointer
  without recruiting the elements it passes, and the whole drag is one undo step. A drag that shifts
  nothing records nothing. Snapping stays **off** (`snapSpacingPt = 0.0`, a continuous drag): the tablet
  snaps to the ruling only when its Snap-to-grid setting is on, and the window has no such setting yet.
  Like a geometry slot, the drag is **not ink**, so the pen's packets stand aside for it too
  (`DesktopTool.inksByPackets()`): only the pen and the two erasers are fed from the driver. That is what
  makes the tool reachable with a stylus at all — while the packets owned the pen the Compose gesture stood
  aside, so a pen in proximity opened a contact and the grab line never moved. `DesktopVerticalSpaceTest`
  drags the grab line with a live packet stream and checks the block follows it.
  The arithmetic is shared: `VerticalSpaceOps`, its `Snapping` and the per-element move (`ElementShift`)
  live in `mobix-core`, and the app's `SelectionOps.translate` delegates to the same `ElementShift`.

### Placing a living element: Plot, Table and Circuit

Plot (`M`) and Table (`T`) place a living element instead of drawing it: a drag is its box, a click its
family's default size, then the lift **asks first** in this lane — the graph dialog's fields, or the table's
grid — before committing **one** element as **one undo step**; a cancelled question leaves nothing behind. A
**circuit component** is asked nothing: its drag's two **ends** are the part — rotated to the hand and snapped
onto the pins in reach — drawn **live** along the pen where it will land
(`DesktopCircuitPreviewTest`). The placement hands the window to the **marquee** (`S`), as the tablet does, so
the next drag moves what was just made; the **pencil**, or a **double-click** on the element, reopens the same
lane on it ([selection.md](selection.md) owns the grips). Geometry, builders and the family palettes
(`CircuitFamily`: passive `K`, active `Shift+K`, logic `Alt+K`) are shared with the tablet, and a slot's
submenu stays its **second** click, a circuit palette included (`ToolSlotClickTest` drives the scene).

### Selecting, moving, resizing and turning

The window's selection is the tablet's, cut along the seam the core already had. **Marquee** (`S`) and
**Lasso** (`V`) share one slot, so the strip still shows one "pick things" tool: a **drag** traces a region
(the straight box, or the freehand loop) which is painted as it is drawn, and the **lift** selects every
element wholly inside it; a **press inside the live outline** acts on the selection instead of starting a
new one — the body moves it, a **corner handle** scales it about the opposite corner, and the **handle above
it** turns it about its centre (`Shift` snaps the turn to 15°). Both paths recompute from the page **as it
was at grab time**, so dragging back and forth cannot compound, and one drag is one edit and **one undo
step**. `Delete` (or `Backspace`) removes what is caught, also as one step; `Esc` drops the outline before it
means anything else. A selection belongs to the page it was drawn on: turning the page lets it go, because
its refs name a layer and an index *of that page*.

The arithmetic is core, not window: `PageSelection` (the region, the caught refs, and translate/scale/rotate
that answer with the region moved the same way), `SelectionEdit` (the page rewrite) and `SelectionOps` live in
`mobix-core` and are shared with the tablet, alongside `ElementShift` and `VerticalSpaceOps`. What stays here
is the pointer half — `DesktopSelection.kt` decides *which* handle a press grabbed and *how far* it was
dragged (`selectionHandleAt`, `scaleFactors`, `rotationBetween`, all pure and JVM-tested), `SelectionGesture.kt`
walks the drag, and `SelectionOverlay.kt` paints the outline, the five handles and the traced region. A
selection drag is **not ink**, so `DesktopTool.inksByPackets()` stays false for the slots: the pen's packets
stand aside and a stylus in proximity still reaches the drag through the Compose pointer. A selection carries
the tablet's **action bar** under its outline, and the **pencil** on it opens the selected living element in
the inspector lane — both owned by [selection.md](selection.md).

### A stroke costs the stroke

The page is a Skia picture (`PagePicture`), and a picture is immutable — so a lift used to re-record the
**whole sheet**, 2–4 ms on `udiff.xopp` and more on a page written for a term. The pen lifts once per
letter, so that was a dropped frame per letter, and the eraser paid it again on every bite. The picture is
now a **base** plus **one tail picture per lift**: each tail holds the elements *one append put on top of
the page's last layer*, in the page's own order — one picture per lift, not one of the whole run, so a long
word cannot stutter before its last letters. Anything changing the ink of the base **or a tail** (an erase,
a reflow, a moved stroke, a part dragged after placement) re-records the sheet and starts a new base — a
tail's elements are compared by identity too, or a placed part would keep the ink it was placed with;
every stroke's outline is built **once** and cached by identity (`StrokePathCache`), so a re-record is a
replay, and the run folds into a base every 160 elements — a pageful of writing. `PagePictureTest` pins the
counters (three strokes leave `records` at one), proves base plus tails is the direct paint, that a changed
tail element is recorded again, and states which edits count as appends;
`DesktopWritingCostTest` pins that a written or rubbed frame costs an idle one and a rub-out rebuilds one.

### Scrolling and zooming a dense page

The page is recorded **once** per document state, not per viewport: `PagePicture` records `udiff.xopp` at
**document scale** and every frame replays it under the canvas's own matrix, so a scroll is a translate and
a zoom is a scale. Scaling a *picture* is not resampling (the commands are vectors) and `PagePictureTest`
measures the difference against the direct paint rather than assuming it; `--probe-scroll` with `--diagnose`
measured **3.6–4.3 ms mean before** and **0.0–0.2 ms after** ([desktop-tablet.md](desktop-tablet.md) owns
the numbers).

### What the window does not do

- No layer editing (hiding one is view state, above).
- No import of anything but a document (no images as elements, no search, no page reordering or insertion).
- No **absolute pen mapping** — the readings are folded into the Compose pointer, already transformed by
  the driver's own active area — and no installer: `run` and `createDistributable` are the entry points.

### The graphics tablet

The pen's two Windows streams, the packet path that draws a stroke, the dial's COM handler and the hand's own
gain are [desktop-tablet.md](desktop-tablet.md)'s. The window's side is one rule: the driver's packets draw
the stroke, the mouse Windows emulates never does, one contact is one stroke. The **hand** pans from the same
packets (`PenPacketPan`), one viewport write per frame; a held **Space** takes them from the stroke path.
The pointer over the sheet is the **tool's**: a ring wherever the tool lays ink at a width, the text cursor
for Text (it writes words, not a band), and the hand for the hand.

### Verification, runnable on any JVM host (no emulator)

`./gradlew :desktop:test` loads `udiff.xopp` (6 pages, ~4 000 strokes) through `DocumentLoader`, opens a ZIP
`.xopp` and a 40-page `.mobix` (including both **misnamed**, in either direction), refuses a text file and a
ZIP with no document inside by name, renders page 1 of each headlessly via `ImageComposeScene`, and renders a
table, a parametric shape, a bundled pixmap and a PDF background — asserting the pixels, not that the call
returned (`LivingAndBackgroundTest`). `DesktopShellTest` renders the tab strip **before and after** a
document is opened and asserts the frames differ (the regression that caught a strip which never redrew),
renders the sidebar, and checks the keyboard's fit and zoom arithmetic. `ToolStripsTest` renders the rail and
the ribbon on their own (every slot must have drawn its glyph — the eraser slot the *member that is picked* —
and the picked one wears the active slot), then the whole window in Rail, Ribbon and Both, reading the paper
out of the pixels: the page starts after the rail's lane and below the ribbon's band in every placement — the
mechanical form of "a band never lies on the page". `OpenFailureTest` pins what a failed open says in each of
its cases and how a command line is put back together (`DesktopLaunchPathTest`). `DesktopSaveTest` is the write's
round trip: an edit saved over a gzip `.xopp` and over a 40-page `.mobix`, each re-opened through
`DocumentLoader` with the container and the untouched pages asserted, a Save As that turns a `.mobix` into a
`.xopp` and re-homes the tab, a packaged `.xopp` refused with its file byte-identical, and the version the
window stamps into a `.mobix` checked against `app/build.gradle.kts`'s `versionName`. `DesktopEditingTest` is
the editing path headless: a 40-page `.mobix` opens through a `CountingEntrySource` with **zero** page
entries read, an edit on page 3 opens that page alone, the model the save writes carries it and a 40-page
repack round-trips it while the untouched pages keep their own ink; the eraser splits a bar where it crosses
it; a pressure sample becomes its filtered width, a highlighter stays one width, and the page-point mapping
undoes density and zoom. Its undo half drives the **shell's own commands**: a stroke, `Ctrl+Z` (model and
painted ink both return), a second undo that reports nothing left, a redo that repaints it, and a save that
starts a new history. `DesktopLifecycleTest` is the lifecycle headless: an edit makes a tab dirty, a save and
an undo back to the save point make it clean, closing a dirty tab asks first, a snapshot written by one shell
is adopted by the next and deleted once saved, and the strip's dot is drawn only while there are unsaved
edits. Every render lands under `desktop/build/desktop-render/`: `glass-parity.png` (the same panel in the
tablet's material and in this window's, by `GlassParityRenderTest`), and
`glass-edge.png` / `glass-over-page.png` / `glass-show-through.png`, which `GlassEdgeTest` samples — the rim
brighter at the top than at the bottom, or it is a border and not the family's edge, and a band of ink under
the panel still reading through it, or the fill is a slab. `GlassMotionTest` drives the arrival through a
scene's frame clock (`render(nanoTime)`): nothing at t=0, faint at 40 ms inside its 82 % box, the glass at
full size by 700 ms, with `GlassPresence` holding it composed through the 230 ms exit.
`PageLayersAndPaperTest` reads pixels too: `isometric-paper.png`'s line midpoints must fall on
`BackgroundGrid.isometric`'s segments, and `hidden-layer.png` is a two-layer page with one off — the band
that is gone is the check.

## Why the core is shared

`mobix-core` is the platform-free module: the document model, the XML/JSON layers, the `.xopp` and
`.mobix` codecs, the paper/ink geometry and the export compilers. The rule its header states is the one
that keeps the two shells honest: **nothing in the core may import `android.*` or Compose.** A file that
meets it moves as a whole; a file that meets it *almost* is cut along the seam rather than copied, so
there is never a second renderer with its own opinion of the drawing.

Measured on 2026-10-22 against the Android editing path (line counts re-checked 2026-10-23):

| Type | Lines | Android imports | Verdict |
|---|---|---|---|
| `render/XoppSynthesis.kt` | 179 | none — only `format.mobix` and `format.model` | **moved** — the export compiles in `mobix-core` |
| `render/PageSvgWriter.kt` | 229 | none — model and `java.util.Base64` | **moved** — the SVG export compiles in `mobix-core` |
| `render/EditHistory.kt` | 69 | none, no imports at all | **moved** — lives in `mobix-core/src/main/java/com/mobixournal/render/` |
| `render/PdfVectorPainter.kt` | 226 | `BitmapFactory`, plus `com.tom_roush.pdfbox` (the Android PDFBox port) | **split** — the geometry is `PdfPagePlan` in `mobix-core`, and this is its Android emitter |
| `io/DocumentIo.kt` | 666 | `ContentResolver`, `Uri`, `OpenableColumns` — and `resolver` is used in exactly one place, `displayName` | **split** — the write is `io/DocumentEncoder` in `mobix-core`, and the SAF shell keeps staging, the stores and the `content://` target |
| `render/ElementEdits.kt` | 152 | `BitmapFactory`, for `inJustDecodeBounds` only | movable behind an injected image-size function |
| `render/DrawingSurfaceInput.kt` | 581 | `MotionEvent`, `KeyEvent`, `SurfaceView`, `ViewConfiguration` | stays Android as the adapter, and it is **live**: it feeds `InkPointerRouter`, so the decisions are the core's (`docs/stylus.md`) and the Compose pointer is the second adapter |
| `render/DrawingSurfaceStrokes.kt` | 683 | `MotionEvent` | stays Android; its erase geometry moved (`PageEraser`/`StrokeEraser` are in the core) and its stroke now arrives as router events — `startStrokeFrom`, `extendStrokeFrom`, `eraseAtPointer` — so only the document-shaped half is left |
| `render/DrawingSurfaceSelection.kt` | 341 | `MotionEvent`, `ClipData`, `ClipboardManager`, `Context` | stays Android; the transform geometry is what could move |

## Decisions

Three decisions taken on 2026-10-21, owned by [`ui-modernization.md`](ui-modernization.md): the chrome is
the app's **own glass on a true-black desk** (Mica/Vibrancy would tint the window with the wallpaper and stop
it matching the tablet); **both tool placements ship** — vertical rail by default, horizontal ribbon as the
alternative — chosen in **Settings**, not by a header chooser; and the tab/view model is Compose **snapshot
state**, redrawn by the state it reads instead of a second copy.
