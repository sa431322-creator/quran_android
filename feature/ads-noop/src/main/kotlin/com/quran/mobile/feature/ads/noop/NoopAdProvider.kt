package com.quran.mobile.feature.ads.noop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.data.di.AppScope
import com.quran.mobile.feature.ads.AdContent
import com.quran.mobile.feature.ads.AdProvider
import com.quran.mobile.feature.ads.AdSlot
import com.quran.mobile.feature.ads.AdState
import com.quran.mobile.feature.ads.NoAdProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.StateFlow

/** Every slot stays [AdState.Hidden]. */
@ContributesBinding(AppScope::class)
@Inject
class NoopAdProvider : AdProvider {
  override fun state(slot: AdSlot): StateFlow<AdState> = NoAdProvider.state(slot)

  @Composable
  override fun Render(content: AdContent, modifier: Modifier) = Unit
}
