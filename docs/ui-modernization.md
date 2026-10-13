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
│ [≡] [Undo] [Redo]    (Floating Island: 1px Hairline Border, Blur)      │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │  [Pen] [Highlighter] [Eraser] [Shapes] [Math] | [Color] [Width]    │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│                                                                        │
│                         CANVAS (Content-First)                         │
│                   Unobtrusive, distraction-free space                  │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### 1. Content-first minimalism ("The canvas is the hero")
- **Visual Weight**: Chrome elements use thin hairline borders ($1\text{ px}$ with subtle $10\text{--}15\%$ alpha)
  instead of chunky drop shadows or filled cards.
- **Glassmorphic Islands**: Toolbars float as translucent frosted capsules (backdrop blur with $85\text{--}90\%$
  opacity), allowing the canvas ruling or PDF beneath to remain subtly visible without losing legibility.
- **Auto-Dimming / Zen Inking Mode**: While the stylus is actively drawing or writing, floating toolbars fade to
  $30\%$ opacity or retract into a thin pill, maximizing focus on the handwriting.

### 2. Modern typography and line-art iconography
- **Typography**: Shift from stock Roboto to a modern, geometric neutral typeface (**Inter** or **Geist**),
  employing refined tabular figures for mathematical coordinates and zoom indicators.
- **Monoline Icons**: Replace bulky filled Material icons with crisp, modern $1.5\text{ dp}$ monoline vector
  iconography (consistent with Lucide / SF Symbols), reducing visual heaviness.

### 3. Pure dark mode depth (True blacks)
- Replaces Material 3's tinted dark surfaces with **true OLED black (`#000000`)** canvas backing and sleek,
  neutral charcoal layers (`#121212`, `#1E1E1E`).
- Accents use vibrant, luminescent micro-highlights rather than pervasive pastel color washes.

---

## Mobile and Tablet ergonomics

```text
Tablet Portrait / Landscape:
- Dockable Floating Capsule: User can drag the toolbar pill to:
  * Top center (Traditional desktop)
  * Bottom center (Thumb-friendly mobile reaching)
  * Left / Right vertical spine (Comfortable palm clearance for left/right-handed stylus users)
- Compact Flyout Inspectors: Color palettes and tip sizes open in sleek, anchored popover bubbles
  directly adjacent to the tool, rather than heavy centered modal dialogs.
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

The system formally supports and preserves two complementary layouts in settings:

### Layout Option 1: Unified Long Dock with Integrated STEM Section
- A single continuous floating glass capsule.
- Writing tools (`[Pen]`, `[Highlighter]`, `[Eraser]`, `[Select]`) sit alongside an accent-bordered
  **STEM Dock** (`[f(x)]`, `[Geometry]`, `[Table]`, `[Circuit]`).
- Every STEM feature is accessible in a **single tap** without opening submenus.
- Can be docked along the bottom or top center, or aligned vertically along the free-hand margin.

### Layout Option 2: Split Floating Islands (Main Capsule + Pinned STEM Satellite)
- Two distinct but visually paired capsules.
- **Island A (Inking)**: Primary drawing tools and color palette, anchored near the free hand for instant switching.
- **Island B (STEM Satellite)**: A dedicated scientific capsule that remains **pinned open** on the screen.
  Users can drag it to any secondary screen area (e.g. top right) while keeping the free-hand toolbar on the left.

### The "Free-Hand Thumb Strip" (Ergonomic edge docking)
- The toolbar or a detached **Quick-Action Thumb Pad** can pin to the left edge (for right-handed writers)
  or the right edge (for left-handed writers).
- Allows toggling pen/eraser or hitting undo with the resting thumb while writing without palm-rejection conflicts.

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
| **Styling & Navigation** | Favorite Colors 1 / 2 / 3 | `1`, `2`, `3` | Free-hand thumb dot tap |
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
2. **Phase 2: Floating Capsule Toolbar**:
   - Replace the rigid side rail and heavy top bar with an adaptive, draggable floating capsule bar.
3. **Phase 3: Contextual Flyout Inspectors**:
   - Transition dialogs (Color & Width, Function Plot, Table editor) into anchor-positioned micro-inspectors.
4. **Phase 4: Desktop Shell Integration**:
   - Apply platform-native window styling (Mica / Vibrancy) in Compose Desktop.
