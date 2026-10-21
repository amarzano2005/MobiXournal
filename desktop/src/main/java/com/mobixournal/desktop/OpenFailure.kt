package com.mobixournal.desktop

/**
 * What the window says when an open failed: a sentence a person can act on, and the detail under it.
 *
 * The shell used to show `exception.message`, which is the wrong thing in both directions: a
 * `NoClassDefFoundError` has *no* message (its `message` is the internal class name, so the user read
 * `com/mobixournal/desktop/DocumentLoader$openMobix$1` and concluded their notebook was broken), while
 * the loader's own refusals — `Formato non riconosciuto`, `archivio ZIP senza content.xml` — were
 * already the whole explanation. [describeOpenFailure] sorts the cases into those two groups so a
 * development accident cannot be reported as a damaged document.
 */
data class OpenFailure(
    /** One line: what happened. */
    val title: String,
    /** The sentence under it: why, and what to do. */
    val detail: String,
)

/**
 * The window's account of [failure].
 *
 * A `LinkageError` (`NoClassDefFoundError`, `NoSuchMethodError`, …) means the **code** of this window
 * was replaced while it was open — a Gradle build rewrote `build/classes` under the live JVM — so the
 * file that failed to open is innocent and reopening it in a fresh window works. That is a fact worth
 * saying out loud, because the alternative reading (\"my notebook is corrupt\") is the one a user
 * actually has, and it costs an hour of panic.
 *
 * An [UnsupportedOperationException] is the loader's own refusal and already explains itself; an
 * [OutOfMemoryError] on a very tall page gets its own line rather than a class name.
 */
fun describeOpenFailure(failure: Throwable): OpenFailure {
    val detail = failure.message?.takeIf { it.isNotBlank() } ?: "nessun dettaglio disponibile"
    return when (failure) {
        is LinkageError -> OpenFailure(
            "L'anteprima è rimasta indietro",
            "Il documento non c'entra: mentre questa finestra era aperta il codice è stato " +
                "ricompilato, e una classe è sparita da sotto i piedi. Chiudi la finestra e rilancia " +
                "l'anteprima (`gradlew :desktop:run`), poi riapri lo stesso file.",
        )
        is UnsupportedOperationException -> OpenFailure("Formato non supportato", detail)
        is OutOfMemoryError -> OpenFailure(
            "Memoria insufficiente",
            "Questa pagina è troppo grande per la memoria disponibile: $detail",
        )
        else -> OpenFailure("Impossibile aprire il documento", "${failure::class.simpleName}: $detail")
    }
}
