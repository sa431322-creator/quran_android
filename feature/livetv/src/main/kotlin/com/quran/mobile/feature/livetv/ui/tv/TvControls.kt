package com.quran.mobile.feature.livetv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.tvFocusFrame

// The remote-driven controls shared by the TV live and radio screens (docs/tv/PLAN.md).

/** A round remote-friendly button with its name under it (TVs have no hover or long-press hints). */
@Composable
internal fun TvControl(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  dimmed: Boolean = false,
  size: Dp = 64.dp
) {
  val palette = LocalTasnimPalette.current
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.width(size + 24.dp)
  ) {
    Box(
      modifier = modifier
        .size(size)
        .tvFocusFrame(focused, CircleShape)
        .background(if (focused) palette.onVideo else palette.onVideo.copy(alpha = 0.14f), CircleShape)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .clearAndSetSemantics { contentDescription = label },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (focused) {
          palette.videoBackground
        } else {
          palette.onVideo.copy(alpha = if (dimmed) 0.55f else 1f)
        },
        modifier = Modifier.size(size * 0.47f)
      )
    }
    Text(
      text = label,
      color = palette.onVideo.copy(alpha = if (focused) 1f else 0.7f),
      fontFamily = Vazirmatn,
      fontSize = 13.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 8.dp)
    )
  }
}

/** A channel (or the radio) to switch to; the playing one is filled. */
@Composable
internal fun TvChip(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalTasnimPalette.current
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val shape = RoundedCornerShape(32.dp)
  Box(
    modifier = modifier
      .height(64.dp)
      .tvFocusFrame(focused, shape)
      .background(
        when {
          focused -> palette.onVideo
          selected -> palette.primary
          else -> palette.onVideo.copy(alpha = 0.14f)
        },
        shape
      )
      .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
      .semantics { this.selected = selected }
      .padding(horizontal = 28.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = if (focused) palette.videoBackground else palette.onVideo,
      fontFamily = Vazirmatn,
      fontSize = 18.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      maxLines = 1,
      softWrap = false
    )
  }
}
