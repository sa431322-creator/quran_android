package com.quran.mobile.feature.library.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.LibraryChapter
import com.quran.mobile.feature.library.ui.LibraryIcons
import kotlinx.coroutines.launch

/** The dark surround of a page in night mode. */
private val NightBackground = Color(0xFF0B0F11)

/** Turns a white page dark and its black text light grey, which is gentler than pure white. */
private val NightFilter = ColorFilter.colorMatrix(
  ColorMatrix(
    floatArrayOf(
      -0.86f, 0f, 0f, 0f, 232f,
      0f, -0.86f, 0f, 0f, 228f,
      0f, 0f, -0.86f, 0f, 220f,
      0f, 0f, 0f, 1f, 0f
    )
  )
)

/**
 * A book: one page per screen, turned right to left like a Persian book. Tapping the page
 * shows or hides the bars above and below it.
 */
@Composable
fun LibraryReaderScreen(
  title: String,
  state: ReaderState,
  chapters: List<LibraryChapter>,
  bookmarks: List<Int>,
  nightMode: Boolean,
  onPageShown: (page: Int, pageCount: Int) -> Unit,
  onToggleBookmark: (page: Int) -> Unit,
  onToggleNightMode: () -> Unit,
  onBack: () -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(if (nightMode) NightBackground else palette.background)
          .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      ) {
        when (state) {
          ReaderState.Loading -> Column {
            ReaderTopBar(title = title, onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp)
            }
          }
          ReaderState.Error -> Column {
            ReaderTopBar(title = title, onBack = onBack)
            Box(
              Modifier
                .fillMaxSize()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = stringResource(R.string.library_reader_error),
                color = palette.muted,
                fontFamily = Vazirmatn,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
              )
            }
          }
          is ReaderState.Ready -> Reader(
            title = title,
            state = state,
            chapters = chapters,
            bookmarks = bookmarks,
            nightMode = nightMode,
            onPageShown = onPageShown,
            onToggleBookmark = onToggleBookmark,
            onToggleNightMode = onToggleNightMode,
            onBack = onBack
          )
        }
      }
    }
  }
}

private enum class ReaderSheet { CONTENTS, BOOKMARKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Reader(
  title: String,
  state: ReaderState.Ready,
  chapters: List<LibraryChapter>,
  bookmarks: List<Int>,
  nightMode: Boolean,
  onPageShown: (page: Int, pageCount: Int) -> Unit,
  onToggleBookmark: (page: Int) -> Unit,
  onToggleNightMode: () -> Unit,
  onBack: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  val pageCount = state.pages.pageCount
  val pagerState = rememberPagerState(initialPage = state.startPage) { pageCount }
  val scope = rememberCoroutineScope()
  var barsVisible by rememberSaveable { mutableStateOf(true) }
  var sheet by rememberSaveable { mutableStateOf<ReaderSheet?>(null) }
  var showGoTo by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }.collect { onPageShown(it, pageCount) }
  }
  fun goTo(page: Int) {
    scope.launch { pagerState.scrollToPage(page.coerceIn(0, pageCount - 1)) }
  }
  // in night mode the bars go dark too, in the dark palette of the live screens
  val barPalette = if (nightMode) {
    palette.copy(
      background = NightBackground,
      ink = palette.onVideo,
      muted = Color(0xFFA9A08C),
      accent = Color(0xFF8CCADB),
      track = Color(0xFF1A252A)
    )
  } else {
    palette
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
    // in a right-to-left layout the next page comes in from the left, as in a Persian book
    HorizontalPager(
      state = pagerState,
      beyondViewportPageCount = 1,
      key = { it },
      modifier = Modifier.fillMaxSize()
    ) { index ->
      Page(
        pages = state.pages,
        index = index,
        widthPx = widthPx,
        nightMode = nightMode,
        onTap = { barsVisible = !barsVisible }
      )
    }
  }

  CompositionLocalProvider(LocalTasnimPalette provides barPalette) {
    AnimatedVisibility(
      visible = barsVisible,
      enter = fadeIn() + slideInVertically { -it },
      exit = fadeOut() + slideOutVertically { -it }
    ) {
      val bookmarked = pagerState.currentPage in bookmarks
      ReaderTopBar(title = title, onBack = onBack, translucent = true) {
        if (chapters.isNotEmpty()) {
          IconButton(onClick = { sheet = ReaderSheet.CONTENTS }, modifier = Modifier.size(44.dp)) {
            Icon(LibraryIcons.Contents, stringResource(R.string.library_reader_contents), tint = barPalette.accent)
          }
        }
        IconButton(onClick = { onToggleBookmark(pagerState.currentPage) }, modifier = Modifier.size(44.dp)) {
          Icon(
            imageVector = if (bookmarked) LibraryIcons.BookmarkFilled else LibraryIcons.Bookmark,
            contentDescription = stringResource(
              if (bookmarked) R.string.library_reader_bookmark_remove else R.string.library_reader_bookmark_add
            ),
            tint = if (bookmarked) barPalette.gold else barPalette.accent
          )
        }
      }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
      AnimatedVisibility(
        visible = barsVisible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
      ) {
        ReaderBottomBar(
          pagerState = pagerState,
          pageCount = pageCount,
          nightMode = nightMode,
          onSeek = ::goTo,
          onToggleNightMode = onToggleNightMode,
          onGoToPage = { showGoTo = true },
          onShowBookmarks = { sheet = ReaderSheet.BOOKMARKS }
        )
      }
    }
  }

  if (showGoTo) {
    GoToPageDialog(
      pageCount = pageCount,
      onGo = { page ->
        showGoTo = false
        goTo(page)
      },
      onDismiss = { showGoTo = false }
    )
  }

  val openSheet = sheet
  if (openSheet != null) {
    ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = palette.background) {
      when (openSheet) {
        ReaderSheet.CONTENTS -> ContentsList(
          chapters = chapters,
          currentPage = pagerState.currentPage,
          onOpen = { page ->
            sheet = null
            goTo(page)
          }
        )
        ReaderSheet.BOOKMARKS -> BookmarksList(
          bookmarks = bookmarks,
          chapters = chapters,
          onOpen = { page ->
            sheet = null
            goTo(page)
          }
        )
      }
    }
  }
}

@Composable
private fun Page(pages: PdfPages, index: Int, widthPx: Int, nightMode: Boolean, onTap: () -> Unit) {
  val palette = LocalTasnimPalette.current
  val bitmap by produceState<Bitmap?>(null, pages, index, widthPx) {
    value = runCatching { pages.render(index, widthPx) }.getOrNull()
  }
  val description = stringResource(
    R.string.library_reader_page_description,
    (index + 1).toString().toPersianDigits()
  )

  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var size by remember { mutableStateOf(IntSize.Zero) }

  fun clamp(value: Offset): Offset {
    val maxX = size.width * (scale - 1) / 2
    val maxY = size.height * (scale - 1) / 2
    return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      // a zoomed page stays inside its own screen
      .clipToBounds()
      .onSizeChanged { size = it }
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = { onTap() },
          onDoubleTap = {
            scale = if (scale > 1f) 1f else 2.5f
            offset = Offset.Zero
          }
        )
      }
      .pointerInput(Unit) {
        // pinch to zoom, and pan while zoomed; a one-finger swipe at normal size is left to
        // the pager so it can turn the page
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false)
          do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1 || scale > 1f) {
              scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
              offset = if (scale == 1f) Offset.Zero else clamp(offset + event.calculatePan())
              event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
          } while (event.changes.any { it.pressed })
        }
      }
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center
  ) {
    val page = bitmap
    if (page == null) {
      CircularProgressIndicator(
        color = palette.accent,
        strokeWidth = 3.dp,
        modifier = Modifier.size(32.dp)
      )
    } else {
      Image(
        bitmap = page.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = if (nightMode) NightFilter else null,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
          }
      )
    }
  }
}

@Composable
private fun ReaderTopBar(
  title: String,
  onBack: () -> Unit,
  translucent: Boolean = false,
  actions: @Composable () -> Unit = {}
) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (translucent) Modifier.background(palette.background.copy(alpha = 0.96f)) else Modifier)
      .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
      Icon(LibraryIcons.Back, stringResource(R.string.library_back), tint = palette.ink)
    }
    Text(
      text = title,
      color = palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .semantics { heading() }
    )
    actions()
  }
}

@Composable
private fun ReaderBottomBar(
  pagerState: PagerState,
  pageCount: Int,
  nightMode: Boolean,
  onSeek: (Int) -> Unit,
  onToggleNightMode: () -> Unit,
  onGoToPage: () -> Unit,
  onShowBookmarks: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  // follows the pager, and the finger while the slider is dragged
  var dragging by remember { mutableStateOf<Float?>(null) }
  val shown = dragging?.toInt() ?: pagerState.currentPage
  val pageLabel = stringResource(
    R.string.library_reader_page,
    (shown + 1).toString().toPersianDigits(),
    pageCount.toString().toPersianDigits()
  )
  val sliderDescription = stringResource(R.string.library_reader_slider)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(palette.background.copy(alpha = 0.96f))
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = pageLabel,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
      )
      if (pageCount > 1) {
        // in a right-to-left layout the slider runs from the right, like the pages
        Slider(
          value = dragging ?: pagerState.currentPage.toFloat(),
          onValueChange = { dragging = it },
          onValueChangeFinished = {
            dragging?.let { onSeek(it.toInt()) }
            dragging = null
          },
          valueRange = 0f..(pageCount - 1).toFloat(),
          colors = SliderDefaults.colors(
            thumbColor = palette.accent,
            activeTrackColor = palette.accent,
            inactiveTrackColor = palette.track
          ),
          modifier = Modifier
            .weight(1f)
            .semantics {
              contentDescription = sliderDescription
              stateDescription = pageLabel
            }
        )
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      BarAction(
        icon = if (nightMode) LibraryIcons.Sun else LibraryIcons.Moon,
        label = stringResource(if (nightMode) R.string.library_reader_night_off else R.string.library_reader_night_on),
        onClick = onToggleNightMode
      )
      BarAction(LibraryIcons.GoToPage, stringResource(R.string.library_reader_go_to_page), onGoToPage)
      BarAction(LibraryIcons.Bookmarks, stringResource(R.string.library_bookmarks), onShowBookmarks)
    }
  }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .heightIn(min = 48.dp)
      .padding(horizontal = 14.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
    Text(text = label, color = palette.muted, fontFamily = Vazirmatn, fontSize = 11.sp)
  }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
  val palette = LocalTasnimPalette.current
  var text by rememberSaveable { mutableStateOf("") }
  var invalid by rememberSaveable { mutableStateOf(false) }
  val count = pageCount.toString().toPersianDigits()
  fun submit() {
    val page = text.toLatinDigits().trim().toIntOrNull()
    if (page == null || page !in 1..pageCount) invalid = true else onGo(page - 1)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = palette.surface,
    title = {
      Text(
        text = stringResource(R.string.library_reader_go_to_page),
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
      )
    },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = {
          text = it.filter { c -> c.isDigit() }.take(6)
          invalid = false
        },
        singleLine = true,
        isError = invalid,
        textStyle = TextStyle(color = palette.ink, fontFamily = Vazirmatn, fontSize = 16.sp),
        placeholder = {
          Text(stringResource(R.string.library_reader_page_hint, count), fontFamily = Vazirmatn, color = palette.muted)
        },
        supportingText = if (invalid) {
          { Text(stringResource(R.string.library_reader_page_invalid, count), fontFamily = Vazirmatn) }
        } else {
          null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = palette.accent,
          unfocusedBorderColor = palette.outline,
          cursorColor = palette.accent
        )
      )
    },
    confirmButton = {
      TextButton(onClick = ::submit) {
        Text(
          stringResource(R.string.library_reader_go),
          color = palette.accent,
          fontFamily = Vazirmatn,
          fontWeight = FontWeight.SemiBold
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.library_reader_cancel), color = palette.muted, fontFamily = Vazirmatn)
      }
    }
  )
}

/** Persian and Arabic-Indic digits typed on a Persian keyboard, read as ASCII digits. */
private fun String.toLatinDigits(): String = buildString(length) {
  for (c in this@toLatinDigits) {
    append(
      when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
      }
    )
  }
}

@Composable
private fun ContentsList(chapters: List<LibraryChapter>, currentPage: Int, onOpen: (Int) -> Unit) {
  val palette = LocalTasnimPalette.current
  val current = chapters.indexOfLast { it.page - 1 <= currentPage }
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
  Column {
    SheetTitle(stringResource(R.string.library_reader_contents))
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)
    ) {
      itemsIndexed(chapters) { index, chapter ->
        val isCurrent = index == current
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (isCurrent) Modifier.background(palette.track) else Modifier)
            .clickable { onOpen(chapter.page - 1) }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = chapter.title,
            color = if (isCurrent) palette.accent else palette.ink,
            fontFamily = Vazirmatn,
            fontSize = 14.sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
          )
          Text(chapter.page.toString().toPersianDigits(), color = palette.muted, fontFamily = Vazirmatn, fontSize = 13.sp)
        }
      }
    }
  }
}

@Composable
private fun BookmarksList(bookmarks: List<Int>, chapters: List<LibraryChapter>, onOpen: (Int) -> Unit) {
  val palette = LocalTasnimPalette.current
  Column {
    SheetTitle(stringResource(R.string.library_reader_bookmarks))
    if (bookmarks.isEmpty()) {
      Text(
        text = stringResource(R.string.library_reader_bookmarks_empty),
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp)
      )
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
      items(bookmarks) { page ->
        // the chapter the page falls in, so a bookmark says more than its number
        val chapter = chapters.lastOrNull { it.page - 1 <= page }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen(page) }
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(LibraryIcons.BookmarkFilled, null, tint = palette.gold, modifier = Modifier.size(20.dp))
          Column(Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.library_reader_page_description, (page + 1).toString().toPersianDigits()),
              color = palette.ink,
              fontFamily = Vazirmatn,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold
            )
            chapter?.let {
              Text(it.title, color = palette.muted, fontFamily = Vazirmatn, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SheetTitle(text: String) {
  Text(
    text = text,
    color = LocalTasnimPalette.current.ink,
    fontFamily = Vazirmatn,
    fontSize = 18.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .semantics { heading() }
  )
}
