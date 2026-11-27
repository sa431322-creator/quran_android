package com.quran.mobile.feature.articles

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.articles.data.Article
import com.quran.mobile.feature.articles.data.ArticlePage
import com.quran.mobile.feature.articles.data.ArticlesApi
import com.quran.mobile.feature.articles.data.ArticlesApiException
import com.quran.mobile.feature.articles.data.ArticlesRepository
import com.quran.mobile.feature.articles.data.RetrofitArticlesApi
import com.quran.mobile.feature.articles.ui.ArticleHtml
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ArticlesTest {

  @get:Rule val tmp = TemporaryFolder()

  private val server = MockWebServer()

  @Before fun start() = server.start()

  @After fun stop() = server.shutdown()

  private fun api() = RetrofitArticlesApi(OkHttpClient(), server.url("/").toString())

  private val articleJson = """
    {"id":7,"title":"عنوان","summary":"خلاصه","author":null,
     "coverUrl":"http://localhost:3000/files/articles/c7-0123abcd.webp",
     "thumbUrl":"http://localhost:3000/files/articles/c7-0123abcd-300.webp",
     "publishedAt":"2026-11-27T10:00:00.000Z","updatedAt":"2026-11-27T10:00:00.000Z",
     "html":"<p>متن</p><p><img src=\"http://localhost:3000/files/articles/7-0123abcd.webp\" alt=\"\" /></p>"}
  """.trimIndent()

  @Test
  fun `reads a page and points localhost links at the server`() = runTest {
    val card = """{"id":7,"title":"عنوان","summary":"","author":null,
      "coverUrl":"http://localhost:3000/files/articles/c7-0123abcd.webp","thumbUrl":null,
      "publishedAt":"2026-11-27T10:00:00.000Z","updatedAt":"2026-11-27T10:00:00.000Z"}"""
    server.enqueue(MockResponse().setBody("""{"items":[$card],"next":"2026-11-27T10:00:00.000Z|7"}"""))
    val page = api().page(null)
    assertThat(page.items.map { it.id }).containsExactly(7L)
    assertThat(page.next).isEqualTo("2026-11-27T10:00:00.000Z|7")
    assertThat(page.items[0].coverUrl).startsWith("http://${server.hostName}:${server.port}/files/")
    assertThat(server.takeRequest().path).isEqualTo("/api/v1/articles?limit=20")
  }

  @Test
  fun `reads an article and rewrites its image links`() = runTest {
    server.enqueue(MockResponse().setBody(articleJson))
    val a = api().article(7)
    assertThat(a.html).contains("src=\"http://${server.hostName}:${server.port}/files/articles/7-0123abcd.webp\"")
    assertThat(server.takeRequest().path).isEqualTo("/api/v1/articles/7")
  }

  @Test(expected = ArticlesApiException.NotFound::class)
  fun `a missing article is NotFound`() = runTest {
    server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
    api().article(9)
  }

  @Test
  fun `the saved copy is used offline and removed when the article is gone`() = runTest {
    val article = Article(7, "t", "", null, null, null, "2026-11-27T10:00:00.000Z", "x", "<p>a</p>")
    var mode = "online"
    val fake = object : ArticlesApi {
      override suspend fun page(before: String?): ArticlePage = when (mode) {
        "online" -> ArticlePage(emptyList(), null)
        else -> throw IOException("offline")
      }

      override suspend fun article(id: Long): Article = when (mode) {
        "online" -> article
        "gone" -> throw ArticlesApiException.NotFound(id)
        else -> throw IOException("offline")
      }
    }
    val repo = ArticlesRepository(fake, tmp.root)
    assertThat(repo.article(7).offline).isFalse()
    assertThat(repo.firstPage().offline).isFalse()

    mode = "offline"
    assertThat(repo.article(7)).isEqualTo(com.quran.mobile.feature.articles.data.Loaded(article, offline = true))
    assertThat(repo.firstPage().offline).isTrue()

    mode = "gone"
    runCatching { repo.article(7) }
    mode = "offline"
    assertThat(runCatching { repo.article(7) }.exceptionOrNull()).isInstanceOf(IOException::class.java)
  }

  @Test
  fun `the page escapes the plain-text parts and keeps the article HTML`() {
    val a = Article(1, "<b>t</b> & co", "s\"q", null, "https://x/c.webp", null, "d", "u", "<p><strong>متن</strong></p>")
    val page = ArticleHtml.page(a, byline = "تسنیم", dark = true)
    assertThat(page).contains("<html lang=\"fa\" dir=\"rtl\" class=\"dark\">")
    assertThat(page).contains("<h1 class=\"title\">&lt;b&gt;t&lt;/b&gt; &amp; co</h1>")
    assertThat(page).contains("<p class=\"summary\">s&quot;q</p>")
    assertThat(page).contains("<div class=\"article-body\"><p><strong>متن</strong></p></div>")
    assertThat(page).contains("<img class=\"cover\" alt=\"\" src=\"https://x/c.webp\">")
  }
}
