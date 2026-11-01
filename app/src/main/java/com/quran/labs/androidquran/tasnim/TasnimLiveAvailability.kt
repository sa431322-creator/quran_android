package com.quran.labs.androidquran.tasnim

import com.quran.data.di.AppScope
import com.quran.mobile.feature.livetv.data.LiveAvailability
import com.quran.mobile.feature.livetv.data.OwnChannelInfo
import com.quran.mobile.feature.livetv.data.ScheduleEntry
import com.quran.mobile.feature.livetv.data.StreamLinks
import com.quran.mobile.feature.livetv.data.StreamLinksProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * The live and radio switches from the Tasnim server's settings (last saved copy, or the defaults).
 * A build without a server has no switches to read, so both stay on and play the bundled clip.
 */
@ContributesBinding(AppScope::class)
@Inject
class TasnimLiveAvailability(private val server: TasnimConfigRepository) : LiveAvailability {
  override fun isLiveAvailable(): Boolean = !server.hasServer || server.cached().features.live
  override fun isRadioAvailable(): Boolean = !server.hasServer || server.cached().features.radio
}

/** The stream links from the Tasnim server's settings; none in a build without a server. */
@ContributesBinding(AppScope::class)
@Inject
class TasnimStreamLinks(private val server: TasnimConfigRepository) : StreamLinksProvider {
  override fun links(): StreamLinks? {
    if (!server.hasServer) return null
    val config = server.cached()
    val streams = config.streams
    return StreamLinks(
      ownLive = streams.ownLive,
      quranLive = streams.quranLive,
      ownRadio = streams.ownRadio,
      quranRadio = streams.quranRadio,
      ownLiveInfo = config.channels.live.toInfo(),
      ownRadioInfo = config.channels.radio.toInfo()
    )
  }

  private fun AppOwnChannel.toInfo() = OwnChannelInfo(
    name = name,
    // drop anything outside the day so the on-air marker stays meaningful
    schedule = schedule
      .filter { it.startMinute in 0 until 24 * 60 && it.title.isNotBlank() }
      .map { ScheduleEntry(it.startMinute, it.title) }
  )
}
