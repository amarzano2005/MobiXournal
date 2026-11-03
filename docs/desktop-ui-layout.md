# The desktop window's layout: panes, strips and shortcuts

> Read when laying out the `desktop/` window — its bands, its sidebar, where the tool strips sit, and
> which key does what. The module's own inventory is [desktop-workstation.md](desktop-workstation.md)'s;
> the window's Settings surface is [desktop-settings.md](desktop-settings.md)'s.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Desktop workstation UI architecture (Windows 11)

On desktop workstations paired with keyboards and graphics tablets (see [desktop-workstation.md](desktop-workstation.md)),
scaling a mobile touch interface up produces clumsy, oversized controls. The window is therefore a
**workstation layout** (inspired by Figma, VS Code and Blender) built from the same material as the tablet's:

```text
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ [≡] [Notebook 1 ×] [Notebook 2 ×] [+]           [⌕] [↶] [↷] [Save] [Export PDF]    │  header band
├─────────┬─────────────────────────────────────────────────────────────────────────────┤
│ Sidebar │                                CANVAS                                       │
│ Pages   │                                                                             │
│ [Ctrl+B]│                                                                             │
├─────────┴─────────────────────────────────────────────────────────────────────────────┤
│ Page 1 / 6    100%    fit page                                  notebook.mobix        │  status bar
└───────────────────────────────────────────────────────────────────────────────────────┘
```

### 1. Window layout & workspace panes
- **Header band, 48 dp** (the token plus 8: it carries the tabs *and* the file commands): one tab per open
  document (each keeps its page, zoom and fit), the `+` for another, and right **Pages and Layers** in one
  capsule, the search field, **undo, redo, Save, PDF**. The field stands there always beside the tabs, so
  using it never moves them or the file commands, and it keeps the **same gap either side** (12 dp to the
  capsule and to the history pair), so it reads as a group of its own rather than the capsule's tail;
  **Pages**, on its left, is the tablet's page section — add, remove, step, format, paper — and **Layers**, to
  its right, the page's stack with the manager's edits (see [search.md](search.md),
  [desktop-workstation.md](desktop-workstation.md)). Both doors are the header's own 36 dp pill, each in a
  slot of that size, with **no seam between them**: the divider that used to stand there made two shrunken
  buttons out of one object. The tool strip ends with its last tool.
- **Collapsible navigation sidebar (`Ctrl+B`)**: page thumbnails and the current page's layers, with an eye
  per layer that hides it without writing to the document; it opens itself only when the document has
  **more than one page**.
- **The sheet alone on the desk**: one page at a time, fitted by what the page is (a sheet whole, a long
  strip to its width), with the fit mode named in the status bar.
- **Status bar**: page counter, effective zoom, fit mode and the container behind the document. While a
  document is being read it says `opening <name>…` instead of `no document`, because the read runs off the
  frame thread: the window keeps painting through an open instead of freezing until it lands.

**Desktop decisions (2026-10-21):**
- **The chrome is the app's own, on a true-black desk — not the window manager's.** The header, the
  sidebar and the tool strips are the same frosted glass the mobile chrome uses, and the desk behind the
  page is `#000000`; platform materials (Windows 11 Mica) are **not** used: Mica tints the window with the
  wallpaper, so it would stop matching the phone and change with the user's own picture.
- **Both tool placements ship**: the vertical rail is the default, the horizontal ribbon the alternative
  for 16:9 monitors. Whichever is on, the strip stands in a **lane of its own** beside the canvas — a band
  with the tablet's capsule in it, never a bar over the page (2026-10-28, `interface-contract.md`); the
  choice lives in the settings file, and the Interface section owns its door.
- **The window's bands are blacker than the tablet's** (2026-10-25): the same glass RGB at
  `DESKTOP_BAND_ALPHA` (0.58), because 0.85 composites to `#131314`, which in a maximised window on a 34"
  screen read as a grey slab beside the phone; the band is `#0C0C0D` — the one tone the shells do not share.

**The shell as built (2026-10-22).** `desktop/` has the header, tab strip, sidebar, workspace, status bar
and both tool strips ([`desktop-workstation.md`](desktop-workstation.md) owns its inventory); from **this**
spoke it takes four rules, and the parity work of 2026-10-22 put them all in the window:

- **A band is chrome, a capsule is an object.** The header, status bar, sidebar and the strips' lanes take
the glass fill and **one 4 dp cast shadow**, no rim. The rail is a **52 dp capsule in a 64 dp lane** (6 dp
of side air, 8 above and below), the ribbon a 48 dp capsule in a 56 dp band; each takes its **own 6 dp
lift** and the family's rim, and a header control is that capsule at 36 dp. Without the lifts the bands were flat,
because over a true-black desk the glass' alpha is invisible: the shadow is the only depth the window has
(`Modifier.bandSurface()` / `glassCapsule()`). The fill and the ink are the shared tokens' — a sheen of the
window's own made the same material lighter (`interface-contract.md` §2 owns the colours).
- **One icon set.** The tool glyphs come from `ToolGlyphCatalogue` (`mobix-core`), plain geometry on the
24-unit grid that the tablet turns into an `ImageVector` and the window draws into a `DrawScope`. Neither
shell keeps a set of its own — the tablet's deep circuit, gate, bond and variant vectors were transcribed
and deleted — so a picker row and a slot face cannot disagree; the picked slot is a 10 dp steel squircle.
The **chrome's** own pictures live in `ChromeGlyphCatalogue` — Material's path data, the selection bar's
marks and the add/close/eye included — and **both** shells read it, so two screens show one set.
- **Every surface answers the pointer**: a tab, a sidebar page, a layer row, a menu option, the open and
  close buttons and every tool slot wear the steel at `hoverSlotAlpha` (0.45) while hovered, one step
  short of the picked fill — `Modifier.hoverFill()` in `GlassEdge.kt`. Before that the rail answered only
  with its tooltip and the sidebar not at all.
- **No motion of its own**: the window's surfaces arrive **and leave** on `DesignTokens.GlassMotion`, read
from the shared tokens rather than picked here. A flyout is opened through `GlassPopup` — a `GlassPresence`
hold plus the same spring — so a menu that closes draws back instead of vanishing; only the bars' hover
tooltips still land with the pointer (`interface-contract.md` §7 owns that exception).
- **The colours and corners are the tokens**: 16 dp tab capsules, the 20 dp panel corner, a 40 dp header,
a 24 dp status bar, Inter with tabular figures, dark-first. `DesktopThemeTest` re-reads the JSON block
above and `ChromeMaterialRenderTest` proves the lift, the flat fill and the rim as pixels.

Picking a colour writes it straight to the settings file (`ink.colour`, `ink.width`), and the palette is
`palette.colours` through `PenPalette.sanitised`, so a hand-edited file cannot put a transparent colour,
a duplicate or an empty palette in front of the picker. The row ends in the **editable custom slot** and
the **add-colour** swatch, both opening the window's own colour panel (saturation/value square, hue
slider, two-way `#RRGGBB` with a live preview, Set/Cancel) in the floating glass rather than a Material
dialog; an invalid hex keeps the last valid colour and leaves Set out of reach. The placement choice is
persisted (`interface.toolPlacement`, Rail by default) and the strips appear once a document is open.

Everything the chrome does can also be done from the keyboard:

| Key | Action |
|---|---|
| `Ctrl + T`, `+` | open a document; a file already open activates its tab instead of opening twice |
| `Ctrl + W`, `×` | close the tab, activating its neighbour — asking first while it has unsaved edits |
| `Ctrl + F` | hand the band's always-on search field the keyboard, its results listed under the sidebar's layers — the menu's **Search** row runs the same command |
| `Ctrl + S` | save the active document back into the container it was opened as; unavailable while the file already holds it |
| `Ctrl + Shift + S` | Save As — one save window, whose **type box** chooses the container |
| `Ctrl + E` | export the active document as a PDF, asking where to put it |
| `Ctrl + Z` / `Ctrl + Shift + Z` | undo / redo the active tab's last command |
| `Ctrl + Tab` / `Ctrl + Shift + Tab` | next / previous document |
| `Ctrl + B`, `≡` | show or hide the sidebar |
| `Ctrl + ,` | open the window's Settings — a page of the window, not a card over the page |
| `Ctrl + =`, `Ctrl + NumPad +` | zoom in — see the layout note below |
| `Ctrl + -`, `Ctrl + NumPad -` | zoom out |
| `Ctrl + 0` / `Ctrl + 1` | fit the page / fit the width |
| `Page Up` / `Page Down` | previous / next page |
| `Space` (held) | pan with the primary button; the middle button always pans |
| `B` `H` `E` `V` `S` `X` `L` `A` `R` `C` `P` `M` `T` `K` | pick that tool — §3's shortcut table is the list |
| `Shift + X` | the axes tool; a bare `X` is the text box |
| `Shift + T` | the formula tool; a bare `T` is the table |
| `1` `2` `3` | pick favourite colour 1 / 2 / 3 |
| `Alt + 1`…`Alt + 3` | make the colour in use that favourite |
| `]` / `[` | wider / thinner — one step through `S`, `M`, `L`, stopping at the ends |

Closing a dirty tab, or the window itself, raises the **Save / Discard / Cancel** question in the floating
glass over the page rather than closing silently; `Esc` is Cancel and the window's X takes the same path.
A tab with unsaved edits wears a dot in the accent after its label (`docs/desktop-documents.md`).

**The two history commands have defined states, not just a binding.** `Ctrl + Z` and `Ctrl + Shift + Z`
act on the **active tab's own** `EditHistory` — undoing in one document never reaches into another — and
both are **unavailable while their stack is empty**: the command is not handled at all, and `canUndo` /
`canRedo` are the state a header control greys the action out on (there is no such control yet, so
`apply` answering false is the whole visible effect). One gesture is one step: a stroke records on its
lift and an eraser drag on its first bite, so a single undo never takes back half a line; an eraser
stroke that touches no ink records nothing. A save starts a new history, because the pages of the
archive it just closed are not steps a tab may restore.

Two decisions taken while building it:
- **The tab and view model is Compose snapshot state, not plain fields.** A `mutableListOf` and ordinary
  `var`s are invisible to Compose: the first run opened a document while the strip stayed empty (`≡`
  and `+`, no tab) and a zoom changed a number nothing redrew. One state, read by the chrome, is the
  only version that cannot disagree with itself — and it still runs in a plain JVM test, because
  snapshot state works outside a composition.
- **Zoom-in is bound to a physical key, not to the `=` character.** On an Italian keyboard `=` *is*
  `Shift + 0`, and AWT delivers no key event for it at all — `Ctrl + =` simply never arrives. The
  keypad's `+` is the layout-independent binding, `Ctrl + shift + 0` is accepted for the layouts where
  `=` shares the `0` key, and `Ctrl` + wheel (zoom at the cursor) is the one that always works.

### 2. Tool placement on Desktop
The drawing and STEM tools are always on screen, in whichever strip the window's saved placement selects
(`toolPlacement` in its settings file; the Interface section will own the control):

- **Rail (default)**: the 52 dp capsule in its 64 dp lane down one edge — the tablet's own geometry, which
  leaves the sheet its full height on 16:9 and 21:9 monitors.
- **Ribbon**: the 48 dp capsule in a 56 dp band under the header, for a window that is short or narrow.
- **Both**: the ribbon above the rail, each with its own slots.
- **1-click STEM access**: `f(x)`, shapes, tables and circuits sit in the same strip as the inking tools,
  with no submenu between the user and them — and so do the **plain figures**: Line, Arrow, Double
  arrow, Rectangle, Ellipse and Axes are six slots of their own on the desktop, one click each. The
  triangle, the trapezoid and the polygon are three more, exactly as the tablet's rail draws them: each
  opens its **own menu** (the four triangle kinds, the three trapezoid kinds, the 3–12 side count) and
  wears the answer it will draw, so a variant is never a second button and never a shared slot.
- **Hover names the slot**: a pill tooltip shows the tool's name and its key (e.g. `Function plot [M]`).

### 3. Universal shortcuts across all tools (Mobile & Desktop)
Every single tool and editing action in MobiXournal carries an assigned shortcut accelerator, providing
seamless parity whether driven by an external tablet keyboard on Android or a workstation on PC:

| Tool / Action Category | Tool Name | Default Keyboard Shortcut | Mobile / Stylus Gesture Equivalent |
|---|---|---|---|
| **Core Inking** | Pen / Brush | `B` | Single tap on pen / Primary tool slot |
| | Highlighter | `H` | Secondary tool slot |
| | Eraser | `E` | Stylus rear eraser tip or barrel button 1 |
| | Eraser (whole stroke) | `Shift + E` | The eraser slot's second member |
| | Lasso Select | `V` | Barrel button hold + drag |
| | Marquee Rect Select | `S` | Selection mode toggle |
| | Select text (PDF) | `Shift + S` | The tablet's PDF text tool: drag across real text to select and copy it |
| | Text Box | `X` | Screen double-tap with finger |
| | Formula (LaTeX / Typst) | `Shift + T` | The Text slot's second member (the tablet's LaTeX tool); asks its source first |
| | Hand Tool | `G` | A slot of the rail: a primary drag moves the page |
| | Pan | `Space` (held) | Two-finger scroll / drag |
| **Geometry & Figures** | Straight Line / Ruler | `L` | Hold-to-straighten dwell (300 ms) |
| | Arrow | `A` | Arrow shape toggle |
| | Double arrow | `Shift + A` | Arrow slot's second member |
| | Rectangle | `R` | Shape dwell |
| | Circle / Ellipse | `C` | Shape dwell |
| | Triangle | `Alt + R` | The Triangle slot's menu: four kinds, plus a scalene one's angles |
| | Trapezoid | `Alt + Z` | The Trapezoid slot's menu: three kinds, plus a scalene one's base angles |
| | Polygon | `P` | The Polygon slot's menu: three to twelve sides, the live count on the slot |
| | Setsquare guide | `Q` | One slot with the compass: picking it lays the instrument on the page |
| | Compass guide | `Shift + Q` | The guides slot's second member |
| **STEM & Advanced** | Vertical space | `Shift + V` | Reflow tool in the rail's Tool pop-up |
| | Cartesian Coordinate Axes | `Shift + X` | 1-tap STEM dock button |
| | Function Plot $f(x)$ | `M` | 1-tap STEM dock button; asks formula, range and flags first |
| | Dynamic Table | `T` | 1-tap STEM dock button; asks its grid first |
| | Circuit parts: passive · active · logic | `K` / `Shift + K` / `Alt + K` | Three STEM dock buttons, each with its palette |
| | Audio Tap / Replay | `Y` | The header audio pill's Play — no tool slot |
| **Styling & Navigation** | Favorite Colors 1 / 2 / 3 | `1`, `2`, `3` | Tap on color swatch / Rail slot |
| | Tool Group Picker | `Alt + Down` | Chevron, right-click or hold on a grouped slot |
| | Pen-tip ring | `Alt + B` | Its own row in **Settings → Ring** — a pen button mapped to a key too |
| | Stroke Width Up / Down | `]`, `[` | Rail's stroke slot |
| | Zoom In / Out | `Ctrl + =`, `Ctrl + -` | Pinch gesture |
| | Undo / Redo | `Ctrl + Z`, `Ctrl + Shift + Z` | Two-finger tap |
| | Full screen (window) | `F11` | — desktop only: the tablet is already the whole screen |

- **Universal Shortcut & Individual Color Remapping**: All keybindings, tools, and **individual color palette slots**
  are remappable via **Settings → Input & Shortcuts**: a shortcut or a stylus gesture can select a specific
  user-defined HEX color (e.g. `Alt+1` for Pure Black `#000000`), not just the 3 factory favourites.

### 4. The pen-tip ring (`Alt + B`)

The tablet's **pen-tip ring** ([`ui-modernization.md`](ui-modernization.md), *The pen-tip ring*) on the PC: `Alt + B`
opens a ring centred on the nib — the last place the pointer was over the page, and the canvas's centre until a
pointer has been there at all — and nothing is on screen until it is summoned. What it carries is the reader's
([`desktop-settings.md`](desktop-settings.md)): an untouched ring is the tablet's own contents in the tablet's own
order — the palette's first five colours (the editable custom HEX slot last among them), the three pen widths, then
Line, Lasso, Marquee, Highlighter, Eraser and Pen — and any face can be given **any tool the window acts on**, so the
ring is not held to those six: a logic gate or a flowchart arrow can sit at the pen as easily as the eraser.
- **How it closes.** A key-opened ring is a **picker** — there is no held button to release — so a **click on a
  slot** commits it and closes the ring, a click inside the ring but off a slot does nothing (it was aimed at
  the ring, not through it), and a click well outside it, `Esc`, or a second `Alt + B` puts it away without
  changing anything. Every press while the ring is up is the ring's: nothing is inked, panned or selected under it.
- **The pointer stays on it, and so does the pen.** The pointer over the ring is the *tool's* ring, not the arrow:
  `DesktopHudLayer` paints it **above** the glass, so the mark the reader aims with is never hidden by the surface it
  aims at. The pen's own packets answer the ring as well — a tip that lands on a face commits it and lays no ink under
  it, because on this machine the packets are the stream and Windows' copy of the contact is not. That contact stays
  the ring's after the surface has gone: the packets close it on the frame thread, so the copy of the same press
  arrives a frame later with nothing on screen and is **swallowed** there rather than becoming a stroke underneath —
  one contact, one answer — and the next press belongs to the page again.
- **What a commit is.** The entries the rail and the flyouts already use (`pickColour`, `pickWidth`,
  `selectTool`), so a ring choice cannot mean something you could not have set by hand; the picked tool's own
  ink is what changes, exactly as the colour capsule changes it.
- **Material.** A floating surface that happens to be round: the floating fill at its panel alpha, the hairline
  rim, the steel plate under the picked slot and the accent ring around it, a swatch in its own colour inside a
  chrome-ink rim, a width as a dot of that width, a tool as its catalogue glyph. Tokens only — `DesktopThemeTest`
  sweeps the chrome sources.
- **What it carries** is the reader's, face by face, and **Settings → Ring** *shows* it: that page draws the
  circle, and a click on a face chooses a **colour** (the palette's, custom included), a **tool** (every one the
  window acts on) or a **width** (S / M / L) — or takes the face off — with the ring redrawn as the choice
  lands, so the ring is arranged by looking at it. The key that opens it is that page's own row.
- **There is no pen-button row.** On a PC a pen's buttons arrive as **keyboard keys**, mapped by its driver,
  so a button reaches the ring by pressing its key; a pen that sends a real barrel bit has that bit read and
  reported by the tablet probe, and the window gives it no job.
- **The dial is not part of it** (decision 2026-11-01). A turn still zooms at the cursor, its documented job in
  [`desktop-tablet.md`](desktop-tablet.md): the ring is a pick the pen is already making, and a second device for
  the same choice would be a second rule about what a notch means.
