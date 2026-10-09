package com.quran.mobile.feature.library.data

/**
 * The library for the app. The published methods are what readers see; the rest are for
 * management and also return unpublished books. Lists are always in display order.
 *
 * Failures are thrown as [LibraryApiException].
 */
interface LibraryRepository {
  /** Published books of [category], or of every category when null. */
  suspend fun publishedBooks(category: LibraryCategory? = null): List<LibraryBook>

  /** A published book, or null when it doesn't exist or isn't published. */
  suspend fun publishedBook(id: String): LibraryBook?

  /** Every book of [category] (or of all categories), published or not. */
  suspend fun allBooks(category: LibraryCategory? = null): List<LibraryBook>

  suspend fun addBook(book: NewLibraryBook): LibraryBook

  suspend fun updateBook(id: String, patch: LibraryBookPatch): LibraryBook

  /**
   * Moves a book to [toIndex] within its category and returns the category in its new
   * order. Only the moved book changes unless there's no gap left, in which case the whole
   * category is renumbered 10, 20, 30…
   */
  suspend fun moveBook(id: String, toIndex: Int): List<LibraryBook>
}
