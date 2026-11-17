package com.quran.mobile.feature.library.reader

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.LibraryChapter
import com.quran.mobile.feature.library.ui.LibraryIcons
import com.quran.mobile.feature.library.ui.tv.TvPill
import com.quran.mobile.feature.library.ui.tv.TvSafeSide
import com.quran.mobile.feature.library.ui.tv.TvSafeTop
import com.quran.mobile.feature.library.ui.tv.tvClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A book on a TV (docs/tv/PLAN.md, phase 3): two pages side by side, as an open Persian book —
 * the page being read on the right, the next one on the left.
 *
 * - Left turns to the next pages and right back, as a Persian book turns; the channel and
 *   page keys do the same.
 * - «بزرگ‌نمایی» shows one page across the screen; up and down move along it.
 * - Center (or menu) opens the tools: contents, bookmark, bookmarks, go to page, zoom and
 *   night mode. They hide again after a few seconds, and back hides them before it leaves.
 *
 * The page on the right is the one saved as the reading position and bookmarked.
 */
@Composable
fun ReaderTvScreen(
  title: String,
  state: ReaderState,
  chapters: List<LibraryChapter>,
  bookmarks: List<Int>,
  nightMode: Boolean,
  onPageShown: (page: Int, pageCount: Int) -> Unit,
  onToggleBookmark: (page: Int) -> Unit,
  onToggleNightMode: () -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(if (nightMode) NightBackground else palette.background),
        contentAlignment = Alignment.Center
      ) {
        when (state) {
          ReaderState.Loading -> CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp)
          ReaderState.Error -> Text(
            text = stringResource(R.string.library_reader_error),
            color = palette.muted,
            fontFamily = Vazirmatn,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
          )
          is ReaderState.Ready -> TvReader(
            title = title,
            state = state,
            chapters = chapters,
            bookmarks = bookmarks,
            nightMode = nightMode,
            onPageShown = onPageShown,
            onToggleBookmark = onToggleBookmark,
            onToggleNightMode = onToggleNightMode
          )
        }
      }
    }
  }
}

private enum class TvReaderDialog { CONTENTS, BOOKMARKS, GO_TO }

@Composable
private fun TvReader(
  title: String,
  state: ReaderState.Ready,
  chapters: List<LibraryChapter>,
  bookmarks: List<Int>,
  nightMode: Boolean,
  onPageShown: (page: Int, pageCount: Int) -> Unit,
  onToggleBookmark: (page: Int) -> Unit,
  onToggleNightMode: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  val pageCount = state.pages.pageCount
  var page by rememberSaveable { mutableIntStateOf(state.startPage) }
  var zoomed by rememberSaveable { mutableStateOf(false) }
  var toolsVisible by rememberSaveable { mutableStateOf(false) }
  var dialog by rememberSaveable { mutableStateOf<TvReaderDialog?>(null) }
  // bumped on every key press, so the tools' hide timer starts again
  var inputCount by remember { mutableIntStateOf(0) }
  val zoomScroll = rememberScrollState()
  val scope = rememberCoroutineScope()
  val idleFocus = remember { FocusRequester() }
  val toolsFocus = remember { FocusRequester() }
  // key-ups of the presses this screen handled itself, so they don't reach a button
  val handledKeys = remember { mutableSetOf<Key>() }
  val step = if (zoomed) 1 else 2

  fun goTo(target: Int) {
    page = target.coerceIn(0, pageCount - 1)
  }

  LaunchedEffect(page) {
    onPageShown(page, pageCount)
    zoomScroll.scrollTo(0)
  }
  LaunchedEffect(toolsVisible, inputCount, dialog) {
    if (toolsVisible && dialog == null) {
      delay(TOOLS_TIMEOUT_MILLIS)
      toolsVisible = false
    }
  }
  // a request made before the window has focus is lost, so it waits for the window
  val windowFocused = LocalWindowInfo.current.isWindowFocused
  LaunchedEffect(toolsVisible, windowFocused, dialog) {
    if (!windowFocused || dialog != null) return@LaunchedEffect
    withFrameNanos { }
    runCatching { if (toolsVisible) toolsFocus.requestFocus() else idleFocus.requestFocus() }
  }
  BackHandler(enabled = toolsVisible) { toolsVisible = false }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent handledKeys.remove(event.key)
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        inputCount++
        if (event.key in handledKeys) return@onPreviewKeyEvent true
        val handled = when (event.key) {
          Key.ChannelUp, Key.PageDown, Key.MediaFastForward -> { goTo(page + step); true }
          Key.ChannelDown, Key.PageUp, Key.MediaRewind -> { goTo(page - step); true }
          else -> if (toolsVisible) {
            false
          } else {
            when (event.key) {
              // a Persian book turns to the left
              Key.DirectionLeft -> { goTo(page + step); true }
              Key.DirectionRight -> { goTo(page - step); true }
              Key.DirectionDown -> {
                if (zoomed) scope.launch { zoomScroll.animateScrollBy(SCROLL_STEP_PX) } else toolsVisible = true
                true
              }
              Key.DirectionUp -> {
                if (zoomed) scope.launch { zoomScroll.animateScrollBy(-SCROLL_STEP_PX) } else toolsVisible = true
                true
              }
              Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu -> { toolsVisible = true; true }
              else -> false
            }
          }
        }
        if (handled) handledKeys += event.key
        handled
      }
  ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
      if (zoomed) {
        // one page across the screen, moved along with up and down
        Column(
          Modifier
            .fillMaxSize()
            .verticalScroll(zoomScroll, enabled = false)
        ) {
          TvPage(pages = state.pages, index = page, widthPx = widthPx, nightMode = nightMode, fillWidth = true)
        }
      } else {
        // the page being read on the right (the start of a right-to-left row), the next on the left
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeSide, vertical = TvSafeTop),
          horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
          TvPage(
            pages = state.pages,
            index = page,
            widthPx = widthPx / 2,
            nightMode = nightMode,
            fillWidth = false,
            // against the spine in the middle, as an open book
            alignment = Alignment.CenterEnd,
            modifier = Modifier.weight(1f)
          )
          if (page + 1 < pageCount) {
            TvPage(
              pages = state.pages,
              index = page + 1,
              widthPx = widthPx / 2,
              nightMode = nightMode,
              fillWidth = false,
              alignment = Alignment.CenterStart,
              modifier = Modifier.weight(1f)
            )
          } else {
            Box(Modifier.weight(1f))
          }
        }
      }
    }

    // in night mode the tools go dark too, as the phone's bars do
    val barPalette = if (nightMode) {
      palette.copy(
        background = NightBackground,
        surface = Color(0xFF1A252A),
        ink = palette.onVideo,
        muted = Color(0xFFA9A08C),
        accent = Color(0xFF8CCADB),
        border = Color(0xFF2A363B),
        track = Color(0xFF1A252A)
      )
    } else {
      palette
    }
    CompositionLocalProvider(LocalTasnimPalette provides barPalette) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
          visible = toolsVisible,
          enter = fadeIn() + slideInVertically { it },
          exit = fadeOut() + slideOutVertically { it }
        ) {
          TvReaderTools(
            title = title,
            page = page,
            pageCount = pageCount,
            twoPages = !zoomed && page + 1 < pageCount,
            hasChapters = chapters.isNotEmpty(),
            bookmarked = page in bookmarks,
            zoomed = zoomed,
            nightMode = nightMode,
            firstFocus = toolsFocus,
            onContents = { dialog = TvReaderDialog.CONTENTS },
            onToggleBookmark = { onToggleBookmark(page) },
            onBookmarks = { dialog = TvReaderDialog.BOOKMARKS },
            onGoTo = { dialog = TvReaderDialog.GO_TO },
            onToggleZoom = { zoomed = !zoomed },
            onToggleNightMode = onToggleNightMode
          )
        }
      }
    }

    // holds the remote's focus while the tools are hidden, so keys still reach this screen
    if (!toolsVisible) {
      Box(
        Modifier
          .size(1.dp)
          .focusRequester(idleFocus)
          .focusable()
      )
    }
  }

  when (dialog) {
    TvReaderDialog.GO_TO -> GoToPageDialog(
      pageCount = pageCount,
      onGo = { target ->
        dialog = null
        goTo(target)
      },
      onDismiss = { dialog = null }
    )
    TvReaderDialog.CONTENTS -> {
      val current = chapters.indexOfLast { it.page - 1 <= page }
      TvListDialog(
        title = stringResource(R.string.library_reader_contents),
        entries = chapters.map { it.title to (it.page - 1) },
        currentIndex = current,
        onOpen = { target ->
          dialog = null
          goTo(target)
        },
        onDismiss = { dialog = null }
      )
    }
    TvReaderDialog.BOOKMARKS -> TvListDialog(
      title = stringResource(R.string.library_reader_bookmarks),
      entries = bookmarks.map { marked ->
        // the chapter the page falls in, so a bookmark says more than its number
        val chapter = chapters.lastOrNull { it.page - 1 <= marked }
        val label = stringResource(R.string.library_reader_page_description, (marked + 1).toString().toPersianDigits())
        (if (chapter != null) "$label · ${chapter.title}" else label) to marked
      },
      currentIndex = -1,
      empty = stringResource(R.string.library_reader_bookmarks_empty),
      onOpen = { target ->
        dialog = null
        goTo(target)
      },
      onDismiss = { dialog = null }
    )
    null -> Unit
  }
}

@Composable
private fun TvPage(
  pages: PdfPages,
  index: Int,
  widthPx: Int,
  nightMode: Boolean,
  fillWidth: Boolean,
  modifier: Modifier = Modifier,
  alignment: Alignment = Alignment.Center
) {
  val palette = LocalTasnimPalette.current
  val bitmap by produceState<Bitmap?>(null, pages, index, widthPx) {
    value = runCatching { pages.render(index, widthPx) }.getOrNull()
  }
  Box(
    modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxHeight()),
    contentAlignment = Alignment.Center
  ) {
    val image = bitmap
    if (image == null) {
      CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    } else {
      Image(
        bitmap = image.asImageBitmap(),
        contentDescription = stringResource(
          R.string.library_reader_page_description,
          (index + 1).toString().toPersianDigits()
        ),
        contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
        alignment = alignment,
        colorFilter = if (nightMode) NightFilter else null,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
      )
    }
  }
}

/** The tools along the bottom: where the reader is, and what the remote can do from here. */
@Composable
private fun TvReaderTools(
  title: String,
  page: Int,
  pageCount: Int,
  twoPages: Boolean,
  hasChapters: Boolean,
  bookmarked: Boolean,
  zoomed: Boolean,
  nightMode: Boolean,
  firstFocus: FocusRequester,
  onContents: () -> Unit,
  onToggleBookmark: () -> Unit,
  onBookmarks: () -> Unit,
  onGoTo: () -> Unit,
  onToggleZoom: () -> Unit,
  onToggleNightMode: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  val shown = if (twoPages) {
    (page + 1).toString().toPersianDigits() + "–" + (page + 2).toString().toPersianDigits()
  } else {
    (page + 1).toString().toPersianDigits()
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(palette.background.copy(alpha = 0.97f))
      .padding(start = TvSafeSide, end = TvSafeSide, top = 16.dp, bottom = TvSafeTop),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      Text(
        text = title,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      Text(
        text = stringResource(R.string.library_reader_page, shown, pageCount.toString().toPersianDigits()),
        color = palette.accent,
        fontFamily = Vazirmatn,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
      )
    }
    // scrolls sideways when the tools don't fit; the focused one is always brought into view
    Row(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        // room for the focused button's frame, which grows past its bounds
        .padding(6.dp)
    ) {
      if (hasChapters) {
        TvPill(
          icon = LibraryIcons.Contents,
          text = stringResource(R.string.library_reader_contents),
          selected = false,
          onClick = onContents,
          modifier = Modifier.focusRequester(firstFocus)
        )
      }
      TvPill(
        icon = if (bookmarked) LibraryIcons.BookmarkFilled else LibraryIcons.Bookmark,
        text = stringResource(
          if (bookmarked) R.string.library_reader_bookmark_remove else R.string.library_reader_bookmark_add
        ),
        selected = bookmarked,
        onClick = onToggleBookmark,
        modifier = if (hasChapters) Modifier else Modifier.focusRequester(firstFocus)
      )
      TvPill(
        icon = LibraryIcons.Bookmarks,
        text = stringResource(R.string.library_bookmarks),
        selected = false,
        onClick = onBookmarks
      )
      TvPill(
        icon = LibraryIcons.GoToPage,
        text = stringResource(R.string.library_reader_go_to_page),
        selected = false,
        onClick = onGoTo
      )
      TvPill(
        text = stringResource(if (zoomed) R.string.library_tv_two_pages else R.string.library_tv_zoom),
        selected = false,
        onClick = onToggleZoom
      )
      TvPill(
        icon = if (nightMode) LibraryIcons.Sun else LibraryIcons.Moon,
        text = stringResource(if (nightMode) R.string.library_reader_night_off else R.string.library_reader_night_on),
        selected = false,
        onClick = onToggleNightMode
      )
    }
    Text(
      text = stringResource(if (zoomed) R.string.library_tv_zoom_hint else R.string.library_tv_reader_hint),
      color = palette.muted,
      fontFamily = Vazirmatn,
      fontSize = 16.sp
    )
  }
}

/** The contents or the bookmarks: a list of pages to jump to, moved through by the remote. */
@Composable
private fun TvListDialog(
  title: String,
  entries: List<Pair<String, Int>>,
  currentIndex: Int,
  onOpen: (page: Int) -> Unit,
  onDismiss: () -> Unit,
  empty: String? = null
) {
  val palette = LocalTasnimPalette.current
  val first = remember { FocusRequester() }
  val focusIndex = currentIndex.coerceAtLeast(0)
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = (focusIndex - 2).coerceAtLeast(0))
  LaunchedEffect(Unit) {
    withFrameNanos { }
    runCatching { first.requestFocus() }
  }
  Dialog(onDismissRequest = onDismiss) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Column(
        modifier = Modifier
          .width(640.dp)
          .heightIn(max = 460.dp)
          .background(palette.background, RoundedCornerShape(28.dp))
          .padding(top = 24.dp)
      ) {
        Text(
          text = title,
          color = palette.ink,
          fontFamily = Vazirmatn,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .padding(horizontal = 28.dp)
            // focusable so the dialog always has a focus, even when the list is empty
            .then(if (entries.isEmpty()) Modifier.focusRequester(first).tvClickable(RoundedCornerShape(8.dp)) {} else Modifier)
        )
        if (entries.isEmpty() && empty != null) {
          Text(
            text = empty,
            color = palette.muted,
            fontFamily = Vazirmatn,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 28.dp)
          )
        }
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          itemsIndexed(entries) { index, (label, page) ->
            val isCurrent = index == currentIndex
            val shape = RoundedCornerShape(14.dp)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .then(if (index == focusIndex) Modifier.focusRequester(first) else Modifier)
                .tvClickable(shape) { onOpen(page) }
                .background(if (isCurrent) palette.track else palette.surface, shape)
                .padding(horizontal = 18.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                text = label,
                color = if (isCurrent) palette.accent else palette.ink,
                fontFamily = Vazirmatn,
                fontSize = 18.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Text(
                text = (page + 1).toString().toPersianDigits(),
                color = palette.muted,
                fontFamily = Vazirmatn,
                fontSize = 16.sp
              )
            }
          }
        }
      }
    }
  }
}

private const val TOOLS_TIMEOUT_MILLIS = 8_000L

/** How far one press of up or down moves a zoomed page. */
private const val SCROLL_STEP_PX = 360f
