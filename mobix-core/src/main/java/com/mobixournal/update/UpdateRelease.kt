package com.mobixournal.update

import com.mobixournal.design.ProjectLinks
import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser

/**
 * Which shell is asking for an update.
 *
 * The two shells ship from **one** GitHub release and carry **two** version numbers (`docs/updates.md`):
 * the release's tag is the Android build's, while the window's number is the one the MSI's own asset name
 * carries (`MobiXournal-1.0.1.msi`). So the platform decides both which asset is the update and which text
 * is the version to compare — a tag-only reading would offer a Windows reader a downgrade.
 */
enum class UpdatePlatform { ANDROID, DESKTOP }

/** Where the releases live: the API that answers with one document, and the pages a person reads. */
object UpdateFeed {

    /** GitHub's latest published release — `assets`, `tag_name`, `html_url` and the notes' body. */
    const val LATEST = "https://api.github.com/repos/amarzano2005/MobiXournal/releases/latest"

    /** The release's own page, which is where "what changed" is read. */
    const val RELEASES = ProjectLinks.GITHUB + "/releases"

    /** The one release named [tag], for a check that already knows which build it is offering. */
    fun pageOf(tag: String): String = RELEASES + "/tag/" + tag
}

/**
 * One published build, as the window and the tablet read it: the version the shell compares with, the page
 * that says what changed, and the asset it would download.
 */
data class UpdateRelease(
    /** The version this *platform* would install — the tag for Android, the MSI's name for Windows. */
    val version: String,
    /** The release's tag, which is how the same release is named on GitHub. */
    val tag: String,
    /** The release page: the reader's own account of what changed. */
    val notesUrl: String,
    /** The asset's download URL, straight from the API. */
    val downloadUrl: String,
    val assetName: String,
    val sizeBytes: Long,
)

/**
 * The release [json] describes for [platform], or null when it is not one this shell could install.
 *
 * A **draft** or a **prerelease** is not an update: the API can be asked for the latest release while one is
 * being prepared, and installing it would ship a build the owner has not published. The first asset whose
 * name the platform's installer can open is the one offered — the release carries both the tablet's APK and
 * the window's MSI, and each shell has exactly one it can run.
 */
fun parseRelease(json: String, platform: UpdatePlatform): UpdateRelease? {
    val root = runCatching { JsonParser.parse(json) }.getOrNull() as? JsonNode.Obj ?: return null
    if (root.getBool("draft", false) || root.getBool("prerelease", false)) return null
    val tag = root.getStrOrNull("tag_name")?.trim().orEmpty()
    if (tag.isEmpty()) return null
    val page = root.getStrOrNull("html_url")?.takeIf { it.startsWith("http") } ?: UpdateFeed.pageOf(tag)
    val assets = root.getArr("assets")?.list.orEmpty().mapNotNull { it as? JsonNode.Obj }
    val asset = assets.firstOrNull { installable(platform, it.getStr("name", "")) } ?: return null
    val name = asset.getStr("name", "")
    val download = asset.getStrOrNull("browser_download_url")?.takeIf { it.startsWith("http") } ?: return null
    val version = when (platform) {
        UpdatePlatform.ANDROID -> versionNumbers(tag)?.joinToString(".")
        UpdatePlatform.DESKTOP -> assetVersion(name)
    } ?: return null
    return UpdateRelease(
        version = version,
        tag = tag,
        notesUrl = page,
        downloadUrl = download,
        assetName = name,
        sizeBytes = (asset.getDoubleOrNull("size") ?: 0.0).toLong(),
    )
}

/**
 * Whether [candidate] is a **later** version than [current]: dotted numbers compared place by place, a
 * missing place read as zero, so `2.0` and `2.0.0` are the same build and `2.0.10` is newer than `2.0.9`.
 *
 * Anything that is not a version compares as "not newer": a check that cannot read a number must never turn
 * itself into an offer to install something unknown.
 */
fun isNewer(candidate: String, current: String): Boolean {
    val remote = versionNumbers(candidate) ?: return false
    val local = versionNumbers(current) ?: return false
    for (place in 0 until maxOf(remote.size, local.size)) {
        val theirs = remote.getOrElse(place) { 0 }
        val ours = local.getOrElse(place) { 0 }
        if (theirs != ours) return theirs > ours
    }
    return false
}

/**
 * The version the window's own asset name carries — `MobiXournal-1.0.1.msi` → `1.0.1` — or null when the
 * name holds no number at all.
 *
 * This is why the MSI's name is part of the contract (`docs/tools.md`, *Desktop installer*): the release tag
 * names the tablet's build, so a window that compared itself with the tag would read `v2.0.0` as newer than
 * its own `1.0.1` forever.
 */
fun assetVersion(assetName: String): String? = versionNumbers(assetName)?.joinToString(".")

/** Whether [assetName] is something [platform]'s own installer can open. */
fun installable(platform: UpdatePlatform, assetName: String): Boolean {
    val name = assetName.lowercase()
    return when (platform) {
        UpdatePlatform.ANDROID -> name.endsWith(".apk")
        UpdatePlatform.DESKTOP -> name.endsWith(".msi")
    }
}

/** The first dotted number in [text], as its places — null when the text holds no number. */
internal fun versionNumbers(text: String): List<Int>? {
    val match = Regex("""(\d+(?:\.\d+)*)""").find(text) ?: return null
    return match.value.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
}
