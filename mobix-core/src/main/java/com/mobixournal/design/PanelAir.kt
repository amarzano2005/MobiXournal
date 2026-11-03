package com.mobixournal.design

/**
 * The air a rounded surface keeps between its own edge and anything it holds — shared by **both** shells.
 *
 * The tablet and the `desktop/` window draw the same panels (the settings surface, the tool flyouts, the
 * menu), so they cannot each own a number: a 6 dp margin on the tablet and a 2 dp one on the PC is the
 * same panel drawn at two tightnesses. The values are plain floats here because `mobix-core` carries no
 * Compose dependency; each shell turns them into its own `dp`.
 */
object PanelAir {
    /**
     * The gap at a panel's rounded edge (6 dp).
     *
     * A corner cuts the outermost few dp off a straight edge, so a line of text or the border of a pill
     * drawn at the container's own padding lands on the curve and reads as clipped. The container pads by
     * this number instead, which also means a caller's own few dp cannot defeat it.
     */
    const val EDGE_DP = 6f

    /**
     * The gap a pill keeps between its rounded end and its label (12 dp).
     *
     * Larger than [EDGE_DP] because the label is *inside* the shape: a curve eats into a label's own
     * line, and 10 dp that looks generous in a wide box is cramped in a 28 dp capsule.
     */
    const val PILL_TEXT_DP = 12f
}
