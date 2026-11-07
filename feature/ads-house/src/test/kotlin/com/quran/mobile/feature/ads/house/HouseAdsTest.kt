package com.quran.mobile.feature.ads.house

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.ads.AdSlot
import com.squareup.moshi.Moshi
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class HouseAdsTest {
  private val cases: Map<*, *> = run {
    val text = javaClass.classLoader!!.getResource("ads-cases.json")!!.readText()
    Moshi.Builder().build().adapter(Any::class.java).fromJson(text) as Map<*, *>
  }
  private val json = Moshi.Builder().build().adapter(Any::class.java)
  private fun case(name: String) = json.toJson(cases[name])
  private val mid = Instant.parse("2026-11-15T00:00:00Z").toEpochMilli()

  @Test
  fun readsTheSharedServerAnswer() {
    val served = ServedAds.parse(case("response"), "api.example.org")
    assertThat(served.refreshAfterSeconds).isEqualTo(900)
    val ad = served.forSlot(AdSlot.LIVE_TV, mid).single()
    assertThat(ad.id).isEqualTo(12)
    assertThat(ad.width).isEqualTo(1280)
    assertThat(ad.height).isEqualTo(200)
    assertThat(ad.actionUrl).startsWith("https://example.org/")
  }

  @Test
  fun ignoresUnknownSlotsAndFieldsOfANewerServer() {
    val served = ServedAds.parse(case("futureResponse"), "api.example.org")
    assertThat(served.ads).isEmpty()
    assertThat(served.refreshAfterSeconds).isEqualTo(900)
  }

  @Test
  fun dropsPicturesFromAnotherHostAndRefusedLinks() {
    assertThat(ServedAds.parse(case("response"), "evil.example").ads).isEmpty()
    val badLink = case("response").replace(
      "https://example.org/?utm_source=tasneem&utm_medium=app&utm_campaign=ad-12",
      "http://example.org/",
    )
    assertThat(ServedAds.parse(badLink, "api.example.org").ads).isEmpty()
  }

  @Test
  fun brokenAnswersMeanNoAds() {
    assertThat(ServedAds.parse("not json", "h").ads).isEmpty()
    assertThat(ServedAds.parse("[]", "h").ads).isEmpty()
    assertThat(ServedAds.parse("""{"slots":{"live_tv":[{"id":"x"}]}}""", "h").ads).isEmpty()
  }

  @Test
  fun expiredOrFutureAdsAreNotShown() {
    val served = ServedAds.parse(case("response"), "api.example.org")
    val before = Instant.parse("2026-10-31T23:59:59Z").toEpochMilli()
    val after = Instant.parse("2026-11-30T00:00:00Z").toEpochMilli()
    assertThat(served.forSlot(AdSlot.LIVE_TV, before)).isEmpty()
    assertThat(served.forSlot(AdSlot.LIVE_TV, after)).isEmpty()
  }

  @Test
  fun picksByWeight() {
    val random = Random(42)
    val items = listOf("a" to 10, "b" to 30)
    val picks = (1..4000).map { pickWeighted(items, random) { it.second }!!.first }
    val share = picks.count { it == "b" } / 4000.0
    assertThat(share).isWithin(0.03).of(0.75)
    assertThat(pickWeighted(emptyList<Pair<String, Int>>(), random) { it.second }).isNull()
  }

  @Test
  fun tehranDayIsUtcPlusThreeAndAHalf() {
    assertThat(tehranDay(Instant.parse("2026-11-07T20:29:59Z").toEpochMilli())).isEqualTo("2026-11-07")
    assertThat(tehranDay(Instant.parse("2026-11-07T20:30:00Z").toEpochMilli())).isEqualTo("2026-11-08")
  }
}

class CountMathTest {
  @Test
  fun addsToTheSameRow() {
    var rows = CountMath.add(emptyList(), 1, "radio", "2026-11-07", 1, 0)
    rows = CountMath.add(rows, 1, "radio", "2026-11-07", 1, 1)
    rows = CountMath.add(rows, 1, "radio", "2026-11-08", 1, 0)
    assertThat(rows).containsExactly(
      CountRow(1, "radio", "2026-11-07", 2, 1),
      CountRow(1, "radio", "2026-11-08", 1, 0),
    )
  }

  @Test
  fun cutsBatchesWithinTheServerLimits() {
    val rows = (1..60).map { CountRow(it, "live_tv", "2026-11-07", 300, 30) }
    val (batch, rest) = CountMath.cut(rows)
    assertThat(batch.size).isAtMost(50)
    assertThat(batch.sumOf { it.impressions }).isAtMost(500)
    assertThat(batch.sumOf { it.clicks }).isAtMost(50)
    assertThat(batch.all { it.impressions <= 200 && it.clicks <= 20 && it.clicks <= it.impressions }).isTrue()
    // nothing is lost except clicks that would exceed their impressions
    assertThat(batch.sumOf { it.impressions } + rest.sumOf { it.impressions }).isEqualTo(60 * 300)
    assertThat(rest.all { it.clicks <= it.impressions }).isTrue()
  }

  @Test
  fun dropsDaysTheServerNoLongerTakes() {
    val rows = listOf(
      CountRow(1, "radio", "2026-10-30", 1, 0),
      CountRow(1, "radio", "2026-10-31", 1, 0),
      CountRow(1, "radio", "2026-11-07", 1, 0),
    )
    assertThat(CountMath.dropOld(rows, "2026-11-07").map { it.day })
      .containsExactly("2026-10-31", "2026-11-07")
  }
}
