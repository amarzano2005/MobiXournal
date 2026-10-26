# Authoring non-stroke elements: text, LaTeX, images, plots, tables, circuits

> Read when you work on the Text, Image, LaTeX, Plot, Table or Circuit tool, or on vertical space: what a
> press places, what the window asks first, and what one undo step is.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). The stroke tools are
[drawing-tools.md](drawing-tools.md)'s; what an element *is* on disk is
[mobix-elements.md](mobix-elements.md)'s, and the living entities are
[mobix-living-entities.md](mobix-living-entities.md)'s._

## Authoring non-stroke elements

**Authoring non-stroke elements.** With the **Text**, **Image**, or **LaTeX** tool active, the
surface is in a *placement* mode (`placeKind`): a one-finger tap (not a drag) raises `onPlace` with
the page-local point, which `EditorOverlays` turns into a keyboard dialog (text/LaTeX) or, for images,
an `onPickImage` callback up to `MainActivity`'s SAF picker. The chosen content is inserted via
`insertText` / `insertTex` / `insertImage` — each a single undoable edit appended to the page's top
layer. Tapping an existing text box reopens it for editing (clearing the content deletes it);
matched by element identity. The view keeps the loaded document intact and only appends/edits, so
every page, layer, and element round-trips through save.

**On the window** the Text slot (`X`) is the same tool, typed in place rather than in a dialog: a press
opens a field **over the sheet** (`TextBoxHost`), positioned by the same page-points-to-pixels mapping the
ink is drawn with, in the ink's colour and at a size the pen's width sets (8–48 pt, `TextAuthoring`).
The slot's pointer is the **text cursor**, not the pen's ring: a circle the size of the nib promised a
band of ink no character has, so the ring is not drawn for Text at all (`toolPointerIcon`), and the field
under it shows the caret instead.
The field wears **no surface**: what is being typed is page content, like the strokes a draft draws, so the
words appear in the document's own ink and face at the document's own place — and, with its padding gone,
exactly where the commit will write them. Only the accent caret marks it (`DesktopTextPaintTest` reads the
paper through a point inside the box). It also breaks lines **where the page does**: a `.xopp` `<text>`
carries no width, so the painter wraps on the box's own newlines and nothing else, and the field used to
invent a third-of-the-page width — a long line wrapped while being typed and snapped back to one line on
the commit, and a caret placed from that layout pointed at a letter the page does not draw there
(`DesktopTextSceneTest` opens a box on a line wider than a third of the page and measures the band where a
second line would be).

Nothing is written while typing — the shell holds a `TextBoxDraft` (`DesktopTextBox`), so the page and its
history are untouched — and the commit (`Esc`, a click elsewhere, or leaving the tool) appends **one**
`TextElement` to the page's top layer as one undo step, left selected. A blank box writes nothing: a press
you change your mind about leaves no element and no step to undo. While the field is up it owns the
keyboard — only `Esc` is the shell's — so `Space`, `B`, `H`, `V` and `Delete` are characters, not commands
(`DesktopTextAuthoringTest`, `DesktopTextSceneTest`).

**A press on words already on the page re-opens them**, which is what makes text written on the tablet, or
in desktop Xournal++, editable in the window at all: `textBoxHit` picks the **top-most** box under the
point with the same padded bounds the tablet's `ElementEdits.pickText` uses, and the field comes up with
that box's own words, its own font description (family, bold, italic), its own size and colour — and the
caret **at the point pressed**, measured with the field's own layout (`TextBoxHost` reads it from the
draft's press point, `caretAnchorPx`), so correcting a word in the middle of a line is one click and not
a run of arrow keys. The retype is **one** edit that replaces the element where it sits — same layer, same
index, same id, same unmodelled attributes — so a corrected box keeps its z-order and the `.mobix`
incremental save keeps keying the same element; emptying the box deletes it, and closing it without
typing records nothing. The caret travels with the text (`TextBoxDraft.typing` is a `TextFieldValue`, not a
string), so a word can be fixed in the middle. While the field is up the sheet stops painting the box it
has taken over (`PagePicture`'s `skip`), or the old words would show through the new ones.

**Its face, size and ink are the inspector lane's** (`InspectorText.kt`): a text box is one of the families
`hasInspector` opens, so a click to select it and the bar's pencil (or a double-click) brings up the same
300 dp column a table or a plot uses, with **family** (Sans / Serif / Monospace — any other family a file
names joins the row as the picked answer), **Bold**, **Italic**, **size** in pt and the palette's colour.
Each is one committed field and one undo step, and all five write what the tablet's dialog writes: a Pango
font description, a size in pt, an ARGB colour, so the box reopens on the tablet with the same face. Its group's second member is the tablet's **LaTeX**
tool, and it asks before it writes, as Plot and Table do: a press with Formula (`Shift+T`) reserves the box
and the lane asks for the source (LaTeX, or Typst with the switch) with a live typeset preview, and **Place**
appends one `MathBlockElement` as one undo step, left selected — a blank source disables Place rather than
drawing nothing (`DesktopLivingSlotsTest`).

## Placing a living element (both shells)

**A slot that places, not draws.** Plot, Table and Circuit are *placement* tools in both shells: the
press does not ink, it reserves a box and writes a living element there. The box is
`mobix-core`'s `LivingPlacement`: a drag's two corners are the element's bounds, a click (or a drag
shorter than `LIVING_PLACEMENT_SLOP_PT` on either axis — 4 pt) is the family's `defaultSize`, and
either answer is pushed back inside the sheet, so an element placed while zoomed out is never half off
the page. The defaults are the tablet's own: a plot takes 45 % of the page's width (240–320 pt) and
starts at `sin(x)` over −10…10; a table is a 3 × 3 grid with a header whose columns and rows divide
the box exactly; a component is the armed `ShapeKind` along the box's horizontal axis with
`CircuitElementFactory`'s canonical pins. Identity is the codec's (`ElementId`), never the placement's.

On the window the press is taken **before** the ink router and `DesktopTool.inksByPackets()` stays
false for these slots (the pen's packets stand aside, as for a figure) and the drag previews the box in
the selection's own dashed outline.

**The window asks before it inserts**, as the tablet does. Lifting the drag does not write anything: the
right-hand lane becomes the family's question — the graph dialog's own fields for a plot (`f(x)`, the
domain's two numbers, critical points, derivative, summary and the shaded integral with its bounds), the
table's grid and header for a table — and **Place** is the edit: one `addToTopLayerOn` append through
`editPage`, one element, **one undo step**, left selected so the pencil on the bar acts on it. Any other
answer — Cancel, `Esc`, a domain that does not open to the right — leaves the page exactly as it was,
because no element ever existed to leave behind. The questions are the model's fields, so what the card
answers is exactly what the inspector can change afterwards (`docs/mobix-elements.md`).

**The circuit parts are three palettes**, the tablet's own (`CircuitFamily`): passive parts (`K`), active
parts (`Shift + K`) and logic gates (`Alt + K`), each slot wearing its family's first part and opening
that family's rows — `CircuitFactory`'s own list and the tablet's picker layout, every cell drawn by
`CircuitInk`. The part **is** the question, so a component is placed by the press that follows the pick
(no card), with rotation 0 and the factory's canonical pins; its value, rotation and label side are the
inspector's. There is no separate Axes slot on the window: the tablet's Axes tool is the coordinate-axis
*figure* (`ShapeKind.COORDINATE_AXIS`), which the window already ships, and a plot draws its own frame
through `PlotInk`.

## Vertical space (`mobix-core`'s `render/VerticalSpaceOps.kt`)

**Vertical space (`render/VerticalSpaceOps.kt`, `mobix-core`).** The **Vertical space** tool
(`EditorTool.VERTICAL_SPACE` → the surface's `verticalSpaceMode`, classified as
`GestureIntent.VERTICAL_SPACE`) reflows a page — and the `desktop/` window has it too
(`DesktopTool.VERTICAL_SPACE`, `Shift+V`), on this same arithmetic, with snapping off because the window
has no Snap-to-grid setting yet (`docs/desktop-workstation.md`): pointer-down latches the grabbed page and the
page-local Y of the grab line, and each move frame re-applies `VerticalSpaceOps.shiftBelow` to the
**gesture-start snapshot** (the same recompute-from-the-start discipline as a selection move, so a
live drag never drifts or compounds). An element moves when its `ElementBounds.of(...).top` is at or
below the line — desktop's *"items which lie entirely between the cursor position and the end of the
page"*, so the line never tears an element in half — and that decision is taken **once, at grab time**:
a block that slides past the elements above it does not recruit them on the way. **Nothing stops the
block at the line.** The shift is the pointer's travel in either direction, so pulling up carries the
block above the line it was grabbed at (and, pulled far enough, off the top of the sheet — where it
stays, as on the desktop, recoverable by dragging back down). A drag that can't move anything returns
the same page list, which keeps `finishGesture` from recording an empty undo step. With
**Snap to grid** on, `dragShift` snaps the *amount* inserted to the page's ruling (`Snapping.spacingY`
→ `Snapping.snap`, i.e. a whole number of ruled lines), which is desktop 1.1.2's *"Added snapping for
vertical space"*; a plain sheet (spacing 0) leaves the drag continuous. **Every layer of the page
moves together — a deliberate difference from the desktop**, which reflows only the current layer: a
note written on one layer must not be left behind by space opened on another. The whole drag is one
undo step, and because it only rewrites coordinates the result round-trips through save unchanged.
