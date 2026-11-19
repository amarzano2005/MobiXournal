package com.mobixournal.desktop.tools

import com.mobixournal.design.ImportCardLayout
import com.mobixournal.desktop.settings.DesktopTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The three widths a question may ask for: one sentence, a choice, or a short table. */
internal enum class DialogWidth(val dp: Dp) {
    /** One decision and two or three answers (`Save your changes?`, `Rename layer`). */
    Compact(360.dp),

    /** The ordinary card: a title, a body, and the answers under it. */
    Standard(420.dp),

    /** A question that carries a grid or a preview beside its words. */
    Wide(520.dp),
}

/**
 * The window's **one modal**: a question asked over the page it is about.
 *
 * The desktop used to answer that question eleven ways — two scrims (one of them `Color.Black` at 45 %),
 * three widths, two corner shapes, footers with and without a commit, and a body that could be opaque or
 * translucent — so the same window asked *Save your changes?* and *Export PDF* in two materials. This
 * frame is that grammar once; a dialog is now its own words, its own body and its own answers
 * (`docs/interface-contract.md` §2, §7).
 *
 * What it owns, and why each part belongs here rather than in a dialog:
 *  - the **scrim** ([DesktopTheme.scrim]) over the whole window, which is also the blocker: a question
 *    takes no stroke on the page behind it, and a tap outside the card closes it only when
 *    [dismissOnOutside] says the question is one a reader may walk away from;
 *  - the **card**, on the same opaque `popupSurface` as a menu, its 8 dp `surfaceShape` and its 1 dp
 *    hairline — one material for every surface that is not the page;
 *  - its **arrival** on the shared spring ([glassSpring]), which is the movement a menu makes, so a
 *    dialog is not a second kind of thing in the same window;
 *  - its **width** ([DialogWidth]), narrowed to the room the window leaves: a window can be dragged
 *    narrower than a card, and being narrow is better than being clipped;
 *  - the **title** in the title face with an optional subtitle under it;
 *  - the **footer**, always `Arrangement.End` with 8 dp between the answers: the ordinary answer on the
 *    left, the commit on the right, and the answer that takes something away in the refusal red.
 *
 * The card also takes the keyboard when it opens, so Tab starts inside the question instead of walking
 * the bands behind it — for a modal that is the only order that makes sense.
 */
@Composable
internal fun DesktopDialogFrame(
    title: String,
    width: DialogWidth = DialogWidth.Standard,
    subtitle: String? = null,
    dismissOnOutside: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(DesktopTheme.scrim)
            // The scrim is the blocker as well as the dimming: a press anywhere outside the card is the
            // answer the reader gave by walking away, and one that lands on the page behind must not.
            .pointerInput(dismissOnOutside) {
                detectTapGestures { if (dismissOnOutside) onDismiss() }
            },
        contentAlignment = Alignment.Center,
    ) {
        val room = ImportCardLayout.cardWidth(maxWidth.value).dp
        val card = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { card.requestFocus() } }
        Column(
            modifier
                .width(minOf(width.dp, room))
                .heightIn(max = (maxHeight - ImportCardLayout.EDGE_DP.dp * 2).coerceAtLeast(0.dp))
                .focusRequester(card)
                .focusable()
                .glassSpring()
                .glassSurface()
                // The card swallows its own taps: a press on the body must not reach the scrim and count
                // as a click outside the question.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            Text(title, color = DesktopTheme.ink, style = DesktopTheme.title)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
            }
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}
