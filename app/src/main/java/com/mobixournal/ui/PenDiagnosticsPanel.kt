package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The on-canvas **pen diagnostics** panel: the raw stylus stream as the app receives it — input
 * devices, pointer actions, tool types, button bits and key codes — over the page it is reporting on.
 *
 * It sits on the canvas rather than in a settings page on purpose. Only the canvas sees the pen: a
 * settings screen layered over it would swallow the very events being diagnosed, and a panel that
 * could not see a button press could not answer the only question it exists to answer — does this
 * pen's button reach the app at all, and if so as what?
 *
 * The panel takes no pointer input of its own except its two buttons (a plain `Text` never consumes a
 * touch), so the pen keeps drawing and hovering underneath it. Its colours are fixed rather than
 * themed: it is a debug overlay on top of an arbitrary page, so it has to read over white paper,
 * black ink and a photo alike.
 *
 * @param lines the log, oldest first; only the newest few are shown — they are the ones being asked
 *   about, and the whole buffer would cover the page.
 * @param onClear empties the log without closing the panel.
 */
@Composable
fun PenDiagnosticsPanel(
    lines: List<String>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier
            .widthIn(max = 380.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE6000000))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pen diagnostics",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) }) {
                Text("Copy", fontSize = 12.sp)
            }
            TextButton(onClick = onClear) { Text("Clear", fontSize = 12.sp) }
        }
        Text(
            text = if (lines.isEmpty()) {
                "No pen events yet — hover and draw to see what the tablet delivers."
            } else {
                // The tail, not the head: the interesting line is always one of the last few.
                lines.takeLast(VISIBLE_LINES).joinToString("\n")
            },
            color = Color(0xFF9BE79B),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            modifier = Modifier.heightIn(max = 260.dp),
        )
    }
}

/** How many log lines the panel shows at once, before the oldest scroll off the top. */
private const val VISIBLE_LINES = 22
