package com.mobixournal

import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mobixournal.ui.SettingsStore
import com.mobixournal.ui.ThemeMode

/**
 * Custom Application class for MobiXournal.
 *
 * Synchronises the user's persisted [ThemeMode] choice with the Android system
 * [UiModeManager] and the active launcher alias (`.LauncherDark` with `#161618` background by
 * default, `.LauncherLight` with `#FFFFFF` background when [ThemeMode.LIGHT] is chosen) so both
 * the app icon and the splash screen match the active theme.
 */
class MobiXournalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val store = SettingsStore(this)
        val settings = store.load()
        applyThemeMode(this, settings.themeMode)
    }

    companion object {
        private const val DARK_ALIAS = "com.mobixournal.LauncherDark"
        private const val LIGHT_ALIAS = "com.mobixournal.LauncherLight"

        /**
         * Applies [mode] to the system [UiModeManager] and switches the active launcher alias so
         * both the home-screen icon and the startup splash window match the selected theme.
         */
        fun applyThemeMode(context: Context, mode: ThemeMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(UiModeManager::class.java)?.let { uiModeManager ->
                    val targetMode = when (mode) {
                        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.DARK, ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_YES
                    }
                    uiModeManager.setApplicationNightMode(targetMode)
                }
            }
            syncLauncherAlias(context, useLight = mode == ThemeMode.LIGHT)
        }

        private fun syncLauncherAlias(context: Context, useLight: Boolean) {
            val pm = context.packageManager ?: return
            val pkg = context.packageName
            val darkComponent = ComponentName(pkg, DARK_ALIAS)
            val lightComponent = ComponentName(pkg, LIGHT_ALIAS)
            val darkState = if (useLight) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            val lightState = if (useLight) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                if (pm.getComponentEnabledSetting(lightComponent) != lightState) {
                    pm.setComponentEnabledSetting(lightComponent, lightState, PackageManager.DONT_KILL_APP)
                }
                if (pm.getComponentEnabledSetting(darkComponent) != darkState) {
                    pm.setComponentEnabledSetting(darkComponent, darkState, PackageManager.DONT_KILL_APP)
                }
            }
        }
    }
}
