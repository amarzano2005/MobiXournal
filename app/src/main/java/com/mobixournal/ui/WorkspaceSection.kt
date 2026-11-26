package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobixournal.MobiXournalApp
import com.mobixournal.ui.theme.DeskDark
import com.mobixournal.ui.theme.DeskLight
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.liquidGlassContentSize

/**
 * Unified **Workspace** section (`Appearance` + `Toolbar`) with visual 3-card theme picker,
 * segmented tool-rail side selector, and collapsible toolbar button customization.
 */
@Composable
fun WorkspaceSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    var buttonsExpanded by remember { mutableStateOf(false) }

    SettingsGroup("Theme") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemePreviewCard(
                    mode = mode,
                    selected = settings.themeMode == mode,
                    onClick = {
                        if (mode != settings.themeMode) {
                            onChange(settings.copy(themeMode = mode))
                            MobiXournalApp.applyNightMode(context, mode)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val selectedSide = if (settings.toolbarPosition == ToolbarPosition.RIGHT) {
        ToolbarPosition.RIGHT
    } else {
        ToolbarPosition.LEFT
    }
    val visibleRailCount = orderedRailItems(settings.railOrder)
        .filterNot { it.id in PINNED_RAIL_IDS || isDividerId(it.id) }
        .count { it.id !in settings.railHidden }
    val visibleTopCount = orderedTopBarItems(settings.topBarOrder)
        .filterNot { isDividerId(it.id) }
        .count { it.id !in settings.topBarHidden }
    val reach = LocalSettingsCardInset.current

    SettingsGroup("Toolbars") {
        Column(Modifier.liquidGlassContentSize()) {
            ControlHeader(
                title = "Main rail side",
                subtitle = "Keep opposite your writing hand (Left for right-handed, Right for left-handed).",
                modifier = Modifier.settingsBlockInset(),
            )
            SettingsSegmentedControl(
                options = listOf(ToolbarPosition.LEFT, ToolbarPosition.RIGHT),
                selected = selectedSide,
                label = { it.label },
                onSelect = { onChange(settings.copy(toolbarPosition = it)) },
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
            )
            RailSideDiagram(
                railOnRight = selectedSide == ToolbarPosition.RIGHT,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fullBleedRow(reach)
                    .fillMaxWidth()
                    .clickable { buttonsExpanded = !buttonsExpanded }
                    .padding(horizontal = reach, vertical = settingsCellInset(reach)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlHeader(
                    title = "Customize rail & figure buttons",
                    subtitle = "${visibleRailCount + visibleTopCount} buttons visible — tap to reorder or hide",
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (buttonsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (buttonsExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = buttonsExpanded) {
                Column {
                    SettingsDivider()
                    MainToolbarButtons(settings, onChange)
                    SettingsDivider()
                    SecondaryToolbarButtons(settings, onChange)
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = chromeIsDark()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else ModernTheme.hairlineBorder(dark)
    val fill = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        ThemeSwatchPreview(mode, dark)
        Spacer(Modifier.height(8.dp))
        Text(
            text = mode.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ThemeSwatchPreview(mode: ThemeMode, dark: Boolean) {
    val swatchShape = RoundedCornerShape(7.dp)
    val border = ModernTheme.hairlineBorder(dark)
    when (mode) {
        ThemeMode.LIGHT -> Box(
            Modifier.fillMaxWidth().height(26.dp).clip(swatchShape).background(DeskLight).border(1.dp, border, swatchShape),
        )
        ThemeMode.DARK -> Box(
            Modifier.fillMaxWidth().height(26.dp).clip(swatchShape).background(DeskDark).border(1.dp, border, swatchShape),
        )
        ThemeMode.SYSTEM -> Row(
            Modifier.fillMaxWidth().height(26.dp).clip(swatchShape).border(1.dp, border, swatchShape),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(DeskLight))
            Box(Modifier.weight(1f).fillMaxHeight().background(DeskDark))
        }
    }
}

/** Miniature editor layout preview showing the tool rail docked on the chosen side. */
@Composable
private fun RailSideDiagram(railOnRight: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val border = ModernTheme.hairlineBorder(chromeIsDark())
    val railStrip: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, border, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!railOnRight) railStrip()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Canvas area",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (railOnRight) railStrip()
    }
}
