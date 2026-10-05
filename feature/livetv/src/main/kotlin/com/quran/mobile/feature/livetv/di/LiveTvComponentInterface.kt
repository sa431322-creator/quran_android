package com.quran.mobile.feature.livetv.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface LiveTvComponentInterface {
  fun liveTvComponentFactory(): LiveTvComponent.Factory
}
