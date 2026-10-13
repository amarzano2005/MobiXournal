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
  the header across the top, the rail's lane down one edge, the dock's band at the bottom in Compact — so
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
  neutral charcoal layers (`#121212`, `#1E1E1E`, and container ladder up to `#333333`).
- Hairline borders use neutral dividers (`#383838`, `#262626`) for clean layer separation without visual clutter.
- Accents use vibrant, luminescent micro-highlights (`PurpleDark = #C084FC`) rather than pervasive pastel color washes.
- On Android 12+ with dynamic color active, `dynamicDarkColorScheme` overrides surface and background tokens with true OLED black and neutral charcoal, preserving dynamic primary accents while eliminating muddy wallpaper tints on app chrome.
- Paper, ruling, and imported PDF backgrounds retain their authentic document data colors (`docs/pages-and-paper.md`), surrounded cleanly by the OLED void.

---

## Mobile and Tablet ergonomics

```text
Tablet Portrait / Landscape:
- Rail on one vertical edge, chosen once in settings (AppSettings.toolbarPosition, Left / Right):
  * a band in its own lane beside the canvas, never a bar over the page;
  * comfortable palm clearance for left- and right-handed stylus users.
- Compact layout: the same tools as a horizontal band along the bottom edge, above the nav-bar inset.
  There is no drag-to-dock and no top/bottom docking: the top band is the header's, and Compact is what
  "toolbar along the bottom" means.
- Anchored Micro-Inspectors: Living entity editors (function plot inspector, table cell editing) open in
  anchored micro-inspectors directly adjacent to the element rather than heavy full-screen dialogs (transient,
  so they may sit over the page).
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

The system formally supports two ergonomic layout modes in settings (`ToolbarLayout`):

### Standard Layout Mode
- **Main Toolbar (Left / Right)**: the vertical glass capsule in a lane of its own along the edge (left or right per user handedness, a band and never a bar over the page), containing the primary handwriting and drawing tools (pen, highlighter, eraser, selection, favorites, layers, pages, zoom). Those are the only two dockings: the `TOP`/`BOTTOM` centre positions are retired (2026-10-13), since the header owns the top band and a bar along the bottom is what Compact means (`docs/chrome.md`).
- **Secondary STEM Toolbar (header)**: the glass dock in the header's tools row, containing all mathematical and scientific tools (shapes, geometry, function graph, tables, circuits, guides), with the document tabs as the row under it — one slab, one closing edge.

### Compact Layout Mode
- **Unified Bottom Toolbar**: a horizontal glass band along the bottom edge, above the navigation-bar inset, combining the primary drawing tools and the 4 compact 1-tap STEM slots (`[f(x)]`, `[Geometry]`, `[Table]`, `[Circuit]`).
- **Clean Header**: the secondary toolbar is gone from the header, which carries the document tabs instead — the canvas keeps everything the header does not spend.

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
   - **Canvas beside the chrome**: the document canvas takes the rectangle the chrome leaves — the rail's lane down one edge, the header band above, the dock's band at the bottom in Compact — so the page is never underneath the glass and the whole visible sheet stays writable. See [`chrome.md`](chrome.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest).
   - **1-tap STEM rail section**: Direct 1-tap buttons for Plot (`f(x)`), Geometry/Shapes, Table, and Audio with dedicated hairline dividers.
3. **Phase 3: Contextual Micro-Inspectors**:
   - Transition living entity editing dialogs (Function Plot, Table editor) into anchor-positioned micro-inspectors.
4. **Phase 4: Desktop Shell Integration**:
   - Apply platform-native window styling (Mica / Vibrancy) in Compose Desktop.
