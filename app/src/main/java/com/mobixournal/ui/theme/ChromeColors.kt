package com.mobixournal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The chrome colours the canvas needs, as plain ARGB ints.
 *
 * The canvas is a classic `SurfaceView` living outside the Compose tree, so it can't read
 * `MaterialTheme` itself. This is the one place that maps the app's [androidx.compose.material3.ColorScheme]
 * onto the canvas' non-Compose chrome, so there is still a single colour scheme driving every surface.
 * Ink, page backgrounds and the pen palette are *document data*, not chrome, and are deliberately absent.
 */
data class CanvasChromeColors(
    /** The surround behind the pages — [rememberToolbarColor], the material the toolbars are made of. */
    val backdrop: Int,
    /**
     * Hairline traced around each sheet. With the surround painted the same colour as the bars, this is
     * what still tells the eye where a page begins when the chrome colour is light (light theme) — the
     * sheet is the document's own near-white, the chrome may be near-white too.
     */
    val pageOutline: Int,
    /** Selection marquee, resize/rotate handles and their arms. */
    val selection: Int,
    /** Outline and grab handles of the setsquare/compass overlay. */
    val guide: Int,
)

/**
 * The bars' colour: the top bar (the `Scaffold`'s container under the transparent `TopAppBar`), the tab
 * strip, and both Android system bars.
 *
 * These used to be three different neutrals — the tab strip sat one tonal step up from the bar it hangs
 * under. Three dark greys stacked down the screen read as three unrelated surfaces rather than one frame
 * around the document, so there is now a single value and all of them take it. Put a new *bar* surface on
 * this, not on a `surfaceContainer*` role, or the bands come back.
 *
 * `background` is the role because that is what the `Scaffold` under the transparent top bar already
 * painted — so the bar the user sees is unchanged, and the strip and the system bars moved to meet it.
 *
 * The toolbars and the canvas surround are deliberately one step up from this, on
 * [rememberToolbarColor].
 */
@Composable
fun rememberChromeColor(): Color = MaterialTheme.colorScheme.background

/**
 * The toolbars' colour: the fill of the Main Toolbar rail and of the Secondary Toolbar's floating dock.
 *
 * This is the app's *implement* colour — the material the tools are made of — and it is the one the
 * canvas surround is painted with, so the desk a page lies on reads as the same material as the tools
 * floating over it rather than as a darker void behind them. The docks keep it as their fill because the
 * bars above stay on [rememberChromeColor]: one tonal step is what makes a floating dock read as floating
 * instead of dissolving into the bar it hangs from.
 *
 * Rail, dock and surround all read this, so they cannot drift apart.
 */
@Composable
fun rememberToolbarColor(): Color = MaterialTheme.colorScheme.surfaceContainer

/**
 * The ink the pop-up menus' rim is drawn with: the surface's own ink, so the edge is dark in the
 * light theme and light in the dark one — an edge in both, rather than one.
 *
 * The menus take the toolbars' own fill ([rememberToolbarColor]), which is what makes a pop-up read
 * as part of the bar it belongs to — but that also means, over the light canvas, a menu's edge can
 * vanish into the desk. This ink drawn around the panel is what keeps the two readable as two
 * layers. How strong it is at each end is [MENU_RIM_ALPHA_TOP] / [MENU_RIM_ALPHA_BOTTOM]: the stroke
 * runs in a vertical gradient, brightest along the top, so the panel reads as a lit plate rather
 * than as an outlined box. Both ends are faint by design — a rim strong enough to notice as a line
 * is a border.
 */
@Composable
fun rememberMenuRimColor(): Color = MaterialTheme.colorScheme.onSurface

/** Strength of the pop-up rim along the panel's lit top edge. */
const val MENU_RIM_ALPHA_TOP = 0.30f

/** Strength of the pop-up rim along the panel's shaded bottom edge. */
const val MENU_RIM_ALPHA_BOTTOM = 0.10f

/** Derives the canvas chrome colours from the ambient Material 3 scheme. */
@Composable
fun rememberCanvasChromeColors(): CanvasChromeColors {
    val scheme = MaterialTheme.colorScheme
    val toolbar = rememberToolbarColor()
    return remember(scheme, toolbar) {
        CanvasChromeColors(
            backdrop = toolbar.toArgb(),
            pageOutline = scheme.outlineVariant.toArgb(),
            selection = scheme.primary.toArgb(),
            guide = scheme.secondary.toArgb(),
        )
    }
}
