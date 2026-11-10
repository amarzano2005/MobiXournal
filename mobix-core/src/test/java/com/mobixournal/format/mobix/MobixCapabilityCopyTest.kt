package com.mobixournal.format.mobix

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copy and paste between documents, as the capability contract states it
 * (`docs/mobix-schema-spec.md`, *Cross-cutting*): the destination's requirement only ever grows, and a
 * selection this build cannot keep is refused rather than accepted and trimmed on the next save.
 *
 * The set a pasted element needs is the **source document's** declaration — an element cannot be
 * separated from what the file that held it says — which is why the decision takes two lists and no
 * element type. The last test is the one that makes the others true rather than decorative: a raised
 * declaration reaches the archive a save writes.
 */
class MobixCapabilityCopyTest {

    private val satisfied = setOf(
        capability("org.mobixournal/stem.connector:1"),
        capability("org.mobixournal/audio.opus:2"),
    )

    private fun capability(text: String): MobixCapability = requireNotNull(MobixCapability.parse(text))

    private fun decide(
        destination: List<String> = emptyList(),
        incoming: List<String> = emptyList(),
    ): MobixPasteVerdict = MobixCapabilityPaste.decide(
        destination = destination.map(::capability),
        incoming = incoming.map(::capability),
        satisfied = satisfied,
    )

    @Test
    fun aPlainV1SelectionLeavesTheDestinationExactlyAsItWas() {
        val verdict = decide()

        assertTrue("a v1 selection is always pasteable", verdict.accepted)
        assertTrue("and claims nothing", verdict.requiredCapabilities.isEmpty())
        assertNull(verdict.refusedBecause)
        assertEquals("an accepted paste says nothing to the user", "", verdict.reason)
    }

    @Test
    fun aSelectionThatNeedsACapabilityThisBuildHasRaisesTheDestination() {
        val verdict = decide(
            destination = listOf("org.mobixournal/stem.connector:1"),
            incoming = listOf("org.mobixournal/audio.opus:2"),
        )

        assertTrue(verdict.accepted)
        assertEquals(
            "the destination's own declaration first, then what the selection adds",
            listOf("org.mobixournal/stem.connector:1", "org.mobixournal/audio.opus:2"),
            verdict.requiredCapabilities.map { it.toString() },
        )
    }

    @Test
    fun aHigherVersionReplacesALowerOneWithoutMovingIt() {
        val verdict = decide(
            destination = listOf("org.mobixournal/audio.opus:1", "org.mobixournal/stem.connector:1"),
            incoming = listOf("org.mobixournal/audio.opus:2"),
        )

        assertEquals(
            listOf("org.mobixournal/audio.opus:2", "org.mobixournal/stem.connector:1"),
            verdict.requiredCapabilities.map { it.toString() },
        )
    }

    @Test
    fun aSelectionThisBuildCannotKeepIsRefusedAndTheDestinationIsUnchanged() {
        val verdict = decide(
            destination = listOf("org.mobixournal/stem.connector:1"),
            incoming = listOf("example.org/lab.notebook:1"),
        )

        assertFalse("accepted-and-trimmed is the failure this rule exists to prevent", verdict.accepted)
        assertEquals(
            "a refused paste adds nothing, so it claims nothing",
            listOf("org.mobixournal/stem.connector:1"),
            verdict.requiredCapabilities.map { it.toString() },
        )
        assertEquals("example.org/lab.notebook:1", verdict.refusedBecause.toString())
        assertTrue("the message names the feature", "example.org/lab.notebook:1" in verdict.reason)
        assertFalse("and what it means", verdict.reason.isBlank())
        assertFalse("never a class name", "MobixCapability" in verdict.reason)
    }

    @Test
    fun pastingTheSameSelectionTwiceChangesNothingTheSecondTime() {
        val once = decide(incoming = listOf("org.mobixournal/audio.opus:2"))
        val twice = MobixCapabilityPaste.decide(
            destination = once.requiredCapabilities,
            incoming = listOf(capability("org.mobixournal/audio.opus:2")),
            satisfied = satisfied,
        )

        assertEquals(once.requiredCapabilities, twice.requiredCapabilities)
    }

    @Test
    fun aRaisedDeclarationReachesTheArchiveASaveWrites() {
        val raised = decide(incoming = listOf("org.mobixournal/audio.opus:2")).requiredCapabilities
        val document = Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(
                        Layer(
                            listOf(
                                Stroke(
                                    points = listOf(StrokePoint(10.0, 10.0, 1.0)),
                                    color = 0xFF000000.toInt(),
                                    tool = Tool.PEN,
                                    capStyle = "round",
                                    uniformWidth = false,
                                    id = "stk_pasted",
                                ),
                            ),
                        ),
                    ),
                    id = "pg_1",
                ),
            ),
            requiredCapabilities = raised,
        )

        val bytes = MobixDocument.of(document, appVersion = "2.0.0", nowMillis = 42L).toBytes(42L)
        val reopened = MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes)))

        assertEquals(
            "the file says what the paste made the document hold",
            listOf("org.mobixournal/audio.opus:2"),
            reopened.manifest.requiredCapabilities.map { it.toString() },
        )
    }
}
