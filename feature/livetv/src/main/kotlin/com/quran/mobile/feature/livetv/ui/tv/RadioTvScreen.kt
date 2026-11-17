package com.quran.mobile.feature.livetv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quran.labs.androidquran.common.ui.core.TvSafeTop
import com.quran.labs.androidquran.common.ui.core.TvSafeSide
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.RadioStation
import com.quran.mobile.feature.livetv.radio.RadioConnection
import com.quran.mobile.feature.livetv.radio.RadioPlaybackState
import com.quran.mobile.feature.livetv.ui.LiveBadge
import com.quran.mobile.feature.livetv.ui.WavingLogo
import com.quran.mobile.feature.livetv.ui.common.LiveIcons
import com.quran.mobile.feature.livetv.ui.common.rememberOnAirIndex

/**
 * «پخش زندهٔ صوتی» on a TV (docs/tv/PLAN.md, phase 2): the station playing on one side, the
 * stations and today's programs on the other, all from the remote.
 *
 * - Play/pause has the first focus; the stations are cards, and the channel keys switch them.
 * - The tabs at the top switch to the live video in one press.
 * - Playback is LiveRadioService's, as on the phone: it keeps going after home or back (the
 *   TV's own media controls and the remote's media keys reach its session), and the live
 *   video, which takes the audio focus, stops it.
 */
@Composable
fun RadioTvScreen(
  stations: List<RadioStation>,
  onOpenVideo: () -> Unit,
  playOnOpen: Boolean = false
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val connection = remember { RadioConnection(context) }
  val playback by connection.state.collectAsState()
  // the station playing in the background service, or our own one before anything plays
  val station = stations.firstOrNull { it.id == playback.stationId } ?: stations.first()
  val defaultStationId = stations.first().id
  val playFocus = remember { FocusRequester() }

  // asked for once: a later start (back from home) keeps the viewer's pause
  val resumeOnce = remember { booleanArrayOf(playOnOpen) }

  // connected only while visible; playback itself continues in LiveRadioService
  DisposableEffect(lifecycleOwner, connection) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          connection.connect(defaultStationId, resume = resumeOnce[0])
          resumeOnce[0] = false
        }
        Lifecycle.Event.ON_STOP -> connection.disconnect()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      connection.disconnect()
    }
  }

  // a request made before the window has focus is lost, so it waits for the window; once per
  // screen, so coming back (from home, or after a dialog) leaves the remote where it was
  val windowFocused = LocalWindowInfo.current.isWindowFocused
  var focusedOnce by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(windowFocused) {
    if (!windowFocused || focusedOnce) return@LaunchedEffect
    withFrameNanos { }
    focusedOnce = runCatching { playFocus.requestFocus() }.isSuccess
  }

  fun switchStation(step: Int) {
    if (stations.size < 2) return
    val index = stations.indexOf(station)
    connection.play(stations[(index + step).mod(stations.size)].id)
  }

  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.videoBackground)
          .onPreviewKeyEvent { event ->
            // the channel keys switch stations; the media keys go on to the radio's session
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
              Key.ChannelUp -> { switchStation(1); true }
              Key.ChannelDown -> { switchStation(-1); true }
              else -> false
            }
          }
          .padding(horizontal = TvSafeSide, vertical = TvSafeTop)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(R.string.livetv_title),
            color = palette.onVideo,
            fontFamily = Vazirmatn,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
          )
          // video ↔ audio in one press, as the live screen's «صوتی» chip
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvChip(
              text = stringResource(R.string.livetv_mode_video),
              selected = false,
              onClick = onOpenVideo
            )
            TvChip(
              text = stringResource(R.string.livetv_mode_audio),
              selected = true,
              onClick = {}
            )
          }
        }
        Spacer(Modifier.height(24.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
          NowPlaying(
            station = station,
            playback = playback,
            playFocus = playFocus,
            onTogglePlay = connection::togglePlayPause,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState())
              // room for the focused card's frame, which grows past its bounds
              .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            if (stations.size > 1) {
              Text(
                text = stringResource(R.string.livetv_tv_stations),
                color = palette.gold,
                fontFamily = Vazirmatn,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              )
              stations.forEach { option ->
                TvChip(
                  text = option.name,
                  selected = option.id == station.id,
                  onClick = { connection.play(option.id) },
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }
            // only our own station has a schedule
            if (station.schedule.isNotEmpty()) {
              Spacer(Modifier.height(8.dp))
              UpNext(entries = station.schedule, onAir = rememberOnAirIndex(station.schedule))
            }
          }
        }
      }
    }
  }
}

/** The station on air: its logo between the sound waves, its name, and the big play button. */
@Composable
private fun NowPlaying(
  station: RadioStation,
  playback: RadioPlaybackState,
  playFocus: FocusRequester,
  onTogglePlay: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalTasnimPalette.current
  Box(
    modifier = modifier
      .background(palette.primary, RoundedCornerShape(28.dp))
      .padding(20.dp)
  ) {
    // in the corner rather than above the logo: a TV screen is short
    if (station.isLive) {
      LiveBadge(pulsing = playback.isPlaying, modifier = Modifier.align(Alignment.TopStart))
    }
    NowPlayingContent(station, playback, playFocus, onTogglePlay, Modifier.align(Alignment.Center))
  }
}

@Composable
private fun NowPlayingContent(
  station: RadioStation,
  playback: RadioPlaybackState,
  playFocus: FocusRequester,
  onTogglePlay: () -> Unit,
  modifier: Modifier
) {
  val palette = LocalTasnimPalette.current
  val showPause = playback.playWhenReady && !playback.hasError
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    WavingLogo(isPlaying = playback.isPlaying)
    Text(
      text = station.name,
      color = palette.onPrimary,
      fontFamily = Vazirmatn,
      fontSize = 24.sp,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center
    )
    Text(
      text = if (playback.hasError) stringResource(R.string.livetv_error) else station.description,
      color = palette.onPrimaryMuted,
      fontFamily = Vazirmatn,
      fontSize = 16.sp,
      textAlign = TextAlign.Center
    )
    Box(contentAlignment = Alignment.TopCenter) {
      TvControl(
        icon = if (showPause) LiveIcons.Pause else LiveIcons.Play,
        label = stringResource(
          when {
            playback.hasError -> R.string.livetv_retry
            showPause -> R.string.livetv_pause
            else -> R.string.livetv_play
          }
        ),
        onClick = onTogglePlay,
        size = 80.dp,
        modifier = Modifier.focusRequester(playFocus)
      )
      if (showPause && playback.isBuffering) {
        CircularProgressIndicator(
          color = palette.onVideo,
          strokeWidth = 3.dp,
          modifier = Modifier
            .size(80.dp)
            .alpha(0.6f)
        )
      }
    }
  }
}
