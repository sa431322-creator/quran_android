package com.quran.mobile.feature.livetv.data

/**
 * The stream addresses set in the admin panel (البث المباشر / الراديو pages); null means not set,
 * and that channel's button is hidden.
 */
data class StreamLinks(
  val ownLive: String? = null,
  val quranLive: String? = null,
  val ownRadio: String? = null,
  val quranRadio: String? = null
)

fun interface StreamLinksProvider {
  /** The last links read from the server, or null in a build without a server. */
  fun links(): StreamLinks?
}
