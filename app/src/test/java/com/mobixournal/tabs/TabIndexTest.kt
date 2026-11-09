package com.mobixournal.tabs

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Test

/** The session index round-trips every tab record, including titles that contain its separators. */
class TabIndexTest {

    private fun doc() = Document(
        pages = listOf(Page(100.0, 100.0, Background.Solid(-1, "plain"), listOf(Layer(emptyList())))),
    )

    private fun roundTrip(session: TabSession): TabSession =
        TabIndex.decode(TabIndex.encode(session)) { doc() }

    @Test
    fun cancellingThePickerDropsOnlyTheProposedConversion() {
        val original = OpenTab("a", "a.xopp", doc(), uri = "content://original", format = SaveFormat.ORIGINAL)
        val proposed = SavePickerState(PendingSave(0, original.id, SaveFormat.MOBIX, "a.mobix"))
        assertEquals(SavePickerState(), proposed.answered(null))
        assertEquals(SaveFormat.ORIGINAL, original.format)
        assertEquals("content://original", original.uri)
        assertEquals("content://copy", proposed.answered("content://copy").uri)
    }

    @Test
    fun imageSourcesSurviveRestartWithoutCollidingAcrossTabs() {
        val a = OpenTab("a", "a.xopp", doc(), images = mapOf("bg.png" to java.io.File("one.png")))
        val b = OpenTab("b", "b.xopp", doc(), images = mapOf("bg.png" to java.io.File("two.png")))
        val back = roundTrip(TabSession(listOf(a, b), 1))
        assertEquals(a.images.mapValues { it.value.absoluteFile }, back.tabs[0].images)
        assertEquals(b.images.mapValues { it.value.absoluteFile }, back.tabs[1].images)
    }

    @Test
    fun pendingPickerKeepsItsOwnerAndFormatWhenSelectionChanges() {
        val a = OpenTab("a", "a.xopp", doc())
        val session = TabSessions(listOf(a, OpenTab("b", "b.xopp", doc())))
        val pending = PendingSave(0, a.id, SaveFormat.MOBIX, "a.mobix")
        session.select(1)
        assertEquals(a, pending.tabIn(session))
        assertEquals(SaveFormat.ORIGINAL, a.format)
        session.close(0)
        assertEquals(null, pending.tabIn(session))
    }

    @Test
    fun `every field survives a round trip`() {
        val session = TabSession(
            tabs = listOf(
                OpenTab("t1", "notes.xopp", doc(), "content://docs/1", SaveFormat.ZIPPED, "/data/bg.pdf", 4),
                OpenTab("t2", "Untitled", doc()),
            ),
            activeIndex = 1,
        )
        val back = roundTrip(session)
        assertEquals(session.activeIndex, back.activeIndex)
        // Decoded tabs are placeholders: the index carries no document, so `hydrated` is false.
        assertEquals(session.tabs.map { it.copy(document = doc(), hydrated = false) }, back.tabs)
    }

    @Test
    fun `a title containing tabs and newlines cannot break the record layout`() {
        val session = TabSession(listOf(OpenTab("t1", "a\tb\nc\\d", doc())), 0)
        val back = roundTrip(session)
        assertEquals(1, back.tabs.size)
        assertEquals("a\tb\nc\\d", back.tabs[0].title)
    }

    @Test
    fun `a truncated line is skipped rather than failing the whole restore`() {
        val text = "active\t0\nt1\tone\t\tORIGINAL\t\t0\nbroken\n"
        val back = TabIndex.decode(text) { doc() }
        assertEquals(listOf("t1"), back.tabs.map { it.id })
    }

    @Test
    fun `an unknown save format falls back to ORIGINAL`() {
        val back = TabIndex.decode("active\t0\nt1\tone\t\tBOGUS\t\t0\n") { doc() }
        assertEquals(SaveFormat.ORIGINAL, back.tabs[0].format)
    }

    @Test
    fun `an active index past the end is clamped`() {
        val back = TabIndex.decode("active\t9\nt1\tone\t\tORIGINAL\t\t0\n") { doc() }
        assertEquals(0, back.activeIndex)
    }

    @Test
    fun `a shared document key survives a round trip`() {
        val session = TabSession(
            listOf(OpenTab("t1", "a", doc(), docKey = "d1"), OpenTab("t2", "a", doc(), docKey = "d1")),
            0,
        )
        val back = roundTrip(session)
        assertEquals(listOf("d1", "d1"), back.tabs.map { it.docKey })
    }

    @Test
    fun `a record written before document keys existed gets its own key`() {
        val back = TabIndex.decode("active\t0\nt1\tone\t\tORIGINAL\t\t0\n") { doc() }
        assertEquals("t1", back.tabs[0].docKey)
    }

    @Test
    fun `a record written with a trailing canvas mode is still read`() {
        // A build that carried a canvas geometry wrote one extra field at the end; it is ignored now
        // that every document is a page stack.
        val back = TabIndex.decode("active\t0\nt1\tone\t\tORIGINAL\t\t0\t\ttrue\tINFINITE_2D\n") { doc() }
        assertEquals("t1", back.tabs[0].id)
        assertEquals(true, back.tabs[0].isReadOnly)
    }

    @Test
    fun `an empty index decodes to an empty session`() {
        val back = TabIndex.decode("") { doc() }
        assertEquals(emptyList<OpenTab>(), back.tabs)
        assertEquals(0, back.activeIndex)
    }
}
