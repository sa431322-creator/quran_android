package com.quran.mobile.feature.livetv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.RadioStation
import com.quran.mobile.feature.livetv.radio.RadioConnection
import com.quran.mobile.feature.livetv.radio.RadioPlaybackState
import com.quran.mobile.feature.livetv.ui.common.LiveHeader
import com.quran.mobile.feature.livetv.ui.common.LiveIcons
import com.quran.mobile.feature.livetv.ui.common.LivePaletteProvider
import com.quran.mobile.feature.livetv.ui.common.LocalLivePalette

/** The «پخش زنده» radio screen from the design canvas (Radio.dc.html). */
@Composable
fun RadioScreen(stations: List<RadioStation>, onBack: () -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val connection = remember { RadioConnection(context) }
  val playback by connection.state.collectAsState()
  val defaultStationId = stations.first().id

  // connected only while visible; playback itself continues in LiveRadioService
  DisposableEffect(lifecycleOwner, connection) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> connection.connect(defaultStationId)
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

  val current = stations.firstOrNull { it.id == playback.currentStationId } ?: stations.first()

  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    LivePaletteProvider {
      val palette = LocalLivePalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
          .verticalScroll(rememberScrollState())
      ) {
        LiveHeader(onBack = onBack) { PhaseChip() }
        NowPlayingCard(
          station = current,
          playback = playback,
          onTogglePlay = connection::togglePlayPause
        )
        Column(
          modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            .selectableGroup(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          stations.forEach { station ->
            StationRow(
              station = station,
              selected = station.id == current.id,
              onClick = { connection.play(station.id) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PhaseChip() {
  val palette = LocalLivePalette.current
  Text(
    text = stringResource(R.string.livetv_phase_two),
    color = palette.chipText,
    fontFamily = Vazirmatn,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier
      .border(1.dp, palette.chipBorder, RoundedCornerShape(14.dp))
      .padding(horizontal = 12.dp, vertical = 6.dp)
  )
}

@Composable
private fun NowPlayingCard(
  station: RadioStation,
  playback: RadioPlaybackState,
  onTogglePlay: () -> Unit
) {
  val palette = LocalLivePalette.current
  Column(
    modifier = Modifier
      .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
      .fillMaxWidth()
      .background(palette.primary, RoundedCornerShape(28.dp))
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    // kept in the layout for on-demand stations so the card doesn't jump between stations
    LiveBadge(
      pulsing = playback.isPlaying,
      modifier = Modifier
        .align(Alignment.Start)
        .alpha(if (station.isLive) 1f else 0f)
        .then(if (station.isLive) Modifier else Modifier.clearAndSetSemantics { })
    )
    WavingLogo(isPlaying = playback.isPlaying)
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = station.name,
        color = palette.onPrimary,
        fontFamily = Vazirmatn,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
      )
      Text(
        text = if (playback.hasError) stringResource(R.string.livetv_error) else station.description,
        color = palette.onPrimaryMuted,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
      )
    }
    PlayButton(playback = playback, onClick = onTogglePlay)
  }
}

@Composable
private fun PlayButton(playback: RadioPlaybackState, onClick: () -> Unit) {
  val palette = LocalLivePalette.current
  val showPause = playback.playWhenReady && !playback.hasError
  FilledIconButton(
    onClick = onClick,
    colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.onPrimary),
    modifier = Modifier.size(72.dp)
  ) {
    if (showPause && playback.isBuffering) {
      CircularProgressIndicator(
        color = palette.primary,
        strokeWidth = 3.dp,
        modifier = Modifier.size(28.dp)
      )
    } else {
      Icon(
        imageVector = if (showPause) LiveIcons.Pause else LiveIcons.Play,
        contentDescription = stringResource(if (showPause) R.string.livetv_pause else R.string.livetv_play),
        tint = palette.primary,
        modifier = Modifier.size(28.dp)
      )
    }
  }
}

/** The round logo between gold sound-wave arcs, which dim while paused. */
@Composable
private fun WavingLogo(isPlaying: Boolean) {
  val palette = LocalLivePalette.current
  val inner by animateFloatAsState(if (isPlaying) 1f else 0.25f, label = "innerWave")
  val outer by animateFloatAsState(if (isPlaying) 0.55f else 0.15f, label = "outerWave")
  Box(modifier = Modifier.size(width = 240.dp, height = 140.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      // the design draws these in a 240×140 box
      val sx = size.width / 240f
      val sy = size.height / 140f
      val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
      fun arc(x0: Float, y0: Float, cx: Float, x1: Float, y1: Float, alpha: Float) {
        val path = Path().apply {
          moveTo(x0 * sx, y0 * sy)
          quadraticTo(cx * sx, 70f * sy, x1 * sx, y1 * sy)
        }
        drawPath(path, palette.gold, alpha = alpha, style = stroke)
      }
      arc(178f, 40f, 192f, 178f, 100f, inner)
      arc(196f, 26f, 218f, 196f, 114f, outer)
      arc(62f, 40f, 48f, 62f, 100f, inner)
      arc(44f, 26f, 22f, 44f, 114f, outer)
    }
    Image(
      painter = painterResource(R.drawable.livetv_logo),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .size(120.dp)
        .clip(CircleShape)
    )
  }
}

@Composable
private fun StationRow(station: RadioStation, selected: Boolean, onClick: () -> Unit) {
  val palette = LocalLivePalette.current
  val shape = RoundedCornerShape(16.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 64.dp)
      .clip(shape)
      .background(if (selected) palette.surface else Color.Transparent)
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) palette.accent else palette.border,
        shape = shape
      )
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(palette.background, RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = LiveIcons.Station,
        contentDescription = null,
        tint = palette.accent,
        modifier = Modifier.size(22.dp)
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = station.name,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = station.description,
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 12.sp
      )
    }
    if (station.isLive) LiveMarker()
  }
}
