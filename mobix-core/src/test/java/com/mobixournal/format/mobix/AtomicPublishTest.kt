package com.mobixournal.format.mobix

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The file half of the publish contract: the bytes land through a staging file beside the target, and a
 * failure of any kind leaves the target byte-identical (`docs/document-session.md`).
 */
class AtomicPublishTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun target(): File = File(temp.root, "notes.mobix").apply { writeBytes("old".toByteArray()) }

    @Test
    fun aSuccessfulPublishReplacesTheTargetWithTheStagedBytes() {
        val file = target()
        val answer = AtomicPublish.publish(file, validate = { assertEquals("new", it.readText()) }) { staged ->
            staged.writeText("new")
            "written"
        }
        assertEquals("written", answer)
        assertEquals("new", file.readText())
        assertNoStagingLeftovers()
    }

    @Test
    fun aFailedWriteLeavesTheTargetUntouched() {
        val file = target()
        val before = file.readBytes()
        runCatching {
            AtomicPublish.publish(file) { staged ->
                staged.writeText("partial")
                throw java.io.IOException("disk full")
            }
        }
        assertArrayEquals("the original is still the document on disk", before, file.readBytes())
        assertNoStagingLeftovers()
    }

    @Test
    fun aDocumentThatFailsValidationIsNotPublished() {
        val file = target()
        val before = file.readBytes()
        val failure = runCatching {
            AtomicPublish.publish(file, validate = { throw AtomicPublish.Refused("it did not reopen") }) {
                it.writeText("new")
            }
        }.exceptionOrNull()
        assertTrue("the refusal is the caller's, not a silent success", failure is AtomicPublish.Refused)
        assertArrayEquals("a refused document never reaches the target", before, file.readBytes())
        assertNoStagingLeftovers()
    }

    @Test
    fun anEmptyWriteIsRefusedRatherThanPublished() {
        val file = target()
        val before = file.readBytes()
        val failure = runCatching { AtomicPublish.publish(file) { /* writes nothing */ } }.exceptionOrNull()
        assertTrue(failure is AtomicPublish.Refused)
        assertArrayEquals(before, file.readBytes())
        assertNoStagingLeftovers()
    }

    @Test
    fun aPortableCopyTargetIsPublishedWithoutLookingAtItsSuffix() {
        val file = File(temp.root, "handed-on.pmobix").apply { writeBytes("old".toByteArray()) }
        AtomicPublish.publish(file) { it.writeText("new") }
        assertEquals("the suffix does not decide the container", "new", file.readText())
    }

    private fun assertNoStagingLeftovers() {
        val leftovers = temp.root.listFiles().orEmpty().filter { it.name.endsWith(".publishing") }
        assertTrue("a staging file was left behind: $leftovers", leftovers.isEmpty())
    }
}
