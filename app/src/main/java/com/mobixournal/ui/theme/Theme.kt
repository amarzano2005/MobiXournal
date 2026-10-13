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
    onSurface = Color(0xFF1B212F),
    surfaceVariant = Color(0xFFF3F6FC),
    onSurfaceVariant = Color(0xFF596579),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1B212F),
)
internal val DarkColors = darkColorScheme(
    primary = PurpleDark,
    secondary = Amber,
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFA0A0A0),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceBright = Color(0xFF373737),
    surfaceDim = Color(0xFF121212),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDED),
    outline = Color(0xFF383838),
    outlineVariant = Color(0xFF262626),
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
                    surfaceContainerLowest = Color(0xFF000000),
                    surfaceBright = Color(0xFF373737),
                    surfaceDim = Color(0xFF121212),
                    background = Color(0xFF000000),
                    onBackground = Color(0xFFEDEDED),
                    onSurface = Color(0xFFEDEDED),
                    outline = Color(0xFF383838),
                    outlineVariant = Color(0xFF262626),
                )
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    // Paint both system bars with the app's own chrome colour instead of leaving them transparent over
    // the launch theme's (always white) window background, then pick the icon contrast from that
    // colour's luminance. Deriving it from `darkTheme` alone gave white icons on a white bar. The bars
    // take the *same* value as the top bar and the tab strip (`rememberChromeColor`), so the frame
    // around the document is one continuous field from the status bar to the navigation bar.
    // `colorScheme.background` directly: [rememberChromeColor] reads the ambient theme, and this runs
    // above the `MaterialTheme` below, so it would report the *outer* scheme rather than this one.
    val chrome = colorScheme.background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = chrome.toArgb()
            window.navigationBarColor = chrome.toArgb()
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
