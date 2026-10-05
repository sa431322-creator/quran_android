package com.quran.mobile.feature.livetv.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tasnim tokens used by the live screens, matching the app's tasnim_* colors. */
@Immutable
data class LivePalette(
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
  val chipText: Color
) {
  val primary = Color(0xFF1F4E56)
  val onPrimary = Color(0xFFF7F0E1)
  val onPrimaryMuted = Color(0xFFBFD8DC)
  val gold = Color(0xFFC8A45E)
  val water = Color(0xFF5FA9C1)

  /** Only for live badges. */
  val live = Color(0xFFB3261E)
  val videoBackground = Color(0xFF0E1518)
  val thumbnail = Color(0xFF1A252A)
  val onVideo = Color(0xFFE8E0CC)
}

private val LightLivePalette = LivePalette(
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
  chipText = Color(0xFF7E5A1C)
)

private val DarkLivePalette = LivePalette(
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
  chipText = Color(0xFFE3C58A)
)

val LocalLivePalette = staticCompositionLocalOf { LightLivePalette }

@Composable
fun LivePaletteProvider(content: @Composable () -> Unit) {
  val palette = if (isSystemInDarkTheme()) DarkLivePalette else LightLivePalette
  CompositionLocalProvider(LocalLivePalette provides palette, content = content)
}
