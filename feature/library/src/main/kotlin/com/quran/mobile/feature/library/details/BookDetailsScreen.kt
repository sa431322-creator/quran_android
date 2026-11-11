package com.quran.mobile.feature.library.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.formatPersianCount
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.DownloadState
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryChapter
import com.quran.mobile.feature.library.data.ReadingProgress
import com.quran.mobile.feature.library.ui.BookCover
import com.quran.mobile.feature.library.ui.LibraryIcons
import com.quran.mobile.feature.library.ui.LoadingState
import com.quran.mobile.feature.library.ui.MessageState
import com.quran.mobile.feature.library.ui.ReadingBar
import com.quran.mobile.feature.library.ui.SectionTitle
import com.quran.mobile.feature.library.ui.fileSizeLabel
import com.quran.mobile.feature.library.ui.persian
import com.quran.mobile.feature.library.ui.readingTimeLabel
import kotlin.math.roundToInt

/** How far the cover reaches up into the teal band. */
private val CoverOverlap = 64.dp

/** Chapters shown before «نمایش همه فصل‌ها». */
private const val COLLAPSED_CHAPTERS = 5

@Composable
fun BookDetailsScreen(
  state: BookDetailsState,
  device: BookOnDevice,
  download: DownloadState,
  onBack: () -> Unit,
  onRetry: () -> Unit,
  onToggleSaved: () -> Unit,
  onShare: () -> Unit,
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
          BookDetailsState.Loading -> Column {
            Band(title = null, saved = false, onBack = onBack, onToggleSaved = null, onShare = null)
            LoadingState()
          }
          BookDetailsState.NotFound -> Column {
            Band(title = null, saved = false, onBack = onBack, onToggleSaved = null, onShare = null)
            MessageState(text = stringResource(R.string.library_book_not_found))
          }
          BookDetailsState.Error -> Column {
            Band(title = null, saved = false, onBack = onBack, onToggleSaved = null, onShare = null)
            MessageState(
              text = stringResource(R.string.library_error),
              action = stringResource(R.string.library_retry),
              onAction = onRetry
            )
          }
          is BookDetailsState.Ready -> Details(
            book = state.book,
            similar = state.similar,
            device = device,
            download = download,
            onBack = onBack,
            onToggleSaved = onToggleSaved,
            onShare = onShare,
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
private fun Details(
  book: LibraryBook,
  similar: List<LibraryBook>,
  device: BookOnDevice,
  download: DownloadState,
  onBack: () -> Unit,
  onToggleSaved: () -> Unit,
  onShare: () -> Unit,
  onRead: (page: Int?) -> Unit,
  onDownload: () -> Unit,
  onOpenBook: (LibraryBook) -> Unit
) {
  var showAllChapters by rememberSaveable(book.id) { mutableStateOf(false) }
  val progress = device.progress
  val currentChapter = remember(book.chapters, progress?.page) {
    progress?.let { p -> book.chapters.indexOfLast { it.page - 1 <= p.page } } ?: -1
  }
  val chapters = if (showAllChapters) book.chapters else book.chapters.take(COLLAPSED_CHAPTERS)
  val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  LazyColumn(
    contentPadding = PaddingValues(bottom = bottom + 24.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    item(key = "band") {
      Band(
        title = stringResource(book.category.titleRes),
        saved = device.saved,
        onBack = onBack,
        onToggleSaved = onToggleSaved,
        onShare = onShare,
        extraBottom = CoverOverlap
      )
    }
    item(key = "cover") {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          // pulls the cover up over the band without leaving a gap below it
          .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val pull = CoverOverlap.roundToPx()
            layout(placeable.width, placeable.height - pull) { placeable.place(0, -pull) }
          },
        contentAlignment = Alignment.Center
      ) {
        BookCover(
          book = book,
          titleSize = 17.sp,
          spine = 8.dp,
          modifier = Modifier
            .size(width = 132.dp, height = 186.dp)
            .border(2.dp, LocalTasnimPalette.current.background, RoundedCornerShape(8.dp))
        )
      }
    }
    item(key = "title") { TitleBlock(book) }
    item(key = "facts") { Facts(book = book, knownPages = progress?.pageCount ?: 0) }
    if (progress != null) {
      item(key = "progress") { ProgressCard(progress) }
    }
    item(key = "actions") {
      Actions(book = book, device = device, download = download, onRead = onRead, onDownload = onDownload)
    }
    if (book.description.isNotEmpty() || book.tags.isNotEmpty()) {
      item(key = "about") { About(book) }
    }
    if (book.chapters.isNotEmpty()) {
      item(key = "contents") {
        SectionTitle(
          text = stringResource(R.string.library_contents),
          trailing = stringResource(R.string.library_chapter_count, formatPersianCount(book.chapters.size)),
          modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp)
        )
      }
      itemsIndexed(chapters, key = { index, _ -> "chapter$index" }) { index, chapter ->
        ChapterRow(
          chapter = chapter,
          status = when {
            currentChapter < 0 -> ChapterStatus.UNREAD
            index < currentChapter -> ChapterStatus.READ
            index == currentChapter -> ChapterStatus.CURRENT
            else -> ChapterStatus.UNREAD
          },
          onClick = { onRead(chapter.page - 1) }
        )
      }
      if (book.chapters.size > COLLAPSED_CHAPTERS) {
        item(key = "allChapters") {
          ToggleLink(
            text = stringResource(
              if (showAllChapters) R.string.library_show_fewer_chapters else R.string.library_show_all_chapters
            ),
            onClick = { showAllChapters = !showAllChapters }
          )
        }
      }
    }
    if (similar.isNotEmpty()) {
      item(key = "similar") { Similar(books = similar, onOpenBook = onOpenBook) }
    }
  }
}

/** The teal band at the top: back, save and share, and the book's category. */
@Composable
private fun Band(
  title: String?,
  saved: Boolean,
  onBack: () -> Unit,
  onToggleSaved: (() -> Unit)?,
  onShare: (() -> Unit)?,
  extraBottom: Dp = 0.dp
) {
  val palette = LocalTasnimPalette.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(palette.primary, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
      .windowInsetsPadding(
        WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
      )
      .padding(bottom = 12.dp + extraBottom)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
        Icon(LibraryIcons.Back, stringResource(R.string.library_back), tint = palette.onPrimary)
      }
      Spacer(Modifier.weight(1f))
      if (onToggleSaved != null) {
        IconButton(onClick = onToggleSaved, modifier = Modifier.size(44.dp)) {
          Icon(
            imageVector = if (saved) LibraryIcons.BookmarkFilled else LibraryIcons.Bookmark,
            contentDescription = stringResource(if (saved) R.string.library_unsave else R.string.library_save),
            tint = if (saved) palette.gold else palette.onPrimary
          )
        }
      }
      if (onShare != null) {
        IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
          Icon(LibraryIcons.Share, stringResource(R.string.library_share), tint = palette.onPrimary)
        }
      }
    }
    if (title != null) {
      Text(
        text = title,
        color = palette.onPrimaryMuted,
        fontFamily = Vazirmatn,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

@Composable
private fun TitleBlock(book: LibraryBook) {
  val palette = LocalTasnimPalette.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp, top = 14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Text(
      text = book.bookTitle,
      color = palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      lineHeight = 30.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { heading() }
    )
    book.author?.let {
      Text(stringResource(R.string.library_author, it), color = palette.accent, fontFamily = Vazirmatn, fontSize = 14.sp)
    }
    book.translator?.let {
      Text(stringResource(R.string.library_translator, it), color = palette.muted, fontFamily = Vazirmatn, fontSize = 13.sp)
    }
  }
}

/** Pages, reading time, size and language in one row; what isn't known is left out. */
@Composable
private fun Facts(book: LibraryBook, knownPages: Int) {
  val palette = LocalTasnimPalette.current
  val pages = book.pageCount ?: knownPages.takeIf { it > 0 }
  val facts = buildList {
    if (pages != null) {
      add(Fact(LibraryIcons.Pages, formatPersianCount(pages), stringResource(R.string.library_stat_pages)))
      add(Fact(LibraryIcons.Clock, readingTimeLabel(pages), stringResource(R.string.library_stat_time)))
    }
    book.fileSizeBytes?.let {
      add(Fact(LibraryIcons.Size, fileSizeLabel(it), stringResource(R.string.library_stat_size)))
    }
    book.language?.let {
      add(Fact(LibraryIcons.Language, it, stringResource(R.string.library_stat_language)))
    }
  }
  if (facts.isEmpty()) return

  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier = Modifier
      .padding(start = 20.dp, end = 20.dp, top = 18.dp)
      .fillMaxWidth()
      .clip(shape)
      .background(palette.surface)
      .border(1.dp, palette.border, shape)
      .padding(vertical = 12.dp)
  ) {
    facts.forEachIndexed { index, fact ->
      if (index > 0) {
        Box(
          Modifier
            .width(1.dp)
            .height(48.dp)
            .background(palette.border)
            .align(Alignment.CenterVertically)
        )
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Icon(fact.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Text(
          text = fact.value,
          color = palette.ink,
          fontFamily = Vazirmatn,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(text = fact.label, color = palette.muted, fontFamily = Vazirmatn, fontSize = 11.sp)
      }
    }
  }
}

private class Fact(val icon: ImageVector, val value: String, val label: String)

@Composable
private fun ProgressCard(progress: ReadingProgress) {
  val palette = LocalTasnimPalette.current
  val shape = RoundedCornerShape(18.dp)
  Column(
    modifier = Modifier
      .padding(start = 20.dp, end = 20.dp, top = 12.dp)
      .fillMaxWidth()
      .clip(shape)
      .background(palette.surface)
      .border(1.dp, palette.border, shape)
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .semantics(mergeDescendants = true) { },
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(R.string.library_your_progress),
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f)
      )
      progress.fraction?.let {
        Text(
          text = (it * 100).roundToInt().persian() + "٪",
          color = palette.accent,
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
    progress.fraction?.let { ReadingBar(fraction = it, height = 6.dp) }
    Text(
      text = stringResource(R.string.library_last_read, (progress.page + 1).persian()),
      color = palette.muted,
      fontFamily = Vazirmatn,
      fontSize = 12.sp
    )
  }
}

/** Read (or download first), and whether the book is kept on the device. */
@Composable
private fun Actions(
  book: LibraryBook,
  device: BookOnDevice,
  download: DownloadState,
  onRead: (page: Int?) -> Unit,
  onDownload: () -> Unit
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
  val enabled = device.offline || (running == null && book.fileUrl != null)
  val onClick = if (device.offline) ({ onRead(null) }) else onDownload

  Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      val shape = RoundedCornerShape(18.dp)
      Row(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 54.dp)
          .clip(shape)
          .background(if (enabled || running != null) palette.primary else palette.track)
          .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        when {
          running != null -> CircularProgressIndicator(
            progress = { running.fraction ?: 0f },
            color = palette.gold,
            trackColor = palette.onPrimary.copy(alpha = 0.2f),
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(20.dp)
          )
          device.offline -> Icon(LibraryIcons.Book, null, tint = palette.onPrimary, modifier = Modifier.size(20.dp))
          book.fileUrl != null -> Icon(LibraryIcons.Download, null, tint = palette.onPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
          text = label,
          color = if (enabled || running != null) palette.onPrimary else palette.muted,
          fontFamily = Vazirmatn,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      if (device.offline) {
        val offlineLabel = stringResource(R.string.library_available_offline)
        Column(
          modifier = Modifier
            .width(64.dp)
            .heightIn(min = 54.dp)
            .border(1.5.dp, palette.accent, RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { stateDescription = offlineLabel },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(LibraryIcons.Done, null, tint = palette.accent, modifier = Modifier.size(20.dp))
          Text(stringResource(R.string.library_offline), color = palette.accent, fontFamily = Vazirmatn, fontSize = 11.sp)
        }
      }
    }
    if (download == DownloadState.Failed) {
      Text(
        text = stringResource(R.string.library_download_failed),
        color = palette.live,
        fontFamily = Vazirmatn,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp)
      )
    }
  }
}

@Composable
private fun About(book: LibraryBook) {
  val palette = LocalTasnimPalette.current
  var expanded by rememberSaveable(book.id) { mutableStateOf(false) }
  var overflows by remember(book.id) { mutableStateOf(false) }
  Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
    SectionTitle(stringResource(R.string.library_about))
    if (book.description.isNotEmpty()) {
      Text(
        text = book.description,
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = Modifier.padding(top = 6.dp)
      )
      if (overflows || expanded) {
        Text(
          text = stringResource(if (expanded) R.string.library_less else R.string.library_more),
          color = palette.accent,
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(vertical = 6.dp, horizontal = 2.dp)
        )
      }
    }
    if (book.tags.isNotEmpty()) {
      // the same outlined gold chip as the live screens' «فاز ۲» badge
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
      ) {
        book.tags.forEach { tag ->
          Text(
            text = tag,
            color = palette.chipText,
            fontFamily = Vazirmatn,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .border(1.dp, palette.chipBorder, RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 5.dp)
          )
        }
      }
    }
  }
}

private enum class ChapterStatus { READ, CURRENT, UNREAD }

@Composable
private fun ChapterRow(chapter: LibraryChapter, status: ChapterStatus, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  val current = status == ChapterStatus.CURRENT
  val statusLabel = when (status) {
    ChapterStatus.READ -> stringResource(R.string.library_chapter_read)
    ChapterStatus.CURRENT -> stringResource(R.string.library_chapter_current)
    ChapterStatus.UNREAD -> null
  }
  val pageLabel = stringResource(R.string.library_reader_page_description, chapter.page.persian())
  Row(
    modifier = Modifier
      .padding(horizontal = 12.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .then(if (current) Modifier.background(palette.track) else Modifier)
      .clickable(role = Role.Button, onClick = onClick)
      .heightIn(min = 48.dp)
      .padding(horizontal = 10.dp, vertical = 8.dp)
      .semantics(mergeDescendants = true) {
        contentDescription = "${chapter.title}، $pageLabel"
        statusLabel?.let { stateDescription = it }
      },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Icon(
      imageVector = when (status) {
        ChapterStatus.READ -> LibraryIcons.Done
        ChapterStatus.CURRENT -> LibraryIcons.Play
        ChapterStatus.UNREAD -> LibraryIcons.Pending
      },
      contentDescription = null,
      tint = if (status == ChapterStatus.UNREAD) palette.outline else palette.accent,
      modifier = Modifier.size(18.dp)
    )
    Text(
      text = chapter.title,
      color = if (current) palette.accent else palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 14.sp,
      fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )
    Text(text = chapter.page.persian(), color = palette.muted, fontFamily = Vazirmatn, fontSize = 13.sp)
  }
}

@Composable
private fun ToggleLink(text: String, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  Text(
    text = text,
    color = palette.accent,
    fontFamily = Vazirmatn,
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .padding(horizontal = 20.dp, vertical = 4.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 12.dp)
  )
}

@Composable
private fun Similar(books: List<LibraryBook>, onOpenBook: (LibraryBook) -> Unit) {
  val palette = LocalTasnimPalette.current
  Column(Modifier.padding(top = 24.dp)) {
    SectionTitle(
      text = stringResource(R.string.library_similar),
      modifier = Modifier.padding(horizontal = 20.dp)
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(books, key = { it.id }) { book ->
        Column(
          modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) { onOpenBook(book) },
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          BookCover(book = book, titleSize = 11.sp, spine = 4.dp, modifier = Modifier.size(96.dp, 134.dp))
          Text(
            text = book.bookTitle,
            color = palette.ink,
            fontFamily = Vazirmatn,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
