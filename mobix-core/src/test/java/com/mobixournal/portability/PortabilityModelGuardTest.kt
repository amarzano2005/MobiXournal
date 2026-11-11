package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classification cannot silently fall behind the model
 * (`docs/mobix-elements.md` → *Text*, `docs/mobix-assets.md` → *Is it inside the file?*).
 *
 * A new payload kind or a new field that can name bytes is one commit away from being invisible to
 * `PortabilityAudit` — and the failure is silent: the report simply says every document is complete. So
 * the audit's coverage is written down here as data, and the test reads the model classes back through
 * their declared fields rather than trusting a list somebody remembered to update.
 *
 * Adding one is a decision, not a fix: classify the field (and the audit reads it), or list it among the
 * hints below and say why it names no bytes.
 */
class PortabilityModelGuardTest {

    /**
     * Every field this audit classifies, per model type. A name here means "the audit reads it and says
     * whether the bytes are inside the document".
     */
    private val classified: Map<Class<*>, Set<String>> = mapOf(
        Background.Pdf::class.java to setOf("filename", "bundledAsset"),
        Background.Pixmap::class.java to setOf("filename", "bundledAsset"),
        Stroke::class.java to setOf("bundledAudio"),
        ImageElement::class.java to setOf("data"),
        TexImageElement::class.java to setOf("data"),
    )

    /** Fields that look like a payload but are deliberately not one, with the rule that says so. */
    private val hints: Map<Class<*>, Map<String, String>> = mapOf(
        TextElement::class.java to mapOf(
            "font" to "a face name, not bytes: the hint rule in docs/mobix-elements.md",
        ),
    )

    /**
     * The field names a classification has to cover: anything that could name a payload. Kept to names a
     * payload could plausibly take — `data`, `filename`, `…asset`, `bundled…` — so an unrelated field
     * (`color`, `content`) does not have to be listed.
     */
    private fun looksLikeAPayload(field: String): Boolean =
        field == "data" || field == "filename" || field == "audioRef" ||
            field.contains("asset", ignoreCase = true) || field.startsWith("bundled")

    private fun declaredPayloadFields(type: Class<*>): Set<String> =
        type.declaredFields.map { it.name }.filter(::looksLikeAPayload).toSet()

    @Test
    fun everyAssetKindIsClassified() {
        assertEquals(
            "a new AssetKind lands in the format's vocabulary, docs/mobix-assets.md's table and the " +
                "portability classification in the same change; MobixFormat.AssetKind gained a kind " +
                "this test does not know",
            setOf("IMAGE", "AUDIO"),
            MobixFormat.AssetKind.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun everyFieldThatCanNameAPayloadIsClassified() {
        // The classified fields and the documented hints together are what a payload-naming field may be.
        val known: Map<Class<*>, Set<String>> =
            classified.mapValues { it.value } + hints.mapValues { it.value.keys }
        val gaps = mutableListOf<String>()
        for ((type, fields) in known) {
            for (field in declaredPayloadFields(type)) {
                if (field !in fields) gaps += "${type.simpleName}.$field"
            }
        }
        assertTrue(
            "these fields can name bytes and neither the audit nor a documented hint covers them: " +
                "$gaps — classify them in PortabilityAudit and add them to `classified`, or say here " +
                "why they name no payload",
            gaps.isEmpty(),
        )
    }

    @Test
    fun theFamilyThatCarriesAudioThroughAttributesIsStillReachable() {
        // A text box names a recording through its `.xopp` `attrs` (`fn`), not through a field of its own:
        // the audit reads `extraAttrs`, which is why TextElement needs no asset field to be classified.
        assertTrue(
            "TextElement must keep carrying the .xopp attributes the audit reads `fn` from",
            TextElement::class.java.declaredFields.any { it.name == "extraAttrs" },
        )
    }

    @Test
    fun aFontIsAHintAndNotAReference() {
        assertTrue(
            "the report must not emit a FONT entry for every page of text; the hint rule is stated in " +
                "docs/mobix-elements.md and the audit's own comment",
            hints.getValue(TextElement::class.java).containsKey("font"),
        )
        assertTrue(
            "no model type may carry bundled font bytes without a classification for them",
            declaredPayloadFields(TextElement::class.java).isEmpty(),
        )
    }
}
