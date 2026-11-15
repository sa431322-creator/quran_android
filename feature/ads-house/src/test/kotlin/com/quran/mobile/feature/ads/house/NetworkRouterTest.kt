package com.quran.mobile.feature.ads.house

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.ads.AdContent
import com.quran.mobile.feature.ads.AdSlot
import com.quran.mobile.feature.ads.AdState
import com.quran.mobile.feature.ads.NetworkAds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkRouterTest {
  private class FakeNetwork(var answerMs: Long = 100, var fill: Boolean = true) : NetworkAds {
    val asked = mutableListOf<AdSlot>()
    var fails = false
    override val id = "admob"
    override suspend fun load(slot: AdSlot): Boolean {
      asked += slot
      delay(answerMs)
      if (fails) error("sdk broke")
      return fill
    }

    @Composable
    override fun Render(slot: AdSlot, modifier: Modifier) = Unit
  }

  private val states = AdSlot.entries.associateWith { MutableStateFlow<AdState>(AdState.Hidden) }
  private val network = FakeNetwork()
  private fun TestScope.router(net: NetworkAds? = network) =
    NetworkRouter(net, states, backgroundScope, now = { testScheduler.currentTime })

  private fun state(slot: AdSlot) = states.getValue(slot).value
  private fun houseAd(slot: AdSlot) =
    AdState.Ready(AdContent(1, slot, File("x.png"), 1280, 200, "alt", null, null))

  private val all = AdSlot.entries.toSet()

  @Test
  fun aNetworkOnlySlotShowsTheNetworkAdOnceItHasLoaded() = runTest {
    val router = router()
    router.visit(AdSlot.RADIO)
    router.settle(emptyMap(), setOf(AdSlot.RADIO))
    runCurrent()
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden) // no space while it loads
    advanceTimeBy(150)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Network(AdSlot.RADIO))
  }

  @Test
  fun aLiveHouseAdWinsAndTheNetworkIsNotAsked() = runTest {
    val router = router()
    router.visit(AdSlot.LIVE_TV)
    router.settle(mapOf(AdSlot.LIVE_TV to houseAd(AdSlot.LIVE_TV)), all)
    advanceTimeBy(5_000)
    assertThat(state(AdSlot.LIVE_TV)).isEqualTo(houseAd(AdSlot.LIVE_TV))
    assertThat(network.asked).isEmpty()
  }

  @Test
  fun withoutAHouseAdTheNetworkFillsIn() = runTest {
    val router = router()
    router.visit(AdSlot.LIVE_TV)
    router.settle(emptyMap(), all)
    advanceTimeBy(150)
    assertThat(state(AdSlot.LIVE_TV)).isEqualTo(AdState.Network(AdSlot.LIVE_TV))
  }

  @Test
  fun onlyTheSlotOfTheOpenedScreenIsAsked() = runTest {
    val router = router()
    router.visit(AdSlot.LIBRARY_DETAILS)
    router.settle(emptyMap(), all)
    advanceTimeBy(150)
    assertThat(network.asked).containsExactly(AdSlot.LIBRARY_DETAILS)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden)
  }

  @Test
  fun aSlotTheServerDidNotAllowIsNeverAsked() = runTest {
    val router = router()
    router.visit(AdSlot.RADIO)
    router.settle(emptyMap(), setOf(AdSlot.LIVE_TV))
    advanceTimeBy(5_000)
    assertThat(network.asked).isEmpty()
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden)
  }

  @Test
  fun nothingIsAskedBeforeTheFirstSettings() = runTest {
    val router = router()
    router.visit(AdSlot.RADIO)
    router.loadAsked()
    advanceTimeBy(5_000)
    assertThat(network.asked).isEmpty()
  }

  @Test
  fun anAnswerLaterThanThreeSecondsIsDropped() = runTest {
    network.answerMs = 3_500
    val router = router()
    router.visit(AdSlot.RADIO)
    router.settle(emptyMap(), all)
    advanceTimeBy(10_000)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden)
  }

  @Test
  fun noFillOrABrokenNetworkLeavesTheSlotEmpty() = runTest {
    val router = router()
    network.fill = false
    router.visit(AdSlot.RADIO)
    router.settle(emptyMap(), all)
    advanceTimeBy(500)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden)

    router.visit(AdSlot.RADIO)
    router.loadAsked() // too soon: not asked again
    advanceTimeBy(500)
    assertThat(network.asked).hasSize(1)

    network.fails = true
    advanceTimeBy(NetworkRouter.RETRY_MS)
    router.visit(AdSlot.RADIO)
    router.loadAsked()
    advanceTimeBy(500)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden)
    assertThat(network.asked).hasSize(2)
  }

  @Test
  fun theKillSwitchRemovesAShownNetworkAdAndStopsALoad() = runTest {
    val router = router()
    router.visit(AdSlot.RADIO)
    router.settle(emptyMap(), all)
    advanceTimeBy(150)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Network(AdSlot.RADIO))

    router.visit(AdSlot.LIVE_TV)
    router.loadAsked() // still loading when the server switches ads off
    runCurrent()
    router.settle(emptyMap(), emptySet())
    advanceTimeBy(5_000)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden)
    assertThat(state(AdSlot.LIVE_TV)).isEqualTo(AdState.Hidden)
  }

  @Test
  fun aShownNetworkAdStaysOverARefreshAndGivesWayToANewHouseAd() = runTest {
    val router = router()
    router.visit(AdSlot.LIVE_TV)
    router.settle(emptyMap(), all)
    advanceTimeBy(150)
    router.visit(AdSlot.LIVE_TV)
    router.settle(emptyMap(), all)
    assertThat(state(AdSlot.LIVE_TV)).isEqualTo(AdState.Network(AdSlot.LIVE_TV))
    advanceTimeBy(500)
    assertThat(network.asked).hasSize(1) // not asked again while its ad is on screen

    router.settle(mapOf(AdSlot.LIVE_TV to houseAd(AdSlot.LIVE_TV)), all)
    assertThat(state(AdSlot.LIVE_TV)).isEqualTo(houseAd(AdSlot.LIVE_TV))
  }

  @Test
  fun aBuildWithoutANetworkShowsHouseAdsOnly() = runTest {
    val router = router(net = null)
    router.visit(AdSlot.RADIO)
    router.settle(mapOf(AdSlot.LIVE_TV to houseAd(AdSlot.LIVE_TV)), all)
    advanceTimeBy(5_000)
    assertThat(state(AdSlot.RADIO)).isEqualTo(AdState.Hidden)
    assertThat(state(AdSlot.LIVE_TV)).isEqualTo(houseAd(AdSlot.LIVE_TV))
  }
}
