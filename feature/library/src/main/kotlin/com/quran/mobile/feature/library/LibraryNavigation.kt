package com.quran.mobile.feature.library

import android.content.Context
import android.widget.Toast
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.details.LibraryBookActivity
import com.quran.mobile.feature.library.reader.LibraryReaderActivity

/** Moves between the library's screens. */
internal object LibraryNavigation {

  fun openDetails(context: Context, book: LibraryBook) {
    context.startActivity(LibraryBookActivity.intent(context, book.id))
  }

  /**
   * Opens [book] in the reader at [page] (0-based), or where the reader left it when null. A
   * book that isn't on the device yet opens its details page instead, where it can be downloaded.
   */
  fun openReader(context: Context, book: LibraryBook, page: Int? = null) {
    val intent = LibraryReaderActivity.intent(context, book, page)
    if (intent != null) {
      context.startActivity(intent)
    } else if (book.fileUrl != null) {
      openDetails(context, book)
    } else {
      Toast.makeText(context, R.string.library_file_unavailable, Toast.LENGTH_SHORT).show()
    }
  }
}
