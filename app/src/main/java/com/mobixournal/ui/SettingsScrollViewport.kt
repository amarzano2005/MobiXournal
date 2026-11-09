package com.mobixournal.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt

/**
 * A settings page's scrolling viewport: the state that moves the page and where its visible part sits on
 * screen.
 *
 * Controls inside a page need both to walk the page under a drag — the palette's auto-scroll reads the
 * state and the window bounds, because its rows are part of the page rather than a list of their own.
 * Bounds are in **window** pixels, the same space [androidx.compose.ui.layout.LayoutCoordinates.positionInWindow]
 * answers in, so a row's place and the strip it enters are measured from one origin.
 */
internal class SettingsScrollViewport(val state: ScrollState) {

    /** The top of the visible page in window pixels. */
    var topPx by mutableIntStateOf(0)
        private set

    /** The bottom of the visible page in window pixels. */
    var bottomPx by mutableIntStateOf(0)
        private set

    /** How much of the page is on screen, in pixels; zero until the page is first laid out. */
    val heightPx: Int get() = bottomPx - topPx

    fun bounds(top: Int, bottom: Int) {
        topPx = top
        bottomPx = bottom
    }
}

/**
 * The viewport of the settings page being composed, or null outside one (a dialog, a preview).
 *
 * The palette's drag asks for it; a host that never provides one simply does not auto-scroll, which is the
 * right answer anywhere the rows are not inside a scrolling page.
 */
internal val LocalSettingsScrollViewport = compositionLocalOf<SettingsScrollViewport?> { null }

/** A scroll state for a settings page, plus the viewport that reports where its visible part sits. */
@Composable
internal fun rememberSettingsScrollViewport(): SettingsScrollViewport {
    val state = rememberScrollState()
    return remember(state) { SettingsScrollViewport(state) }
}

/**
 * Report this node's window bounds to [viewport]. Place it on the node the scrolling clips — the page's
 * own box, before `verticalScroll` — so the strips are the visible edges, not the content's edges.
 */
internal fun Modifier.settingsViewport(viewport: SettingsScrollViewport): Modifier =
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        viewport.bounds(bounds.top.roundToInt(), bounds.bottom.roundToInt())
    }
