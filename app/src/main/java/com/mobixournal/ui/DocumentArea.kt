package com.mobixournal.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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

/** Symmetric two-phase crossfade boundaries so Home never visually overlaps the sheet or rails. */
private fun homeLayerAlpha(progress: Float): Float = ((progress - 0.42f) / 0.58f).coerceIn(0f, 1f)
private fun editorChromeReveal(progress: Float): Float = ((0.46f - progress) / 0.46f).coerceIn(0f, 1f)
private fun editorVeilAlpha(progress: Float): Float = (progress / 0.46f).coerceIn(0f, 1f)

/**
 * The document area: **the entry screen when nothing is open, the editor when something is**, and
 * symmetric, two-phase 144 Hz transitions between them.
 *
 * Entrance (Home → Editor, `p: 1 → 0`) fades Home out first (`p: 1 → 0.42`) over a clean theme
 * backdrop scrim and then unveils the sheet together with the top bar and side rail (`p: 0.46 → 0`);
 * exit (Editor → Home, `p: 0 → 1`) plays the exact dual in reverse so neither the sheet nor the
 * rails ever overlap the Home screen.
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
        derivedStateOf { !latestEmpty || entryProgress.value < 0.48f }
    }
    val showVeil by remember {
        derivedStateOf { showEditor && entryProgress.value > 0.005f }
    }
    val showEntry by remember {
        derivedStateOf { latestEmpty || entryProgress.value > 0.42f }
    }
    val backdropColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.background(backdropColor)) {
        if (showEditor) {
            editor()
        }
        if (showVeil) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = editorVeilAlpha(entryProgress.value.coerceIn(0f, 1f)) }
                    .background(backdropColor),
            )
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
