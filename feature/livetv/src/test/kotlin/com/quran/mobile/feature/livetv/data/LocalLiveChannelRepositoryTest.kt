package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalLiveChannelRepositoryTest {

  private val channel = LocalLiveChannelRepository().channel()

  @Test
  fun channelPlaysBundledSample() {
    assertThat(channel.source).isEqualTo(LiveStreamSource.LocalAsset("second_stream.mp4"))
  }

  @Test
  fun scheduleIsSortedWithinTheDay() {
    val starts = channel.schedule.map { it.startMinute }
    assertThat(starts).isNotEmpty()
    assertThat(starts).isInStrictOrder()
    starts.forEach { assertThat(it).isIn(0 until 24 * 60) }
  }

  @Test
  fun stationScheduleIsSortedWithinTheDay() {
    LocalRadioStationRepository().stations().forEach { station ->
      val starts = station.schedule.map { it.startMinute }
      assertThat(starts).isNotEmpty()
      assertThat(starts).isInStrictOrder()
      starts.forEach { assertThat(it).isIn(0 until 24 * 60) }
    }
  }
}
