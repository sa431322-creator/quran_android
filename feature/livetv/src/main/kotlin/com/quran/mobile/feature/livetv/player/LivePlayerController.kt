package com.quran.mobile.feature.livetv.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the single [ExoPlayer] used by the Live TV screen and is the only class that
 * touches Media3 player APIs. Callers pair [initialize] with [release]; the player
 * does not exist between the two, so nothing is held while the screen is hidden.
 *
 * This player is separate from the one in AudioService, which strips video renderers.
 */
class LivePlayerController(
  context: Context,
  private val source: LiveStreamSource
) {
  private val appContext = context.applicationContext
  private var player: ExoPlayer? = null
  private var alignedToBroadcastClock = false

  private val _state = MutableStateFlow<LivePlayerState>(LivePlayerState.Buffering)
  val state: StateFlow<LivePlayerState> = _state.asStateFlow()

  private val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      val player = player ?: return
      if (playbackState == Player.STATE_READY) alignToBroadcastClock(player)
      updateState(player)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      player?.let(::updateState)
    }

    override fun onPlayerError(error: PlaybackException) {
      _state.value = LivePlayerState.Error(error.errorCodeName)
    }
  }

  /** Builds and starts the player, or returns the current one if already running. */
  fun initialize(): Player {
    player?.let { return it }

    alignedToBroadcastClock = false
    _state.value = LivePlayerState.Buffering
    val audioAttributes = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
      .build()

    val exoPlayer = ExoPlayer.Builder(appContext)
      .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
      .build()
    player = exoPlayer
    exoPlayer.addListener(listener)
    exoPlayer.setMediaItem(LiveStreamMediaItems.from(source))
    exoPlayer.repeatMode = if (source.loops) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    exoPlayer.playWhenReady = true
    exoPlayer.prepare()
    return exoPlayer
  }

  /** Re-prepares after an error. */
  fun retry() {
    val player = player ?: return
    _state.value = LivePlayerState.Buffering
    player.prepare()
  }

  /** Destroys the player. Safe to call more than once. */
  fun release() {
    player?.let {
      it.removeListener(listener)
      it.release()
    }
    player = null
    _state.value = LivePlayerState.Buffering
  }

  private fun updateState(player: Player) {
    val error = player.playerError
    _state.value = when {
      error != null -> LivePlayerState.Error(error.errorCodeName)
      player.isPlaying -> LivePlayerState.Playing
      player.playbackState == Player.STATE_READY -> LivePlayerState.Paused
      else -> LivePlayerState.Buffering
    }
  }

  /**
   * A looping clip would restart at 0:00 every time the screen opens, which breaks the
   * illusion of a live feed. Jump to where a broadcast started at the epoch would be now.
   */
  private fun alignToBroadcastClock(player: Player) {
    if (!source.loops || alignedToBroadcastClock) return
    val duration = player.duration
    if (duration == C.TIME_UNSET || duration <= 0) return
    alignedToBroadcastClock = true
    player.seekTo(System.currentTimeMillis() % duration)
  }
}
