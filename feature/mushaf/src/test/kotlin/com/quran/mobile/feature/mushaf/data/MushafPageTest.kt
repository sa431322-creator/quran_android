package com.quran.mobile.feature.mushaf.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MushafPageTest {

  private fun raw(page: Int, lines: Iterable<Int>, starts: List<Pair<Int, Int>> = emptyList()) =
    RawMushafPage(page, 1, 1, 1 to 1, lines.associateWith { listOf("x") }, starts)

  private fun page(page: Int, sura: Int, ayah: Int) =
    MushafPage(page, sura, 1, sura to ayah, emptyList())

  @Test
  fun eachWordGetsItsAyahInReadingOrder() {
    val raw = RawMushafPage(
      page = 3, sura = 2, juz = 1, firstAyah = 2 to 6,
      lines = mapOf(2 to listOf("c", "d"), 1 to listOf("a", "b", "x")),
      suraStarts = emptyList(),
      wordAyahs = listOf(ayahKey(2, 6), ayahKey(2, 6), ayahKey(2, 7), ayahKey(2, 7), ayahKey(2, 8))
    )
    val lines = buildMushafPages(listOf(raw(1, 1..1), raw(2, 1..1), raw)).last().lines
    assertThat((lines[0] as MushafLine.Text).ayahs).containsExactly(ayahKey(2, 6), ayahKey(2, 6), ayahKey(2, 7)).inOrder()
    assertThat((lines[1] as MushafLine.Text).ayahs).containsExactly(ayahKey(2, 7), ayahKey(2, 8)).inOrder()
    assertThat(ayahKeySura(ayahKey(114, 6))).isEqualTo(114)
    assertThat(ayahKeyAyah(ayahKey(114, 6))).isEqualTo(6)
  }

  @Test
  fun fatihaHasHeaderButNoSeparateBasmala() {
    val pages = buildMushafPages(listOf(raw(1, 2..8, listOf(1 to 2))))
    assertThat(pages[0].lines).hasSize(8)
    assertThat(pages[0].lines[0]).isEqualTo(MushafLine.SuraHeader(1))
    assertThat(pages[0].lines[1]).isInstanceOf(MushafLine.Text::class.java)
  }

  @Test
  fun suraGetsHeaderAndBasmalaAboveItsFirstAyah() {
    val pages = buildMushafPages(listOf(raw(1, 1..1), raw(2, 1..1), raw(3, (1..6) + (9..15), listOf(3 to 9))))
    assertThat(pages[2].lines[6]).isEqualTo(MushafLine.SuraHeader(3))
    assertThat(pages[2].lines[7]).isEqualTo(MushafLine.Basmala)
  }

  @Test
  fun tawbaHasNoBasmala() {
    val pages = buildMushafPages(listOf(raw(1, 1..1), raw(2, 1..1), raw(3, 2..15, listOf(9 to 2))))
    assertThat(pages[2].lines[0]).isEqualTo(MushafLine.SuraHeader(9))
    assertThat(pages[2].lines.drop(1)).doesNotContain(MushafLine.Basmala)
  }

  @Test
  fun headerMovesToThePreviousPageWhenSuraStartsOnLineTwo() {
    val pages = buildMushafPages(
      listOf(raw(1, 1..1), raw(2, 1..1), raw(3, 1..14), raw(4, 2..15, listOf(4 to 2)))
    )
    assertThat(pages[2].lines[14]).isEqualTo(MushafLine.SuraHeader(4))
    assertThat(pages[3].lines[0]).isEqualTo(MushafLine.Basmala)
  }

  @Test
  fun suraJumpsGoToThePageWithTheHeader() {
    val pages = buildMushafPages(
      listOf(raw(1, 2..8, listOf(1 to 2)), raw(2, 3..8, listOf(2 to 3)), raw(3, 1..14), raw(4, 2..15, listOf(4 to 2)))
    )
    assertThat(suraHeaderPages(pages)).containsExactly(1, 1, 2, 2, 4, 3)
  }

  @Test
  fun anAyahsLastWordIsItsEndMarkerUnlessItGoesOnToTheNextPage() {
    // page 1: 1:1 (2 words), 1:2 (3 words, the last one only begins here and goes on to page 2)
    val first = RawMushafPage(
      1, 1, 1, 1 to 1, mapOf(1 to listOf("a", "b", "c"), 2 to listOf("d", "e")), emptyList(),
      listOf(ayahKey(1, 1), ayahKey(1, 1), ayahKey(1, 2), ayahKey(1, 2), ayahKey(1, 2))
    )
    val second = RawMushafPage(
      2, 1, 1, 1 to 2, mapOf(1 to listOf("f", "g")), emptyList(),
      listOf(ayahKey(1, 2), ayahKey(1, 2))
    )
    val pages = buildMushafPages(listOf(first, second))
    assertThat((pages[0].lines[0] as MushafLine.Text).ends).containsExactly(false, true, false).inOrder()
    assertThat((pages[0].lines[1] as MushafLine.Text).ends).containsExactly(false, false).inOrder()
    assertThat((pages[1].lines[0] as MushafLine.Text).ends).containsExactly(false, true).inOrder()
  }
}
