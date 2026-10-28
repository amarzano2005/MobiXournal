# The desktop window's Settings

> Read when a change touches a Settings row, its stored key, or the window's own choices.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What it is

`Ctrl+,` (or the header menu's Settings row) opens a **floating surface over the page** — glass, the
family's rim, a 20 dp corner — so a setting is changed while looking at the thing it changes. It is
**draggable by its title bar**: it opens centred, which is over the sheet the reader came to compare
against, and dragging it is how the thing being changed is kept in view. The offset is clamped so the panel
cannot be lost off an edge (and it is not stored — a window that opened with its panel half off the screen
would be the setting nobody chose). Its fill is `DesktopTheme.settingsPanel`, the glass at **0.97**: at the
floating alpha the page's own ink came up through the labels.

Its index is **ten sections** — Stylus, Shortcuts, Figures, Colors, Toolbar, Navigation, Appearance,
Documents, Backup and About — the tablet's own wherever the topic is the tablet's too, and each wearing the
shared `SettingsGlyphCatalogue` mark (`SettingsSections.kt`). They are listed under the tablet's **four
areas** — *Input*, *Drawing*, *Interface*, *App & data* (`DesktopSettingArea`) — because ten names in one
flat column is a list read top to bottom while four short groups is one scanned by its shape, and a reader
who uses both shells finds the same subjects under the same headings. What the window does **not** carry is
the phone's Editor, Gestures and Storage pages: they configure a default tool, a fingertip and a cache this
shell has no row for, and a page of "tablet only" rows is a page the reader learns to skip. A page is
**rows**, the name and the control at the right edge (`SettingsRows.kt`).

A row lights **the whole width of its card**: the group clips its rows and pads them vertically only, and
the 12 dp that reads as the card's padding sits inside the row. A highlight with its own margins inside
the card reads as a card inside a card — an element that lights by halves — and that is the shape the row
had before it was measured (`DesktopSettingsStyleTest` diffs a hovered group edge to edge).

The list is **names only** — a glyph and a word per section — because ten two-line summaries is a page of
prose to scan past, and the index is a list, not a column of paragraphs.

The header carries a **search field** beside the close glyph, and it narrows the index **only while it
holds the keyboard**: a section stays in the list when its title, its summary or one of its search aliases
contains the word, and clicking away gives the whole index back. The field is deliberately not focused when
the surface opens — the list is what a reader comes back for, and a bar that already has the keyboard turns
the first letter of a shortcut into a search.

The panel always **opens on Stylus** (`DesktopSettingSection.DEFAULT`), whatever page it was closed on: the
pen is the choice a session starts from, and a window that came back on *About* would be remembering the one
page nobody went back for. A caller that means another section names it — the hamburger's pen block asks for
the stylus explicitly — and the index picks any of the ten afterwards.

### One line per setting, and a help line

A row is **one line tall**: its name, and its answer at the right edge. The sentence that explains the
answer is not drawn under the name — it goes to the panel's **help line**, the footer under the body, which
shows the sentence of whatever the pointer is over (a page row, or an index row, where it is that section's
summary) and the open page's own summary at rest. Two lines per row made a page of twelve settings run past
the bottom of a 640 dp panel: the reader scrolled to find out what a setting already on screen wanted, and
the answers were what got pushed off. The hand-off is the rows' (`LocalSettingsHelp`, `SettingsHelpTest`),
so a page rendered outside the panel — a fixture — simply keeps its sentences to itself.

A note is still **one clause**, not a paragraph: a setting that needs explaining at length is a setting
whose name is wrong, and the detail belongs in this spoke.

### The page's grammar

One rule per kind of thing, so a reader can tell an answer from an action at a glance:

- a **value** ends in a slider with its number beside the track (`Minimum pressure`, `Multiplier`);
- a **choice** ends in a **select**, and in nothing else: the answer in hand on a capsule with a chevron,
  the rest of the list in a menu under it (`Stream`, `Placement`, `New document`). Three lozenges of phrase
  length read as three big buttons, wrapped onto two lines to say one thing, and shouted louder than every
  real control on the page — and a row of short pills for *Left · Right · Top* was the same control in a
  louder costume, two shapes for one kind of thing. The capsule takes the pointer, `Enter`/`Space`, and
  Left/Right to step through the answers; the menu is the panel's fill with its rows **inset as capsules**,
  because a full-bleed row is cut by the popup's rounded edge — a line of selection with its end sliced off
  (`SettingsSelect`);
- a **toggle** ends in the switch, and the whole row is the hit area (`Pressure sensitivity`);
- a **readout** ends in a word or a number with no control at all (`Recovery snapshot` → *After every edit*);
- a **command** is a row with **nothing at its end**: the label is in the accent and the row is the button
  (`Reset to defaults`, `Export settings…`, `SettingsCommand`) — one label for every way back to the
  factory values, in whichever section it stands.

A **destructive** mark is never a command row: it is a red glyph on the thing it destroys — the × on a pen
preset — because "delete which one?" is a question a row at the bottom of a list cannot answer.

A row that has a mechanism behind it is a real control and changes the open window the moment it is
clicked. A row the window cannot answer is **drawn and disabled with one sentence saying what is
missing** — never invented and never silently dropped, because a list that pretends a setting does not
exist is how the two shells drifted apart in the first place. The rule for a live row is the one the
section lives by: **a hook, a key, and a test** (`DesktopSettingsTest`, `DesktopSettingsStyleTest`).

Every stored answer lives in `DesktopSettings` (`%APPDATA%\MobiXournal\desktop.properties`), is written
the moment the row is clicked, and is read back by the next window — a setting that only reached Compose
state would look right until the next launch.

## Display — the status bar's own section

Under **Appearance → Status bar**, because every row here is about the screen the window paints on:

- **Full screen** (`F11`, or the row's switch). The window takes the whole screen through its own
  placement (`FullScreenMode`), and gives it back to the placement it had. Deliberately **not stored**: a
  window opens as a window, and a file that remembered "full screen" would take the desk from a reader who
  double-clicked a notebook.
- **Target rate** — 60, 90, 120 or 144 Hz (`display.targetHz`, default 120), and the row **exists only
  while the window is full screen**. A refresh rate is the panel's mode, not something a window can apply
  to itself, so offering it windowed would be exactly the dead setting this section exists to avoid. While
  full screen, choosing a rate asks the display again without leaving and re-entering the mode.
- **Frame time** (`display.frameTime`) starts and stops the status bar's paint readout, and the **Display**
  row under it always reports the rate the panel is **really** at (`refreshLabel(displayRefreshHz())`).

`DesktopDisplay.kt` is that conversation with the platform, and its rule is the refusal: a laptop panel
has no 144 Hz mode and a remote session refuses every mode change, so `requestRefresh` answers `false`,
changes nothing, and the status bar keeps reporting the true rate — a target that read back as applied
would make the readout the one line in the window that lies. The mode is matched on the display's **own
resolution and bit depth** and on the **exact** rate: a 90 Hz target answered with 60 Hz is a setting that
did nothing while looking applied. A refused or unsupported mode is not an error dialog, it is one row
that did not happen.

Nothing in the window writes the display's mode except a full-screen request, and leaving full screen (or
closing the window) puts the panel's previous mode back.

## Shortcuts — the key table, as a page

Every command the window has is a row: the tools (their slot's key), and the shell's own commands (open, save,
save as, export, the clipboard, pages, zoom, full screen, Settings). Each group is a **heading over its rows**
and nothing else — there is no separate *Detect* row and no group that repeats its own name as a row.

Renaming is **click the row, then press the key**: the row lights with *Press a key…*, and the next press this
table has a name for is bound to it. The press is the **window's**, not the page's — the arm is
`DesktopSettingsState.armedShortcut`, read by `DesktopShellState.onKey` before anything else, because the
window sees a press before any focusable row can. (Keeping the arm and the focus inside the page is what used
to fail: the row was drawn waiting, the surface captured the key, and nothing was ever bound.) `Esc` puts the
waiting row away, clicking into the search field does too, and neither touches the table.

The rule is **one key, one command** and it is `ShortcutKeys.bind`'s (in `mobix-core`): the key's previous
holder loses it, and the conflict line names the command that did — a key is never silently stolen. A stored
row whose value is `ShortcutKeys.UNBOUND` (`-`) is a command that deliberately has no key, so a displaced row
cannot find its factory key again at the next start. **Reset to defaults** writes nothing at all, which is the
same statement: the factory table is every row that is not in the file. A conflict is reported in a **Binding**
card above the groups, and only while it is the last thing that happened — it is an answer, not a section.

`interface.shortcuts` holds only the rows that **differ** from the factory ones, as `id:key` pairs, so a build
that adds a command ships it with its own key instead of with none. The key a press makes is
`ShortcutTable.name`'s (`Ctrl+Shift+S`, `PageDown`, `Space`) — the same string the pill prints, the file
stores and the row shows — and `ShortcutTable.entries` is the one list the page draws and the keyboard
resolves through. The codec, the conflict rule and the reset are shared with the tablet's Shortcuts section,
which is why a key means the same tool on both shells; the defaults are not shared, because a phone's commands
are not a window's.

### Toggle — the switch keys

The page **leads with a Toggle group**, before the two tables: three commands that behave like a stylus
button rather than like a tool's slot. A press picks the tool, and the next press puts back the tool that was
in hand — `Alt+E` the eraser, `Alt+H` the hand, `Alt+S` the selection. The rule is `ToolToggle`'s, as a pure
function: the eraser and the selection are **families of two** on the page (partial and whole-stroke,
rectangle and loop), so a press while *either* member is in hand already counts as "there" and goes back
rather than jumping to the sibling. A last tool that is itself in the family cannot be the answer, so the pen
is.

Which member a press **picks** is a choice, so each of the two families carries a cascade under its row —
*Eraser* (partial / whole stroke) and *Selection* (marquee / lasso) — stored as `interface.toggleEraser` and
`interface.toggleSelection`. The hand has no siblings, so its row is alone.

The bindings the table does **not** own stay in `DesktopKeys.unlistedCommand`: `=` arriving as `Shift+0` on an
Italian keyboard, the keypad's `+`/`-`, `Backspace` beside `Delete`, and the arrows beside the page keys. They
are not rows, so they are not remappable — and none stops working when another row is remapped.

## Stylus — the tablet's own section

Under **Stylus**, in two groups — the pen's **Pressure** (what it is read from, whether a press changes the
width, and the preset that names its two numbers followed by those numbers) and the **Active area** the device
reads — because the hand decides how hard it presses while the device decides how far that hand travels:

- **Pressure → Stream** (`tablet.penStream`): *Automatic* tries Wintab — the driver's `Wintab32.dll`, whose
  packets carry the pressure at the tablet's own rate — and falls back to Windows Ink, which walks the
  digitizer list with `GetPointerPenInfo`; *Windows Ink* skips the driver library, for a pen whose Wintab
  reports a contact the pointer stream reports better. The choice **reopens the readers** with the next
  frame (`DesktopTablet.open` is keyed on it), so a switch is never a stroke that jumps half way through
  one, and the status bar keeps naming the stream actually open — a preference this host cannot honour
  says so instead of looking applied.
- **Pressure → Pressure sensitivity** (`tablet.pressureEnabled`, on by default), then **Minimum pressure**
  (`tablet.minimumPressure`) and **Multiplier** (`tablet.pressureMultiplier`): the tablet's Stylus rows, and
  the two numbers of desktop Xournal++'s own filter (`docs/stylus.md`). The floor is the thinnest line a
  light press can draw, the multiplier how far above 1.00 a light writer reaches the full width; both belong
  to the **preset in use** and are read by the same shared filter the phone and the desktop vector painter
  use, so a stroke is the same width on either shell. A `tablet.pressureExponent` written by an earlier
  build is **ignored**: the row is gone and so is the shaping, because a knob no surface shows is a
  difference nobody can see between the phone's widths and the window's. With sensitivity **off** every
  stroke is drawn at its
  set width, and the two numbers stay on screen because that is the state they are remembered in. The
  surface's hamburger menu carries the same rows in the same order (`MenuPenBlock`), at the menu's smaller
  line, because they are reached mid-drawing.
- **Pressure → Presets** — the tablet's named pairs, as one **carousel**: a strip of chips where the preset
  in use wears the accent on the steel fill, one click applies it (`tablet.penPresets`,
  `tablet.selectedPenPreset`). The strip's two marks act on the thing they sit on: a **red ×** beside a chip
  removes that pair — drawn only while a second one exists, because the pen always draws with one — and a
  **`+`** caps the last chip, opening the one name field the *Stylus* page and the menu's Pen block share
  (the same draft, whichever surface did it; the `+` is dimmed while a name is being written). A preset is **only those two
  numbers**, and the sliders under the strip edit *its* numbers (their notes say so), so moving one
  **rewrites the live entry** and the lit chip never names numbers the pen is not drawing with. Saving an
  existing name rewrites it in place (the handle, and so the selection, survives), and a stored line this
  build cannot read falls back to the factory preset rather than emptying the strip.
- **Pressure → Reset to defaults** puts the floor and the multiplier back on the factory numbers (0.05 and
  1.00×) through the same two setters, so it rewrites the live preset like a slider would — the tablet's
  *Reset*, and the only way to undo a slider dragged far without a drag back.
- **Active area → Mapped surface** (`tablet.activeArea`): *Full* is the driver's own **system context**, the
  surface every other program on the machine sees, and it is what the window has always read. *Tablet* maps
  a **centred rectangle** of that surface onto the window's client area instead, so a small movement of the
  hand covers the page.
- **Width** and **Height** (`tablet.areaWidthPct`, `tablet.areaHeightPct`, 10…100 %) and **Aspect lock**
  (`tablet.aspectLock`, on by default). The percentages are of the **surface**, not millimetres, because the
  driver declares its axes in counts and no physical size this shell can trust — and both spans being
  percentages is what makes the lock one line of arithmetic: an equal pair *is* the tablet's own shape, so
  the lock moves the height with the width. A *Tablet* area grown to 100 % on both axes is the whole
  surface, and the reader takes the system context rather than opening a second one for the same rectangle.

The mechanism is a real context of the window's own: `TabletAreaMap` turns the rectangle into the driver's
`lcInOrg`/`lcInExt` (the device axes, `DVC_X`/`DVC_Y`) and the client area into `lcOut`, with
`CXO_SYSTEM` cleared — a screen-mapped context would ignore every field. A driver that declares no usable
axis, or a context it refuses to open, **keeps the system context** rather than leaving a dead pen.

The window has **no barrel-button row**: the driver reads the pen's buttons and the window receives them as
the eraser tip, so there is no second meaning to configure — and a row for it was a page of one dead
setting.

## Toolbar — the strips' place and their order

Under **Toolbar**, in two groups, because *where* the strip stands and *what order* it lists the tools in are
two different questions:

- **Tool strips → Placement** (`stripPlacement`): **Left**, **Right** or **Top**, and nothing else. Left and
  Right are the tablet's **rail**, on the edge opposite the writing hand; Top is the **ribbon** under the
  header. The side is part of the placement rather than a second row, so there is no "a ribbon on the left".
  Both strips are re-laid live, and each **takes a lane** (decision 2026-10-28,
  `docs/interface-contract.md`): the canvas is handed what is left, so the choice moves the page's room and
  no bar ever stands on the sheet.
- **Buttons**: one row per **slot**, its members listed underneath (the eraser holds partial and
  whole-stroke, the Text slot prose and formula), with a pair of chevrons that move it up or down. The
  arrangement is `DesktopSettingsState.toolGroups` and both strips read it, so a button moved here moves in
  the rail and in the ribbon — and a picker's chevron follows its slot. **Reset to defaults** puts every
  slot back; like the key table's own reset, it writes nothing, because the factory order is every
  slot that is not in the file (`toolbar.order`).

## Figures — a width of their own

Under **Figures**: the **preset** (the tablet's three S/M/L slots, `PenPalette.DEFAULT_WIDTHS`) and a
**slider** for anything between them (`figures.width`). A figure has no pressure — it is drawn at its full
width — so this value is what a rectangle, an arrow, a circuit or a placed element is stroked with, and it
is why a figure can match a light pen stroke instead of the pen's widest slot. The pen's own widths stay in
the strip's colour capsule, per tool.

## Documents — the container a new file is born as

Under **Documents**, **New document** (`documents.format`) is the tablet's three-way choice: **`.mobix`**
keeps figures and tables as recipes, **`.xopp`** is a plain gzip document for desktop Xournal++, and
**zipped `.xopp`** carries its attachments inside one file. The tab a new document opens in is written as
that container, and its proposed name carries the same suffix (`Untitled 1.mobix`), so the first save writes
what the setting promised. **Save As** still switches either way, and an opened document keeps its own
format whatever this row says (`docs/mobix-format.md`).

## Backup — the settings, in a file

Under **Backup**, **Export settings…** writes every choice in `desktop.properties` to a file with a stamp
(`backup.format` / `backup.version`), and **Import settings…** reads one back (`DesktopSettingsBackup`). The
import is **wholesale**: a backup is a configuration, so a key it does not carry is a key that was not set
when it was written. A file that is not a backup, or one written by a newer build, is **refused with its
reason** and nothing changes — the sentence appears under the rows, not in a dialog. A successful import
re-reads the inks and the palette, so the change is visible on the page behind the panel rather than only on
the rows.
