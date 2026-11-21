package com.quran.labs.androidquran.view

/** What the ornamental page frame's buttons ask of the screen showing the page (the reader). */
interface PageFrameHost {
  /** The round button on the right of the page's header: the mushaf's settings menu. */
  fun onPageFrameMenu()

  /** The round button on the left of the page's header: search. */
  fun onPageFrameSearch()

  /** The translation pages' footer button: the translations and tafsirs to download and choose. */
  fun onPageFrameTranslations()
}
