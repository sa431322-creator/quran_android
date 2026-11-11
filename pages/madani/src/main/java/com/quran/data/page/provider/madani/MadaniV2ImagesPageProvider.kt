package com.quran.data.page.provider.madani

import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.pages.data.madaniv2.MadaniV2DataSource
import com.quran.labs.androidquran.pages.madani.R

/**
 * The Madinah mushaf V2 as page images, rendered from the same page fonts and layout as the text
 * pages (tools/mushaf/images), with their ayah highlighting database. Downloaded from the Tasnim
 * server ([serverBaseUrl]/data/madani_v2/); kept apart from the classic images on the device.
 */
class MadaniV2ImagesPageProvider(
  private val serverBaseUrl: String,
  private val classic: PageProvider = MadaniPageProvider()
) : PageProvider by classic {
  private val baseUrl = "${serverBaseUrl.trim().removeSuffix("/")}/data/$PAGE_TYPE_DIRECTORY"

  override fun getDataSource() = dataSource

  override fun getPageContentType(): PageContentType = PageContentType.Image

  override fun pageType() = PAGE_TYPE

  override fun getImageVersion() = 1

  override fun getImagesBaseUrl() = "$baseUrl/"

  override fun getImagesZipBaseUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getImagesDirectoryName() = PAGE_TYPE_DIRECTORY

  override fun getAyahInfoDirectoryName() = PAGE_TYPE_DIRECTORY

  override fun getPreviewTitle() = R.string.madani_v2_images_title

  override fun getPreviewDescription() = R.string.madani_v2_images_description

  /** only with a server to download the images from */
  override fun isSelectable() = serverBaseUrl.isNotBlank()

  companion object {
    const val PAGE_TYPE = "madani_v2_images"
    private const val PAGE_TYPE_DIRECTORY = "madani_v2"
    private val dataSource by lazy { MadaniV2DataSource() }
  }
}
