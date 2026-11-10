package com.mobixournal.format.mobix

import com.mobixournal.format.mobix.MobixOpenOutcome.FULL_EDIT
import com.mobixournal.format.mobix.MobixOpenOutcome.PRESERVED
import com.mobixournal.format.mobix.MobixOpenOutcome.READ_ONLY
import com.mobixournal.format.mobix.MobixOpenOutcome.REFUSE_WRITE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate as a matrix (`docs/mobix-schema-spec.md`, *Declared capabilities*): every combination of
 * (required known / required unknown / required at a version above this build) × (optional known /
 * optional unknown) × (`minAppVersion` below / at / above) produces exactly one outcome, and the two
 * precedence rules the contract singles out hold.
 *
 * The fixtures (`MobixCapabilityFixturesTest`) prove the outcomes against real files; this proves the
 * *space* between them, which is what a hand-written manifest of a third-party writer will land in.
 * The whole table is checked against one `satisfied` set, so a change to [MobixCapability.satisfies]
 * cannot pass by moving this build's claims.
 */
class MobixCapabilityGateTest {

    /** What this build implements here: one feature at v1, another at v2. */
    private val satisfied = setOf(
        capability("org.mobixournal/stem.connector:1"),
        capability("org.mobixournal/audio.opus:2"),
    )

    private fun capability(text: String): MobixCapability =
        requireNotNull(MobixCapability.parse(text)) { "$text is an identifier this test wrote wrongly" }

    private fun verdictFor(
        required: List<String> = emptyList(),
        optional: List<String> = emptyList(),
        minAppVersion: String = APP_VERSION,
    ): MobixOpenVerdict = MobixCapabilities.evaluate(
        manifest = MobixManifest.forNewDocument(
            documentId = "doc_gate",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = NOW,
        ).copy(
            requiredCapabilities = required.map(::capability),
            optionalCapabilities = optional.map(::capability),
            minAppVersion = minAppVersion,
        ),
        satisfied = satisfied,
        appVersion = APP_VERSION,
    )

    /**
     * One row of the matrix: the declaration in, the verdict out, including the list of capabilities
     * that decided it (in the writer's own order) and the two affordance flags the shells read.
     */
    private fun assertRow(
        expected: MobixOpenOutcome,
        required: List<String> = emptyList(),
        optional: List<String> = emptyList(),
        minAppVersion: String = APP_VERSION,
        unsatisfied: List<String> = emptyList(),
        unknownOptional: List<String> = emptyList(),
        because: String? = null,
    ) {
        val verdict = verdictFor(required, optional, minAppVersion)
        val what = "required=$required optional=$optional minAppVersion=$minAppVersion"

        assertEquals("outcome — $what", expected, verdict.outcome)
        assertEquals("the required capabilities that decided it — $what", unsatisfied, verdict.unsatisfiedRequired.map { it.toString() })
        assertEquals("the optional capabilities that decided it — $what", unknownOptional, verdict.unknownOptional.map { it.toString() })
        assertEquals("what decided it — $what", because, verdict.because)

        val writable = expected == FULL_EDIT || expected == PRESERVED
        assertEquals("mayWrite — $what", writable, verdict.mayWrite)
        assertEquals("mayEdit — $what", writable, verdict.mayEdit)

        assertTrue("the notice is a sentence — $what", verdict.reason.length > 20)
        assertFalse("the notice never names a class — $what", "MobixOpenVerdict" in verdict.reason)
        assertFalse("nor a Kotlin type — $what", "MobixCapabil" in verdict.reason)
    }

    @Test
    fun aSatisfiedDeclarationEditsNormally() {
        // Nothing declared: a v1 document, or one written before capabilities existed.
        assertRow(FULL_EDIT)
        // A requirement this build implements at exactly that version.
        assertRow(FULL_EDIT, required = listOf("org.mobixournal/stem.connector:1"))
        // …and one it implements at a *later* version, which satisfies the earlier one.
        assertRow(FULL_EDIT, required = listOf("org.mobixournal/audio.opus:1"))
        // A requirement and an optional feature, both implemented.
        assertRow(
            FULL_EDIT,
            required = listOf("org.mobixournal/stem.connector:1"),
            optional = listOf("org.mobixournal/audio.opus:2"),
        )
        // A floor below this build is no obstacle at all.
        assertRow(FULL_EDIT, minAppVersion = "1.0.0")
        // An absent floor means any app (`forNewDocument` writes "0.0.0" for a third-party writer).
        assertRow(FULL_EDIT, minAppVersion = "0.0.0")
    }

    @Test
    fun aFloorAboveThisBuildShowsEveryPageAndWritesNothing() {
        assertRow(READ_ONLY, minAppVersion = "9.0.0", because = "9.0.0")
        // `minAppVersion` outranks the optional list: a reader that cannot keep the file intact at all
        // must not claim to have preserved the parts it does not draw.
        assertRow(
            READ_ONLY,
            optional = listOf("example.org/future.notes:1"),
            minAppVersion = "9.0.0",
            because = "9.0.0",
        )
        // …and it does not attribute the decision to a capability that never decided anything.
        assertRow(READ_ONLY, required = listOf("org.mobixournal/stem.connector:1"), minAppVersion = "9.0.0", because = "9.0.0")
    }

    @Test
    fun anUnknownOptionalFeatureIsPreservedRatherThanRefused() {
        assertRow(PRESERVED, optional = listOf("example.org/future.notes:1"), unknownOptional = listOf("example.org/future.notes:1"))
        // Beside a requirement this build satisfies: the requirement is not the reason, so it is not named.
        assertRow(
            PRESERVED,
            required = listOf("org.mobixournal/stem.connector:1"),
            optional = listOf("example.org/future.notes:1"),
            unknownOptional = listOf("example.org/future.notes:1"),
        )
        // A *higher* version of a feature this build implements is not satisfied, and is preserved.
        assertRow(PRESERVED, optional = listOf("org.mobixournal/audio.opus:3"), unknownOptional = listOf("org.mobixournal/audio.opus:3"))
    }

    @Test
    fun anUnsatisfiedRequirementRefusesToWrite() {
        // A capability this build implements, required at a version it does not reach.
        assertRow(REFUSE_WRITE, required = listOf("org.mobixournal/audio.opus:3"), unsatisfied = listOf("org.mobixournal/audio.opus:3"))
        // A feature from a foreign namespace this build has never heard of.
        assertRow(REFUSE_WRITE, required = listOf("example.org/lab.notebook:1"), unsatisfied = listOf("example.org/lab.notebook:1"))
        // A different feature is never satisfied by another one's version, however high it is.
        assertRow(REFUSE_WRITE, required = listOf("org.mobixournal/audio:9"), unsatisfied = listOf("org.mobixournal/audio:9"))
        // A refused requirement turns the optional list into no promise at all: nothing is preserved, so
        // nothing is reported as preserved either.
        assertRow(
            REFUSE_WRITE,
            required = listOf("example.org/lab.notebook:1"),
            optional = listOf("example.org/future.notes:1"),
            unsatisfied = listOf("example.org/lab.notebook:1"),
        )
    }

    @Test
    fun aRequirementOutranksEveryPermissiveFloor() {
        // The precedence rule that is easy to get backwards: a version number cannot talk the file out of
        // what it says it needs.
        assertRow(REFUSE_WRITE, required = listOf("example.org/lab.notebook:1"), minAppVersion = "1.0.0", unsatisfied = listOf("example.org/lab.notebook:1"))
        // …not even a floor high enough to be read-only on its own.
        assertRow(REFUSE_WRITE, required = listOf("example.org/lab.notebook:1"), minAppVersion = "9.0.0", unsatisfied = listOf("example.org/lab.notebook:1"))
        // And the same capability written in both lists is one refusal, not two: `required` is the floor,
        // and nothing in the optional list is promised once the file may not be written.
        assertRow(
            REFUSE_WRITE,
            required = listOf("example.org/lab.notebook:1"),
            optional = listOf("example.org/lab.notebook:1"),
            unsatisfied = listOf("example.org/lab.notebook:1"),
        )
    }

    @Test
    fun severalUnknownRequirementsNameAllOfThemInTheWritersOrder() {
        assertRow(
            REFUSE_WRITE,
            required = listOf("example.org/a:1", "example.org/b:2", "org.mobixournal/stem.connector:1"),
            unsatisfied = listOf("example.org/a:1", "example.org/b:2"),
        )
        assertRow(
            PRESERVED,
            optional = listOf("example.org/a:1", "example.org/b:2"),
            unknownOptional = listOf("example.org/a:1", "example.org/b:2"),
        )
    }

    private companion object {
        /** This build's version in the matrix — above every floor stated as satisfied, below "9.0.0". */
        const val APP_VERSION = "2.0.0"

        const val NOW = 1_790_000_000_000L
    }
}
