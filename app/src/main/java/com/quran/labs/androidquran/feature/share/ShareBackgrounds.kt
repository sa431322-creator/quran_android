package com.quran.labs.androidquran.feature.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colors a card uses on top of its background. */
data class SharePalette(
  /** the background's main color, which video cards fade toward between ayahs */
  val base: Int,
  /** the drifting light of videos, and how strongly it shows */
  val light: Int,
  val lightAlpha: Float = 0.55f,
  val frame: Int,
  val text: Int,
  val muted: Int,
  val panel: Int,
  val badge: Int
)

/**
 * The five card backgrounds, drawn in code in Tasnim's navy (#0B1426) and gold (#D4AF37) with
 * Islamic patterns, so they stay sharp at any size and need no image files.
 */
object ShareBackgrounds {
  const val COUNT = 5

  private const val NAVY = 0xFF0B1426.toInt()
  private const val NAVY_LIGHT = 0xFF1A2C55.toInt()
  private const val GOLD = 0xFFD4AF37.toInt()
  private const val GOLD_DEEP = 0xFFB08A2E.toInt()
  private const val CREAM = 0xFFF7EFDD.toInt()
  private const val CREAM_DEEP = 0xFFEBDDBE.toInt()
  private const val INK = 0xFF2A2114.toInt()
  private const val EMERALD = 0xFF0E3B2E.toInt()
  private const val EMERALD_DARK = 0xFF06231A.toInt()

  fun palette(index: Int): SharePalette = when (index.mod(COUNT)) {
    2 -> SharePalette(
      base = CREAM, light = GOLD, lightAlpha = 0.18f,
      frame = GOLD_DEEP, text = INK, muted = 0xFF6B5A3A.toInt(),
      panel = 0x14000000, badge = 0xFFFFFAEE.toInt()
    )
    4 -> SharePalette(
      base = EMERALD_DARK, light = GOLD,
      frame = GOLD, text = 0xFFFBF6EA.toInt(), muted = 0xFFCBBF9E.toInt(),
      panel = 0x1FFFFFFF, badge = 0x33000000
    )
    else -> SharePalette(
      base = NAVY, light = GOLD,
      frame = GOLD, text = 0xFFFBF6EA.toInt(), muted = 0xFFCBBF9E.toInt(),
      panel = 0x1FFFFFFF, badge = 0x33000000
    )
  }

  fun draw(canvas: Canvas, index: Int, width: Int, height: Int) {
    val w = width.toFloat()
    val h = height.toFloat()
    when (index.mod(COUNT)) {
      0 -> starNight(canvas, w, h)
      1 -> crescent(canvas, w, h)
      2 -> paper(canvas, w, h)
      3 -> mihrab(canvas, w, h)
      else -> emerald(canvas, w, h)
    }
  }

  /** Navy with a faint repeating gold eight-pointed star pattern. */
  private fun starNight(canvas: Canvas, w: Float, h: Float) {
    canvas.drawColor(NAVY)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = w / 540f
      color = withAlpha(GOLD, 26)
    }
    val step = w / 6f
    var y = -step / 2
    var row = 0
    while (y < h + step) {
      var x = if (row % 2 == 0) 0f else step / 2
      while (x < w + step) {
        canvas.drawPath(star(x, y, step * 0.34f, step * 0.24f), paint)
        x += step
      }
      y += step / 2
      row++
    }
    vignette(canvas, w, h)
  }

  /** Night-blue gradient with a soft gold glow and a crescent at the top. */
  private fun crescent(canvas: Canvas, w: Float, h: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, h, NAVY_LIGHT, NAVY, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = RadialGradient(
      w / 2, h * 0.08f, w * 0.7f,
      withAlpha(GOLD, 60), withAlpha(GOLD, 0), Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    // small stars
    paint.color = withAlpha(GOLD, 70)
    val dots = listOf(0.12f to 0.2f, 0.85f to 0.15f, 0.2f to 0.62f, 0.9f to 0.5f, 0.75f to 0.86f, 0.08f to 0.9f)
    dots.forEach { (fx, fy) -> canvas.drawPath(star(w * fx, h * fy, w * 0.012f, w * 0.005f), paint) }
    vignette(canvas, w, h)
  }

  /** Cream paper with a gold arabesque border, for dark text. */
  private fun paper(canvas: Canvas, w: Float, h: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
      w / 2, h / 2, maxOf(w, h) * 0.75f, CREAM, CREAM_DEEP, Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = w / 700f
    paint.color = withAlpha(GOLD_DEEP, 40)
    // a band of interlaced circles along the edges
    val r = w / 30f
    var x = r
    while (x < w) {
      canvas.drawCircle(x, r * 1.2f, r, paint)
      canvas.drawCircle(x, h - r * 1.2f, r, paint)
      x += r * 1.4f
    }
    var y = r
    while (y < h) {
      canvas.drawCircle(r * 1.2f, y, r, paint)
      canvas.drawCircle(w - r * 1.2f, y, r, paint)
      y += r * 1.4f
    }
  }

  /** Navy with a gold mihrab arch around the text. */
  private fun mihrab(canvas: Canvas, w: Float, h: Float) {
    canvas.drawColor(NAVY)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
      w / 2, h * 0.45f, w * 0.65f, withAlpha(NAVY_LIGHT, 255), NAVY, Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.color = withAlpha(GOLD, 70)
    paint.strokeWidth = w / 360f
    val left = w * 0.1f
    val right = w * 0.9f
    val top = h * 0.07f
    val bottom = h * 0.95f
    val shoulder = top + (right - left) * 0.42f
    val arch = Path().apply {
      moveTo(left, bottom)
      lineTo(left, shoulder)
      quadTo(left, top + (shoulder - top) * 0.25f, w / 2, top)
      quadTo(right, top + (shoulder - top) * 0.25f, right, shoulder)
      lineTo(right, bottom)
    }
    canvas.drawPath(arch, paint)
    paint.strokeWidth = w / 900f
    canvas.save()
    canvas.scale(0.94f, 0.96f, w / 2, bottom)
    canvas.drawPath(arch, paint)
    canvas.restore()
    vignette(canvas, w, h)
  }

  /** Deep emerald with gold leaf ornaments in the corners. */
  private fun emerald(canvas: Canvas, w: Float, h: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, w, h, EMERALD, EMERALD_DARK, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = w / 600f
    paint.color = withAlpha(GOLD, 60)
    val size = min(w, h) * 0.3f
    listOf(0f to 0f, w to 0f, 0f to h, w to h).forEach { (cx, cy) ->
      for (i in 1..4) {
        canvas.drawCircle(cx, cy, size * i / 4f, paint)
      }
      for (i in 0 until 12) {
        val angle = Math.toRadians(i * 30.0)
        canvas.drawLine(
          cx, cy,
          cx + (size * cos(angle)).toFloat(), cy + (size * sin(angle)).toFloat(),
          paint
        )
      }
    }
    vignette(canvas, w, h)
  }

  private fun vignette(canvas: Canvas, w: Float, h: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
      w / 2, h / 2, maxOf(w, h) * 0.75f,
      Color.TRANSPARENT, 0x66000000, Shader.TileMode.CLAMP
    )
    canvas.drawRect(RectF(0f, 0f, w, h), paint)
  }

  /** An eight-pointed star (two squares turned 45° from each other) centred on [cx], [cy]. */
  fun star(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val path = Path()
    for (i in 0 until 16) {
      val radius = if (i % 2 == 0) outer else inner
      val angle = Math.PI / 8 * i - Math.PI / 2
      val x = cx + (radius * cos(angle)).toFloat()
      val y = cy + (radius * sin(angle)).toFloat()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
  }

  private fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha shl 24)
}
