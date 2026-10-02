package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.persiantafsir.data.PersianTafsirRepository
import com.quran.mobile.persiantafsir.model.PersianTafsirAyah
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Sliding panel page showing the bundled (offline) Persian tafsir for the selected ayah(s).
 */
class AyahPersianTafsirFragment : AyahActionFragment() {
  private var scrollView: ScrollView? = null
  private var content: LinearLayout? = null
  private var emptyState: View? = null
  private var progressBar: ProgressBar? = null

  @Inject
  lateinit var quranSettings: QuranSettings

  @Inject
  lateinit var persianTafsirRepository: PersianTafsirRepository

  private val scope = MainScope()
  private var loadJob: Job? = null

  object Provider : AyahActionFragmentProvider {
    override val order = SlidingPagerAdapter.PERSIAN_TAFSIR_PAGE
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_persian_tafsir
    override fun newAyahActionFragment() = AyahPersianTafsirFragment()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  override fun onDetach() {
    scope.cancel()
    super.onDetach()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.persian_tafsir_panel, container, false)
    scrollView = view.findViewById(R.id.tafsir_scroll)
    content = view.findViewById(R.id.tafsir_content)
    emptyState = view.findViewById(R.id.empty_state)
    progressBar = view.findViewById(R.id.progress)

    val controls = view.findViewById<View>(R.id.controls)
    controls.findViewById<View>(R.id.next_ayah)
      .setOnClickListener { readingEventPresenter.selectNextAyah() }
    controls.findViewById<View>(R.id.previous_ayah)
      .setOnClickListener { readingEventPresenter.selectPreviousAyah() }

    ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
      val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
      controls.updatePadding(bottom = navBarInsets.bottom)
      insets
    }
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    refreshView()
  }

  override fun onDestroyView() {
    loadJob?.cancel()
    scrollView = null
    content = null
    emptyState = null
    progressBar = null
    super.onDestroyView()
  }

  override fun refreshView() {
    val start = start ?: return
    val end = end ?: return
    if (content == null) return

    loadJob?.cancel()
    progressBar?.visibility = View.VISIBLE
    loadJob = scope.launch {
      val ayat = persianTafsirRepository.tafsir(start, end)
      progressBar?.visibility = View.GONE
      if (ayat.isNullOrEmpty()) {
        content?.removeAllViews()
        emptyState?.visibility = View.VISIBLE
      } else {
        emptyState?.visibility = View.GONE
        showAyat(ayat)
      }
    }
  }

  private fun showAyat(ayat: List<PersianTafsirAyah>) {
    val content = content ?: return
    val context = content.context
    content.removeAllViews()

    val textSize = quranSettings.translationTextSize.toFloat()
    val textColor = ContextCompat.getColor(context, R.color.text_primary)
    val accentColor = ContextCompat.getColor(context, R.color.translation_translator_color)
    val horizontalMargin = resources.getDimensionPixelSize(R.dimen.translation_left_right_margin)
    val verticalMargin = resources.getDimensionPixelSize(R.dimen.translation_top_bottom_margin)

    fun addText(text: String, configure: TextView.() -> Unit) {
      val view = TextView(context).apply {
        this.text = text
        textDirection = View.TEXT_DIRECTION_RTL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        setTextColor(textColor)
        this.textSize = textSize
        configure()
      }
      val params = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
      )
      params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
      content.addView(view, params)
    }

    ayat.forEach { ayah ->
      addText(
        getString(
          R.string.persian_tafsir_ayah_header,
          ayah.suraName,
          String.format(PERSIAN, "%d", ayah.suraAyah.ayah)
        )
      ) {
        setTypeface(null, Typeface.BOLD)
      }
      ayah.bismillah?.let { bismillah ->
        addText(bismillah) {
          setTextColor(accentColor)
          textAlignment = View.TEXT_ALIGNMENT_GRAVITY
          gravity = Gravity.CENTER_HORIZONTAL
        }
      }
      addText(ayah.text) {
        setTextAppearance(R.style.TranslationText)
        setTextColor(textColor)
        this.textSize = textSize
        setLineSpacing(0f, 1.3f)
        setTextIsSelectable(true)
      }
    }
    scrollView?.scrollTo(0, 0)
  }

  companion object {
    private val PERSIAN: Locale = Locale.forLanguageTag("fa")
  }
}
