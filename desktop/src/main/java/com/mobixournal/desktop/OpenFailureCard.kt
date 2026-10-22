package com.mobixournal.desktop

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A failed open, shown **over the document you already have** instead of in place of it.
 *
 * The shell used to replace the whole canvas with the error, which is the same mistake as closing a
 * tab because a second file would not open: the notebook that was on screen was fine, and the user lost
 * it to a message about a different file. The card is a *transient surface* (`docs/ui-modernization.md`)
 * — it may sit over the page because it comes and goes with a gesture — so it floats at the top centre
 * with the family's hairline rim and 20 dp corner, and goes away on a click or `Esc`.
 *
 * Its fill is **opaque**, unlike the menus': this card sits at the top of the canvas, so it straddles the
 * edge where the desk meets the sheet. At the family's 0.88 the part over the white page came out a tone
 * lighter than the part over the black desk, and the panel read as two materials split down its middle —
 * a rendering fault, to the eye. Frost belongs to a menu that hangs from a bar it matches; a message to
 * be read once and dismissed is better solid.
 */
@Composable
internal fun OpenFailureCard(failure: OpenFailure, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 520.dp)
            // The opaque fill stays (see the note above) and is passed explicitly: every other floating
            // surface is translucent, and this is the one that has a written reason not to be.
            .glassSurface(DesktopTheme.panel)
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = failure.title,
                color = DesktopTheme.ink,
                fontFamily = DesktopTheme.inter,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("Esc", color = DesktopTheme.inkMuted, fontFamily = DesktopTheme.inter, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = failure.detail,
            color = DesktopTheme.inkSecondary,
            fontFamily = DesktopTheme.inter,
            fontSize = 12.sp,
        )
    }
}

