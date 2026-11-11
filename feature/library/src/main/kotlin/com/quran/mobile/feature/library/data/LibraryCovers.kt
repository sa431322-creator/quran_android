package com.quran.mobile.feature.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Cover images: a [BUNDLED_BOOK_PREFIX] path for one shipped in the app, or an http(s) link,
 * which is downloaded once into the cache directory. Decoded covers are kept in memory.
 */
object LibraryCovers {

  // about 8 MB of decoded covers, plenty for a shelf
  private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }

  /** The cover at [url] decoded at least [widthPx] wide (when the image is), or null if it can't be read. */
  suspend fun load(context: Context, url: String, widthPx: Int): Bitmap? {
    val key = "$url@$widthPx"
    memory.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
      try {
        val bitmap = if (url.startsWith(BUNDLED_BOOK_PREFIX)) {
          val asset = url.removePrefix(BUNDLED_BOOK_PREFIX)
          decode(widthPx) { context.assets.open(asset) }
        } else {
          val file = cachedFile(context, url)
          decode(widthPx) { file.inputStream() }
        }
        bitmap?.also { memory.put(key, it) }
      } catch (_: IOException) {
        null
      }
    }
  }

  /** Decodes twice: once for the size, then scaled down by a power of two to about [widthPx]. */
  private fun decode(widthPx: Int, open: () -> InputStream): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return open().use { BitmapFactory.decodeStream(it, null, options) }
  }

  // synchronized so two screens asking for the same new cover don't write one file at once
  @Synchronized
  private fun cachedFile(context: Context, url: String): File {
    val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
      .joinToString("") { "%02x".format(it) }
    val file = File(context.cacheDir, "library/covers/$name.img")
    if (file.exists()) return file
    file.parentFile?.mkdirs()
    val partial = File(file.path + ".part")
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = TIMEOUT_MILLIS
      connection.readTimeout = TIMEOUT_MILLIS
      if (connection.responseCode !in 200..299) throw IOException("cover HTTP ${connection.responseCode}")
      connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
      if (!partial.renameTo(file)) throw IOException("could not store cover")
      return file
    } finally {
      connection.disconnect()
      partial.delete()
    }
  }

  private const val TIMEOUT_MILLIS = 15_000
}
