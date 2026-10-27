package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Our own station and the Quran station, with the stream links from the admin panel. A station
 * without a link is left out. A build without a server has no links, so our station plays the
 * audio of the bundled sample clip (the radio player ignores its video track) and the Quran
 * station is left out. Our station's [bracketed] name and schedule are placeholders.
 */
@ContributesBinding(AppScope::class)
@Inject
class ConfiguredRadioStationRepository(
  private val linksProvider: StreamLinksProvider
) : RadioStationRepository {

  override fun stations(): List<RadioStation> {
    val links = linksProvider.links()
    val own = if (links == null) {
      LiveStreamSource.LocalAsset(ConfiguredLiveChannelRepository.SAMPLE_ASSET)
    } else {
      links.ownRadio?.let(::remoteStreamOf)
    }
    return listOfNotNull(
      own?.let { OWN_STATION.copy(source = it) },
      links?.quranRadio?.let { QURAN_STATION.copy(source = remoteStreamOf(it)) }
    )
  }

  companion object {
    const val OWN_ID = "radio-main"
    const val QURAN_ID = "radio-quran"

    private val OWN_STATION = RadioStation(
      id = OWN_ID,
      name = "[نام رادیو]",
      description = "تلاوت شبانه‌روزی قرآن",
      isLive = true,
      source = LiveStreamSource.LocalAsset(ConfiguredLiveChannelRepository.SAMPLE_ASSET),
      schedule = listOf(
        ScheduleEntry(5 * 60 + 30, "تلاوت سحرگاهی"),
        ScheduleEntry(9 * 60, "[نام برنامه]"),
        ScheduleEntry(12 * 60 + 15, "اذان و نماز ظهر"),
        ScheduleEntry(16 * 60, "تفسیر صوتی به فارسی"),
        ScheduleEntry(20 * 60 + 30, "[نام برنامه]")
      )
    )

    private val QURAN_STATION = RadioStation(
      id = QURAN_ID,
      name = "رادیو قرآن کریم",
      description = "پخش زنده",
      isLive = true,
      source = LiveStreamSource.LocalAsset(ConfiguredLiveChannelRepository.SAMPLE_ASSET),
      schedule = emptyList()
    )
  }
}
