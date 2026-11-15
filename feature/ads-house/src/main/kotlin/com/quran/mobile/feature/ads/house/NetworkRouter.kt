package com.quran.mobile.feature.ads.house

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.di.AppScope
import com.quran.mobile.feature.ads.AdSlot
import com.quran.mobile.feature.ads.AdState
import com.quran.mobile.feature.ads.NetworkAds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Empty in every build without `-PnetworkAds`; one entry (AdMob) in the Google Play build. */
@ContributesTo(AppScope::class)
interface NetworkAdsBindings {
  @Multibinds(allowEmpty = true)
  val networkAds: Set<NetworkAds>
}

/**
 * Decides between a house ad and a network ad in each slot (ads plan §9.2):
 * `house` → house only; `network` → network only; `house_then_network` → the house ad when one
 * is live, else the network.
 *
 * The network is asked only for a slot whose screen was opened, never for all four at once,
 * and never before the server settings allowed it. A slot stays empty until the network
 * reports a loaded ad; an answer later than [timeoutMs] is dropped so the screen never jumps.
 * A slot the network left empty is not asked again for [RETRY_MS].
 * Main thread only.
 */
internal class NetworkRouter(
  val network: NetworkAds?,
  private val states: Map<AdSlot, MutableStateFlow<AdState>>,
  private val scope: CoroutineScope,
  private val timeoutMs: Long = NETWORK_TIMEOUT_MS,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private var allowed = emptySet<AdSlot>()
  private val asked = mutableSetOf<AdSlot>()
  private val loading = mutableMapOf<AdSlot, Job>()
  private val lastAskMs = mutableMapOf<AdSlot, Long>()

  /** A screen asked for [slot]. Follow with [loadAsked] unless a refresh is about to [settle]. */
  fun visit(slot: AdSlot) {
    if (network != null) asked += slot
  }

  /**
   * After each refresh: [house] is the house ad of every slot (missing = none) and
   * [networkSlots] the slots where the server lets this build ask the network right now.
   * A house ad always wins; a network ad already on screen stays while it is still allowed.
   */
  fun settle(house: Map<AdSlot, AdState>, networkSlots: Set<AdSlot>) {
    allowed = if (network == null) emptySet() else networkSlots
    for ((slot, state) in states) {
      val houseAd = house[slot] ?: AdState.Hidden
      val keepNetworkAd =
        houseAd == AdState.Hidden && slot in allowed && state.value is AdState.Network
      if (!keepNetworkAd) state.value = houseAd
      if (slot !in allowed) loading.remove(slot)?.cancel()
    }
    loadAsked()
  }

  /** Asks the network for the visited slots that are allowed, empty and not already loading. */
  fun loadAsked() {
    val network = network ?: return
    val slots = asked.toList()
    asked.clear()
    for (slot in slots) {
      val state = states.getValue(slot)
      if (slot !in allowed || state.value != AdState.Hidden || loading[slot]?.isActive == true) {
        continue
      }
      // scrolling an empty slot in and out of view must not send a request each time
      if (lastAskMs[slot]?.let { now() - it < RETRY_MS } == true) continue
      lastAskMs[slot] = now()
      loading[slot] = scope.launch {
        val loaded = withTimeoutOrNull(timeoutMs) {
          try {
            network.load(slot)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            false
          }
        } ?: false
        if (loaded && slot in allowed && state.value == AdState.Hidden) {
          state.value = AdState.Network(slot)
        }
      }
    }
  }

  companion object {
    const val NETWORK_TIMEOUT_MS = 3_000L
    const val RETRY_MS = 30_000L
  }
}

/**
 * A network ad as the app frames it: the «تبلیغ» label above the ad, never drawn over it (ad
 * networks forbid covering their view), then the network's own view.
 */
@Composable
internal fun NetworkAdFrame(modifier: Modifier, ad: @Composable () -> Unit) {
  Column(modifier.fillMaxWidth()) {
    Text(
      text = LABEL,
      color = Color.White,
      fontSize = 11.sp,
      modifier = Modifier
        .padding(bottom = 4.dp)
        .background(Color(0xD91C1A16), RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp),
    )
    ad()
  }
}
