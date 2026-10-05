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

  /** A remote stream, such as an HLS (.m3u8) or DASH (.mpd) live feed. */
  data class Remote(val url: String, val format: StreamFormat) : LiveStreamSource {
    override val loops: Boolean = false
  }
}

enum class StreamFormat { HLS, DASH, PROGRESSIVE }
