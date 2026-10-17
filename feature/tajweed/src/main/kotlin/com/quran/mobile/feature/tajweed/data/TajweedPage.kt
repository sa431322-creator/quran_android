package com.quran.mobile.feature.tajweed.data

/** One row of a tajweed mushaf page. */
sealed interface TajweedLine {
  /** Words of one line; each word is glyph codes for that page's QPC V4 tajweed font. */
  data class Text(val words: List<String>) : TajweedLine
  data class SuraHeader(val sura: Int) : TajweedLine
  data object Basmala : TajweedLine
  data object Blank : TajweedLine
}

data class TajweedPage(
  val page: Int,
  val sura: Int,
  val juz: Int,
  val lines: List<TajweedLine>
) {
  /** The first two pages are short and centered, like the printed mushaf. */
  val isOpening: Boolean get() = page <= 2
}

/** Raw page data as stored in assets/tajweed/pages.json. */
data class RawTajweedPage(
  val page: Int,
  val sura: Int,
  val juz: Int,
  /** line number (1-15) to words */
  val lines: Map<Int, List<String>>,
  /** sura to the line of its first ayah */
  val suraStarts: List<Pair<Int, Int>>
)

const val TAJWEED_PAGE_COUNT = 604
const val TAJWEED_LINES_PER_PAGE = 15
private const val OPENING_LINES = 8
private const val SURA_FATIHA = 1
private const val SURA_TAWBA = 9

/**
 * Fills the lines between the text: a sura header and (except for at-Tawba and al-Fatiha, whose
 * basmala is its first ayah) a basmala above every sura's first ayah. When a sura starts on the
 * first lines of a page, its header sits on the last line of the page before.
 */
fun buildTajweedPages(raw: List<RawTajweedPage>): List<TajweedPage> {
  val slots = raw.map { page ->
    val count = if (page.page <= 2) OPENING_LINES else TAJWEED_LINES_PER_PAGE
    Array<TajweedLine>(count) { TajweedLine.Blank }
  }

  raw.forEachIndexed { index, page ->
    page.lines.forEach { (line, words) -> slots[index][line - 1] = TajweedLine.Text(words) }
    page.suraStarts.forEach { (sura, line) ->
      val decorations = if (sura == SURA_FATIHA || sura == SURA_TAWBA) {
        listOf(TajweedLine.SuraHeader(sura))
      } else {
        listOf(TajweedLine.SuraHeader(sura), TajweedLine.Basmala)
      }
      decorations.forEachIndexed { i, decoration ->
        var target = index
        var slot = line - decorations.size + i - 1
        if (slot < 0) {
          target = index - 1
          slot += slots[target].size
        }
        if (target >= 0 && slots[target][slot] == TajweedLine.Blank) {
          slots[target][slot] = decoration
        }
      }
    }
  }

  return raw.mapIndexed { index, page ->
    TajweedPage(page.page, page.sura, page.juz, slots[index].toList())
  }
}

/** The page showing each sura's header, which is the page before its text for some suras. */
fun suraHeaderPages(pages: List<TajweedPage>): Map<Int, Int> =
  pages.flatMap { page ->
    page.lines.filterIsInstance<TajweedLine.SuraHeader>().map { it.sura to page.page }
  }.toMap()
