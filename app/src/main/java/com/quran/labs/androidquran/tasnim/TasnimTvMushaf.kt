package com.quran.labs.androidquran.tasnim

import android.content.Context
import androidx.activity.OnBackPressedCallback
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import com.quran.labs.androidquran.view.SlidingTabLayout
import com.quran.mobile.feature.livetv.radio.LiveRadioService

/**
 * The mushaf on TV (docs/tv/PLAN.md, phase 4): the Tasnim side of the small hooks in the
 * upstream mushaf screens, so syncs with upstream stay plain merges.
 */
object TasnimTvMushaf {

  /**
   * Shares the tab row's width between its tabs. [SlidingTabLayout] sizes them from the whole
   * screen's width, which on TV (where the list sits beside a side column) pushes all but the
   * first tab out of view.
   */
  fun fitTabsToWidth(indicator: SlidingTabLayout) {
    indicator.doOnLayout {
      val strip = indicator.getChildAt(0) as? ViewGroup ?: return@doOnLayout
      val count = strip.childCount
      if (count == 0 || indicator.width == 0) return@doOnLayout
      val width = indicator.width / count
      for (i in 0 until count) {
        val tab = strip.getChildAt(i)
        tab.layoutParams = tab.layoutParams.apply { this.width = width }
      }
      indicator.scrollTo(0, 0)
    }
  }

  /**
   * The remote in the mushaf reader. Left/right already turn pages (the pager handles the
   * arrows, right to left). This adds:
   * - center / enter / menu on the page: show or hide the bars (sura jump, audio, settings);
   * - play / play-pause with nothing playing: start the recitation from this page. Android
   *   sends media keys to the last media session that played, when there is one, so this runs
   *   only before any: after a recitation the keys reach AudioService's session (pause/resume),
   *   and a paused radio's session is stopped on entering the reader ([onReaderResume]). A first
   *   recitation can always start from the audio bar's play button, reachable from the bars.
   *
   * Returns true when the key was handled.
   */
  fun onReaderKeyDown(
    keyCode: Int,
    event: KeyEvent,
    focused: View?,
    pager: View,
    hasAudio: Boolean,
    toggleBars: () -> Unit,
    play: () -> Unit
  ): Boolean {
    if (event.repeatCount > 0) return false
    return when (keyCode) {
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
      KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU -> {
        // only on the page itself: on a bar's button center presses that button
        if (focused == null || focused === pager || keyCode == KeyEvent.KEYCODE_MENU) {
          toggleBars()
          true
        } else {
          false
        }
      }
      KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
        if (hasAudio) false else { play(); true }
      }
      else -> false
    }
  }

  /**
   * The page fills the screen, so the remote can't move up from it into the bars: showing them
   * puts the focus on their first button, hiding them gives it back to the page.
   */
  fun focusBars(visible: Boolean, bars: View, pager: View) {
    if (visible) bars.post { bars.requestFocus() } else pager.requestFocus()
  }

  /** With the bars hidden (or nothing focused), the remote's focus belongs on the page. */
  fun keepFocus(barsHidden: Boolean, focused: View?, pager: View) {
    if (focused == null || (barsHidden && focused !== pager)) pager.requestFocus()
  }

  /** The reader came to the front: a paused radio lets go of the remote's media keys. */
  fun onReaderResume(context: Context) {
    LiveRadioService.stopIfPaused(context)
  }

  /**
   * Back hides the bars first on TV (they don't hide by themselves while the remote is in
   * them), then leaves. Enabled only while the bars show: see [onBarsShown].
   */
  fun barsBackCallback(hideBars: () -> Unit) = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() = hideBars()
  }

  fun onBarsShown(callback: OnBackPressedCallback, shown: Boolean) {
    callback.isEnabled = shown
  }

  /** The bars' auto-hide waits while the remote is inside them. */
  fun mayAutoHide(focused: View?, pager: View): Boolean = focused == null || focused === pager
}
