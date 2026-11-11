package com.quran.mobile.feature.livetv.data

/**
 * Where a live channel's video comes from. The UI only ever sees this type, so moving
 * from the bundled sample clip to a real stream is a change in the repository alone.
 */
sealed interface LiveStreamSource {
  /** Whether the player should loop the source to fake a continuous broadcast. */
  val loops: Boolean

  /** A video bundled in the app's assets, looped to mimic a 24/7 feed. */
  data class LocalAsset(val assetPath: String) : LiveStreamSource {
    override val loops: Boolean = true
  }

  /** Several bundled videos played one after another, the whole list looped. */
  data class LocalPlaylist(val assetPaths: List<String>) : LiveStreamSource {
    init {
      require(assetPaths.isNotEmpty()) { "a playlist needs at least one video" }
    }

    override val loops: Boolean = true
  }

  /** A remote stream, such as an HLS (.m3u8) or DASH (.mpd) live feed. */
  data class Remote(val url: String, val format: StreamFormat) : LiveStreamSource {
    override val loops: Boolean = false
  }
}

enum class StreamFormat { HLS, DASH, PROGRESSIVE }

/**
 * A stream from the admin panel: .m3u8 plays as HLS, .mpd as DASH, anything else progressively.
 * The Tasnim server's official Quran and Sunnah links have no extension and redirect to signed
 * links (tasneem-backend ADR 0009): HLS for the TV channels, DASH for the radios (audio-only track).
 * The player picks the format before following redirects, so they are recognised by path.
 */
fun remoteStreamOf(url: String): LiveStreamSource.Remote {
  val path = url.substringBefore('#').substringBefore('?').lowercase()
  val format = when {
    path.endsWith(".m3u8") || OFFICIAL_TV_PATHS.any(path::endsWith) -> StreamFormat.HLS
    path.endsWith(".mpd") || OFFICIAL_RADIO_PATHS.any(path::endsWith) -> StreamFormat.DASH
    else -> StreamFormat.PROGRESSIVE
  }
  return LiveStreamSource.Remote(url, format)
}

private val OFFICIAL_TV_PATHS = listOf("/api/v1/live/quran", "/api/v1/live/sunnah")
private val OFFICIAL_RADIO_PATHS = listOf("/api/v1/live/quran-radio", "/api/v1/live/sunnah-radio")
