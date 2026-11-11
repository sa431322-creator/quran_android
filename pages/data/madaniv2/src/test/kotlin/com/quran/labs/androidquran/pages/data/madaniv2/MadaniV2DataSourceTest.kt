package com.quran.labs.androidquran.pages.data.madaniv2

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import org.junit.Test

class MadaniV2DataSourceTest {
  private val source = MadaniV2DataSource()
  private val quranInfo = QuranInfo(source)

  @Test
  fun tableSizes() {
    assertThat(source.pageForSuraArray.size).isEqualTo(114)
    assertThat(source.suraForPageArray.size).isEqualTo(604)
    assertThat(source.ayahForPageArray.size).isEqualTo(604)
    assertThat(source.pageForJuzArray.size).isEqualTo(30)
    assertThat(source.quarterStartByPage.size).isEqualTo(604)
    assertThat(source.quartersArray.size).isEqualTo(240)
    assertThat(source.numberOfAyahsForSuraArray.sum()).isEqualTo(6236)
  }

  @Test
  fun pagesStartInReadingOrder() {
    for (page in 1 until 604) {
      val sura = source.suraForPageArray[page - 1]
      val ayah = source.ayahForPageArray[page - 1]
      val nextSura = source.suraForPageArray[page]
      val nextAyah = source.ayahForPageArray[page]
      assertThat(nextSura > sura || (nextSura == sura && nextAyah >= ayah)).isTrue()
    }
  }

  @Test
  fun everySuraStartsOnItsPage() {
    for (sura in 1..114) {
      assertThat(quranInfo.getPageFromSuraAyah(sura, 1)).isEqualTo(source.pageForSuraArray[sura - 1])
    }
  }

  @Test
  fun sajdaPagesMatchTheV2Layout() {
    // from docs/text-mushaf/reports/data-2026-11-06.md
    val sajda = mapOf(
      (7 to 206) to 176, (13 to 15) to 251, (16 to 50) to 272, (17 to 109) to 293,
      (19 to 58) to 309, (22 to 18) to 334, (22 to 77) to 341, (25 to 60) to 365,
      (27 to 26) to 379, (32 to 15) to 416, (38 to 24) to 454, (41 to 38) to 480,
      (53 to 62) to 528, (84 to 21) to 589, (96 to 19) to 598
    )
    sajda.forEach { (ayah, page) ->
      assertThat(quranInfo.getPageFromSuraAyah(ayah.first, ayah.second)).isEqualTo(page)
    }
  }

  @Test
  fun juzInPageHeaderMatchesThePrint() {
    // the four pages where a juz starts below the first line, read from the V2 print
    assertThat(quranInfo.getJuzForDisplayFromPage(62)).isEqualTo(4)
    assertThat(quranInfo.getJuzForDisplayFromPage(121)).isEqualTo(6)
    assertThat(quranInfo.getJuzForDisplayFromPage(201)).isEqualTo(10)
    assertThat(quranInfo.getJuzForDisplayFromPage(502)).isEqualTo(26)
  }
}
