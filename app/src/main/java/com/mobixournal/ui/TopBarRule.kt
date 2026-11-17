package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark

/**
 * The hairline between the top bar's **tool preview** and the first figure of its dock — the secondary
 * toolbar's one division.
 *
 * The preview says what the pen *is* (the tool, its colour, its tip); the figures say what it can be
 * asked to draw. At the dock's plain slot gap the two read as one long run of buttons, and the preview —
 * the only surface there that is not a choice — stopped reading as a read-out.
 *
 * It is the rail's separator ([SideToolbar]'s rule above the pinned slots) turned upright: the family's
 * `hairlineBorder`, one dp, **half a slot** tall, with the dock's own 6 dp of air on each side. One
 * separator in the chrome, drawn either way round — the header's navigation capsule divides its two
 * halves (`NavigationCapsule`) with the same line, asking for no air of its own there because each half
 * already keeps its inset.
 *
 * @param air The gap the rule leaves on each side; the dock takes the default, a capsule whose halves
 * carry their own insets passes `0.dp`.
 */
@Composable
internal fun TopBarToolDivider(air: Dp = 6.dp) {
    Box(
        Modifier
            .padding(horizontal = air)
            .width(1.dp)
            .height(20.dp)
            .background(ModernTheme.hairlineBorder(chromeIsDark())),
    )
}
