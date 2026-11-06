package com.quran.data.page.provider.madani

import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.pages.data.madaniv2.MadaniV2DataSource
import com.quran.labs.androidquran.pages.madani.R

/**
 * The King Fahd Madinah mushaf V2 (QPC V2, 1421H print), drawn as text from its page fonts.
 * Audio, databases and downloads are the classic Madani page type's; only the pages differ.
 */
class MadaniV2PageProvider(
  private val classic: PageProvider = MadaniPageProvider()
) : PageProvider by classic {

  override fun getDataSource() = dataSource

  override fun getPageContentType(): PageContentType = PageContentType.Text

  override fun pageType() = PAGE_TYPE

  override fun getPreviewTitle() = R.string.madani_v2_title

  override fun getPreviewDescription() = R.string.madani_v2_description

  companion object {
    const val PAGE_TYPE = "madani_v2"
    private val dataSource by lazy { MadaniV2DataSource() }
  }
}
