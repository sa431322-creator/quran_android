package com.quran.mobile.feature.tajweed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.tajweed.R
import com.quran.mobile.feature.tajweed.TajweedPlayOptions
import com.quran.mobile.feature.tajweed.TajweedPlayResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** What a tapped ayah offers in the tajweed mushaf, backed by the app's own features. */
data class TajweedAyahActions(
  /** the Persian translation and tafsir of an ayah, or null when it is not available */
  val tafsir: suspend (sura: Int, ayah: Int) -> String?,
  /** starts the recitation, or the download of its audio first */
  val play: (sura: Int, ayah: Int, allowMobileDownload: Boolean, options: TajweedPlayOptions) -> TajweedPlayResult,
  val stop: () -> Unit,
  /** opens the reader's bookmark sheet: collections, highlight colors and reading bookmarks */
  val openBookmarks: (sura: Int, ayah: Int) -> Unit = { _, _ -> },
  /** copies the ayah's text; false when the Arabic text is not downloaded yet */
  val copy: (sura: Int, ayah: Int) -> Boolean = { _, _ -> false },
  /** shares the ayah's text; false when the Arabic text is not downloaded yet */
  val shareText: (sura: Int, ayah: Int) -> Boolean = { _, _ -> false },
  val shareLink: (sura: Int, ayah: Int) -> Unit = { _, _ -> },
  /** each highlighted ayah's [com.quran.mobile.feature.tajweed.data.ayahKey] and color */
  val highlights: Flow<Map<Int, HighlightColor>> = flowOf(emptyMap())
)

private sealed interface TafsirState {
  data object Loading : TafsirState
  data class Loaded(val text: String?) : TafsirState
}

/** Same choices as the reader's playback settings: 1–25 plays, or until stopped (-1). */
private val VERSE_REPEATS = listOf(0, 1, 2, 4, -1)
private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

/**
 * The tapped ayah: bookmarks and highlights, copying and sharing, recitation from it with the
 * reader's playback options, and its Persian translation and tafsir.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AyahSheet(
  sura: Int,
  ayah: Int,
  suraNames: List<String>,
  actions: TajweedAyahActions,
  options: TajweedPlayOptions,
  onOptionsChange: (TajweedPlayOptions) -> Unit,
  onDismiss: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  var playResult by remember(sura, ayah) { mutableStateOf<TajweedPlayResult?>(null) }
  var textMissing by remember(sura, ayah) { mutableStateOf(false) }
  // once it plays, the sheet makes way for the page, which follows the recitation
  val play = { allowMobileDownload: Boolean ->
    playResult = actions.play(sura, ayah, allowMobileDownload, options)
    if (playResult == TajweedPlayResult.PLAYING) onDismiss()
  }
  val tafsir by produceState<TafsirState>(TafsirState.Loading, sura, ayah) {
    value = TafsirState.Loaded(
      try {
        actions.tafsir(sura, ayah)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        null
      }
    )
  }
  val chipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = palette.primary,
    selectedLabelColor = palette.onPrimary,
    labelColor = palette.ink
  )

  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
    Column(
      Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = stringResource(
          R.string.tajweed_ayah_title,
          suraNames.getOrElse(sura - 1) { sura.toString() },
          ayah.toString().toPersianDigits()
        ),
        fontFamily = NotoKufiArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = palette.ink,
        modifier = Modifier.semantics { heading() }
      )

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val assistColors = AssistChipDefaults.assistChipColors(labelColor = palette.primary)
        AssistChip(
          onClick = {
            onDismiss()
            actions.openBookmarks(sura, ayah)
          },
          label = { Text(stringResource(R.string.tajweed_bookmark)) },
          colors = assistColors
        )
        AssistChip(
          onClick = { textMissing = !actions.copy(sura, ayah) },
          label = { Text(stringResource(R.string.tajweed_copy)) },
          colors = assistColors
        )
        AssistChip(
          onClick = { textMissing = !actions.shareText(sura, ayah) },
          label = { Text(stringResource(R.string.tajweed_share_text)) },
          colors = assistColors
        )
        AssistChip(
          onClick = { actions.shareLink(sura, ayah) },
          label = { Text(stringResource(R.string.tajweed_share_link)) },
          colors = assistColors
        )
      }
      if (textMissing) {
        Text(stringResource(R.string.tajweed_text_missing), fontSize = 13.sp, color = palette.muted)
      }

      HorizontalDivider(color = palette.border)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = { play(false) },
          colors = ButtonDefaults.buttonColors(
            containerColor = palette.primary,
            contentColor = palette.onPrimary
          )
        ) {
          Text(stringResource(R.string.tajweed_play), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = actions.stop) {
          Text(stringResource(R.string.tajweed_stop), color = palette.primary)
        }
      }
      when (playResult) {
        TajweedPlayResult.DOWNLOADING ->
          Text(stringResource(R.string.tajweed_audio_downloading), fontSize = 13.sp, color = palette.muted)
        TajweedPlayResult.NEEDS_DOWNLOAD_CONSENT -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(stringResource(R.string.tajweed_audio_mobile_data), fontSize = 13.sp, color = palette.muted)
          TextButton(onClick = { play(true) }) {
            Text(stringResource(R.string.tajweed_audio_download), color = palette.primary, fontWeight = FontWeight.Bold)
          }
        }
        TajweedPlayResult.PLAYING, null -> Unit
      }

      OptionLabel(stringResource(R.string.tajweed_repeat_ayah))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        VERSE_REPEATS.forEach { repeat ->
          FilterChip(
            selected = options.verseRepeat == repeat,
            onClick = { onOptionsChange(options.copy(verseRepeat = repeat)) },
            label = {
              Text(
                if (repeat < 0) stringResource(R.string.tajweed_repeat_forever)
                else stringResource(R.string.tajweed_repeat_times, (repeat + 1).toString().toPersianDigits())
              )
            },
            colors = chipColors
          )
        }
      }
      OptionLabel(stringResource(R.string.tajweed_speed))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SPEEDS.forEach { speed ->
          FilterChip(
            selected = options.speed == speed,
            onClick = { onOptionsChange(options.copy(speed = speed)) },
            label = { Text(speed.toString().removeSuffix(".0").replace('.', '٫').toPersianDigits() + "×") },
            colors = chipColors
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = options.toEndOfSura,
          onCheckedChange = { onOptionsChange(options.copy(toEndOfSura = it)) },
          colors = CheckboxDefaults.colors(checkedColor = palette.primary)
        )
        Text(stringResource(R.string.tajweed_to_end_of_sura), fontSize = 14.sp, color = palette.ink)
      }

      HorizontalDivider(color = palette.border)
      Text(
        text = stringResource(R.string.tajweed_tafsir),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = palette.accent
      )
      when (val state = tafsir) {
        TafsirState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.size(28.dp), color = palette.primary)
        }
        is TafsirState.Loaded -> Text(
          text = state.text ?: stringResource(R.string.tajweed_tafsir_unavailable),
          fontSize = 16.sp,
          lineHeight = 28.sp,
          color = if (state.text == null) palette.muted else palette.ink
        )
      }
    }
  }
}

@Composable
private fun OptionLabel(text: String) {
  Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LocalTasnimPalette.current.muted)
}
