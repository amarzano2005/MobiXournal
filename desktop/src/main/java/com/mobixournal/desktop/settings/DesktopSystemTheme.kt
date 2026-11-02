package com.mobixournal.desktop.settings

import java.util.Locale

/**
 * What **Windows** is set to, read once and remembered — the answer behind [DesktopThemeMode.SYSTEM].
 *
 * The window is dark-first and this is the one question it asks the operating system: whether the desktop
 * is on its light app theme (`AppsUseLightTheme` under the personalisation key). The read is a `reg query`,
 * which is a process, so it happens **once per window** and everything after it is a boolean.
 *
 * Everything here is best effort: a host that is not Windows, a key that is missing, a refused process —
 * each answers dark, which is the window's own default and never a reason for it to fail to open.
 */
internal object DesktopSystemTheme {

    private val cached: Boolean by lazy { read() }

    /** Whether Windows is on the light app theme; false on every host that cannot say. */
    fun isLight(): Boolean = cached

    private fun read(): Boolean = runCatching {
        val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        if (!os.contains("windows")) return@runCatching false
        val process = ProcessBuilder(
            "reg",
            "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v",
            "AppsUseLightTheme",
        ).redirectErrorStream(true).start()
        val answer = process.inputStream.bufferedReader().readText()
        process.waitFor()
        answer.contains("0x1")
    }.getOrDefault(false)
}
