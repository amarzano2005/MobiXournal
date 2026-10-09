package com.mobixournal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The building blocks every settings page is laid out with, so the pages share one look:
 *
 * - a page is a stack of [SettingsGroup]s — a small accent-coloured **header** over a rounded,
 *   tonal **card** holding that group's controls (the shape Android's own settings use);
 * - inside a card, controls are separated by a [SettingsDivider] hairline, never a full-bleed rule;
 * - explanatory prose that belongs to the whole group goes in a [SettingsNote].
 *
 * Grouping is the page's *order*: related controls sit in one card, and a reader can scan the
 * headers to find the one they want instead of reading every subtitle top to bottom.
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Surface(
            shape = SettingsCardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
        }
    }
}

/** The hairline between two controls inside one [SettingsGroup] card. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** A muted paragraph explaining a group or a control — secondary to the controls around it. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * A control's own heading: its name and, under it, what it does. Shared by the switches, radio
 * groups and sliders so every control in every card reads with the same hierarchy.
 */
@Composable
fun ControlHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The corner radius of every settings card (groups and the index lists). */
internal val SettingsCardShape = RoundedCornerShape(20.dp)
