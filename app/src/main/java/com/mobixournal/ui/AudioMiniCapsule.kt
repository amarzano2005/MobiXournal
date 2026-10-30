package com.mobixournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.ui.theme.AlertOrange
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.MutedTintDark
import com.mobixournal.ui.theme.MutedTintLight
import com.mobixournal.ui.theme.SignalAmber
import com.mobixournal.ui.theme.SignalGreen
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/**
 * Modern floating frosted-glass mini capsule situated in the top bar leading empty space
 * directly above the main toolbar lane.
 * Houses the audio actions moved from the main toolbar:
 * 1. Microphone / Stop button (toggle recording; solid red when recording; long-press for settings menu)
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
    val glassColor = rememberGlassSurfaceColor()
    val hairlineColor = ModernTheme.hairlineBorder(isDark)

    Surface(
        shape = ModernTheme.capsuleShape,
        color = glassColor,
        shadowElevation = 6.dp,
        modifier = modifier.floatingRim(ModernTheme.capsuleCornerRadiusDp),
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MicCapsuleButton(audio = audio, isDark = isDark)
                PauseCapsuleButton(audio = audio, isDark = isDark)
                PlayCapsuleButton(audio = audio, tool = tool, onTool = onTool, isDark = isDark)
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicCapsuleButton(audio = audio, isDark = isDark)
                PauseCapsuleButton(audio = audio, isDark = isDark)
                PlayCapsuleButton(audio = audio, tool = tool, onTool = onTool, isDark = isDark)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicCapsuleButton(audio: AudioUiState, isDark: Boolean) {
    var showMenu by remember { mutableStateOf(false) }
    val isRecording = audio.recording
    // A take belongs to its tab: while one runs in another document there is one microphone and it is
    // that tab's, so this button reports it instead of opening a second capture (`ownsRecording`).
    val elsewhere = audio.recordingElsewhere
    val recordingBg = if (isRecording) DocumentRed else Color.Transparent

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(recordingBg)
            .combinedClickable(
                enabled = elsewhere == null,
                onClick = audio.onToggleRecord,
                onLongClick = { showMenu = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val iconTint = when {
            isRecording -> Color.White
            elsewhere != null -> if (isDark) MutedTintDark else MutedTintLight
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        Icon(
            imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
            contentDescription = when {
                isRecording -> "Stop recording"
                elsewhere != null -> "Recording in another tab"
                else -> "Start audio recording"
            },
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
            if (isDark) MutedTintDark else MutedTintLight,
            Icons.Outlined.Pause,
        )
        // Resuming a **recording** is not starting **playback**: a record dot says "the microphone
        // is open again", where the play triangle said the same thing as the button below it.
        isPaused -> Triple(
            SignalGreen,
            Color.White,
            Icons.Outlined.FiberManualRecord,
        )
        else -> Triple(
            SignalAmber,
            Color.White,
            Icons.Outlined.Pause,
        )
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                onClick = { if (isRecording) audio.onTogglePauseRecord() },
                enabled = isRecording,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isPaused) "Resume recording" else "Pause recording",
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
    val playActive = tool == EditorTool.PLAY_OBJECT || audio.playing || audio.clipOn
    val playBg = if (playActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(playBg)
            .combinedClickable(
                onClick = {
                    if (tool != EditorTool.PLAY_OBJECT) {
                        onTool(EditorTool.PLAY_OBJECT)
                    }
                    audio.onPlayAutonomous()
                },
                onLongClick = { audio.onToggleMiniPlayer() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The button keeps its play/pause meaning whatever the recording is doing: state belongs to
        // the pop-up, so the toolbar never turns into a status bar.
        val tint = when {
            playActive -> MaterialTheme.colorScheme.primary
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        Icon(
            imageVector = if (audio.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = if (audio.playing) "Pause playback" else "Play audio",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AudioCapsuleMenuContent(audio: AudioUiState, onDismiss: () -> Unit) {
    MenuHeading("Audio")
    // The take is another tab's: the row says whose, and is not a command here.
    audio.recordingElsewhere?.let { title ->
        DropdownMenuItem(
            text = { Text("Recording in $title") },
            leadingIcon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
            enabled = false,
            onClick = {},
        )
    }
    DropdownMenuItem(
        text = { Text(if (audio.recording) "Stop recording" else "Start recording") },
        leadingIcon = {
            Icon(
                if (audio.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = null,
            )
        },
        enabled = audio.recordingElsewhere == null,
        onClick = { audio.onToggleRecord(); onDismiss() },
    )
    if (audio.recording) {
        DropdownMenuItem(
            text = { Text(if (audio.recordingPaused) "Resume recording" else "Pause") },
            leadingIcon = {
                Icon(
                    if (audio.recordingPaused) Icons.Outlined.FiberManualRecord else Icons.Outlined.Pause,
                    contentDescription = null,
                )
            },
            onClick = { audio.onTogglePauseRecord(); onDismiss() },
        )
    }
    if (audio.clipOn) {
        DropdownMenuItem(
            text = { Text(if (audio.playing) "Pause playback" else "Resume playback") },
            leadingIcon = {
                Icon(
                    if (audio.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                )
            },
            onClick = { audio.onPlayAutonomous(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(if (audio.miniPlayerVisible) "Hide mini-player" else "Show mini-player") },
            leadingIcon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
            onClick = { audio.onToggleMiniPlayer(); onDismiss() },
        )
    } else {
        DropdownMenuItem(
            text = { Text("Play audio") },
            leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
            onClick = { audio.onPlayAutonomous(); onDismiss() },
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Save separate audio") },
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
        text = { Text(if (audio.folderChosen) "Change audio folder…" else "Choose audio folder…") },
        onClick = { audio.onChooseFolder(); onDismiss() },
    )
}

