package com.quran.mobile.feature.mushaf.data

/** One row of a text mushaf page. */
sealed interface MushafLine {
  /**
   * Words of one line; each word is glyph codes for that page's QPC font, [ayahs] holds each
   * word's ayah as [ayahKey], and [ends] marks the words that are an ayah's end marker (the
   * page draws its own marker in their place).
   */
  data class Text(
    val words: List<String>,
    val ayahs: List<Int>,
    val ends: List<Boolean> = List(words.size) { false }
  ) : MushafLine
  data class SuraHeader(val sura: Int) : MushafLine
  data object Basmala : MushafLine
  data object Blank : MushafLine
}

data class MushafPage(
  val page: Int,
  val sura: Int,
  val juz: Int,
  /** sura and ayah of the page's first word */
  val firstAyah: Pair<Int, Int>,
  val lines: List<MushafLine>
) {
  /** The first two pages are short and centered, like the printed mushaf. */
  val isOpening: Boolean get() = page <= 2
}

/** Raw page data as stored in assets/mushaf/v4/pages.json. */
data class RawMushafPage(
  val page: Int,
  val sura: Int,
  val juz: Int,
  /** sura and ayah of the page's first word */
  val firstAyah: Pair<Int, Int>,
  /** line number (1-15) to words */
  val lines: Map<Int, List<String>>,
  /** sura to the line of its first ayah */
  val suraStarts: List<Pair<Int, Int>>,
  /** each word's [ayahKey], in reading order */
  val wordAyahs: List<Int> = emptyList()
)

/** One number for a sura and ayah, ordered like the ayahs themselves. */
fun ayahKey(sura: Int, ayah: Int): Int = sura * 1000 + ayah
fun ayahKeySura(key: Int): Int = key / 1000
fun ayahKeyAyah(key: Int): Int = key % 1000

const val MUSHAF_PAGE_COUNT = 604
const val MUSHAF_LINES_PER_PAGE = 15
private const val OPENING_LINES = 8
private const val SURA_FATIHA = 1
private const val SURA_TAWBA = 9

/**
 * Fills the lines between the text: a sura header and (except for at-Tawba and al-Fatiha, whose
 * basmala is its first ayah) a basmala above every sura's first ayah. When a sura starts on the
 * first lines of a page, its header sits on the last line of the page before.
 */
fun buildMushafPages(raw: List<RawMushafPage>): List<MushafPage> {
  val slots = raw.map { page ->
    val count = if (page.page <= 2) OPENING_LINES else MUSHAF_LINES_PER_PAGE
    Array<MushafLine>(count) { MushafLine.Blank }
  }

  raw.forEachIndexed { index, page ->
    // an ayah's last word is its end marker, unless the ayah goes on onto the next page
    val continuesOnNextPage = raw.getOrNull(index + 1)?.wordAyahs?.firstOrNull()
    val isEnd = { w: Int ->
      val key = page.wordAyahs.getOrElse(w) { 0 }
      val next = page.wordAyahs.getOrNull(w + 1)
      key != 0 && if (next == null) key != continuesOnNextPage else next != key
    }
    var word = 0
    page.lines.toSortedMap().forEach { (line, words) ->
      val ayahs = List(words.size) { page.wordAyahs.getOrElse(word + it) { 0 } }
      val ends = List(words.size) { isEnd(word + it) }
      word += words.size
      slots[index][line - 1] = MushafLine.Text(words, ayahs, ends)
    }
    page.suraStarts.forEach { (sura, line) ->
      val decorations = if (sura == SURA_FATIHA || sura == SURA_TAWBA) {
        listOf(MushafLine.SuraHeader(sura))
      } else {
        listOf(MushafLine.SuraHeader(sura), MushafLine.Basmala)
      }
      decorations.forEachIndexed { i, decoration ->
        var target = index
        var slot = line - decorations.size + i - 1
        if (slot < 0) {
          target = index - 1
          slot += slots[target].size
        }
        if (target >= 0 && slots[target][slot] == MushafLine.Blank) {
          slots[target][slot] = decoration
        }
      }
    }
  }

  return raw.mapIndexed { index, page ->
    MushafPage(page.page, page.sura, page.juz, page.firstAyah, slots[index].toList())
  }
}

/** The page showing each sura's header, which is the page before its text for some suras. */
fun suraHeaderPages(pages: List<MushafPage>): Map<Int, Int> =
  pages.flatMap { page ->
    page.lines.filterIsInstance<MushafLine.SuraHeader>().map { it.sura to page.page }
  }.toMap()
