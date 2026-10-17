package com.quran.mobile.feature.tajweed

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens the tajweed mushaf at a sura and ayah and returns the sura and ayah of the page it was
 * left at, or null when it was left on the page it opened at (or before its pages loaded).
 */
class TajweedMushafContract : ActivityResultContract<Pair<Int, Int>, Pair<Int, Int>?>() {

  override fun createIntent(context: Context, input: Pair<Int, Int>): Intent =
    Intent(context, TajweedMushafActivity::class.java)
      .putExtra(TajweedMushafActivity.EXTRA_SURA, input.first)
      .putExtra(TajweedMushafActivity.EXTRA_AYAH, input.second)

  override fun parseResult(resultCode: Int, intent: Intent?): Pair<Int, Int>? {
    if (resultCode != Activity.RESULT_OK || intent == null) return null
    val sura = intent.getIntExtra(TajweedMushafActivity.EXTRA_SURA, 0)
    val ayah = intent.getIntExtra(TajweedMushafActivity.EXTRA_AYAH, 0)
    return if (sura > 0 && ayah > 0) sura to ayah else null
  }
}
