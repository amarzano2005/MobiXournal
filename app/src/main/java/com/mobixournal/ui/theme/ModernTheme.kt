package com.mobixournal.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern design token provider for MobiXournal.
 *
 * Owned by `docs/ui-modernization.md` (Design token specification).
 * Centralizes design tokens across surface, geometry, and animation so that
 * later modernization tasks (floating capsules, inspectors, HUD) read from here
 * rather than scattering raw literals across composables.
 *
 * Material 3 keeps everything these tokens do not cover.
 */
object ModernTheme {

    // --- Surface tokens ---

    /** Raw string definition of [glassDark] from `docs/ui-modernization.md`. */
    const val GLASS_DARK_RAW: String = "rgba(22, 22, 24, 0.85)"

    /** Raw string definition of [glassLight] from `docs/ui-modernization.md`. */
    const val GLASS_LIGHT_RAW: String = "rgba(255, 255, 255, 0.88)"

    /** Raw string definition of [hairlineBorder] from `docs/ui-modernization.md`. */
    const val HAIRLINE_BORDER_RAW: String = "rgba(255, 255, 255, 0.12)"

    /** Dark glassmorphic capsule fill: `rgba(22, 22, 24, 0.85)`. */
    val glassDark: Color = Color(red = 22 / 255f, green = 22 / 255f, blue = 24 / 255f, alpha = 0.85f)

    /** Light glassmorphic capsule fill: `rgba(255, 255, 255, 0.88)`. */
    val glassLight: Color = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.88f)

    /** Subtle hairline rim stroke: `rgba(255, 255, 255, 0.12)`. */
    val hairlineBorder: Color = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.12f)

    /** Subtle hairline rim stroke in light mode: `rgba(0, 0, 0, 0.12)`. */
    val hairlineBorderLight: Color = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.12f)

    /** Raw string definition of [floatingPanelAlpha] from `docs/ui-modernization.md`. */
    const val FLOATING_PANEL_ALPHA_RAW: String = "0.93"

    /** Floating-surface opacity, as documented: **0.93**. */
    const val FLOATING_PANEL_ALPHA_VAL: Double = 0.93

    /**
     * How opaque a **floating surface** is — the bars' glass with the see-through turned down
     * ([floatingPanelAlpha]).
     *
     * A bar carries one glyph; a floating surface carries rows of text — a menu's items, an action
     * bar's labels, a badge's page number — and it can land over the sheet's own ink. At the bars' 0.85
     * the panel's secondary ink measured **4.41:1** against a paper page in the dark theme — below the
     * 4.5:1 floor for body text — and the page's strokes showed through the rows while they were being
     * read. 0.93 keeps the frost (about 7% still comes through, which is what ties the surface to the
     * bar under it) and puts the ink back above the floor (`ColorContractGuardTest` computes it, over
     * both extremes of backdrop, in both themes).
     */
    val floatingPanelAlpha: Float = FLOATING_PANEL_ALPHA_VAL.toFloat()

    /** Raw string definition of [panelCornerRadiusDp] from `docs/ui-modernization.md`. */
    const val PANEL_CORNER_RADIUS_DP_VAL: Int = 20

    /**
     * The corner **every floating surface** is cut with — the bars' dock corner, so a menu, an action
     * bar and a dialog (`Shapes.extraLarge`, i.e. `AppShapes`, which is what Material's dialogs read)
     * all turn at the same radius instead of 20dp over 28dp over a 50% pill.
     */
    val panelCornerRadiusDp: Dp = PANEL_CORNER_RADIUS_DP_VAL.dp

    /** Backdrop blur radius: 24 dp. */
    const val BLUR_RADIUS_DP_VAL: Int = 24
    val blurRadiusDp: Dp = 24.dp

    // --- Geometry tokens ---

    /** Floating capsule corner radius: 16 dp. */
    const val CAPSULE_CORNER_RADIUS_DP_VAL: Int = 16
    val capsuleCornerRadiusDp: Dp = 16.dp

    /** Compact tool/action button dimension: 40 dp. */
    const val COMPACT_BUTTON_SIZE_DP_VAL: Int = 40
    val compactButtonSizeDp: Dp = 40.dp

    /** Hairline border stroke width: 1.0 dp. */
    const val HAIRLINE_STROKE_WIDTH_DP_VAL: Double = 1.0
    val hairlineStrokeWidthDp: Dp = 1.0.dp

    // --- Animation tokens ---

    /** Physics spring stiffness: 380. */
    const val springStiffness: Float = 380f

    /** Physics spring damping coefficient: 28. */
    const val springDamping: Float = 28f

    /** Auto-dim and crossfade transition duration: 150 ms. */
    const val fadeTransitionMs: Int = 150

    // --- Derived Compose shapes and animation specs ---

    /** Capsule shape built from [capsuleCornerRadiusDp]. */
    val capsuleShape: RoundedCornerShape = RoundedCornerShape(capsuleCornerRadiusDp)

    /**
     * Damping ratio calculated from physics spring constants:
     * \zeta = c / (2 * sqrt(k * m)), with m = 1.
     * With k = 380 and c = 28, \zeta \approx 0.718.
     */
    val springDampingRatio: Float = springDamping / (2f * kotlin.math.sqrt(springStiffness))

    /** Creates a Compose [SpringSpec] configured with the modern theme spring constants. */
    fun <T> springSpec(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = springDampingRatio,
        stiffness = springStiffness,
        visibilityThreshold = visibilityThreshold,
    )

    /** Creates a Compose [TweenSpec] configured with the modern theme fade duration. */
    fun <T> fadeSpec(): TweenSpec<T> = tween(
        durationMillis = fadeTransitionMs,
    )

    /**
     * Resolves the glass surface background colour depending on dark mode state.
     */
    fun glassSurface(isDark: Boolean): Color = if (isDark) glassDark else glassLight

    /**
     * Resolves the hairline border stroke colour depending on dark mode state.
     */
    fun hairlineBorder(isDark: Boolean): Color = if (isDark) hairlineBorder else hairlineBorderLight

    /**
     * Resolves the fill of a **floating surface**: the bars' own glass ([glassSurface]), RGB untouched,
     * with the see-through turned down to [floatingPanelAlpha].
     *
     * One material for everything that floats over the page — the pop-ups and submenus, the action bars
     * that appear with a selection, the STEM editors' chrome, the badges, dialogs and overlays — so none
     * of them can be a different surface from the bar it appears over. Same colour as that bar by
     * construction; *less transparent* than it, because a floating surface is what text is read on
     * (see [floatingPanelAlpha]).
     */
    fun floatingSurface(isDark: Boolean): Color = glassSurface(isDark).copy(alpha = floatingPanelAlpha)
}
