package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfiguredRepositoriesTest {

  private val withoutServer = StreamLinksProvider { null }

  @Test
  fun withoutServerOnlyOurChannelPlaysTheBundledSample() {
    val channels = ConfiguredLiveChannelRepository(withoutServer).channels()
    assertThat(channels.map { it.id }).containsExactly(ConfiguredLiveChannelRepository.OWN_ID)
    assertThat(channels.single().source).isEqualTo(LiveStreamSource.LocalAsset("qurhan_42.mp4"))

    val stations = ConfiguredRadioStationRepository(withoutServer).stations()
    assertThat(stations.map { it.id }).containsExactly(ConfiguredRadioStationRepository.OWN_ID)
  }

  @Test
  fun bothChannelsUseTheAdminLinksOurOwnFirst() {
    val links = StreamLinks(
      ownLive = "https://s.example/live/index.m3u8",
      quranLive = "https://q.example/tv.mpd?token=1",
      ownRadio = "https://s.example/radio",
      quranRadio = "https://q.example/radio.mp3"
    )
    val channels = ConfiguredLiveChannelRepository { links }.channels()
    assertThat(channels.map { it.source }).containsExactly(
      LiveStreamSource.Remote("https://s.example/live/index.m3u8", StreamFormat.HLS),
      LiveStreamSource.Remote("https://q.example/tv.mpd?token=1", StreamFormat.DASH)
    ).inOrder()
    assertThat(channels.last().schedule).isEmpty()

    val stations = ConfiguredRadioStationRepository { links }.stations()
    assertThat(stations.map { it.id }).containsExactly(
      ConfiguredRadioStationRepository.OWN_ID,
      ConfiguredRadioStationRepository.QURAN_ID
    ).inOrder()
    assertThat(stations.map { it.source }).containsExactly(
      LiveStreamSource.Remote("https://s.example/radio", StreamFormat.PROGRESSIVE),
      LiveStreamSource.Remote("https://q.example/radio.mp3", StreamFormat.PROGRESSIVE)
    ).inOrder()
  }

  @Test
  fun aChannelWithoutALinkIsLeftOut() {
    val onlyQuran = StreamLinks(quranLive = "https://q.example/tv.m3u8")
    assertThat(ConfiguredLiveChannelRepository { onlyQuran }.channels().map { it.id })
      .containsExactly(ConfiguredLiveChannelRepository.QURAN_ID)
    assertThat(ConfiguredRadioStationRepository { onlyQuran }.stations()).isEmpty()
  }

  @Test
  fun ourScheduleIsSortedWithinTheDay() {
    val schedules = listOf(
      ConfiguredLiveChannelRepository(withoutServer).channels().single().schedule,
      ConfiguredRadioStationRepository(withoutServer).stations().single().schedule
    )
    schedules.forEach { schedule ->
      val starts = schedule.map { it.startMinute }
      assertThat(starts).isNotEmpty()
      assertThat(starts).isInStrictOrder()
      starts.forEach { assertThat(it).isIn(0 until 24 * 60) }
    }
  }
}
