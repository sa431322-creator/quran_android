package com.quran.common.networking

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The Tasnim server keeps a copy of the Mushaf files (page images, patches, ayah info, the
 * Arabic search database, translations, page-type previews and the V4 tajweed fonts) under
 * `data/...`. This maps an upstream file link to its copy there, or null when there is none.
 */
object MirrorFallback {
  private const val QURAN_ANDROID_HOST = "android.quran.com"
  private const val QURAN_FILES_HOST = "files.quran.app"
  private const val QURAN_APP_HOST = "quran.app"
  private const val TARTEEL_CDN_HOST = "static-cdn.tarteel.ai"

  private const val QURAN_ANDROID_PREFIX = "/data/"
  private const val QURAN_FILES_PREFIX = "/hafs/madani/"
  private const val PAGE_TYPE_PREFIX = "/data/pagetypes/snips/"
  private const val AUDIO_DATABASES = "data/databases/audio/"
  private const val TAJWEED_FONT_PREFIX = "/qul/fonts/quran_fonts/v4-tajweed/ttf/"

  fun fallbackUrl(url: HttpUrl, tasnimBase: String): HttpUrl? {
    val base = normalisedBase(tasnimBase) ?: return null
    val path = url.encodedPath
    val mirrorPath = when (url.host) {
      QURAN_ANDROID_HOST -> path.after(QURAN_ANDROID_PREFIX)?.let { "data/$it" }
      QURAN_FILES_HOST -> path.after(QURAN_FILES_PREFIX)?.let { "data/$it" }
      QURAN_APP_HOST -> path.after(PAGE_TYPE_PREFIX)?.let { "data/pagetypes/snips/$it" }
      TARTEEL_CDN_HOST -> path.after(TAJWEED_FONT_PREFIX)?.let { "data/qul/v4-tajweed/ttf/$it" }
      else -> null
    } ?: return null
    // audio timing databases are not backed up yet (audio is a later phase)
    if (mirrorPath.startsWith(AUDIO_DATABASES)) return null

    val query = url.encodedQuery?.let { "?$it" }.orEmpty()
    val server = base.toHttpUrlOrNull() ?: return null
    return ReachableUrl.of("$base$mirrorPath$query", server).toHttpUrlOrNull()
  }

  /** The base url ending with "/", or null when there is no Tasnim server. */
  fun normalisedBase(tasnimBase: String): String? {
    val base = tasnimBase.trim().ifBlank { return null }
    return if (base.endsWith("/")) base else "$base/"
  }

  private fun String.after(prefix: String): String? =
    if (startsWith(prefix) && length > prefix.length) substring(prefix.length) else null
}
