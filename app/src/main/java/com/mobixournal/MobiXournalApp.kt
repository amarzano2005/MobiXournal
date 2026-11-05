package com.mobixournal

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.mobixournal.ui.SettingsStore
import com.mobixournal.ui.ThemeMode

/**
 * Custom Application class for MobiXournal.
 *
 * Keeps the two answers a launch reads in step with the persisted [ThemeMode]: the `-night`
 * qualifier, which decides the startup window's background, and the active launcher alias
 * (`.LauncherDark` with `#161618` by default, `.LauncherLight` with `#FFFFFF` on [ThemeMode.LIGHT]),
 * which decides the home-screen icon.
 *
 * **They are applied at different moments, and that is deliberate.** Disabling the
 * `activity-alias` that a running task was launched through makes Android finish that task — the
 * system finishes activities whose component it just disabled, `DONT_KILL_APP` or not — so syncing
 * the alias as soon as the theme was picked closed the app on the spot, before the notice offering a
 * restart could be read. The qualifier is safe to move while the window is up (it is a local
 * configuration change, and `MainActivity` declares `uiMode`), so the splash is right at the next
 * start either way; the alias is switched only inside [restartForTheme], once the affinity is on its
 * way out and no window of ours is left to close.
 */
class MobiXournalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The qualifier only: this is a configuration change, while touching the alias here would
        // disable the very component this launch came through.
        applyNightMode(this, SettingsStore(this).load().themeMode)
    }

    companion object {
        private const val DARK_ALIAS = "com.mobixournal.LauncherDark"
        private const val LIGHT_ALIAS = "com.mobixournal.LauncherLight"

        /** How long a restart waits, so the finishing window can persist the open tabs first. */
        private const val RESTART_DELAY_MS = 350L

        /**
         * Move the app's `-night` qualifier to [mode], which is what the startup window reads.
         *
         * Android draws the starting window from the theme of the component that launched the app —
         * `MainActivity`'s, since an `activity-alias` only carries the icon and the label — before any
         * of our code runs, so the splash background is a day/night-qualified item
         * (`values/themes.xml`, `values-night/themes.xml`) and this call is what moves the answer.
         * [ThemeMode.SYSTEM] hands the qualifier back to the system ([UiModeManager.MODE_NIGHT_AUTO]);
         * pinning `MODE_NIGHT_YES` for it used to leave the splash dark under an app painting light.
         * There is no app-level qualifier on API 30 and below, where the alias remains the only lever.
         */
        fun applyNightMode(context: Context, mode: ThemeMode) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
            val targetMode = when (mode) {
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
            uiModeManager.setApplicationNightMode(targetMode)
        }

        /**
         * Restart the app through its launcher entry so the icon and the splash wear the theme just
         * chosen.
         *
         * The affinity is finished first and the rest is posted a beat later: closing the window
         * persists the open tabs, and a restart must not cost what a colour change never should. The
         * qualifier and the alias are moved inside that beat, when nothing of ours is on screen —
         * switching the alias any earlier finishes the task the user is sitting in, and the restart
         * would arrive as the app closing by itself.
         */
        fun restartForTheme(context: Context) {
            val activity = context as? Activity ?: return
            val app = activity.applicationContext
            val mode = SettingsStore(app).load().themeMode
            activity.finishAffinity()
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { applyNightMode(app, mode) }
                runCatching { syncLauncherAlias(app, useLight = mode == ThemeMode.LIGHT) }
                // Resolved after the switch: the entry that has to come back is the alias just
                // enabled, and an intent naming the one just disabled would not resolve at all.
                val entry = runCatching {
                    app.packageManager.getLaunchIntentForPackage(app.packageName)
                }.getOrNull()
                if (entry != null) {
                    entry.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    runCatching { app.startActivity(entry) }
                }
            }, RESTART_DELAY_MS)
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
