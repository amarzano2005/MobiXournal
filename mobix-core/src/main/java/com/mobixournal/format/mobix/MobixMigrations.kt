package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode

/**
 * The registered format-version steps: the one place a change to `formatVersion` is allowed to land.
 *
 * `MobixManifest.CURRENT_FORMAT_VERSION` is a promise about the *shape* of a file. Bumping it without a
 * step that reads the older shape is how a format loses documents: the writer emits a new number, its own
 * reader parses it, and every file written before the bump is refused or — worse — half-read. So the
 * constant and this registry move together: [steps] is empty while the format is at its baseline, and
 * `MobixMigrationsTest` fails when the highest step and the constant disagree.
 *
 * A [Step] is deliberately a pair of functions rather than a version-specific class: it upgrades a raw
 * `manifest.json`, and it can undo itself. The inverse is not ceremony — it is what lets a document be
 * written back at a version an older build reads, and what a test asserts a step against, because a
 * migration with no inverse is a one-way trip in a file format that promises round trips.
 *
 * Nothing here invents a version to have something to migrate: with `formatVersion` at 1 the seam is
 * empty, and the value is that the seam exists and forgetting it fails the build.
 */
object MobixMigrations {

    /** The version a document at the format's baseline carries: the registry starts here. */
    const val BASE_VERSION: Int = 1

    /**
     * One step from [fromVersion] to `fromVersion + 1`.
     *
     * @param fromVersion the version this step reads.
     * @param upgrade the `manifest.json` of a document at [fromVersion], rewritten at `fromVersion + 1`.
     * @param downgrade the inverse: the `manifest.json` of a document at `fromVersion + 1`, rewritten at
     *   [fromVersion]. Registration asserts nothing about it — `MobixMigrationsTest` composes the two on a
     *   sample and requires the original back, because that is the only place the claim can be checked.
     */
    data class Step(
        val fromVersion: Int,
        val upgrade: (JsonNode.Obj) -> JsonNode.Obj,
        val downgrade: (JsonNode.Obj) -> JsonNode.Obj,
    ) {
        /** The version this step produces. */
        val toVersion: Int get() = fromVersion + 1
    }

    /**
     * Every registered step, oldest first. Empty while the format is at [BASE_VERSION], which is the
     * honest state of a first schema rather than a gap in the design.
     */
    val steps: List<Step> = emptyList()

    /** The version the registry can produce: the last registered step's, or [BASE_VERSION] when empty. */
    fun highestVersion(registered: List<Step> = steps): Int = registered.lastOrNull()?.toVersion ?: BASE_VERSION

    /**
     * [manifest] read at [fromVersion], upgraded to [toVersion] (this build's version by default).
     *
     * Refuses by name rather than guessing. A version newer than the target is neither half-read nor
     * silently downgraded, and a version the registry has no step for is a bump nobody wrote.
     *
     * @throws IllegalArgumentException when [toVersion] is past the last registered step, when
     *   [fromVersion] is newer than [toVersion], or when a step is missing on the way up.
     */
    fun migrateForward(
        manifest: JsonNode.Obj,
        fromVersion: Int,
        toVersion: Int = MobixManifest.CURRENT_FORMAT_VERSION,
        registered: List<Step> = steps,
    ): JsonNode.Obj {
        val highest = highestVersion(registered)
        require(toVersion <= highest) {
            "no registered step reaches formatVersion $toVersion: the highest this build knows is $highest"
        }
        if (fromVersion > toVersion) {
            throw IllegalArgumentException(
                "formatVersion $fromVersion is newer than supported $toVersion: this build has no step " +
                    "for it, so the document is refused rather than read as an older one",
            )
        }
        var version = fromVersion
        var node = manifest
        while (version < toVersion) {
            val step = registered.firstOrNull { it.fromVersion == version }
                ?: throw IllegalArgumentException("no registered step from formatVersion $version")
            node = step.upgrade(node)
            version = step.toVersion
        }
        return node
    }

    /**
     * [manifest] read at [fromVersion], written back at [targetVersion] — the direction a save uses when
     * the file has to stay readable by the build that wrote it.
     *
     * @throws IllegalArgumentException when a step is absent on the way down.
     */
    fun migrateBackward(
        manifest: JsonNode.Obj,
        fromVersion: Int,
        targetVersion: Int = BASE_VERSION,
        registered: List<Step> = steps,
    ): JsonNode.Obj {
        var version = fromVersion
        var node = manifest
        while (version > targetVersion) {
            val step = registered.firstOrNull { it.toVersion == version }
                ?: throw IllegalArgumentException(
                    "no registered step into formatVersion ${version - 1}: $version cannot be written " +
                        "back to $targetVersion without one",
                )
            node = step.downgrade(node)
            version = step.fromVersion
        }
        return node
    }
}
