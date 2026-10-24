package com.mobixournal.render

/**
 * The tablet's **three circuit palettes**, as one shared list: passive parts, active parts and logic
 * gates.
 *
 * The split is not cosmetic. `app/ui/ToolGroups.kt` draws three buttons (`circuit`, `circuit_active`,
 * `logic`) and this enum is that division in a form the window can read too, so a part cannot appear in a
 * different family on the two shells. [members] is the group's contents in the order its picker draws
 * them (the tablet's own order), and [rows] is the layout its palette uses — each row a line of the
 * glass — which is why the tables are not flattened into one list.
 *
 * [face] is the part a slot shows and places when nothing else is armed: the group's first member, the
 * same one the tablet's button faces.
 */
enum class CircuitFamily(val title: String, val members: List<ShapeKind>, val rows: List<List<ShapeKind>>) {

    /** Resistors, capacitors, coils, ground, the two switches and a junction — the passive half. */
    PASSIVE(
        title = "Passive circuits",
        members = listOf(
            ShapeKind.RESISTOR, ShapeKind.CAPACITOR, ShapeKind.INDUCTOR,
            ShapeKind.GROUND, ShapeKind.SWITCH_OPEN, ShapeKind.SWITCH_CLOSED,
            ShapeKind.JUNCTION,
        ),
        rows = listOf(
            listOf(ShapeKind.RESISTOR, ShapeKind.CAPACITOR),
            listOf(ShapeKind.INDUCTOR, ShapeKind.GROUND),
            listOf(ShapeKind.SWITCH_OPEN, ShapeKind.SWITCH_CLOSED),
            listOf(ShapeKind.JUNCTION),
        ),
    ),

    /** Diodes, the amplifier and the transistors, the two sources and a transformer. */
    ACTIVE(
        title = "Active circuits",
        members = listOf(
            ShapeKind.DIODE, ShapeKind.LED, ShapeKind.ZENER_DIODE,
            ShapeKind.OPAMP, ShapeKind.BJT_NPN, ShapeKind.BJT_PNP,
            ShapeKind.DC_SOURCE, ShapeKind.CURRENT_SOURCE,
            ShapeKind.TRANSFORMER,
        ),
        rows = listOf(
            listOf(ShapeKind.DIODE, ShapeKind.LED, ShapeKind.ZENER_DIODE),
            listOf(ShapeKind.OPAMP, ShapeKind.BJT_NPN, ShapeKind.BJT_PNP),
            listOf(ShapeKind.DC_SOURCE, ShapeKind.CURRENT_SOURCE, ShapeKind.TRANSFORMER),
        ),
    ),

    /** The gates, each beside its inverted twin and the plain ones together. */
    LOGIC(
        title = "Logic gates",
        members = listOf(
            ShapeKind.AND_GATE, ShapeKind.OR_GATE, ShapeKind.NOT_GATE,
            ShapeKind.NAND_GATE, ShapeKind.NOR_GATE, ShapeKind.XOR_GATE,
            ShapeKind.XNOR_GATE, ShapeKind.BUFFER_GATE,
        ),
        rows = listOf(
            listOf(ShapeKind.AND_GATE, ShapeKind.NAND_GATE),
            listOf(ShapeKind.OR_GATE, ShapeKind.NOR_GATE),
            listOf(ShapeKind.XOR_GATE, ShapeKind.XNOR_GATE),
            listOf(ShapeKind.NOT_GATE, ShapeKind.BUFFER_GATE),
        ),
    ),
    ;

    /** The part the slot faces, and places when the palette has not armed another. */
    val face: ShapeKind get() = members.first()

    init {
        require(rows.all { it.all(members::contains) }) { "$title's rows must only draw its own members" }
        require(rows.flatten().toSet() == members.toSet()) { "$title's rows must offer every member once" }
    }

    companion object {
        /** The family that owns [kind], or null for a shape that is not a circuit part. */
        fun of(kind: ShapeKind): CircuitFamily? = entries.firstOrNull { kind in it.members }
    }
}
