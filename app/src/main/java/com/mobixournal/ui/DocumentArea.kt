package com.mobixournal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Subtle scale lift on the pure-Compose entry layer; the SurfaceView editor never scales. */
private const val ENTRY_OVERLAY_SCALE_DELTA = 0.025f

/** Fast critically-damped springs tuned for snappy 120–144 Hz Home ↔ Editor transitions (~135–160 ms). */
private val EntryExitSpring = spring<Float>(dampingRatio = 1.0f, stiffness = 1400f, visibilityThreshold = 0.005f)
private val EntryEnterSpring = spring<Float>(dampingRatio = 1.0f, stiffness = 1100f, visibilityThreshold = 0.005f)

/**
 * The document area: **the entry screen when nothing is open, the editor when something is**, and
 * a snappy 144 Hz transition between them.
 *
 * Unlike `SurfaceView`, the pure-Compose entry layer animates cleanly with `graphicsLayer`. Keeping
 * the editor's `DrawingSurfaceView` steady at `scale = 1f` and `alpha = 1f` while `entry` clears
 * rapidly on top eliminates visual SurfaceView detachment, bounce, and frame-0 flashes.
 */
@Composable
internal fun DocumentArea(
    empty: Boolean,
    modifier: Modifier = Modifier,
    entry: @Composable () -> Unit,
    editor: @Composable () -> Unit,
) {
    val latestEmpty by rememberUpdatedState(empty)
    val entryProgress by animateFloatAsState(
        targetValue = if (empty) 1f else 0f,
        animationSpec = if (empty) EntryEnterSpring else EntryExitSpring,
        label = "DocumentAreaEntry",
    )
    val showEntry by remember {
        derivedStateOf { latestEmpty || entryProgress > 0.02f }
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
                        val p = entryProgress.coerceIn(0f, 1f)
                        alpha = if (latestEmpty) p else p * p
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
