package com.quran.mobile.feature.library.presenter

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.library.data.DefaultLibraryRepository
import com.quran.mobile.feature.library.data.LibraryCategory
import com.quran.mobile.feature.library.data.MockLibraryApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibraryPresenterTest {

  private val api = MockLibraryApi()
  private val presenter = LibraryPresenter(DefaultLibraryRepository(api))

  @Test
  fun loadsTheFirstCategoryOnBind() = runTest {
    presenter.state.test {
      assertThat(awaitItem().books).isEqualTo(BooksState.Loading)
      presenter.bind(backgroundScope)

      val loaded = awaitItem()
      assertThat(loaded.selectedCategory).isEqualTo(LibraryCategory.QURAN_SCIENCES)
      val books = (loaded.books as BooksState.Loaded).books
      assertThat(books.map { it.id }).containsExactly("1", "2", "15").inOrder()
    }
  }

  @Test
  fun bindRestoresASavedCategory() = runTest {
    presenter.bind(backgroundScope, LibraryCategory.TAJWEED)
    presenter.state.test {
      assertThat(awaitItem().selectedCategory).isEqualTo(LibraryCategory.TAJWEED)
      val books = (awaitItem().books as BooksState.Loaded).books
      assertThat(books.map { it.id }).containsExactly("7", "8").inOrder()
    }
  }

  @Test
  fun selectingACategoryLoadsItsBooks() = runTest {
    presenter.bind(backgroundScope)
    presenter.state.test {
      skipItems(1)
      awaitItem() // first category loaded

      presenter.selectCategory(LibraryCategory.TAFSIR)
      val loading = awaitItem()
      assertThat(loading.selectedCategory).isEqualTo(LibraryCategory.TAFSIR)
      assertThat(loading.books).isEqualTo(BooksState.Loading)

      val books = (awaitItem().books as BooksState.Loaded).books
      assertThat(books.map { it.order }).containsExactly(10, 20, 30, 50).inOrder()
    }
  }

  @Test
  fun showsEmptyState() = runTest {
    api.scenario = MockLibraryApi.Scenario.EMPTY
    presenter.bind(backgroundScope)
    presenter.state.test {
      skipItems(1)
      assertThat(awaitItem().books).isEqualTo(BooksState.Empty)
    }
  }

  @Test
  fun showsErrorAndRetries() = runTest {
    api.scenario = MockLibraryApi.Scenario.ERROR
    presenter.bind(backgroundScope)
    presenter.state.test {
      skipItems(1)
      assertThat(awaitItem().books).isEqualTo(BooksState.Error)

      api.scenario = MockLibraryApi.Scenario.NORMAL
      presenter.retry()
      assertThat(awaitItem().books).isEqualTo(BooksState.Loading)
      assertThat(awaitItem().books).isInstanceOf(BooksState.Loaded::class.java)
    }
  }
}
