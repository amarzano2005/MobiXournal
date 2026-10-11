# Colour: the shared palette and the chrome's colour roles

> Read when you change a colour, the picker, the palette state, or a theme role.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Colour palette (`ColorPalette.kt`, `ColorPicker.kt`)

The one colour picker all three sites share (the pen pop-up, the text-box dialog, the selection
recolour menu), plus the shortcut tables that jump straight to a colour or a tool (see
`ShortcutsSection.kt` and the `toolShortcutKeys` / `colorShortcutKeys` maps in `AppSettings`):

- **`ColorPaletteState`** — the shared colour state: the editable custom slot, persisted in
  `AppSettings`. One instance is threaded to every picker
  (toolbar palette, text-box dialog, selection recolour menu), so a colour redefined anywhere reads
  the *same* custom slot. There is no recently-used list: the palette itself is the list.
  `custom` is **nullable**: the slot is empty on a fresh install (`AppSettings.customColor` is null)
  rather than pre-filled with a colour nobody chose, and an empty slot is stored as an *absent* pref
  key and an explicit `null` in the JSON backup, so "empty" and "black" stay distinguishable.
  `addColor` is the pop-up's append (opaque, de-duplicated, capped at `MAX_PEN_COLORS`).
- **`ColorPaletteRows`** — the one colour picker composable: the user's palette (`penColors`,
  seeded with the eight hexes of `PEN_COLORS` and predefined English names) then the
  editable custom slot (marked with a pencil, long-press to redefine; an empty slot *opens* the
  editor on a tap, since it holds no colour to pick).
  A tap reports the colour through `onPick`; the host decides what it means (set the pen,
  restyle the selection, colour the text). `compact` (the toolbar pop-up) lays the swatches in a
  **single horizontally scrolling row** so the menu's height is fixed however many colours the
  palette holds — a wrapping grid would grow the pop-up with every swatch added — and `onAdd`
  appends the trailing add-colour swatch.
- **Colour parity with desktop.** Beyond the palette, every colour that names a *document* or *paper*
  appearance is taken from desktop Xournal++: the named `.xopp` colours (`format/XoppColor.kt`,
  upstream's `PREDEFINED_COLORS` → `Colors::xopp_royalblue` / `xopp_deepskyblue` / `lime` /
  `xopp_darkorange` / …), the ruling hues (`BackgroundGrid.LINED_RGB` = `xopp_dodgerblue`,
  `GRAPH_RGB`/`DOT_RGB` = `xopp_silver`), and the highlighter tip sizes
  (`DrawingSurfaceDefaults.HIGHLIGHTER_SIZES_PT`). Colours that have **no** desktop counterpart — the
  Material theme chrome, the radial-palette scrim, the tab-duplicate dots, the app-only red ruled
  margin — are deliberately left alone; `DesktopColorParityTest` pins the ones that do.
- **`CustomColorPickerDialog`** — the HSV/hex dialog behind the custom slot: a
  saturation/value square over a hue slider,  plus a two-way `#RRGGBB` hex field and a live
  preview. Always opaque; the parent persists the result. Its optional `palette` argument adds an
  **Existing colours** row — the app's own swatches, scrolled horizontally — so a new colour can
  start from one that already exists instead of from a hex the user would have to know; tapping a
  swatch loads it into the editor and nothing is committed until **Set**.

The chrome's own colour roles — the bars' `rememberChromeColor()` and the implement colour
`rememberToolbarColor()` — are recorded where the rest of the pinned stack is: [`stack.md`](stack.md).
