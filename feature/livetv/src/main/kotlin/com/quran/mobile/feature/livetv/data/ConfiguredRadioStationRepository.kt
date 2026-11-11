package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Our own station, the Quran station and the Sunnah station, with the stream links from the admin
 * panel. A station without a link is left out. A build without a server has no links, so our
 * station plays the audio of the bundled sample clip (the radio player ignores its video track)
 * and the other two are left out. Our station's name and schedule come from the admin panel too; the
 * [bracketed] name and the schedule below are what a build without a server shows.
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
      links.ownRadio?.let(::directStreamOf)
    }
    return listOfNotNull(
      own?.let {
        val station = if (links == null) OWN_STATION else OWN_STATION.withInfo(links.ownRadioInfo)
        station.copy(source = it)
      },
      links?.quranRadio?.let(::directStreamOf)?.let { QURAN_STATION.copy(source = it) },
      links?.sunnahRadio?.let(::directStreamOf)?.let { SUNNAH_STATION.copy(source = it) }
    )
  }

  companion object {
    const val OWN_ID = "radio-main"
    const val QURAN_ID = "radio-quran"
    const val SUNNAH_ID = "radio-sunnah"

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

    private val SUNNAH_STATION = RadioStation(
      id = SUNNAH_ID,
      name = "رادیو سنت نبوی",
      description = "پخش زنده",
      isLive = true,
      source = LiveStreamSource.LocalAsset(ConfiguredLiveChannelRepository.SAMPLE_ASSET),
      schedule = emptyList()
    )
  }
}
