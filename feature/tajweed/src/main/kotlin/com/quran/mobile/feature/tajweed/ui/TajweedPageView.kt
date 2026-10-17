package com.quran.mobile.feature.tajweed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val TajweedInk = Color(0xFF000000)

/**
 * How the host draws its pages, so tajweed pages look like the reader's: its paper, its page
 * header and footer, its sura header frame and its selection and recitation colors.
 */
data class TajweedPageStyle(
  val paper: Color,
  val headerFont: FontFamily,
  val headerColor: Color,
  val suraFrameFill: Color,
  val suraFrameGold: Color,
  val suraFramePill: Color,
  val selection: Color,
  val recitation: Color
)

/** The page's header and footer text, as the reader writes them for its own pages. */
data class TajweedPageLabels(val suraName: String, val juz: String, val pageNumber: String)

/** Where an ayah is on screen, in window coordinates: its first and its last word. */
data class TajweedAyahBounds(val first: Rect, val last: Rect)

@Composable
fun TajweedPageView(
  page: TajweedPage,
  pageFont: FontFamily,
  basmalaFont: FontFamily,
  style: TajweedPageStyle,
  labels: TajweedPageLabels,
  suraHeaderName: (Int) -> String,
  modifier: Modifier = Modifier,
  /** the [com.quran.mobile.feature.tajweed.data.ayahKey] selected, if any */
  selectedAyah: Int? = null,
  /** the ayah being recited, if any */
  recitedAyah: Int? = null,
  /** highlighted ayat ([com.quran.mobile.feature.tajweed.data.ayahKey]) and their colors */
  highlights: Map<Int, Color> = emptyMap(),
  onTap: () -> Unit = {},
  onAyahLongPress: (key: Int, bounds: TajweedAyahBounds) -> Unit = { _, _ -> }
) {
  // each text line's words and position, to find all of a long-pressed ayah's words
  val lines = remember(page.page) { HashMap<Int, PlacedLine>() }
  val ayahBounds: (Int) -> TajweedAyahBounds? = { key -> boundsOf(key, lines) }

  Column(
    modifier
      .fillMaxSize()
      .background(style.paper)
      .padding(horizontal = 14.dp, vertical = 4.dp)
  ) {
    PageLabelRow(start = labels.suraName, end = labels.juz, style = style)
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
        page.lines.forEachIndexed { index, line ->
          val rowModifier = Modifier.fillMaxWidth().height(rowHeight)
          when (line) {
            is TajweedLine.Text -> PageLine(
              index = index,
              lines = lines,
              words = line.words,
              ayahs = line.ayahs,
              textStyle = textStyle,
              centered = page.isOpening,
              colorFor = { key ->
                when (key) {
                  selectedAyah -> style.selection
                  recitedAyah -> style.recitation
                  else -> highlights[key]
                }
              },
              onTap = onTap,
              onLongPress = { key -> ayahBounds(key)?.let { onAyahLongPress(key, it) } },
              modifier = rowModifier
            )
            is TajweedLine.SuraHeader -> SuraHeader(
              name = suraHeaderName(line.sura),
              style = style,
              rowHeight = rowHeight,
              modifier = rowModifier.tapToToggle(onTap)
            )
            TajweedLine.Basmala -> Basmala(basmalaFont, fontPx, rowModifier.tapToToggle(onTap))
            TajweedLine.Blank -> Box(rowModifier.tapToToggle(onTap))
          }
        }
      }
    }
    PageLabelRow(center = labels.pageNumber, style = style)
  }
}

private fun Modifier.tapToToggle(onTap: () -> Unit) =
  pointerInput(onTap) { detectTapGestures { onTap() } }

/** The page's header (sura and juz) or footer (page number), like the reader's page overlay. */
@Composable
private fun PageLabelRow(style: TajweedPageStyle, start: String = "", end: String = "", center: String = "") {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    val labelStyle = TextStyle(fontFamily = style.headerFont, color = style.headerColor, fontSize = 14.sp)
    if (center.isNotEmpty()) {
      Text(center, style = labelStyle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
      // right to left: the sura on the right, the juz on the left, as on the reader's pages
      Text(start, style = labelStyle, modifier = Modifier.weight(1f))
      Text(end, style = labelStyle)
    }
  }
}

/** One text line's words, where they were placed, and where the line is in the window. */
private class PlacedLine {
  var ayahs: List<Int> = emptyList()
  var lefts = FloatArray(0)
  var rights = FloatArray(0)
  var coordinates: LayoutCoordinates? = null

  fun wordAt(x: Float): Int? {
    if (lefts.isEmpty()) return null
    lefts.indices.firstOrNull { x >= lefts[it] && x <= rights[it] }?.let { return it }
    // between words: the nearest one
    return lefts.indices.minBy { minOf(kotlin.math.abs(x - lefts[it]), kotlin.math.abs(x - rights[it])) }
  }

  fun wordRect(word: Int): Rect? {
    val layout = coordinates?.takeIf { it.isAttached } ?: return null
    val topLeft = layout.localToWindow(Offset(lefts[word], 0f))
    return Rect(topLeft, Size(rights[word] - lefts[word], layout.size.height.toFloat()))
  }
}

private fun boundsOf(key: Int, lines: Map<Int, PlacedLine>): TajweedAyahBounds? {
  val words = lines.entries.sortedBy { it.key }.flatMap { (_, line) ->
    line.ayahs.indices.filter { line.ayahs[it] == key }.map { line to it }
  }
  val first = words.firstOrNull()?.let { (line, word) -> line.wordRect(word) } ?: return null
  val last = words.last().let { (line, word) -> line.wordRect(word) } ?: return null
  return TajweedAyahBounds(first, last)
}

/**
 * Lays the words out right to left. Full lines are justified edge to edge like the printed
 * page; short lines are centered with a word space between words. A tap toggles the reader's
 * bars and a long press selects the pressed word's ayah, as on the reader's pages; each word is
 * drawn over its ayah's color, with the gaps between an ayah's words covered too.
 */
@Composable
private fun PageLine(
  index: Int,
  lines: MutableMap<Int, PlacedLine>,
  words: List<String>,
  ayahs: List<Int>,
  textStyle: TextStyle,
  centered: Boolean,
  colorFor: (Int) -> Color?,
  onTap: () -> Unit,
  onLongPress: (Int) -> Unit,
  modifier: Modifier
) {
  val placed = remember(index) { PlacedLine().also { lines[index] = it } }
  placed.ayahs = ayahs
  // read the latest values from the long-lived gesture detector instead of restarting it
  val currentOnTap by rememberUpdatedState(onTap)
  val currentOnLongPress by rememberUpdatedState(onLongPress)
  Layout(
    modifier = modifier
      .onGloballyPositioned { placed.coordinates = it }
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = { currentOnTap() },
          onLongPress = { offset ->
            placed.wordAt(offset.x)?.let { word -> placed.ayahs.getOrNull(word)?.let(currentOnLongPress) }
          }
        )
      }
      .drawBehind {
        for (word in placed.lefts.indices) {
          val key = ayahs.getOrNull(word) ?: continue
          val color = colorFor(key) ?: continue
          // words run right to left, so the next word is on the left; cover the gap up to it
          val left = if (ayahs.getOrNull(word + 1) == key) placed.rights[word + 1] else placed.lefts[word]
          drawRect(color = color, topLeft = Offset(left, 0f), size = Size(placed.rights[word] - left, size.height))
        }
      },
    content = {
      words.forEach { Text(text = it, style = textStyle, softWrap = false, maxLines = 1) }
    }
  ) { measurables, constraints ->
    val placeables = measurables.map { it.measure(Constraints()) }
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val gaps = placeables.size - 1
    val wordSpace = textStyle.fontSize.toPx() * 0.25f
    val natural = placeables.sumOf { it.width }
    val scale = minOf(1f, TYPICAL_LINE_EM * textStyle.fontSize.toPx() / natural)
    val total = natural * scale
    val justify = !centered && gaps > 0 && total >= width * JUSTIFY_THRESHOLD
    val gap = if (justify) (width - total) / gaps else wordSpace
    val used = total + gap * gaps
    placed.lefts = FloatArray(placeables.size)
    placed.rights = FloatArray(placeables.size)
    layout(width, height) {
      var right = if (justify) width.toFloat() else width - (width - used) / 2f
      placeables.forEachIndexed { index, placeable ->
        placed.rights[index] = right
        right -= placeable.width * scale
        placed.lefts[index] = right
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

/**
 * The reader's sura header frame (MushafSuraHeaderDrawer): a teal panel with a gold border and
 * gold diamonds around a cream pill that holds the sura's name.
 */
@Composable
private fun SuraHeader(name: String, style: TajweedPageStyle, rowHeight: Dp, modifier: Modifier) {
  Box(modifier.padding(vertical = rowHeight * 0.08f), contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .fillMaxSize()
        .drawBehind { drawSuraFrame(style) },
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = name,
        style = TextStyle(
          fontFamily = style.headerFont,
          color = TajweedInk,
          fontSize = with(LocalDensity.current) { (rowHeight * 0.42f).toSp() }
        ),
        maxLines = 1
      )
    }
  }
}

private fun DrawScope.drawSuraFrame(style: TajweedPageStyle) {
  val corner = size.height * 0.08f
  drawRoundRect(style.suraFrameFill, cornerRadius = CornerRadius(corner, corner))
  val goldWidth = 1.5.dp.toPx()
  val inset = size.height * 0.08f
  drawRoundRect(
    style.suraFrameGold,
    topLeft = Offset(inset, inset),
    size = Size(size.width - inset * 2, size.height - inset * 2),
    cornerRadius = CornerRadius(corner / 2, corner / 2),
    style = Stroke(goldWidth)
  )
  // the cream pill in the middle half
  val pillHeight = size.height * 0.72f
  val pill = Rect(
    Offset(size.width * 0.25f, (size.height - pillHeight) / 2),
    Size(size.width * 0.5f, pillHeight)
  )
  val pillRadius = CornerRadius(pillHeight / 2, pillHeight / 2)
  drawRoundRect(style.suraFramePill, pill.topLeft, pill.size, pillRadius)
  drawRoundRect(style.suraFrameGold, pill.topLeft, pill.size, pillRadius, style = Stroke(goldWidth))
  // a gold diamond in the middle of each side panel
  val diamond = size.height * 0.16f
  listOf((inset + pill.left) / 2, (pill.right + size.width - inset) / 2).forEach { cx ->
    val path = Path().apply {
      moveTo(cx, center.y - diamond)
      lineTo(cx + diamond, center.y)
      lineTo(cx, center.y + diamond)
      lineTo(cx - diamond, center.y)
      close()
    }
    drawPath(path, style.suraFrameGold, style = Stroke(goldWidth))
    val inner = Path().apply {
      moveTo(cx, center.y - diamond / 2)
      lineTo(cx + diamond / 2, center.y)
      lineTo(cx, center.y + diamond / 2)
      lineTo(cx - diamond / 2, center.y)
      close()
    }
    drawPath(inner, style.suraFrameGold)
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
