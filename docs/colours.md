# Colour: the shared palette and the chrome's colour roles

> Read when you change a colour, the picker, the palette state, or a theme role.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Colour palette (`ColorPalette.kt`, `ColorPicker.kt`)

**The values and the rules live in `mobix-core`, not in the picker.** `design/PenPalette.kt` is the one
home of the factory eight (desktop Xournal++'s hexes, in its published order), the 24-swatch ceiling
(`MAX_COLORS`), the three width slots (0.85 / 1.5 / 2.6 pt as `DEFAULT_WIDTHS`, labelled `S`/`M`/`L`)
and the 0.5–15 pt range, plus the pure rules every shell calls: `opaque`, `add` (opaque,
de-duplicated, capped), `sanitised` (the same, falling back to the factory list when empty),
`clampWidth` and `widthSlot`. `PEN_COLORS`, the `XOPP_*` names, `PEN_WIDTH_LABELS`,
`AppSettings.MAX_PEN_COLORS` / `DEFAULT_PEN_WIDTHS` / `sanitized()` and `ColorPaletteState.addColor`
read it instead of spelling their own copies, because the desktop window needs the same eight hexes and
the same three widths — what a palette *is* cannot be decided twice. What stays in `ui/` is the UI: the
swatch rows, the custom-slot affordances and the HSV/hex dialog.

**One ink per drawing tool, on both shells.** A palette is what the user has; the colour and width a tool
writes with belong to **that tool**. The tablet keeps `EditorUiState.color` / `highlighterColor`
(`penWidth` / `highlighterWidth`) and a terna of favourites each (`AppSettings.penFavorites` /
`highlighterFavorites`), so a yellow highlighter never repaints the pencil and `1`–`3` take the terna of the
tool in hand; the window mirrors it with `DesktopInks` and `DesktopFavourites`, keyed by the picked slot.
The custom slot stays a property of the palette (`AppSettings.customColor`, `palette.custom`), because
redefining it redefines it for both tools. `DesktopInkSplitTest` pins the pair.

**On the desktop** the palette is the same data, kept in the window's own settings file
(`%APPDATA%\MobiXournal\desktop.properties`): `palette.colours` as a comma-separated list of eight-digit
ARGB hex, `ink.colour` / `ink.width` and `ink.highlighterColour` / `ink.highlighterWidth` for what the
strips show per tool, and `interface.colourFavourites` / `interface.highlighterFavourites` for the two
ternas. The strip tails carry a colour slot
and a size slot, opening the palette row and the `S`/`M`/`L` widths as floating glass flyouts. Reading
the file runs the list through `PenPalette.sanitised`, so an edited file cannot put a transparent colour,
a duplicate or an empty palette in front of the picker. The row ends in the editable custom slot and the
add-colour swatch; both open the window's own panel — a saturation/value square, a hue slider and a
two-way `#RRGGBB` field with a live preview (`ColourPickerPanel.kt`, rules in `ColourPickerModel.kt`),
which commits the custom slot or appends through `PenPalette.add` and picks the colour. It is the same
contract as the tablet's dialog (always opaque, a strict hex field, an invalid entry that keeps the last
valid colour and says so) built in the desktop's glass, because `CustomColorPickerDialog` lives in the
app's `ui/` and reads `AppSettings`.

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
  restyle the selection, colour the text). `compact` (the toolbar's Colour & size pop-up and the
  selection's recolour menu) lays the swatches in a **fixed-width grid**: every colour is on screen
  without scrolling a row, and the menu keeps one width however many colours the palette holds — an
  extra swatch costs a second line rather than a wider menu — and `onAdd` appends the trailing
  add-colour swatch.
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
- **The floating material — the same colour, less see-through**: everything that floats **over** the
  page reads `rememberFloatingSurfaceColor()` = `ModernTheme.floatingSurface(isDark)`, i.e. the bar's
  glass **RGB untouched** at the `floatingPanelAlpha` token (**0.88**, `ModernTheme.floatingPanelAlpha`),
  which is the most see-through that token can be while both ink roles stay above the WCAG floor.
  *Floating* is one family by design: the pop-ups and submenus (`ToolbarMenu`), the action bars that come
  up with a selection and with the table/PDF-text/region tools (`EditorActionBars`), the STEM
  editors' chrome, the page/zoom/scroll badges, the hand-rolled dialogs (`OnboardingDialog`,
  `KeyDetectionDialog`, the search-indexing card) and the transfer overlay. So one surface can never be a
  different material from the bar it appears over — it used to be a container role of its own, which is
  what left a lavender menu hanging from a grey bar.
- **One floating edge, one floating corner (`panelCornerRadiusDp`, 20dp)**: a menu, an action bar and a
  **dialog** turn at the docks' radius *and* wear the same lit hairline
  (`theme/FloatingGlass.kt` — `floatingRim`), which is what tells a translucent panel apart from the
  desk behind it. The action bars draw it through `FloatingSurface` and the STEM editors through
  `FloatingDialog`; Material's `AlertDialog` reads `Shapes.extraLarge` (`DialogTokens.ContainerShape` =
  `CornerExtraLarge` in material3 1.2.1), which `Theme.kt`'s `AppShapes` re-cuts once, so all
  twenty-five dialogs turn at that radius without one call site passing a shape. A dialog's container is
  `colorScheme.surface` (the same white/charcoal the glass is mixed from); a modal that does not go
  through `FloatingDialog` stays **opaque**, since it sits on its own scrim where 0.88 and 1.0 of one
  colour are nearly the same picture.
- **The measured legibility floor**: the floating fill is the *most* see-through the ink allows, and the
  number is measured rather than chosen — a bar carries one glyph, a floating surface carries rows of
  text over the page, and at the bars' 0.85 the dark theme's secondary ink came out at 4.41:1 over a
  white sheet, under the 4.5:1 WCAG AA body-text floor. The token is 0.88: **4.90:1** for the dark
  theme's secondary ink over a white sheet and **4.68:1** for the light theme's over black ink.
  `ColorContractGuardTest` recomputes both ink roles over a white sheet and over black ink, in both
  themes.
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
- **The frame is one material**: `EditorTopBar` and the side rail lane (`RailLane`) both wear the band's glass — the lane composited over the chrome colour, so its pixels are the header's own — and together they are the two arms of one frame around the canvas. The canvas keeps its own background inside that frame: a deep `#000000` on the dark scheme and the greyer desk on the light one, which is why the lane must not borrow it (it would turn the frame's leg black in one theme and grey in the other). Neither band wears an elevation, and their shadow is **one** shadow: the header's closing edge is inset where the lane begins and the lane fades the same edge down its canvas side, so the corner has no seam — the rule and its strength are `interface-contract.md`'s.
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
`rememberGlassSurfaceColor()`, the floating filler `rememberFloatingSurfaceColor()`, and the recessed
plates' `rememberToolbarColor()` — are recorded where the rest of the pinned stack is:
[`stack.md`](stack.md).
