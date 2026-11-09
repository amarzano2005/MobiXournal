# The desktop window's Settings

> Read when a change touches a Settings row, its stored key, or the window's own choices.
> For the task: [Where a setting lives](guide-settings-and-shortcuts.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What it is

`Ctrl+,` (or the header menu's Settings row) opens a **floating surface over the page** — glass, the
family's rim, a 20 dp corner — so a setting is changed while looking at the thing it changes. The pointer is
the panel's while it is up: a press or a wheel notch on a row never reaches the page behind it, because a
sheet that read the click would ink what nothing on screen was marking. It is
**draggable by its title bar**: it opens centred, which is over the sheet the reader came to compare
against, and dragging it is how the thing being changed is kept in view. The offset is clamped so the panel
cannot be lost off an edge (and it is not stored — a window that opened with its panel half off the screen
would be the setting nobody chose). Its fill is `DesktopTheme.settingsPanel`, the glass at **0.97**: at the
floating alpha the page's own ink came up through the labels.

Its index is **twelve sections** — Stylus, Shortcuts, Audio, Figures, Colors, Ring, Toolbar, Navigation,
Appearance, Documents, Backup and About — the tablet's own wherever the topic is the tablet's too, and each
wearing the shared `SettingsGlyphCatalogue` mark (`SettingsSections.kt`). Desktop navigation follows four task areas: **Workspace** (Appearance, Toolbar, Navigation), **Input** (Stylus, Shortcuts, Ring), **Drawing** (Figures, Colors), and **Files & app** (Documents, Audio, Backup, About). This desktop grouping keeps pen shortcuts together and recording beside document workflows. What the window does **not** carry is
the phone's Editor, Gestures and Storage pages: they configure a default tool, a fingertip and a cache this
shell has no row for, and a page of "tablet only" rows is a page the reader learns to skip. A page is
**rows**, the name and the control at the right edge (`SettingsRows.kt`), and a group's heading starts on the
**labels' column** rather than on the card's edge — the rows inside are inset 12 dp, so a group's name and the
names under it begin at the same x.

Desktop groups are flat: a heading, a quiet divider and aligned property rows. Only the outer floating
window wears glass and a rim. This desktop-specific composition shares the tablet's palette, glyphs
and control states while using the wider pointer/keyboard workspace. The panel adapts up to 1050 by
760 dp, with a 210 dp navigation column and a separator before the content.

The list is **names only** — a glyph and a word per section — because two-line summaries are a page to skip.

The header carries a unified **search field** beside the close glyph: a section stays in the list
when its title, its summary, aliases or commands match, and the filter stays active while browsing
results until cleared with `×` or `Esc`. Queries match all words, including area names; results and fallback pages follow the visible index order. It is deliberately not focused on opening: the list is what
a reader returns for, and a focused bar eats a shortcut's first letter.

The panel remembers its last visited section for the current window. A caller can still explicitly
request a section, such as Stylus from the pen menu. Switching sections starts the content at its heading; both navigation and content have themed scrollbars for longer lists.

### Labels and explanations

Rows keep aligned control columns and a minimum 40 dp hit area. Explanations are visible beneath their
labels, up to two lines; the hover footer remains available for the complete sentence. Each page starts
with its title and summary. Groups use whitespace and a hairline instead of nested rounded cards.

### The page's grammar

One rule per kind of thing, so a reader can tell an answer from an action at a glance:

- a **value** ends in a slider with its number beside the track (`Minimum pressure`, `Multiplier`);
- a **choice** ends in a **select**, and in nothing else: the answer in hand on a compact field with a chevron,
  the rest of the list in a menu under it (`Stream`, `Placement`, `New document`) — a row of lozenges was the
  same control in a louder costume. The field takes the pointer, `Enter`/`Space`, and
  Left/Right to step through the answers; the menu is the panel's fill with its rows **inset as compact controls**,
  because a full-bleed row is cut by the popup's rounded edge — a line of selection with its end sliced off
  (`SettingsSelect`);
- a **toggle** ends in a square checkbox, and the whole row is the hit area (`Pressure sensitivity`);
- a **readout** ends in a word or a number with no control at all, at the row's own inset so its small type sits
  on the labels' column (`Version` → *1.0.1*);
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

## Appearance — the tone, and the status bar

**Theme** (`interface.theme`) is the window's tone — **System**, **Dark** or **Light**, System when the file says nothing: the window asks Windows' own *Choose your mode* once, and a host that cannot be asked is dark.
One flag decides every colour in `DesktopTheme`, so desk, bands, ink and rims turn over together.
Under **Appearance**, display controls and status readouts have separate groups:

- **Full screen** (`F11`, or the row's checkbox). The window takes the whole screen through its own
  placement (`FullScreenMode`), and gives it back to the placement it had. Deliberately **not stored**: a
  window opens as a window, and a file that remembered "full screen" would take the desk from a
  double-click.
- **Target rate** — 60, 90, 120 or 144 Hz (`display.targetHz`, default 120), and the row **exists only
  while the window is full screen**. A refresh rate is the panel's mode, not something a window can apply
  to itself, so offering it windowed would be exactly the dead setting this section exists to avoid. While
  full screen, choosing a rate asks the display again without leaving and re-entering the mode.
- **Display** reports the actual refresh rate in its group. **Frame time** (`display.frameTime`) and the painting readout live under **Status bar**.

`DesktopDisplay.kt` is that conversation with the platform, and its rule is the refusal: a laptop panel
has no 144 Hz mode and a remote session refuses every mode change, so `requestRefresh` answers `false`,
changes nothing, and the status bar keeps reporting the true rate — a target that read back as applied
would make the readout the one line in the window that lies. The mode is matched on the display's **own
resolution and bit depth** and on the **exact** rate: a 90 Hz target answered with 60 Hz is a setting that
did nothing while looking applied — one row that did not happen, not an error dialog.

Nothing in the window writes the display's mode except a full-screen request, and leaving full screen (or
closing the window) puts the panel's previous mode back.

## Shortcuts — the key table, as a page

Every command the window has is a row: the tools (their slot's key), and the shell's own commands (open, save,
save as, export, the clipboard, pages, zoom, full screen, Settings) — **sorted into families** in the strips' own
order (`ShortcutFamilies`: the switches first, then ink, selection and text, shapes, STEM, guides, document, editing, view, the pen) and
**searchable** by name, by the chord the window holds or by the tool a key picks. Each group is a **heading over
its rows** and nothing else: no separate *Detect* row, and no group that repeats its own name as a row.

**Specific parts have rows.** The three circuit slots pick their part from a palette; every part of the three
families has a row under its own slot (*Resistor*, *LED*, *XOR gate*), **unbound** until a key is pressed into
it, as the tablet's per-tool fields start — and such a key picks the family's slot **and** arms the part.

Renaming is **click the row, then press the key**: the row lights with *Press a key…*, and the next press this
table has a name for is bound to it. The press is the **window's** — the arm is `DesktopSettingsState.armedShortcut`,
read by `DesktopShellState.onKey` before any focusable row sees it, which is what used to fail. `Esc` puts the
waiting row away, clicking into the search field does too, and neither touches the table.

The rule is **one key, one command** and it is `ShortcutKeys.bind`'s (in `mobix-core`): the key's previous
holder loses it, and the conflict line names the command that did — a key is never silently stolen. A stored
row whose value is `ShortcutKeys.UNBOUND` (`-`) is a command that deliberately has no key, so a displaced row
cannot find its factory key again at the next start. **Reset to defaults** writes nothing at all, which is the
same statement: the factory table is every row that is not in the file. A conflict is reported in a **Binding**
card above the groups, and only while it is the last thing that happened — it is an answer, not a section.

`interface.shortcuts` holds only the rows that **differ** from the factory ones, as `id:key` pairs, so a build
that adds a command ships it with its own key instead of with none. The key a press makes is `ShortcutTable.name`'s
(`Ctrl+Shift+S`, `PageDown`, `Space`) — the same string the pill prints, the file stores and the row shows — and
`ShortcutTable.entries` is the one list the page draws and the keyboard resolves through. The codec, the conflict
rule and the reset are the tablet's too; the defaults are not, because a phone's commands are not a window's.

### Toggle — the switch keys

The page **leads with a Toggle group**, before the two tables: three commands that behave like a stylus
button rather than like a tool's slot. A press picks the tool, and the next press puts back the tool that was
in hand — `Alt+E` the eraser, `Alt+H` the hand, `Alt+S` the selection. The rule is `ToolToggle`'s, as a pure
function: the eraser and the selection are **families of two** on the page (partial and whole-stroke,
rectangle and loop), so a press while *either* member is in hand already counts as "there" and goes back
rather than jumping to the sibling. A last tool that is itself in the family cannot be the answer, so the pen
is.

Which member a press **picks** is a choice, answered **in the toggle's own row** — *Partial / Whole stroke*
beside the eraser's key, *Lasso / Marquee* beside the selection's — and stored as `interface.toggleEraser` and
`interface.toggleSelection`. The hand has no siblings, so its row is alone.

The bindings the table does **not** own stay in `DesktopKeys.unlistedCommand`: `=` arriving as `Shift+0` on an
Italian keyboard, the keypad's `+`/`-`, `Backspace` beside `Delete`, and the arrows beside the page keys. They
are not rows, so they are not remappable.

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
  surface's hamburger menu carries the same drawing rows in the same order (`MenuPenBlock`), at the
  menu's smaller line, because they are reached mid-drawing — the stream itself is the page's row only,
  since which driver is open is not a fact about the stroke being made.
- **Pressure → Presets** — the tablet's named pairs, as a strip of chips where the active one wears the accent,
  one click applies it (`tablet.penPresets`, `tablet.selectedPenPreset`). A **red ×** removes a pair, a **`+`**
  opens the name field, and moving a slider edits the active preset's values.
- **Pressure → Reset to defaults** restores the factory floor and multiplier (0.05 and 1.00×).
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

## Ring — the ring drawn, and what each face holds

Under **Drawing**, beside **Colors** (the palette's own page — [colours.md](colours.md)): **the ring as it is drawn at the pen** — one glass disc, the same faces in
the same order — not a list of rows, so the page answers *what does my ring look like*. **Clicking a face**
opens its chooser, the three families the ring can hold, each with every answer it has: **Colours** (the
palette's swatches, the custom HEX one included), **Tools** (every tool the window puts in hand, from the pen
to a logic gate) and **Widths** (the pen's three slots, each marked by a dot as thick as its stroke). *Remove this position* leaves the ring. The first pick
**pins** it — an untouched ring is *automatic*: the palette's first five colours, the widths and the six tools
(Line, Lasso, Marquee, Highlighter, Eraser, Pen), following the palette — and **Reset to defaults** brings that back. Sixteen faces at most, stored as one line
(`ring.slots`: `c:` a colour, `w:` a width, `t:` a tool); a ring with no faces does not open. **Key** is the
Shortcuts table's *Ring at the pen* row, armed from here. There is no *side button* row: on a PC a pen's
buttons arrive as keyboard keys, so the key is the whole door.

## Toolbar — the strips' place and their order

Under **Toolbar**, in two groups, because *where* the strip stands and *what order* it lists the tools in are
two different questions:

- **Tool strips → Placement** (`stripPlacement`): **Left**, **Right** or **Top**, and nothing else. Left and
  Right are the tablet's **rail**, on the edge opposite the writing hand; Top is the **ribbon** under the
  header. The side is part of the placement rather than a second row, so there is no "a ribbon on the left".
  Both strips are re-laid live, and each **takes a lane** (decision 2026-10-28, `docs/interface-contract.md`):
  the canvas is handed what is left.
- **Buttons**: one row per **slot**, its members listed underneath (the eraser holds partial and
  whole-stroke, the Text slot prose and formula), with a **drag handle** that carries it anywhere in the
  list — the rows between glide open on a short tween, so the gap is where the slot will land, and the
  focused handle steps it one place with an arrow key, which is the list's keyboard path now that a slot
  row draws no chevrons — and, in the
  same list and at its own place in it, the row for **Insert image** (`interface.imageAfter`), the strip's only
  non-ink command: the rows are the strip's contents (`stripItems`), so that row sits where the rail draws it. The arrangement is `DesktopSettingsState.toolGroups` and both strips read it,
  so a button moved here moves in the rail and in the ribbon. **Reset to defaults** puts every slot back;
  the factory order is every slot not in `toolbar.order`, so it writes nothing. A fresh install opens on
  that order: the ink tools, the selection and text commands, the figures, the STEM slots, then the guides.

## Figures — a width of their own

Under **Figures**: the **preset** (the tablet's three S/M/L slots, `PenPalette.DEFAULT_WIDTHS`) and a
**slider** for anything between them (`figures.width`). A figure has no pressure — it is drawn at its full
width — so this value is what a rectangle, an arrow, a circuit or a placed element is stroked with, and it
is why a figure can match a light pen stroke instead of the pen's widest slot. The pen's own widths stay in
the strip's colour capsule, per tool.

The page also carries the two figures whose shape a drag does not fully say: a **triangle**'s kind
(equilateral, right-angled, isosceles, scalene) and a **trapezoid**'s (isosceles, right-angled, scalene),
with the angles a scalene one is built from (`figure.*`, read and written by `FigureSettings`). They are the
tablet's own four and three, so a figure is the same on both shells; the rows only appear where they apply
(the scalene's angles are not shown for an equilateral triangle).

## Documents — the container a new file is born as

Under **Documents**, **New document** (`documents.format`) is the tablet's three-way choice: **`.mobix`**
keeps figures and tables as recipes, **`.xopp`** is a plain gzip document for desktop Xournal++, and
**zipped `.xopp`** carries its attachments inside one file. The tab a new document opens in is written as
that container, and its proposed name carries the same suffix (`Untitled 1.mobix`), so the first save writes
what the setting promised. **Save As** still switches either way, and an opened document keeps its own
format whatever this row says (`docs/mobix-format.md`).

## Audio — the microphone and the output

Under **Audio**, **Microphone** (`audio.inputDevice`) and **Output** (`audio.outputDevice`) are the devices
this machine has, by the platform's own names, with **System default** as the first answer — the answer a
fresh install has, stored as the *absence* of the key rather than as an empty name. The list is read again
every time the page opens, because a headset can be plugged in while the window runs.

A name that is **no longer on the machine** drops to the system default, in the store as well; the recorder and
player fall back to default if a device disappears mid-session (`DesktopAudioDevices.kt`, `chooseDevice`).

**Outside the app**: **Save separate audio** (`audio.saveExternally`) and its **folder** (`audio.exportFolder`)
copy takes externally on completion while keeping them in the document. **Input level** monitors the microphone.

## Navigation — zoom stepping and smooth scrolling

Under **Navigation**, document opening (`interface.fitOnOpen`, **Fit width** by default — a document opens at
100 %, its width filling the window), the sidebar policy (`interface.sidebarPolicy`), and canvas navigation feel:

- **Zoom step**: **Wheel & dial rate** (`navigation.zoomStepWheel`, 2…30%, default 12%) sets the exponential zoom
  factor per mouse wheel notch (`Ctrl + wheel`) or dial click; **Keyboard shortcut step**
  (`navigation.zoomStepKeyboard`, 5…75%, default 25%) sets the scaling factor for `Ctrl +` and `Ctrl -`. Both
  rates are bounded by the window's limits — **25 % … 3 200 % of the 100 % scale** (`Viewport.MIN_ZOOM`/
  `MAX_ZOOM`), so the readout reaches those two numbers on any page.
- **Scrolling**: **Smooth scrolling** (`navigation.smoothScroll`, on by default) glides between wheel notches with
  an immediate lead response followed by exponential decay; **Scroll distance** (`navigation.scrollStepDp`,
  16…160 dp, default 64 dp) sets travel per notch, and **Scroll fluidity** (`navigation.scrollFluidity`, 10…90%,
  default 60%) balances responsiveness and glide. **Reset navigation defaults** restores factory values.

## Backup — the settings, in a file

Under **Backup**, **Export settings…** writes every choice in `desktop.properties` to a file with a stamp
(`backup.format` / `backup.version`), and **Import settings…** reads one back (`DesktopSettingsBackup`). The
import is **wholesale**: a backup is a configuration, so a key it does not carry is a key that was not set
when it was written. A file that is not a backup, or one written by a newer build, is **refused with its
reason** and nothing changes — the sentence appears under the rows, not in a dialog. A successful import
re-reads the inks and the palette, so the change is visible on the page behind the panel rather than only on
the rows, and **Reset all settings…** empties the file — a missing key *is* its default (`resetAllSettings`).
