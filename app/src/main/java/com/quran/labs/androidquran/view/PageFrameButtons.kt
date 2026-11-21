package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R

/** The ornamental page frame's round buttons: the mushaf menu (right) and search (left). */
object PageFrameButtons {

  /** Adds the frame's menu or search button to [parent]; a tap goes to the screen's [PageFrameHost]. */
  @JvmStatic
  fun create(context: Context, parent: ViewGroup, isMenu: Boolean): ImageButton {
    val button = ImageButton(context)
    button.setImageResource(if (isMenu) R.drawable.ic_tasnim_menu else R.drawable.ic_tasnim_search)
    button.contentDescription = context.getString(if (isMenu) R.string.menu_settings else R.string.menu_search)
    button.scaleType = android.widget.ImageView.ScaleType.CENTER
    button.setOnClickListener {
      val host = context as? PageFrameHost ?: return@setOnClickListener
      if (isMenu) host.onPageFrameMenu() else host.onPageFrameSearch()
    }
    parent.addView(button, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    return button
  }

  /** Cream (night: dark) faces ringed in teal and gilt, with the icon in teal (night: gilt). */
  @JvmStatic
  fun style(context: Context, nightMode: Boolean, vararg buttons: ImageButton) {
    fun c(id: Int) = ContextCompat.getColor(context, id)
    val ring = c(if (nightMode) R.color.page_frame_band_night else R.color.page_frame_band)
    val face = c(if (nightMode) R.color.page_frame_button_night else R.color.page_frame_button)
    val gold = c(if (nightMode) R.color.mushaf_gilt_night else R.color.page_frame_gold)
    val icon = c(if (nightMode) R.color.mushaf_gilt_night else R.color.page_frame_band)
    val inset = Math.round(
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics)
    )
    for (button in buttons) {
      val outer = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ring)
      }
      val inner = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(face)
        setStroke(maxOf(1, inset / 2), gold)
      }
      val background = LayerDrawable(arrayOf<Drawable>(outer, inner))
      background.setLayerInset(1, inset, inset, inset, inset)
      button.background = background
      button.setColorFilter(icon)
    }
  }
}
