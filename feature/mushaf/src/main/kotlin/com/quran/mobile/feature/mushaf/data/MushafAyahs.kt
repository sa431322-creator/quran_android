package com.quran.mobile.feature.mushaf.data

/**
 * One word of an ayah as its page draws it: the glyph codes of [page]'s QPC font. [isEnd] marks
 * the ayah's end marker, which a card draws as its own gilt marker, like the page.
 */
data class AyahGlyph(val page: Int, val glyph: String, val isEnd: Boolean)

/**
 * Every ayah's words in reading order, keyed by [ayahKey], from the mushaf's pages. An ayah that
 * runs over a page turn keeps each word with its own page (and so its own page font).
 */
fun ayahGlyphs(pages: List<MushafPage>): Map<Int, List<AyahGlyph>> {
  val ayahs = LinkedHashMap<Int, MutableList<AyahGlyph>>()
  for (page in pages) {
    for (line in page.lines) {
      if (line !is MushafLine.Text) continue
      line.words.forEachIndexed { index, glyph ->
        val key = line.ayahs.getOrNull(index) ?: return@forEachIndexed
        ayahs.getOrPut(key) { mutableListOf() } += AyahGlyph(page.page, glyph, line.ends.getOrElse(index) { false })
      }
    }
  }
  return ayahs
}
