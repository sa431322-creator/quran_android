package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalLiveChannelRepositoryTest {

  private val channels = LocalLiveChannelRepository().channels()

  @Test
  fun everyChannelPlaysBundledSample() {
    assertThat(channels).isNotEmpty()
    val sample = LiveStreamSource.LocalAsset(LocalLiveChannelRepository.SAMPLE_ASSET)
    channels.forEach { assertThat(it.source).isEqualTo(sample) }
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
