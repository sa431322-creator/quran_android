package com.quran.mobile.feature.livetv.data

/** A live video channel. Names and programs are placeholders until real channels exist. */
data class LiveChannel(
  val id: String,
  val name: String,
  val description: String,
  val currentProgram: String,
  val source: LiveStreamSource,
  val schedule: List<ScheduleEntry>,
  /** Concurrent viewers, or null while there is no backend to count them. */
  val viewerCount: Int? = null
)

/** Today's schedule is shown relative to now, as on the design: now, next, then later. */
enum class ScheduleSlot { NOW, NEXT, LATER }

data class ScheduleEntry(
  val slot: ScheduleSlot,
  val title: String,
  val note: String
)
