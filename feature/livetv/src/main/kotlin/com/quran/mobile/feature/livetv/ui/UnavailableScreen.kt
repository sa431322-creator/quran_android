package com.quran.mobile.feature.livetv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.ui.common.LiveHeader

/** Shown in place of the video or radio screen while that broadcast is switched off. */
@Composable
fun UnavailableScreen(message: String, onBack: () -> Unit) {
  // the screen's copy is Persian, so lay it out right to left regardless of device locale
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      ) {
        LiveHeader(onBack = onBack)
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Image(
            painter = painterResource(R.drawable.livetv_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(112.dp)
              .clip(CircleShape)
              .alpha(0.6f)
          )
          Spacer(Modifier.height(24.dp))
          Text(
            text = stringResource(R.string.livetv_unavailable_title),
            color = palette.ink,
            fontFamily = Vazirmatn,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
          )
          Spacer(Modifier.height(10.dp))
          Text(
            text = message,
            color = palette.muted,
            fontFamily = Vazirmatn,
            fontSize = 15.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(28.dp))
          OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.dp, palette.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.ink)
          ) {
            Text(
              text = stringResource(R.string.livetv_back),
              fontFamily = Vazirmatn,
              fontSize = 15.sp,
              modifier = Modifier.padding(horizontal = 16.dp)
            )
          }
        }
      }
    }
  }
}
