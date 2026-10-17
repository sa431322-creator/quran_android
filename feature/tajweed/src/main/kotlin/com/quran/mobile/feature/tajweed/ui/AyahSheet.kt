package com.quran.mobile.feature.tajweed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.tajweed.R
import com.quran.mobile.feature.tajweed.TajweedPlayResult
import kotlinx.coroutines.CancellationException

/** What a tapped ayah offers in the tajweed mushaf, backed by the app's tafsir and audio. */
class TajweedAyahActions(
  /** the Persian translation and tafsir of an ayah, or null when it is not available */
  val tafsir: suspend (sura: Int, ayah: Int) -> String?,
  /** starts the recitation, or the download of its audio first */
  val play: (sura: Int, ayah: Int, allowMobileDownload: Boolean) -> TajweedPlayResult,
  val stop: () -> Unit
)

private sealed interface TafsirState {
  data object Loading : TafsirState
  data class Loaded(val text: String?) : TafsirState
}

/** The tapped ayah: recitation from it, and its Persian translation and tafsir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AyahSheet(
  sura: Int,
  ayah: Int,
  suraNames: List<String>,
  actions: TajweedAyahActions,
  onDismiss: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  var playResult by remember(sura, ayah) { mutableStateOf<TajweedPlayResult?>(null) }
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

  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
    Column(
      Modifier
        .fillMaxWidth()
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
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = { playResult = actions.play(sura, ayah, false) },
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
          TextButton(onClick = { playResult = actions.play(sura, ayah, true) }) {
            Text(stringResource(R.string.tajweed_audio_download), color = palette.primary, fontWeight = FontWeight.Bold)
          }
        }
        TajweedPlayResult.PLAYING, null -> Unit
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
          color = if (state.text == null) palette.muted else palette.ink,
          modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
        )
      }
    }
  }
}
