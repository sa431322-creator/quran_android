package com.quran.mobile.feature.articles.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.articles.R
import com.quran.mobile.feature.articles.data.ArticleCard

/** What the list shows. */
sealed interface ArticlesState {
  data object Loading : ArticlesState
  data object Error : ArticlesState
  data class Loaded(
    val items: List<ArticleCard>,
    val next: String?,
    val offline: Boolean,
    val loadingMore: Boolean = false
  ) : ArticlesState
}

@Composable
fun ArticlesScreen(
  state: ArticlesState,
  loadCover: suspend (String) -> Bitmap?,
  onOpen: (ArticleCard) -> Unit,
  onMore: () -> Unit,
  onRetry: () -> Unit,
  onBack: () -> Unit
) {
  // the articles are Persian, so lay them out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      ) {
        Header(title = stringResource(R.string.articles_title), onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
          when (state) {
            ArticlesState.Loading -> CircularProgressIndicator(
              color = palette.accent,
              modifier = Modifier.align(Alignment.Center)
            )
            ArticlesState.Error -> Message(
              text = stringResource(R.string.articles_error),
              action = stringResource(R.string.articles_retry),
              onAction = onRetry
            )
            is ArticlesState.Loaded ->
              if (state.items.isEmpty()) {
                Message(text = stringResource(R.string.articles_empty))
              } else {
                ArticleList(state, loadCover, onOpen, onMore)
              }
          }
        }
      }
    }
  }
}

@Composable
internal fun Header(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
      Icon(
        imageVector = ArticleIcons.Back,
        contentDescription = stringResource(R.string.articles_back),
        tint = palette.ink
      )
    }
    Text(
      text = title,
      fontFamily = NotoKufiArabic,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      color = palette.ink,
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
private fun ArticleList(
  state: ArticlesState.Loaded,
  loadCover: suspend (String) -> Bitmap?,
  onOpen: (ArticleCard) -> Unit,
  onMore: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  LazyColumn(
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (state.offline) {
      item {
        Text(
          text = stringResource(R.string.articles_offline),
          fontFamily = Vazirmatn,
          fontSize = 13.sp,
          color = palette.muted,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
    items(state.items, key = { it.id }) { article ->
      Card(article, loadCover, onClick = { onOpen(article) })
    }
    if (state.next != null) {
      item {
        if (state.loadingMore) {
          CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp))
        } else {
          TextButton(onClick = onMore, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
              text = stringResource(R.string.articles_more),
              fontFamily = Vazirmatn,
              fontWeight = FontWeight.Bold,
              color = palette.accent
            )
          }
        }
      }
    }
  }
}

@Composable
private fun Card(article: ArticleCard, loadCover: suspend (String) -> Bitmap?, onClick: () -> Unit) {
  val palette = LocalTasnimPalette.current
  val by = stringResource(R.string.articles_by)
  val shape = RoundedCornerShape(18.dp)
  Column(
    modifier = Modifier
      // a readable column on tablets
      .widthIn(max = 640.dp)
      .fillMaxWidth()
      .clip(shape)
      .background(palette.surface)
      .border(1.dp, palette.border, shape)
      .clickable(onClick = onClick)
  ) {
    val url = article.thumbUrl ?: article.coverUrl
    if (url != null) {
      var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
      LaunchedEffect(url) { bitmap = loadCover(url) }
      Box(
        Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .background(palette.track)
      ) {
        bitmap?.let {
          Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = article.title,
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        color = palette.accent
      )
      if (article.summary.isNotBlank()) {
        Text(
          text = article.summary,
          fontFamily = Vazirmatn,
          fontSize = 14.sp,
          lineHeight = 24.sp,
          color = palette.muted,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis
        )
      }
      Text(
        text = ArticleDates.byline(article.author, article.publishedAt) { by.format(it) },
        fontFamily = Vazirmatn,
        fontSize = 12.sp,
        color = palette.muted
      )
    }
  }
}

@Composable
internal fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
  val palette = LocalTasnimPalette.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = ArticleIcons.Article,
      contentDescription = null,
      tint = palette.muted,
      modifier = Modifier.size(40.dp)
    )
    Text(
      text = text,
      fontFamily = Vazirmatn,
      fontSize = 15.sp,
      color = palette.muted,
      modifier = Modifier.padding(top = 12.dp)
    )
    if (action != null) {
      TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text = action, fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, color = palette.accent)
      }
    }
  }
}
