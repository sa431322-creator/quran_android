package com.quran.mobile.feature.livetv.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import com.quran.mobile.feature.livetv.data.StreamFormat

/** Maps a [LiveStreamSource] to the Media3 [MediaItem] the player understands. */
object LiveStreamMediaItems {

  fun from(source: LiveStreamSource): MediaItem = when (source) {
    is LiveStreamSource.LocalAsset -> MediaItem.fromUri("asset:///${source.assetPath}")
    is LiveStreamSource.Remote ->
      MediaItem.Builder()
        .setUri(source.url)
        .setMimeType(mimeTypeFor(source.format))
        .build()
  }

  private fun mimeTypeFor(format: StreamFormat): String? = when (format) {
    StreamFormat.HLS -> MimeTypes.APPLICATION_M3U8
    StreamFormat.DASH -> MimeTypes.APPLICATION_MPD
    // let the player sniff the container
    StreamFormat.PROGRESSIVE -> null
  }
}
