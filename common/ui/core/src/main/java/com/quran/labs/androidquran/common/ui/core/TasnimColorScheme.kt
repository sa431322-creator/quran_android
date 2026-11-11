package com.quran.labs.androidquran.common.ui.core

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Tasnim's look on top of upstream's theme (docs/upstream-base/PLAN.md, section A): the accent
 * through upstream's [QuranAccent], and the cream / night surfaces over upstream's color scheme.
 * Identity: dark teal #1F4E56, cream #F7F0E1, gold #C8A45E; night #0E1518.
 */
object TasnimColorScheme {

  /** Keep in sync with accent_navy_gold in app/src/madani/res (Tasnim's override of it). */
  val Accent = QuranAccent(
    light = Color(0xFF1F4E56),
    dark = Color(0xFF8CCADB),
    onLight = Color(0xFFF7F0E1),
    onDark = Color(0xFF0E1518)
  )

  /** [scheme] (upstream's, with the accent as primary) in Tasnim's surfaces and secondary colors. */
  fun apply(scheme: ColorScheme, useDarkTheme: Boolean): ColorScheme =
    if (useDarkTheme) {
      scheme.copy(
        primaryContainer = Color(0xFF1F4E56),
        onPrimaryContainer = Color(0xFFD5E6E8),
        secondary = Color(0xFFA9C9CC),
        onSecondary = Color(0xFF143A40),
        secondaryContainer = Color(0xFF2A3A40),
        onSecondaryContainer = Color(0xFFDCEAEA),
        tertiary = Color(0xFFE3C58A),
        onTertiary = Color(0xFF3A2A08),
        tertiaryContainer = Color(0xFF5C4416),
        onTertiaryContainer = Color(0xFFF1E2C0),
        background = Color(0xFF0E1518),
        onBackground = Color(0xFFE8E0CC),
        surface = Color(0xFF1A252A),
        onSurface = Color(0xFFE8E0CC),
        surfaceVariant = Color(0xFF2A3A40),
        onSurfaceVariant = Color(0xFFA9A08C),
        outline = Color(0xFF7D8A8E),
        inverseOnSurface = Color(0xFF0E1518),
        inverseSurface = Color(0xFFE8E0CC),
        // the containers (sheets, dialogs, menus): Tasnim's night, not Material's default
        surfaceContainerLowest = Color(0xFF0A1012),
        surfaceContainerLow = Color(0xFF141D21),
        surfaceContainer = Color(0xFF1A252A),
        surfaceContainerHigh = Color(0xFF22303A),
        surfaceContainerHighest = Color(0xFF0E1518)
      )
    } else {
      scheme.copy(
        primaryContainer = Color(0xFFD5E6E8),
        onPrimaryContainer = Color(0xFF143A40),
        secondary = Color(0xFF3C6B6D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCEAEA),
        onSecondaryContainer = Color(0xFF143A40),
        tertiary = Color(0xFF7E5A1C),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF1E2C0),
        onTertiaryContainer = Color(0xFF3A2A08),
        background = Color(0xFFF7F0E1),
        onBackground = Color(0xFF1C1A16),
        surface = Color(0xFFFCF8EF),
        onSurface = Color(0xFF1C1A16),
        surfaceVariant = Color(0xFFEDE4D0),
        onSurfaceVariant = Color(0xFF5A5347),
        outline = Color(0xFF8A8170),
        inverseOnSurface = Color(0xFFF7F0E1),
        inverseSurface = Color(0xFF2E2B25),
        // the containers (sheets, dialogs, menus): Tasnim's cream, not Material's lavender
        surfaceContainerLowest = Color(0xFFFFFBF3),
        surfaceContainerLow = Color(0xFFFCF8EF),
        surfaceContainer = Color(0xFFF7F0E1),
        surfaceContainerHigh = Color(0xFFF1E9D6),
        surfaceContainerHighest = Color(0xFFF7F0E1)
      )
    }
}
