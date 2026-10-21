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

    // --- Geometry tokens ------------------------------------------------------------------------

    /** A capsule's corner (tool slots, tabs, chips), in dp. */
    const val CAPSULE_CORNER_RADIUS_DP: Int = 16

    /** One compact tool/action button, in dp. */
    const val COMPACT_BUTTON_SIZE_DP: Int = 40

    // --- Animation tokens -----------------------------------------------------------------------

    /** Physics spring stiffness for the app's own motion language. */
    const val SPRING_STIFFNESS: Float = 380f

    /** Physics spring damping coefficient. */
    const val SPRING_DAMPING: Float = 28f

    /** The auto-dim / crossfade duration, in ms. */
    const val FADE_TRANSITION_MS: Int = 150

    // --- Palette --------------------------------------------------------------------------------

    /** The desk both shells lie the page on: true OLED black. */
    const val DESK_RGB: Int = 0x000000

    /** The luminescent accent, dark theme (`BlueDark`). */
    const val ACCENT_DARK_RGB: Int = 0x60A5FA

    /** The accent, light theme (`Blue`). */
    const val ACCENT_LIGHT_RGB: Int = 0x2563EB

    /** The steel a *picked* tool slot wears (`ActiveSlotDark`) — never the accent. */
    const val ACTIVE_SLOT_DARK_RGB: Int = 0x38383E

    /** The chrome's own ink on the dark glass. */
    const val INK_DARK_RGB: Int = 0xE6E6EF

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
