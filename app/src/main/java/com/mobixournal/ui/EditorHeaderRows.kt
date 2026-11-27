package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.LocalChromeLayoutPolicy
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberChromeInk

/** Height of Row 1 (document tabs & naked header controls), below the status-bar inset. */
internal val HEADER_DOC_ROW_HEIGHT = 44.dp

/** Height of Row 2 (corner active-tool ring + dedicated STEM dock + horizontal Audio capsule). */
internal val HEADER_TOOLS_ROW_HEIGHT = 46.dp

/**
 * Row 1 right-hand document and navigation actions: naked glyphs at rest directly on the header band,
 * grouped with [TopBarToolDivider] hairlines instead of individual floating glass capsules.
 */
@Composable
internal fun EditorDocumentActions(
    ui: EditorUiState,
    pane: PaneState,
    stripTabs: TabsUiState,
    settings: AppSettings?,
    onSettingsChange: ((AppSettings) -> Unit)?,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit,
    onShare: (ShareFormat) -> Unit,
    onSaveAsFormat: (com.mobixournal.format.SaveFormat) -> Unit,
    splitView: Boolean,
    onToggleSplitView: () -> Unit,
    searchProgressProvider: () -> Float = { 0f },
    onSearchAnchorRightWindowX: (Float) -> Unit = {},
) {
    val chromePolicy = LocalChromeLayoutPolicy.current
    val chromeInk = rememberChromeInk()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = (1f - searchProgressProvider() * 2.5f).coerceIn(0f, 1f)
            },
        ) {
            NavigationCapsule(ui)
        }
        if (chromePolicy.showInlineSearchWhenClosed) {
            SearchTriggerButton(
                open = pane.searchOpen,
                progressProvider = searchProgressProvider,
                onOpen = { pane.searchOpen = true },
                modifier = Modifier.onGloballyPositioned { coords ->
                    onSearchAnchorRightWindowX(coords.positionInWindow().x + coords.size.width)
                },
            )
        }
        if (chromePolicy.showInlineSplitView) {
            IconButton(onClick = onToggleSplitView, modifier = Modifier.size(36.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (splitView) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.VerticalSplit,
                        contentDescription = if (splitView) "Close split view" else "Split view",
                        tint = if (splitView) MaterialTheme.colorScheme.primary else chromeInk,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        TopBarToolDivider(air = 4.dp)
        UndoRedoButtons(pane = pane, chromeInk = chromeInk)
        TopBarToolDivider(air = 4.dp)
        QuickPdfExport(ui, pane)
        val canSave = !pane.isReadOnly && stripTabs.canSave
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canSave) MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f) else Color.Transparent),
            )
            SaveControl(enabled = canSave, onSave = onSave)
        }
        EditorOverflowMenu(
            ui = ui,
            pane = pane,
            stripTabs = stripTabs,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onOpen = onOpen,
            onNewTab = onNewTab,
            onSave = onSave,
            onImportPdf = onImportPdf,
            onExportPagePng = onExportPagePng,
            onExportPageSvg = onExportPageSvg,
            onSavePortableCopy = onSavePortableCopy,
            onShare = onShare,
            onSaveAsFormat = onSaveAsFormat,
        )
    }
}

@Composable
private fun UndoRedoButtons(pane: PaneState, chromeInk: Color) {
    val undoEnabled = !pane.isReadOnly && pane.canUndo
    val redoEnabled = !pane.isReadOnly && pane.canRedo
    IconButton(onClick = { pane.surface?.undo() }, enabled = undoEnabled, modifier = Modifier.size(36.dp)) {
        Icon(
            ChromeGlyph.UNDO.imageVector(),
            contentDescription = "Undo",
            tint = if (undoEnabled) chromeInk else chromeInk.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
    IconButton(onClick = { pane.surface?.redo() }, enabled = redoEnabled, modifier = Modifier.size(36.dp)) {
        Icon(
            ChromeGlyph.REDO.imageVector(),
            contentDescription = "Redo",
            tint = if (redoEnabled) chromeInk else chromeInk.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Row 2 of the header: places the frameless [StandaloneToolIndicator] directly above the rail's column
 * (forming the pivot of the L-frame), gives the full remaining width to [TopBarToolsRow], and keeps a
 * compact horizontal [AudioMiniCapsule] on the opposite side.
 */
@Composable
internal fun EditorToolsStripRow(
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    audio: AudioUiState,
    railOnLeft: Boolean,
) {
    val styleCallbacks = rememberToolbarStyleCallbacks(ui, pane.surface, settings, onSettingsChange)
    val indicator = @Composable {
        StandaloneToolIndicator(
            ui = ui,
            styleCallbacks = styleCallbacks,
            triangleKind = settings.triangleKind,
            trapezoidKind = settings.trapezoidKind,
        )
    }
    val audioPill = @Composable {
        AudioMiniCapsule(
            audio = audio,
            tool = ui.tool,
            vertical = false,
            frameless = true,
            onTool = { newTool -> pane.surface?.activateTool(newTool, ui, settings, onSettingsChange) },
        )
    }
    val showAudioCapsule = !LocalChromeLayoutPolicy.current.isCompact || audio.recording || audio.playing || audio.clipOn

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_TOOLS_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (railOnLeft) {
            indicator()
            TopBarToolDivider(air = 4.dp)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                TopBarToolsRow(ui = ui, pane = pane, settings = settings, onSettingsChange = onSettingsChange)
            }
            if (showAudioCapsule) {
                TopBarToolDivider(air = 4.dp)
                Box(modifier = Modifier.padding(end = 8.dp)) { audioPill() }
            }
        } else {
            if (showAudioCapsule) {
                Box(modifier = Modifier.padding(start = 8.dp)) { audioPill() }
                TopBarToolDivider(air = 4.dp)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TopBarToolsRow(ui = ui, pane = pane, settings = settings, onSettingsChange = onSettingsChange)
            }
            TopBarToolDivider(air = 4.dp)
            indicator()
        }
    }
}
