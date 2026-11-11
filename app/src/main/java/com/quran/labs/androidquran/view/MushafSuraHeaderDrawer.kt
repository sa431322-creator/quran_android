package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws the Tasnim sura header frame (teal panel, gold border and diamonds, cream pill) over the
 * sura header frame of the page image, in day mode only. The sura name of the image is left
 * showing through a window in the pill, so the original calligraphy is kept as is.
 *
 * Header positions are estimates (see AyahInfoDatabaseHandler), so each one is only drawn after
 * the left border of the frame is found in the page bitmap near the estimate.
 */
class MushafSuraHeaderDrawer(
  context: Context,
  private val isNightMode: () -> Boolean
) : ImageDrawHelper {
  private val density = context.resources.displayMetrics.density

  // frame tops (in reference coordinates) found in each page bitmap
  private val frameTops = WeakHashMap<Bitmap, FloatArray>()
  private val path = Path()
  private val outerRect = RectF()
  private val pillRect = RectF()
  private val windowRect = RectF()

  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ContextCompat.getColor(context, R.color.mushaf_sura_header_fill)
    style = Paint.Style.FILL
  }

  private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ContextCompat.getColor(context, R.color.mushaf_sura_header_pill)
    style = Paint.Style.FILL
  }

  private val goldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ContextCompat.getColor(context, R.color.mushaf_sura_header_gold)
    style = Paint.Style.STROKE
    strokeWidth = 1.5f * density
  }

  private val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ContextCompat.getColor(context, R.color.mushaf_sura_header_gold)
    style = Paint.Style.FILL
  }

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    if (isNightMode() || pageCoordinates.suraHeaders.isEmpty()) return
    val drawable = image.drawable as? BitmapDrawable ?: return
    val bitmap = drawable.bitmap ?: return
    if (bitmap.isRecycled || drawable.intrinsicWidth <= 0) return

    val tops = frameTops.getOrPut(bitmap) { findFrameTops(bitmap) }
    if (tops.isEmpty()) return

    // reference coordinates -> image coordinates
    val scale = drawable.intrinsicWidth / REFERENCE_WIDTH
    pageCoordinates.suraHeaders.forEach { header ->
      // header.y is in image coordinates (ayahinfo_<width>.db); the frame tops are in reference ones
      val headerY = header.y / scale
      val top = tops.minByOrNull { abs(it + FRAME_HEIGHT / 2 - headerY) } ?: return@forEach
      if (abs(top + FRAME_HEIGHT / 2 - headerY) <= MAX_ESTIMATE_ERROR) {
        drawHeader(canvas, image, top, scale)
      }
    }
  }

  private fun drawHeader(canvas: Canvas, image: ImageView, top: Float, scale: Float) {
    val outer = map(image, scale, FRAME_LEFT - PAD, top - PAD,
      FRAME_RIGHT + PAD, top + FRAME_HEIGHT + PAD, outerRect)
    val pill = map(image, scale, PILL_LEFT, top + PILL_TOP,
      PILL_RIGHT, top + PILL_BOTTOM, pillRect)
    val window = map(image, scale, WINDOW_LEFT, top + WINDOW_TOP,
      WINDOW_RIGHT, top + WINDOW_BOTTOM, windowRect)
    val pillRadius = pill.height() / 2
    val windowRadius = WINDOW_RADIUS / (FRAME_HEIGHT + 2 * PAD) * outer.height()
    val cornerRadius = CORNER_RADIUS * density

    // teal panel around the pill
    path.reset()
    path.fillType = Path.FillType.EVEN_ODD
    path.addRoundRect(outer, cornerRadius, cornerRadius, Path.Direction.CW)
    path.addRoundRect(pill, pillRadius, pillRadius, Path.Direction.CW)
    canvas.drawPath(path, fillPaint)

    // cream pill, with a window that leaves the sura name of the image showing
    path.reset()
    path.addRoundRect(pill, pillRadius, pillRadius, Path.Direction.CW)
    path.addRoundRect(window, windowRadius, windowRadius, Path.Direction.CW)
    canvas.drawPath(path, pillPaint)

    // gold inner border and pill outline
    val inset = 3.5f * density
    outer.inset(inset, inset)
    canvas.drawRoundRect(outer, cornerRadius / 2, cornerRadius / 2, goldStroke)
    val half = goldStroke.strokeWidth / 2
    pill.inset(half, half)
    canvas.drawRoundRect(pill, pillRadius - half, pillRadius - half, goldStroke)

    // gold diamonds in the middle of each side panel
    val size = outer.height() * 0.2f
    drawDiamond(canvas, (outer.left + pill.left) / 2, outer.centerY(), size)
    drawDiamond(canvas, (pill.right + outer.right) / 2, outer.centerY(), size)
  }

  private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, size: Float) {
    path.reset()
    path.fillType = Path.FillType.WINDING
    addDiamond(cx, cy, size)
    canvas.drawPath(path, goldStroke)

    path.reset()
    addDiamond(cx, cy, size * 0.45f)
    canvas.drawPath(path, goldFill)
  }

  private fun addDiamond(cx: Float, cy: Float, size: Float) {
    path.moveTo(cx, cy - size)
    path.lineTo(cx + size, cy)
    path.lineTo(cx, cy + size)
    path.lineTo(cx - size, cy)
    path.close()
  }

  private fun map(
    image: ImageView, scale: Float,
    left: Float, top: Float, right: Float, bottom: Float, out: RectF
  ): RectF {
    out.set(left * scale, top * scale, right * scale, bottom * scale)
    image.imageMatrix.mapRect(out)
    out.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
    return out
  }

  /**
   * Finds the frames in the page bitmap by their left border: a dark vertical run of exactly the
   * height of a frame. Nothing else on a page comes close (the longest other run is a third of it).
   */
  private fun findFrameTops(bitmap: Bitmap): FloatArray {
    val bitmapScale = bitmap.width / REFERENCE_WIDTH
    val x = (BORDER_X * bitmapScale).roundToInt()
    if (x !in 0 until bitmap.width) return FloatArray(0)
    val expected = FRAME_HEIGHT * bitmapScale
    val tolerance = maxOf(2f, 3 * bitmapScale)

    val tops = mutableListOf<Float>()
    var start = -1
    for (y in 0..bitmap.height) {
      val dark = y < bitmap.height && Color.alpha(bitmap.getPixel(x, y)) > 128
      if (dark && start < 0) {
        start = y
      } else if (!dark && start >= 0) {
        if (abs((y - start) - expected) <= tolerance) {
          tops.add(start / bitmapScale)
        }
        start = -1
      }
    }
    return tops.toFloatArray()
  }

  private companion object {
    // geometry of the madani header frame, measured on the 1260px wide images
    const val REFERENCE_WIDTH = 1260f
    const val FRAME_LEFT = 55f
    const val FRAME_RIGHT = 1200f
    const val FRAME_HEIGHT = 141f
    const val BORDER_X = 57f
    const val PAD = 3f
    const val PILL_LEFT = 324f
    const val PILL_RIGHT = 930f
    const val PILL_TOP = 8f
    const val PILL_BOTTOM = 133f
    // the sura names stay within x 490..805 and 25..108 below the frame top
    const val WINDOW_LEFT = 420f
    const val WINDOW_RIGHT = 834f
    const val WINDOW_TOP = 21f
    const val WINDOW_BOTTOM = 120f
    const val WINDOW_RADIUS = 24f
    // dp; small enough that the rounded corners still cover the square corners of the frame
    const val CORNER_RADIUS = 2.5f
    const val MAX_ESTIMATE_ERROR = 120f
  }
}
