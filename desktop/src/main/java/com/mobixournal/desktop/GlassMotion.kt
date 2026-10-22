package com.mobixournal.desktop

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
 * The window's flyouts live in a `Popup` and still appear instantly: a desktop popup has no composition
 * of its own to hold open after its flag clears, so there is no exit to play — the honest state, kept
 * rather than faked. The surfaces drawn **inside** the shell (the colour panel first) get the full
 * behaviour through [GlassPresence].
 */

/** Whether the glass currently being composed is on its way **out** ([GlassPresence] provides it). */
val LocalGlassClosing = compositionLocalOf { false }

/**
 * Keeps an in-shell surface composed for as long as its exit takes.
 *
 * Without it, `if (open) { Panel() }` tears the panel down between two frames the instant `open` clears:
 * the arrival plays in full and the departure does not exist. Gate the call with this instead, and the
 * content hears [LocalGlassClosing] (which [glassSpring] reads as "not visible") and is released after
 * [DesignTokens.GlassMotion.EXIT_HOLD_MS] — the same 230 ms the tablet's `DialogPresence` holds a dialog
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
