package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalLiveChannelRepositoryTest {

  private val channels = LocalLiveChannelRepository().channels()

  @Test
  fun mainChannelPlaysBothClipsInOrder() {
    assertThat(channels.first().source).isEqualTo(
      LiveStreamSource.LocalPlaylist(
        listOf(LocalLiveChannelRepository.SAMPLE_ASSET, LocalLiveChannelRepository.SECOND_ASSET)
      )
    )
  }

  @Test
  fun otherChannelsPlayBundledSample() {
    val sample = LiveStreamSource.LocalAsset(LocalLiveChannelRepository.SAMPLE_ASSET)
    channels.drop(1).forEach { assertThat(it.source).isEqualTo(sample) }
  }

  @Test
  fun channelIdsAreUnique() {
    assertThat(channels.map { it.id }).containsNoDuplicates()
  }

  @Test
  fun scheduleStartsWithExactlyOneProgramOnAir() {
    channels.forEach { channel ->
      assertThat(channel.schedule.first().slot).isEqualTo(ScheduleSlot.NOW)
      assertThat(channel.schedule.count { it.slot == ScheduleSlot.NOW }).isEqualTo(1)
    }
  }
}
