package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph

/**
 * The window's **overflow menu** — the mobile bar's menu, ported and laid out the same way.
 *
 * The tablet's menu (its photo is the reference) is four **tiles** of art over a word across the top,
 * the exports as a pair of **chips** under them, and the plain commands below a hairline. The window
 * keeps that shape with its own commands: New, Open, Save and Save As on the top row; the two exports
 * as chips, in the accent the tablet gives them; the page list under the rule.
 *
 * What is deliberately **not** here is the tablet's Import PDF, Page as PNG and Settings: the first two
 * are commands the window does not have yet, and a dead row in a menu is worse than one the window
 * grows into. The rows are added as the commands land (`TODO.toml`).
 *
 * The panel is [MenuPanel] rather than this `Popup`'s body, because a popup cannot be rendered in a
 * headless scene: the panel is what a test draws, clicks and files as a PNG, and this wrapper only adds
 * the button that opens it and the offset it opens at.
 */
@Composable
internal fun HeaderMenu(
    sidebarVisible: Boolean,
    canSave: Boolean,
    /** Whether a document is open at all — what makes the two export chips available. */
    canExport: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    onExportSvg: () -> Unit,
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        HeaderGlyph(active = open, onClick = { open = !open }) { color ->
            drawChromeGlyph(ChromeGlyph.MENU, color)
        }
        if (open) {
            Popup(
                offset = with(density) { IntOffset(0, (DesktopTheme.headerHeight + 6.dp).roundToPx()) },
                onDismissRequest = { open = false },
                properties = FloatingSurface,
            ) {
                // Every command closes the menu first: the panel is a set of commands, not a toolbox.
                MenuPanel(
                    sidebarVisible = sidebarVisible,
                    canSave = canSave,
                    canExport = canExport,
                    onNew = { open = false; onNew() },
                    onOpen = { open = false; onOpen() },
                    onSave = { open = false; onSave() },
                    onSaveAs = { open = false; onSaveAs() },
                    onExportPdf = { open = false; onExportPdf() },
                    onExportSvg = { open = false; onExportSvg() },
                    onToggleSidebar = { open = false; onToggleSidebar() },
                    onSettings = { open = false; onSettings() },
                )
            }
        }
    }
}

/** The menu's panel: four tiles, two chips, and the page list — the mobile menu, with its rules. */
@Composable
internal fun MenuPanel(
    sidebarVisible: Boolean,
    canSave: Boolean,
    canExport: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    onExportSvg: () -> Unit,
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .glassSurface()
            .width(MenuMetrics.width)
            .padding(MenuMetrics.padding),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MenuMetrics.tileGap),
        ) {
            MenuTile("New", ChromeGlyph.NEW, enabled = true, onClick = onNew)
            MenuTile("Open", ChromeGlyph.OPEN, enabled = true, onClick = onOpen)
            MenuTile("Save", ChromeGlyph.SAVE, enabled = canSave, onClick = onSave)
            MenuTile("Save As", ChromeGlyph.SAVE_AS, enabled = true, onClick = onSaveAs)
        }
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MenuMetrics.chipGap),
        ) {
            // The tablet's rule for the two exports: the PDF chip is the document red, the page raster
            // stays in the accent. Colouring both the same made the PDF look like any other export.
            MenuChip("Export PDF", ChromeGlyph.PDF, canExport, Modifier.weight(1f), DesktopTheme.documentRed, onExportPdf)
            MenuChip("Export SVG", ChromeGlyph.PICTURE, canExport, Modifier.weight(1f), DesktopTheme.accent, onExportSvg)
        }
        Spacer(Modifier.height(MenuMetrics.sectionGap))
        MenuDivider()
        MenuRow(
            label = if (sidebarVisible) "Hide page list" else "Show page list",
            shortcut = "Ctrl+B",
            enabled = true,
            onClick = onToggleSidebar,
        )
        // The tablet's Settings row, in the window: its own page rather than a card over the sheet, so it
        // is here with the other commands and not in the chrome it changes.
        MenuRow(
            label = "Settings",
            shortcut = "Ctrl+,",
            enabled = true,
            onClick = onSettings,
        )
    }
}

/**
 * The panel's numbers, in one place: the layout reads them and so does `DesktopMenuTest`, which clicks
 * the panel by the same arithmetic the panel was laid out with. A test that guesses a coordinate is a
 * test that fails the day a gap changes for a good reason.
 */
internal object MenuMetrics {
    val width = 320.dp
    val padding = 10.dp
    val tileGap = 2.dp
    val tileHeight = 64.dp
    val chipGap = 8.dp
    val chipHeight = 38.dp
    val sectionGap = 10.dp

    /** The rule between the chips and the page list, with its own air above and below. */
    val dividerHeight = 11.dp
    val rowHeight = 34.dp
    val chipRadius = 12.dp

    /** One tile's width: four of them, their gaps, and the panel's own padding. */
    val tileWidth: androidx.compose.ui.unit.Dp
        get() = (width - padding * 2 - tileGap * 3) / 4

    /** One chip's width: two of them and their gap. */
    val chipWidth: androidx.compose.ui.unit.Dp
        get() = (width - padding * 2 - chipGap) / 2
}

/**
 * One of the four top commands: its glyph over its word, the whole tile one target.
 *
 * The tile is the mobile menu's own shape — art above the label rather than beside it — because a row
 * of four words is a list, and a row of four pictures with words under them is a palette.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowScope.MenuTile(label: String, glyph: ChromeGlyph, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = MenuMetrics.tileHeight)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(22.dp)) {
            drawChromeGlyph(glyph, if (enabled) DesktopTheme.chromeInk else DesktopTheme.inkMuted)
        }
        Text(
            label,
            color = if (enabled) DesktopTheme.chromeInk else DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 1,
        )
    }
}

/**
 * An export: art and word in the **accent**, on a chip of its own — the tablet's blue chips.
 *
 * The chip is a capsule rather than a fifth tile because exporting is not a file command (nothing about
 * the document changes): the tablet draws that difference and the window keeps it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuChip(
    label: String,
    glyph: ChromeGlyph,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** The command's own colour while it can act — red for a PDF, the accent for a page export. */
    tone: Color,
    onClick: () -> Unit,
) {
    val ink = if (enabled) tone else DesktopTheme.inkMuted
    Row(
        modifier
            .height(MenuMetrics.chipHeight)
            .glassCapsule(
                cornerRadius = MenuMetrics.chipRadius,
                fill = DesktopTheme.glass,
                shadow = DesktopTheme.capsuleShadow,
            )
            .hoverFill(DesktopTheme.capsuleShape, active = !enabled)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(18.dp)) { drawChromeGlyph(glyph, ink) }
        Text(label, color = ink, style = DesktopTheme.body, maxLines = 1)
    }
}

/** One plain row of the menu: a label, an optional accelerator, and the disabled tone when it cannot act. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuRow(
    label: String,
    shortcut: String? = null,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MenuMetrics.rowHeight)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape, active = false)
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            modifier = Modifier.weight(1f),
        )
        if (shortcut != null) {
            Text(shortcut, color = DesktopTheme.accent, style = DesktopTheme.numeric)
        }
    }
}

/** A hairline between two parts of the menu, in the band's own rim ink. */
@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    Spacer(Modifier.height(4.dp))
}
