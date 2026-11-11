package com.quran.mobile.feature.ads

/**
 * Where an ad may appear. Mirrors `AD_SLOTS` in tasneem-backend `packages/contracts/src/ads.ts`
 * (ADR 0010); never a Quran reading screen. A slot id the server sends but this list does not
 * know is ignored.
 */
enum class AdSlot(val id: String) {
  LIVE_TV("live_tv"),
  RADIO("radio"),
  LIBRARY_LIST("library_list"),
  LIBRARY_DETAILS("library_details");

  companion object {
    fun fromId(id: String): AdSlot? = entries.firstOrNull { it.id == id }
  }
}

/** What the server says to do in a slot. An unknown mode is [OFF]. */
enum class AdMode(val id: String) {
  OFF("off"),
  HOUSE("house"),
  NETWORK("network"),
  HOUSE_THEN_NETWORK("house_then_network");

  val showsHouseAds: Boolean get() = this == HOUSE || this == HOUSE_THEN_NETWORK

  companion object {
    fun fromId(id: String?): AdMode = entries.firstOrNull { it.id == id } ?: OFF
  }
}
