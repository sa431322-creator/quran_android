package com.quran.mobile.feature.library.data

import com.quran.mobile.feature.library.data.LibraryCategory.QURAN_SCIENCES
import com.quran.mobile.feature.library.data.LibraryCategory.QURAN_TRANSLATION
import com.quran.mobile.feature.library.data.LibraryCategory.TAFSIR
import com.quran.mobile.feature.library.data.LibraryCategory.TAJWEED

/**
 * Sample books for [MockLibraryApi]. The [bracketed] titles are placeholders for real books
 * and have no file; real books carry the PDF bundled under assets/books. Each category has
 * one unpublished book to exercise the published filter.
 */
internal object MockLibraryData {

  val BOOKS: List<LibraryBook> = listOf(
    book("1", QURAN_SCIENCES, 10, "[کتاب علوم قرآن ۱]", "درآمدی بر تاریخ نزول و جمع‌آوری قرآن"),
    book("2", QURAN_SCIENCES, 20, "[کتاب علوم قرآن ۲]", "آشنایی با مکی و مدنی و اسباب نزول"),
    book("3", QURAN_SCIENCES, 30, "[کتاب علوم قرآن ۳]", "ناسخ و منسوخ و محکم و متشابه", published = false),

    book("4", QURAN_TRANSLATION, 10, "[ترجمه قرآن ۱]", "ترجمهٔ روان فارسی همراه با متن عربی"),
    book("5", QURAN_TRANSLATION, 20, "[ترجمه قرآن ۲]", "ترجمهٔ تحت‌اللفظی برای آموزش واژگان"),
    book("6", QURAN_TRANSLATION, 30, "[ترجمه قرآن ۳]", "ترجمهٔ همراه با توضیحات کوتاه", published = false),
    book(
      "14",
      QURAN_TRANSLATION,
      40,
      "قرآن کریم و ترجمه معانی آن به زبان دری",
      "ترجمه معانی آیات قرآن کریم است. جای تردید نیست که یکی از مهم‌ترین ابزارهای پژوهشی و " +
        "راههای درک کامل پیام الله متعال، ترجمه صحیح و روان از معانی آیات کتاب خداست، و این کار، " +
        "در اثر حاضر به خوبی انجام شده است. این ترجمه به زبان دری توسط مولوی محمد انور بدخشانی " +
        "تدوین شده است و توسط دکتر عبدالغفور عبدالحق بلوچی و شيخ قريب الله مطيع مراجعه گردیده است.",
      fileUrl = BUNDLED_BOOK_PREFIX + "books/quran-dari-translation.pdf"
    ),

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
    published: Boolean = true,
    fileUrl: String? = null
  ) = LibraryBook(
    id = id,
    bookTitle = title,
    description = description,
    published = published,
    category = category,
    fileUrl = fileUrl,
    order = order
  )
}
