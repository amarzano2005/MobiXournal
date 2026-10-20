package com.mobixournal.ui

import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun pageLabel(currentPage: Int, pageCount: Int): String =
    "${(currentPage + 1).coerceAtMost(pageCount)} / $pageCount"

/**
 * What the badge reads on an infinite canvas: the **tile** the view is centred on, as its coordinate.
 *
 * There is no "page X of Y" on a board — its parts are ordered by the file rather than by where they
 * sit, so the only positional fact that is both true and useful is where you are, and a coordinate is
 * also how you would say it out loud ("I wrote it around tile 1, −1"). The tile is shown whether or not
 * it has ink yet: on an infinite canvas looking at an empty cell is exactly how you find the next one to
 * write in (`docs/chrome.md` → *A document with no pages*).
 */
internal fun tileLabel(cell: Pair<Int, Int>): String = "${cell.first}, ${cell.second}"

/**
 * [zoom] as a whole percentage — `100%`, `125%`. The same rendering the rail's zoom popup shows,
 * so the badge and the popup can never disagree about what 100% looks like.
 */
fun zoomLabel(zoom: Float): String = "${(zoom * 100).roundToInt()}%"

/**
 * The badges' corner as a Compose [Alignment]: the bottom row of the canvas, on the edge **opposite
 * the Main Toolbar**.
 *
 * There is no placement setting behind this any more. The rail owns one vertical edge already, so
 * the only side that is actually free is the other one — and it is also the side the writing hand is
 * not on, which is the whole point of a handedness choice: dock the rail left (right-handed) and the
 * badges go bottom-right; dock it right (left-handed) and they move bottom-left to match. One rule,
 * and the pair never sits under the tools or under the palm.
 */
fun pageCounterAlignment(toolbarPosition: ToolbarPosition): Alignment = when (toolbarPosition) {
    ToolbarPosition.LEFT -> Alignment.BottomEnd
    ToolbarPosition.RIGHT -> Alignment.BottomStart
}

/**
 * The always-visible position badge: "page X of Y" on a page list, the tile's coordinate on a board.
 *
 * It sits over the canvas rather than in the top bar so it survives full-page mode, where the whole bar
 * is hidden. [currentPage] is 0-based (as [PaneState.currentPage] is); the label adds the 1. [tile] is
 * the board's own reading of where the view is, and takes over from the page label when it is non-null:
 * the two are never shown together, because a document has one geometry or the other.
 */
@Composable
fun PageCounter(
    currentPage: Int,
    pageCount: Int,
    tile: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(BADGE_CORNER)
    Surface(
        modifier = modifier,
        shape = shape,
        color = rememberFloatingSurfaceColor(),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = if (tile != null) tileLabel(tile) else pageLabel(currentPage, pageCount),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Corner of the frosted pill both badges wear. */
private val BADGE_CORNER = 14.dp

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
    val shape = RoundedCornerShape(BADGE_CORNER)
    Surface(
        // Clip before the click so the ripple stays inside the badge's rounded corners.
        modifier = modifier.clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = rememberFloatingSurfaceColor(),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = zoomLabel(zoom),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
