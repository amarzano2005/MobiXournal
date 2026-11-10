package com.mobixournal.format.mobix

/**
 * The one answer a reader gives about a file whose declared capabilities it compared with its own
 * (`docs/mobix-schema-spec.md`, *Declared capabilities*).
 *
 * The ordering of the constants is the contract's **precedence**, strictest first: a document that
 * cannot be written must not be described as merely read-only, and one that cannot be drawn must not be
 * described as editable.
 */
enum class MobixOpenOutcome {
    /** A required capability this build can neither satisfy nor keep: the pages may be looked at, nothing written. */
    REFUSE_WRITE,

    /** Every page can be shown, but nothing may be written: the build cannot keep the document intact. */
    READ_ONLY,

    /** Editable elsewhere; the constructs this build does not implement are carried through untouched. */
    PRESERVED,

    /** Nothing this build does not understand is involved: the document is edited and saved normally. */
    FULL_EDIT,
}

/**
 * What the open may do with a document, plus the capabilities that decided it — so the UI presents the
 * gate's answer instead of re-deriving one from the manifest ([MobixCapabilities.evaluate] is the only
 * place that comparison happens).
 *
 * [reason] is written for a person: it names the features and says what is disabled. It is English copy,
 * never a class name, and the shells show it as it is.
 */
data class MobixOpenVerdict(
    val outcome: MobixOpenOutcome,

    /** The required capabilities this build does not implement; empty for [MobixOpenOutcome.FULL_EDIT]. */
    val unsatisfiedRequired: List<MobixCapability> = emptyList(),

    /**
     * The optional capabilities this build does not implement; the constructs they name are preserved.
     *
     * Always empty for [MobixOpenOutcome.REFUSE_WRITE]: a document that may not be written back keeps
     * nothing, so reporting an optional construct as preserved there would be a promise the outcome has
     * already broken.
     */
    val unknownOptional: List<MobixCapability> = emptyList(),

    /** What decided the outcome, for the notice [reason] summarises: a version, or a capability. */
    val because: String? = null,
) {

    /** Whether this verdict lets the document be written at all. */
    val mayWrite: Boolean
        get() = outcome == MobixOpenOutcome.FULL_EDIT || outcome == MobixOpenOutcome.PRESERVED

    /**
     * Whether this verdict lets a page be changed.
     *
     * The same answer as [mayWrite], because a document whose unmodelled constructs are preserved *is*
     * editable everywhere else: what this build must not do is rewrite those constructs, and the
     * retention channel enforces that rather than a disabled canvas. Only [MobixOpenOutcome.READ_ONLY]
     * and [MobixOpenOutcome.REFUSE_WRITE] turn editing off.
     */
    val mayEdit: Boolean
        get() = outcome == MobixOpenOutcome.FULL_EDIT || outcome == MobixOpenOutcome.PRESERVED

    /** The sentence a notice shows: what the file needs, and what this build therefore does not allow. */
    val reason: String
        get() = when (outcome) {
            MobixOpenOutcome.FULL_EDIT -> "This notebook needs nothing this build cannot do."

            MobixOpenOutcome.PRESERVED -> buildString {
                append("This notebook uses ")
                append(listOf(unsatisfiedRequired, unknownOptional).flatten().joined())
                append(" which this build does not implement; those elements are kept as they are. ")
                append("Everything else can be edited and saved.")
            }

            MobixOpenOutcome.READ_ONLY -> buildString {
                append("This notebook was written for ")
                append(because?.takeIf { it.isNotBlank() } ?: "a newer build")
                append(". It opens read-only: drawing, Save and Save As are unavailable, ")
                append("so nothing on the page can be lost.")
            }

            MobixOpenOutcome.REFUSE_WRITE -> buildString {
                append("This notebook requires ")
                append(unsatisfiedRequired.joined())
                append(", which this build cannot keep intact. It opens for reading only: ")
                append("nothing may be drawn, saved or exported over it.")
            }
        }

    private fun List<MobixCapability>.joined(): String = joinToString(", ") { it.toString() }
}

/**
 * The gate: what this build implements compared with what a file declares. The comparison lives here and
 * nowhere else, so the notice a shell shows and the state it disables cannot drift apart.
 */
object MobixCapabilities {

    /**
     * Compare [manifest]'s declaration with [satisfied] — the capabilities this build implements — and
     * [appVersion].
     *
     * Precedence is the contract's, strictest first:
     *
     *  1. a required capability this build does not satisfy → [MobixOpenOutcome.REFUSE_WRITE]. `required`
     *     is a **write floor**: a writer that marks a capability required is saying the document cannot
     *     be written correctly without understanding it, so keeping the bytes is not enough.
     *  2. a `minAppVersion` above this build → [MobixOpenOutcome.READ_ONLY];
     *  3. an optional capability this build does not satisfy → [MobixOpenOutcome.PRESERVED];
     *  4. otherwise [MobixOpenOutcome.FULL_EDIT].
     *
     * An unknown required capability always beats a permissive `minAppVersion` — a version number cannot
     * talk the file out of what it says it needs — and among several unknown requirements the strictest
     * wins, because refusing to write is never weakened by another capability being understood.
     *
     * Step 3 is only honest because of the retention rule (`docs/mobix-elements.md`): every family the
     * format models keeps what it did not read, and an element type with no model is kept whole. A build
     * without that rule would have to answer [MobixOpenOutcome.READ_ONLY] here instead, since it cannot
     * hand back a construct it would have rewritten.
     */
    fun evaluate(
        manifest: MobixManifest,
        satisfied: Set<MobixCapability>,
        appVersion: String,
    ): MobixOpenVerdict {
        val missingRequired = manifest.requiredCapabilities.filterNot { it.satisfies(satisfied) }
        val unknownOptional = manifest.optionalCapabilities.filterNot { it.satisfies(satisfied) }

        if (missingRequired.isNotEmpty()) {
            return MobixOpenVerdict(MobixOpenOutcome.REFUSE_WRITE, unsatisfiedRequired = missingRequired)
        }
        if (manifest.requiresAppNewerThan(appVersion)) {
            return MobixOpenVerdict(MobixOpenOutcome.READ_ONLY, because = manifest.minAppVersion)
        }
        if (unknownOptional.isNotEmpty()) {
            return MobixOpenVerdict(MobixOpenOutcome.PRESERVED, unknownOptional = unknownOptional)
        }
        return MobixOpenVerdict(MobixOpenOutcome.FULL_EDIT)
    }
}
