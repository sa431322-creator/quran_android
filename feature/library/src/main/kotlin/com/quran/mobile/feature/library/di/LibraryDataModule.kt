package com.quran.mobile.feature.library.di

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.di.AppScope
import com.quran.mobile.feature.library.data.LibraryApi
import com.quran.mobile.feature.library.data.MockLibraryApi
import com.quran.mobile.feature.library.data.RetrofitLibraryApi
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
class LibraryDataModule {

  /** The Tasnim server when the build names one; otherwise the in-app sample books. */
  @SingleIn(AppScope::class)
  @Provides
  fun provideLibraryApi(
    okHttpClient: OkHttpClient,
    @Named(DependencyInjectionConstants.TASNIM_API_BASE_URL) baseUrl: String
  ): LibraryApi =
    if (baseUrl.isBlank()) MockLibraryApi() else RetrofitLibraryApi(okHttpClient, baseUrl)
}
