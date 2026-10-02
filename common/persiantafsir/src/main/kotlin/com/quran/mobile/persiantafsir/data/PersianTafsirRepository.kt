package com.quran.mobile.persiantafsir.data

import com.quran.data.model.SuraAyah
import com.quran.mobile.persiantafsir.model.PersianTafsirAyah
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PersianTafsirRepository @Inject constructor(
  private val databaseProvider: PersianTafsirDatabaseProvider
) {

  /**
   * Returns the Persian tafsir for every ayah from [start] to [end] (inclusive), or null if the
   * database could not be opened.
   */
  suspend fun tafsir(start: SuraAyah, end: SuraAyah): List<PersianTafsirAyah>? {
    val database = databaseProvider.provideDatabase() ?: return null
    return withContext(Dispatchers.IO) {
      database.persianTafsirQueries
        .tafsirForRange(start.key(), end.key()) { sura, ayah, text, name, bismillah ->
          PersianTafsirAyah(
            suraAyah = SuraAyah(sura.toInt(), ayah.toInt()),
            suraName = name,
            text = text,
            bismillah = if (ayah == 1L) bismillah else null
          )
        }
        .executeAsList()
    }
  }

  private fun SuraAyah.key(): Long = sura * 1000L + ayah
}
