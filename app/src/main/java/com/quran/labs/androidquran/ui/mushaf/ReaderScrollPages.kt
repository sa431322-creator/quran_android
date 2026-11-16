package com.quran.labs.androidquran.ui.mushaf

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.mushaf.data.ayahKey
import com.quran.mobile.feature.mushaf.ui.MushafPageLabels

/**
 * The reader's scrolling pages: a layer over the reader's text pages with the mushaf's pages one
 * under the other, plain or tajweed as the reader's pages are. It is how the mushaf is read when
 * the reader is set to scroll instead of turning pages («خواندن پیمایشی»), and what moves on its
 * own in auto-scroll («پیمایش خودکار»). The reader is kept on the page at the middle of the
 * screen, so leaving the layer lands on that page.
 */
class ReaderScrollPages(
  /** the reader's pages, looked up when the layer first shows (the reader sets them up late) */
  private val pages: () -> View,
  private val labels: (Int) -> MushafPageLabels,
  private val suraName: (Int) -> String,
  private val actions: ScrollPagesActions
) {
  val state = ScrollPagesState()
  private var view: ComposeView? = null

  val isShowing: Boolean get() = view?.isVisible == true

  /** Whether the layer is showing and moving on its own (or stopped by a tap, in auto-scroll). */
  val isAutoScrolling: Boolean get() = isShowing && state.auto

  /** Shows the layer at the top of [page], read by hand. */
  fun show(page: Int) {
    val pages = pages()
    val context = pages.context
    state.startPage = page
    state.currentPage = page
    state.jumpTo = null
    state.auto = false
    state.playing = false
    state.session++
    val layer = view ?: ComposeView(context).also { layer ->
      layer.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
      // no touch falls through to the reader's page behind
      layer.isClickable = true
      layer.setContent {
        QuranTheme {
          ScrollPages(state, labels, suraName, object : ScrollPagesActions by actions {
            override fun onPage(page: Int) {
              if (isShowing) actions.onPage(page)
            }

            override fun onSpeedChanged(speed: Int) {
              state.speed = speed
              setSpeed(context, speed)
            }
          })
        }
      }
      // right above the pages, under the reader's bars
      val parent = pages.parent as ViewGroup
      parent.addView(
        layer,
        parent.indexOfChild(pages) + 1,
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      )
      view = layer
    }
    layer.isVisible = true
  }

  fun hide() {
    state.auto = false
    state.playing = false
    view?.isVisible = false
  }

  /** The text starts moving on its own from where it is; the layer must be showing. */
  fun startAuto() {
    state.speed = speed(pages().context)
    state.auto = true
    state.playing = true
  }

  /** Back to reading by hand, where the text is. */
  fun stopAuto() {
    state.auto = false
    state.playing = false
  }

  /** Stops the text where it is (the reader went to the background); a tap resumes. */
  fun pause() {
    state.playing = false
  }

  /** The reader went to [page] by other means (a jump, the recitation): the layer follows. */
  fun showPage(page: Int) {
    state.jumpTo = page
  }

  fun onPlaybackAyah(suraAyah: SuraAyah?) {
    state.recitedAyah = suraAyah?.let { ayahKey(it.sura, it.ayah) }
  }

  fun onSelection(suraAyah: SuraAyah?) {
    state.selectedAyah = suraAyah?.let { ayahKey(it.sura, it.ayah) }
  }

  fun updateSettings(nightMode: Boolean, nightBrightness: Int, paper: Int) {
    state.nightMode = nightMode
    state.nightBrightness = nightBrightness
    state.paper = paper
  }

  companion object {
    private const val PREFERENCES = "auto_scroll"
    private const val KEY_SPEED = "speed"
    private const val KEY_SCROLL_READING = "scroll_reading"

    private fun preferences(context: Context) =
      context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Whether the mushaf is read scrolling instead of turning pages; kept across launches. */
    fun isScrollReading(context: Context): Boolean =
      preferences(context).getBoolean(KEY_SCROLL_READING, false)

    fun setScrollReading(context: Context, scrollReading: Boolean) {
      preferences(context).edit { putBoolean(KEY_SCROLL_READING, scrollReading) }
    }

    /** The auto-scroll speed last chosen; kept across launches. */
    private fun speed(context: Context): Int =
      preferences(context)
        .getInt(KEY_SPEED, AUTO_SCROLL_DEFAULT_SPEED)
        .coerceIn(AUTO_SCROLL_SECONDS_PER_PAGE.indices)

    private fun setSpeed(context: Context, speed: Int) {
      preferences(context).edit { putInt(KEY_SPEED, speed) }
    }
  }
}
