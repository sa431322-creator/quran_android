package com.quran.mobile.feature.library.data

/**
 * The library endpoints: [RetrofitLibraryApi] talks to the Tasnim server, [MockLibraryApi] serves
 * sample books from memory while the build names no server (see LibraryDataModule).
 *
 * Failures are thrown as [LibraryApiException], or as an IOException when the network is down;
 * a real implementation maps HTTP errors to [LibraryApiException].
 */
interface LibraryApi {
  /** `GET /api/library?category={category}&published={published}`; null skips a filter. */
  suspend fun getBooks(category: String? = null, published: Boolean? = null): List<LibraryBook>

  /** `GET /api/library/:id` */
  suspend fun getBook(id: String): LibraryBook

  /** `POST /api/library` */
  suspend fun createBook(book: NewLibraryBook): LibraryBook

  /** `PATCH /api/library/:id` */
  suspend fun updateBook(id: String, patch: LibraryBookPatch): LibraryBook

  /** `PATCH /api/library/reorder`; applies all updates or none. */
  suspend fun reorder(updates: List<OrderUpdate>): List<LibraryBook>
}

sealed class LibraryApiException(message: String) : Exception(message) {
  class NotFound(val id: String) : LibraryApiException("no book with id $id")
  class Validation(message: String) : LibraryApiException(message)
  class Unavailable(message: String) : LibraryApiException(message)
}
