package com.quran.labs.androidquran.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.util.TypefaceManager

/**
 * Draws a share card: background, a gold Quranic frame, the sura name, the ayahs (in the app's
 * Uthmani text font, read only: the mushaf fonts are not changed), an optional tafsir panel, an
 * info line (sura, ayah, page, juz) and the Tasnim logo with the download link.
 *
 * The same renderer makes the composer's preview, the shared image and every frame of a video.
 */
class ShareCardRenderer(context: Context) {
  private val appContext = context.applicationContext
  private val arabicTypeface: Typeface = TypefaceManager.getUthmaniTypeface(appContext)
  private val uiTypeface: Typeface =
    ResourcesCompat.getFont(appContext, com.quran.mobile.common.ui.core.R.font.tasnim_ui)
      ?: Typeface.DEFAULT
  private val uiMediumTypeface: Typeface =
    ResourcesCompat.getFont(appContext, com.quran.mobile.common.ui.core.R.font.tasnim_ui_medium)
      ?: Typeface.DEFAULT_BOLD
  private val logo: Bitmap? by lazy {
    BitmapFactory.decodeResource(appContext.resources, R.drawable.tasnim_logo)
  }

  /** Renders at the card's full size, or scaled down by [scale] for previews. */
  fun render(content: ShareCardContent, options: ShareCardOptions, scale: Float = 1f): Bitmap {
    val width = (options.aspect.width * scale).toInt()
    val height = (options.aspect.height * scale).toInt()
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    draw(canvas, content, options)
    return bitmap
  }

  /** A small swatch of background [index] for the picker. */
  fun thumbnail(index: Int, size: Int): Bitmap {
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    ShareBackgrounds.draw(canvas, index, size, size)
    val palette = ShareBackgrounds.palette(index)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = size / 40f
      color = palette.frame
    }
    val inset = size * 0.14f
    canvas.drawRect(inset, inset, size - inset, size - inset, paint)
    paint.style = Paint.Style.FILL
    canvas.drawPath(ShareBackgrounds.star(size / 2f, size / 2f, size * 0.14f, size * 0.07f), paint)
    return bitmap
  }

  private fun draw(canvas: Canvas, content: ShareCardContent, options: ShareCardOptions) {
    val w = options.aspect.width.toFloat()
    val h = options.aspect.height.toFloat()
    val palette = ShareBackgrounds.palette(options.background)
    ShareBackgrounds.draw(canvas, options.background, options.aspect.width, options.aspect.height)

    // the frame: a heavy outer line, a fine inner line and a star on each corner
    val margin = 44f
    val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      color = palette.frame
      strokeWidth = 5f
    }
    val outer = RectF(margin, margin, w - margin, h - margin)
    canvas.drawRect(outer, framePaint)
    framePaint.strokeWidth = 2f
    val inner = RectF(outer.left + 14f, outer.top + 14f, outer.right - 14f, outer.bottom - 14f)
    canvas.drawRect(inner, framePaint)
    val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.frame }
    listOf(
      outer.left to outer.top, outer.right to outer.top,
      outer.left to outer.bottom, outer.right to outer.bottom
    ).forEach { (x, y) ->
      canvas.drawPath(ShareBackgrounds.star(x, y, 26f, 12f), starPaint)
    }

    val contentLeft = inner.left + 44f
    val contentRight = inner.right - 44f
    val contentWidth = (contentRight - contentLeft).toInt()
    var top = inner.top + 36f

    // header: the sura name in a gold-edged cartouche
    if (options.showSuraName) {
      val titlePaint = textPaint(uiMediumTypeface, 46f, palette.frame)
      val titleWidth = titlePaint.measureText(content.suraTitle)
      val boxWidth = titleWidth + 120f
      val boxHeight = 84f
      val box = RectF(w / 2 - boxWidth / 2, top, w / 2 + boxWidth / 2, top + boxHeight)
      val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.badge }
      canvas.drawRoundRect(box, boxHeight / 2, boxHeight / 2, fill)
      framePaint.strokeWidth = 3f
      canvas.drawRoundRect(box, boxHeight / 2, boxHeight / 2, framePaint)
      canvas.drawPath(ShareBackgrounds.star(box.left + 34f, box.centerY(), 12f, 6f), starPaint)
      canvas.drawPath(ShareBackgrounds.star(box.right - 34f, box.centerY(), 12f, 6f), starPaint)
      val baseline = box.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2
      canvas.drawText(content.suraTitle, w / 2 - titleWidth / 2, baseline, titlePaint)
      top = box.bottom + 40f
    }

    // footer, from the bottom up: logo and link, then the info line
    var bottom = inner.bottom - 28f
    val linkPaint = textPaint(uiTypeface, 26f, palette.muted)
    val logoSize = 60f
    val maxLinkWidth = inner.width() - 2 * 28f - logoSize - 16f
    while (linkPaint.textSize > 14f && linkPaint.measureText(content.downloadLink) > maxLinkWidth) {
      linkPaint.textSize -= 1f
    }
    val linkText = TextUtils.ellipsize(content.downloadLink, linkPaint, maxLinkWidth, TextUtils.TruncateAt.END).toString()
    val linkWidth = linkPaint.measureText(linkText)
    val rowWidth = logoSize + 16f + linkWidth
    val rowLeft = w / 2 - rowWidth / 2
    val rowTop = bottom - logoSize
    logo?.let {
      canvas.drawBitmap(it, null, RectF(rowLeft, rowTop, rowLeft + logoSize, bottom), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    val linkBaseline = rowTop + logoSize / 2 - (linkPaint.descent() + linkPaint.ascent()) / 2
    canvas.drawText(linkText, rowLeft + logoSize + 16f, linkBaseline, linkPaint)
    bottom = rowTop - 18f

    val info = listOfNotNull(
      content.rangeLabel,
      content.pageLabel.takeIf { options.showPage },
      content.juzLabel.takeIf { options.showJuz }
    ).joinToString("  ·  ")
    val infoPaint = textPaint(uiMediumTypeface, 32f, palette.text)
    val infoLayout = layout(info, infoPaint, contentWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 2)
    bottom -= infoLayout.height
    drawLayout(canvas, infoLayout, contentLeft, bottom)
    // a thin gold rule between the body and the info line
    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.frame; strokeWidth = 2f }
    bottom -= 22f
    canvas.drawLine(w / 2 - 120f, bottom, w / 2 + 120f, bottom, rulePaint)
    canvas.drawPath(ShareBackgrounds.star(w / 2, bottom, 10f, 5f), starPaint)
    bottom -= 30f

    // body: the ayahs, and the tafsir panel under them
    val available = bottom - top
    val arabic = arabicText(content, options)
    val tafsir = if (options.showTafsir) tafsirText(content, options) else null
    val panelPadding = 28f
    val tafsirWidth = (contentWidth - 2 * panelPadding).toInt()

    // taller cards have room for bigger text; long passages shrink from here until they fit
    var arabicSize = (80f * h / 1080f).coerceIn(80f, 110f)
    var tafsirSize = 34f
    var arabicLayout: StaticLayout
    var tafsirLayout: StaticLayout? = null
    var sourceLayout: StaticLayout? = null
    while (true) {
      arabicLayout = layout(
        arabic, textPaint(arabicTypeface, arabicSize, palette.text), contentWidth,
        Layout.Alignment.ALIGN_CENTER, spacing = 1.45f
      )
      if (tafsir != null) {
        tafsirLayout = layout(
          tafsir, textPaint(uiTypeface, tafsirSize, palette.text), tafsirWidth,
          Layout.Alignment.ALIGN_NORMAL, spacing = 1.3f
        )
        sourceLayout = content.tafsirSourceName?.let {
          layout(it, textPaint(uiTypeface, 24f, palette.muted), tafsirWidth, Layout.Alignment.ALIGN_NORMAL)
        }
      }
      val total = arabicLayout.height + tafsirBlockHeight(tafsirLayout, sourceLayout, panelPadding)
      if (total <= available) break
      when {
        arabicSize > 40f -> arabicSize -= 2f
        tafsir != null && tafsirSize > 24f -> tafsirSize -= 1f
        else -> {
          // still too long at the smallest sizes: cut the end with an ellipsis
          val tafsirShare = if (tafsir != null) 0.4f else 0f
          val arabicPaint = textPaint(arabicTypeface, arabicSize, palette.text)
          arabicLayout = layout(
            arabic, arabicPaint, contentWidth, Layout.Alignment.ALIGN_CENTER, spacing = 1.45f,
            maxLines = linesThatFit(arabicLayout, available * (1 - tafsirShare))
          )
          if (tafsir != null && tafsirLayout != null) {
            val tafsirRoom = available - arabicLayout.height -
                tafsirBlockHeight(null, sourceLayout, panelPadding)
            tafsirLayout = layout(
              tafsir, textPaint(uiTypeface, tafsirSize, palette.text), tafsirWidth,
              Layout.Alignment.ALIGN_NORMAL, spacing = 1.3f,
              maxLines = linesThatFit(tafsirLayout, tafsirRoom)
            )
          }
          break
        }
      }
    }

    val bodyHeight = arabicLayout.height + tafsirBlockHeight(tafsirLayout, sourceLayout, panelPadding)
    var y = top + ((available - bodyHeight) / 2).coerceAtLeast(0f)
    drawLayout(canvas, arabicLayout, contentLeft, y)
    y += arabicLayout.height
    if (tafsirLayout != null) {
      y += TAFSIR_GAP
      val panelHeight = tafsirBlockHeight(tafsirLayout, sourceLayout, panelPadding) - TAFSIR_GAP
      val panel = RectF(contentLeft, y, contentRight, y + panelHeight)
      val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.panel }
      canvas.drawRoundRect(panel, 24f, 24f, panelPaint)
      drawLayout(canvas, tafsirLayout, contentLeft + panelPadding, y + panelPadding)
      sourceLayout?.let {
        drawLayout(canvas, it, contentLeft + panelPadding, y + panelPadding + tafsirLayout.height + 10f)
      }
    }
  }

  private fun tafsirBlockHeight(tafsir: StaticLayout?, source: StaticLayout?, padding: Float): Float {
    if (tafsir == null) return 0f
    return TAFSIR_GAP + padding * 2 + tafsir.height + (source?.let { it.height + 10f } ?: 0f)
  }

  private fun arabicText(content: ShareCardContent, options: ShareCardOptions): String =
    content.verses.joinToString(" ") { verse ->
      if (options.showAyahNumbers) {
        // the Uthmani font draws Arabic-Indic digits as the ornate end-of-ayah mark
        verse.arabic + " " + arabicDigits(verse.suraAyah.ayah)
      } else {
        verse.arabic
      }
    }

  private fun tafsirText(content: ShareCardContent, options: ShareCardOptions): String? {
    val parts = content.verses.mapNotNull { verse ->
      verse.tafsir?.takeIf { it.isNotBlank() }?.let { text ->
        if (options.showAyahNumbers && content.verses.size > 1) {
          "(" + persianDigits(verse.suraAyah.ayah) + ") " + text.trim()
        } else {
          text.trim()
        }
      }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
  }

  private fun textPaint(typeface: Typeface, size: Float, color: Int) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      this.typeface = typeface
      textSize = size
      this.color = color
    }

  private fun layout(
    text: String,
    paint: TextPaint,
    width: Int,
    alignment: Layout.Alignment,
    spacing: Float = 1f,
    maxLines: Int = Int.MAX_VALUE
  ): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
      .setAlignment(alignment)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setLineSpacing(0f, spacing)
      .setIncludePad(true)
      .setMaxLines(maxLines.coerceAtLeast(1))
      .setEllipsize(TextUtils.TruncateAt.END)
      .build()

  private fun linesThatFit(layout: StaticLayout, height: Float): Int {
    var lines = 0
    while (lines < layout.lineCount && layout.getLineBottom(lines) <= height) lines++
    return lines.coerceAtLeast(1)
  }

  private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
  }

  companion object {
    private const val TAFSIR_GAP = 36f

    fun arabicDigits(number: Int): String =
      number.toString().map { '٠' + (it - '0') }.joinToString("")

    fun persianDigits(number: Int): String =
      number.toString().map { '۰' + (it - '0') }.joinToString("")
  }
}
