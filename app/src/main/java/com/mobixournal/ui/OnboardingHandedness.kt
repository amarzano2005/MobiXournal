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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

/**
 * Onboarding's fourth step: right-handed or left-handed.
 *
 * The choice is [AppSettings.toolbarPosition] — the rail down the **left** edge with the buttons
 * under a right hand (the default), or the **right** edge for a left hand. It is asked here, at first
 * launch, because it is the one layout decision that is obvious to the user and invisible to the app,
 * and because the editor is composed behind this dialog: tapping a card moves the rail live, so the
 * choice can be made by looking at it rather than by reading about it.
 *
 * An explicit choice, not a toggle: both cards read as options, with the current one marked.
 */
@Composable
internal fun OnboardingStep4Handedness(
    selected: ToolbarPosition,
    onSelect: (ToolbarPosition) -> Unit,
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Right-handed or left-handed?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "The Main Toolbar docks to one vertical edge of the canvas. Put it on the side of " +
                "your writing hand so the buttons stay under it and off the page — pick either now, " +
                "and change it any time in Settings → Interface → Toolbar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HandednessCard(
            title = "Right-handed",
            description = "Main Toolbar on the left edge. The default.",
            arrowRight = false,
            selected = selected == ToolbarPosition.LEFT,
            onClick = { onSelect(ToolbarPosition.LEFT) },
        )

        HandednessCard(
            title = "Left-handed",
            description = "Main Toolbar on the right edge.",
            arrowRight = true,
            selected = selected == ToolbarPosition.RIGHT,
            onClick = { onSelect(ToolbarPosition.RIGHT) },
        )
    }
}

/** One hand-choice card: an arrow pointing at the edge the rail moves to, a title, and a check. */
@Composable
private fun HandednessCard(
    title: String,
    description: String,
    arrowRight: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(14.dp))
            // The whole card is the option, so a tap anywhere on it — text included — picks the hand.
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (arrowRight) Icons.AutoMirrored.Filled.ArrowForward
            else Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
