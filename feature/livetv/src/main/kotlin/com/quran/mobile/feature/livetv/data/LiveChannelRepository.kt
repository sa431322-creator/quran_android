package com.quran.mobile.feature.livetv.data

interface LiveChannelRepository {
  /** Channels that have a stream, our own first; the screen opens on the first. May be empty. */
  fun channels(): List<LiveChannel>

  /** Our own channel's name, for the home screen's «پخش زنده» card. */
  fun ownChannelName(): String
}
