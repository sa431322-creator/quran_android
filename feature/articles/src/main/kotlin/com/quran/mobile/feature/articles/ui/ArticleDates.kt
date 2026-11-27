package com.quran.mobile.feature.articles.ui

import android.icu.text.DateFormat
import android.icu.util.ULocale
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** "۶ آذر ۱۴۰۵": the Persian (solar) date the panel and the website show for an article. */
internal object ArticleDates {
  private val persian: DateFormat =
    DateFormat.getDateInstance(DateFormat.LONG, ULocale("fa_IR@calendar=persian"))

  fun format(iso: String): String {
    val date = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'").firstNotNullOfOrNull {
      try {
        SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)
      } catch (_: ParseException) {
        null
      }
    } ?: return ""
    return synchronized(persian) { persian.format(date) }
  }

  /** Author and date under a title, joined with a Persian comma («·» reads like the digit ۰). */
  fun byline(author: String?, iso: String, by: (String) -> String): String =
    listOfNotNull(author?.takeIf { it.isNotBlank() }?.let(by), format(iso).ifEmpty { null })
      .joinToString("، ")
}
