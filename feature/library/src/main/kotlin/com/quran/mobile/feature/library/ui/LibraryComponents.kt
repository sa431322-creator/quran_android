package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.formatPersianCount
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.LibraryBook
import com.quran.mobile.feature.library.data.LibraryCategory
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Cover colors, one per category so a book's shelf shows at a glance. They come from the
 * Tasnim palette (the teal of the live and radio screens, its gold-brown, deep water and ink)
 * and stay the same in dark mode, like printed covers.
 */
internal fun coverColor(category: LibraryCategory): Color = when (category) {
  LibraryCategory.QURAN_SCIENCES -> Color(0xFF1F4E56)
  LibraryCategory.QURAN_TRANSLATION -> Color(0xFF7E5A1C)
  LibraryCategory.TAJWEED -> Color(0xFF2C6577)
  LibraryCategory.TAFSIR -> Color(0xFF2A2620)
}

/** A drawn cover: the category's color, a darker spine on the right and a gold frame. */
@Composable
internal fun BookCover(
  book: LibraryBook,
  modifier: Modifier = Modifier,
  titleSize: TextUnit = 14.sp,
  spine: Dp = 6.dp
) {
  val palette = LocalTasnimPalette.current
  val shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)
  Row(
    modifier = modifier
      .clip(shape)
      .background(coverColor(book.category))
      // decoration only; the title is read out by the card around it
      .clearAndSetSemantics { }
  ) {
    // in a right-to-left row the first child sits on the right, where a Persian book's spine is
    Box(
      Modifier
        .width(spine)
        .fillMaxHeight()
        .background(Color.Black.copy(alpha = 0.22f))
    )
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(6.dp)
        .border(1.dp, palette.gold.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
        .padding(3.dp)
        .border(0.5.dp, palette.gold.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        .padding(horizontal = 6.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = book.bookTitle,
        color = palette.onPrimary,
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = titleSize,
        lineHeight = titleSize * 1.45f,
        textAlign = TextAlign.Center,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/** A thin rounded progress bar; [fraction] is 0 to 1 and fills from the start (right). */
@Composable
internal fun ReadingBar(
  fraction: Float,
  modifier: Modifier = Modifier,
  track: Color = LocalTasnimPalette.current.track,
  fill: Color = LocalTasnimPalette.current.accent,
  height: Dp = 4.dp
) {
  Box(
    modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(height))
      .background(track)
  ) {
    Box(
      Modifier
        .fillMaxWidth(fraction.coerceIn(0f, 1f))
        .fillMaxHeight()
        .background(fill, RoundedCornerShape(height))
    )
  }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = text,
      color = palette.ink,
      fontFamily = Vazirmatn,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier
        .weight(1f)
        .semantics { heading() }
    )
    if (trailing != null) {
      Text(text = trailing, color = palette.muted, fontFamily = Vazirmatn, fontSize = 13.sp)
    }
  }
}

/** Who wrote or translated the book, for one line under its title; null when unknown. */
internal val LibraryBook.byline: String?
  get() = author ?: translator

internal fun Int.persian(): String = toString().toPersianDigits()

@Composable
@ReadOnlyComposable
internal fun pagesLabel(pages: Int): String = stringResource(R.string.library_pages, formatPersianCount(pages))

/** About two minutes a page, rounded to minutes under an hour and to hours above. */
@Composable
@ReadOnlyComposable
internal fun readingTimeLabel(pages: Int): String {
  val minutes = pages * MINUTES_PER_PAGE
  return if (minutes < 60) {
    stringResource(R.string.library_minutes, minutes.persian())
  } else {
    stringResource(R.string.library_hours, (minutes / 60f).roundToInt().persian())
  }
}

@Composable
@ReadOnlyComposable
internal fun fileSizeLabel(bytes: Long): String {
  val megabytes = bytes / (1024f * 1024f)
  return if (megabytes >= 1f) {
    val value = String.format(Locale.US, "%.1f", megabytes).removeSuffix(".0")
    stringResource(R.string.library_megabytes, value.toPersianDigits().replace('.', '٫'))
  } else {
    stringResource(R.string.library_kilobytes, (bytes / 1024f).roundToInt().coerceAtLeast(1).persian())
  }
}

private const val MINUTES_PER_PAGE = 2
