package com.quran.mobile.feature.tajweed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.mobile.feature.tajweed.R
import com.quran.mobile.feature.tajweed.data.TAJWEED_PAGE_COUNT
import com.quran.mobile.feature.tajweed.data.TajweedData
import com.quran.mobile.feature.tajweed.data.TajweedPage
import com.quran.mobile.feature.tajweed.data.TajweedRepository
import com.quran.mobile.feature.tajweed.data.ayahKey
import com.quran.mobile.feature.tajweed.data.indexToFollow
import com.quran.mobile.feature.tajweed.data.pageFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Lets the host turn the tajweed pages: to a page, to an ayah, or after the recitation. */
class TajweedPagesController {
  internal sealed interface Request {
    data class Page(val page: Int) : Request
    data class Ayah(val sura: Int, val ayah: Int) : Request
    data class Follow(val sura: Int, val ayah: Int) : Request
  }

  internal val requests = Channel<Request>(Channel.UNLIMITED)

  fun showPage(page: Int) {
    requests.trySend(Request.Page(page))
  }

  /** Turns to the page showing [sura]:[ayah]. */
  fun showAyah(sura: Int, ayah: Int) {
    requests.trySend(Request.Ayah(sura, ayah))
  }

  /** Turns to the recited ayah's page unless it is already showing, like the reader follows. */
  fun follow(sura: Int, ayah: Int) {
    requests.trySend(Request.Follow(sura, ayah))
  }
}

/**
 * The tajweed mushaf's pages, for a host that draws the reader's bars around them. Taps and long
 * presses go to the host like the reader's pages report them.
 */
@Composable
fun TajweedPages(
  repository: TajweedRepository,
  basmalaFont: FontFamily,
  style: TajweedPageStyle,
  labels: (TajweedPage) -> TajweedPageLabels,
  suraHeaderName: (Int) -> String,
  initialPage: Int,
  controller: TajweedPagesController,
  selectedAyah: Int?,
  recitedAyah: Int?,
  highlights: Map<Int, Color>,
  onPageShown: (TajweedPage) -> Unit,
  onTap: () -> Unit,
  onAyahLongPress: (sura: Int, ayah: Int, bounds: TajweedAyahBounds) -> Unit,
  modifier: Modifier = Modifier,
  /** sura and ayah to open at instead of [initialPage], when opened from the mushaf reader */
  startAyah: Pair<Int, Int>? = null
) {
  var dataAttempt by remember { mutableIntStateOf(0) }
  // null while loading; a failure is shown with a retry instead of crashing the screen
  val data by produceState<Result<TajweedData>?>(null, dataAttempt) {
    value = null
    value = orNull { repository.data() }?.let { Result.success(it) }
      ?: Result.failure(IllegalStateException("tajweed pages unavailable"))
  }
  val pagerState = rememberPagerState(initialPage = (initialPage - 1).coerceIn(0, TAJWEED_PAGE_COUNT - 1)) {
    TAJWEED_PAGE_COUNT
  }
  val loadedPages = data?.getOrNull()?.pages
  val currentOnPageShown by rememberUpdatedState(onPageShown)

  // the reader's ayah is only known once the pages are loaded; jump to it once. The pager is not
  // laid out yet, so the jump is requested for its first layout rather than awaited here.
  var startHandled by rememberSaveable { mutableStateOf(startAyah == null) }
  var startIndex by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(loadedPages) {
    if (!startHandled && startAyah != null && loadedPages != null) {
      val index = loadedPages.pageFor(startAyah.first, startAyah.second) - 1
      startIndex = index
      pagerState.requestScrollToPage(index)
    }
  }
  LaunchedEffect(pagerState.currentPage, startIndex) {
    if (!startHandled && pagerState.currentPage == startIndex) startHandled = true
  }
  LaunchedEffect(loadedPages, pagerState.currentPage, startHandled) {
    if (!startHandled) return@LaunchedEffect
    loadedPages?.getOrNull(pagerState.currentPage)?.let { currentOnPageShown(it) }
    // fetch the neighbours so turning the page does not wait for the network
    val page = pagerState.currentPage + 1
    listOf(page + 1, page - 1)
      .filter { it in 1..TAJWEED_PAGE_COUNT }
      .forEach { launch { orNull { repository.font(it) } } }
  }

  LaunchedEffect(controller, loadedPages) {
    val pages = loadedPages ?: return@LaunchedEffect
    controller.requests.receiveAsFlow().collect { request ->
      when (request) {
        is TajweedPagesController.Request.Page ->
          pagerState.scrollToPage((request.page - 1).coerceIn(0, TAJWEED_PAGE_COUNT - 1))
        is TajweedPagesController.Request.Ayah ->
          pagerState.scrollToPage(pages.pageFor(request.sura, request.ayah) - 1)
        is TajweedPagesController.Request.Follow ->
          pages.indexToFollow(request.sura, request.ayah, pagerState.currentPage)
            ?.let { pagerState.animateScrollToPage(it) }
      }
    }
  }

  // the mushaf reads right to left whatever the device language is
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    val result = data
    val loaded = result?.getOrNull()
    when {
      result == null ->
        Box(modifier.fillMaxSize().background(style.paper), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      loaded == null -> PageMessage(
        stringResource(R.string.tajweed_data_error),
        style = style,
        action = stringResource(R.string.tajweed_retry),
        onAction = { dataAttempt++ },
        onTap = onTap
      )
      else -> HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1
      ) { index ->
        val page = loaded.pages[index]
        TajweedPageContent(
          page = page,
          repository = repository,
          basmalaFont = basmalaFont,
          style = style,
          labels = labels(page),
          suraHeaderName = suraHeaderName,
          selectedAyah = selectedAyah,
          recitedAyah = recitedAyah,
          highlights = highlights,
          onTap = onTap,
          onAyahLongPress = { key, bounds -> onAyahLongPress(key / 1000, key % 1000, bounds) }
        )
      }
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
  style: TajweedPageStyle,
  labels: TajweedPageLabels,
  suraHeaderName: (Int) -> String,
  selectedAyah: Int?,
  recitedAyah: Int?,
  highlights: Map<Int, Color>,
  onTap: () -> Unit,
  onAyahLongPress: (Int, TajweedAyahBounds) -> Unit
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
    is FontState.Ready -> TajweedPageView(
      page = page,
      pageFont = current.family,
      basmalaFont = basmalaFont,
      style = style,
      labels = labels,
      suraHeaderName = suraHeaderName,
      selectedAyah = selectedAyah,
      recitedAyah = recitedAyah,
      highlights = highlights,
      onTap = onTap,
      onAyahLongPress = onAyahLongPress
    )
    FontState.Loading -> PageMessage(
      stringResource(R.string.tajweed_loading),
      style = style,
      showProgress = true,
      onTap = onTap
    )
    FontState.Failed -> PageMessage(
      stringResource(R.string.tajweed_error),
      style = style,
      action = stringResource(R.string.tajweed_retry),
      onAction = { attempt++ },
      onTap = onTap
    )
  }
}

@Composable
private fun PageMessage(
  message: String,
  style: TajweedPageStyle,
  showProgress: Boolean = false,
  action: String? = null,
  onAction: () -> Unit = {},
  onTap: () -> Unit
) {
  Column(
    Modifier
      .fillMaxSize()
      .background(style.paper)
      .pointerInput(onTap) { detectTapGestures { onTap() } }
      .padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (showProgress) CircularProgressIndicator(color = style.suraFrameFill)
    Text(message, color = style.headerColor, fontSize = 14.sp)
    if (action != null) {
      TextButton(onClick = onAction) { Text(action, color = style.suraFrameFill) }
    }
  }
}

/** The [ayahKey] of [sura]:[ayah], for the hosts that keep ayat as keys. */
fun tajweedAyahKey(sura: Int, ayah: Int): Int = ayahKey(sura, ayah)
