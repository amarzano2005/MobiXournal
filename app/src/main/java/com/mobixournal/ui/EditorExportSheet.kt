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
import com.mobixournal.format.SaveFormat
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
) {
    FormatGridSubView(
        title = "Export",
        isReadOnly = isReadOnly,
        shareMode = false,
        onBack = onBack,
        onPdf = onExportPdf,
        onPng = onExportPng,
        onSvg = onExportSvg,
        onPortable = onSavePortable,
    )
}

@Composable
internal fun ShareDrilldownView(
    isReadOnly: Boolean,
    onBack: () -> Unit,
    onShare: (ShareFormat) -> Unit,
) {
    FormatGridSubView(
        title = "Share",
        isReadOnly = isReadOnly,
        shareMode = true,
        onBack = onBack,
        onPdf = { onShare(ShareFormat.PDF) },
        onPng = { onShare(ShareFormat.PNG) },
        onSvg = { onShare(ShareFormat.SVG) },
        onPortable = { onShare(ShareFormat.PORTABLE) },
    )
}

@Composable
private fun FormatGridSubView(
    title: String,
    isReadOnly: Boolean,
    shareMode: Boolean,
    onBack: () -> Unit,
    onPdf: () -> Unit,
    onPng: () -> Unit,
    onSvg: () -> Unit,
    onPortable: () -> Unit,
) {
    val ink = rememberChromeInk()
    val accent = MaterialTheme.colorScheme.primary
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
            Text(title, style = MaterialTheme.typography.labelLarge, color = ink)
            Spacer(Modifier.width(48.dp))
        }
        ExportFormatGrid(
            isReadOnly = isReadOnly,
            shareMode = shareMode,
            ink = ink,
            accent = accent,
            onPdf = onPdf,
            onPng = onPng,
            onSvg = onSvg,
            onPortable = onPortable,
        )
    }
}

@Composable
private fun ExportFormatGrid(
    isReadOnly: Boolean,
    shareMode: Boolean,
    ink: Color,
    accent: Color,
    onPdf: () -> Unit,
    onPng: () -> Unit,
    onSvg: () -> Unit,
    onPortable: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormatCard(
                badge = "PDF",
                title = "PDF Document",
                subtitle = if (shareMode) "Send PDF via app" else "All pages · Vector",
                tint = DocumentRed,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = onPdf,
                modifier = Modifier.weight(1f),
            )
            ExportFormatCard(
                badge = "PNG",
                title = "Page Image",
                subtitle = if (shareMode) "Send PNG via app" else "Current page · High-res",
                tint = accent,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = onPng,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormatCard(
                badge = "SVG",
                title = "Scalable Vector",
                subtitle = if (shareMode) "Send SVG via app" else "Editable strokes",
                tint = accent,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = onSvg,
                modifier = Modifier.weight(1f),
            )
            ExportFormatCard(
                badge = "PKG",
                title = "Portable .pmobix",
                subtitle = if (shareMode) "Send notebook file" else "Includes assets",
                tint = accent,
                ink = ink,
                shareMode = shareMode,
                enabled = !isReadOnly,
                onClick = onPortable,
                modifier = Modifier.weight(1f),
            )
        }
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

@Composable
internal fun SaveAsDrilldownView(
    isReadOnly: Boolean,
    onBack: () -> Unit,
    onPickFormat: (SaveFormat) -> Unit,
) {
    val ink = rememberChromeInk()
    val accent = MaterialTheme.colorScheme.primary
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
            Text("Save As", style = MaterialTheme.typography.labelLarge, color = ink)
            Spacer(Modifier.width(48.dp))
        }
        SaveAsFormatRow(
            badge = ".mobix",
            title = "MobiXournal (.mobix)",
            subtitle = "Native format · Everything stays editable",
            accent = accent,
            ink = ink,
            enabled = !isReadOnly,
            onClick = { onPickFormat(SaveFormat.MOBIX) },
        )
        SaveAsFormatRow(
            badge = ".xopp",
            title = "Xournal++ (.xopp)",
            subtitle = "Standard gzip · PDF stays linked",
            accent = accent,
            ink = ink,
            enabled = !isReadOnly,
            onClick = { onPickFormat(SaveFormat.ORIGINAL) },
        )
        SaveAsFormatRow(
            badge = "ZIP",
            title = "Zipped (single .xopp)",
            subtitle = "Portable · PDF embedded inside",
            accent = accent,
            ink = ink,
            enabled = !isReadOnly,
            onClick = { onPickFormat(SaveFormat.ZIPPED) },
        )
    }
}

@Composable
private fun SaveAsFormatRow(
    badge: String,
    title: String,
    subtitle: String,
    accent: Color,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rememberSheetSurfaceColor())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) accent else ink.copy(alpha = 0.35f),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background((if (enabled) accent else ink).copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
