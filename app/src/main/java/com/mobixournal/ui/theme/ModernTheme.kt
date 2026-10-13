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
}
