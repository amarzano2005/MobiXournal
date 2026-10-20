package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.DocumentRed
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/**
 * One **glass pane** of the overflow menu — a miniature of the toolbars' capsule.
 *
 * The menu's actions used to be plain `OutlinedButton`s and `FilterChip`s, which on a frosted menu read
 * as flat Material controls lying on the glass rather than as panes cut from it. A pane borrows the
 * recipe the dock and the rail are built from: the dock's glass fill (`rememberGlassSurfaceColor`), a
 * small cast shadow (the bar itself carries 6 dp), a sheen along the lit top edge — strong enough to
 * lift the pane a shade **above** the menu's own glass, the way the dock sits above the desk, instead
 * of letting the equal-alpha fill read as a recess — and the family's hairline rim ([floatingRim]). So a
 * menu row looks like chrome the app already has, only smaller.
 *
 * [wash] is an optional tint laid over the glass (a selected preset), [padding] the inner breathing
 * room; everything else about the pane is fixed, so no row of the menu can invent its own glass.
 */
@Composable
private fun GlassPane(
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    shadowElevation: Dp = 3.dp,
    padding: PaddingValues = PaddingValues(vertical = 9.dp, horizontal = 6.dp),
    wash: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = chromeIsDark()
    val shape = RoundedCornerShape(corner)
    val sheenTop = Color.White.copy(alpha = if (dark) 0.17f else 0.62f)
    val sheenBottom = Color.White.copy(alpha = if (dark) 0.035f else 0.08f)
    Box(
        modifier = modifier
            .shadow(shadowElevation, shape)
            .clip(shape)
            .background(rememberGlassSurfaceColor())
            .background(Brush.verticalGradient(listOf(sheenTop, sheenBottom)))
            .then(if (wash != null) Modifier.background(wash) else Modifier)
            .floatingRim(corner)
            .padding(padding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A pane carrying a glyph and a label — the PDF and page-export actions.
 *
 * The tint is the caller's: [DocumentRed] for the two PDF actions, the primary accent for the page
 * exports. A disabled pane fades its ink instead of dropping the glass, so the row keeps its shape.
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
    val alpha = if (enabled) 1f else 0.38f
    GlassPane(modifier = modifier.clickable(enabled = enabled, onClick = onClick)) {
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
 * A **glass preset chip**: the pen presets wear the same pane as the PDF actions rather than a Material
 * [androidx.compose.material3.FilterChip], so the menu has one kind of button all the way down.
 *
 * The live preset is the one the accent writes and the one the accent washes, so "which pen am I
 * holding" is readable without hunting for a check mark.
 */
@Composable
private fun GlassPresetChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ink = if (selected) MaterialTheme.colorScheme.primary else rememberChromeInk()
    GlassPane(
        corner = 10.dp,
        shadowElevation = 2.dp,
        padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        wash = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * The **rule between two parts of the menu** — the file/PDF actions, the pen, the app.
 *
 * A Material [HorizontalDivider] was invisible here: its outline variant is darker than the glass it
 * would sit on, so the menu read as one undivided column. This is the chrome's own hairline instead,
 * feathered to nothing at both ends the way the panel's rim is, so it separates the parts without
 * turning into a box edge.
 */
@Composable
private fun MenuRule() {
    val ink = rememberChromeInk().copy(alpha = 0.22f)
    val clear = ink.copy(alpha = 0f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(clear, ink, clear))),
    )
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
                    tint = rememberChromeInk(),
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

            MenuRule()

            // Pen Subsection: the heading is the chrome's own ink, with the section's glyph beside it,
            // and the pen-parameters button on the far end in the same ink.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Draw,
                        contentDescription = null,
                        tint = rememberChromeInk(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Pen",
                        style = MaterialTheme.typography.labelLarge,
                        color = rememberChromeInk(),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { open = false; onOpenPenParameters() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = "Pen settings",
                            tint = rememberChromeInk(),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (settings != null && settings.penPresets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        settings.penPresets.forEach { preset ->
                            GlassPresetChip(
                                name = preset.name,
                                selected = preset.id == settings.selectedPenPresetId,
                                onClick = {
                                    open = false
                                    onSelectPenPreset(preset)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // No horizontal padding of its own: the row's glyph sits on the same left edge as the
                // **Pen** heading's glyph above it, so the section reads as one column. Its ink stays
                // the secondary grey it has always been — this row is a setting, not the heading.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            open = false
                            onTogglePenDiagnostics()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (penDiagnostics) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (penDiagnostics) "Hide pen diagnostics" else "Pen diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (penDiagnostics) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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

            MenuRule()

            // Settings
            DropdownMenuItem(
                text = { Text("Settings", color = rememberChromeInk()) },
                leadingIcon = {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = rememberChromeInk())
                },
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
