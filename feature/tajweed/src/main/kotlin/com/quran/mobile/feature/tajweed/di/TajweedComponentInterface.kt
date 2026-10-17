package com.quran.mobile.feature.tajweed.di

import com.quran.data.core.ReadingBookmarkUpdater
import com.quran.data.dao.HighlightsDao
import com.quran.data.di.AppScope
import com.quran.mobile.feature.ayahbookmark.presenter.AyahBookmarkPresenter
import com.quran.mobile.feature.tajweed.TajweedAudio
import com.quran.mobile.feature.tajweed.TajweedSharing
import com.quran.mobile.persiantafsir.data.PersianTafsirRepository
import dev.zacsweers.metro.ContributesTo

/** What the tajweed mushaf takes from the app: tafsir, recitation, bookmarks and sharing. */
@ContributesTo(AppScope::class)
interface TajweedComponentInterface {
  fun persianTafsirRepository(): PersianTafsirRepository
  fun tajweedAudio(): TajweedAudio
  fun tajweedSharing(): TajweedSharing
  fun highlightsDao(): HighlightsDao

  // for the reader's bookmark sheet, which the tajweed mushaf shows too
  fun ayahBookmarkPresenterFactory(): AyahBookmarkPresenter.Factory
  fun readingBookmarkUpdater(): ReadingBookmarkUpdater
}
