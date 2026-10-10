package com.quran.mobile.feature.library.data

import android.content.Context

/** Where the reader left a book. [page] is 0-based; [pageCount] is 0 when not yet known. */
data class ReadingProgress(val page: Int, val pageCount: Int, val updatedAt: Long) {
  /** How much of the book is read, 0 to 1, or null while the page count is unknown. */
  val fraction: Float?
    get() = if (pageCount > 0) ((page + 1f) / pageCount).coerceIn(0f, 1f) else null

  /** This progress with [count] as the page count when the reader hasn't recorded one yet. */
  fun withPageCount(count: Int?): ReadingProgress =
    if (pageCount == 0 && count != null && count > 0) copy(pageCount = count) else this
}

/**
 * What the library remembers on this device: reading progress, page bookmarks, saved books and
 * the reader's night mode. Every screen reads the same preferences file, so a change made in
 * the reader shows up in the library the next time it reads the store.
 */
class LibraryReadingStore(context: Context) {

  private val preferences =
    context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

  fun progress(bookId: String): ReadingProgress? {
    if (!preferences.contains(pageKey(bookId))) return null
    return ReadingProgress(
      page = preferences.getInt(pageKey(bookId), 0),
      pageCount = preferences.getInt(PAGE_COUNT + bookId, 0),
      updatedAt = preferences.getLong(UPDATED_AT + bookId, 0L)
    )
  }

  /** Progress of every book opened on this device, by book id. */
  fun allProgress(): Map<String, ReadingProgress> =
    preferences.all.keys
      .filter { it.startsWith(PAGE) }
      .map { it.removePrefix(PAGE) }
      .mapNotNull { id -> progress(id)?.let { id to it } }
      .toMap()

  fun saveProgress(bookId: String, page: Int, pageCount: Int, now: Long = System.currentTimeMillis()) {
    preferences.edit()
      .putInt(pageKey(bookId), page)
      .putInt(PAGE_COUNT + bookId, pageCount)
      .putLong(UPDATED_AT + bookId, now)
      .apply()
  }

  /** The bookmarked pages of [bookId], 0-based and ascending. */
  fun bookmarks(bookId: String): List<Int> =
    preferences.getStringSet(BOOKMARKS + bookId, emptySet()).orEmpty()
      .mapNotNull { it.toIntOrNull() }
      .sorted()

  /** Bookmarked pages of every book that has some, by book id. */
  fun allBookmarks(): Map<String, List<Int>> =
    preferences.all.keys
      .filter { it.startsWith(BOOKMARKS) }
      .map { it.removePrefix(BOOKMARKS) }
      .associateWith { bookmarks(it) }
      .filterValues { it.isNotEmpty() }

  /** Adds or removes the bookmark on [page] and returns whether the page is bookmarked now. */
  fun toggleBookmark(bookId: String, page: Int): Boolean {
    // the returned set must not be modified, so work on a copy
    val pages = preferences.getStringSet(BOOKMARKS + bookId, emptySet()).orEmpty().toMutableSet()
    val added = pages.add(page.toString()) || !pages.remove(page.toString())
    preferences.edit().putStringSet(BOOKMARKS + bookId, pages).apply()
    return added
  }

  fun savedBooks(): Set<String> = preferences.getStringSet(SAVED_BOOKS, emptySet()).orEmpty().toSet()

  /** Saves or unsaves [bookId] and returns whether it is saved now. */
  fun toggleSaved(bookId: String): Boolean {
    val saved = savedBooks().toMutableSet()
    val added = saved.add(bookId) || !saved.remove(bookId)
    preferences.edit().putStringSet(SAVED_BOOKS, saved).apply()
    return added
  }

  var nightMode: Boolean
    get() = preferences.getBoolean(NIGHT_MODE, false)
    set(value) = preferences.edit().putBoolean(NIGHT_MODE, value).apply()

  private fun pageKey(bookId: String) = PAGE + bookId

  private companion object {
    // the reader's original file and page key, kept so earlier progress isn't lost
    const val PREFERENCES = "library_reader"
    const val PAGE = "page_"
    const val PAGE_COUNT = "pages_"
    const val UPDATED_AT = "read_at_"
    const val BOOKMARKS = "bookmarks_"
    const val SAVED_BOOKS = "saved_books"
    const val NIGHT_MODE = "night_mode"
  }
}
