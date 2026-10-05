package com.quran.mobile.feature.livetv.data

interface RadioStationRepository {
  /** All stations, in display order. Never empty. */
  fun stations(): List<RadioStation>

  fun station(id: String): RadioStation? = stations().firstOrNull { it.id == id }
}
