package com.quran.mobile.feature.livetv.data

interface LiveChannelRepository {
  /** Tasnim broadcasts a single video channel. */
  fun channel(): LiveChannel
}
