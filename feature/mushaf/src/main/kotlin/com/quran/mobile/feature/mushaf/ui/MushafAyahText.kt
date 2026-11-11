package com.quran.mobile.feature.mushaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.quran.mobile.feature.mushaf.data.AyahGlyph
import com.quran.mobile.feature.mushaf.data.MushafRepository
import java.io.File

/**
 * One ayah on its own (an ayah card), in the mushaf's own glyphs: each word in the font of the
 * page it is on, wrapped right to left to the width given, and the ayah's end marker drawn as the
 * page's gilt marker. The Quran glyphs are the page's, unchanged; only the line breaks differ.
 */
@Composable
fun MushafAyahText(
  glyphs: List<AyahGlyph>,
  ayahNumber: Int,
  fonts: Map<Int, FontFamily>,
  fontSize: TextUnit,
  style: MushafPageStyle,
  modifier: Modifier = Modifier
) {
  val dp = with(LocalDensity.current) { fontSize.toPx().toDp() }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    FlowRow(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(dp * 0.22f),
      verticalArrangement = Arrangement.spacedBy(dp * 0.35f),
      itemVerticalAlignment = Alignment.CenterVertically
    ) {
      glyphs.forEach { glyph ->
        if (glyph.isEnd) {
          // the same marker as on the page, sized like a word
          MushafAyahMarker(ayahNumber, style, dp * 1.25f)
        } else {
          Text(
            text = glyph.glyph,
            style = TextStyle(fontFamily = fonts[glyph.page], fontSize = fontSize, color = style.ink),
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.drawWithContent { drawContentFiltered(style.glyphFilter) }
          )
        }
      }
    }
  }
}

/** An ayah's number in the page's gilt marker (eight-pointed star and ring), [size] across. */
@Composable
fun MushafAyahMarker(ayahNumber: Int, style: MushafPageStyle, size: Dp, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  Box(
    modifier
      .size(size)
      .drawBehind {
        drawAyahMarker(measurer, ayahNumber, center, this.size.minDimension, style.gilt, style.ink, style.headerFont)
      }
  )
}

/**
 * A sura's opening as the page draws it: the gilt header frame with [name], then the basmala
 * (none for at-Tawba, and none for al-Fatiha, whose basmala is its first ayah).
 */
@Composable
fun MushafSuraStart(
  sura: Int,
  name: String,
  style: MushafPageStyle,
  basmalaFont: FontFamily,
  rowHeight: Dp,
  modifier: Modifier = Modifier
) {
  val fontPx = with(LocalDensity.current) { rowHeight.toPx() / 1.9f }
  Column(modifier) {
    SuraHeader(name, style, rowHeight, Modifier.fillMaxWidth().height(rowHeight))
    if (sura != 1 && sura != 9) {
      Basmala(basmalaFont, fontPx, Modifier.fillMaxWidth().height(rowHeight), style.ink)
    }
  }
}

/** The fonts of an ayah's pages: loading, ready, or failed (offline and not stored yet). */
sealed interface PageFontsState {
  data object Loading : PageFontsState
  data class Ready(val fonts: Map<Int, FontFamily>) : PageFontsState
  data object Failed : PageFontsState
}

/**
 * The fonts of [pages], downloading the ones not stored yet; a new [attempt] tries again after a
 * failure. Fonts already stored are there on the first frame.
 */
@Composable
fun rememberPageFonts(repository: MushafRepository, pages: List<Int>, attempt: Int = 0): PageFontsState {
  // looked up once per ayah and edition, not on every recomposition (a file check per page)
  val stored = remember(repository, pages) {
    pages.mapNotNull { page -> repository.cachedFont(page)?.let { page to PageFonts.family(it) } }.toMap()
      .takeIf { it.size == pages.size }
      ?.let { PageFontsState.Ready(it) }
  }
  val state by produceState(stored ?: PageFontsState.Loading, repository, pages, attempt) {
    // new keys start again: the value kept from the last ones is another edition's or page's fonts
    value = stored ?: PageFontsState.Loading
    if (stored == null) {
      value = orNull { pages.associateWith { page -> PageFonts.family(repository.font(page)) } }
        ?.let { PageFontsState.Ready(it) }
        ?: PageFontsState.Failed
    }
  }
  return state
}

/** Draws the content through [filter], when there is one (a night page's [MushafPageStyle.glyphFilter]). */
internal fun ContentDrawScope.drawContentFiltered(filter: ColorFilter?) {
  if (filter == null) {
    drawContent()
    return
  }
  drawIntoCanvas { canvas ->
    // the layer reaches past the box: a QPC glyph's ink often runs outside its advance (the tail
    // of a nun, marks above and below), and a layer the box's size would cut it off. By about a
    // glyph's size (the box's height), not its width: a page line's layer stays small
    val margin = size.height
    canvas.saveLayer(
      Rect(-margin, -margin, size.width + margin, size.height + margin),
      Paint().apply { colorFilter = filter }
    )
    drawContent()
    canvas.restore()
  }
}

/** One [FontFamily] per page font file, shared by the cards, so scrolling does not reload them. */
private object PageFonts {
  private const val MAX = 48
  private val families = object : LinkedHashMap<String, FontFamily>(MAX, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FontFamily>?) = size > MAX
  }

  fun family(file: File): FontFamily = synchronized(families) {
    families.getOrPut(file.path) { FontFamily(Font(file)) }
  }
}
