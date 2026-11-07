package com.quran.labs.androidquran.ui.mushaf

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.quran.labs.androidquran.BuildConfig
import com.quran.mobile.feature.mushaf.data.MushafEdition
import com.quran.mobile.feature.mushaf.data.MushafRepository

/**
 * The text mushaf's pages and fonts, shared by every page of the reader, and how its pages are
 * drawn: the plain Madinah mushaf (QPC V2) or the tajweed mushaf (QPC V4, colored). Both hold the
 * same ayahs on each of the 604 pages, so switching keeps the reader on its page; the pages read
 * [tajweed] and redraw themselves when it changes.
 */
object TextMushaf {
  private const val PREFERENCES = "text_mushaf"
  private const val KEY_TAJWEED = "tajweed"

  private val repositories = mutableMapOf<MushafEdition, MushafRepository>()
  private var loaded = false
  private var tajweedState by mutableStateOf(false)

  /** Whether the reader's text pages are drawn as the tajweed mushaf; kept across launches. */
  fun isTajweed(context: Context): Boolean {
    load(context)
    return tajweedState
  }

  fun setTajweed(context: Context, tajweed: Boolean) {
    load(context)
    if (tajweedState == tajweed) return
    tajweedState = tajweed
    preferences(context).edit { putBoolean(KEY_TAJWEED, tajweed) }
  }

  /** The repository for the current drawing; read in composition, it follows [setTajweed]. */
  fun repository(context: Context): MushafRepository =
    repository(context, if (isTajweed(context)) MushafEdition.TAJWEED_V4 else MushafEdition.PLAIN_V2)

  fun repository(context: Context, edition: MushafEdition): MushafRepository = synchronized(this) {
    repositories.getOrPut(edition) {
      // the tajweed fonts have a copy on the Tasnim server, used when QUL cannot be reached
      MushafRepository(context.applicationContext, BuildConfig.TASNIM_API_BASE_URL, edition)
    }
  }

  private fun load(context: Context) {
    if (loaded) return
    tajweedState = preferences(context).getBoolean(KEY_TAJWEED, false)
    loaded = true
  }

  private fun preferences(context: Context) =
    context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
