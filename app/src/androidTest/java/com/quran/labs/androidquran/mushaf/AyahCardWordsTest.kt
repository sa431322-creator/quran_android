package com.quran.labs.androidquran.mushaf

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import com.quran.mobile.feature.mushaf.data.MushafEdition
import com.quran.mobile.feature.mushaf.data.MushafRepository
import com.quran.mobile.feature.mushaf.data.ayahKeyAyah
import com.quran.mobile.feature.mushaf.data.ayahKeySura
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The ayah cards wrap an ayah between its words and never inside one, so every word must fit a
 * card on the narrowest phone at the largest text size: 360 dp, less the list's and the card's
 * padding, at the cards' largest Arabic size (64 sp, the text-size setting's maximum).
 */
@RunWith(AndroidJUnit4::class)
class AyahCardWordsTest {
  @get:Rule val compose = createComposeRule()

  private val pages = listOf(1, 2, 50, 77, 575, 569, 568, 600, 576, 303, 565, 519, 457)

  @Test
  fun everyWordFitsACardAtTheLargestText() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    val data = runBlocking { MushafRepository(context, "", MushafEdition.PLAIN_V2).data() }
    val fonts = pages.associateWith { page ->
      val file = File(context.cacheDir, "mushaf-test-p$page.ttf")
      testAssets.open("mushaf-test-fonts/p$page.ttf").use { input -> file.outputStream().use { input.copyTo(it) } }
      FontFamily(Font(file))
    }
    var measurer: TextMeasurer? = null
    var density: Density? = null
    compose.setContent {
      measurer = rememberTextMeasurer()
      density = LocalDensity.current
    }
    compose.waitForIdle()
    val textMeasurer = measurer!!
    // 360 dp less the list's 16 dp and the card's 14 dp on each side
    val cardWidth = with(density!!) { (360.dp - 32.dp - 28.dp).toPx() }

    val tooWide = mutableListOf<String>()
    var words = 0
    for ((key, glyphs) in data.ayahs) {
      for (glyph in glyphs) {
        if (glyph.isEnd || glyph.page !in fonts) continue
        words++
        val width = textMeasurer.measure(
          glyph.glyph,
          TextStyle(fontFamily = fonts.getValue(glyph.page), fontSize = 64.sp),
          softWrap = false,
          maxLines = 1
        ).size.width
        if (width > cardWidth) tooWide += "${ayahKeySura(key)}:${ayahKeyAyah(key)} on page ${glyph.page}: $width px > $cardWidth"
      }
    }
    assertWithMessage("words checked: $words").that(words).isGreaterThan(1000)
    assertWithMessage("${tooWide.size} words wider than a card:\n" + tooWide.take(20).joinToString("\n"))
      .that(tooWide).isEmpty()
  }
}
