package com.quran.labs.androidquran.feature.share

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ShareCardRendererTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun content(verses: Int, tafsir: String?) = ShareCardContent(
    suraTitle = "سوره بقره",
    verses = (1..verses).map { ShareVerse(SuraAyah(2, it), "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ ".repeat(6), tafsir) },
    rangeLabel = "سوره بقره: ۱-$verses",
    pageLabel = "صفحه ۲",
    juzLabel = "جزء ۱",
    tafsirSourceName = tafsir?.let { "تفسیر فارسی" },
    downloadLink = "tasnim.app"
  )

  @Test
  fun `every size renders at its full resolution`() {
    val renderer = ShareCardRenderer(context)
    ShareAspect.entries.forEach { aspect ->
      val bitmap = renderer.render(content(1, null), ShareCardOptions(aspect = aspect))
      assertThat(bitmap.width).isEqualTo(aspect.width)
      assertThat(bitmap.height).isEqualTo(aspect.height)
    }
  }

  @Test
  fun `a long passage with tafsir still renders on every background`() {
    val renderer = ShareCardRenderer(context)
    val long = content(20, "متن تفسیر بلند ".repeat(80))
    for (background in 0 until ShareBackgrounds.COUNT) {
      val bitmap = renderer.render(long, ShareCardOptions(background = background, showTafsir = true), scale = 0.5f)
      assertThat(bitmap.width).isEqualTo(540)
    }
  }

  @Test
  fun `ayah numbers use Arabic-Indic digits and tafsir numbers Persian digits`() {
    assertThat(ShareCardRenderer.arabicDigits(255)).isEqualTo("٢٥٥")
    assertThat(ShareCardRenderer.persianDigits(255)).isEqualTo("۲۵۵")
  }
}
