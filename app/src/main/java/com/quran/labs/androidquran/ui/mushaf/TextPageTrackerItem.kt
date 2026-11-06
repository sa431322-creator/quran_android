package com.quran.labs.androidquran.ui.mushaf

import android.content.Context
import android.view.View
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.labs.androidquran.view.TabletView
import com.quran.mobile.feature.mushaf.data.MushafEdition
import com.quran.mobile.feature.mushaf.data.MushafRepository
import com.quran.mobile.feature.mushaf.data.ayahKey
import com.quran.mobile.feature.mushaf.data.ayahKeyAyah
import com.quran.mobile.feature.mushaf.data.ayahKeySura

/**
 * The reader's view of a text page: which ayah is under a touch, where an ayah is (for the ayah
 * toolbar), and the page's highlights (selection, recitation, bookmarks, colors).
 */
class TextPageTrackerItem internal constructor(
  page: Int,
  private val layout: QuranTextPageLayout
) : AyahTrackerItem(page) {

  override fun getAyahForPosition(page: Int, x: Float, y: Float): SuraAyah? {
    if (page != this.page || x < 0f || y < 0f) return null
    val key = layout.hits.ayahAt(layout.toWindow(Offset(x, y))) ?: return null
    return SuraAyah(ayahKeySura(key), ayahKeyAyah(key))
  }

  override fun getToolBarPosition(page: Int, sura: Int, ayah: Int): SelectionIndicator {
    if (page != this.page) return SelectionIndicator.None
    val bounds = layout.hits.boundsOf(ayahKey(sura, ayah)) ?: return SelectionIndicator.None
    // like an image page: relative to the page, or to both pages on a tablet
    val reference: View = layout.parent as? TabletView ?: layout
    val location = IntArray(2)
    reference.getLocationInWindow(location)
    fun Rect.relative() = SelectionRectangle(
      left - location[0], top - location[1], right - location[0], bottom - location[1]
    )
    return SelectionIndicator.SelectedItemPosition(bounds.first.relative(), bounds.last.relative())
  }

  override fun onHighlightAyah(
    page: Int,
    sura: Int,
    ayah: Int,
    word: Int,
    type: HighlightType,
    scrollToAyah: Boolean
  ): Boolean {
    return if (page == this.page) {
      layout.highlights.add(type, ayahKey(sura, ayah))
      true
    } else {
      // a single highlight (selection, recitation) moved to another page
      if (type.isSingle) layout.highlights.clear(type)
      false
    }
  }

  override fun onHighlightAyat(page: Int, ayahKeys: Set<String>, type: HighlightType) {
    if (page == this.page) {
      layout.highlights.set(type, ayahKeys.mapNotNull { key ->
        val (sura, ayah) = key.split(':').map { it.toIntOrNull() ?: return@mapNotNull null }
        ayahKey(sura, ayah)
      }.toSet())
    }
  }

  override fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, word: Int, type: HighlightType) {
    if (page == this.page) layout.highlights.remove(type, ayahKey(sura, ayah))
  }

  override fun onUnHighlightAyahType(type: HighlightType) {
    layout.highlights.clear(type)
  }

  override fun getAyahView(): View = layout
}

/** A text page's highlighted ayat by highlight type, observed by the page's composition. */
class TextPageHighlights {
  private val byType = mutableStateMapOf<HighlightType, Set<Int>>()

  fun keysOf(type: HighlightType): Set<Int> = byType[type].orEmpty()

  fun add(type: HighlightType, key: Int) {
    byType[type] = if (type.isSingle) setOf(key) else keysOf(type) + key
  }

  fun set(type: HighlightType, keys: Set<Int>) {
    byType[type] = keys
  }

  fun remove(type: HighlightType, key: Int) {
    byType[type] = keysOf(type) - key
  }

  fun clear(type: HighlightType) {
    byType.remove(type)
  }

  /** Every highlight except the selection and the recitation, which the page draws itself. */
  fun otherColors(colorOf: (HighlightType) -> Color): Map<Int, Color> =
    byType.entries
      .filter { it.key != HighlightTypes.SELECTION && it.key != HighlightTypes.AUDIO }
      .sortedBy { it.key }
      .flatMap { (type, keys) -> keys.map { it to colorOf(type) } }
      .toMap()
}

/** The text mushaf's pages and fonts, shared by every page of the reader. */
object TextMushaf {
  @Volatile private var repository: MushafRepository? = null

  fun repository(context: Context): MushafRepository =
    repository ?: synchronized(this) {
      repository ?: MushafRepository(context.applicationContext, "", MushafEdition.PLAIN_V2)
        .also { repository = it }
    }
}
