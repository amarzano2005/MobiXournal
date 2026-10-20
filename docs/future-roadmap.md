# Strategic architecture roadmap: the three primary steps

> Read when planning or executing the transition from the legacy .xopp companion to the next-generation native note-taking platform. Establishes the authoritative architectural hierarchy between data foundation, mobile UX, and desktop workstation.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## The three-pillar architectural hierarchy

MobiXournal's evolution from a single-platform `.xopp` companion into a modern, full-featured scientific
note-taking suite proceeds along **three strictly ordered strategic pillars**:

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                           STEP 1: NEW NATIVE FORMAT (.mobix)                           │
│  Data foundation, streaming container, living entities, math & audio                   │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Enables rich data & fast paging
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                         STEP 2: NEW MOBILE GRAPHICAL INTERFACE                         │
│  Lightweight frosted glass, pen-tip radial HUD, snap-to-text, KaTeX/Typst              │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Shares KMP core & modernized tokens
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                       STEP 3: SOFTWARE DESKTOP WORKSTATION                             │
│  Compose Multiplatform + Skia GPU, drawing-tablet tuning, dials & plugins              │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: The native `.mobix` format (Data Foundation)

The foundational layer eliminates the memory, performance, and semantic bottlenecks of `.xopp`.
No user-facing interface can exceed the capabilities of the data model beneath it.

### Owning architecture spokes:
1. **Container & Schema**: [mobix-schema-spec.md](mobix-schema-spec.md) defines the PKWARE ZIP container
   (`application/vnd.mobixournal+zip`), `manifest.json`, and the schema of living entities:
   - Dynamic Tables (`TableElement` with editable rows, columns, alignments, and cell text).
   - Parametric Function Plots (`PlotElement` preserving formulas $f(x)$, intervals, and styles).
   - Parametric Shapes (`ParametricShapeElement` with vertex handles).
   - Circuit & Electronics Components (`CircuitElement` with magnetic connection pins).
   - High-fidelity Inking (`Stroke` with tilt, azimuth, and $\Delta t$ millisecond timestamps — and
     deliberately no barrel roll: Android exposes no roll axis, so the platform cannot supply it).
2. **Lossless Round-Trip Bridge**: [mobix-format.md](mobix-format.md) provides
   bidirectional `.xopp` ingestion and synthesis export, plus official Lua companion plugins for desktop Xournal++.
3. **Reactive Math Engine**: [math-engine.md](math-engine.md) powers real-time expression evaluation, numerical
   derivatives, definite integrals, roots, extrema, parametric curves, and polar plots.
4. **Infinite Canvas Engine**: [infinite-canvas.md](infinite-canvas.md) introduces infinite vertical,
   horizontal, and 2D whiteboard coordinate modes with 2D spatial chunking and intelligent print slicing.
5. **Continuous Audio Synchronization**: [audio-inking-sync.md](audio-inking-sync.md) bundles compressed Opus speech
   directly in `assets/`, mapping every inking point to audio timestamps for karaoke replay and bi-directional scrub.
6. **Conflict-Free Storage & Sync**: [sync-and-storage.md](sync-and-storage.md) implements three-way page-level
   merging across Nextcloud, Syncthing, and cloud providers without collision files.

---

## Step 2: Modern mobile graphic interface (User Experience)

With the living data model established, the Android interface transitions away from heavy Material Design
toward a lightweight, content-first creative tool.

### Owning architecture spokes:
1. **Modern Design System**: [ui-modernization.md](ui-modernization.md) replaces rigid toolbars with:
   - Translucent frosted glass capsules (1px hairline borders, $85\%$ blur opacity).
   - Pure OLED black dark mode (`#000000`) and crisp $1.5\text{ dp}$ monoline line-art iconography.
   - One layout, two jobs: the vertical rail in its own lane (writing tools) and the header STEM dock.
   - Pen-Tip Radial HUD (pie menu) summoned by barrel button for rapid color, width, and tool switching.
   - Universal shortcut and individual custom color remapping (every tool and custom HEX color slot remappable).
2. **Auto-Shape Perfection**: [shape-perfection-engine.md](shape-perfection-engine.md) provides $300\text{ ms}$
   pen dwell recognition, transforming freehand sketches into living parametric shapes with interactive vertex handles.
3. **On-Device Scientific Typesetting**: [latex-typst-rendering.md](latex-typst-rendering.md) compiles LaTeX
   via KaTeX and modern Typst markup offline in $< 15\text{ ms}$ into double-tap editable `MathBlockElement` entities.
4. **Advanced PDF Annotations**: [pdf-annotation-engine.md](pdf-annotation-engine.md) adds the magnetic
   "Snap-to-Text" highlighter and native ISO PDF/A annotation exports.
5. **Multi-Window Productivity**: [multi-window-workspace.md](multi-window-workspace.md) powers side-by-side
   split view, Samsung DeX freeform windows, and cross-window/cross-app drag and drop of living snippets.

---

## Step 3: Desktop workstation software (PC / Mac / Linux)

The third step scales MobiXournal onto desktop workstations, bringing extreme performance and hardware
synergy for mouse, keyboard, and drawing tablet users.

### Owning architecture spokes:
1. **Multiplatform Core & GPU Engine**: [desktop-workstation.md](desktop-workstation.md) extracts
   the shared Kotlin Multiplatform (KMP) core and drives rendering via **Skia/Skiko** on GPU backends
   (DirectX 12 on Windows, Vulkan on Linux, Metal on macOS), achieving $< 4\text{ ms}$ frame times at 120–240 Hz.
2. **Desktop Workstation UI Layout**: [ui-modernization.md](ui-modernization.md) adapts the interface into:
   - Slim native header bar with document tabs.
   - Collapsible navigation sidebar (`Ctrl+B`) for pages, layers, and PDF outlines.
   - Right Property Inspector panel for non-modal editing of plots, tables, and shapes.
   - Direct on-screen tool strips with permanent 1-click STEM access (dockable as a top/bottom ribbon or a widescreen lateral palette).
   - Bimanual desktop flow combining left-hand keyboard shortcuts with pen tablet inking.
3. **Graphics Tablet & Dial Integration**: [desktop-workstation.md](desktop-workstation.md) implements
   multi-protocol driver support (Wintab / Windows Ink / Wayland `zwp_tablet_manager_v2`), stepless rotary dial zoom,
   aspect ratio locks for screenless tablets, and pen-tip radial HUD pie menus.
4. **Extensible Plugin Ecosystem**: [plugin-system.md](plugin-system.md) enables sandboxed Lua and WASM
   plugins for custom exporters (LaTeX, Typst, Anki) and automated handwriting/MathOCR.

---

## Inter-pillar dependency matrix

| Step | Depends On | Unlocks |
|---|---|---|
| **Step 1: `.mobix` Format** | Existing Kotlin render & math foundations | Living elements, infinite canvas, audio sync, page streaming |
| **Step 2: Mobile UI** | Step 1 data model (`TableElement`, `PlotElement`, etc.) | Fluid bimanual drafting, 1-tap STEM docking, modern visual identity |
| **Step 3: Desktop App** | Step 1 data core + Step 2 design tokens & icons | Cross-platform parity, hardware dial support, professional PC workstation |
