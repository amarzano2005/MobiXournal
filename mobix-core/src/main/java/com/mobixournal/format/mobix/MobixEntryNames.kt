package com.mobixournal.format.mobix

/**
 * The rules an archive's entry names must satisfy before anything is read out of it
 * (`docs/mobix-format.md`, *What a reader must refuse*).
 *
 * A name here is an **identity**, not a path: `pages[]` refers to a part by its `entry`, an element
 * refers to its bytes by `asset`, and the reader answers a name with the first entry that has it. That
 * makes three shapes dangerous in ways a filesystem would call normal, and this layer refuses rather
 * than repairs them — normalising a name would make two different documents out of one file:
 *
 *  - **a duplicate** — two entries with one name mean one part can shadow another, and which one wins
 *    would depend on the order the archive happens to store them in;
 *  - **a traversal or an absolute path** (`../`, `/x`, `C:\x`) — this reader never extracts to disk, so
 *    nothing here can leave the archive, but a tool that did unpack it would inherit whatever a hostile
 *    writer put in the name;
 *  - **a backslash or a control character** — the format's separators are `/`, and a name carrying a
 *    NUL or a newline cannot be written back into a manifest or shown in an error without escaping it.
 *
 * The check is allocation-light and touches no filesystem API: it is a property of the string, so it
 * costs one pass over names that were already in memory.
 */
internal object MobixEntryNames {

    /** Reject the first name that breaks a rule, naming the entry and the rule in the message. */
    fun validate(names: List<String>) {
        val seen = HashSet<String>(names.size)
        for (name in names) {
            if (name.isEmpty()) refuse(name, "has no name")
            if (name.contains('\\')) refuse(name, "uses a backslash; this format's separator is \"/\"")
            if (name.startsWith("/")) refuse(name, "is an absolute path")
            if (name.length >= 2 && name[1] == ':' && name[0].isLetter()) {
                refuse(name, "is an absolute Windows path")
            }
            if (name.split('/').any { it == ".." }) refuse(name, "traverses out of the archive with \"..\"")
            if (name.split('/').any { it == "." }) refuse(name, "contains a \".\" path segment")
            if (name.any { it.isISOControl() }) refuse(name, "contains a control character")
            if (!seen.add(name)) refuse(name, "appears twice; an entry name is an identity here")
        }
    }

    private fun refuse(name: String, why: String): Nothing =
        throw MobixContainer.MalformedMobixException("archive entry \"$name\" $why")
}
