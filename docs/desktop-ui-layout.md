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
│ [≡] [Notebook 1 ×] [Notebook 2 ×] [+]           [↶] [↷] [Save] [Export PDF]         │  header band
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
  document (each keeps its page, zoom and fit), the `+` for another, and right **undo, redo, Save, PDF**.
- **Collapsible navigation sidebar (`Ctrl+B`)**: page thumbnails and the current page's layers, with an eye
  per layer that hides it without writing to the document; it opens itself only when the document has
  **more than one page**.
- **The sheet alone on the desk**: one page at a time, fitted by what the page is (a sheet whole, a long
  strip to its width), with the fit mode named in the status bar.
- **Status bar**: page counter, effective zoom, fit mode and the container behind the document.

**Desktop decisions (2026-10-21):**
- **The chrome is the app's own, on a true-black desk — not the window manager's.** The header, the
  sidebar and the tool strips are the same frosted glass the mobile chrome uses, and the desk behind the
  page is `#000000`; platform materials (Windows 11 Mica) are **not** used: Mica tints the window with the
  wallpaper, so it would stop matching the phone and change with the user's own picture.
- **Both tool placements ship**: the vertical rail is the default, the horizontal ribbon the alternative
  for 16:9 monitors. Whichever is on, the strip is a capsule in a lane of **air** beside the canvas, never a
  bar over the page; the choice lives in the settings file, and the Interface section will own its door.
- **The window's bands are blacker than the tablet's** (2026-10-25): the same glass RGB at
  `DESKTOP_BAND_ALPHA` (0.58), because 0.85 composites to `#131314`, which in a maximised window on a 34"
  screen read as a grey slab beside the phone; the band is `#0C0C0D` — the one tone the shells do not share.

**The shell as built (2026-10-22).** `desktop/` has the header, tab strip, sidebar, workspace, status bar
and both tool strips ([`desktop-workstation.md`](desktop-workstation.md) owns its inventory); from **this**
spoke it takes four rules, and the parity work of 2026-10-22 put them all in the window:

- **A band is chrome, a capsule is an object.** The header, status bar and sidebar take the glass fill
and **one 4 dp cast shadow**, no rim. A strip's lane is **air, not a band** — painting it too stacked two
sheets of 0.85 glass and the rail read #161618. The rail is a **52 dp capsule in a 64 dp lane** (6 dp of
side air, 8 above and below), the ribbon a 48 dp capsule in a 56 dp band; each takes its **own 6 dp lift**
and the family's rim, and a header control is that capsule at 36 dp. Without the lifts the bands were flat,
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
- **No motion of its own**: the windows' surfaces arrive on `DesignTokens.GlassMotion` too. The `Popup`
flyouts still appear instantly — a desktop popup has no composition to hold open once its flag clears.
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
  with no submenu between the user and them — and so does **every figure**: Line, Arrow, Double arrow,
  Rectangle, Ellipse, Spline and Axes are seven slots of their own on the desktop, one click each.
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
| | Text Box | `X` | Screen double-tap with finger |
| | Formula (LaTeX / Typst) | `Shift + T` | The Text slot's second member (the tablet's LaTeX tool); asks its source first |
| | Hand Tool | `G` | A slot of the rail: a primary drag moves the page |
| | Pan | `Space` (held) | Two-finger scroll / drag |
| **Geometry & Figures** | Straight Line / Ruler | `L` | Hold-to-straighten dwell (300 ms) |
| | Arrow | `A` | Arrow shape toggle |
| | Double arrow | `Shift + A` | Arrow slot's second member |
| | Rectangle | `R` | Shape dwell |
| | Circle / Ellipse | `C` | Shape dwell |
| | Free Spline | `P` | Spline tool slot |
| **STEM & Advanced** | Vertical space | `Shift + V` | Reflow tool in the rail's Tool pop-up |
| | Cartesian Coordinate Axes | `Shift + X` | 1-tap STEM dock button |
| | Function Plot $f(x)$ | `M` | 1-tap STEM dock button; asks formula, range and flags first |
| | Dynamic Table | `T` | 1-tap STEM dock button; asks its grid first |
| | Circuit parts: passive · active · logic | `K` / `Shift + K` / `Alt + K` | Three STEM dock buttons, each with its palette |
| | Audio Tap / Replay | `Y` | Audio tool slot |
| **Styling & Navigation** | Favorite Colors 1 / 2 / 3 | `1`, `2`, `3` | Tap on color swatch / Rail slot |
| | Tool Group Picker | `Alt + Down` | Chevron, right-click or hold on a grouped slot |
| | Stroke Width Up / Down | `]`, `[` | Rail's size slot |
| | Zoom In / Out | `Ctrl + =`, `Ctrl + -` | Pinch gesture |
| | Undo / Redo | `Ctrl + Z`, `Ctrl + Shift + Z` | Two-finger tap |
| | Full screen (window) | `F11` | — desktop only: the tablet is already the whole screen |

- **Universal Shortcut & Individual Color Remapping**: All keybindings, tools, and **individual color palette slots**
  are remappable via **Settings → Input & Shortcuts**: a shortcut or a stylus gesture can select a specific
  user-defined HEX color (e.g. `Alt+1` for Pure Black `#000000`), not just the 3 factory favourites.
