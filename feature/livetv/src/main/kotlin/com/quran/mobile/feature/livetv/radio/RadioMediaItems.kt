package com.quran.mobile.feature.livetv.radio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.quran.mobile.feature.livetv.data.RadioStation
import com.quran.mobile.feature.livetv.player.LiveStreamMediaItems

/** Maps a [RadioStation] to the playable [MediaItem] shown in the media notification. */
object RadioMediaItems {

  /** A reference sent from the UI; the service resolves it with [from]. */
  fun request(stationId: String): MediaItem = MediaItem.Builder().setMediaId(stationId).build()

  fun from(station: RadioStation, artworkUri: Uri?): MediaItem =
    LiveStreamMediaItems.from(station.source)
      .buildUpon()
      .setMediaId(station.id)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(station.name)
          .setArtist(station.description)
          .setArtworkUri(artworkUri)
          .setIsPlayable(true)
          .setIsBrowsable(false)
          .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
          .build()
      )
      .build()
}
