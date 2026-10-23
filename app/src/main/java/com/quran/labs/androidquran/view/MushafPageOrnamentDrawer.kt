package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.util.TypefaceManager

/**
 * Draws the Tasnim page ornament in the empty space above and below the page image on tall
 * screens: the sura name in a gold-outlined pill with two eight-pointed stars, the juz' line and a
 * gold rule above the page; a gold rule and the page number in an eight-pointed star below it.
 *
 * When there is less room it falls back to a single line above and below, and when there is
 * hardly any room it draws nothing, so the caller keeps the original page info text. The page
 * image itself is never drawn over.
 */
class MushafPageOrnamentDrawer(context: Context) {
  private val density = context.resources.displayMetrics.density
  private val scaledDensity = density * context.resources.configuration.fontScale

  private val dayGold = ContextCompat.getColor(context, R.color.mushaf_ornament_gold)
  private val nightGold = ContextCompat.getColor(context, R.color.mushaf_ornament_gold_night)
  private val dayText = ContextCompat.getColor(context, R.color.mushaf_ornament_text)
  private val dayMuted = ContextCompat.getColor(context, R.color.mushaf_ornament_text_muted)

  private val path = Path()
  private val rect = RectF()

  private val suraPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    typeface = TypefaceManager.getTafseerTypeface(context)
    textAlign = Paint.Align.CENTER
  }

  private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    typeface = ResourcesCompat.getFont(context, com.quran.mobile.common.ui.core.R.font.tasnim_ui)
    textAlign = Paint.Align.CENTER
  }

  private val goldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
  }

  private val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }

  /**
   * Draws the ornament between [topLimit] and the top of [image], and between the bottom of
   * [image] and [bottomLimit]. Returns false (drawing nothing) when there is too little room, so
   * the caller can draw its usual page info instead.
   */
  fun draw(
    canvas: Canvas,
    width: Int,
    image: RectF,
    topLimit: Float,
    bottomLimit: Float,
    suraText: String,
    infoText: String,
    pageText: String,
    isNightMode: Boolean,
    nightTextColor: Int
  ): Boolean {
    val topRoom = image.top - topLimit
    val bottomRoom = bottomLimit - image.bottom
    // the heights follow the text sizes, so large system font sizes fall back to a smaller layout
    val margin = dp(EDGE_MARGIN)
    val isFull = topRoom >= fullHeaderHeight() + margin && bottomRoom >= fullFooterHeight() + margin
    if (!isFull && (topRoom < compactLineHeight() + margin || bottomRoom < compactLineHeight() + margin)) {
      return false
    }

    val gold = if (isNightMode) nightGold else dayGold
    val text = if (isNightMode) nightTextColor else dayText
    val muted = if (isNightMode) withAlpha(nightTextColor, 0.75f) else dayMuted
    goldStroke.color = gold
    goldFill.color = gold

    val cx = width / 2f
    if (isFull) {
      drawFullHeader(canvas, cx, image.top, width, suraText, infoText, text, muted)
      drawFullFooter(canvas, cx, image.bottom, width, pageText, text)
    } else {
      drawCompactHeader(canvas, cx, (topLimit + image.top) / 2, width, suraText, infoText, text, muted)
      drawCompactFooter(canvas, cx, (image.bottom + bottomLimit) / 2, pageText, text)
    }
    return true
  }

  private fun fullHeaderHeight(): Float {
    infoPaint.textSize = sp(INFO_TEXT_SIZE)
    return dp(GAP_TO_PAGE + 8f + 8f) + textHeight(infoPaint) + pillHeight()
  }

  private fun fullFooterHeight(): Float = dp(GAP_TO_PAGE + 8f) + dp(PAGE_STAR_SIZE) * SQRT2

  private fun compactLineHeight(): Float {
    suraPaint.textSize = sp(COMPACT_SURA_TEXT_SIZE)
    return textHeight(suraPaint)
  }

  /** The pill grows with the sura name when the system font size is large. */
  private fun pillHeight(): Float {
    suraPaint.textSize = sp(SURA_TEXT_SIZE)
    return maxOf(dp(PILL_HEIGHT), textHeight(suraPaint) + dp(10f))
  }

  private fun textHeight(paint: Paint): Float = paint.descent() - paint.ascent()

  private fun drawFullHeader(
    canvas: Canvas, cx: Float, imageTop: Float, width: Int,
    suraText: String, infoText: String, text: Int, muted: Int
  ) {
    // gold rule just above the page
    val ruleY = imageTop - dp(GAP_TO_PAGE)
    drawRule(canvas, cx, ruleY, ruleHalfWidth(width))

    // juz' / hizb line above the rule
    infoPaint.textSize = sp(INFO_TEXT_SIZE)
    infoPaint.color = muted
    val infoBaseline = ruleY - dp(8f) - infoPaint.descent()
    if (infoText.isNotEmpty()) {
      drawFittedText(canvas, infoText, cx, infoBaseline, width * 0.9f, infoPaint)
    }

    // sura name pill above that
    val pillHeight = pillHeight()
    suraPaint.color = text
    val pillBottom = infoBaseline + infoPaint.ascent() - dp(8f)
    val pillTop = pillBottom - pillHeight
    val starSize = dp(PILL_STAR_SIZE)
    val padding = dp(18f)
    val starGap = dp(9f)
    val maxTextWidth = maxOf(0f, width * 0.8f - 2 * (padding + starSize + starGap))
    val nameWidth = minOf(suraPaint.measureText(suraText), maxTextWidth)
    val halfWidth = nameWidth / 2 + starGap + starSize + padding
    rect.set(cx - halfWidth, pillTop, cx + halfWidth, pillBottom)
    goldStroke.strokeWidth = dp(1.2f)
    canvas.drawRoundRect(rect, pillHeight / 2, pillHeight / 2, goldStroke)

    val centerY = rect.centerY()
    drawFittedText(canvas, suraText, cx, centeredBaseline(suraPaint, centerY), maxTextWidth, suraPaint)
    val starOffset = nameWidth / 2 + starGap + starSize / 2
    drawStar(canvas, cx - starOffset, centerY, starSize / 2, filled = true)
    drawStar(canvas, cx + starOffset, centerY, starSize / 2, filled = true)
  }

  private fun drawFullFooter(
    canvas: Canvas, cx: Float, imageBottom: Float, width: Int, pageText: String, text: Int
  ) {
    val ruleY = imageBottom + dp(GAP_TO_PAGE)
    drawRule(canvas, cx, ruleY, ruleHalfWidth(width))

    val radius = dp(PAGE_STAR_SIZE) / 2
    val centerY = ruleY + dp(8f) + radius * SQRT2
    goldStroke.strokeWidth = dp(1.1f)
    drawStar(canvas, cx, centerY, radius, filled = false)

    infoPaint.textSize = sp(PAGE_TEXT_SIZE)
    infoPaint.color = text
    drawFittedText(canvas, pageText, cx, centeredBaseline(infoPaint, centerY), radius * 2.2f, infoPaint)
  }

  private fun drawCompactHeader(
    canvas: Canvas, cx: Float, centerY: Float, width: Int,
    suraText: String, infoText: String, text: Int, muted: Int
  ) {
    suraPaint.textSize = sp(COMPACT_SURA_TEXT_SIZE)
    suraPaint.color = text
    infoPaint.textSize = sp(INFO_TEXT_SIZE)
    infoPaint.color = muted

    val starSize = dp(COMPACT_STAR_SIZE)
    val gap = dp(8f)
    val dotSize = dp(COMPACT_DOT_SIZE)
    val available = maxOf(0f, width * 0.85f - 2 * (starSize + gap))
    val nameWidth = minOf(suraPaint.measureText(suraText), available)
    val separatorWidth = if (infoText.isEmpty()) 0f else dotSize + 2 * gap
    val infoWidth = if (infoText.isEmpty()) 0f else
      minOf(infoPaint.measureText(infoText), maxOf(0f, available - nameWidth - separatorWidth))
    val total = nameWidth + if (infoWidth > 0) separatorWidth + infoWidth else 0f

    // star, sura name, dot, juz' info, star, in the reading direction of the sura name
    val rtl = isRtl(suraText)
    val start = if (rtl) cx + total / 2 else cx - total / 2
    val dir = if (rtl) -1 else 1
    drawStar(canvas, start - dir * (gap + starSize / 2), centerY, starSize / 2, filled = true)
    drawStar(canvas, start + dir * (total + gap + starSize / 2), centerY, starSize / 2, filled = true)
    drawFittedText(canvas, suraText, start + dir * nameWidth / 2,
      centeredBaseline(suraPaint, centerY), nameWidth, suraPaint)
    if (infoWidth > 0) {
      drawDiamond(canvas, start + dir * (nameWidth + gap + dotSize / 2), centerY, dotSize / 2)
      drawFittedText(canvas, infoText, start + dir * (nameWidth + separatorWidth + infoWidth / 2),
        centeredBaseline(infoPaint, centerY), infoWidth, infoPaint)
    }
  }

  private fun isRtl(text: String): Boolean {
    for (c in text) {
      when (Character.getDirectionality(c)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
        Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
      }
    }
    return true
  }

  private fun drawCompactFooter(
    canvas: Canvas, cx: Float, centerY: Float, pageText: String, text: Int
  ) {
    infoPaint.textSize = sp(PAGE_TEXT_SIZE)
    infoPaint.color = text
    canvas.drawText(pageText, cx, centeredBaseline(infoPaint, centerY), infoPaint)

    val offset = infoPaint.measureText(pageText) / 2 + dp(9f)
    val size = dp(COMPACT_DIAMOND_SIZE) / 2
    drawDiamond(canvas, cx - offset - size, centerY, size)
    drawDiamond(canvas, cx + offset + size, centerY, size)
  }

  /** A thin gold line with a small gold diamond in its middle. */
  private fun drawRule(canvas: Canvas, cx: Float, y: Float, halfWidth: Float) {
    val diamond = dp(RULE_DIAMOND_SIZE) / 2
    val gap = dp(4f)
    goldStroke.strokeWidth = dp(0.8f)
    canvas.drawLine(cx - halfWidth, y, cx - diamond - gap, y, goldStroke)
    canvas.drawLine(cx + diamond + gap, y, cx + halfWidth, y, goldStroke)
    drawDiamond(canvas, cx, y, diamond)
  }

  private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, half: Float) {
    path.reset()
    path.moveTo(cx, cy - half)
    path.lineTo(cx + half, cy)
    path.lineTo(cx, cy + half)
    path.lineTo(cx - half, cy)
    path.close()
    canvas.drawPath(path, goldFill)
  }

  /** An eight-pointed star made of two overlapping squares of half side [half]. */
  private fun drawStar(canvas: Canvas, cx: Float, cy: Float, half: Float, filled: Boolean) {
    path.reset()
    path.addRect(cx - half, cy - half, cx + half, cy + half, Path.Direction.CW)
    val diagonal = half * SQRT2
    path.moveTo(cx, cy - diagonal)
    path.lineTo(cx + diagonal, cy)
    path.lineTo(cx, cy + diagonal)
    path.lineTo(cx - diagonal, cy)
    path.close()
    canvas.drawPath(path, if (filled) goldFill else goldStroke)
  }

  /** Draws [text] centered on [x], scaled down horizontally if it is wider than [maxWidth]. */
  private fun drawFittedText(
    canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, paint: Paint
  ) {
    val textWidth = paint.measureText(text)
    if (textWidth <= maxWidth || textWidth <= 0f) {
      canvas.drawText(text, x, baseline, paint)
    } else {
      val scale = maxWidth / textWidth
      canvas.save()
      canvas.scale(scale, scale, x, baseline)
      canvas.drawText(text, x, baseline, paint)
      canvas.restore()
    }
  }

  private fun centeredBaseline(paint: Paint, centerY: Float): Float =
    centerY - (paint.ascent() + paint.descent()) / 2

  private fun ruleHalfWidth(width: Int): Float = minOf(width * 0.36f, dp(150f))

  private fun withAlpha(color: Int, alpha: Float): Int =
    Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))

  private fun dp(value: Float): Float = value * density

  private fun sp(value: Float): Float = value * scaledDensity

  private companion object {
    const val SQRT2 = 1.4142135f

    // dp kept free between the ornament and the screen edge
    const val EDGE_MARGIN = 10f

    const val GAP_TO_PAGE = 10f
    const val PILL_HEIGHT = 36f
    const val PILL_STAR_SIZE = 9f
    const val PAGE_STAR_SIZE = 24f
    const val RULE_DIAMOND_SIZE = 7f
    const val COMPACT_STAR_SIZE = 8f
    const val COMPACT_DIAMOND_SIZE = 6f
    const val COMPACT_DOT_SIZE = 4f

    // sp
    const val SURA_TEXT_SIZE = 20f
    const val COMPACT_SURA_TEXT_SIZE = 17f
    const val INFO_TEXT_SIZE = 12.5f
    const val PAGE_TEXT_SIZE = 13f
  }
}
