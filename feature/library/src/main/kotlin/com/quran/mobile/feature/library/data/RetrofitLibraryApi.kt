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
 * [LibraryApi] backed by the Tasnim server (`tasneem-backend`, legacy `/api/library` contract).
 * The server only exposes published books to the app; the write calls are admin-only there.
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

  override suspend fun getBooks(category: String?, published: Boolean?): List<LibraryBook> =
    call(id = null) { service.books(category, published) }
      .mapNotNull { it.toBook() }

  override suspend fun getBook(id: String): LibraryBook =
    call(id) { service.book(id) }.toBook() ?: throw LibraryApiException.NotFound(id)

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
      id = id,
      bookTitle = bookTitle,
      description = description,
      published = published,
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
}

internal interface LibraryService {
  @GET("api/library")
  suspend fun books(
    @Query("category") category: String?,
    @Query("published") published: Boolean?
  ): List<BookDto>

  @GET("api/library/{id}")
  suspend fun book(@Path("id") id: String): BookDto
}

/** The server's JSON; field names match [LibraryBook], `category` is a [LibraryCategory.id]. */
@JsonClass(generateAdapter = true)
internal data class BookDto(
  val id: String,
  val bookTitle: String,
  val description: String,
  val published: Boolean,
  val category: String,
  val fileUrl: String?,
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
