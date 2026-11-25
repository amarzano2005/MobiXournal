# Design system: surface tokens, palette, and motion

> Read when changing visual tokens, surface materials, colour roles, or shared UI motion across Android and Desktop.
> For the task: [Where a setting lives](guide-settings-and-shortcuts.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Scope

Both the Android editor and the `desktop/` window consume the shared token specification in `com.mobixournal.design.DesignTokens` (`mobix-core`). Band placement and viewport framing live in [chrome.md](chrome.md); desktop-specific panes and strips live in [desktop-ui-layout.md](desktop-ui-layout.md).

---

## Core design principles

```text
┌────────────────────────────────────────────────────────────────────────┐
│ [≡] [Undo] [Redo]   [Pen] [Highlighter] [Eraser] [Shapes] [Math]       │ ← header band
│ [Untitled] [Untitled.mobix]                                            │ ← tab row
├────────┬───────────────────────────────────────────────────────────────┤
│ ╭────╮ │                                                               │
│ │ ✏️ │ │                    CANVAS (Content-First)                    │
│ │ 🖍️ │ │        the sheet begins where the chrome ends                 │
│ │ 🧹 │ │                                                               │
│ ╰────╯ │                                                               │
└────────┴───────────────────────────────────────────────────────────────┘
   ↑ rail lane: a dedicated band beside the page, never over the sheet
```

### 1. Surface hierarchy and bands
- **Hairline borders**: `1 px` (`hairlineStrokeWidthDp = 1.0`) at `10–15%` alpha (`hairlineBorder`), without elevated cards or double rims.
- **Bands vs. transient overlays**: toolbars live in translucent bands (`85–88%` opacity, `24 dp` blur) outside the page rectangle so they never occlude ink or intercept stylus input ([chrome.md](chrome.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest)). Only transient surfaces — anchored popovers, dialogs, and selection action bars — float over the sheet.
- **Single closing edge**: headers and lanes close on one edge with a single shadow (`bandShadowDp = 4`) and no inner divider lines between rows.

### 2. Typography and iconography
- **Typography**: neutral sans-serif (**Inter** / **Geist**) with tabular numerals for coordinates, measurements, and zoom readouts.
- **Monoline glyphs**: `1.5 dp` stroke vector icons on a `24 × 24` grid (`toolGlyphGrid = 24`, `toolGlyphStrokeUnits = 1.5`) defined in `ToolGlyphCatalogue` and `ChromeGlyph`.

### 3. Dark and light palette contract
- **Neutral surface ramp**: dark mode uses `#000000` canvas backing with `#121212`, `#1E1E1E`, `#252528` (`NeutralContainerDark`), and `#38383E` (`ActiveSlotDark`), separated by `#383838` / `#262626` dividers. Accents use `#60A5FA` (`BlueDark`) in dark mode and `#2563EB` (`Blue`) in light mode.
- **Dynamic theming**: `AppSettings.dynamicColor` defaults to `false` (`XoppTheme(dynamicColor = false)`). Both `dynamicDarkColorScheme` and `dynamicLightColorScheme` pass through `pinnedDarkChrome()` / `pinnedLightChrome()` (`Theme.kt`) so container roles stay neutral and never inherit Material 3's violet baseline (`#F3EDF7` / `#EADDFF` / `#E8DEF8`).
- **Chrome ink**: everything drawn on the glass uses `rememberChromeInk` (white on dark glass, surface ink on light glass) instead of hard-coded `Color.White`; accent fills keep `onPrimary`/white.
- **Glass and floating fills**: toolbars read `rememberGlassSurfaceColor()`, and floating surfaces read `rememberFloatingSurfaceColor()` (`floatingPanelAlpha = 0.88`). That alpha clears the WCAG AA 4.5:1 floor for secondary body text in both themes over white and black sheets, verified by `ColorContractGuardTest`.
- **Floating surface mandate**: every dialog, menu, inspector, badge, toast, and selection bar (`ToolbarMenu`, `EditorActionBars`, `FloatingDialog`, `FloatingSurface`, `GlassToastHost` in `theme/FloatingGlass.kt`) must use `rememberFloatingSurfaceColor()`, `Modifier.floatingRim()` (`DesignTokens.RIM_*`), and `panelCornerRadiusDp` (`20 dp`) — never opaque Material cards or system `Toast`s.
- **Kotlin-drawn rim and fixed spring motion**: `floatingRim()` / `drawFloatingRim()` paints the rim in Kotlin across all API levels (no AGSL shaders). `liquidGlassSpringAnimation` and `DialogPresence` are always enabled.
- **Document data**: paper, ruling, and PDF backgrounds retain their document colours ([pages-and-paper.md](pages-and-paper.md)).

---

## One language: English on every surface

**Every string the app shows is English** — Android chrome, dialogs, toasts, error copy, and the desktop window. Strings live in the code that renders them with no localisation catalogue.

- **New user-facing text is written in English in the same commit as the feature.**
- **Error copy states what happened and what to do**, without exposing internal class names, lambdas, or `toString()` output ([desktop-documents.md](desktop-documents.md)).

---

## Mobile and Tablet layout

- **Glass tiles and PDF tint**: *New / Open / Save / Save As* use icon-and-label rows, while *Import PDF*, *Export PDF*, *Page as PNG*, and *Page as SVG* use `GlassActionButton` tiles (`rememberGlassSurfaceColor()`, `3 dp` shadow, top-edge sheen, `Modifier.floatingRim()`). PDF actions are tinted in `DocumentRed` (`#E53935`) with no red outline; page exports use the primary accent. Pen presets use `GlassPane` chips, and menu sections are parted by `MenuRule`.
- **Rail placement**: `AppSettings.toolbarPosition` places the main rail on the `Left` or `Right` vertical edge in its own lane beside the canvas (no `TOP`/`BOTTOM` rail docking).
- **Compact layout**: places the primary tools in a horizontal band along the bottom edge above the navigation-bar inset.
- **Inspectors**: the plot inspector (`PlotInspectorAnchor`) anchors beside its selection; the table editor uses a fixed dialog (`TableCellEditorDialog`, [mobix-elements.md](mobix-elements.md)).

### Bimanual ergonomics and tool slots

- **Main Toolbar (Left / Right)**: vertical capsule in its edge lane (`52 dp` glass, `6 dp` side padding via `RailSizing.kt`), holding handwriting, selection, text, image, vertical space, favourites, guides, layers, pages, zoom, and background controls.
- **Secondary STEM Toolbar (header)**: horizontal dock in the header's tools row for shapes, geometry, function graphs, tables, circuits, and guides, with the document tab strip directly below it.

### The pen-tip ring: the barrel button's summons

- **Trigger**: with *Settings → Stylus → Barrel button* set to **Ring** (*While held* or *Double-click*), pressing the button while hovering opens a ring centred on the nib (`hoverX`/`hoverY`).
- **Modes (`HudTrigger`)**: *While held* commits the slot under the pen on button release (landing the tip cancels without inking); *Double-click* opens the ring as a tap picker until a slot, outside area, or second double-click closes it.
- **Layout**: clockwise from top — the palette's first five colours, three pen widths, and six tools (Line, Lasso, Marquee, Highlighter, Eraser, Pen), defined by `RadialHud.FACTORY_TOOLS` and `factoryColours`. Customised in **Settings → Ring** (Desktop) and **Settings → Drawing → Ring** (Android).
- **Commit and material**: `applyToolPreset` / `activateTool` commits the selected slot (releasing inside the `32 dp` dead zone cancels). Uses `floatingPanelAlpha`, a `1 dp` rim in chrome ink, and `40 dp` slots (`COMPACT_BUTTON_SIZE_DP`) on a `116 dp` radius.
- **Shared core**: geometry and angle math live in `render/RadialHud.kt` (`RadialHudTest`). Summoned by the stylus barrel button on Android ([stylus.md](stylus.md)) and `Alt+B` on Desktop ([desktop-ui-layout.md](desktop-ui-layout.md)).

---

## Design token specification

The authoritative values live in **`com.mobixournal.design.DesignTokens`** (`mobix-core`). Both `ModernTheme` (Android) and `DesktopTheme` (Desktop) wrap these values, and `ModernThemeTest` / `DesktopThemeTest` verify parity against the JSON block below. `DesktopThemeTest` and `ColorContractGuardTest` forbid raw `Color(0x…)` literals in chrome files.

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
      "hoverSlotAlpha": 0.45,
      "textSelectionWashAlpha": 0.22
    },
    "geometry": {
      "capsuleCornerRadiusDp": 16,
      "compactButtonSizeDp": 40,
      "compactActionHeightDp": 32,
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
      "glassMotion": {
        "startScale": 0.94, "popoverSlideDp": 6, "openDamping": 0.96, "openStiffness": 800, "fadeInMs": 90,
        "closeDamping": 1.0, "closeStiffness": 1400, "fadeOutMs": 90, "exitHoldMs": 130,
        "sheetStartScale": 0.98, "sheetSlideDp": 24, "sheetOpenDamping": 0.95, "sheetOpenStiffness": 720, "sheetFadeInMs": 110,
        "sheetCloseDamping": 1.0, "sheetCloseStiffness": 1300, "sheetFadeOutMs": 95,
        "dialogStartScale": 0.96, "dialogSlideDp": 0, "dialogOpenDamping": 0.96, "dialogOpenStiffness": 800, "dialogFadeInMs": 100,
        "dialogCloseDamping": 1.0, "dialogCloseStiffness": 1400, "dialogFadeOutMs": 90
      }
    }
  }
}
```

---

## Shell details

- **Canvas bounds**: the canvas occupies the rectangle left by the header band and the rail lane ([chrome.md](chrome.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest)).
- **Settings overlay**: opens and closes inside `DialogPresence(ui.showSettings)` using `liquidGlassSpringAnimation` (`SettingsPresenceTest`). Each `SettingsArea` and `SettingsSection` carries a monoline glyph in `SectionRow` and search results.
- **Save indicator**: saving encodes an immutable snapshot without covering the canvas; `SaveActivity` drives the Save button's progress arc, and `OpenTab.unsavedEdits` / `DesktopTab.unsaved` drives the tab dirty dot and Save button enabled state.
- **Exit motion**: `DialogPresence(visible)` (Android) and `GlassPresence` / `GlassPopup` (Desktop) hold pop-ups and dialogs composed for `FLOATING_EXIT_MS` (`150 ms`) while reversing the spring (`rememberClosingArgument` preserves nullable dialog subjects). Animation modifiers sit *before* `background` / rim modifiers in the chain, and only the panel surface animates (child rows do not animate separately).
- **Plot slot and editor**: the *Function graph* button uses `ToolGlyph.PLOT` (`EditorRegions.kt`). Tapping/dragging places a plot via `AdvancedFunctionGraphDialog`, while editing an existing `PlotElement` opens `PlotInspectorAnchor` beside the selection (`applyPlotEdit`).
