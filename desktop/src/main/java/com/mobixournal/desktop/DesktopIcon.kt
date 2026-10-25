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

/**
 * Take the app's **logo out of the window's caption**, keeping it on the taskbar.
 *
 * A packaged window inherits the executable's icon, so the title bar wore the app's art beside a caption
 * that already carries no name: a logo, a gap, then the tabs. Windows asks AWT for a **small** icon (the
 * caption's, 16–24 px) and a **big** one (the taskbar's and Alt-Tab's, 32–256 px), and `iconImages` is
 * picked per size by the closest match — so a transparent image at the caption's sizes and the art at the
 * taskbar's sizes gives exactly that split, with no Win32 call of its own and nothing to get wrong on a
 * host that is not Windows.
 *
 * A failure is silent: a window without an icon is worse than a window with the wrong one.
 */
internal fun captionIcon(window: java.awt.Window) {
    val images = runCatching { captionlessImages() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
    runCatching { window.iconImages = images }
}

/** The transparent caption sizes, then the art at the taskbar's — the split [captionIcon] relies on. */
private fun captionlessImages(): List<java.awt.Image> {
    val art = useResource("icon.png") { javax.imageio.ImageIO.read(it) }
    val blank = { side: Int -> java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB) }
    return listOf(blank(16), blank(24)) + listOf(32, 48, 64, 256).map { side ->
        art.getScaledInstance(side, side, java.awt.Image.SCALE_SMOOTH)
    }
}
