package com.quran.mobile.feature.library.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The library's icons, drawn for a right-to-left screen like the live screens' icons. */
internal object LibraryIcons {
  val Back = stroked("M9 6 L15 12 L9 18")
  val Book = stroked("M12 6 Q8 4 3 5 V19 Q8 18 12 20 Q16 18 21 19 V5 Q16 4 12 6 Z M12 6 V20")
  val Open = stroked("M15 6 L9 12 L15 18")

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
