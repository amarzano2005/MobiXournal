package com.mobixournal.format.mobix

/**
 * One capability a `.mobix` file declares about itself (`docs/mobix-schema-spec.md` owns the contract
 * and the four outcomes; this is the value type the manifest carries).
 *
 * An identifier is `<namespace>/<feature>:<version>`: a reverse-DNS namespace the declaring writer
 * owns, a lower-case dot-or-dash feature name, and a positive integer version. The namespace exists so
 * two writers cannot collide on one word, and it is deliberately **not a URL** — nothing is fetched, so
 * a capability never becomes a network dependency or a privacy leak.
 *
 * A version is a promise about the *shape* of the feature, and a later version is a superset of the one
 * before it. That is why [satisfies] compares versions instead of matching them: a reader that handles
 * `…:2` can read `…:1` correctly, and a reader that handles only `…:1` must not claim `…:2`.
 */
data class MobixCapability(
    /** The reverse-DNS namespace: who owns the feature's meaning. */
    val namespace: String,

    /** The feature's name within [namespace]: lower-case, dot-or-dash separated. */
    val feature: String,

    /** The shape's version; positive, and a later one is a superset of an earlier one. */
    val version: Int,
) : Comparable<MobixCapability> {

    /** The canonical spelling, so a value written back is the one that was read. */
    override fun toString(): String = "$namespace/$feature:$version"

    /**
     * Whether [declared] — the capabilities a reader implements, or another file declares — covers this
     * one: the same namespace and feature at a version `>=` this one. Nothing else matches, so a
     * different feature never satisfies this one however high its version is.
     */
    fun satisfies(declared: Iterable<MobixCapability>): Boolean =
        declared.any { it.namespace == namespace && it.feature == feature && it.version >= version }

    /** Ordering by canonical spelling, so two manifests can be compared in a test without one built in. */
    override fun compareTo(other: MobixCapability): Int = toString().compareTo(other.toString())

    companion object {
        /** A reverse-DNS namespace: at least two dot-separated labels, lower-case, dashes allowed inside. */
        private val NAMESPACE = Regex("""^[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+(-[a-z0-9]+)*)+$""")

        /** A feature name: lower-case alphanumerics separated by single dots or dashes. */
        private val FEATURE = Regex("""^[a-z0-9]+([.-][a-z0-9]+)*$""")

        /**
         * Parse [text], or null when it is not exactly the grammar.
         *
         * A value that does not parse is a **corrupt declaration**, not a capability from the future: a
         * reader cannot tell an identifier it does not know (which is fine, and preserved) from a
         * misspelled one (which would silently weaken the file's own requirements), so the manifest
         * codec refuses both and the open fails with the offending text named.
         */
        fun parse(text: String): MobixCapability? {
            val slash = text.indexOf('/')
            if (slash <= 0) return null
            val colon = text.lastIndexOf(':')
            if (colon <= slash + 1) return null
            val namespace = text.substring(0, slash)
            val feature = text.substring(slash + 1, colon)
            val version = text.substring(colon + 1).toIntOrNull() ?: return null
            if (version <= 0) return null
            if (!NAMESPACE.matches(namespace) || !FEATURE.matches(feature)) return null
            return MobixCapability(namespace, feature, version)
        }
    }
}
