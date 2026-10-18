package com.mobixournal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

/**
 * Modern floating frosted-glass mini capsule situated directly above the main toolbar.
 * Houses:
 * 1. Microphone button: 1-tap toggle for audio recording (pulsing/colored when active)
 * 2. Player button: 1-tap toggle for the audio player / play-pause playback
 */
@Composable
fun AudioMiniCapsule(
    audio: AudioUiState,
    horizontal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isDark = chromeIsDark()
    val glassColor = rememberFloatingSurfaceColor()
    val hairlineColor = ModernTheme.hairlineBorder(isDark)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = glassColor,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, hairlineColor),
        modifier = modifier,
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleButtons(audio = audio)
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CapsuleButtons(audio = audio)
            }
        }
    }
}

@Composable
private fun CapsuleButtons(audio: AudioUiState) {
    val recordingBg by animateColorAsState(
        targetValue = if (audio.recording) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        else Color.Transparent,
        label = "recordingBg",
    )
    val playingBg by animateColorAsState(
        targetValue = if (audio.playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else Color.Transparent,
        label = "playingBg",
    )

    // Microphone button: toggle audio recording
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(recordingBg)
            .clickable(onClick = audio.onToggleRecord),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (audio.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
            contentDescription = if (audio.recording) "Ferma registrazione" else "Avvia registrazione audio",
            tint = if (audio.recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }

    // Player button: toggle mini-player visibility or playback
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(playingBg)
            .clickable {
                if (audio.clipOn) {
                    if (!audio.miniPlayerVisible) {
                        audio.onToggleMiniPlayer()
                    } else {
                        if (audio.playing) audio.onPause() else audio.onResume()
                    }
                } else {
                    audio.onToggleMiniPlayer()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val playerIcon = when {
            audio.playing -> Icons.Outlined.Pause
            audio.clipOn -> Icons.Outlined.PlayArrow
            else -> Icons.Outlined.GraphicEq
        }
        val playerTint = when {
            audio.playing -> MaterialTheme.colorScheme.primary
            audio.clipOn -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        }
        Icon(
            imageVector = playerIcon,
            contentDescription = "Riproduttore audio",
            tint = playerTint,
            modifier = Modifier.size(18.dp),
        )
    }
}
