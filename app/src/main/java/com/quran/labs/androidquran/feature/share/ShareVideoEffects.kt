package com.quran.labs.androidquran.feature.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The gentle motion of a shared recitation video: a soft gold light drifting across the card, and
 * a short fade toward the background color where one ayah's card gives way to the next.
 */
@OptIn(UnstableApi::class)
object ShareVideoEffects {
  private const val FADE_MS = 280L
  private const val FADE_STRENGTH = 0.85f
  private const val LIGHT_PERIOD_MS = 9000.0

  /**
   * Effects for a whole video whose cards change at [boundariesMs] (from 0, each card's start,
   * then the end). [background] is the card's main background color.
   */
  fun forVideo(boundariesMs: List<Long>, background: Int, light: Int): List<Effect> =
    listOf(
      OverlayEffect(ImmutableList.of<TextureOverlay>(DriftingLight(light))),
      FadeBetweenCards(boundariesMs, background)
    )

  /** How far the picture is mixed toward the background at [timeMs]: 1 at a cut, 0 elsewhere. */
  fun fadeAmount(timeMs: Long, boundariesMs: List<Long>): Float {
    val nearest = boundariesMs.minOfOrNull { abs(it - timeMs) } ?: return 0f
    if (nearest >= FADE_MS) return 0f
    return FADE_STRENGTH * (1f - nearest.toFloat() / FADE_MS)
  }

  /** Mixes every pixel toward one color by the fade amount (a column-major 4×4 RGBA matrix). */
  private class FadeBetweenCards(private val boundariesMs: List<Long>, background: Int) : RgbMatrix {
    private val r = Color.red(background) / 255f
    private val g = Color.green(background) / 255f
    private val b = Color.blue(background) / 255f

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
      val s = fadeAmount(presentationTimeUs / 1000, boundariesMs)
      val keep = 1f - s
      return floatArrayOf(
        keep, 0f, 0f, 0f,
        0f, keep, 0f, 0f,
        0f, 0f, keep, 0f,
        s * r, s * g, s * b, 1f
      )
    }
  }

  /** A soft round glow that slowly sweeps across the frame and back. */
  private class DriftingLight(color: Int) : BitmapOverlay() {
    private val glow: Bitmap = createBitmap(GLOW_SIZE, GLOW_SIZE).also { bitmap ->
      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          GLOW_SIZE / 2f, GLOW_SIZE / 2f, GLOW_SIZE / 2f,
          (color and 0x00FFFFFF) or (0x70 shl 24), color and 0x00FFFFFF, Shader.TileMode.CLAMP
        )
      }
      Canvas(bitmap).drawCircle(GLOW_SIZE / 2f, GLOW_SIZE / 2f, GLOW_SIZE / 2f, paint)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = glow

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
      val phase = presentationTimeUs / 1000.0 / LIGHT_PERIOD_MS * 2 * PI
      // anchors are in normalized frame coordinates, -1 to 1
      val x = (0.9 * sin(phase)).toFloat()
      val y = (0.35 * sin(phase * 0.5 + 1.0)).toFloat()
      return StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(x, y)
        .setAlphaScale(0.55f)
        .build()
    }

    companion object {
      private const val GLOW_SIZE = 900
    }
  }
}
