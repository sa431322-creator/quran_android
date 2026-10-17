package com.quran.mobile.feature.tajweed.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TajweedPageTest {

  private fun raw(page: Int, lines: Iterable<Int>, starts: List<Pair<Int, Int>> = emptyList()) =
    RawTajweedPage(page, 1, 1, lines.associateWith { listOf("x") }, starts)

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
