package com.quran.mobile.feature.ads.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quran.data.di.AppScope
import com.quran.mobile.feature.ads.AdSlot
import com.quran.mobile.feature.ads.NetworkAds
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay

/**
 * Stands in for AdMob until step 6.2 brings the SDK: answers like a network that always has an
 * ad, after a short wait, and draws a grey banner-size box. It makes no request of any kind.
 */
@ContributesIntoSet(AppScope::class)
@Inject
class PlaceholderNetworkAds : NetworkAds {
  override val id: String = "admob"

  override suspend fun load(slot: AdSlot): Boolean {
    delay(LOAD_MS)
    return true
  }

  @Composable
  override fun Render(slot: AdSlot, modifier: Modifier) {
    Box(
      modifier
        .fillMaxWidth()
        .height(BANNER_HEIGHT_DP.dp)
        .background(Color(0xFFBDBDBD)),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = "network ad placeholder · ${slot.id}", color = Color.Black)
    }
  }

  private companion object {
    const val LOAD_MS = 300L
    const val BANNER_HEIGHT_DP = 50
  }
}
