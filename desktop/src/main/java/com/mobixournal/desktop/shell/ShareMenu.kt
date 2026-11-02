package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Locale

/**
 * What the **Share** menu last did, in one sentence, for the status band.
 *
 * One object rather than a field on the shell for the reason `DesktopPdfTextNotice` is one: it is a notice
 * about the press that has just happened, it belongs to no document, and the band that prints it is a
 * read-only surface. The next press replaces it.
 */
internal object DesktopShareNotice {
    var text: String? by mutableStateOf(null)

    fun say(message: String) {
        text = message
    }
}

/**
 * The three things a window can do with a document it is not writing into: **show it in Explorer**, **copy
 * its path**, and **export it** — the answers to "where is this file" and "I need it elsewhere", which
 * before this lived nowhere in the window at all.
 *
 * The commands are injected ([reveal], [copy]) so the rules can be tested without opening an Explorer
 * window or writing to a test machine's clipboard: what the tests own is *when* each runs and what the
 * band is told, and what the window owns is the two platform calls at the bottom of this file.
 *
 * A document with no file yet refuses both file commands in a sentence rather than doing nothing visible
 * (`docs/ui-modernization.md`, the error-copy rule): the row stays on screen, disabled, and its reason is
 * printed when it cannot run.
 */
internal class ShareActions(
    private val tab: () -> DesktopTab?,
    private val say: (String) -> Unit = DesktopShareNotice::say,
    private val reveal: (File) -> Boolean = ::revealInExplorer,
    private val copy: (String) -> Boolean = ::copyToClipboard,
) {

    /** Whether there is a document to share at all — the capsule's enabled state. */
    val hasDocument: Boolean get() = tab() != null

    /** Whether it has a file to point at; an unsaved document does not. */
    val canReveal: Boolean get() = tab()?.let { !it.unsaved } == true

    /** Show the document in Explorer, selected. */
    fun showInExplorer(): Boolean {
        val open = tab() ?: return refuse("Open a document first.")
        if (open.unsaved) return refuse("Save the document before showing it in Explorer.")
        if (!reveal(open.file)) return refuse("Explorer could not be opened for that file.")
        say("Shown in Explorer")
        return true
    }

    /** Put the document's path on the system clipboard. */
    fun copyPath(): Boolean {
        val open = tab() ?: return refuse("Open a document first.")
        if (open.unsaved) return refuse("Save the document before copying its path.")
        val path = open.file.absolutePath
        if (!copy(path)) return refuse("This machine has no clipboard to copy to.")
        say("Copied $path")
        return true
    }

    private fun refuse(sentence: String): Boolean {
        say(sentence)
        return false
    }
}

/** The window's Share menu, over the document in view. */
@Composable
internal fun ShareMenuPanel(share: ShareActions, onExport: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .glassSurface()
            .width(ShareMenuMetrics.width)
            .padding(ShareMenuMetrics.padding),
        verticalArrangement = Arrangement.spacedBy(ShareMenuMetrics.gap),
    ) {
        ShareRow(ChromeGlyph.OPEN, "Show in Explorer", enabled = share.canReveal) {
            onClose()
            share.showInExplorer()
        }
        ShareRow(ChromeGlyph.COPY, "Copy path", enabled = share.canReveal) {
            onClose()
            share.copyPath()
        }
        ShareRow(ChromeGlyph.SAVE_AS, "Export…", enabled = share.hasDocument) {
            onClose()
            onExport()
        }
    }
}

/** The panel's numbers, in one place: the width, and the row the band's capsule opens it at. */
internal object ShareMenuMetrics {
    val width = 208.dp
    val padding = 6.dp
    val rowHeight = 30.dp
    val gap = 1.dp
    val glyphSlot = 20.dp
    val rowGlyph = 16.dp
}

/** One row: its glyph, its word, and the muted tone when the document cannot do it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ShareRow(glyph: ChromeGlyph, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ShareMenuMetrics.rowHeight)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape, active = !enabled)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ShareMenuMetrics.glyphSlot), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.size(ShareMenuMetrics.rowGlyph)) {
                drawChromeGlyph(glyph, if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted)
            }
        }
        Spacer(Modifier.width(ShareMenuMetrics.gap + 4.dp))
        Text(
            label,
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            maxLines = 1,
        )
    }
}

/** The shell's Share menu: the commands over whatever tab is in view. */
internal fun DesktopShellState.shareActions(): ShareActions = ShareActions(tab = { active })

/**
 * Show [file] in Explorer with the file selected — `explorer.exe /select,`, which is the only way Windows
 * offers to point at a file rather than at its folder.
 *
 * False on a host that is not Windows, and false when the process could not start: a menu row that cannot
 * work says so in the band instead of pretending.
 */
internal fun revealInExplorer(file: File): Boolean = runCatching {
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    if (!os.contains("windows")) return@runCatching false
    ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
    true
}.getOrDefault(false)

/** [text] on the system clipboard; false when the host has no toolkit to write to (a headless test run). */
internal fun copyToClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    true
}.getOrDefault(false)
