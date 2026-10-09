package com.quran.mobile.feature.library.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultLibraryRepositoryTest {

  private val api = MockLibraryApi()
  private val repository = DefaultLibraryRepository(api)

  @Test
  fun publishedBooksHideUnpublishedOnes() = runTest {
    val books = repository.publishedBooks()
    assertThat(books).isNotEmpty()
    assertThat(books.all { it.published }).isTrue()
    assertThat(books.map { it.id }).containsNoneOf("3", "6", "9", "13")
  }

  @Test
  fun publishedBooksAreSortedPerCategory() = runTest {
    LibraryCategory.entries.forEach { category ->
      val books = repository.publishedBooks(category)
      assertThat(books.map { it.category }.toSet()).containsExactly(category)
      assertThat(books.map { it.order }).isInOrder()
    }
  }

  @Test
  fun publishedBookIsNullForUnpublishedOrMissing() = runTest {
    assertThat(repository.publishedBook("10")?.id).isEqualTo("10")
    assertThat(repository.publishedBook("13")).isNull()
    assertThat(repository.publishedBook("999")).isNull()
  }

  @Test
  fun allBooksIncludeUnpublished() = runTest {
    assertThat(repository.allBooks(LibraryCategory.TAFSIR).map { it.id })
      .containsExactly("10", "11", "12", "13", "16").inOrder()
  }

  @Test
  fun equalOrdersSortById() = runTest {
    repository.updateBook("12", LibraryBookPatch(order = 10))
    assertThat(repository.publishedBooks(LibraryCategory.TAFSIR).map { it.id })
      .containsExactly("10", "12", "11", "16").inOrder()
  }

  @Test
  fun moveBookUsesTheGapAndLeavesOthersAlone() = runTest {
    val tafsir = repository.moveBook("12", toIndex = 1)
    assertThat(tafsir.map { it.id }).containsExactly("10", "12", "11", "13", "16").inOrder()
    assertThat(tafsir.map { it.order }).containsExactly(10, 15, 20, 40, 50).inOrder()
    // other categories keep their own orders
    assertThat(repository.allBooks(LibraryCategory.TAJWEED).map { it.order })
      .containsExactly(10, 20, 30).inOrder()
  }

  @Test
  fun moveBookRenumbersWhenThereIsNoGap() = runTest {
    repository.updateBook("11", LibraryBookPatch(order = 11))
    val tafsir = repository.moveBook("12", toIndex = 1)
    assertThat(tafsir.map { it.id }).containsExactly("10", "12", "11", "13", "16").inOrder()
    assertThat(tafsir.map { it.order }).containsExactly(10, 20, 30, 40, 50).inOrder()
  }

  @Test
  fun moveBookRejectsBadIndex() = runTest {
    val error = runCatching { repository.moveBook("10", toIndex = 9) }.exceptionOrNull()
    assertThat(error).isInstanceOf(LibraryApiException.Validation::class.java)
  }

  @Test
  fun addedBookShowsOnceItIsPublished() = runTest {
    val draft = repository.addBook(
      NewLibraryBook(bookTitle = "پیش‌نویس", description = "", published = false, category = "tafsir")
    )
    assertThat(repository.publishedBooks(LibraryCategory.TAFSIR).map { it.id }).doesNotContain(draft.id)

    repository.updateBook(draft.id, LibraryBookPatch(published = true))
    assertThat(repository.publishedBooks(LibraryCategory.TAFSIR).last().id).isEqualTo(draft.id)
  }
}
