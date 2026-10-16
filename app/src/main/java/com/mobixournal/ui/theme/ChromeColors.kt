package com.mobixournal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    /** The surround behind the pages — [rememberChromeColor], true OLED black in dark mode or light neutral. */
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
 * strip, both Android system bars, and the canvas backdrop.
 *
 * These used to be three different neutrals — the tab strip sat one tonal step up from the bar it hangs
 * under. Three dark greys stacked down the screen read as three unrelated surfaces rather than one frame
 * around the document, so there is now a single value and all of them take it. Put a new *bar* surface on
 * this, not on a `surfaceContainer*` role, or the bands come back.
 *
 * `background` is the role because that is what the `Scaffold` under the transparent top bar already
 * painted — so the bar the user sees is unchanged, and the strip, the system bars and the canvas backdrop
 * take it (true OLED black in dark mode, light neutral in light mode).
 *
 * The toolbars are deliberately one step up from this, on [rememberToolbarColor] (neutral charcoal in dark mode).
 */
@Composable
fun rememberChromeColor(): Color = MaterialTheme.colorScheme.background

/**
 * The fill of every chrome **bar**: the rail, the header slab, the figures' dock and their buttons.
 *
 * This is the app's frosted glass ([ModernTheme.glassSurface]), and it is deliberately *one* function
 * rather than one call to the token per surface, so no two bands can end up different materials.
 * A **pop-up** takes [rememberMenuSurfaceColor] instead — the same colour, less see-through — rather
 * than this, because a panel is what text is read on (it used to paint [rememberToolbarColor],
 * `surfaceContainer`, which in the light theme was the baseline lavender `#F3EDF7`: a blue-grey panel
 * hanging from a near-white bar, in a palette with no violet in it).
 *
 * Bar and panel read the *same* dark/light answer, so the pair cannot disagree when a device's system
 * setting and the app's own `ThemeMode` differ either.
 */
@Composable
fun rememberGlassSurfaceColor(): Color = ModernTheme.glassSurface(isSystemInDarkTheme())

/**
 * The fill of the **pop-ups** the chrome opens: the bars' glass with the see-through turned down
 * ([ModernTheme.menuPanelSurface]).
 *
 * One function for the same reason its bar-side twin is one: every pop-up in the app is built by
 * `ToolbarMenu`, and this is what that one material paints — so the menu reads as the bar's own colour
 * while staying readable over the page behind it.
 */
@Composable
fun rememberMenuSurfaceColor(): Color = ModernTheme.menuPanelSurface(isSystemInDarkTheme())

/**
 * The raised **panels'** colour: dialog and picker chrome (the function-plot chrome, the share and
 * page dialogs), i.e. `surfaceContainer` — [NeutralContainerLight] in the light theme,
 * [NeutralContainerDark] in the dark one.
 *
 * It is *not* the toolbars' colour even though the role was named for them: the rail, the header slab
 * and the dock are frosted glass ([rememberGlassSurfaceColor]) and have been since the chrome was
 * banded. A new panel takes this; a new *bar* or pop-up takes the glass.
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
    return remember(scheme) {
        CanvasChromeColors(
            backdrop = scheme.background.toArgb(),
            pageOutline = scheme.outlineVariant.toArgb(),
            selection = scheme.primary.toArgb(),
            guide = scheme.secondary.toArgb(),
        )
    }
}
