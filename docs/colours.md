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

## Semantic Theme Tokens & Chrome Roles (`Color.kt`, `Theme.kt`, `ChromeColors.kt`)

**The rule the whole palette answers to: black, grey, and one blue.** Nothing in the chrome is a
second hue, a pastel, or a tint of the accent — *especially* not the violet-tinted neutrals Material 3
fills in for any role a scheme does not name. See _The violet trap_ below for why that needs saying.
- **`DeskDark` (`#000000`)** & **`DeskLight` (`#F8F9FA`)**: True OLED black canvas backdrop in dark
  mode and clean warm paper neutral in light mode (`rememberChromeColor()`). Both the canvas surround
  and the empty desk behind the toolbars read this token, ensuring absolute chromatic consistency
  between the main toolbar rail and secondary toolbar top bar without mismatched tones.
- **Glass surfaces — the bars *and* the pop-ups**: one filler, `rememberGlassSurfaceColor()`, wraps
  `ModernTheme.glassSurface(isDark)` (`glassDark` `rgba(22, 22, 24, 0.85)` / `glassLight`
  `rgba(255, 255, 255, 0.88)`) and every chrome **bar** reads **it** rather than the token: the rail
  capsule, the header slab, the figures' dock, the tab chips. Hairlines are
  `ModernTheme.hairlineBorder(isDark)` throughout.
- **The pop-up panel — the same colour, less see-through**: `ToolbarMenu`, the app's one menu material,
  takes `rememberMenuSurfaceColor()` = `ModernTheme.menuPanelSurface(isDark)`, i.e. the bar's glass
  **RGB untouched** at the `menuPanelAlpha` token (**0.93**, `ModernTheme.menuPanelAlpha`). So a menu
  is the bar's own colour by construction rather than by two call sites agreeing, and it is *less*
  transparent on purpose: a bar carries one glyph and a panel carries rows of text over the page. The
  floor is measured — at the bars' 0.85 the dark theme's secondary ink came out at 4.41:1 over a white
  sheet, under the 4.5:1 WCAG AA body-text floor — and `ColorContractGuardTest` recomputes both ink
  roles over a white sheet and over black ink, in both themes.
- **`NeutralContainerDark` (`#252528`)** & **`NeutralContainerLight` (`#EAEDF0`)**: the neutral grey of
  `surfaceContainer` — the *raised panels* (dialog and `PlotGraphDialogChrome` chrome, section plates),
  one per theme. Neither theme leaves a container role to Material 3 any more: both schemes are built
  by `pinnedLightChrome()` / `pinnedDarkChrome()` (`Theme.kt`), which name **every** container, outline
  and inverse role, and the dynamic schemes pass through the same pin.
- **`ActiveSlotDark` (`#38383E`)** & **`ActiveSlotLight` (`#DEE3E7`)**: neutral steel for
  `primaryContainer`/`secondaryContainer` — active tool selection pills across the side rail and the top
  bar's `ActiveToolIndicator` cap, and selected rows/cells (pick grids, the settings side menu),
  avoiding jarring neon or periwinkle fills.
- **Disabled dynamic colour tinting**: `dynamicColor` defaults to `false` in `AppSettings` and
  `XoppTheme`, and both `dynamicLightColorScheme`/`dynamicDarkColorScheme` are run through the same pin
  as the static schemes — so a wallpaper palette can only ever reach the error reds. (The light
dynamic scheme used to re-pin primary and secondary *only*, which was the second way a lavender
container could reach a light-theme screen.)
- **Unified toolbar band surfaces**: Both the top bar (`EditorTopBar`) and the side rail lane (`RailLane`) wrap in an identical full-span `Surface(color = glassColor, shadowElevation = 4.dp)`,
  ensuring that both horizontal and vertical chrome bands share the exact same frosted glass surface and shadow elevation without naked black cutouts.
- **`TextLightOnDark` (`#EDEDED`)** & **`TextDarkOnLight` (`#1B212F`)**: High-legibility neutral text.
- **`TextSecondaryOnDark` (`#A0A0A0`)** & **`TextSecondaryOnLight` (`#5E6266`)**: Secondary caption text —
  both **neutral** greys. The light one was a blue-grey (`#596579`), which put a colour cast on every
  caption, pop-up heading and hint in the light theme.
- **`BlueDark` (`#60A5FA`)** & **`Blue` (`#2563EB`)**: Modern crisp royal blue primary and secondary accents, replacing legacy purple across active tools, tabs, selection indicators, and chrome icons. The luminous dark accent takes true black ink (`OnAccentDark`) rather than Material 3's baseline `#381E72`.
- **`Amber` (`#FFFFB300`)**: Canvas guide overlay accent.

### The violet trap

Material 3's baseline neutrals are **violet-tinted**: `surfaceContainer` `#F3EDF7`, `surfaceVariant`
`#E7E0EC`, `primaryContainer` `#EADDFF`, `secondaryContainer` `#E8DEF8`, `outline` `#79747E`. A scheme
that names a handful of roles and inherits the rest therefore paints lavender wherever it did not look
— a light-theme pop-up panel, a picked cell in a picker submenu, a selected settings row — which reads
as *blue-grey* on screen beside a genuinely grey bar.

So the light scheme names its whole chrome ramp (`NeutralContainerLight` `#EAEDF0`, its `Low`/`High`
`#F4F6F8`/`#E3E7EB`, `Highest` `#DCE1E6`, `OutlineLight` `#767B7F`, `OutlineVariantLight` `#C4C9CD`) and
the contract is enforced by **sweep**, not by spot-check: `ColorContractGuardTest` walks every
container/outline role of both schemes and measures each one's **green deficit** — in a violet neutral
the green channel sits far below both neighbours (`#EADDFF` is 34 away) while the shipped charcoal ramp
never leans past 6 (`#38383E`). The guard's own self-test pins the metric against both lists, so the
threshold cannot be quietly loosened into a test that passes on lavender.

The chrome's own colour roles — the bars' `rememberChromeColor()`, the one glass filler
`rememberGlassSurfaceColor()`, and the panels' `rememberToolbarColor()` — are recorded where the rest of
the pinned stack is: [`stack.md`](stack.md).
