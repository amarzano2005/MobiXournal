package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.rememberChromeInk

enum class ShareFormat { PDF, PNG, SVG, PORTABLE }

@Composable
internal fun ExportDrilldownView(
    isReadOnly: Boolean,
    onBack: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPng: () -> Unit,
    onExportSvg: () -> Unit,
    onSavePortable: () -> Unit,
    onShare: (ShareFormat) -> Unit,
) {
    val ink = rememberChromeInk()
    val accent = MaterialTheme.colorScheme.primary
    var shareMode by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Text("Back", style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Text("Export & Share", style = MaterialTheme.typography.labelLarge, color = ink)
            Spacer(Modifier.width(48.dp))
        }
        ExportModeSwitcher(
            shareMode = shareMode,
            ink = ink,
            onSelectShareMode = { shareMode = it },
        )
        ExportFormatGrid(
            isReadOnly = isReadOnly,
            shareMode = shareMode,
            ink = ink,
            accent = accent,
            onExportPdf = onExportPdf,
            onExportPng = onExportPng,
            onExportSvg = onExportSvg,
            onSavePortable = onSavePortable,
            onShare = onShare,
        )
    }
}

@Composable
private fun ExportFormatGrid(
    isReadOnly: Boolean,
    shareMode: Boolean,
    ink: Color,
    accent: Color,
    onExportPdf: () -> Unit,
    onExportPng: () -> Unit,
    onExportSvg: () -> Unit,
    onSavePortable: () -> Unit,
    onShare: (ShareFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormatCard(
                badge = "PDF",
                title = "PDF Document",
                subtitle = if (shareMode) "Send PDF via app" else "Save all pages",
                tint = DocumentRed,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = { if (shareMode) onShare(ShareFormat.PDF) else onExportPdf() },
                modifier = Modifier.weight(1f),
            )
            ExportFormatCard(
                badge = "PNG",
                title = "Page Image",
                subtitle = if (shareMode) "Send PNG via app" else "Save current page",
                tint = accent,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = { if (shareMode) onShare(ShareFormat.PNG) else onExportPng() },
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormatCard(
                badge = "SVG",
                title = "Scalable Vector",
                subtitle = if (shareMode) "Send SVG via app" else "Save vector file",
                tint = accent,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = { if (shareMode) onShare(ShareFormat.SVG) else onExportSvg() },
                modifier = Modifier.weight(1f),
            )
            ExportFormatCard(
                badge = "PKG",
                title = "Portable .pmobix",
                subtitle = if (shareMode) "Send notebook file" else "Save with assets",
                tint = accent,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = { if (shareMode) onShare(ShareFormat.PORTABLE) else onSavePortable() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExportModeSwitcher(
    shareMode: Boolean,
    ink: Color,
    onSelectShareMode: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rememberSheetSurfaceColor())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExportModePill(
            icon = Icons.Outlined.FileDownload,
            label = "Save to device",
            selected = !shareMode,
            ink = ink,
            onClick = { onSelectShareMode(false) },
            modifier = Modifier.weight(1f),
        )
        ExportModePill(
            icon = Icons.Outlined.Share,
            label = "Share via…",
            selected = shareMode,
            ink = ink,
            onClick = { onSelectShareMode(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExportModePill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) ink.copy(alpha = 0.14f) else Color.Transparent
    val fg = if (selected) ink else ink.copy(alpha = 0.58f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
        )
    }
}

@Composable
private fun ExportFormatCard(
    badge: String,
    title: String,
    subtitle: String,
    tint: Color,
    ink: Color,
    shareMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveTint = if (enabled) tint else ink.copy(alpha = 0.35f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(rememberSheetSurfaceColor())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = effectiveTint,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(effectiveTint.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Icon(
                if (shareMode) Icons.Outlined.Share else Icons.Outlined.FileDownload,
                contentDescription = null,
                tint = ink.copy(alpha = if (enabled) 0.45f else 0.25f),
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) ink else ink.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = if (enabled) 0.55f else 0.3f),
            maxLines = 1,
        )
    }
}
