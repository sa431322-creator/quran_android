package com.quran.mobile.feature.library.data

import androidx.annotation.StringRes
import com.quran.mobile.feature.library.R

/**
 * The library's four categories, in display order. [id] is the stable value stored and sent
 * to the API; [titleRes] is the Persian name shown to the user and is never used as a key.
 */
enum class LibraryCategory(val id: String, @StringRes val titleRes: Int) {
  QURAN_SCIENCES("quran-sciences", R.string.library_category_quran_sciences),
  QURAN_TRANSLATION("quran-translation", R.string.library_category_quran_translation),
  TAJWEED("tajweed", R.string.library_category_tajweed),
  TAFSIR("tafsir", R.string.library_category_tafsir);

  companion object {
    fun fromId(id: String): LibraryCategory? = entries.firstOrNull { it.id == id }
  }
}
