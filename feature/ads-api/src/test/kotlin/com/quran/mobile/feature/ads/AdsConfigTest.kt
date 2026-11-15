package com.quran.mobile.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdsConfigTest {
  @Test
  fun missingOrBrokenSectionMeansNoAds() {
    for (json in listOf(null, "", "not json", "[]", "{}", """{"ads":null}""", """{"ads":"on"}""")) {
      assertThat(AdsConfig.fromAppConfigJson(json)).isEqualTo(AdsConfig.OFF)
    }
  }

  @Test
  fun aBrokenAdsSectionLeavesNoAdsButParsesTheRestOfTheBodyFine() {
    val json = """{"links":{"website":"https://x.org"},"ads":{"enabled":"yes","slots":7}}"""
    val config = AdsConfig.fromAppConfigJson(json)
    assertThat(config.enabled).isFalse()
    assertThat(config.slots).isEmpty()
  }

  @Test
  fun outOfRangeListSpacingFallsBackToTheDefault() {
    val json = """{"ads":{"enabled":true,"libraryListEvery":1}}"""
    assertThat(AdsConfig.fromAppConfigJson(json).libraryListEvery)
      .isEqualTo(AdsConfig.DEFAULT_LIBRARY_LIST_EVERY)
  }

  @Test
  fun offWhenDisabledEvenIfSlotsSayHouse() {
    val json = """{"ads":{"enabled":false,"slots":{"radio":"house"}}}"""
    assertThat(AdsConfig.fromAppConfigJson(json).showsHouseAds(AdSlot.RADIO, 1)).isFalse()
  }

  @Test
  fun networkAdsNeedTheSwitchTheNamedNetworkAndANetworkMode() {
    fun config(enabled: Boolean = true, network: String = "admob", blocked: String = "") =
      AdsConfig.fromAppConfigJson(
        """{"ads":{"enabled":$enabled,"network":"$network","blockedVersions":[$blocked],"slots":
          {"radio":"network","live_tv":"house_then_network","library_list":"house"}}}"""
      )
    assertThat(config().showsNetworkAds(AdSlot.RADIO, 7, "admob")).isTrue()
    assertThat(config().showsNetworkAds(AdSlot.LIVE_TV, 7, "admob")).isTrue()
    // house-only and unset slots never ask the network
    assertThat(config().showsNetworkAds(AdSlot.LIBRARY_LIST, 7, "admob")).isFalse()
    assertThat(config().showsNetworkAds(AdSlot.LIBRARY_DETAILS, 7, "admob")).isFalse()
    // the kill switch, a blocked version, and a server that names no or another network
    assertThat(config(enabled = false).showsNetworkAds(AdSlot.RADIO, 7, "admob")).isFalse()
    assertThat(config(blocked = "7").showsNetworkAds(AdSlot.RADIO, 7, "admob")).isFalse()
    assertThat(config(network = "none").showsNetworkAds(AdSlot.RADIO, 7, "admob")).isFalse()
    assertThat(config(network = "tapsell").showsNetworkAds(AdSlot.RADIO, 7, "admob")).isFalse()
    assertThat(config(network = "surprise").showsNetworkAds(AdSlot.RADIO, 7, "admob")).isFalse()
    // a network-only slot shows no house ad
    assertThat(config().showsHouseAds(AdSlot.RADIO, 7)).isFalse()
  }

  @Test
  fun slotIdsMatchTheServerRegistry() {
    assertThat(AdSlot.entries.map { it.id })
      .containsExactly("live_tv", "radio", "library_list", "library_details")
    assertThat(AdSlot.fromId("home")).isNull()
    assertThat(AdMode.fromId("sponsored")).isEqualTo(AdMode.OFF)
  }
}
