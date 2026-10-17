# The chrome: editor screen, rail, top bar, settings

> Read when you change the editor's layout, the rail or the toolbars, a pop-up's material, or the settings screen.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Chrome (`ui/`): the editor screen

**Chrome (`ui/`).** `EditorScreen` is the one editor screen: a top bar (`EditorTopBar`) with undo/redo
icon buttons, a compact quick **Export PDF** button and a one-tap **Save** button after it (Save
outermost, i.e. under the thumb), and a
**☰ overflow menu** (`DropdownMenu`) holding Open, Import PDF, Export PDF, Save, Save As,
Pen diagnostics, and Settings; a **Main Toolbar `SideToolbar`** with core drawing tool slots and pop-up panels; a secondary toolbar (`TopBarToolsRow`) for geometric figures and STEM tools embedded in the top bar. Both toolbars follow the modern design system floating glass capsule style.

The app chrome provides two configurable layout modes (`ToolbarLayout` in `AppSettings`):
- **Standard (`DEFAULT`)**: The Main Toolbar is docked along the vertical edge (Left or Right, per `ToolbarPosition`) as a vertical floating capsule for classic writing tools, paired with the Secondary Toolbar in the top bar containing all STEM tools.
- **Compact (`COMPACT`)**: A unified floating horizontal toolbar docked at the bottom center containing primary writing tools plus 4 compact 1-tap STEM slots (`[f(x)]`, `[Geometry]`, `[Table]`, `[Circuit]`); the secondary toolbar in the top bar is hidden, displaying only the document title.

The rail's edge is not a placement menu but the **handedness** answer: a right hand writes from the left,
so `ToolbarPosition.LEFT` is the default and `RIGHT` is its mirror — and those are the two values it has,
because the `TOP`/`BOTTOM` centre dockings were retired (2026-10-13: the header owns the top band and a
toolbar along the bottom is what Compact *means*, so neither could be drawn as anything but a second bar).
First launch asks it as **which hand writes**, each answer carrying a miniature of the editor with the rail
on the edge that hand implies, and the settings picker later offers the two edges under **Settings →
Interface → Toolbar**. A stored or imported `TOP`/`BOTTOM` ages into `LEFT` in the readers
(`AppSettings.load`, `AppSettingsBackup.fromNode`), so an install from that era still opens with a rail it
can place. The tour that asks it is owned by [`stylus.md`](stylus.md#first-launch-onboarding).

The chrome never sits **on** the page: the header (the top bar and, inside it, the row of document tabs),
the rail's lane and (in Compact) the bottom band each take a band of their own and the canvas takes what
is left, which is what makes the whole visible page writable — the layout is described under _The frame
around the page_, below.

`orderedTopBarItems` places a slot the saved order doesn't name (one added since that order was written) at its **factory
position** — just ahead of the next factory item the order does name — rather than appending it past
everything the user arranged, which is what would otherwise park a new button at the wrong end of an
existing install's row.
The element selection's action bar floats beside the selection itself (see [`selection.md`](selection.md#selecting-objects-render)).

## The rail's Colour & size slot

The rail also carries the **Colour & size slot** (`ColorSizeRailSlot`, `ui/ColorSizeRailSlot.kt`) — the
one rail slot that is not a button opening a menu: its main surface *is* the control. It is a vertical
stack of `AppSettings.FAVORITE_COUNT` (3) favourite-colour dots, one tap away, with a **chevron** beside
them onto the full **Colour & size** pop-up (`ColorSizePopup`: palette, tip sizes, line style). That
chevron is where the *separate* Colour & size rail position went — the two slots were one errand, and
retiring `color` (`PANEL_RAIL_ITEMS` no longer names it, and a saved order naming it is dropped) gave
the rail a position back while the richer pop-up stayed one tap away. `favoriteToolFor` decides whose
terna the dots draw — the highlighter's while the highlighter
is live, the pen's otherwise (a non-inking tool such as the eraser offers the pen's rather than an empty
strip) — so the pen and the highlighter keep **separate** favourite colours, exactly as they already keep
separate colour fields, while the slot never holds two ternas. The slot measures exactly **one slot**
(`LocalRailSlotSize`), so it cannot cost the rail the tools it shows at a glance: the dots take 52% of
its width as a column of three cells, each `slot / 3` tall with the dot inset 2dp inside it — the cell
is the *touch target* while the feedback drawn on it is clipped to `DotCellShape`, a **true circle
centred in the cell** (not `CircleShape`, which would inscribe an oval in a cell wider than it is tall),
so a tap's ripple and a hovering stylus' state layer light up the dot rather than a grey box around it —
and a
centred **square** chevron button of 40% of the slot sits beside them across a 4dp gap — a square
button rather than a full-height strip, because a strip as tall as the slot and only as wide as a
glyph reads as empty space with an arrow in it and puts a target where the hand expects none. A tap goes through `pickFavoriteColor`, which writes the colour into
the **owning tool's** slot (`AppSettings.lastColor` for the pen, `highlighterColor` for the highlighter —
the same write the Colour & size pop-up makes) and applies it to the active canvas stroke directly without switching
the active tool away (so drawing tools like line, rectangle, or eraser keep their tool without reverting to the pen). A long-press opens the palette over that dot and rewrites it
(`assignFavorite`). `sanitizeFavorites` pads a short, long or corrupt pref to exactly three opaque,
distinct colours — the row is a fixed three, so "three" is a shape the slot depends on, not a
preference — and `AppSettingsBackup` carries both lists verbatim. The factory sets are
`DEFAULT_PEN_FAVORITES` = **black, red, green** and `DEFAULT_HIGHLIGHTER_FAVORITES` = **yellow, green,
blue**. Both ternas are edited side by side under **Settings → Drawing → Colors**
(`ColorsSection.FavoritesEditor`), where seeing them together *is* the point; the rail draws one at a
time.

## The rail never cuts a slot — and never shrinks one

**The rail never cuts a slot — and never shrinks one.** `ToolbarShell` (`ui/SideToolbar.kt`) measures
the height it is given (`BoxWithConstraints`) and hands the visible slot count to `railContentScale`
(`ui/RailSizing.kt`), a pure function: it counts the slots that fit **whole** at their own size (`m`
slots take `m·slot + (m−1)·spacing`) and spreads exactly that many over the space, so the slot after
them starts at the rail's bottom edge instead of being cut by it. The scale it returns is therefore
never below 1 — a rail button is never drawn smaller than the size it was designed at, which is what
keeps the rail usable at a glance, and what an all-slots-visible rail wastes most of its length on —
and only a few percent above it, capped at `RAIL_MAX_SCALE` (1.08) so the slot stays within the rail's
inner width. The leftover strip below the last whole slot is spent on slightly larger buttons rather
than on a sliver of the next one. Slots the height cannot hold are a **scroll away**, and a rail that
fits entirely is left at scale 1. The shell publishes the scale as the slot size
(`LocalRailSlotSize`, read by every slot — tool buttons, pop-up buttons, the one-tap colour slot; the
zoom slot's percentage label reads the factor itself via `LocalRailSlotScale`, since its content is
text and not a dp size) and applies it to the gaps but **not** to the column's padding, so the
whole-number fit stays exact.

The zoom slot is the rail's one slot whose *content grows with the value*: `41%` fits any slot,
`100%` and `381%` do not. Its label therefore **fits itself to the slot** (`fittedLabelSp`): the text
is measured against the slot's width minus a 7dp inset and the font comes down by that ratio, floored
at 62% of the base size — so a three-digit percentage is *smaller*, never cropped by the circle. The
percentage itself is formatted by one function (`zoomLabel`) shared with the canvas's zoom badge, so
the two can never disagree.

## Chrome styling: the Material 3 floating dock

Chrome styling is one design, with no switch: the Material 3 floating dock.
- The Main Toolbar fill is translucent (`rememberToolbarColor()` at 88% alpha) with a 1dp hairline at 25% alpha, and it opens with a **1-tap STEM section** (`StemRailSection`, `ui/StemRailSection.kt`): f(x) (opens the graph dialog), Geometry, Table, Circuit — one tap each, no sub-menus, grouped in an accent-tinted capsule between hairline dividers. Pinned by `StemRailSectionTest`.
- The Main Toolbar renders as a floating dock surface (`SideToolbarModernWidth` = 56dp) with rounded corners (`20.dp`), tonal elevation (`3.dp`), shadow elevation (`4.dp`), subtle border, and squircle tool buttons (`12.dp`) at the rail's own whole-slot sizing (44dp slots, grown by a few percent when that hides a sliver of the next one); the Secondary Toolbar in the top bar renders as an adaptive floating dock surface (`40.dp` height) enclosing the visible figure tools with rounded corners (`20.dp`), tonal and shadow elevation, dynamically adapting its width to the number of visible figures, and not drawn at all while none are visible — and every glyph in it, the guides slot and the indicator face included, is drawn at the rail's own size (`ToolIconSize`, half a rail slot), because these vectors scale their stroke with the icon and a smaller glyph there is *thinner ink* beside the rail rather than a lighter style (`ToolGlyphWeightTest`); the **active tool indicator** is the dock's own **end cap** (`ActiveToolIndicator`), grey running flush into the dock's rounded border, `TOP_BAR_DOCK_HEIGHT` (40dp) tall with `TOP_BAR_DOCK_CORNER` (`20.dp`), the dock's tonal and shadow elevation and hairline border, its face (`ToolIndicatorFace`) showing the selected tool, colour and stroke width with no thickness bar beside the size letter, tapping it opening the Colour & size pop-up, and its grey the `surfaceContainerHighest` role — a surface step, never the accent `primaryContainer` a picked tool button wears; the style it replaced — the indicator standing **outside** the dock as a button of its own (`StandaloneToolIndicator`) in the top bar's leading slot, the corner between the rail's column and the dock, sized so a left-docked rail's dock starts on the document tab strip's own line, and trailing the dock when the rail is docked right (`standaloneIndicatorSlot`, pinned by `IndicatorSlotTest`) — is kept behind the `INDICATOR_AS_DOCK_CAP` constant as a one-flip restore path. The cap is the style in force because the standalone pill's fixed width is exactly the rail's own column (`TOP_BAR_INDICATOR_WIDTH`), so it sits flush against the *screen's* left edge; as the cap the indicator starts clear of both the edge and the rail, and the run still reads rail → indicator → figures; the top bar uses `TOP_BAR_ROW_HEIGHT` (52dp) with a grouped undo/redo pill container (`18.dp`), a compact quick Export PDF button followed by the accent-tonal Save button (so Save sits outermost), and active document title chip; tabs use 38dp height with pill chips (`14.dp`); page counter and zoom badges use frosted rounded pills (`14.dp`); floating action bars use rounded capsules.
Each rail button owns its own `DropdownMenu`, so the pop-up
is anchored to that button (opening to the right of the rail) rather than filling the screen. Every
one of those menus — the rail's, the dock's, the views'/pages'/layers' panels, the ☰ overflow and
the settings drop-downs — is built by `ToolbarMenu` (`ToolbarPopup.kt`), the app's **one menu
material**: the frosted glass the bars paint with its see-through turned down
(`rememberFloatingSurfaceColor()`, the `floatingPanelAlpha` token at 0.88 — a floating surface is read
on, a bar is not), the same fill the action bars (`FloatingSurface`), the STEM editors
(`FloatingDialog`), the badges, the hand-rolled dialogs and the transfer overlay take, the same lit
hairline (`floatingRim`, one implementation for all of them), and the same corner (`panelCornerRadiusDp`, which `AppShapes.extraLarge` hands Material's
own `AlertDialog`s too, so a dialog and a menu cannot turn at two radii)
and `surfaceTint` pinned to it (M3 paints the
menu's 3dp `ContainerElevation` as a tint wash, which otherwise lands it a step off the bar) and the
dock's 20dp corner in place of M3's 4dp `Shapes.extraSmall`, since neither the fill nor the corner is
a parameter `DropdownMenu` exposes in 1.2.1 — the ambient theme is the only lever, and the override
stays inside the menu's own scope. On top of that material the menu wears a **lit rim**: a 1dp
hairline drawn after the panel in a vertical gradient of the surface's own ink (bright along the top
edge, faint along the bottom), so a pop-up reads as a panel lying above the bar it hangs from rather
than dissolving into the same grey. A raised shadow is deliberately not the cue — the pop-up's window
is no larger than the panel, so an elevated menu paints its blur *inside* its own top edge and clips
away at the sides (both measured on the emulator). `ToolbarMenuGuardTest` fails the build if any other
source file calls `DropdownMenu(`, which is what keeps a new pop-up from quietly bringing the paler,
squarer panel back. The
rail's head is **one slot per tool group** (`ToolGroups.kt`): `TOOL_GROUPS` partitions every
`EditorTool` into named groups (pen · highlighter · eraser · line · rectangle · shape · pan · select ·
insert · vspace · play). **Line** and **Rectangle** are one-tool groups of their own; every other
figure shares the **shape** group, faced by default with the ellipse (the circle). `ToolGroupButton`
renders each as a single button faced with that group's current tool — a tap activates that tool, and
tapping the slot **again** once it is the live tool opens a `DropdownMenu` over the group's members (a
long-press opens it too, but is no longer required). `ToolGroupPicker` (`ToolbarPopup.kt`, shared with
the modern top bar's compact slots) lays those members out as **rows of compact icon-and-label
buttons** rather than one full-width row each, with related tools sharing a row: a group declares its
grouping in `ToolGroup.pickerLayout` and `pickerRows()` resolves it against the members actually shown
(so the diode family, the two transistors and the two sources sit together, each inverted logic gate
beside its twin, and any member a layout doesn't name still wraps three to a row rather than going
missing; the user-ordered Shapes slot declares no grouping at all, so its order is preserved). The two
figures that ship in several geometric **kinds** rather than a group — the triangle and the trapezoid,
whose secondary-toolbar buttons carry a variant submenu — get the same row treatment from
`ToolVariantPicker` (`ToolbarPopup.kt`), which lays the kinds out through the pure `variantRows()`
two to a row: the live kind is tinted, and the kind that has a dialog behind it (the scalene, whose
angles are configurable) keeps its pencil so picking it and opening that dialog stays one gesture.
Every button is a radio item and the slot's live member is tinted in the primary container colour — the same
"this one is on" language as the rail — so the picker reads as one setting with several choices. A tool slot whose group has **more than one member** wears a small **chevron in its bottom-right
corner** (`BoxScope.MenuChevron`, `ToolbarPopup.kt`, drawn by `ToolGroupButton`), so a slot that can
be re-faced reads at a glance differently from one that just activates a single tool; the panel
pop-ups (`ToolbarPopupButton`) deliberately do **not** wear it — they each open a panel, not a
submenu of alternatives. The **insert** group carries one member — **Image**, the file/gallery
picker — while text authoring lives in its own sibling **text** group (Text, LaTeX), so the rail
keeps pictures and typed content in separate slots. A pick writes
`AppSettings.toolGroupSelections` (a `groupId → EditorTool` map, encoded as `group:TOOL` pairs in
one pref, so slots survive a restart) and activates the tool in the same gesture. `selected()` and
`decodeToolGroupSelections()` both drop non-member entries, so a stale pref degrades to the group's
first tool rather than facing a slot at a tool that has since moved. `startingTool()` resolves the
opening tool as `defaultTool`'s **group selection**, so the rail's face and the live tool agree on
launch. The **Figures** list in settings includes all geometric figures (`FIGURE_TOOLS` in `ToolGroups.kt`, including simple line and rectangle): `orderedShapeTools()` and
`visibleShapeTools()` read `AppSettings.shapeOrder` / `shapeHidden` (tool names, encoded like the rail
ids) and the **Figures** settings section edits them with the same drag/toggle list the Toolbar
section uses (`ReorderableRowList`), keeping visibility and order in sync with the secondary top bar (`visibleTopBarItems` filters items hidden in either `topBarHidden` or `shapeHidden`). The same section owns the **default figure size**
(`AppSettings.defaultShapeSlot`, an index into `penWidths`). That default exists because a figure is
drawn at its set width with no pressure while the pen thins with pressure, so with pressure on a
figure reads thicker than a light pen stroke — the slot is the knob that closes the gap without
leaving the desktop's width semantics. The line **style** (Solid/Dashed/Dash-dot/Dotted) lives
only in the Colour & size pop-up — the separate **Style** rail slot was
removed — so a dashed pen is one tap away as in the desktop's pen options. The
pop-up itself is deliberately compact: one **horizontally scrolling** row of swatches
(`ColorPaletteRows(compact = true)`, ending in the add-colour swatch that appends to `penColors` and
selects the new colour — the row scrolls so the menu's height never changes with the palette's size),
the three tip sizes as one bar of small boxes (`WidthSlotBar`) and line style as one row of chips
(`LineStyleChips`). **Which positions the rail shows, and in what order**, is data too
(`RailItems.kt`):
`RAIL_ITEMS` names every position — every tool group plus the Colour/Shape-recognition/
Guides/Layers/Zoom/Background/Pages/Audio panels — in an explicit factory order
(`FACTORY_RAIL_ORDER`): the drawing tools reached for most first (pen, highlighter, colour & size,
eraser), then the rest of the tools and panels, with the rarer controls (shape recognition,
guides) trailing the rail. `SideToolbar` renders `visibleRailItems(railOrder, railHidden)`,
dispatching each id to its group button or panel. The Toolbar settings section edits those two prefs
(`AppSettings.railOrder`, a comma-separated id list, and `railHidden`, the same encoding for the
switched-off ids); a row is reordered by **long-press drag** (`detectDragGesturesAfterLongPress`),
which steps `moveRailItem()` one place each time the finger crosses a row height, so the list
reshuffles live. The in-flight order is held in the section's own `draggedOrder` state because
several steps can land inside one frame, before `onChange` has recomposed `settings`. `orderedRailItems()` **appends** any id the saved order omits in factory order, so
a position added in a later release still appears for an existing install, and `decodeRailIds()`
drops ids that no longer exist. The tools are UI-level
`EditorTool`s (Hand is view-only pan and Text/Image/LaTeX are placement modes, none a document tool,
so `EditorScreen.applyTool` maps them to the surface's `handMode` / `placeKind` and maps the three
drawing tools to the document `Tool`). The **Pages** pop-up is a page navigator: `Page N / M` with
◀ / ▶ to jump to the previous/next page (`goToPage` scrolls the stack; the surface reports the page
under the viewport centre via `onCurrentPageChanged`), plus Add / Remove page, a **Contents…** row (the
backing PDF's outline — see _PDF Contents_ above) and a **Bookmarks** section (see _Page bookmarks_). A **right-edge scroll
thumb** (`ScrollThumb.kt`, overlaid on the canvas in a `Box` sibling of the `AndroidView`) gives
PDF-style fast paging: the surface reports its vertical scroll geometry via
`onScrollChanged(scrollY, totalHeightPx, viewportPx)` (all content px, already zoom-scaled), the thumb
sizes/positions itself from that ratio and **dragging it** drives `DrawingSurfaceView.scrollToY`. The
touch target is only the thumb *band* (a small region tracking the scroll position), not the full
right edge, so a stylus can still draw over the page's right margin everywhere but the thumb; the
thumb is a **2dp neutral-grey capsule** (`rememberScrollThumbColor`, the theme's secondary ink
rather than the accent blue) sitting **flush against the pane's edge**, faint when idle, brightening
after a scroll and brightest while dragged, with a page-number bubble beside it. Nothing is drawn
*in* the bar: there is no grip bulge and no inset, so it reads as a hairline at the sheet's edge
rather than as a control lying on the page, and it never covers a line of the ruling. It is a pure navigation affordance — no `.xopp` state, so nothing
round-trips.

## The frame around the page: the chrome's bands, and the canvas's rest

**The chrome is laid out beside the page, never on it.** The header band, the rail's lane and (in
Compact) the bottom band are rows of the layout, and the canvas — an `AndroidView`-hosted
`DrawingSurfaceView` — is handed what is left:

- `EditorScreen` is a `Column`: the header first, `EditorBody` in the weight that remains.
- `EditorBody` is a `Row` in Standard — the rail's lane (exactly `SideToolbarModernTotalWidth`, the edge
  `AppSettings.toolbarPosition` names) and the canvas taking the weight — or a `Column` in Compact: the
  canvas, then the horizontal dock's band above the navigation-bar inset.
- the **document tabs** are the header's own second row, inside the bar's surface (`EditorTopBar`), so no
  row of the chrome ends on an edge the row beside it does not share. Their chips are inset by the rail's
  lane plus their 8dp lead-in (`TAB_STRIP_LEAD_IN`), which keeps them over the canvas and on the figures'
  dock's starting line, and the lane itself begins below the whole header — the tab row is what
  `RailSizing.kt`'s narrower gap exists to pay for. In **split view** that row is cut the way the canvas
  is (`SplitTabRow`): one strip per pane, cut by the canvas's own geometry — the same
  `splitPaneSpans` the divider lays itself out with, and the same 22dp handle gap
  (`SPLIT_HANDLE_WIDTH`, the constant `SplitLayout` draws; its semantics are in
  [data-path.md](data-path.md)) — so each half's chips, numbered pane
  badge and "+" sit over that half for every divider position — not only at 50/50. The focused pane's
  badge is accent-filled and the other is outlined; touching a lane focuses its pane, and its "+" opens
  the new document there. Compact has one row and no room for two lanes, so it keeps the focused pane's
  strip in the title slot, badge included.

**The split's divider is furniture, not a bar.** It is a hairline rule (`RULE_WIDTH`) that **fades into
the sheet at both ends** — a vertical gradient, transparent → rule → transparent — with a **floating
capsule grip** centred on it: a frosted fill (`surfaceContainerHighest`), a hairline border and an
accent-blue core, lifted by a soft shadow. Nothing fills the 22dp grab lane, so the two pages still read
as two sheets with the desk's own colour between them; while the grip is in hand the capsule widens,
brightens, its border picks up the accent and a barely-there accent wash crosses the lane, and it settles
back to a quiet bead on release. That replaces the earlier filled band with a heavy rounded grip, and
keeps the same one-tap-off-the-page rule as the rest of the chrome — the divider is transient furniture
*over* the seam between two pages, and it never covers a pixel inside either sheet.

Whatever the chrome occupies the canvas does not, and that is the point. A translucent bar floating *on*
the page hides the ink under it and swallows the stylus there, and the strip it covers is the top of the
sheet — where a note taker's hand reaches first. Every pixel of the page the user can see is a pixel
they can write on.

**The rule this encodes — and its one carve-out — is owned by**
[ui-modernization.md](ui-modernization.md#core-design-principles): *bands never lie on the page; transient
surfaces may*. A radial HUD, an anchored popover, a dialog or a selection bar comes and goes with the
gesture it belongs to; a band is always there, so it never costs the sheet a pixel.

The **desk** is what the bands sit on: `EditorScreen`'s `Scaffold` takes `rememberChromeColor()` (the
scheme's `background`, the same colour the canvas paints around a page) instead of transparency, so the
header, the rail's lane and the bottom band read as one surface with the page lying on it rather than as
holes onto the window behind.

**The canvas is the screen.** The document runs edge to edge under both system bars: `MainActivity`
takes the window out of the system's insets (`setDecorFitsSystemWindows(false)` + `enableEdgeToEdge`)
and every system-bar colour it sets — and that `XoppTheme` sets — is **transparent**, so the only things
drawn over the page's top and bottom edges are the bars' own contents (the clock, the gesture handle).
In Standard there is no bottom band, so the sheet still ends at the glass of the gesture bar.
`XoppTheme`'s `SideEffect` still runs on every theme change, but it now only *chooses the icon
contrast* (`isAppearanceLightStatusBars` / `…NavigationBars`, from the chrome colour's luminance) and
leaves the colours alone. That split is the whole rule: **the app has no colour of its own in the
system bars, only icons.**

This is the one place the two halves of the chrome could silently disagree, and it did: for a while
`XoppTheme` painted both bars with `colorScheme.background` while `MainActivity` set them transparent,
and the later of the two won. An opaque `navigationBarColor` on a full-bleed canvas is not a tint over
the page — the framework draws the bar's own background *above* the app's window (the emulator measures
it as a hard-edged band of `#FAF8FF` in the light theme, `#000000` in the dark one) — so the page
appeared to stop 64px short of the screen's bottom edge and no amount of Compose drawing inside the
action could reach it. The band tracked the *app's* colour rather than the launcher's, which is what
identified it; it is nobody else's surface. With both bars transparent the page reaches the bottom edge
and the gesture handle floats on the paper.

**One header, one slab — and no line through it.** The bar and the row that carries the tabs are the two
rows of *one* surface, and nothing is drawn along the header's edges or between its rows: the slab's own
colour (the header glass) and its `4dp` shadow are the whole of its edge, and its two rows are told apart
by their content — the tools above, the chips below. It used to wear a `BorderStroke` rim **and** a hairline
inside itself, between the rows (`Modifier.topHairline`), as a tab row under a top app bar is drawn; on the
true-black dark scheme both read as grey rules across the whole window — the rim closing the header at the
top *and* the bottom of the screen, the hairline cutting the slab in two — so what this page calls one
header was banded by three grey lines. Both were removed on request (2026-10-13): the header's closing edge
is its shadow. Nested surfaces with a rim each read as two bars with a seam across the middle, and that is
measured, not theorised: with the tabs on a slab of their own, the header's bottom edge sat at 76dp over
the rail's lane and at 114dp over the page, a 38dp step with the chips starting to one side of it. The
rows' heights are `TOP_BAR_ROW_HEIGHT` (52dp,
`ui/TopBarIndicator.kt`) for the tools — padded by `statusBarsPadding()`, a device value, deliberately not
part of the constant — and `TAB_STRIP_HEIGHT` (`ui/TabStrip.kt`, 38dp) for the tabs; the lane therefore
starts below the header's *whole* height, not below the bar's row of it. In Compact the tabs ride in the
bar's **title** slot instead — the same slab, one row, because the dock holds the tools there.

## A saved toolbar order is not a stale one

**A saved toolbar order is not a stale one.** The Secondary Toolbar's order is a pref
(`AppSettings.topBarOrder`) whose *default* is `DEFAULT_TOP_BAR_ORDER`, and `orderedTopBarItems()`
puts any slot the saved order doesn't name at its **factory position** rather than at the end — so a
position added in a later release reaches an existing install. The other half of that problem is a
slot that *moved*: the whole settings object is written on any save, so an install whose row was
never touched still carries a full copy of the factory list **as it was then**, and a saved order
deliberately wins over the factory's. `migrateTopBarOrder()` is the one-shot repair for exactly
that: an order that is still an old factory list verbatim (`LEGACY_TOP_BAR_ORDERS`) is refreshed to
the current one once, behind the `top_bar_order_migrated_v2` flag, while a hand-arranged row — which
always differs from the old default — is never rewritten.

## A document with no pages

**The chrome on an infinite canvas.** A chunked `.mobix` has tiles, not pages ([infinite-canvas.md](infinite-canvas.md)
owns the mode), and every surface here that used to count pages had to answer for it. The rule is the one
the epic set: **no surface may claim a page that does not exist** — and, just as important, none may offer a
control whose effect is invisible, or one that would rewrite the file's own chunk grid.

One field carries the whole answer: `PaneState.board`, fed by `DrawingSurfaceView.onBoardReading` with a
`BoardReading` — the **tile** the viewport is centred on, as its coordinate, and how many tiles the document
holds. It is **null exactly when the canvas is not a board**, so "is this a board" and "which tile is in
view" cannot disagree, and the field flips back to null the moment a page list takes the adopted document's
place. Per surface:

- **The position badge** (`ui/PageCounter.kt`) reads the tile's coordinate (`tileLabel`, e.g. `1, -1`) instead
  of "page X of Y". It is shown whether or not the cell has ink: on an infinite canvas, looking at an empty
  cell is how you find the next one to write in. The **zoom badge** above it is untouched — zoom is real on a
  board. `pageLabel` and `tileLabel` are pure functions of the numbers, pinned by `PageCounterTest`.
- **The right-edge scroll thumb** (`ui/ScrollThumb.kt`) is not drawn, and neither is its page bubble: a board's
  content is unbounded in every direction, so a thumb fraction of "how far down" is a number about nothing.
  The canvas reports the viewport as its whole extent (`DrawingSurfaceView.reportScroll`), which is the same
  report a document shorter than the screen makes, so the thumb hides itself and the chrome needs no flag.
- **The Pages menu** (`ui/ToolbarPagesPopup.kt`) becomes a **tiles** reading: which tile you are looking at,
  and how many there are — plus the one row about the *document* rather than its parts, **Page format…**,
  which names the geometry a board is in and is the way back to pages. Every other row addresses a page and
  a board has none: adding or removing one would rewrite the chunk grid the file names its parts by, so
  those rows are gone rather than disabled. The **pages-per-row grid and its edit mode** are inert on a
  board for the same reason (the board pass replaces the page stack, so `columns` changes nothing on screen)
  and are not offered.
- **Bookmarks** are not offered on a board. A bookmark is a page flag drawn on the page it belongs to, and the
  board pass draws no page outline or flag; a bookmark you could set and never see would be the same lie in
  the other direction. Bookmarks for a board's tiles are left in the store untouched — nothing is lost, it is
  simply not shown yet.
- **Find still works**, and a hit on a board is a **tile**: results carry the document part index they were
  found in, the highlight is drawn in that tile's own box and the jump scrolls to it (`chromeBoxFor` is the one
  seam between a part's box on a board and its box in a page stack). Hits are counted and cycled exactly as on
  a page list.
- **Split view still mirrors a board.** A mirrored pane is a second *view* of the same document, so it is
  handed the board's grid along with the document (`MirrorSync.propagate` → `applyMirroredDocument`): a chunk
  created in one pane is drawn — and saved — in the same cell by both. Without that the second pane would keep
  the grid it opened with, which for a cell created since is a wrong place rather than a missing one.
- **The tools the board cannot carry out say so.** The page-shaped gestures — the rubber band and its
  clipboard, the background-region copy, PDF text selection, the vertical-space drag — are refused on the
  canvas with one message (`DrawingSurfaceView.onBoardBlocked`, "Infinite canvases are drawing only for
  now") instead of half-working on a tile grid; the rail keeps offering their tools, and the refusal is what
  the user sees. Drawing, the eraser, the placement tools and undo/redo work, in the tile's own coordinates.

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
lives beside its pop-up (`ToolbarColorPopup.kt` / `ToolbarSizePopup.kt`); the **live** palette is `AppSettings.penColors`, seeded with `PEN_COLORS` and edited under **Settings → Drawing → Colors**
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
