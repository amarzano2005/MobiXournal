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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.ModernTheme

/**
 * Onboarding's second step: which format a **new** document is written in.
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
internal fun OnboardingStepDocumentFormat(
    selected: NewDocumentFormat,
    onSelect: (NewDocumentFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Default document format",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Choose the format for new documents. Opened notebooks always keep their own format, and you can switch anytime via Save As.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DocumentFormatCard(
            title = "MobiXournal document",
            extension = ".mobix",
            features = listOf(
                "Embedded compressed audio synced with ink replay (no missing sidecars)",
                "Living, re-editable STEM elements (plots, formulas, tables, circuits)",
                "Full stylus telemetry (pressure, tilt, azimuth, temporal dynamics)",
                "Full interoperability with the MobiXournal Desktop client (or Save As to .xopp)",
            ),
            recommended = true,
            selected = selected == NewDocumentFormat.MOBIX,
            onClick = { onSelect(NewDocumentFormat.MOBIX) },
        )

        DocumentFormatCard(
            title = "Xournal++ document",
            extension = ".xopp",
            features = listOf(
                "Direct compatibility with desktop Xournal++ without conversion",
                "Standard vector ink (elements flattened to ink; external WAV audio sidecars)",
            ),
            recommended = false,
            selected = selected == NewDocumentFormat.XOPP,
            onClick = { onSelect(NewDocumentFormat.XOPP) },
        )

        Text(
            text = "Changeable anytime in Settings → App & data → Documents.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One format card: the name, the extension that lands on disk, structured bullet highlights,
 * and a check when it is the choice.
 */
@Composable
private fun DocumentFormatCard(
    title: String,
    extension: String,
    features: List<String>,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(ModernTheme.capsuleCornerRadiusDp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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
        for (feature in features) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
