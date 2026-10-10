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
  val Search = stroked("M10 10 m-6 0 a6 6 0 1 0 12 0 a6 6 0 1 0 -12 0", "M14.5 14.5 L20 20")
  val Close = stroked("M6 6 L18 18 M18 6 L6 18")
  val Bookmark = stroked("M6 4 H18 V20 L12 16 L6 20 Z")
  val BookmarkFilled = filled("M6 4 H18 V20 L12 16 L6 20 Z")
  val Bookmarks = stroked("M8 7 H18 V21 L13 17.5 L8 21 Z", "M6 17 V3 H15")
  val Share = stroked(
    "M15.5 5 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M3.5 12 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M15.5 19 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0",
    "M8.2 10.9 L15.8 6.1 M8.2 13.1 L15.8 17.9"
  )
  val Contents = stroked("M9 6 H20 M9 12 H20 M9 18 H20", "M4 6 H5 M4 12 H5 M4 18 H5")
  val Moon = stroked("M20 14.5 A8 8 0 1 1 9.5 4 A6.5 6.5 0 0 0 20 14.5 Z")
  val Sun = stroked(
    "M12 12 m-4 0 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0",
    "M12 2 V4 M12 20 V22 M2 12 H4 M20 12 H22 M4.9 4.9 L6.3 6.3 M17.7 17.7 L19.1 19.1 M4.9 19.1 L6.3 17.7 M17.7 6.3 L19.1 4.9"
  )
  val Download = stroked("M12 4 V15 M7 10 L12 15 L17 10", "M5 20 H19")
  val Done = stroked("M12 12 m-9 0 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0", "M8 12 L11 15 L16 9")
  val Pending = stroked("M12 12 m-9 0 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0")
  val Play = filled("M17 5 V19 L6 12 Z")
  val Clock = stroked("M12 12 m-9 0 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0", "M12 7 V12 L15 14")
  val Pages = stroked("M7 3 H14 L19 8 V21 H7 Z", "M14 3 V8 H19", "M10 13 H16 M10 17 H16")
  val Size = stroked("M5 7 Q12 3 19 7 Q12 11 5 7 Z", "M5 7 V17 Q12 21 19 17 V7", "M5 12 Q12 16 19 12")
  val Language = stroked(
    "M12 12 m-9 0 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0",
    "M3 12 H21",
    "M12 3 Q16 7.5 16 12 Q16 16.5 12 21 Q8 16.5 8 12 Q8 7.5 12 3 Z"
  )
  val GoToPage = stroked("M7 3 H17 V21 H7 Z", "M14 12 H10 M12 10 L14 12 L12 14")

  private fun builder() = ImageVector.Builder(
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = false
  )

  private fun stroked(vararg paths: String): ImageVector =
    builder().apply {
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

  private fun filled(path: String): ImageVector =
    builder().addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
