package com.mobixournal.desktop

import com.mobixournal.desktop.io.*
import com.mobixournal.desktop.shell.*
import com.mobixournal.desktop.ink.withElement
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopRecoveryJournalTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun nativeCheckpointsAppendDeltasAndUndoSurvivesCompaction() {
        val original = temp.newFile("original.mobix")
        SyntheticNotebook.write(original, SyntheticNotebook.pages(40))
        val archive = original.inputStream().use(com.mobixournal.format.mobix.MobixContainer::open)
        val preserved = listOf("recovery/position", "recovery/order", "order", "entries/future")
        original.outputStream().use { output ->
            com.mobixournal.format.mobix.MobixContainer.save(archive.entries + preserved.map {
                com.mobixournal.format.mobix.MobixContainer.Entry(it, "unknown-value".toByteArray())
            }, output)
        }
        val document = DocumentLoader.load(original)
        val tab = DesktopTab(document)
        val store = DesktopRecovery(temp.newFolder())
        try {
            val initial = document.snapshot()
            val count = document.page(0).layers.sumOf { it.elements.size }
            document.editPage(0) { it.withElement(TextElement("Sans", 10.0, 1.0, 2.0, 0, "first")) }
            val entry = requireNotNull(store.save(tab))
            val baseline = entry.snapshot.readBytes()
            document.editPage(0) { it.withElement(TextElement("Sans", 10.0, 1.0, 2.0, 0, "second")) }
            assertNotNull(store.save(tab))
            assertArrayEquals("a delta does not rewrite the baseline", baseline, entry.snapshot.readBytes())
            val records = store.dir.listFiles()!!.filter { it.name.contains(".delta-") }
            assertEquals(1, records.size)
            assertTrue(records.single().length() < entry.snapshot.length())
            document.restore(initial)
            assertNotNull(store.save(tab))
            java.io.File(store.dir, "interrupted.writing").writeText("partial")
            val recovered = store.entries().single()
            DocumentLoader.load(recovered.snapshot).use { loaded ->
                assertEquals(count, loaded.page(0).layers.sumOf { it.elements.size })
            }
            com.mobixournal.format.mobix.MobixEntrySource.fromFile(recovered.snapshot).use { source ->
                preserved.forEach { assertEquals("unknown-value", source.readBytes(it)!!.toString(Charsets.UTF_8)) }
            }
            assertFalse(store.dir.listFiles()!!.any { it.name.contains(".delta-") })
        } finally { document.close() }
    }
}
