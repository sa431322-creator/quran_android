package com.quran.labs.androidquran.feature.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareVideoEffectsTest {
  private val boundaries = listOf(0L, 4000L, 9000L)

  @Test
  fun `the picture is mixed most at a card change`() {
    assertThat(ShareVideoEffects.fadeAmount(4000, boundaries)).isWithin(0.001f).of(0.85f)
  }

  @Test
  fun `the fade eases out within a few hundred milliseconds`() {
    val near = ShareVideoEffects.fadeAmount(4100, boundaries)
    val farther = ShareVideoEffects.fadeAmount(4200, boundaries)
    assertThat(near).isGreaterThan(farther)
    assertThat(farther).isGreaterThan(0f)
    assertThat(ShareVideoEffects.fadeAmount(4300, boundaries)).isEqualTo(0f)
  }

  @Test
  fun `between card changes the picture is untouched`() {
    assertThat(ShareVideoEffects.fadeAmount(6500, boundaries)).isEqualTo(0f)
  }
}
