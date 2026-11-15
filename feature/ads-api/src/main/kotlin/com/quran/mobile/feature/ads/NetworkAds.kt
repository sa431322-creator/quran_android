package com.quran.mobile.feature.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An ad network (AdMob in the Google Play build, ADR 0010 / ads plan §9). Only builds made with
 * `-PnetworkAds` bind one; every other build has none and shows house ads only.
 *
 * The network never decides where or whether it shows: the server settings do ([AdsConfig]),
 * and nothing here runs until a screen with a slot that allows a network ad is opened.
 */
interface NetworkAds {
  /** The value of `ads.network` in the server settings that switches this network on. */
  val id: String

  /**
   * Asks for one ad for [slot] and returns true once it is ready to draw. False when there is
   * none (no fill, no consent, no connection). The caller stops waiting after ~3 seconds.
   */
  suspend fun load(slot: AdSlot): Boolean

  /** Draws the ad [load] reported, in a box of fixed height. The network draws its own view. */
  @Composable
  fun Render(slot: AdSlot, modifier: Modifier)
}
