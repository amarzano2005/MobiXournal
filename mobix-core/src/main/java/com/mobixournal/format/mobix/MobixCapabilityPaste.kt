package com.mobixournal.format.mobix

/**
 * What pasting a selection into a document does to **that document's own declaration**
 * (`docs/mobix-schema-spec.md`, *Cross-cutting*).
 *
 * A paste is where a capability rule is easiest to get wrong in the quiet way: the elements arrive,
 * the page shows them, and the save writes a file that no longer says what it holds — so the next
 * older reader trims exactly what the paste added. The rule is therefore stated as a decision the
 * caller cannot skip: the destination's set **grows** to include what the pasted elements need, or the
 * paste is refused with a sentence naming the feature.
 */
data class MobixPasteVerdict(
    /** Whether the elements may be pasted at all. */
    val accepted: Boolean,

    /**
     * The destination's `requiredCapabilities` after the paste — the union of what it already declared
     * and what the selection needs. Unchanged when the paste is refused: a refused paste adds nothing,
     * so it claims nothing.
     */
    val requiredCapabilities: List<MobixCapability>,

    /** The capability the paste was refused for, or null when it was accepted. */
    val refusedBecause: MobixCapability? = null,
) {

    /**
     * The sentence a shell shows when [accepted] is false: what the selection needs and what that means
     * for the user. English copy, naming the feature and never a class.
     */
    val reason: String
        get() = if (accepted) {
            ""
        } else {
            "This selection needs $refusedBecause, which this build cannot keep intact. " +
                "It was not pasted: pasting it here would save a page without it."
        }
}

/**
 * The paste half of the capability contract. One decision per paste, computed from three sets and
 * nothing else — no manifest, no UI — so both shells reach the same answer and a test can ask what a
 * paste does to a declaration without building a document.
 */
object MobixCapabilityPaste {

    /**
     * Decide a paste of elements that need [incoming] into a document whose own requirement is
     * [destination].
     *
     * The elements a build can draw are not the question: a paste is refused when this build cannot keep
     * a capability the selection carries, because accepting it would produce a page the save cannot
     * represent. When every incoming capability is satisfied the paste is accepted and the destination's
     * set becomes the [unionCapabilities] of the two — the destination's order first, a higher version of
     * one feature replacing a lower one. A selection that declares nothing (every v1 element) leaves the
     * destination exactly as it was.
     *
     * @param satisfied what this build implements — `MobixFormat.declaredCapabilities` in production.
     */
    fun decide(
        destination: List<MobixCapability>,
        incoming: List<MobixCapability>,
        satisfied: Iterable<MobixCapability> = MobixFormat.declaredCapabilities,
    ): MobixPasteVerdict {
        val missing = incoming.filterNot { it.satisfies(satisfied) }
        if (missing.isNotEmpty()) {
            return MobixPasteVerdict(accepted = false, requiredCapabilities = destination, refusedBecause = missing.first())
        }
        return MobixPasteVerdict(
            accepted = true,
            requiredCapabilities = unionCapabilities(destination, incoming),
        )
    }
}
