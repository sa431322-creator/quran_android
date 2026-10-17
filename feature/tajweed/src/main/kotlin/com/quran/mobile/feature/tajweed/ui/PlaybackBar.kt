package com.quran.mobile.feature.tajweed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.tajweed.R
import com.quran.mobile.feature.tajweed.TajweedPlayback
import kotlinx.coroutines.flow.Flow

/** The app's recitation as the tajweed mushaf follows and controls it. */
class TajweedPlaybackControls(
  /** the ayah being recited, or null when nothing is playing */
  val playback: Flow<TajweedPlayback?>,
  /** emits when a recitation download finishes */
  val audioDownloaded: Flow<Unit>,
  val pause: () -> Unit,
  val resume: () -> Unit,
  val stop: () -> Unit
)

/**
 * Shown while a recitation plays, or while its audio downloads before playing ([playback] null):
 * the recited ayah, pause or resume, and stop.
 */
@Composable
internal fun PlaybackBar(
  playback: TajweedPlayback?,
  suraNames: List<String>,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onStop: () -> Unit
) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .background(palette.surface, RoundedCornerShape(50))
      .padding(start = 16.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    if (playback == null) {
      CircularProgressIndicator(Modifier.size(18.dp), color = palette.primary, strokeWidth = 2.dp)
    }
    Text(
      text = if (playback == null) {
        stringResource(R.string.tajweed_audio_downloading_short)
      } else {
        stringResource(
          R.string.tajweed_ayah_title,
          suraNames.getOrElse(playback.sura - 1) { playback.sura.toString() },
          playback.ayah.toString().toPersianDigits()
        )
      },
      color = palette.ink,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      modifier = Modifier.weight(1f)
    )
    if (playback != null) {
      IconButton(onClick = if (playback.paused) onResume else onPause, modifier = Modifier.size(44.dp)) {
        Icon(
          imageVector = if (playback.paused) TajweedIcons.Play else TajweedIcons.Pause,
          contentDescription = stringResource(if (playback.paused) R.string.tajweed_resume else R.string.tajweed_pause),
          tint = palette.primary
        )
      }
    }
    IconButton(onClick = onStop, modifier = Modifier.size(44.dp)) {
      Icon(TajweedIcons.Stop, contentDescription = stringResource(R.string.tajweed_stop), tint = palette.primary)
    }
  }
}
