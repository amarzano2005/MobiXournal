# Modern UI design system: lightweight, content-first interface

> Read when redesigning or modernizing the mobile and desktop graphical interface, transitioning away from heavy Material Design paradigms, implementing floating capsules, or aligning cross-platform aesthetics.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: moving beyond heavy Material Design

The interface was built on Google's standard Material 3 conventions — filled container pills, coloured
surface tints, heavy drop shadows and full-screen dialogs. Functional on a phone, that aesthetic is
visual clutter on a tablet or a desktop: it reads as an "Android OS utility" rather than as a focused
creative tool (Figma, Linear, Concepts, Freeform).

The **Modern UI Design System** replaces it with a clean, lightweight, content-first interface shared by
the tablet and the `desktop/` window; the layout it produces is owned by [chrome.md](chrome.md).

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
  **The rule, and its one carve-out** — *bands never lie on the page; transient surfaces may*: an anchored
  popover, a dialog or a selection bar comes and goes with the gesture, so it may sit over the ink, and
  none of them is ever a permanent band.
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
- **What it is.** With Settings → Stylus → Barrel button → *While held* set to **Hud** — or its **Double-click** set to *Radial HUD* — pressing the stylus button with the tip off the glass opens a ring centred on the nib (the last `hoverX`/`hoverY`), not a palette parked on screen: nothing exists until the button is pressed, and the release takes it away. The double-click binding exists so a pen that keeps the held button for erasing (the default) can still reach the ring; the second click's press opens it and that press's release commits (a pen whose own firmware reports a whole double-click as one click opens it on that click, and its release then finds no slot under the nib).
- **What it holds**, clockwise from the top: the user's palette swatches in their stored order (the editable custom HEX colour last — its first one-flick home), then the three pen widths, then the three writing tools (pen, highlighter, eraser). The rail and the STEM dock keep the rest; the ring is a writing control, and the 33 files of the old NeXopp palette are the weight it exists *not* to repeat.
- **How a flick commits.** The pen crosses the ring; the slot under it takes the accent ring and the steel plate, with a haptic tick at each boundary; the release commits it through the same `applyToolPreset` / `activateTool` entries the rail uses, so a ring choice can never mean something the user could not have set by hand. A release inside the 32 dp dead zone or off the slot band commits nothing, and a tip that lands while the ring is open cancels it without writing a stroke.
- **Material.** A floating surface, round: the floating fill at `floatingPanelAlpha`, the 1 dp hairline rim in the chrome ink, and 40 dp slots (`COMPACT_BUTTON_SIZE_DP`) on a 116 dp radius. Faces are read without labels — a swatch is its colour, a width is a dot of that width, a tool is its `ToolGlyphCatalogue` glyph drawn through the canvas adapter.

---

## Desktop workstation UI architecture (Windows 11)

On desktop workstations paired with keyboards and graphics tablets (see [desktop-workstation.md](desktop-workstation.md)),
scaling a mobile touch interface up produces clumsy, oversized controls. The window is therefore a
**workstation layout** (inspired by Figma, VS Code and Blender) built from the same material as the tablet's:

```text
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ [≡] [Notebook 1 ×] [Notebook 2 ×] [+]                          [Tool placement]      │  header band
├─────────┬─────────────────────────────────────────────────────────────────────────────┤
│ Sidebar │                                CANVAS                                       │
│ Pages   │                                                                             │
│ Layers  │                                                                             │
│ [Ctrl+B]│                                                                             │
├─────────┴─────────────────────────────────────────────────────────────────────────────┤
│ Page 1 / 6    100%    fit page                                  notebook.mobix        │  status bar
└───────────────────────────────────────────────────────────────────────────────────────┘
```

### 1. Window layout & workspace panes
- **Slim header band**: one tab per open document (each keeps its own page, zoom and fit), the `+` to open
  another, and the **Tool placement** control; the window wears the OS's own decorations.
- **Collapsible navigation sidebar (`Ctrl+B`)**: page thumbnails and the current page's layers, with an eye
  per layer that hides it from the page and from its thumbnail without writing to the document.
- **The sheet alone on the desk**: one page at a time, fitted by what the page is (a sheet whole, a long
  strip to its width), with the fit mode named in the status bar.
- **Status bar**: page counter, effective zoom, fit mode and the container behind the document.

**Desktop decisions (2026-10-21):**
- **The chrome is the app's own, on a true-black desk — not the window manager's.** The header, the
  sidebar and the tool strips are the same frosted glass the mobile chrome uses, and the desk behind the
  page is `#000000`; platform materials (Windows 11 Mica) are **not** used. Mica replaces the
  app's material with a wallpaper-tinted one, so the desktop window would stop matching the phone and
  its contrast would change with the user's wallpaper — the opposite of the pinned ramps above.
- **Both tool placements ship**: the vertical rail is the default, the horizontal ribbon is the
  alternative for 16:9 monitors, and the header's **Tool placement** control chooses Rail / Ribbon /
  Both. Whichever is on, the strip is a capsule in a lane of **air** beside the canvas, never a bar over
  the page. The choice is written to the window's settings file, so it survives a restart.

**The shell as built (2026-10-22).** `desktop/` has the header, tab strip, sidebar, workspace, status
bar and both tool strips; [`desktop-workstation.md`](desktop-workstation.md) owns the shell's inventory.
From **this** spoke it takes four rules, and the parity work of 2026-10-22 is what put them all in the
window:

- **A band is chrome, a capsule is an object.** The header, status bar and sidebar take the glass fill
and **one 4 dp cast shadow**, and **no rim** (the rule above). A strip's lane is **air, not a band** —
painting it too stacked two sheets of 0.85 glass and the rail read #161618. The rail is a **52 dp capsule
in a 64 dp lane** (6 dp of side air, 8 above and below) and the ribbon a 48 dp capsule in a 56 dp band;
each takes its **own 6 dp lift** and the family's rim, and a header control is the same capsule at 36 dp.
Without those lifts the bands were a flat fill, because over a true-black desk the glass' alpha is
invisible: the shadow is the only depth the window has (`Modifier.bandSurface()` / `glassCapsule()`,
`GlassEdge.kt`). The fill is the **shared token with no gradient of the window's own** — the tablet's
bands are flat, and a sheen over the PC's made the same material a shade lighter — and glyphs are the
chrome ink (white), the picked slot steel with the glyph in the accent, exactly as the rail draws them.
- **One icon set.** The tool glyphs come from `ToolGlyphCatalogue` (`mobix-core`), plain geometry on the
24-unit grid that the tablet turns into an `ImageVector` and the window draws into a `DrawScope`; the
window keeps no second set. The picked slot is a 10 dp squircle in the steel `#38383E`.
- **Every surface answers the pointer**: a tab, a sidebar page, a layer row, a menu option, the open and
  close buttons and every tool slot wear the steel at `hoverSlotAlpha` (0.45) while hovered, one step
  short of the picked fill — `Modifier.hoverFill()` in `GlassEdge.kt`. Before that the rail answered only
  with its tooltip and the sidebar not at all.
- **No motion of its own**: the windows' surfaces arrive on `DesignTokens.GlassMotion` too. The `Popup`
flyouts still appear instantly — a desktop popup has no composition to hold open once its flag clears.
- **The colours and corners are the tokens**: 16 dp tab capsules, the 20 dp panel corner, a 40 dp header,
a 24 dp status bar, Inter with tabular figures, dark-first. `DesktopThemeTest` re-reads the JSON block
above and `ChromeMaterialRenderTest` proves the lift, the flat fill and the rim as pixels.

Picking a colour writes it straight to the settings file (`ink.colour`, `ink.width`), and the palette is
`palette.colours` through `PenPalette.sanitised`, so a hand-edited file cannot put a transparent colour,
a duplicate or an empty palette in front of the picker. The row ends in the **editable custom slot** and
the **add-colour** swatch, both opening the window's own colour panel (saturation/value square, hue
slider, two-way `#RRGGBB` with a live preview, Set/Cancel) in the floating glass rather than a Material
dialog; an invalid hex keeps the last valid colour and leaves Set out of reach. The placement choice is
persisted (`interface.toolPlacement`, Rail by default) and the strips appear once a document is open.

Everything the chrome does can also be done from the keyboard:

| Key | Action |
|---|---|
| `Ctrl + T`, `+` | open a document; a file already open activates its tab instead of opening twice |
| `Ctrl + W`, `×` | close the tab, activating its neighbour — asking first while it has unsaved edits |
| `Ctrl + S` | save the active document back into the container it was opened as; unavailable while the file already holds it |
| `Ctrl + Shift + S` | Save As — the name's suffix chooses the container |
| `Ctrl + Z` / `Ctrl + Shift + Z` | undo / redo the active tab's last command |
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

Closing a dirty tab, or the window itself, raises the **Save / Discard / Cancel** question in the floating
glass over the page rather than closing silently; `Esc` is Cancel. The window's X goes through the same
path, so neither door can lose work, and a tab with unsaved edits wears a dot in the accent after its
label (`docs/desktop-workstation.md`).

**The two history commands have defined states, not just a binding.** `Ctrl + Z` and `Ctrl + Shift + Z`
act on the **active tab's own** `EditHistory` — undoing in one document never reaches into another — and
both are **unavailable while their stack is empty**: the command is not handled at all, and `canUndo` /
`canRedo` are the state a header control greys the action out on (there is no such control yet, so
`apply` answering false is the whole visible effect). One gesture is one step: a stroke records on its
lift and an eraser drag on its first bite, so a single undo never takes back half a line; an eraser
stroke that touches no ink records nothing. A save starts a new history, because the pages of the
archive it just closed are not steps a tab may restore.

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

### 2. Tool placement on Desktop
The drawing and STEM tools are always on screen, in whichever strip the header's **Tool placement** control
selects:

- **Rail (default)**: the 52 dp capsule in its 64 dp lane down one edge — the tablet's own geometry, which
  leaves the sheet its full height on 16:9 and 21:9 monitors.
- **Ribbon**: the 48 dp capsule in a 56 dp band under the header, for a window that is short or narrow.
- **Both**: the ribbon above the rail, each with its own slots.
- **1-click STEM access**: `f(x)`, shapes, tables and circuits sit in the same strip as the inking tools,
  with no submenu between the user and them.
- **Hover names the slot**: a pill tooltip shows the tool's name and its key (e.g. `Function plot [M]`).

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
| | Pan / Hand Tool | `Space` (held) | Two-finger scroll / drag |
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
| | Stroke Width Up / Down | `]`, `[` | Rail's size slot |
| | Zoom In / Out | `Ctrl + =`, `Ctrl + -` | Pinch gesture |
| | Undo / Redo | `Ctrl + Z`, `Ctrl + Shift + Z` | Two-finger tap |

- **Universal Shortcut & Individual Color Remapping**: All keybindings, tools, and **individual color palette slots**
  are remappable via **Settings → Input & Shortcuts**. A keyboard shortcut or a stylus gesture can select a
  specific user-defined custom HEX color (e.g. `Alt+1` for Pure Black `#000000`, `Alt+2` for Crimson Red
  `#D32F2F`, `Alt+3` for Royal Blue `#1565C0`), not just the 3 factory favourites.

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
  [`chrome.md`](chrome.md#the-frame-around-the-page-the-chromes-bands-and-the-canvass-rest).
- **Settings opens with the app's own spring**: the settings screen is an overlay on the still-composed
  editor, and it enters with `liquidGlassSpringAnimation` like every menu and inspector.
- **Every settings row is identified by a glyph**: each `SettingsArea` and `SettingsSection` carries its own
  monoline icon in a tinted disc at the head of its row (`SectionRow`), and the same glyph follows the row
  into the search results. The glyph is part of the enum, so a new section cannot be added without one.
- **Dialogs leave the way they arrived**: a dialog is its own window, so Compose tore it down the instant its
  flag cleared — the entrance spring played in full and the panel vanished mid-frame. `DialogPresence(visible)`
  holds the window composed for `FLOATING_EXIT_MS` (230 ms) while the surface runs the animation backwards
  (the 0.82 scale of a `0.85/850` spring, a 170 ms fade, `LocalFloatingSurfaceClosing`), and
  `rememberClosingArgument` keeps the dialog's subject when the flag was a nullable one. Every floating
  dialog — the editor's, the pages menu's, the settings pickers' — is gated that way.
- **The plot editor anchors to its element**: editing an existing `PlotElement` opens `PlotInspectorAnchor`
  beside the selection, commits through `applyPlotEdit` with full undo coverage and unedited fields kept;
  creating a new plot uses the modal `AdvancedFunctionGraphDialog`, whose placement arithmetic is
  viewport-centred.
