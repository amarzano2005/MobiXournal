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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mobixournal.design.DesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The floating glass's **arrival**, as the tablet already plays it.
 *
 * A panel does not fade in: it grows from 82 % of its size on an under-damped spring while its alpha
 * rises over 110 ms, and it draws back to that scale while fading out over 170 ms. On the tablet this is
 * [com.mobixournal.ui.theme.liquidGlassSpringAnimation]; this is the same movement, reading the same
 * `DesignTokens.GlassMotion` numbers, for the window's surfaces — which is the only way two shells can
 * claim one motion language while drawing in two modules.
 *
 * Every surface gets the full behaviour, the flyouts included: [GlassPopup] is the window's one pop-up,
 * and a hover tooltip is the pointer's readout rather than a surface the user fired
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

/**
 * The window's **one pop-up**: a [Popup] that arrives *and leaves* on the shared spring.
 *
 * A desktop `Popup` used to appear instantly because its caller composed it under `if (open)`: the content
 * left the composition between two frames the moment the flag cleared, so there was nothing to run a
 * closing half on, and the window showed its menus popping while the surfaces inside the shell sprang.
 * [GlassPresence] is the missing half — it holds the composition for
 * [DesignTokens.GlassMotion.EXIT_HOLD_MS] and tells the panel inside that it is closing — and this is the
 * only way the window opens one: `GlassPopup(visible = open, offset = …, onDismissRequest = …) { Panel() }`.
 *
 * The spring is applied **outside** the panel's fill ([Modifier.glassSpring] wraps the content, whose own
 * `glassSurface()` draws the glass): an alpha layer placed after a fill fades the empty space under the
 * panel and leaves the panel itself opaque and full-sized.
 *
 * A hover tooltip is not this — it is the pointer's readout, not a surface the user fired
 * (`docs/interface-contract.md` §7).
 */
@Composable
internal fun GlassPopup(
    visible: Boolean = !LocalGlassClosing.current,
    onDismissRequest: () -> Unit,
    offset: IntOffset,
    properties: PopupProperties = FloatingSurface,
    content: @Composable () -> Unit,
) {
    GlassPresence(visible) {
        Popup(offset = offset, onDismissRequest = onDismissRequest, properties = properties) {
            Box(Modifier.glassSpring()) { content() }
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
 * The family's arrival on a surface that composes inside the shell: scale from
 * [DesignTokens.GlassMotion.START_SCALE] to 1 with the shared spring, and alpha over
 * [DesignTokens.GlassMotion.FADE_IN_MS].
 *
 * The origin is the panel's centre, so a centred panel grows in place; a surface anchored to a corner
 * passes its own.
 */
@Composable
fun Modifier.glassSpring(
    visible: Boolean = !LocalGlassClosing.current,
    origin: TransformOrigin = TransformOrigin(0.5f, 0.5f),
): Modifier {
    val start = DesignTokens.GlassMotion.START_SCALE
    val scale = remember { Animatable(if (visible) start else 1f) }
    val alpha = remember { Animatable(if (visible) 0f else 1f) }

    LaunchedEffect(visible) {
        if (visible) {
            scale.snapTo(start)
            alpha.snapTo(0f)
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
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
            // The closing half runs backwards to the same scale, inside the hold GlassPresence keeps.
            launch {
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
