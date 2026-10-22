package com.quran.mobile.feature.library.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class RetrofitLibraryApiTest {
  private val server = MockWebServer()
  private lateinit var api: RetrofitLibraryApi

  @Before
  fun setUp() {
    server.start()
    api = RetrofitLibraryApi(OkHttpClient(), server.url("/").toString())
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun reply(code: Int, body: String) {
    server.enqueue(MockResponse().setResponseCode(code).setBody(body))
  }

  // Exactly what tasneem-backend's GET /api/library returns (see its legacy snapshot).
  private val tafsir = """
    {"id":"16","bookTitle":"تفسیر نور","description":"…","published":true,"category":"tafsir",
     "fileUrl":"http://localhost:3000/files/books/16-732cf0f7.pdf","order":10,
     "coverUrl":"https://cdn.example.org/files/covers/16-1a2b3c4d.webp","author":"دکتر مصطفی خرم‌دل",
     "translator":null,"language":"فارسی","pageCount":2029,"fileSizeBytes":12345,
     "tags":["تفسیر"],"chapters":[{"title":"مقدمه","page":7}]}
  """.trimIndent()

  @Test
  fun `maps the server's books and sends the filters`() = runTest {
    reply(200, "[$tafsir]")

    val books = api.getBooks(category = "tafsir", published = true)

    val request = server.takeRequest()
    assertThat(request.path).isEqualTo("/api/library?category=tafsir&published=true")
    assertThat(books).hasSize(1)
    val book = books.single()
    assertThat(book.id).isEqualTo("16")
    assertThat(book.category).isEqualTo(LibraryCategory.TAFSIR)
    assertThat(book.order).isEqualTo(10)
    assertThat(book.translator).isNull()
    assertThat(book.fileSizeBytes).isEqualTo(12345L)
    assertThat(book.tags).containsExactly("تفسیر")
    assertThat(book.chapters).containsExactly(LibraryChapter("مقدمه", 7))
  }

  @Test
  fun `points localhost links at the server address and leaves real hosts alone`() = runTest {
    reply(200, "[$tafsir]")

    val book = api.getBooks().single()

    val expected = server.url("/files/books/16-732cf0f7.pdf").toString()
    assertThat(book.fileUrl).isEqualTo(expected)
    assertThat(book.coverUrl).isEqualTo("https://cdn.example.org/files/covers/16-1a2b3c4d.webp")
  }

  @Test
  fun `skips books on a shelf this app version does not know`() = runTest {
    reply(200, "[$tafsir, ${tafsir.replace("\"tafsir\"", "\"new-shelf\"").replace("\"16\"", "\"99\"")}]")

    assertThat(api.getBooks().map { it.id }).containsExactly("16")
  }

  @Test
  fun `reads one book, and missing fields fall back to their defaults`() = runTest {
    reply(
      200,
      """{"id":"7","bookTitle":"x","description":"","published":true,"category":"tajweed",
         "fileUrl":null,"order":20}"""
    )

    val book = api.getBook("7")

    assertThat(server.takeRequest().path).isEqualTo("/api/library/7")
    assertThat(book.fileUrl).isNull()
    assertThat(book.tags).isEmpty()
    assertThat(book.chapters).isEmpty()
  }

  @Test
  fun `maps HTTP errors to LibraryApiException`() = runTest {
    reply(404, """{"error":{"code":"NotFound","message":"No such book","requestId":"r"}}""")
    val notFound = runCatching { api.getBook("404") }.exceptionOrNull()
    assertThat(notFound).isInstanceOf(LibraryApiException.NotFound::class.java)
    assertThat((notFound as LibraryApiException.NotFound).id).isEqualTo("404")

    reply(422, """{"error":{"code":"Validation","message":"bad","requestId":"r"}}""")
    assertThat(runCatching { api.getBooks() }.exceptionOrNull())
      .isInstanceOf(LibraryApiException.Validation::class.java)

    reply(503, "")
    assertThat(runCatching { api.getBooks() }.exceptionOrNull())
      .isInstanceOf(LibraryApiException.Unavailable::class.java)
  }

  @Test
  fun `a missing list means the server address is wrong, not a missing book`() = runTest {
    reply(404, "")
    assertThat(runCatching { api.getBooks() }.exceptionOrNull())
      .isInstanceOf(LibraryApiException.Unavailable::class.java)
  }

  @Test
  fun `an answer that cannot be read is Unavailable, never a crash`() = runTest {
    reply(200, """[{"id":"16","bookTitle":"x","pageCount":"many"}]""")
    assertThat(runCatching { api.getBooks() }.exceptionOrNull())
      .isInstanceOf(LibraryApiException.Unavailable::class.java)

    reply(200, "not json at all")
    assertThat(runCatching { api.getBooks() }.exceptionOrNull())
      .isInstanceOf(LibraryApiException.Unavailable::class.java)
  }

  @Test
  fun `a network failure stays an IOException`() = runTest {
    server.shutdown()
    assertThat(runCatching { api.getBooks() }.exceptionOrNull())
      .isInstanceOf(java.io.IOException::class.java)
  }

  @Test
  fun `a book on an unknown shelf is not found`() = runTest {
    reply(200, tafsir.replace("\"tafsir\"", "\"new-shelf\""))

    assertThat(runCatching { api.getBook("16") }.exceptionOrNull())
      .isInstanceOf(LibraryApiException.NotFound::class.java)
  }

  @Test
  fun `writes are refused because books are edited in the admin panel`() {
    assertThrows(UnsupportedOperationException::class.java) {
      kotlinx.coroutines.runBlocking { api.reorder(emptyList()) }
    }
    assertThat(server.requestCount).isEqualTo(0)
  }
}
