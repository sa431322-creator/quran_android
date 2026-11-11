package com.quran.labs.androidquran.ui.cards

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.mushaf.data.MushafRepository
import com.quran.mobile.feature.mushaf.data.ayahKey

/**
 * The reader's ayah cards: a layer over the reader's pages, under its bars, that the reader shows
 * from its «ترجمه و تفسیر» tab and hides for the mushaf and tajweed tabs. The reader keeps the
 * pages behind it on the card at the top, so leaving the cards lands on the same ayah.
 */
class ReaderAyahCards(
  /** the reader's pages, looked up when the cards first show (the reader sets them up late) */
  private val pages: () -> View,
  private val suraName: (Int) -> String,
  private val actions: AyahCardsActions
) {
  val state = AyahCardsState()
  private var view: ComposeView? = null

  val isShowing: Boolean get() = view?.isVisible == true

  /**
   * Shows the cards at [start], drawn with [repository]'s fonts, between bars [topInset] and
   * [bottomInset] px high.
   */
  fun show(start: SuraAyah, repository: MushafRepository, topInset: Int, bottomInset: Int) {
    val pages = pages()
    val context = pages.context
    state.repository = repository
    state.topInset = topInset
    state.bottomInset = bottomInset
    state.showAll = showAll(context)
    state.scrollTo = ScrollRequest(ayahKey(start.sura, start.ayah))
    val cards = view ?: ComposeView(context).also { cards ->
      cards.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
      cards.setContent {
        QuranTheme {
          AyahCards(state, suraName, object : AyahCardsActions by actions {
            override fun onShowAllChanged(showAll: Boolean) {
              setShowAll(context, showAll)
              actions.onShowAllChanged(showAll)
            }
          })
        }
      }
      // right above the pages, under the reader's bars and toolbar
      val parent = pages.parent as ViewGroup
      parent.addView(
        cards,
        parent.indexOfChild(pages) + 1,
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      )
      view = cards
    }
    cards.isVisible = true
  }

  fun hide() {
    view?.isVisible = false
  }

  /** The recited ayah: tinted, and brought on screen when the cards show. */
  fun onPlaybackAyah(suraAyah: SuraAyah?) {
    val key = suraAyah?.let { ayahKey(it.sura, it.ayah) }
    state.playingAyah = key
    if (key != null && isShowing) state.scrollTo = ScrollRequest(key, onlyIfHidden = true)
  }

  fun onSelection(suraAyah: SuraAyah?) {
    state.selectedAyah = suraAyah?.let { ayahKey(it.sura, it.ayah) }
  }

  fun updateSettings(nightMode: Boolean, nightBrightness: Int, textSize: Int, columns: Int) {
    state.nightMode = nightMode
    state.nightBrightness = nightBrightness
    state.textSize = textSize
    state.columns = columns
  }

  companion object {
    private const val PREFERENCES = "ayah_cards"
    private const val KEY_SHOW_ALL = "show_text"

    /** Whether the cards show the Persian text; kept across launches. */
    fun showAll(context: Context): Boolean =
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_ALL, true)

    private fun setShowAll(context: Context, showAll: Boolean) {
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putBoolean(KEY_SHOW_ALL, showAll) }
    }
  }
}
