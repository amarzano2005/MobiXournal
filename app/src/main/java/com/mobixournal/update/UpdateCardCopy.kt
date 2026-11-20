package com.mobixournal.update

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Why the last attempt stopped. The card's body and the About row read their sentence from the same
 * mapping ([updateFailureSentence]), so a failure is worded once and cannot say two different things.
 */
enum class UpdateFailure { CHECK, DOWNLOAD, INSTALL }

/**
 * What the card's filled answer **does**. The phase decides which action it is; the label comes from the
 * action, so a button's wording and its behaviour cannot drift apart (`docs/updates.md`).
 */
internal enum class UpdateAction {
    UPDATE_NOW,
    RETRY,
    INSTALL,
    CANCEL,
    ;

    val label: String
        get() = when (this) {
            UPDATE_NOW -> "Update now"
            RETRY -> "Try again"
            INSTALL -> "Install"
            CANCEL -> "Cancel"
        }
}

/** The words the update card wears in one [UpdatePhase] — the only place any of them is written. */
internal data class UpdateCardCopy(
    val title: String,
    val subtitle: String,
    val body: String?,
    /** The notes link's label, or null when the card is busy downloading or offering the install. */
    val notes: String?,
    /** **Skip this version**, or null once it is skipped or the phase has no offer to skip. */
    val skip: String?,
    val primary: UpdateAction,
    val secondary: String?,
)

/** What the card says when the bytes are in hand and Android's installer has (or will) take over. */
internal const val UPDATE_DOWNLOADED_SENTENCE = "The update is downloaded. Android's installer will finish it."

/** What the card says when this build has been skipped and a newer one is not on the table. */
internal const val UPDATE_SKIP_LABEL = "Skip this version"

/**
 * What [phase] says about [version] on a build that carries [current].
 *
 * [failure] is [AppUpdates.failure] — the reason, never the sentence; [downloaded] is whether the APK
 * already arrived, which is what tells a failed **download** (ask again) from a failed **install** (the
 * bytes are here: hand them to Android again). [skippedVersion] hides the skip answer for the build the
 * reader already skipped, so the card does not offer a choice that would do nothing.
 */
internal fun updateCardFor(
    phase: UpdatePhase,
    version: String,
    current: String,
    failure: UpdateFailure?,
    downloaded: Boolean,
    skippedVersion: String?,
): UpdateCardCopy {
    val skip = skipAnswerFor(version, skippedVersion)
    return when (phase) {
        UpdatePhase.DOWNLOADING -> UpdateCardCopy(
            title = "MobiXournal $version",
            subtitle = "Downloading the update",
            body = null,
            notes = null,
            skip = null,
            primary = UpdateAction.CANCEL,
            secondary = null,
        )
        UpdatePhase.READY -> downloadedCard(version, current, UPDATE_DOWNLOADED_SENTENCE)
        UpdatePhase.FAILED -> if (downloaded) {
            downloadedCard(version, current, updateFailureSentence(UpdateFailure.INSTALL))
        } else {
            offerCard(
                version = version,
                current = current,
                body = updateFailureSentence(failure ?: UpdateFailure.DOWNLOAD),
                skip = skip,
                primary = UpdateAction.RETRY,
            )
        }
        // IDLE, CHECKING and UP_TO_DATE never raise the card; when one is showing it is the offer itself.
        else -> offerCard(version, current, body = null, skip = skip, primary = UpdateAction.UPDATE_NOW)
    }
}

/** The offer, worded once: the build on the table, the notes, and the answers an offer has. */
private fun offerCard(
    version: String,
    current: String,
    body: String?,
    skip: String?,
    primary: UpdateAction,
) = UpdateCardCopy(
    title = "MobiXournal $version",
    subtitle = "Update available · you have $current",
    body = body,
    notes = "What's changed in $version",
    skip = skip,
    primary = primary,
    secondary = "Remind me later",
)

/** The bytes are in hand: the fresh download and the refused installer say the same thing but the sentence. */
private fun downloadedCard(version: String, current: String, body: String) = UpdateCardCopy(
    title = "Update downloaded",
    subtitle = "MobiXournal $version · you have $current",
    body = body,
    notes = null,
    skip = null,
    primary = UpdateAction.INSTALL,
    secondary = "Later",
)

/** The one sentence a failure has, shared by the card's body and the About row. */
internal fun updateFailureSentence(failure: UpdateFailure?): String = when (failure) {
    UpdateFailure.CHECK -> "Could not check for updates. Check your connection and try again."
    UpdateFailure.DOWNLOAD -> "The download did not finish. Check your connection and try again."
    UpdateFailure.INSTALL ->
        "Android did not open its installer. Allow MobiXournal to install apps and try again."
    null -> "Could not check for updates."
}

/**
 * The one line About prints under its command, read from the same copy as the card: the version in hand,
 * the newer one on offer, the download's share, or the failure's own sentence.
 */
internal fun updateStatusLine(
    phase: UpdatePhase,
    version: String?,
    current: String,
    failure: UpdateFailure?,
    progress: Float,
    totalBytes: Long,
): String = when (phase) {
    UpdatePhase.IDLE -> "Tap to check for updates."
    UpdatePhase.CHECKING -> "Checking for updates…"
    UpdatePhase.UP_TO_DATE -> "MobiXournal $current is the latest version."
    UpdatePhase.AVAILABLE -> version?.let { "Version $it is available." } ?: ""
    UpdatePhase.DOWNLOADING ->
        if (totalBytes > 0L) "Downloading ${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%" else "Downloading…"
    UpdatePhase.READY -> UPDATE_DOWNLOADED_SENTENCE
    UpdatePhase.FAILED -> updateFailureSentence(failure)
}

/**
 * Whether the **launch** check may raise the card: never for a build the reader skipped, and never for
 * About's own command, which exists precisely to reopen an offer the reader asks for.
 */
internal fun updateShouldInterrupt(fromLaunch: Boolean, version: String, skippedVersion: String?): Boolean =
    !fromLaunch || version != skippedVersion

/**
 * The line under the progress bar: the percentage and the bytes only when the server declared a length.
 *
 * A response without `Content-Length` has no honest percentage — the old card printed "Downloading 0%"
 * for the whole of such a download — so it says only that it is downloading, and the bar stays
 * indeterminate.
 */
internal fun updateProgressLine(known: Boolean, fraction: Float, received: Long, total: Long): String {
    if (!known || total <= 0L) return "Downloading…"
    val percent = (fraction.coerceIn(0f, 1f) * 100f).roundToInt()
    return "$percent% · ${megabytes(received)} of ${megabytes(total)}"
}

private fun skipAnswerFor(version: String, skippedVersion: String?): String? =
    if (version == skippedVersion) null else UPDATE_SKIP_LABEL

/** Bytes as the card shows them: one decimal of a megabyte, English decimal point whatever the device. */
private fun megabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
