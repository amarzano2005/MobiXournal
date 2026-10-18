package com.mobixournal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
 * Houses the audio actions moved from the main toolbar:
 * 1. Microphone / Stop button (toggle recording; red square when recording; long-press for settings menu)
 * 2. Pause / Resume button (pauses recording without finalizing; resumes seamlessly)
 * 3. Play object button (activates stroke audio playback mode and controls mini-player, using GraphicEq)
 */
@Composable
fun AudioMiniCapsule(
    audio: AudioUiState,
    tool: EditorTool = EditorTool.PEN,
    onTool: (EditorTool) -> Unit = {},
    vertical: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isDark = chromeIsDark()
    val glassColor = if (isDark) Color(0xFF222226) else rememberFloatingSurfaceColor()
    val hairlineColor = if (isDark) Color(0x40FFFFFF) else ModernTheme.hairlineBorder(isDark)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = glassColor,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, hairlineColor),
        modifier = modifier,
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MicCapsuleButton(audio = audio, isDark = isDark)
                PauseCapsuleButton(audio = audio, isDark = isDark)
                PlayCapsuleButton(audio = audio, tool = tool, onTool = onTool, isDark = isDark)
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicCapsuleButton(audio = audio, isDark = isDark)
                PauseCapsuleButton(audio = audio, isDark = isDark)
                PlayCapsuleButton(audio = audio, tool = tool, onTool = onTool, isDark = isDark)
            }
        }
    }
}

private val VividRed = Color(0xFFE53935)
private val VividAmber = Color(0xFFF57C00)
private val VividGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicCapsuleButton(audio: AudioUiState, isDark: Boolean) {
    var showMenu by remember { mutableStateOf(false) }
    val isRecording = audio.recording
    val recordingBg = if (isRecording) VividRed else Color.Transparent

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(recordingBg)
            .combinedClickable(
                onClick = audio.onToggleRecord,
                onLongClick = { showMenu = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val iconTint = when {
            isRecording -> Color.White
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        Icon(
            imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
            contentDescription = if (isRecording) "Termina registrazione" else "Avvia registrazione audio",
            tint = iconTint,
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
private fun PauseCapsuleButton(audio: AudioUiState, isDark: Boolean) {
    val isRecording = audio.recording
    val isPaused = audio.recordingPaused

    val (bg, iconTint, icon) = when {
        !isRecording -> Triple(
            Color.Transparent,
            if (isDark) Color(0x33FFFFFF) else Color(0x33000000),
            Icons.Outlined.Pause,
        )
        isPaused -> Triple(
            VividGreen,
            Color.White,
            Icons.Outlined.PlayArrow,
        )
        else -> Triple(
            VividAmber,
            Color.White,
            Icons.Outlined.Pause,
        )
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg)
            .combinedClickable(
                onClick = { if (isRecording) audio.onTogglePauseRecord() },
                enabled = isRecording,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isPaused) "Riprendi registrazione" else "Metti in pausa registrazione",
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayCapsuleButton(
    audio: AudioUiState,
    tool: EditorTool,
    onTool: (EditorTool) -> Unit,
    isDark: Boolean,
) {
    val playActive = tool == EditorTool.PLAY_OBJECT || audio.playing
    val playBg by animateColorAsState(
        targetValue = if (playActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else Color.Transparent,
        label = "playBg",
    )

    Box(
        modifier = Modifier
            .size(24.dp)
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
        val tint = when {
            playActive -> MaterialTheme.colorScheme.primary
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        Icon(
            imageVector = if (audio.playing) Icons.Outlined.Pause else Icons.Outlined.GraphicEq,
            contentDescription = "Riproduci audio / Play object",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AudioCapsuleMenuContent(audio: AudioUiState, onDismiss: () -> Unit) {
    MenuHeading("Audio")
    DropdownMenuItem(
        text = { Text(if (audio.recording) "Termina registrazione" else "Avvia registrazione") },
        leadingIcon = {
            Icon(
                if (audio.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = null,
            )
        },
        onClick = { audio.onToggleRecord(); onDismiss() },
    )
    if (audio.recording) {
        DropdownMenuItem(
            text = { Text(if (audio.recordingPaused) "Riprendi registrazione" else "Metti in pausa") },
            leadingIcon = {
                Icon(
                    if (audio.recordingPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = null,
                )
            },
            onClick = { audio.onTogglePauseRecord(); onDismiss() },
        )
    }
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
            Switch(
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
