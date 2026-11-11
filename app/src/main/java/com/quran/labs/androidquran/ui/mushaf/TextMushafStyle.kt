package com.quran.labs.androidquran.ui.mushaf

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.mobile.feature.mushaf.ui.MushafPageStyle
import com.quran.mobile.feature.mushaf.ui.nightGlyphFilter

/**
 * The text mushaf's look (Tasnim's gilt design), day or night, and its fonts: shared by the
 * reader's text pages and the ayah cards.
 */
object TextMushafStyle {
  private class Fonts(val basmala: FontFamily, val header: FontFamily, val title: FontFamily)

  @Volatile private var fonts: Fonts? = null

  private fun fonts(context: Context): Fonts = fonts ?: synchronized(this) {
    fonts ?: Fonts(
      basmala = runCatching { FontFamily(TypefaceManager.getUthmaniTypeface(context)) }.getOrDefault(FontFamily.Default),
      header = FontFamily(TypefaceManager.getHeaderFooterTypeface(context)),
      // sura names in the Kitab face, as in Tasnim's identity guide
      title = FontFamily(TypefaceManager.getTafseerTypeface(context))
    ).also { fonts = it }
  }

  fun basmalaFont(context: Context): FontFamily = fonts(context).basmala

  /** The night ink: white at the reader's night text brightness. */
  fun ink(nightMode: Boolean, nightBrightness: Int): Color =
    if (nightMode) Color(255, 255, 255, nightBrightness) else Color.Black

  /** Drawn over [paper] (transparent: the host draws the paper, as the reader's page frame does). */
  fun pageStyle(
    context: Context,
    nightMode: Boolean,
    nightBrightness: Int,
    paper: Color = Color.Transparent
  ): MushafPageStyle {
    fun color(id: Int) = Color(ContextCompat.getColor(context, id))
    val ink = ink(nightMode, nightBrightness)
    val fonts = fonts(context)
    return MushafPageStyle(
      paper = paper,
      headerFont = fonts.header,
      headerColor = color(if (nightMode) R.color.mushaf_label_night else R.color.mushaf_label),
      accent = color(R.color.mushaf_accent),
      gilt = color(if (nightMode) R.color.mushaf_gilt_night else R.color.mushaf_gilt),
      selection = color(R.color.selection_highlight),
      recitation = color(R.color.audio_highlight),
      ink = ink,
      titleFont = fonts.title,
      glyphFilter = if (nightMode) nightGlyphFilter(ink) else null
    )
  }
}
