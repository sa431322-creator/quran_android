package com.quran.mobile.feature.library.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An in-memory [LibraryApi] that behaves like the backend will: it validates requests,
 * answers after a short delay and keeps writes until the process dies. Swap the binding
 * to a Retrofit implementation once the real API exists.
 *
 * It serves [MockLibraryData] in the app; tests give it their own [seed].
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MockLibraryApi internal constructor(seed: List<LibraryBook>) : LibraryApi {

  @Inject
  constructor() : this(MockLibraryData.BOOKS)

  /** What the mock answers with, to try the screen's empty and error states. */
  enum class Scenario { NORMAL, EMPTY, ERROR }

  internal var scenario = Scenario.NORMAL
  internal var latencyMillis = 400L

  private val mutex = Mutex()
  private val books = seed.associateBy { it.id }.toMutableMap()
  private var nextId = (books.keys.maxOfOrNull { it.toInt() } ?: 0) + 1

  override suspend fun getBooks(category: String?, published: Boolean?): List<LibraryBook> =
    respond {
      val wanted = category?.let { requireCategory(it) }
      if (scenario == Scenario.EMPTY) return@respond emptyList()
      books.values
        .filter { wanted == null || it.category == wanted }
        .filter { published == null || it.published == published }
        .sortedWith(LibraryOrdering.displayComparator)
    }

  override suspend fun getBook(id: String): LibraryBook = respond { requireBook(id) }

  override suspend fun createBook(book: NewLibraryBook): LibraryBook = respond {
    val category = requireCategory(book.category)
    val order = book.order?.also { requireOrder(it) } ?: nextOrderIn(category)
    val title = requireText(book.bookTitle, "bookTitle")
    val created = LibraryBook(
      id = (nextId++).toString(),
      bookTitle = title,
      description = book.description.trim(),
      published = book.published,
      category = category,
      fileUrl = book.fileUrl?.trim()?.ifEmpty { null },
      order = order
    )
    books[created.id] = created
    created
  }

  override suspend fun updateBook(id: String, patch: LibraryBookPatch): LibraryBook = respond {
    val book = requireBook(id)
    val category = patch.category?.let { requireCategory(it) } ?: book.category
    val order = when {
      patch.order != null -> patch.order.also { requireOrder(it) }
      category != book.category -> nextOrderIn(category)
      else -> book.order
    }
    val updated = book.copy(
      bookTitle = patch.bookTitle?.let { requireText(it, "bookTitle") } ?: book.bookTitle,
      description = patch.description?.trim() ?: book.description,
      published = patch.published ?: book.published,
      category = category,
      fileUrl = if (patch.fileUrl == null) book.fileUrl else patch.fileUrl.trim().ifEmpty { null },
      order = order
    )
    books[id] = updated
    updated
  }

  override suspend fun reorder(updates: List<OrderUpdate>): List<LibraryBook> = respond {
    if (updates.isEmpty()) throw LibraryApiException.Validation("reorder needs at least one book")
    if (updates.map { it.id }.toSet().size != updates.size) {
      throw LibraryApiException.Validation("reorder lists a book twice")
    }
    // validate everything first so a bad entry changes nothing
    updates.forEach { requireBook(it.id); requireOrder(it.order) }
    updates.map { update ->
      books.getValue(update.id).copy(order = update.order).also { books[it.id] = it }
    }
  }

  private suspend fun <T> respond(block: () -> T): T {
    delay(latencyMillis)
    return mutex.withLock {
      if (scenario == Scenario.ERROR) throw LibraryApiException.Unavailable("mock server error")
      block()
    }
  }

  private fun nextOrderIn(category: LibraryCategory): Int =
    LibraryOrdering.nextOrder(books.values.filter { it.category == category }.map { it.order })

  private fun requireBook(id: String): LibraryBook =
    books[id] ?: throw LibraryApiException.NotFound(id)

  private fun requireCategory(id: String): LibraryCategory =
    LibraryCategory.fromId(id) ?: throw LibraryApiException.Validation("unknown category $id")

  private fun requireOrder(order: Int) {
    if (order <= 0) throw LibraryApiException.Validation("order must be positive, was $order")
  }

  private fun requireText(value: String, field: String): String =
    value.trim().ifEmpty { throw LibraryApiException.Validation("$field is required") }
}
