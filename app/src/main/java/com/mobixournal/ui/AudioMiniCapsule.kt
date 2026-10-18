package com.mobixournal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

/**
 * Modern floating frosted-glass mini capsule situated in the top bar leading empty space
 * directly above the main toolbar lane.
 * Houses the two audio buttons moved from the main toolbar:
 * 1. Microphone button (toggle audio recording, pulsing red when recording; long-press for audio menu)
 * 2. Play object button (activates stroke audio playback mode and controls mini-player, using GraphicEq)
 */
@Composable
fun AudioMiniCapsule(
    audio: AudioUiState,
    tool: EditorTool = EditorTool.PEN,
    onTool: (EditorTool) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = chromeIsDark()
    val glassColor = rememberFloatingSurfaceColor()
    val hairlineColor = ModernTheme.hairlineBorder(isDark)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = glassColor,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, hairlineColor),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicCapsuleButton(audio = audio)
            PlayCapsuleButton(audio = audio, tool = tool, onTool = onTool)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicCapsuleButton(audio: AudioUiState) {
    var showMenu by remember { mutableStateOf(false) }
    val recordingBg by animateColorAsState(
        targetValue = if (audio.recording) MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        else Color.Transparent,
        label = "recordingBg",
    )

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(recordingBg)
            .combinedClickable(
                onClick = audio.onToggleRecord,
                onLongClick = { showMenu = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (audio.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
            contentDescription = if (audio.recording) "Ferma registrazione" else "Avvia registrazione audio",
            tint = if (audio.recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )

        ToolbarMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            AudioCapsuleMenuContent(audio = audio, onDismiss = { showMenu = false })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayCapsuleButton(
    audio: AudioUiState,
    tool: EditorTool,
    onTool: (EditorTool) -> Unit,
) {
    val playActive = tool == EditorTool.PLAY_OBJECT || audio.playing
    val playBg by animateColorAsState(
        targetValue = if (playActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else Color.Transparent,
        label = "playBg",
    )

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(playBg)
            .combinedClickable(
                onClick = {
                    if (tool == EditorTool.PLAY_OBJECT) {
                        if (audio.clipOn) {
                            if (!audio.miniPlayerVisible) {
                                audio.onToggleMiniPlayer()
                            } else {
                                if (audio.playing) audio.onPause() else audio.onResume()
                            }
                        } else {
                            onTool(EditorTool.PEN)
                        }
                    } else {
                        onTool(EditorTool.PLAY_OBJECT)
                        if (audio.clipOn && !audio.miniPlayerVisible) {
                            audio.onToggleMiniPlayer()
                        }
                    }
                },
                onLongClick = { audio.onToggleMiniPlayer() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (audio.playing) Icons.Outlined.Pause else Icons.Outlined.GraphicEq,
            contentDescription = "Riproduci audio / Play object",
            tint = if (playActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AudioCapsuleMenuContent(audio: AudioUiState, onDismiss: () -> Unit) {
    MenuHeading("Audio")
    DropdownMenuItem(
        text = { Text(if (audio.recording) "Ferma registrazione" else "Avvia registrazione") },
        leadingIcon = {
            Icon(
                if (audio.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = null,
            )
        },
        onClick = { audio.onToggleRecord(); onDismiss() },
    )
    if (audio.clipOn) {
        DropdownMenuItem(
            text = { Text(if (audio.miniPlayerVisible) "Nascondi mini-player" else "Mostra mini-player") },
            leadingIcon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
            onClick = { audio.onToggleMiniPlayer(); onDismiss() },
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Salva audio separato") },
        trailingIcon = {
            androidx.compose.material3.Switch(
                checked = audio.saveStandalone,
                onCheckedChange = { audio.onToggleSaveStandalone(it) },
            )
        },
        onClick = { audio.onToggleSaveStandalone(!audio.saveStandalone) },
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(if (audio.folderChosen) "Cambia cartella audio…" else "Scegli cartella audio…") },
        onClick = { audio.onChooseFolder(); onDismiss() },
    )
}
