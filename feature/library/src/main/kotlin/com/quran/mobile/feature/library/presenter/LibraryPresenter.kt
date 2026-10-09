package com.quran.mobile.feature.library.presenter

import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.library.data.LibraryApiException
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryCategory
import com.quran.mobile.feature.library.data.LibraryRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class LibraryUiState(
  val selectedCategory: LibraryCategory = LibraryCategory.entries.first(),
  val books: BooksState = BooksState.Loading
)

sealed interface BooksState {
  data object Loading : BooksState
  data object Empty : BooksState
  data object Error : BooksState
  data class Loaded(val books: List<LibraryBook>) : BooksState
}

/** Loads the published books of the selected category. */
@ActivityScope
class LibraryPresenter @Inject constructor(private val repository: LibraryRepository) {

  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()

  private var scope: CoroutineScope? = null
  private var loadJob: Job? = null

  /**
   * Starts loading [category] (the first one when null) in [scope]; the presenter stops when
   * [scope] is cancelled.
   */
  fun bind(scope: CoroutineScope, category: LibraryCategory? = null) {
    this.scope = scope
    load(category ?: _state.value.selectedCategory)
  }

  fun selectCategory(category: LibraryCategory) {
    if (category == _state.value.selectedCategory && _state.value.books != BooksState.Error) return
    load(category)
  }

  fun retry() = load()

  private fun load(category: LibraryCategory = _state.value.selectedCategory) {
    loadJob?.cancel()
    // one update, so the category and its loading state are never seen apart
    _state.update { it.copy(selectedCategory = category, books = BooksState.Loading) }
    val scope = scope ?: return
    loadJob = scope.launch {
      val books = try {
        val books = repository.publishedBooks(category)
        if (books.isEmpty()) BooksState.Empty else BooksState.Loaded(books)
      } catch (_: LibraryApiException) {
        BooksState.Error
      } catch (_: IOException) {
        // what a real backend fails with when the network is down
        BooksState.Error
      }
      _state.update { it.copy(books = books) }
    }
  }
}
