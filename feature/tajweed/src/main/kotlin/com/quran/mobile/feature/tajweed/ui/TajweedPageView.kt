package com.quran.mobile.feature.tajweed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.mobile.feature.tajweed.R
import com.quran.mobile.feature.tajweed.data.TAJWEED_LINES_PER_PAGE
import com.quran.mobile.feature.tajweed.data.TajweedLine
import com.quran.mobile.feature.tajweed.data.TajweedPage

/**
 * A full line of a QPC V4 page usually measures about 16.2 em of glyphs, so the text is
 * sized for 17.4 em across the page and every page reads at the same size.
 */
private const val LINE_EM = 17.4f
/** Lines with less text than this (sura endings, the opening pages) are centered, not justified. */
private const val JUSTIFY_THRESHOLD = 0.85f
/**
 * Some V4 page fonts are not fitted yet: a few of their lines run up to 22 em. Lines wider
 * than a typical full line are shrunk to it, so every line keeps the page's word spacing.
 */
private const val TYPICAL_LINE_EM = 16.4f
private const val MIN_ROW_EM = 1.75f
private const val SCROLL_ROW_EM = 1.95f

/** The tajweed colors are fixed in the fonts, so the page is always drawn on paper. */
val TajweedPaper = Color(0xFFFCF8EF)
private val TajweedInk = Color(0xFF000000)

@Composable
fun TajweedPageView(
  page: TajweedPage,
  pageFont: FontFamily,
  basmalaFont: FontFamily,
  suraNames: List<String>,
  modifier: Modifier = Modifier
) {
  BoxWithConstraints(
    modifier
      .fillMaxSize()
      .background(TajweedPaper)
      .padding(horizontal = 14.dp, vertical = 8.dp)
  ) {
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidth.toPx() }
    val heightPx = with(density) { maxHeight.toPx() }
    val fontPx = widthPx / LINE_EM
    val fitRow = heightPx / TAJWEED_LINES_PER_PAGE
    // landscape and short screens scroll instead of squeezing the lines together
    val scrolls = fitRow < fontPx * MIN_ROW_EM
    val rowPx = if (scrolls) fontPx * SCROLL_ROW_EM else fitRow
    val rowHeight = with(density) { rowPx.toDp() }
    val textStyle = TextStyle(
      fontFamily = pageFont,
      fontSize = with(density) { fontPx.toSp() },
      color = TajweedInk
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight()),
      verticalArrangement = if (page.isOpening) Arrangement.Center else Arrangement.Top
    ) {
      page.lines.forEach { line ->
        val rowModifier = Modifier.fillMaxWidth().height(rowHeight)
        when (line) {
          is TajweedLine.Text -> PageLine(line.words, textStyle, page.isOpening, rowModifier)
          is TajweedLine.SuraHeader -> SuraHeader(
            name = suraNames.getOrElse(line.sura - 1) { line.sura.toString() },
            rowHeight = rowHeight,
            modifier = rowModifier
          )
          TajweedLine.Basmala -> Basmala(basmalaFont, fontPx, rowModifier)
          TajweedLine.Blank -> Box(rowModifier)
        }
      }
    }
  }
}

/**
 * Lays the words out right to left. Full lines are justified edge to edge like the printed
 * page; short lines are centered with a word space between words.
 */
@Composable
private fun PageLine(words: List<String>, style: TextStyle, centered: Boolean, modifier: Modifier) {
  Layout(
    modifier = modifier,
    content = {
      words.forEach { Text(text = it, style = style, softWrap = false, maxLines = 1) }
    }
  ) { measurables, constraints ->
    val placeables = measurables.map { it.measure(Constraints()) }
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val gaps = placeables.size - 1
    val wordSpace = style.fontSize.toPx() * 0.25f
    val natural = placeables.sumOf { it.width }
    val scale = minOf(1f, TYPICAL_LINE_EM * style.fontSize.toPx() / natural)
    val total = natural * scale
    val justify = !centered && gaps > 0 && total >= width * JUSTIFY_THRESHOLD
    val gap = if (justify) (width - total) / gaps else wordSpace
    val used = total + gap * gaps
    layout(width, height) {
      var right = if (justify) width.toFloat() else width - (width - used) / 2f
      placeables.forEach { placeable ->
        right -= placeable.width * scale
        val y = (height - placeable.height) / 2
        if (scale < 1f) {
          placeable.placeWithLayer(right.toInt(), y) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0.5f)
          }
        } else {
          placeable.place(right.toInt(), y)
        }
        right -= gap
      }
    }
  }
}

@Composable
private fun SuraHeader(name: String, rowHeight: Dp, modifier: Modifier) {
  val palette = LocalTasnimPalette.current
  Box(modifier.padding(vertical = rowHeight * 0.12f), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .border(1.5.dp, palette.gold, RoundedCornerShape(6.dp))
        .padding(2.5.dp)
        .border(0.8.dp, palette.gold, RoundedCornerShape(4.dp))
        .background(palette.primary.copy(alpha = 0.06f), RoundedCornerShape(4.dp)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = stringResource(R.string.tajweed_sura_header, name),
        fontFamily = NotoKufiArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = palette.primary,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun Basmala(font: FontFamily, fontPx: Float, modifier: Modifier) {
  val fontSize = with(LocalDensity.current) { (fontPx * 0.95f).toSp() }
  Box(modifier, contentAlignment = Alignment.Center) {
    Text(
      text = BASMALA,
      fontFamily = font,
      fontSize = fontSize,
      color = TajweedInk,
      textAlign = TextAlign.Center,
      maxLines = 1,
      softWrap = false
    )
  }
}

private const val BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
