package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryCategory
import com.quran.mobile.feature.library.presenter.BooksState
import com.quran.mobile.feature.library.presenter.LibraryPresenter
import com.quran.mobile.feature.library.presenter.LibraryUiState

/** The «کتابخانه» screen: the four categories and the published books of the selected one. */
@Composable
fun LibraryScreen(
  presenter: LibraryPresenter,
  onOpenBook: (LibraryBook) -> Unit,
  onBack: () -> Unit
) {
  val state by presenter.state.collectAsState()
  LibraryScreen(
    state = state,
    onSelectCategory = presenter::selectCategory,
    onRetry = presenter::retry,
    onOpenBook = onOpenBook,
    onBack = onBack
  )
}

@Composable
fun LibraryScreen(
  state: LibraryUiState,
  onSelectCategory: (LibraryCategory) -> Unit,
  onRetry: () -> Unit,
  onOpenBook: (LibraryBook) -> Unit,
  onBack: () -> Unit
) {
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
        LibraryHeader(onBack = onBack)
        CategoryChips(
          categories = LibraryCategory.entries,
          selected = state.selectedCategory,
          onSelect = onSelectCategory
        )
        Box(
          Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          when (val books = state.books) {
            BooksState.Loading -> LoadingState()
            BooksState.Empty -> MessageState(text = stringResource(R.string.library_empty))
            BooksState.Error -> MessageState(
              text = stringResource(R.string.library_error),
              action = stringResource(R.string.library_retry),
              onAction = onRetry
            )
            is BooksState.Loaded -> BookList(books = books.books, onOpenBook = onOpenBook)
          }
        }
      }
    }
  }
}

@Composable
private fun LibraryHeader(onBack: () -> Unit) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
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
      modifier = Modifier.semantics { heading() }
    )
  }
}

@Composable
private fun CategoryChips(
  categories: List<LibraryCategory>,
  selected: LibraryCategory,
  onSelect: (LibraryCategory) -> Unit
) {
  val palette = LocalTasnimPalette.current
  val description = stringResource(R.string.library_categories)
  // wraps instead of scrolling so all four categories are always in view
  FlowRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .semantics { contentDescription = description }
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    categories.forEach { category ->
      val isSelected = category == selected
      val shape = RoundedCornerShape(20.dp)
      Text(
        text = stringResource(category.titleRes),
        color = if (isSelected) palette.onSelectedChip else palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
          .heightIn(min = 40.dp)
          .clip(shape)
          .background(if (isSelected) palette.selectedChip else palette.surface)
          .border(1.dp, if (isSelected) palette.selectedChip else palette.border, shape)
          .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(category) })
          .padding(horizontal = 16.dp, vertical = 9.dp)
      )
    }
  }
}

@Composable
private fun BookList(books: List<LibraryBook>, onOpenBook: (LibraryBook) -> Unit) {
  LazyColumn(
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(books, key = { it.id }) { book ->
      BookRow(book = book, onClick = { onOpenBook(book) })
    }
  }
}

@Composable
private fun BookRow(book: LibraryBook, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  val shape = RoundedCornerShape(16.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 72.dp)
      .clip(shape)
      .background(palette.surface)
      .border(1.dp, palette.border, shape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .background(palette.background, RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = LibraryIcons.Book,
        contentDescription = null,
        tint = palette.accent,
        modifier = Modifier.size(24.dp)
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        text = book.bookTitle,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
      )
      if (book.description.isNotEmpty()) {
        Text(
          text = book.description,
          color = palette.muted,
          fontFamily = Vazirmatn,
          fontSize = 13.sp,
          lineHeight = 20.sp,
          // a long description is cut here; the book itself has the rest
          maxLines = 3,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
    Icon(
      imageVector = LibraryIcons.Open,
      contentDescription = null,
      tint = palette.muted,
      modifier = Modifier.size(20.dp)
    )
  }
}

@Composable
private fun LoadingState() {
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
private fun MessageState(text: String, action: String? = null, onAction: () -> Unit = {}) {
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
