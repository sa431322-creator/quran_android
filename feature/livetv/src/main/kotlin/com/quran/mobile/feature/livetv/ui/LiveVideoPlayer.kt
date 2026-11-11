package com.quran.mobile.feature.livetv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.formatPersianCount
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import com.quran.mobile.feature.livetv.player.LivePlayerController
import com.quran.mobile.feature.livetv.player.LivePlayerState
import com.quran.mobile.feature.livetv.ui.common.LiveIcons

/**
 * The live video surface with its overlays: «زنده» badge, viewer count, logo watermark
 * and a control bar (play/pause, mute, Persian subtitles, fullscreen). Live TV has no
 * seek bar; the thin line above the controls only marks the live edge.
 *
 * The player exists only while the screen is started: it is built on ON_START and
 * released on ON_STOP (backgrounding) and when this composable leaves (navigating away).
 * The screen is kept awake only while video is actually playing.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveVideoPlayer(
  source: LiveStreamSource,
  viewerCount: Int?,
  isFullscreen: Boolean,
  onToggleFullscreen: () -> Unit,
  onSubtitlesUnavailable: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val palette = LocalTasnimPalette.current
  val controller = remember(source) { LivePlayerController(context, source) }
  val playerView = remember {
    PlayerView(context).apply {
      // live tv has no seek bar, fast-forward or rewind; the controls are drawn in compose
      useController = false
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
    }
  }
  val state by controller.state.collectAsState()
  val isMuted by controller.isMuted.collectAsState()
  val subtitlesEnabled by controller.subtitlesEnabled.collectAsState()
  val hasSubtitles by controller.hasSubtitles.collectAsState()

  DisposableEffect(lifecycleOwner, controller) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> playerView.player = controller.initialize()
        Lifecycle.Event.ON_STOP -> {
          playerView.player = null
          controller.release()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      playerView.player = null
      controller.release()
    }
  }

  Box(modifier = modifier.background(palette.videoBackground)) {
    AndroidView(
      factory = { playerView },
      update = { it.keepScreenOn = state == LivePlayerState.Playing },
      modifier = Modifier.fillMaxSize()
    )

    when (state) {
      LivePlayerState.Buffering -> CircularProgressIndicator(
        color = palette.onVideo,
        modifier = Modifier.align(Alignment.Center)
      )
      is LivePlayerState.Error -> Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(R.string.livetv_error),
          color = palette.onVideo,
          fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = controller::retry) {
          Text(
            text = stringResource(R.string.livetv_retry),
            color = palette.onVideo,
            fontFamily = Vazirmatn
          )
        }
      }
      LivePlayerState.Playing, LivePlayerState.Paused -> Unit
    }

    // the watermark sits on the left of the rtl screen, as on the design
    Image(
      painter = painterResource(R.drawable.livetv_logo),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(12.dp)
        .size(34.dp)
        .clip(CircleShape)
        .alpha(0.85f)
    )

    Row(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      LiveBadge(pulsing = state == LivePlayerState.Playing)
      ViewerCount(viewerCount)
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        // keeps the controls readable over bright video
        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
        .padding(start = 6.dp, end = 6.dp, bottom = 4.dp)
    ) {
      Box(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
          .height(3.dp)
          .background(palette.water, RoundedCornerShape(2.dp))
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        val paused = state == LivePlayerState.Paused
        ControlButton(
          icon = if (paused) LiveIcons.Play else LiveIcons.Pause,
          label = stringResource(if (paused) R.string.livetv_play else R.string.livetv_pause),
          onClick = controller::togglePlayPause,
          enabled = state !is LivePlayerState.Error
        )
        ControlButton(
          icon = if (isMuted) LiveIcons.VolumeOff else LiveIcons.Volume,
          label = stringResource(if (isMuted) R.string.livetv_unmute else R.string.livetv_mute),
          onClick = controller::toggleMute
        )
        Text(
          text = stringResource(R.string.livetv_title),
          color = palette.onVideo,
          fontFamily = Vazirmatn,
          fontSize = 12.sp,
          modifier = Modifier.weight(1f)
        )
        val subtitlesState = stringResource(
          if (subtitlesEnabled) R.string.livetv_state_on else R.string.livetv_state_off
        )
        ControlButton(
          icon = LiveIcons.Subtitles,
          label = stringResource(R.string.livetv_subtitles),
          onClick = {
            // a stream without subtitle tracks would make the toggle look broken
            if (!subtitlesEnabled && !hasSubtitles) onSubtitlesUnavailable()
            controller.toggleSubtitles()
          },
          dimmed = !subtitlesEnabled,
          modifier = Modifier.semantics { stateDescription = subtitlesState }
        )
        ControlButton(
          icon = if (isFullscreen) LiveIcons.FullscreenExit else LiveIcons.Fullscreen,
          label = stringResource(
            if (isFullscreen) R.string.livetv_exit_fullscreen else R.string.livetv_fullscreen
          ),
          onClick = onToggleFullscreen
        )
      }
    }
  }
}

@Composable
private fun ViewerCount(viewerCount: Int?) {
  val palette = LocalTasnimPalette.current
  val count = viewerCount?.let(::formatPersianCount)
    ?: stringResource(R.string.livetv_viewers_unknown)
  Row(
    modifier = Modifier
      .background(palette.videoBackground.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(
      imageVector = LiveIcons.Viewers,
      contentDescription = null,
      tint = palette.onVideo,
      modifier = Modifier.size(14.dp)
    )
    Text(
      text = stringResource(R.string.livetv_viewers, count),
      color = palette.onVideo,
      fontFamily = Vazirmatn,
      fontSize = 12.sp
    )
  }
}

@Composable
private fun ControlButton(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  dimmed: Boolean = false
) {
  val tint = LocalTasnimPalette.current.onPrimary
  IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(44.dp)) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = tint.copy(alpha = if (dimmed || !enabled) 0.55f else 1f),
      modifier = Modifier.size(22.dp)
    )
  }
}
