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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/** Reads a PDF bundled with the app, one page at a time. */
class LibraryReaderActivity : AppCompatActivity() {

  private val scope = MainScope()
  private var pages: PdfPages? = null
  private var state by mutableStateOf<ReaderState>(ReaderState.Loading)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
    val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
    val assetPath = intent.getStringExtra(EXTRA_ASSET_PATH)
    val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
    val pageKey = "page_$bookId"

    if (assetPath == null) {
      state = ReaderState.Error
    } else {
      scope.launch {
        state = try {
          val file = PdfPages.bundledFile(applicationContext, assetPath)
          // back on the main thread and still active, so onDestroy will close it
          val opened = PdfPages.open(file)
          pages = opened
          if (opened.pageCount == 0) {
            ReaderState.Error
          } else {
            val lastPage = preferences.getInt(pageKey, 0).coerceIn(0, opened.pageCount - 1)
            ReaderState.Ready(opened, lastPage)
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
          onPageShown = { page -> preferences.edit().putInt(pageKey, page).apply() },
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
    private const val PREFERENCES = "library_reader"

    fun intent(context: Context, bookId: String, title: String, assetPath: String): Intent =
      Intent(context, LibraryReaderActivity::class.java)
        .putExtra(EXTRA_BOOK_ID, bookId)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_ASSET_PATH, assetPath)
  }
}

sealed interface ReaderState {
  data object Loading : ReaderState
  data object Error : ReaderState
  /** [startPage] is where the reader left the book last time. */
  data class Ready(val pages: PdfPages, val startPage: Int) : ReaderState
}
