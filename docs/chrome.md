# The chrome: editor screen, rail and dock

> Read when you change the editor's layout, the rail or the toolbars, or a pop-up's material.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Chrome (`ui/`): the editor screen

**Chrome (`ui/`).** `EditorScreen` is the one editor screen: a top bar (`EditorTopBar`) with undo/redo
icon buttons, a compact quick **Export PDF** button and a one-tap **Save** button after it (Save
outermost, i.e. under the thumb), and a
**☰ overflow menu** (`DropdownMenu`) holding Open, Import PDF, Export PDF, Save, Save As,
Pen diagnostics, and Settings; a **Main Toolbar `SideToolbar`** with core drawing tool slots and pop-up panels; a secondary toolbar (`TopBarToolsRow`) for geometric figures and STEM tools embedded in the top bar. Both toolbars follow the modern design system floating glass capsule style.

There is **one layout**, and each bar has one job. The Main Toolbar is the vertical docked capsule
(Left or Right, per `ToolbarPosition`) of **writing tools only**: pen, highlighter, eraser, pan, select,
text/pictures, vertical space, and the colour & size, layers, zoom and pages panels — **pages** being
the whole page errand: navigation, columns, format, and a row onto the page-background dialog for paper,
rule spacing and stationery. The Secondary Toolbar is the one home of the **STEM tools** (figures,
function graph, table, circuits, logic, chemistry, guides), because a rail slot for them was the same
button twice on one screen; audio and playback live on the audio capsule, shape recognition is a toggle.

The rail's edge is not a placement menu but the **handedness** answer: a right hand writes from the left,
so `ToolbarPosition.LEFT` is the default and `RIGHT` is its mirror — and those are the two values it has,
because the `TOP`/`BOTTOM` centre dockings were retired (2026-10-13: the header owns the top band, so a
centre docking could only be drawn as a second bar over the page).
First launch asks it as **which hand writes**, each answer carrying a miniature of the editor with the rail
on the edge that hand implies, and the settings picker later offers the two edges under **Settings →
Interface → Toolbar**. A stored or imported `TOP`/`BOTTOM` ages into `LEFT` in the readers
(`AppSettings.load`, `AppSettingsBackup.fromNode`), so an install from that era still opens with a rail it
can place. The tour that asks it is owned by [`stylus.md`](stylus.md#first-launch-onboarding).

The chrome never sits **on** the page: the header (top bar plus its tabs row) and the rail's lane each
take a band of their own and the canvas takes what is left, so the whole visible page stays writable —
see [chrome-frame.md](chrome-frame.md). The **desktop window** is the one shell whose tool strips float
instead (2026-10-26): a cursor says what is about to be touched — `docs/interface-contract.md` owns it.

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
- The Main Toolbar fill is translucent (`rememberToolbarColor()` at 88% alpha) with a 1dp hairline at 25% alpha, and it carries **no STEM section**: the f(x)/Geometry/Table/Circuit shortcuts and the figures live in the Secondary Toolbar, their one home (see the layout note at the top of this page). The slot list is pinned by `RailItemsTest`.
- The Main Toolbar renders as a floating dock surface (`SideToolbarModernWidth` = 52dp, the `railCapsuleWidthDp` token) with rounded corners (`16.dp`), no tonal elevation, shadow elevation (`6.dp`, the `capsuleShadowDp` token), the family's rim, and squircle tool buttons (`10.dp`) at the rail's own whole-slot sizing (40dp slots, grown by a few percent when that hides a sliver of the next one); its lane is `railLaneWidthDp` (64dp), which is the width the canvas is held out of; the lane's fill is the glass **composited over the chrome colour** rather than laid translucent, because behind it there is nothing but the chrome colour and the 12% a translucent band kept would show the desk's own edge across the rail; neither band casts a shadow — the header shades its own bottom edge (`interface-contract.md`, the Band row) and the lane, standing on the desk beside the page, casts nothing, where an elevation used to throw a smear back up onto the header in the lane's column only, a step across the header's closing edge; the Secondary Toolbar in the top bar renders as an adaptive floating dock surface (`40.dp` height) enclosing the visible figure tools with rounded corners (`20.dp`), tonal and shadow elevation, dynamically adapting its width to the number of visible figures, and not drawn at all while none are visible — and every glyph in it, the guides slot and the indicator face included, is drawn at the rail's own size (`ToolIconSize`, half a rail slot), because these vectors scale their stroke with the icon and a smaller glyph there is *thinner ink* beside the rail rather than a lighter style (`ToolGlyphWeightTest`); the **active tool indicator** is the dock's own **end cap** (`ActiveToolIndicator`), grey running flush into the dock's rounded border, `TOP_BAR_DOCK_HEIGHT` (40dp) tall with `TOP_BAR_DOCK_CORNER` (`20.dp`), the dock's tonal and shadow elevation and hairline border, its face (`ToolIndicatorFace`) showing the selected tool, colour and stroke width with no thickness bar beside the size letter, tapping it opening the Colour & size pop-up, and its grey the `surfaceContainerHighest` role — a surface step, never the accent `primaryContainer` a picked tool button wears; the style it replaced — the indicator standing **outside** the dock as a button of its own (`StandaloneToolIndicator`) in the top bar's leading slot, the corner between the rail's column and the dock, sized so a left-docked rail's dock starts on the document tab strip's own line, and trailing the dock when the rail is docked right (`standaloneIndicatorSlot`, pinned by `IndicatorSlotTest`) — is kept behind the `INDICATOR_AS_DOCK_CAP` constant as a one-flip restore path. The cap is the style in force because the standalone pill's fixed width is exactly the rail's own column (`TOP_BAR_INDICATOR_WIDTH`), so it sits flush against the *screen's* left edge; as the cap the indicator starts clear of both the edge and the rail, and the run still reads rail → indicator → figures; the top bar uses `TOP_BAR_ROW_HEIGHT` (52dp) with a grouped undo/redo pill container (`18.dp`), a compact quick Export PDF button followed by the accent-tonal Save button (so Save sits outermost), and active document title chip; tabs use 38dp height with pill chips (`14.dp`); page counter and zoom badges use frosted rounded pills (`14.dp`) and take the canvas corner **opposite the Main Toolbar** — bottom-right for a left rail, bottom-left for a right rail, derived from `settings.toolbarPosition` by `pageCounterAlignment` rather than saved as a placement of their own; floating action bars use rounded capsules.
Each rail button owns its own `DropdownMenu`, so the pop-up
is anchored to that button (opening to the right of the rail) rather than filling the screen. Every
one of those menus — the rail's, the dock's, the views'/pages'/layers' panels and the ☰ overflow —
is built by `ToolbarMenu` (`ToolbarPopup.kt`), the app's **one menu
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
missing; a family that declares no grouping at all, like the top bar's Shapes, keeps its own order). The two
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
launch. The figure set includes all geometric figures (`FIGURE_TOOLS` in `ToolGroups.kt`, including simple line and rectangle): `orderedShapeTools()` reads
`AppSettings.shapeOrder` (tool names, encoded like the rail ids). Its order and `shapeHidden` are edited **once**, in the **Toolbar** section's Secondary Toolbar list (`ReorderableRowList`), which writes both lists so visibility stays in sync with the secondary top bar and the Shapes submenu (`visibleTopBarItems` filters items hidden in either `topBarHidden` or `shapeHidden`) — the **Figures** section no longer repeats that list. The Figures section owns only the **default figure size**
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
`RAIL_ITEMS` names every position — every writing tool group plus the Colour/Layers/Zoom/Pages
panels — in an explicit factory order
(`FACTORY_RAIL_ORDER`): the drawing tools reached for most first (pen, highlighter, colour & size,
eraser), then the rest of the tools and panels, with the page-level panels trailing the rail
(audio, play object, shape recognition, guides and STEM slots live elsewhere). `SideToolbar` renders
`visibleRailItems(railOrder, railHidden)`. The Toolbar settings section edits those two prefs
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
*in* the bar — no grip bulge, no inset — so it reads as a hairline at the sheet's edge rather than a
control lying on the page, and it never covers a line of the ruling; it is pure navigation, with no
`.xopp` state, so nothing round-trips.

The frame's bands and divider, the saved toolbar order and the page-less canvas are [chrome-frame.md](chrome-frame.md)'s.

The Settings shell, its search and the prefs behind them are [chrome-settings.md](chrome-settings.md)'s.
