package com.quran.data.source

sealed class PageContentType {
  data object Image : PageContentType()
  data class Line(val ratio: Float, val lineHeight: Int, val allowOverlapOfLines: Boolean): PageContentType()

  /** Pages drawn as text from page fonts: no page images to download. */
  data object Text : PageContentType()
}
