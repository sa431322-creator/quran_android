package com.quran.mobile.feature.library.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MockLibraryApiTest {

  private val api = MockLibraryApi()

  @Test
  fun sampleDataFollowsTheLibraryRules() {
    val books = MockLibraryData.BOOKS
    assertThat(books.map { it.id }).containsNoDuplicates()
    assertThat(books.map { it.category }.toSet()).containsExactlyElementsIn(LibraryCategory.entries)
    // no made-up download links
    books.forEach { assertThat(it.fileUrl).isNull() }
    books.groupBy { it.category }.values.forEach { inCategory ->
      assertThat(inCategory.map { it.order }).isEqualTo((1..inCategory.size).map { it * 10 })
    }
  }

  @Test
  fun categoryIdsAreStable() {
    assertThat(LibraryCategory.entries.map { it.id })
      .containsExactly("quran-sciences", "quran-translation", "tajweed", "tafsir").inOrder()
    assertThat(LibraryCategory.fromId("tafsir")).isEqualTo(LibraryCategory.TAFSIR)
    assertThat(LibraryCategory.fromId("تفسیر قرآن کریم")).isNull()
  }

  @Test
  fun getBooksFiltersByCategoryAndPublished() = runTest {
    val tafsir = api.getBooks(category = "tafsir")
    assertThat(tafsir.map { it.category }.toSet()).containsExactly(LibraryCategory.TAFSIR)
    assertThat(tafsir.any { !it.published }).isTrue()

    val publishedTafsir = api.getBooks(category = "tafsir", published = true)
    assertThat(publishedTafsir.map { it.order }).containsExactly(10, 20, 30).inOrder()

    val all = api.getBooks()
    assertThat(all).hasSize(MockLibraryData.BOOKS.size)
  }

  @Test
  fun getBooksRejectsUnknownCategory() = runTest {
    val error = runCatching { api.getBooks(category = "poetry") }.exceptionOrNull()
    assertThat(error).isInstanceOf(LibraryApiException.Validation::class.java)
  }

  @Test
  fun getBookReturnsUnpublishedForManagementAndFailsForUnknownId() = runTest {
    assertThat(api.getBook("13").published).isFalse()
    val error = runCatching { api.getBook("999") }.exceptionOrNull()
    assertThat(error).isInstanceOf(LibraryApiException.NotFound::class.java)
  }

  @Test
  fun createBookGoesLastInItsCategory() = runTest {
    val created = api.createBook(
      NewLibraryBook(bookTitle = "  عنوان  ", description = "", published = true, category = "tajweed")
    )
    assertThat(created.order).isEqualTo(40)
    assertThat(created.bookTitle).isEqualTo("عنوان")
    assertThat(created.fileUrl).isNull()
    assertThat(api.getBook(created.id)).isEqualTo(created)
    assertThat(created.id).isNotIn(MockLibraryData.BOOKS.map { it.id })
  }

  @Test
  fun createBookValidatesFields() = runTest {
    val blankTitle = NewLibraryBook(bookTitle = " ", description = "", published = true, category = "tafsir")
    val badCategory = blankTitle.copy(bookTitle = "x", category = "unknown")
    val badOrder = blankTitle.copy(bookTitle = "x", order = 0)
    listOf(blankTitle, badCategory, badOrder).forEach { book ->
      val error = runCatching { api.createBook(book) }.exceptionOrNull()
      assertThat(error).isInstanceOf(LibraryApiException.Validation::class.java)
    }
  }

  @Test
  fun updateBookChangesOnlyGivenFields() = runTest {
    val updated = api.updateBook("1", LibraryBookPatch(published = false, order = 15))
    assertThat(updated.published).isFalse()
    assertThat(updated.order).isEqualTo(15)
    assertThat(updated.bookTitle).isEqualTo(MockLibraryData.BOOKS[0].bookTitle)
  }

  @Test
  fun emptyFileUrlRemovesTheFile() = runTest {
    api.updateBook("1", LibraryBookPatch(fileUrl = "https://example.org/book.pdf"))
    assertThat(api.updateBook("1", LibraryBookPatch(bookTitle = "x")).fileUrl).isNotNull()
    assertThat(api.updateBook("1", LibraryBookPatch(fileUrl = " ")).fileUrl).isNull()
  }

  @Test
  fun rejectedCreateDoesNotUseAnId() = runTest {
    runCatching {
      api.createBook(NewLibraryBook(bookTitle = "", description = "", published = true, category = "tafsir"))
    }
    val created = api.createBook(
      NewLibraryBook(bookTitle = "x", description = "", published = true, category = "tafsir")
    )
    assertThat(created.id).isEqualTo("14")
  }

  @Test
  fun movingToAnotherCategoryPutsTheBookLastThere() = runTest {
    val moved = api.updateBook("1", LibraryBookPatch(category = "tafsir"))
    assertThat(moved.category).isEqualTo(LibraryCategory.TAFSIR)
    assertThat(moved.order).isEqualTo(50)
  }

  @Test
  fun reorderIsAllOrNothing() = runTest {
    val error = runCatching {
      api.reorder(listOf(OrderUpdate("1", 50), OrderUpdate("999", 10)))
    }.exceptionOrNull()
    assertThat(error).isInstanceOf(LibraryApiException.NotFound::class.java)
    assertThat(api.getBook("1").order).isEqualTo(10)

    val reordered = api.reorder(listOf(OrderUpdate("1", 30), OrderUpdate("3", 10)))
    assertThat(reordered.map { it.order }).containsExactly(30, 10).inOrder()
  }

  @Test
  fun reorderRejectsDuplicatesAndBadOrders() = runTest {
    listOf(
      emptyList(),
      listOf(OrderUpdate("1", 10), OrderUpdate("1", 20)),
      listOf(OrderUpdate("1", -10))
    ).forEach { updates ->
      val error = runCatching { api.reorder(updates) }.exceptionOrNull()
      assertThat(error).isInstanceOf(LibraryApiException.Validation::class.java)
    }
  }

  @Test
  fun scenariosSimulateEmptyAndError() = runTest {
    api.scenario = MockLibraryApi.Scenario.EMPTY
    assertThat(api.getBooks()).isEmpty()

    api.scenario = MockLibraryApi.Scenario.ERROR
    val error = runCatching { api.getBooks() }.exceptionOrNull()
    assertThat(error).isInstanceOf(LibraryApiException.Unavailable::class.java)
  }
}
