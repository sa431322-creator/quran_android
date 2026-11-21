package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.util.TypefaceManager

/**
 * Tasnim's ornamental page frame, drawn around a single mushaf page: a teal band of gilt
 * lozenges with star rosettes at the corners, a header of two gilt cartouches (juz on the right,
 * sura on the left) between the page's menu and search buttons, and the page number in a
 * cartouche under the page. It only draws around the page: the page itself (its script, its
 * lines and ayat) is laid out untouched inside [content].
 */
class TasnimPageFrame(private val context: Context) {

  private fun dp(value: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

  /** The band around the page. */
  val band = dp(13f)
  /** Header and footer rows inside the band. */
  val header = dp(56f)
  val footer = dp(40f)
  /** The round buttons at the header's ends. */
  val buttonSize = dp(42f).toInt()

  private val gap = dp(3f)

  /** The screen's cutouts at the top and bottom of the page (pixels): the frame keeps clear of them. */
  private var insetTop = 0f
  private var insetBottom = 0f

  /** Sets the cutouts' insets; true when they changed. */
  fun setInsets(top: Int, bottom: Int): Boolean {
    if (insetTop == top.toFloat() && insetBottom == bottom.toFloat()) return false
    insetTop = top.toFloat()
    insetBottom = bottom.toFloat()
    return true
  }

  private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
  private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
  private val path = Path()
  private val rect = RectF()

  private var night = false
  private var teal = 0
  private var tealLight = 0
  private var gold = 0
  private var cream = 0
  private var ink = 0

  init {
    setNightMode(false)
  }

  var sura: String = ""
  var juz: String = ""
  var page: String = ""

  private val titleTypeface: Typeface? by lazy {
    runCatching { TypefaceManager.getTafseerTypeface(context) }.getOrNull()
  }
  private val labelTypeface: Typeface? by lazy {
    runCatching { TypefaceManager.getHeaderFooterTypeface(context) }.getOrNull()
  }

  fun setNightMode(nightMode: Boolean) {
    night = nightMode
    fun c(id: Int) = ContextCompat.getColor(context, id)
    teal = c(if (nightMode) R.color.page_frame_band_night else R.color.page_frame_band)
    tealLight = c(if (nightMode) R.color.page_frame_band_light_night else R.color.page_frame_band_light)
    gold = c(if (nightMode) R.color.mushaf_gilt_night else R.color.page_frame_gold)
    cream = c(if (nightMode) R.color.page_frame_cartouche_night else R.color.page_frame_cartouche)
    ink = c(if (nightMode) R.color.mushaf_label_night else R.color.page_frame_ink)
  }

  /** Where the page goes inside a frame [width] by [height]. */
  fun content(width: Int, height: Int, out: RectF): RectF {
    out.set(band + gap * 2, insetTop + band + header, width - band - gap * 2, height - insetBottom - band - footer)
    return out
  }

  /** Where the menu (right) and search (left) buttons go: their top and their inset from the sides. */
  fun buttonTop(): Int = (insetTop + band + (header - buttonSize) / 2f).toInt()
  fun buttonInset(): Int = (band + gap * 2).toInt()

  fun draw(canvas: Canvas, width: Int, height: Int) {
    val w = width.toFloat()
    val h = height - insetTop - insetBottom
    canvas.save()
    canvas.translate(0f, insetTop)
    drawBand(canvas, w, h)
    drawHeader(canvas, w)
    drawFooter(canvas, w, h)
    canvas.restore()
  }

  // ---- the band ------------------------------------------------------------------------------

  private fun drawBand(canvas: Canvas, w: Float, h: Float) {
    val half = band / 2f
    // the band itself
    fill.color = teal
    stroke.color = teal
    stroke.strokeWidth = band
    rect.set(half, half, w - half, h - half)
    canvas.drawRect(rect, stroke)

    // gilt rules on both edges of the band, and a finer one inside it
    stroke.color = gold
    stroke.strokeWidth = dp(1.2f)
    rect.set(dp(1f), dp(1f), w - dp(1f), h - dp(1f))
    canvas.drawRect(rect, stroke)
    rect.set(band, band, w - band, h - band)
    canvas.drawRect(rect, stroke)
    stroke.strokeWidth = dp(0.7f)
    rect.set(band + gap, band + gap, w - band - gap, h - band - gap)
    canvas.drawRect(rect, stroke)

    // a row of gilt lozenges along each side, evenly spaced between the corner rosettes
    val step = band * 1.05f
    lozenges(canvas, band * 1.5f, w - band * 1.5f, half, horizontal = true, step = step)
    lozenges(canvas, band * 1.5f, w - band * 1.5f, h - half, horizontal = true, step = step)
    lozenges(canvas, band * 1.5f, h - band * 1.5f, half, horizontal = false, step = step)
    lozenges(canvas, band * 1.5f, h - band * 1.5f, w - half, horizontal = false, step = step)

    // the corner rosettes
    for ((x, y) in listOf(half to half, w - half to half, half to h - half, w - half to h - half)) {
      rosette(canvas, x, y, band * 0.95f)
    }
  }

  private fun lozenges(canvas: Canvas, from: Float, to: Float, across: Float, horizontal: Boolean, step: Float) {
    val count = ((to - from) / step).toInt().coerceAtLeast(1)
    val spacing = (to - from) / count
    val size = band * 0.32f
    for (i in 0..count) {
      val along = from + spacing * i
      val x = if (horizontal) along else across
      val y = if (horizontal) across else along
      if (i % 2 == 0) {
        diamond(canvas, x, y, size, gold)
        fill.color = teal
        canvas.drawCircle(x, y, size * 0.32f, fill)
      } else {
        fill.color = tealLight
        canvas.drawCircle(x, y, size * 0.42f, fill)
        fill.color = gold
        canvas.drawCircle(x, y, size * 0.2f, fill)
      }
    }
  }

  private fun diamond(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
    path.reset()
    path.moveTo(x, y - r)
    path.lineTo(x + r, y)
    path.lineTo(x, y + r)
    path.lineTo(x - r, y)
    path.close()
    fill.color = color
    canvas.drawPath(path, fill)
  }

  /** An eight-pointed gilt star with a teal heart: two squares, one turned 45°. */
  private fun rosette(canvas: Canvas, x: Float, y: Float, size: Float) {
    val half = size * 0.36f
    fill.color = gold
    canvas.drawRect(x - half, y - half, x + half, y + half, fill)
    canvas.save()
    canvas.rotate(45f, x, y)
    canvas.drawRect(x - half, y - half, x + half, y + half, fill)
    canvas.restore()
    fill.color = teal
    canvas.drawCircle(x, y, half * 0.55f, fill)
    fill.color = gold
    canvas.drawCircle(x, y, half * 0.22f, fill)
  }

  // ---- header and footer ---------------------------------------------------------------------

  private fun drawHeader(canvas: Canvas, w: Float) {

    val centerY = band + header / 2f
    // between the two buttons
    val left = buttonInset() + buttonSize + gap * 2
    val right = w - buttonInset() - buttonSize - gap * 2
    val middle = (left + right) / 2f
    val cartoucheH = header * 0.56f
    val joint = dp(26f)

    // the ornament strip the cartouches sit on
    ornamentStrip(canvas, left, right, centerY, header * 0.22f)
    // a small rosette between the two
    rosette(canvas, middle, centerY, joint * 0.7f)

    // the juz on the right, the sura on the left, as in the printed mushaf's header
    cartouche(canvas, middle + joint / 2f, right, centerY, cartoucheH, juz, labelTypeface, 0.36f)
    cartouche(canvas, left, middle - joint / 2f, centerY, cartoucheH, sura, titleTypeface, 0.42f)

    // a gilt rule under the header
    stroke.color = gold
    stroke.strokeWidth = dp(0.7f)
    val ruleY = band + header - gap
    canvas.drawLine(band + gap * 3, ruleY, w - band - gap * 3, ruleY, stroke)
  }

  private fun drawFooter(canvas: Canvas, w: Float, h: Float) {
    val centerY = h - band - footer / 2f
    val left = band + gap * 4
    val right = w - band - gap * 4
    stroke.color = gold
    stroke.strokeWidth = dp(0.7f)
    val ruleY = h - band - footer + gap
    canvas.drawLine(band + gap * 3, ruleY, w - band - gap * 3, ruleY, stroke)

    val badgeW = dp(64f)
    val badgeH = footer * 0.62f
    ornamentStrip(canvas, left, w / 2f - badgeW / 2f - gap, centerY, footer * 0.24f)
    ornamentStrip(canvas, w / 2f + badgeW / 2f + gap, right, centerY, footer * 0.24f)
    cartouche(canvas, w / 2f - badgeW / 2f, w / 2f + badgeW / 2f, centerY, badgeH, page, labelTypeface, 0.5f)
  }

  /** A thin teal strip of gilt lozenges between [left] and [right]. */
  private fun ornamentStrip(canvas: Canvas, left: Float, right: Float, centerY: Float, height: Float) {
    if (right - left < height) return
    fill.color = teal
    rect.set(left, centerY - height / 2f, right, centerY + height / 2f)
    canvas.drawRect(rect, fill)
    stroke.color = gold
    stroke.strokeWidth = dp(0.8f)
    canvas.drawRect(rect, stroke)
    val r = height * 0.3f
    val step = height * 1.1f
    val count = ((right - left - height) / step).toInt()
    if (count <= 0) return
    val spacing = (right - left - height) / count
    for (i in 0..count) diamond(canvas, left + height / 2f + spacing * i, centerY, r, gold)
  }

  /**
   * A cartouche: a cream plaque with pointed ends, outlined in gilt and teal, with [label]
   * centered on it (shrunk with an ellipsis if it is too long).
   */
  private fun cartouche(
    canvas: Canvas,
    left: Float,
    right: Float,
    centerY: Float,
    height: Float,
    label: String,
    typeface: Typeface?,
    textScale: Float
  ) {
    if (right - left < height) return
    val top = centerY - height / 2f
    val bottom = centerY + height / 2f
    val point = height * 0.42f
    path.reset()
    path.moveTo(left, centerY)
    path.lineTo(left + point, top)
    path.lineTo(right - point, top)
    path.lineTo(right, centerY)
    path.lineTo(right - point, bottom)
    path.lineTo(left + point, bottom)
    path.close()
    fill.color = cream
    canvas.drawPath(path, fill)
    stroke.color = teal
    stroke.strokeWidth = dp(2.2f)
    canvas.drawPath(path, stroke)
    stroke.color = gold
    stroke.strokeWidth = dp(1f)
    canvas.drawPath(path, stroke)

    if (label.isEmpty()) return
    text.typeface = typeface
    text.color = ink
    text.textSize = height * textScale * 1.6f
    val available = right - left - point * 2
    val shown = TextUtils.ellipsize(label, text, available, TextUtils.TruncateAt.END).toString()
    val fm = text.fontMetrics
    canvas.drawText(shown, (left + right) / 2f, centerY - (fm.ascent + fm.descent) / 2f, text)
  }
}
