package com.quran.mobile.feature.livetv.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.livetv.R

private val LiveRed = Color(0xFFD32F2F)

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "liveDot")
  val dotAlpha by transition.animateFloat(
    initialValue = 1f,
    targetValue = 0.25f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
    label = "liveDotAlpha"
  )

  Row(
    modifier = modifier
      .background(LiveRed, RoundedCornerShape(50))
      .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      Modifier
        .size(8.dp)
        .alpha(dotAlpha)
        .background(Color.White, CircleShape)
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = stringResource(R.string.livetv_badge),
      color = Color.White,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold
    )
  }
}
