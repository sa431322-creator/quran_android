package com.quran.labs.androidquran.ui.mushaf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.feature.mushaf.data.MushafPage
import com.quran.mobile.feature.mushaf.ui.MushafFontPage
import com.quran.mobile.feature.mushaf.ui.MushafPageLabels
import com.quran.mobile.feature.mushaf.ui.mushafPageFullHeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.floor

/** What the reader's auto-scroll shows and how fast; the reader sets it, the pages follow. */
class AutoScrollState {
  /** raised on every start, so the pages open again at [startPage] */
  var session by mutableIntStateOf(0)
  var startPage by mutableIntStateOf(1)
  /** the page at the middle of the screen */
  var currentPage by mutableIntStateOf(1)
  var playing by mutableStateOf(false)
  /** an index into [AUTO_SCROLL_SECONDS_PER_PAGE] */
  var speed by mutableIntStateOf(AUTO_SCROLL_DEFAULT_SPEED)
  var nightMode by mutableStateOf(false)
  var nightBrightness by mutableIntStateOf(255)
  var paper by mutableIntStateOf(0)
}

/** The speeds on offer, slowest first: how long one page takes to pass, in seconds. */
internal val AUTO_SCROLL_SECONDS_PER_PAGE = intArrayOf(300, 240, 180, 150, 120, 105, 90, 75, 60, 45, 30)
internal const val AUTO_SCROLL_DEFAULT_SPEED = 4

/** How long the controls stay up over moving text after the last touch. */
private const val CONTROLS_TIMEOUT_MS = 3000L
/** How long the screen rests on a page before the reader behind is brought to it. */
private const val PAGE_SETTLE_MS = 300L

/**
 * The mushaf's pages one under the other, moving up on their own like a teleprompter: each page
 * drawn whole, exactly as the reader's text page (plain or tajweed, following the reader). A tap
 * stops or resumes; dragging moves the text by hand and the scrolling carries on from there.
 */
@Composable
internal fun AutoScrollPages(
  state: AutoScrollState,
  labels: (Int) -> MushafPageLabels,
  suraName: (Int) -> String,
  /** the page at the middle of the screen changed, and stayed */
  onPage: (Int) -> Unit,
  /** a tap on the text, after it stopped or resumed */
  onTouch: () -> Unit,
  onSpeedChanged: (Int) -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  // plain or tajweed: reading the shared setting here redraws the pages when it changes
  val repository = TextMushaf.repository(context)
  val pages by produceState<List<MushafPage>?>(null, repository) {
    value = runCatching { repository.data().pages }.getOrNull()
  }
  val paper = Color(state.paper)
  val style = TextMushafStyle.pageStyle(context, state.nightMode, state.nightBrightness, paper)
  val basmalaFont = TextMushafStyle.basmalaFont(context)
  val currentOnPage by rememberUpdatedState(onPage)

  BoxWithConstraints(Modifier.fillMaxSize().background(paper)) {
    val all = pages ?: return@BoxWithConstraints
    val pageHeight = mushafPageFullHeight(maxWidth, maxHeight)
    val pageHeightPx = with(LocalDensity.current) { pageHeight.toPx() }
    val seconds = AUTO_SCROLL_SECONDS_PER_PAGE[state.speed.coerceIn(AUTO_SCROLL_SECONDS_PER_PAGE.indices)]
    val pxPerSecond = pageHeightPx / seconds
    // a touch on the controls or the page: keeps the controls up a little longer
    var touches by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(state.playing, touches) {
      controlsVisible = true
      if (state.playing) {
        delay(CONTROLS_TIMEOUT_MS)
        controlsVisible = false
      }
    }

    key(state.session) {
      val list = rememberLazyListState(
        initialFirstVisibleItemIndex = (state.startPage - 1).coerceIn(0, all.lastIndex)
      )
      LaunchedEffect(list) {
        snapshotFlow { list.middlePage() }
          .filterNotNull()
          .distinctUntilChanged()
          .collectLatest { page ->
            state.currentPage = page
            delay(PAGE_SETTLE_MS)
            currentOnPage(page)
          }
      }
      LaunchedEffect(list, state.playing, pxPerSecond) {
        if (state.playing) autoScroll(list, pxPerSecond) { state.playing = false }
      }

      LazyColumn(Modifier.fillMaxSize(), state = list) {
        items(count = all.size, key = { all[it].page }) { index ->
          val page = all[index]
          Box(Modifier.fillMaxWidth().height(pageHeight)) {
            MushafFontPage(
              page = page,
              repository = repository,
              basmalaFont = basmalaFont,
              style = style,
              labels = labels(page.page),
              suraHeaderName = suraName,
              selectedAyah = null,
              recitedAyah = null,
              highlights = emptyMap(),
              onTap = {
                state.playing = !state.playing
                touches++
                onTouch()
              }
            )
          }
        }
      }
    }

    AnimatedVisibility(
      visible = controlsVisible,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      AutoScrollControls(
        playing = state.playing,
        speedLabel = stringResource(R.string.tasnim_auto_scroll_per_page, clock(seconds)),
        canSlowDown = state.speed > 0,
        canSpeedUp = state.speed < AUTO_SCROLL_SECONDS_PER_PAGE.lastIndex,
        onPlayPause = {
          state.playing = !state.playing
          touches++
        },
        onSpeed = { change ->
          onSpeedChanged((state.speed + change).coerceIn(AUTO_SCROLL_SECONDS_PER_PAGE.indices))
          touches++
        },
        onClose = onClose
      )
    }
  }
}

/** The page under the middle of the screen. */
private fun LazyListState.middlePage(): Int? {
  val info = layoutInfo
  val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
  return info.visibleItemsInfo.firstOrNull { it.offset <= middle && it.offset + it.size > middle }?.let { it.index + 1 }
}

/**
 * Moves [list] up [pxPerSecond] until cancelled, in whole pixels on every frame; rests while the
 * user drags or flings the text, and calls [onEnd] at the end of the last page.
 */
private suspend fun autoScroll(list: LazyListState, pxPerSecond: Float, onEnd: () -> Unit) {
  var last = withFrameNanos { it }
  var owed = 0f
  while (true) {
    val now = withFrameNanos { it }
    // a long frame (the app was away) does not jump the text
    val elapsed = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
    last = now
    if (list.isScrollInProgress) {
      owed = 0f
      continue
    }
    // not laid out yet: nothing to move, and no end to have reached
    if (list.layoutInfo.totalItemsCount == 0) continue
    if (!list.canScrollForward) {
      onEnd()
      return
    }
    owed += pxPerSecond * elapsed
    val step = floor(owed)
    if (step < 1f) continue
    owed -= step
    try {
      list.scrollBy(step)
    } catch (e: CancellationException) {
      // the user's finger took the list over: carry on once it lets go, unless this job ended
      currentCoroutineContext().ensureActive()
    }
  }
}

/** [seconds] as minutes:seconds in the app's digits. */
private fun clock(seconds: Int): String {
  val rest = seconds % 60
  return QuranUtils.getLocalizedNumber(seconds / 60) + ":" +
      QuranUtils.getLocalizedNumber(rest / 10) + QuranUtils.getLocalizedNumber(rest % 10)
}

@Composable
private fun AutoScrollControls(
  playing: Boolean,
  speedLabel: String,
  canSlowDown: Boolean,
  canSpeedUp: Boolean,
  onPlayPause: () -> Unit,
  onSpeed: (Int) -> Unit,
  onClose: () -> Unit
) {
  Surface(
    modifier = Modifier.navigationBarsPadding().padding(16.dp),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shadowElevation = 6.dp
  ) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose) {
        Icon(QuranIcons.Close, contentDescription = stringResource(R.string.tasnim_auto_scroll_close))
      }
      TextButton(onClick = { onSpeed(-1) }, enabled = canSlowDown) {
        Text(stringResource(R.string.tasnim_auto_scroll_slower), fontSize = 14.sp)
      }
      Text(
        speedLabel,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.widthIn(min = 96.dp)
      )
      TextButton(onClick = { onSpeed(1) }, enabled = canSpeedUp) {
        Text(stringResource(R.string.tasnim_auto_scroll_faster), fontSize = 14.sp)
      }
      IconButton(onClick = onPlayPause) {
        Icon(
          if (playing) QuranIcons.Pause else QuranIcons.PlayArrow,
          contentDescription = stringResource(
            if (playing) R.string.tasnim_auto_scroll_pause else R.string.tasnim_auto_scroll_play
          ),
          tint = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}
