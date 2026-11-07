package com.quran.mobile.feature.ads

import com.squareup.moshi.Moshi

/**
 * The `ads` section of the server's app settings (the kill switch). Read apart from the rest of
 * the settings and never strictly: an unknown slot is ignored, an unknown mode or network means
 * off/none, and a broken section means "no ads" — it never breaks the other settings.
 */
data class AdsConfig(
  val enabled: Boolean = false,
  val slots: Map<AdSlot, AdMode> = emptyMap(),
  val libraryListEvery: Int = DEFAULT_LIBRARY_LIST_EVERY,
  /** A hint only: `none`, `admob`, … ; unknown values read as `none`. */
  val network: String = "none",
  val blockedVersions: Set<Int> = emptySet(),
) {
  fun mode(slot: AdSlot): AdMode = slots[slot] ?: AdMode.OFF

  /** Whether this app version shows house ads in [slot] at all. */
  fun showsHouseAds(slot: AdSlot, versionCode: Int): Boolean =
    enabled && versionCode !in blockedVersions && mode(slot).showsHouseAds

  companion object {
    const val DEFAULT_LIBRARY_LIST_EVERY = 8
    val OFF = AdsConfig()
    private val KNOWN_NETWORKS = setOf("none", "admob", "tapsell", "adivery")
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    /** [json] is the whole app-config body; its `ads` key is read, everything else ignored. */
    fun fromAppConfigJson(json: String?): AdsConfig = runCatching {
      val root = json?.let { adapter.fromJson(it) } as? Map<*, *> ?: return OFF
      from(root["ads"])
    }.getOrDefault(OFF)

    /** [section] is the decoded `ads` object (maps, lists, numbers as Double). */
    fun from(section: Any?): AdsConfig = runCatching {
      val ads = section as? Map<*, *> ?: return OFF
      val slots = (ads["slots"] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
        val slot = AdSlot.fromId(key as? String ?: return@mapNotNull null) ?: return@mapNotNull null
        slot to AdMode.fromId(value as? String)
      }.toMap()
      val every = (ads["libraryListEvery"] as? Number)?.toInt()
        ?.takeIf { it in 3..30 } ?: DEFAULT_LIBRARY_LIST_EVERY
      val network = (ads["network"] as? String)?.takeIf { it in KNOWN_NETWORKS } ?: "none"
      val blocked = (ads["blockedVersions"] as? List<*>).orEmpty()
        .mapNotNull { (it as? Number)?.toInt() }.toSet()
      AdsConfig(
        enabled = ads["enabled"] as? Boolean ?: false,
        slots = slots,
        libraryListEvery = every,
        network = network,
        blockedVersions = blocked,
      )
    }.getOrDefault(OFF)
  }
}
