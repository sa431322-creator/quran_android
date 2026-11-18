package com.quran.labs.androidquran.feature.share

import com.quran.data.model.SuraAyah

/** Output sizes of a share card. Width is always 1080 so text sizes stay the same. */
enum class ShareAspect(val width: Int, val height: Int) {
  SQUARE(1080, 1080),
  PORTRAIT(1080, 1350),
  STORY(1080, 1920)
}

/** Where the text under the ayah comes from. */
sealed interface TafsirSource {
  val key: String

  data object Persian : TafsirSource {
    override val key = "persian"
  }

  data class Translation(val filename: String, val name: String) : TafsirSource {
    override val key = "translation:$filename"
  }
}

/** What the reader picked in the share composer. */
data class ShareCardOptions(
  val aspect: ShareAspect = ShareAspect.SQUARE,
  val background: Int = 0,
  val showSuraName: Boolean = true,
  val showAyahNumbers: Boolean = true,
  val showPage: Boolean = true,
  val showJuz: Boolean = true,
  val showTafsir: Boolean = false,
  val tafsirKey: String = TafsirSource.Persian.key,
  /** videos: a drifting light and a fade between ayahs */
  val videoMotion: Boolean = true,
  /** videos: every ayah on one card, the one being recited in full color and the rest dimmed */
  val videoAllAyahs: Boolean = false
)

/** One ayah of the card: its Arabic text and, when chosen, the text shown under it. */
data class ShareVerse(
  val suraAyah: SuraAyah,
  val arabic: String,
  val tafsir: String?
)

/** Everything a card shows besides the reader's options. */
data class ShareCardContent(
  /** e.g. "سوره بقره" */
  val suraTitle: String,
  val verses: List<ShareVerse>,
  /** e.g. "سوره بقره: ۲۵۵-۲۵۷" */
  val rangeLabel: String,
  /** e.g. "صفحه ۴۲" or "صفحه ۴۲-۴۳" */
  val pageLabel: String,
  /** e.g. "جزء ۳" */
  val juzLabel: String,
  val tafsirSourceName: String?,
  /** the app's download link, shown in the corner with the logo */
  val downloadLink: String
)
