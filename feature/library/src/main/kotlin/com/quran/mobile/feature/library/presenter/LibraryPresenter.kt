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

/**
 * [selectedCategory] is null for «همه», every category. [books] holds every published book;
 * [visibleBooks] is what the chosen category and [query] leave of it.
 */
data class LibraryUiState(
  val selectedCategory: LibraryCategory? = null,
  val query: String = "",
  val books: BooksState = BooksState.Loading
) {
  val visibleBooks: List<LibraryBook>
    get() {
      val all = (books as? BooksState.Loaded)?.books ?: return emptyList()
      val terms = normalize(query).split(' ').filter { it.isNotEmpty() }
      return all.filter { book ->
        (selectedCategory == null || book.category == selectedCategory) &&
          terms.all { term -> book.searchText.contains(term) }
      }
    }

  /** How many books each category has, whatever the query. */
  val categoryCounts: Map<LibraryCategory, Int>
    get() = ((books as? BooksState.Loaded)?.books ?: emptyList()).groupingBy { it.category }.eachCount()

  val totalCount: Int
    get() = (books as? BooksState.Loaded)?.books?.size ?: 0
}

sealed interface BooksState {
  data object Loading : BooksState
  data object Empty : BooksState
  data object Error : BooksState
  data class Loaded(val books: List<LibraryBook>) : BooksState
}

/** Loads every published book once; choosing a category or searching filters them in place. */
@ActivityScope
class LibraryPresenter @Inject constructor(private val repository: LibraryRepository) {

  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()

  private var scope: CoroutineScope? = null
  private var loadJob: Job? = null

  /**
   * Starts loading in [scope] with [category] selected (null for every category); the
   * presenter stops when [scope] is cancelled.
   */
  fun bind(scope: CoroutineScope, category: LibraryCategory? = null) {
    this.scope = scope
    _state.update { it.copy(selectedCategory = category) }
    load()
  }

  fun selectCategory(category: LibraryCategory?) {
    _state.update { it.copy(selectedCategory = category) }
    // choosing a category after a failed load tries again, as it always has
    if (_state.value.books == BooksState.Error) load()
  }

  fun search(query: String) {
    _state.update { it.copy(query = query) }
  }

  fun retry() = load()

  private fun load() {
    loadJob?.cancel()
    _state.update { it.copy(books = BooksState.Loading) }
    val scope = scope ?: return
    loadJob = scope.launch {
      val books = try {
        val books = repository.publishedBooks()
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

/** Everything a search matches against, normalized like the query. */
private val LibraryBook.searchText: String
  get() = normalize(
    listOfNotNull(bookTitle, author, translator, description, tags.joinToString(" "))
      .joinToString(" ")
  )

/** Folds Arabic letter forms into Persian ones and drops case, so «كتاب» finds «کتاب». */
internal fun normalize(text: String): String =
  text.lowercase()
    .replace('ك', 'ک')
    .replace('ي', 'ی')
    .replace('ى', 'ی')
    .replace('ة', 'ه')
    .replace(Char(0x200C), ' ') // zero-width non-joiner
    .replace(Regex("""\s+"""), " ")
    .trim()
