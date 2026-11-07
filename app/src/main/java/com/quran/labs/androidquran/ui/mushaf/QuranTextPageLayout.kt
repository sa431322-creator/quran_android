package com.quran.labs.androidquran.ui.mushaf

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import com.quran.data.model.highlight.HighlightType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.QuranPageLayout
import com.quran.mobile.feature.mushaf.data.MushafPage
import com.quran.mobile.feature.mushaf.ui.MushafFontPage
import com.quran.mobile.feature.mushaf.ui.MushafGesture
import com.quran.mobile.feature.mushaf.ui.MushafPageHits
import com.quran.mobile.feature.mushaf.ui.MushafPageLabels
import com.quran.mobile.feature.mushaf.ui.MushafPageStyle

/**
 * One page of the text (font) mushaf in the reader, in the reader's own page frame (borders,
 * background, night mode). Touches go to the page controller like an image page's, and
 * [trackerItem] gives the reader the ayah under a touch, an ayah's bounds and its highlights.
 */
@SuppressLint("ViewConstructor")
class QuranTextPageLayout(
  context: Context,
  private val page: Int,
  private val quranDisplayData: QuranDisplayData
) : QuranPageLayout(context) {

  private val composeView = ComposeView(context)
  internal val hits = MushafPageHits()
  internal val highlights = TextPageHighlights()
  val trackerItem = TextPageTrackerItem(page, this)

  private var nightMode by mutableStateOf(false)
  private var nightBrightness by mutableStateOf(255)

  init {
    isFullWidth = true
    initialize()
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    composeView.setContent { QuranTheme { Content() } }
  }

  override fun generateContentView(context: Context, isLandscape: Boolean): View = composeView

  // the text page scrolls itself on short screens, keeping the page's proportions
  override fun shouldWrapWithScrollView(): Boolean = false

  override fun updateView(quranSettings: QuranSettings) {
    super.updateView(quranSettings)
    nightMode = quranSettings.isNightMode
    nightBrightness = quranSettings.nightModeTextBrightness
  }

  @androidx.compose.runtime.Composable
  private fun Content() {
    val repository = TextMushaf.repository(context)
    val mushafPage by produceState<MushafPage?>(null, page) {
      value = runCatching { repository.data().pages.getOrNull(page - 1) }.getOrNull()
    }
    val current = mushafPage ?: return
    val style = pageStyle()
    MushafFontPage(
      page = current,
      repository = repository,
      basmalaFont = basmalaFont,
      style = style,
      labels = labels,
      suraHeaderName = { sura -> quranDisplayData.getSuraName(context, sura, true) },
      selectedAyah = highlights.keysOf(HighlightTypes.SELECTION).firstOrNull(),
      recitedAyah = highlights.keysOf(HighlightTypes.AUDIO).firstOrNull(),
      highlights = highlights.otherColors(::colorOf),
      onTap = {},
      hits = hits,
      onGesture = ::onGesture
    )
  }

  private val basmalaFont: FontFamily by lazy {
    runCatching { FontFamily(TypefaceManager.getUthmaniTypeface(context)) }.getOrDefault(FontFamily.Default)
  }

  private val headerFont: FontFamily by lazy { FontFamily(TypefaceManager.getHeaderFooterTypeface(context)) }

  // sura names in the Kitab face, as in Tasnim's identity guide
  private val titleFont: FontFamily by lazy { FontFamily(TypefaceManager.getTafseerTypeface(context)) }

  private val labels: MushafPageLabels by lazy {
    MushafPageLabels(
      suraName = quranDisplayData.getSuraNameFromPage(context, page, true),
      juz = quranDisplayData.getJuzDisplayStringForPage(context, page),
      pageNumber = QuranUtils.getLocalizedNumber(page)
    )
  }

  private fun color(id: Int) = Color(ContextCompat.getColor(context, id))

  private fun pageStyle(): MushafPageStyle {
    // the page frame draws the paper (day or night); the text page draws only on top of it
    val ink = if (nightMode) Color(255, 255, 255, nightBrightness) else Color.Black
    return MushafPageStyle(
      paper = Color.Transparent,
      headerFont = headerFont,
      headerColor = color(if (nightMode) R.color.mushaf_label_night else R.color.mushaf_label),
      accent = color(R.color.mushaf_accent),
      gilt = color(if (nightMode) R.color.mushaf_gilt_night else R.color.mushaf_gilt),
      selection = color(R.color.selection_highlight),
      recitation = color(R.color.audio_highlight),
      ink = ink,
      titleFont = titleFont
    )
  }

  private fun colorOf(type: HighlightType): Color =
    color(if (nightMode) type.nightColorResId else type.colorResId)

  /** A touch on the page, as the reader's page controller expects it from an image page. */
  private fun onGesture(gesture: MushafGesture, window: Offset) {
    val controller = pageController ?: return
    val type = when (gesture) {
      MushafGesture.TAP -> AyahSelectedListener.EventType.SINGLE_TAP
      MushafGesture.DOUBLE_TAP -> AyahSelectedListener.EventType.DOUBLE_TAP
      MushafGesture.LONG_PRESS -> AyahSelectedListener.EventType.LONG_PRESS
    }
    val local = if (window.isSpecified) toLocal(window) else Offset(-1f, -1f)
    val time = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, local.x, local.y, 0)
    try {
      controller.handleTouchEvent(event, type, page)
    } finally {
      event.recycle()
    }
  }

  internal fun toLocal(window: Offset): Offset {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Offset(window.x - location[0], window.y - location[1])
  }

  internal fun toWindow(local: Offset): Offset {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Offset(local.x + location[0], local.y + location[1])
  }
}
