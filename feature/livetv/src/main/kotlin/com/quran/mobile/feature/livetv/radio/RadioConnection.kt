package com.quran.mobile.feature.livetv.radio

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RadioPlaybackState(
  /** The user wants it playing, even if it is still buffering. */
  val playWhenReady: Boolean = false,
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val hasError: Boolean = false
)

/**
 * The radio screen's link to [LiveRadioService]. Disconnecting does not stop playback;
 * the service keeps playing in the background until it is paused.
 */
class RadioConnection(context: Context) {
  private val appContext = context.applicationContext
  private var future: ListenableFuture<MediaController>? = null
  private var controller: MediaController? = null

  private val _state = MutableStateFlow(RadioPlaybackState())
  val state: StateFlow<RadioPlaybackState> = _state.asStateFlow()

  private val listener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) = updateState(player)
  }

  /** Connects, and starts [defaultStationId] if nothing has been played yet. */
  fun connect(defaultStationId: String) {
    if (future != null) return
    val token = SessionToken(appContext, ComponentName(appContext, LiveRadioService::class.java))
    val pending = MediaController.Builder(appContext, token).buildAsync()
    future = pending
    pending.addListener(
      {
        // disconnect() may have run while connecting
        if (future !== pending || pending.isCancelled) return@addListener
        val connected = runCatching { pending.get() }.getOrNull() ?: return@addListener
        controller = connected
        connected.addListener(listener)
        if (connected.mediaItemCount == 0) play(defaultStationId) else updateState(connected)
      },
      ContextCompat.getMainExecutor(appContext)
    )
  }

  fun disconnect() {
    controller?.removeListener(listener)
    future?.let(MediaController::releaseFuture)
    future = null
    controller = null
  }

  /** Plays [stationId], switching stations if another one is current. */
  fun play(stationId: String) {
    val controller = controller ?: return
    if (controller.currentMediaItem?.mediaId != stationId) {
      controller.setMediaItem(RadioMediaItems.request(stationId))
    }
    if (controller.playbackState == Player.STATE_IDLE || controller.playerError != null) {
      controller.prepare()
    }
    controller.play()
  }

  fun togglePlayPause() {
    val controller = controller ?: return
    val stationId = controller.currentMediaItem?.mediaId
    when {
      controller.playWhenReady && controller.playerError == null -> controller.pause()
      stationId != null -> play(stationId)
    }
  }

  private fun updateState(player: Player) {
    _state.value = RadioPlaybackState(
      playWhenReady = player.playWhenReady,
      isPlaying = player.isPlaying,
      isBuffering = player.playbackState == Player.STATE_BUFFERING,
      hasError = player.playerError != null
    )
  }
}
