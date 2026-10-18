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
 */
object AudioStandaloneExporter {

    private const val MOBIXOURNAL_FOLDER = "MobiXournal"

    /**
     * Exports [sourceFile] to the public media collection.
     * Returns true if the file was successfully written.
     */
    fun exportStandalone(context: Context, sourceFile: File): Boolean {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return false
        val extension = sourceFile.extension.lowercase()
        val mimeType = when (extension) {
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "m4a", "aac" -> "audio/mp4"
            else -> "audio/*"
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(context, sourceFile, mimeType)
        } else {
            exportViaPublicDirectory(sourceFile)
        }
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
