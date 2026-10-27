package com.quran.mobile.feature.livetv.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Our own channel and the Quran channel, with the stream links from the admin panel. A channel
 * without a link is left out. A build without a server has no links, so our channel plays the
 * clip bundled in assets and the Quran channel is left out. Our channel's [bracketed] name and
 * schedule are placeholders for the real ones; the Quran channel has no schedule.
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
      links.ownLive?.let(::remoteStreamOf)
    }
    return listOfNotNull(
      own?.let { OWN_CHANNEL.copy(source = it) },
      links?.quranLive?.let { QURAN_CHANNEL.copy(source = remoteStreamOf(it)) }
    )
  }

  override fun ownChannelName(): String = OWN_CHANNEL.name

  companion object {
    const val SAMPLE_ASSET = "qurhan_42.mp4"
    const val OWN_ID = "channel-main"
    const val QURAN_ID = "channel-quran"

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
  }
}
