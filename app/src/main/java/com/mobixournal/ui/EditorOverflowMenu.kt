package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/**
 * One **glass tile** of the overflow menu's action rows.
 *
 * The file and PDF actions used to be plain `OutlinedButton`s, which on a frosted menu read as flat
 * Material controls lying on the glass rather than as panes cut from it. A tile is the menu's own
 * material: the glass fill, a vertical sheen from the top edge (the light the bar and the menu are
 * lit by) and the family's hairline rim ([floatingRim]) — so the tile looks translucent *because* the
 * menu behind it is, which is what makes the row feel like glass rather than like buttons.
 *
 * The tint is the caller's: [DocumentRed] for the two PDF actions, the primary accent for the page
 * exports. Disabled tiles fade the tint instead of dropping the fill, so the row keeps its shape.
 */
@Composable
private fun GlassActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val dark = chromeIsDark()
    val shape = RoundedCornerShape(14.dp)
    val alpha = if (enabled) 1f else 0.38f
    // **No fill of its own.** The tile is a *pane*, not a chip: it lays only light over the menu's
    // glass — a sheen that is brightest along the top edge and fades to nothing by the bottom — so
    // what shows through the tile is the same frosted backdrop showing through the menu. A solid glass
    // fill (the first attempt) stacked a second 85% opaque layer on the menu and read as a grey button
    // pasted on glass, the opposite of the effect.
    val sheenTop = Color.White.copy(alpha = if (dark) 0.12f else 0.70f)
    val sheenMid = Color.White.copy(alpha = if (dark) 0.045f else 0.30f)
    val sheenBottom = Color.White.copy(alpha = if (dark) 0.012f else 0.10f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(sheenTop, sheenMid, sheenBottom)))
            // The family's hairline, bright at the top and faint at the bottom: the specular edge that
            // tells the eye this is a pane of glass rather than a lighter rectangle.
            .floatingRim(14.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint.copy(alpha = alpha),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = tint.copy(alpha = alpha),
            )
        }
    }
}

/**
 * The top-bar overflow ("hamburger") menu: the glass button that opens it and the panel itself —
 * compact quick file actions, the PDF row, the active page's own export row, the pen subsection, and
 * Settings.
 *
 * It is a region of its own, which is why it lives here rather than in `EditorRegions`: everything it
 * shows arrives as an argument or a callback, and nothing in the editor reaches into it.
 */
@Composable
internal fun OverflowMenu(
    isReadOnly: Boolean = false,
    settings: AppSettings?,
    onSelectPenPreset: (PenPreset) -> Unit,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSettings: () -> Unit,
    penDiagnostics: Boolean,
    onTogglePenDiagnostics: () -> Unit,
    onOpenPenParameters: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val isDark = chromeIsDark()
    val glassColor = rememberGlassSurfaceColor()
    val hairlineColor = ModernTheme.hairlineBorder(isDark)
    Box {
        Surface(
            shape = CircleShape,
            color = glassColor,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(horizontal = 2.dp).floatingRim(18.dp),
        ) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Menu,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        ToolbarMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 320.dp),
        ) {
            // Quick File Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MenuQuickAction(
                    icon = Icons.AutoMirrored.Outlined.NoteAdd,
                    label = "New",
                    onClick = { open = false; onNewTab() },
                    modifier = Modifier.weight(1f),
                )
                MenuQuickAction(
                    icon = Icons.Outlined.FileOpen,
                    label = "Open",
                    onClick = { open = false; onOpen() },
                    modifier = Modifier.weight(1f),
                )
                MenuQuickAction(
                    icon = Icons.Outlined.Save,
                    label = "Save",
                    enabled = !isReadOnly,
                    onClick = { open = false; onSave() },
                    modifier = Modifier.weight(1f),
                )
                MenuQuickAction(
                    icon = Icons.Outlined.SaveAs,
                    label = "Save As",
                    enabled = !isReadOnly,
                    onClick = { open = false; onSaveAs() },
                    modifier = Modifier.weight(1f),
                )
            }

            // PDF Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassActionButton(
                    icon = Icons.Outlined.PictureAsPdf,
                    label = "Import PDF",
                    tint = DocumentRed,
                    enabled = !isReadOnly,
                    onClick = { open = false; onImportPdf() },
                    modifier = Modifier.weight(1f),
                )
                GlassActionButton(
                    icon = Icons.Outlined.PictureAsPdf,
                    label = "Export PDF",
                    tint = DocumentRed,
                    enabled = !isReadOnly,
                    onClick = { open = false; onExportPdf() },
                    modifier = Modifier.weight(1f),
                )
            }

            // Export the active page alone, as a shareable raster image or a scalable vector file.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassActionButton(
                    icon = Icons.Outlined.Image,
                    label = "Page as PNG",
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = !isReadOnly,
                    onClick = { open = false; onExportPagePng() },
                    modifier = Modifier.weight(1f),
                )
                GlassActionButton(
                    icon = Icons.Outlined.Image,
                    label = "Page as SVG",
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = !isReadOnly,
                    onClick = { open = false; onExportPageSvg() },
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Pen Subsection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Pen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = { open = false; onOpenPenParameters() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = "Pen parameters",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (settings != null && settings.penPresets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        settings.penPresets.forEach { preset ->
                            val isSelected = preset.id == settings.selectedPenPresetId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    open = false
                                    onSelectPenPreset(preset)
                                },
                                label = { Text(preset.name, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            open = false
                            onTogglePenDiagnostics()
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (penDiagnostics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (penDiagnostics) "Hide pen diagnostics" else "Pen diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (penDiagnostics) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Settings
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = { open = false; onSettings() },
            )
        }
    }
}

@Composable
private fun MenuQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}
