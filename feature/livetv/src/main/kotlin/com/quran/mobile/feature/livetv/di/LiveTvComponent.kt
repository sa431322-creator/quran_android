package com.quran.mobile.feature.livetv.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.livetv.LiveTvActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface LiveTvComponent {
  fun inject(liveTvActivity: LiveTvActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): LiveTvComponent
  }
}
