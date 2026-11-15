package com.quran.mobile.feature.ads.network

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.ads.AdSlot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaceholderNetworkAdsTest {
  @Test
  fun answersAsAdmobWithAnAdForEverySlot() = runTest {
    val ads = PlaceholderNetworkAds()
    assertThat(ads.id).isEqualTo("admob")
    for (slot in AdSlot.entries) assertThat(ads.load(slot)).isTrue()
  }
}
