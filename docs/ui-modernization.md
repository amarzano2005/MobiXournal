# Modern UI design system: lightweight, content-first interface

> Read when redesigning or modernizing the mobile and desktop graphical interface, transitioning away from heavy Material Design paradigms, implementing floating capsules, or aligning cross-platform aesthetics.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: moving beyond heavy Material Design

The current UI (documented in [chrome.md](chrome.md)) relies heavily on Google's standard Material 3
conventions (filled container pills, colored surface tints, heavy drop shadows, and full-screen dialogs).
While functional on phones, this aesthetic creates visual clutter on tablets and desktop workstations:
it feels like an "Android OS utility" rather than a focused, sleek creative tool (like Figma, Linear,
Concepts, or Apple Freeform).

The **Modern UI Design System** establishes a clean, lightweight, content-first interface shared across
mobile and desktop.

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
- **Visual Weight**: Chrome elements use thin hairline borders ($1\text{ px}$ with subtle $10\text{--}15\%$ alpha)
  instead of chunky drop shadows or filled cards.
- **Glass in bands, never over the page**: the toolbars are translucent frosted capsules (backdrop blur at
  $85\text{--}90\%$ opacity, $1\text{ px}$ hairline at $12\%$ alpha) that live in **bands of their own** —
  the header across the top and the rail's lane down one edge — so
  what the glass reveals is the **desk** the page lies on, not the page. A translucent bar *on* the sheet
  hides the ink under it and swallows the stylus there, and the strip it covers is the top of the sheet,
  where a hand reaches first (the layout is [chrome.md](chrome.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest)).
  **The rule, and its one carve-out** — *bands never lie on the page; transient surfaces may*: a radial HUD,
  an anchored popover, a dialog or a selection bar comes and goes with the gesture, so it may sit over the
  ink, and none of them is ever a permanent band.
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
  The desktop's failed-open card is the worked example (`docs/desktop-workstation.md`): the same event
  reported as `NoClassDefFoundError: …$openMobix$1` read as a corrupt notebook, and reads as "the
  preview has fallen behind" once it is said in English and about the right subject.

Localising into other languages is a future product decision (string resources, one catalogue per
language, a setting) and nothing here forecloses it. Until that decision is taken, English literals are
the contract for both shells.

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

---

## Desktop workstation UI architecture (Windows 11)

On desktop workstations paired with keyboards and graphics tablets (see [desktop-workstation.md](desktop-workstation.md)),
scaling up a mobile touch interface creates clumsy, oversized controls. Desktop MobiXournal adopts a
**professional bimanual workstation layout** (inspired by modern suites like Figma, VS Code, and Blender):

```text
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ [≡] [Notebook 1 ×] [Notebook 2 ×] [+]          [Search 🔍]      [Share] [Settings ⚙] │  (Header Bar)
├─────────┬─────────────────────────────────────────────────────────────────┬───────────┤
│ Sidebar │                           CANVAS                                │ Inspector │
│ Pages   │                                                                 │ Properties│
│ Layers  │                 [Radial HUD Palette (At Pen Tip)]               │ Formula / │
│ Outline │                                                                 │ Table /   │
│ [Ctrl+B]│                                                                 │ Stroke    │
└─────────┴─────────────────────────────────────────────────────────────────┴───────────┘
```

### 1. Window layout & workspace panes
- **Slim Native Header**: Displays tabbed open notebooks, document path breadcrumbs, and native OS window
  decorations.
- **Collapsible Navigation Sidebar (`Ctrl+B`)**: Slide-out panel for page grid thumbnails, layer manager,
  PDF bookmarks outline, and indexed search results. Can be collapsed with one hotkey for distraction-free canvas.
- **Right Property Inspector (Non-Modal Editing)**: When a `PlotElement`, `TableElement`, or shape is selected,
  its parameters (formula string, intervals, cell borders, stroke styles) appear in the right panel without
  popping up modal dialogs over the canvas.

**Desktop decisions (2026-10-21):**
- **The chrome is the app's own, on a true-black desk — not the window manager's.** The header, the sidebar
  and the inspector are the same frosted glass the mobile chrome uses, and the desk behind the page is
  `#000000`; platform materials (Windows 11 Mica) are **not** used. Mica replaces the
  app's material with a wallpaper-tinted one, so the desktop window would stop matching the phone and
  its contrast would change with the user's wallpaper — the opposite of the pinned ramps above.
- **Both tool placements ship**: the vertical rail is the default, the horizontal ribbon is the
  alternative for 16:9 monitors, and **Settings → Interface → Tool placement** chooses Rail / Ribbon /
  Both. Whichever is on, it is a band in its own lane beside the canvas, never over the page. Until the
  settings window exists (`desktop-settings-and-status-ui`), the same entry is switched from a **Tool
  placement** control in the header: both write one value, so they cannot disagree.

**The shell as built (2026-10-22).** `desktop/` has the header, tab strip, sidebar, workspace, status
bar and both tool strips of the drawing above; [`desktop-workstation.md`](desktop-workstation.md) owns the
shell's inventory. From **this** spoke it takes four rules, and the parity work of 2026-10-22 is what
put them all in the window:

- **A band is chrome, a capsule is an object.** The header, status bar, sidebar and the strips' lanes
take the glass fill with **one 4 dp cast shadow** and a vertical sheen, and **no rim** (the rule above).
The rail is a **52 dp capsule in a 64 dp lane** — the tablet's own geometry, 6 dp of side air and 8 dp
above and below — and the ribbon a 48 dp capsule in a 56 dp band; each takes its **own 6 dp lift**, the
family's rim and the sheen. A header control is the same capsule at 36 dp. Without those two lifts the
bands were a flat fill, because over a true-black desk the glass' alpha is invisible: the shadow and the
sheen are the only depth the window has (`Modifier.bandSurface()` / `glassCapsule()`, `GlassEdge.kt`).
- **One icon set.** The fifteen tool glyphs come from `ToolGlyphCatalogue` (`mobix-core`), plain geometry
on the 24-unit grid that the tablet turns into an `ImageVector` and the window draws into a `DrawScope`;
the window no longer keeps a second, hand-drawn set. The picked slot is a 10 dp squircle in the steel
`#38383E`. Floating surfaces keep the drawn rim over the 0.88 fill, as documented above.
- **No motion of its own**: the windows' surfaces arrive on `DesignTokens.GlassMotion` too. The `Popup`
flyouts still appear instantly — a desktop popup has no composition to hold open once its flag clears.
- **The colours and corners are the tokens**: 16 dp tab capsules, the 20 dp panel corner, a 40 dp header,
a 24 dp status bar, Inter with tabular figures, dark-first. `DesktopThemeTest` re-reads the JSON block
above, `ChromeMaterialRenderTest` proves the lift, the sheen and the rim as pixels, and
`ToolGlyphCatalogueTest` pins the geometry both shells read.

Picking a colour writes it straight to the settings file (`ink.colour`, `ink.width`), and the palette is
`palette.colours` through `PenPalette.sanitised`, so a hand-edited file cannot put a transparent colour,
a duplicate or an empty palette in front of the picker. The row ends in the **editable custom slot** and
the **add-colour** swatch, both opening the window's own colour panel (saturation/value square, hue
slider, two-way `#RRGGBB` with a live preview, Set/Cancel) in the floating glass rather than a Material
dialog; an invalid hex keeps the last valid colour and leaves Set out of reach. The placement choice is
persisted (`interface.toolPlacement`, Rail by default) and the strips appear once a document is open.
The right-hand inspector is the part still missing.

Everything the chrome does can also be done from the keyboard:

| Key | Action |
|---|---|
| `Ctrl + T`, `+` | open a document; a file already open activates its tab instead of opening twice |
| `Ctrl + W`, `×` | close the tab, activating its neighbour |
| `Ctrl + Tab` / `Ctrl + Shift + Tab` | next / previous document |
| `Ctrl + B`, `≡` | show or hide the sidebar |
| `Ctrl + =`, `Ctrl + NumPad +` | zoom in — see the layout note below |
| `Ctrl + -`, `Ctrl + NumPad -` | zoom out |
| `Ctrl + 0` / `Ctrl + 1` | fit the page / fit the width |
| `Page Up` / `Page Down` | previous / next page |
| `Space` (held) | pan with the primary button; the middle button always pans |
| `B` `H` `E` `V` `S` `X` `L` `A` `R` `C` `P` `M` `T` `K` | pick that tool — §3's shortcut table is the list |
| `Shift + X` | the axes tool; a bare `X` is the text box |
| `1` `2` `3` | pick favourite colour 1 / 2 / 3 |
| `Alt + 1`…`Alt + 3` | make the colour in use that favourite |
| `]` / `[` | wider / thinner — one step through `S`, `M`, `L`, stopping at the ends |

Two decisions taken while building it:
- **The tab and view model is Compose snapshot state, not plain fields.** A `mutableListOf` and ordinary
  `var`s are invisible to Compose: the first run opened a document while the strip stayed empty (`≡`
  and `+`, no tab) and a zoom changed a number nothing redrew. One state, read by the chrome, is the
  only version that cannot disagree with itself — and it still runs in a plain JVM test, because
  snapshot state works outside a composition.
- **Zoom-in is bound to a physical key, not to the `=` character.** On an Italian keyboard `=` *is*
  `Shift + 0`, and AWT delivers no key event for it at all — `Ctrl + =` simply never arrives. The
  keypad's `+` is the layout-independent binding, `Ctrl + shift + 0` is accepted for the layouts where
  `=` shares the `0` key, and `Ctrl` + wheel (zoom at the cursor) is the one that always works.

### 2. Visual tool placement and docking on Desktop
Unlike the header bar (which handles documents) and the sidebar (which handles navigation), the **drawing and STEM tools**
remain visually accessible on screen for direct mouse or pen clicking:

```text
Desktop Tool Placements:
Option A: Floating Top/Bottom Ribbon (Figma/Freeform style)
┌────────────────────────────────────────────────────────────────────────────────────────┐
│  [✏️ Pen (B)] [🖍️ Highl (H)] [🧹 Eraser (E)] [✂️ Sel (V)] │ [📈 f(x) (M)] [📐 Geo] [📊 Tab (T)] [⚡ Ckt]  │
└────────────────────────────────────────────────────────────────────────────────────────┘

Option B: Widescreen Left Tool Palette (Photoshop/Illustrator style)
┌───────────┐
│ [✏️ (B)]   │
│ [🖍️ (H)]   │  - Compact 42px vertical strip docked to the left canvas edge.
│ [🧹 (E)]   │  - Optimized for 16:9 and 21:9 monitors: leaves full vertical height for A4 sheets.
│ [✂️ (V)]   │
│ ───────── │
│ [📈 (M)]   │  - STEM tools permanently visible at 1-click.
│ [📐 Geo]  │
│ [📊 (T)]   │
│ [⚡ Ckt]   │
│ ───────── │
│ [🔴 Color]│
└───────────┘
```

- **Permanent 1-Click STEM Access**: The STEM group ($f(x)$, shapes, tables, circuits) is always visible
  in the tool strip, maintaining immediate 1-click execution without buried submenus.
- **Visual Hover Tooltips with Hotkeys**: Hovering the pen or cursor over any button reveals a subtle
  pill tooltip displaying the tool name and single-letter keyboard shortcut (e.g. `Funzione f(x) [M]`).

### 3. Universal shortcuts across all tools (Mobile & Desktop)
Every single tool and editing action in MobiXournal carries an assigned shortcut accelerator, providing
seamless parity whether driven by an external tablet keyboard on Android or a workstation on PC:

| Tool / Action Category | Tool Name | Default Keyboard Shortcut | Mobile / Stylus Gesture Equivalent |
|---|---|---|---|
| **Core Inking** | Pen / Brush | `B` | Single tap on pen / Primary tool slot |
| | Highlighter | `H` | Secondary tool slot |
| | Eraser | `E` | Stylus rear eraser tip or barrel button 1 |
| | Lasso Select | `V` | Barrel button hold + drag |
| | Marquee Rect Select | `S` | Selection mode toggle |
| | Text Box | `X` | Screen double-tap with finger |
| | Pan / Hand Tool | `Space` (held) or `H` | Two-finger scroll / drag |
| **Geometry & Figures** | Straight Line / Ruler | `L` | Hold-to-straighten dwell (300 ms) |
| | Arrow | `A` | Arrow shape toggle |
| | Rectangle | `R` | Shape dwell |
| | Circle / Ellipse | `C` | Shape dwell |
| | Free Spline | `P` | Spline tool slot |
| **STEM & Advanced** | Cartesian Coordinate Axes | `Shift + X` | 1-tap STEM dock button |
| | Function Plot $f(x)$ | `M` | 1-tap STEM dock button |
| | Dynamic Table | `T` | 1-tap STEM dock button |
| | Circuit Components | `K` | 1-tap STEM dock button |
| | Audio Tap / Replay | `Y` | Audio tool slot |
| **Styling & Navigation** | Favorite Colors 1 / 2 / 3 | `1`, `2`, `3` | Tap on color swatch / Rail slot |
| | Stroke Width Up / Down | `]`, `[` | Rotary Touch Ring / Dial Wheel |
| | Zoom In / Out | `Ctrl + =`, `Ctrl + -` | Pinch gesture / Dial Wheel |
| | Undo / Redo | `Ctrl + Z`, `Ctrl + Shift + Z` | Two-finger tap / Three-finger tap |

- **Universal Shortcut & Individual Color Remapping**: All keybindings, tools, and **individual color palette slots**
  are fully remappable via **Settings → Input & Shortcuts**. Users can bind any keyboard shortcut, stylus gesture,
  or dial action to directly select a specific user-defined custom HEX color (e.g. `Alt+1` for Pure Black `#000000`,
  `Alt+2` for Crimson Red `#D32F2F`, `Alt+3` for Royal Blue `#1565C0`), not just the 3 factory favorites.
- **Pen-Tip Radial HUD Menu (Pie Menu)**: Clicking the pen barrel button summons an instant circular HUD
  directly under the pen nib, selecting colors, widths, or tools in $< 0.1\text{ s}$ without moving the hand.
  **Decision (2026-10-13, recorded in this, its owning spoke): the HUD is new work, not NeXopp's palette
  returning.** The palette [codebase.md](codebase.md) records as deleted — 33 files: its renderer, tap
  layer, haptics, action and catalog code, and a `Palette` settings section of its own — stays deleted.
  What comes back is transient and smaller. Nothing is on screen until the barrel button is pressed; the
  ring is anchored at the nib and dismissed on release (a second press or a cancelled gesture closes it
  without committing); and it has no settings section, because the keybindings and the palette slots
  already live under **Settings → Input & Shortcuts**. The ring holds the user's own swatches **first** —
  the one-flick home a custom HEX slot never had — then the pen widths, then the few tools with no other
  1-tap place; the rail and the STEM dock keep the rest, which is the weight that made the old palette
  heavy on screen. It draws through `ModernTheme` and the canvas chrome (`render/CanvasChrome.kt`), not a
  Compose dialog, so the menu sits under the pen without stealing focus. Tracked in `TODO.toml` as the
  three *pen-tip radial HUD* tasks (summon, draw, flick-and-commit).

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
      "sheenAlphaTop": 0.10,
      "sheenAlphaBottom": 0.02
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

## Phased migration plan

1. **Phase 1: Design Tokens, Typography & Monoline Iconography**:
   - Extract UI colors and dimensions into a modern token provider (`ModernTheme`).
   - Bundle clean geometric typography (**Inter**) in `res/font/` with tabular figures (`tnum`) via `ModernTypography` (`ui/theme/Type.kt`).
   - Swap stock filled Material icons for consistent $1.5\text{ dp}$ monoline vector assets across toolbars, rails, and popups.
2. **Phase 2: floating glass capsules, laid out in bands** (shipped, except where noted):
   - The rail and the header's docks are the frosted capsules the tokens describe — but they are laid out
     **beside** the canvas, in bands, not as a floating bar over it: the rail is not draggable and docks to
     a vertical edge only, and the header carries the document tabs as its second row (the principle above).
   - **Vertical rail capsule**: Translucent frosted capsule (`ModernTheme.glassDark` / `ModernTheme.glassLight` with ~85-88% opacity, 1px hairline border at 12% alpha, 16dp rounded corners, 40dp compact button slots, 52dp inner width + 6dp horizontal padding = 64dp total width), standing in a lane exactly that wide whose height begins below the **whole** header; its gap and padding budget (`RailSizing.kt`) is what keeps every slot whole and on screen without a scroll.
   - **Canvas beside the chrome**: the document canvas takes the rectangle the chrome leaves — the rail's lane down one edge and the header band above — so the page is never underneath the glass and the whole visible sheet stays writable. See [`chrome.md`](chrome.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest).
   - **Settings opens with the app's own spring**: the settings screen is an overlay on the still-composed editor, and it enters with `liquidGlassSpringAnimation` like every menu and inspector, instead of snapping in.
   - **Every settings row is identified by a glyph**: each `SettingsArea` and `SettingsSection` carries its own monoline icon, drawn in a tinted disc at the head of its row (`SectionRow`) — a list of twelve text rows is read at a glance, and the same glyph follows the row into the search results. The glyph is part of the enum, so a new section cannot be added without one.
   - **Dialogs leave the way they arrived**: a dialog is its own window, so Compose tore it down the instant its flag cleared — the entrance spring played in full and the panel vanished mid-frame. `DialogPresence(visible)` holds the window composed for `FLOATING_EXIT_MS` (230 ms) while the surface runs the animation backwards (same 0.82 scale as a `0.85/850` spring, a 170 ms fade, `LocalFloatingSurfaceClosing`), and `rememberClosingArgument` keeps the dialog's subject when the flag was a nullable one (`flag != null`). The hold is what the eye needs: at the first take, 130 ms, the panel left in about eight frames and read as no animation at all. Every floating dialog — the editor's, the pages menu's, the settings pickers' — is gated that way.
3. **Phase 3: Contextual Micro-Inspectors & Pen-Tip Radial HUD** (active in Step 2 `TODO.toml`):
   - **Chrome & system bar guards** (`guard-the-system-bars-as-one-transparent-contract`, `guard-the-chrome-bands-as-one-geometry-contract`): mechanical test suites pinning edge-to-edge transparency and layout band partitioning.
   - **Pen-Tip Radial HUD** (`summon-and-anchor-the-pen-tip-radial-hud`, `draw-the-radial-hud-as-glass-chrome`, `track-the-flick-and-commit-a-colour-width-or-tool`): barrel-button summoned pie menu rendered as frosted glass at the nib with flick-and-commit presets.
   - **Anchored micro-inspectors** (`anchored-flyout-inspector-for-the-function-plot-editor`): living entity editing dialogs (Function Plot, Table editor, Circuits, Parametric Shapes) transitioning into anchor-positioned micro-inspectors adjacent to the selection on canvas. The plot editor opens as `PlotInspectorAnchor` beside the selection when editing an existing `PlotElement`, committing through `applyPlotEdit` with full undo coverage and unedited field preservation; new plot creation continues to use modal `AdvancedFunctionGraphDialog` to preserve viewport-centered placement arithmetic.
4. **Phase 4: Desktop Shell Integration** (Step 3 roadmap):
   - Apply platform-native window styling (Mica / Vibrancy) in Compose Desktop.
