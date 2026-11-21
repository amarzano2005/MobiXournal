# The Settings shell: its two shapes, its search, and the prefs behind them

> Read when you add, move or remove a section of the tablet's Settings, or change how it is searched.
> For the task: [Where a setting lives](guide-settings-and-shortcuts.md), [The words this guide uses](guide-glossary.md).

How the phone's Settings screen is laid out and searched — the areas, the sidebar, and the settings
objects the sections read and write. What the window's own Settings surface looks like is
[desktop-settings.md](desktop-settings.md)'s, and the chrome the screen is reached from is
[chrome.md](chrome.md)'s.

## Settings: two shapes, one set of areas

Choosing Settings from the ☰ menu swaps in `SettingsScreen`, which has **two responsive shapes** over
one shared section model. `SettingsChrome` paints the screen container with
`MaterialTheme.colorScheme.background` (`DeskDark` in dark mode, `DeskLight` in light mode) so every
`SettingsCard` (`ModernTheme.settingsPanel`) separates cleanly from the backdrop in both themes.

On screens at least `SETTINGS_TWO_PANE_MIN_WIDTH` (600dp) wide (`TwoPaneSettings`), `SettingsSidebar`
(`SettingsSidebar.kt`) stays permanently visible on the left with a rounded-pill `SettingsSearchField`
and all fourteen `SettingsSection`s listed directly under their five uppercase `SettingsArea` eyebrows
(**Input**, **Drawing**, **Interface**, **App & data**, **About**) alongside trailing live value readouts
(`sectionLiveValue` in `SettingsOverview.kt`). Selecting any row updates the right detail pane
(`TwoPaneDetailPane`) immediately via `AnimatedContent` (`liquidGlassFadeTransform`), without an
intermediate area-list screen or top quick-controls bar. Narrower screens (`StackedSettings`) drop the
sidebar for a flat 1-tap grouped index (`GroupedSettingsIndex`) with compact single-line rows (`SectionRow`
with `summary = null`), trailing live readouts, and instant search (`SearchResultRows`); tapping any row
opens its section directly. System back mirrors the title-bar arrow (section → search → exit on phones;
clear search → exit on tablets).

## Rows light the card's width

A row's press, selection or drag highlight is **the card's width** — the same rule the window's rows keep
(`desktop-settings.md`). A card pads its content by `SettingsRowInset` (16 dp) and a Compose ripple can
only paint inside its own node, so a row laid out in that padded content lit a smaller copy of the card's
shape: a box inside a box. `SettingsCard` (`SettingsLayout.kt`) pads by that inset *and* publishes it as
`LocalSettingsCardInset`; a row (`SwitchRow`, `<T> OptionGroup`, `SectionRow`, `RailItemRow`, `LinkRow`)
opens with `fullBleedRow(reach)`, which lays it out into the padding while keeping its content inset, and
carries no `clip` of its own — the card's rounded clip is the row's shape. The card pads **horizontally
only**: a vertical inset left the first and last rows' highlight a rectangle that stopped short of the
card's rounded ends, so every row now reaches the top and bottom edges and the clip supplies the corners.
The air **between two cells** is the rows' own (`SettingsCellAir`): a divider carries no margin, because a
ripple paints inside its own node, and a divider that kept its spacing left an unlit strip between the
light and the hairline. Outside a card the local is zero, so the same rows inside dialogs (the shape
editor's switches) do not move. `SettingsRowFullBleedTest` pins the arithmetic and the wiring, so a row
added later cannot go back to a half-lit box.

## A choice is a select

A **choice** — one answer out of a short list — is drawn as a **select** (`<T> OptionGroup`): the answer in
hand at the row's end in the accent, a chevron after it, and the rest of the list in a `ToolbarMenu` under
the row with the answer in hand checked. The whole row is the hit area, subtitle included, as a switch
row's is. It used to be one radio row per answer, and that column was the wrong instrument on a card: the
answers nobody is using took a dot and a line each, the reader scanned four of them to find the one already
set, and the answer in hand had no line of its own to be read on. One line per choice is also the grammar
the window's Settings keeps (`desktop-settings.md`), so a subject both shells carry is asked the same way on
both. The list opens **under its row**, its end edge on the row's — under the answer and its chevron, the thing it
belongs to, as the window's select opens — and kept inside the window: it sits above the
row only when the row is too near the bottom for the list to fit under it, and its height is capped so a long
list scrolls rather than running off the screen — never a panel parked somewhere else (`SettingsSelectPositionTest`).
The list is **as wide as its longest answer** — never wider than the row — and wears the settings panel's fill
made opaque: over a card the floating glass let the rows behind it read through, which is a stain rather than a
menu, and a list stretched to the card's width was a sheet across four words. `SettingsChoiceTest` holds
the component to it — the row opens a menu, the list takes the row's width, and no radio row is left in the
file.

## Settings search

**Search** runs over the sections from both shapes (`SettingsSearch.kt`, pure): a query matches a
section's title, its summary, or its `keywords` aliases — the everyday words for the controls a user
might type instead of the app's label ("momentum", "theme", "cache", "left-handed"). The aliases
are what makes search *find* rather than filter titles, so `SettingsSearchTest` pins a handful of those
queries to their sections and requires every section to carry aliases and answer to its own title. A
result names the area it lives in (`areaOf`), so a hit found by an alias still says where the setting
belongs; opening one goes straight to the section. `SettingsAreaTest` pins the structure the whole
screen rests on: the areas cover every section exactly once (an unlisted section would be unreachable)
and no area owns a single child (a one-child area is a tap that explains nothing). Section bodies live
in their own `*Section.kt` files (`StylusSection.kt`, `EditorSection.kt`, …, `ShortcutsSection.kt`). The **Ring** section (`RingSection.kt`, under Drawing) draws the pen-tip ring and edits its faces: a tap picks a face and a select under the drawing says what it holds — a colour, a width or **any tool the rail has**, and the ring is stored as one line under `ring.slots` (`RadialHud.encodeSlots`) — absent means the factory ring, which is what *Reset to defaults* writes. The **Shortcuts** section (`ShortcutsSection.kt`, backed by pure helpers in `ShortcutCatalog.kt`) replaces separate subpages with a single searchable, categorized view: a pill `SettingsSearchField`, horizontally scrollable `ShortcutFilterTab` pills (**All**, **Bound**, **Toggles**, **Tools**, **Colors**), a duplicate-key conflict banner (`duplicateShortcutKeys`), toggles (`penEraserToggleKey`, `handToggleKey`, `selectToggleKey`, `fullPageToggleKey`, `ringShortcutKey`), `ShortcutToolFamily` cards (`Ink`, `Selection & navigation`, `Insert & media`, `Shapes & diagrams`, plus collapsible `Circuits & logic` that auto-expands during search/Bound filtering and `boundRetiredTools` for non-empty legacy keys), palette rows (`colourShortcutRows`), and a two-step inline reset (`resetShortcutsToDefaults`). Tool and colour maps (`toolShortcutKeys`, `colorShortcutKeys`) persist through `encodeToolShortcuts` / `decodeToolShortcuts` and `encodeColorShortcuts` / `decodeColorShortcuts`, migrating legacy keys (`black_pen_key` etc.) once. `EditorKeyShortcuts.routeShortcutKey` resolves characters in fixed precedence — toggles first, then colour, then tool — and each full-bleed `ShortcutKeyRow` shows a `ShortcutKeyCap` badge (highlighted on conflict) that opens `KeyDetectionDialog` on tap. The factory pen palette (`PEN_COLORS` — desktop Xournal++'s default palette, hex for hex; see `PaletteColorsTest`) and `PEN_WIDTH_LABELS`
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

**The theme is a select in its own card (`AppearanceSection`).** Opening *Appearance* from the grouped index or tablet sidebar displays its `OptionGroup` inside `SettingsGroup("Theme")` (*System*, *Light*, *Dark*) while binding `SettingsSection.APPEARANCE.title` and `SettingsSection.APPEARANCE.summary` so the two shells stay paired (`SettingsCopyParityTest`).

**A change takes effect immediately** without prompting for a restart: picking a mode repaints the live window and updates the app's `-night` qualifier (`MobiXournalApp.applyNightMode`), using `mobix-core`'s shared `ThemeMode` enum.

**A backup carries the audio choices too.** `AppSettingsBackup` writes `audioFolderUri` and `saveStandaloneAudio` beside the rest, so another device keeps where recordings are copied and whether they are copied at all (`audio.md` owns what the two mean). **About is an area of its own, and one page.** Its `SettingsSection.ABOUT` keeps `inline = true` as the single child of `SettingsArea.ABOUT` (`SettingsAreaTest`) and renders directly as a custom layout: a **hero card** names the app, the build and the device and carries an explicit **Check for updates** button under the number it asks about, with a **compact GitHub button** (the brand's own octocat, `ChromeGlyph.GITHUB`) beside it opening the repository; then a pair of **door cards** opens the project's issue tracker — *Report a problem* the bug form, *Suggest an idea* the feature form, both files in `.github/ISSUE_TEMPLATE` addressed through `ProjectLinks` (`mobix-core`) — with **Copy app details** under them, because that button exists to be pasted into one of those two forms; and the last card is **Credits & License**, holding only the two disclosures (the credits, then the licence), so the legal text is the end of the page instead of the middle of a list of actions. A form rather than a blank issue box because it asks for the build and the platform in fields; the window carries the same three steps in its own grammar (`desktop-settings.md`).

## The rail's two fixed slots

**Pages** and **Layers** are not in the Toolbar section's list at all: they are the rail's navigation, so
they always stand at its foot under a hairline rule and the list offers neither a drag handle nor a
switch for them (`PINNED_RAIL_IDS`, `railSections`). A position that could be hidden or shuffled into
the pens lost the two doors a reader reaches for most, and the line is what tells the tools above from
the panels below — the section says so in one note instead of listing two dead rows. `RailItemsTest`
pins the cut, and the rail's adaptive pitch is told the rule's height, so the last slot still ends on
the rail's bottom edge.

## The vertical rhythm

Four numbers carry the screen's spacing, and their order is the rule:

- a **cell** pads above and below its text by `SettingsCellPad` plus `SettingsCellAir` — the air exists
  because a row's press has to reach the divider, and it is invisible until you count it twice;
- two **cards** stand `SettingsGroupGap` apart, which must stay *larger* than two cells together, or the
  groups run into each other while their own rows drift apart (the rhythm guard fails the build if it does);
- a **group header** sits `SettingsHeaderGap` above its card: it names the card, so it belongs to it;
- a block that is not a row — a slider's header, a note — takes `settingsBlockInset()` instead of its own
  guess, so its text lands on the rows' column.

- a **card** keeps `SettingsCellAir` off its own rounded edges, and a full-width **pill** keeps
  `SettingsActionPillAir` on each side — a border drawn on the text column reads as clipped by the card.
The side menu has its own step: pills are `SettingsSidebarRowHeight` tall, `SettingsSidebarRowGap` apart,
with `SettingsSidebarGapAfterField` between the search field and the first of them — a field is not a pill,
and the list must not read as a continuation of it.

