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

## Desktop alignment (PC / Mac / Linux workstations)

On desktop workstations (see [native-format-and-desktop.md](native-format-and-desktop.md)), the interface
adapts to match professional creative suites:

| Aspect | Mobile / Tablet Shell | Desktop Workstation Shell |
|---|---|---|
| **Form factor** | Touch / Stylus floating capsule | Sleek slim header or floating tool palette |
| **Tool Inspector** | Touch-friendly floating sheet | Compact collapsible side inspector panel |
| **System Materials** | Android hardware blur | Native Windows 11 Mica / Acrylic, macOS Vibrancy, Linux Wayland blur |
| **Keyboard & Dial** | On-screen touches | Hardware hotkeys (B, E, V, L), rotary dial radial menus |

---

## Design token specification

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

1. **Phase 1: Design Tokens & Monoline Iconography**:
   - Extract UI colors and dimensions into a modern token provider (`ModernTheme`).
   - Swap stock filled Material icons for consistent $1.5\text{ dp}$ monoline vector assets.
2. **Phase 2: Floating Capsule Toolbar**:
   - Replace the rigid side rail and heavy top bar with an adaptive, draggable floating capsule bar.
3. **Phase 3: Contextual Flyout Inspectors**:
   - Transition dialogs (Color & Width, Function Plot, Table editor) into anchor-positioned micro-inspectors.
4. **Phase 4: Desktop Shell Integration**:
   - Apply platform-native window styling (Mica / Vibrancy) in Compose Desktop.
