package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Placeholder stations from the design; the [bracketed] names are placeholders for the
 * real ones. Until real stream URLs exist, every station plays the audio of the bundled
 * sample clip (the radio player ignores its video track).
 */
@ContributesBinding(AppScope::class)
@Inject
class LocalRadioStationRepository : RadioStationRepository {

  override fun stations(): List<RadioStation> = STATIONS

  private companion object {
    val sample = LiveStreamSource.LocalAsset(LocalLiveChannelRepository.SAMPLE_ASSET)

    val STATIONS = listOf(
      RadioStation(
        id = "radio-main",
        name = "[نام رادیو]",
        description = "تلاوت شبانه‌روزی قرآن",
        isLive = true,
        source = sample
      ),
      RadioStation(
        id = "radio-mosque",
        name = "پخش زنده — [نام مسجد یا حرم]",
        description = "نماز و مراسم به‌صورت زنده",
        isLive = true,
        source = sample
      ),
      RadioStation(
        id = "radio-recitations",
        name = "تلاوت‌های برگزیده",
        description = "فهرستی تازه از قاریان برجسته",
        isLive = false,
        source = sample
      ),
      RadioStation(
        id = "radio-tafsir",
        name = "تفسیر صوتی",
        description = "درس‌های کوتاه تفسیر به فارسی",
        isLive = false,
        source = sample
      )
    )
  }
}
