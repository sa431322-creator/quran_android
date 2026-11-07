package com.quran.mobile.feature.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** One ad ready to draw: its picture is already on disk, so showing it never waits. */
data class AdContent(
  val id: Int,
  val slot: AdSlot,
  val image: File,
  val width: Int,
  val height: Int,
  val altText: String,
  /** Already checked with [adLinkProblem]; null when the ad has no link. */
  val actionUrl: String?,
  /** Cards only. */
  val actionText: String?,
)

sealed interface AdState {
  /** Nothing to show: the slot takes no space at all. */
  data object Hidden : AdState
  data class Ready(val ad: AdContent) : AdState
}

/** Bound by the app: ads-house in builds with ads, ads-noop otherwise. */
interface AdProvider {
  fun state(slot: AdSlot): StateFlow<AdState>

  @Composable
  fun Render(content: AdContent, modifier: Modifier)

  /** The library list shows its ad card after this many books (server setting, 3–30). */
  fun libraryListEvery(): Int = AdsConfig.DEFAULT_LIBRARY_LIST_EVERY
}

/** Shows nothing, ever. Used by ads-noop and when no provider was given to the screen. */
object NoAdProvider : AdProvider {
  private val hidden = MutableStateFlow<AdState>(AdState.Hidden)
  override fun state(slot: AdSlot): StateFlow<AdState> = hidden

  @Composable
  override fun Render(content: AdContent, modifier: Modifier) = Unit
}

/** Screens with a slot provide the injected [AdProvider] here, around their content. */
val LocalAdProvider = staticCompositionLocalOf<AdProvider> { NoAdProvider }

/**
 * The ad of [slot], or nothing. Emits no layout at all until an ad is [AdState.Ready], so the
 * screen never jumps while an ad loads.
 */
@Composable
fun AdBanner(slot: AdSlot, modifier: Modifier = Modifier) {
  val provider = LocalAdProvider.current
  val flow = remember(provider, slot) { provider.state(slot) }
  val state by flow.collectAsState()
  val ready = state as? AdState.Ready ?: return
  provider.Render(ready.ad, modifier)
}
