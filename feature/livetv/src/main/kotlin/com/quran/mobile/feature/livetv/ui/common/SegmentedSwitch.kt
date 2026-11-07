package com.quran.mobile.feature.livetv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.Vazirmatn

/**
 * The pill switch from the design's header (تصویری / صوتی). Two options keep their own widths;
 * three or more (our channel, Quran, Sunnah) share the full width so the names fit on a phone.
 */
@Composable
fun SegmentedSwitch(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalTasnimPalette.current
  val shareWidth = options.size > 2
  Row(
    modifier = modifier
      .then(if (shareWidth) Modifier.fillMaxWidth() else Modifier)
      .background(palette.track, RoundedCornerShape(20.dp))
      .padding(3.dp)
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    options.forEachIndexed { index, label ->
      val selected = index == selectedIndex
      Box(
        modifier = Modifier
          .then(if (shareWidth) Modifier.weight(1f) else Modifier)
          .height(34.dp)
          .clip(RoundedCornerShape(17.dp))
          .then(if (selected) Modifier.background(palette.primary) else Modifier)
          .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
          .padding(horizontal = if (shareWidth) 6.dp else 14.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = label,
          color = if (selected) palette.onPrimary else palette.ink,
          fontFamily = Vazirmatn,
          fontSize = 13.sp,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
