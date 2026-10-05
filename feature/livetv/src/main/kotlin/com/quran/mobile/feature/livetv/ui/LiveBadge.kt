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
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.ui.common.LocalLivePalette

/** The red «زنده» pill. [pulsing] animates the dot while the broadcast is actually playing. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, pulsing: Boolean = true) {
  Row(
    modifier = modifier
      .background(LocalLivePalette.current.live, RoundedCornerShape(14.dp))
      .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      Modifier
        .size(7.dp)
        .then(if (pulsing) Modifier.pulsing() else Modifier)
        .background(Color.White, CircleShape)
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = stringResource(R.string.livetv_badge),
      color = Color.White,
      fontFamily = Vazirmatn,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

// a modifier of its own so the infinite animation only runs while pulsing
@Composable
private fun Modifier.pulsing(): Modifier {
  val transition = rememberInfiniteTransition(label = "liveDot")
  val dotAlpha by transition.animateFloat(
    initialValue = 1f,
    targetValue = 0.25f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
    label = "liveDotAlpha"
  )
  return this.alpha(dotAlpha)
}

/** The small «● زنده» marker used in channel and station rows. */
@Composable
fun LiveMarker(modifier: Modifier = Modifier) {
  Text(
    text = stringResource(R.string.livetv_live_marker),
    color = LocalLivePalette.current.live,
    fontFamily = Vazirmatn,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = modifier
  )
}
