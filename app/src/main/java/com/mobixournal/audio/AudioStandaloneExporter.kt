package com.mobixournal.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a completed audio recording to the device's public media storage (Recordings or Music)
 * as a standalone audio file when requested by user preferences.
 *
 * What lands there is **`.m4a` (MP4/AAC)** whatever the app recorded: Ogg/Opus and WAV both play inside
 * the app, but outside it Ogg/Opus is the format an uploader most often refuses — an assistant that
 * does not recognise it falls back to reading the bytes as text — and a lecture-length WAV is far too
 * big to send anywhere ([AudioTranscoder]). A recording already in M4A is copied as it is; a recording
 * whose bytes cannot be transcoded is copied as it is too, so nothing is ever lost by exporting.
 */
object AudioStandaloneExporter {

    private const val MOBIXOURNAL_FOLDER = "MobiXournal"

    /**
     * Exports [sourceFile] to the public media collection.
     * Returns true if the file was successfully written.
     */
    fun exportStandalone(context: Context, sourceFile: File): Boolean {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return false
        val copy = portableCopy(context, sourceFile) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(context, copy.file, copy.mimeType)
            } else {
                exportViaPublicDirectory(copy.file)
            }
        } finally {
            if (copy.temporary) copy.file.delete()
        }
    }

    /** The file the media collection receives, and whether it is a scratch copy to delete afterwards. */
    private class PortableCopy(val file: File, val mimeType: String, val temporary: Boolean)

    /**
     * The exportable shape of [sourceFile]: copied through [AudioTranscoder] into the cache when it is
     * not already M4A, or handed back untouched when it is — or when the transcode is not possible here.
     */
    private fun portableCopy(context: Context, sourceFile: File): PortableCopy? {
        if (sourceFile.extension.equals(AudioTranscoder.CONTAINER_SUFFIX, ignoreCase = true)) {
            return PortableCopy(sourceFile, AudioTranscoder.MIME_TYPE, temporary = false)
        }
        val target = File(context.cacheDir, AudioTranscoder.exportedFileName(sourceFile.name))
        return if (AudioTranscoder.toM4a(sourceFile, target)) {
            PortableCopy(target, AudioTranscoder.MIME_TYPE, temporary = true)
        } else {
            PortableCopy(sourceFile, mimeTypeFor(sourceFile), temporary = false)
        }
    }

    /** The media-store type of a file that could not be transcoded, from its extension. */
    internal fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a", "aac" -> AudioTranscoder.MIME_TYPE
        "mp3" -> "audio/mpeg"
        else -> "audio/*"
    }

    private fun exportViaMediaStore(context: Context, sourceFile: File, mimeType: String): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "${Environment.DIRECTORY_RECORDINGS}/$MOBIXOURNAL_FOLDER"
            } else {
                "${Environment.DIRECTORY_MUSIC}/$MOBIXOURNAL_FOLDER"
            }
            val details = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, directory)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(collection, details) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                details.clear()
                details.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, details, null, null)
            }
            true
        }.getOrDefault(false)
    }

    private fun exportViaPublicDirectory(sourceFile: File): Boolean {
        return runCatching {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val dir = File(base, MOBIXOURNAL_FOLDER).also { it.mkdirs() }
            val target = File(dir, sourceFile.name)
            sourceFile.copyTo(target, overwrite = true)
            target.exists() && target.length() > 0L
        }.getOrDefault(false)
    }
}
