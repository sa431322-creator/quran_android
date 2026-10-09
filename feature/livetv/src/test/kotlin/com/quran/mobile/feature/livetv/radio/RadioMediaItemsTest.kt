package com.quran.mobile.feature.livetv.radio

import android.net.Uri
import androidx.media3.common.MediaMetadata
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.livetv.data.LocalLiveChannelRepository
import com.quran.mobile.feature.livetv.data.LocalRadioStationRepository
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RadioMediaItemsTest {

  private val repository = LocalRadioStationRepository()

  @Test
  fun singleStationPlaysTheSample() {
    val stations = repository.stations()
    assertThat(stations).hasSize(1)
    val sample = LiveStreamSource.LocalAsset(LocalLiveChannelRepository.SAMPLE_ASSET)
    assertThat(stations.single().source).isEqualTo(sample)
  }

  @Test
  fun lookupById() {
    val first = repository.stations().first()
    assertThat(repository.station(first.id)).isEqualTo(first)
    assertThat(repository.station("missing")).isNull()
  }

  @Test
  fun requestCarriesOnlyTheStationId() {
    val item = RadioMediaItems.request("radio-main")
    assertThat(item.mediaId).isEqualTo("radio-main")
    assertThat(item.localConfiguration).isNull()
  }

  @Test
  fun resolvedItemIsPlayableWithMetadata() {
    val station = repository.stations().first()
    val artwork = Uri.parse("android.resource://pkg/drawable/livetv_logo")
    val item = RadioMediaItems.from(station, artwork)

    assertThat(item.mediaId).isEqualTo(station.id)
    assertThat(item.localConfiguration!!.uri.toString())
      .isEqualTo("asset:///${LocalLiveChannelRepository.SAMPLE_ASSET}")
    assertThat(item.mediaMetadata.title.toString()).isEqualTo(station.name)
    assertThat(item.mediaMetadata.artist.toString()).isEqualTo(station.description)
    assertThat(item.mediaMetadata.artworkUri).isEqualTo(artwork)
    assertThat(item.mediaMetadata.mediaType).isEqualTo(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
  }
}
