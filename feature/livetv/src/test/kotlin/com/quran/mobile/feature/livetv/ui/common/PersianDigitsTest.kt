package com.quran.mobile.feature.livetv.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersianDigitsTest {

  @Test
  fun convertsAsciiDigitsOnly() {
    assertThat("ساعت 10:05".toPersianDigits()).isEqualTo("ساعت ۱۰:۰۵")
  }

  @Test
  fun formatsCountsWithPersianSeparator() {
    assertThat(formatPersianCount(7)).isEqualTo("۷")
    assertThat(formatPersianCount(1250)).isEqualTo("۱٬۲۵۰")
    assertThat(formatPersianCount(1_000_000)).isEqualTo("۱٬۰۰۰٬۰۰۰")
  }
}
