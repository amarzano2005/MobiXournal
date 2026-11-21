package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark

/**
 * Unified **Files & Data** section (`Documents` + `Storage` + `Backup`) with visual new-document
 * format cards, cache budget indicator, storage limits, and JSON backup/reset actions.
 */
@Composable
fun FilesDataSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("New documents") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormatOptionCard(
                title = "MobiXournal (.mobix)",
                subtitle = "Editable plots, tables & circuits",
                selected = settings.newDocumentFormat == NewDocumentFormat.MOBIX,
                onClick = { onChange(settings.copy(newDocumentFormat = NewDocumentFormat.MOBIX)) },
                modifier = Modifier.weight(1f),
            )
            FormatOptionCard(
                title = "Xournal++ (.xopp)",
                subtitle = "Universal flattened ink",
                selected = settings.newDocumentFormat == NewDocumentFormat.XOPP,
                onClick = { onChange(settings.copy(newDocumentFormat = NewDocumentFormat.XOPP)) },
                modifier = Modifier.weight(1f),
            )
        }
        SettingsDivider()
        SwitchRow(
            title = "Save automatically",
            subtitle = "Writes the document a few seconds after you stop writing (only for named files).",
            checked = settings.autoSave,
            onCheckedChange = { onChange(settings.copy(autoSave = it)) },
        )
    }
    StorageSection(settings, onChange)
    BackupSection(settings, onChange)
}

@Composable
private fun FormatOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = chromeIsDark()
    val shape = RoundedCornerShape(12.dp)
    val border = if (selected) MaterialTheme.colorScheme.primary else ModernTheme.hairlineBorder(dark)
    val fill = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
