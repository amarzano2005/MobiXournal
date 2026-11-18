package com.mobixournal.update

import com.mobixournal.design.ProjectLinks

/**
 * The same release, read **without the API** (`docs/updates.md`).
 *
 * `api.github.com` answers an anonymous request with `403` once the calling IP has spent sixty calls in the
 * hour, and a whole network can share one address — so a reader on an older build was told there was nothing
 * to install while 2.3.0 was on the release page. The refusal is now reported honestly, but reporting a
 * failure is still not an update; this is the second door to the same facts, and it costs no quota:
 *
 *  - `releases.atom` is a public feed, not the API. It lists published releases newest first, and each
 *    entry's link carries the tag.
 *  - the release page's own asset list (`releases/expanded_assets/<tag>`) names the files, which is what
 *    tells each shell which artifact is its own and which number the window carries.
 *
 * The feed cannot say whether a release is a **prerelease** — it lists what was published. The API is still
 * the primary source for exactly that reason; this path runs only after the API refused to answer, and the
 * project publishes no prerelease (`docs/releasing.md`, *Cutting a release*), so what it can offer is what the
 * release page shows. Every field is derived, never guessed: no asset of this shell's kind, no offer.
 */
object UpdateFallback {

    /** GitHub's published-releases feed: public, unauthenticated, and outside the API's hourly quota. */
    const val ATOM: String = ProjectLinks.GITHUB + "/releases.atom"

    /** The page that lists one release's files — the same list the release page shows when expanded. */
    fun assetsPageOf(tag: String): String = ProjectLinks.GITHUB + "/releases/expanded_assets/" + tag

    /** The newest tag in an atom [feed], or null when the feed names none. */
    fun tagIn(feed: String): String? =
        Regex("""/releases/tag/([A-Za-z0-9._+\-]+)""").find(feed)?.groupValues?.get(1)

    /** The file names an assets [page] lists, in the order the page shows them. */
    fun assetNamesIn(page: String): List<String> =
        Regex("""/releases/download/[^"'\s<>]+/([^"'/?#\s<>]+)""")
            .findAll(page)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    /**
     * The release [tag] describes, for [platform], as the shells read it — [assetNames] come from
     * [assetNamesIn]. Null when the release names no installer this shell could run, or when the tag holds
     * no number to compare: an unreadable page is not an invitation to install something.
     *
     * The size is not on the atom feed or the asset list, so it is reported as unknown (0); the download
     * reports its own progress from the response it gets, so nothing in either shell depends on it.
     */
    fun release(tag: String, assetNames: List<String>, platform: UpdatePlatform): UpdateRelease? {
        val asset = assetNames.firstOrNull { installable(platform, it) } ?: return null
        val version = when (platform) {
            UpdatePlatform.ANDROID -> versionNumbers(tag)?.joinToString(".")
            UpdatePlatform.DESKTOP -> assetVersion(asset)
        } ?: return null
        return UpdateRelease(
            version = version,
            tag = tag,
            notesUrl = UpdateFeed.pageOf(tag),
            downloadUrl = ProjectLinks.GITHUB + "/releases/download/" + tag + "/" + asset,
            assetName = asset,
            sizeBytes = 0,
        )
    }
}
