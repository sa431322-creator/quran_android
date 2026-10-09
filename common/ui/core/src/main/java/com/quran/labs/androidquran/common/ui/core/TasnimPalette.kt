package com.quran.labs.androidquran.common.ui.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tasnim tokens for the Compose screens (live, radio, library), matching the app's tasnim_* colors. */
@Immutable
data class TasnimPalette(
  val background: Color,
  val surface: Color,
  val ink: Color,
  val muted: Color,
  val border: Color,
  val outline: Color,
  val accent: Color,
  val track: Color,
  val selectedChip: Color,
  val onSelectedChip: Color,
  val chipText: Color,
  val chipBorder: Color
) {
  val primary = Color(0xFF1F4E56)
  val onPrimary = Color(0xFFF7F0E1)
  val onPrimaryMuted = Color(0xFFBFD8DC)
  val gold = Color(0xFFC8A45E)
  val water = Color(0xFF5FA9C1)

  /** Only for live badges and the video player. */
  val live = Color(0xFFB3261E)
  val videoBackground = Color(0xFF0E1518)
  val onVideo = Color(0xFFE8E0CC)
}

private val LightTasnimPalette = TasnimPalette(
  background = Color(0xFFF7F0E1),
  surface = Color(0xFFFCF8EF),
  ink = Color(0xFF1C1A16),
  muted = Color(0xFF5A5347),
  border = Color(0xFFE4DAC4),
  outline = Color(0xFFDCCFAF),
  accent = Color(0xFF1F4E56),
  track = Color(0xFFEAE0CA),
  selectedChip = Color(0xFF1C1A16),
  onSelectedChip = Color(0xFFF7F0E1),
  chipText = Color(0xFF7E5A1C),
  chipBorder = Color(0xFFC8B48A)
)

private val DarkTasnimPalette = TasnimPalette(
  background = Color(0xFF0E1518),
  surface = Color(0xFF1A252A),
  ink = Color(0xFFE8E0CC),
  muted = Color(0xFFA9A08C),
  border = Color(0xFF2A3A40),
  outline = Color(0xFF3A4C52),
  accent = Color(0xFF8CCADB),
  track = Color(0xFF1A252A),
  selectedChip = Color(0xFFE8E0CC),
  onSelectedChip = Color(0xFF0E1518),
  chipText = Color(0xFFE3C58A),
  chipBorder = Color(0xFF5C4416)
)

val LocalTasnimPalette = staticCompositionLocalOf { LightTasnimPalette }

@Composable
fun TasnimPaletteProvider(content: @Composable () -> Unit) {
  val palette = if (isSystemInDarkTheme()) DarkTasnimPalette else LightTasnimPalette
  CompositionLocalProvider(LocalTasnimPalette provides palette, content = content)
}
