package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.quran.mobile.feature.library.presenter.LibraryUiState

/** What this device knows about the books; the activity re-reads it whenever the screen returns. */
data class LibraryShelf(
  val progress: Map<String, ReadingProgress> = emptyMap(),
  val bookmarks: Map<String, List<Int>> = emptyMap(),
  val savedBooks: Set<String> = emptySet(),
  // changes on every refresh, so checks that read the disk (is a book downloaded?) run again
  val refreshedAt: Long = 0L
)

/**
 * The «کتابخانه» screen: search, the categories, the book the reader is in the middle of and
 * a shelf of covers.
 */
@Composable
fun LibraryScreen(
  presenter: LibraryPresenter,
  shelf: LibraryShelf,
  isOffline: (LibraryBook) -> Boolean,
  onOpenBook: (LibraryBook) -> Unit,
  onContinueBook: (LibraryBook) -> Unit,
  onOpenBookmark: (LibraryBook, Int) -> Unit,
  onBack: () -> Unit
) {
  val state by presenter.state.collectAsState()
  LibraryScreen(
    state = state,
    shelf = shelf,
    isOffline = isOffline,
    onSelectCategory = presenter::selectCategory,
    onSearch = presenter::search,
    onRetry = presenter::retry,
    onOpenBook = onOpenBook,
    onContinueBook = onContinueBook,
    onOpenBookmark = onOpenBookmark,
    onBack = onBack
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  state: LibraryUiState,
  shelf: LibraryShelf,
  isOffline: (LibraryBook) -> Boolean,
  onSelectCategory: (LibraryCategory?) -> Unit,
  onSearch: (String) -> Unit,
  onRetry: () -> Unit,
  onOpenBook: (LibraryBook) -> Unit,
  onContinueBook: (LibraryBook) -> Unit,
  onOpenBookmark: (LibraryBook, Int) -> Unit,
  onBack: () -> Unit
) {
  var showBookmarks by rememberSaveable { mutableStateOf(false) }
  val allBooks = (state.books as? BooksState.Loaded)?.books.orEmpty()

  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      ) {
        LibraryHeader(onBack = onBack, onShowBookmarks = { showBookmarks = true })
        SearchField(query = state.query, onQueryChange = onSearch)
        CategoryChips(
          selected = state.selectedCategory,
          counts = state.categoryCounts,
          total = state.totalCount,
          onSelect = onSelectCategory
        )
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
              onAction = onRetry
            )
            is BooksState.Loaded -> Shelf(
              state = state,
              allBooks = allBooks,
              shelf = shelf,
              isOffline = isOffline,
              onOpenBook = onOpenBook,
              onContinueBook = onContinueBook
            )
          }
        }
      }

      if (showBookmarks) {
        ModalBottomSheet(
          onDismissRequest = { showBookmarks = false },
          containerColor = palette.background
        ) {
          BookmarksSheet(
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

@Composable
private fun LibraryHeader(onBack: () -> Unit, onShowBookmarks: () -> Unit) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
      Icon(
        imageVector = LibraryIcons.Back,
        contentDescription = stringResource(R.string.library_back),
        tint = palette.ink
      )
    }
    Text(
      text = stringResource(R.string.library_title),
      fontFamily = NotoKufiArabic,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      color = palette.ink,
      modifier = Modifier
        .weight(1f)
        .semantics { heading() }
    )
    IconButton(onClick = onShowBookmarks, modifier = Modifier.size(44.dp)) {
      Icon(
        imageVector = LibraryIcons.Bookmarks,
        contentDescription = stringResource(R.string.library_bookmarks),
        tint = palette.accent
      )
    }
  }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  val palette = LocalTasnimPalette.current
  val keyboard = LocalSoftwareKeyboardController.current
  val hint = stringResource(R.string.library_search_hint)
  val shape = RoundedCornerShape(16.dp)
  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    textStyle = TextStyle(color = palette.ink, fontFamily = Vazirmatn, fontSize = 14.sp),
    cursorBrush = SolidColor(palette.accent),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 6.dp),
    decorationBox = { field ->
      Row(
        modifier = Modifier
          .heightIn(min = 48.dp)
          .clip(shape)
          .background(palette.surface)
          .border(1.dp, palette.border, shape)
          .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = LibraryIcons.Search,
          contentDescription = null,
          tint = palette.muted,
          modifier = Modifier.size(20.dp)
        )
        Box(Modifier.weight(1f)) {
          if (query.isEmpty()) {
            Text(text = hint, color = palette.muted, fontFamily = Vazirmatn, fontSize = 14.sp, maxLines = 1)
          }
          field()
        }
        if (query.isNotEmpty()) {
          IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
            Icon(
              imageVector = LibraryIcons.Close,
              contentDescription = stringResource(R.string.library_search_clear),
              tint = palette.muted,
              modifier = Modifier.size(18.dp)
            )
          }
        } else {
          Spacer(Modifier.width(10.dp))
        }
      }
    }
  )
}

/** «همه» and the four categories in one sliding row, each with how many books it holds. */
@Composable
private fun CategoryChips(
  selected: LibraryCategory?,
  counts: Map<LibraryCategory, Int>,
  total: Int,
  onSelect: (LibraryCategory?) -> Unit
) {
  val description = stringResource(R.string.library_categories)
  val options = listOf<LibraryCategory?>(null) + LibraryCategory.entries
  LazyRow(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = description }
      .selectableGroup()
  ) {
    items(options, key = { it?.id ?: "all" }) { category ->
      CategoryChip(
        label = stringResource(category?.titleRes ?: R.string.library_all),
        count = if (category == null) total else counts[category] ?: 0,
        selected = category == selected,
        onClick = { onSelect(category) }
      )
    }
  }
}

@Composable
private fun CategoryChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  val shape = RoundedCornerShape(20.dp)
  // the selected chip takes the teal of the live screens' switch
  Row(
    modifier = Modifier
      .heightIn(min = 40.dp)
      .clip(shape)
      .background(if (selected) palette.primary else palette.surface)
      .border(1.dp, if (selected) palette.primary else palette.border, shape)
      .selectable(selected = selected, role = Role.Tab, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = label,
      color = if (selected) palette.onPrimary else palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 14.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    )
    Text(
      text = formatPersianCount(count),
      color = if (selected) palette.onPrimaryMuted else palette.muted,
      fontFamily = Vazirmatn,
      fontSize = 12.sp
    )
  }
}

@Composable
private fun Shelf(
  state: LibraryUiState,
  allBooks: List<LibraryBook>,
  shelf: LibraryShelf,
  isOffline: (LibraryBook) -> Boolean,
  onOpenBook: (LibraryBook) -> Unit,
  onContinueBook: (LibraryBook) -> Unit
) {
  val visible = remember(state) { state.visibleBooks }
  // the most recently read book; hidden while searching so results come first
  val continueBook = remember(allBooks, shelf.progress) {
    allBooks
      .filter { it.id in shelf.progress }
      .maxByOrNull { shelf.progress.getValue(it.id).updatedAt }
  }
  val title = stringResource(state.selectedCategory?.titleRes ?: R.string.library_all_books)

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    if (continueBook != null && state.query.isBlank()) {
      item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
        ContinueCard(
          book = continueBook,
          progress = shelf.progress.getValue(continueBook.id).withPageCount(continueBook.pageCount),
          onClick = { onContinueBook(continueBook) }
        )
      }
    }
    item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
      SectionTitle(text = title, trailing = formatPersianCount(visible.size))
    }
    if (visible.isEmpty()) {
      item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
        Text(
          text = stringResource(R.string.library_no_results),
          color = LocalTasnimPalette.current.muted,
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
        )
      }
    }
    items(visible, key = { it.id }) { book ->
      BookCard(
        book = book,
        progress = shelf.progress[book.id]?.withPageCount(book.pageCount),
        offline = remember(book.id, shelf) { isOffline(book) },
        onClick = { onOpenBook(book) }
      )
    }
  }
}

/** The book the reader left last, in the radio card's teal with a gold progress bar. */
@Composable
private fun ContinueCard(book: LibraryBook, progress: ReadingProgress, onClick: () -> Unit) {
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
      .clip(shape)
      .background(palette.primary)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    BookCover(
      book = book,
      titleSize = 8.sp,
      spine = 3.dp,
      modifier = Modifier.size(width = 58.dp, height = 82.dp)
    )
    Column(Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.library_continue_reading),
        color = palette.onPrimaryMuted,
        fontFamily = Vazirmatn,
        fontSize = 12.sp
      )
      Text(
        text = book.bookTitle,
        color = palette.onPrimary,
        fontFamily = Vazirmatn,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(Modifier.height(10.dp))
      progress.fraction?.let { fraction ->
        ReadingBar(
          fraction = fraction,
          track = palette.onPrimary.copy(alpha = 0.2f),
          fill = palette.gold,
          height = 5.dp
        )
        Spacer(Modifier.height(6.dp))
      }
      Text(text = pageLabel, color = palette.onPrimaryMuted, fontFamily = Vazirmatn, fontSize = 12.sp)
    }
    Box(
      modifier = Modifier
        .size(46.dp)
        .background(palette.onPrimary, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = LibraryIcons.Play,
        contentDescription = null,
        tint = palette.primary,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

@Composable
private fun BookCard(
  book: LibraryBook,
  progress: ReadingProgress?,
  offline: Boolean,
  onClick: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(role = Role.Button, onClick = onClick),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    BookCover(
      book = book,
      titleSize = 15.sp,
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.7f)
    )
    progress?.fraction?.let { ReadingBar(fraction = it, height = 3.dp, modifier = Modifier.padding(top = 2.dp)) }
    Text(
      text = book.bookTitle,
      color = palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 21.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 4.dp)
    )
    book.byline?.let {
      Text(
        text = it,
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      book.pageCount?.let {
        Text(text = pagesLabel(it), color = palette.muted, fontFamily = Vazirmatn, fontSize = 12.sp)
      }
      Spacer(Modifier.weight(1f))
      if (offline) {
        Icon(
          imageVector = LibraryIcons.Done,
          contentDescription = stringResource(R.string.library_available_offline),
          tint = palette.accent,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}

/** Saved books and bookmarked pages of every book, from the header's bookmark button. */
@Composable
private fun BookmarksSheet(
  books: List<LibraryBook>,
  shelf: LibraryShelf,
  onOpenBook: (LibraryBook) -> Unit,
  onOpenBookmark: (LibraryBook, Int) -> Unit
) {
  val palette = LocalTasnimPalette.current
  val saved = remember(books, shelf.savedBooks) { books.filter { it.id in shelf.savedBooks } }
  val marked = remember(books, shelf.bookmarks) {
    books.mapNotNull { book -> shelf.bookmarks[book.id]?.let { book to it } }
  }

  LazyColumn(
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    item {
      Text(
        text = stringResource(R.string.library_bookmarks),
        color = palette.ink,
        fontFamily = NotoKufiArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.semantics { heading() }
      )
    }
    if (saved.isEmpty() && marked.isEmpty()) {
      item {
        Text(
          text = stringResource(R.string.library_bookmarks_empty),
          color = palette.muted,
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          lineHeight = 22.sp,
          modifier = Modifier.padding(vertical = 16.dp)
        )
      }
    }
    if (saved.isNotEmpty()) {
      item { SectionTitle(stringResource(R.string.library_saved_books), Modifier.padding(top = 6.dp)) }
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(saved, key = { it.id }) { book ->
            Column(
              modifier = Modifier
                .width(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = { onOpenBook(book) }),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              BookCover(book, titleSize = 10.sp, spine = 4.dp, modifier = Modifier.size(84.dp, 118.dp))
              Text(
                text = book.bookTitle,
                color = palette.ink,
                fontFamily = Vazirmatn,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
    if (marked.isNotEmpty()) {
      item { SectionTitle(stringResource(R.string.library_bookmarked_pages), Modifier.padding(top = 6.dp)) }
      marked.forEach { (book, pages) ->
        items(pages, key = { "${book.id}:$it" }) { page ->
          BookmarkRow(book = book, page = page, onClick = { onOpenBookmark(book, page) })
        }
      }
    }
  }
}

@Composable
internal fun BookmarkRow(book: LibraryBook, page: Int, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(palette.surface)
      .border(1.dp, palette.border, shape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(
      imageVector = LibraryIcons.BookmarkFilled,
      contentDescription = null,
      tint = palette.gold,
      modifier = Modifier.size(20.dp)
    )
    Column(Modifier.weight(1f)) {
      Text(
        text = book.bookTitle,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = stringResource(R.string.library_reader_page_description, (page + 1).persian()),
        color = palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 12.sp
      )
    }
    Icon(
      imageVector = LibraryIcons.Open,
      contentDescription = null,
      tint = palette.muted,
      modifier = Modifier.size(18.dp)
    )
  }
}

@Composable
internal fun LoadingState() {
  val palette = LocalTasnimPalette.current
  val description = stringResource(R.string.library_loading)
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(
      color = palette.accent,
      strokeWidth = 3.dp,
      modifier = Modifier
        .size(36.dp)
        .semantics { contentDescription = description }
    )
  }
}

@Composable
internal fun MessageState(text: String, action: String? = null, onAction: () -> Unit = {}) {
  val palette = LocalTasnimPalette.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = text,
      color = palette.muted,
      fontFamily = Vazirmatn,
      fontSize = 15.sp,
      textAlign = TextAlign.Center
    )
    if (action != null) {
      Text(
        text = action,
        color = palette.onPrimary,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
          .padding(top = 16.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(palette.primary)
          .clickable(role = Role.Button, onClick = onAction)
          .padding(horizontal = 20.dp, vertical = 10.dp)
      )
    }
  }
}
