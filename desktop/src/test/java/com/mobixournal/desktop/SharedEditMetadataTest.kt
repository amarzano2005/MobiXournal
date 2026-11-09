package com.mobixournal.desktop

import com.mobixournal.desktop.ink.withElement
import com.mobixournal.format.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedEditMetadataTest {
    @Test fun appendingAnElementKeepsUnknownLayerAttributes() {
        val layer = Layer(emptyList(), "named", mapOf("future" to "preserved"))
        val page = Page(100.0, 100.0, Background.Solid(-1, "plain"), listOf(layer))
        val text = TextElement("Sans", 10.0, 1.0, 2.0, 0, "hello")
        val edited = page.withElement(text)
        assertEquals(layer.extraAttrs, edited.layers.single().extraAttrs)
        assertEquals(layer.name, edited.layers.single().name)
        assertEquals(listOf(text), edited.layers.single().elements)
    }
}
