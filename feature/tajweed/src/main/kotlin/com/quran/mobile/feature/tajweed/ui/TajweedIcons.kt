package com.quran.mobile.feature.tajweed.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Drawn for a right-to-left screen already, so they do not auto-mirror. Tint them with Icon. */
object TajweedIcons {
  val Back = stroked("M9 6 L15 12 L9 18")
  val List = stroked("M5 7 H19", "M5 12 H19", "M5 17 H13")
}

private fun stroked(vararg paths: String): ImageVector =
  ImageVector.Builder(
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = false
  ).apply {
    paths.forEach {
      addPath(
        pathData = addPathNodes(it),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      )
    }
  }.build()
