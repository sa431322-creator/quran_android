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

  // Exactly what tasneem-backend's GET /api/v1/books returns per book (contract `Book`).
  private val tafsir = """
    {"id":16,"title":"تفسیر نور","description":"…","category":"tafsir","order":10,
     "author":"دکتر مصطفی خرم‌دل","translator":null,"language":"فارسی","pageCount":2029,
     "fileUrl":"http://localhost:3000/files/books/16-732cf0f7.pdf","fileSizeBytes":12345,
     "coverUrl":"https://cdn.example.org/files/covers/16-1a2b3c4d.webp",
     "coverThumbUrl":"https://cdn.example.org/files/covers/16-1a2b3c4d-300.webp",
     "tags":["تفسیر"],"publishedAt":"2026-10-20T10:00:00.000Z","updatedAt":"2026-10-21T10:00:00.000Z"}
  """.trimIndent()

  private fun page(total: Int, vararg books: String) =
    """{"items":[${books.joinToString(",")}],"total":$total,"page":1,"limit":100}"""

  private fun book(id: Int) = tafsir.replace("\"id\":16", "\"id\":$id")

  @Test
  fun `maps the server's books and sends the filters`() = runTest {
    reply(200, page(1, tafsir))

    val books = api.getBooks(category = "tafsir", published = true)

    val request = server.takeRequest()
    assertThat(request.path).isEqualTo("/api/v1/books?category=tafsir&page=1&limit=100")
    assertThat(books).hasSize(1)
    val book = books.single()
    assertThat(book.id).isEqualTo("16")
    assertThat(book.bookTitle).isEqualTo("تفسیر نور")
    assertThat(book.published).isTrue()
    assertThat(book.category).isEqualTo(LibraryCategory.TAFSIR)
    assertThat(book.order).isEqualTo(10)
    assertThat(book.translator).isNull()
    assertThat(book.fileSizeBytes).isEqualTo(12345L)
    assertThat(book.tags).containsExactly("تفسیر")
    // The list has no chapters; the details screen reads them from the book itself.
    assertThat(book.chapters).isEmpty()
  }

  @Test
  fun `reads every page of a long list`() = runTest {
    reply(200, page(3, book(1), book(2)))
    reply(200, page(3, book(3)))

    val books = api.getBooks()

    assertThat(books.map { it.id }).containsExactly("1", "2", "3").inOrder()
    assertThat(server.takeRequest().path).isEqualTo("/api/v1/books?page=1&limit=100")
    assertThat(server.takeRequest().path).isEqualTo("/api/v1/books?page=2&limit=100")
    assertThat(server.requestCount).isEqualTo(2)
  }

  @Test
  fun `stops when a page comes back empty even if the total says more`() = runTest {
    reply(200, page(5, book(1)))
    reply(200, page(5))

    assertThat(api.getBooks().map { it.id }).containsExactly("1")
    assertThat(server.requestCount).isEqualTo(2)
  }

  @Test
  fun `drafts are never public, so asking for them asks nothing`() = runTest {
    assertThat(api.getBooks(published = false)).isEmpty()
    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun `points localhost links at the server address and leaves real hosts alone`() = runTest {
    reply(200, page(1, tafsir))

    val book = api.getBooks().single()

    val expected = server.url("/files/books/16-732cf0f7.pdf").toString()
    assertThat(book.fileUrl).isEqualTo(expected)
    assertThat(book.coverUrl).isEqualTo("https://cdn.example.org/files/covers/16-1a2b3c4d.webp")
  }

  @Test
  fun `skips books on a shelf this app version does not know`() = runTest {
    reply(200, page(2, tafsir, book(99).replace("\"tafsir\"", "\"new-shelf\"")))

    assertThat(api.getBooks().map { it.id }).containsExactly("16")
  }

  @Test
  fun `reads one book with its chapters, and missing fields fall back to their defaults`() =
    runTest {
      reply(
        200,
        """{"id":7,"title":"x","category":"tajweed","order":20,"fileUrl":null,
           "chapters":[{"title":"مقدمه","page":7}]}"""
      )

      val book = api.getBook("7")

      assertThat(server.takeRequest().path).isEqualTo("/api/v1/books/7")
      assertThat(book.id).isEqualTo("7")
      assertThat(book.fileUrl).isNull()
      assertThat(book.description).isEmpty()
      assertThat(book.tags).isEmpty()
      assertThat(book.chapters).containsExactly(LibraryChapter("مقدمه", 7))
    }

  @Test
  fun `an id that is not a number cannot exist on the server`() = runTest {
    val notFound = runCatching { api.getBook("abc") }.exceptionOrNull()
    assertThat(notFound).isInstanceOf(LibraryApiException.NotFound::class.java)
    assertThat(server.requestCount).isEqualTo(0)
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
    reply(200, """{"items":[{"id":16,"title":"x","pageCount":"many"}],"total":1}""")
    assertThat(runCatching { api.getBooks() }.exceptionOrNull())
      .isInstanceOf(LibraryApiException.Unavailable::class.java)

    // The old /api/library answer (a plain list) is not readable as a page.
    reply(200, "[$tafsir]")
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
