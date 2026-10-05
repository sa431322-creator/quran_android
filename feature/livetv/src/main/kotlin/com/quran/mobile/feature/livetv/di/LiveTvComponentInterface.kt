package com.quran.mobile.feature.livetv.di

import com.quran.data.di.AppScope
import com.quran.mobile.feature.livetv.data.RadioStationRepository
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface LiveTvComponentInterface {
  fun liveTvComponentFactory(): LiveTvComponent.Factory

  // for LiveRadioService, which has no component of its own
  fun radioStationRepository(): RadioStationRepository
}
