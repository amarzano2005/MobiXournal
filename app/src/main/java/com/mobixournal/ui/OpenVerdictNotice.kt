package com.mobixournal.ui

import com.mobixournal.format.mobix.MobixOpenOutcome
import com.mobixournal.format.mobix.MobixOpenVerdict

/**
 * What an open verdict asks a shell to show (`docs/mobix-schema-spec.md`, *Declared capabilities*), as
 * one value: the notice's copy, whether it is a card to answer or a line to read, and whether the
 * document may be written.
 *
 * The decision lives here — not in a composable and not in the activity — because the notice and the
 * actions it disables are two readings of the same verdict. A copy that promises something the state
 * does not do is then a test failure, not a bug report that arrives a week later.
 */
data class OpenVerdictNotice(
    /** The document's name, for a card that names what it is talking about. */
    val name: String,

    /** The card's heading; empty for [asksForAnswer] false, which has no heading to show. */
    val title: String,

    /** What the file needs and what this build therefore does — the verdict's own sentence. */
    val body: String,

    /** The card's confirming action; empty when there is no card. */
    val confirmLabel: String,

    /** A card the reader answers, or a line they read while the document opens. */
    val asksForAnswer: Boolean,

    /** Whether the document may be written back: false for both outcomes that disable saving. */
    val readOnly: Boolean,
)

/**
 * [verdict]'s notice, or null when the document opens for editing with nothing to say.
 *
 * Both outcomes that disable writing get a card, because both are a decision the reader takes part in
 * ("open it anyway, read-only" or "open it for reading" rather than "refuse"). The outcome that only
 * *preserves* gets a line, because the document is fully editable and a dialog would imply otherwise.
 */
fun openVerdictNotice(name: String, verdict: MobixOpenVerdict): OpenVerdictNotice? =
    when (verdict.outcome) {
        MobixOpenOutcome.FULL_EDIT -> null

        MobixOpenOutcome.PRESERVED -> OpenVerdictNotice(
            name = name,
            title = "",
            body = verdict.reason,
            confirmLabel = "",
            asksForAnswer = false,
            readOnly = false,
        )

        MobixOpenOutcome.READ_ONLY -> OpenVerdictNotice(
            name = name,
            title = "Newer version document",
            body = verdict.reason,
            confirmLabel = "Open read-only",
            asksForAnswer = true,
            readOnly = true,
        )

        MobixOpenOutcome.REFUSE_WRITE -> OpenVerdictNotice(
            name = name,
            title = "Unsupported features",
            body = verdict.reason,
            confirmLabel = "Open for reading",
            asksForAnswer = true,
            readOnly = true,
        )
    }
