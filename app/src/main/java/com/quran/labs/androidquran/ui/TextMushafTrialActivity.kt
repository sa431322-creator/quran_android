package com.quran.labs.androidquran.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.feature.tajweed.data.TajweedPage
import com.quran.mobile.feature.tajweed.data.TajweedRepository
import com.quran.mobile.feature.tajweed.data.TextMushafEdition
import com.quran.mobile.feature.tajweed.ui.TajweedPageLabels
import com.quran.mobile.feature.tajweed.ui.TajweedPageStyle
import com.quran.mobile.feature.tajweed.ui.TajweedPageView
import dev.zacsweers.metro.Inject

/**
 * Trial only: a few pages of the Madinah mushaf V2 drawn from the King Fahd QPC V2 page fonts,
 * each next to a V2 page image (debug assets, from King Saud University's electronic mushaf),
 * to compare them before converting every page.
 */
class TextMushafTrialActivity : ComponentActivity() {
  @Inject lateinit var quranDisplayData: QuranDisplayData

  private enum class View(val label: String, val edition: TextMushafEdition?) {
    TEXT("متن V2", TextMushafEdition.MADINAH_V2_TRIAL),
    IMAGE("تصویر V2 (مرجع)", null)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    (application as QuranApplication).applicationComponent.inject(this)
    super.onCreate(savedInstanceState)
    val repositories = View.entries.mapNotNull { view ->
      view.edition?.let { view to TajweedRepository(this, "", it) }
    }.toMap()
    val basmalaFont = runCatching { FontFamily(TypefaceManager.getUthmaniTypeface(this)) }
      .getOrDefault(FontFamily.Default)
    val style = TajweedPageStyle(
      paper = Color(ContextCompat.getColor(this, R.color.page_background)),
      headerFont = FontFamily(TypefaceManager.getHeaderFooterTypeface(this)),
      headerColor = Color(ContextCompat.getColor(this, R.color.overlay_text_color)),
      suraFrameFill = Color(ContextCompat.getColor(this, R.color.mushaf_sura_header_fill)),
      suraFrameGold = Color(ContextCompat.getColor(this, R.color.mushaf_sura_header_gold)),
      suraFramePill = Color(ContextCompat.getColor(this, R.color.mushaf_sura_header_pill)),
      selection = Color(ContextCompat.getColor(this, R.color.selection_highlight)),
      recitation = Color(ContextCompat.getColor(this, R.color.audio_highlight))
    )
    val firstPage = intent.getIntExtra(EXTRA_PAGE, 0)

    setContentView(ComposeView(this).apply { setContent {
      QuranTheme {
        // every edition's trial pages, by page number
        val pages by produceState<Map<View, Map<Int, TajweedPage>>?>(null) {
          value = repositories.mapValues { (_, repository) ->
            runCatching { repository.data().pages }.getOrDefault(emptyList()).associateBy { it.page }
          }
        }
        Box(
          Modifier
            .fillMaxSize()
            .background(style.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
          val loaded = pages
          val pageNumbers = loaded?.values?.flatMap { it.keys }?.distinct()?.sorted().orEmpty()
          when {
            loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            pageNumbers.isEmpty() -> Text("No trial pages", Modifier.align(Alignment.Center))
            else -> TrialPager(pageNumbers, loaded, repositories, basmalaFont, style, firstPage)
          }
        }
      }
    } })
  }

  @Composable
  private fun TrialPager(
    pageNumbers: List<Int>,
    pages: Map<View, Map<Int, TajweedPage>>,
    repositories: Map<View, TajweedRepository>,
    basmalaFont: FontFamily,
    style: TajweedPageStyle,
    firstPage: Int
  ) {
    val start = pageNumbers.indexOf(firstPage).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = start) { pageNumbers.size }
    var view by rememberSaveable { mutableStateOf(View.TEXT) }
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
      ) {
        View.entries.forEach { option ->
          FilterChip(
            selected = view == option,
            onClick = { view = option },
            label = { Text(option.label) }
          )
        }
      }
      // pages turn right to left, like the reader
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val number = pageNumbers[index]
            val page = pages[view]?.get(number)
            val repository = repositories[view]
            when {
              view == View.IMAGE -> PageImage(number)
              page != null && repository != null -> PageText(page, repository, basmalaFont, style)
              else -> Box(Modifier.fillMaxSize()) {
                Text("Page $number is not in this trial", Modifier.align(Alignment.Center))
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun PageText(
    page: TajweedPage,
    repository: TajweedRepository,
    basmalaFont: FontFamily,
    style: TajweedPageStyle
  ) {
    val font by produceState<FontFamily?>(null, page.page, repository) {
      value = runCatching { FontFamily(Font(repository.font(page.page))) }.getOrNull()
    }
    val family = font
    if (family == null) {
      Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
      return
    }
    TajweedPageView(
      page = page,
      pageFont = family,
      basmalaFont = basmalaFont,
      style = style,
      labels = TajweedPageLabels(
        suraName = quranDisplayData.getSuraName(this, page.sura, true),
        juz = String.format(getString(R.string.juz2_description), QuranUtils.getLocalizedNumber(page.juz)),
        pageNumber = QuranUtils.getLocalizedNumber(page.page)
      ),
      suraHeaderName = { sura -> quranDisplayData.getSuraName(this, sura, true) }
    )
  }

  @Composable
  private fun PageImage(page: Int) {
    val bitmap = remember(page) {
      runCatching {
        assets.open("$REFERENCE_IMAGES/page${page.toString().padStart(3, '0')}.png").use(BitmapFactory::decodeStream)
      }.getOrNull()
    }
    Box(Modifier.fillMaxSize()) {
      if (bitmap == null) {
        Text("No V2 reference image for page $page", Modifier.align(Alignment.Center))
      } else {
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize().padding(4.dp)
        )
      }
    }
  }

  companion object {
    const val EXTRA_PAGE = "page"
    /** V2 page images for comparison, in the debug build's assets only */
    private const val REFERENCE_IMAGES = "text-mushaf-trial/v2-reference"
  }
}
