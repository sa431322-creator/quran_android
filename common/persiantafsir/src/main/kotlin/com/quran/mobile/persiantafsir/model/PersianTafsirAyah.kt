package com.quran.mobile.persiantafsir.model

import com.quran.data.model.SuraAyah

data class PersianTafsirAyah(
  val suraAyah: SuraAyah,
  val suraName: String,
  val text: String,
  /** the surah's bismillah line, only set for the first ayah of surahs that have one */
  val bismillah: String?
)
