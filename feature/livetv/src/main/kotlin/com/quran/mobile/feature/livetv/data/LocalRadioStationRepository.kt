package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * The placeholder station from the design; the [bracketed] names and the schedule are
 * placeholders for the real ones. Until a real stream URL exists, the station plays the
 * audio of the bundled sample clip (the radio player ignores its video track).
 */
@ContributesBinding(AppScope::class)
@Inject
class LocalRadioStationRepository : RadioStationRepository {

  override fun stations(): List<RadioStation> = STATIONS

  private companion object {
    // Tasnim broadcasts a single station; the list shape is what the media session browses
    val STATIONS = listOf(
      RadioStation(
        id = "radio-main",
        name = "[نام رادیو]",
        description = "تلاوت شبانه‌روزی قرآن",
        isLive = true,
        source = LiveStreamSource.LocalAsset(LocalLiveChannelRepository.SAMPLE_ASSET),
        schedule = listOf(
          ScheduleEntry(5 * 60 + 30, "تلاوت سحرگاهی"),
          ScheduleEntry(9 * 60, "[نام برنامه]"),
          ScheduleEntry(12 * 60 + 15, "اذان و نماز ظهر"),
          ScheduleEntry(16 * 60, "تفسیر صوتی به فارسی"),
          ScheduleEntry(20 * 60 + 30, "[نام برنامه]")
        )
      )
    )
  }
}
