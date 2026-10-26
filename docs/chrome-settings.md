# The Settings shell: its two shapes, its search, and the prefs behind them

> Read when you add, move or remove a section of the tablet's Settings, or change how it is searched.

How the phone's Settings screen is laid out and searched — the areas, the sidebar, and the settings
objects the sections read and write. What the window's own Settings surface looks like is
[desktop-settings.md](desktop-settings.md)'s, and the chrome the screen is reached from is
[chrome.md](chrome.md)'s.

## Settings: two shapes, one set of areas

Choosing Settings
from the ☰ menu swaps in `SettingsScreen`, which is shaped like a tablet's system settings and has **two
shapes** for one set of areas. On a screen at least `SETTINGS_TWO_PANE_MIN_WIDTH` (600dp) wide the four
`SettingsArea`s — **Input**, **Drawing**, **Interface**, **App & data** — are a **permanent side menu**
(`SettingsSidebar.kt`: its own search field and one row per area) with the selected area's
`SettingsSection`s in the pane beside it, and an opened section replaces that pane (the title bar and the
back arrow return to the list). Narrower than that the side menu is dropped for the pushed flow — index,
then area, then section — because a permanent list on a phone would leave the controls a strip to live
in. Back pops whichever shape is on screen one step at a time (section → search results → area → out of
settings), so the system back button mirrors the title-bar arrow either way.

## Settings search

**Search** runs over the sections from both shapes (`SettingsSearch.kt`, pure): a query matches a
section's title, its summary, or its `keywords` aliases — the everyday words for the controls a user
might type instead of the app's label ("momentum", "wallpaper", "cache", "left-handed"). The aliases
are what makes search *find* rather than filter titles, so `SettingsSearchTest` pins a handful of those
queries to their sections and requires every section to carry aliases and answer to its own title. A
result names the area it lives in (`areaOf`), so a hit found by an alias still says where the setting
belongs; opening one goes straight to the section. `SettingsAreaTest` pins the structure the whole
screen rests on: the areas cover every section exactly once (an unlisted section would be unreachable)
and no area owns a single child (a one-child area is a tap that explains nothing). Section bodies live
in their own `*Section.kt` files (`StylusSection.kt`, `EditorSection.kt`, …, `ShortcutsSection.kt`). The **Shortcuts** section
owns the whole keyboard-shortcut table: `AppSettings.penEraserToggleKey` / `handToggleKey` (the two
toggles) plus two maps — `toolShortcutKeys` (one key per `EditorTool`) and `colorShortcutKeys` (one
key per palette swatch). They are persisted by `encodeToolShortcuts` / `decodeToolShortcuts`
and `encodeColorShortcuts` / `decodeColorShortcuts` (the same `key:value` comma-list style as the
other codecs, dropping unknown tools/colours and blank keys), migrating the old per-colour keys
(`black_pen_key` etc.) once. `EditorRegions.onKeyPressed` resolves a character against those maps —
toggles first, then colour, then tool — so the table is data, not code. The factory pen palette (`PEN_COLORS` — desktop Xournal++'s default palette, hex for hex; see `PaletteColorsTest`) and `PEN_WIDTH_LABELS`
now read `mobix-core`'s `design/PenPalette.kt`, the one home of the values and rules (`ToolbarColorPopup.kt` / `ToolbarSizePopup.kt` keep only the UI); the **live** palette is `AppSettings.penColors`, seeded with `PEN_COLORS` and edited under **Settings → Drawing → Colors**
(`ColorsSection.kt`: add / redefine / delete, capped at `MAX_PEN_COLORS`, opaque and
de-duplicated by `sanitized()`, round-tripped by `encodePenColors` / `decodePenColors` which falls
back to the factory list rather than blanking the pickers). The user's own colours are part of the
palette, not guests of it: neither `sanitized()` nor `decodePenColors` prunes a colour for being
absent from `PREDEFINED_COLOR_NAMES` (that map is display names only), so a swatch added here comes
back on the next launch and travels in — and returns from — the JSON backup. Deleting a colour drops its
`colorShortcutKeys` entry with it, and the last swatch can't be deleted. The user-configurable pen widths and the editable custom colour are
persisted in `AppSettings`/`SettingsStore`, and the arbitrary-colour HSV/hex picker is in
`ColorPicker.kt`. Every place a colour is chosen — the pen's rail button, the text-box dialog, the
selection recolour menu — renders the **one** `ColorPaletteRows` component (`ColorPalette.kt`):
the user's swatches, the editable custom slot (long-press → `CustomColorEditor`),
reading and writing one `ColorPaletteState` over `AppSettings` (its `colors` is `penColors`, so an
edit under Settings shows up everywhere at once). The swatches wrap (`FlowRow`) so the
custom slot survives a narrow dialog, and the HSV editor is hoisted *outside* the menu that opened it
so dismissing that menu doesn't take the dialog with it. `AppSettings` also remembers the pen you
left off with — `lastColor`/`lastWidth`, re-pushed onto a freshly created surface by `EditorScreen`;
`withColorUsed(color)` is now just that write, since the recently-used list was removed along with
its pref key. The **Select** tool adds a rail entry and a floating action bar; its
mechanics are in [`selection.md`](selection.md#selecting-objects-render).
