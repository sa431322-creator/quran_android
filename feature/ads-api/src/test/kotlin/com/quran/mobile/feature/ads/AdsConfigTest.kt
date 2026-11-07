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
  fun slotIdsMatchTheServerRegistry() {
    assertThat(AdSlot.entries.map { it.id })
      .containsExactly("live_tv", "radio", "library_list", "library_details")
    assertThat(AdSlot.fromId("home")).isNull()
    assertThat(AdMode.fromId("sponsored")).isEqualTo(AdMode.OFF)
  }
}
