package com.quran.mobile.feature.library

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.library.data.LibraryCategory
import com.quran.mobile.feature.library.data.LibraryFiles
import com.quran.mobile.feature.library.data.LibraryReadingStore
import com.quran.mobile.feature.library.di.LibraryComponentInterface
import com.quran.mobile.feature.library.presenter.LibraryPresenter
import com.quran.mobile.feature.library.ui.LibraryScreen
import com.quran.mobile.feature.library.ui.LibraryShelf
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class LibraryActivity : AppCompatActivity() {

  @Inject
  lateinit var presenter: LibraryPresenter

  private val scope = MainScope()
  private lateinit var store: LibraryReadingStore
  private var shelf by mutableStateOf(LibraryShelf())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LibraryComponentInterface
    injector?.libraryComponentFactory()?.generate()?.inject(this)
    store = LibraryReadingStore(this)

    // keeps the chosen category across rotation, which rebuilds the presenter
    val category = savedInstanceState?.getString(STATE_CATEGORY)?.let(LibraryCategory::fromId)
    presenter.bind(scope, category)
    savedInstanceState?.getString(STATE_QUERY)?.let(presenter::search)

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        LibraryScreen(
          presenter = presenter,
          shelf = shelf,
          isOffline = { LibraryFiles.isOffline(this, it) },
          onOpenBook = { LibraryNavigation.openDetails(this, it) },
          onContinueBook = { LibraryNavigation.openReader(this, it) },
          onOpenBookmark = { book, page -> LibraryNavigation.openReader(this, book, page) },
          onBack = onBackPressedDispatcher::onBackPressed
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // progress, bookmarks and saved books may have changed in the reader or a details page
    shelf = LibraryShelf(
      progress = store.allProgress(),
      bookmarks = store.allBookmarks(),
      savedBooks = store.savedBooks(),
      refreshedAt = SystemClock.elapsedRealtime()
    )
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    val state = presenter.state.value
    outState.putString(STATE_CATEGORY, state.selectedCategory?.id)
    outState.putString(STATE_QUERY, state.query)
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private companion object {
    const val STATE_CATEGORY = "category"
    const val STATE_QUERY = "query"
  }
}
