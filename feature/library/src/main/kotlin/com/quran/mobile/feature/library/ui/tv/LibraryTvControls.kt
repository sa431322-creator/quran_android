package com.quran.mobile.feature.library.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.labs.androidquran.common.ui.core.tvFocusFrame

// The remote-driven pieces shared by the library's TV screens (docs/tv/PLAN.md, phase 3).

/** A pill-shaped button or tab (a category, search, bookmarks), framed in gold under the remote. */
@Composable
internal fun TvPill(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  trailing: String? = null
) {
  val palette = LocalTasnimPalette.current
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val shape = RoundedCornerShape(28.dp)
  val ink = if (selected) palette.onPrimary else palette.ink
  Row(
    modifier = modifier
      .heightIn(min = 56.dp)
      .tvFocusFrame(focused, shape)
      .background(if (selected) palette.primary else palette.surface, shape)
      .border(1.dp, if (selected) palette.primary else palette.border, shape)
      .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
      .semantics { this.selected = selected }
      .padding(horizontal = 22.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    icon?.let { Icon(imageVector = it, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp)) }
    Text(
      text = text,
      color = ink,
      fontFamily = Vazirmatn,
      fontSize = 18.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      maxLines = 1
    )
    trailing?.let {
      Text(
        text = it,
        color = if (selected) palette.onPrimaryMuted else palette.muted,
        fontFamily = Vazirmatn,
        fontSize = 16.sp
      )
    }
  }
}
