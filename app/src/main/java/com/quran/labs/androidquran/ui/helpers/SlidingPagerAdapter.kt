package com.quran.labs.androidquran.ui.helpers

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.quran.labs.androidquran.ui.fragment.AyahPlaybackFragment
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment
import com.quran.labs.androidquran.view.IconPageIndicator
import com.quran.mobile.di.AyahActionFragmentProvider

class SlidingPagerAdapter(
  fm: FragmentManager,
  private val isRtl: Boolean,
  additionalPanels: Set<AyahActionFragmentProvider>,
) : FragmentStatePagerAdapter(fm, "sliding_without_tag"), IconPageIndicator.IconPagerAdapter {

  private val pages: ArrayList<AyahActionFragmentProvider> = arrayListOf()

  init {
    // Add the core ayah action panels
    pages.add(AyahTranslationFragment.Provider)
    pages.add(AyahPlaybackFragment.Provider)

    // Since additionalPanel Set may be unsorted, put them in a list and sort them by page number..
    val additionalPages: ArrayList<AyahActionFragmentProvider> = ArrayList(additionalPanels)
    additionalPages.sortWith { o1, o2 -> o1.order.compareTo(o2.order) }
    // ..then add them to the pages list
    pages.addAll(additionalPages)
  }

  override fun getIconResId(index: Int): Int {
    val pos = getPagePosition(index)
    return pages[pos].iconResId
  }

  override fun getCount(): Int = pages.size

  override fun getItem(position: Int): Fragment {
    val pos = getPagePosition(position)
    return pages[pos].newAyahActionFragment()
  }

  fun getPagePosition(page: Int): Int {
    return if (isRtl) pages.size - 1 - page else page
  }

  /**
   * Returns the pager position of the panel with the given [AyahActionFragmentProvider.order],
   * or -1 if no such panel is present. Unlike [getPagePosition], this does not assume that a
   * panel's order matches its index, which is not true once optional panels are added.
   */
  fun getPagePositionForOrder(order: Int): Int {
    val index = pages.indexOfFirst { it.order == order }
    return if (index < 0) -1 else getPagePosition(index)
  }

  companion object {
    const val TRANSLATION_PAGE = 0
    const val AUDIO_PAGE = 1
    const val TRANSCRIPT_PAGE = 2
    const val PERSIAN_TAFSIR_PAGE = 3
  }
}
