# Selection actions: copy, align, and the window's bar

> Read when you change what a selected element can be asked to do: the four-flavour clipboard, align and distribute, or the window's action bar and its inspector lane.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

What a selection *is* — the marquee, the lasso, the handles and the transforms — is [selection.md](selection.md)'s.

## Copying and dropping a selection

A copy leaves the canvas in **four flavours at once** (`render/ElementTransfer.kt` and
`render/SelectionTransfer.kt`): `application/vnd.mobixournal.element` carries the elements themselves
(geometry, living recipe and formula source) so another view or window can paste them; `image/svg+xml`
is a vector page of the selection (`PageSvgWriter`); `image/png` is rendered into `cache/clipboard/` and
handed out through the app's `FileProvider`; `text/plain` is a readable summary — a text box's content, a
formula's source, the element kinds. A paste prefers the element flavour, so a duplicated plot arrives
a live plot, not a picture of one.

A **long-press on the selection** lifts it as a system drag carrying that same clip (`render/SelectionDragOut.kt`);
a touch that moves before the timeout stays the ordinary move/resize gesture, and only a finger can lift
one — a stylus is drawing.

**Dropping** accepts the same flavours (plus an image, a PDF or a text file): elements land at the drop
point as one undoable edit and are selected; text is classified by `render/ExternalIngest.kt` — a
CSV/TSV grid becomes a living table whose columns divide the requested width, a short LaTeX line a math
block, prose a text box; an image becomes an `ImageElement`; a PDF is handed to the editor, which appends
its pages. Nothing is imported silently: the drop point is the element's placement.

## Align & distribute (`SelectionOps.align` / `distribute`)

**Align & distribute (`SelectionOps.align` / `distribute`).** With two or more elements selected the
same bar offers the six alignments — left/centre/right and top/middle/bottom — plus horizontal and
vertical distribution. Alignment translates every selected element but the **anchor**: the outermost
one on that axis, so the group lines up on its own extent and nothing moves further than it has to
(aligning left moves everything right of the leftmost element onto it, not all of them across the
page). Distribution keeps the two end elements and respaces the rest evenly between them, sorting by
centre so a straggler doesn't jump the queue. Both are pure page-list rewrites recorded as **one**
undo step, and both are no-ops — with no history entry — when the selection is too small to align
(two) or distribute (three). The scope and round-trip reasoning for what rotate/resize can touch lives in
[`stylus.md`](stylus.md) (Selection — desktop parity).

## The window's bar and its inspector lane

A selection is not only something to drag: the window draws the tablet's **action bar** under its outline —
Cut, Copy, Paste, Duplicate, Recolour, Line width, Align & distribute, Edit, Delete — and each is the shared
command, not a second implementation. The clipboard holds model elements, so a copied plot pastes as a live
plot, a paste lands **beside** what it copied and stays selected, `Cut` is the copy and the deletion in one
step, and `Ctrl+D` duplicates. Copy **never writes the system clipboard**: a drawing is not a string, and
from Android 13 the platform's copy banner announced one over the page, so the app keeps its own clipboard
(shared by both panes) while content pasted in from elsewhere still arrives through the system one.
**Recolour** opens the same slim swatch strip the tablet shows — one line under the bar, no title — and a
picked swatch restyles what is caught; **Line width** still takes the value the strip's own ink would write. The bar rides below the outline (above it when the page ends below),
clamped inside the canvas; with nothing caught it shrinks to the **Paste button alone** at the page's
bottom centre, and only while a pick tool (marquee or lasso) is in hand — the commands keep working
whatever the tool, so `Ctrl+V` still pastes with the pen picked. Both states are **one** surface placed
on an animated offset (`animateIntOffsetAsState`), so a new selection grows the pill into the bar while
it glides under the ink; a hard switch flicked one surface out and the other in on the same frame. A control the selection cannot support is dimmed and does nothing (`docs/interface-contract.md`
§5); the rules live in `SelectionBarUi`, so a JVM test reads them rather than a screen.

**A living element's own grips** are the on-page half of its editor (`LivingGrips.kt`): a parametric figure
wears a ring on each corner, and dragging one bends the figure — the side leading into the corner snaps onto
the 0/30/45/90 directions within the tablet's 7° tolerance, `Shift` frees it — while a table wears a ring on
each **interior** divider, and dragging one widens the column beside it without moving the box or a single
cell's text. Outer edges are not grips (the box's own corner squares scale those), and a grip is caught
before the eight handles — and before a press on the ink is read as a pick, so a corner answers a hand that
lands a hair outside the outline. The radius is the handles' own screen-pixel rule, so a divider is as easy
to catch zoomed out as in. A plot's interval and a component's outline are **not** grips: those numbers live in the
lane. `LivingGripsTest` pins the arithmetic, `DesktopLivingGripsSceneTest` the gesture (one undo step per
drag, a miss moving the element instead of dividing it).

A **click on an element picks it** — `SelectionTester.pickTopmost`, the topmost element under the pointer — and
the drag that follows carries the new selection, so picking one thing up no longer means boxing it; a click on
empty paper lets it go. The **pencil** on that bar opens the selected element's editor, and is offered only
for exactly one element the window has an editor for; a **double-click on any of them**, selected or not, is
the same command (`TapTracker`: 400 ms, 8 px), so the tablet's double-tap has a twin on the page.
`hasInspector` is that one rule (`LivingFamily.of`, which is the living families **and** a text box);
`DesktopSelectionBarTest` walks the families — plot, table, figure, component, formula, text — and fails if
one does not answer `EDIT`, so a family added later cannot ship uneditable.

That editor is the **inspector lane**: a resizable column on the right, so the page keeps the
rectangle that is left and nothing lies over the sheet. It is made of the **settings panel's** material
(`DesktopTheme.settingsPanel`, the near-solid glass of `docs/desktop-settings.md`) rather than the bands',
because this column is read while an element is edited and holds the same kind of fields a settings page
does — while still taking **no rim**: a lane is geometry of its own, not a pane lying on the ink
(`docs/chrome.md`). It is drawn in **three parts, always** — a header, a
body of property groups and a footer — and it is up **only while there is something to edit**: a placement asking its
values, the one element the pencil opened, or a **text box being typed** — a box in the field has a face, a
size and an ink too, and the moment to set them is while the words are being written, so pressing with the
Text slot opens the column and committing the box (or its `Done`, its ×, `Esc`) closes both. A selection
alone opens no column, and the lane goes the
moment the selection stops naming the element it belongs to (`attachedLivingEditor`), so moving on to
another element or to empty paper leaves the page its full width again (`DesktopInspectorLaneTest` renders
the lane's own pixel with nothing selected, with a selection, while editing, and after the selection moves
away).

The **header** is the lane's identity, not just its title: the family's own catalogue glyph, the family's
name, and one line of the element's content (`sin(x) · −6…6`, `4 × 6 · header`, `BJT NPN · 10 kΩ`, a text
box's first line and its size) from `livingSummary`, because three plots on a page are all `Plot` and the
fields below write into exactly one of them. The body uses flat, collapsible groups with aligned fields
and quiet dividers. Settings and inspectors share typography, colours and control states while each
keeps the space its desktop task needs.

The sections offer only what the model carries — a plot's formula, domain, resolution, the
display flags and **one colour per curve**; a table's grid, header row, rules and **cells** (a column added divides
the width rather than growing the table); a figure's side lengths and interior angles as read-outs beside its
outline; a component's part, value, placement and stroke; a formula's source and language; and a **text box's
face** (family, bold, italic), its size and its ink (`InspectorText.kt`) — the tablet's text dialog's own
controls, minus the words, which the Text tool types on the sheet (`docs/drawing-tools.md`). Underline is not
among them because the format cannot store it. A field commits on
`Enter` or when the caret leaves, as **one** undo step, and an unchanged value writes nothing; `Esc` puts a
field back, `Esc` again closes the lane, and a focused field wears the accent's ring so the keyboard user can
see where they are typing. While a field holds the caret the key is typed into it rather than read as a
shortcut — a letter is a letter there, not a tool's accelerator, which is what used to end the edit the
moment a value was typed (`WindowKeys`). `LivingTarget.kt` resolves the ref against the document as it is *now*, so an undo
cannot leave a panel showing a value the file no longer holds.

**Reading the element is also how the lane follows it.** A page read is not snapshot state, so the column
reads the document's **edit count** (`DesktopDocument.edits`) through `livingTarget`; without that one
read the cards kept the element they were first composed with, and every control wrote a copy of *that*
one — Bold pressed twice stayed bold, the italic after it dropped the bold, and a swatch last in the card
restored the original face. Each control also reports through a holder (`rememberUpdatedState`), because a
`pointerInput` keeps the lambda it was built with; the two halves are what make a row of controls behave
like one editor (`DesktopInspectorLaneTest` clicks the real rows: bold on, bold off, bold+italic, a chip,
a swatch, and reads the element back).

A placement never leaves the tool armed: once the element exists it is what stays selected, and the window
picks the **marquee** (`S`) the way the tablet does, so the next drag moves the element just placed instead of
placing a second one.

## Desktop property layout

The lane starts at 380 dp. Drag its left divider to resize between 320 and 520 dp; keyboard focus on
the divider enables Left/Right adjustment and Home to reset. Its effective width is capped at 55% of
available row space so a narrow window retains canvas space. Width is window state, not a document edit.
Property groups are flat and their titled headers can be folded with click, Enter or Space. All groups
start expanded, including the plot Preview; folding changes presentation only. Existing Place/Cancel and direct-edit/Done semantics
are unchanged. Fields retain their current numeric validation and undo boundaries. Focused group
headers and resize handles receive navigation keys before global shortcuts; other shortcut keys remain
available, and disposing a control releases its keyboard ownership. Property fields and buttons use compact corners, boolean rows use square checkboxes, and secondary actions stay unfilled; other window chrome is unchanged.
