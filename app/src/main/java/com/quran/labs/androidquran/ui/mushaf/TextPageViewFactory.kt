package com.quran.labs.androidquran.ui.mushaf

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import com.quran.data.page.provider.madani.MadaniV2PageProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.page.common.data.PageMode
import com.quran.page.common.factory.PageViewFactory
import com.quran.page.common.factory.PageViewFactoryProvider
import dev.zacsweers.metro.Inject

/** Text pages for the Madinah mushaf V2 page type; every other page type keeps its images. */
@Inject
class TextPageViewFactoryProvider(
  private val quranDisplayData: QuranDisplayData
) : PageViewFactoryProvider {
  override fun providePageViewFactory(pageType: String): PageViewFactory? =
    if (pageType == MadaniV2PageProvider.PAGE_TYPE) TextPageViewFactory(quranDisplayData) else null
}

/**
 * The Arabic pages of the reader as text: a [TextPageFragment] for a single page, and a
 * [QuranTextPageLayout] for each of a tablet's two pages. Translation pages stay as they are.
 */
class TextPageViewFactory(private val quranDisplayData: QuranDisplayData) : PageViewFactory {
  override fun providePage(pageNumber: Int, pageMode: PageMode): Fragment? =
    if (pageMode == PageMode.SingleArabicPage) TextPageFragment.newInstance(pageNumber) else null

  override fun providePageView(context: Context, pageNumber: Int, pageMode: PageMode): View? =
    QuranTextPageLayout(context, pageNumber, quranDisplayData)
}
