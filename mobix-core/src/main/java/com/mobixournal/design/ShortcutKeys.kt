package com.mobixournal.design

/**
 * **The key table**, as one model both shells read: what a stored table looks like, what binding a key
 * does when another command already holds it, and what "factory settings" means.
 *
 * The two shells keep their own *commands* (a tool on the phone is not a tool here, and the desktop has
 * commands the phone has no use for), so nothing in this file knows a command's name beyond the string a
 * shell gives it. What it does own is the part that must not be written twice: the wire format the file
 * stores, the **one key, one command** rule — the displaced command loses its key and the row says which
 * one it was — and the reset.
 */
object ShortcutKeys {

    /** A key as it is stored and compared: trimmed, and nothing else (case is the modifier's business). */
    fun normalize(key: String): String = key.trim()

    /**
     * The stored form of **"this command has no key"**.
     *
     * An empty value has always meant "not named here" (the phone's own rule, and a file a person edited),
     * so the one-key-one-command rule needs a word of its own to say a command lost its key: without it, the
     * command would find its factory key again at the next start and the theft would undo itself.
     */
    const val UNBOUND = "-"

    /**
     * [keys] as the stored string: `id:key` pairs joined by commas, in the map's own order.
     *
     * An empty key is dropped — "nothing here" is the absence of an entry, or [UNBOUND] when the shell has
     * to say that a command deliberately has none.
     */
    fun encode(keys: Map<String, String>): String =
        keys.entries
            .filter { it.value.isNotEmpty() }
            .joinToString(",") { "${it.key}:${normalize(it.value)}" }

    /**
     * Parse what [encode] wrote.
     *
     * [known] is the shell's own list of command ids: an entry naming a command this build no longer has is
     * dropped, so a table written by another version degrades to the defaults instead of a dead row. A null
     * [known] keeps every id, which is what a test and the backup's reader want.
     */
    fun decode(raw: String?, known: Collection<String>? = null): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val ids = known?.toSet()
        return raw.split(',')
            .mapNotNull { entry ->
                val pair = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val id = pair[0].trim()
                val key = normalize(pair[1])
                if (id.isEmpty() || key.isEmpty()) return@mapNotNull null
                if (ids != null && id !in ids) return@mapNotNull null
                id to key
            }
            .toMap()
    }

    /** What one binding changed: the new table, and the command that lost the key, if any. */
    data class Binding(
        val table: Map<String, String>,
        val command: String,
        val key: String,
        /** The command that held [key] before this binding — the row that says what was displaced. */
        val displaced: String?,
    )

    /** Which command holds [key] in [table], ignoring [except]; null when the key is free. */
    fun holder(table: Map<String, String>, key: String, except: String? = null): String? {
        val wanted = normalize(key)
        if (wanted.isEmpty()) return null
        return table.entries.firstOrNull { it.key != except && normalize(it.value) == wanted }?.key
    }

    /**
     * Bind [key] to [command].
     *
     * The **one key, one command** rule, in both directions: the command's own previous key goes, and so
     * does any other command's claim on [key] — [Binding.displaced] names it so the surface can say which
     * command lost the key instead of silently stealing it. An empty [key] removes the binding.
     */
    fun bind(table: Map<String, String>, command: String, key: String): Binding {
        val wanted = normalize(key)
        if (wanted.isEmpty()) return Binding(table - command, command, "", null)
        val displaced = holder(table, wanted, except = command)
        val next = table.toMutableMap()
        next[command] = wanted
        if (displaced != null) next.remove(displaced)
        return Binding(next, command, wanted, displaced)
    }

    /**
     * The table a shell should read: its [defaults] with [stored] laid over them.
     *
     * An empty [stored] is the defaults, and so is a table that fails to name anything this build knows —
     * which is what makes "Restore defaults" simply [restore]. A stored [UNBOUND] value removes the row: that
     * is how a command that lost its key stays without one instead of finding its factory key again.
     */
    fun merge(defaults: Map<String, String>, stored: Map<String, String>): Map<String, String> =
        if (stored.isEmpty()) {
            defaults.toMap()
        } else {
            (defaults + stored).filterValues { it.isNotEmpty() && it != UNBOUND }
        }

    /** The factory table: [defaults] with nothing remembered. */
    fun restore(defaults: Map<String, String>): Map<String, String> = defaults.toMap()
}
