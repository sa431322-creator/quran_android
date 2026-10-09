package com.quran.mobile.feature.livetv.data

/** The live video channel. Its name and programs are placeholders until the real channel exists. */
data class LiveChannel(
  val id: String,
  val name: String,
  val source: LiveStreamSource,
  /** Today's programs, sorted by start time. */
  val schedule: List<ScheduleEntry>,
  /** Concurrent viewers, or null while there is no backend to count them. */
  val viewerCount: Int? = null
)
