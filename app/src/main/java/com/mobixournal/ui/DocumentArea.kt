package com.mobixournal.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/** Subtle scale lift on the pure-Compose entry layer; the SurfaceView editor never scales. */
private const val ENTRY_OVERLAY_SCALE_DELTA = 0.025f

/** Two-phase crossfade boundaries so the Home screen and editor rails never overlap visibly. */
private fun homeLayerAlpha(progress: Float): Float = ((progress - 0.40f) / 0.60f).coerceIn(0f, 1f)
private fun editorChromeReveal(progress: Float): Float = ((0.48f - progress) / 0.48f).coerceIn(0f, 1f)

/**
 * The document area: **the entry screen when nothing is open, the editor when something is**, and
 * symmetric, two-phase 144 Hz transitions between them.
 *
 * Entrance (Home → Editor, `p: 1 → 0`) fades Home out first (`p: 1 → 0.40`) over the steady sheet
 * and then reveals the top bar and side rail (`p: 0.48 → 0`); exit (Editor → Home, `p: 0 → 1`)
 * plays the exact dual in reverse so the rails never bleed through the Home cards.
 */
@Composable
internal fun DocumentArea(
    empty: Boolean,
    modifier: Modifier = Modifier,
    entry: @Composable () -> Unit,
    editor: @Composable () -> Unit,
) {
    val controller = LocalChromeInsetController.current
    val latestEmpty by rememberUpdatedState(empty)
    val entryProgress = remember { Animatable(if (empty) 1f else 0f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(empty, controller) {
        if (!initialized) {
            initialized = true
            val initial = if (empty) 1f else 0f
            entryProgress.snapTo(initial)
            controller.entryReveal = editorChromeReveal(initial)
            return@LaunchedEffect
        }
        // Keep rails hidden while EditorBody + SurfaceView mount on enter, or let Home prepare its
        // offscreen layer on exit, before starting the spring on a clean vsync.
        withFrameNanos { }
        if (!empty) withFrameNanos { }
        entryProgress.animateTo(
            targetValue = if (empty) 1f else 0f,
            animationSpec = chromeSlideSpring(),
        ) {
            controller.entryReveal = editorChromeReveal(value.coerceIn(0f, 1f))
        }
        controller.entryReveal = if (empty) 0f else 1f
    }

    val showEditor by remember {
        derivedStateOf { !latestEmpty || entryProgress.value < 0.99f }
    }
    val showEntry by remember {
        derivedStateOf { latestEmpty || entryProgress.value > 0.40f }
    }

    Box(modifier = modifier) {
        if (showEditor) {
            editor()
        }
        if (showEntry) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = entryProgress.value.coerceIn(0f, 1f)
                        val a = homeLayerAlpha(p)
                        compositingStrategy = if (a < 0.999f || !latestEmpty) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                        alpha = a
                        val s = 1f + ENTRY_OVERLAY_SCALE_DELTA * (1f - a)
                        scaleX = s
                        scaleY = s
                    },
            ) {
                entry()
            }
        }
    }
}
