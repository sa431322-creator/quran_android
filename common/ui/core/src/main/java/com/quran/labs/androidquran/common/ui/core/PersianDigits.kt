package com.quran.labs.androidquran.common.ui.core

/** Replaces ASCII digits with Persian ones (۰–۹). */
fun String.toPersianDigits(): String = buildString(length) {
  for (c in this@toPersianDigits) {
    append(if (c in '0'..'9') '۰' + (c - '0') else c)
  }
}

/** Formats a count with Persian digits and the Persian thousands separator, e.g. ۱٬۲۵۰. */
fun formatPersianCount(count: Int): String =
  count.toString()
    .reversed()
    .chunked(3)
    .joinToString("٬")
    .reversed()
    .toPersianDigits()
