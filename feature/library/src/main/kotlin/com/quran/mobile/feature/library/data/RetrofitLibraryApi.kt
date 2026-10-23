package com.quran.mobile.feature.library.data

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

/**
 * [LibraryApi] backed by the Tasnim server (`tasneem-backend`, `/api/v1/books`).
 * The server only exposes published books to the app; the write calls are admin-only there.
 * Ids stay the decimal strings the app already stores (progress, bookmarks): `14` ↔ `"14"`.
 */
class RetrofitLibraryApi internal constructor(
  private val service: LibraryService,
  private val baseUrl: HttpUrl
) : LibraryApi {

  constructor(client: OkHttpClient, baseUrl: String) : this(
    Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create())
      .build()
      .create(LibraryService::class.java),
    baseUrl.toHttpUrl()
  )

  /**
   * Every published book of [category]: the list comes in pages, read until all `total` came.
   * Drafts are never public, so asking for unpublished books gives nothing.
   */
  override suspend fun getBooks(category: String?, published: Boolean?): List<LibraryBook> {
    if (published == false) return emptyList()
    val books = mutableListOf<BookDto>()
    var page = 1
    while (page <= MAX_PAGES) {
      val answer = call(id = null) { service.books(category, page, PAGE_SIZE) }
      books += answer.items
      if (answer.items.isEmpty() || books.size >= answer.total) break
      page++
    }
    return books.mapNotNull { it.toBook() }
  }

  override suspend fun getBook(id: String): LibraryBook {
    // Older copies of the list used the same numbers as text; anything else cannot exist.
    val number = id.toLongOrNull() ?: throw LibraryApiException.NotFound(id)
    return call(id) { service.book(number) }.toBook() ?: throw LibraryApiException.NotFound(id)
  }

  override suspend fun createBook(book: NewLibraryBook): LibraryBook = adminOnly()

  override suspend fun updateBook(id: String, patch: LibraryBookPatch): LibraryBook = adminOnly()

  override suspend fun reorder(updates: List<OrderUpdate>): List<LibraryBook> = adminOnly()

  private fun adminOnly(): Nothing =
    throw UnsupportedOperationException("books are edited in the Tasnim admin panel")

  /**
   * HTTP errors and answers that can't be read become [LibraryApiException]; a network failure
   * stays an IOException. Screens catch exactly these two, so nothing else may escape.
   */
  private suspend fun <T> call(id: String?, block: suspend () -> T): T =
    try {
      block()
    } catch (e: HttpException) {
      throw when {
        // A missing list means the address is wrong, not that a book is gone.
        e.code() == 404 && id != null -> LibraryApiException.NotFound(id)
        e.code() == 404 -> LibraryApiException.Unavailable("no library at this server address")
        e.code() in 400..499 -> LibraryApiException.Validation("server refused: HTTP ${e.code()}")
        else -> LibraryApiException.Unavailable("server error: HTTP ${e.code()}")
      }
    } catch (e: JsonDataException) {
      throw LibraryApiException.Unavailable("unreadable server answer: ${e.message}")
    } catch (e: JsonEncodingException) {
      throw LibraryApiException.Unavailable("unreadable server answer: ${e.message}")
    }

  /** Unknown categories (a newer server) are skipped instead of breaking the whole shelf. */
  private fun BookDto.toBook(): LibraryBook? {
    val shelf = LibraryCategory.fromId(category) ?: return null
    return LibraryBook(
      id = id.toString(),
      bookTitle = title,
      description = description,
      // `/api/v1` only ever lists published books.
      published = true,
      category = shelf,
      fileUrl = fileUrl?.let(::reachable),
      order = order,
      coverUrl = coverUrl?.let(::reachable),
      author = author,
      translator = translator,
      language = language,
      pageCount = pageCount,
      fileSizeBytes = fileSizeBytes,
      tags = tags,
      chapters = chapters.map { LibraryChapter(it.title, it.page) }
    )
  }

  private fun reachable(url: String): String = ReachableUrl.of(url, baseUrl)

  private companion object {
    /** The server's largest page. */
    const val PAGE_SIZE = 100

    /** 10 000 books; a guard against a server that never says "done". */
    const val MAX_PAGES = 100
  }
}

internal interface LibraryService {
  @GET("api/v1/books")
  suspend fun books(
    @Query("category") category: String?,
    @Query("page") page: Int,
    @Query("limit") limit: Int
  ): BookPageDto

  @GET("api/v1/books/{id}")
  suspend fun book(@Path("id") id: Long): BookDto
}

/** `GET /api/v1/books`: one page of published books, in display order. */
@JsonClass(generateAdapter = true)
internal data class BookPageDto(val items: List<BookDto>, val total: Int)

/**
 * A book from `/api/v1/books` (list) or `/api/v1/books/{id}` (details, which adds chapters).
 * `category` is a [LibraryCategory.id].
 */
@JsonClass(generateAdapter = true)
internal data class BookDto(
  val id: Long,
  val title: String,
  val description: String = "",
  val category: String,
  val fileUrl: String? = null,
  val order: Int,
  val coverUrl: String? = null,
  val author: String? = null,
  val translator: String? = null,
  val language: String? = null,
  val pageCount: Int? = null,
  val fileSizeBytes: Long? = null,
  val tags: List<String> = emptyList(),
  val chapters: List<ChapterDto> = emptyList()
)

@JsonClass(generateAdapter = true)
internal data class ChapterDto(val title: String, val page: Int)
