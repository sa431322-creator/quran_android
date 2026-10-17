package com.quran.labs.androidquran.feature.tajweed

import android.app.Activity
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.ShareUtil
import com.quran.mobile.feature.tajweed.TajweedSharing
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import timber.log.Timber

/** Copies and shares a tajweed ayah with the reader's [ShareUtil] and Arabic text database. */
@ContributesBinding(AppScope::class)
class TajweedAyahSharing @Inject constructor(
  private val arabicDatabaseUtils: ArabicDatabaseUtils,
  private val shareUtil: ShareUtil,
  private val quranFileUtils: QuranFileUtils
) : TajweedSharing {

  override fun copy(activity: Activity, sura: Int, ayah: Int): Boolean =
    withVerse(sura, ayah) { shareUtil.copyVerses(activity, it) }

  override fun shareText(activity: Activity, sura: Int, ayah: Int): Boolean =
    withVerse(sura, ayah) { shareUtil.shareVerses(activity, it) }

  override fun shareLink(activity: Activity, sura: Int, ayah: Int) {
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
