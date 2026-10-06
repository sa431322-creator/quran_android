package com.quran.mobile.feature.livetv.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Makes a looping clip behave like a 24/7 broadcast: when it first becomes ready, and
 * whenever the user resumes it, it jumps to where a broadcast started at the epoch would
 * be now. Without this the clip restarts at 0:00 on every open and resumes where it was
 * paused, which a live feed never does.
 *
 * In a playlist the sync is within the video playing at the time; the playlist as a
 * whole always opens on its first video.
 */
internal class BroadcastClockSync(
  private val player: Player,
  private val shouldSync: () -> Boolean,
  private val now: () -> Long = System::currentTimeMillis
) : Player.Listener {
  private var synced = false

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    // repeating or moving on to the next video of a playlist is the same broadcast
    // continuing; only a new source (such as switching radio station) is a new one
    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) synced = false
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_READY && !synced) sync()
  }

  override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
    if (playWhenReady &&
      reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
      player.playbackState == Player.STATE_READY
    ) {
      sync()
    }
  }

  /** Forgets the last sync, for example after the player is re-prepared. */
  fun reset() {
    synced = false
  }

  private fun sync() {
    if (!shouldSync()) return
    val duration = player.duration
    if (duration == C.TIME_UNSET || duration <= 0) return
    synced = true
    player.seekTo(livePosition(duration, now()))
  }

  companion object {
    fun livePosition(durationMs: Long, nowMs: Long): Long = nowMs % durationMs
  }
}
