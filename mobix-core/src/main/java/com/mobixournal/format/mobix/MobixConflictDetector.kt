package com.mobixournal.format.mobix

import java.util.regex.Pattern

/**
 * Kind of third-party sync service that produced a conflict file.
 */
enum class SyncClientKind {
    /** Syncthing conflict files (e.g. `notes.sync-conflict-20261012-143022-DEVICEID.mobix`). */
    SYNCTHING,

    /** Nextcloud conflict files (e.g. `notes (conflicted copy 2026-10-12 143022).mobix`). */
    NEXTCLOUD,
}

/**
 * Information extracted from a candidate conflict filename.
 *
 * @property baseName The inferred base document name without the conflict marker (e.g. `notes.mobix`).
 * @property client The detected sync client kind.
 * @property rawConflictMarker The specific conflict marker portion extracted from the filename.
 */
data class SyncConflictInfo(
    val baseName: String,
    val client: SyncClientKind,
    val rawConflictMarker: String,
)

/**
 * Pure filename pattern matcher for third-party sync engine conflict markers (`docs/sync-and-storage.md`).
 *
 * Recognises:
 * 1. **Syncthing**: `.sync-conflict-YYYYMMDD-HHMMSS-DEVICEID` injected before or after the file extension.
 *    - e.g. `document.sync-conflict-20261012-143022-7XABCD.mobix`
 *    - e.g. `document.mobix.sync-conflict-20261012-143022-7XABCD`
 * 2. **Nextcloud / ownCloud**: `(conflicted copy YYYY-MM-DD ...)` or `(conflicted copy ...)` inserted before extension.
 *    - e.g. `document (conflicted copy 2026-10-12 143022).mobix`
 *    - e.g. `document (conflicted copy user-tablet 2026-10-12).mobix`
 */
object MobixConflictDetector {

    // Syncthing regex: looks for .sync-conflict-YYYYMMDD-HHMMSS-<optional_tag>
    // Can appear either before extension (.sync-conflict-...\.mobix) or at the end of the file.
    private val SYNCTHING_BEFORE_EXT = Pattern.compile("""^(.*?)\.sync-conflict-\d{8}-\d{6}-[A-Za-z0-9_-]+(\.[^.]+)$""")
    private val SYNCTHING_AFTER_EXT = Pattern.compile("""^(.*?\.[^.]+)\.sync-conflict-\d{8}-\d{6}-[A-Za-z0-9_-]+$""")

    // Nextcloud regex: looks for " (conflicted copy ...)" before the extension
    private val NEXTCLOUD_BEFORE_EXT = Pattern.compile("""^(.*?)\s+\(conflicted copy[^)]*\)(\.[^.]+)$""", Pattern.CASE_INSENSITIVE)

    /**
     * Inspect [filename] and return [SyncConflictInfo] if it matches a known sync conflict pattern,
     * or null if it is a regular document name.
     */
    fun matchConflict(filename: String): SyncConflictInfo? {
        val stBefore = SYNCTHING_BEFORE_EXT.matcher(filename)
        if (stBefore.matches()) {
            val prefix = stBefore.group(1).orEmpty()
            val ext = stBefore.group(2).orEmpty()
            val base = prefix + ext
            val marker = filename.substring(prefix.length, filename.length - ext.length)
            return SyncConflictInfo(baseName = base, client = SyncClientKind.SYNCTHING, rawConflictMarker = marker)
        }

        val stAfter = SYNCTHING_AFTER_EXT.matcher(filename)
        if (stAfter.matches()) {
            val base = stAfter.group(1).orEmpty()
            val marker = filename.substring(base.length)
            return SyncConflictInfo(baseName = base, client = SyncClientKind.SYNCTHING, rawConflictMarker = marker)
        }

        val nc = NEXTCLOUD_BEFORE_EXT.matcher(filename)
        if (nc.matches()) {
            val prefix = nc.group(1).orEmpty()
            val ext = nc.group(2).orEmpty()
            val base = prefix + ext
            val marker = filename.substring(prefix.length, filename.length - ext.length).trim()
            return SyncConflictInfo(baseName = base, client = SyncClientKind.NEXTCLOUD, rawConflictMarker = marker)
        }

        return null
    }

    /**
     * Given the [canonicalName] of a `.mobix` document (e.g. `notes.mobix`), returns true if
     * [candidateFilename] is a conflict copy belonging to that document.
     */
    fun isConflictFor(canonicalName: String, candidateFilename: String): Boolean {
        val info = matchConflict(candidateFilename) ?: return false
        return info.baseName.equals(canonicalName, ignoreCase = true)
    }

    /**
     * Filter a collection of [siblingFilenames] in the same directory, returning only those that
     * are conflict files targeting [canonicalName].
     */
    fun findConflictSiblings(canonicalName: String, siblingFilenames: Collection<String>): List<String> =
        siblingFilenames.filter { isConflictFor(canonicalName, it) }
}
