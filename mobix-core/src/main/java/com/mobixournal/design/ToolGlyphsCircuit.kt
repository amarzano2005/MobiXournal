package com.mobixournal.design

/**
 * The **circuit and logic-gate** half of the tool-glyph catalogue: the members a STEM picker offers
 * beyond the resistor, the diode and the AND gate that were already here.
 *
 * These are transcriptions, not new art. Each figure was the tablet's own `ImageVector` drawn on this
 * same 24-unit grid with a 1.5-unit round stroke, so moving it here changed no coordinate — what
 * changed is **who owns it**: the geometry now lives in `mobix-core`, where the window's painter and
 * the tablet's vector builder both read it, and that file has been deleted. A picker row and a rail
 * slot therefore show the same picture by construction.
 *
 * Two things were normalised on the way in, because the catalogue has one stroke and no fills:
 * the heavier strokes (the BJT base bar at 2.5, the LED's rays at 1.75, the transformer at 1.8) are now
 * the grid's own weight, and the filled marks (a diode's solid body, an emitter arrow, the junction
 * dot, a source's solid plate) are either a closed outline or — where a solid dot is the meaning —
 * a circle. That is the same trade the catalogue already made for `DIODE`, so a picker full of these
 * icons still reads as one hand rather than a mixture.
 */

/** A capacitor: two plates with a wire to each. */
internal fun capacitor(): List<GlyphPart> = listOf(
    seg(2f, 12f, 9.5f, 12f),
    seg(9.5f, 5f, 9.5f, 19f),
    seg(14.5f, 5f, 14.5f, 19f),
    seg(14.5f, 12f, 22f, 12f),
)

/** An inductor: three humps on a wire. */
internal fun inductor(): List<GlyphPart> = listOf(
    seg(2f, 14f, 5f, 14f),
    coil(5f, 14f, 9.5f, controlY = 8f),
    coil(9.5f, 14f, 14.5f, controlY = 8f),
    coil(14.5f, 14f, 19f, controlY = 8f),
    seg(19f, 14f, 22f, 14f),
)

/** Ground: a stem over three shortening bars. */
internal fun ground(): List<GlyphPart> = listOf(
    seg(12f, 3f, 12f, 11f),
    seg(4f, 11f, 20f, 11f),
    seg(7.5f, 15f, 16.5f, 15f),
    seg(10.5f, 19f, 13.5f, 19f),
)

/** An LED: the diode, with two rays and their barbs leaving the body. */
internal fun led(): List<GlyphPart> = listOf(
    seg(2f, 13f, 7.5f, 13f),
    loop(7.5f, 7.5f, 14.5f, 13f, 7.5f, 18.5f),
    seg(14.5f, 6.5f, 14.5f, 19.5f),
    seg(14.5f, 13f, 22f, 13f),
    // The rays are what say *emitting*; without them this is the plain diode above.
    seg(10.5f, 6f, 14.5f, 2f),
    run(12.2f, 2f, 14.5f, 2f, 14.5f, 4.3f),
    seg(15f, 7.5f, 19f, 3.5f),
    run(16.7f, 3.5f, 19f, 3.5f, 19f, 5.8f),
)

/** A zener diode: the diode with the cathode bar bent at both ends. */
internal fun zenerDiode(): List<GlyphPart> = listOf(
    seg(2f, 12f, 8f, 12f),
    loop(8f, 6.5f, 15.5f, 12f, 8f, 17.5f),
    // The two bends are the whole difference from `DIODE`: one towards the cathode, one away.
    run(18f, 6.5f, 15.5f, 6.5f, 15.5f, 17.5f, 13f, 17.5f),
    seg(15.5f, 12f, 22f, 12f),
)

/** An op-amp: a triangle pointing at its output, with `+` over `-` on its inputs. */
internal fun opAmp(): List<GlyphPart> = listOf(
    loop(6.5f, 4.5f, 18f, 12f, 6.5f, 19.5f),
    seg(2f, 8f, 6.5f, 8f),
    seg(2f, 16f, 6.5f, 16f),
    seg(18f, 12f, 22f, 12f),
    // The inverting input is the bare bar; the non-inverting one carries its cross.
    seg(8.5f, 8f, 11f, 8f),
    seg(8.5f, 16f, 11f, 16f),
    seg(9.75f, 14.75f, 9.75f, 17.25f),
)

/** An NPN transistor: the enclosed body, its three leads and an emitter arrow pointing out. */
internal fun bjtNpn(): List<GlyphPart> = bjtBody() + listOf(
    // The arrow leaves the base bar, so the device's direction is legible at 20 dp.
    loop(15.5f, 18f, 12.2f, 17.8f, 13.6f, 15f),
)

/** A PNP transistor: the same body, the same leads, the arrow turned back towards the base. */
internal fun bjtPnp(): List<GlyphPart> = bjtBody() + listOf(
    loop(10.2f, 14.4f, 13.6f, 14.2f, 12.2f, 17f),
)

/** A DC source: the cell's two plates, the long one carrying a `+` when read as its cross. */
internal fun dcSource(): List<GlyphPart> = listOf(
    seg(2f, 12f, 9f, 12f),
    seg(9f, 4f, 9f, 20f),
    seg(15f, 12f, 22f, 12f),
    // The battery's cross marks the polarity on the short side.
    seg(4.5f, 7f, 7.5f, 7f),
    seg(6f, 5.5f, 6f, 8.5f),
    loop(13.5f, 8f, 15.5f, 8f, 15.5f, 16f, 13.5f, 16f),
)

/** A current source: the enclosed body with the arrow it drives through it. */
internal fun currentSource(): List<GlyphPart> = listOf(
    seg(2f, 12f, 5.5f, 12f),
    seg(18.5f, 12f, 22f, 12f),
    GlyphPart.Circle(12f, 12f, 6.5f),
    seg(8.5f, 12f, 15.5f, 12f),
    loop(16.5f, 12f, 12.5f, 9.5f, 12.5f, 14.5f),
)

/** An OR gate: the curved shield, two inputs, one output. */
internal fun orGate(): List<GlyphPart> = listOf(
    seg(2f, 8f, 8f, 8f),
    seg(2f, 16f, 8f, 16f),
    GlyphPart.Cubic(6f, 5f, 9f, 10f, 9f, 14f, 6f, 19f),
    GlyphPart.Cubic(6f, 19f, 13f, 19f, 17f, 15f, 18f, 12f),
    GlyphPart.Cubic(18f, 12f, 17f, 9f, 13f, 5f, 6f, 5f),
    seg(18f, 12f, 22f, 12f),
)

/** A NOT gate: the triangle and the bubble that inverts it. */
internal fun notGate(): List<GlyphPart> = listOf(
    seg(2f, 12f, 6f, 12f),
    loop(6f, 6f, 14f, 12f, 6f, 18f),
    bubble(16f, 12f),
    seg(18f, 12f, 22f, 12f),
)

/** A NAND gate: the AND body with the inverting bubble on its output. */
internal fun nandGate(): List<GlyphPart> = listOf(
    seg(2f, 8f, 6f, 8f),
    seg(2f, 16f, 6f, 16f),
    run(6f, 5f, 10f, 5f),
    GlyphPart.Cubic(10f, 5f, 14.5f, 5f, 14.5f, 19f, 10f, 19f),
    seg(10f, 19f, 6f, 19f),
    seg(6f, 19f, 6f, 5f),
    bubble(16f, 12f),
    seg(18f, 12f, 22f, 12f),
)

/** A NOR gate: the OR shield with the inverting bubble on its output. */
internal fun norGate(): List<GlyphPart> = listOf(
    seg(2f, 8f, 7f, 8f),
    seg(2f, 16f, 7f, 16f),
    GlyphPart.Cubic(5f, 5f, 8f, 10f, 8f, 14f, 5f, 19f),
    GlyphPart.Cubic(5f, 19f, 11f, 19f, 14f, 15f, 15f, 12f),
    GlyphPart.Cubic(15f, 12f, 14f, 9f, 11f, 5f, 5f, 5f),
    bubble(17f, 12f),
    seg(19f, 12f, 22f, 12f),
)

/** An XOR gate: the OR shield with the extra back arc that means *exclusive*. */
internal fun xorGate(): List<GlyphPart> =
    listOf(
        seg(2f, 8f, 7f, 8f),
        seg(2f, 16f, 7f, 16f),
        // The back arc sits a unit behind the shield, which is what reads as *exclusive*.
        GlyphPart.Cubic(3f, 5f, 6f, 10f, 6f, 14f, 3f, 19f),
    ) + norShield + listOf(seg(18f, 12f, 22f, 12f))

/** An XNOR gate: the exclusive shield with the inverting bubble. */
internal fun xnorGate(): List<GlyphPart> =
    listOf(
        seg(2f, 8f, 7f, 8f),
        seg(2f, 16f, 7f, 16f),
        GlyphPart.Cubic(3f, 5f, 6f, 10f, 6f, 14f, 3f, 19f),
    ) + norShield + listOf(bubble(17f, 12f), seg(19f, 12f, 22f, 12f))

/** A buffer: a triangle, no bubble — the gate that only repeats its input. */
internal fun bufferGate(): List<GlyphPart> = listOf(
    seg(2f, 12f, 6f, 12f),
    loop(6f, 5f, 18f, 12f, 6f, 19f),
    seg(18f, 12f, 22f, 12f),
)

/** An open switch: the lever lifted off its far contact. */
internal fun switchOpen(): List<GlyphPart> = listOf(
    run(2f, 15f, 7f, 15f, 17f, 6f),
    seg(17f, 15f, 22f, 15f),
    seg(17f, 12f, 17f, 18f),
)

/** A closed switch: one continuous wire between the two contacts. */
internal fun switchClosed(): List<GlyphPart> = listOf(
    seg(2f, 12f, 22f, 12f),
    seg(7f, 9f, 7f, 15f),
    seg(17f, 9f, 17f, 15f),
)

/** A transformer: two coils either side of a laminated core. */
internal fun transformer(): List<GlyphPart> = listOf(
    // Primary coil above the core, secondary below it; the humps face each other.
    seg(2f, 7f, 5f, 7f),
    coil(5f, 7f, 9f, controlY = 11f),
    coil(9f, 7f, 13f, controlY = 11f),
    coil(13f, 7f, 17f, controlY = 11f),
    seg(17f, 7f, 22f, 7f),
    seg(2f, 17f, 5f, 17f),
    coil(5f, 17f, 9f, controlY = 13f),
    coil(9f, 17f, 13f, controlY = 13f),
    coil(13f, 17f, 17f, controlY = 13f),
    seg(17f, 17f, 22f, 17f),
    // The core is two bars, never joined across: one bar would be a wire, not a core.
    seg(6f, 11f, 18f, 11f),
    seg(6f, 13f, 18f, 13f),
)

/** A junction: the connection dot where two circuit wires meet. */
internal fun junction(): List<GlyphPart> = listOf(GlyphPart.Circle(12f, 12f, 3f))

// --- shared figure pieces ------------------------------------------------------------------------

/** The body, base lead, collector and emitter leads of a bipolar transistor. */
private fun bjtBody(): List<GlyphPart> = listOf(
    GlyphPart.Circle(13f, 12f, 6.5f),
    seg(2f, 12f, 9.5f, 12f),
    // Collector leaves upwards, emitter downwards: the two directions are what make it a transistor.
    run(9.5f, 10f, 15.5f, 6f, 15.5f, 2f),
    run(9.5f, 14f, 15.5f, 18f, 15.5f, 22f),
    // The base bar: the one stroke both variants share and both arrows read against.
    seg(9.5f, 7.5f, 9.5f, 16.5f),
)

/**
 * The body of an OR gate followed by the extra back arc of an exclusive one — the two strokes that
 * differ between `xorGate`/`xnorGate` and `orGate`/`norGate`.
 */
private val norShield: List<GlyphPart>
    get() = listOf(
    GlyphPart.Cubic(6f, 5f, 9f, 10f, 9f, 14f, 6f, 19f),
    GlyphPart.Cubic(6f, 19f, 13f, 19f, 17f, 15f, 18f, 12f),
    GlyphPart.Cubic(18f, 12f, 17f, 9f, 13f, 5f, 6f, 5f),
)

/** The inversion bubble a negated gate wears on its output. */
private fun bubble(cx: Float, cy: Float): GlyphPart = GlyphPart.Circle(cx, cy, 2f)

/**
 * One coil hump: a half-round bump from [x1] to [x2] whose two control points sit at [controlY],
 * which is what makes an inductor read as windings and a transformer's coils face its core.
 */
private fun coil(x1: Float, y: Float, x2: Float, controlY: Float): GlyphPart =
    GlyphPart.Cubic(x1, y, x1, controlY, x2, controlY, x2, y)

/** A straight stroke between two points, on the 24-unit grid. */
private fun seg(x1: Float, y1: Float, x2: Float, y2: Float): GlyphPart =
    GlyphPart.Line(x1, y1, x2, y2)

/** An open run of connected strokes from x/y pairs. */
private fun run(vararg xy: Float): GlyphPart = GlyphPart.Polyline(points(*xy), closed = false)

/** A closed outline from x/y pairs. */
private fun loop(vararg xy: Float): GlyphPart = GlyphPart.Polyline(points(*xy), closed = true)

/** x/y pairs as glyph points. */
private fun points(vararg xy: Float): List<GlyphPoint> =
    xy.toList().chunked(2).map { (x, y) -> GlyphPoint(x, y) }
