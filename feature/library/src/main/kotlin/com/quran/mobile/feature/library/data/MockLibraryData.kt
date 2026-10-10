package com.quran.mobile.feature.library.data

import com.quran.mobile.feature.library.data.LibraryCategory.QURAN_SCIENCES
import com.quran.mobile.feature.library.data.LibraryCategory.QURAN_TRANSLATION
import com.quran.mobile.feature.library.data.LibraryCategory.TAFSIR
import com.quran.mobile.feature.library.data.LibraryCategory.TAJWEED

/**
 * The library's books until the backend exists: one per category, each with its PDF bundled
 * under assets/books. Page counts and sizes are those of the bundled files.
 */
internal object MockLibraryData {

  val BOOKS: List<LibraryBook> = listOf(
    book(
      "15",
      QURAN_SCIENCES,
      10,
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
      fileUrl = BUNDLED_BOOK_PREFIX + "books/quran-and-modern-science.pdf",
      author = "دکتر ذاکر نایک",
      translator = "انس محمودی",
      language = "فارسی",
      pageCount = 89,
      fileSizeBytes = 885_443,
      tags = listOf("اعجاز علمی", "اخترشناسی", "پزشکی", "جنین‌شناسی"),
      chapters = MockLibraryChapters.MODERN_SCIENCE
    ),
    book(
      "14",
      QURAN_TRANSLATION,
      10,
      "قرآن کریم و ترجمه معانی آن به زبان دری",
      "ترجمه معانی آیات قرآن کریم است. جای تردید نیست که یکی از مهم‌ترین ابزارهای پژوهشی و " +
        "راههای درک کامل پیام الله متعال، ترجمه صحیح و روان از معانی آیات کتاب خداست، و این کار، " +
        "در اثر حاضر به خوبی انجام شده است. این ترجمه به زبان دری توسط مولوی محمد انور بدخشانی " +
        "تدوین شده است و توسط دکتر عبدالغفور عبدالحق بلوچی و شيخ قريب الله مطيع مراجعه گردیده است.",
      fileUrl = BUNDLED_BOOK_PREFIX + "books/quran-dari-translation.pdf",
      translator = "مولوی محمد انور بدخشانی",
      language = "دری",
      pageCount = 1249,
      fileSizeBytes = 21_313_400,
      tags = listOf("ترجمه", "دری", "متن کامل قرآن"),
      chapters = MockLibraryChapters.DARI_TRANSLATION
    ),
    book(
      "17",
      TAJWEED,
      10,
      "تجوید آسان",
      "درسنامه‌ای مشروح در قواعد علم تجوید و شیوه صحیح‌خوانی قرآن کریم است. کتاب با بیان اهمیت " +
        "تلاوت صحیحِ و زیبای قرآن و فضایل حفظ و از برداشتن آن آغاز می‌شود. سپس نویسنده به بحث " +
        "اصلی وارد شده و مختصری از علم تجوید و اصول تجویدی قرآن به روایتِ حفص از عاصم ابن ابی " +
        "النجود بازگو می‌کند. کتاب با بحثِ مخارج و صفات مهم حروف و احکام تلفظ آنها شروع شده و " +
        "سپس قاعده‌های شناسایی و تلفظ حروف متجانس و قلب و إخفا تشریح می‌گردد. بحث در شناسایی " +
        "مَدهای متّصل و مُنفصل و لازم و عارض و قواعد و علائم همزه وصل در ادامه می‌آید. پس از هر " +
        "درس، مجموعه تمریناتی برای درک و به یادسپاری بهتر مطالب ارائه شده است. کتاب، با بیان " +
        "نکاتی درباره اوصاف و ویژگی‌های قرآن و آداب و شرایط تلاوت آن خاتمه می‌یابد.",
      fileUrl = BUNDLED_BOOK_PREFIX + "books/tajweed-asan.pdf",
      author = "دکتر ابو عاصم عبدالعزیز عبدالفتاح قاری",
      translator = "عبدالکریم محمدی",
      language = "فارسی",
      pageCount = 109,
      fileSizeBytes = 1_251_399,
      tags = listOf("تجوید", "مخارج حروف", "روایت حفص"),
      chapters = MockLibraryChapters.TAJWEED_ASAN
    ),
    book(
      "16",
      TAFSIR,
      10,
      "تفسیر نور",
      "مجموعه دو تفسیر «انوار القرآن» و «نور» است که به ترجمه و توضیح آیات قرآن کریم اختصاص " +
        "دارد. تفسیر انوار القرآن، گزیده‌ای است از سه تفسیرِ «فتح القدیر» شوکانی، «تفسیر ابن کثیر» " +
        "و «تفسیر المنیر». در ابتدای این تفسیر، مقدمه کوتاهی درباره اعتقادات اهل سنت پیرامون " +
        "اسماء و صفات الهی آمده و در ادامه، ترجمه و توضیح آیات که در واقع گزیده‌ای از تفاسیر فوق " +
        "الذکر است، در قالب 5 جلد عرضه شده است. تفسیر نور نیز در 5 جلد ارائه شده و نویسنده کوشیده " +
        "است تا الفاظ مشکل و تعابیر دقیق آیات را با زبانی ساده و شفاف بیان نماید.",
      fileUrl = BUNDLED_BOOK_PREFIX + "books/tafsir-noor.pdf",
      language = "فارسی",
      pageCount = 2029,
      fileSizeBytes = 23_026_158,
      tags = listOf("تفسیر", "انوار القرآن")
    )
  )

  private fun book(
    id: String,
    category: LibraryCategory,
    order: Int,
    title: String,
    description: String,
    fileUrl: String,
    author: String? = null,
    translator: String? = null,
    language: String? = null,
    pageCount: Int? = null,
    fileSizeBytes: Long? = null,
    tags: List<String> = emptyList(),
    chapters: List<LibraryChapter> = emptyList()
  ) = LibraryBook(
    id = id,
    bookTitle = title,
    description = description,
    published = true,
    category = category,
    fileUrl = fileUrl,
    order = order,
    author = author,
    translator = translator,
    language = language,
    pageCount = pageCount,
    fileSizeBytes = fileSizeBytes,
    tags = tags,
    chapters = chapters
  )
}
