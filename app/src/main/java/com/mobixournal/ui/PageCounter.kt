package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A hazard-free "page X of Y" label. Uses coerceAtMost so it never throws when pageCount == 0.
 */
internal fun pageLabel(currentPage: Int, pageCount: Int): String =
    "${(currentPage + 1).coerceAtMost(pageCount)} / $pageCount"

/**
 * [zoom] as a whole percentage — `100%`, `125%`. The same rendering the rail's zoom popup shows,
 * so the badge and the popup can never disagree about what 100% looks like.
 */
fun zoomLabel(zoom: Float): String = "${(zoom * 100).roundToInt()}%"

/**
 * The badge's corner as a Compose [Alignment], from the two configured axes (Appearance settings).
 */
fun pageCounterAlignment(
    vertical: PageCounterVertical,
    horizontal: PageCounterHorizontal,
): Alignment = when (vertical) {
    PageCounterVertical.TOP -> when (horizontal) {
        PageCounterHorizontal.LEFT -> Alignment.TopStart
        PageCounterHorizontal.CENTER -> Alignment.TopCenter
        PageCounterHorizontal.RIGHT -> Alignment.TopEnd
    }
    PageCounterVertical.CENTER -> when (horizontal) {
        PageCounterHorizontal.LEFT -> Alignment.CenterStart
        PageCounterHorizontal.CENTER -> Alignment.Center
        PageCounterHorizontal.RIGHT -> Alignment.CenterEnd
    }
    PageCounterVertical.BOTTOM -> when (horizontal) {
        PageCounterHorizontal.LEFT -> Alignment.BottomStart
        PageCounterHorizontal.CENTER -> Alignment.BottomCenter
        PageCounterHorizontal.RIGHT -> Alignment.BottomEnd
    }
}

/**
 * The always-visible "page X of Y" badge.
 *
 * It sits over the canvas rather than in the top bar so it survives full-page mode, where the whole
 * bar is hidden. [currentPage] is 0-based (as [PaneState.currentPage] is); the label adds the 1.
 */
@Composable
fun PageCounter(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        tonalElevation = 3.dp,
    ) {
        Text(
            text = pageLabel(currentPage, pageCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The always-visible zoom badge: [PageCounter]'s twin, stacked directly above it so the two read as
 * one control rather than two loose chips. Tapping it resets the zoom to 100%, the same action as
 * the rail's zoom-popup percentage.
 */
@Composable
fun ZoomBadge(
    zoom: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    Surface(
        // Clip before the click so the ripple stays inside the badge's rounded corners.
        modifier = modifier.clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        tonalElevation = 3.dp,
    ) {
        Text(
            text = zoomLabel(zoom),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
