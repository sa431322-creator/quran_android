package com.quran.mobile.feature.library.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.tvClickable
import com.quran.labs.androidquran.common.ui.core.TvSafeTop
import com.quran.labs.androidquran.common.ui.core.TvSafeSide
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.formatPersianCount
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.DownloadState
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.ui.BookCover
import com.quran.mobile.feature.library.ui.LibraryIcons
import com.quran.mobile.feature.library.ui.LoadingState
import com.quran.mobile.feature.library.ui.MessageState
import com.quran.mobile.feature.library.ui.ReadingBar
import com.quran.mobile.feature.library.ui.fileSizeLabel
import com.quran.mobile.feature.library.ui.persian
import com.quran.mobile.feature.library.ui.readingTimeLabel
import com.quran.mobile.feature.library.ui.tv.TvPill
import kotlin.math.roundToInt

/**
 * A book's page on a TV (docs/tv/PLAN.md, phase 3): the cover on one side; on the other the
 * title, facts and progress, the actions (the main one — read, continue or download — has the
 * first focus), the description, every chapter and similar books, all reachable by the remote.
 * No share button: TVs have no share sheet.
 */
@Composable
fun BookDetailsTvScreen(
  state: BookDetailsState,
  device: BookOnDevice,
  download: DownloadState,
  onRetry: () -> Unit,
  onToggleSaved: () -> Unit,
  onRead: (page: Int?) -> Unit,
  onDownload: () -> Unit,
  onOpenBook: (LibraryBook) -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Box(
        Modifier
          .fillMaxSize()
          .background(palette.background)
      ) {
        when (state) {
          BookDetailsState.Loading -> LoadingState()
          BookDetailsState.NotFound -> MessageState(text = stringResource(R.string.library_book_not_found))
          BookDetailsState.Error -> MessageState(
            text = stringResource(R.string.library_error),
            action = stringResource(R.string.library_retry),
            onAction = onRetry
          )
          is BookDetailsState.Ready -> TvDetails(
            book = state.book,
            similar = state.similar,
            device = device,
            download = download,
            onToggleSaved = onToggleSaved,
            onRead = onRead,
            onDownload = onDownload,
            onOpenBook = onOpenBook
          )
        }
      }
    }
  }
}

@Composable
private fun TvDetails(
  book: LibraryBook,
  similar: List<LibraryBook>,
  device: BookOnDevice,
  download: DownloadState,
  onToggleSaved: () -> Unit,
  onRead: (page: Int?) -> Unit,
  onDownload: () -> Unit,
  onOpenBook: (LibraryBook) -> Unit
) {
  val palette = LocalTasnimPalette.current
  val progress = device.progress
  val currentChapter = remember(book.chapters, progress?.page) {
    progress?.let { p -> book.chapters.indexOfLast { it.page - 1 <= p.page } } ?: -1
  }
  val mainAction = remember { FocusRequester() }

  // the main action takes the first focus, once the window can take it; a book opened from
  // «similar books» is a new page and starts there again
  val windowFocused = LocalWindowInfo.current.isWindowFocused
  var focused by rememberSaveable(book.id) { mutableStateOf(false) }
  LaunchedEffect(windowFocused, book.id) {
    if (!windowFocused || focused) return@LaunchedEffect
    withFrameNanos { }
    focused = runCatching { mainAction.requestFocus() }.isSuccess
  }

  Row(
    modifier = Modifier
      .fillMaxSize()
      .padding(start = TvSafeSide, end = TvSafeSide),
    horizontalArrangement = Arrangement.spacedBy(48.dp)
  ) {
    // the teal band of the phone page, as a column behind the cover
    Column(
      modifier = Modifier
        .width(300.dp)
        .fillMaxHeight()
        .background(palette.primary, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
        .padding(horizontal = 24.dp, vertical = TvSafeTop + 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        text = stringResource(book.category.titleRes),
        color = palette.onPrimaryMuted,
        fontFamily = Vazirmatn,
        fontSize = 18.sp
      )
      BookCover(
        book = book,
        titleSize = 20.sp,
        spine = 8.dp,
        modifier = Modifier
          .size(width = 220.dp, height = 310.dp)
          .border(2.dp, palette.background, RoundedCornerShape(8.dp))
      )
    }

    LazyColumn(
      contentPadding = PaddingValues(top = TvSafeTop + 12.dp, bottom = TvSafeTop + 24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
    ) {
      item(key = "title") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = book.bookTitle,
            color = palette.ink,
            fontFamily = Vazirmatn,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 44.sp,
            modifier = Modifier.semantics { heading() }
          )
          book.author?.let {
            Text(stringResource(R.string.library_author, it), color = palette.accent, fontFamily = Vazirmatn, fontSize = 18.sp)
          }
          book.translator?.let {
            Text(stringResource(R.string.library_translator, it), color = palette.muted, fontFamily = Vazirmatn, fontSize = 18.sp)
          }
        }
      }
      item(key = "facts") { TvFacts(book = book, knownPages = progress?.pageCount ?: 0) }
      if (progress != null) {
        item(key = "progress") {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = stringResource(R.string.library_last_read, (progress.page + 1).persian()),
                color = palette.ink,
                fontFamily = Vazirmatn,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
              )
              progress.fraction?.let {
                Text(
                  text = (it * 100).roundToInt().persian() + "٪",
                  color = palette.accent,
                  fontFamily = Vazirmatn,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
            progress.fraction?.let { ReadingBar(fraction = it, height = 6.dp) }
          }
        }
      }
      item(key = "actions") {
        TvActions(
          book = book,
          device = device,
          download = download,
          mainAction = mainAction,
          onRead = onRead,
          onDownload = onDownload,
          onToggleSaved = onToggleSaved
        )
      }
      if (book.description.isNotEmpty()) {
        item(key = "about") {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvSection(stringResource(R.string.library_about))
            Text(
              text = book.description,
              color = palette.muted,
              fontFamily = Vazirmatn,
              fontSize = 18.sp,
              lineHeight = 30.sp
            )
          }
        }
      }
      if (book.chapters.isNotEmpty()) {
        item(key = "contents") {
          TvSection(
            stringResource(R.string.library_contents) + " · " +
              stringResource(R.string.library_chapter_count, formatPersianCount(book.chapters.size))
          )
        }
        // every chapter: on a TV a list is quicker to move down than a «show all» link
        itemsIndexed(book.chapters, key = { index, _ -> "chapter$index" }) { index, chapter ->
          val current = index == currentChapter
          val read = currentChapter >= 0 && index < currentChapter
          val shape = RoundedCornerShape(14.dp)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .tvClickable(shape) { onRead(chapter.page - 1) }
              .background(if (current) palette.track else palette.surface, shape)
              .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Icon(
              imageVector = when {
                read -> LibraryIcons.Done
                current -> LibraryIcons.Play
                else -> LibraryIcons.Pending
              },
              contentDescription = null,
              tint = if (read || current) palette.accent else palette.outline,
              modifier = Modifier.size(22.dp)
            )
            Text(
              text = chapter.title,
              color = if (current) palette.accent else palette.ink,
              fontFamily = Vazirmatn,
              fontSize = 18.sp,
              fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Text(text = chapter.page.persian(), color = palette.muted, fontFamily = Vazirmatn, fontSize = 16.sp)
          }
        }
      }
      if (similar.isNotEmpty()) {
        item(key = "similar") {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSection(stringResource(R.string.library_similar))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(22.dp),
              // room for the focused cover's frame, which grows past its bounds
              contentPadding = PaddingValues(10.dp)
            ) {
              items(similar, key = { it.id }) { other ->
                Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  BookCover(
                    book = other,
                    titleSize = 12.sp,
                    spine = 4.dp,
                    modifier = Modifier
                      .size(120.dp, 168.dp)
                      .tvClickable(RoundedCornerShape(8.dp)) { onOpenBook(other) }
                  )
                  Text(
                    text = other.bookTitle,
                    color = palette.ink,
                    fontFamily = Vazirmatn,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TvSection(text: String) {
  Text(
    text = text,
    color = LocalTasnimPalette.current.ink,
    fontFamily = Vazirmatn,
    fontSize = 22.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier
      .padding(top = 8.dp)
      .semantics { heading() }
  )
}

/** Pages, reading time, size and language in a row; what isn't known is left out. */
@Composable
private fun TvFacts(book: LibraryBook, knownPages: Int) {
  val palette = LocalTasnimPalette.current
  val pages = book.pageCount ?: knownPages.takeIf { it > 0 }
  val facts = buildList {
    if (pages != null) {
      add(LibraryIcons.Pages to formatPersianCount(pages) + " " + stringResource(R.string.library_stat_pages))
      add(LibraryIcons.Clock to readingTimeLabel(pages))
    }
    book.fileSizeBytes?.let { add(LibraryIcons.Size to fileSizeLabel(it)) }
    book.language?.let { add(LibraryIcons.Language to it) }
  }
  if (facts.isEmpty()) return
  Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
    facts.forEach { (icon, text) ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Text(text = text, color = palette.ink, fontFamily = Vazirmatn, fontSize = 18.sp)
      }
    }
  }
}

/** Read, continue or download (the first focus), and save; a running download shows its share. */
@Composable
private fun TvActions(
  book: LibraryBook,
  device: BookOnDevice,
  download: DownloadState,
  mainAction: FocusRequester,
  onRead: (page: Int?) -> Unit,
  onDownload: () -> Unit,
  onToggleSaved: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  val progress = device.progress
  val running = download as? DownloadState.Running
  val label = when {
    device.offline && progress != null ->
      stringResource(R.string.library_continue_from, (progress.page + 1).persian())
    device.offline -> stringResource(R.string.library_start_reading)
    running != null -> stringResource(R.string.library_downloading) +
      (running.fraction?.let { " " + (it * 100).roundToInt().persian() + "٪" } ?: "")
    book.fileUrl != null -> stringResource(R.string.library_download) +
      (book.fileSizeBytes?.let { " (" + fileSizeLabel(it) + ")" } ?: "")
    else -> stringResource(R.string.library_file_unavailable)
  }
  // a running download keeps the focus on its button and ignores presses
  val onClick = when {
    device.offline -> ({ onRead(null) })
    running == null && book.fileUrl != null -> onDownload
    else -> ({})
  }
  val shape = RoundedCornerShape(30.dp)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(20.dp),
      // room for the focused button's frame, which grows past its bounds
      modifier = Modifier.padding(6.dp)
    ) {
      Row(
        modifier = Modifier
          .focusRequester(mainAction)
          .heightIn(min = 60.dp)
          .tvClickable(shape, onClick)
          .background(palette.primary, shape)
          .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        when {
          running != null -> CircularProgressIndicator(
            progress = { running.fraction ?: 0f },
            color = palette.gold,
            trackColor = palette.onPrimary.copy(alpha = 0.2f),
            strokeWidth = 3.dp,
            modifier = Modifier.size(24.dp)
          )
          device.offline -> Icon(LibraryIcons.Book, null, tint = palette.onPrimary, modifier = Modifier.size(24.dp))
          book.fileUrl != null -> Icon(LibraryIcons.Download, null, tint = palette.onPrimary, modifier = Modifier.size(24.dp))
        }
        Text(
          text = label,
          color = palette.onPrimary,
          fontFamily = Vazirmatn,
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1
        )
      }
      TvPill(
        icon = if (device.saved) LibraryIcons.BookmarkFilled else LibraryIcons.Bookmark,
        text = stringResource(if (device.saved) R.string.library_unsave else R.string.library_save),
        selected = device.saved,
        onClick = onToggleSaved
      )
      if (device.offline) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(LibraryIcons.Done, null, tint = palette.accent, modifier = Modifier.size(22.dp))
          Text(stringResource(R.string.library_offline), color = palette.accent, fontFamily = Vazirmatn, fontSize = 16.sp, maxLines = 1)
        }
      }
    }
    if (download == DownloadState.Failed) {
      Text(
        text = stringResource(R.string.library_download_failed),
        color = palette.live,
        fontFamily = Vazirmatn,
        fontSize = 16.sp
      )
    }
    Spacer(Modifier.height(4.dp))
  }
}
