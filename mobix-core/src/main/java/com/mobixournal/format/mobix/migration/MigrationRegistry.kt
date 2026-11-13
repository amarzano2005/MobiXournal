package com.mobixournal.format.mobix.migration

/**
 * The migrations this build knows, and the plan between two `formatVersion`s.
 *
 * The registry does not invent a path. With `formatVersion` at 1 and nothing registered, [CURRENT] holds
 * no migration and [plan] answers null for every pair — which is the honest answer, not a gap: there is
 * nothing to migrate today. A pair that no chain covers is null for the same reason, and a caller must
 * treat null as "this build cannot read that source at this version" rather than as "no work to do".
 *
 * A chain composes: registrations 1 → 2 and 2 → 3 make [plan] answer a 1 → 3 plan whose steps and
 * preconditions are the union, and whose [FormatMigration.revertible] is true only when every hop is.
 */
class MigrationRegistry(migrations: List<FormatMigration> = emptyList()) {

    private val byFrom: Map<Int, FormatMigration>

    init {
        val sorted = migrations.sortedBy { it.fromVersion }
        val duplicated = sorted.groupBy { it.fromVersion }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) {
            "two migrations start at formatVersion(s) $duplicated: one hop out of a version is all a " +
                "reader can choose between"
        }
        val overlapping = sorted.zipWithNext().filter { (earlier, later) -> later.fromVersion < earlier.toVersion }
        require(overlapping.isEmpty()) {
            "overlapping migrations: " + overlapping.joinToString { (a, b) ->
                "${a.fromVersion}->${a.toVersion} and ${b.fromVersion}->${b.toVersion}"
            }
        }
        byFrom = sorted.associateBy { it.fromVersion }
    }

    /** Every registered migration, oldest first. Empty while the format is at its baseline. */
    val migrations: List<FormatMigration> get() = byFrom.values.toList()

    /**
     * The plan from [fromVersion] to [toVersion], or null when no chain exists.
     *
     * Null covers the three cases a caller has to keep apart by asking more questions: the versions are
     * equal (nothing to do), a version is lower than the source (a downgrade this registry does not
     * model), or no registered migration reaches the target (a bump nobody wrote).
     */
    fun plan(fromVersion: Int, toVersion: Int): FormatMigration? {
        if (toVersion <= fromVersion) return null
        val chosen = ArrayList<FormatMigration>()
        var version = fromVersion
        while (version < toVersion) {
            val next = byFrom[version] ?: return null
            if (next.toVersion > toVersion) return null
            chosen += next
            version = next.toVersion
        }
        if (chosen.size == 1) return chosen.single()
        return FormatMigration(
            fromVersion = fromVersion,
            toVersion = toVersion,
            preconditions = chosen.flatMap { it.preconditions }.distinct(),
            steps = chosen.flatMap { it.steps },
            revertible = chosen.all { it.revertible },
        )
    }

    companion object {

        /** What this build ships: no migration, because `formatVersion` is at its baseline. */
        val CURRENT: MigrationRegistry = MigrationRegistry()
    }
}
