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
- **One floating material, one floating corner — the glassmorphic mandate for all dialogs & popups**: *floating* means everything the chrome puts **over** the page for a moment rather than in a band beside it — the pop-ups and submenus (`ToolbarMenu`), the action bars that come up with a selection and with the table/spline/PDF-text/region tools (`EditorActionBars`), the STEM editors' chrome, the page/zoom/scroll badges, system notifications/toasts (`GlassToastHost`), and all dialogs (`FloatingDialog`, `OnboardingDialog`, `KeyDetectionDialog`, `AudioOverwriteConsentDialog`). They all read that one fill (`rememberFloatingSurfaceColor()`), wear the family's hairline rim (`Modifier.floatingRim()`), and are cut at `panelCornerRadiusDp` (**20dp**). **Strict rule**: No UI element (dialog, pop-up, submenu, toast, or action bar) may use bare opaque Material cards or standard system `Toast`s — everything must strictly use `FloatingDialog` / `FloatingSurface` / `GlassToastHost` to maintain visual coherence. The STEM editors and system confirmation dialogs use `FloatingDialog` (`theme/FloatingGlass.kt`), which overrides `surface`/`surfaceTint` inside the dialog's scope and draws the family's rim on the dialog's `Surface`.
- **One material, drawn in Kotlin — no optical shaders**: floating chrome is a tinted fill plus a shaded hairline rim, painted by `floatingRim()` / `drawFloatingRim()` in `theme/FloatingGlass.kt`. The AGSL specular-rim and lens-bevel shaders that once added a curvature gleam and edge refraction are **removed**: they drew a second, light-direction-dependent edge on API 33+ only, so the same panel had one edge on a new device and another on an older one, and their switch was one more thing to explain. The rim is now the same pixels on every device.
- **The spring motion is not a preference**: opening and closing animations (`liquidGlassSpringAnimation` and `DialogPresence`) are the app's own motion language, so they are always on — the *Liquid glass animations* and *Liquid glass optical shaders* switches are gone from **Settings → Interface → Appearance**.
- **One neutral ramp per theme, nothing inherited**: a scheme that names a few roles inherits Material 3's **violet-tinted** baseline for the rest (`surfaceContainer` `#F3EDF7`, `primaryContainer` `#EADDFF`, `secondaryContainer` `#E8DEF8`). Both schemes are therefore built by naming their whole container/outline ramp (`pinnedLightChrome()`/`pinnedDarkChrome()`), and `ColorContractGuardTest` sweeps every one of those roles for a violet wash instead of spot-checking a handful.
- Paper, ruling, and imported PDF backgrounds retain their authentic document data colors (`docs/pages-and-paper.md`), surrounded cleanly by the OLED void.

---

## Mobile and Tablet ergonomics

```text
Tablet Portrait / Landscape:   - **The menu's action rows are glass tiles, and the PDF actions wear the document red**: *New / Open / Save / Save As* keep the icon-and-label quick actions, while *Import PDF*, *Export PDF*, *Page as PNG* and *Page as SVG* are **glass tiles** (`GlassActionButton`) instead of outlined Material buttons lying flat on the glass. A tile has **no fill of its own**: it lays only light over the menu's glass — a sheen brightest along the top edge and fading to nothing at the bottom — closed by the family's hairline rim (`Modifier.floatingRim`), so what shows through a tile is the same frosted backdrop showing through the menu. (An 85%-opaque fill was the first attempt and read as a grey button pasted on glass: two stacked panes, not one.) The tint is the only difference between the rows: the two PDF actions are inked in `DocumentRed` (`#E53935`, the same red as the recording dot) **without any red outline**, and the two page exports in the primary accent, so the PDF row still reads as the PDF row at a glance.
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

## Desktop workstation UI architecture (PC / Mac / Linux)

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
  decorations with seamless Windows 11 Mica / macOS Vibrancy materials.
- **Collapsible Navigation Sidebar (`Ctrl+B`)**: Slide-out panel for page grid thumbnails, layer manager,
  PDF bookmarks outline, and indexed search results. Can be collapsed with one hotkey for distraction-free canvas.
- **Right Property Inspector (Non-Modal Editing)**: When a `PlotElement`, `TableElement`, or shape is selected,
  its parameters (formula string, intervals, cell borders, stroke styles) appear in the right panel without
  popping up modal dialogs over the canvas.

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

The typed provider in the codebase is `com.mobixournal.ui.theme.ModernTheme` ([ModernTheme.kt](../app/src/main/java/com/mobixournal/ui/theme/ModernTheme.kt)), pinned by `ModernThemeTest` to fail the build on drift:

```json
{
  "theme": {
    "surface": {
      "glassDark": "rgba(22, 22, 24, 0.85)",
      "glassLight": "rgba(255, 255, 255, 0.88)",
      "hairlineBorder": "rgba(255, 255, 255, 0.12)",
      "floatingPanelAlpha": 0.88,
      "panelCornerRadiusDp": 20,
      "blurRadiusDp": 24
    },
    "geometry": {
      "capsuleCornerRadiusDp": 16,
      "compactButtonSizeDp": 40,
      "hairlineStrokeWidthDp": 1.0
    },
    "animation": {
      "springStiffness": 380,
      "springDamping": 28,
      "fadeTransitionMs": 150
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
