package com.mobixournal.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mobixournal.format.model.ImageElement
import org.jetbrains.skia.Image as SkiaImage

/**
 * An LRU cache of decoded Skia [ImageBitmap] instances for [ImageElement]s.
 *
 * Decodes compressed image bytes (PNG, JPEG, WEBP, etc.) once and keeps the decoded bitmap across
 * frames and layout passes. Keyed by the raw byte array reference [ImageElement.data], so moving or
 * scaling an element during interactive gestures (which creates modified [ImageElement] copies with
 * unchanged bytes) reuses the cached bitmap without re-decoding.
 */
internal object DesktopImageCache {
    private const val MAX_ENTRIES = 128

    private val lock = Any()
    private val lruCache = object : LinkedHashMap<Any, ImageBitmap?>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, ImageBitmap?>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    /**
     * Resolves the [ImageBitmap] for [element], decoding and caching if needed.
     * Returns null if decoding fails (e.g. corrupt or invalid bytes).
     */
    fun get(element: ImageElement): ImageBitmap? {
        val key = element.data
        synchronized(lock) {
            if (lruCache.containsKey(key)) {
                return lruCache[key]
            }
        }
        val bitmap = runCatching {
            SkiaImage.makeFromEncoded(element.data).toComposeImageBitmap()
        }.getOrNull()

        synchronized(lock) {
            lruCache[key] = bitmap
        }
        return bitmap
    }

    fun clear() {
        synchronized(lock) {
            lruCache.clear()
        }
    }
}
