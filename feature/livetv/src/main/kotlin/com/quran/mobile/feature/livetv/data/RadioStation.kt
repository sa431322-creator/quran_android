package com.quran.mobile.feature.livetv.data

/** An audio station. Its name and programs are placeholders until the real station exists. */
data class RadioStation(
  val id: String,
  val name: String,
  val description: String,
  /** A live broadcast rather than an on-demand playlist; shows the «زنده» badge. */
  val isLive: Boolean,
  val source: LiveStreamSource,
  /** Today's programs, sorted by start time. */
  val schedule: List<ScheduleEntry>
)
