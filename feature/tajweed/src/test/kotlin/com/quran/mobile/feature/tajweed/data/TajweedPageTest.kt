package com.quran.mobile.feature.tajweed.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TajweedPageTest {

  private fun raw(page: Int, lines: Iterable<Int>, starts: List<Pair<Int, Int>> = emptyList()) =
    RawTajweedPage(page, 1, 1, 1 to 1, lines.associateWith { listOf("x") }, starts)

  private fun page(page: Int, sura: Int, ayah: Int) =
    TajweedPage(page, sura, 1, sura to ayah, emptyList())

  @Test
  fun eachWordGetsItsAyahInReadingOrder() {
    val raw = RawTajweedPage(
      page = 3, sura = 2, juz = 1, firstAyah = 2 to 6,
      lines = mapOf(2 to listOf("c", "d"), 1 to listOf("a", "b", "x")),
      suraStarts = emptyList(),
      wordAyahs = listOf(ayahKey(2, 6), ayahKey(2, 6), ayahKey(2, 7), ayahKey(2, 7), ayahKey(2, 8))
    )
    val lines = buildTajweedPages(listOf(raw(1, 1..1), raw(2, 1..1), raw)).last().lines
    assertThat((lines[0] as TajweedLine.Text).ayahs).containsExactly(ayahKey(2, 6), ayahKey(2, 6), ayahKey(2, 7)).inOrder()
    assertThat((lines[1] as TajweedLine.Text).ayahs).containsExactly(ayahKey(2, 7), ayahKey(2, 8)).inOrder()
    assertThat(ayahKeySura(ayahKey(114, 6))).isEqualTo(114)
    assertThat(ayahKeyAyah(ayahKey(114, 6))).isEqualTo(6)
  }

  @Test
  fun pageForFindsThePageWhereTheAyahIs() {
    // first ayahs of pages 1, 2, 3, 76, 77 and 604 in the V4 layout
    val pages = listOf(page(1, 1, 1), page(2, 2, 1), page(3, 2, 6), page(76, 3, 195), page(77, 4, 1), page(604, 112, 1))
    assertThat(pages.pageFor(1, 7)).isEqualTo(1)
    assertThat(pages.pageFor(2, 1)).isEqualTo(2)
    assertThat(pages.pageFor(2, 5)).isEqualTo(2)
    assertThat(pages.pageFor(2, 6)).isEqualTo(3)
    assertThat(pages.pageFor(3, 200)).isEqualTo(76)
    assertThat(pages.pageFor(4, 1)).isEqualTo(77)
    assertThat(pages.pageFor(114, 6)).isEqualTo(604)
  }

  @Test
  fun fatihaHasHeaderButNoSeparateBasmala() {
    val pages = buildTajweedPages(listOf(raw(1, 2..8, listOf(1 to 2))))
    assertThat(pages[0].lines).hasSize(8)
    assertThat(pages[0].lines[0]).isEqualTo(TajweedLine.SuraHeader(1))
    assertThat(pages[0].lines[1]).isInstanceOf(TajweedLine.Text::class.java)
  }

  @Test
  fun suraGetsHeaderAndBasmalaAboveItsFirstAyah() {
    val pages = buildTajweedPages(listOf(raw(1, 1..1), raw(2, 1..1), raw(3, (1..6) + (9..15), listOf(3 to 9))))
    assertThat(pages[2].lines[6]).isEqualTo(TajweedLine.SuraHeader(3))
    assertThat(pages[2].lines[7]).isEqualTo(TajweedLine.Basmala)
  }

  @Test
  fun tawbaHasNoBasmala() {
    val pages = buildTajweedPages(listOf(raw(1, 1..1), raw(2, 1..1), raw(3, 2..15, listOf(9 to 2))))
    assertThat(pages[2].lines[0]).isEqualTo(TajweedLine.SuraHeader(9))
    assertThat(pages[2].lines.drop(1)).doesNotContain(TajweedLine.Basmala)
  }

  @Test
  fun headerMovesToThePreviousPageWhenSuraStartsOnLineTwo() {
    val pages = buildTajweedPages(
      listOf(raw(1, 1..1), raw(2, 1..1), raw(3, 1..14), raw(4, 2..15, listOf(4 to 2)))
    )
    assertThat(pages[2].lines[14]).isEqualTo(TajweedLine.SuraHeader(4))
    assertThat(pages[3].lines[0]).isEqualTo(TajweedLine.Basmala)
  }

  @Test
  fun suraJumpsGoToThePageWithTheHeader() {
    val pages = buildTajweedPages(
      listOf(raw(1, 2..8, listOf(1 to 2)), raw(2, 3..8, listOf(2 to 3)), raw(3, 1..14), raw(4, 2..15, listOf(4 to 2)))
    )
    assertThat(suraHeaderPages(pages)).containsExactly(1, 1, 2, 2, 4, 3)
  }
}
