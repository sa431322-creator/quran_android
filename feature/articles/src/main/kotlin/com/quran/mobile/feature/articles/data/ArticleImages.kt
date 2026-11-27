package com.quran.mobile.feature.articles.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Article covers for the list: downloaded once into the cache directory (so the list keeps its
 * pictures offline) and kept decoded in memory. Article pages load their images in the WebView.
 */
class ArticleImages(private val client: OkHttpClient) {

  private val memory = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }

  suspend fun load(context: Context, url: String): Bitmap? {
    memory.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
      try {
        val file = File(File(context.cacheDir, "article-images"), sha1(url))
        if (!file.exists()) download(url, file)
        BitmapFactory.decodeFile(file.path)?.also { memory.put(url, it) }
      } catch (_: IOException) {
        null
      } catch (_: IllegalArgumentException) {
        null // a link OkHttp can't use (not http/https): no picture, never a crash
      }
    }
  }

  private fun download(url: String, file: File) {
    file.parentFile?.mkdirs()
    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      val tmp = File(file.path + ".tmp")
      response.body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
      if (!tmp.renameTo(file)) throw IOException("could not save $url")
    }
  }

  private fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
