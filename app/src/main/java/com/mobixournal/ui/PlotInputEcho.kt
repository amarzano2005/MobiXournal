package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingSurface

/**
 * Which plot-inspector text field the keyboard is editing, and what it currently holds.
 *
 * The anchored inspector can sit behind the IME — the app draws edge-to-edge, so `adjustResize`
 * gives the canvas nothing to clamp against. This state is written by the inspector's own fields
 * through [plotInputEcho] and read by [PlotInputEchoBar], so the user can always see the text they
 * are typing in one cell above the keyboard even when the field itself is covered.
 *
 * Only the *focused* field is echoed: [focus] records it on focus gain and drops it on focus loss,
 * while [update] follows its text as it changes. A field that leaves the composition releases the
 * echo ([release]), so switching mode tabs or dismissing the inspector never leaves a stale cell.
 */
internal class PlotInputEchoState {
    var label by mutableStateOf<String?>(null)
        private set
    var text by mutableStateOf("")
        private set

    // Which fields hold focus right now. A tap that moves focus from one field to the next delivers
    // the gain and the loss in an order Compose does not promise, so a loss only clears the echo
    // when no other field has already taken it over.
    private val focused = mutableSetOf<String>()

    fun focus(label: String, focused: Boolean, text: String) {
        if (focused) {
            this.focused += label
            this.label = label
            this.text = text
        } else {
            this.focused -= label
            if (this.label == label) this.label = this.focused.lastOrNull()
        }
    }

    fun update(label: String, text: String) {
        if (this.label == label) this.text = text
    }

    fun release(label: String) {
        focused -= label
        if (this.label == label) this.label = null
    }
}

/** The inspector's echo channel; null everywhere else, so no other text field grows a copy. */
internal val LocalPlotInputEcho = compositionLocalOf<PlotInputEchoState?> { null }

/**
 * Attach [label] and its current [value] to a plot-inspector text field.
 *
 * A no-op when no echo is provided, which is what keeps the full-screen graph dialog and every
 * other text field in the app untouched. The focus callback records the field; [SideEffect] keeps
 * the echoed text in step with every keystroke.
 */
@Composable
internal fun Modifier.plotInputEcho(label: String, value: String): Modifier {
    val echo = LocalPlotInputEcho.current ?: return this
    SideEffect { echo.update(label, value) }
    DisposableEffect(echo, label) {
        onDispose { echo.release(label) }
    }
    return onFocusChanged { echo.focus(label, it.isFocused, value) }
}

/**
 * One floating cell above the keyboard, showing the focused inspector field's label and text.
 *
 * It is pinned to the bottom of the canvas and lifted by [imePadding], so it lands exactly on the
 * keyboard's top edge instead of behind it. Hidden while the IME is closed: the real field is
 * visible then, and a second copy would only be in the way.
 */
@Composable
internal fun PlotInputEchoBar(
    state: PlotInputEchoState,
    modifier: Modifier = Modifier,
) {
    val label = state.label ?: return
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (!imeVisible) return

    // The IME padding belongs on the wrapper, not on the glass: `FloatingSurface` paints its
    // background over its own size, so padding inside it would stretch the glass down over the
    // keyboard. The wrapper keeps the visible cell the height of its text.
    Box(modifier = modifier.imePadding()) {
        FloatingSurface(
            modifier = Modifier.widthIn(min = 240.dp, max = 480.dp),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = state.text.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
