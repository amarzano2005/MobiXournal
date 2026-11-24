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
 * a stutter-free 144 Hz transition between them.
 *
 * Unlike `SurfaceView`, the pure-Compose entry layer animates cleanly with `graphicsLayer`. When
 * transitioning from Home to Editor, we let `editor()` inflate its `DrawingSurfaceView` and post
 * its first frame underneath the opaque entry overlay (`withFrameNanos`) before starting
 * [chromeSlideSpring], so the spring clock never loses its opening frames to view inflation.
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
        if (!empty) {
            // Let EditorBody + DrawingSurfaceView mount and complete first surfaceChanged/paint
            // while entry() is still opaque on top, then start the spring on a clean vsync.
            withFrameNanos { }
            withFrameNanos { }
        }
        entryProgress.animateTo(
            targetValue = if (empty) 1f else 0f,
            animationSpec = chromeSlideSpring(),
        )
    }

    val showEntry by remember {
        derivedStateOf { latestEmpty || entryProgress.value > 0.01f }
    }

    Box(modifier = modifier) {
        if (!empty) {
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
                        val s = if (latestEmpty) {
                            1f - ENTRY_OVERLAY_SCALE_DELTA * (1f - p)
                        } else {
                            1f + ENTRY_OVERLAY_SCALE_DELTA * (1f - p)
                        }
                        scaleX = s
                        scaleY = s
                    },
            ) {
                entry()
            }
        }
    }
}
