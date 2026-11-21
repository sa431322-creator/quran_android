package com.quran.labs.androidquran.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat

/**
 * Tasnim's ornamental page frame fixed on the screen around [content]: the band, the juz and sura
 * cartouches between the menu and search buttons, and the page number underneath. For readers
 * whose text moves under one frame (the scrolling pages, the ayah cards); the paper is drawn
 * behind the frame, and [content] is laid out inside it.
 */
@SuppressLint("ViewConstructor")
class TasnimFramedLayout(context: Context, private val content: View) : ViewGroup(context) {

  private val frame = TasnimPageFrame(context)
  private val contentRect = RectF()
  private val menuButton = PageFrameButtons.create(context, this, isMenu = true)
  private val searchButton = PageFrameButtons.create(context, this, isMenu = false)

  // as on the mushaf's pages, a landscape screen has no room for the frame: the content fills it
  // (the reader is made again when the screen turns, so this holds for the layout's life)
  private val framed: Boolean =
    context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE

  init {
    addView(content, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    setWillNotDraw(false)
    // no touch on the frame falls through to the reader's page behind
    isClickable = true
    PageFrameButtons.style(context, false, menuButton, searchButton)
    if (!framed) {
      menuButton.visibility = GONE
      searchButton.visibility = GONE
    }
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      val cutout = insets.displayCutout
      if (frame.setInsets(cutout?.safeInsetTop ?: 0, cutout?.safeInsetBottom ?: 0)) {
        requestLayout()
        invalidate()
      }
      insets
    }
  }

  /** The paper behind the frame and the frame's colors, day or night. */
  fun update(nightMode: Boolean, paper: Int) {
    setBackgroundColor(paper)
    frame.setNightMode(nightMode)
    PageFrameButtons.style(context, nightMode, menuButton, searchButton)
    invalidate()
  }

  /** The sura, juz and page number shown in the frame. */
  fun setLabels(sura: String, juz: String, page: String) {
    if (frame.sura == sura && frame.juz == juz && frame.page == page) return
    frame.sura = sura
    frame.juz = juz
    frame.page = page
    invalidate()
  }

  /** How far the frame reaches into the screen at the top and the bottom (pixels). */
  fun contentTop(): Int = if (framed) frame.content(width, height, contentRect).top.toInt() else 0
  fun contentBottomInset(): Int =
    if (framed) (height - frame.content(width, height, contentRect).bottom).toInt() else 0

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val height = MeasureSpec.getSize(heightMeasureSpec)
    setMeasuredDimension(width, height)
    if (framed) frame.content(width, height, contentRect) else contentRect.set(0f, 0f, width.toFloat(), height.toFloat())
    content.measure(
      MeasureSpec.makeMeasureSpec(maxOf(0, Math.round(contentRect.width())), MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(maxOf(0, Math.round(contentRect.height())), MeasureSpec.EXACTLY)
    )
    val size = MeasureSpec.makeMeasureSpec(frame.buttonSize, MeasureSpec.EXACTLY)
    menuButton.measure(size, size)
    searchButton.measure(size, size)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    if (framed) {
      frame.content(measuredWidth, measuredHeight, contentRect)
    } else {
      contentRect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
    }
    content.layout(
      Math.round(contentRect.left), Math.round(contentRect.top),
      Math.round(contentRect.right), Math.round(contentRect.bottom)
    )
    val top = frame.buttonTop()
    val inset = frame.buttonInset()
    val size = frame.buttonSize
    searchButton.layout(inset, top, inset + size, top + size)
    menuButton.layout(measuredWidth - inset - size, top, measuredWidth - inset, top + size)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (framed) frame.draw(canvas, width, height)
  }
}
