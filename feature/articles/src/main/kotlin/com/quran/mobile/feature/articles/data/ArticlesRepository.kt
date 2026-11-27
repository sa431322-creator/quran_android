package com.quran.mobile.feature.articles.data

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A result plus whether it came from the saved copy because the server could not be reached. */
data class Loaded<T>(val value: T, val offline: Boolean)

/**
 * Articles from the server, with the last list and every opened article saved in [dir], so they
 * can be read again without internet. Images inside an article come from the WebView's cache.
 */
class ArticlesRepository(
  private val api: ArticlesApi,
  private val dir: File
) {
  private val moshi = Moshi.Builder().build()
  private val pageAdapter = moshi.adapter(ArticlePage::class.java)
  private val articleAdapter = moshi.adapter(Article::class.java)

  /** The newest articles; the saved first page when offline. Throws when there is neither. */
  suspend fun firstPage(): Loaded<ArticlePage> =
    try {
      val page = api.page(null)
      save(LIST_FILE) { pageAdapter.toJson(page) }
      Loaded(page, offline = false)
    } catch (e: IOException) {
      read(LIST_FILE) { pageAdapter.fromJson(it) }?.let { Loaded(it, offline = true) } ?: throw e
    }

  /** Older articles after the cursor [before] (never saved: only the first page is). */
  suspend fun olderPage(before: String): ArticlePage = api.page(before)

  /** One article; the saved copy when offline. A removed article also deletes its saved copy. */
  suspend fun article(id: Long): Loaded<Article> {
    val file = "article-$id.json"
    return try {
      val article = api.article(id)
      save(file) { articleAdapter.toJson(article) }
      Loaded(article, offline = false)
    } catch (e: ArticlesApiException.NotFound) {
      withContext(Dispatchers.IO) { File(dir, file).delete() }
      throw e
    } catch (e: IOException) {
      read(file) { articleAdapter.fromJson(it) }?.let { Loaded(it, offline = true) } ?: throw e
    }
  }

  private suspend fun save(name: String, json: () -> String) = withContext(Dispatchers.IO) {
    try {
      dir.mkdirs()
      val tmp = File(dir, "$name.tmp")
      tmp.writeText(json())
      tmp.renameTo(File(dir, name))
    } catch (_: IOException) {
      // a full disk only costs the offline copy
    }
  }

  private suspend fun <T> read(name: String, parse: (String) -> T?): T? = withContext(Dispatchers.IO) {
    try {
      File(dir, name).takeIf { it.exists() }?.readText()?.let(parse)
    } catch (_: Exception) {
      null // an unreadable old copy is as good as none
    }
  }

  private companion object {
    const val LIST_FILE = "list.json"
  }
}
