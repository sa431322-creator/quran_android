package com.quran.labs.androidquran.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ShareUtilTest {
  private val quranInfo = QuranInfo(MadaniDataSource())
  private val shareUtil = ShareUtil(QuranDisplayData(quranInfo), quranInfo)

  @Test
  fun `a single ayah shows its page`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertThat(shareUtil.pageLabel(context, SuraAyah(1, 1), null)).isEqualTo(" — صفحه 1")
  }

  @Test
  fun `ayahs on one page show that page once`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertThat(shareUtil.pageLabel(context, SuraAyah(1, 1), SuraAyah(1, 7))).isEqualTo(" — صفحه 1")
  }

  @Test
  fun `ayahs across pages show the page range`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertThat(shareUtil.pageLabel(context, SuraAyah(1, 7), SuraAyah(2, 5))).isEqualTo(" — صفحه 1-2")
  }

  @Test
  fun `range labels cover one ayah, one sura and two suras`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertThat(shareUtil.rangeLabel(context, SuraAyah(2, 255), SuraAyah(2, 255)))
      .isEqualTo("Surah Al-Baqarah: 255")
    assertThat(shareUtil.rangeLabel(context, SuraAyah(2, 255), SuraAyah(2, 257)))
      .isEqualTo("Surah Al-Baqarah: 255-257")
    assertThat(shareUtil.rangeLabel(context, SuraAyah(2, 286), SuraAyah(3, 3)))
      .isEqualTo("Surah Al-Baqarah: 286 - Surah Āli-ʿImrān: 3")
  }
}
