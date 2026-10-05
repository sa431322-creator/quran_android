package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * MVP channel backed by the clip bundled in assets. When the streaming backend exists,
 * replace this with a repository returning [LiveStreamSource.Remote]; nothing else changes.
 */
@ContributesBinding(AppScope::class)
@Inject
class LocalLiveChannelRepository : LiveChannelRepository {

  override fun currentChannel(): LiveChannel = LiveChannel(
    id = "mvp-local-preview",
    title = "MVP Local Preview",
    source = LiveStreamSource.LocalAsset(SAMPLE_ASSET),
    guide = listOf(
      ProgramGuideEntry("۰۶:۰۰", "تلاوت صبحگاهی"),
      ProgramGuideEntry("۰۸:۰۰", "تفسیر سورهٔ بقره"),
      ProgramGuideEntry("۱۰:۰۰", "محفل انس با قرآن", isOnAir = true),
      ProgramGuideEntry("۱۲:۰۰", "اذان ظهر و تلاوت"),
      ProgramGuideEntry("۱۴:۰۰", "آموزش تجوید"),
      ProgramGuideEntry("۱۶:۰۰", "داستان‌های قرآنی"),
      ProgramGuideEntry("۱۸:۰۰", "ترتیل جزء روز"),
      ProgramGuideEntry("۲۰:۰۰", "گفت‌وگوی قرآنی"),
      ProgramGuideEntry("۲۲:۰۰", "تلاوت شبانه")
    )
  )

  companion object {
    const val SAMPLE_ASSET = "sample_stream.mp4"
  }
}
