package com.quran.mobile.feature.livetv.player

import androidx.media3.common.MimeTypes
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import com.quran.mobile.feature.livetv.data.StreamFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiveStreamMediaItemsTest {

  @Test
  fun localAssetMapsToAssetUri() {
    val item = LiveStreamMediaItems.from(LiveStreamSource.LocalAsset("sample_stream.mp4"))
    assertThat(item.localConfiguration!!.uri.toString()).isEqualTo("asset:///sample_stream.mp4")
  }

  @Test
  fun hlsSetsM3u8MimeType() {
    val url = "https://example.com/live/master.m3u8"
    val item = LiveStreamMediaItems.from(LiveStreamSource.Remote(url, StreamFormat.HLS))
    assertThat(item.localConfiguration!!.uri.toString()).isEqualTo(url)
    assertThat(item.localConfiguration!!.mimeType).isEqualTo(MimeTypes.APPLICATION_M3U8)
  }

  @Test
  fun dashSetsMpdMimeType() {
    val item = LiveStreamMediaItems.from(
      LiveStreamSource.Remote("https://example.com/live/manifest.mpd", StreamFormat.DASH)
    )
    assertThat(item.localConfiguration!!.mimeType).isEqualTo(MimeTypes.APPLICATION_MPD)
  }

  @Test
  fun onlyLocalSourcesLoop() {
    assertThat(LiveStreamSource.LocalAsset("a.mp4").loops).isTrue()
    assertThat(LiveStreamSource.Remote("https://x/y.m3u8", StreamFormat.HLS).loops).isFalse()
  }
}
