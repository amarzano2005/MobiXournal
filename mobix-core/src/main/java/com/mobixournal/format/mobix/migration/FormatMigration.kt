package com.mobixournal.format.mobix.migration

/**
 * One planned migration between two `formatVersion`s: what the source must satisfy, the ordered steps,
 * a checkpoint after each, and whether the result can be undone.
 *
 * ## The rule this page owns: when `formatVersion` may be bumped
 *
 * A bump is legal only for a change an older reader **cannot preserve**:
 *
 * - a renamed or removed required field;
 * - a change to entry naming (a part's location, or the `assets/` grammar);
 * - a compression baseline that v1 readers reject;
 * - a change to what an existing element family **means**, so the same bytes would be read differently.
 *
 * None of these qualifies: an additive optional field, a new element family, a new asset kind, a derived
 * entry such as an index, or a writer upgrade. Version 1's preserve rules already cover the unknown ones
 * — a reader keeps what it does not model — and [com.mobixournal.format.mobix.MobixMigrations] is where
 * a bump's actual step has to be registered. `MobixMigrationsTest` keeps that registry and
 * `MobixManifest.CURRENT_FORMAT_VERSION` in step, and this model refuses to describe a bump nobody wrote.
 *
 * A plan **never runs itself and never touches a file**: it is data a caller inspects first. Execution,
 * a backup, and the replacement that publishes the result belong to the save path, not here.
 *
 * @property fromVersion the `formatVersion` this plan reads (never equal to [toVersion]).
 * @property toVersion the `formatVersion` it produces, one or more steps later.
 * @property preconditions what the source must declare, or must not contain, before the plan is usable.
 * @property steps the transformations in order; exactly one per version crossed.
 * @property revertible whether every step can be undone, i.e. whether a save may write the older version
 *   back. A migration with no inverse is a one-way trip in a file format that promises round trips.
 */
data class FormatMigration(
    val fromVersion: Int,
    val toVersion: Int,
    val preconditions: List<Precondition>,
    val steps: List<Step>,
    val revertible: Boolean,
) {

    init {
        require(toVersion > fromVersion) {
            "a migration must cross at least one version: $fromVersion -> $toVersion is not a plan"
        }
        require(steps.size == toVersion - fromVersion) {
            "a plan from $fromVersion to $toVersion needs ${toVersion - fromVersion} step(s), " +
                "not ${steps.size}: a version nobody wrote a step for is a bump, not a migration"
        }
        require(steps.map { it.toVersion } == (fromVersion + 1..toVersion).toList()) {
            "the steps must produce ${fromVersion + 1}..$toVersion in order, got " +
                steps.map { it.toVersion }
        }
        require(steps.all { it.checkpoint.version == it.toVersion }) {
            "every step is followed by its own checkpoint: a checkpoint naming another version would " +
                "validate a state the step did not produce"
        }
    }

    /**
     * Something the source has to satisfy. Each unmet precondition becomes **one diagnostic naming it**,
     * not an exception: a file that cannot migrate is a normal outcome of opening an old document, and
     * the caller needs the reason to show or log.
     */
    sealed interface Precondition {

        /** The source must declare this capability (`<namespace>/<feature>:<version>`). */
        data class Capability(val id: String) : Precondition

        /** The source must not contain this element family (or other named construct). */
        data class Construct(val family: String) : Precondition
    }

    /** One version crossing, with the checkpoint that proves it landed. */
    data class Step(
        val toVersion: Int,
        val description: String,
        val checkpoint: Checkpoint,
    )

    /** What must hold after [version]: the document reopens, its digests match, its capabilities parse. */
    data class Checkpoint(val version: Int, val requirement: String)

    /**
     * What a source document declares, as far as preconditions are concerned. Deliberately values rather
     * than a `MobixDocument`: a plan can then be reasoned about, and tested, without opening a file.
     */
    data class Evidence(
        val capabilities: Set<String> = emptySet(),
        val constructs: Set<String> = emptySet(),
    )

    /** One refused precondition, with the sentence a caller can show. */
    data class Diagnostic(val precondition: Precondition, val message: String)

    /** One diagnostic per unmet precondition; an empty list means the plan may be applied. */
    fun diagnostics(evidence: Evidence): List<Diagnostic> = preconditions.mapNotNull { precondition ->
        when (precondition) {
            is Precondition.Capability ->
                if (precondition.id in evidence.capabilities) {
                    null
                } else {
                    Diagnostic(
                        precondition,
                        "the source does not declare capability ${precondition.id}, which this " +
                            "migration needs",
                    )
                }

            is Precondition.Construct ->
                if (precondition.family in evidence.constructs) {
                    Diagnostic(
                        precondition,
                        "the source contains ${precondition.family}, which this migration cannot " +
                            "carry across",
                    )
                } else {
                    null
                }
        }
    }
}
