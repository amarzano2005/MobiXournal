# The frame: the chrome's bands, the saved order, and the empty document

> Read when you change where the chrome sits around the page, the order the tools keep between runs, or
> what a page-shaped surface shows on a canvas that has no pages.

The chrome's place in the window — bands beside the page, the toolbar order that survives an update, and
what every page-shaped surface does on a canvas that has none. The editor screen, the rail and the dock's
material are [chrome.md](chrome.md)'s; the Settings shell it used to sit beside is
[chrome-settings.md](chrome-settings.md)'s.

## The frame around the page: the chrome's bands, and the canvas's rest

**The chrome is laid out beside the page, never on it**: the header band and the rail's lane are rows of
the layout, and the canvas (`DrawingSurfaceView`) is handed what is left. `EditorScreen` is a `Column`
(header first, `EditorBody` in the weight that remains); `EditorBody` is a `Row` of the rail's lane
(exactly `SideToolbarModernTotalWidth`, the edge `AppSettings.toolbarPosition` names) and the canvas.
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
  the new document there. A caller with no settings to read has one row and no room for two lanes, so it
  keeps the focused pane's strip in the title slot, badge included.

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
surfaces may*. An anchored popover, a dialog or a selection bar comes and goes with the gesture it
belongs to; a band is always there, so it never costs the sheet a pixel.

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
starts below the header's *whole* height, not below the bar's row of it. A caller with no settings
to read (a preview) keeps the tabs in the bar's **title** slot instead — the same slab, one row, because
there is no second row to give them.

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
owns the mode), and every surface here that used to count pages had to answer for it. The rule is:
**no surface may claim a page that does not exist** — and, just as important, none may offer a control
whose effect is invisible, or one that would rewrite the file's own chunk grid.

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
  content is unbounded, so a thumb fraction of "how far down" is a number about nothing, and the canvas reports
  the viewport as its whole extent — the report a short document makes — so the thumb hides itself.
- **The Pages menu** (`ui/ToolbarPagesPopup.kt`) becomes a **tiles** reading: which tile you are looking at,
  and how many there are — plus the rows about the *document* rather than its parts, **Page format**
  (geometry and the strip's **Fixed width/height** bound, [infinite-canvas.md](infinite-canvas.md) →
  *The bound on the non-infinite axis*) and **Page background** (its paper and ruling). Format is also
  the way back to pages. Every other row addresses a page and a board has none: adding or removing one
  would rewrite the chunk grid the file names its parts by, so those rows are gone rather than disabled. The
  **pages-per-row grid and its edit mode** are inert on a board for the same reason (the board pass
  replaces the page stack, so `columns` changes nothing on screen) and are not offered.
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
  canvas with one message (`DrawingSurfaceView.onBoardBlocked`, `infinite_canvas_only_drawing`) that names
  what *does* work before what does not; the rail keeps offering their tools, and the refusal is what the
  user sees. Drawing, the eraser, the placement tools and undo/redo work, in the tile's own coordinates.
