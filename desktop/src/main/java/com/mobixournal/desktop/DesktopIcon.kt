package com.mobixournal.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource

/**
 * The window's **icon** — the app's own art, the same picture the tablet's launcher shows.
 *
 * The frame used to wear the JVM's coffee cup, because a Compose window with no `icon` falls back to it:
 * on the taskbar and in the alt-tab switcher the app was a generic console program. The PNG ships in the
 * module's resources (and `desktop/icon/` carries the `.ico` the Windows package is stamped with), so the
 * picture travels with the code rather than being extracted from the Android module at run time.
 *
 * Null when the resource cannot be read, so a broken asset costs an icon and never the window.
 */
internal fun appIcon(): Painter? = runCatching {
    useResource("icon.png") { stream -> BitmapPainter(loadImageBitmap(stream)) }
}.getOrNull()
