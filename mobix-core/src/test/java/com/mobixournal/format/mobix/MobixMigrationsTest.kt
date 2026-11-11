package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migration seam (`MobixMigrations`): what a bump of `MobixManifest.CURRENT_FORMAT_VERSION` owes.
 *
 * The format is at version 1, so nothing migrates yet — and that is the state this test pins. An empty
 * registry is only honest while something notices it staying empty: the first assertion fails the moment
 * the constant moves without a step, which is the one mistake that would make every earlier file
 * unreadable. The rest exercises the machinery on a step written here, because a seam nothing runs is a
 * seam nobody knows works.
 */
class MobixMigrationsTest {

    private val sample = """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED"}"""

    private fun manifest(text: String): JsonNode.Obj =
        MobixJson.parseJson(text, "manifest") as JsonNode.Obj

    private fun textOf(node: JsonNode): String = JsonWriter.write(node)

    @Test
    fun theHighestRegisteredStepIsTheVersionThisBuildWrites() {
        assertEquals(
            "bumping MobixManifest.CURRENT_FORMAT_VERSION without registering a step is a migration " +
                "nobody wrote: every file from before the bump would be read by a reader that does not " +
                "know its shape",
            MobixManifest.CURRENT_FORMAT_VERSION,
            MobixMigrations.highestVersion(),
        )
        assertTrue(
            "the format is at its baseline, so the registry is empty on purpose: a step is written when " +
                "a version is, never to have something to test",
            MobixMigrations.steps.isEmpty(),
        )
    }

    @Test
    fun aDocumentAtTheCurrentVersionPassesThroughUnchanged() {
        assertEquals(
            "a document this build wrote is not migrated, in either direction",
            sample,
            textOf(MobixMigrations.migrateForward(manifest(sample), 1)),
        )
        assertEquals(sample, textOf(MobixMigrations.migrateBackward(manifest(sample), 1)))
    }

    @Test
    fun aVersionNewerThanThisBuildIsRefusedByName() {
        val newer = """{"formatVersion":2,"documentId":"d","canvasMode":"PAGINATED"}"""

        val failure = assertThrows(IllegalArgumentException::class.java) {
            MobixMigrations.migrateForward(manifest(newer), 2)
        }
        assertTrue(failure.message.orEmpty(), "formatVersion 2" in failure.message.orEmpty())
        assertTrue(
            "the refusal says which version it cannot read, so the reader learns what happened",
            "newer" in failure.message.orEmpty(),
        )
        assertEquals(
            "and the node is untouched: a refusal is not a partial migration",
            newer,
            textOf(manifest(newer)),
        )
    }

    @Test
    fun aStepAndItsInverseAreTheIdentityOnTheSampleTheyWereWrittenFor() {
        val step = MobixMigrations.Step(
            fromVersion = 1,
            upgrade = { node ->
                val map = LinkedHashMap<String, JsonNode>(node.map)
                map["title"] = JsonNode.of("migrated")
                JsonNode.Obj(map)
            },
            downgrade = { node ->
                val map = LinkedHashMap<String, JsonNode>(node.map)
                map.remove("title")
                JsonNode.Obj(map)
            },
        )
        val registered = listOf(step)

        val upgraded = MobixMigrations.migrateForward(manifest(sample), 1, toVersion = 2, registered = registered)
        assertTrue(
            "the step ran: its own field is in the upgraded manifest",
            "\"title\":\"migrated\"" in textOf(upgraded),
        )
        assertEquals(
            "a step with no inverse is a one-way trip in a format that promises round trips",
            sample,
            textOf(MobixMigrations.migrateBackward(upgraded, 2, targetVersion = 1, registered = registered)),
        )
    }

    @Test
    fun aVersionWithNoRegisteredStepIsRefusedRatherThanSkipped() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MobixMigrations.migrateForward(manifest(sample), 1, toVersion = 3)
        }
        assertTrue(
            "the message names the version the registry cannot reach, not a stacktrace: " +
                failure.message.orEmpty(),
            "formatVersion 3" in failure.message.orEmpty(),
        )
    }
}
