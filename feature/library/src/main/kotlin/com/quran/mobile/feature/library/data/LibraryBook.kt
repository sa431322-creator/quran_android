package com.quran.mobile.feature.library.data

/**
 * A library book. Field names match the API's JSON so a real backend maps one to one.
 *
 * [order] positions the book inside its [category] only (10, 20, 30…); it is not an id.
 * [fileUrl] is null while the book has no file yet.
 */
data class LibraryBook(
  val id: String,
  val bookTitle: String,
  val description: String,
  val published: Boolean,
  val category: LibraryCategory,
  val fileUrl: String?,
  val order: Int
)

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
