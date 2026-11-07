package com.quran.mobile.feature.livetv.data

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.livetv.ui.youTubeEmbedUrl
import org.junit.Test

class YouTubeLinksTest {

  private val video = LiveStreamSource.YouTube(videoId = "dQw4w9WgXcQ")
  private val channel = LiveStreamSource.YouTube(channelId = "UCabcdefghijklmnopqrstuv")

  @Test
  fun readsTheVideoIdFromEveryVideoLinkShape() {
    listOf(
      "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10",
      "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
      "https://youtu.be/dQw4w9WgXcQ?si=abc",
      "https://www.youtube.com/live/dQw4w9WgXcQ?feature=shared",
      "https://www.youtube.com/embed/dQw4w9WgXcQ",
      "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
      "https://www.youtube.com/shorts/dQw4w9WgXcQ",
      "HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ"
    ).forEach { assertThat(ownLiveStreamOf(it)).isEqualTo(video) }
  }

  @Test
  fun aChannelLinkPlaysItsCurrentLiveStream() {
    assertThat(ownLiveStreamOf("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"))
      .isEqualTo(channel)
    assertThat(ownLiveStreamOf("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv/live"))
      .isEqualTo(channel)
    assertThat(ownLiveStreamOf("https://www.youtube.com/embed/live_stream?channel=UCabcdefghijklmnopqrstuv"))
      .isEqualTo(channel)
  }

  @Test
  fun aYouTubeLinkWithoutAnIdIsLeftOut() {
    listOf(
      "https://www.youtube.com/@tasnim",
      "https://www.youtube.com/@tasnim/live",
      "https://www.youtube.com/watch?v=short",
      "https://www.youtube.com/"
    ).forEach { assertThat(ownLiveStreamOf(it)).isNull() }
  }

  @Test
  fun otherLinksStayOnExoPlayer() {
    assertThat(ownLiveStreamOf("https://s.example/live/index.m3u8"))
      .isEqualTo(LiveStreamSource.Remote("https://s.example/live/index.m3u8", StreamFormat.HLS))
    // a lookalike host is not youtube
    assertThat(ownLiveStreamOf("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
      .isInstanceOf(LiveStreamSource.Remote::class.java)
  }

  @Test
  fun radioIgnoresYouTubeLinks() {
    assertThat(directStreamOf("https://youtu.be/dQw4w9WgXcQ")).isNull()
    assertThat(directStreamOf("https://q.example/radio.mp3"))
      .isEqualTo(LiveStreamSource.Remote("https://q.example/radio.mp3", StreamFormat.PROGRESSIVE))
  }

  @Test
  fun onlyOurOwnChannelTakesAYouTubeLink() {
    val links = StreamLinks(
      ownLive = "https://youtu.be/dQw4w9WgXcQ",
      quranLive = "https://youtu.be/dQw4w9WgXcQ",
      sunnahLive = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv",
      ownRadio = "https://youtu.be/dQw4w9WgXcQ"
    )
    // the Quran and Sunnah channels are left out rather than playing youtube
    val channels = ConfiguredLiveChannelRepository { links }.channels()
    assertThat(channels.map { it.id }).containsExactly(ConfiguredLiveChannelRepository.OWN_ID)
    assertThat(channels.map { it.source }).containsExactly(video)
    assertThat(ConfiguredRadioStationRepository { links }.stations()).isEmpty()
  }

  @Test
  fun embedUrlCarriesTheOriginAndKeepsYouTubeFullscreenOff() {
    val origin = "https://com.example.app"
    assertThat(youTubeEmbedUrl(video, origin)).isEqualTo(
      "https://www.youtube.com/embed/dQw4w9WgXcQ?" +
        "enablejsapi=1&autoplay=1&playsinline=1&fs=0&rel=0&hl=fa&origin=$origin"
    )
    assertThat(youTubeEmbedUrl(channel, origin)).startsWith(
      "https://www.youtube.com/embed/live_stream?channel=UCabcdefghijklmnopqrstuv&enablejsapi=1"
    )
  }
}
