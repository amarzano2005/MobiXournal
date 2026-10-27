# Modern UI design system: lightweight, content-first interface

> Read when redesigning or modernizing the mobile and desktop graphical interface, transitioning away from heavy Material Design paradigms, implementing floating capsules, or aligning cross-platform aesthetics.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: moving beyond heavy Material Design

The interface was built on Google's standard Material 3 conventions — filled container pills, coloured
surface tints, heavy drop shadows and full-screen dialogs. Functional on a phone, that aesthetic is
visual clutter on a tablet or a desktop: it reads as an "Android OS utility" rather than as a focused
creative tool (Figma, Linear, Concepts, Freeform).

The **Modern UI Design System** replaces it with a clean, lightweight, content-first interface shared by
the tablet and the `desktop/` window; the layout it produces is owned by [chrome.md](chrome.md), and the
window's own panes and strips by [desktop-ui-layout.md](desktop-ui-layout.md).

---

## Core design principles

```text
┌────────────────────────────────────────────────────────────────────────┐
│ [≡] [Undo] [Redo]   [Pen] [Highlighter] [Eraser] [Shapes] [Math]       │ ← header band
│ [Untitled] [Untitled.mobix]                                            │ ← the tabs, its 2nd row
├────────┬───────────────────────────────────────────────────────────────┤
│ ╭────╮ │                                                               │
│ │ ✏️ │ │                    CANVAS (Content-First)                    │
│ │ 🖍️ │ │        the sheet begins where the chrome ends                 │
│ │ 🧹 │ │        Unobtrusive, distraction-free space                     │
│ ╰────╯ │                                                               │
└────────┴───────────────────────────────────────────────────────────────┘
   ↑ the rail's lane: a band beside the page, never a bar over it
```

### 1. Content-first minimalism ("The canvas is the hero")
- **Visual Weight**: thin hairline borders ($1\text{ px}$, $10\text{--}15\%$ alpha), never chunky shadows or filled cards.
- **Glass in bands, never over the page**: the toolbars are translucent frosted capsules (backdrop blur at
  $85\text{--}90\%$ opacity, $1\text{ px}$ hairline at $12\%$ alpha) that live in **bands of their own** —
  the header across the top and the rail's lane down one edge — so the glass reveals the **desk** the page
  lies on. A bar *on* the sheet hides the ink under it, swallows the stylus there, and covers the top of the
  sheet, where a hand reaches first ([chrome-frame.md](chrome-frame.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest)).
  **The rule, and its one carve-out** — *bands never lie on the page; transient surfaces may*: an anchored
  popover, dialog or selection bar comes and goes with the gesture, so it may sit over the ink, never as a band.
- **One edge per surface**: a header closes on **one** bottom edge, across the full width, with one shadow
  and **no rim**; its rows (tools, document tabs) are separated by their own content, not by a line. A rim
  and an inner hairline are tidy on a light scheme, but over the true-black one they read as three grey
  rules across the window — one above the header, one through it, one below — so the slab's fill and its
  shadow are the whole of its edge (removed 2026-10-13; `docs/chrome.md` owns the note). Two nested slabs
  with a rim each still read as two bars, and a row that stops short of the full width still leaves a notch
  of bare desk inside the header's own edge — both were measured on the device before they were fixed.

### 2. Modern typography and line-art iconography
- **Typography**: Shift from stock Roboto to a modern, geometric neutral typeface (**Inter** or **Geist**),
  employing refined tabular figures for mathematical coordinates and zoom indicators.
- **Monoline Icons**: Replace bulky filled Material icons with crisp, modern $1.5\text{ dp}$ monoline vector
  iconography (consistent with Lucide / SF Symbols), reducing visual heaviness.

### 3. Pure dark mode depth (True blacks)
- Replaces Material 3's tinted dark surfaces with **true OLED black (`#000000`)** canvas backing and sleek,
  neutral charcoal layers (`#121212`, `#1E1E1E`, `#252528` `NeutralContainerDark` for submenus/dialogs, and `#38383E` `ActiveSlotDark` for tool selections).
- Hairline borders use neutral dividers (`#383838`, `#262626`) for clean layer separation without visual clutter.
- Accents use vibrant, luminescent micro-highlights (`BlueDark = #60A5FA` in dark mode, `Blue = #2563EB` in light mode) rather than pervasive pastel color washes or legacy purple.
- **Dynamic theming disabled by default**: `dynamicColor` is turned off by default (`AppSettings.dynamicColor = false`, `XoppTheme(dynamicColor = false)`) to prevent Android wallpaper palettes from turning menus, chips, and tool indicators periwinkle/blue. In addition, **both** `dynamicDarkColorScheme` and `dynamicLightColorScheme` are run through the same `pinnedDarkChrome()` / `pinnedLightChrome()` pin as the static schemes (`Theme.kt`), so a wallpaper palette can only reach the error reds — never a container.
- **Chromatic consistency across toolbars — and their pop-ups**: the background under the main toolbar rail and the secondary toolbar top bar must not disagree. Both `EditorTopBar` and `RailLane` wrap in continuous full-span `Surface(color = glassColor, shadowElevation = 4.dp)` bands over the unified true OLED black backdrop (`rememberChromeColor()`), while floating toolbars share identical frosted glass fills (`rgba(22, 22, 24, 0.85)` in dark mode) without conflicting backing layers (`unify-ui-color-palette-and-semantic-theme-contract`). Bars read that fill from the one function `rememberGlassSurfaceColor()` (`ChromeColors.kt`) and **everything that floats** from `rememberFloatingSurfaceColor()` — the *same* colour, with the see-through turned down to the `floatingPanelAlpha` token (**0.88**) — so a menu cannot be a different material from the bar it hangs from: it used to paint `surfaceContainer` while the bars painted the glass, which in the light theme left the pop-up on Material 3's baseline **lavender** `#F3EDF7` beside a near-white bar. The alpha is the **most see-through the ink allows**, measured rather than chosen: a bar carries one glyph, a floating surface carries rows of text over the page, and at the bars' own 0.85 the dark theme's secondary ink came out at **4.41:1** over a white sheet — under the 4.5:1 WCAG AA floor for body text. The half-step to 0.93 bought that back by making the panel a slab (at 7%, nothing shows through, and over the true-black desk a charcoal panel at 0.93 and an opaque one are the same picture); 0.88 clears the floor in both themes with room — **4.90:1** for the dark theme's secondary ink over a white sheet, **4.68:1** for the light theme's over black ink — so it is as far as the fill can go without trading legibility for frost. `ColorContractGuardTest` recomputes both ratios over both extremes of backdrop in both themes, so the material cannot be made more see-through without a failing build.
- **One floating material, one floating corner — the glassmorphic mandate for all dialogs & popups**: *floating* means everything the chrome puts **over** the page for a moment rather than in a band beside it — the pop-ups and submenus (`ToolbarMenu`), the action bars that come up with a selection and with the table/spline/PDF-text/region tools (`EditorActionBars`), the STEM editors' chrome, the page/zoom/scroll badges, system notifications/toasts (`GlassToastHost`), and all dialogs (`FloatingDialog`, `OnboardingDialog`, `KeyDetectionDialog`, `AudioOverwriteConsentDialog`). They all read that one fill (`rememberFloatingSurfaceColor()`), wear the family's hairline rim (`Modifier.floatingRim()`), and are cut at `panelCornerRadiusDp` (**20dp**). The rim itself is a **spec, not a drawing done twice**: the stroke width, the two gradient alphas and the feathered inner ring's width and alpha are `DesignTokens.RIM_*` (`rimWidthDp`, `rimAlphaTop`, `rimAlphaBottom`, `rimInnerWidthScale`, `rimInnerAlphaScale` in the block below), so a shell that draws its own edge reads the same numbers — the desktop wearing that edge is its glass-parity work, not a second opinion about how bright a rim should be. **Strict rule**: No UI element (dialog, pop-up, submenu, toast, or action bar) may use bare opaque Material cards or standard system `Toast`s — everything must strictly use `FloatingDialog` / `FloatingSurface` / `GlassToastHost` to maintain visual coherence. The STEM editors and system confirmation dialogs use `FloatingDialog` (`theme/FloatingGlass.kt`), which overrides `surface`/`surfaceTint` inside the dialog's scope and draws the family's rim on the dialog's `Surface`.
- **One material, drawn in Kotlin — no optical shaders**: floating chrome is a tinted fill plus a shaded hairline rim, painted by `floatingRim()` / `drawFloatingRim()` in `theme/FloatingGlass.kt`. The AGSL specular-rim and lens-bevel shaders that once added a curvature gleam and edge refraction are **removed**: they drew a second, light-direction-dependent edge on API 33+ only, so the same panel had one edge on a new device and another on an older one, and their switch was one more thing to explain. The rim is now the same pixels on every device.
- **The spring motion is not a preference**: opening and closing animations (`liquidGlassSpringAnimation` and `DialogPresence`) are the app's own motion language, so they are always on — the *Liquid glass animations* and *Liquid glass optical shaders* switches are gone from **Settings → Interface → Appearance**.
- **One neutral ramp per theme, nothing inherited**: a scheme that names a few roles inherits Material 3's **violet-tinted** baseline for the rest (`surfaceContainer` `#F3EDF7`, `primaryContainer` `#EADDFF`, `secondaryContainer` `#E8DEF8`). Both schemes are therefore built by naming their whole container/outline ramp (`pinnedLightChrome()`/`pinnedDarkChrome()`), and `ColorContractGuardTest` sweeps every one of those roles for a violet wash instead of spot-checking a handful.
- Paper, ruling, and imported PDF backgrounds retain their authentic document data colors (`docs/pages-and-paper.md`), surrounded cleanly by the OLED void.

---

## One language: English on every surface

**Every string the app shows is English** — the Android editor's chrome, dialogs, toasts and error copy,
and the desktop preview's window. The desktop is the same product as the tablet's, not a translated
companion: a second language in the tree is a second place for one message to drift out of date, and a
user who opens the same notebook on a PC and a tablet must not meet two voices for the same action.

This is a rule about **copy**, not about code comments, commit messages or these docs. Two consequences
worth stating plainly:

- **New user-facing text is written in English, in the same commit as the feature** — a tooltip, a
  status-bar word, a dialog title, an error notice. Italian (or any other language) reaching a screen
  through a literal is a bug against this rule, not a stylistic preference.
- **Error copy names what happened and what to do**, in that same voice, and never shows an internal
  identifier — a class name, a synthetic lambda, an exception's `toString()` — where a person is looking.
  The desktop's failed-open card is the worked example (`docs/desktop-documents.md`): the same event
  reported as `NoClassDefFoundError: …$openMobix$1` read as a corrupt notebook, and reads as "the
  preview has fallen behind" once it is said in English and about the right subject.

English literals are the contract for both shells: strings live in the code that shows them, in one
language, and there is no localisation catalogue in the tree.

---

## Mobile and Tablet ergonomics

```text
Tablet Portrait / Landscape:   - **The menu's action rows are glass tiles, and the PDF actions wear the document red**: *New / Open / Save / Save As* keep the icon-and-label quick actions, while *Import PDF*, *Export PDF*, *Page as PNG* and *Page as SVG* are **glass tiles** (`GlassActionButton`) instead of outlined Material buttons lying flat on the glass — a **miniature of the toolbars' capsule**, built from the recipe the dock and the rail are: the dock's own glass fill (`rememberGlassSurfaceColor()`), a small cast shadow (the bar carries 6 dp, a tile 3 dp), a sheen brightest along the lit top edge, and the family's hairline rim (`Modifier.floatingRim`). The sheen is what lifts the tile a shade *above* the menu's glass instead of letting the equal-alpha fill read as a recess, so a menu row looks like chrome the app already has, only smaller. The same pane (`GlassPane`) is what the **pen presets** wear, so the presets are glass chips rather than Material `FilterChip`s — the live preset written in the accent and washed with it. The menu's three parts are parted by a **feathered hairline** (`MenuRule`): a Material divider's outline is darker than the glass it sits on and vanished into it. The *Pen* heading and its glyph, the pen-parameters button and the *Settings* gear are in the chrome's own ink (`rememberChromeInk`) — white on the dark glass, the surface ink on the light one.

**`rememberChromeInk` is the chrome's ink, and a bare `Color.White` is a bug.** Everything drawn *on the glass* — the rail's tools, the figures' dock, the search and undo/redo pair, the overflow menu's glyphs and headings, the menu chevrons, the rail's size slot, the guide picker — takes that one function, because the light theme's glass is white: hard-coded white icons made the whole chrome iconless the moment a user switched to the light theme. Accent fills (a primary button, the recording capsule, a coloured chip) keep `onPrimary`/white, since those fills are dark in both themes. *Pen diagnostics* stays in the secondary grey: it is a switch, not a heading, and it carries no horizontal padding of its own so its glyph sits on the same left edge as the heading's above it. The tint is the only difference between the rows: the two PDF actions are inked in `DocumentRed` (`#E53935`, the same red as the recording dot) **without any red outline**, and the two page exports in the primary accent, so the PDF row still reads as the PDF row at a glance.
   - Rail on one vertical edge, chosen once in settings (AppSettings.toolbarPosition, Left / Right):
  * a band in its own lane beside the canvas, never a bar over the page;
  * comfortable palm clearance for left- and right-handed stylus users.
- Compact layout: the same tools as a horizontal band along the bottom edge, above the nav-bar inset.
  There is no drag-to-dock and no top/bottom docking: the top band is the header's, and Compact is what
  "toolbar along the bottom" means.
- Anchored Micro-Inspectors: Living entity editors open beside the element rather than heavy full-screen dialogs
  (transient, so they may sit over the page): the plot inspector anchors next to its selection, while the table
  uses a **fixed editor dialog** (`docs/mobix-elements.md`) — one panel with every cell editable, because an
  anchored one-cell strip fought the keyboard.
```

---

## Mobile and Tablet ergonomics: free-hand interaction & layout options

During stylus note-taking, the user's dominant hand is occupied holding the pen against the screen.
To maximize drafting speed, the modern UI embraces **bimanual tablet ergonomics**: primary controls
(undo, redo, tool switching, favorites) are positioned where the **non-dominant "free hand"** can tap
them with a thumb or index finger without lifting or moving the pen.

```text
┌────────────────────────────────────────────────────────────────────────┐
│ [Non-Dominant Free Hand]                       [Dominant Pen Hand]     │
│   Thumb taps floating controls                   Draws continuous ink  │
│   (Undo, Eraser toggle, f(x))                    on the canvas         │
└────────────────────────────────────────────────────────────────────────┘
```

There is **one** layout, and each bar has one job (`docs/chrome.md`):

### The rail: writing tools
- **Main Toolbar (Left / Right)**: the vertical glass capsule in a lane of its own along the edge (left or right per user handedness, a band and never a bar over the page), containing the primary handwriting and drawing tools (pen, highlighter, eraser, pan, select, text, pictures, vertical space, favourites, guides, layers, pages, zoom, background). Those are the only two dockings: the `TOP`/`BOTTOM` centre positions are retired (2026-10-13).

### The header: STEM tools
- **Secondary STEM Toolbar (header)**: the glass dock in the header's tools row, containing all mathematical and scientific tools (shapes, geometry, function graph, tables, circuits, guides), with the document tabs as the row under it — one slab, one closing edge. It is their **only** home: duplicating them on the rail cost a second copy of the same buttons, and the audio, play-object and shape-recognition slots left the rail for the audio capsule and the Settings toggle respectively.

### The pen-tip HUD: the barrel button's ring
- **What it is.** With Settings → Stylus → Barrel button → *While held* set to **Hud** — or its **Double-click** set to *Radial HUD* — pressing the stylus button with the tip off the glass opens a ring centred on the nib (the last `hoverX`/`hoverY`), not a palette parked on screen: nothing exists until the button is pressed.
- **Two ways to close it, because there are two ways to open it** (`HudTrigger`). *While held* is a **hover gesture**: letting the button go commits the slot under the pen, and a tip that lands cancels the ring without writing a stroke. *Double-click* has no held button to release — the release that follows the opening click is the release of that same click — so it opens the ring as a **picker**: it stays on screen, through the pen leaving hover range if need be, until a slot is **tapped**; a tap elsewhere inside the ring does nothing, a tap well outside it closes the ring without changing anything, and another double-click closes it too. The double-click binding exists so a pen that keeps the held button for erasing (the default) can still reach the ring.
- **What it holds**, clockwise from the top: the user's palette swatches in their stored order (the editable custom HEX colour last — its first one-flick home), then the three pen widths, then the three writing tools (pen, highlighter, eraser). The rail and the STEM dock keep the rest; the ring is a writing control, and the 33 files of the old NeXopp palette are the weight it exists *not* to repeat.
- **How a flick commits.** The pen crosses the ring; the slot under it takes the accent ring and the steel plate, with a haptic tick at each boundary; the release — or, for the picker, the tap — commits it through the same `applyToolPreset` / `activateTool` entries the rail uses, so a ring choice can never mean something the user could not have set by hand. Releasing (while held) inside the 32 dp dead zone or off the slot band commits nothing, and a tip that lands then cancels the ring without writing a stroke.
- **Material.** A floating surface, round: the floating fill at `floatingPanelAlpha`, the 1 dp hairline rim in the chrome ink, and 40 dp slots (`COMPACT_BUTTON_SIZE_DP`) on a 116 dp radius. Faces are read without labels — a swatch is its colour, a width is a dot of that width, a tool is its `ToolGlyphCatalogue` glyph drawn through the canvas adapter.

---

The desktop window's layout — panes, strip placement, shortcuts — is [desktop-ui-layout.md](desktop-ui-layout.md)'s.

---

## Design token specification

The numbers live in **`com.mobixournal.design.DesignTokens`** (plain Kotlin, `mobix-core`) — one home for both shells. Each shell wraps them in the Compose types it draws with: `ModernTheme` for the Android chrome and `DesktopTheme` for the desktop window, and the two guards `ModernThemeTest` / `DesktopThemeTest` re-read this JSON block, so a token cannot move in code without moving here. A chrome file may not paint a colour of its own: `DesktopThemeTest` sweeps the desktop's chrome sources for `Color(0x…)` literals, the same way `ColorContractGuardTest` sweeps the Android chrome.

```json
{
  "theme": {
    "surface": {
      "glassDark": "rgba(22, 22, 24, 0.85)",
      "glassLight": "rgba(255, 255, 255, 0.88)",
      "hairlineBorder": "rgba(255, 255, 255, 0.12)",
      "floatingPanelAlpha": 0.88,
      "panelCornerRadiusDp": 20,
      "blurRadiusDp": 24,
      "rimWidthDp": 1.0,
      "rimAlphaTop": 0.12,
      "rimAlphaBottom": 0.04,
      "rimInnerWidthScale": 1.5,
      "rimInnerAlphaScale": 0.35,
      "bandShadowDp": 4,
      "capsuleShadowDp": 6,
      "chromeInkDark": "#FFFFFF",
      "hoverSlotAlpha": 0.45
    },
    "geometry": {
      "capsuleCornerRadiusDp": 16,
      "compactButtonSizeDp": 40,
      "hairlineStrokeWidthDp": 1.0,
      "railSlotCornerRadiusDp": 10,
      "railCapsuleWidthDp": 52,
      "railLanePaddingDp": 6,
      "railLaneWidthDp": 64,
      "railOuterPaddingDp": 8,
      "headerControlSizeDp": 36,
      "toolGlyphGrid": 24,
      "toolGlyphStrokeUnits": 1.5
    },
    "animation": {
      "springStiffness": 380,
      "springDamping": 28,
      "fadeTransitionMs": 150,
      "glassMotion": { "startScale": 0.82, "openDamping": 0.74, "openStiffness": 550, "fadeInMs": 110, "closeDamping": 0.85, "closeStiffness": 850, "fadeOutMs": 170, "exitHoldMs": 230 }
    }
  }
}
```

---

## Shell details stated outright

- **The rail is a capsule in a lane exactly as wide as it** — 52 dp of glass and 6 dp of air each side, the
  height beginning below the **whole** header. Its gap and padding budget (`RailSizing.kt`) keeps every
  slot whole and on screen without a scroll. The rail is not draggable and docks to a vertical edge only,
  and the header carries the document tabs as its second row (the principle above).
- **The canvas takes the rectangle the chrome leaves** — the rail's lane down one edge and the header band
  above — so the page is never underneath the glass and the whole visible sheet stays writable. See
  [`chrome-frame.md`](chrome-frame.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest).
- **Settings opens with the app's own spring**: the settings screen is an overlay on the still-composed
  editor, and it enters with `liquidGlassSpringAnimation` like every menu and inspector.
- **Every settings row is identified by a glyph**: each `SettingsArea` and `SettingsSection` carries its own
  monoline icon in a tinted disc at the head of its row (`SectionRow`), and the same glyph follows the row
  into the search results. The glyph is part of the enum, so a new section cannot be added without one.
- **Dialogs leave the way they arrived**: a dialog is its own window, so Compose tore it down the instant its
  flag cleared — the entrance spring played in full and the panel vanished mid-frame. `DialogPresence(visible)`
  holds the window composed for `FLOATING_EXIT_MS` (230 ms) while the surface runs the entrance animation
  backwards, and `rememberClosingArgument` keeps the dialog's subject when the flag was a nullable one. Every floating
  dialog — the editor's, the pages menu's, the settings pickers' — is gated that way.
- **The plot editor anchors to its element**: editing an existing `PlotElement` opens `PlotInspectorAnchor`
  beside the selection (committed through `applyPlotEdit`, unedited fields kept); a new plot uses the modal
  `AdvancedFunctionGraphDialog`, whose placement arithmetic is viewport-centred.
