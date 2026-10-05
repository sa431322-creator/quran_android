package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalLiveChannelRepositoryTest {

  private val channel = LocalLiveChannelRepository().currentChannel()

  @Test
  fun channelPlaysBundledSample() {
    assertThat(channel.title).isEqualTo("MVP Local Preview")
    assertThat(channel.source)
      .isEqualTo(LiveStreamSource.LocalAsset(LocalLiveChannelRepository.SAMPLE_ASSET))
  }

  @Test
  fun guideHasExactlyOneProgramOnAir() {
    assertThat(channel.guide).isNotEmpty()
    assertThat(channel.guide.count { it.isOnAir }).isEqualTo(1)
  }
}
