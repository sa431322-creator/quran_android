package com.quran.mobile.feature.livetv.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScheduleTimeTest {

  @Test
  fun formatsTwentyFourHourTimeInPersianDigits() {
    assertThat(formatScheduleTime(0)).isEqualTo("۰۰:۰۰")
    assertThat(formatScheduleTime(6 * 60 + 30)).isEqualTo("۰۶:۳۰")
    assertThat(formatScheduleTime(21 * 60 + 5)).isEqualTo("۲۱:۰۵")
  }
}
