package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Onboarding's last step: which format a **new** document is written in.
 *
 * The one question the format policy deliberately asks the user rather than answering for them
 * (`docs/mobix-format.md`, *Which format a document is written in*): the format is the boundary of
 * what the app can do, so the trade-off is shown here, once, instead of being discovered when a file
 * reaches a laptop. The choice is [AppSettings.newDocumentFormat], written straight through like the
 * handedness step before it, so it is answered as much by reading as by tapping — and it is asked
 * exactly once, because [EditorScreen] only shows the tour until `hasSeenOnboarding` is set.
 *
 * The recommended card is marked, not pre-chosen silently: the default in the settings is `.mobix`,
 * and the badge is what says so out loud.
 */
@Composable
internal fun OnboardingStep5DocumentFormat(
    selected: NewDocumentFormat,
    onSelect: (NewDocumentFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "What should a new document be?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "MobiXournal writes two kinds of file. Both draw and save the same pages — they " +
                "differ in what the file keeps, which is what decides whether desktop Xournal++ opens " +
                "it directly. Documents you open keep their own format; this is only what new ones are.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DocumentFormatCard(
            title = "MobiXournal document",
            extension = ".mobix",
            description = "Keeps graphs, tables, shapes and circuits as the things you authored, plus " +
                "pen telemetry — so they reopen as editable objects. Desktop Xournal++ needs the " +
                "companion plugin, or one Save As to interchange it.",
            recommended = true,
            selected = selected == NewDocumentFormat.MOBIX,
            onClick = { onSelect(NewDocumentFormat.MOBIX) },
        )

        DocumentFormatCard(
            title = "Xournal++ document",
            extension = ".xopp",
            description = "The file desktop Xournal++ reads and writes itself, so a copy opens on a " +
                "laptop with nothing installed. Graphs and tables are written out as ink: exactly as " +
                "they look, but no longer editable as a formula.",
            recommended = false,
            selected = selected == NewDocumentFormat.XOPP,
            onClick = { onSelect(NewDocumentFormat.XOPP) },
        )

        Text(
            text = "Changeable any time in Settings → App & data → Documents.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One format card: the name, the extension that lands on disk, what the file keeps, and a check when
 * it is the choice. The whole card is the option, so a tap anywhere on it — description included —
 * picks the format.
 */
@Composable
private fun DocumentFormatCard(
    title: String,
    extension: String,
    description: String,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = extension,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (recommended) {
                Spacer(Modifier.width(8.dp))
                RecommendedBadge()
            }
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The word "Recommended" on the format this app is built around. */
@Composable
private fun RecommendedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "Recommended",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
