package com.quran.mobile.feature.articles.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons in the style of the library's (24dp, 1.8 stroke). */
internal object ArticleIcons {
  val Back = stroked("M9 6 L15 12 L9 18")
  val Share = stroked(
    "M15.5 5 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M3.5 12 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M15.5 19 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M8.2 10.9 L15.8 6.1 M8.2 13.1 L15.8 17.9"
  )
  val Article = stroked("M5 4 H19 V20 H5 Z", "M8 8 H16 M8 12 H16 M8 16 H13")

  private fun stroked(vararg paths: String): ImageVector =
    ImageVector.Builder(
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
      autoMirror = false
    ).apply {
      paths.forEach { path ->
        addPath(
          pathData = addPathNodes(path),
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 1.8f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round
        )
      }
    }.build()
}
