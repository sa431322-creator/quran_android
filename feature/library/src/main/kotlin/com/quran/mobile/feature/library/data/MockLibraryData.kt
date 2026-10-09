package com.quran.mobile.feature.library.data

import com.quran.mobile.feature.library.data.LibraryCategory.QURAN_SCIENCES
import com.quran.mobile.feature.library.data.LibraryCategory.QURAN_TRANSLATION
import com.quran.mobile.feature.library.data.LibraryCategory.TAFSIR
import com.quran.mobile.feature.library.data.LibraryCategory.TAJWEED

/**
 * Sample books for [MockLibraryApi]. The [bracketed] titles are placeholders for real books,
 * and none has a file yet, so every [LibraryBook.fileUrl] is null. Each category has one
 * unpublished book to exercise the published filter.
 */
internal object MockLibraryData {

  val BOOKS: List<LibraryBook> = listOf(
    book("1", QURAN_SCIENCES, 10, "[کتاب علوم قرآن ۱]", "درآمدی بر تاریخ نزول و جمع‌آوری قرآن"),
    book("2", QURAN_SCIENCES, 20, "[کتاب علوم قرآن ۲]", "آشنایی با مکی و مدنی و اسباب نزول"),
    book("3", QURAN_SCIENCES, 30, "[کتاب علوم قرآن ۳]", "ناسخ و منسوخ و محکم و متشابه", published = false),

    book("4", QURAN_TRANSLATION, 10, "[ترجمه قرآن ۱]", "ترجمهٔ روان فارسی همراه با متن عربی"),
    book("5", QURAN_TRANSLATION, 20, "[ترجمه قرآن ۲]", "ترجمهٔ تحت‌اللفظی برای آموزش واژگان"),
    book("6", QURAN_TRANSLATION, 30, "[ترجمه قرآن ۳]", "ترجمهٔ همراه با توضیحات کوتاه", published = false),

    book("7", TAJWEED, 10, "[کتاب تجوید ۱]", "مخارج حروف و صفات آن‌ها برای نوآموزان"),
    book("8", TAJWEED, 20, "[کتاب تجوید ۲]", "احکام نون ساکن، تنوین و مدّ"),
    book("9", TAJWEED, 30, "[کتاب تجوید ۳]", "وقف و ابتدا در تلاوت", published = false),

    book("10", TAFSIR, 10, "[کتاب تفسیر ۱]", "تفسیر موضوعی سوره‌های کوتاه"),
    book("11", TAFSIR, 20, "[کتاب تفسیر ۲]", "تفسیر ترتیبی جزء سی‌ام"),
    book("12", TAFSIR, 30, "[کتاب تفسیر ۳]", "درس‌هایی از تفسیر سورهٔ حمد"),
    book("13", TAFSIR, 40, "[کتاب تفسیر ۴]", "تفسیر آیات اخلاقی", published = false)
  )

  private fun book(
    id: String,
    category: LibraryCategory,
    order: Int,
    title: String,
    description: String,
    published: Boolean = true
  ) = LibraryBook(
    id = id,
    bookTitle = title,
    description = description,
    published = published,
    category = category,
    fileUrl = null,
    order = order
  )
}
