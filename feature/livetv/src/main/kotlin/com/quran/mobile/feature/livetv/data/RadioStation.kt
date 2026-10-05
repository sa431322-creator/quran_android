package com.quran.mobile.feature.livetv.data

/** An audio station. Names are placeholders until real stations exist. */
data class RadioStation(
  val id: String,
  val name: String,
  val description: String,
  /** A live broadcast rather than an on-demand playlist; shows the «زنده» marker. */
  val isLive: Boolean,
  val source: LiveStreamSource
)
