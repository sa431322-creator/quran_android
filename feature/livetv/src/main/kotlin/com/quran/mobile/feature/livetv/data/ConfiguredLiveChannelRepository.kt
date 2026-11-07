package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Our own channel, the Quran channel and the Sunnah channel, with the stream links from the admin
 * panel. A channel without a link is left out. A build without a server has no links, so our
 * channel plays the clip bundled in assets and the other two are left out. Our channel's name and
 * schedule come from the admin panel too; the [bracketed] name and the schedule below are what a
 * build without a server shows. The Quran and Sunnah channels have no schedule.
 */
@ContributesBinding(AppScope::class)
@Inject
class ConfiguredLiveChannelRepository(
  private val linksProvider: StreamLinksProvider
) : LiveChannelRepository {

  override fun channels(): List<LiveChannel> {
    val links = linksProvider.links()
    val own = if (links == null) {
      LiveStreamSource.LocalAsset(SAMPLE_ASSET)
    } else {
      links.ownLive?.let(::ownLiveStreamOf)
    }
    return listOfNotNull(
      own?.let { ownChannel(links).copy(source = it) },
      // youtube is for our own channel only
      links?.quranLive?.let(::directStreamOf)?.let { QURAN_CHANNEL.copy(source = it) },
      links?.sunnahLive?.let(::directStreamOf)?.let { SUNNAH_CHANNEL.copy(source = it) }
    )
  }

  override fun ownChannelName(): String = ownChannel(linksProvider.links()).name

  private fun ownChannel(links: StreamLinks?): LiveChannel =
    if (links == null) OWN_CHANNEL else OWN_CHANNEL.withInfo(links.ownLiveInfo)

  companion object {
    const val SAMPLE_ASSET = "qurhan_42.mp4"
    const val OWN_ID = "channel-main"
    const val QURAN_ID = "channel-quran"
    const val SUNNAH_ID = "channel-sunnah"

    private val OWN_CHANNEL = LiveChannel(
      id = OWN_ID,
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

    private val QURAN_CHANNEL = LiveChannel(
      id = QURAN_ID,
      name = "شبکهٔ قرآن کریم",
      source = LiveStreamSource.LocalAsset(SAMPLE_ASSET),
      schedule = emptyList()
    )

    private val SUNNAH_CHANNEL = LiveChannel(
      id = SUNNAH_ID,
      name = "شبکهٔ سنت نبوی",
      source = LiveStreamSource.LocalAsset(SAMPLE_ASSET),
      schedule = emptyList()
    )
  }
}
