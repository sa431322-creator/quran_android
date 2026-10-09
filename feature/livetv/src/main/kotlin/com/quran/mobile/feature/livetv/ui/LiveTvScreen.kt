package com.quran.mobile.feature.livetv.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveChannel
import com.quran.mobile.feature.livetv.ui.common.LiveHeader
import com.quran.mobile.feature.livetv.ui.common.LiveIcons
import com.quran.mobile.feature.livetv.ui.common.ScheduleSection
import com.quran.mobile.feature.livetv.ui.common.rememberOnAirIndex
import com.quran.mobile.feature.livetv.ui.common.SegmentedSwitch

/** The «پخش زندهٔ تصویری» screen from the design canvas (LiveVideo.dc.html). */
@Composable
fun LiveTvScreen(
  channel: LiveChannel,
  onBack: () -> Unit,
  onOpenAudio: () -> Unit
) {
  var isFullscreen by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val onAir = rememberOnAirIndex(channel.schedule)
  // the title follows the program the schedule marks as on air
  val programTitle = channel.schedule.getOrNull(onAir)?.title ?: channel.name

  FullscreenEffect(isFullscreen)
  BackHandler(enabled = isFullscreen) { isFullscreen = false }

  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(if (isFullscreen) Color.Black else palette.background)
          .then(
            if (isFullscreen) {
              Modifier
            } else {
              Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            }
          )
      ) {
        if (!isFullscreen) {
          LiveHeader(onBack = onBack) {
            SegmentedSwitch(
              options = listOf(
                stringResource(R.string.livetv_mode_video),
                stringResource(R.string.livetv_mode_audio)
              ),
              selectedIndex = 0,
              onSelect = { if (it == 1) onOpenAudio() }
            )
          }
        }

        // kept at the same place in the tree so entering fullscreen keeps the same player
        LiveVideoPlayer(
          source = channel.source,
          viewerCount = channel.viewerCount,
          isFullscreen = isFullscreen,
          onToggleFullscreen = { isFullscreen = !isFullscreen },
          onSubtitlesUnavailable = {
            Toast.makeText(context, R.string.livetv_subtitles_unavailable, Toast.LENGTH_SHORT).show()
          },
          modifier = if (isFullscreen) {
            Modifier.fillMaxSize()
          } else {
            Modifier
              .fillMaxWidth()
              .aspectRatio(16f / 9f)
          }
        )

        if (!isFullscreen) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .verticalScroll(rememberScrollState())
              .padding(bottom = 16.dp)
          ) {
            ProgramInfo(title = programTitle, onShare = { shareProgram(context, channel, programTitle) })
            ScheduleSection(channel.schedule, onAir)
          }
        }
      }
    }
  }
}

/** Landscape with hidden system bars while fullscreen; back to normal otherwise. */
@Composable
private fun FullscreenEffect(isFullscreen: Boolean) {
  val activity = LocalContext.current as? Activity ?: return
  DisposableEffect(activity, isFullscreen) {
    val window = activity.window
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (isFullscreen) {
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      insetsController.show(WindowInsetsCompat.Type.systemBars())
    }
    onDispose { }
  }
}

private fun shareProgram(context: android.content.Context, channel: LiveChannel, program: String) {
  val text = context.getString(R.string.livetv_share_text, channel.name, program)
  val send = Intent(Intent.ACTION_SEND)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, text)
  context.startActivity(Intent.createChooser(send, context.getString(R.string.livetv_share)))
}

@Composable
private fun ProgramInfo(title: String, onShare: () -> Unit) {
  val palette = LocalTasnimPalette.current
  // the design keeps a single channel, so only the program title sits beside share
  Row(
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = title,
      color = palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 18.sp,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 28.sp,
      modifier = Modifier.weight(1f)
    )
    OutlinedIconButton(
      onClick = onShare,
      border = BorderStroke(1.dp, palette.outline),
      modifier = Modifier.size(44.dp)
    ) {
      Icon(
        imageVector = LiveIcons.Share,
        contentDescription = stringResource(R.string.livetv_share),
        tint = palette.ink,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}
