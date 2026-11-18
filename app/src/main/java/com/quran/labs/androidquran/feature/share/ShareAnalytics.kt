package com.quran.labs.androidquran.feature.share

import com.quran.analytics.AnalyticsProvider
import com.quran.data.model.SuraAyah
import dev.zacsweers.metro.Inject

/** Counts how ayahs are shared ("ayah_share"), so we can see which ways people use. */
class ShareAnalytics @Inject constructor(private val analyticsProvider: AnalyticsProvider) {

  /** [kind] is one of text, copy, link, image, save, video, audio. */
  fun logShare(kind: String, start: SuraAyah, end: SuraAyah, options: ShareCardOptions? = null) {
    val params = buildMap<String, Any> {
      put("kind", kind)
      put("sura", start.sura)
      put("ayahs", if (start.sura == end.sura) end.ayah - start.ayah + 1 else 0)
      options?.let {
        put("size", it.aspect.name.lowercase())
        put("background", it.background)
        put("tafsir", it.showTafsir)
        if (kind == "video") {
          put("motion", it.videoMotion)
          put("all_ayahs", it.videoAllAyahs)
        }
      }
    }
    analyticsProvider.logEvent("ayah_share", params)
  }
}
