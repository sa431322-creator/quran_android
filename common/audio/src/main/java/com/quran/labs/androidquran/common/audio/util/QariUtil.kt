package com.quran.labs.androidquran.common.audio.util

import android.content.Context
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.audio.model.QariItem
import dev.zacsweers.metro.Inject

class QariUtil @Inject constructor(
  private val pageProvider: PageProvider,
  private val serverQaris: ServerQariSource,
) {

  /**
   * Get a list of all available qaris as [Qari]: the Tasnim server's list when the app has one,
   * otherwise the built-in list of the page type.
   *
   * Unlike the method with the context parameter, this version does not resolve names; use
   * `displayName(context)` (server qaris carry [Qari.name], built-in ones a resource id).
   */
  fun getQariList(): List<Qari> {
    return serverQaris.cached()?.takeIf { it.isNotEmpty() } ?: pageProvider.getQaris()
  }

  /**
   * Get the default qari id when no qari is selected.
    */
  fun getDefaultQariId(): Int {
    return pageProvider.getDefaultQariId()
  }

  /**
   * Get a list of all available qaris as [QariItem]s
   *
   * @param context the current context
   * @return a list of [QariItem] representing the qaris to show.
   */
  fun getQariList(context: Context): List<QariItem> {
    return getQariList().map { item -> QariItem.fromQari(context, item) }
  }
}
