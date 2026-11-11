package com.quran.mobile.feature.ads.house

import com.quran.mobile.feature.ads.AdSlot
import com.quran.mobile.feature.ads.adLinkProblem
import com.squareup.moshi.Moshi
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlin.random.Random

// Pure logic of house ads, kept apart from Android so unit tests cover it: reading the server
// answer forgivingly, picking an ad by weight, and the Tehran day used for counts.

/** One ad from `GET /api/v1/ads`, already filtered by the server. */
data class ServedAd(
  val id: Int,
  val slot: AdSlot,
  val imageUrl: String,
  val width: Int,
  val height: Int,
  val altText: String,
  val actionUrl: String?,
  val actionText: String?,
  val weight: Int,
  val startsAtMs: Long,
  val endsAtMs: Long,
) {
  fun liveAt(nowMs: Long) = nowMs in startsAtMs until endsAtMs
}

data class ServedAds(val ads: List<ServedAd>, val refreshAfterSeconds: Int) {
  fun forSlot(slot: AdSlot, nowMs: Long) = ads.filter { it.slot == slot && it.liveAt(nowMs) }

  companion object {
    val EMPTY = ServedAds(emptyList(), DEFAULT_REFRESH_SECONDS)
    const val DEFAULT_REFRESH_SECONDS = 900
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    /**
     * Reads the answer without being strict: unknown slots and fields are ignored, and an ad with
     * a missing field, a picture on another host, or a link the app would refuse is dropped.
     * [apiHost] is our server's host; pictures from anywhere else are never loaded.
     */
    fun parse(json: String, apiHost: String): ServedAds = runCatching {
      val root = adapter.fromJson(json) as? Map<*, *> ?: return EMPTY
      val refresh = (root["refreshAfterSeconds"] as? Number)?.toInt()
        ?.coerceIn(60, 86_400) ?: DEFAULT_REFRESH_SECONDS
      val slots = root["slots"] as? Map<*, *> ?: return EMPTY
      val ads = slots.flatMap { (key, list) ->
        val slot = AdSlot.fromId(key as? String ?: "") ?: return@flatMap emptyList()
        (list as? List<*>).orEmpty().mapNotNull { readAd(it as? Map<*, *>, slot, apiHost) }
      }
      ServedAds(ads, refresh)
    }.getOrDefault(EMPTY)

    private fun readAd(m: Map<*, *>?, slot: AdSlot, apiHost: String): ServedAd? = runCatching {
      m ?: return null
      val imageUrl = m["imageUrl"] as? String ?: return null
      val host = runCatching { java.net.URI(imageUrl).host }.getOrNull()
      if (host == null || !host.equals(apiHost, ignoreCase = true)) return null
      val link = (m["actionUrl"] as? String)?.takeIf { it.isNotBlank() }
      if (link != null && adLinkProblem(link) != null) return null
      val width = (m["width"] as? Number)?.toInt() ?: return null
      val height = (m["height"] as? Number)?.toInt() ?: return null
      if (width <= 0 || height <= 0) return null
      ServedAd(
        id = (m["id"] as? Number)?.toInt() ?: return null,
        slot = slot,
        imageUrl = imageUrl,
        width = width,
        height = height,
        altText = m["altText"] as? String ?: "",
        actionUrl = link,
        actionText = (m["actionText"] as? String)?.takeIf { it.isNotBlank() },
        weight = ((m["weight"] as? Number)?.toInt() ?: 1).coerceIn(1, 100),
        startsAtMs = Instant.parse(m["startsAt"] as? String ?: return null).toEpochMilli(),
        endsAtMs = Instant.parse(m["endsAt"] as? String ?: return null).toEpochMilli(),
      )
    }.getOrNull()
  }
}

/** Weighted random pick: an ad with weight 20 comes up twice as often as one with weight 10. */
fun <T> pickWeighted(items: List<T>, random: Random, weight: (T) -> Int): T? {
  if (items.isEmpty()) return null
  val total = items.sumOf { weight(it).coerceAtLeast(1) }
  var r = random.nextInt(total)
  for (item in items) {
    r -= weight(item).coerceAtLeast(1)
    if (r < 0) return item
  }
  return items.last()
}

/** Iran has had no daylight saving since 2022: Tehran is always UTC+03:30. */
private val TEHRAN = ZoneOffset.ofHoursMinutes(3, 30)

/** "YYYY-MM-DD" of [epochMs] in Tehran — the day counts are filed under (server contract). */
fun tehranDay(epochMs: Long): String =
  Instant.ofEpochMilli(epochMs).atOffset(TEHRAN).toLocalDate().toString()

/**
 * The impression rule: counted once the ad has been [ready] (≥ 50 % visible, screen resumed,
 * picture drawn) for [IMPRESSION_MS] without a break; once per screen visit. A tap before that
 * counts the view too, and only the first tap of a visit counts, so clicks never exceed
 * impressions.
 */
class ImpressionGate(
  private val scope: kotlinx.coroutines.CoroutineScope,
  /** Kept by the provider per screen, so a view re-created on the same visit remembers it. */
  private val visit: VisitState = VisitState(),
  private val onCount: (impressions: Int, clicks: Int) -> Unit,
) {
  val counted get() = visit.counted
  private var timer: kotlinx.coroutines.Job? = null

  fun update(ready: Boolean) {
    if (counted) return
    if (!ready) {
      timer?.cancel()
      timer = null
    } else if (timer?.isActive != true) {
      timer = scope.launch {
        kotlinx.coroutines.delay(IMPRESSION_MS)
        visit.counted = true
        onCount(1, 0)
      }
    }
  }

  fun click() {
    timer?.cancel()
    if (visit.clicked) return
    onCount(if (visit.counted) 0 else 1, 1)
    visit.counted = true
    visit.clicked = true
  }

  /** The screen came back after being stopped: a new visit may count again. */
  fun newVisit() {
    timer?.cancel()
    timer = null
    visit.counted = false
    visit.clicked = false
  }

  companion object {
    const val IMPRESSION_MS = 1_000L
  }
}

/** What one ad already counted on one screen visit. */
class VisitState(var counted: Boolean = false, var clicked: Boolean = false)
