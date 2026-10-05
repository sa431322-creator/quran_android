package com.quran.mobile.feature.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveChannel
import com.quran.mobile.feature.livetv.data.ProgramGuideEntry

@Composable
fun LiveTvScreen(channel: LiveChannel, modifier: Modifier = Modifier) {
  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(modifier = modifier) {
      LiveVideoPlayer(
        source = channel.source,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .background(Color.Black)
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = channel.title,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        LiveBadge()
      }

      Text(
        text = stringResource(R.string.livetv_guide_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(channel.guide, key = { it.startTime }) { entry -> ProgramGuideRow(entry) }
      }
    }
  }
}

@Composable
private fun ProgramGuideRow(entry: ProgramGuideEntry) {
  val background = if (entry.isOnAir) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceVariant
  }
  val content = if (entry.isOnAir) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(background, RoundedCornerShape(12.dp))
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = entry.startTime, color = content, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.width(16.dp))
    Text(
      text = entry.title,
      color = content,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = if (entry.isOnAir) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.weight(1f)
    )
    if (entry.isOnAir) {
      Text(
        text = stringResource(R.string.livetv_on_air),
        color = content,
        style = MaterialTheme.typography.labelMedium
      )
    }
  }
}
