package com.mobixournal.desktop.shell

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

/** The tablet's own settle ([com.mobixournal.ui.DocumentArea] moves between the same two surfaces). */
private val EntranceSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * One slot on screen, arriving: the entry screen springs in when the last tab closes, and the page
 * springs in when a document opens, so neither swap is a cut.
 *
 * [key] is what counts as "the other surface" — the window passes whether it has a document open — and a
 * change of it replays the arrival. Only the surface that arrives is animated: the one leaving is drawn
 * from live state, and a closed document has nothing left to paint, so it is gone on the same frame.
 *
 * The tablet's [com.mobixournal.ui.DocumentArea] is the same move, in its own shell: the two modules share
 * `mobix-core` and no Compose code, so the shape of the move is written twice and the two shells read
 * alike.
 */
@Composable
internal fun ScreenEntrance(key: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    // Only a **change** of surface arrives: the first composition is already where the window belongs, and
    // animating it would make a document restored at startup start invisible.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
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
        content()
    }
}
