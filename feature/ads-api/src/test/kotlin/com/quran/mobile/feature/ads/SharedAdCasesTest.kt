package com.quran.mobile.feature.ads

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.moshi.Moshi
import org.junit.Test

/**
 * The cases shared with the server (tasneem-backend packages/contracts/test-cases/ads.json,
 * copied to src/test/resources/ads-cases.json). When the server's file changes, copy it again.
 */
class SharedAdCasesTest {
  private val cases: Map<*, *> = run {
    val text = javaClass.classLoader!!.getResource("ads-cases.json")!!.readText()
    Moshi.Builder().build().adapter(Any::class.java).fromJson(text) as Map<*, *>
  }

  @Test
  fun linksFollowTheServerRules() {
    val links = cases["links"] as List<*>
    assertThat(links.size).isAtLeast(40)
    for (case in links) {
      case as Map<*, *>
      val name = case["name"] as String
      var input = case["input"] as String
      (case["repeatToLength"] as? Number)?.let { input = input.padEnd(it.toInt(), 'a') }
      val ok = case["ok"] as Boolean
      assertWithMessage("$name: $input → ${adLinkProblem(input)}")
        .that(adLinkProblem(input) == null)
        .isEqualTo(ok)
    }
  }

  @Test
  fun readsTheSampleConfig() {
    val config = AdsConfig.from(cases["config"])
    assertThat(config.enabled).isTrue()
    assertThat(config.mode(AdSlot.LIVE_TV)).isEqualTo(AdMode.HOUSE)
    assertThat(config.mode(AdSlot.RADIO)).isEqualTo(AdMode.OFF)
    assertThat(config.blockedVersions).containsExactly(3642)
    assertThat(config.showsHouseAds(AdSlot.LIVE_TV, versionCode = 3642)).isFalse()
    assertThat(config.showsHouseAds(AdSlot.LIVE_TV, versionCode = 3700)).isTrue()
  }

  @Test
  fun aNewerServersConfigReadsLikeTheExpectedOne() {
    val future = AdsConfig.from(cases["futureConfig"])
    val expected = AdsConfig.from(cases["futureConfigExpected"])
    assertThat(future).isEqualTo(expected)
    // unknown mode → off, unknown slot ignored, unknown network → none
    assertThat(future.mode(AdSlot.RADIO)).isEqualTo(AdMode.OFF)
    assertThat(future.slots.keys).containsExactlyElementsIn(AdSlot.entries)
    assertThat(future.network).isEqualTo("none")
  }
}
