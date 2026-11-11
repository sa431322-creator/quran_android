package com.quran.labs.androidquran.ui.mushaf

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.withSelectionIndicator
import com.quran.data.model.selection.withYScroll
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.QuranPage
import com.quran.labs.androidquran.ui.util.PageController
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject

/** One page of the text (font) mushaf in the reader, in place of an image page. */
class TextPageFragment : Fragment(), PageController, QuranPage, AyahInteractionHandler {
  private var pageNumber = 0
  private var layout: QuranTextPageLayout? = null

  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var ayahTrackerPresenter: AyahTrackerPresenter
  @Inject lateinit var readingEventPresenter: ReadingEventPresenter

  override fun onAttach(context: Context) {
    super.onAttach(context)
    pageNumber = arguments?.getInt(PAGE_NUMBER_EXTRA) ?: 1
    (activity as PagerActivity).pagerActivityComponent
      .quranPageComponentFactory()
      .generate(intArrayOf(pageNumber))
      .inject(this)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val layout = QuranTextPageLayout(requireContext(), pageNumber, quranDisplayData)
    layout.setPageController(this, pageNumber, quranInfo.skip)
    this.layout = layout
    return layout
  }

  override fun onDestroyView() {
    layout = null
    super.onDestroyView()
  }

  override fun onResume() {
    super.onResume()
    updateView()
  }

  override fun onStart() {
    super.onStart()
    ayahTrackerPresenter.bind(this)
  }

  override fun onStop() {
    ayahTrackerPresenter.unbind(this)
    super.onStop()
  }

  override fun updateView() {
    if (isAdded) layout?.updateView(quranSettings)
  }

  override fun getAyahTracker(): AyahTracker = ayahTrackerPresenter

  override fun getAyahTrackerItems(): Array<AyahTrackerItem> =
    layout?.let { arrayOf<AyahTrackerItem>(it.trackerItem) } ?: emptyArray()

  override fun handleTouchEvent(event: MotionEvent, eventType: AyahSelectedListener.EventType, page: Int): Boolean {
    return isVisible && ayahTrackerPresenter.handleTouchEvent(requireActivity(), event, eventType, page, false)
  }

  override fun handleRetryClicked() {}

  override fun onScrollChanged(y: Float) {
    val selection = readingEventPresenter.currentAyahSelection()
    if (selection !is AyahSelection.None) {
      val updated = selection.withSelectionIndicator(selection.selectionIndicator().withYScroll(-y))
      readingEventPresenter.onAyahSelection(updated)
    }
  }

  override fun handleLongPress(suraAyah: SuraAyah) {
    if (isVisible) ayahTrackerPresenter.onLongPress(suraAyah)
  }

  override fun endAyahMode() {
    if (isVisible) ayahTrackerPresenter.endAyahMode()
  }

  companion object {
    private const val PAGE_NUMBER_EXTRA = "pageNumber"

    fun newInstance(page: Int) = TextPageFragment().apply {
      arguments = Bundle().apply { putInt(PAGE_NUMBER_EXTRA, page) }
    }
  }
}
