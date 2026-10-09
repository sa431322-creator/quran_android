package com.quran.mobile.feature.library.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.font.FontWeight
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
import com.quran.mobile.feature.library.ui.LibraryIcons

/** A bundled book: one page per screen, turned right to left like a Persian book. */
@Composable
fun LibraryReaderScreen(
  title: String,
  state: ReaderState,
  onPageShown: (Int) -> Unit,
  onBack: () -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      ) {
        when (state) {
          ReaderState.Loading -> {
            ReaderHeader(title = title, pageLabel = null, onBack = onBack)
            Box(
              Modifier
                .weight(1f)
                .fillMaxWidth(),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp)
            }
          }
          ReaderState.Error -> {
            ReaderHeader(title = title, pageLabel = null, onBack = onBack)
            Box(
              Modifier
                .weight(1f)
                .fillMaxWidth()
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
          is ReaderState.Ready -> Pages(title, state, onPageShown, onBack)
        }
      }
    }
  }
}

@Composable
private fun ColumnScope.Pages(
  title: String,
  state: ReaderState.Ready,
  onPageShown: (Int) -> Unit,
  onBack: () -> Unit
) {
  val pagerState = rememberPagerState(initialPage = state.startPage) { state.pages.pageCount }
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }.collect(onPageShown)
  }

  val pageLabel = stringResource(
    R.string.library_reader_page,
    (pagerState.currentPage + 1).toString().toPersianDigits(),
    state.pages.pageCount.toString().toPersianDigits()
  )
  ReaderHeader(title = title, pageLabel = pageLabel, onBack = onBack)

  BoxWithConstraints(
    Modifier
      .weight(1f)
      .fillMaxWidth()
  ) {
    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
    // in a right-to-left layout the next page comes in from the left, as in a Persian book
    HorizontalPager(
      state = pagerState,
      beyondViewportPageCount = 1,
      key = { it },
      modifier = Modifier.fillMaxSize()
    ) { index ->
      Page(pages = state.pages, index = index, widthPx = widthPx)
    }
  }
}

@Composable
private fun Page(pages: PdfPages, index: Int, widthPx: Int) {
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
      // a zoomed page stays under the header
      .clipToBounds()
      .onSizeChanged { size = it }
      .pointerInput(Unit) {
        detectTapGestures(
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
private fun ReaderHeader(title: String, pageLabel: String?, onBack: () -> Unit) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
      Icon(
        imageVector = LibraryIcons.Back,
        contentDescription = stringResource(R.string.library_back),
        tint = palette.ink
      )
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
    if (pageLabel != null) {
      Text(text = pageLabel, color = palette.muted, fontFamily = Vazirmatn, fontSize = 13.sp)
    }
  }
}
