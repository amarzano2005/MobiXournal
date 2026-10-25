package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph

/**
 * The window's **header**: the menu, the tab strip, and the file commands on its right.
 *
 * The band carries the two things the mobile bar does, in the same order — the overflow menu on the
 * left and the actions that are used constantly on the right — and the strip between them. What the
 * mobile bar puts there is **undo, redo and the file actions** (its photo is the reference); the window
 * has the same four, with Save drawn as a floppy and the PDF export as a page of words, because a
 * window with no touch targets has to say what a button is by its picture alone.
 *
 * The *placement* chooser this band used to end with is gone (`docs/desktop-workstation.md`): moving the
 * strips is a setting, and a setting belongs in the Interface section rather than in the chrome it moves.
 */
@Composable
internal fun Header(
    tabs: DesktopTabs,
    sidebarVisible: Boolean,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    onExportSvg: () -> Unit,
    onToggleSidebar: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val tab = tabs.active
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.headerHeight)
            .bandSurface()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderMenu(
            sidebarVisible = sidebarVisible,
            canSave = tab?.isDirty == true,
            canExport = tab != null,
            onNew = onNew,
            onOpen = onOpen,
            onSave = onSave,
            onSaveAs = onSaveAs,
            onExportPdf = onExportPdf,
            onExportSvg = onExportSvg,
            onToggleSidebar = onToggleSidebar,
        )
        Spacer(Modifier.width(10.dp))
        TabStrip(
            tabs = tabs,
            onActivate = onActivate,
            onClose = onClose,
            onMove = onMove,
            onNew = onNew,
            modifier = Modifier.weight(1f),
        )
        // Two groups, not one row of four: the history step and the file command are answered by
        // different questions ("what did I just do" and "where does this go"), and the wider gap is the
        // whole of the difference this band has room to draw.
        Spacer(Modifier.width(12.dp))
        HeaderGlyph(
            active = false,
            enabled = tab?.canUndo == true,
            onClick = onUndo,
        ) { color -> drawChromeGlyph(ChromeGlyph.UNDO, color) }
        Spacer(Modifier.width(6.dp))
        HeaderGlyph(
            active = false,
            enabled = tab?.canRedo == true,
            onClick = onRedo,
        ) { color -> drawChromeGlyph(ChromeGlyph.REDO, color) }
        Spacer(Modifier.width(12.dp))
        // The tablet's colours for the two file commands: Save in the accent, PDF in the document red.
        // They were both chrome ink here, which made the one command that writes a PDF look like the
        // history steps beside it. The rule is `docs/ui-modernization.md`'s, not the window's.
        HeaderGlyph(
            active = false,
            enabled = tab?.isDirty == true,
            colour = DesktopTheme.accent,
            onClick = onSave,
        ) { color -> drawChromeGlyph(ChromeGlyph.SAVE, color) }
        Spacer(Modifier.width(6.dp))
        HeaderGlyph(
            active = false,
            enabled = tab != null,
            colour = DesktopTheme.documentRed,
            onClick = onExportPdf,
        ) { color -> drawChromeGlyph(ChromeGlyph.PDF, color) }
    }
}

/**
 * A header control: one of the catalogue's glyphs on a **capsule of the header's own glass** — the
 * tablet's icon buttons beside the dock, at the desktop's size.
 *
 * [enabled] is what an empty history or a clean document looks like: the capsule stays (the band is not
 * redesigned by state) and the glyph goes muted, the same tone the menu's disabled rows wear. A disabled
 * control takes no pointer at all, so a click on it is not a command that did nothing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HeaderGlyph(
    active: Boolean,
    enabled: Boolean = true,
    /** A command that wears one of the **named** colours (Save, PDF); null keeps the chrome's ink. */
    colour: Color? = null,
    onClick: () -> Unit,
    draw: DrawScope.(androidx.compose.ui.graphics.Color) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    // Two states, two fills: the picked steel for an open menu, the **hover step** for a resting
    // pointer, never the picked fill for a mere hover (`docs/interface-contract.md` §6).
    val fill = when {
        active && enabled -> DesktopTheme.activeSlot
        hovered && enabled -> DesktopTheme.hoverSlot
        else -> DesktopTheme.glass
    }
    Box(
        Modifier
            .size(DesktopTheme.headerControlSize)
            .glassCapsule(
                cornerRadius = DesktopTheme.headerControlSize / 2,
                fill = fill,
                shadow = DesktopTheme.bandShadow,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(enabled, active) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(DesktopTheme.headerGlyphSize)) {
            // The chrome ink, lit or not: on the tablet a header glyph is white and only its capsule
            // changes tone when it is active.
            draw(if (enabled) colour ?: DesktopTheme.chromeInk else DesktopTheme.inkMuted)
        }
    }
}
