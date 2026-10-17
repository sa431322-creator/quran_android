package com.quran.mobile.feature.tajweed

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.font.FontFamily
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.data.model.SuraAyah
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.ayahbookmark.AyahBookmarkWrapper
import com.quran.mobile.feature.ayahbookmark.di.AyahBookmarkWrapperInjector
import com.quran.mobile.feature.tajweed.data.ayahKey
import kotlinx.coroutines.flow.map
import com.quran.mobile.feature.tajweed.data.TajweedRepository
import com.quran.mobile.feature.tajweed.di.TajweedComponentInterface
import com.quran.mobile.feature.tajweed.ui.TajweedAyahActions
import com.quran.mobile.feature.tajweed.ui.TajweedMushafScreen
import com.quran.mobile.feature.tajweed.ui.TajweedPlaybackControls

/**
 * A separate, experimental tajweed mushaf (QPC V4 tajweed fonts from QUL). It does not touch the
 * app's own mushaf, its fonts or its reading position; it remembers its own last page.
 *
 * Opened from the mushaf reader with [TajweedMushafContract], it starts at the reader's ayah and
 * returns the ayah it was left at, since the two mushafs lay their pages out differently.
 */
class TajweedMushafActivity : AppCompatActivity(), AyahBookmarkWrapperInjector {

  private var dependencies: TajweedComponentInterface? = null
  // hosts the reader's bookmark sheet above the Compose screen
  private lateinit var overlay: FrameLayout
  // the bookmark sheet saves when it is closed with Done or swiped away, so back is not allowed
  // to close the whole screen underneath it and lose those choices
  private val bookmarkSheetBack = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() = Unit
  }

  private var position: Pair<Int, Int>? = null
  // where it opened: leaving from there returns nothing, so the reader stays on its own page
  // rather than moving to wherever this page's first ayah falls in the reader's layout
  private var startPosition: Pair<Int, Int>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    savedInstanceState?.let { state ->
      val sura = state.getInt(STATE_START_SURA, 0)
      val ayah = state.getInt(STATE_START_AYAH, 0)
      if (sura > 0 && ayah > 0) startPosition = sura to ayah
    }
    enableEdgeToEdge()

    val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
    val repository = TajweedRepository(this)
    // the app's own Hafs font, used as is for the basmala lines
    val basmalaFont = runCatching { FontFamily(Typeface.createFromAsset(assets, BASMALA_FONT)) }
      .getOrDefault(FontFamily.Default)

    val sura = intent.getIntExtra(EXTRA_SURA, 0)
    val ayah = intent.getIntExtra(EXTRA_AYAH, 0)
    val startAyah = if (sura > 0 && ayah > 0) sura to ayah else null

    val dependencies = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? TajweedComponentInterface
    this.dependencies = dependencies
    val audio = dependencies?.tajweedAudio()
    val ayahActions = if (dependencies != null && audio != null) {
      val tafsirRepository = dependencies.persianTafsirRepository()
      val sharing = dependencies.tajweedSharing()
      TajweedAyahActions(
        tafsir = { sura, ayah ->
          tafsirRepository.tafsir(SuraAyah(sura, ayah), SuraAyah(sura, ayah))?.firstOrNull()?.text
        },
        play = { sura, ayah, allowMobileDownload, options ->
          audio.playFrom(this, sura, ayah, allowMobileDownload, options)
        },
        stop = { audio.stop(this) },
        openBookmarks = ::showBookmarkSheet,
        copy = { sura, ayah -> sharing.copy(this, sura, ayah) },
        shareText = { sura, ayah -> sharing.shareText(this, sura, ayah) },
        shareLink = { sura, ayah -> sharing.shareLink(this, sura, ayah) },
        highlights = dependencies.highlightsDao().highlightsFlow().map { highlights ->
          highlights.associate { ayahKey(it.suraAyah.sura, it.suraAyah.ayah) to it.color }
        }
      )
    } else {
      null
    }
    val playbackControls = audio?.let { player ->
      TajweedPlaybackControls(
        playback = player.playback,
        audioDownloaded = player.audioDownloaded,
        pause = { player.pause(this) },
        resume = { player.resume(this) },
        stop = { player.stop(this) }
      )
    }

    setContent {
      QuranTheme {
        TasnimPaletteProvider {
          TajweedMushafScreen(
            repository = repository,
            basmalaFont = basmalaFont,
            initialPage = preferences.getInt(KEY_LAST_PAGE, 1),
            onPageChanged = { page -> preferences.edit().putInt(KEY_LAST_PAGE, page).apply() },
            onBack = onBackPressedDispatcher::onBackPressed,
            startAyah = startAyah,
            onPositionChanged = { current ->
              if (startPosition == null) startPosition = current
              position = current
            },
            onMushaf = if (startAyah != null) ::finish else null,
            ayahActions = ayahActions,
            playbackControls = playbackControls
          )
        }
      }
    }

    overlay = FrameLayout(this).apply { visibility = View.GONE }
    addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    onBackPressedDispatcher.addCallback(this, bookmarkSheetBack)
  }

  override fun injectAyahBookmarkWrapper(ayahBookmarkWrapper: AyahBookmarkWrapper) {
    dependencies?.let { component ->
      ayahBookmarkWrapper.ayahBookmarkPresenterFactory = component.ayahBookmarkPresenterFactory()
      ayahBookmarkWrapper.readingBookmarkUpdater = component.readingBookmarkUpdater()
    }
  }

  /** The reader's own bookmark sheet, as the reader shows it from an ayah's menu. */
  private fun showBookmarkSheet(sura: Int, ayah: Int) {
    if (dependencies == null) return
    val wrapper = AyahBookmarkWrapper(this, SuraAyah(sura, ayah)) {
      overlay.removeAllViews()
      overlay.visibility = View.GONE
      bookmarkSheetBack.isEnabled = false
    }
    overlay.removeAllViews()
    overlay.addView(wrapper, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    overlay.visibility = View.VISIBLE
    bookmarkSheetBack.isEnabled = true
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    startPosition?.let { (sura, ayah) ->
      outState.putInt(STATE_START_SURA, sura)
      outState.putInt(STATE_START_AYAH, ayah)
    }
  }

  override fun finish() {
    position?.takeIf { it != startPosition }?.let { (sura, ayah) ->
      setResult(RESULT_OK, Intent().putExtra(EXTRA_SURA, sura).putExtra(EXTRA_AYAH, ayah))
    }
    super.finish()
  }

  internal companion object {
    const val EXTRA_SURA = "sura"
    const val EXTRA_AYAH = "ayah"
    const val STATE_START_SURA = "start_sura"
    const val STATE_START_AYAH = "start_ayah"
    const val PREFERENCES = "tajweed_mushaf"
    const val KEY_LAST_PAGE = "last_page"
    const val BASMALA_FONT = "uthmanic_hafs_ver12.otf"
  }
}
