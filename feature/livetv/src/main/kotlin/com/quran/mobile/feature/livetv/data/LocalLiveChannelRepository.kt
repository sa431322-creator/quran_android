package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Placeholder channels from the design, all backed by the clip bundled in assets. The
 * [bracketed] names are placeholders for the real channel names. When the streaming
 * backend exists, replace this with a repository returning [LiveStreamSource.Remote];
 * nothing else changes.
 */
@ContributesBinding(AppScope::class)
@Inject
class LocalLiveChannelRepository : LiveChannelRepository {

  override fun channels(): List<LiveChannel> = listOf(
    LiveChannel(
      id = "channel-main",
      name = "[نام شبکه]",
      description = "تلاوت و نماز جماعت به‌صورت زنده",
      currentProgram = "[عنوان برنامهٔ در حال پخش]",
      source = sample,
      schedule = schedule
    ),
    LiveChannel(
      id = "channel-shrine",
      name = "حرم — [نام حرم]",
      description = "تصویر زنده از صحن و مراسم",
      currentProgram = "پخش زندهٔ مراسم از [نام حرم]",
      source = sample,
      schedule = schedule
    ),
    LiveChannel(
      id = "channel-persian",
      name = "قرآن به فارسی",
      description = "تفسیر و ترجمهٔ تصویری",
      currentProgram = "تفسیر قرآن به زبان فارسی",
      source = sample,
      schedule = schedule
    )
  )

  companion object {
    const val SAMPLE_ASSET = "second_stream.mp4"

    private val sample = LiveStreamSource.LocalAsset(SAMPLE_ASSET)

    private val schedule = listOf(
      ScheduleEntry(ScheduleSlot.NOW, "تلاوت مجلسی", "[نام قاری]"),
      ScheduleEntry(ScheduleSlot.NEXT, "نماز جماعت", "پخش زنده"),
      ScheduleEntry(ScheduleSlot.LATER, "تفسیر قرآن به فارسی", "[نام استاد]"),
      ScheduleEntry(ScheduleSlot.LATER, "دعا و مناجات", "پخش زنده")
    )
  }
}
