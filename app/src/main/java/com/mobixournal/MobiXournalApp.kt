package com.mobixournal

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
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

        /** How long a restart waits, so the finishing window can persist the open tabs first. */
        private const val RESTART_DELAY_MS = 350L

        /**
         * Applies [mode] to the system [UiModeManager] and switches the active launcher alias so
         * both the home-screen icon and the startup splash window match the selected theme.
         *
         * **The night qualifier is what the splash reads.** Android draws the starting window from
         * the theme of the component that launched the app — `MainActivity`'s, since an
         * `activity-alias` only carries the icon and the label — so the splash background is a
         * day/night-qualified item (`values/themes.xml`, `values-night/themes.xml`) and this call is
         * what moves the app's `-night` answer to the chosen theme. Pinning `MODE_NIGHT_YES` for
         * [ThemeMode.SYSTEM] used to leave the splash dark while the app itself painted light; on
         * API 30 and below there is no app-level qualifier to set, so the alias remains the only
         * lever there.
         */
        fun applyThemeMode(context: Context, mode: ThemeMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(UiModeManager::class.java)?.let { uiModeManager ->
                    val targetMode = when (mode) {
                        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                        // AUTO is the app-level "no override": the qualifier follows the system again.
                        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    }
                    uiModeManager.setApplicationNightMode(targetMode)
                }
            }
            syncLauncherAlias(context, useLight = mode == ThemeMode.LIGHT)
        }

        /**
         * Restart the app through its launcher entry so the theme just chosen is the one the
         * starting window and the home-screen icon wear.
         *
         * The alias, the `-night` qualifier and the splash background are all read when the app is
         * launched, so a live window cannot repaint them; only a fresh launch can. The launch intent
         * is the launcher's own, so the enabled alias (light or dark) is the entry that comes back.
         * The affinity is finished first and the start is posted a beat later: closing the window
         * persists the open tabs, and a restart must not cost what a colour change never should.
         */
        fun restartForTheme(context: Context) {
            val activity = context as? Activity ?: return
            val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val app = activity.applicationContext
            activity.finishAffinity()
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { app.startActivity(launch) } }, RESTART_DELAY_MS)
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
