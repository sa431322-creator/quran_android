package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
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
}
