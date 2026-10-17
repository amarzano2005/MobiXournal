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
 * A **floating surface** takes [rememberFloatingSurfaceColor] instead — the same colour, less
 * see-through — rather than this, because a panel is what text is read on (it used to paint
 * [rememberToolbarColor],
 * `surfaceContainer`, which in the light theme was the baseline lavender `#F3EDF7`: a blue-grey panel
 * hanging from a near-white bar, in a palette with no violet in it).
 *
 * Bar and panel read the *same* dark/light answer — the app's own `ThemeMode`, through
 * [chromeIsDark] — so neither can be lit for a theme its ink does not come from. Asking the OS
 * directly (`isSystemInDarkTheme`) is what used to let a bar paint the light theme's white glass
 * while its icons and text came from the dark scheme.
 */
@Composable
fun rememberGlassSurfaceColor(): Color = ModernTheme.glassSurface(chromeIsDark())

/**
 * The fill of every **floating surface**: the bars' glass with the see-through turned down
 * ([ModernTheme.floatingSurface]).
 *
 * "Floating" is everything the chrome puts *over* the page for a moment rather than in a band beside
 * it, and they are one material because they are one idea: the pop-ups and submenus (`ToolbarMenu`),
 * the action bars that come up with a selection (`EditorActionBars`), the STEM editors' chrome, the
 * page/zoom/scroll badges, the dialogs that are not Material's own (`OnboardingDialog`,
 * `KeyDetectionDialog`, the search-indexing card) and the transfer overlay. Material's own `AlertDialog`s
 * read `colorScheme.surface`, which the schemes pin to the same charcoal/white the glass is made of,
 * and take [ModernTheme.panelCornerRadiusDp] through `AppShapes` — so a dialog and a menu are one
 * material too, without every call site passing a colour.
 *
 * One function for the same reason its bar-side twin is one: a floating surface that read its own
 * container role is how a lavender menu once hung from a grey bar.
 */
@Composable
fun rememberFloatingSurfaceColor(): Color = ModernTheme.floatingSurface(chromeIsDark())

/**
 * The **plate** colour: the opaque neutral step for a surface *inside* a floating one — the function
 * dialog's mode-selector track, a thumbnail well, a caption strip. `surfaceContainer`:
 * [NeutralContainerLight] in the light theme, [NeutralContainerDark] in the dark one.
 *
 * It is *not* the toolbars' colour, whatever the role's name suggests: the rail, the header slab and the
 * dock are frosted glass ([rememberGlassSurfaceColor]) and have been since the chrome was banded, and
 * anything that *floats* — a menu, an action bar, a badge, a dialog — takes
 * [rememberFloatingSurfaceColor]. This is the third, quieter job: something that needs to read as a
 * recessed plate rather than a raised surface.
 */
@Composable
fun rememberToolbarColor(): Color = MaterialTheme.colorScheme.surfaceContainer

/**
 * The **scroll thumb's** ink: the chrome's own secondary grey — [TextSecondaryOnLight] in the light
 * theme, [TextSecondaryOnDark] in the dark one.
 *
 * The thumb used to be a `primary` (accent-blue) bar, which read as a live control competing with
 * the page rather than as the quiet hint beside it. It now borrows the neutral every caption uses,
 * and `ScrollThumb` draws it at low alpha (faint when idle, brighter while dragged), so the bar is
 * a grey shadow at the sheet's edge in both themes instead of a blue stripe.
 */
@Composable
fun rememberScrollThumbColor(): Color = if (chromeIsDark()) TextSecondaryOnDark else TextSecondaryOnLight

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
