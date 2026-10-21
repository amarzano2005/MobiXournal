package com.mobixournal.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens

/**
 * The Compose face of the design tokens: turns [DesignTokens] (plain Kotlin, `mobix-core`) into the
 * colours, radii and animation specs the Android chrome draws with. The desktop reads the same source
 * through `DesktopTheme`, so the two shells cannot disagree about what the glass is.
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
    const val GLASS_DARK_RAW: String = DesignTokens.GLASS_DARK_RAW

    /** Raw string definition of [glassLight] from `docs/ui-modernization.md`. */
    const val GLASS_LIGHT_RAW: String = DesignTokens.GLASS_LIGHT_RAW

    /** Raw string definition of [hairlineBorder] from `docs/ui-modernization.md`. */
    const val HAIRLINE_BORDER_RAW: String = DesignTokens.HAIRLINE_BORDER_RAW

    /** Dark glassmorphic capsule fill: `rgba(22, 22, 24, 0.85)`. */
    val glassDark: Color = tokenColour(DesignTokens.GLASS_DARK_RGB, DesignTokens.GLASS_DARK_ALPHA)

    /** Light glassmorphic capsule fill: `rgba(255, 255, 255, 0.88)`. */
    val glassLight: Color = tokenColour(DesignTokens.GLASS_LIGHT_RGB, DesignTokens.GLASS_LIGHT_ALPHA)

    /** Subtle hairline rim stroke: `rgba(255, 255, 255, 0.12)`. */
    val hairlineBorder: Color = tokenColour(0xFFFFFF, DesignTokens.HAIRLINE_ALPHA)

    /** Subtle hairline rim stroke in light mode: `rgba(0, 0, 0, 0.12)`. */
    val hairlineBorderLight: Color = tokenColour(0x000000, DesignTokens.HAIRLINE_ALPHA)

    /** Raw string definition of [floatingPanelAlpha] from `docs/ui-modernization.md`. */
    const val FLOATING_PANEL_ALPHA_RAW: String = DesignTokens.FLOATING_PANEL_ALPHA_RAW

    /** Floating-surface opacity, as documented: **0.88**. */
    val FLOATING_PANEL_ALPHA_VAL: Double = DesignTokens.FLOATING_PANEL_ALPHA.toDouble()

    /**
     * How opaque a **floating surface** is — the bars' glass with the see-through turned down
     * ([floatingPanelAlpha]).
     *
     * It is the **most see-through the ink allows**, not a taste: a bar carries one glyph, a floating
     * surface carries rows of text — a menu's items, an action bar's labels, a badge's page number — and
     * it can land over the sheet's own ink. At the bars' 0.85 the dark theme's secondary ink measured
     * **4.41:1** over a paper page, below the 4.5:1 floor for body text. The half-step this token used
     * to take (0.93) bought that back by making the panel a slab: at seven percent, nothing shows
     * through, and over the true-black desk a charcoal panel at 0.93 and an opaque one are the same
     * picture — which is what "the transparency does not apply" looks like.
     *
     * 0.88 is where both themes still clear the floor with room — the dark theme's secondary ink over a
     * white sheet measures **4.90:1**, the light theme's over black ink **4.68:1** — so it is as far as
     * the fill can go without trading legibility for frost. `ColorContractGuardTest` recomputes both
     * over both extremes of backdrop, so it cannot be made more see-through by an edit that does not
     * also fix the ink.
     */
    val floatingPanelAlpha: Float = FLOATING_PANEL_ALPHA_VAL.toFloat()

    /** Raw string definition of [panelCornerRadiusDp] from `docs/ui-modernization.md`. */
    const val PANEL_CORNER_RADIUS_DP_VAL: Int = DesignTokens.PANEL_CORNER_RADIUS_DP

    /**
     * The corner **every floating surface** is cut with — the bars' dock corner, so a menu, an action
     * bar and a dialog (`Shapes.extraLarge`, i.e. `AppShapes`, which is what Material's dialogs read)
     * all turn at the same radius instead of 20dp over 28dp over a 50% pill.
     */
    val panelCornerRadiusDp: Dp = PANEL_CORNER_RADIUS_DP_VAL.dp

    /** Backdrop blur radius: 24 dp. */
    const val BLUR_RADIUS_DP_VAL: Int = DesignTokens.BLUR_RADIUS_DP
    val blurRadiusDp: Dp = BLUR_RADIUS_DP_VAL.dp

    // --- Geometry tokens ---

    /** Floating capsule corner radius: 16 dp. */
    const val CAPSULE_CORNER_RADIUS_DP_VAL: Int = DesignTokens.CAPSULE_CORNER_RADIUS_DP
    val capsuleCornerRadiusDp: Dp = CAPSULE_CORNER_RADIUS_DP_VAL.dp

    /** Compact tool/action button dimension: 40 dp. */
    const val COMPACT_BUTTON_SIZE_DP_VAL: Int = DesignTokens.COMPACT_BUTTON_SIZE_DP
    val compactButtonSizeDp: Dp = COMPACT_BUTTON_SIZE_DP_VAL.dp

    /** Hairline border stroke width: 1.0 dp. */
    const val HAIRLINE_STROKE_WIDTH_DP_VAL: Double = DesignTokens.HAIRLINE_STROKE_WIDTH_DP
    val hairlineStrokeWidthDp: Dp = HAIRLINE_STROKE_WIDTH_DP_VAL.dp

    // --- Animation tokens ---

    /** Physics spring stiffness: 380. */
    const val springStiffness: Float = DesignTokens.SPRING_STIFFNESS

    /** Physics spring damping coefficient: 28. */
    const val springDamping: Float = DesignTokens.SPRING_DAMPING

    /** Auto-dim and crossfade transition duration: 150 ms. */
    const val fadeTransitionMs: Int = DesignTokens.FADE_TRANSITION_MS

    /** A documented token's RGB + alpha as a Compose [Color]. */
    private fun tokenColour(rgb: Int, alpha: Float): Color = Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = alpha,
    )

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
