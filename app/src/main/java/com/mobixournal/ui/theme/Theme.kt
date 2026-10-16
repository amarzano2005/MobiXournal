package com.mobixournal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mobixournal.ui.ThemeMode

/**
 * Re-pins every chrome role of a light scheme, in one place.
 *
 * `lightColorScheme()` fills whatever it is not told with Material 3's **baseline** palette, whose
 * neutrals are violet-tinted: `surfaceContainer` `#F3EDF7`, `primaryContainer` `#EADDFF`,
 * `secondaryContainer` `#E8DEF8`, `surfaceVariant` `#E7E0EC`. This app's palette is black, grey and one
 * blue accent, so a role left at the baseline is a role that can show a lavender panel, a periwinkle
 * chip or a violet selected cell — which is exactly what a light-theme pop-up and its selected rows
 * were doing, since only a handful of roles had ever been named here.
 *
 * So the light scheme is not built by naming a few roles and inheriting the rest: the whole chrome is
 * pinned, and **both** [LightColors] and the Android 12+ dynamic light scheme pass through this, so a
 * wallpaper palette cannot tint a container either. Error reds are deliberately *not* pinned: they are
 * semantic, not chrome. Pinned role by role by `ColorContractGuardTest`.
 */
private fun ColorScheme.pinnedLightChrome(): ColorScheme = copy(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = ActiveSlotLight,
    onPrimaryContainer = TextDarkOnLight,
    secondary = Blue,
    onSecondary = Color.White,
    secondaryContainer = ActiveSlotLight,
    onSecondaryContainer = TextDarkOnLight,
    tertiary = TextSecondaryOnLight,
    onTertiary = Color.White,
    tertiaryContainer = NeutralContainerLightHigh,
    onTertiaryContainer = TextDarkOnLight,
    background = DeskLight,
    onBackground = TextDarkOnLight,
    surface = Color.White,
    onSurface = TextDarkOnLight,
    surfaceVariant = NeutralContainerLightHigh,
    onSurfaceVariant = TextSecondaryOnLight,
    surfaceTint = NeutralContainerLight,
    surfaceBright = Color.White,
    surfaceDim = NeutralContainerLightHighest,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = NeutralContainerLightLow,
    surfaceContainer = NeutralContainerLight,
    surfaceContainerHigh = NeutralContainerLightHigh,
    surfaceContainerHighest = NeutralContainerLightHighest,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = NeutralContainerDark,
    inverseOnSurface = TextLightOnDark,
    inversePrimary = BlueDark,
    scrim = Color.Black,
)

/**
 * The dark theme's own half of the pin: the true-OLED black desk with sleek **neutral charcoal**
 * layers, shared by [DarkColors] and the dynamic dark scheme (a wallpaper palette used to be free to
 * tint the containers the copy here does not name).
 *
 * Note `primaryContainer`/`secondaryContainer` are the neutral steel greys ([ActiveSlotDark]) rather
 * than the baseline's violet, so "picked" and "selected" wear grey here and in the light theme, never
 * a pastel.
 */
private fun ColorScheme.pinnedDarkChrome(): ColorScheme = copy(
    primary = BlueDark,
    onPrimary = OnAccentDark,
    primaryContainer = ActiveSlotDark,
    onPrimaryContainer = TextLightOnDark,
    secondary = BlueDark,
    onSecondary = OnAccentDark,
    secondaryContainer = ActiveSlotDark,
    onSecondaryContainer = TextLightOnDark,
    tertiary = TextSecondaryOnDark,
    onTertiary = OnAccentDark,
    tertiaryContainer = NeutralContainerDark,
    onTertiaryContainer = TextLightOnDark,
    background = DeskDark,
    onBackground = TextLightOnDark,
    surface = Color(0xFF161618),
    onSurface = TextLightOnDark,
    surfaceVariant = NeutralContainerDark,
    onSurfaceVariant = TextSecondaryOnDark,
    surfaceTint = NeutralContainerDark,
    surfaceBright = Color(0xFF424248),
    surfaceDim = Color(0xFF141416),
    surfaceContainerLowest = DeskDark,
    surfaceContainerLow = Color(0xFF18181A),
    surfaceContainer = NeutralContainerDark,
    surfaceContainerHigh = Color(0xFF2C2C30),
    surfaceContainerHighest = Color(0xFF38383E),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = TextLightOnDark,
    inverseOnSurface = TextDarkOnLight,
    inversePrimary = Blue,
    scrim = Color.Black,
)

internal val LightColors: ColorScheme = lightColorScheme().pinnedLightChrome()
internal val DarkColors: ColorScheme = darkColorScheme().pinnedDarkChrome()

/**
 * The app's shape set: Material 3's, with **one** change — `extraLarge`, which is what Material's
 * `DialogTokens.ContainerShape` resolves to, is the chrome's own floating corner
 * ([ModernTheme.panelCornerRadiusDp], 20dp) instead of Material's 28dp.
 *
 * One change, every dialog: the app's twenty-five `AlertDialog`s — and any added later — then turn at the
 * same radius as the menus, the action bars and the docks, with no call site passing a shape and no
 * wrapper to remember. `ColorContractGuardTest` pins the value, so dialogs and menus cannot drift into
 * two corners.
 */
internal val AppShapes: Shapes = Shapes().copy(
    extraLarge = RoundedCornerShape(ModernTheme.panelCornerRadiusDp),
)

/**
 * Whether [mode] means "paint dark" right now — SYSTEM defers to the OS setting.
 *
 * Use this to decide which Material 3 colour scheme to apply.
 */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The app's Material 3 (Material You) theme. Uses dynamic colour on Android 12+.
 *
 * @param darkTheme Whether to use the dark colour scheme. Defaults to the system setting.
 * @param dynamicColor Whether to use Android 12+ dynamic colours from the wallpaper.
 * @param content The composable content to render with this theme.
 */
@Composable
fun XoppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // A wallpaper palette may only reach the roles the pin does not name (the error colours).
            // The two dynamic schemes used to be copied by hand, and the *light* one only had primary
            // and secondary re-pinned — so enabling dynamic colour turned every light container violet
            // again. Both halves now go through the same pin as the static schemes.
            if (darkTheme) {
                dynamicDarkColorScheme(context).pinnedDarkChrome()
            } else {
                dynamicLightColorScheme(context).pinnedLightChrome()
            }
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    // Both system bars are left **clear**, because the document runs edge to edge underneath them: the
    // canvas is full-bleed and the chrome is the floating top bar and the rail, so any colour painted
    // here is a band of window the page cannot reach — a strip of the app's own background along the
    // bottom edge of the screen, read as if the page had ended early. Only the *icons* are still chosen
    // here, which is what the two `isAppearanceLight*Bars` flags are for: a light chrome takes dark
    // icons. Deriving that from `darkTheme` alone gave white icons on a white bar, so it comes from the
    // chrome colour's luminance. `colorScheme.background` is read directly rather than through
    // [rememberChromeColor], which reads the ambient theme and would report the *outer* scheme — this
    // runs above the `MaterialTheme` below.
    val chrome = colorScheme.background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            val lightIcons = chrome.luminance() > 0.5f
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ModernTypography,
        shapes = AppShapes,
        content = content,
    )
}
