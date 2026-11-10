package com.mobixournal.format.mobix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capability identifier (`docs/mobix-schema-spec.md`): the grammar, and the one comparison rule the
 * contract states — a later version of the same feature satisfies an earlier one, and nothing else does.
 *
 * The grammar is checked strictly on purpose. An identifier that does not parse is a corrupt
 * declaration rather than a capability from the future: a reader cannot tell a typo from an unknown
 * feature, and treating a typo as "unknown" would silently drop the file's own requirement.
 */
class MobixCapabilityTest {

    @Test
    fun theCanonicalSpellingParsesFieldForField() {
        val capability = MobixCapability.parse("org.mobixournal/stem.connector:1")

        assertEquals("org.mobixournal", capability!!.namespace)
        assertEquals("stem.connector", capability.feature)
        assertEquals(1, capability.version)
        assertEquals("and writes back the spelling it read", "org.mobixournal/stem.connector:1", capability.toString())
    }

    @Test
    fun aDashAndASingleLabelNamespaceAreBothAllowed() {
        assertEquals("org.example-app/paper-ruling:12", MobixCapability.parse("org.example-app/paper-ruling:12").toString())
        assertEquals("org.example/audio:3", MobixCapability.parse("org.example/audio:3")!!.toString())
    }

    @Test
    fun anythingOutsideTheGrammarIsRefused() {
        val refused = listOf(
            "Stem/Connector",          // capitalised
            "org.mobixournal/Stem:1",  // capitalised feature
            "org.mobixournal/stem:0",  // version must be positive
            "org.mobixournal/stem:-1",
            "org.mobixournal/stem:1.5",
            "org.mobixournal/stem",    // no version
            "x",                       // no namespace, no version
            "a/b/c:1",                 // a slash inside the feature
            "org.mobixournal/:1",      // no feature
            "/stem:1",                 // no namespace
            "org.mobixournal/stem: 1", // whitespace is not part of the grammar
            "org.mobixournal/stem.a..b:1",
            "org..mobixournal/stem:1",
        )

        for (text in refused) assertNull("\"$text\" is not an identifier", MobixCapability.parse(text))
    }

    @Test
    fun aLaterVersionSatisfiesAnEarlierOne() {
        val v1 = MobixCapability.parse("org.mobixournal/stem.connector:1")!!
        val v2 = MobixCapability.parse("org.mobixournal/stem.connector:2")!!

        assertTrue("2 handles what 1 promised", v1.satisfies(setOf(v2)))
        assertTrue("a build satisfies the version it implements", v1.satisfies(setOf(v1)))
        assertFalse("1 must not claim 2", v2.satisfies(setOf(v1)))
        assertFalse("an older version is not a superset", v2.satisfies(setOf(v1, v1)))
    }

    @Test
    fun aDifferentFeatureOrNamespaceNeverMatchesHoweverHighItsVersion() {
        val wanted = MobixCapability.parse("org.mobixournal/audio.opus:2")!!

        assertFalse(wanted.satisfies(setOf(MobixCapability.parse("org.mobixournal/audio.flac:9")!!)))
        assertFalse(wanted.satisfies(setOf(MobixCapability.parse("com.example/audio.opus:9")!!)))
        assertFalse("nothing at all is not a match", wanted.satisfies(emptySet()))
    }
}
