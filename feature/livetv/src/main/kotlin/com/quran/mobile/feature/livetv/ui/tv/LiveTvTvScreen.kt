package com.quran.mobile.feature.livetv.ui.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.tvFocusFrame
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveChannel
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import com.quran.mobile.feature.livetv.data.ScheduleEntry
import com.quran.mobile.feature.livetv.player.LivePlayerController
import com.quran.mobile.feature.livetv.player.LivePlayerState
import com.quran.mobile.feature.livetv.ui.LiveBadge
import com.quran.mobile.feature.livetv.ui.LivePlayerSurface
import com.quran.mobile.feature.livetv.ui.YouTubeLivePlayer
import com.quran.mobile.feature.livetv.ui.YouTubeRemote
import com.quran.mobile.feature.livetv.ui.common.LiveIcons
import com.quran.mobile.feature.livetv.ui.common.formatScheduleTime
import com.quran.mobile.feature.livetv.ui.common.rememberOnAirIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * «پخش زندهٔ تصویری» on a TV (docs/tv/PLAN.md, phase 1): the broadcast fills the screen and
 * everything works from the remote.
 *
 * - Any key shows the control panel under the video; it hides again after a few seconds
 *   without input, and back hides it before it leaves the screen.
 * - Without the panel, up/down and the channel keys switch channel, and center opens the panel.
 * - The play/pause keys work at any time.
 *
 * The panel sits under the video rather than over it, because nothing of ours may be drawn over
 * YouTube's player; the video shrinks while the panel is open.
 */
@Composable
fun LiveTvTvScreen(
  channels: List<LiveChannel>,
  onOpenAudio: () -> Unit
) {
  val context = LocalContext.current
  var selectedId by rememberSaveable { mutableStateOf(channels.first().id) }
  val channel = channels.firstOrNull { it.id == selectedId } ?: channels.first()
  val source = channel.source
  val onAir = rememberOnAirIndex(channel.schedule)
  // null without a schedule: the channel's name alone is the heading then
  val programTitle = channel.schedule.getOrNull(onAir)?.title

  // exactly one of the two drives the broadcast, depending on the source
  val exoController = remember(source) {
    if (source is LiveStreamSource.YouTube) null else LivePlayerController(context, source)
  }
  val youTubeRemote = remember { YouTubeRemote() }

  val neverState = remember { MutableStateFlow<LivePlayerState>(LivePlayerState.Buffering) }
  val exoState by (exoController?.state ?: neverState).collectAsState()
  val isMuted by (exoController?.isMuted ?: youTubeRemote.isMuted).collectAsState()
  val subtitlesEnabled by (exoController?.subtitlesEnabled ?: neverFalse).collectAsState()
  val hasSubtitles by (exoController?.hasSubtitles ?: neverFalse).collectAsState()
  val youTubePlaying by youTubeRemote.isPlaying.collectAsState()
  val youTubePaused by youTubeRemote.isPaused.collectAsState()

  val isError = exoController != null && exoState is LivePlayerState.Error
  // still loading is neither playing nor paused
  val isPaused = if (exoController != null) exoState == LivePlayerState.Paused else youTubePaused
  val isPlaying = if (exoController != null) exoState == LivePlayerState.Playing else youTubePlaying

  var panelVisible by remember { mutableStateOf(true) }
  // bumped on every key press, so the panel's hide timer starts again
  var inputCount by remember { mutableIntStateOf(0) }
  val idleFocus = remember { FocusRequester() }
  val panelFocus = remember { FocusRequester() }
  // key-ups of the presses this screen handled itself, so they don't reach a button
  val handledKeys = remember { mutableSetOf<Key>() }

  fun switchChannel(step: Int) {
    if (channels.size < 2) return
    val index = channels.indexOf(channel)
    selectedId = channels[(index + step).mod(channels.size)].id
  }

  fun togglePlayPause() {
    if (exoController != null) exoController.togglePlayPause() else youTubeRemote.togglePlayPause()
  }

  BackHandler(enabled = panelVisible) { panelVisible = false }

  // an error needs the retry button: the panel opens for it and doesn't hide by itself
  // (back still hides it, then leaves)
  LaunchedEffect(isError) { if (isError) panelVisible = true }
  LaunchedEffect(panelVisible, inputCount, isError) {
    if (panelVisible && !isError) {
      delay(PANEL_TIMEOUT_MILLIS)
      panelVisible = false
    }
  }
  // a request made before the window has focus is lost (the screen would open with nothing
  // focused), so it is made again once the window gets it
  val windowFocused = LocalWindowInfo.current.isWindowFocused
  LaunchedEffect(panelVisible, windowFocused) {
    if (!windowFocused) return@LaunchedEffect
    // the focus target appears in the frame after the change
    withFrameNanos { }
    runCatching { if (panelVisible) panelFocus.requestFocus() else idleFocus.requestFocus() }
  }

  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
          .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent handledKeys.remove(event.key)
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            inputCount++
            // repeats of a held key this screen took stay with it, so they can't start a
            // press on the button that got focus meanwhile (its key-up would never come)
            if (event.key in handledKeys) return@onPreviewKeyEvent true
            val handled = when (event.key) {
              Key.MediaPlayPause -> { togglePlayPause(); true }
              Key.MediaPlay -> { if (isPaused) togglePlayPause(); true }
              Key.MediaPause, Key.MediaStop -> { if (!isPaused) togglePlayPause(); true }
              Key.ChannelUp -> { switchChannel(1); true }
              Key.ChannelDown -> { switchChannel(-1); true }
              else -> if (panelVisible) {
                false
              } else {
                when (event.key) {
                  // with a single channel there is nothing to switch: show the controls instead
                  Key.DirectionUp -> {
                    if (channels.size > 1) switchChannel(1) else panelVisible = true
                    true
                  }
                  Key.DirectionDown -> {
                    if (channels.size > 1) switchChannel(-1) else panelVisible = true
                    true
                  }
                  Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                  Key.DirectionLeft, Key.DirectionRight, Key.Menu -> { panelVisible = true; true }
                  else -> false
                }
              }
            }
            if (handled) handledKeys += event.key
            handled
          }
      ) {
        Column(Modifier.fillMaxSize()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
          ) {
            if (source is LiveStreamSource.YouTube) {
              YouTubeLivePlayer(source = source, remote = youTubeRemote, modifier = Modifier.fillMaxSize())
            } else if (exoController != null) {
              OurVideo(controller = exoController, state = exoState, isPlaying = isPlaying)
            }
          }

          AnimatedVisibility(
            visible = panelVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
          ) {
            ControlPanel(
              channels = channels,
              channel = channel,
              programTitle = programTitle,
              onAir = onAir,
              isPlaying = isPlaying,
              isPaused = isPaused,
              isError = isError,
              isMuted = isMuted,
              showSubtitles = exoController != null,
              subtitlesEnabled = subtitlesEnabled,
              firstFocus = panelFocus,
              onPlayPause = {
                if (isError) exoController?.retry() else togglePlayPause()
              },
              onMute = {
                if (exoController != null) exoController.toggleMute() else youTubeRemote.toggleMute()
              },
              onSubtitles = {
                if (!subtitlesEnabled && !hasSubtitles) {
                  Toast.makeText(context, R.string.livetv_subtitles_unavailable, Toast.LENGTH_SHORT).show()
                }
                exoController?.toggleSubtitles()
              },
              onSelectChannel = { selectedId = it.id },
              onOpenAudio = onOpenAudio
            )
          }
        }

        // holds the remote's focus while the panel is hidden, so keys still reach this screen
        if (!panelVisible) {
          Box(
            Modifier
              .size(1.dp)
              .focusRequester(idleFocus)
              .focusable()
          )
        }
      }
    }
  }
}

/** Our own stream (ExoPlayer), with the live badge and watermark drawn over it as on the phone. */
@Composable
private fun OurVideo(controller: LivePlayerController, state: LivePlayerState, isPlaying: Boolean) {
  val palette = LocalTasnimPalette.current
  Box(Modifier.fillMaxSize()) {
    LivePlayerSurface(controller = controller, modifier = Modifier.fillMaxSize())
    when (state) {
      LivePlayerState.Buffering -> CircularProgressIndicator(
        color = palette.onVideo,
        modifier = Modifier
          .align(Alignment.Center)
          .size(56.dp)
      )
      is LivePlayerState.Error -> Text(
        text = stringResource(R.string.livetv_error),
        color = palette.onVideo,
        fontFamily = Vazirmatn,
        fontSize = 24.sp,
        modifier = Modifier.align(Alignment.Center)
      )
      LivePlayerState.Playing, LivePlayerState.Paused -> Unit
    }
    LiveBadge(
      pulsing = isPlaying,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = TvSafeSide, top = TvSafeTop)
    )
    // the watermark sits on the left of the rtl screen, as on the phone
    Image(
      painter = painterResource(R.drawable.livetv_logo),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(end = TvSafeSide, top = TvSafeTop)
        .size(56.dp)
        .clip(CircleShape)
        .alpha(0.85f)
    )
  }
}

@Composable
private fun ControlPanel(
  channels: List<LiveChannel>,
  channel: LiveChannel,
  programTitle: String?,
  onAir: Int,
  isPlaying: Boolean,
  isPaused: Boolean,
  isError: Boolean,
  isMuted: Boolean,
  showSubtitles: Boolean,
  subtitlesEnabled: Boolean,
  firstFocus: FocusRequester,
  onPlayPause: () -> Unit,
  onMute: () -> Unit,
  onSubtitles: () -> Unit,
  onSelectChannel: (LiveChannel) -> Unit,
  onOpenAudio: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(palette.videoBackground)
      .padding(start = TvSafeSide, end = TvSafeSide, top = 20.dp, bottom = TvSafeTop),
    horizontalArrangement = Arrangement.spacedBy(40.dp)
  ) {
    Column(Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        LiveBadge(pulsing = isPlaying)
        Text(
          text = channel.name,
          color = if (programTitle == null) palette.onVideo else palette.gold,
          fontFamily = Vazirmatn,
          fontSize = if (programTitle == null) 28.sp else 18.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      if (programTitle != null) {
        Text(
          text = programTitle,
          color = palette.onVideo,
          fontFamily = Vazirmatn,
          fontSize = 28.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 6.dp)
        )
      }
      Spacer(Modifier.height(18.dp))
      // scrolls sideways when the channels don't fit; the focused one is always brought into view
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          // room for the focused item's frame, which grows past its bounds
          .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        TvControl(
          icon = if (isPaused || isError) LiveIcons.Play else LiveIcons.Pause,
          label = stringResource(
            when {
              isError -> R.string.livetv_retry
              isPaused -> R.string.livetv_play
              else -> R.string.livetv_pause
            }
          ),
          onClick = onPlayPause,
          modifier = Modifier.focusRequester(firstFocus)
        )
        TvControl(
          icon = if (isMuted) LiveIcons.VolumeOff else LiveIcons.Volume,
          label = stringResource(if (isMuted) R.string.livetv_unmute else R.string.livetv_mute),
          onClick = onMute
        )
        if (showSubtitles) {
          TvControl(
            icon = LiveIcons.Subtitles,
            label = stringResource(R.string.livetv_subtitles),
            onClick = onSubtitles,
            dimmed = !subtitlesEnabled
          )
        }
        Spacer(Modifier.width(12.dp))
        if (channels.size > 1) {
          channels.forEach { option ->
            TvChip(
              text = option.name,
              selected = option.id == channel.id,
              onClick = { onSelectChannel(option) }
            )
          }
        }
        TvChip(
          text = stringResource(R.string.livetv_mode_audio),
          selected = false,
          onClick = onOpenAudio
        )
      }
      if (channels.size > 1) {
        Text(
          text = stringResource(R.string.livetv_tv_channel_hint),
          color = palette.onVideo.copy(alpha = 0.6f),
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          modifier = Modifier.padding(top = 14.dp)
        )
      }
    }
    // only our own channel has a schedule
    if (channel.schedule.isNotEmpty()) {
      UpNext(entries = channel.schedule, onAir = onAir)
    }
  }
}

/** The program on air and the next few, from today's schedule. */
@Composable
internal fun UpNext(entries: List<ScheduleEntry>, onAir: Int) {
  val palette = LocalTasnimPalette.current
  val from = onAir.coerceAtLeast(0)
  Column(
    modifier = Modifier.widthIn(max = 360.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = stringResource(R.string.livetv_schedule_title),
      color = palette.gold,
      fontFamily = Vazirmatn,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold
    )
    entries.drop(from).take(UP_NEXT_COUNT).forEachIndexed { index, entry ->
      val isOnAir = index == 0 && onAir >= 0
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text(
          text = formatScheduleTime(entry.startMinute),
          color = if (isOnAir) palette.live else palette.onVideo.copy(alpha = 0.7f),
          fontFamily = Vazirmatn,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = entry.title,
          color = palette.onVideo,
          fontFamily = Vazirmatn,
          fontSize = 16.sp,
          fontWeight = if (isOnAir) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

private val neverFalse = MutableStateFlow(false)

private const val PANEL_TIMEOUT_MILLIS = 6_000L
private const val UP_NEXT_COUNT = 4

