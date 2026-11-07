package com.quran.mobile.feature.ads.noop

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.ads.AdSlot
import com.quran.mobile.feature.ads.AdState
import org.junit.Test

class NoopAdProviderTest {
  @Test
  fun everySlotStaysHidden() {
    val provider = NoopAdProvider()
    for (slot in AdSlot.entries) assertThat(provider.state(slot).value).isEqualTo(AdState.Hidden)
  }
}
