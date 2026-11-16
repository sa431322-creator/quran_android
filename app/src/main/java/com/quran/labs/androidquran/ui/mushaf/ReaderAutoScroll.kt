package com.quran.labs.androidquran.ui.mushaf

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.mushaf.ui.MushafPageLabels

/**
 * The reader's auto-scroll («پیمایش خودکار»): a layer over the reader's text pages on which the
 * mushaf moves up on its own, plain or tajweed as the reader's pages are. The reader is kept on
 * the page at the middle of the screen, so leaving the layer lands on that page.
 */
class ReaderAutoScroll(
  /** the reader's pages, looked up when the layer first shows (the reader sets them up late) */
  private val pages: () -> View,
  private val labels: (Int) -> MushafPageLabels,
  private val suraName: (Int) -> String,
  /** the page on screen changed: the reader follows */
  private val onPage: (Int) -> Unit,
  /** a tap on the text (it stops or resumes) */
  private val onTouch: () -> Unit,
  private val onClose: () -> Unit
) {
  val state = AutoScrollState()
  private var view: ComposeView? = null

  val isShowing: Boolean get() = view?.isVisible == true

  /** Starts scrolling from the top of [page]. */
  fun show(page: Int) {
    val pages = pages()
    val context = pages.context
    state.startPage = page
    state.currentPage = page
    state.speed = speed(context)
    state.playing = true
    state.session++
    val layer = view ?: ComposeView(context).also { layer ->
      layer.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
      layer.setContent {
        QuranTheme {
          AutoScrollPages(
            state = state,
            labels = labels,
            suraName = suraName,
            onPage = { if (isShowing) onPage(it) },
            onTouch = onTouch,
            onSpeedChanged = { speed ->
              state.speed = speed
              setSpeed(context, speed)
            },
            onClose = onClose
          )
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
    state.playing = false
    view?.isVisible = false
  }

  /** Stops the text where it is (the reader went to the background); a tap resumes. */
  fun pause() {
    state.playing = false
  }

  fun updateSettings(nightMode: Boolean, nightBrightness: Int, paper: Int) {
    state.nightMode = nightMode
    state.nightBrightness = nightBrightness
    state.paper = paper
  }

  companion object {
    private const val PREFERENCES = "auto_scroll"
    private const val KEY_SPEED = "speed"

    /** The speed last chosen; kept across launches. */
    private fun speed(context: Context): Int =
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getInt(KEY_SPEED, AUTO_SCROLL_DEFAULT_SPEED)
        .coerceIn(AUTO_SCROLL_SECONDS_PER_PAGE.indices)

    private fun setSpeed(context: Context, speed: Int) {
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putInt(KEY_SPEED, speed) }
    }
  }
}
