package com.quran.labs.androidquran.view

import android.content.Context

class QuranTabletImagePageLayout(context: Context) : QuranImagePageLayout(context) {

  override fun shouldWrapWithScrollView(): Boolean = false

  // a tablet's two pages side by side: no room for the ornamental frame
  override fun shouldShowFrame(): Boolean = false
}
