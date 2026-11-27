package com.quran.mobile.feature.articles.data

import com.quran.common.networking.ReachableUrl
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** One article in the list (`GET /api/v1/articles`), as the control panel published it. */
@JsonClass(generateAdapter = true)
data class ArticleCard(
  val id: Long,
  val title: String,
  val summary: String,
  val author: String?,
  val coverUrl: String?,
  val thumbUrl: String?,
  /** ISO time shown as the article's date. */
  val publishedAt: String,
  val updatedAt: String
)

/** A whole article: the card plus its sanitised HTML body (`GET /api/v1/articles/{id}`). */
@JsonClass(generateAdapter = true)
data class Article(
  val id: Long,
  val title: String,
  val summary: String,
  val author: String?,
  val coverUrl: String?,
  val thumbUrl: String?,
  val publishedAt: String,
  val updatedAt: String,
  val html: String
)

@JsonClass(generateAdapter = true)
data class ArticlePage(
  val items: List<ArticleCard>,
  /** Pass as `before` for the next page; null = no more. */
  val next: String?
)

/** The server refused or answered something unreadable (a network failure stays an IOException). */
sealed class ArticlesApiException(message: String) : Exception(message) {
  class NotFound(id: Long) : ArticlesApiException("no published article $id")
  class Unavailable(why: String) : ArticlesApiException(why)
}

interface ArticlesApi {
  suspend fun page(before: String?): ArticlePage

  suspend fun article(id: Long): Article
}

/** [ArticlesApi] on the Tasnim server (tasneem-backend, ADR 0016). */
class RetrofitArticlesApi internal constructor(
  private val service: ArticlesService,
  private val baseUrl: HttpUrl
) : ArticlesApi {

  constructor(client: OkHttpClient, baseUrl: String) : this(
    Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create())
      .build()
      .create(ArticlesService::class.java),
    baseUrl.toHttpUrl()
  )

  override suspend fun page(before: String?): ArticlePage =
    call(null) { service.list(PAGE_SIZE, before) }.let { page ->
      page.copy(items = page.items.map { it.copy(coverUrl = reachable(it.coverUrl), thumbUrl = reachable(it.thumbUrl)) })
    }

  override suspend fun article(id: Long): Article =
    call(id) { service.article(id) }.let { a ->
      a.copy(
        coverUrl = reachable(a.coverUrl),
        thumbUrl = reachable(a.thumbUrl),
        html = reachableImages(a.html)
      )
    }

  private fun reachable(url: String?): String? = url?.let { ReachableUrl.of(it, baseUrl) }

  /** Images in the body are links on the server; a dev server's localhost ones are pointed at it. */
  private fun reachableImages(html: String): String =
    IMG_SRC.replace(html) { m -> m.groupValues[1] + ReachableUrl.of(m.groupValues[2], baseUrl) + "\"" }

  private suspend fun <T> call(id: Long?, block: suspend () -> T): T =
    try {
      block()
    } catch (e: HttpException) {
      throw when {
        e.code() == 404 && id != null -> ArticlesApiException.NotFound(id)
        else -> ArticlesApiException.Unavailable("server answered HTTP ${e.code()}")
      }
    } catch (e: JsonDataException) {
      throw ArticlesApiException.Unavailable("unreadable server answer: ${e.message}")
    } catch (e: JsonEncodingException) {
      throw ArticlesApiException.Unavailable("unreadable server answer: ${e.message}")
    }

  private companion object {
    const val PAGE_SIZE = 20
    val IMG_SRC = Regex("(<img[^>]*\\ssrc=\")([^\"]*)\"")
  }
}

internal interface ArticlesService {
  @GET("api/v1/articles")
  suspend fun list(@Query("limit") limit: Int, @Query("before") before: String?): ArticlePage

  @GET("api/v1/articles/{id}")
  suspend fun article(@Path("id") id: Long): Article
}

/** No server in this build (release before a domain is chosen): there are no articles yet. */
class NoArticlesApi : ArticlesApi {
  override suspend fun page(before: String?) = ArticlePage(emptyList(), null)

  override suspend fun article(id: Long): Article = throw ArticlesApiException.NotFound(id)
}
