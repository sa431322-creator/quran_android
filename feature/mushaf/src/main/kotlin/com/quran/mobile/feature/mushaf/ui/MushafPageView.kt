package com.quran.mobile.feature.mushaf.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.mobile.feature.mushaf.data.MUSHAF_LINES_PER_PAGE
import com.quran.mobile.feature.mushaf.data.MushafLine
import com.quran.mobile.feature.mushaf.data.MushafPage

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
/** the printed page's line spacing (53.8 px rows for 30.2 px text in the V2 print) */
private const val PRINT_ROW_EM = 1.78f
/** the page's labels (sura, juz, page number), relative to a text size fitted to the width */
private const val LABEL_EM = 0.5f
private const val LABEL_ROW_EM = 2.2f

private val MushafInk = Color(0xFF000000)

/**
 * How the host draws its pages, so tajweed pages look like the reader's: its paper, its page
 * header and footer, its sura header frame and its selection and recitation colors.
 */
data class MushafPageStyle(
  val paper: Color,
  val headerFont: FontFamily,
  val headerColor: Color,
  /** the page's accent (Tasnim teal): progress and buttons on the page */
  val accent: Color,
  /** the gilding: ayah markers, the sura frame, the page number's star */
  val gilt: Color,
  val selection: Color,
  val recitation: Color,
  /** the text's color; a monochrome page font (V2) takes it, a color one (V4 tajweed) keeps its own */
  val ink: Color = MushafInk,
  /** sura names, in the header frame and the page's label */
  val titleFont: FontFamily = headerFont,
  /**
   * Applied to the page font's glyphs only (not the decorations): a color page font (V4 tajweed)
   * draws its letters in black of its own, which [ink] cannot change, so a night page needs one.
   */
  val glyphFilter: ColorFilter? = null
)

/**
 * A night page's [MushafPageStyle.glyphFilter]: dark glyphs turn into [ink] and colors lighten by
 * as much as they are dark, keeping their hue, so tajweed colors stay told apart on a dark page.
 * White stays white and transparent stays transparent.
 */
fun nightGlyphFilter(ink: Color): ColorFilter {
  // each channel: c + ink_c * (1 - average of r, g, b), in 0..255 like Android's color matrix
  fun row(channel: Int, inkChannel: Float): FloatArray {
    val lift = inkChannel * ink.alpha
    return FloatArray(5) { column ->
      when (column) {
        channel -> 1f - lift / 3f
        in 0..2 -> -lift / 3f
        4 -> 255f * lift
        else -> 0f
      }
    }
  }
  return ColorFilter.colorMatrix(
    ColorMatrix(row(0, ink.red) + row(1, ink.green) + row(2, ink.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
  )
}

/** The page's header and footer text, as the reader writes them for its own pages. */
data class MushafPageLabels(val suraName: String, val juz: String, val pageNumber: String)

/** Where an ayah is on screen, in window coordinates: its first and its last word. */
data class MushafAyahBounds(val first: Rect, val last: Rect)

/**
 * Where a laid out page's words are, for a host that handles touches itself: the ayah under a
 * point and the bounds of an ayah, both in window coordinates.
 */
class MushafPageHits {
  internal val lines = HashMap<Int, PlacedLine>()

  /** How the page was sized, in pixels: text size, row height, text column width, scrolling. */
  var metrics: MushafPageMetrics? = null
    internal set

  /** The [com.quran.mobile.feature.mushaf.data.ayahKey] of the word at [window], if any. */
  fun ayahAt(window: Offset): Int? {
    for (line in lines.values) {
      val layout = line.coordinates?.takeIf { it.isAttached } ?: continue
      val local = layout.windowToLocal(window)
      if (local.y < 0f || local.y > layout.size.height) continue
      val word = line.wordAt(local.x) ?: continue
      return line.ayahs.getOrNull(word)
    }
    return null
  }

  fun boundsOf(key: Int): MushafAyahBounds? = boundsOf(key, lines)

  /** Each laid out text line by row: where its words are and how wide the line is (pixels). */
  fun lineExtents(): Map<Int, MushafLineExtent> = lines.mapValues { (_, line) ->
    MushafLineExtent(line.lefts.toList(), line.rights.toList(), line.coordinates?.size?.width ?: 0)
  }
}

/** A text line as laid out: each word's left and right edge, and the line's width. */
data class MushafLineExtent(val lefts: List<Float>, val rights: List<Float>, val width: Int)

/** How a page was laid out on this screen (pixels). */
data class MushafPageMetrics(
  val pageWidth: Float,
  val pageHeight: Float,
  val fontPx: Float,
  val rowPx: Float,
  val columnWidth: Float,
  val labelFontPx: Float,
  val scrolls: Boolean
)

/** A touch on a page, reported with its point in window coordinates. */
enum class MushafGesture { TAP, DOUBLE_TAP, LONG_PRESS }

@Composable
fun MushafPageView(
  page: MushafPage,
  pageFont: FontFamily,
  basmalaFont: FontFamily,
  style: MushafPageStyle,
  labels: MushafPageLabels,
  suraHeaderName: (Int) -> String,
  modifier: Modifier = Modifier,
  /** the [com.quran.mobile.feature.mushaf.data.ayahKey] selected, if any */
  selectedAyah: Int? = null,
  /** the ayah being recited, if any */
  recitedAyah: Int? = null,
  /** highlighted ayat ([com.quran.mobile.feature.mushaf.data.ayahKey]) and their colors */
  highlights: Map<Int, Color> = emptyMap(),
  onTap: () -> Unit = {},
  onAyahLongPress: (key: Int, bounds: MushafAyahBounds) -> Unit = { _, _ -> },
  /** filled with the page's word positions, for a host that hit-tests itself */
  hits: MushafPageHits? = null,
  /** when set, every touch on the page goes here instead of [onTap] and [onAyahLongPress] */
  onGesture: ((MushafGesture, Offset) -> Unit)? = null
) {
  // each text line's words and position, to find all of a long-pressed ayah's words
  val pageHits = hits ?: remember { MushafPageHits() }
  val lines = remember(page.page, pageHits) { pageHits.lines.also { it.clear() } }
  val ayahBounds: (Int) -> MushafAyahBounds? = { key -> boundsOf(key, lines) }
  val handledAbove = onGesture != null
  val currentOnGesture by rememberUpdatedState(onGesture)
  var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
  fun report(gesture: MushafGesture, local: Offset) {
    val coordinates = root?.takeIf { it.isAttached } ?: return
    currentOnGesture?.invoke(gesture, coordinates.localToWindow(local))
  }

  Column(
    modifier
      .then(
        if (handledAbove) {
          Modifier
            .onGloballyPositioned { root = it }
            .pointerInput(Unit) {
              detectTapGestures(
                onTap = { report(MushafGesture.TAP, it) },
                onDoubleTap = { report(MushafGesture.DOUBLE_TAP, it) },
                onLongPress = { report(MushafGesture.LONG_PRESS, it) }
              )
            }
        } else {
          Modifier
        }
      )
      .fillMaxSize()
      .background(style.paper)
      .padding(horizontal = 14.dp, vertical = 4.dp)
  ) {
    // Everything on the page is sized from the page, never from the system font size, so the
    // layout (15 lines, the same words on each) only scales: from the width, and in portrait no
    // larger than lets the 15 rows fit at the print's line spacing; landscape scrolls instead.
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val density = LocalDensity.current
      // a host may measure with an open-ended size (a page is measured that way while a tablet
      // rotates): a size far beyond the window is taken as the window, never as the page
      val window = LocalWindowInfo.current.containerSize
      val widthPx = sane(with(density) { maxWidth.toPx() }, window.width)
      val heightPx = sane(with(density) { maxHeight.toPx() }, window.height)
      val widthFontPx = widthPx / LINE_EM
      val labelFontPx = widthFontPx * LABEL_EM
      val labelRowPx = labelFontPx * LABEL_ROW_EM
      val textHeightPx = (heightPx - 2 * labelRowPx).coerceAtLeast(1f)
      val portrait = heightPx >= widthPx
      val fontPx = if (portrait) {
        minOf(widthFontPx, textHeightPx / (MUSHAF_LINES_PER_PAGE * PRINT_ROW_EM))
      } else {
        widthFontPx
      }
      val fitRow = textHeightPx / MUSHAF_LINES_PER_PAGE
      // landscape and short screens scroll instead of squeezing the lines together
      val scrolls = fitRow < fontPx * MIN_ROW_EM
      val rowPx = if (scrolls) fontPx * SCROLL_ROW_EM else fitRow
      val rowHeight = with(density) { rowPx.toDp() }
      val columnWidthPx = minOf(widthPx, fontPx * LINE_EM)
      val textStyle = TextStyle(
        fontFamily = pageFont,
        fontSize = with(density) { fontPx.toSp() },
        color = style.ink
      )
      val labelStyle = TextStyle(
        fontFamily = style.headerFont,
        color = style.headerColor,
        fontSize = with(density) { labelFontPx.toSp() }
      )
      SideEffect {
        pageHits.metrics = MushafPageMetrics(widthPx, heightPx, fontPx, rowPx, columnWidthPx, labelFontPx, scrolls)
      }

      Column(Modifier.fillMaxSize()) {
      PageLabelRow(
        start = labels.suraName, end = labels.juz, style = labelStyle, height = labelRowPx,
        startStyle = labelStyle.copy(fontFamily = style.titleFont, fontSize = labelStyle.fontSize * 1.3f)
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .width(with(density) { columnWidthPx.toDp() })
          .align(Alignment.CenterHorizontally)
          .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight()),
        verticalArrangement = if (page.isOpening) Arrangement.Center else Arrangement.Top
      ) {
        page.lines.forEachIndexed { index, line ->
          val rowModifier = Modifier.fillMaxWidth().height(rowHeight)
          when (line) {
            is MushafLine.Text -> PageLine(
              index = index,
              lines = lines,
              words = line.words,
              ayahs = line.ayahs,
              ends = line.ends,
              textStyle = textStyle,
              gilt = style.gilt,
              glyphFilter = style.glyphFilter,
              markerFont = style.headerFont, // plain digits: the Uthmani face draws its own ayah ornament around them
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
              handledAbove = handledAbove,
              modifier = rowModifier
            )
            is MushafLine.SuraHeader -> SuraHeader(
              name = suraHeaderName(line.sura),
              style = style,
              rowHeight = rowHeight,
              modifier = rowModifier.tapToToggle(onTap, handledAbove)
            )
            MushafLine.Basmala -> Basmala(basmalaFont, fontPx, rowModifier.tapToToggle(onTap, handledAbove), style.ink)
            MushafLine.Blank -> Box(rowModifier.tapToToggle(onTap, handledAbove))
          }
        }
      }
      PageNumber(labels.pageNumber, labelStyle.copy(color = style.ink), style.gilt, labelRowPx)
      }
    }
  }
}

private fun Modifier.tapToToggle(onTap: () -> Unit, handledAbove: Boolean) =
  if (handledAbove) this else pointerInput(onTap) { detectTapGestures { onTap() } }

/** [size], or the window's [windowSize] when [size] is more than four windows (an open-ended measure). */
private fun sane(size: Float, windowSize: Int): Float =
  if (windowSize > 0 && (size.isInfinite() || size > windowSize * 4f)) windowSize.toFloat() else size

/** The page's header (sura and juz) or footer (page number), like the reader's page overlay. */
@Composable
private fun PageLabelRow(
  style: TextStyle,
  height: Float,
  start: String = "",
  end: String = "",
  center: String = "",
  startStyle: TextStyle = style
) {
  val rowHeight = with(LocalDensity.current) { height.toDp() }
  Row(
    Modifier.fillMaxWidth().height(rowHeight).testTag(LABEL_ROW_TAG),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (center.isNotEmpty()) {
      Text(center, style = style, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
      // right to left: the sura on the right, the juz on the left, as on the reader's pages
      Text(start, style = startStyle, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
      Text(end, style = style, maxLines = 1, softWrap = false)
    }
  }
}

/** The page number in a gilt eight-pointed star, centered under the page. */
@Composable
private fun PageNumber(number: String, style: TextStyle, gilt: Color, height: Float) {
  val rowHeight = with(LocalDensity.current) { height.toDp() }
  Box(Modifier.fillMaxWidth().height(rowHeight).testTag(LABEL_ROW_TAG), contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .height(rowHeight * 0.95f)
        .aspectRatio(1f)
        .drawBehind { drawStar(gilt, center, size.minDimension * 0.5f, size.minDimension * 0.04f, filled = false) },
      contentAlignment = Alignment.Center
    ) {
      Text(number, style = style, maxLines = 1, softWrap = false)
    }
  }
}

/**
 * An eight-pointed star: two squares, one turned 45°, of half-side [radius] * 0.55 around [center]
 * (the same proportions as the ayah markers).
 */
internal fun DrawScope.drawStar(color: Color, center: Offset, radius: Float, strokeWidth: Float, filled: Boolean) {
  val half = radius * 0.55f
  val square = Rect(center.x - half, center.y - half, center.x + half, center.y + half)
  val paint = if (filled) Fill else Stroke(strokeWidth)
  drawRect(color, square.topLeft, square.size, style = paint)
  rotate(45f, center) { drawRect(color, square.topLeft, square.size, style = paint) }
}

/** Test tag of the page's label rows. */
const val LABEL_ROW_TAG = "mushafPageLabels"

/** One text line's words, where they were placed, and where the line is in the window. */
internal class PlacedLine {
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

private fun boundsOf(key: Int, lines: Map<Int, PlacedLine>): MushafAyahBounds? {
  val words = lines.entries.sortedBy { it.key }.flatMap { (_, line) ->
    line.ayahs.indices.filter { line.ayahs[it] == key }.map { line to it }
  }
  val first = words.firstOrNull()?.let { (line, word) -> line.wordRect(word) } ?: return null
  val last = words.last().let { (line, word) -> line.wordRect(word) } ?: return null
  return MushafAyahBounds(first, last)
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
  ends: List<Boolean>,
  textStyle: TextStyle,
  gilt: Color,
  markerFont: FontFamily,
  glyphFilter: ColorFilter?,
  centered: Boolean,
  colorFor: (Int) -> Color?,
  onTap: () -> Unit,
  onLongPress: (Int) -> Unit,
  handledAbove: Boolean,
  modifier: Modifier
) {
  val placed = remember(index) { PlacedLine() }
  // registered on every composition: the page view may be given another page, which clears the map
  lines[index] = placed
  placed.ayahs = ayahs
  // read the latest values from the long-lived gesture detector instead of restarting it
  val currentOnTap by rememberUpdatedState(onTap)
  val currentOnLongPress by rememberUpdatedState(onLongPress)
  val measurer = rememberTextMeasurer()
  Layout(
    modifier = modifier
      .onGloballyPositioned { placed.coordinates = it }
      .then(
        if (handledAbove) {
          Modifier
        } else {
          Modifier.pointerInput(Unit) {
            detectTapGestures(
              onTap = { currentOnTap() },
              onLongPress = { offset ->
                placed.wordAt(offset.x)?.let { word -> placed.ayahs.getOrNull(word)?.let(currentOnLongPress) }
              }
            )
          }
        }
      )
      .drawBehind {
        for (word in placed.lefts.indices) {
          val key = ayahs.getOrNull(word) ?: continue
          val color = colorFor(key) ?: continue
          // words run right to left, so the next word is on the left; cover the gap up to it
          val left = if (ayahs.getOrNull(word + 1) == key) placed.rights[word + 1] else placed.lefts[word]
          drawRect(color = color, topLeft = Offset(left, 0f), size = Size(placed.rights[word] - left, size.height))
        }
      }
      .drawWithContent {
        drawContentFiltered(glyphFilter)
        for (word in placed.lefts.indices) {
          if (ends.getOrNull(word) != true) continue
          val number = ayahNumber(ayahs[word])
          val diameter = minOf((placed.rights[word] - placed.lefts[word]) * 1.35f, size.height * 0.8f)
          val center = Offset((placed.lefts[word] + placed.rights[word]) / 2f, size.height / 2f)
          drawAyahMarker(measurer, number, center, diameter, gilt, textStyle.color, markerFont)
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
        if (ends.getOrNull(index) == true) {
          // an ayah's end marker keeps its glyph's place in the line, but the page draws its own
          // gilt marker there instead of the font's (which has colors of its own)
        } else if (scale < 1f) {
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
 * The sura header frame (Tasnim's gilt design): a double gilt rule around a gilt-outlined pill
 * with the sura's name, between two filled eight-pointed stars.
 */
@Composable
internal fun SuraHeader(name: String, style: MushafPageStyle, rowHeight: Dp, modifier: Modifier) {
  Box(modifier.padding(vertical = rowHeight * 0.09f), contentAlignment = Alignment.Center) {
    Row(
      Modifier
        .fillMaxSize()
        .drawBehind { drawSuraFrame(style.gilt) },
      horizontalArrangement = Arrangement.spacedBy(rowHeight * 0.22f, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically
    ) {
      FrameStar(style.gilt, rowHeight * 0.26f)
      Box(
        Modifier
          .height(rowHeight * 0.56f)
          .drawBehind {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(style.gilt, cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
          }
          .padding(horizontal = rowHeight * 0.45f),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = name,
          style = TextStyle(
            fontFamily = style.titleFont,
            color = style.ink,
            fontSize = with(LocalDensity.current) { (rowHeight * 0.36f).toSp() }
          ),
          maxLines = 1,
          softWrap = false
        )
      }
      FrameStar(style.gilt, rowHeight * 0.26f)
    }
  }
}

@Composable
private fun FrameStar(gilt: Color, size: Dp) {
  Box(Modifier.height(size).aspectRatio(1f).drawBehind { drawStar(gilt, center, this.size.minDimension / 2f, 0f, filled = true) })
}

/** The header's double gilt rule: an outer line and a finer one just inside it. */
private fun DrawScope.drawSuraFrame(gilt: Color) {
  val outer = 1.5.dp.toPx()
  val inner = 1.dp.toPx()
  val inset = 5.dp.toPx()
  val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
  drawRoundRect(gilt, Offset(outer / 2, outer / 2), Size(size.width - outer, size.height - outer), corner, Stroke(outer))
  drawRoundRect(
    gilt, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
    CornerRadius(2.dp.toPx(), 2.dp.toPx()), Stroke(inner)
  )
}

/**
 * The ayah's end marker (Tasnim's gilt design): a gilt eight-pointed star and ring around the
 * ayah's number, in Arabic-Indic digits.
 */
internal fun DrawScope.drawAyahMarker(
  measurer: TextMeasurer,
  number: Int,
  center: Offset,
  diameter: Float,
  gilt: Color,
  ink: Color,
  font: FontFamily
) {
  val radius = diameter / 2f
  drawStar(gilt, center, radius, diameter * 0.05f, filled = false)
  drawCircle(gilt, radius * 0.62f, center, style = Stroke(diameter * 0.03f))
  val digits = number.toString().map { '٠' + (it - '0') }.joinToString("")
  val layout = measurer.measure(
    digits,
    TextStyle(fontFamily = font, color = ink, fontSize = (diameter * if (number > 99) 0.36f else 0.5f).toSp()),
    softWrap = false,
    maxLines = 1
  )
  drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

private fun ayahNumber(key: Int): Int = com.quran.mobile.feature.mushaf.data.ayahKeyAyah(key)

@Composable
internal fun Basmala(font: FontFamily, fontPx: Float, modifier: Modifier, ink: Color = MushafInk) {
  val fontSize = with(LocalDensity.current) { (fontPx * 0.95f).toSp() }
  Box(modifier, contentAlignment = Alignment.Center) {
    Text(
      text = BASMALA,
      fontFamily = font,
      fontSize = fontSize,
      color = ink,
      textAlign = TextAlign.Center,
      maxLines = 1,
      softWrap = false
    )
  }
}

private const val BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
