package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailItemsTest {

    @Test
    fun `rail item ids are unique`() {
        val ids = RAIL_ITEMS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every tool group has a rail item`() {
        val ids = RAIL_ITEMS.map { it.id }.toSet()
        assertTrue(TOOL_GROUPS.all { it.id in ids })
    }

    @Test
    fun `empty order is the factory order`() {
        assertEquals(RAIL_ITEMS, orderedRailItems(emptyList()))
    }

    @Test
    fun `a partial order keeps its items first and appends the rest`() {
        val ordered = orderedRailItems(listOf("pages", "rectangle"))
        assertEquals(listOf("pages", "rectangle"), ordered.take(2).map { it.id })
        assertEquals(RAIL_ITEMS.size, ordered.size)
        assertEquals(RAIL_ITEMS.toSet(), ordered.toSet())
    }

    @Test
    fun `unknown and duplicate ids are ignored`() {
        val ordered = orderedRailItems(listOf("nope", "zoom", "zoom"))
        assertEquals("zoom", ordered.first().id)
        assertEquals(RAIL_ITEMS.size, ordered.size)
    }

    @Test
    fun `hidden items are dropped from the visible rail`() {
        val visible = visibleRailItems(emptyList(), setOf("zoom", "pages"))
        assertEquals(RAIL_ITEMS.size - 2, visible.size)
        assertTrue(visible.none { it.id == "zoom" || it.id == "pages" })
    }

    @Test
    fun `moving an item swaps it with its neighbour`() {
        val moved = moveRailItem(emptyList(), 0, 1)
        val factory = RAIL_ITEMS.map { it.id }
        assertEquals(listOf(factory[1], factory[0]), moved.take(2))
        assertEquals(factory.size, moved.size)
    }

    @Test
    fun `a move off either end is a no-op`() {
        val factory = RAIL_ITEMS.map { it.id }
        assertEquals(factory, moveRailItem(emptyList(), 0, -1))
        assertEquals(factory, moveRailItem(emptyList(), factory.lastIndex, 1))
    }

    @Test
    fun `ids round-trip through encode and decode`() {
        val ids = listOf("pages", "line", "zoom")
        assertEquals(ids, decodeRailIds(encodeRailIds(ids)))
        assertEquals(emptyList<String>(), decodeRailIds(null))
        assertEquals(listOf("line"), decodeRailIds("line,gone"))
    }

    @Test
    fun `line, rectangle, shapes group, arrows and table each have a rail slot`() {
        val ids = RAIL_ITEMS.map { it.id }
        assertTrue(listOf("line", "rectangle", "shape", "arrow", "table", "circuit", "circuit_active", "logic").all { it in ids })
    }

    @Test
    fun `factory order matches the documented default`() {
        val ids = RAIL_ITEMS.map { it.id }
        assertEquals(
            listOf(
                "pen", "highlighter", "favorites", "eraser", "pan", "select",
                "text", "insert", "vspace", "zoom", "pages", "background", "layers", "audio", "play", "shapes",
                "line", "rectangle", "shape", "arrow", "table", "circuit", "circuit_active", "logic", "guides",
            ),
            ids,
        )
    }

    @Test
    fun `the retired presets slot is gone rather than lingering as a dead button`() {
        assertTrue(RAIL_ITEMS.none { it.id == "presets" })
        assertTrue(visibleRailItems(listOf("presets", "zoom"), emptySet()).none { it.id == "presets" })
        assertEquals(emptyList<String>(), decodeRailIds("presets"))
    }

    @Test
    fun `the retired colour and size slot is gone, folded into the favourites slot`() {
        // The two positions were one errand: the dots live in the `favorites` slot now, along with the
        // chevron onto the pop-up, and a saved order naming `color` simply drops it.
        assertTrue(RAIL_ITEMS.none { it.id == "color" })
        assertEquals(emptyList<String>(), decodeRailIds("color"))
        assertEquals("Colour & size", RAIL_ITEMS.first { it.id == "favorites" }.label)
    }

    @Test
    fun `the retired style slot is gone rather than lingering as a dead button`() {
        assertTrue(RAIL_ITEMS.none { it.id == "style" })
        // A saved order naming it must degrade to a rail without it, not crash or resurrect it.
        assertTrue(visibleRailItems(listOf("style", "zoom"), emptySet()).none { it.id == "style" })
        assertEquals(emptyList<String>(), decodeRailIds("style"))
    }

    @Test
    fun `shape recognition has a rail slot that is not a tool group`() {
        assertTrue(RAIL_ITEMS.any { it.id == "shapes" })
        assertEquals(null, toolGroupForRailItem("shapes"))
        // Existing installs stored an order without it; it must still appear on the rail.
        assertTrue(visibleRailItems(listOf("pages", "zoom"), emptySet()).any { it.id == "shapes" })
    }

    @Test
    fun `tool groups resolve from their rail id and panels do not`() {
        assertEquals(TOOL_GROUPS.first(), toolGroupForRailItem(TOOL_GROUPS.first().id))
        assertEquals(null, toolGroupForRailItem("zoom"))
    }
}
