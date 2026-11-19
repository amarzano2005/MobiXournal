package com.mobixournal.desktop.io

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A failed open, shown **over the document you already have** instead of in place of it.
 *
 * The shell used to replace the whole canvas with the error, which is the same mistake as closing a
 * tab because a second file would not open: the notebook that was on screen was fine, and the user lost
 * it to a message about a different file. The card is a *transient surface* (`docs/ui-modernization.md`)
 * — it may sit over the page because it comes and goes with a gesture — so it floats at the top centre in
 * the popup material every other surface uses: the opaque `popupSurface`, the 8 dp `surfaceShape` corner,
 * and the title in the heading face. It goes away on a click or `Esc`.
 *
 * Its rim is the **refusal red at 40 %** rather than the usual hairline: this card says something went
 * wrong, and the one surface in the window that reports a failure should not look exactly like a menu that
 * offers a choice. The fill is opaque — unlike the menus' — because the card straddles the edge where the
 * desk meets the sheet, and a translucent panel read as two materials split down its middle.
 */
@Composable
internal fun OpenFailureCard(failure: OpenFailure, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(DesktopTheme.popupRadius))
            .background(DesktopTheme.popupSurface)
            .border(
                1.dp,
                DesktopTheme.danger.copy(alpha = FAILURE_RIM_ALPHA),
                RoundedCornerShape(DesktopTheme.popupRadius),
            )
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = failure.title,
                color = DesktopTheme.ink,
                style = DesktopTheme.heading,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.weight(1f))
            // One quiet hint, held off the title so it reads as the card's keyboard answer rather than as
            // the tail of the message.
            Text(
                "Esc",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = failure.detail,
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
    }
}

/** The failure card's rim: the refusal red, taken down to a hairline's weight. */
private const val FAILURE_RIM_ALPHA = 0.40f
