package com.quran.mobile.feature.livetv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.quran.mobile.feature.livetv.R

/** Back button, the «پخش زنده» title, and an optional trailing slot. */
@Composable
fun LiveHeader(onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
  val palette = LocalTasnimPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
      Icon(
        imageVector = LiveIcons.Back,
        contentDescription = stringResource(R.string.livetv_back),
        tint = palette.ink
      )
    }
    Box(Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.livetv_title),
        fontFamily = NotoKufiArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = palette.ink,
        modifier = Modifier.semantics { heading() }
      )
    }
    trailing()
  }
}
