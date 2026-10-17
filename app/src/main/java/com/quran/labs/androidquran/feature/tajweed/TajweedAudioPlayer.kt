package com.quran.labs.androidquran.feature.tajweed

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem.Companion.fromQari
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenter
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenterScreen
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.feature.tajweed.TajweedAudio
import com.quran.mobile.feature.tajweed.TajweedPlayResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Plays the tajweed mushaf's ayat the way the reader does ([AudioPresenter], [AudioService]),
 * with the reader's qari and its download and streaming settings.
 */
@ContributesBinding(AppScope::class)
class TajweedAudioPlayer @Inject constructor(
  private val audioPresenter: AudioPresenter,
  private val qariManager: CurrentQariManager,
  private val audioUtils: AudioUtils,
  private val quranSettings: QuranSettings,
  private val quranInfo: QuranInfo
) : TajweedAudio {

  override fun playFrom(
    activity: Activity,
    sura: Int,
    ayah: Int,
    allowMobileDownload: Boolean
  ): TajweedPlayResult {
    var result = TajweedPlayResult.PLAYING
    val screen = object : AudioPresenterScreen {
      override fun handlePlayback(request: AudioRequest?) {
        val intent = Intent(activity, AudioService::class.java).setAction(AudioService.ACTION_PLAYBACK)
        request?.let { intent.putExtra(AudioService.EXTRA_PLAY_INFO, it) }
        activity.startService(intent)
      }

      override fun handleRequiredDownload(downloadIntent: Intent?) = proceedWithDownload(downloadIntent)

      override fun proceedWithDownload(downloadIntent: Intent?) {
        // like the reader, audio is only downloaded over mobile data once the user agrees
        if (allowMobileDownload || QuranUtils.isOnWifiNetwork(activity)) {
          result = TajweedPlayResult.DOWNLOADING
          downloadIntent?.let { activity.startService(it) }
        } else {
          result = TajweedPlayResult.NEEDS_DOWNLOAD_CONSENT
        }
      }

      override fun getString(resId: Int): String = activity.getString(resId)
    }

    // the presenter calls the screen synchronously from play()
    audioPresenter.bind(screen)
    try {
      val start = SuraAyah(sura, ayah)
      val page = quranInfo.getPageFromSuraAyah(sura, ayah)
      val end = audioUtils.getLastAyahToPlay(start, page, quranSettings.preferredDownloadAmount, false)
        ?: return TajweedPlayResult.PLAYING
      audioPresenter.play(
        start,
        end,
        fromQari(activity, qariManager.currentQari()),
        verseRepeat = 0,
        rangeRepeat = 0,
        enforceRange = false,
        playbackSpeed = 1.0f,
        shouldStream = quranSettings.shouldStream()
      )
    } finally {
      audioPresenter.unbind(screen)
    }
    return result
  }

  override fun stop(context: Context) {
    context.startService(audioUtils.getAudioIntent(context, AudioService.ACTION_STOP))
  }
}
