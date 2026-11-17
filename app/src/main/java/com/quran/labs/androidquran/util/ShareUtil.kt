package com.quran.labs.androidquran.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.widget.Toast
import androidx.annotation.StringRes
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.ui.util.ToastCompat
import com.quran.mobile.translation.model.LocalTranslation
import dev.zacsweers.metro.Inject
import java.text.NumberFormat

class ShareUtil @Inject internal constructor(
  private val quranDisplayData: QuranDisplayData,
  private val quranInfo: QuranInfo
) {

  fun copyVerses(activity: Activity, verses: List<QuranText>) {
    val text = getShareText(activity, verses)
    copyToClipboard(activity, text)
  }

  fun copyToClipboard(activity: Activity, text: String?) {
    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(activity.getString(R.string.app_name), text)
    cm.setPrimaryClip(clip)
    ToastCompat.makeText(
      activity, activity.getString(R.string.ayah_copied_popup),
      Toast.LENGTH_SHORT
    ).show()
  }

  fun shareVerses(activity: Activity, verses: List<QuranText>) {
    val text = getShareText(activity, verses)
    shareViaIntent(activity, text, com.quran.labs.androidquran.common.toolbar.R.string.share_ayah_text)
  }

  fun shareAyahLink(activity: Activity, start: SuraAyah, end: SuraAyah) {
    // keep the preview range aligned with the URL, which is limited to a single sura.
    val endAyah = if (end.sura == start.sura) end.ayah else quranInfo.getNumberOfAyahs(start.sura)
    val url = buildString {
      append("https://quran.com/${start.sura}/${start.ayah}")
      if (endAyah != start.ayah) {
        append("-$endAyah")
      }
    }

    val previewTitle = rangeLabel(activity, start, SuraAyah(start.sura, endAyah))

    shareViaIntent(
      activity, url, com.quran.labs.androidquran.common.toolbar.R.string.share_ayah,
      previewTitle
    )
  }

  fun shareViaIntent(
    activity: Activity,
    text: String?,
    @StringRes titleResId: Int,
    previewTitle: String? = null
  ) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
      previewTitle?.let { putExtra(Intent.EXTRA_TITLE, it) }
    }
    activity.startActivity(Intent.createChooser(intent, activity.getString(titleResId)))
  }

  fun getShareText(
    context: Context,
    ayahInfo: QuranAyahInfo,
    translationNames: Array<LocalTranslation>
  ): String {
    return buildString {
      ayahInfo.arabicText?.let {
        append("{ ")
        append(ArabicDatabaseUtils.getAyahWithoutBasmallah(ayahInfo.sura, ayahInfo.ayah, ayahInfo.arabicText.trim()))
        append(" }")
        append("\n")
        append("[")
        append(quranDisplayData.getSuraAyahString(context, ayahInfo.sura, ayahInfo.ayah, R.string.sura_ayah_sharing_str))
        append(pageLabel(context, SuraAyah(ayahInfo.sura, ayahInfo.ayah), null))
        append("]")
      }

      ayahInfo.texts.forEachIndexed { i, translation ->
        val text = translation.text
        if (text.isNotEmpty()) {
          append("\n\n")
          if (i < translationNames.size) {
            append(translationNames[i].resolveTranslatorName())
            append(":\n")
          }

          // remove footnotes for now
          val spannableStringBuilder = SpannableStringBuilder(text)
          translation.footnoteCognizantText(
            spannableStringBuilder,
            listOf(),
            { _ -> SpannableString("") },
            { builder, _, _ -> builder }
          )
          append(spannableStringBuilder)
        }
      }
      if (ayahInfo.arabicText == null) {
        append("\n")
        append("-")
        append(quranDisplayData.getSuraAyahString(context, ayahInfo.sura, ayahInfo.ayah, R.string.sura_ayah_notification_str))
        append(pageLabel(context, SuraAyah(ayahInfo.sura, ayahInfo.ayah), null))
      }
    }
  }

  /** "سوره بقره: ۲۵۵" or "سوره بقره: ۲۵۵-۲۵۷", or "سوره بقره: ۲۸۶ - سوره آل عمران: ۳" across suras. */
  fun rangeLabel(context: Context, start: SuraAyah, end: SuraAyah): String {
    val numberFormat = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    return buildString {
      append(quranDisplayData.getSuraName(context, start.sura, true))
      append(": ")
      append(numberFormat.format(start.ayah))
      if (end != start) {
        if (end.sura != start.sura) {
          append(" - ")
          append(quranDisplayData.getSuraName(context, end.sura, true))
          append(": ")
        } else {
          append("-")
        }
        append(numberFormat.format(end.ayah))
      }
    }
  }

  /**
   * Tasnim: the page (or pages) of the mushaf the shared ayahs are on, as " — صفحه ۴۲" or
   * " — صفحه ۴۲-۴۳", appended inside the sura label.
   */
  internal fun pageLabel(context: Context, start: SuraAyah, end: SuraAyah?): String {
    val numberFormat = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    val startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah)
    val endPage = end?.let { quranInfo.getPageFromSuraAyah(it.sura, it.ayah) } ?: startPage
    val pages = if (endPage > startPage) {
      numberFormat.format(startPage) + "-" + numberFormat.format(endPage)
    } else {
      numberFormat.format(startPage)
    }
    return " — " + context.getString(R.string.tasnim_share_page, pages)
  }

  private fun getShareText(activity: Activity, verses: List<QuranText>): String {
    val size = verses.size
    val wantInlineAyahNumbers = size > 1
    val locale = QuranUtils.getCurrentLocale()
    val numberFormat = NumberFormat.getNumberInstance(locale)
    return buildString {
      append("{ ")
      for (i in 0 until size) {
        if (i > 0) {
          append(" ")
        }

        append(ArabicDatabaseUtils.getAyahWithoutBasmallah(verses[i].sura, verses[i].ayah, verses[i].text.trim()))
        if (wantInlineAyahNumbers) {
          append(" (")
          append(numberFormat.format(verses[i].ayah))
          append(")")
        }
      }

      // append } and a new line after last ayah
      append(" }\n")
      // append [ before sura label
      append("[")
      val (sura, ayah) = verses[0]
      append(quranDisplayData.getSuraName(activity, sura, true))
      append(": ")
      append(numberFormat.format(ayah))
      if (size > 1) {
        val (sura1, ayah1) = verses[size - 1]
        if (sura != sura1) {
          append(" - ")
          append(quranDisplayData.getSuraName(activity, sura1, true))
          append(": ")
        } else {
          append("-")
        }
        append(numberFormat.format(ayah1))
      }
      append(pageLabel(activity, SuraAyah(sura, ayah), verses[size - 1].let { SuraAyah(it.sura, it.ayah) }))
      // close sura label and append two new lines
      append("]")
    }
  }
}
