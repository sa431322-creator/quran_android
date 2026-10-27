package com.quran.mobile.feature.livetv.data

interface RadioStationRepository {
  /** Stations that have a stream, our own first; the screen opens on the first. May be empty. */
  fun stations(): List<RadioStation>

  fun station(id: String): RadioStation? = stations().firstOrNull { it.id == id }
}
