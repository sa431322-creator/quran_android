package com.quran.labs.androidquran.tasnim

import com.quran.labs.androidquran.data.Constants

/**
 * Host for the quran.app JSON lists (`data/translations.php`, `data/audio_updates.php`).
 * The Tasnim server mirrors them with the same paths and JSON; without a Tasnim server
 * (blank base URL, e.g. release builds for now) the app keeps asking quran.app.
 * Always ends with "/" so it works both as a prefix and as a Retrofit base URL.
 */
object TasnimDataHost {
  fun of(tasnimBaseUrl: String): String {
    val base = tasnimBaseUrl.trim().ifBlank { Constants.HOST }
    return if (base.endsWith("/")) base else "$base/"
  }
}
