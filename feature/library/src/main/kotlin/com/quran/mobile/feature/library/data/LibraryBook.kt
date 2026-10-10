package com.quran.mobile.feature.library.data

/**
 * A library book. Field names match the API's JSON so a real backend maps one to one.
 *
 * [order] positions the book inside its [category] only (10, 20, 30…); it is not an id.
 * [fileUrl] is an http(s) link, a [BUNDLED_BOOK_PREFIX] path for a PDF shipped in the app, or
 * null while the book has no file yet.
 *
 * The fields after [order] describe the book for its shelf and details page; any of them may
 * be missing, and the screens leave out what isn't known. [coverUrl] is a link or a
 * [BUNDLED_BOOK_PREFIX] path like [fileUrl]; without it the cover is drawn.
 */
data class LibraryBook(
  val id: String,
  val bookTitle: String,
  val description: String,
  val published: Boolean,
  val category: LibraryCategory,
  val fileUrl: String?,
  val order: Int,
  val coverUrl: String? = null,
  val author: String? = null,
  val translator: String? = null,
  val language: String? = null,
  val pageCount: Int? = null,
  val fileSizeBytes: Long? = null,
  val tags: List<String> = emptyList(),
  val chapters: List<LibraryChapter> = emptyList()
)

/** A chapter of a book; [page] is 1-based, as printed in the book. */
data class LibraryChapter(val title: String, val page: Int)

/** Body of `POST /api/library`. [category] is a [LibraryCategory.id]; a null [order] goes last. */
data class NewLibraryBook(
  val bookTitle: String,
  val description: String,
  val published: Boolean,
  val category: String,
  val fileUrl: String? = null,
  val order: Int? = null
)

/**
 * Body of `PATCH /api/library/:id`; null fields stay unchanged and an empty [fileUrl] removes
 * the file. Moving a book to another [category] without an [order] puts it last there.
 */
data class LibraryBookPatch(
  val bookTitle: String? = null,
  val description: String? = null,
  val published: Boolean? = null,
  val category: String? = null,
  val fileUrl: String? = null,
  val order: Int? = null
)

/** One entry of `PATCH /api/library/reorder`. */
data class OrderUpdate(val id: String, val order: Int)

/** [LibraryBook.fileUrl] prefix of a PDF bundled in the app's assets. */
const val BUNDLED_BOOK_PREFIX = "file:///android_asset/"

/** The asset path of a book bundled with the app, or null when its file is elsewhere. */
val LibraryBook.bundledAssetPath: String?
  get() = fileUrl
    ?.takeIf { it.startsWith(BUNDLED_BOOK_PREFIX) }
    ?.removePrefix(BUNDLED_BOOK_PREFIX)
    ?.takeIf { it.isNotEmpty() }
