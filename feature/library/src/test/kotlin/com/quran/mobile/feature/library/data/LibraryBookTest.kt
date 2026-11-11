package com.quran.mobile.feature.library.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryBookTest {

  private val book = MockLibraryData.BOOKS.first()

  @Test
  fun bundledAssetPathIsThePathInsideAssets() {
    val bundled = book.copy(fileUrl = "file:///android_asset/books/a.pdf")
    assertThat(bundled.bundledAssetPath).isEqualTo("books/a.pdf")
  }

  @Test
  fun otherFilesAreNotBundled() {
    assertThat(book.copy(fileUrl = null).bundledAssetPath).isNull()
    assertThat(book.copy(fileUrl = "https://example.org/a.pdf").bundledAssetPath).isNull()
    assertThat(book.copy(fileUrl = "file:///android_asset/").bundledAssetPath).isNull()
  }

  @Test
  fun theDariTranslationShipsWithTheApp() {
    val dari = MockLibraryData.BOOKS.single { it.id == "14" }
    assertThat(dari.category).isEqualTo(LibraryCategory.QURAN_TRANSLATION)
    assertThat(dari.published).isTrue()
    assertThat(dari.bundledAssetPath).isEqualTo("books/quran-dari-translation.pdf")
  }

  @Test
  fun theModernScienceBookShipsWithTheApp() {
    val book = MockLibraryData.BOOKS.single { it.id == "15" }
    assertThat(book.category).isEqualTo(LibraryCategory.QURAN_SCIENCES)
    assertThat(book.published).isTrue()
    assertThat(book.order).isEqualTo(10)
    assertThat(book.bundledAssetPath).isEqualTo("books/quran-and-modern-science.pdf")
  }

  @Test
  fun tafsirNoorShipsWithTheApp() {
    val book = MockLibraryData.BOOKS.single { it.id == "16" }
    assertThat(book.category).isEqualTo(LibraryCategory.TAFSIR)
    assertThat(book.published).isTrue()
    assertThat(book.order).isEqualTo(10)
    assertThat(book.bundledAssetPath).isEqualTo("books/tafsir-noor.pdf")
  }

  @Test
  fun tajweedAsanShipsWithTheApp() {
    val book = MockLibraryData.BOOKS.single { it.id == "17" }
    assertThat(book.category).isEqualTo(LibraryCategory.TAJWEED)
    assertThat(book.published).isTrue()
    assertThat(book.order).isEqualTo(10)
    assertThat(book.bundledAssetPath).isEqualTo("books/tajweed-asan.pdf")
  }
}
