package com.mobixournal.design

/**
 * The design tokens of `docs/ui-modernization.md`, as **plain Kotlin** — no Compose types, so both
 * shells and the core can read them.
 *
 * This object is the one home of the numbers: `ModernTheme` (Android) and `DesktopTheme` (desktop)
 * turn them into `Color`/`Dp`/specs, and `ModernThemeTest` / `DesktopThemeTest` re-read the documented
 * JSON block so a token cannot drift from the doc without a failing build. Before it, the desktop's
 * chrome carried its own `Color(0x…)` literals: two shells, two greys, and no place that said which one
 * the doc meant.
 *
 * Values are the documented ones, in the documented spelling: [GLASS_DARK_RAW] and friends exist so the
 * test can compare *the string the doc shows* with *the string the code uses*, rather than a colour that
 * happens to round to it.
 */
object DesignTokens {

    // --- Surface tokens (`docs/ui-modernization.md`, Design token specification) -----------------

    /** Raw definition of [glassDark]: the header's and the rail's frosted fill. */
    const val GLASS_DARK_RAW: String = "rgba(22, 22, 24, 0.85)"

    /** Raw definition of [glassLight]: the same material in the light theme. */
    const val GLASS_LIGHT_RAW: String = "rgba(255, 255, 255, 0.88)"

    /** Raw definition of [hairlineBorder]: one edge, one pixel, 12 % alpha. */
    const val HAIRLINE_BORDER_RAW: String = "rgba(255, 255, 255, 0.12)"

    /** Raw definition of [floatingPanelAlpha]. */
    const val FLOATING_PANEL_ALPHA_RAW: String = "0.88"

    /** `#161618` — the glass' RGB, before its 0.85 alpha. */
    const val GLASS_DARK_RGB: Int = 0x161618

    /** The glass' alpha, as the doc's `rgba(…, 0.85)` writes it. */
    const val GLASS_DARK_ALPHA: Float = 0.85f

    /** `#FFFFFF` — the light theme's glass RGB. */
    const val GLASS_LIGHT_RGB: Int = 0xFFFFFF

    /** The light glass' alpha. */
    const val GLASS_LIGHT_ALPHA: Float = 0.88f

    /** The hairline rim's RGB: white, at [HAIRLINE_ALPHA]. */
    const val HAIRLINE_RGB: Int = 0xFFFFFF

    /** The hairline rim's alpha: `rgba(255, 255, 255, 0.12)`. */
    const val HAIRLINE_ALPHA: Float = 0.12f

    /** The hairline's stroke width, in dp. */
    const val HAIRLINE_STROKE_WIDTH_DP: Double = 1.0

    /**
     * How opaque a floating surface is: the bars' glass with the see-through turned down, measured so
     * the panel's ink still clears the 4.5:1 body-text floor over a white sheet and over black ink
     * (`ColorContractGuardTest` recomputes it). Not a taste — see `ModernTheme.floatingPanelAlpha`.
     */
    const val FLOATING_PANEL_ALPHA: Float = 0.88f

    /** The corner every floating surface and panel is cut with, in dp. */
    const val PANEL_CORNER_RADIUS_DP: Int = 20

    /** Backdrop blur radius behind the glass, in dp. */
    const val BLUR_RADIUS_DP: Int = 24

    // --- The raised glass: what lifts a surface off the desk -------------------------------------
    //
    // A band (the header, the status bar, the sidebar, a tool lane) and a floating capsule (the rail,
    // the ribbon, a header control) are the same material, and what tells them apart from the desk is
    // the lift under them. Both shells take the numbers from here: the tablet puts them on a Material
    // `Surface`, the desktop draws the same lift with `Modifier.shadow`, so "raised" means one thing.
    //
    // Over the true-black desk the glass' alpha is invisible (a near-black at 0.85 and an opaque one
    // are the same pixels), so the shadow is the **only** depth cue the window has.

    /** The cast shadow under a **band**: 4 dp — the tablet's header, status bar and rail lane. */
    const val BAND_SHADOW_DP: Int = 4

    /** The cast shadow under a **floating capsule**: 6 dp — the tablet's rail, a header control. */
    const val CAPSULE_SHADOW_DP: Int = 6

    // --- The floating glass's edge --------------------------------------------------------------
    //
    // A floating surface is not a filled box with a border: it wears one drawn edge — a hairline shaded
    // along its length, bright at the top and faint at the bottom, so the panel reads as lit from above,
    // plus a feathered inner ring that walks the outer stroke into the fill instead of stopping at it.
    // The four numbers below are that edge, and they live here rather than in the Android module because
    // the desktop draws the same one over its own true-black desk (`docs/ui-modernization.md`).

    /** How thick the rim's stroke is, in dp: one hairline, drawn inside the surface's edge. */
    const val RIM_WIDTH_DP: Double = 1.0

    /** The rim's alpha at the top of the surface — the lit end of the gradient. */
    const val RIM_ALPHA_TOP: Float = 0.12f

    /** The rim's alpha at the bottom — the end that falls away into the desk. */
    const val RIM_ALPHA_BOTTOM: Float = 0.04f

    /** The feathered inner ring's width, as a multiple of [RIM_WIDTH_DP]. */
    const val RIM_INNER_WIDTH_SCALE: Float = 1.5f

    /** The inner ring's alpha, as a fraction of the rim's own at the same height. */
    const val RIM_INNER_ALPHA_SCALE: Float = 0.35f

    // --- Geometry tokens ------------------------------------------------------------------------

    /** A capsule's corner (tool slots, tabs, chips), in dp. */
    const val CAPSULE_CORNER_RADIUS_DP: Int = 16

    /** One compact tool/action button, in dp. */
    const val COMPACT_BUTTON_SIZE_DP: Int = 40

    /** A tool slot's own corner — the 10 dp squircle inside the rail's capsule (`RoundedCornerShape(10)`). */
    const val RAIL_SLOT_CORNER_RADIUS_DP: Int = 10

    /** The rail's floating capsule, in dp — its own width, inside the lane that holds it. */
    const val RAIL_CAPSULE_WIDTH_DP: Int = 52

    /** The air between the rail capsule and its lane's edge, on each side, in dp. */
    const val RAIL_LANE_PADDING_DP: Int = 6

    /** The whole lane: the capsule plus its air on both sides — 64 dp, the tablet's own lane. */
    const val RAIL_LANE_WIDTH_DP: Int =
        RAIL_CAPSULE_WIDTH_DP + RAIL_LANE_PADDING_DP * 2

    /** The capsule's own top/bottom air inside the lane, so it reads as a capsule and not a band. */
    const val RAIL_OUTER_PADDING_DP: Int = 8

    /** A control in the header band (the sidebar toggle, placement, and the tablet's icon buttons): 36 dp. */
    const val HEADER_CONTROL_SIZE_DP: Int = 36

    // --- Tool glyphs: one grid, one weight ------------------------------------------------------

    /**
     * The grid every tool glyph is drawn on, in design units — the 24-unit icon grid. A glyph is
     * **geometry only**: the shells scale it to the slot they draw, so the same art reads at 20 dp in
     * a rail slot and at 24 dp in a picker (`docs/ui-modernization.md`, monoline iconography).
     */
    const val TOOL_GLYPH_GRID: Float = 24f

    /**
     * The stroke a tool glyph is drawn with, **in grid units** (1.5 of 24). Both shells scale geometry
     * and stroke together, so the two toolbars draw the same optical weight at the same size — which
     * is what "monoline" means here: one line, not one width at one zoom level.
     */
    const val TOOL_GLYPH_STROKE_UNITS: Float = 1.5f

    // --- Animation tokens -----------------------------------------------------------------------

    /** Physics spring stiffness for the app's own motion language. */
    const val SPRING_STIFFNESS: Float = 380f

    /** Physics spring damping coefficient. */
    const val SPRING_DAMPING: Float = 28f

    /** The auto-dim / crossfade duration, in ms. */
    const val FADE_TRANSITION_MS: Int = 150

    /**
     * The floating glass's **motion** — how a panel arrives and leaves. Plain Kotlin, because both
     * shells draw the same arrival and the numbers are the app's motion language, not one platform's
     * taste (`docs/ui-modernization.md`).
     *
     * A surface opens from [START_SCALE] to 1 on an under-damped spring while its alpha fades in over
     * [FADE_IN_MS]; it draws back to the same scale on the slightly stiffer [CLOSE_STIFFNESS] spring
     * while fading out over [FADE_OUT_MS]. A host that tears its surface down the instant the flag
     * clears has to hold it for [EXIT_HOLD_MS] instead, or the exit is cut off mid-flight.
     */
    object GlassMotion {

        /** Where a panel starts (and ends) its arrival: 82 % of its size, around the anchor. */
        const val START_SCALE: Float = 0.82f

        /** The arrival spring's damping — under-damped, so it settles with a cushioned snap. */
        const val OPEN_DAMPING_RATIO: Float = 0.74f

        /** The arrival spring's stiffness. */
        const val OPEN_STIFFNESS: Float = 550f

        /** The arrival's alpha fade, in ms. */
        const val FADE_IN_MS: Int = 110

        /** The closing spring's damping: stiffer, so the panel leaves without a visible bounce. */
        const val CLOSE_DAMPING_RATIO: Float = 0.85f

        /** The closing spring's stiffness. */
        const val CLOSE_STIFFNESS: Float = 850f

        /** The closing alpha fade, in ms. */
        const val FADE_OUT_MS: Int = 170

        /**
         * How long a host keeps a closing surface composed. A shorter hold played the exit out in
         * ~8 frames, which read as no animation at all.
         */
        const val EXIT_HOLD_MS: Int = 230
    }

    // --- Palette --------------------------------------------------------------------------------

    /** The desk both shells lie the page on: true OLED black. */
    const val DESK_RGB: Int = 0x000000

    /** The luminescent accent, dark theme (`BlueDark`). */
    const val ACCENT_DARK_RGB: Int = 0x60A5FA

    /** The accent, light theme (`Blue`). */
    const val ACCENT_LIGHT_RGB: Int = 0x2563EB

    /** The steel a *picked* tool slot wears (`ActiveSlotDark`) — never the accent. */
    const val ACTIVE_SLOT_DARK_RGB: Int = 0x38383E

    /**
     * How much of the picked slot's steel a **hovered** slot wears: the state between rest and picked.
     *
     * The window's slots had no hover fill at all — only the tooltip — so a pointer resting on the rail
     * gave no answer about which slot it was on. A token rather than an opacity written at the call site,
     * because the two shells must be able to hover at the same weight.
     */
    const val HOVER_SLOT_ALPHA: Float = 0.45f

    /** The chrome's own ink on the dark glass. */
    const val INK_DARK_RGB: Int = 0xE6E6EF

    /**
     * The ink a **glyph on the glass** is drawn in: pure white, which is what the tablet's chrome uses
     * (`rememberChromeInk`). It is a token rather than a `Color.White` because the window draws the same
     * icons and the two shells must not disagree about how bright a tool is.
     */
    const val CHROME_INK_DARK_RGB: Int = 0xFFFFFF

    /** Raw definition of [CHROME_INK_DARK_RGB], as the doc's token block writes it. */
    const val CHROME_INK_DARK_RAW: String = "#FFFFFF"

    /** The secondary ink: status-bar figures, layer names. */
    const val INK_SECONDARY_DARK_RGB: Int = 0x9A9AA6

    /** The muted ink: labels that only explain, never invite. */
    const val INK_MUTED_DARK_RGB: Int = 0x6E6E78

    /** The ink a refusal is written in — the one place a chrome colour is not neutral. */
    const val DANGER_DARK_RGB: Int = 0xFF8A80

    // --- Desktop chrome metrics (`docs/desktop-workstation.md`, the shell as built) --------------

    /** The header band: one row of document tabs, closing on one bottom edge. */
    const val HEADER_HEIGHT_DP: Int = 40

    /** The status bar: page, zoom, fit, container, progress. */
    const val STATUS_HEIGHT_DP: Int = 24

    /** The stroke every chrome glyph is drawn with — monoline, `1.5 dp`, never a filled icon. */
    const val GLYPH_STROKE_DP: Float = 1.5f

    /** The page's shadow radius: one shadow, under the sheet, and no second edge anywhere. */
    const val PAGE_SHADOW_DP: Int = 12

    /**
     * The page shadow's RGB. On a true-black desk a shadow cannot be *darker* than the desk, so the
     * sheet's lift is drawn as the few units above black that a soft falloff would leave.
     */
    const val PAGE_SHADOW_RGB: Int = 0x15151A
}
