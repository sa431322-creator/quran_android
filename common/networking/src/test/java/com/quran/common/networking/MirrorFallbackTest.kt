package com.quran.common.networking

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class MirrorFallbackTest {
  private val base = "https://tasnim.example/"

  private fun fallback(url: String, tasnimBase: String = base) =
    MirrorFallback.fallbackUrl(url.toHttpUrl(), tasnimBase)?.toString()

  @Test
  fun `android quran com data files map to the tasnim data folder`() {
    assertThat(fallback("https://android.quran.com/data/zips/images_1260.zip"))
      .isEqualTo("https://tasnim.example/data/zips/images_1260.zip")
    assertThat(fallback("https://android.quran.com/data/width_1024/page004.png"))
      .isEqualTo("https://tasnim.example/data/width_1024/page004.png")
    assertThat(fallback("https://android.quran.com/data/patches/v8/patch1260_v8_to_v9.zip"))
      .isEqualTo("https://tasnim.example/data/patches/v8/patch1260_v8_to_v9.zip")
    assertThat(fallback("https://android.quran.com/data/databases/ayahinfo/ayahinfo_1260.zip"))
      .isEqualTo("https://tasnim.example/data/databases/ayahinfo/ayahinfo_1260.zip")
    assertThat(fallback("https://android.quran.com/data/databases/quran.ar.uthmani.v2.db.zip"))
      .isEqualTo("https://tasnim.example/data/databases/quran.ar.uthmani.v2.db.zip")
  }

  @Test
  fun `translation downloads keep their query`() {
    assertThat(fallback("https://android.quran.com/data/getTranslation.php?id=33&ext=zip"))
      .isEqualTo("https://tasnim.example/data/getTranslation.php?id=33&ext=zip")
  }

  @Test
  fun `files quran app madani files map to the tasnim data folder`() {
    assertThat(fallback("https://files.quran.app/hafs/madani/zips/images_800.zip"))
      .isEqualTo("https://tasnim.example/data/zips/images_800.zip")
    assertThat(fallback("https://files.quran.app/hafs/madani/width_480/page604.png"))
      .isEqualTo("https://tasnim.example/data/width_480/page604.png")
  }

  @Test
  fun `translation redirect targets have no copy`() {
    assertThat(fallback("https://files.quran.app/databases/translations/en.sahih.db.zip")).isNull()
  }

  @Test
  fun `page type previews map to the tasnim data folder`() {
    assertThat(fallback("https://quran.app/data/pagetypes/snips/madani.png"))
      .isEqualTo("https://tasnim.example/data/pagetypes/snips/madani.png")
    assertThat(fallback("https://quran.app/data/translations.php?v=5")).isNull()
  }

  @Test
  fun `tajweed fonts map to the tasnim qul folder`() {
    assertThat(fallback("https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v4-tajweed/ttf/p12.ttf"))
      .isEqualTo("https://tasnim.example/data/qul/v4-tajweed/ttf/p12.ttf")
  }

  @Test
  fun `other hosts and paths have no copy`() {
    assertThat(fallback("https://download.quranicaudio.com/quran/x/001.mp3")).isNull()
    assertThat(fallback("https://android.quran.com/other/file.zip")).isNull()
    assertThat(fallback("https://android.quran.com/data/")).isNull()
    assertThat(fallback("https://tasnim.example/data/zips/images_1260.zip")).isNull()
    // audio timing databases are not backed up yet
    assertThat(fallback("https://android.quran.com/data/databases/audio/husary.zip")).isNull()
  }

  @Test
  fun `blank base means no fallback`() {
    assertThat(fallback("https://android.quran.com/data/zips/images_1260.zip", "")).isNull()
    assertThat(fallback("https://android.quran.com/data/zips/images_1260.zip", "  ")).isNull()
  }

  @Test
  fun `base without a trailing slash is normalised`() {
    assertThat(fallback("https://android.quran.com/data/zips/images_1260.zip", "http://10.0.2.2:3000"))
      .isEqualTo("http://10.0.2.2:3000/data/zips/images_1260.zip")
  }
}
