package com.quran.mobile.feature.library.presenter

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.library.data.DefaultLibraryRepository
import com.quran.mobile.feature.library.data.LibraryCategory
import com.quran.mobile.feature.library.data.MockLibraryApi
import com.quran.mobile.feature.library.data.TestLibraryData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibraryPresenterTest {

  private val api = MockLibraryApi(TestLibraryData.BOOKS)
  private val presenter = LibraryPresenter(DefaultLibraryRepository(api))

  @Test
  fun loadsEveryPublishedBookOnBind() = runTest {
    presenter.state.test {
      assertThat(awaitItem().books).isEqualTo(BooksState.Loading)
      presenter.bind(backgroundScope)

      val loaded = awaitItem()
      assertThat(loaded.selectedCategory).isNull()
      assertThat(loaded.visibleBooks.map { it.id })
        .containsExactly("1", "2", "4", "5", "7", "8", "10", "11", "12").inOrder()
      assertThat(loaded.totalCount).isEqualTo(9)
      assertThat(loaded.categoryCounts).containsExactly(
        LibraryCategory.QURAN_SCIENCES, 2,
        LibraryCategory.QURAN_TRANSLATION, 2,
        LibraryCategory.TAJWEED, 2,
        LibraryCategory.TAFSIR, 3
      )
    }
  }

  @Test
  fun bindRestoresASavedCategory() = runTest {
    presenter.bind(backgroundScope, LibraryCategory.TAJWEED)
    presenter.state.test {
      assertThat(awaitItem().selectedCategory).isEqualTo(LibraryCategory.TAJWEED)
      assertThat(awaitItem().visibleBooks.map { it.id }).containsExactly("7", "8").inOrder()
    }
  }

  @Test
  fun selectingACategoryFiltersWithoutReloading() = runTest {
    presenter.bind(backgroundScope)
    presenter.state.test {
      skipItems(1)
      awaitItem() // every book loaded

      presenter.selectCategory(LibraryCategory.TAFSIR)
      val tafsir = awaitItem()
      assertThat(tafsir.books).isInstanceOf(BooksState.Loaded::class.java)
      assertThat(tafsir.visibleBooks.map { it.order }).containsExactly(10, 20, 30).inOrder()

      presenter.selectCategory(null)
      assertThat(awaitItem().visibleBooks).hasSize(9)
    }
  }

  @Test
  fun searchMatchesTitleAndDescriptionAcrossLetterForms() = runTest {
    presenter.bind(backgroundScope)
    presenter.state.test {
      skipItems(2)

      // Arabic «ي» and «ك» find the Persian letters in «تفسیر» and «کوتاه»
      presenter.search("تفسير كوتاه")
      assertThat(awaitItem().visibleBooks.map { it.id }).containsExactly("10")

      presenter.selectCategory(LibraryCategory.TAJWEED)
      assertThat(awaitItem().visibleBooks).isEmpty()

      presenter.search("")
      assertThat(awaitItem().visibleBooks.map { it.id }).containsExactly("7", "8").inOrder()
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
