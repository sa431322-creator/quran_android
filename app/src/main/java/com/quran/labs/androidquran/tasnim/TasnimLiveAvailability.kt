package com.quran.labs.androidquran.tasnim

import com.quran.data.di.AppScope
import com.quran.mobile.feature.livetv.data.LiveAvailability
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * The live and radio switches from the Tasnim server's settings (last saved copy, or the defaults).
 * A build without a server has no switches to read, so both stay on and play the bundled clip.
 */
@ContributesBinding(AppScope::class)
@Inject
class TasnimLiveAvailability(private val server: TasnimConfigRepository) : LiveAvailability {
  override fun isLiveAvailable(): Boolean = !server.hasServer || server.cached().features.live
  override fun isRadioAvailable(): Boolean = !server.hasServer || server.cached().features.radio
}
