package com.quran.mobile.feature.library.details

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.library.LibraryNavigation
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.DownloadState
import com.quran.mobile.feature.library.data.LibraryApiException
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryDownloads
import com.quran.mobile.feature.library.data.LibraryFiles
import com.quran.mobile.feature.library.data.LibraryReadingStore
import com.quran.mobile.feature.library.data.LibraryRepository
import com.quran.mobile.feature.library.data.ReadingProgress
import com.quran.mobile.feature.library.di.LibraryComponentInterface
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/** A book's details page: its cover, facts, progress, description, chapters and similar books. */
class LibraryBookActivity : AppCompatActivity() {

  @Inject
  lateinit var repository: LibraryRepository

  private val scope = MainScope()
  private lateinit var store: LibraryReadingStore
  private lateinit var bookId: String
  private var loadJob: Job? = null

  private var state by mutableStateOf<BookDetailsState>(BookDetailsState.Loading)
  private var device by mutableStateOf(BookOnDevice())
  private var download by mutableStateOf<DownloadState>(DownloadState.Idle)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LibraryComponentInterface
    injector?.libraryComponentFactory()?.generate()?.inject(this)
    store = LibraryReadingStore(this)
    bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
    load()
    // the download itself lives in LibraryDownloads, so it carries on across rotation
    scope.launch {
      LibraryDownloads.states.collect {
        download = it[bookId] ?: DownloadState.Idle
        refreshDevice()
      }
    }

    // the page opens on the teal band, so the status bar keeps light icons
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

    setContent {
      QuranTheme {
        BookDetailsScreen(
          state = state,
          device = device,
          download = download,
          onBack = onBackPressedDispatcher::onBackPressed,
          onRetry = ::load,
          onToggleSaved = {
            store.toggleSaved(bookId)
            refreshDevice()
          },
          onShare = ::share,
          onRead = { page -> readyBook()?.let { LibraryNavigation.openReader(this, it, page) } },
          onDownload = ::startDownload,
          onOpenBook = { LibraryNavigation.openDetails(this, it) }
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // the reader may have moved on since this page was last shown
    refreshDevice()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun load() {
    loadJob?.cancel()
    state = BookDetailsState.Loading
    loadJob = scope.launch {
      state = try {
        val book = repository.publishedBook(bookId)
        if (book == null) {
          BookDetailsState.NotFound
        } else {
          // the same category first, then the rest, each in the library's own order
          val similar = repository.publishedBooks()
            .filter { it.id != book.id }
            .sortedBy { if (it.category == book.category) 0 else 1 }
            .take(MAX_SIMILAR)
          BookDetailsState.Ready(book, similar)
        }
      } catch (_: LibraryApiException) {
        BookDetailsState.Error
      } catch (_: IOException) {
        BookDetailsState.Error
      }
      refreshDevice()
    }
  }

  private fun refreshDevice() {
    val book = readyBook()
    device = BookOnDevice(
      progress = store.progress(bookId)?.withPageCount(book?.pageCount),
      saved = bookId in store.savedBooks(),
      offline = book != null && LibraryFiles.isOffline(this, book)
    )
  }

  private fun startDownload() {
    readyBook()?.let { LibraryDownloads.start(this, it) }
  }

  private fun share() {
    val book = readyBook() ?: return
    val text = buildString {
      append(book.bookTitle)
      book.author?.let { append('\n').append(getString(R.string.library_author, it)) }
      book.translator?.let { append('\n').append(getString(R.string.library_translator, it)) }
      if (book.description.isNotEmpty()) {
        append("\n\n").append(book.description.take(SHARE_DESCRIPTION_LENGTH))
        if (book.description.length > SHARE_DESCRIPTION_LENGTH) append('…')
      }
    }
    val send = Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_SUBJECT, book.bookTitle)
      .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, getString(R.string.library_share)))
  }

  private fun readyBook(): LibraryBook? = (state as? BookDetailsState.Ready)?.book

  companion object {
    private const val EXTRA_BOOK_ID = "book_id"
    private const val MAX_SIMILAR = 8
    private const val SHARE_DESCRIPTION_LENGTH = 220

    fun intent(context: Context, bookId: String): Intent =
      Intent(context, LibraryBookActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
  }
}

sealed interface BookDetailsState {
  data object Loading : BookDetailsState
  data object NotFound : BookDetailsState
  data object Error : BookDetailsState
  data class Ready(val book: LibraryBook, val similar: List<LibraryBook>) : BookDetailsState
}

/** What this device holds for the book. */
data class BookOnDevice(
  val progress: ReadingProgress? = null,
  val saved: Boolean = false,
  val offline: Boolean = false
)
