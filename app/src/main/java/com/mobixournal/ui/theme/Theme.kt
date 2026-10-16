package com.mobixournal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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

internal val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Amber,
    surface = Color.White,
    onSurface = TextDarkOnLight,
    surfaceVariant = Color(0xFFF3F6FC),
    onSurfaceVariant = TextSecondaryOnLight,
    background = DeskLight,
    onBackground = TextDarkOnLight,
)
internal val DarkColors = darkColorScheme(
    primary = PurpleDark,
    secondary = Amber,
    surface = Color(0xFF121212),
    onSurface = TextLightOnDark,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = TextSecondaryOnDark,
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    surfaceContainerLowest = DeskDark,
    surfaceBright = Color(0xFF373737),
    surfaceDim = Color(0xFF121212),
    background = DeskDark,
    onBackground = TextLightOnDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context).copy(
                    surface = Color(0xFF121212),
                    surfaceVariant = Color(0xFF1E1E1E),
                    surfaceContainer = Color(0xFF1E1E1E),
                    surfaceContainerLow = Color(0xFF121212),
                    surfaceContainerHigh = Color(0xFF242424),
                    surfaceContainerHighest = Color(0xFF2C2C2C),
                    surfaceContainerLowest = DeskDark,
                    surfaceBright = Color(0xFF373737),
                    surfaceDim = Color(0xFF121212),
                    background = DeskDark,
                    onBackground = TextLightOnDark,
                    onSurface = TextLightOnDark,
                    outline = OutlineDark,
                    outlineVariant = OutlineVariantDark,
                )
            } else {
                dynamicLightColorScheme(context)
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
        content = content,
    )
}
