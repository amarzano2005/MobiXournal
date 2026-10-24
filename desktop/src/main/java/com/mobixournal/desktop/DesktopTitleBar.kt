package com.mobixournal.desktop

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.Locale

/**
 * **The system title bar, painted in the window's own material.**
 *
 * The window's bands are translucent glass over a true-black desk (`docs/interface-contract.md` §1), and
 * the band's *effective* colour is that composite — which is what [DesktopTheme.bandRgb] holds. The
 * Windows caption cannot be given a fill with an alpha, so before this the top of the window was a light
 * system bar sitting on a dark application: two materials, one above the other, and the seam read as a
 * frame the app had been dropped into.
 *
 * Windows 11 (and Windows 10 from build 18985) lets a window say otherwise through DWM: the immersive
 * dark mode turns the caption's text and glyphs white, and the caption, border and text colours can be
 * set outright. This file is that conversation — one call per attribute, on the window's `HWND`, which
 * JNA already hands out the same way the pen and dial readers get it ([com.sun.jna.Native.getWindowPointer]).
 *
 * Everything here is **best effort**. A host that is not Windows, an older Windows that does not know an
 * attribute, a DWM that refuses a colour: each of those answers false and the window opens exactly as it
 * did before — a title bar that does not blend is a cosmetic gap, never a reason for the app not to start.
 */
private interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int
}

/** `DWMWA_USE_IMMERSIVE_DARK_MODE`: a BOOL that makes the caption dark and its glyphs white. */
private const val USE_IMMERSIVE_DARK_MODE = 20

/**
 * The attribute's number on Windows 10 builds before 18985, where dark mode lived at 19.
 *
 * Tried only when [USE_IMMERSIVE_DARK_MODE] is refused, so a Windows 11 host never asks the old one.
 */
private const val USE_IMMERSIVE_DARK_MODE_LEGACY = 19

/** `DWMWA_BORDER_COLOR`: the one-pixel outline Windows draws round the window. */
private const val BORDER_COLOR = 34

/** `DWMWA_CAPTION_COLOR`: the title bar's own fill. */
private const val CAPTION_COLOR = 35

/** `DWMWA_TEXT_COLOR`: the title text, and the colour the min/max/close glyphs follow. */
private const val TEXT_COLOR = 36

/** `S_OK` — the one HRESULT that means the attribute took. */
private const val S_OK = 0

/** The size of a `BOOL` or a `COLORREF`, both four bytes: what `cbAttribute` carries here. */
private const val ATTRIBUTE_BYTES = 4

/**
 * Paint [window]'s native title bar in the app's band colour, and answer whether DWM accepted it.
 *
 * The three colours, in Win32's `COLORREF` order, are the window's own tokens rather than numbers of
 * this file's own: the caption is [DesktopTheme.bandColourRef] (the glass composited over the desk, so
 * the caption and the header band are one surface), the border is the desk's — Windows' default light
 * outline round a black window is the seam this call removes — and the text is the chrome's ink.
 *
 * It is safe to call more than once; DWM simply re-applies the attributes.
 */
internal fun applyDarkTitleBar(window: Window): Boolean {
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    if (!os.contains("windows")) return false
    val library = runCatching { Native.load("dwmapi", Dwmapi::class.java) as Dwmapi }.getOrNull() ?: return false
    val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return false
    val dark = setAttribute(library, hwnd, USE_IMMERSIVE_DARK_MODE, 1) ||
        setAttribute(library, hwnd, USE_IMMERSIVE_DARK_MODE_LEGACY, 1)
    val caption = setAttribute(library, hwnd, CAPTION_COLOR, DesktopTheme.bandColourRef)
    // The border and the text are the finishing touches: a build that refuses them keeps its caption.
    setAttribute(library, hwnd, BORDER_COLOR, DesktopTheme.deskColourRef)
    setAttribute(library, hwnd, TEXT_COLOR, DesktopTheme.inkColourRef)
    return dark && caption
}

/** One `DwmSetWindowAttribute` call: true when DWM answered `S_OK`. */
private fun setAttribute(library: Dwmapi, hwnd: Pointer, attribute: Int, value: Int): Boolean =
    runCatching {
        // The value is written into native memory because the call takes a pointer to four bytes; JNA
        // frees the block when it is collected, as the tablet readers' own buffers are.
        val buffer = Memory(ATTRIBUTE_BYTES.toLong())
        buffer.setInt(0, value)
        library.DwmSetWindowAttribute(hwnd, attribute, buffer, ATTRIBUTE_BYTES) == S_OK
    }.getOrDefault(false)
