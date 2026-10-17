package com.quran.mobile.feature.tajweed

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.font.FontFamily
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.mobile.feature.tajweed.data.TajweedRepository
import com.quran.mobile.feature.tajweed.ui.TajweedMushafScreen

/**
 * A separate, experimental tajweed mushaf (QPC V4 tajweed fonts from QUL). It does not touch the
 * app's own mushaf, its fonts or its reading position; it remembers its own last page.
 */
class TajweedMushafActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
    val repository = TajweedRepository(this)
    // the app's own Hafs font, used as is for the basmala lines
    val basmalaFont = runCatching { FontFamily(Typeface.createFromAsset(assets, BASMALA_FONT)) }
      .getOrDefault(FontFamily.Default)

    setContent {
      QuranTheme {
        TasnimPaletteProvider {
          TajweedMushafScreen(
            repository = repository,
            basmalaFont = basmalaFont,
            initialPage = preferences.getInt(KEY_LAST_PAGE, 1),
            onPageChanged = { page -> preferences.edit().putInt(KEY_LAST_PAGE, page).apply() },
            onBack = onBackPressedDispatcher::onBackPressed
          )
        }
      }
    }
  }

  private companion object {
    const val PREFERENCES = "tajweed_mushaf"
    const val KEY_LAST_PAGE = "last_page"
    const val BASMALA_FONT = "uthmanic_hafs_ver12.otf"
  }
}
