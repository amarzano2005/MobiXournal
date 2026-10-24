package com.mobixournal.render

import com.mobixournal.ui.EditorTool
import com.mobixournal.ui.secondaryToolGroup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The **one** statement of which circuit parts belong to which family, checked against the tablet's own
 * tool groups.
 *
 * `CircuitFamily` (in `mobix-core`) is what the window's three circuit slots offer; `SECONDARY_TOOL_GROUPS`
 * is what the tablet's three buttons offer. They are two descriptions of one decision, so this test reads
 * the tablet's groups and fails the build if a part moved, was added on one side only, or changed place in
 * the order the palette draws — the drift a hand-kept second list always eventually collects.
 */
class CircuitPaletteDriftTest {

    @Test
    fun `the window's three palettes are the tablet's three groups`() {
        for ((family, id) in FAMILIES) {
            val group = secondaryToolGroup(id) ?: error("the tablet has no '$id' group")
            assertEquals(
                "${family.title} must offer the tablet's members, in its order",
                group.tools.map { ShapeKind.valueOf(it.name) },
                family.members,
            )
            assertEquals(
                "${family.title}'s palette must draw the tablet's rows",
                group.pickerLayout.map { row -> row.map { ShapeKind.valueOf(it.name) } },
                family.rows,
            )
        }
    }

    @Test
    fun `every family's parts are circuit shapes`() {
        for (family in CircuitFamily.entries) {
            for (kind in family.members) {
                assertEquals("${kind.name} belongs to ${family.title}", family, CircuitFamily.of(kind))
                assertEquals(
                    "${kind.name} must be a circuit part to be placed",
                    true,
                    CircuitElementFactory.isCircuitKind(kind),
                )
            }
        }
        // The chemistry bond and the figures share the shape library but are not parts.
        assertEquals(null, CircuitFamily.of(ShapeKind.RECTANGLE))
        assertEquals(null, CircuitFamily.of(ShapeKind.CHEM_BOND_SINGLE))
    }

    private companion object {
        val FAMILIES = listOf(
            CircuitFamily.PASSIVE to "circuit",
            CircuitFamily.ACTIVE to "circuit_active",
            CircuitFamily.LOGIC to "logic",
        )
    }
}
