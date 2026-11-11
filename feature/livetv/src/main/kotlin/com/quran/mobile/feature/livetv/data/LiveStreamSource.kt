package com.quran.mobile.feature.livetv.data

/**
 * Where a live channel's video comes from. The UI only ever sees this type, so moving
 * from the bundled sample clip to a real stream is a change in the repository alone.
 */
sealed interface LiveStreamSource {
  /** Whether the player should loop the source to fake a continuous broadcast. */
  val loops: Boolean

  /** A video bundled in the app's assets, looped to mimic a 24/7 feed. */
  data class LocalAsset(val assetPath: String) : LiveStreamSource {
    override val loops: Boolean = true
  }

  /** Several bundled videos played one after another, the whole list looped. */
  data class LocalPlaylist(val assetPaths: List<String>) : LiveStreamSource {
    init {
      require(assetPaths.isNotEmpty()) { "a playlist needs at least one video" }
    }

    override val loops: Boolean = true
  }

  /** A remote stream, such as an HLS (.m3u8) or DASH (.mpd) live feed. */
  data class Remote(val url: String, val format: StreamFormat) : LiveStreamSource {
    override val loops: Boolean = false
  }

  /**
   * A YouTube video or a channel's current live stream. It plays in YouTube's own embedded
   * player, not ExoPlayer, since YouTube serves no stream link an app may play directly.
   */
  data class YouTube(val videoId: String? = null, val channelId: String? = null) : LiveStreamSource {
    init {
      require((videoId == null) != (channelId == null)) { "a video id or a channel id, not both" }
    }

    override val loops: Boolean = false
  }
}

enum class StreamFormat { HLS, DASH, PROGRESSIVE }

/**
 * A stream from the admin panel: .m3u8 plays as HLS, .mpd as DASH, anything else progressively.
 * The Tasnim server's official Quran and Sunnah links have no extension and redirect to signed
 * links (tasneem-backend ADR 0009): HLS for the TV channels, DASH for the radios (audio-only track).
 * The player picks the format before following redirects, so they are recognised by path.
 */
fun remoteStreamOf(url: String): LiveStreamSource.Remote {
  val path = url.substringBefore('#').substringBefore('?').lowercase()
  val format = when {
    path.endsWith(".m3u8") || OFFICIAL_TV_PATHS.any(path::endsWith) -> StreamFormat.HLS
    path.endsWith(".mpd") || OFFICIAL_RADIO_PATHS.any(path::endsWith) -> StreamFormat.DASH
    else -> StreamFormat.PROGRESSIVE
  }
  return LiveStreamSource.Remote(url, format)
}

/**
 * Our own channel's live video link from the admin panel, the only link that may be YouTube: a
 * YouTube link plays in YouTube's player, anything else as [remoteStreamOf]. Null for a YouTube
 * link that names no video or channel id (such as an @handle, which only the YouTube Data API
 * can resolve), so the channel is left out.
 */
fun ownLiveStreamOf(url: String): LiveStreamSource? =
  if (isYouTubeLink(url)) youTubeSourceOf(url) else remoteStreamOf(url)

/**
 * Any other link from the admin panel (the Quran and Sunnah channels, and every radio), which
 * plays only in ExoPlayer; null for a YouTube link, so that channel or station is left out.
 */
fun directStreamOf(url: String): LiveStreamSource.Remote? =
  if (isYouTubeLink(url)) null else remoteStreamOf(url)

private val YOUTUBE_HOSTS = setOf(
  "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com"
)
private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")

// any youtube.com subdomain (music., gaming., …) counts, so it never reaches ExoPlayer
internal fun isYouTubeLink(url: String): Boolean =
  hostOf(url)?.let { it in YOUTUBE_HOSTS || it.endsWith(".youtube.com") } == true

/**
 * Reads youtu.be/ID, /watch?v=ID, /live/ID, /embed/ID, /shorts/ID and, for a channel's
 * current live stream, /channel/UC…(/live) or /embed/live_stream?channel=UC….
 */
internal fun youTubeSourceOf(url: String): LiveStreamSource.YouTube? {
  val host = hostOf(url) ?: return null
  val afterHost = url.substringAfter("://").substringAfter('/', "").substringBefore('#')
  val segments = afterHost.substringBefore('?').split('/').filter { it.isNotEmpty() }
  val query = afterHost.substringAfter('?', "").split('&').associate {
    it.substringBefore('=') to it.substringAfter('=', "")
  }

  val video = when {
    host == "youtu.be" -> segments.firstOrNull()
    segments.firstOrNull() == "watch" -> query["v"]
    // embed/live_stream names a channel, and "live_stream" is itself 11 id-like characters
    segments.size >= 2 && segments[0] in setOf("live", "embed", "shorts") &&
      segments[1] != "live_stream" -> segments[1]
    else -> null
  }
  if (video != null && VIDEO_ID.matches(video)) return LiveStreamSource.YouTube(videoId = video)

  val channel = when {
    segments.firstOrNull() == "channel" -> segments.getOrNull(1)
    segments.getOrNull(1) == "live_stream" -> query["channel"]
    else -> null
  }
  return channel?.takeIf { CHANNEL_ID.matches(it) }?.let { LiveStreamSource.YouTube(channelId = it) }
}

private fun hostOf(url: String): String? {
  if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
    return null
  }
  return url.substringAfter("://").substringBefore('/').substringBefore('?')
    .substringBefore('#').substringAfter('@').substringBefore(':').lowercase()
}

private val OFFICIAL_TV_PATHS = listOf("/api/v1/live/quran", "/api/v1/live/sunnah")
private val OFFICIAL_RADIO_PATHS = listOf("/api/v1/live/quran-radio", "/api/v1/live/sunnah-radio")
