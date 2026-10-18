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
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.window.Dialog
import com.mobixournal.audio.LanguagePackStatus
import com.mobixournal.audio.SUPPORTED_TRANSCRIPTION_LANGUAGES
import com.mobixournal.audio.checkSpeechLanguagePack
import com.mobixournal.audio.triggerSpeechLanguageDownload
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
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
        border = BorderStroke(ModernTheme.hairlineStrokeWidthDp, hairlineColor),
        modifier = modifier,
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
            .clickable(
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
        val tint = when {
            playActive -> MaterialTheme.colorScheme.primary
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        Icon(
            imageVector = if (audio.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = if (audio.playing) "Metti in pausa riproduzione" else "Riproduci audio",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AudioCapsuleMenuContent(audio: AudioUiState, onDismiss: () -> Unit) {
    var showLanguageDialog by remember { mutableStateOf(false) }

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
            text = { Text(if (audio.playing) "Metti in pausa riproduzione" else "Riprendi riproduzione") },
            leadingIcon = {
                Icon(
                    if (audio.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                )
            },
            onClick = { audio.onPlayAutonomous(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(if (audio.miniPlayerVisible) "Nascondi mini-player" else "Mostra mini-player") },
            leadingIcon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
            onClick = { audio.onToggleMiniPlayer(); onDismiss() },
        )
    } else {
        DropdownMenuItem(
            text = { Text("Riproduci audio") },
            leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
            onClick = { audio.onPlayAutonomous(); onDismiss() },
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = {
            val currentLangName = SUPPORTED_TRANSCRIPTION_LANGUAGES.firstOrNull {
                it.code == audio.transcriptionLanguage
            }?.displayName ?: audio.transcriptionLanguage
            Column {
                Text("Lingua trascrizione")
                Text(
                    text = currentLangName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        leadingIcon = { Icon(Icons.Outlined.Subtitles, contentDescription = null) },
        onClick = { showLanguageDialog = true },
    )
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

    if (showLanguageDialog) {
        TranscriptionLanguageDialog(
            currentLanguage = audio.transcriptionLanguage,
            onLanguageSelected = { audio.onLanguageChange(it) },
            onDismiss = {
                showLanguageDialog = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun TranscriptionLanguageDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf(currentLanguage) }
    var packStatus by remember { mutableStateOf<LanguagePackStatus?>(null) }
    var checking by remember { mutableStateOf(true) }

    LaunchedEffect(selectedLang) {
        checking = true
        packStatus = null
        checkSpeechLanguagePack(context, selectedLang) { status ->
            packStatus = status
            checking = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = rememberFloatingSurfaceColor(),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Lingua Trascrizione Vocale",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                LanguageSelectionList(
                    selectedLang = selectedLang,
                    onSelect = {
                        selectedLang = it
                        onLanguageSelected(it)
                    },
                )

                HorizontalDivider()

                LanguagePackStatusCard(
                    checking = checking,
                    packStatus = packStatus,
                    selectedLang = selectedLang,
                    onTriggerDownload = { triggerSpeechLanguageDownload(context, selectedLang) },
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Chiudi")
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectionList(
    selectedLang: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (lang in SUPPORTED_TRANSCRIPTION_LANGUAGES) {
            val isSelected = lang.code == selectedLang
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent,
                    )
                    .clickable { onSelect(lang.code) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lang.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePackStatusCard(
    checking: Boolean,
    packStatus: LanguagePackStatus?,
    selectedLang: String,
    onTriggerDownload: () -> Unit,
) {
    when {
        checking -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Verifica pacchetto vocale...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        packStatus == LanguagePackStatus.INSTALLED -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Pacchetto offline installato sul dispositivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        packStatus == LanguagePackStatus.DOWNLOAD_AVAILABLE -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE65100).copy(alpha = 0.12f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "⚠️ Pacchetto offline non installato. Per trascrivere senza connessione, scarica il modello vocale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE65100),
                )
                Button(
                    onClick = onTriggerDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Scarica pacchetto", fontSize = 12.sp, color = Color.White)
                }
            }
        }
        else -> {
            Text(
                text = "Trascrizione gestita tramite Google Speech Services",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
