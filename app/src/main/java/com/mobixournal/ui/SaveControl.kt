package com.mobixournal.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.SavingSpin
import com.mobixournal.io.SaveActivity

/**
 * The tablet's **Save** command, in the top bar's floating dock: a floppy at rest, and a turning arc while a
 * write is in flight (`SavingSpin`, the same mark the window's header draws).
 *
 * It lives beside the dock rather than inside `EditorRegions.kt` because that file is at its recorded line
 * ceiling, and because the control has two states to keep in agreement with the window's: the muted 0.38
 * tone when there is nothing to write, and the arc when there is a write going out. `enabled` is the
 * document's answer (a read-only tab never offers a save, a clean one has nothing to write); the mark is the
 * save's own.
 *
 * A save the reader did **not** ask for — auto save — turns the same arc. A save says nothing else at all: no
 * line is laid over the canvas for it, asked for or not, because the control the person keeps looking at is
 * exactly where "is it saved?" is asked (`docs/data-path.md`).
 */
@Composable
internal fun SaveControl(enabled: Boolean, onSave: () -> Unit) {
    val saving = SaveActivity.saving
    val writable = enabled && !saving
    IconButton(onClick = onSave, enabled = writable, modifier = Modifier.size(36.dp)) {
        if (saving) {
            SavingSpinMark(colour = MaterialTheme.colorScheme.secondary)
        } else {
            Icon(
                ChromeGlyph.SAVE.imageVector(),
                contentDescription = "Save",
                // The glyph wears the state, not only the click: a Save with nothing to write dims to the
                // same 0.38 the history pair beside it uses, or a blue icon would go on offering a write.
                tint = MaterialTheme.colorScheme.secondary.let {
                    if (enabled) it else it.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The tablet's drawing of the shared saving mark: a quarter arc going round once per
 * [SavingSpin.TURN_MS], closed with round caps, in the colour the Save glyph wears.
 *
 * The turn lives in a `State` read **inside** the draw scope, so the arc repaints on every frame without
 * recomposing the bar around it — the same split the window makes (`SavingArc.kt`).
 */
@Composable
internal fun SavingSpinMark(colour: Color, size: Dp = 20.dp) {
    val turn = rememberSpinTurn()
    Canvas(
        Modifier
            .size(size)
            .semantics { contentDescription = "Saving" },
    ) {
        val side = minOf(this.size.width, this.size.height)
        val stroke = side * (SavingSpin.STROKE_UNITS / SavingSpin.GLYPH_BOX)
        val inset = stroke / 2f
        rotate(turn.value * 360f) {
            drawArc(
                color = colour,
                startAngle = 0f,
                sweepAngle = SavingSpin.SWEEP_DEGREES,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(side - stroke, side - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** The turn the mark animates on: `0..1` of one revolution per [SavingSpin.TURN_MS], linear by design. */
@Composable
private fun rememberSpinTurn(): State<Float> {
    val transition = rememberInfiniteTransition(label = "saving")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SavingSpin.TURN_MS, easing = LinearEasing)),
        label = "turn",
    )
}
