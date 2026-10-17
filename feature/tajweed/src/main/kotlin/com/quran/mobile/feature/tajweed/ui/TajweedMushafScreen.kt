package com.quran.mobile.feature.tajweed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.tajweed.R
import com.quran.mobile.feature.tajweed.data.TAJWEED_PAGE_COUNT
import com.quran.mobile.feature.tajweed.data.TajweedData
import com.quran.mobile.feature.tajweed.data.TajweedPage
import com.quran.mobile.feature.tajweed.data.TajweedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.quran.mobile.common.ui.core.R as coreR

@Composable
fun TajweedMushafScreen(
  repository: TajweedRepository,
  basmalaFont: FontFamily,
  initialPage: Int,
  onPageChanged: (Int) -> Unit,
  onBack: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  var dataAttempt by remember { mutableIntStateOf(0) }
  // null while loading; a failure is shown with a retry instead of crashing the screen
  val data by produceState<Result<TajweedData>?>(null, dataAttempt) {
    value = null
    value = orNull { repository.data() }?.let { Result.success(it) }
      ?: Result.failure(IllegalStateException("tajweed pages unavailable"))
  }
  val suraNames = stringArrayResource(coreR.array.sura_names).toList()
  val pagerState = rememberPagerState(initialPage = (initialPage - 1).coerceIn(0, TAJWEED_PAGE_COUNT - 1)) {
    TAJWEED_PAGE_COUNT
  }
  val scope = rememberCoroutineScope()
  var showSuras by remember { mutableStateOf(false) }

  LaunchedEffect(pagerState.currentPage) {
    val page = pagerState.currentPage + 1
    onPageChanged(page)
    // fetch the neighbours so turning the page does not wait for the network
    listOf(page + 1, page - 1)
      .filter { it in 1..TAJWEED_PAGE_COUNT }
      .forEach { launch { orNull { repository.font(it) } } }
  }

  // the mushaf reads right to left whatever the device language is
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(
      Modifier
        .fillMaxSize()
        .background(palette.background)
        .systemBarsPadding()
    ) {
      val current = data?.getOrNull()?.pages?.getOrNull(pagerState.currentPage)
      Header(
        subtitle = current?.let {
          suraNames.getOrElse(it.sura - 1) { "" } + " · " + stringResource(
            R.string.tajweed_page_juz,
            it.page.toString().toPersianDigits(),
            it.juz.toString().toPersianDigits()
          )
        }.orEmpty(),
        onBack = onBack,
        onSuras = { showSuras = true }
      )

      val result = data
      val loaded = result?.getOrNull()
      when {
        result == null ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        loaded == null -> PageMessage(
          stringResource(R.string.tajweed_data_error),
          action = stringResource(R.string.tajweed_retry),
          onAction = { dataAttempt++ }
        )
        else -> HorizontalPager(
          state = pagerState,
          modifier = Modifier.fillMaxSize(),
          beyondViewportPageCount = 1
        ) { index ->
          TajweedPageContent(loaded.pages[index], repository, basmalaFont, suraNames)
        }
      }
    }

    val loaded = data?.getOrNull()
    if (showSuras && loaded != null) {
      SuraListDialog(
        suraNames = suraNames,
        onSura = { sura ->
          showSuras = false
          loaded.suraHeaderPages[sura]?.let { page -> scope.launch { pagerState.scrollToPage(page - 1) } }
        },
        onDismiss = { showSuras = false }
      )
    }
  }
}

/** Runs [block], returning null when it fails; cancellation still propagates. */
private suspend fun <T : Any> orNull(block: suspend () -> T): T? =
  try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }

private sealed interface FontState {
  data object Loading : FontState
  data class Ready(val family: FontFamily) : FontState
  data object Failed : FontState
}

@Composable
private fun TajweedPageContent(
  page: TajweedPage,
  repository: TajweedRepository,
  basmalaFont: FontFamily,
  suraNames: List<String>
) {
  var attempt by remember { mutableIntStateOf(0) }
  val cached = remember(page.page) {
    repository.cachedFont(page.page)?.let { FontState.Ready(FontFamily(Font(it))) }
  }
  val state by produceState<FontState>(cached ?: FontState.Loading, page.page, attempt) {
    if (value !is FontState.Ready) {
      value = FontState.Loading
      value = orNull { repository.font(page.page) }
        ?.let { FontState.Ready(FontFamily(Font(it))) }
        ?: FontState.Failed
    }
  }

  when (val current = state) {
    is FontState.Ready -> TajweedPageView(page, current.family, basmalaFont, suraNames)
    FontState.Loading -> PageMessage(stringResource(R.string.tajweed_loading), showProgress = true)
    FontState.Failed -> PageMessage(
      stringResource(R.string.tajweed_error),
      action = stringResource(R.string.tajweed_retry),
      onAction = { attempt++ }
    )
  }
}

@Composable
private fun PageMessage(
  message: String,
  showProgress: Boolean = false,
  action: String? = null,
  onAction: () -> Unit = {}
) {
  val palette = LocalTasnimPalette.current
  Column(
    Modifier
      .fillMaxSize()
      .background(TajweedPaper)
      .padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (showProgress) CircularProgressIndicator(color = palette.primary)
    Text(message, color = palette.muted, fontSize = 14.sp)
    if (action != null) {
      TextButton(onClick = onAction) { Text(action, color = palette.primary, fontWeight = FontWeight.Bold) }
    }
  }
}

@Composable
private fun Header(subtitle: String, onBack: () -> Unit, onSuras: () -> Unit) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
      Icon(TajweedIcons.Back, contentDescription = stringResource(R.string.tajweed_back), tint = palette.ink)
    }
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.tajweed_title),
          fontFamily = NotoKufiArabic,
          fontWeight = FontWeight.Bold,
          fontSize = 20.sp,
          color = palette.ink,
          modifier = Modifier.semantics { heading() }
        )
        Text(
          text = stringResource(R.string.tajweed_experimental),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = palette.chipText,
          modifier = Modifier
            .background(palette.track, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        )
      }
      Text(text = subtitle, fontSize = 13.sp, color = palette.muted, maxLines = 1)
    }
    IconButton(onClick = onSuras, modifier = Modifier.size(44.dp)) {
      Icon(TajweedIcons.List, contentDescription = stringResource(R.string.tajweed_suras), tint = palette.ink)
    }
  }
}

@Composable
private fun SuraListDialog(suraNames: List<String>, onSura: (Int) -> Unit, onDismiss: () -> Unit) {
  val palette = LocalTasnimPalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.tajweed_suras), fontFamily = NotoKufiArabic) },
    text = {
      LazyColumn(Modifier.heightIn(max = 420.dp)) {
        itemsIndexed(suraNames) { index, name ->
          Text(
            text = "${(index + 1).toString().toPersianDigits()}. $name",
            color = palette.ink,
            fontSize = 16.sp,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSura(index + 1) }
              .padding(vertical = 10.dp, horizontal = 4.dp)
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.tajweed_close)) }
    }
  )
}
