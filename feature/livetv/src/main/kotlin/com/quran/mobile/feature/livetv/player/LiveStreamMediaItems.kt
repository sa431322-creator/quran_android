package com.quran.mobile.feature.livetv.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import com.quran.mobile.feature.livetv.data.StreamFormat

/** Maps a [LiveStreamSource] to the Media3 [MediaItem]s the player understands. */
object LiveStreamMediaItems {

  /** Every item to queue for [source], in play order. */
  fun playlist(source: LiveStreamSource): List<MediaItem> = when (source) {
    is LiveStreamSource.LocalPlaylist -> source.assetPaths.map(::assetItem)
    else -> listOf(from(source))
  }

  /** A single item for [source]; for a playlist that is its first video. */
  fun from(source: LiveStreamSource): MediaItem = when (source) {
    is LiveStreamSource.LocalAsset -> assetItem(source.assetPath)
    is LiveStreamSource.LocalPlaylist -> assetItem(source.assetPaths.first())
    is LiveStreamSource.Remote ->
      MediaItem.Builder()
        .setUri(source.url)
        .setMimeType(mimeTypeFor(source.format))
        .build()
  }

  private fun assetItem(assetPath: String): MediaItem = MediaItem.fromUri("asset:///$assetPath")

  private fun mimeTypeFor(format: StreamFormat): String? = when (format) {
    StreamFormat.HLS -> MimeTypes.APPLICATION_M3U8
    StreamFormat.DASH -> MimeTypes.APPLICATION_MPD
    // let the player sniff the container
    StreamFormat.PROGRESSIVE -> null
  }
}
