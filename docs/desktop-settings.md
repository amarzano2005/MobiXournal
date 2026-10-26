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

Its index is **ten sections** — Stylus, Toolbar, Figures, Colors, Shortcuts, Navigation, Appearance,
Documents, Backup and About — the tablet's own wherever the topic is the tablet's too, and each wearing the
shared `SettingsGlyphCatalogue` mark (`SettingsSections.kt`). What the window does **not** carry is the
phone's Editor, Gestures and Storage pages: they configure a default tool, a fingertip and a cache this
shell has no row for, and a page of "tablet only" rows is a page the reader learns to skip. A page is
**rows**, the name and one sentence on the left and the control at the right edge (`SettingsRows.kt`).

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
save as, export, the clipboard, pages, zoom, full screen, Settings). A row shows the key it holds; clicking it
arms **Detect** and the next press is bound, so the field cannot bind a key the keyboard does not name. The
press is caught on the page's container rather than on the row (`ShortcutSettings`), which is what makes one
press enough: arming a row hands the focus to the page, and `Esc` is the way out of a field that is waiting.

The rule is **one key, one command** and it is `ShortcutKeys.bind`'s (in `mobix-core`): the key's previous
holder loses it, and the conflict line names the command that did — a key is never silently stolen. A stored
row whose value is `ShortcutKeys.UNBOUND` (`-`) is a command that deliberately has no key, so a displaced row
cannot find its factory key again at the next start. **Restore defaults** writes nothing at all, which is the
same statement: the factory table is every row that is not in the file.

`interface.shortcuts` holds only the rows that **differ** from the factory ones, as `id:key` pairs, so a build
that adds a command ships it with its own key instead of with none. The key a press makes is
`ShortcutTable.name`'s (`Ctrl+Shift+S`, `PageDown`, `Space`) — the same string the pill prints, the file
stores and the row shows — and `ShortcutTable.entries` is the one list the page draws and the keyboard
resolves through. The codec, the conflict rule and the reset are shared with the tablet's Shortcuts section,
which is why a key means the same tool on both shells; the defaults are not shared, because a phone's commands
are not a window's.

The bindings the table does **not** own stay in `DesktopKeys.unlistedCommand`: `=` arriving as `Shift+0` on an
Italian keyboard, the keypad's `+`/`-`, `Backspace` beside `Delete`, and the arrows beside the page keys. They
are not rows, so they are not remappable — and none stops working when another row is remapped.

## Stylus — the tablet's own section

Under **Stylus**, in two groups, because the device decides both which stream exists and how much of it the
window listens to:

- **Pen → Stream** (`tablet.penStream`): *Automatic* tries Wintab — the driver's `Wintab32.dll`, whose
  packets carry the pressure at the tablet's own rate — and falls back to Windows Ink, which walks the
  digitizer list with `GetPointerPenInfo`; *Windows Ink* skips the driver library, for a pen whose Wintab
  reports a contact the pointer stream reports better. The choice **reopens the readers** with the next
  frame (`DesktopTablet.open` is keyed on it), so a switch is never a stroke that jumps half way through
  one, and the status bar keeps naming the stream actually open — a preference this host cannot honour
  says so instead of looking applied.
- **Pen → Pressure sensitivity** (`tablet.pressureEnabled`, on by default), then **Minimum pressure**
  (`tablet.minimumPressure`) and **Multiplier** (`tablet.pressureMultiplier`): the tablet's Stylus rows, and
  the two numbers of desktop Xournal++'s own filter (`docs/stylus.md`). The floor is the thinnest line a
  light press can draw, the multiplier how far above 1.00 a light writer reaches the full width; both are
  read by the same shared filter the phone and the desktop vector painter use, so a stroke is the same
  width on either shell. With sensitivity **off** every stroke is drawn at its set width, and the two
  numbers stay on screen because that is the state they are remembered in. The surface's hamburger menu
  carries the same three controls (`MenuPenBlock`), because they are reached mid-drawing.
- **Pen → Pressure curve** (`tablet.pressureExponent`, 0.5…2.0): **mine, not the desktop's**. 1.0 is the
  line above, and any other reading is opt-in shaping applied *before* the Xournal++ filter at both of the
  pen's adapters (`PenPacketStroke` for the driver's packets, `PageInkGesture.inkPointer` for Compose's
  pointer) rather than after the width, because a curve in the width would bend the eraser radius and the
  highlighter's width too. Windows' emulated mouse is a pressure-less path, so its strokes stay at full
  width and the row cannot change them.
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
  Both strips are re-laid live, and both **float over the page** (decision 2026-10-26,
  `docs/interface-contract.md`): the canvas keeps the whole body, so the choice moves the tools and never the
  sheet.
- **Buttons**: one row per **slot**, its members listed underneath (the eraser holds partial and
  whole-stroke, the Text slot prose and formula), with a pair of chevrons that move it up or down. The
  arrangement is `DesktopSettingsState.toolGroups` and both strips read it, so a button moved here moves in
  the rail and in the ribbon — and a picker's chevron follows its slot. **Factory order** puts every slot
  back; like **Restore defaults** on the key table, it writes nothing, because the factory order is every
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
