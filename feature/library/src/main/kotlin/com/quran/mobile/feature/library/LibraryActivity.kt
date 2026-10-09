package com.quran.mobile.feature.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryCategory
import com.quran.mobile.feature.library.di.LibraryComponentInterface
import com.quran.mobile.feature.library.presenter.LibraryPresenter
import com.quran.mobile.feature.library.ui.LibraryScreen
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class LibraryActivity : AppCompatActivity() {

  @Inject
  lateinit var presenter: LibraryPresenter

  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LibraryComponentInterface
    injector?.libraryComponentFactory()?.generate()?.inject(this)

    // keeps the chosen category across rotation, which rebuilds the presenter
    val category = savedInstanceState?.getString(STATE_CATEGORY)?.let(LibraryCategory::fromId)
    presenter.bind(scope, category)

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        LibraryScreen(
          presenter = presenter,
          onOpenBook = ::openBook,
          onBack = onBackPressedDispatcher::onBackPressed
        )
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_CATEGORY, presenter.state.value.selectedCategory.id)
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  /** Opens a web link in the browser; there's no in-app reader yet, nor any local files. */
  private fun openBook(book: LibraryBook) {
    val uri = book.fileUrl?.let(Uri::parse)
    if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https")) {
      Toast.makeText(this, R.string.library_file_unavailable, Toast.LENGTH_SHORT).show()
      return
    }
    try {
      startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(this, R.string.library_file_unavailable, Toast.LENGTH_SHORT).show()
    }
  }

  private companion object {
    const val STATE_CATEGORY = "category"
  }
}
