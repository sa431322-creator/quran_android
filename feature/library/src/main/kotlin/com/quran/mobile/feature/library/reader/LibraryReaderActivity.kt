package com.quran.mobile.feature.library.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryChapter
import com.quran.mobile.feature.library.data.LibraryFiles
import com.quran.mobile.feature.library.data.LibraryReadingStore
import com.quran.mobile.feature.library.data.bundledAssetPath
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** Reads a book's PDF from the device, one page at a time. */
class LibraryReaderActivity : AppCompatActivity() {

  private val scope = MainScope()
  private var pages: PdfPages? = null
  private lateinit var store: LibraryReadingStore
  private lateinit var bookId: String

  private var state by mutableStateOf<ReaderState>(ReaderState.Loading)
  private var bookmarks by mutableStateOf(emptyList<Int>())
  private var nightMode by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
    val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
    val assetPath = intent.getStringExtra(EXTRA_ASSET_PATH)
    val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
    // a page asked for by the caller, kept only for the first opening and not after rotation
    val requestedPage = intent.getIntExtra(EXTRA_PAGE, -1).takeIf { it >= 0 && savedInstanceState == null }
    val chapters = chaptersFrom(intent)
    store = LibraryReadingStore(this)
    bookmarks = store.bookmarks(bookId)
    nightMode = store.nightMode

    if (assetPath == null && filePath == null) {
      state = ReaderState.Error
    } else {
      scope.launch {
        state = try {
          val file = if (assetPath != null) PdfPages.bundledFile(applicationContext, assetPath) else File(filePath!!)
          // back on the main thread and still active, so onDestroy will close it
          val opened = PdfPages.open(file)
          pages = opened
          if (opened.pageCount == 0) {
            ReaderState.Error
          } else {
            val start = requestedPage ?: store.progress(bookId)?.page ?: 0
            ReaderState.Ready(opened, start.coerceIn(0, opened.pageCount - 1))
          }
        } catch (_: IOException) {
          ReaderState.Error
        } catch (_: SecurityException) {
          // thrown by PdfRenderer for a password-protected PDF
          ReaderState.Error
        }
      }
    }

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        LibraryReaderScreen(
          title = title,
          state = state,
          chapters = chapters,
          bookmarks = bookmarks,
          nightMode = nightMode,
          onPageShown = { page, pageCount -> store.saveProgress(bookId, page, pageCount) },
          onToggleBookmark = { page ->
            store.toggleBookmark(bookId, page)
            bookmarks = store.bookmarks(bookId)
          },
          onToggleNightMode = {
            nightMode = !nightMode
            store.nightMode = nightMode
          },
          onBack = onBackPressedDispatcher::onBackPressed
        )
      }
    }
  }

  override fun onDestroy() {
    scope.cancel()
    pages?.close()
    super.onDestroy()
  }

  companion object {
    private const val EXTRA_BOOK_ID = "book_id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_ASSET_PATH = "asset_path"
    private const val EXTRA_FILE_PATH = "file_path"
    private const val EXTRA_PAGE = "page"
    private const val EXTRA_CHAPTER_TITLES = "chapter_titles"
    private const val EXTRA_CHAPTER_PAGES = "chapter_pages"

    /**
     * Opens [book] at [page] (0-based), or where the reader left it when null. Null when the
     * book's PDF isn't on the device.
     */
    fun intent(context: Context, book: LibraryBook, page: Int? = null): Intent? {
      val intent = Intent(context, LibraryReaderActivity::class.java)
        .putExtra(EXTRA_BOOK_ID, book.id)
        .putExtra(EXTRA_TITLE, book.bookTitle)
        .putExtra(EXTRA_CHAPTER_TITLES, book.chapters.map { it.title }.toTypedArray())
        .putExtra(EXTRA_CHAPTER_PAGES, book.chapters.map { it.page }.toIntArray())
      if (page != null) intent.putExtra(EXTRA_PAGE, page)

      val assetPath = book.bundledAssetPath
      val downloaded = LibraryFiles.downloadedFile(context, book.id)
      return when {
        assetPath != null -> intent.putExtra(EXTRA_ASSET_PATH, assetPath)
        downloaded.exists() -> intent.putExtra(EXTRA_FILE_PATH, downloaded.path)
        else -> null
      }
    }

    private fun chaptersFrom(intent: Intent): List<LibraryChapter> {
      val titles = intent.getStringArrayExtra(EXTRA_CHAPTER_TITLES) ?: return emptyList()
      val pages = intent.getIntArrayExtra(EXTRA_CHAPTER_PAGES) ?: return emptyList()
      return titles.zip(pages.toList()) { title, page -> LibraryChapter(title, page) }
    }
  }
}

sealed interface ReaderState {
  data object Loading : ReaderState
  data object Error : ReaderState
  /** [startPage] is where the reader opens: the page asked for, or where it was left. */
  data class Ready(val pages: PdfPages, val startPage: Int) : ReaderState
}
