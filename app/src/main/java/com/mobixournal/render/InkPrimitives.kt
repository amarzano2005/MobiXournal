package com.mobixournal.render

import com.mobixournal.format.FontDescription
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool

/**
 * The ink primitives the four families' compilers build a figure out of — one place where a synthesized
 * stroke and a synthesized label get their shared shape, so four compilers cannot each decide for
 * themselves what a pen stroke looks like.
 *
 * The choices here are **the ones the drawing surfaces make**, and that is the point: a figure
 * compiled on export must look like the figure that was inserted on screen. Shapes and plots are
 * emitted by the pen at a constant width with round caps ([com.mobixournal.render.ShapeBuilder]'s own
 * convention, and `DrawingSurfaceStrokes`' when it commits a shape), and a label is a text box in
 * [FIGURE_LABEL_FONT] — the same font and size the app draws a plot's ticks in.
 */
internal object InkPrimitives {

    /** The cap style every synthesized figure uses: what the shape tools draw with. */
    const val CAP: String = "round"

    /** A point of a synthesized stroke: constant width, since a figure has no pressure stream. */
    fun pt(x: Double, y: Double, widthPt: Double): StrokePoint = StrokePoint(x, y, widthPt)

    /**
     * A synthesized stroke: the pen tool, a constant width, and [fill] (a 0..255 alpha painted inside
     * a closed figure) when the figure is filled.
     */
    fun stroke(
        points: List<StrokePoint>,
        color: Int,
        widthPt: Double,
        lineStyle: LineStyle = LineStyle.PLAIN,
        fill: Int? = null,
    ): Stroke = Stroke(
        tool = Tool.PEN,
        color = color,
        capStyle = CAP,
        points = points,
        uniformWidth = true,
        lineStyle = lineStyle,
        fill = fill,
    )

    /**
     * A synthesized label. [bold] composes the Pango-style description the `.xopp` stores
     * ([FontDescription]) rather than a second spelling of "bold" this format would have to keep in
     * sync with the desktop's.
     */
    fun text(content: String, x: Double, y: Double, color: Int, bold: Boolean = false): TextElement =
        TextElement(
            font = if (bold) FontDescription(FIGURE_LABEL_FONT, bold = true, italic = false).compose() else FIGURE_LABEL_FONT,
            size = FIGURE_LABEL_SIZE_PT,
            x = x,
            y = y,
            color = color,
            content = content,
        )
}
