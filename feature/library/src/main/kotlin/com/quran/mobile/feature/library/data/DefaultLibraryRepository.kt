package com.quran.mobile.feature.library.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
@Inject
class DefaultLibraryRepository(private val api: LibraryApi) : LibraryRepository {

  override suspend fun publishedBooks(category: LibraryCategory?): List<LibraryBook> =
    // filtered again here so an unpublished book never reaches a reader, whatever the server does
    api.getBooks(category?.id, published = true)
      .filter { it.published }
      .sortedWith(LibraryOrdering.displayComparator)

  override suspend fun publishedBook(id: String): LibraryBook? =
    try {
      api.getBook(id).takeIf { it.published }
    } catch (_: LibraryApiException.NotFound) {
      null
    }

  override suspend fun allBooks(category: LibraryCategory?): List<LibraryBook> =
    api.getBooks(category?.id).sortedWith(LibraryOrdering.displayComparator)

  override suspend fun addBook(book: NewLibraryBook): LibraryBook = api.createBook(book)

  override suspend fun updateBook(id: String, patch: LibraryBookPatch): LibraryBook =
    api.updateBook(id, patch)

  override suspend fun moveBook(id: String, toIndex: Int): List<LibraryBook> {
    val book = api.getBook(id)
    val others = allBooks(book.category).filter { it.id != id }
    if (toIndex !in 0..others.size) {
      throw LibraryApiException.Validation("index $toIndex out of 0..${others.size}")
    }

    val order = LibraryOrdering.orderAt(others.map { it.order }, toIndex)
    if (order != null) {
      api.updateBook(id, LibraryBookPatch(order = order))
    } else {
      val ids = others.map { it.id }.toMutableList().apply { add(toIndex, id) }
      api.reorder(LibraryOrdering.renumber(ids))
    }
    return allBooks(book.category)
  }
}
