package com.quran.mobile.feature.tajweed

import android.app.Activity

/** Copying and sharing an ayah from the tajweed mushaf, the way the reader does. */
interface TajweedSharing {
  /** Copies the ayah's Arabic text; false when that text is not downloaded yet. */
  fun copy(activity: Activity, sura: Int, ayah: Int): Boolean

  /** Shares the ayah's Arabic text; false when that text is not downloaded yet. */
  fun shareText(activity: Activity, sura: Int, ayah: Int): Boolean

  fun shareLink(activity: Activity, sura: Int, ayah: Int)
}
