package com.mobixournal.desktop.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.mobixournal.design.SavingSpin

/**
 * The window's half of the **saving mark** ([SavingSpin]) — the quarter arc the header's Save glyph becomes
 * while the bytes go out.
 *
 * It is drawn on the same box the glyph catalogue uses, so the control does not change size when the mark
 * changes: a Save that grew a pixel while writing would shift the two commands beside it, which is the one
 * thing a band's right-hand group must not do.
 *
 * [turn] is passed in rather than animated here so the caller can keep it in a `State` and read it inside
 * the draw lambda: a spinner must repaint, not recompose the band sixty times a second.
 */
internal fun DrawScope.drawSavingArc(color: Color, turn: Float) {
    val side = minOf(size.width, size.height)
    val stroke = side * (SavingSpin.STROKE_UNITS / SavingSpin.GLYPH_BOX)
    val inset = stroke / 2f
    rotate(degrees = turn * 360f) {
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = SavingSpin.SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(side - stroke, side - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * The turn a saving mark animates on: `0..1` of one revolution per [SavingSpin.TURN_MS], linear because an
 * eased turn reads as a pulse rather than as progress.
 *
 * A `State` rather than a value, so a `Canvas` invalidates its own drawing each frame instead of the
 * composition that asked for it.
 */
@Composable
internal fun rememberSavingTurn(saving: Boolean): State<Float> {
    // The transition is only started while something is writing: an idle window does not schedule frames for
    // an arc nobody can see.
    if (!saving) return androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "saving")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SavingSpin.TURN_MS, easing = LinearEasing)),
        label = "turn",
    )
}
