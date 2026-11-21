package com.quran.data.page.provider

import com.google.common.truth.Truth.assertThat
import com.quran.data.source.MushafType
import org.junit.Test

class QuranDataModuleMushafTypeTest {

  // Tasnim registers the classic Madani page type and its two Madinah V2 page types only
  @Test
  fun `each page type names the mushaf whose page numbers it uses`() {
    val mushafTypes = mapOf(
      "madani" to QuranDataModule.provideMadaniPageSet(),
      "madani_v2" to QuranDataModule.provideMadaniV2PageSet(),
      "madani_v2_images" to QuranDataModule.provideMadaniV2ImagesPageSet("https://example.invalid")
    ).mapValues { (_, pageProvider) -> pageProvider.mushafType() }

    assertThat(mushafTypes).containsExactly(
      "madani", MushafType.MADANI_1405,
      "madani_v2", MushafType.MADANI_1405,
      "madani_v2_images", MushafType.MADANI_1405
    )
  }
}
