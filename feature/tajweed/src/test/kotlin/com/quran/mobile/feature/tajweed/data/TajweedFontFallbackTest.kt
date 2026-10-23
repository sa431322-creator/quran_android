package com.quran.mobile.feature.tajweed.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TajweedFontFallbackTest {

  @Test
  fun `fonts fall back to the tasnim qul folder`() {
    assertThat(tajweedFontFallbackUrl("http://10.0.2.2:3000/", 12))
      .isEqualTo("http://10.0.2.2:3000/data/qul/v4-tajweed/ttf/p12.ttf")
    assertThat(tajweedFontFallbackUrl("http://10.0.2.2:3000", 604))
      .isEqualTo("http://10.0.2.2:3000/data/qul/v4-tajweed/ttf/p604.ttf")
  }

  @Test
  fun `blank base means no fallback`() {
    assertThat(tajweedFontFallbackUrl("", 1)).isNull()
    assertThat(tajweedFontFallbackUrl(" ", 1)).isNull()
  }
}
