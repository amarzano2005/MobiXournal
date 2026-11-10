package com.mobixournal.format.json

import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixManifest
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nesting cap (`docs/mobix-format.md`, *What a reader must refuse*): a deeply nested file must be
 * refused as a document, not end the process with a `StackOverflowError`.
 *
 * The parser recurses once per level, so the cap is what stands between a 20 kB download of `{`
 * characters and a dead app; the document boundaries are where that cap becomes this format's own
 * error, so both halves are asserted here.
 */
class JsonDepthLimitTest {

    /** [levels] nested objects around a number, e.g. `{"a":{"a":1}}` for 2. */
    private fun nested(levels: Int): String = "{\"a\":".repeat(levels) + "1" + "}".repeat(levels)

    @Test
    fun aDocumentAtTheCapParses() {
        assertTrue(
            "the deepest legal document still parses",
            JsonParser.parse(nested(JsonParser.MAX_DEPTH)) is JsonNode.Obj,
        )
    }

    @Test
    fun oneLevelPastTheCapIsRefused() {
        val failure = assertThrows(JsonParser.TooDeepException::class.java) {
            JsonParser.parse(nested(JsonParser.MAX_DEPTH + 1))
        }
        assertTrue(
            "the message says how deep the limit is",
            JsonParser.MAX_DEPTH.toString() in failure.message.orEmpty(),
        )
    }

    @Test
    fun aManifestThatNestsTooDeepIsARejectedDocument() {
        val deep = "{\"formatVersion\":1,\"documentId\":\"d\",\"canvasMode\":\"PAGINATED\"," +
            "\"future\":" + nested(JsonParser.MAX_DEPTH) + "}"

        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixManifest.decode(deep)
        }

        assertTrue(
            "the refusal names the part it happened in: ${failure.message}",
            "manifest.json" in failure.message.orEmpty(),
        )
        assertTrue(
            "and keeps the parser's own exception as the cause",
            failure.cause is JsonParser.TooDeepException,
        )
    }

    @Test
    fun aShallowUnknownValueIsStillAccepted() {
        val manifest = MobixManifest.decode(
            """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED","future":{"a":{"b":1}}}""",
        )

        assertTrue("an additive nested value is not a hostile file", manifest.extra.containsKey("future"))
    }
}
