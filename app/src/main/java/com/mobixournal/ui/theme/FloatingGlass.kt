package com.mobixournal.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    // Pure uniform transparency: no perimeter stroke is drawn, eliminating any opaque border demarcation
    return this
}

/** [floatingRim]'s stroke stub, kept for API compatibility with pure uniform transparency. */
internal fun DrawScope.drawFloatingRim(cornerRadiusPx: Float, rim: Color) {
    // Pure uniform transparency: no perimeter strokes to maintain 100% homogeneous translucency
}

/** The rim's thickness token — zero in pure uniform transparency mode. */
internal val MENU_RIM_WIDTH = 0.dp

/** CompositionLocal providing the liquid glass opening animation preference. */
val LocalLiquidGlassAnimations = compositionLocalOf { true }

/**
 * Apple-style liquid glass opening animation for popups, menus, and dialogs.
 * Smoothly scales from 0.85 with fluid spring physics (overshooting softly) and gentle fade-in.
 * Can be disabled by user preference via [LocalLiquidGlassAnimations].
 */
@Composable
fun Modifier.liquidGlassPopupAnimation(
    enabled: Boolean = LocalLiquidGlassAnimations.current,
): Modifier {
    if (!enabled) return this

    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.68f, // Fluid Apple-style spring with subtle liquid bounce
                    stiffness = 450f,
                ),
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 140),
            )
        }
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
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
            // The modifier lands on the dialog's own `Surface`, which is what lets the rim be drawn
            // on the panel's bounds.
            modifier = modifier.floatingRim().liquidGlassPopupAnimation(),
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
