package com.mobixournal.format.mobix

import com.mobixournal.format.model.Layer
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures 13-17: the capability contract (`docs/mobix-schema-spec.md`, *Declared capabilities*) as
 * files, not as prose.
 *
 * Each one is named for the outcome the gate must produce, so a change in precedence has to change a
 * fixture's name too. The assertions check the **outcome** and the **shape** of the reason — the
 * capability identifier appears in it, and it never names a class — not the sentence itself, which the
 * shells own and reword freely.
 */
class MobixCapabilityFixturesTest {

    /** What this build implements: the v1 baseline, one feature at version 1 and two later ones. */
    private val satisfied: Set<MobixCapability> = setOf(
        MobixCapability.parse("org.mobixournal/stem.connector:1")!!,
        MobixCapability.parse("org.mobixournal/audio.opus:1")!!,
        MobixCapability.parse("org.mobixournal/paper.isometric:2")!!,
    )

    private fun bytes(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("fixtures/mobix-v1/$name.mobix")
            ?.use { it.readBytes() }
            ?: error("missing fixture fixtures/mobix-v1/$name.mobix")

    private fun archive(name: String): MobixContainer.Archive =
        MobixContainer.open(ByteArrayInputStream(bytes(name)))

    private fun manifest(name: String): MobixManifest =
        MobixManifest.decode(archive(name).require(MobixFormat.MANIFEST_ENTRY).bytes.toString(Charsets.UTF_8))

    private fun verdict(name: String, appVersion: String = "2.0.0"): MobixOpenVerdict =
        MobixCapabilities.evaluate(manifest(name), satisfied, appVersion)

    @Test
    fun aRequiredCapabilityThisBuildDoesNotImplementRefusesToWrite() {
        val verdict = verdict("13-required-capability-unknown")

        assertEquals(MobixOpenOutcome.REFUSE_WRITE, verdict.outcome)
        assertFalse("nothing may be written", verdict.mayWrite)
        assertFalse("and nothing may be edited", verdict.mayEdit)
        assertEquals(
            listOf("example.org/stem.connector:1"),
            verdict.unsatisfiedRequired.map { it.toString() },
        )
        assertTrue("the notice names the feature", "example.org/stem.connector:1" in verdict.reason)
        assertFalse("the notice is copy, not a class name", "MobixOpenVerdict" in verdict.reason)
        assertFalse("nor a Kotlin class in general", "MobixCapabil" in verdict.reason)
    }

    @Test
    fun anOptionalCapabilityThisBuildDoesNotImplementIsPreservedAndSaved() {
        val name = "14-optional-capability-unknown"
        val verdict = verdict(name)

        assertEquals(MobixOpenOutcome.PRESERVED, verdict.outcome)
        assertTrue("the document is still saved", verdict.mayWrite)
        assertTrue("editable everywhere: only the constructs this build cannot draw are left alone", verdict.mayEdit)
        assertTrue("the notice names the feature", "example.org/stem.connector:1" in verdict.reason)

        // The construct itself is kept: the save writes the element back as it arrived.
        val document = MobixDocument.open(archive(name))
        val element = document.pages.single().layers.flatMap(Layer::elements).single()
        assertTrue("an unmodelled element is kept whole", element is com.mobixournal.format.model.RawElement)
        val saved = MobixContainer.open(ByteArrayInputStream(document.toBytes(1_790_000_000_000L)))
        assertTrue(
            "and its own fields survive the save",
            "sin(x) * cos(y)" in saved.text("pages/page-0001.json")!!,
        )
    }

    @Test
    fun aRequiredVersionAboveTheDeclaredOneIsNotSatisfied() {
        val name = "15-capability-higher-version"
        assertEquals("the file everywhere", "org.mobixournal/stem.connector:2", manifest(name).requiredCapabilities.single().toString())

        val verdict = verdict(name)

        assertEquals(
            "a build that implements :1 must not claim a :2 requirement",
            MobixOpenOutcome.REFUSE_WRITE,
            verdict.outcome,
        )
        assertTrue("and says which version it is short of", "stem.connector:2" in verdict.reason)
    }

    @Test
    fun bothListsKeepTheirWrittenOrderAndNoCapabilityIsDuplicated() {
        val name = "16-capability-additive-order"
        val before = manifest(name)

        assertEquals(
            listOf("org.mobixournal/audio.opus:1", "example.org/stem.connector:1"),
            before.requiredCapabilities.map { it.toString() },
        )
        assertEquals(
            listOf("example.org/future.notes:3", "org.mobixournal/paper.isometric:2"),
            before.optionalCapabilities.map { it.toString() },
        )

        val saved = MobixManifest.decode(
            MobixContainer.open(ByteArrayInputStream(MobixDocument.open(archive(name)).toBytes(1_790_000_000_000L)))
                .require(MobixFormat.MANIFEST_ENTRY).bytes.toString(Charsets.UTF_8),
        )
        assertEquals("the order is the writer's, kept on save", before.requiredCapabilities, saved.requiredCapabilities)
        assertEquals(before.optionalCapabilities, saved.optionalCapabilities)
        assertTrue(
            "and neither list fell into the unknown-fields channel",
            saved.extra.keys.none { it.endsWith("Capabilities") },
        )
    }

    @Test
    fun aThirdPartyWriterWithoutAMinAppVersionIsReadable() {
        val name = "17-third-party-writer"
        val before = manifest(name)

        assertEquals("an absent floor means any app", "0.0.0", before.minAppVersion)
        assertEquals(
            listOf("com.example/lab.notebook:1"),
            before.optionalCapabilities.map { it.toString() },
        )
        assertEquals(
            "an unknown optional feature from a foreign namespace is preserved, not refused",
            MobixOpenOutcome.PRESERVED,
            verdict(name).outcome,
        )

        val savedArchive = MobixContainer.open(ByteArrayInputStream(MobixDocument.open(archive(name)).toBytes(1_790_000_000_000L)))
        val saved = MobixManifest.decode(savedArchive.require(MobixFormat.MANIFEST_ENTRY).bytes.toString(Charsets.UTF_8))
        assertEquals("a file that never declared a floor keeps a no-op one", "0.0.0", saved.minAppVersion)
        assertEquals(
            "the foreign capability is still declared",
            listOf("com.example/lab.notebook:1"),
            saved.optionalCapabilities.map { it.toString() },
        )
    }

    @Test
    fun aFileThatDeclaresNothingNeedsNothing() {
        val verdict = MobixCapabilities.evaluate(manifest("01-minimal-single-page"), satisfied, "2.0.0")

        assertEquals(MobixOpenOutcome.FULL_EDIT, verdict.outcome)
        assertTrue(verdict.mayWrite)
        assertTrue(verdict.mayEdit)
        assertTrue("no feature is named when none decided the outcome", verdict.unsatisfiedRequired.isEmpty())
    }
}
