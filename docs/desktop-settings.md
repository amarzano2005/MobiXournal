# The desktop window's Settings

> Read when a change touches a Settings row, its stored key, or the window's own choices.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What it is

`Ctrl+,` (or the header menu's Settings row) opens a **floating surface over the page** — glass, the
family's rim, a 20 dp corner — so a setting is changed while looking at the thing it changes. Its index is
the **tablet's thirteen sections**, in the tablet's order, each wearing the shared `SettingsGlyphCatalogue`
mark (`SettingsSections.kt`); a page is **rows**, the name and one sentence on the left and the control at
the right edge (`SettingsRows.kt`).

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

**Barrel button** is drawn and unavailable: the driver reads the pen's buttons and the window receives them
as the eraser tip, so there is no second meaning to configure yet.
