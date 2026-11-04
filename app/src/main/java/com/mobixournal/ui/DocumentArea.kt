package com.mobixournal.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/** How far back a surface sits before it settles: just under full size, so the move reads as an arrival. */
private const val ENTRANCE_FROM_SCALE = 0.94f

/** The fade is shorter than the spring on purpose: the shape settles after the surface is already there. */
private const val ENTRANCE_FADE_MS = 160

/** The app's own settle — a little under critical damping, so the surface arrives rather than springs. */
private val EntranceSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * The document area: **the entry screen when nothing is open, the page when something is**, and the move
 * between the two.
 *
 * A sheet opens by growing into the window from [ENTRANCE_FROM_SCALE] while it fades in, and closing the
 * last tab plays the same arrival for the entry screen — so leaving a sheet lands back on the screen you
 * started from instead of cutting to it. Only the surface that is arriving is animated: the one leaving
 * is gone on the same frame the state changes, because it is drawn from live state (a closed document has
 * nothing left to paint) and a half-configured page is worse than a cut.
 *
 * The two surfaces are slots rather than flags or a long argument list: the editor's own parameters are
 * `EditorScreen`'s business, and this file owns only which one is on screen and how it arrives.
 */
@Composable
internal fun DocumentArea(
    empty: Boolean,
    modifier: Modifier = Modifier,
    entry: @Composable () -> Unit,
    editor: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    // Only a **change** of surface arrives: the first composition is already where the window belongs, and
    // animating it would make a restored document — or a test's own first frame — start invisible.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(empty) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        scale.snapTo(ENTRANCE_FROM_SCALE)
        alpha.snapTo(0f)
        launch { scale.animateTo(1f, EntranceSpring) }
        alpha.animateTo(1f, tween(ENTRANCE_FADE_MS))
    }
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
    ) {
        if (empty) entry() else editor()
    }
}
