package com.mobixournal.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.design.DesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The **one edge** a floating surface wears.
 *
 * The fill that ties a pop-up to its bar is also what lets it dissolve into the surface it hangs
 * over: over the true-black desk, a charcoal panel at [ModernTheme.floatingSurface]'s alpha is a
 * panel with nothing between it and the desk behind. The cue is this hairline, drawn in the ambient
 * surface's own ink ([rememberMenuRimColor]) — so it is an edge in the light theme and in the dark
 * one — *after* the surface's content, so it reads as the panel's own boundary rather than as
 * something behind it, and shaded along its length (bright at the top, faint at the bottom) so the
 * panel reads as lit from above instead of as an outlined box.
 *
 * It lives here, once, because every floating surface draws it: the pop-ups (`ToolbarMenu`), the
 * action bars ([FloatingSurface]) and the STEM editors ([FloatingDialog]) used to be one material
 * with **three** edges — a rim on the menus, a flat 1dp border on the selection bar and nothing at
 * all on the table/spline/region/text bars, which is what made the table bar read as a plate lying
 * on the page rather than a pane of glass over it.
 *
 * A raised shadow is deliberately **not** the cue for a pop-up: its window is only as large as the
 * panel, so the blur has nowhere to fall and lands *inside* the panel's own top edge (measured on
 * the device: a 14dp elevated menu painted a grey wash over its first rows). A rim reads the same on
 * all four edges. The bars keep their shadow, because they live in the window and have room for it.
 */
@Composable
internal fun Modifier.floatingRim(
    cornerRadius: Dp = ModernTheme.panelCornerRadiusDp,
): Modifier {
    val rim = rememberMenuRimColor()
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    return drawWithContent {
        drawContent()
        drawFloatingRim(radiusPx, rim)
    }
}

/** [floatingRim]'s stroke, for a surface that already holds its own ink and radius. */
internal fun DrawScope.drawFloatingRim(cornerRadiusPx: Float, rim: Color) {
    val width = MENU_RIM_WIDTH.toPx()
    // Half a hairline in from the edge: a stroke straddles its path, and the outer half would be
    // wasted on the corner's own curve.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                rim.copy(alpha = MENU_RIM_ALPHA_TOP),
                rim.copy(alpha = MENU_RIM_ALPHA_BOTTOM),
            ),
            startY = 0f,
            endY = size.height,
        ),
        topLeft = Offset(width / 2f, width / 2f),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(cornerRadiusPx),
        style = Stroke(width = width),
    )
    // Soft inner transition that feathers the outer border seamlessly into the transparent central fill
    val innerWidth = width * DesignTokens.RIM_INNER_WIDTH_SCALE
    val innerInset = width + innerWidth / 2f
    if (size.width > innerInset * 2 && size.height > innerInset * 2) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    rim.copy(alpha = MENU_RIM_ALPHA_TOP * DesignTokens.RIM_INNER_ALPHA_SCALE),
                    rim.copy(alpha = MENU_RIM_ALPHA_BOTTOM * DesignTokens.RIM_INNER_ALPHA_SCALE),
                ),
                startY = 0f,
                endY = size.height,
            ),
            topLeft = Offset(innerInset, innerInset),
            size = Size(size.width - innerInset * 2, size.height - innerInset * 2),
            cornerRadius = CornerRadius(maxOf(0f, cornerRadiusPx - width)),
            style = Stroke(width = innerWidth),
        )
    }
}

/** The rim's thickness — one hairline, drawn inside the surface's edge (a shared token). */
internal val MENU_RIM_WIDTH = DesignTokens.RIM_WIDTH_DP.dp

/**
 * How long a floating surface takes to **draw back** when it closes: the closing spring settles in
 * about 200 ms and the fade lands at 170 ms, so the window is held for 230 ms — long enough for the
 * exit to read (a shorter hold played it out in ~8 frames, which looked exactly like a snap).
 */
const val FLOATING_EXIT_MS = DesignTokens.GlassMotion.EXIT_HOLD_MS

/**
 * Whether the floating surface being composed is on its way **out**. [DialogPresence] provides it while
 * a dialog plays its closing half, so [liquidGlassSpringAnimation] can run the animation backwards
 * before the window is taken away.
 */
val LocalFloatingSurfaceClosing = compositionLocalOf { false }

/**
 * Apple-style liquid glass opening and closing animation for popups, menus, and dialogs: a fluid,
 * organic spring on the scale with an alpha fade. Always on — it is the app's own motion language,
 * not a preference, so there is no switch to turn it off.
 */
@Composable
fun Modifier.liquidGlassSpringAnimation(
    expanded: Boolean = true,
    origin: TransformOrigin = TransformOrigin(0f, 0f),
): Modifier {
    val start = DesignTokens.GlassMotion.START_SCALE
    val scale = remember { Animatable(if (expanded) start else 1f) }
    val alpha = remember { Animatable(if (expanded) 0f else 1f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            scale.snapTo(start)
            alpha.snapTo(0f)
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        // Apple iOS fluid spring: rapid release with organic deceleration and cushioned
                        // snap (no cartoon bouncing). The numbers are the shared glass tokens.
                        dampingRatio = DesignTokens.GlassMotion.OPEN_DAMPING_RATIO,
                        stiffness = DesignTokens.GlassMotion.OPEN_STIFFNESS,
                    ),
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = DesignTokens.GlassMotion.FADE_IN_MS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        } else {
            // The closing half, run backwards: it draws back to the same 0.82 scale and fades out. Both
            // land inside [FLOATING_EXIT_MS], the hold the host keeps the window for — a spring that
            // settled later would have its tail cut off by the window going away.
            launch {
                // A spring on the way out too, so the panel reads as drawing back rather than as being
                // cut: lightly damped, so it settles in ~200 ms without a visible bounce.
                scale.animateTo(
                    targetValue = start,
                    animationSpec = spring(
                        dampingRatio = DesignTokens.GlassMotion.CLOSE_DAMPING_RATIO,
                        stiffness = DesignTokens.GlassMotion.CLOSE_STIFFNESS,
                    ),
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = DesignTokens.GlassMotion.FADE_OUT_MS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
        transformOrigin = origin
    }
}

/**
 * The one **floating surface**: the bars' glass
 * ([rememberFloatingSurfaceColor]) cut at the floating corner
 * ([ModernTheme.panelCornerRadiusDp]), with [floatingRim]'s edge and a shadow it has room to cast.
 *
 * Use it for everything the chrome puts *over* the page that is **not** a Material menu or dialog —
 * the action bars that come up with a selection and with the table/spline/PDF-text/region tools.
 * The corners are the point as much as the fill: those bars used to ask for
 * `MaterialTheme.shapes.large` (16dp) or a 50% pill while every menu, badge and dialog turned at
 * 20dp, so "one floating surface, one corner" was true everywhere except where the user actually
 * touched it.
 */
@Composable
fun FloatingSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ModernTheme.panelCornerRadiusDp),
    shadowElevation: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(shadowElevation, shape, clip = false)
            .clip(shape)
            .background(rememberFloatingSurfaceColor())
            .floatingRim(),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/**
 * A Material [AlertDialog] wearing the **floating material** — the same fill, tint cancelled and
 * the same rim the menus and the action bars wear — so a STEM editor is not the one panel in the
 * app that is a plain card while everything around it is glass.
 *
 * The container colour comes from `DialogTokens.ContainerColor`, which resolves to the **ambient**
 * `colorScheme.surface`, so overriding `surface` inside this scope is what makes Material's own
 * dialog shell take the floating fill: there is no `containerColor` on the public `AlertDialog`
 * overload this app uses. `surfaceTint` is pinned to the same colour for the same reason it is in
 * [com.mobixournal.ui.ToolbarMenu] — the container's elevation is painted as a tint wash, and a
 * wash of the fill over the fill is a no-op.
 *
 * The dialog keeps its own scrim, so it is *nearly* the same picture as an opaque card over a
 * dimmed page; what changes is that the panel is now the app's material rather than Material's
 * default one, and it carries the family's edge.
 */
@Composable
fun FloatingDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    val surface = rememberFloatingSurfaceColor()
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = scheme.copy(surface = surface, surfaceTint = surface)) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            containerColor = surface,
            modifier = modifier
                .floatingRim()
                .liquidGlassSpringAnimation(
                    expanded = !LocalFloatingSurfaceClosing.current,
                    origin = TransformOrigin(0.5f, 0.5f),
                ),
            properties = properties,
            // No tonal wash: with `surface` and `surfaceTint` the same colour the RGB would survive it,
            // but the composite still stacks the tint's alpha over the fill's and lands the panel a
            // shade more opaque than the token. Zero elevation keeps the material exact.
            tonalElevation = 0.dp,
            icon = icon,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
}

/**
 * Keeps a dialog's content **composed** for as long as its closing animation takes.
 *
 * A dialog lives in its own window, and Compose tears that window down the instant the caller stops
 * composing it — so a `visible` flag that flips to false used to make the panel vanish mid-frame while
 * its *opening* spring played in full. [DialogPresence] is the missing half: it watches the flag, tells
 * the surface inside that it is closing ([LocalFloatingSurfaceClosing], which
 * [liquidGlassSpringAnimation] reads as "expanded = false"), waits out [FLOATING_EXIT_MS], and only
 * then lets the content go.
 *
 * Callers gate a dialog with it instead of `if (visible) { … }`:
 *
 * ```
 * DialogPresence(ui.showSaveAs) { SaveAsDialog(…) }
 * ```
 *
 * Nothing else about the dialog changes — it still composes immediately when the flag turns true, and
 * the scrim still swallows taps for the exit's ~130 ms, which is the interval in which the panel is
 * visibly leaving.
 */
@Composable
fun DialogPresence(visible: Boolean, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(visible) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            closing = false
        } else if (shown) {
            closing = true
            delay(FLOATING_EXIT_MS.toLong())
            shown = false
            closing = false
        }
    }
    if (shown) {
        CompositionLocalProvider(LocalFloatingSurfaceClosing provides closing) { content() }
    }
}

/**
 * The subject of a dialog gated on a nullable state (`flag != null`).
 *
 * [DialogPresence] keeps a dialog composed while it closes, and by then the flag has already cleared —
 * so the dialog needs the value it was opened *with*, not the one the state holds now. This holds the
 * last non-null one for exactly as long as that composition lives: no state write, so it cannot loop a
 * recomposition, and the value is forgotten the moment the window really goes.
 */
@Composable
fun <T : Any> rememberClosingArgument(value: T?): T? {
    val held = remember { HeldValue<T>() }
    if (value != null) held.value = value
    return held.value
}

/** A plain cell [rememberClosingArgument] writes into — deliberately not snapshot state. */
private class HeldValue<T> {
    var value: T? = null
}
