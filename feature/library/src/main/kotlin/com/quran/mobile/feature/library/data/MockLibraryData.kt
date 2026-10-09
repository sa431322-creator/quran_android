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
    book(
      "15",
      QURAN_SCIENCES,
      40,
      "قرآن و علوم نو بنیان",
      "نمونه‌هایی از اعجاز علمی قرآن کریم را بیان می‌کند و هماهنگی و انطباق آیات نورانی وحی را با " +
        "جدیدترین یافته‌های علمی دانشمندان مقایسه می‌نماید. منظور از اعجاز علمی، اخباری است که " +
        "قرآن کریم و یا سنت پیامبر ـ صلی الله علیه و سلم ـ در مورد حقایقی ارائه می‌دهد که علوم " +
        "تجربی آن را به اثبات رسانده است؛ حقایقی که امکان درک آنها با امکانات چهارده قرن پیش " +
        "ناممکن بوده است و این نشان از راستیِ نبوت پیامبر و ارتباط ایشان با سرچشمه وحی دارد. " +
        "نویسنده این اثر، که یکی از شناخته‌شده‌ترین دعوتگران معاصر است، در آغاز به موضوع " +
        "مبارزه‌طلبی (تحدی) قرآن کریم در عرصه بلاغی و علمی پرداخته و آن را یکی از مهم‌ترین " +
        "دلایل اعجاز و وثاقتِ کلام وحی می‌داند. وی در ادامه، یافته‌های جدید بشری را در " +
        "عرصه‌های گوناگون علمی برشمرده و هماهنگی آن را با آیات قرآن کریم نشان می‌دهد. برخی از " +
        "این رشته‌های علمی عبارتند از: اخترشناسی، فیزیک، جغرافیا، زیست شناسی گیاهی و جانوری، " +
        "پزشکی، فیزیولوژی، جنین شناسی و جاور شناسی.",
      fileUrl = BUNDLED_BOOK_PREFIX + "books/quran-and-modern-science.pdf"
    ),

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
    book("13", TAFSIR, 40, "[کتاب تفسیر ۴]", "تفسیر آیات اخلاقی", published = false),
    book(
      "16",
      TAFSIR,
      50,
      "تفسیر نور",
      "مجموعه دو تفسیر «انوار القرآن» و «نور» است که به ترجمه و توضیح آیات قرآن کریم اختصاص " +
        "دارد. تفسیر انوار القرآن، گزیده‌ای است از سه تفسیرِ «فتح القدیر» شوکانی، «تفسیر ابن کثیر» " +
        "و «تفسیر المنیر». در ابتدای این تفسیر، مقدمه کوتاهی درباره اعتقادات اهل سنت پیرامون " +
        "اسماء و صفات الهی آمده و در ادامه، ترجمه و توضیح آیات که در واقع گزیده‌ای از تفاسیر فوق " +
        "الذکر است، در قالب 5 جلد عرضه شده است. تفسیر نور نیز در 5 جلد ارائه شده و نویسنده کوشیده " +
        "است تا الفاظ مشکل و تعابیر دقیق آیات را با زبانی ساده و شفاف بیان نماید.",
      fileUrl = BUNDLED_BOOK_PREFIX + "books/tafsir-noor.pdf"
    )
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
