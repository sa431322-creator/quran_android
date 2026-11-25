package com.quran.labs.androidquran.tasnim

import android.content.Context
import androidx.activity.OnBackPressedCallback
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import com.quran.labs.androidquran.R
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
   * - center / enter on the page: show or hide the bars (sura jump, audio, settings);
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
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        // only on the page itself: on a bar's button center presses that button
        if (focused == null || focused === pager) {
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
   * The remote's menu key opens the mushaf menu, which on a landscape screen has no frame
   * button. AppCompat takes this key for the bar's overflow before onKeyDown, so this runs from
   * dispatchKeyEvent. Returns true when the key was taken.
   */
  fun interceptMenuKey(event: KeyEvent, overlay: View, openMenu: () -> Unit): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_MENU) return false
    // a panel already open (the menu itself, a sheet) is closed with back, not replaced
    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled && overlay.visibility != View.VISIBLE) {
      openMenu()
    }
    return true
  }

  /**
   * The remote over the scrolling (vertical) pages, which have nothing to focus: with the focus on
   * the pages, up/down move the text and left/right go to the next or previous page (left is
   * next, as a Persian book turns). Returns true when the key was taken.
   */
  fun onScrollPagesKey(
    keyCode: Int,
    focused: View?,
    pager: View,
    scroll: (direction: Int) -> Unit,
    turn: (delta: Int) -> Unit
  ): Boolean {
    if (focused != null && focused !== pager) return false
    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> scroll(-1)
      KeyEvent.KEYCODE_DPAD_DOWN -> scroll(1)
      KeyEvent.KEYCODE_DPAD_LEFT -> turn(1)
      KeyEvent.KEYCODE_DPAD_RIGHT -> turn(-1)
      else -> return false
    }
    return true
  }

  /**
   * The page fills the screen, so the remote can't move up from it into the bars: showing them
   * puts the focus on their first button, hiding them gives it back to what is being read.
   */
  fun focusBars(visible: Boolean, bars: View, surface: View) {
    if (visible) bars.post { bars.requestFocus() } else surface.requestFocus()
  }

  /**
   * With the bars hidden (or nothing focused), the remote's focus belongs on what is being read
   * ([surface]: the pages, or the translation cards or scrolling pages over them, whose buttons
   * the remote moves through); not while a panel is open over it ([overlay]: the mushaf menu, a
   * bookmark or page theme sheet), which hides the bars itself and keeps the focus.
   */
  fun keepFocus(barsHidden: Boolean, focused: View?, surface: View, overlay: View, pager: View) {
    if (overlay.visibility == View.VISIBLE) return
    val onHiddenPages = focused === pager && surface !== pager
    if (focused == null || onHiddenPages || (barsHidden && !surface.holds(focused))) {
      // a view shown this moment is laid out (and has focusable children) only after this frame
      surface.post { surface.requestFocus() }
    }
  }

  /**
   * An arrow key on the pages while the translation cards or scrolling pages cover them: the
   * remote goes into those instead (the pages behind would turn unseen).
   */
  fun moveIntoSurface(keyCode: Int, focused: View?, surface: View, pager: View): Boolean {
    if (surface === pager || focused !== pager) return false
    if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN &&
      keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
    ) {
      return false
    }
    surface.requestFocus()
    return true
  }

  /**
   * The reader bar on TV: the mushaf menu gets a button there, since its page frame button is
   * only on portrait screens and a TV is always landscape; turning the screen means nothing on
   * a TV, so that button goes.
   */
  fun prepareReaderMenu(context: Context, menu: Menu) {
    if (menu.findItem(R.id.tasnim_tv_mushaf_menu) == null) {
      menu.add(Menu.NONE, R.id.tasnim_tv_mushaf_menu, Menu.NONE, R.string.tasnim_menu_title)
        .setIcon(R.drawable.ic_tasnim_menu)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }
    menu.findItem(R.id.rotate_screen)?.isVisible = false
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
  fun mayAutoHide(focused: View?, surface: View): Boolean = focused == null || surface.holds(focused)

  /** Whether [view] is this view or inside it. */
  private fun View.holds(view: View): Boolean {
    var current: View? = view
    while (current != null) {
      if (current === this) return true
      current = current.parent as? View
    }
    return false
  }
}
