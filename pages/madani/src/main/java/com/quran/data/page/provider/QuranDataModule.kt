package com.quran.data.page.provider

import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.page.provider.madani.MadaniV2ImagesPageProvider
import com.quran.data.page.provider.madani.MadaniV2PageProvider
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.data.pageinfo.mapper.IdentityAyahMapper
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.drawing.AyahMarkerDrawable
import com.quran.labs.androidquran.common.drawing.helper.AyahMarkerDrawer
import com.quran.labs.androidquran.common.drawing.helper.SuraHeaderDrawer
import com.quran.labs.androidquran.pages.common.madani.MadaniPageProvider
import com.quran.labs.androidquran.pages.common.madani.upgrade.MadaniDataUpgrade
import com.quran.labs.androidquran.pages.common.madani.upgrade.MadaniPreferencesUpgrade
import com.quran.page.common.draw.ImageDrawHelper
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@BindingContainer
object QuranDataModule {

  @Named(DependencyInjectionConstants.FALLBACK_PAGE_TYPE)
  @JvmStatic
  @Provides
  fun provideFallbackPageType(): String = "madani"

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani")
  fun provideMadaniPageSet(): PageProvider {
    return MadaniPageProvider()
  }

  /** The Madinah mushaf V2 drawn as text; its pages come from the app's PageViewFactoryProvider. */
  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(MadaniV2PageProvider.PAGE_TYPE)
  fun provideMadaniV2PageSet(): PageProvider {
    return MadaniV2PageProvider()
  }

  /** The Madinah mushaf V2 as page images, from the Tasnim server. */
  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(MadaniV2ImagesPageProvider.PAGE_TYPE)
  fun provideMadaniV2ImagesPageSet(
    @Named(DependencyInjectionConstants.TASNIM_API_BASE_URL) tasnimBaseUrl: String
  ): PageProvider {
    return MadaniV2ImagesPageProvider(tasnimBaseUrl)
  }

  // Upstream's other mushafs (new Madani, line-by-line, Shemerly, tajweed images) are open source
  // now, but Tasnim reads the Madinah V2 mushaf only, so they are not registered here.

  @JvmStatic
  @Provides
  @ElementsIntoSet
  fun provideImageDrawHelpers(): Set<ImageDrawHelper> {
    return setOf(
        AyahMarkerDrawer(AyahMarkerDrawable(0x79004d40, 0x79e0f2f1)),
        SuraHeaderDrawer(
          com.quran.labs.androidquran.common.drawing.R.drawable.chapter_hdr,
          0xff004d40.toInt()
        )
    )
  }

  @JvmStatic
  @Provides
  fun provideLocalDataUpgrade(madaniDataUpgrade: MadaniDataUpgrade): LocalDataUpgrade = madaniDataUpgrade

  @JvmStatic
  @Provides
  fun providePreferencesUpgrade(madaniPreferencesUpgrade: MadaniPreferencesUpgrade): PreferencesUpgrade =
    madaniPreferencesUpgrade

  @JvmStatic
  @Provides
  fun provideAyahMapper(): AyahMapper = IdentityAyahMapper()
}
