package com.quran.labs.androidquran.di.component.activity

import com.quran.data.di.QuranReadingScope
import com.quran.data.di.QuranScope
import com.quran.labs.androidquran.ui.TajweedReaderActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.mobile.di.QuranReadingActivityComponent
import com.quran.mobile.feature.audiobar.AudioBarWrapper
import com.quran.mobile.feature.ayahbookmark.AyahBookmarkWrapper
import com.quran.mobile.feature.ayahbookmark.readingbookmark.ReadingBookmarkSheetWrapper
import com.quran.mobile.feature.qarilist.QariListWrapper
import com.quran.page.common.toolbar.AyahToolBar
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

/**
 * The tajweed reader's graph: the same reading scope as [PagerActivityComponent], so it gets the
 * reader's own ayah toolbar, audio bar, qari list and bookmark sheets.
 */
@QuranScope
@GraphExtension(QuranReadingScope::class)
interface TajweedReaderComponent : QuranReadingActivityComponent {
  fun inject(tajweedReaderActivity: TajweedReaderActivity)
  fun inject(ayahToolBar: AyahToolBar)

  fun inject(qariListWrapper: QariListWrapper)
  fun inject(audioBarWrapper: AudioBarWrapper)
  fun inject(ayahBookmarkWrapper: AyahBookmarkWrapper)
  fun inject(readingBookmarkSheetWrapper: ReadingBookmarkSheetWrapper)

  @GraphExtension.Factory
  interface Factory {
    fun generate(@Provides ayahSelectedListener: AyahSelectedListener): TajweedReaderComponent
  }
}
