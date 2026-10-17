package com.quran.labs.androidquran.feature.tajweed

import android.app.Activity
import com.quran.data.model.SuraAyah
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.ShareUtil
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import timber.log.Timber

/**
 * Copies and shares an ayah from the tajweed mushaf the way the reader does, with its [ShareUtil]
 * and Arabic text database. Copy and text sharing return false when that text is not downloaded.
 */
class TajweedAyahSharing @Inject constructor(
  private val arabicDatabaseUtils: ArabicDatabaseUtils,
  private val shareUtil: ShareUtil,
  private val quranFileUtils: QuranFileUtils
) {

  fun copy(activity: Activity, sura: Int, ayah: Int): Boolean =
    withVerse(sura, ayah) { shareUtil.copyVerses(activity, it) }

  fun shareText(activity: Activity, sura: Int, ayah: Int): Boolean =
    withVerse(sura, ayah) { shareUtil.shareVerses(activity, it) }

  fun shareLink(activity: Activity, sura: Int, ayah: Int) {
    val suraAyah = SuraAyah(sura, ayah)
    shareUtil.shareAyahLink(activity, suraAyah, suraAyah)
  }

  @Suppress("CheckResult") // a single short read; it finishes on its own
  private fun withVerse(sura: Int, ayah: Int, action: (List<QuranText>) -> Unit): Boolean {
    // the reader asks for the Arabic text database to be downloaded in this case
    if (!quranFileUtils.hasArabicSearchDatabase()) return false
    val suraAyah = SuraAyah(sura, ayah)
    arabicDatabaseUtils.getVerses(suraAyah, suraAyah)
      .filter { it.isNotEmpty() }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ action(it) }, { Timber.e(it, "unable to read the ayah text") })
    return true
  }
}
