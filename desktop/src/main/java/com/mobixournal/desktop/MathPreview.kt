package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.MathFlavor
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.MathBlockInk

/**
 * The **live typeset preview** of the formula a placement is asking for.
 *
 * The tablet's LaTeX tool shows the formula as it is written, and a window that only revealed it after
 * **Place** would be asking the user to type blind. What is drawn here is the block's own ink —
 * `LivingPlacement.math` composed into a box, then compiled by `MathBlockInk.texImage`, the very bridge the
 * page, the `.xopp` export and the PDF run — so the preview cannot promise a formula the sheet will not
 * draw.
 *
 * It reads the pending source and flavour as snapshot state, so it follows every keystroke; the paper is
 * the same white the graph preview uses, because formula ink on the lane's dark glass would be invisible.
 */
@Composable
internal fun MathQuestionPreview(pending: PendingPlacement) {
    MathPreview(source = pending.mathSource, flavour = pending.mathFlavor)
}

/** The same preview for a block that already exists — the lane's editor draws what it is editing. */
@Composable
internal fun MathPreview(source: String, flavour: MathFlavor) {
    val measurer = rememberTextMeasurer()
    Box(
        Modifier
            .fillMaxWidth()
            .height(MATH_PREVIEW_HEIGHT)
            .background(DesktopTheme.previewPaper, DesktopTheme.railSlotShape)
            .padding(6.dp),
    ) {
        if (source.isBlank()) {
            Text(
                "Type a source to typeset it here",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val block = LivingPlacement.math(
                    bounds = Bounds(0.0, 0.0, size.width.toDouble(), size.height.toDouble()),
                    source = source,
                    flavour = flavour,
                    colour = PREVIEW_MATH_INK,
                )
                drawTexImage(MathBlockInk.texImage(block), scale = 1f, measurer = measurer)
            }
        }
    }
}

/** The preview's height in dp: a formula card, not a thumbnail. */
private val MATH_PREVIEW_HEIGHT = 96.dp

/** The formula's ink in the preview: near-black on paper, as the tablet's dialog draws it. */
private const val PREVIEW_MATH_INK = 0xFF2A2A2A.toInt()
