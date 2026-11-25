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
import com.quran.labs.androidquran.view.TasnimFramedLayout
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
  // the cards in Tasnim's ornamental frame, fixed on the screen
  private var view: TasnimFramedLayout? = null

  val isShowing: Boolean get() = view?.isVisible == true

  /** The cards while they show, where the TV remote's focus belongs (TasnimTvMushaf). */
  val focusView: View? get() = view?.takeIf { it.isVisible }

  /**
   * Shows the cards at [start], drawn with [repository]'s fonts, between bars [topInset] and
   * [bottomInset] px high.
   */
  fun show(start: SuraAyah, repository: MushafRepository, topInset: Int, bottomInset: Int) {
    val pages = pages()
    val context = pages.context
    state.repository = repository
    state.showAll = showAll(context)
    state.scrollTo = ScrollRequest(ayahKey(start.sura, start.ayah))
    val cards = view ?: ComposeView(context).let { cards ->
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
      val framed = TasnimFramedLayout(context, cards)
      framed.update(state.nightMode, state.paper)
      // right above the pages, under the reader's bars and toolbar
      val parent = pages.parent as ViewGroup
      parent.addView(
        framed,
        parent.indexOfChild(pages) + 1,
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      )
      view = framed
      framed
    }
    // the bars cover the frame's header and footer first: the cards step aside only for the rest
    state.topInset = maxOf(0, topInset - cards.contentTop())
    state.bottomInset = maxOf(0, bottomInset - cards.contentBottomInset())
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

  fun updateSettings(
    nightMode: Boolean,
    nightBrightness: Int,
    paper: Int,
    textSize: Int,
    columns: Int,
    ayahTextSize: Int = textSize
  ) {
    state.ayahTextSize = ayahTextSize
    state.nightMode = nightMode
    state.nightBrightness = nightBrightness
    state.paper = paper
    state.textSize = textSize
    state.columns = columns
    view?.update(nightMode, paper)
  }

  /** The frame's sura, juz and page number: those of the card at the top. */
  fun setFrameLabels(sura: String, juz: String, page: String) {
    view?.setLabels(sura, juz, page)
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
