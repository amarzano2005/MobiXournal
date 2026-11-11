package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDigests
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixSafetyCaps
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.audioRef
import java.io.File

/** How much a finding matters: an [ERROR] is a document that breaks the rule, a [WARNING] is one worth saying. */
enum class AssetSeverity {
    ERROR,
    WARNING,
}

/**
 * The `assets/` rules from `docs/mobix-assets.md`, each as its own id so a report can be read beside the
 * contract rather than as prose.
 */
enum class AssetRule {
    /** The name is not `assets/<prefix>_<64 lowercase hex>.<suffix>`. */
    NAME_GRAMMAR,

    /** The prefix is not one of the two kinds the vocabulary defines. */
    UNKNOWN_PREFIX,

    /** The bytes do not hash to the digest their own name claims. */
    DIGEST_MISMATCH,

    /** A part names an `assets/` entry the archive does not carry — a corrupt document. */
    MISSING_ENTRY,

    /** An entry no page names: legal under the preserve-unknown rule, and worth reporting. */
    ORPHAN_ENTRY,

    /** A container limit refused the archive before its assets could be checked. */
    CONTAINER_LIMIT,

    /** A page could not be decoded, so the names it references could not be read. */
    UNREADABLE_PAGE,
}

/** One finding: [rule] about [entry], with the page that raised it when a page did. */
data class AssetFinding(
    val severity: AssetSeverity,
    val rule: AssetRule,
    val entry: String,
    val pageIndex: Int? = null,
    val detail: String = "",
)

/**
 * What [MobixAssetValidator] found: the findings in rule order, how many `assets/` entries were examined,
 * and **the limits the check had to apply** ([MobixSafetyCaps]) — so a reader can tell a clean file from
 * one that only looked clean because a cap stopped the walk.
 */
data class AssetValidation(
    val findings: List<AssetFinding>,
    val assetCount: Int,
    val caps: MobixSafetyCaps,
) {
    /** True when no rule was broken. An orphan is reported and still clean: the format allows one. */
    val clean: Boolean get() = findings.none { it.severity == AssetSeverity.ERROR }

    /** The findings that break a rule. */
    val errors: List<AssetFinding> get() = findings.filter { it.severity == AssetSeverity.ERROR }
}

/**
 * **The `assets/` rules published as a validator**, not only as reader behaviour: it takes a `.mobix` and
 * answers which rule it breaks, without opening a document, drawing anything or writing a byte.
 *
 * Four questions, one per rule in `docs/mobix-assets.md`:
 *
 *  1. **Grammar.** Every `assets/` entry name is `<prefix>_<64 lowercase hex>.<suffix>` ([AssetRule.NAME_GRAMMAR]),
 *     with a prefix the vocabulary defines ([AssetRule.UNKNOWN_PREFIX]). This is the check
 *     [MobixFormat.assetEntry] enforces on the way *out*; here it is asked on the way in.
 *  2. **Content addressing.** The bytes hash to the digest the name carries ([AssetRule.DIGEST_MISMATCH]):
 *     the one check that catches a payload damaged or replaced after the write. The reader refuses such
 *     bytes when a page draws them, which means nobody sees the damage until the page is opened; a
 *     validator sees it now.
 *  3. **Both directions of the reference graph.** A name a part references with no entry is an error
 *     ([AssetRule.MISSING_ENTRY]); an entry no part names is an orphan, reported and allowed
 *     ([AssetRule.ORPHAN_ENTRY]) because preserving what this build does not understand is the
 *     container's own rule. A picture element's entry is matched **by digest** rather than by name,
 *     since the model keeps the bytes and not the container they arrived in.
 *  4. **What it could not check.** A cap refusal and an undecodable page are reported as findings rather
 *     than swallowed: a validation that quietly stopped must not read as a clean file.
 *
 * It writes, repairs and rewrites nothing — and its signatures take an entry source or a `File`, never an
 * Android type, so the independent CLI reuses exactly this code rather than a second implementation of
 * the same rules.
 */
object MobixAssetValidator {

    /** Validate the archive at [file]; the source it opens is closed with it. */
    fun validate(file: File, caps: MobixSafetyCaps = MobixSafetyCaps.Default): AssetValidation =
        MobixEntrySource.fromFile(file).use { validate(it, caps) }

    /**
     * Validate [source] under [caps]. The caller owns [source]: this closes nothing it did not open, and
     * the reader it builds is left open for the same reason.
     */
    fun validate(source: MobixEntrySource, caps: MobixSafetyCaps = MobixSafetyCaps.Default): AssetValidation {
        val names = source.entryNames
        val findings = ArrayList<AssetFinding>()
        try {
            caps.refuseBeyondEntryCount(names.size)
        } catch (refused: MobixContainer.MalformedMobixException) {
            return AssetValidation(
                findings = listOf(
                    AssetFinding(AssetSeverity.ERROR, AssetRule.CONTAINER_LIMIT, "", detail = refused.message.orEmpty()),
                ),
                assetCount = 0,
                caps = caps,
            )
        }
        val assets = names.filter { it.startsWith(MobixFormat.ASSETS_DIR) }.sorted()
        findings += assets.flatMap { checkEntry(it, source, caps) }

        val referenced = LinkedHashSet<String>()
        val carried = LinkedHashSet<String>()
        val reader = MobixPagedReader(source, caps = caps)
        try {
            reader.manifest.defaultPaper?.let { referencesOf(it, referenced) }
            for (index in 0 until reader.pageCount) {
                val page = runCatching { reader.getPage(index) }.getOrElse { failure ->
                    findings += AssetFinding(
                        AssetSeverity.ERROR,
                        AssetRule.UNREADABLE_PAGE,
                        entry = reader.pagePart(index)?.entry.orEmpty(),
                        pageIndex = index,
                        detail = failure.message.orEmpty(),
                    )
                    null
                } ?: continue
                referencesOf(page, referenced, carried)
            }
        } catch (refused: MobixContainer.MalformedMobixException) {
            // A reader that will not start is a container problem, and one finding, not an exception
            // thrown out of a validator: the caller asked what is wrong with the file.
            return AssetValidation(findings + AssetFinding(
                AssetSeverity.ERROR, AssetRule.CONTAINER_LIMIT, "", detail = refused.message.orEmpty(),
            ), assets.size, caps)
        } finally {
            reader.clearCache()
        }

        val present = assets.toSet()
        val referencedEntries = referenced.map { withAssetsPrefix(it) }.filter { inAssetsShape(it) }.toSet()
        for (name in referencedEntries.filter { it !in present }.sorted()) {
            findings += AssetFinding(AssetSeverity.ERROR, AssetRule.MISSING_ENTRY, name)
        }
        // An entry that already broke a rule is not *also* an orphan: one fault, one finding, because a
        // report that lists the same damaged entry twice reads as two problems.
        val faulty = findings.map { it.entry }.toSet()
        for (name in assets) {
            if (name in referencedEntries || name in faulty) continue
            val digest = MobixDigests.digestIn(name)
            if (digest != null && digest in carried) continue
            findings += AssetFinding(
                AssetSeverity.WARNING,
                AssetRule.ORPHAN_ENTRY,
                name,
                detail = "no part names this entry; the container keeps it, and nothing draws it",
            )
        }
        return AssetValidation(findings.sortedWith(FINDING_ORDER), assets.size, caps)
    }

    // ---- one entry's name and bytes ----------------------------------------------------------------

    /**
     * The grammar and the digest of one `assets/` entry. The name is checked first and the bytes are
     * only hashed when the name is well formed — a digest is only meaningful against a name that claims
     * one.
     */
    private fun checkEntry(name: String, source: MobixEntrySource, caps: MobixSafetyCaps): List<AssetFinding> {
        val grammar = grammarFinding(name)
        if (grammar != null) return listOf(grammar)
        val claimed = MobixDigests.digestIn(name) ?: return listOf(grammarFinding(name)!!)
        val bytes = try {
            caps.read(source, name, caps.maxAssetBytes)
        } catch (refused: MobixContainer.MalformedMobixException) {
            return listOf(
                AssetFinding(AssetSeverity.ERROR, AssetRule.CONTAINER_LIMIT, name, detail = refused.message.orEmpty()),
            )
        } ?: return listOf(
            AssetFinding(AssetSeverity.ERROR, AssetRule.MISSING_ENTRY, name, detail = "listed in the directory, absent when read"),
        )
        val actual = MobixAssetSink.sha256(bytes)
        if (actual == claimed) return emptyList()
        return listOf(
            AssetFinding(
                AssetSeverity.ERROR,
                AssetRule.DIGEST_MISMATCH,
                name,
                detail = "the name claims $claimed, the bytes hash to $actual",
            ),
        )
    }

    /** The syntax finding for [name], or null when it is a well-formed `assets/` entry of a known kind. */
    private fun grammarFinding(name: String): AssetFinding? {
        val bare = name.removePrefix(MobixFormat.ASSETS_DIR)
        val prefix = bare.substringBefore('_', "")
        val rest = bare.removePrefix("${prefix}_")
        val hash = rest.substringBefore('.')
        val suffix = rest.substringAfter('.', "")
        val known = MobixFormat.AssetKind.entries.any { it.prefix == prefix }
        val wellFormed = prefix.isNotEmpty() && hash.length == DIGEST_LENGTH &&
            hash.all { it in "0123456789abcdef" } && suffix.isNotEmpty() && suffix.all { it.isLetterOrDigit() }
        return when {
            !wellFormed -> AssetFinding(
                AssetSeverity.ERROR,
                AssetRule.NAME_GRAMMAR,
                name,
                detail = "expected assets/<prefix>_<64 lowercase hex>.<suffix>, letters and digits only",
            )
            !known -> AssetFinding(
                AssetSeverity.ERROR,
                AssetRule.UNKNOWN_PREFIX,
                name,
                detail = "known prefixes: ${MobixFormat.AssetKind.entries.joinToString(", ") { it.prefix }}",
            )
            else -> null
        }
    }

    private fun inAssetsShape(name: String): Boolean =
        MobixFormat.AssetKind.entries.any { MobixFormat.isAssetEntry(name, it) }

    private fun withAssetsPrefix(name: String): String =
        if (name.startsWith(MobixFormat.ASSETS_DIR)) name else MobixFormat.ASSETS_DIR + name

    // ---- what a page references --------------------------------------------------------------------

    /**
     * The entry names a page's background names, plus the digests of the payloads its elements **carry**:
     * a picture element keeps its bytes in the model, so its entry is matched by content rather than by
     * the container the bytes arrived in.
     */
    private fun referencesOf(page: Page, referenced: MutableSet<String>, carried: MutableSet<String>) {
        referencesOf(page.background, referenced)
        for (layer in page.layers) {
            for (element in layer.elements) {
                when (element) {
                    is ImageElement -> carried += MobixAssetSink.sha256(element.data)
                    is TexImageElement -> element.data?.let { carried += MobixAssetSink.sha256(it) }
                    is Stroke -> {
                        element.bundledAudio?.let(referenced::add)
                        element.audioRef()?.filename
                            ?.takeIf { MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO) }
                            ?.let(referenced::add)
                    }
                    // Every other family carries no payload today; a preserved element is not this
                    // build's JSON to interpret, which the report states rather than guesses at.
                    else -> Unit
                }
            }
        }
    }

    /** A background's bundled entry, when it has one — a bare filename is a reference, not an asset. */
    private fun referencesOf(background: Background, referenced: MutableSet<String>) {
        when (background) {
            is Background.Pdf -> background.bundledAsset?.let(referenced::add)
            is Background.Pixmap -> background.bundledAsset?.let(referenced::add)
            is Background.Solid -> Unit
        }
    }

    /** Errors before warnings, then by page, then by entry: a report reads the same way twice. */
    private val FINDING_ORDER = compareBy<AssetFinding>(
        { it.severity.ordinal },
        { it.pageIndex ?: -1 },
        { it.rule.ordinal },
        { it.entry },
    )

    /** The length of the lowercase hex SHA-256 every asset name carries (`docs/mobix-assets.md`). */
    private const val DIGEST_LENGTH = 64
}
