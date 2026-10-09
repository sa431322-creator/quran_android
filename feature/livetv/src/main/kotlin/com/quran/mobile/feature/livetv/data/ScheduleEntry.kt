package com.quran.mobile.feature.livetv.data

/** One program on today's schedule. Titles are placeholders until the broadcast backend exists. */
data class ScheduleEntry(
  /** Start time, in minutes since local midnight. */
  val startMinute: Int,
  val title: String
)

/**
 * The index of the program on air at [nowMinute] (minutes since local midnight), for a
 * schedule sorted by start time: the last program that has started. Before the first start
 * of the day, the previous day's last program is still on air. -1 for an empty schedule.
 */
fun List<ScheduleEntry>.onAirIndex(nowMinute: Int): Int {
  if (isEmpty()) return -1
  val started = indexOfLast { it.startMinute <= nowMinute }
  return if (started >= 0) started else lastIndex
}
