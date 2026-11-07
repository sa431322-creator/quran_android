package com.quran.mobile.feature.livetv.data

/**
 * What the admin panel sets for the live and radio screens (البث المباشر / الراديو pages). A null
 * link means not set, and that channel's button is hidden.
 */
data class StreamLinks(
  val ownLive: String? = null,
  val quranLive: String? = null,
  val sunnahLive: String? = null,
  val ownRadio: String? = null,
  val quranRadio: String? = null,
  val sunnahRadio: String? = null,
  val ownLiveInfo: OwnChannelInfo = OwnChannelInfo(),
  val ownRadioInfo: OwnChannelInfo = OwnChannelInfo()
)

/** Our own channel's name and daily schedule from the admin panel. */
data class OwnChannelInfo(
  /** Null or blank: keep the built-in placeholder name. */
  val name: String? = null,
  /** Empty: the screen shows no schedule. */
  val schedule: List<ScheduleEntry> = emptyList()
)

fun interface StreamLinksProvider {
  /** The last links read from the server, or null in a build without a server. */
  fun links(): StreamLinks?
}

/** The panel's name (unless blank) and schedule, in time order, over the built-in channel. */
internal fun LiveChannel.withInfo(info: OwnChannelInfo): LiveChannel = copy(
  name = info.name?.takeIf { it.isNotBlank() } ?: name,
  schedule = info.schedule.sortedBy { it.startMinute }
)

internal fun RadioStation.withInfo(info: OwnChannelInfo): RadioStation = copy(
  name = info.name?.takeIf { it.isNotBlank() } ?: name,
  schedule = info.schedule.sortedBy { it.startMinute }
)
