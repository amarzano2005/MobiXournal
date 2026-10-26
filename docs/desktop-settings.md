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
