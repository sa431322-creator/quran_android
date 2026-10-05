package com.quran.mobile.feature.livetv.data

data class LiveChannel(
  val id: String,
  val title: String,
  val source: LiveStreamSource,
  val guide: List<ProgramGuideEntry>
)

data class ProgramGuideEntry(
  val startTime: String,
  val title: String,
  val isOnAir: Boolean = false
)
