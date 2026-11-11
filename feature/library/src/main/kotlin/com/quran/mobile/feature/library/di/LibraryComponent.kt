package com.quran.mobile.feature.library.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.library.LibraryActivity
import com.quran.mobile.feature.library.details.LibraryBookActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface LibraryComponent {
  fun inject(libraryActivity: LibraryActivity)

  fun inject(libraryBookActivity: LibraryBookActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): LibraryComponent
  }
}
