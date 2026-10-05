package com.quran.mobile.feature.livetv.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The live screens' icons, traced from the design's 24×24 SVGs. They are drawn for a
 * right-to-left screen already, so none of them auto-mirror. Tint them with Icon.
 */
object LiveIcons {
  val Back = stroked("M9 6 L15 12 L9 18")
  val Play = filled("M17 5 V19 L6 12 Z")
  val Pause = filled("M6 5 H10 V19 H6 Z M14 5 H18 V19 H14 Z")
  val Volume = stroked("M20 9 V15 H16 L11 19 V5 L16 9 Z", "M7 9 Q4 12 7 15")
  val VolumeOff = stroked("M20 9 V15 H16 L11 19 V5 L16 9 Z", "M3 9.5 L8 14.5 M8 9.5 L3 14.5")
  val Subtitles = stroked("M5 5 H19 Q21 5 21 7 V17 Q21 19 19 19 H5 Q3 19 3 17 V7 Q3 5 5 5 Z", "M7 14 H11 M13 14 H17 M7 10 H17")
  val Fullscreen = stroked("M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15")
  val FullscreenExit = stroked("M9 4 V9 H4 M20 9 H15 V4 M15 20 V15 H20 M4 15 H9 V20")
  val Viewers = stroked("M2 12 Q12 3 22 12 Q12 21 2 12 Z", CIRCLE_3)
  val VideoChannel = stroked("M5 6 H15 Q17 6 17 8 V16 Q17 18 15 18 H5 Q3 18 3 16 V8 Q3 6 5 6 Z", "M17 10 L21 7 V17 L17 14")
  val Share = stroked(
    "M15.5 5 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M3.5 12 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M15.5 19 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M8.2 10.9 L15.8 6.1 M8.2 13.1 L15.8 17.9"
  )
  val Bell = stroked("M6 16 V11 A6 6 0 0 1 18 11 V16 L20 18 H4 Z", "M10 21 H14")
  val Station = stroked(
    "M9.8 12 a2.2 2.2 0 1 0 4.4 0 a2.2 2.2 0 1 0 -4.4 0",
    "M8 8 Q5 12 8 16 M16 8 Q19 12 16 16"
  )
}

private const val CIRCLE_3 = "M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0"

private fun builder() = ImageVector.Builder(
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24f,
  viewportHeight = 24f,
  autoMirror = false
)

private fun ImageVector.Builder.strokePath(path: String) = addPath(
  pathData = addPathNodes(path),
  stroke = SolidColor(Color.Black),
  strokeLineWidth = 1.8f,
  strokeLineCap = StrokeCap.Round,
  strokeLineJoin = StrokeJoin.Round
)

private fun stroked(vararg paths: String): ImageVector =
  builder().apply { paths.forEach { strokePath(it) } }.build()

private fun filled(path: String): ImageVector =
  builder().addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
