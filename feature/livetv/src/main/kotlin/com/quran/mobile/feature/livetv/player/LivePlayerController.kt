package com.quran.mobile.feature.livetv.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the single [ExoPlayer] used by the Live TV screen and is the only class that
 * touches Media3 player APIs. Callers pair [initialize] with [release]; the player
 * does not exist between the two, so nothing is held while the screen is hidden.
 * Mute and subtitle choices survive a release so they still apply after backgrounding.
 *
 * This player is separate from the one in AudioService, which strips video renderers.
 */
class LivePlayerController(
  context: Context,
  private val source: LiveStreamSource
) {
  private val appContext = context.applicationContext
  private var player: ExoPlayer? = null
  private var clockSync: BroadcastClockSync? = null

  private val _state = MutableStateFlow<LivePlayerState>(LivePlayerState.Buffering)
  val state: StateFlow<LivePlayerState> = _state.asStateFlow()

  private val _isMuted = MutableStateFlow(false)
  val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

  private val _subtitlesEnabled = MutableStateFlow(false)
  val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

  /** Whether the current stream carries any subtitle track. */
  private val _hasSubtitles = MutableStateFlow(false)
  val hasSubtitles: StateFlow<Boolean> = _hasSubtitles.asStateFlow()

  private val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      player?.let(::updateState)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      player?.let(::updateState)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      player?.let(::updateState)
    }

    override fun onTracksChanged(tracks: Tracks) {
      _hasSubtitles.value = tracks.containsType(C.TRACK_TYPE_TEXT)
    }

    override fun onPlayerError(error: PlaybackException) {
      _state.value = LivePlayerState.Error(error.errorCodeName)
    }
  }

  /** Builds and starts the player, or returns the current one if already running. */
  fun initialize(): Player {
    player?.let { return it }

    _state.value = LivePlayerState.Buffering
    val audioAttributes = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
      .build()

    val exoPlayer = ExoPlayer.Builder(appContext)
      .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
      .build()
    player = exoPlayer
    val sync = BroadcastClockSync(exoPlayer, shouldSync = { source.loops })
    clockSync = sync
    exoPlayer.addListener(listener)
    exoPlayer.addListener(sync)
    exoPlayer.volume = if (_isMuted.value) 0f else 1f
    applySubtitlePreference(exoPlayer)
    exoPlayer.setMediaItem(LiveStreamMediaItems.from(source))
    exoPlayer.repeatMode = if (source.loops) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    exoPlayer.playWhenReady = true
    exoPlayer.prepare()
    return exoPlayer
  }

  /** Pauses, or resumes at the live position. */
  fun togglePlayPause() {
    val player = player ?: return
    if (player.playWhenReady) player.pause() else player.play()
  }

  fun toggleMute() {
    val muted = !_isMuted.value
    _isMuted.value = muted
    player?.volume = if (muted) 0f else 1f
  }

  /** Turns Persian subtitles on or off; they show whenever the stream carries them. */
  fun toggleSubtitles() {
    _subtitlesEnabled.value = !_subtitlesEnabled.value
    player?.let(::applySubtitlePreference)
  }

  /** Re-prepares after an error. */
  fun retry() {
    val player = player ?: return
    _state.value = LivePlayerState.Buffering
    clockSync?.reset()
    player.prepare()
  }

  /** Destroys the player. Safe to call more than once. */
  fun release() {
    player?.let { player ->
      player.removeListener(listener)
      clockSync?.let(player::removeListener)
      player.release()
    }
    player = null
    clockSync = null
    _hasSubtitles.value = false
    _state.value = LivePlayerState.Buffering
  }

  private fun applySubtitlePreference(player: Player) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
      .setPreferredTextLanguage(SUBTITLE_LANGUAGE)
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !_subtitlesEnabled.value)
      .build()
  }

  private fun updateState(player: Player) {
    val error = player.playerError
    _state.value = when {
      error != null -> LivePlayerState.Error(error.errorCodeName)
      player.isPlaying -> LivePlayerState.Playing
      !player.playWhenReady -> LivePlayerState.Paused
      player.playbackState == Player.STATE_READY -> LivePlayerState.Paused
      else -> LivePlayerState.Buffering
    }
  }

  private companion object {
    const val SUBTITLE_LANGUAGE = "fa"
  }
}
