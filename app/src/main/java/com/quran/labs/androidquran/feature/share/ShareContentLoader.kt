package com.quran.labs.androidquran.feature.share

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ShareUtil
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.persiantafsir.data.PersianTafsirRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Loads what a share card shows: the ayahs, their tafsir or translation, and the labels. */
class ShareContentLoader @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val persianTafsirRepository: PersianTafsirRepository,
  private val translationsDBAdapter: TranslationsDBAdapter,
  private val quranFileUtils: QuranFileUtils,
  private val ayahMapper: AyahMapper,
  private val shareUtil: ShareUtil
) {

  /** Persian tafsir first, then the translations the reader has downloaded. */
  suspend fun tafsirSources(): List<TafsirSource> {
    val translations = runCatching { translationsDBAdapter.getTranslations().first() }
      .getOrDefault(emptyList())
      .sortedBy { it.displayOrder }
      .map { TafsirSource.Translation(it.filename, it.resolveTranslatorName()) }
    return listOf(TafsirSource.Persian) + translations
  }

  /** The whole mushaf page an ayah is on. */
  fun pageRange(suraAyah: SuraAyah): Pair<SuraAyah, SuraAyah> {
    val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
    val range = quranInfo.getVerseRangeForPage(page)
    return SuraAyah(range.startSura, range.startAyah) to SuraAyah(range.endingSura, range.endingAyah)
  }

  /** Every ayah from [start] to [end] with the text of [source] under it, or null without one. */
  suspend fun load(
    start: SuraAyah,
    end: SuraAyah,
    sources: List<TafsirSource>,
    sourceKey: String?
  ): ShareCardContent? = withContext(Dispatchers.IO) {
    // the text drawn on the card comes from the table made for the Uthmani font (the same text the
    // translation view shows); the share_text table is for plain text and breaks with this font
    val arabic = runCatching {
      DatabaseHandler.getDatabaseHandler(appContext, QuranDataProvider.QURAN_ARABIC_DATABASE, quranFileUtils)
        .getVerses(verseRange(start, end), DatabaseHandler.TextType.ARABIC)
    }
      .getOrNull()
      ?.takeIf { it.isNotEmpty() }
      ?: return@withContext null

    val source = sources.firstOrNull { it.key == sourceKey }
    val tafsirByAyah: Map<SuraAyah, String> = when (source) {
      null -> emptyMap()
      TafsirSource.Persian ->
        persianTafsirRepository.tafsir(start, end).orEmpty().associate { it.suraAyah to it.text }
      is TafsirSource.Translation -> translation(source.filename, start, end)
    }

    val verses = arabic.map { text ->
      val suraAyah = SuraAyah(text.sura, text.ayah)
      ShareVerse(
        suraAyah = suraAyah,
        arabic = ArabicDatabaseUtils.getAyahWithoutBasmallah(text.sura, text.ayah, text.text.trim()),
        tafsir = tafsirByAyah[suraAyah]
      )
    }
    content(verses, source)
  }

  /** The same content, cut down to one ayah, for one frame of a video. */
  fun forVerse(content: ShareCardContent, verse: ShareVerse): ShareCardContent {
    val start = verse.suraAyah
    return content.copy(
      suraTitle = quranDisplayData.getSuraName(appContext, start.sura, true),
      verses = listOf(verse),
      rangeLabel = shareUtil.rangeLabel(appContext, start, start),
      pageLabel = pageLabel(start, start),
      juzLabel = juzLabel(start)
    )
  }

  private fun content(verses: List<ShareVerse>, source: TafsirSource?): ShareCardContent {
    val start = verses.first().suraAyah
    val end = verses.last().suraAyah
    val title = if (start.sura == end.sura) {
      quranDisplayData.getSuraName(appContext, start.sura, true)
    } else {
      quranDisplayData.getSuraName(appContext, start.sura, true) + " - " +
          quranDisplayData.getSuraName(appContext, end.sura, false)
    }
    return ShareCardContent(
      suraTitle = title,
      verses = verses,
      rangeLabel = shareUtil.rangeLabel(appContext, start, end),
      pageLabel = pageLabel(start, end),
      juzLabel = juzLabel(start),
      tafsirSourceName = when (source) {
        null -> null
        TafsirSource.Persian -> appContext.getString(R.string.tasnim_share_persian_tafsir)
        is TafsirSource.Translation -> source.name
      },
      downloadLink = downloadLink()
    )
  }

  private fun pageLabel(start: SuraAyah, end: SuraAyah): String =
    shareUtil.pageLabel(appContext, start, end).removePrefix(" — ")

  private fun juzLabel(start: SuraAyah): String {
    val page = quranInfo.getPageFromSuraAyah(start.sura, start.ayah)
    return appContext.getString(
      R.string.tasnim_share_juz, QuranUtils.getLocalizedNumber(quranInfo.getJuzForDisplayFromPage(page))
    )
  }

  /**
   * The link printed on cards: the update link set in the Tasnim admin panel, else the website,
   * else the app's store page.
   */
  fun downloadLink(): String =
    shareUtil.appLink(appContext)
      .removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

  private fun translation(filename: String, start: SuraAyah, end: SuraAyah): Map<SuraAyah, String> {
    return runCatching {
      val range = verseRange(start, end)
      val handler = DatabaseHandler.getDatabaseHandler(appContext, filename, quranFileUtils)
      val mapped = ayahMapper.mapRange(range)
      val data = handler.getVerses(mapped, DatabaseHandler.TextType.TRANSLATION)
      ayahMapper.mapKufiData(range, data).associate { verse ->
        // some tafsirs explain several ayahs together: the other ayahs hold only the number of the
        // row with the text, which the database handler puts in extraData
        val text = if (TranslationUtil.getHyperlinkAyahId(verse) != null) verse.extraData.orEmpty() else verse.text
        SuraAyah(verse.sura, verse.ayah) to clean(text)
      }
    }.getOrDefault(emptyMap())
  }

  private fun verseRange(start: SuraAyah, end: SuraAyah): VerseRange {
    val startId = quranInfo.getAyahId(start.sura, start.ayah)
    val endId = quranInfo.getAyahId(end.sura, end.ayah)
    return VerseRange(start.sura, start.ayah, end.sura, end.ayah, endId - startId + 1)
  }

  /** Drops footnote marks and markup that only make sense inside the app. */
  private fun clean(text: String): String =
    text.replace(Regex("<[^>]+>"), "")
      .replace(Regex("\\[\\[[^]]*]]"), "")
      .replace(Regex("\\s+"), " ")
      .trim()
}
