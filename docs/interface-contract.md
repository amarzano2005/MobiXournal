# Interface contract — what a new UI element must satisfy

> Read before adding **anything** to the interface: a tool slot, a header control, a band, a flyout, a
> dialog, a badge, a status readout. It holds for both shells — the Android editor and the `desktop/`
> window — because they draw one product with one material. A new element that breaks a rule here is a
> defect even if it looks fine on the machine it was written on.

This is a **prescriptive** document, not a description of what exists: the design system's numbers live in
[`ui-modernization.md`](ui-modernization.md) and `DesignTokens`, the layout in [`chrome.md`](chrome.md) — and, for the window, [`desktop-ui-layout.md`](desktop-ui-layout.md).
Where a rule below names a test, that test is the enforcement — a rule with no enforcement is either given
one in the same change or removed from this contract.

## 1. Material: three classes, no fourth

Every surface is one of three objects. Pick the class from what the element *does*, never from taste.

| Class | What it is | Recipe | Never |
|---|---|---|---|
| **Band** | chrome that borders the canvas — header, status bar, sidebar, a lane's dividing rule | the glass fill + **one 4 dp edge**, no rim; on the tablet the edge is drawn *inside* the band, on the window the band is an **opaque tone** and casts nothing | never lies over the page, never a shadow on the band beside it |
| **Capsule** | an object lying *in* a band's space — the tablet's rail, STEM dock, and audio capsule (header actions stay naked glyphs on the band) | the glass fill + its own 6 dp lift + the family's rim, inside a lane of **air** | never a second sheet of glass behind it |
| **Floating surface** | something shown over the page for a moment — menus, dialogs, toasts, badges | Android: glass at `floatingPanelAlpha` + rim at 20 dp corner; desktop: opaque `popupSurface` + 1 dp hairline at 8 dp corner (no cast elevation) | never an opaque Material card or a system `Toast` |
| **None** | the page's own content being authored — a stroke being drawn, a text box being typed | the document's own ink and face, in the document's own place | no fill, no rim, no padding |

- **One style, two shells.** A look is decided **once**, in `DesignTokens` (`mobix-core`), and lands on the tablet and the window in the same change: the glass, its alphas, each theme's rim pair, the desk, the ink trio, the steel and the corners are **read**, never re-declared. A shell that needs a different number says why in the token's own KDoc and here — the window's flat tones (`DESKTOP_BAND_*_RGB`, `DESKTOP_SURFACE_*_RGB`, `DESKTOP_CONTROL_*_RGB`) and its own corners. `ColorContractGuardTest` (`oneStyleTwoShellsTheLightMaterialIsReadFromTheTokens`) fails on a light-theme literal that should have been a token; `LightMaterialTest` and `DesktopThemeTest` hold the window's side.
- **The window is flat (2026-11-19).** A desktop surface is an **opaque tone**, never a glass over the desk — band, surface and control from `DESKTOP_*_RGB` — separated by tone and one hairline, with no shadow on a band, under a capsule or on the canvas edge. A popup is that surface with one hairline and the desktop corners, and what is behind it never reads through. Capsules keep the rim; the tablet keeps its glass. `DesktopPopupMaterialTest`, `DesktopThemeTest`, `ChromeMaterialRenderTest`.
- **A lane is air, not a band.** Painting a lane and its capsule both gives 0.85 over 0.85 (0.98) and the strip composites to `#161618` where the tablet measures `#131314`. This is the mistake the contract exists to prevent; it was made once and is now caught in pixels by `ToolStripsTest`. **The tablet's bands are that lane's other side**: the header and the rail lane wear the same glass (`RailLane` composites it over the chrome colour, so both render the band's exact tone) and make one frame around the canvas. The lane **never takes the canvas's background** — the floor around the page — because that turns the frame's leg deep black on the dark scheme and grey on the light one, breaking it at the corner.
- **A band is not a panel.** No rim above and below a slit of chrome: on the true-black scheme that reads as two grey rules across the window. The edge is a band's whole boundary.
- **On paper a band is the sheet's white, edged by one shade on the side that looks at the canvas.** A band takes the glass at **full** opacity there (`panel`), so the header is the same white as the page it frames and the system caption can be given that white too; what separates it from the sheet is a gradient drawn **on the paper** along the canvas' own edges (`BAND_EDGE_ALPHA_LIGHT`, reaching `BAND_EDGE_SHADOW_DP`) — the canvas draws the top line and the side line, so they turn its corner and neither band's shade runs across the band beside it. A capsule is that white with the family's ink rim, the picked steel at `HOVER_SLOT_ALPHA_LIGHT` under the pointer and the full steel when picked, while an **unavailable command is its muted glyph alone**. The rim carries more of the same ink on paper (`RIM_ALPHA_TOP_LIGHT`, 0.20). An **Android floating** surface is that recipe's one exception to full opacity: it keeps the white at the `floatingPanelAlpha` token, so a menu is still glass over the page, and the token is its bound — the sheet's tone shows through it, its handwriting does not. `LightMaterialTest` holds every answer, its pixel test included.
- **A band casts nothing onto the band beside it.** The tablet's bands are rows of the *window*, so a shadow cast outside one can only land on the surface composed after it: the header's fell on the rail's lane and on the canvas (both painted it over, so it was never seen) while the lane's own fell back **up** onto the header in the lane's column alone — the header closing on a soft edge over the rail and on a hard step over the page. The band therefore draws its edge **inside itself** (`BandBottomShadow`, `ui/EditorRegions.kt`; strength `bandShadowAlpha`): a `4 dp` fade at its closing edge, one direction, landing on no other surface. The window casts **no** shadow at all: a flat band and its lane are separated from the canvas by tone and one 1 dp divider (`Modifier.bandDivider`), never by a lift.
- **The frame's two arms share one edge.** The header's edge is a *segment*, not a full-width line: it runs only over the width the canvas has (`bandClosingEdge(startInset, endInset)`, inset by the rail's lane width on the side the lane is docked) and the lane draws the same fade, `4 dp` tall and the same scrim, down its edge facing the page (`bandSideEdge`). Together they are one corner: a full-width header edge crossing the lane's column put a soft line across the joint and made one frame read as two surfaces. `ChromeGeometryGuardTest.bandsShadeTheirOwnClosingEdgeAndCastNothing` fails the build if a tablet band gains an elevation again, or loses its own edge.
- A picker, menu or dialog that comes and goes **may** lie over the page; a band **may not**.
- **The window asks every question one way**: `DesktopDialogFrame` — a scrim that dims and blocks, the opaque popup surface at the 8 dp corner, the commit at the end of the footer; `DesktopDialogContractTest` holds it.
- **Living-element editors follow one rule per shell.** On **desktop**, every living element (`Plot`, `Table`, `ParametricShape`, `Circuit`, `MathBlock` and any future family) opens in `InspectorLane` beside the sheet. On **Android**, pick the container by interaction rather than taste:
  - **Anchored floating inspector (`LivingInspectorAnchor`)** when editing is **contextual and visual** (the user needs to see the element on the sheet while adjusting orientation, label side, curves, geometry or live preview), using either a docked in-app keyboard (`Plot`, `Circuit`, `ParametricShape`) or an IME-adaptive formula bar above the keyboard with active cell canvas highlight (`Table` in `TableEditorHost`, with `Cells` and `Layout & Style` tabs). Aspect-lock toggles on the selection bar are shown only when `SelectionResizeMath.supportsAspectLockToggle` holds (single shapes and non-shaft strokes), persisting their lock state per element while allowing drags from anywhere inside the selection box or polygon interior (with Xournal++-style continuous edge panning relative to visible chrome bounds). `LivingInspectorAnchor` picks the side (right, left, below, above) with minimum overlap over the figure in the canvas's unpadded coordinate space while clamping strictly inside `safeInsets` (`insetPadding`, clear of the tab strip and rail) and above any active keyboard (`WindowInsets.ime` or docked `SHEET`), plays `DialogPresence` + `liquidGlassSpringAnimation` (`POPOVER`) in place once measured, and supports instant manual header dragging; tabbed inspectors (`Curves`/`Options` on Plot, `Geometry`/`Style` on Shape, `Cells`/`Layout & Style` on Table) share underline `InspectorTabBar` navigation, a high-contrast hairline divider (`onSurface` at 22%), smooth `AnimatedContent` + `liquidGlassContentSize()` tab transitions, and borderless `InspectorChip` secondary options.
  - **Fixed centred dialog (`DialogPresence` + `FloatingDialog` / `FloatingSurface`)** when editing is **text-heavy multiline source** with the system IME (`MathBlock`) so edge-to-edge IME resizing never covers or shifts fields under the stylus. Dialog bodies must scroll vertically (`verticalScroll`).
- **The pen-tip ring is a floating surface that happens to be round** ([`ui-modernization.md`](ui-modernization.md)): the floating fill and the rim at its own radius instead of the 20 dp panel corner, the picked slot on the steel with the accent ring around it — a menu's tokens, arranged round because the choice is made with a flick.
- **A list inside Settings is a floating surface on the settings panel's fill**: every other menu keeps the floating glass, but a select's list lands over a settings card, where that glass let the row behind it read through the answers it was showing. It takes the near-solid panel the cards themselves wear, and keeps the rim, the panel corner and the spring; the window's own select opens on that fill for the same reason ([`desktop-settings.md`](desktop-settings.md)).
- **The window's tool strips are docked bands** (lane 2026-10-28, flat band 2026-11-19). The rail stands in a band down an edge and the ribbon in a band under the header — the header's own material and its one 1 dp line on the canvas side — with the slots standing *in* that fill: 36 dp on a 4 dp corner, in a 48 dp rail or a 44 dp ribbon, and a 24 dp hairline between the five tool families. No capsule stands in the lane and no bar lies on the sheet: the canvas is handed what is left. The strips lay themselves *over* the page between 2026-10-26 and the lane decision; that left the canvas full width and the page running behind the glass, and one window is not a second design language. The band is opaque to the pointer (`DesktopTheme.glass`) — a translucent bar turned grey over a white sheet. The canvas can still be told to frame a fitted page around a surface that lies on it (`ChromeInset`), which no strip asks for now.
- **Page content being authored is not a surface.** A stroke being drawn is a draft on the sheet, and the desktop's in-place text field is the same thing for words: it takes the document's own ink, face and place, and **no fill and no rim** — it wore the floating glass once and read as a charcoal slab across a third of a white page. Only the accent caret marks it. `DesktopTextPaintTest` reads the paper through a point inside the box, so glass cannot come back.

## 2. Colour: tokens or nothing

- Only values from `com.mobixournal.design.DesignTokens` (wrapped by `ModernTheme` / `DesktopTheme`).
  **A hex literal may only be spelled in `ui/theme/`** — the palette's one home — and every other file
  reads a token or a theme function. `ColorContractGuardTest` sweeps the whole `ui` tree for `Color(0x)`
  outside that directory and fails by file and line.
- `Color.White` / `Color.Black` are allowed only as an **on-colour** (text or glyph on a filled accent,
  signal or steel surface) and in a renderer's own contrast pair (the colour picker's black-and-white
  cursor, a swatch's tint chosen by luminance) — never as a surface fill the chrome returns to.
- The chrome ink is **`#FFFFFF`** (`CHROME_INK_DARK_RGB`): tool glyphs and header glyphs are white in both
  shells, active or not. The secondary and muted inks are for figures and explanatory labels only.
- **Two commands have a colour of their own, on both shells**: a PDF command is `DocumentRed`
  (`DOCUMENT_RED_RGB`, `#E53935`) and Save is the accent — the tablet's convention, which the window now
  follows (`DesktopTheme.documentRed`, `DesktopTheme.accent`). A third named colour is a decision for this
  file, not a call site's taste.
- **A destructive command is the refusal red** (`danger`), never the document red: `danger` is what takes
  something away or refuses a value (`DANGER_*_RGB`), `documentRed` is the PDF convention above. The window's
  four button variants (`DesktopButtonVariant`) and their one state recipe are [§6](#6-states-five-all-of-them-drawn);
  a filled Material button is not a variant, and a dialog's answer is not a capsule.
- **The window's tones are its own**: bands, surfaces and controls take `DESKTOP_*_RGB` opaque, never the
  tablet's glass (2026-11-19, superseding the 2026-10-25 thinner band); pointer white at
  `DESKTOP_HOVER_DARK_ALPHA`, picked `DESKTOP_ACTIVE_SLOT_DARK_RGB`. The ink and accent are shared.
- The picked state of an icon/tool slot is the **steel `#38383E`** (`ACTIVE_SLOT_DARK_RGB`) with the glyph in the accent
  (`#60A5FA`). A new "selected" look is not a design decision; it is a second state the user has to learn.
- **Desktop text feedback:** the global text-control rule is [below](#6-states-five-all-of-them-drawn). Sidebar fields keep a hairline at rest and an accent/error underline for focus/refusal, without capsule lift or glow.
- Enforced by `ColorContractGuardTest` (Android chrome) and `DesktopThemeTest` (desktop chrome literal
  sweep), and mirrored in the token block of [`ui-modernization.md`](ui-modernization.md).

## 3. Geometry: the token numbers, not close ones

| Measure | Value | Token |
|---|---|---|
| Header / status bar | 40 dp / 24 dp, both shells (the window's header is the token) | `HEADER_HEIGHT_DP`, `STATUS_HEIGHT_DP` |
| Rail / ribbon band | 48 dp wide / 44 dp tall, 36 dp slots, 6 dp of air | `DESKTOP_RAIL_WIDTH_DP`, `DESKTOP_RIBBON_HEIGHT_DP` |
| Tool slot | window 36 dp on a 4 dp corner; tablet 40 dp squircle | `COMPACT_BUTTON_SIZE_DP`, `RAIL_SLOT_CORNER_RADIUS_DP` |
| Header control | 28 dp icon command | `DESKTOP_HEADER_BUTTON_DP` |
| Capsule / panel corner | 16 dp / 20 dp | `CAPSULE_CORNER_RADIUS_DP`, `PANEL_CORNER_RADIUS_DP` |
| Rim stroke | 1 dp | `RIM_WIDTH_DP` |
| Desktop control / surface corners | 4 dp / 8 dp | `DESKTOP_CONTROL_CORNER_DP`, `DESKTOP_POPUP_CORNER_RADIUS_DP` |
| Desktop dropdown row / inset | 28 dp / 4 dp | `DESKTOP_POPUP_ROW_HEIGHT_DP`, `DESKTOP_POPUP_INSET_DP` |

- Nothing interactive is smaller than a tool slot. A control that needs to be smaller is a control that
  belongs in a menu.
- No band overlaps the page: the canvas starts where the last band ends, on both shells.

## 4. Icons: one catalogue

- Every tool glyph is `ToolGlyphCatalogue` (`mobix-core`) on the **24-unit grid** with a **1.5-unit stroke**
  (`TOOL_GLYPH_GRID`, `TOOL_GLYPH_STROKE_UNITS`); the tablet builds an `ImageVector`, the desktop fills a
  `DrawScope`. A shell may not keep a hand-drawn set of its own, and `material.icons` is not an import the
  desktop's chrome may have.
- The **settings'** glyphs are `SettingsGlyphCatalogue` (`mobix-core`) too: one mark per section, named
  after the section, drawn by the tablet as an `ImageVector` and by the window into its `DrawScope`. A
  settings row may not carry a Material icon on one shell and a blank space on the other —
  `SettingsGlyphParityTest` and `DesktopSettingsGlyphTest` fail by name if it does.
- A settings mark is a **silhouette with one idea in it**, because an index row draws it at 16 dp: a pen,
  a sheet, a drop, two fingertips on the glass. An abstract diagram of the same idea (a nib drawn as a
  stick over a triangle, a tap as a ring inside a ring) reads as a funnel and a target at that size and
  is a bug, not a style. Where the product already has the picture — the **Stylus** section wears
  `ToolGlyph.PEN`, the rail's own pen — the catalogue **reuses** it instead of drawing a second pen.
  Art is judged by eye, so `SettingsGlyphRenderTest` paints every mark at 16 dp (asserting each draws
  ink) and writes `build/settings-glyphs.png` and `build/settings-glyphs-index.png` to look at.
- The **chrome's** glyphs (`ChromeGlyphCatalogue`, `mobix-core`) are every mark that is not a tool face:
  undo, redo, the file commands, the menu, the dismiss and add marks, the layer eye, and the whole
  selection bar — cut, copy, paste, duplicate, recolour, width, edit, delete and the six alignments with
  the two distributions. The art is the path data Material publishes, transcribed once as `d` strings on
  the same 24-unit grid — the one exception is `ChromeGlyph.GITHUB`, whose octocat is the brand's own
  published path on that grid, because no Material icon names the service and a hand-drawn mark on each
  shell is exactly the drift this catalogue prevents — and **both shells read it**: the tablet builds an `ImageVector` from the string
  (`ChromeGlyphIcons.kt`), the window fills a `Path` parsed from it (`SvgPath.kt`) — a shell may not
  hand-draw a mark of its own, which is how a phone and a PC came to show two nearly-equal icons for one
  command. `ChromeGlyphsTest` holds every glyph to a shape that parses, stays on the grid and is its own
  picture.
- The `when` in `ToolGlyphCatalogue.parts` is exhaustive: a new tool does not compile without art.
  `ToolGlyphCoverageTest` additionally requires **every** `EditorTool` — the rail's slots, the STEM dock's
  families, every member a picker row offers, the variant faces a slot takes on and the authoring modes —
  to come from the catalogue, and requires no two tools to share a picture. The tolerated set is empty; a
  tool added without a glyph, or a slot slipping back to a Material icon, fails the build.
- Art rules, learned the hard way: the glyph must read at **20 dp**; it must use the grid's area (a small
  shape in a big box reads as a blob); a pair of tools that differ only in subtlety (eraser vs whole-stroke
  eraser) must differ by a whole stroke (the crease, the sweeps), never by a shade.

## 5. Text

- **English**, sentence case, on both shells. No abbreviations invented by the UI.
- An error says **what happened and what to do**, in the user's terms. Never a class name, a stack frame,
  or an internal identifier where a person is looking ([`ui-modernization.md`](ui-modernization.md) owns
  the wording rule).
- A number the user reads (page, zoom, count) is set with tabular figures so it does not shuffle.

## 6. States: five, all of them drawn

Every interactive element defines **default, hover, picked, disabled, focus**. For icon/tool slots, picked is
the steel slot; hover is the same steel at `hoverSlotAlpha` (**0.45**), never a colour change of the glyph and
never the picked fill; disabled keeps its ink but loses its hit area; focus is visible without relying on
the pointer. **On the window** hover is white at 8 % (`DESKTOP_HOVER_DARK_ALPHA`), picked is its active slot,
and a focused control wears an accent ring — 1 dp on a glyph control, 2 dp on a button. A new element with only a default state is unfinished.

The state layer **follows the shape**: an indication is drawn by the node that owns it, and a rounded control clips to its own shape **before** the click (`FavoriteSwatch`'s `DotCellShape`, `ZoomBadge`, the menu's panes whose click `GlassPane` takes inside its clip), so a tap or hover lights the capsule, circle or squircle the reader sees and not the rectangle around it. `ShapeFeedbackTest` holds the shared controls to that order, because a clip one modifier too late still compiles and reads correctly.

**Desktop commands (owner rule, 2026-11-19, superseding the 2026-11-17 text-only rule):** a filled command is `DesktopButton` in one of four variants — **Primary** (accent fill, `onAccent` ink), **Secondary** (control fill, strong hairline, ink), **Ghost** (nothing at rest, `inkSecondary`), **Destructive** (the refusal red at 14/22/30 %, a 40 % `danger` hairline) — at the 4 dp control corner, with hover one step past the rest fill, press one further, disabled keeping its shape at 45 % ink and keyboard focus a 2 dp accent ring. A glyph command is `DesktopIconButton` (4 dp, nothing at rest, `hoverSlot`/`activeSlot`, a 1 dp accent hairline on focus, a delayed 350 ms `StripPill`); a label that navigates — a tab, a status link — stays `DesktopTextAction`/`desktopTextFeedback`: ink and one underline, the accent for picked and focus, never a box; a **preset** is the one choice that control draws as a **chip** (its opt-in `chip` recipe): a 4 dp hairline at rest, the accent's rim and its 12 % wash on the answer in hand, and no underline, because a chip is a control rather than a link. A field is `DesktopTextField`: `panelField` fill, 1 dp `borderStrong` at rest, the accent while typed in, `danger` on a refused value, value and placeholder in one 12 sp tabular face.
`DesktopTextFeedbackContractTest` pins the factories, forbids a Material button and forbids a capsule state layer on a command; `DesktopGlobalTextFeedbackTest` reads the four variants, the field and the label out of real pixels.

The main hamburger menu, entry-screen commands and document tabs retain their original capsule/glass presentation (owner-requested exceptions, checked by `DesktopTextFeedbackContractTest`). Other distinct cases: icon tools and switches retain shaped slot feedback; popup menu choices retain the full-width rounded feedback checked by `SidebarTextFeedbackTest`; selection in document text, thumbnails, layers and search results remains content selection. Editable fields retain their caret, native text-selection highlight and bounded focus marker. These are not text-command state layers.

On Android, the state layer is clipped to the visible control shape (`ShapeFeedbackTest`). A document tab there keeps its chip glass and accent edge. Desktop icon controls use shaped `hoverFill` / `hoverCapsule`; `DesktopShapeFeedbackTest` and `ToolStripsTest` keep those controls responsive without applying the icon rule to text commands.

A control that opens a surface is **also a control that closes it**: the second click is the surface's own
dismissal, so the trigger may not reopen what its own click has just put away (`DismissGuard` — a Compose
popup dismisses on the press the trigger is about to take, and a plain `open = !open` flips the flag back
up). Every trigger of every popup and flyout goes through it; `DismissGuardTest` holds the rule.

A **status readout** is the one thing that is not interactive, and it is not exempt from colour: the
desktop's dirty dot is a token mark that is either there or absent, with no hover, focus or picked state —
those five belong to the control beside it (the close glyph). `DesktopLifecycleTest` reads the dot out of
the strip's pixels, so a readout that stops being drawn fails the build.

## 7. Motion

Native desktop popup windows appear and disappear immediately at full scale and opacity (`GlassPopupGuardTest`); scaling or fading content inside the window would expose underlying controls. In-shell surfaces may retain their arrival motion. The following shared spring recipe applies to Android and those in-shell surfaces.

Only `DesignTokens.GlassMotion` (role-based scale, slide, stiffness, damping, fade across `POPOVER`,
`SHEET` and `DIALOG`, all critically or near-critically damped with no splash overshoot). No bespoke
`tween`, no `animateFloatAsState` with hand-picked durations, and no animation on a band.

- **Android and in-shell floating surfaces arrive *and* leave** on that spring — a pop-up, a flyout, a list, a dialog, a
  toast, on both shells. A surface that animates one way only is a defect, not a simplification; the
  numbers and their reasoning live in [`ui-modernization.md`](ui-modernization.md).
- **The surface is held composed for the exit.** A dialog's window and a pop-up's window are torn down
  between two frames the instant the flag clears, so a bare `if (open) { … }` cannot animate out: gate it
  with `DialogPresence(visible)` on the tablet (including `PaneSelectionBars` and `PenDiagnosticsPanel`) or `GlassPresence(visible)` in the window, and open the
  window's native pop-up through `GlassPopup`, which preserves visibility and input policy without that spring.
  `GlassPopupGuardTest` fails the build on a raw `Popup(`.
- **The spring goes outside the fill**: the animation modifier comes *before* the `background` /
  `glassSurface()` / `glassPanel()` in a surface's chain. Written after it, the layer owns only the
  panel's children — the glass lands opaque and full-size while the rows inside move.
- **The bounce belongs to the panel, once.** Rows, chips and glyphs inside a surface never animate
  separately.
- **A hover tooltip is not a surface that arrives**: it is the pointer's readout, so it lands with the
  pointer (`StripPill` in the window). That is the one recorded exception, and it is a deliberate one.

## 8. Keyboard (desktop)

- Every chrome action is reachable from the keyboard, and every shortcut appears in the table of
  [`ui-modernization.md`](ui-modernization.md) §3. A shortcut that exists only in code is undocumented
  behaviour; one that exists only in the doc is a lie.
- A physical-key binding is chosen where the layout would otherwise swallow it (an Italian keyboard has no
  `Ctrl + =`): bind the key, not the character.

## 9. Parity: one product, two shells

- The same element wears the same fill, ink, corner and glyph on both shells. If the desktop cannot draw
  something yet (a submenu, a ring), it is **absent**, not approximated in a second visual language.
- The parity is measured, not asserted: a pixel test compares the fill and the ink against the shared
  tokens (`ChromeMaterialRenderTest`, `ToolStripsTest`), and a colour test compares the two themes.

## 10. Documentation and tests in the same change

- The owning spoke is updated in the **same commit** ([`architecture.md`](architecture.md) says which spoke
  owns what); a new spoke is registered in `architecture.md` **and** in the `AGENTS.md` map, or
  `DocsGuardTest` fails.
- The change ships with the check that would have caught its absence: a render/pixel assertion for
  material or ink, a catalogue test for a glyph, a token re-read for a new number, a keyboard test for a
  shortcut.
- A behaviour that cannot survive a save → reopen in `.mobix` or `.xopp` is out of scope until the format
  carries it ([`mobix-format.md`](mobix-format.md)).

## Checklist

Before a UI change is called done, every line is true — and `todo.py done` will not close the task until the answers are declared ([`design-workflow.md`](design-workflow.md#closing-a-ui-change), *Closing a UI change*):

- [ ] the surface is a band, a capsule or a floating surface — and it takes that class's full recipe
- [ ] every colour, radius and size is a token; no literals were added
- [ ] the glyph (if any) is in the catalogue and reads at 20 dp
- [ ] all five states exist
- [ ] text is English and an error names the next step
- [ ] every desktop action has a documented shortcut
- [ ] both shells agree, or the element is deliberately absent from one
- [ ] the owning doc is updated and the guard suite is green
- [ ] the test that would catch its removal exists

## Air at a rounded edge

A rounded container owns the gap between its own edge and anything inside it, and that gap is
`PanelEdgeAir` (6 dp) — one token for settings cards, floating bars and pop-ups, so no surface guesses
its own number. A corner cuts the outermost dp off a straight edge, so text or a border drawn at the
container's own padding lands on the curve and reads as clipped; the container pads instead, which also
means a caller's few dp cannot defeat it. A **pill** is a different case: its label sits *inside* the
shape, so it keeps `PillTextInset` (12 dp), because a curve eats into a label's own line.
Both numbers are declared in `mobix-core`'s `PanelAir`; each shell only converts them to `dp`, so the
tablet and the window cannot disagree about how tight an edge is.

A **list** is the one case where the container keeps no horizontal air of its own. A row's highlight is
painted by the row's own node, so padding the list sideways leaves every selected answer as a square
band that stops short of the strip's curve — the lit part no longer wears the surface's shape. The list
pads its rows **vertically** only, the panel's rounded clip supplies the ends, and each row carries the
inset its own label needs (`OptionMenuItem`'s 16 dp).

A **tablet settings card** keeps its horizontal air and rounded clipping. Desktop settings and live
inspectors use flat property groups instead: a heading, whitespace and a hairline, with no nested glass
or group rim. Their outer panel/lane keeps the existing material. This is the desktop layout decision for the pointer/keyboard workspace; palette, glyphs and minimum 40 dp hit areas remain shared. Desktop row hover is rectangular within its group bounds; `DesktopSettingsStyleTest` checks the pixels. The desktop inspector divider uses a full-height 6 dp resize target with keyboard alternatives, as a boundary handle rather than a button slot. Desktop property controls use the 4 dp `PROPERTY_CONTROL_CORNER_DP` token, square checkbox indicators and unfilled secondary actions; other chrome retains its shapes. Plot previews in both shells inherit the panel background and adapt ink for contrast without recoloring documents.

A select whose answers belong to **several families** prints a **head** over each one — the ring's
*Colours*, *Tools* and *Widths*, with *Remove* under *Leave it out*. A head names a family, never a choice,
and both shells name the same families in the same order.

