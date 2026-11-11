package com.quran.mobile.feature.livetv.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.NotoKufiArabic
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.toPersianDigits
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.ScheduleEntry
import com.quran.mobile.feature.livetv.data.onAirIndex
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The index of the program on air now in [entries], updated on each minute boundary so it
 * moves with the clock. Shared by the screen's title and [ScheduleSection].
 */
@Composable
fun rememberOnAirIndex(entries: List<ScheduleEntry>): Int {
  val nowMinute by produceState(currentMinuteOfDay()) {
    while (true) {
      delay(MILLIS_PER_MINUTE - System.currentTimeMillis() % MILLIS_PER_MINUTE)
      value = currentMinuteOfDay()
    }
  }
  return entries.onAirIndex(nowMinute)
}

/** «برنامهٔ امروز»: today's programs with their start times; [onAir] is marked. */
@Composable
fun ScheduleSection(entries: List<ScheduleEntry>, onAir: Int, modifier: Modifier = Modifier) {
  val palette = LocalTasnimPalette.current

  Column(modifier.padding(horizontal = 20.dp)) {
    Text(
      text = stringResource(R.string.livetv_schedule_title),
      color = palette.ink,
      fontFamily = NotoKufiArabic,
      fontSize = 17.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier
        .padding(bottom = 6.dp)
        .semantics { heading() }
    )
    entries.forEachIndexed { index, entry ->
      ScheduleRow(entry = entry, isOnAir = index == onAir)
    }
  }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry, isOnAir: Boolean) {
  val palette = LocalTasnimPalette.current
  val context = LocalContext.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 58.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text(
      text = formatScheduleTime(entry.startMinute),
      color = if (isOnAir) palette.live else palette.accent,
      fontFamily = Vazirmatn,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.width(56.dp)
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = entry.title,
        color = palette.ink,
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
      )
      if (isOnAir) {
        Text(
          text = stringResource(R.string.livetv_on_air),
          color = palette.live,
          fontFamily = Vazirmatn,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
    // reminders need real program times from the backend
    IconButton(
      onClick = {
        Toast.makeText(context, R.string.livetv_reminder_soon, Toast.LENGTH_SHORT).show()
      },
      modifier = Modifier.size(44.dp)
    ) {
      Icon(
        imageVector = LiveIcons.Bell,
        contentDescription = stringResource(R.string.livetv_reminder, entry.title),
        tint = palette.accent,
        modifier = Modifier.size(20.dp)
      )
    }
  }
  HorizontalDivider(color = palette.border)
}

private const val MILLIS_PER_MINUTE = 60_000L

private fun currentMinuteOfDay(): Int {
  val now = Calendar.getInstance()
  return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}

/** «۰۶:۳۰» for 390: 24-hour time in Persian digits, whatever the device locale. */
internal fun formatScheduleTime(minuteOfDay: Int): String =
  "%02d:%02d".format(Locale.ROOT, minuteOfDay / 60, minuteOfDay % 60).toPersianDigits()
