package com.quran.mobile.feature.ads

import android.content.Context
import android.content.Intent

/**
 * What ads-house needs from the app: the Tasnim server address and its `ads` settings. The app
 * binds it (it already keeps the server's settings); ads-noop never asks for it.
 */
interface AdsServer {
  /** Blank when this build talks to no server: then there are no ads at all. */
  val baseUrl: String

  /** The `ads` settings last received from the server, or [AdsConfig.OFF]. Instant. */
  fun cachedConfig(): AdsConfig

  /** Asks the server again (at most every ~15 minutes); returns the cached copy on any failure. */
  suspend fun refreshConfig(): AdsConfig

  /** Where «گزارش تبلیغ» (report this ad) goes: the server's email and/or Telegram links. */
  fun reportLinks(): List<String>

  /** Opens a book of our library for a `tasneem://library/book/{id}` ad link (inside the app). */
  fun bookIntent(context: Context, bookId: String): Intent?
}
