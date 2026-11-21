package com.quran.labs.androidquran.ui.translation

import com.quran.data.model.SuraAyah

/**
 * What an ayah's buttons on the translation pages ask of the reader (the activity showing the
 * pages): share the ayah, recite from it, and save it in the bookmarks.
 */
interface TranslationAyahActions {
  fun onShareAyah(suraAyah: SuraAyah)
  fun onPlayAyah(suraAyah: SuraAyah)
  fun onBookmarkAyah(suraAyah: SuraAyah)
}
