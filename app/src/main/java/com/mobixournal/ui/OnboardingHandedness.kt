package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.rememberChromeColor

/**
 * Onboarding's fourth step: **which hand writes**, which is the only thing that decides where the rail
 * goes.
 *
 * The choice is [AppSettings.toolbarPosition], and it is a handedness answer rather than a placement
 * menu: a right hand writes from the left of the sheet, so the tools belong on the **left** (the
 * default) where the free hand already waits; a left hand is the mirror. The step says that in words and
 * shows it — each card carries a miniature of the editor with the rail drawn on the edge it would take —
 * because the same question asked as "Left or Right" is a coin flip nobody can answer for the user.
 *
 * The editor is composed behind this dialog, so a tap moves the rail live: the choice can be answered by
 * looking at it rather than by reading about it. It is asked exactly once (see [OnboardingDialog]) and is
 * changeable later under **Settings -> Interface -> Toolbar**.
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
                    imageVector = Icons.Default.PanTool,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Which hand do you write with?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "The Main Toolbar docks to the edge opposite your writing hand: that keeps the tools " +
                "under your free hand and out from under your palm. Pick your hand and watch the rail move " +
                "behind this dialog.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // One group, one answer: the cards are a radio set, so the choice is announced as such.
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HandednessCard(
                title = "Right-handed",
                description = "You write with the right hand: the toolbar docks on the left edge, under " +
                    "the free left hand.",
                side = ToolbarPosition.LEFT,
                selected = selected == ToolbarPosition.LEFT,
                onClick = { onSelect(ToolbarPosition.LEFT) },
            )

            HandednessCard(
                title = "Left-handed",
                description = "You write with the left hand: the toolbar docks on the right edge, under " +
                    "the free right hand.",
                side = ToolbarPosition.RIGHT,
                selected = selected == ToolbarPosition.RIGHT,
                onClick = { onSelect(ToolbarPosition.RIGHT) },
            )
        }

        Text(
            text = "Changeable any time in Settings → Interface → Toolbar.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One hand's card: a miniature of the editor with the rail on the edge this hand implies, the hand
 * itself in words, and a check on the live choice. The whole card is the option, so a tap anywhere on it
 * picks the hand.
 */
@Composable
private fun HandednessCard(
    title: String,
    description: String,
    side: ToolbarPosition,
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
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarSidePreview(side = side, selected = selected)
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

/**
 * The miniature of the editor this step decides with: the desk the chrome paints, the rail's lane on the
 * edge [side] names, and the sheet in what is left — the same picture the layout itself draws, small
 * enough to compare two of them at a glance.
 *
 * Desk and rail are the real chrome tokens ([rememberChromeColor], and the primary colour of the live
 * choice); the sheet is **paper white** rather than `surface`, because the page belongs to the document
 * and not to the theme. Drawn with `surface`, a sheet would be #121212 on the dark theme's own #181818
 * card — the comparison would vanish in exactly the case this step exists for. In the light theme, where
 * the desk is near-white too, the page keeps the hairline edge it has on the canvas.
 */
@Composable
private fun ToolbarSidePreview(side: ToolbarPosition, selected: Boolean) {
    val railColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val outline =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .width(76.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(rememberChromeColor())
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (side == ToolbarPosition.RIGHT) {
            MiniPage(Modifier.weight(1f))
            Spacer(Modifier.width(3.dp))
        }
        MiniRail(tint = railColor)
        if (side == ToolbarPosition.LEFT) {
            Spacer(Modifier.width(3.dp))
            MiniPage(Modifier.weight(1f))
        }
    }
}

/** The rail's lane: a capsule the width of a lane, with three slots down it. */
@Composable
private fun MiniRail(tint: Color) {
    Column(
        modifier = Modifier
            .width(14.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 0.9f))
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}

/** The sheet: white paper with a few ruling lines, so it reads as paper rather than as empty space. */
@Composable
private fun MiniPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .padding(horizontal = 5.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
