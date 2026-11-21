package com.mobixournal.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.rememberChromeInk
import java.util.Locale


@Composable
internal fun DocumentTabView(
    isReadOnly: Boolean,
    showSearchAction: Boolean = false,
    onNewTab: () -> Unit,
    onOpen: () -> Unit,
    onSaveAs: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val ink = rememberChromeInk()
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactFileTile(
                icon = ChromeGlyph.NEW.imageVector(),
                label = "New",
                onClick = onNewTab,
                modifier = Modifier.weight(1f),
            )
            CompactFileTile(
                icon = ChromeGlyph.OPEN.imageVector(),
                label = "Open",
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            )
            CompactFileTile(
                icon = ChromeGlyph.SAVE_AS.imageVector(),
                label = "Save As",
                enabled = !isReadOnly,
                onClick = onSaveAs,
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(rememberSheetSurfaceColor()),
        ) {
            if (showSearchAction) {
                SheetActionRow(
                    icon = Icons.Outlined.Search,
                    badgeTint = accent,
                    title = "Search in document",
                    subtitle = null,
                    ink = ink,
                    enabled = true,
                    onClick = onOpenSearch,
                )
            }
            SheetActionRow(
                icon = ChromeGlyph.PDF.imageVector(),
                badgeTint = DocumentRed,
                title = "Import PDF",
                subtitle = null,
                ink = ink,
                enabled = !isReadOnly,
                onClick = onImportPdf,
            )
            SheetActionRow(
                icon = ChromeGlyph.EXPORT.imageVector(),
                badgeTint = accent,
                title = "Export",
                subtitle = "PDF, PNG, SVG, PMOBIX",
                ink = ink,
                enabled = !isReadOnly,
                onClick = onOpenExport,
            )
        }
    }
}

@Composable
private fun CompactFileTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ink = rememberChromeInk()
    val contentColor = if (enabled) ink else ink.copy(alpha = 0.38f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(rememberSheetSurfaceColor())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    badgeTint: Color,
    title: String,
    subtitle: String?,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val effectiveTint = if (enabled) badgeTint else ink.copy(alpha = 0.35f)
    val textTint = if (enabled) ink else ink.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(effectiveTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = effectiveTint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = textTint)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = if (enabled) 0.54f else 0.32f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = ink.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun StudioTabView(
    settings: AppSettings?,
    fullPage: Boolean,
    penDiagnostics: Boolean,
    onSelectPenPreset: (PenPreset) -> Unit,
    onToggleFingerDraws: () -> Unit,
    onToggleFullPage: () -> Unit,
    onToggleToolbarPosition: () -> Unit,
    onTogglePenDiagnostics: () -> Unit,
) {
    val ink = rememberChromeInk()
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (settings != null && settings.penPresets.isNotEmpty()) {
            PresetCurveGrid(
                presets = settings.penPresets,
                selectedId = settings.selectedPenPresetId,
                ink = ink,
                accent = accent,
                onSelect = onSelectPenPreset,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(rememberSheetSurfaceColor()),
        ) {
            if (settings != null) {
                SheetSwitchRow(
                    icon = Icons.Outlined.TouchApp,
                    label = "Finger drawing",
                    checked = settings.fingerDraws,
                    ink = ink,
                    accent = accent,
                    onClick = onToggleFingerDraws,
                )
            }
            SheetSwitchRow(
                icon = Icons.Outlined.Fullscreen,
                label = "Focus mode",
                checked = fullPage,
                ink = ink,
                accent = accent,
                onClick = onToggleFullPage,
            )
            if (settings != null) {
                SheetValueRow(
                    icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                    label = "Toolbar side",
                    value = settings.toolbarPosition.label,
                    ink = ink,
                    onClick = onToggleToolbarPosition,
                )
            }
            SheetSwitchRow(
                icon = Icons.Outlined.BugReport,
                label = "Pen diagnostics",
                checked = penDiagnostics,
                ink = ink,
                accent = accent,
                onClick = onTogglePenDiagnostics,
            )
        }
    }
}

@Composable
private fun PresetCurveGrid(
    presets: List<PenPreset>,
    selectedId: String,
    ink: Color,
    accent: Color,
    onSelect: (PenPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { preset ->
                    PresetCurveCard(
                        preset = preset,
                        selected = preset.id == selectedId,
                        ink = ink,
                        accent = accent,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PresetCurveCard(
    preset: PenPreset,
    selected: Boolean,
    ink: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) accent.copy(alpha = 0.14f) else rememberSheetSurfaceColor()
    val strokeColor = if (selected) accent else ink.copy(alpha = 0.8f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            val minW = (1.4f + preset.minimumPressure * 3.5f).coerceIn(1.2f, 6f)
            val maxW = (minW * preset.pressureMultiplier).coerceIn(1.6f, 8.5f)
            val midX = size.width * 0.5f
            val path1 = Path().apply {
                moveTo(4f, size.height * 0.7f)
                quadraticBezierTo(size.width * 0.26f, size.height * 0.18f, midX, size.height * 0.52f)
            }
            val path2 = Path().apply {
                moveTo(midX, size.height * 0.52f)
                quadraticBezierTo(size.width * 0.74f, size.height * 0.85f, size.width - 4f, size.height * 0.3f)
            }
            drawPath(path1, strokeColor, style = Stroke(width = minW, cap = StrokeCap.Round))
            drawPath(path2, strokeColor, style = Stroke(width = maxW, cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = String.format(Locale.US, "%.1fx", preset.pressureMultiplier),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.52f),
            )
        }
    }
}

@Composable
private fun SheetSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    ink: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val thumbOffset by animateDpAsState(targetValue = if (checked) 16.dp else 0.dp, label = "SwitchThumb")
    val trackColor = if (checked) accent else ink.copy(alpha = 0.16f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 20.dp)
                .clip(CircleShape)
                .background(trackColor)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun SheetValueRow(
    icon: ImageVector,
    label: String,
    value: String,
    ink: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = 0.8f),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ink.copy(alpha = 0.10f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun SheetFooterRow(
    onPenTuning: () -> Unit,
    onSettings: () -> Unit,
) {
    val ink = rememberChromeInk()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FooterPillButton(
            icon = Icons.Outlined.Tune,
            label = "Pen Tuning",
            ink = ink,
            onClick = onPenTuning,
            modifier = Modifier.weight(1f),
        )
        FooterPillButton(
            icon = Icons.Outlined.Settings,
            label = "Settings",
            ink = ink,
            onClick = onSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FooterPillButton(
    icon: ImageVector,
    label: String,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(rememberSheetSurfaceColor())
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.72f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}
