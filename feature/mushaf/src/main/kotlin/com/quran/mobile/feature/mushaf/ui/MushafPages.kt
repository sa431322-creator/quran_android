package com.quran.mobile.feature.mushaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.mobile.feature.mushaf.R
import com.quran.mobile.feature.mushaf.data.MushafPage
import com.quran.mobile.feature.mushaf.data.MushafRepository
import kotlinx.coroutines.CancellationException

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

/**
 * One page with its font: loads the page's font (downloading it the first time), with a message
 * while it loads or when it cannot, then draws the page.
 */
@Composable
fun MushafFontPage(
  page: MushafPage,
  repository: MushafRepository,
  basmalaFont: FontFamily,
  style: MushafPageStyle,
  labels: MushafPageLabels,
  suraHeaderName: (Int) -> String,
  selectedAyah: Int?,
  recitedAyah: Int?,
  highlights: Map<Int, Color>,
  onTap: () -> Unit,
  onAyahLongPress: (Int, MushafAyahBounds) -> Unit = { _, _ -> },
  hits: MushafPageHits? = null,
  onGesture: ((MushafGesture, Offset) -> Unit)? = null
) {
  // the loading and error messages have no words: a tap there is a plain tap
  val messageTap: () -> Unit = { onGesture?.invoke(MushafGesture.TAP, Offset.Unspecified) ?: onTap() }
  var attempt by remember { mutableIntStateOf(0) }
  val cached = remember(page.page, repository) {
    repository.cachedFont(page.page)?.let { FontState.Ready(FontFamily(Font(it))) }
  }
  val state by produceState<FontState>(cached ?: FontState.Loading, page.page, repository, attempt) {
    // a new page or edition starts again: the value kept from the last one is another font
    value = cached ?: FontState.Loading
    if (value !is FontState.Ready) {
      value = orNull { repository.font(page.page) }
        ?.let { FontState.Ready(FontFamily(Font(it))) }
        ?: FontState.Failed
    }
  }

  when (val current = state) {
    is FontState.Ready -> MushafPageView(
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
      onAyahLongPress = onAyahLongPress,
      hits = hits,
      onGesture = onGesture
    )
    FontState.Loading -> PageMessage(
      stringResource(R.string.mushaf_loading),
      style = style,
      showProgress = true,
      onTap = messageTap
    )
    FontState.Failed -> PageMessage(
      stringResource(R.string.mushaf_error),
      style = style,
      action = stringResource(R.string.mushaf_retry),
      onAction = { attempt++ },
      onTap = messageTap
    )
  }
}

@Composable
private fun PageMessage(
  message: String,
  style: MushafPageStyle,
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
    if (showProgress) CircularProgressIndicator(color = style.accent)
    Text(message, color = style.headerColor, fontSize = 14.sp)
    if (action != null) {
      TextButton(onClick = onAction) { Text(action, color = style.accent) }
    }
  }
}
