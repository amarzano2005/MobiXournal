# Where a setting lives, and what the keys are

> Read when you cannot find a setting, want a key for a command, or need to put the pen's pressure curve
> back the way it was.

_A guide page for Android and Windows: the settings areas on each shell, the window's keys, and the pen presets. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.2.0** and desktop **1.2.0**.

## Opening Settings, and finding one

Both shells keep settings in one list, and both let you search it instead of scrolling: the window filters
its sections by name and by keyword, and the tablet's list has the same search over the same areas.
`Ctrl+,` opens it in the window.

## The window's twelve sections

| Section | What changing it affects |
|---|---|
| **Stylus** | The pen stream it reads, the pressure numbers, the active area, and the preset that names them |
| **Toolbar** | Where the tool strips stand, and the order of their buttons |
| **Figures** | The width the figure tools draw at, and each figure's own variant |
| **Colors** | The palette, the pen's colour and width, the line style, the favourites |
| **Ring** | What the pen-tip ring holds, and the key that opens it |
| **Shortcuts** | The key table below, remapped command by command |
| **Audio** | The microphone it records from and the output it plays through |
| **Navigation** | How a document opens, and how the page list behaves |
| **Appearance** | The theme, and what the status bar reads |
| **Documents** | The format a new document is written in, and its page |
| **Backup** | The recovery snapshot, and the file that carries your settings |
| **About** | The version, the formats it reads, and what the app is |

## The tablet's areas

One scrolling list, grouped by what the choice touches: the ink and its pressure, the barrel button, touch
and palm handling, panning, momentum, the two- and three-finger taps, the drawing aids and shape
recognition, new documents and their default and quick page sizes, both toolbars and their buttons, the pen
palette and favourite colours, the ring, audio, import and export, the cache, a reset, and credits and
licence. The groups are the same areas the window calls sections — a setting you know on one shell is
findable on the other by its subject, not by its name.

Two lists are arranged by hand. On either shell, drag a swatch's handle in the Colors page to change the
palette's order — every picker, the Ring and the colour shortcuts follow it. In the window's Toolbar page,
drag a tool slot by its handle to move it in both the rail and the ribbon; the chevrons beside it step one
place for the keyboard.

## The window's keys

| Command | Key | | Command | Key |
|---|---|---|---|---|
| Eraser, or the last tool | `Alt+E` | | Undo | `Ctrl+Z` |
| Hand, or the last tool | `Alt+H` | | Redo | `Ctrl+Shift+Z` |
| Selection, or the last tool | `Alt+S` | | Cut | `Ctrl+X` |
| Open a document | `Ctrl+T` | | Copy | `Ctrl+C` |
| Search the document | `Ctrl+F` | | Paste | `Ctrl+V` |
| Save | `Ctrl+S` | | Duplicate | `Ctrl+D` |
| Save as | `Ctrl+Shift+S` | | Delete the selection | `Delete` |
| Export PDF | `Ctrl+E` | | Show the pages | `Ctrl+B` |
| Close the tab | `Ctrl+W` | | Settings | `Ctrl+,` |
| Next tab | `Ctrl+Tab` | | Full screen | `F11` |
| Previous tab | `Ctrl+Shift+Tab` | | Pan with the pointer | `Space` |
| Ring at the pen | `Alt+B` | | Next page | `PageDown` |
| Zoom in | `Ctrl+=` | | Previous page | `PageUp` |
| Zoom out | `Ctrl+-` | | Fit the page | `Ctrl+0` |
| Fit the width (100%) | `Ctrl+1` | | Dismiss the notice | `Esc` |

The tablet has no keyboard table to learn: its tools live on the rails, and the barrel button is the
shortcut — what that button does is a setting, and the pen's own eraser tip needs none.

## The pen's pressure, and putting it back

A pressure preset **is** two numbers: the minimum pressure and the multiplier that turns pressure into
width. The window's Stylus section lets you name a preset, keep several, and switch between them; the
tablet's Pressure group carries the same pair. **Reset** restores the factory floor and multiplier for the
preset that is active, which is the way back from a curve you tuned until the ink felt wrong.

The numbers are shared with desktop Xournal++'s filter rather than invented here, so a line drawn at a
given pressure should feel the same in both apps; [how the pen behaves](guide-inking-and-gestures.md)
covers what the width does on the page.

## Backup, and moving your settings

The window's Backup section writes a recovery snapshot and a file that carries your settings, so a new
machine can be given the same key table and the same pen curve without being set up by hand. Settings are
per installation, not per document — they do not travel inside a notebook, and a `.mobix` or `.xopp`
opened on another machine arrives with no preferences attached.

## How it works

- [chrome-settings.md](chrome-settings.md) — the Settings shell, its search, and the prefs behind it.
- [desktop-settings.md](desktop-settings.md) — the window's store keys and what each one holds.
- [desktop-ui-layout.md](desktop-ui-layout.md) — the window's layout, bars and tool slots.
- [ui-modernization.md](ui-modernization.md) — the copy rules a setting's label and note follow.
