# Interface contract — what a new UI element must satisfy

> Read before adding **anything** to the interface: a tool slot, a header control, a band, a flyout, a
> dialog, a badge, a status readout. It holds for both shells — the Android editor and the `desktop/`
> window — because they draw one product with one material. A new element that breaks a rule here is a
> defect even if it looks fine on the machine it was written on.

This is a **prescriptive** document, not a description of what exists: the design system's numbers live in
[`ui-modernization.md`](ui-modernization.md) and `DesignTokens`, the layout in [`chrome.md`](chrome.md).
Where a rule below names a test, that test is the enforcement — a rule with no enforcement is either given
one in the same change or removed from this contract.

## 1. Material: three classes, no fourth

Every surface is one of three objects. Pick the class from what the element *does*, never from taste.

| Class | What it is | Recipe | Never |
|---|---|---|---|
| **Band** | chrome that borders the canvas — header, status bar, sidebar, a lane's dividing rule | the glass fill + **one 4 dp cast shadow**, no rim, no gradient | never lies over the page |
| **Capsule** | an object lying *in* a band's space — the tablet's rail and ribbon, a header control | the glass fill + its own 6 dp lift + the family's rim, inside a lane of **air** | never a second sheet of glass behind it |
| **Floating surface** | something shown over the page for a moment — menus, dialogs, toasts, badges | the glass fill at `floatingPanelAlpha` + the rim, at the 20 dp panel corner | never an opaque Material card or a system `Toast` |
| **None** | the page's own content being authored — a stroke being drawn, a text box being typed | the document's own ink and face, in the document's own place | no fill, no rim, no padding |

- **A lane is air, not a band.** Painting a lane and its capsule both gives 0.85 over 0.85 (0.98) and the
  strip composites to `#161618` where the tablet measures `#131314`. This is the mistake the contract
  exists to prevent; it was made once and is now caught in pixels by `ToolStripsTest`.
- **A band is not a panel.** No rim above and below a slit of chrome: on the true-black scheme that reads
  as two grey rules across the window. The shadow is a band's whole edge.
- A picker, menu or dialog that comes and goes **may** lie over the page; a band **may not**.
- **The window's tool strips are capsules held *over* the page** (decision 2026-10-26). On the desktop the
  rail and the ribbon float on the sheet — the capsule's recipe unchanged, glass, its own 6 dp lift and the
  family's rim — and the canvas keeps the whole body, so choosing an edge no longer moves the page. The
  tablet keeps its bands: a fingertip has no cursor to tell it what it is about to touch, while a mouse and
  a stylus on a desk do. A strip is opaque to the pointer, so a press on its glass is never a stroke under it.
- **Page content being authored is not a surface.** A stroke being drawn is a draft on the sheet, and the
  desktop's in-place text field is the same thing for words: it takes the document's own ink, face and
  place, and **no fill and no rim** — it wore the floating glass once and read as a charcoal slab across a
  third of a white page. Only the accent caret marks it. `DesktopTextPaintTest` reads the paper through a
  point inside the box, so glass cannot come back.

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
- **One tone is the window's own**: its bands take the glass RGB at `DESKTOP_BAND_ALPHA` (0.58, i.e.
  `#0C0C0D` over the black desk) rather than the tablet's 0.85 (`#131314`), by the owner's decision of
  2026-10-25 — `ui-modernization.md` owns the reasoning. Every other colour is shared, and the desktop's
  chrome tests pin this one so it cannot drift silently.
- The picked state is the **steel `#38383E`** (`ACTIVE_SLOT_DARK_RGB`) with the glyph in the accent
  (`#60A5FA`). A new "selected" look is not a design decision; it is a second state the user has to learn.
- Enforced by `ColorContractGuardTest` (Android chrome) and `DesktopThemeTest` (desktop chrome literal
  sweep), and mirrored in the token block of [`ui-modernization.md`](ui-modernization.md).

## 3. Geometry: the token numbers, not close ones

| Measure | Value | Token |
|---|---|---|
| Header / status bar | 40 dp / 24 dp (the window's header is the token **+ 8**) | `HEADER_HEIGHT_DP`, `STATUS_HEIGHT_DP`; `DesktopTheme.headerHeight` |
| Rail capsule in its lane | 52 dp in 64 dp (6 dp side air, 8 dp above and below) | `RAIL_*` |
| Ribbon capsule in its band | 48 dp in 56 dp | `ribbonCapsuleHeight` |
| Tool slot | 40 dp, 10 dp squircle | `COMPACT_BUTTON_SIZE_DP`, `RAIL_SLOT_CORNER_RADIUS_DP` |
| Header control | 36 dp capsule | `HEADER_CONTROL_SIZE_DP` |
| Capsule / panel corner | 16 dp / 20 dp | `CAPSULE_CORNER_RADIUS_DP`, `PANEL_CORNER_RADIUS_DP` |
| Rim stroke | 1 dp | `RIM_WIDTH_DP` |

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
  the same 24-unit grid, and **both shells read it**: the tablet builds an `ImageVector` from the string
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

Every interactive element defines **default, hover, picked, disabled, focus**. The picked state is the
steel slot; hover is the same steel at `hoverSlotAlpha` (**0.45**), never a colour change of the glyph and
never the picked fill; disabled keeps its ink but loses its hit area; focus is visible without relying on
the pointer. A new element with only a default state is unfinished.

On the desktop every surface wears `Modifier.hoverFill()` (`GlassEdge.kt`), and `ToolStripsTest` reads
the hovered pixel against the unhovered one, so a control that answers only its tooltip — as the rail did
— fails.

A **status readout** is the one thing that is not interactive, and it is not exempt from colour: the
desktop's dirty dot is a token mark that is either there or absent, with no hover, focus or picked state —
those five belong to the control beside it (the close glyph). `DesktopLifecycleTest` reads the dot out of
the strip's pixels, so a readout that stops being drawn fails the build.

## 7. Motion

Only `DesignTokens.GlassMotion` (stiffness, damping, fade). No bespoke `tween`, no `animateFloatAsState`
with hand-picked durations, and no animation on a band. A desktop `Popup` may appear instantly — a popup
has no composition to hold open once its flag clears — but it may not animate on its own curve.

## 8. Keyboard (desktop)

- Every chrome action is reachable from the keyboard, and every shortcut appears in the table of
  [`ui-modernization.md`](ui-modernization.md) §3. A shortcut that exists only in code is undocumented
  behaviour; one that exists only in the doc is a lie.
- A physical-key binding is chosen where the layout would otherwise swallow it (an Italian keyboard has no
  `Ctrl + =`): bind the key, not the character.

## 9. Parity: one product, two shells

- The same element wears the same fill, ink, corner and glyph on both shells. If the desktop cannot draw
  something yet (a submenu, a radial HUD), it is **absent**, not approximated in a second visual language.
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

Before a UI change is called done, every line is true:

- [ ] the surface is a band, a capsule or a floating surface — and it takes that class's full recipe
- [ ] every colour, radius and size is a token; no literals were added
- [ ] the glyph (if any) is in the catalogue and reads at 20 dp
- [ ] all five states exist
- [ ] text is English and an error names the next step
- [ ] every desktop action has a documented shortcut
- [ ] both shells agree, or the element is deliberately absent from one
- [ ] the owning doc is updated and the guard suite is green
- [ ] the test that would catch its removal exists
