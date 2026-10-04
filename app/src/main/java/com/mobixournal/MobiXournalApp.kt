package com.mobixournal

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.mobixournal.ui.SettingsStore
import com.mobixournal.ui.ThemeMode

/**
 * Custom Application class for MobiXournal.
 *
 * Synchronises the user's persisted [ThemeMode] choice with the Android system
 * [UiModeManager] on process startup, ensuring the splash screen window matches the
 * app's theme (Light, Dark, or System) even before [MainActivity] is composed.
 */
class MobiXournalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val store = SettingsStore(this)
        val settings = store.load()
        applyThemeMode(this, settings.themeMode)
    }

    companion object {
        /**
         * Applies [mode] to the system [UiModeManager] so the OS WindowManager persists
         * and respects the app-level night mode on startup/splash screen.
         */
        fun applyThemeMode(context: Context, mode: ThemeMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
                val targetMode = when (mode) {
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                }
                uiModeManager.setApplicationNightMode(targetMode)
            }
        }
    }
}
