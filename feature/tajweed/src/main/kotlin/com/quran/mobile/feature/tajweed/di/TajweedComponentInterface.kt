package com.quran.mobile.feature.tajweed.di

import com.quran.data.di.AppScope
import com.quran.mobile.feature.tajweed.TajweedAudio
import com.quran.mobile.persiantafsir.data.PersianTafsirRepository
import dev.zacsweers.metro.ContributesTo

/** What the tajweed mushaf takes from the app: the Persian tafsir and the recitation. */
@ContributesTo(AppScope::class)
interface TajweedComponentInterface {
  fun persianTafsirRepository(): PersianTafsirRepository
  fun tajweedAudio(): TajweedAudio
}
