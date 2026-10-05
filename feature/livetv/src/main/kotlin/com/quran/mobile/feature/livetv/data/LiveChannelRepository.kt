package com.quran.mobile.feature.livetv.data

interface LiveChannelRepository {
  /** All channels, in display order. Never empty. */
  fun channels(): List<LiveChannel>
}
