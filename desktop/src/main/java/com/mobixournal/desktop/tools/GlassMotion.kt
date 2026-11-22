package com.mobixournal.desktop.tools
import com.mobixournal.desktop.ink.of

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mobixournal.design.DesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The floating glass's **arrival**, as the tablet already plays it.
 *
 * Motion is differentiated across [DesignTokens.GlassMotion.SurfaceRole]s on near-critically
 * damped springs (no splash overshoot), reading the same `DesignTokens.GlassMotion` numbers as
 * [com.mobixournal.ui.theme.liquidGlassSpringAnimation] — which is the only way two shells can
 * claim one motion language while drawing in two modules.
 *
 * This motion applies to in-shell surfaces. Native menus use [GlassPopup] without scale or fade;
 * a hover tooltip is the pointer's readout rather than a surface the user fired
 * (`docs/interface-contract.md` §7 records that exception).
 */

/** Whether the glass currently being composed is on its way **out** ([GlassPresence] provides it). */
val LocalGlassClosing = compositionLocalOf { false }

/**
 * Keeps an in-shell surface composed for as long as its exit takes.
 *
 * Without it, `if (open) { Panel() }` tears the panel down between two frames the instant `open` clears:
 * the arrival plays in full and the departure does not exist. Gate the call with this instead, and the
 * content hears [LocalGlassClosing] (which [glassSpring] reads as "not visible") and is released after
 * [DesignTokens.GlassMotion.EXIT_HOLD_MS] — the same hold the tablet's `DialogPresence` keeps a dialog
 * window for.
 */
@Composable
fun GlassPresence(visible: Boolean, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(visible) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            closing = false
        } else if (shown) {
            closing = true
            delay(DesignTokens.GlassMotion.EXIT_HOLD_MS.toLong())
            shown = false
            closing = false
        }
    }
    if (shown) {
        CompositionLocalProvider(LocalGlassClosing provides closing) { content() }
    }
}

/** Native desktop menus appear immediately at full size and opacity, with the caller's input policy. */
@Composable
internal fun GlassPopup(
    visible: Boolean = !LocalGlassClosing.current,
    onDismissRequest: () -> Unit,
    offset: IntOffset,
    properties: PopupProperties = FloatingSurface,
    content: @Composable () -> Unit,
) {
    // A native desktop menu covers its final rectangle on its first frame. Scaling/fading its
    // content inside that window exposes the controls underneath and makes the menu look clipped.
    if (visible) {
        Popup(offset = offset, onDismissRequest = onDismissRequest, properties = properties) {
            content()
        }
    }
}

/**
 * The subject of a pop-up gated on a nullable state (`flag != null`).
 *
 * [GlassPresence] keeps a surface composed while it closes, and by then the state that opened it is
 * already null — so the panel needs the value it was opened *with*. This holds the last non-null one for
 * exactly as long as that composition lives: no state write, so it cannot loop a recomposition, and the
 * value is forgotten the moment the window really goes. The tablet's `rememberClosingArgument` is the
 * same idea on the other shell.
 */
@Composable
internal fun <T : Any> rememberClosingValue(value: T?): T? {
    val held = remember { HeldValue<T>() }
    if (value != null) held.value = value
    return held.value
}

/** A plain cell [rememberClosingValue] writes into — deliberately not snapshot state. */
private class HeldValue<T> {
    var value: T? = null
}

/**
 * The family's arrival on a surface that composes inside the shell: scale from [role]'s
 * `startScale` to 1 alongside its anchor glide on the shared spring, and alpha over `fadeInMs`.
 *
 * The origin is the panel's centre, so a centred panel scales in place; a surface anchored to a corner
 * or bottom edge passes its own.
 */
@Composable
fun Modifier.glassSpring(
    visible: Boolean = !LocalGlassClosing.current,
    origin: TransformOrigin = TransformOrigin(0.5f, 0.5f),
    role: DesignTokens.GlassMotion.SurfaceRole = DesignTokens.GlassMotion.SurfaceRole.POPOVER,
): Modifier {
    val spec = DesignTokens.GlassMotion.spec(role)
    val slidePx = with(LocalDensity.current) { spec.slideDp.dp.toPx() }
    val progress = remember { Animatable(if (visible) 0f else 1f) }
    val alpha = remember { Animatable(if (visible) 0f else 1f) }

    LaunchedEffect(visible, role) {
        if (visible) {
            progress.snapTo(0f)
            alpha.snapTo(0f)
            launch {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = spec.openDamping,
                        stiffness = spec.openStiffness,
                    ),
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = spec.fadeInMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        } else {
            // The closing half runs backwards inside the hold GlassPresence keeps.
            launch {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = spec.closeDamping,
                        stiffness = spec.closeStiffness,
                    ),
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = spec.fadeOutMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    return this.graphicsLayer {
        val p = progress.value
        val s = spec.startScale + (1f - spec.startScale) * p
        scaleX = s
        scaleY = s
        translationY = DesignTokens.GlassMotion.slideDirectionSign(role, origin.pivotFractionY) * slidePx * (1f - p)
        this.alpha = alpha.value
        transformOrigin = origin
    }
}
