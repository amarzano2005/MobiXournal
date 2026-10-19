package com.mobixournal.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Type of Whisper on-device model supported by the application.
 */
enum class WhisperModelType(
    val id: String,
    val displayName: String,
    val prefix: String,
    val approximateSizeMb: Int,
    val isTiny: Boolean,
) {
    TINY(
        id = "whisper-tiny",
        displayName = "Whisper Tiny (Consigliato, ~39 MB)",
        prefix = "tiny",
        approximateSizeMb = 39,
        isTiny = true,
    ),
    BASE(
        id = "whisper-base",
        displayName = "Whisper Base (Alta precisione, ~75 MB)",
        prefix = "base",
        approximateSizeMb = 75,
        isTiny = false,
    ),
}

/**
 * Manages downloading, verifying, and storing offline Whisper multilingual models
 * inside the app's internal storage (`files/whisper_models/`).
 */
object WhisperModelManager {

    private const val HF_TINY_BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"
    private const val HF_BASE_BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main"

    /**
     * Directory containing local Whisper model weights.
     */
    fun getModelsDirectory(context: Context): File {
        val dir = File(context.filesDir, "whisper_models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Directory for a specific [modelType].
     */
    fun getModelDir(context: Context, modelType: WhisperModelType): File {
        val dir = File(getModelsDirectory(context), modelType.id)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Checks whether all required ONNX files and tokens exist and are non-empty for [modelType].
     */
    fun isModelInstalled(context: Context, modelType: WhisperModelType): Boolean {
        val dir = getModelDir(context, modelType)
        val p = modelType.prefix
        val encoder = File(dir, "$p-encoder.int8.onnx")
        val decoder = File(dir, "$p-decoder.int8.onnx")
        val tokens = File(dir, "$p-tokens.txt")
        return encoder.isFile && encoder.length() > 5_000_000L &&
            decoder.isFile && decoder.length() > 5_000_000L &&
            tokens.isFile && tokens.length() > 100_000L
    }

    /**
     * Checks if any Whisper model is installed and ready to use, preferring Tiny.
     */
    fun getAvailableModel(context: Context): WhisperModelType? {
        if (isModelInstalled(context, WhisperModelType.TINY)) return WhisperModelType.TINY
        if (isModelInstalled(context, WhisperModelType.BASE)) return WhisperModelType.BASE
        return null
    }

    /**
     * Deletes local files for [modelType].
     */
    fun deleteModel(context: Context, modelType: WhisperModelType): Boolean {
        val dir = getModelDir(context, modelType)
        return dir.deleteRecursively()
    }

    /**
     * Downloads model weights for [modelType] with progressive progress reporting.
     *
     * @param onProgress Callback invoked with (downloadedBytes, totalEstimatedBytes, progressRatio0to1).
     * @param isCancelled Callback to allow aborting the download.
     */
    suspend fun downloadModel(
        context: Context,
        modelType: WhisperModelType,
        onProgress: (downloaded: Long, total: Long, ratio: Float) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): Boolean = withContext(Dispatchers.IO) {
        val dir = getModelDir(context, modelType)
        val baseUrl = if (modelType.isTiny) HF_TINY_BASE_URL else HF_BASE_BASE_URL
        val p = modelType.prefix

        val filesToDownload = listOf(
            "$p-encoder.int8.onnx",
            "$p-decoder.int8.onnx",
            "$p-tokens.txt",
        )

        val totalExpectedBytes = modelType.approximateSizeMb.toLong() * 1024L * 1024L
        var totalDownloaded = 0L

        for (fileName in filesToDownload) {
            if (isCancelled()) {
                dir.deleteRecursively()
                return@withContext false
            }

            val targetFile = File(dir, fileName)
            val fileUrl = "$baseUrl/$fileName"
            val success = downloadSingleFile(
                urlStr = fileUrl,
                destination = targetFile,
                onChunk = { bytesRead ->
                    totalDownloaded += bytesRead
                    val ratio = (totalDownloaded.toFloat() / totalExpectedBytes.toFloat()).coerceIn(0f, 0.99f)
                    onProgress(totalDownloaded, totalExpectedBytes, ratio)
                },
                isCancelled = isCancelled,
            )

            if (!success) {
                dir.deleteRecursively()
                return@withContext false
            }
        }

        onProgress(totalExpectedBytes, totalExpectedBytes, 1.0f)
        isModelInstalled(context, modelType)
    }

    private fun downloadSingleFile(
        urlStr: String,
        destination: File,
        onChunk: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        val tempFile = File(destination.parentFile, "${destination.name}.tmp")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) return false

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } >= 0) {
                        if (isCancelled()) {
                            tempFile.delete()
                            return false
                        }
                        output.write(buffer, 0, bytes)
                        onChunk(bytes)
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (destination.exists()) destination.delete()
                return tempFile.renameTo(destination)
            }
            return false
        } catch (_: Exception) {
            tempFile.delete()
            return false
        } finally {
            connection?.disconnect()
        }
    }
}
