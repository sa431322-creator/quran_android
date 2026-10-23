package com.quran.labs.androidquran.tasnim

import com.quran.data.di.AppScope
import com.quran.mobile.feature.livetv.data.LiveAvailability
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** The live and radio switches from the Tasnim server's settings (last saved copy, or the defaults). */
@ContributesBinding(AppScope::class)
@Inject
class TasnimLiveAvailability(private val server: TasnimConfigRepository) : LiveAvailability {
  override fun isLiveAvailable(): Boolean = server.cached().features.live
  override fun isRadioAvailable(): Boolean = server.cached().features.radio
}
