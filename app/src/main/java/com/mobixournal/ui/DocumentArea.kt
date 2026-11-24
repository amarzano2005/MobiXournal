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

/**
 * The document area: **the entry screen when nothing is open, the editor when something is**, and
 * symmetric, stutter-free 144 Hz transitions between them.
 *
 * Entrance (Home → Editor, `p: 1 → 0`) and exit (Editor → Home, `p: 0 → 1`) are exact mathematical
 * duals: the editor stays steady underneath at `scale = 1f, alpha = 1f`, while the pure-Compose
 * entry overlay animates along the same curve (`alpha = p`, `scale = 1 + 0.025 * (1 - p)`) on an
 * offscreen GPU layer after waiting for view inflation (`withFrameNanos`).
 */
@Composable
internal fun DocumentArea(
    empty: Boolean,
    modifier: Modifier = Modifier,
    entry: @Composable () -> Unit,
    editor: @Composable () -> Unit,
) {
    val latestEmpty by rememberUpdatedState(empty)
    val entryProgress = remember { Animatable(if (empty) 1f else 0f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(empty) {
        if (!initialized) {
            initialized = true
            entryProgress.snapTo(if (empty) 1f else 0f)
            return@LaunchedEffect
        }
        // Let the arriving subtree finish inflating (EditorBody + SurfaceView on enter, or
        // NotebookEntryScreen offscreen layer on exit) before starting the spring on a clean vsync.
        withFrameNanos { }
        if (!empty) withFrameNanos { }
        entryProgress.animateTo(
            targetValue = if (empty) 1f else 0f,
            animationSpec = chromeSlideSpring(),
        )
    }

    val showEditor by remember {
        derivedStateOf { !latestEmpty || entryProgress.value < 0.99f }
    }
    val showEntry by remember {
        derivedStateOf { latestEmpty || entryProgress.value > 0.01f }
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
                        compositingStrategy = if (p < 0.999f || !latestEmpty) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                        alpha = p
                        val s = 1f + ENTRY_OVERLAY_SCALE_DELTA * (1f - p)
                        scaleX = s
                        scaleY = s
                    },
            ) {
                entry()
            }
        }
    }
}
