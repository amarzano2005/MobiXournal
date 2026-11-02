package com.mobixournal.desktop.shell
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.FloatingSurface
import com.mobixournal.desktop.tools.glassSurface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * **Where the open document is**, on the band: its path, shortened from the left, and the whole of it on
 * the pointer.
 *
 * A tab strip says the file's name, which is enough until two documents are called `notes.mobix` in two
 * folders. The band had spare width, and the honest thing to put there is the answer to "which one is
 * this" — read from the left when it fits, and **shortened from the left** when it does not: the tail is
 * what tells two paths apart, so the tail is what survives. The drive and the file name always stay.
 *
 * The band is the material, so the row takes no card and no rim: one line of the label role, and the full
 * path in a glass pill under it while the pointer rests, because a shortened path is a promise about the
 * whole one.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HeaderBreadcrumb(tab: DesktopTab?, modifier: Modifier = Modifier) {
    tab ?: return
    val path = if (tab.unsaved) "Unsaved document" else tab.file.absolutePath
    var hovered by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            text = shortenPath(path),
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false },
        )
        if (hovered) {
            Popup(
                offset = IntOffset(0, DesktopTheme.headerHeight.value.toInt() - 12),
                properties = FloatingSurface,
            ) {
                Box(Modifier.glassSurface().padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 720.dp)) {
                    Text(path, color = DesktopTheme.ink, style = DesktopTheme.label)
                }
            }
        }
    }
}

/**
 * [path] shortened from the left to [maxChars], keeping the drive and the file name.
 *
 * Pure, so the rule can be read back: a path that already fits is untouched; a deeper one keeps its head
 * (the drive, or the first folder of a UNC path), then as many **whole** segments from the tail as the
 * budget allows, then the file name — joined by the same separator the path used. A segment is never cut in
 * half, because half a folder name reads as a different folder, and the drive never goes, because
 * `…\notes\a.mobix` says less than `C:\…\a.mobix` with the same width.
 */
internal fun shortenPath(path: String, maxChars: Int = 46): String {
    if (path.length <= maxChars) return path
    val separator = if (path.contains('\\')) '\\' else '/'
    val parts = path.split(separator).filter { it.isNotBlank() }
    if (parts.size <= 2) return path
    val head = parts.first()
    val name = parts.last()
    val tail = ArrayDeque<String>()
    var kept = head.length + name.length + 3 // the two separators and the ellipsis
    for (part in parts.subList(1, parts.size - 1).asReversed()) {
        if (kept + part.length + 1 > maxChars) break
        tail.addFirst(part)
        kept += part.length + 1
    }
    val body = (listOf(head) + tail.toList()).joinToString(separator.toString())
    return "$body$separator…$separator$name"
}
