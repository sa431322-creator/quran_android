package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
import java.util.TimeZone
import org.junit.Test

class ScheduleEntryTest {

  private val schedule = listOf(
    ScheduleEntry(6 * 60, "a"),
    ScheduleEntry(12 * 60, "b"),
    ScheduleEntry(21 * 60, "c")
  )

  @Test
  fun programOnAirIsTheLastOneStarted() {
    assertThat(schedule.onAirIndex(6 * 60)).isEqualTo(0)
    assertThat(schedule.onAirIndex(11 * 60 + 59)).isEqualTo(0)
    assertThat(schedule.onAirIndex(12 * 60)).isEqualTo(1)
    assertThat(schedule.onAirIndex(23 * 60 + 59)).isEqualTo(2)
  }

  @Test
  fun beforeTheFirstStartYesterdaysLastProgramIsOnAir() {
    assertThat(schedule.onAirIndex(0)).isEqualTo(2)
    assertThat(schedule.onAirIndex(5 * 60 + 59)).isEqualTo(2)
  }

  @Test
  fun emptyScheduleHasNothingOnAir() {
    assertThat(emptyList<ScheduleEntry>().onAirIndex(600)).isEqualTo(-1)
  }

  // 2026-01-01T12:00Z: Tehran +03:30, Berlin +01:00, Kabul +04:30 (no daylight saving in January)
  private val winter = 1_767_268_800_000L

  @Test
  fun tehranTimesAreShownInTheViewersTime() {
    val berlin = TimeZone.getTimeZone("Europe/Berlin")
    // 20:00 Tehran = 17:30 Berlin
    assertThat(tehranToLocalMinute(20 * 60, winter, berlin)).isEqualTo(17 * 60 + 30)
    // 02:00 Tehran = 23:30 Berlin the day before
    assertThat(tehranToLocalMinute(2 * 60, winter, berlin)).isEqualTo(23 * 60 + 30)
    // 23:00 Tehran = 00:00 Kabul the next day
    assertThat(tehranToLocalMinute(23 * 60, winter, TimeZone.getTimeZone("Asia/Kabul")))
      .isEqualTo(0)
    assertThat(tehranToLocalMinute(600, winter, TimeZone.getTimeZone("Asia/Tehran"))).isEqualTo(600)
  }
}
