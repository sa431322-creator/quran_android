package com.quran.labs.androidquran.common.ui.core

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Whether the app runs on a TV (Android TV, Google TV, Fire TV). This is the same test as the
 * `-television` resource qualifier, so code and resources always agree on what a TV is.
 */
fun Context.isTv(): Boolean = resources.configuration.isTv()

/** [Context.isTv] for composables. */
@Composable
fun isTv(): Boolean = LocalConfiguration.current.isTv()

private fun Configuration.isTv(): Boolean =
  (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

/**
 * The TV focus look: a gold frame and a slight zoom on the item the remote is on, so the
 * viewer always sees where they are from across the room. [focused] usually comes from the
 * item's `MutableInteractionSource.collectIsFocusedAsState()`.
 */
@Composable
fun Modifier.tvFocusFrame(focused: Boolean, shape: Shape): Modifier {
  val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tvFocusScale")
  return this
    .graphicsLayer {
      scaleX = scale
      scaleY = scale
    }
    // no border at all when unfocused: a 0.dp border would still draw a hairline
    .then(if (focused) Modifier.border(3.dp, LocalTasnimPalette.current.gold, shape) else Modifier)
}

/** The TV safe area: overscan can hide the outer 5% of the picture, so content stays inside. */
val TvSafeSide = 48.dp
val TvSafeTop = 27.dp

/** A clickable area with the TV focus look ([tvFocusFrame]) while the remote is on it. */
@Composable
fun Modifier.tvClickable(shape: Shape, onClick: () -> Unit): Modifier {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  return this
    .tvFocusFrame(focused, shape)
    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
}
