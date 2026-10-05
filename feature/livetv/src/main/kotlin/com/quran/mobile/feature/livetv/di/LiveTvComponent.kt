package com.quran.mobile.feature.livetv.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.livetv.LiveTvActivity
import com.quran.mobile.feature.livetv.radio.RadioActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface LiveTvComponent {
  fun inject(liveTvActivity: LiveTvActivity)
  fun inject(radioActivity: RadioActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): LiveTvComponent
  }
}
