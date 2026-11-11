package com.quran.labs.androidquran.core.worker.di

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.labs.androidquran.feature.audio.api.AudioUpdateService
import com.quran.labs.androidquran.tasnim.TasnimDataHost
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import retrofit2.Retrofit.Builder
import retrofit2.converter.moshi.MoshiConverterFactory

@BindingContainer
object AudioUpdateModule {

  @Provides
  fun provideAudioUpdateService(
    @Named(DependencyInjectionConstants.TASNIM_API_BASE_URL) tasnimBaseUrl: String
  ): AudioUpdateService {
    val retrofit = Builder()
        .baseUrl(TasnimDataHost.of(tasnimBaseUrl))
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
    return retrofit.create(AudioUpdateService::class.java)
  }
}
