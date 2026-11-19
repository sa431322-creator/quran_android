package com.quran.mobile.feature.livetv.data

import java.util.TimeZone

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

private val TEHRAN: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

/**
 * The Tasnim server writes program times in Tehran time, so everyone watches a program at the
 * same moment (tasneem-backend ADR 0014). This turns one into minutes since the device's local
 * midnight, using today's offsets. java.util.TimeZone, not java.time: minSdk is 24.
 */
fun tehranToLocalMinute(
  tehranMinute: Int,
  nowMillis: Long = System.currentTimeMillis(),
  local: TimeZone = TimeZone.getDefault(),
): Int {
  val shift = (local.getOffset(nowMillis) - TEHRAN.getOffset(nowMillis)) / 60_000
  return Math.floorMod(tehranMinute + shift, 24 * 60)
}
