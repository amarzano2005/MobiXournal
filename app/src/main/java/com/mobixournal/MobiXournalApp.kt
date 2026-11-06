package com.mobixournal

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mobixournal.ui.SettingsStore
import com.mobixournal.ui.ThemeMode

/**
 * Custom Application class for MobiXournal.
 *
 * Keeps both the `-night` qualifier and the active launcher alias
 * (`.LauncherDark` with `Theme.MobiXournal.Dark` + `#161618` background,
 * `.LauncherLight` with `Theme.MobiXournal.Light` + `#FFFFFF` background)
 * aligned with the persisted [ThemeMode].
 */
class MobiXournalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightMode(this, SettingsStore(this).load().themeMode)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) {
                syncIfMainActivityLeaving(activity)
            }
            override fun onActivityStopped(activity: Activity) {
                syncIfMainActivityLeaving(activity)
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun syncIfMainActivityLeaving(activity: Activity) {
        if (activity is MainActivity && !activity.isChangingConfigurations) {
            val mode = SettingsStore(this).load().themeMode
            val useLight = when (mode) {
                ThemeMode.LIGHT -> true
                ThemeMode.DARK -> false
                ThemeMode.SYSTEM ->
                    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
                        Configuration.UI_MODE_NIGHT_YES
            }
            syncLauncherAlias(this, useLight = useLight)
        }
    }

    companion object {
        private const val DARK_ALIAS = "com.mobixournal.LauncherDark"
        private const val LIGHT_ALIAS = "com.mobixournal.LauncherLight"

        /** How long a restart waits, so the finishing window can persist the open tabs first. */
        private const val RESTART_DELAY_MS = 350L

        /**
         * Move the app's `-night` qualifier and persisted `SplashScreen` theme to [mode].
         */
        fun applyNightMode(context: Context, mode: ThemeMode) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val uiModeManager = context.getSystemService(UiModeManager::class.java)
            val targetMode = when (mode) {
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
            uiModeManager?.setApplicationNightMode(targetMode)
            if (context is Activity) {
                val splashTheme = when (mode) {
                    ThemeMode.LIGHT -> R.style.Theme_MobiXournal_Light
                    ThemeMode.DARK -> R.style.Theme_MobiXournal_Dark
                    ThemeMode.SYSTEM -> Resources.ID_NULL
                }
                runCatching { context.splashScreen.setSplashScreenTheme(splashTheme) }
            }
        }

        fun restartForTheme(context: Context) {
            val activity = context as? Activity ?: return
            val app = activity.applicationContext
            val mode = SettingsStore(app).load().themeMode
            activity.finishAffinity()
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { applyNightMode(app, mode) }
                runCatching { syncLauncherAlias(app, useLight = mode == ThemeMode.LIGHT) }
                val entry = runCatching {
                    app.packageManager.getLaunchIntentForPackage(app.packageName)
                }.getOrNull()
                if (entry != null) {
                    entry.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    runCatching { app.startActivity(entry) }
                }
            }, RESTART_DELAY_MS)
        }

        internal fun syncLauncherAlias(context: Context, useLight: Boolean) {
            val pm = context.packageManager ?: return
            val pkg = context.packageName
            val darkComponent = ComponentName(pkg, DARK_ALIAS)
            val lightComponent = ComponentName(pkg, LIGHT_ALIAS)
            val toEnable = if (useLight) lightComponent else darkComponent
            val toDisable = if (useLight) darkComponent else lightComponent
            val enableMatches =
                pm.getComponentEnabledSetting(toEnable) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            val disableMatches =
                pm.getComponentEnabledSetting(toDisable) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (enableMatches && disableMatches) return

            runCatching {
                pm.setComponentEnabledSetting(
                    toEnable,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                pm.setComponentEnabledSetting(
                    toDisable,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
