package com.quran.labs.androidquran.mushaf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.quran.mobile.feature.mushaf.data.MushafEdition
import com.quran.mobile.feature.mushaf.data.MushafLine
import com.quran.mobile.feature.mushaf.data.MushafPage
import com.quran.mobile.feature.mushaf.data.MushafRepository
import com.quran.mobile.feature.mushaf.ui.LABEL_ROW_TAG
import com.quran.mobile.feature.mushaf.ui.MushafPageHits
import com.quran.mobile.feature.mushaf.ui.MushafPageLabels
import com.quran.mobile.feature.mushaf.ui.MushafPageStyle
import com.quran.mobile.feature.mushaf.ui.MushafPageView
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The text mushaf on the screen sizes people use, in portrait and landscape, at the smallest and
 * largest font scales: the page layout (15 lines, the same words on each) only scales.
 *
 * Pages: the opening pages, a sura start, a header on the page before its sura, the five pages
 * with the widest lines (V2 font metrics) and the five with the most words.
 */
@RunWith(AndroidJUnit4::class)
class MushafPageSizesTest {
  @get:Rule val compose = createComposeRule()

  private val pages = listOf(1, 2, 50, 77, 575, 569, 568, 600, 576, 303, 565, 519, 457)
  private val portraitSizes = listOf(
    DpSize(360.dp, 640.dp),   // small phone
    DpSize(393.dp, 852.dp),   // standard phone
    DpSize(430.dp, 932.dp),   // large phone
    DpSize(600.dp, 960.dp),   // 7-8" tablet
    DpSize(800.dp, 1280.dp),  // 10-11" tablet
    DpSize(1024.dp, 1366.dp)  // 12-13" tablet
  )
  private val fontScales = listOf(0.85f, 1f, 1.3f, 2f)

  private class Config(val page: MushafPage, val font: FontFamily, val size: DpSize, val fontScale: Float)

  @Test
  fun pagesOnlyScaleOnEveryScreenAndFontSize() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    val data = runBlocking { MushafRepository(context, "", MushafEdition.PLAIN_V2).data() }
    val fonts = pages.associateWith { page ->
      val file = File(context.cacheDir, "mushaf-test-p$page.ttf")
      testAssets.open("mushaf-test-fonts/p$page.ttf").use { input -> file.outputStream().use { input.copyTo(it) } }
      FontFamily(Font(file))
    }
    val style = MushafPageStyle(
      paper = Color.White, headerFont = FontFamily.Default, headerColor = Color.DarkGray,
      accent = Color.DarkGray, gilt = Color.Yellow,
      selection = Color.Cyan, recitation = Color.Green
    )
    val hits = MushafPageHits()
    var config by mutableStateOf<Config?>(null)

    compose.setContent {
      val current = config ?: return@setContent
      DeviceConfigurationOverride(
        DeviceConfigurationOverride.ForcedSize(current.size) then DeviceConfigurationOverride.FontScale(current.fontScale)
      ) {
        MushafPageView(
          page = current.page,
          pageFont = current.font,
          basmalaFont = FontFamily.Default,
          style = style,
          // the longest sura name in the header, to catch labels that do not fit
          labels = MushafPageLabels("سُورَةُ آلِ عِمۡرَانَ", "الجزء ٣٠", "٦٠٤"),
          suraHeaderName = { "سُورَةُ آلِ عِمۡرَانَ" },
          hits = hits
        )
      }
    }

    val failures = mutableListOf<String>()
    var checks = 0
    for (pageNumber in pages) {
      val page = data.pages[pageNumber - 1]
      val textLines = page.lines.withIndex().filter { it.value is MushafLine.Text }
      for (portrait in portraitSizes) {
        for (size in listOf(portrait, DpSize(portrait.height, portrait.width))) {
          val fontPxAtScale = mutableMapOf<Float, Float>()
          for (scale in fontScales) {
            config = Config(page, fonts.getValue(pageNumber), size, scale)
            compose.waitForIdle()
            val where = "page $pageNumber, ${size.width.value.toInt()}x${size.height.value.toInt()} dp, font x$scale"
            val m = hits.metrics
            if (m == null) {
              failures += "$where: not laid out"
              continue
            }
            checks++
            fontPxAtScale[scale] = m.fontPx
            val isPortrait = size.height >= size.width
            // portrait: all 15 rows on the screen, at no less than the print's line spacing
            if (isPortrait && m.scrolls) failures += "$where: scrolls in portrait"
            if (m.rowPx < m.fontPx * 1.75f - 0.5f) failures += "$where: rows ${m.rowPx} px for ${m.fontPx} px text"
            if (m.columnWidth > m.pageWidth + 0.5f) failures += "$where: text column wider than the page"
            // the same lines with the same words, every word inside its line
            val extents = hits.lineExtents()
            for ((row, line) in textLines) {
              val extent = extents[row]
              val words = (line as MushafLine.Text).words.size
              when {
                extent == null -> failures += "$where: line ${row + 1} not laid out"
                extent.lefts.size != words -> failures += "$where: line ${row + 1} has ${extent.lefts.size} words, not $words"
                extent.lefts.min() < -1f || extent.rights.max() > extent.width + 1f ->
                  failures += "$where: line ${row + 1} runs out of its width " +
                    "(${extent.lefts.min()}..${extent.rights.max()} in ${extent.width})"
              }
            }
            // the page's labels fit their line
            compose.onAllNodes(
              SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and hasAnyAncestor(hasTestTag(LABEL_ROW_TAG)),
              useUnmergedTree = true
            ).fetchSemanticsNodes().forEach { node ->
              val results = mutableListOf<TextLayoutResult>()
              node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
              // what is drawn: the line's own extent and height against the label's box (a
              // no-wrap paragraph is laid out at the row's width, so hasVisualOverflow misleads)
              val r = results.firstOrNull()
              val drawnWidth = r?.let { it.getLineRight(0) - it.getLineLeft(0) } ?: 0f
              if (r != null && (r.lineCount > 1 || drawnWidth > r.size.width + 1f || r.multiParagraph.height > r.size.height + 1f)) {
                failures += "$where: label «${r.layoutInput.text}» does not fit " +
                  "(text ${r.multiParagraph.width}x${r.multiParagraph.height}, box ${r.size}, limits ${r.layoutInput.constraints})"
              }
            }
          }
          // the system font size never changes the page
          val sizes = fontPxAtScale.values
          if (sizes.isNotEmpty() && sizes.max() - sizes.min() > 0.5f) {
            failures += "page $pageNumber, ${size.width.value.toInt()}x${size.height.value.toInt()} dp: text size changes with font scale $fontPxAtScale"
          }
        }
      }
    }
    val (labels, others) = failures.partition { it.contains("label «") }
    val summary = "${failures.size} problems in $checks layouts (${others.size} layout, ${labels.size} label):\n" +
      (others.take(30) + labels.take(10)).joinToString("\n")
    assertWithMessage(summary).that(failures).isEmpty()
    assertThat(checks).isEqualTo(pages.size * portraitSizes.size * 2 * fontScales.size)
  }
}
