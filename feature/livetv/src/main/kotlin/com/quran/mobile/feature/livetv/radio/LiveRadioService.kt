package com.quran.mobile.feature.livetv.radio

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.livetv.data.RadioStation
import com.quran.mobile.feature.livetv.data.RadioStationRepository
import com.quran.mobile.feature.livetv.di.LiveTvComponentInterface
import com.quran.mobile.feature.livetv.player.BroadcastClockSync

/**
 * Plays radio stations in the background with a media notification. Media3 starts and
 * stops the foreground service and builds the notification from the session.
 *
 * This is separate from AudioService, which is built around ayah-by-ayah recitation
 * (gapless ayah files, highlighting, repeat ranges) and has no notion of a stream.
 * Both request audio focus, so starting one pauses the other.
 */
@OptIn(UnstableApi::class)
class LiveRadioService : MediaSessionService() {

  private var session: MediaSession? = null
  private lateinit var stations: RadioStationRepository

  override fun onCreate() {
    super.onCreate()
    val injector = (application as QuranApplicationComponentProvider)
      .provideQuranApplicationComponent() as LiveTvComponentInterface
    stations = injector.radioStationRepository()

    val audioAttributes = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()
    val player = ExoPlayer.Builder(this)
      .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
      .setHandleAudioBecomingNoisy(true)
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .build()
    // radio is audio only; the placeholder stations reuse a video clip
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
      .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
      .build()
    player.addListener(
      BroadcastClockSync(player, shouldSync = { currentStation(player)?.source?.loops == true })
    )
    player.addListener(object : Player.Listener {
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val loops = currentStation(player)?.source?.loops == true
        player.repeatMode = if (loops) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
      }
    })

    session = MediaSession.Builder(this, player)
      .setCallback(SessionCallback())
      .setSessionActivity(
        PendingIntent.getActivity(
          this,
          0,
          Intent(this, RadioActivity::class.java),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      .build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

  override fun onTaskRemoved(rootIntent: Intent?) {
    // keep playing in the background, but don't linger once paused and swiped away
    val player = session?.player
    if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
  }

  override fun onDestroy() {
    session?.run {
      player.release()
      release()
    }
    session = null
    super.onDestroy()
  }

  private fun currentStation(player: Player): RadioStation? =
    player.currentMediaItem?.mediaId?.let(stations::station)

  private inner class SessionCallback : MediaSession.Callback {

    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
      // a live station can't be seeked or skipped; this also trims the notification
      val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        .removeAll(
          Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
          Player.COMMAND_SEEK_BACK,
          Player.COMMAND_SEEK_FORWARD,
          Player.COMMAND_SEEK_TO_PREVIOUS,
          Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
          Player.COMMAND_SEEK_TO_NEXT,
          Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )
        .build()
      return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailablePlayerCommands(commands)
        .build()
    }

    /** Controllers only send station ids; the stream address is resolved here. */
    override fun onAddMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
      val artworkUri = Uri.parse("android.resource://$packageName/drawable/livetv_logo")
      val resolved = mediaItems.mapNotNull { item ->
        stations.station(item.mediaId)?.let { RadioMediaItems.from(it, artworkUri) }
      }
      return Futures.immediateFuture(resolved.toMutableList())
    }
  }
}
