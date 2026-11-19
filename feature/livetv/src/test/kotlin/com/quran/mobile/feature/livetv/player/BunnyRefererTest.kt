package com.quran.mobile.feature.livetv.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BunnyRefererTest {

  @Test
  fun onlyBunnyCdnHostsGetThePlayerReferer() {
    assertThat(isBunnyCdn("vz-b3e0aee1-614.b-cdn.net")).isTrue()
    assertThat(isBunnyCdn("VZ-1.B-CDN.NET")).isTrue()
    assertThat(isBunnyCdn("b-cdn.net.example.com")).isFalse()
    assertThat(isBunnyCdn("live.kwikmotion.com")).isFalse()
    assertThat(isBunnyCdn(null)).isFalse()
  }
}
