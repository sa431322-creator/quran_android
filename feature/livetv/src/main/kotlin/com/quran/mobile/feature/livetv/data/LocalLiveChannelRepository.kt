package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * The placeholder channel from the design, backed by the clip bundled in assets. The
 * [bracketed] names and the schedule are placeholders for the real ones. When the streaming
 * backend exists, replace this with a repository returning [LiveStreamSource.Remote];
 * nothing else changes.
 */
@ContributesBinding(AppScope::class)
@Inject
class LocalLiveChannelRepository : LiveChannelRepository {

  override fun channel(): LiveChannel = CHANNEL

  companion object {
    const val SAMPLE_ASSET = "second_stream.mp4"

    private val CHANNEL = LiveChannel(
      id = "channel-main",
      name = "[نام شبکه]",
      source = LiveStreamSource.LocalAsset(SAMPLE_ASSET),
      schedule = listOf(
        ScheduleEntry(6 * 60, "تلاوت صبحگاهی"),
        ScheduleEntry(8 * 60 + 30, "[نام برنامه]"),
        ScheduleEntry(12 * 60 + 15, "نماز جماعت ظهر"),
        ScheduleEntry(15 * 60, "تفسیر قرآن به فارسی"),
        ScheduleEntry(18 * 60 + 45, "[نام برنامه]"),
        ScheduleEntry(21 * 60, "دعا و مناجات")
      )
    )
  }
}
