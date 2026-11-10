package com.mobixournal.ui

import com.mobixournal.format.mobix.MobixCapability
import com.mobixournal.format.mobix.MobixOpenOutcome
import com.mobixournal.format.mobix.MobixOpenVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notice each verdict produces, and the actions it disables — driven by a **fake verdict**, so the
 * copy and the state are checked against the contract's outcomes rather than against whatever a real
 * file happens to produce (`docs/mobix-schema-spec.md`, *Declared capabilities*).
 *
 * The last test is the reason this mapping exists at all: if a shell shows a notice that a document is
 * read-only, the same object has to say the document is read-only. Two derivations would let those
 * drift, and the drift reads to the user as a promise the app broke.
 */
class OpenVerdictNoticeTest {

    private fun capability(text: String): MobixCapability = requireNotNull(MobixCapability.parse(text))

    private fun notice(verdict: MobixOpenVerdict, name: String = "notes.mobix") = openVerdictNotice(name, verdict)

    @Test
    fun aFullEditSaysNothing() {
        assertNull("a document this build understands opens with no notice", notice(MobixOpenVerdict(MobixOpenOutcome.FULL_EDIT)))
    }

    @Test
    fun aPreservedDocumentGetsALineAndStaysEditable() {
        val verdict = MobixOpenVerdict(
            outcome = MobixOpenOutcome.PRESERVED,
            unknownOptional = listOf(capability("example.org/future.notes:1")),
        )

        val notice = requireNotNull(notice(verdict))
        assertFalse("a preserved document is not a question", notice.asksForAnswer)
        assertFalse("and it is editable, so nothing is disabled", notice.readOnly)
        assertTrue("the line names the feature", "example.org/future.notes:1" in notice.body)
        assertTrue("and there is no heading to show", notice.title.isEmpty())
    }

    @Test
    fun aNewerVersionIsACardThatOpensReadOnly() {
        val verdict = MobixOpenVerdict(MobixOpenOutcome.READ_ONLY, because = "2.0.0")

        val notice = requireNotNull(notice(verdict))
        assertTrue("the reader answers it", notice.asksForAnswer)
        assertTrue("and opening it disables writing", notice.readOnly)
        assertEquals("Newer version document", notice.title)
        assertEquals("Open read-only", notice.confirmLabel)
        assertTrue("the sentence names the version", "2.0.0" in notice.body)
    }

    @Test
    fun aRefusedWriteNamesTheFeatureAndSaysWhatIsDisabled() {
        val verdict = MobixOpenVerdict(
            outcome = MobixOpenOutcome.REFUSE_WRITE,
            unsatisfiedRequired = listOf(capability("example.org/lab.notebook:1")),
        )

        val notice = requireNotNull(notice(verdict))
        assertEquals("Unsupported features", notice.title)
        assertEquals("Open for reading", notice.confirmLabel)
        assertTrue("what is disabled is stated", "nothing may be drawn, saved or exported" in notice.body)
        assertTrue("the feature is named", "example.org/lab.notebook:1" in notice.body)
        assertFalse("never a class name", "MobixOpen" in notice.body)
        assertTrue(notice.readOnly)
    }

    @Test
    fun everyOutcomeKeepsTheNoticeAndTheDisabledActionsInStep() {
        val verdicts = listOf(
            MobixOpenVerdict(MobixOpenOutcome.FULL_EDIT),
            MobixOpenVerdict(MobixOpenOutcome.PRESERVED, unknownOptional = listOf(capability("example.org/a:1"))),
            MobixOpenVerdict(MobixOpenOutcome.READ_ONLY, because = "9.0.0"),
            MobixOpenVerdict(MobixOpenOutcome.REFUSE_WRITE, unsatisfiedRequired = listOf(capability("example.org/b:1"))),
        )

        for (verdict in verdicts) {
            val notice = notice(verdict)
            if (verdict.outcome == MobixOpenOutcome.FULL_EDIT) {
                assertNull("nothing to say about a document that opens normally", notice)
                continue
            }
            assertNotNull("every other outcome says something", notice)
            assertEquals(
                "a shell disables writing exactly when the verdict does",
                !verdict.mayWrite,
                notice!!.readOnly,
            )
            assertEquals(
                "and editing follows the same answer",
                !verdict.mayEdit,
                notice.readOnly,
            )
            assertTrue("the sentence is the verdict's own", notice.body == verdict.reason)
        }
    }
}
