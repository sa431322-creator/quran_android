package com.quran.data.page.provider.madani

import com.quran.data.source.MushafType
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

  /**
   * The mushaf mobile-sync files this page type's page reading bookmarks under. Kept the classic
   * one on purpose: Tasnim stores and reads its page bookmarks under this one label, so they stay
   * where they are. It only names the print to quran.com's sync, which Tasnim does not use; set
   * the V2 print's own mushaf (and move the stored pages) before turning that sync on.
   */
  override fun mushafType() = MushafType.MADANI_1405

  override fun getPreviewTitle() = R.string.madani_v2_title

  override fun getPreviewDescription() = R.string.madani_v2_description

  override fun isSelectable() = true

  companion object {
    const val PAGE_TYPE = "madani_v2"
    private val dataSource by lazy { MadaniV2DataSource() }
  }
}
