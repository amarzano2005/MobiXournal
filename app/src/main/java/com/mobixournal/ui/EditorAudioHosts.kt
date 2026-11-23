package com.mobixournal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.mobixournal.format.SaveFormat

/**
 * Narrow Compose scope wrapper around [EditorTopBar] so 10 Hz audio state ticks only recompose the
 * top bar rather than the root editor screen and canvas panes.
 */
@Composable
internal fun EditorTopBarAudioHost(
    ui: EditorUiState,
    pane: PaneState,
    activeTabs: TabsUiState,
    paneTabs: List<TabsUiState>,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit,
    onShare: (ShareFormat) -> Unit,
    onSaveAsFormat: (SaveFormat) -> Unit,
    splitView: Boolean,
    onToggleSplitView: () -> Unit,
    audioProvider: @Composable () -> AudioUiState,
) {
    EditorTopBar(
        ui = ui,
        pane = pane,
        tabs = activeTabs,
        paneTabs = paneTabs,
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
        splitView = splitView,
        onToggleSplitView = onToggleSplitView,
        audio = audioProvider(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Narrow Compose scope wrapper around [AudioMiniPlayer] isolating playback playhead updates from
 * sibling editor overlays and canvases.
 */
@Composable
internal fun AudioMiniPlayerHost(
    tool: EditorTool,
    audioProvider: @Composable () -> AudioUiState,
    modifier: Modifier = Modifier,
) {
    val audio = audioProvider()
    LaunchedEffect(tool) {
        if (tool != EditorTool.PLAY_OBJECT && audio.miniPlayerVisible) {
            audio.onStopPlayback()
        }
    }
    AudioMiniPlayer(
        visible = audio.miniPlayerVisible,
        playing = audio.playing,
        playheadMs = audio.playheadMs ?: 0,
        durationMs = audio.durationMs ?: 0,
        waveform = audio.waveform,
        clipTitle = audio.clipTitle,
        playbackSpeed = audio.playbackSpeed,
        onTogglePlay = audio.onPlayAutonomous,
        onSeek = audio.onScrub,
        onSkip = audio.onSkip,
        onSpeedChange = audio.onSpeedChange,
        onClose = audio.onStopPlayback,
        modifier = modifier,
    )
}
