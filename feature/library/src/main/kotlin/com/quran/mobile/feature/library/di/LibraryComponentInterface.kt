package com.quran.mobile.feature.library.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface LibraryComponentInterface {
  fun libraryComponentFactory(): LibraryComponent.Factory
}
