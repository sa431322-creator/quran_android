package com.quran.mobile.feature.livetv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import com.quran.mobile.feature.livetv.player.LivePlayerController
import com.quran.mobile.feature.livetv.player.LivePlayerState

/**
 * A non-interactive video surface for a live channel.
 *
 * The player exists only while the screen is started: it is built on ON_START and
 * released on ON_STOP (backgrounding) and when this composable leaves (navigating away).
 * The screen is kept awake only while video is actually playing.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveVideoPlayer(source: LiveStreamSource, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val controller = remember(source) { LivePlayerController(context, source) }
  val playerView = remember {
    PlayerView(context).apply {
      // live tv has no seek bar, fast-forward or rewind
      useController = false
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
    }
  }
  val state by controller.state.collectAsState()

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

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    AndroidView(
      factory = { playerView },
      update = { it.keepScreenOn = state == LivePlayerState.Playing },
      modifier = Modifier.fillMaxSize()
    )

    when (state) {
      LivePlayerState.Buffering -> CircularProgressIndicator(color = Color.White)
      is LivePlayerState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(R.string.livetv_error), color = Color.White)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = controller::retry) {
          Text(text = stringResource(R.string.livetv_retry), color = Color.White)
        }
      }
      LivePlayerState.Playing, LivePlayerState.Paused -> Unit
    }
  }
}
