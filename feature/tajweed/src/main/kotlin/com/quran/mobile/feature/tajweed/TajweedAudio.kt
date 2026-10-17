package com.quran.mobile.feature.tajweed

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Recitation for the tajweed mushaf, played by the app's audio service with the reader's qari. */
interface TajweedAudio {
  /**
   * Starts reciting from [sura]:[ayah]. When the qari's audio has to be downloaded first, the
   * download starts on Wi-Fi or with [allowMobileDownload]; otherwise the user is asked first,
   * like the reader does.
   */
  fun playFrom(activity: Activity, sura: Int, ayah: Int, allowMobileDownload: Boolean): TajweedPlayResult

  fun pause(context: Context)

  fun resume(context: Context)

  fun stop(context: Context)

  /** the ayah being recited, as the reader follows it, or null when nothing is playing */
  val playback: Flow<TajweedPlayback?>

  /** emits each time a download of recitation audio finishes */
  val audioDownloaded: Flow<Unit>
}

data class TajweedPlayback(val sura: Int, val ayah: Int, val paused: Boolean)

enum class TajweedPlayResult {
  PLAYING,
  /** the audio is downloading; playing again after it finishes will work */
  DOWNLOADING,
  /** the audio must be downloaded over mobile data, which the user has to agree to */
  NEEDS_DOWNLOAD_CONSENT
}
