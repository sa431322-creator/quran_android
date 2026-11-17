package com.quran.mobile.feature.library.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.formatPersianCount
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryCategory
import com.quran.mobile.feature.library.data.ReadingProgress
import com.quran.mobile.feature.library.presenter.BooksState
import com.quran.mobile.feature.library.presenter.LibraryPresenter
import com.quran.mobile.feature.library.ui.BookCover
import com.quran.mobile.feature.library.ui.LibraryIcons
import com.quran.mobile.feature.library.ui.LibraryShelf
import com.quran.mobile.feature.library.ui.LoadingState
import com.quran.mobile.feature.library.ui.MessageState
import com.quran.mobile.feature.library.ui.ReadingBar
import com.quran.mobile.feature.library.ui.byline
import com.quran.mobile.feature.library.ui.pagesLabel
import com.quran.mobile.feature.library.ui.persian
import kotlinx.coroutines.launch

/**
 * «کتابخانه» on a TV (docs/tv/PLAN.md, phase 3): the categories as a row of tabs and the books
 * as a grid of covers, all from the remote.
 *
 * - The search field only appears from its button, so moving down the screen never lands in a
 *   text field and opens the keyboard by itself.
 * - The bookmarks open in a dialog rather than the phone's bottom sheet.
 * - Coming back from a book, the focus is still on its cover.
 */
@Composable
fun LibraryTvScreen(
  presenter: LibraryPresenter,
  shelf: LibraryShelf,
  isOffline: (LibraryBook) -> Boolean,
  onOpenBook: (LibraryBook) -> Unit,
  onContinueBook: (LibraryBook) -> Unit,
  onOpenBookmark: (LibraryBook, Int) -> Unit
) {
  val state by presenter.state.collectAsState()
  var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
  var showBookmarks by rememberSaveable { mutableStateOf(false) }
  val allBooks = (state.books as? BooksState.Loaded)?.books.orEmpty()
  val firstFocus = remember { FocusRequester() }
  val searchButton = remember { FocusRequester() }
  val scope = rememberCoroutineScope()

  // a request made before the window has focus is lost, so it waits for the window; a later
  // return (from a book) keeps the focus where it was
  val windowFocused = LocalWindowInfo.current.isWindowFocused
  var focusedOnce by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(windowFocused, state.books is BooksState.Loaded) {
    if (!windowFocused || focusedOnce) return@LaunchedEffect
    withFrameNanos { }
    focusedOnce = runCatching { firstFocus.requestFocus() }.isSuccess
  }

  // back closes the search first (and clears it), then leaves
  BackHandler(enabled = searching) {
    searching = false
    presenter.search("")
    // the field leaves; the remote goes back to the button that opened it
    scope.launch {
      withFrameNanos { }
      runCatching { searchButton.requestFocus() }
    }
  }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.background)
          .padding(top = TvSafeTop)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = TvSafeSide),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            text = stringResource(R.string.library_title),
            fontFamily = NotoKufiArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = palette.ink,
            modifier = Modifier
              .weight(1f)
              .semantics { heading() }
          )
          TvPill(
            icon = LibraryIcons.Search,
            text = stringResource(R.string.library_search),
            selected = searching,
            onClick = { searching = true },
            modifier = Modifier.focusRequester(searchButton)
          )
          TvPill(
            icon = LibraryIcons.Bookmarks,
            text = stringResource(R.string.library_bookmarks),
            selected = false,
            onClick = { showBookmarks = true }
          )
        }
        if (searching) {
          TvSearchField(query = state.query, onQueryChange = presenter::search)
        }
        // the categories; «همه» takes the first focus
        LazyRow(
          contentPadding = PaddingValues(horizontal = TvSafeSide, vertical = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          val options = listOf<LibraryCategory?>(null) + LibraryCategory.entries
          items(options, key = { it?.id ?: "all" }) { category ->
            val count = if (category == null) state.totalCount else state.categoryCounts[category] ?: 0
            TvPill(
              text = stringResource(category?.titleRes ?: R.string.library_all),
              trailing = formatPersianCount(count),
              selected = category == state.selectedCategory,
              onClick = { presenter.selectCategory(category) },
              modifier = if (category == null) Modifier.focusRequester(firstFocus) else Modifier
            )
          }
        }
        Box(
          Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          when (state.books) {
            BooksState.Loading -> LoadingState()
            BooksState.Empty -> MessageState(text = stringResource(R.string.library_empty))
            BooksState.Error -> MessageState(
              text = stringResource(R.string.library_error),
              action = stringResource(R.string.library_retry),
              onAction = presenter::retry
            )
            is BooksState.Loaded -> {
              val visible = remember(state) { state.visibleBooks }
              // the most recently read book; hidden while searching so results come first
              val continueBook = remember(allBooks, shelf.progress) {
                allBooks.filter { it.id in shelf.progress }
                  .maxByOrNull { shelf.progress.getValue(it.id).updatedAt }
              }
              LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                contentPadding = PaddingValues(
                  start = TvSafeSide,
                  end = TvSafeSide,
                  top = 8.dp,
                  bottom = TvSafeTop + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize()
              ) {
                if (continueBook != null && state.query.isBlank()) {
                  item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                    TvContinueCard(
                      book = continueBook,
                      progress = shelf.progress.getValue(continueBook.id).withPageCount(continueBook.pageCount),
                      onClick = { onContinueBook(continueBook) }
                    )
                  }
                }
                if (visible.isEmpty()) {
                  item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                    MessageState(text = stringResource(R.string.library_no_results))
                  }
                }
                items(visible, key = { it.id }) { book ->
                  TvBookCard(
                    book = book,
                    progress = shelf.progress[book.id]?.withPageCount(book.pageCount),
                    offline = remember(book.id, shelf) { isOffline(book) },
                    onClick = { onOpenBook(book) }
                  )
                }
              }
            }
          }
        }
      }

      if (showBookmarks) {
        Dialog(onDismissRequest = { showBookmarks = false }) {
          TvBookmarks(
            books = allBooks,
            shelf = shelf,
            onOpenBook = {
              showBookmarks = false
              onOpenBook(it)
            },
            onOpenBookmark = { book, page ->
              showBookmarks = false
              onOpenBookmark(book, page)
            }
          )
        }
      }
    }
  }
}

/** The search box, focused with the keyboard up as it appears; «search» on the keyboard goes to the results. */
@Composable
private fun TvSearchField(query: String, onQueryChange: (String) -> Unit) {
  val palette = LocalTasnimPalette.current
  val keyboard = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val focus = remember { FocusRequester() }
  val shape = RoundedCornerShape(18.dp)
  LaunchedEffect(Unit) {
    withFrameNanos { }
    runCatching { focus.requestFocus() }
    keyboard?.show()
  }
  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    textStyle = TextStyle(color = palette.ink, fontFamily = Vazirmatn, fontSize = 20.sp),
    cursorBrush = SolidColor(palette.accent),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = {
      keyboard?.hide()
      // the categories, then the results, are below the field
      focusManager.moveFocus(FocusDirection.Down)
    }),
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = TvSafeSide, end = TvSafeSide, top = 16.dp)
      .focusRequester(focus),
    decorationBox = { field ->
      Row(
        modifier = Modifier
          .heightIn(min = 60.dp)
          .background(palette.surface, shape)
          .border(2.dp, palette.gold, shape)
          .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Icon(
          imageVector = LibraryIcons.Search,
          contentDescription = null,
          tint = palette.muted,
          modifier = Modifier.size(24.dp)
        )
        Box(Modifier.weight(1f)) {
          if (query.isEmpty()) {
            Text(
              text = stringResource(R.string.library_search_hint),
              color = palette.muted,
              fontFamily = Vazirmatn,
              fontSize = 20.sp,
              maxLines = 1
            )
          }
          field()
        }
      }
    }
  )
}

@Composable
private fun TvBookCard(
  book: LibraryBook,
  progress: ReadingProgress?,
  offline: Boolean,
  onClick: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // only the cover is framed, so the frame doesn't run over the title under it
    BookCover(
      book = book,
      titleSize = 17.sp,
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.7f)
        .tvClickable(RoundedCornerShape(10.dp), onClick)
    )
    progress?.fraction?.let { ReadingBar(fraction = it, height = 4.dp, modifier = Modifier.padding(top = 4.dp)) }
    Text(
      text = book.bookTitle,
      color = palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 18.sp,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 26.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 6.dp)
    )
    book.byline?.let {
      Text(
        text = it,
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      book.pageCount?.let {
        Text(text = pagesLabel(it), color = palette.muted, fontFamily = Vazirmatn, fontSize = 15.sp)
      }
      Spacer(Modifier.weight(1f))
      if (offline) {
        Icon(
          imageVector = LibraryIcons.Done,
          contentDescription = stringResource(R.string.library_available_offline),
          tint = palette.accent,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

/** The book the reader left last, as on the phone, sized for the TV. */
@Composable
private fun TvContinueCard(book: LibraryBook, progress: ReadingProgress, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  val shape = RoundedCornerShape(24.dp)
  val page = (progress.page + 1).persian()
  val pageLabel = if (progress.pageCount > 0) {
    stringResource(R.string.library_page_of, page, progress.pageCount.persian())
  } else {
    stringResource(R.string.library_reader_page_description, page)
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .tvClickable(shape, onClick)
      .background(palette.primary, shape)
      .padding(18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    BookCover(book = book, titleSize = 9.sp, spine = 3.dp, modifier = Modifier.size(width = 70.dp, height = 98.dp))
    Column(Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.library_continue_reading),
        color = palette.onPrimaryMuted,
        fontFamily = Vazirmatn,
        fontSize = 16.sp
      )
      Text(
        text = book.bookTitle,
        color = palette.onPrimary,
        fontFamily = Vazirmatn,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(Modifier.height(10.dp))
      progress.fraction?.let { fraction ->
        ReadingBar(fraction = fraction, track = palette.onPrimary.copy(alpha = 0.2f), fill = palette.gold, height = 6.dp)
        Spacer(Modifier.height(6.dp))
      }
      Text(text = pageLabel, color = palette.onPrimaryMuted, fontFamily = Vazirmatn, fontSize = 16.sp)
    }
    Box(
      modifier = Modifier
        .size(56.dp)
        .background(palette.onPrimary, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = LibraryIcons.Play, contentDescription = null, tint = palette.primary, modifier = Modifier.size(24.dp))
    }
  }
}

/** Saved books and bookmarked pages, as the phone's sheet, in a dialog the remote can move through. */
@Composable
private fun TvBookmarks(
  books: List<LibraryBook>,
  shelf: LibraryShelf,
  onOpenBook: (LibraryBook) -> Unit,
  onOpenBookmark: (LibraryBook, Int) -> Unit
) {
  val palette = LocalTasnimPalette.current
  val saved = remember(books, shelf.savedBooks) { books.filter { it.id in shelf.savedBooks } }
  val marked = remember(books, shelf.bookmarks) {
    books.flatMap { book -> shelf.bookmarks[book.id].orEmpty().map { book to it } }
  }
  val first = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    withFrameNanos { }
    runCatching { first.requestFocus() }
  }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    LazyColumn(
      contentPadding = PaddingValues(28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier
        .width(640.dp)
        .heightIn(max = 460.dp)
        .background(palette.background, RoundedCornerShape(28.dp))
    ) {
      item {
        Text(
          text = stringResource(R.string.library_bookmarks),
          color = palette.ink,
          fontFamily = NotoKufiArabic,
          fontWeight = FontWeight.Bold,
          fontSize = 24.sp,
          // focusable so the dialog always has a focus, even with nothing saved
          modifier = Modifier
            .semantics { heading() }
            .then(if (saved.isEmpty() && marked.isEmpty()) Modifier.focusRequester(first).tvClickable(RoundedCornerShape(8.dp)) {} else Modifier)
        )
      }
      if (saved.isEmpty() && marked.isEmpty()) {
        item {
          Text(
            text = stringResource(R.string.library_bookmarks_empty),
            color = palette.muted,
            fontFamily = Vazirmatn,
            fontSize = 18.sp,
            lineHeight = 28.sp
          )
        }
      }
      if (saved.isNotEmpty()) {
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(6.dp)) {
            items(saved, key = { it.id }) { book ->
              BookCover(
                book,
                titleSize = 11.sp,
                spine = 4.dp,
                modifier = Modifier
                  .size(100.dp, 140.dp)
                  .then(if (book == saved.first()) Modifier.focusRequester(first) else Modifier)
                  .tvClickable(RoundedCornerShape(8.dp)) { onOpenBook(book) }
              )
            }
          }
        }
      }
      items(marked, key = { (book, page) -> "${book.id}:$page" }) { (book, page) ->
        val isFirst = saved.isEmpty() && marked.first() == (book to page)
        TvBookmarkRow(
          book = book,
          page = page,
          onClick = { onOpenBookmark(book, page) },
          modifier = if (isFirst) Modifier.focusRequester(first) else Modifier
        )
      }
    }
  }
}

/** A bookmarked page, as the phone's row, with the TV frame. */
@Composable
private fun TvBookmarkRow(book: LibraryBook, page: Int, onClick: () -> Unit, modifier: Modifier) {
  val palette = LocalTasnimPalette.current
  val shape = RoundedCornerShape(16.dp)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .tvClickable(shape, onClick)
      .background(palette.surface, shape)
      .border(1.dp, palette.border, shape)
      .padding(horizontal = 18.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Icon(imageVector = LibraryIcons.BookmarkFilled, contentDescription = null, tint = palette.gold, modifier = Modifier.size(24.dp))
    Column(Modifier.weight(1f)) {
      Text(
        text = book.bookTitle,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = stringResource(R.string.library_reader_page_description, (page + 1).persian()),
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 16.sp
      )
    }
  }
}
