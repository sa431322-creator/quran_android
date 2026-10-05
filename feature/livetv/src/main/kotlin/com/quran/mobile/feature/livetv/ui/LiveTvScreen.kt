package com.quran.mobile.feature.livetv.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveChannel
import com.quran.mobile.feature.livetv.data.ScheduleEntry
import com.quran.mobile.feature.livetv.data.ScheduleSlot
import com.quran.mobile.feature.livetv.ui.common.LiveHeader
import com.quran.mobile.feature.livetv.ui.common.LiveIcons
import com.quran.mobile.feature.livetv.ui.common.LivePaletteProvider
import com.quran.mobile.feature.livetv.ui.common.LocalLivePalette
import com.quran.mobile.feature.livetv.ui.common.SegmentedSwitch

private enum class LiveTab { CHANNELS, SCHEDULE }

/** The «پخش زندهٔ تصویری» screen from the design canvas (LiveVideo.dc.html). */
@Composable
fun LiveTvScreen(
  channels: List<LiveChannel>,
  onBack: () -> Unit,
  onOpenAudio: () -> Unit
) {
  var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
  var tab by rememberSaveable { mutableStateOf(LiveTab.CHANNELS) }
  var isFullscreen by rememberSaveable { mutableStateOf(false) }
  val channel = channels[selectedIndex.coerceIn(channels.indices)]
  val context = LocalContext.current

  FullscreenEffect(isFullscreen)
  BackHandler(enabled = isFullscreen) { isFullscreen = false }

  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    LivePaletteProvider {
      val palette = LocalLivePalette.current
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
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
          ) {
            item(key = "info") {
              ProgramInfo(channel = channel, onShare = { shareChannel(context, channel) })
            }
            item(key = "tabs") {
              Tabs(selected = tab, onSelect = { tab = it })
            }
            when (tab) {
              LiveTab.CHANNELS -> itemsIndexed(channels, key = { _, it -> it.id }) { index, item ->
                ChannelRow(
                  channel = item,
                  selected = index == selectedIndex,
                  onClick = { selectedIndex = index }
                )
              }
              LiveTab.SCHEDULE -> itemsIndexed(
                channel.schedule,
                key = { index, _ -> "schedule-$index" }
              ) { _, entry ->
                ScheduleRow(entry)
              }
            }
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

private fun shareChannel(context: android.content.Context, channel: LiveChannel) {
  val text = context.getString(R.string.livetv_share_text, channel.name, channel.currentProgram)
  val send = Intent(Intent.ACTION_SEND)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, text)
  context.startActivity(Intent.createChooser(send, context.getString(R.string.livetv_share)))
}

@Composable
private fun ProgramInfo(channel: LiveChannel, onShare: () -> Unit) {
  val palette = LocalLivePalette.current
  Column(
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = channel.currentProgram,
      color = palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 18.sp,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 28.sp
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .background(palette.primary, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = LiveIcons.VideoChannel,
          contentDescription = null,
          tint = palette.onPrimary,
          modifier = Modifier.size(20.dp)
        )
      }
      Column(Modifier.weight(1f)) {
        Text(
          text = channel.name,
          color = palette.ink,
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = channel.description,
          color = palette.muted,
          fontFamily = Vazirmatn,
          fontSize = 12.sp
        )
      }
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
}

@Composable
private fun Tabs(selected: LiveTab, onSelect: (LiveTab) -> Unit) {
  Row(
    modifier = Modifier
      .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    TabChip(stringResource(R.string.livetv_tab_channels), selected == LiveTab.CHANNELS) {
      onSelect(LiveTab.CHANNELS)
    }
    TabChip(stringResource(R.string.livetv_tab_schedule), selected == LiveTab.SCHEDULE) {
      onSelect(LiveTab.SCHEDULE)
    }
  }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
  val palette = LocalLivePalette.current
  val shape = RoundedCornerShape(20.dp)
  Box(
    modifier = Modifier
      .height(40.dp)
      .then(
        if (selected) {
          Modifier.background(palette.selectedChip, shape)
        } else {
          Modifier.border(1.dp, palette.outline, shape)
        }
      )
      .selectable(selected = selected, role = Role.Tab, onClick = onClick)
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = if (selected) palette.onSelectedChip else palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

@Composable
private fun ChannelRow(channel: LiveChannel, selected: Boolean, onClick: () -> Unit) {
  val palette = LocalLivePalette.current
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier = Modifier
      .padding(horizontal = 20.dp, vertical = 4.dp)
      .fillMaxWidth()
      .background(if (selected) palette.surface else Color.Transparent, shape)
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) palette.accent else palette.border,
        shape = shape
      )
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(width = 96.dp, height = 54.dp)
        .background(palette.thumbnail, RoundedCornerShape(8.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = LiveIcons.Play,
        contentDescription = null,
        tint = palette.onVideo,
        modifier = Modifier.size(20.dp)
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = channel.name,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = channel.description,
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
    LiveMarker(Modifier.padding(end = 4.dp))
  }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry) {
  val palette = LocalLivePalette.current
  val context = LocalContext.current
  val (tag, tagColor) = when (entry.slot) {
    ScheduleSlot.NOW -> stringResource(R.string.livetv_slot_now) to palette.live
    ScheduleSlot.NEXT -> stringResource(R.string.livetv_slot_next) to palette.accent
    ScheduleSlot.LATER -> stringResource(R.string.livetv_slot_later) to palette.muted
  }
  Column(Modifier.padding(horizontal = 20.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 58.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = tag,
        color = tagColor,
        fontFamily = Vazirmatn,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.width(52.dp)
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = entry.title,
          color = palette.ink,
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text(text = entry.note, color = palette.muted, fontFamily = Vazirmatn, fontSize = 12.sp)
      }
      // reminders need real program times from the backend
      IconButton(
        onClick = {
          Toast.makeText(context, R.string.livetv_reminder_soon, Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.size(44.dp)
      ) {
        Icon(
          imageVector = LiveIcons.Bell,
          contentDescription = stringResource(R.string.livetv_reminder, entry.title),
          tint = palette.accent,
          modifier = Modifier.size(20.dp)
        )
      }
    }
    HorizontalDivider(color = palette.border)
  }
}
