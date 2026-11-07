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
    ).forEach { assertThat(liveStreamOf(it)).isEqualTo(video) }
  }

  @Test
  fun aChannelLinkPlaysItsCurrentLiveStream() {
    assertThat(liveStreamOf("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"))
      .isEqualTo(channel)
    assertThat(liveStreamOf("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv/live"))
      .isEqualTo(channel)
    assertThat(liveStreamOf("https://www.youtube.com/embed/live_stream?channel=UCabcdefghijklmnopqrstuv"))
      .isEqualTo(channel)
  }

  @Test
  fun aYouTubeLinkWithoutAnIdIsLeftOut() {
    listOf(
      "https://www.youtube.com/@tasnim",
      "https://www.youtube.com/@tasnim/live",
      "https://www.youtube.com/watch?v=short",
      "https://www.youtube.com/"
    ).forEach { assertThat(liveStreamOf(it)).isNull() }
  }

  @Test
  fun otherLinksStayOnExoPlayer() {
    assertThat(liveStreamOf("https://s.example/live/index.m3u8"))
      .isEqualTo(LiveStreamSource.Remote("https://s.example/live/index.m3u8", StreamFormat.HLS))
    // a lookalike host is not youtube
    assertThat(liveStreamOf("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
      .isInstanceOf(LiveStreamSource.Remote::class.java)
  }

  @Test
  fun radioIgnoresYouTubeLinks() {
    assertThat(radioStreamOf("https://youtu.be/dQw4w9WgXcQ")).isNull()
    assertThat(radioStreamOf("https://q.example/radio.mp3"))
      .isEqualTo(LiveStreamSource.Remote("https://q.example/radio.mp3", StreamFormat.PROGRESSIVE))
  }

  @Test
  fun channelsWithYouTubeLinksUseTheYouTubePlayer() {
    val links = StreamLinks(
      ownLive = "https://youtu.be/dQw4w9WgXcQ",
      quranLive = "https://www.youtube.com/@quran",
      ownRadio = "https://youtu.be/dQw4w9WgXcQ"
    )
    val channels = ConfiguredLiveChannelRepository { links }.channels()
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
